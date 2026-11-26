package com.example.service

import android.content.Context
import android.net.Uri
import com.example.data.local.ChatEntity
import com.example.data.local.MessageEntity
import com.example.data.repository.ChatRepository
import com.example.data.repository.DeletedMessageSignal
import com.example.data.repository.ParsedWhatsAppMessage
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

object ChatBackupManager {

    suspend fun exportBackupToUri(
        context: Context,
        targetUri: Uri,
        repository: ChatRepository,
    ): String {
        val chats: List<ChatEntity> = repository.allChats.first()
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        val chatsArray = JSONArray()
        var totalMessages = 0

        for (chat in chats) {
            val msgs: List<MessageEntity> = repository.observeMessagesForChat(chat.chatId).first()
            val chatJson = JSONObject().apply {
                put("chatId", chat.chatId)
                put("title", chat.title)
                put("packageName", chat.packageName)
                put("appLabel", chat.appLabel)
                put("isGroup", chat.isGroup)
            }
            val msgArray = JSONArray()
            for (msg in msgs) {
                totalMessages++
                msgArray.put(
                    JSONObject().apply {
                        put("senderName", msg.senderName)
                        put("body", msg.body)
                        put("timestamp", msg.timestamp)
                        put("packageName", msg.packageName)
                        put("appLabel", msg.appLabel)
                        put("isGroupMessage", msg.isGroupMessage)
                        put("notificationKey", msg.notificationKey)
                        put("dedupKey", msg.dedupKey)
                        put("deletedInWhatsApp", msg.deletedInWhatsApp)
                        put("deletedAtTimestamp", msg.deletedAtTimestamp ?: 0L)
                        put("messageType", msg.messageType)
                        put("mediaDuration", msg.mediaDuration ?: "")
                        put("isViewOnce", msg.isViewOnce)
                        put("mediaFilePath", msg.mediaFilePath ?: "")
                    }
                )
            }
            chatJson.put("messages", msgArray)
            chatsArray.put(chatJson)
        }

        root.put("chats", chatsArray)
        context.contentResolver.openOutputStream(targetUri)?.bufferedWriter()?.use { writer ->
            writer.write(root.toString(2))
        } ?: return "Could not write backup file to phone storage."

        return "Saved backup ($totalMessages messages across ${chats.size} chats) to phone storage."
    }

    suspend fun restoreBackupFromUri(
        context: Context,
        sourceUri: Uri,
        repository: ChatRepository,
    ): StorageImportSummary {
        val raw = context.contentResolver.openInputStream(sourceUri)?.bufferedReader()?.use { it.readText() }
            ?: return StorageImportSummary(0, 0, "Could not read backup file.")

        val root = runCatching { JSONObject(raw) }.getOrNull()
            ?: return StorageImportSummary(0, 0, "Invalid ChatReader backup file.")
        val chatsArray = root.optJSONArray("chats")
            ?: return StorageImportSummary(0, 0, "No chats found in backup file.")

        var imported = 0
        var skipped = 0

        for (i in 0 until chatsArray.length()) {
            val chatJson = chatsArray.optJSONObject(i) ?: continue
            val chatId = chatJson.optString("chatId")
            val chatTitle = chatJson.optString("title")
            val pkg = chatJson.optString("packageName", "com.whatsapp")
            val appLabel = chatJson.optString("appLabel", "WhatsApp")
            val isGroup = chatJson.optBoolean("isGroup", false)
            val msgs = chatJson.optJSONArray("messages") ?: continue

            for (j in 0 until msgs.length()) {
                val m = msgs.optJSONObject(j) ?: continue
                val parsed = ParsedWhatsAppMessage(
                    chatId = chatId,
                    chatTitle = chatTitle,
                    senderName = m.optString("senderName", chatTitle),
                    body = m.optString("body"),
                    timestamp = m.optLong("timestamp"),
                    packageName = m.optString("packageName", pkg),
                    appLabel = m.optString("appLabel", appLabel),
                    isGroup = m.optBoolean("isGroupMessage", isGroup),
                    notificationKey = m.optString("notificationKey"),
                    dedupKey = m.optString("dedupKey"),
                    messageType = m.optString("messageType", "TEXT"),
                    mediaDuration = m.optString("mediaDuration").takeIf { it.isNotBlank() },
                    isViewOnce = m.optBoolean("isViewOnce", false),
                    mediaFilePath = m.optString("mediaFilePath").takeIf { it.isNotBlank() },
                )
                val inserted = repository.saveIncomingMessage(parsed, checkRecentDuplicate = false)
                if (inserted) {
                    imported++
                    if (m.optBoolean("deletedInWhatsApp", false)) {
                        val deletedTs = m.optLong("deletedAtTimestamp", parsed.timestamp)
                        repository.markDeletedInWhatsApp(
                            DeletedMessageSignal(
                                chatId = chatId,
                                notificationKey = parsed.notificationKey,
                                deletedAtTimestamp = if (deletedTs > 0L) deletedTs else parsed.timestamp,
                            )
                        )
                    }
                } else {
                    skipped++
                }
            }
        }

        val summary = when {
            imported > 0 -> "Restored $imported message(s) from phone storage backup."
            skipped > 0 -> "All $skipped backed-up messages are already saved."
            else -> "Backup file contained no messages."
        }
        return StorageImportSummary(imported, skipped, summary)
    }
}
