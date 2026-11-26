package com.example.service

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.data.repository.DeletedMessageSignal
import com.example.data.repository.ParsedWhatsAppMessage
import java.security.MessageDigest

data class RawNotificationPayload(
    val packageName: String,
    val notificationKey: String,
    val postTime: Long,
    val flags: Int,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val conversationTitle: String?,
    val isGroupConversation: Boolean,
    val styleMessages: List<StyleMessageItem>,
) {
    data class StyleMessageItem(
        val sender: String?,
        val text: String,
        val timestamp: Long,
    )
}

data class ParsedNotificationResult(
    val messages: List<ParsedWhatsAppMessage> = emptyList(),
    val deletedSignals: List<DeletedMessageSignal> = emptyList(),
)

object WhatsAppNotificationParser {
    val SUPPORTED_PACKAGES = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
    )

    private val IGNORED_TITLES = setOf(
        "whatsapp",
        "whatsapp business",
        "whatsapp web",
    )

    private val IGNORED_PHRASES = listOf(
        "checking for new messages",
        "whatsapp web is currently active",
        "backup in progress",
        "finished backup",
        "restoring media",
        "deleting messages",
        "new messages",
        "messages from",
    )

    private val DELETED_MESSAGE_MARKERS = setOf(
        "this message was deleted",
        "you deleted this message",
        "message deleted",
        "this message was deleted.",
        "you deleted this message.",
        "यह संदेश हटाया गया था",
        "यह संदेश मिटा दिया गया",
        "आपने यह संदेश मिटा दिया",
        "diese nachricht wurde gelöscht",
        "ce message a été supprimé",
        "este mensaje fue eliminado",
        "esta mensagem foi apagada",
        "questo messaggio è stato eliminato",
        "تم حذف هذه الرسالة",
    )

    private val MESSAGE_COUNT_SUFFIX_REGEX = Regex("""\s*\(\d+\s+messages?\)$""", RegexOption.IGNORE_CASE)

    fun appLabelForPackage(packageName: String): String = when (packageName) {
        "com.whatsapp.w4b" -> "WA Business"
        else -> "WhatsApp"
    }

    fun isDeletedMessageMarker(text: String): Boolean {
        val normalized = text.trim().lowercase().trimEnd('.')
        return normalized in DELETED_MESSAGE_MARKERS ||
            "$normalized." in DELETED_MESSAGE_MARKERS ||
            normalized.contains("this message was deleted") ||
            normalized.contains("you deleted this message")
    }

    fun fromStatusBarNotification(sbn: StatusBarNotification): List<ParsedWhatsAppMessage> {
        return parseEventsFromStatusBarNotification(sbn).messages
    }

    fun parseEventsFromStatusBarNotification(sbn: StatusBarNotification): ParsedNotificationResult {
        val packageName = sbn.packageName ?: return ParsedNotificationResult()
        if (packageName !in SUPPORTED_PACKAGES) return ParsedNotificationResult()

        val notification = sbn.notification ?: return ParsedNotificationResult()
        val extras: Bundle = notification.extras ?: Bundle.EMPTY

        val messagingStyle = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(notification)

        val styleMessages = messagingStyle?.messages?.mapNotNull { msg ->
            val body = msg.text?.toString()?.trim().orEmpty()
            if (body.isEmpty()) return@mapNotNull null
            RawNotificationPayload.StyleMessageItem(
                sender = msg.person?.name?.toString()?.trim(),
                text = body,
                timestamp = msg.timestamp.takeIf { it > 0L } ?: sbn.postTime,
            )
        }.orEmpty()

        val payload = RawNotificationPayload(
            packageName = packageName,
            notificationKey = sbn.key.orEmpty(),
            postTime = sbn.postTime,
            flags = notification.flags,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            conversationTitle = messagingStyle?.conversationTitle?.toString()
                ?: extras.getCharSequence(NotificationCompat.EXTRA_CONVERSATION_TITLE)?.toString(),
            isGroupConversation = messagingStyle?.isGroupConversation
                ?: extras.getBoolean(NotificationCompat.EXTRA_IS_GROUP_CONVERSATION, false),
            styleMessages = styleMessages,
        )

        return parseEvents(payload)
    }

    fun parse(payload: RawNotificationPayload): List<ParsedWhatsAppMessage> {
        return parseEvents(payload).messages
    }

    fun parseEvents(payload: RawNotificationPayload): ParsedNotificationResult {
        if (payload.packageName !in SUPPORTED_PACKAGES) return ParsedNotificationResult()
        if ((payload.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return ParsedNotificationResult()

        val appLabel = appLabelForPackage(payload.packageName)

        if (payload.styleMessages.isNotEmpty()) {
            val rawConvTitle = payload.conversationTitle
                ?.trim()
                ?.replace(MESSAGE_COUNT_SUFFIX_REGEX, "")
                ?.takeIf { it.isNotBlank() }

            val fallbackTitle = cleanTitle(payload.title)
            val resolvedChatTitle = rawConvTitle ?: fallbackTitle
            if (resolvedChatTitle.isBlank() || isNoiseTitle(resolvedChatTitle)) {
                return ParsedNotificationResult()
            }

            val isGroup = payload.isGroupConversation ||
                (rawConvTitle != null && payload.styleMessages.any {
                    !it.sender.isNullOrBlank() && !it.sender.equals(rawConvTitle, ignoreCase = true)
                })

            val chatId = buildChatId(payload.packageName, resolvedChatTitle)
            val messages = mutableListOf<ParsedWhatsAppMessage>()
            val deletedSignals = mutableListOf<DeletedMessageSignal>()

            payload.styleMessages.forEach { item ->
                val ts = item.timestamp.takeIf { it > 0L } ?: payload.postTime
                if (isDeletedMessageMarker(item.text)) {
                    deletedSignals += DeletedMessageSignal(
                        chatId = chatId,
                        notificationKey = payload.notificationKey,
                        deletedAtTimestamp = ts,
                    )
                    return@forEach
                }
                if (isNoiseBody(item.text)) return@forEach

                val sender = item.sender?.takeIf { it.isNotBlank() } ?: resolvedChatTitle
                messages += ParsedWhatsAppMessage(
                    chatId = chatId,
                    chatTitle = resolvedChatTitle,
                    senderName = sender,
                    body = item.text,
                    timestamp = ts,
                    packageName = payload.packageName,
                    appLabel = appLabel,
                    isGroup = isGroup,
                    notificationKey = payload.notificationKey,
                    dedupKey = sha256("${payload.packageName}|$chatId|$sender|${item.text}|$ts"),
                )
            }

            return ParsedNotificationResult(
                messages = messages,
                deletedSignals = deletedSignals,
            )
        }

        val rawTitle = cleanTitle(payload.title)
        val rawBody = (payload.bigText ?: payload.text)?.trim().orEmpty()
        if (rawTitle.isBlank() || rawBody.isBlank()) return ParsedNotificationResult()
        if (isNoiseTitle(rawTitle)) return ParsedNotificationResult()

        val convTitle = payload.conversationTitle
            ?.trim()
            ?.replace(MESSAGE_COUNT_SUFFIX_REGEX, "")
            ?.takeIf { it.isNotBlank() }

        val (chatTitle, senderName, isGroup) = when {
            convTitle != null -> {
                val sender = rawTitle.removePrefix("$convTitle:").trim().ifBlank { rawTitle }
                Triple(convTitle, sender, true)
            }
            rawTitle.contains(":") -> {
                val idx = rawTitle.indexOf(':')
                val group = rawTitle.substring(0, idx).trim()
                val sender = rawTitle.substring(idx + 1).trim()
                if (group.isNotBlank() && sender.isNotBlank()) {
                    Triple(group, sender, true)
                } else {
                    Triple(rawTitle, rawTitle, payload.isGroupConversation)
                }
            }
            else -> Triple(rawTitle, rawTitle, payload.isGroupConversation)
        }

        val chatId = buildChatId(payload.packageName, chatTitle)
        val ts = payload.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()

        if (isDeletedMessageMarker(rawBody)) {
            return ParsedNotificationResult(
                deletedSignals = listOf(
                    DeletedMessageSignal(
                        chatId = chatId,
                        notificationKey = payload.notificationKey,
                        deletedAtTimestamp = ts,
                    )
                )
            )
        }

        if (isNoiseBody(rawBody)) return ParsedNotificationResult()

        return ParsedNotificationResult(
            messages = listOf(
                ParsedWhatsAppMessage(
                    chatId = chatId,
                    chatTitle = chatTitle,
                    senderName = senderName,
                    body = rawBody,
                    timestamp = ts,
                    packageName = payload.packageName,
                    appLabel = appLabel,
                    isGroup = isGroup,
                    notificationKey = payload.notificationKey,
                    dedupKey = sha256("${payload.packageName}|$chatId|$senderName|$rawBody|$ts"),
                )
            )
        )
    }

    private fun cleanTitle(title: String?): String {
        return title
            ?.trim()
            ?.replace(MESSAGE_COUNT_SUFFIX_REGEX, "")
            ?.trim()
            .orEmpty()
    }

    private fun isNoiseTitle(title: String): Boolean {
        val lower = title.lowercase()
        return lower in IGNORED_TITLES || IGNORED_PHRASES.any { lower.contains(it) }
    }

    private fun isNoiseBody(body: String): Boolean {
        val lower = body.lowercase().trim()
        if (lower.isEmpty()) return true
        return IGNORED_PHRASES.any { lower == it || lower.startsWith(it) }
    }

    private fun buildChatId(packageName: String, chatTitle: String): String {
        return "$packageName|${chatTitle.lowercase().trim()}"
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
