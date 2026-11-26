package com.example.data.repository

import com.example.data.local.ChatDao
import com.example.data.local.ChatEntity
import com.example.data.local.MessageEntity
import kotlinx.coroutines.flow.Flow
import java.io.File

object MessageTypes {
    const val TEXT = "TEXT"
    const val VOICE = "VOICE"
    const val IMAGE = "IMAGE"
    const val VIDEO = "VIDEO"
    const val DOCUMENT = "DOCUMENT"
    const val CALL = "CALL"
}

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
    val messageType: String = MessageTypes.TEXT,
    val mediaDuration: String? = null,
    val isViewOnce: Boolean = false,
    val mediaUriString: String? = null,
    val mediaFilePath: String? = null,
)

data class DeletedMessageSignal(
    val chatId: String,
    val notificationKey: String,
    val deletedAtTimestamp: Long,
    val chatTitle: String = "",
    val packageName: String = "com.whatsapp",
    val appLabel: String = "WhatsApp",
    val isGroup: Boolean = false,
)

class ChatRepository(private val chatDao: ChatDao) {
    val allChats: Flow<List<ChatEntity>> = chatDao.observeAllChats()
    val recentMessages: Flow<List<MessageEntity>> = chatDao.observeRecentMessages()
    val allMediaMessages: Flow<List<MessageEntity>> = chatDao.observeAllMediaMessages()
    val totalMessageCount: Flow<Int> = chatDao.observeTotalMessageCount()
    val deletedMessageCount: Flow<Int> = chatDao.observeDeletedMessageCount()
    val voiceMessageCount: Flow<Int> = chatDao.observeVoiceMessageCount()
    val mediaSavedCount: Flow<Int> = chatDao.observeMediaSavedCount()
    val totalUnreadCount: Flow<Int> = chatDao.observeTotalUnreadCount()

    fun observeChat(chatId: String): Flow<ChatEntity?> = chatDao.observeChat(chatId)

    fun observeMessagesForChat(chatId: String): Flow<List<MessageEntity>> =
        chatDao.observeMessagesForChat(chatId)

    suspend fun saveIncomingMessage(
        parsed: ParsedWhatsAppMessage,
        checkRecentDuplicate: Boolean = true,
    ): Boolean {
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
            messageType = parsed.messageType,
            mediaDuration = parsed.mediaDuration,
            isViewOnce = parsed.isViewOnce,
            mediaFilePath = parsed.mediaFilePath,
            checkRecentDuplicate = checkRecentDuplicate,
        )
    }

    suspend fun attachMediaToMessage(
        dedupKey: String,
        mediaFilePath: String,
        mediaDuration: String? = null,
    ) {
        chatDao.attachMediaToMessage(
            dedupKey = dedupKey,
            mediaFilePath = mediaFilePath,
            mediaDuration = mediaDuration,
        )
    }

    suspend fun markDeletedInWhatsApp(signal: DeletedMessageSignal): Boolean {
        return chatDao.markLatestMessageAsDeletedInWhatsApp(
            chatId = signal.chatId,
            notificationKey = signal.notificationKey,
            deletedAt = signal.deletedAtTimestamp,
            chatTitle = signal.chatTitle,
            packageName = signal.packageName,
            appLabel = signal.appLabel,
            isGroup = signal.isGroup,
        )
    }

    suspend fun markChatAsRead(chatId: String) = chatDao.markChatAsRead(chatId)

    suspend fun setPinned(chatId: String, pinned: Boolean) = chatDao.setPinned(chatId, pinned)

    suspend fun deleteChat(chatId: String) {
        val paths = chatDao.getMediaPathsForChat(chatId)
        chatDao.deleteChat(chatId)
        paths.forEach { path -> runCatching { File(path).delete() } }
    }

    suspend fun clearAll() {
        val paths = chatDao.getAllMediaPaths()
        chatDao.clearAll()
        paths.forEach { path -> runCatching { File(path).delete() } }
    }
}
