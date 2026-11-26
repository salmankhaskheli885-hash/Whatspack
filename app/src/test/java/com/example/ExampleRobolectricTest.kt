package com.example

import com.example.data.local.ChatDao
import com.example.data.local.ChatEntity
import com.example.data.local.MessageEntity
import com.example.data.repository.ChatRepository
import com.example.service.RawNotificationPayload
import com.example.service.WhatsAppNotificationParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class InMemoryChatDao : ChatDao {
    private val chatsFlow = MutableStateFlow<List<ChatEntity>>(emptyList())
    private val messagesFlow = MutableStateFlow<List<MessageEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAllChats(): Flow<List<ChatEntity>> = chatsFlow.map { list ->
        list.sortedWith(compareByDescending<ChatEntity> { it.isPinned }.thenByDescending { it.lastTimestamp })
    }

    override fun observeChat(chatId: String): Flow<ChatEntity?> =
        chatsFlow.map { list -> list.firstOrNull { it.chatId == chatId } }

    override suspend fun getChatOnce(chatId: String): ChatEntity? =
        chatsFlow.value.firstOrNull { it.chatId == chatId }

    override fun observeMessagesForChat(chatId: String): Flow<List<MessageEntity>> =
        messagesFlow.map { list ->
            list.filter { it.chatId == chatId }.sortedWith(compareBy<MessageEntity> { it.timestamp }.thenBy { it.id })
        }

    override fun observeRecentMessages(limit: Int): Flow<List<MessageEntity>> =
        messagesFlow.map { list ->
            list.sortedWith(compareByDescending<MessageEntity> { it.timestamp }.thenByDescending { it.id }).take(limit)
        }

    override fun observeTotalMessageCount(): Flow<Int> = messagesFlow.map { it.size }

    override fun observeDeletedMessageCount(): Flow<Int> =
        messagesFlow.map { list -> list.count { it.deletedInWhatsApp } }

    override fun observeTotalUnreadCount(): Flow<Int> =
        chatsFlow.map { list -> list.sumOf { it.unreadCount } }

    override suspend fun getLatestActiveMessageByKey(
        chatId: String,
        notificationKey: String,
    ): MessageEntity? = messagesFlow.value
        .filter { it.chatId == chatId && it.notificationKey == notificationKey && !it.deletedInWhatsApp }
        .maxWithOrNull(compareBy<MessageEntity> { it.timestamp }.thenBy { it.id })

    override suspend fun getLatestActiveMessageForChat(chatId: String): MessageEntity? =
        messagesFlow.value
            .filter { it.chatId == chatId && !it.deletedInWhatsApp }
            .maxWithOrNull(compareBy<MessageEntity> { it.timestamp }.thenBy { it.id })

    override suspend fun countDeletedInChat(chatId: String): Int =
        messagesFlow.value.count { it.chatId == chatId && it.deletedInWhatsApp }

    override suspend fun markMessageDeletedInWhatsApp(messageId: Long, deletedAt: Long) {
        messagesFlow.value = messagesFlow.value.map {
            if (it.id == messageId) it.copy(deletedInWhatsApp = true, deletedAtTimestamp = deletedAt) else it
        }
    }

    override suspend fun updateChatDeletedCount(chatId: String, deletedCount: Int) {
        chatsFlow.value = chatsFlow.value.map {
            if (it.chatId == chatId) it.copy(deletedCount = deletedCount) else it
        }
    }

    override suspend fun upsertChat(chat: ChatEntity) {
        val updated = chatsFlow.value.filterNot { it.chatId == chat.chatId } + chat
        chatsFlow.value = updated
    }

    override suspend fun insertMessage(message: MessageEntity): Long {
        if (messagesFlow.value.any { it.dedupKey == message.dedupKey }) {
            return -1L
        }
        val assigned = message.copy(id = nextId++)
        messagesFlow.value = messagesFlow.value + assigned
        return assigned.id
    }

    override suspend fun markChatAsRead(chatId: String) {
        chatsFlow.value = chatsFlow.value.map {
            if (it.chatId == chatId) it.copy(unreadCount = 0) else it
        }
    }

    override suspend fun setPinned(chatId: String, pinned: Boolean) {
        chatsFlow.value = chatsFlow.value.map {
            if (it.chatId == chatId) it.copy(isPinned = pinned) else it
        }
    }

    override suspend fun deleteChat(chatId: String) {
        chatsFlow.value = chatsFlow.value.filterNot { it.chatId == chatId }
        messagesFlow.value = messagesFlow.value.filterNot { it.chatId == chatId }
    }

    override suspend fun deleteAllMessages() {
        messagesFlow.value = emptyList()
    }

    override suspend fun deleteAllChats() {
        chatsFlow.value = emptyList()
    }
}

class ChatRepositoryTest {

    @Test
    fun `repository preserves original message when WhatsApp deletes it`() = runTest {
        val repository = ChatRepository(InMemoryChatDao())
        val initialEvents = WhatsAppNotificationParser.parseEvents(
            RawNotificationPayload(
                packageName = "com.whatsapp",
                notificationKey = "0|com.whatsapp|1|null|1001",
                postTime = 1_764_100_000_000L,
                flags = 0,
                title = "Aarav Sharma",
                text = "Kal milte hain 5 baje",
                bigText = null,
                conversationTitle = null,
                isGroupConversation = false,
                styleMessages = emptyList(),
            )
        )
        assertEquals(1, initialEvents.messages.size)
        assertTrue(repository.saveIncomingMessage(initialEvents.messages.first()))
        assertFalse(repository.saveIncomingMessage(initialEvents.messages.first()))

        // Sender now deletes the message in WhatsApp ("Delete for everyone")
        val deletedEvents = WhatsAppNotificationParser.parseEvents(
            RawNotificationPayload(
                packageName = "com.whatsapp",
                notificationKey = "0|com.whatsapp|1|null|1001",
                postTime = 1_764_100_005_000L,
                flags = 0,
                title = "Aarav Sharma",
                text = "This message was deleted",
                bigText = null,
                conversationTitle = null,
                isGroupConversation = false,
                styleMessages = emptyList(),
            )
        )
        assertEquals(1, deletedEvents.deletedSignals.size)
        assertTrue(repository.markDeletedInWhatsApp(deletedEvents.deletedSignals.first()))

        val chats = repository.allChats.first()
        assertEquals(1, chats.size)
        assertEquals("Kal milte hain 5 baje", chats.first().lastMessageText)
        assertEquals(1, chats.first().deletedCount)
        assertEquals(1, repository.deletedMessageCount.first())

        val messages = repository.observeMessagesForChat(chats.first().chatId).first()
        assertEquals(1, messages.size)
        assertEquals("Kal milte hain 5 baje", messages.first().body)
        assertTrue(messages.first().deletedInWhatsApp)
    }
}
