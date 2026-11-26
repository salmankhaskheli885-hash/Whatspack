package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY isPinned DESC, lastTimestamp DESC")
    fun observeAllChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE chatId = :chatId LIMIT 1")
    fun observeChat(chatId: String): Flow<ChatEntity?>

    @Query("SELECT * FROM chats WHERE chatId = :chatId LIMIT 1")
    suspend fun getChatOnce(chatId: String): ChatEntity?

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC, id ASC")
    fun observeMessagesForChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecentMessages(limit: Int = 100): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages")
    fun observeTotalMessageCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE deletedInWhatsApp = 1")
    fun observeDeletedMessageCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(unreadCount), 0) FROM chats")
    fun observeTotalUnreadCount(): Flow<Int>

    @Query(
        "SELECT * FROM messages WHERE chatId = :chatId AND notificationKey = :notificationKey " +
            "AND deletedInWhatsApp = 0 ORDER BY timestamp DESC, id DESC LIMIT 1"
    )
    suspend fun getLatestActiveMessageByKey(chatId: String, notificationKey: String): MessageEntity?

    @Query(
        "SELECT * FROM messages WHERE chatId = :chatId AND deletedInWhatsApp = 0 " +
            "ORDER BY timestamp DESC, id DESC LIMIT 1"
    )
    suspend fun getLatestActiveMessageForChat(chatId: String): MessageEntity?

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND deletedInWhatsApp = 1")
    suspend fun countDeletedInChat(chatId: String): Int

    @Query("UPDATE messages SET deletedInWhatsApp = 1, deletedAtTimestamp = :deletedAt WHERE id = :messageId")
    suspend fun markMessageDeletedInWhatsApp(messageId: Long, deletedAt: Long)

    @Query("UPDATE chats SET deletedCount = :deletedCount WHERE chatId = :chatId")
    suspend fun updateChatDeletedCount(chatId: String, deletedCount: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChat(chat: ChatEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("UPDATE chats SET unreadCount = 0 WHERE chatId = :chatId")
    suspend fun markChatAsRead(chatId: String)

    @Query("UPDATE chats SET isPinned = :pinned WHERE chatId = :chatId")
    suspend fun setPinned(chatId: String, pinned: Boolean)

    @Query("DELETE FROM chats WHERE chatId = :chatId")
    suspend fun deleteChat(chatId: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM chats")
    suspend fun deleteAllChats()

    @Transaction
    suspend fun recordIncomingMessage(
        chatId: String,
        chatTitle: String,
        packageName: String,
        appLabel: String,
        isGroup: Boolean,
        senderName: String,
        body: String,
        timestamp: Long,
        notificationKey: String,
        dedupKey: String,
    ): Boolean {
        val existingChat = getChatOnce(chatId)
        if (existingChat == null) {
            upsertChat(
                ChatEntity(
                    chatId = chatId,
                    title = chatTitle,
                    packageName = packageName,
                    appLabel = appLabel,
                    isGroup = isGroup,
                    lastMessageText = body,
                    lastSenderName = senderName,
                    lastTimestamp = timestamp,
                    messageCount = 0,
                    unreadCount = 0,
                    isPinned = false,
                    deletedCount = 0,
                )
            )
        }

        val rowId = insertMessage(
            MessageEntity(
                chatId = chatId,
                senderName = senderName,
                body = body,
                timestamp = timestamp,
                packageName = packageName,
                appLabel = appLabel,
                isGroupMessage = isGroup,
                notificationKey = notificationKey,
                dedupKey = dedupKey,
            )
        )

        if (rowId == -1L) {
            return false
        }

        val currentChat = getChatOnce(chatId)
        val shouldUpdatePreview = currentChat == null || timestamp >= currentChat.lastTimestamp
        upsertChat(
            ChatEntity(
                chatId = chatId,
                title = chatTitle,
                packageName = packageName,
                appLabel = appLabel,
                isGroup = isGroup || (currentChat?.isGroup == true),
                lastMessageText = if (shouldUpdatePreview) body else currentChat!!.lastMessageText,
                lastSenderName = if (shouldUpdatePreview) senderName else currentChat!!.lastSenderName,
                lastTimestamp = if (shouldUpdatePreview) timestamp else currentChat!!.lastTimestamp,
                messageCount = (currentChat?.messageCount ?: 0) + 1,
                unreadCount = (currentChat?.unreadCount ?: 0) + 1,
                isPinned = currentChat?.isPinned ?: false,
                deletedCount = currentChat?.deletedCount ?: 0,
            )
        )
        return true
    }

    @Transaction
    suspend fun markLatestMessageAsDeletedInWhatsApp(
        chatId: String,
        notificationKey: String,
        deletedAt: Long,
    ): Boolean {
        val target = if (notificationKey.isNotBlank()) {
            getLatestActiveMessageByKey(chatId, notificationKey)
                ?: getLatestActiveMessageForChat(chatId)
        } else {
            getLatestActiveMessageForChat(chatId)
        } ?: return false

        markMessageDeletedInWhatsApp(target.id, deletedAt)
        val updatedCount = countDeletedInChat(chatId)
        updateChatDeletedCount(chatId, updatedCount)
        return true
    }

    @Transaction
    suspend fun clearAll() {
        deleteAllMessages()
        deleteAllChats()
    }
}
