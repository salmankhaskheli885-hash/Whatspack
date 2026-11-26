package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["chatId"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["chatId"]),
        Index(value = ["dedupKey"], unique = true),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val chatId: String,
    val senderName: String,
    val body: String,
    val timestamp: Long,
    val packageName: String,
    val appLabel: String,
    val isGroupMessage: Boolean,
    val notificationKey: String,
    val dedupKey: String,
    val deletedInWhatsApp: Boolean = false,
    val deletedAtTimestamp: Long? = null,
)
