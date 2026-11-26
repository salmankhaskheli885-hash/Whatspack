package com.example

import com.example.data.local.AdminPromoConfig
import com.example.data.local.AdminPromoManager
import com.example.data.local.ChatDao
import com.example.data.local.ChatEntity
import com.example.data.local.MessageEntity
import com.example.data.repository.ChatRepository
import com.example.data.repository.MessageTypes
import com.example.service.RawNotificationPayload
import com.example.service.WhatsAppNotificationParser
import com.example.util.AppShareHelper
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

    override fun observeAllMediaMessages(): Flow<List<MessageEntity>> =
        messagesFlow.map { list ->
            list.filter {
                it.messageType in setOf(
                    MessageTypes.IMAGE,
                    MessageTypes.VIDEO,
                    MessageTypes.VOICE,
                    MessageTypes.DOCUMENT,
                ) || it.isViewOnce || it.mediaFilePath != null
            }.sortedWith(compareByDescending<MessageEntity> { it.timestamp }.thenByDescending { it.id })
        }

    override fun observeTotalMessageCount(): Flow<Int> = messagesFlow.map { it.size }

    override fun observeDeletedMessageCount(): Flow<Int> =
        messagesFlow.map { list -> list.count { it.deletedInWhatsApp } }

    override fun observeVoiceMessageCount(): Flow<Int> =
        messagesFlow.map { list -> list.count { it.messageType == MessageTypes.VOICE } }

    override fun observeMediaSavedCount(): Flow<Int> =
        messagesFlow.map { list -> list.count { it.isViewOnce || it.mediaFilePath != null } }

    override fun observeTotalUnreadCount(): Flow<Int> =
        chatsFlow.map { list -> list.sumOf { it.unreadCount } }

    override suspend fun getMediaPathsForChat(chatId: String): List<String> =
        messagesFlow.value.filter { it.chatId == chatId }.mapNotNull { it.mediaFilePath }

    override suspend fun getAllMediaPaths(): List<String> =
        messagesFlow.value.mapNotNull { it.mediaFilePath }

    override suspend fun countMatchingRecentMessages(
        chatId: String,
        senderName: String,
        body: String,
        minTimestamp: Long,
        maxTimestamp: Long,
    ): Int = messagesFlow.value.count {
        it.chatId == chatId &&
            it.senderName == senderName &&
            it.body == body &&
            it.timestamp in minTimestamp..maxTimestamp
    }

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

    override suspend fun getLatestMessageForChat(chatId: String): MessageEntity? =
        messagesFlow.value
            .filter { it.chatId == chatId }
            .maxWithOrNull(compareBy<MessageEntity> { it.timestamp }.thenBy { it.id })

    override suspend fun countDeletedInChat(chatId: String): Int =
        messagesFlow.value.count { it.chatId == chatId && it.deletedInWhatsApp }

    override suspend fun markMessageDeletedInWhatsApp(messageId: Long, deletedAt: Long) {
        messagesFlow.value = messagesFlow.value.map {
            if (it.id == messageId) it.copy(deletedInWhatsApp = true, deletedAtTimestamp = deletedAt) else it
        }
    }

    override suspend fun attachMediaToMessage(
        dedupKey: String,
        mediaFilePath: String,
        mediaDuration: String?,
    ) {
        messagesFlow.value = messagesFlow.value.map {
            if (it.dedupKey == dedupKey && it.mediaFilePath.isNullOrBlank()) {
                it.copy(
                    mediaFilePath = mediaFilePath,
                    mediaDuration = it.mediaDuration ?: mediaDuration,
                )
            } else {
                it
            }
        }
    }

    override suspend fun updateChatDeletedCount(chatId: String, deletedCount: Int) {
        chatsFlow.value = chatsFlow.value.map {
            if (it.chatId == chatId) it.copy(deletedCount = deletedCount) else it
        }
    }

    override suspend fun insertChatIfAbsent(chat: ChatEntity): Long {
        if (chatsFlow.value.any { it.chatId == chat.chatId }) return -1L
        chatsFlow.value = chatsFlow.value + chat
        return 1L
    }

    override suspend fun updateChat(chat: ChatEntity) {
        chatsFlow.value = chatsFlow.value.map {
            if (it.chatId == chat.chatId) chat else it
        }
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

    @Test
    fun `repository saves WA Business voice message and tracks voice count`() = runTest {
        val repository = ChatRepository(InMemoryChatDao())
        val events = WhatsAppNotificationParser.parseEvents(
            RawNotificationPayload(
                packageName = "com.whatsapp.w4b",
                notificationKey = "w4b_voice",
                postTime = 1_764_100_010_000L,
                flags = 0,
                title = "Client Ayesha",
                text = "🎤 Voice message (0:19)",
                bigText = null,
                conversationTitle = null,
                isGroupConversation = false,
                styleMessages = emptyList(),
            )
        )

        assertEquals(1, events.messages.size)
        assertTrue(repository.saveIncomingMessage(events.messages.first()))
        assertEquals(1, repository.voiceMessageCount.first())

        val chat = repository.allChats.first().first()
        assertEquals("WA Business", chat.appLabel)
        assertEquals(MessageTypes.VOICE, chat.lastMessageType)
    }

    @Test
    fun `repository keeps View Once media record even after WhatsApp deletion until user deletes`() = runTest {
        val repository = ChatRepository(InMemoryChatDao())
        val events = WhatsAppNotificationParser.parseEvents(
            RawNotificationPayload(
                packageName = "com.whatsapp",
                notificationKey = "once_photo_key",
                postTime = 1_764_100_020_000L,
                flags = 0,
                title = "Zaid",
                text = "📷 View once photo",
                bigText = null,
                conversationTitle = null,
                isGroupConversation = false,
                styleMessages = emptyList(),
            )
        )

        assertEquals(1, events.messages.size)
        val parsed = events.messages.first().copy(mediaFilePath = "/tmp/fake_preview.jpg")
        assertTrue(parsed.isViewOnce)
        assertTrue(repository.saveIncomingMessage(parsed))
        assertEquals(1, repository.mediaSavedCount.first())

        val deletedEvents = WhatsAppNotificationParser.parseEvents(
            RawNotificationPayload(
                packageName = "com.whatsapp",
                notificationKey = "once_photo_key",
                postTime = 1_764_100_025_000L,
                flags = 0,
                title = "Zaid",
                text = "This message was deleted",
                bigText = null,
                conversationTitle = null,
                isGroupConversation = false,
                styleMessages = emptyList(),
            )
        )
        assertTrue(repository.markDeletedInWhatsApp(deletedEvents.deletedSignals.first()))

        val savedMessages = repository.observeMessagesForChat(parsed.chatId).first()
        assertEquals(1, savedMessages.size)
        assertTrue(savedMessages.first().isViewOnce)
        assertTrue(savedMessages.first().deletedInWhatsApp)
        assertEquals("/tmp/fake_preview.jpg", savedMessages.first().mediaFilePath)

        repository.deleteChat(parsed.chatId)
        assertTrue(repository.allChats.first().isEmpty())
        assertEquals(0, repository.mediaSavedCount.first())
    }

    @Test
    fun `repository retains all messages inside a chat across multiple inserts and updates`() = runTest {
        val repository = ChatRepository(InMemoryChatDao())
        val first = WhatsAppNotificationParser.parse(
            RawNotificationPayload(
                packageName = "com.whatsapp",
                notificationKey = "k1",
                postTime = 1_764_200_000_000L,
                flags = 0,
                title = "My Tel",
                text = "First message",
                bigText = null,
                conversationTitle = null,
                isGroupConversation = false,
                styleMessages = emptyList(),
            )
        ).first()
        val second = WhatsAppNotificationParser.parse(
            RawNotificationPayload(
                packageName = "com.whatsapp",
                notificationKey = "k2",
                postTime = 1_764_200_010_000L,
                flags = 0,
                title = "My Tel",
                text = "Second message",
                bigText = null,
                conversationTitle = null,
                isGroupConversation = false,
                styleMessages = emptyList(),
            )
        ).first()
        val third = WhatsAppNotificationParser.parse(
            RawNotificationPayload(
                packageName = "com.whatsapp",
                notificationKey = "k3",
                postTime = 1_764_200_020_000L,
                flags = 0,
                title = "My Tel",
                text = "Third message",
                bigText = null,
                conversationTitle = null,
                isGroupConversation = false,
                styleMessages = emptyList(),
            )
        ).first()

        assertTrue(repository.saveIncomingMessage(first))
        assertTrue(repository.saveIncomingMessage(second))
        assertTrue(repository.saveIncomingMessage(third))

        val chat = repository.allChats.first().single()
        assertEquals(3, chat.messageCount)
        assertEquals("Third message", chat.lastMessageText)

        val messages = repository.observeMessagesForChat(chat.chatId).first()
        assertEquals(3, messages.size)
        assertEquals(listOf("First message", "Second message", "Third message"), messages.map { it.body })
    }

    @Test
    fun `repository attaches SAF media to existing notification message and exposes it in allMediaMessages`() = runTest {
        val repository = ChatRepository(InMemoryChatDao())
        val photoEvent = WhatsAppNotificationParser.parse(
            RawNotificationPayload(
                packageName = "com.whatsapp",
                notificationKey = "photo_notif",
                postTime = 1_764_300_000_000L,
                flags = 0,
                title = "Bilal",
                text = "📷 Photo",
                bigText = null,
                conversationTitle = null,
                isGroupConversation = false,
                styleMessages = emptyList(),
            )
        ).first()

        assertTrue(repository.saveIncomingMessage(photoEvent))
        repository.attachMediaToMessage(
            dedupKey = photoEvent.dedupKey,
            mediaFilePath = "/data/user/0/com.example/files/captured_media/IMG-01.jpg",
            mediaDuration = null,
        )

        val mediaItems = repository.allMediaMessages.first()
        assertEquals(1, mediaItems.size)
        assertEquals("/data/user/0/com.example/files/captured_media/IMG-01.jpg", mediaItems.first().mediaFilePath)
    }

    @Test
    fun `repository records deleted notice even when original message was missed`() = runTest {
        val repository = ChatRepository(InMemoryChatDao())
        val deletedOnly = WhatsAppNotificationParser.parseEvents(
            RawNotificationPayload(
                packageName = "com.whatsapp",
                notificationKey = "deleted_only_key",
                postTime = 1_764_400_000_000L,
                flags = 0,
                title = "Usman",
                text = "This message was deleted",
                bigText = null,
                conversationTitle = null,
                isGroupConversation = false,
                styleMessages = emptyList(),
            )
        )
        assertEquals(1, deletedOnly.deletedSignals.size)
        assertTrue(repository.markDeletedInWhatsApp(deletedOnly.deletedSignals.first()))
        assertFalse(
            repository.markDeletedInWhatsApp(
                deletedOnly.deletedSignals.first().copy(deletedAtTimestamp = 1_764_400_005_000L)
            )
        )

        val chat = repository.allChats.first().single()
        assertEquals("Usman", chat.title)
        assertEquals(1, chat.deletedCount)
        val msgs = repository.observeMessagesForChat(chat.chatId).first()
        assertEquals(1, msgs.size)
        assertTrue(msgs.first().deletedInWhatsApp)
    }

    @Test
    fun `admin panel verifies pin 2124 and embeds promo text in share message`() {
        assertTrue(AdminPromoManager.verifyPin("2124"))
        assertTrue(AdminPromoManager.verifyPin(" 2124 "))
        assertFalse(AdminPromoManager.verifyPin("0000"))
        assertFalse(AdminPromoManager.verifyPin("2125"))

        val withPromo = AppShareHelper.buildShareMessage(
            AdminPromoConfig(promoText = "Special Offer: Contact 03112765988"),
        )
        assertTrue(withPromo.contains("Special Offer: Contact 03112765988"))
        assertTrue(withPromo.contains(AppShareHelper.DIRECT_APK_DOWNLOAD_URL))
    }
}
