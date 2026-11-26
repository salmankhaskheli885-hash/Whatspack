package com.example.data.repository

import com.example.data.local.ChatDao
import com.example.data.local.ChatEntity
import com.example.data.local.MessageEntity
import kotlinx.coroutines.flow.Flow

data class ParsedWhatsAppMessage(
    val chatId: String,
    val chatTitle: String,
    val senderName: String,
    val body: String,
    val timestamp: Long,
    val packageName: String,
    val appLabel: String,
    val isGroup: Boolean,
    val notificationKey: String,
    val dedupKey: String,
)

data class DeletedMessageSignal(
    val chatId: String,
    val notificationKey: String,
    val deletedAtTimestamp: Long,
)

class ChatRepository(private val chatDao: ChatDao) {
    val allChats: Flow<List<ChatEntity>> = chatDao.observeAllChats()
    val recentMessages: Flow<List<MessageEntity>> = chatDao.observeRecentMessages()
    val totalMessageCount: Flow<Int> = chatDao.observeTotalMessageCount()
    val deletedMessageCount: Flow<Int> = chatDao.observeDeletedMessageCount()
    val totalUnreadCount: Flow<Int> = chatDao.observeTotalUnreadCount()

    fun observeChat(chatId: String): Flow<ChatEntity?> = chatDao.observeChat(chatId)

    fun observeMessagesForChat(chatId: String): Flow<List<MessageEntity>> =
        chatDao.observeMessagesForChat(chatId)

    suspend fun saveIncomingMessage(parsed: ParsedWhatsAppMessage): Boolean {
        return chatDao.recordIncomingMessage(
            chatId = parsed.chatId,
            chatTitle = parsed.chatTitle,
            packageName = parsed.packageName,
            appLabel = parsed.appLabel,
            isGroup = parsed.isGroup,
            senderName = parsed.senderName,
            body = parsed.body,
            timestamp = parsed.timestamp,
            notificationKey = parsed.notificationKey,
            dedupKey = parsed.dedupKey,
        )
    }

    suspend fun markDeletedInWhatsApp(signal: DeletedMessageSignal): Boolean {
        return chatDao.markLatestMessageAsDeletedInWhatsApp(
            chatId = signal.chatId,
            notificationKey = signal.notificationKey,
            deletedAt = signal.deletedAtTimestamp,
        )
    }

    suspend fun markChatAsRead(chatId: String) = chatDao.markChatAsRead(chatId)

    suspend fun setPinned(chatId: String, pinned: Boolean) = chatDao.setPinned(chatId, pinned)

    suspend fun deleteChat(chatId: String) = chatDao.deleteChat(chatId)

    suspend fun clearAll() = chatDao.clearAll()
}
