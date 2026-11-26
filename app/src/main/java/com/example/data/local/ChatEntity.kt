package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val chatId: String,
    val title: String,
    val packageName: String,
    val appLabel: String,
    val isGroup: Boolean,
    val lastMessageText: String,
    val lastSenderName: String,
    val lastTimestamp: Long,
    val messageCount: Int,
    val unreadCount: Int,
    val isPinned: Boolean = false,
    val deletedCount: Int = 0,
    val lastMessageType: String = "TEXT",
)
