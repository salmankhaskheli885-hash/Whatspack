package com.example.service

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.data.repository.DeletedMessageSignal
import com.example.data.repository.MessageTypes
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
    val textLines: List<String> = emptyList(),
) {
    data class StyleMessageItem(
        val sender: String?,
        val text: String,
        val timestamp: Long,
        val dataMimeType: String? = null,
        val dataUriString: String? = null,
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
        "wa business",
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

    private val SUMMARY_COUNT_LINE_REGEX = Regex(
        """^\d+\s+(new\s+)?messages?(\s+from\s+\d+\s+chats?)?$""",
        RegexOption.IGNORE_CASE,
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
    private val DURATION_REGEX = Regex("""\b(\d{1,2}:\d{2})\b""")

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
            val rawText = msg.text?.toString()?.trim().orEmpty()
            val mime = msg.dataMimeType?.trim()
            if (rawText.isEmpty() && mime.isNullOrBlank()) return@mapNotNull null

            val resolvedText = if (rawText.isNotEmpty()) {
                rawText
            } else {
                fallbackTextForMimeType(mime)
            }

            RawNotificationPayload.StyleMessageItem(
                sender = msg.person?.name?.toString()?.trim(),
                text = resolvedText,
                timestamp = msg.timestamp.takeIf { it > 0L } ?: sbn.postTime,
                dataMimeType = mime,
                dataUriString = msg.dataUri?.toString(),
            )
        }.orEmpty()

        @Suppress("DEPRECATION")
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotEmpty) }
            .orEmpty()

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
            textLines = textLines,
        )

        return parseEvents(payload)
    }

    fun parse(payload: RawNotificationPayload): List<ParsedWhatsAppMessage> {
        return parseEvents(payload).messages
    }

    fun parseEvents(payload: RawNotificationPayload): ParsedNotificationResult {
        if (payload.packageName !in SUPPORTED_PACKAGES) return ParsedNotificationResult()

        val isSummary = (payload.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val appLabel = appLabelForPackage(payload.packageName)

        // 1. MessagingStyle messages (standard in WhatsApp & WhatsApp Business)
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
                val (msgType, duration, formattedBody, isViewOnce) = classifyMessage(item.text, item.dataMimeType)
                messages += ParsedWhatsAppMessage(
                    chatId = chatId,
                    chatTitle = resolvedChatTitle,
                    senderName = sender,
                    body = formattedBody,
                    timestamp = ts,
                    packageName = payload.packageName,
                    appLabel = appLabel,
                    isGroup = isGroup,
                    notificationKey = payload.notificationKey,
                    dedupKey = sha256("${payload.packageName}|$chatId|$sender|$formattedBody|$ts"),
                    messageType = msgType,
                    mediaDuration = duration,
                    isViewOnce = isViewOnce,
                    mediaUriString = item.dataUriString,
                )
            }

            return ParsedNotificationResult(
                messages = messages,
                deletedSignals = deletedSignals,
            )
        }

        // 2. InboxStyle EXTRA_TEXT_LINES (often used by WhatsApp Business / bundled notifications)
        if (payload.textLines.isNotEmpty()) {
            val parsedFromLines = parseTextLines(payload, appLabel)
            if (parsedFromLines.messages.isNotEmpty() || parsedFromLines.deletedSignals.isNotEmpty()) {
                return parsedFromLines
            }
        }

        if (isSummary) return ParsedNotificationResult()

        // 3. Standard EXTRA_TITLE + EXTRA_BIG_TEXT / EXTRA_TEXT fallback
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

        val (msgType, duration, formattedBody, isViewOnce) = classifyMessage(rawBody, null)

        return ParsedNotificationResult(
            messages = listOf(
                ParsedWhatsAppMessage(
                    chatId = chatId,
                    chatTitle = chatTitle,
                    senderName = senderName,
                    body = formattedBody,
                    timestamp = ts,
                    packageName = payload.packageName,
                    appLabel = appLabel,
                    isGroup = isGroup,
                    notificationKey = payload.notificationKey,
                    dedupKey = sha256("${payload.packageName}|$chatId|$senderName|$formattedBody|$ts"),
                    messageType = msgType,
                    mediaDuration = duration,
                    isViewOnce = isViewOnce,
                )
            )
        )
    }

    private fun parseTextLines(
        payload: RawNotificationPayload,
        appLabel: String,
    ): ParsedNotificationResult {
        val messages = mutableListOf<ParsedWhatsAppMessage>()
        val deletedSignals = mutableListOf<DeletedMessageSignal>()
        val fallbackChat = cleanTitle(payload.conversationTitle ?: payload.title)
        val ts = payload.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()

        payload.textLines.forEachIndexed { idx, line ->
            if (isNoiseBody(line)) return@forEachIndexed

            val colonIdx = line.indexOf(':')
            val (chatTitle, senderName, bodyText, isGroup) = if (colonIdx > 0) {
                val prefix = line.substring(0, colonIdx).trim()
                val rest = line.substring(colonIdx + 1).trim()
                if (rest.isEmpty()) return@forEachIndexed

                val atIdx = prefix.indexOf('@')
                if (atIdx > 0) {
                    val sender = prefix.substring(0, atIdx).trim()
                    val group = prefix.substring(atIdx + 1).trim()
                    LineParts(group.ifBlank { prefix }, sender.ifBlank { prefix }, rest, true)
                } else {
                    val resolvedChat = if (fallbackChat.isNotBlank() && !isNoiseTitle(fallbackChat)) {
                        fallbackChat
                    } else {
                        prefix
                    }
                    val groupChat = fallbackChat.isNotBlank() && !isNoiseTitle(fallbackChat) &&
                        !fallbackChat.equals(prefix, ignoreCase = true)
                    LineParts(resolvedChat, prefix, rest, groupChat)
                }
            } else {
                if (fallbackChat.isBlank() || isNoiseTitle(fallbackChat)) return@forEachIndexed
                LineParts(fallbackChat, fallbackChat, line, payload.isGroupConversation)
            }

            if (isNoiseTitle(chatTitle)) return@forEachIndexed
            val chatId = buildChatId(payload.packageName, chatTitle)

            if (isDeletedMessageMarker(bodyText)) {
                deletedSignals += DeletedMessageSignal(
                    chatId = chatId,
                    notificationKey = payload.notificationKey,
                    deletedAtTimestamp = ts,
                )
                return@forEachIndexed
            }

            val (msgType, duration, formattedBody, isViewOnce) = classifyMessage(bodyText, null)
            messages += ParsedWhatsAppMessage(
                chatId = chatId,
                chatTitle = chatTitle,
                senderName = senderName,
                body = formattedBody,
                timestamp = ts + idx,
                packageName = payload.packageName,
                appLabel = appLabel,
                isGroup = isGroup,
                notificationKey = payload.notificationKey,
                dedupKey = sha256("${payload.packageName}|$chatId|$senderName|$formattedBody|${ts + idx}"),
                messageType = msgType,
                mediaDuration = duration,
                isViewOnce = isViewOnce,
            )
        }

        return ParsedNotificationResult(messages = messages, deletedSignals = deletedSignals)
    }

    private data class LineParts(
        val chatTitle: String,
        val senderName: String,
        val body: String,
        val isGroup: Boolean,
    )

    private fun fallbackTextForMimeType(mimeType: String?): String {
        val lower = mimeType?.lowercase().orEmpty()
        return when {
            lower.startsWith("audio/") -> "Voice message"
            lower.startsWith("image/") -> "Photo"
            lower.startsWith("video/") -> "Video"
            lower.startsWith("application/") -> "Document"
            else -> "Media message"
        }
    }

    private data class MessageClassification(
        val type: String,
        val duration: String?,
        val formattedBody: String,
        val isViewOnce: Boolean,
    )

    private fun classifyMessage(
        rawBody: String,
        mimeType: String?,
    ): MessageClassification {
        val lower = rawBody.lowercase().trim()
        val mime = mimeType?.lowercase().orEmpty()
        val duration = DURATION_REGEX.find(rawBody)?.groupValues?.getOrNull(1)
        val isViewOnce = lower.contains("view once") ||
            rawBody.contains("1️⃣") ||
            (lower.contains("once") && (lower.contains("photo") || lower.contains("video")))

        if (lower.contains("missed voice call") || lower.contains("missed video call")) {
            return MessageClassification(MessageTypes.CALL, null, rawBody, false)
        }

        val isVoice = mime.startsWith("audio/") ||
            rawBody.contains("\uD83C\uDFA4") || // 🎤
            rawBody.contains("\uD83C\uDF99") || // 🎙
            lower.contains("voice message") ||
            lower.contains("voice note") ||
            lower.startsWith("audio") ||
            lower.contains("وائس میسج") ||
            lower.contains("ध्वनि संदेश") ||
            lower.contains("आवाज़ संदेश") ||
            (duration != null && (lower == duration || lower == "($duration)" || lower.contains("ptt")))

        if (isVoice) {
            val cleanBody = if (duration != null && !lower.contains("voice")) {
                "Voice message ($duration)"
            } else if (lower.isBlank()) {
                "Voice message"
            } else {
                rawBody
            }
            return MessageClassification(MessageTypes.VOICE, duration, cleanBody, isViewOnce)
        }

        val isImage = mime.startsWith("image/") ||
            rawBody.contains("\uD83D\uDCF7") || // 📷
            lower == "photo" ||
            lower.startsWith("photo ") ||
            lower.endsWith("photo") ||
            lower.contains("view once photo") ||
            (isViewOnce && (lower.contains("photo") || lower.contains("image"))) ||
            lower == "image" ||
            lower == "gif" ||
            lower == "sticker"
        if (isImage) {
            return MessageClassification(MessageTypes.IMAGE, null, rawBody, isViewOnce)
        }

        val isVideo = mime.startsWith("video/") ||
            rawBody.contains("\uD83C\uDFA5") || // 🎥
            rawBody.contains("\uD83D\uDCF9") || // 📹
            lower == "video" ||
            lower.startsWith("video (") ||
            lower.contains("video (") ||
            lower.endsWith("video") ||
            lower.contains("view once video") ||
            (isViewOnce && lower.contains("video"))
        if (isVideo) {
            return MessageClassification(MessageTypes.VIDEO, duration, rawBody, isViewOnce)
        }

        val isDoc = mime.startsWith("application/") ||
            rawBody.contains("\uD83D\uDCC4") || // 📄
            lower.startsWith("document")
        if (isDoc) {
            return MessageClassification(MessageTypes.DOCUMENT, null, rawBody, false)
        }

        return MessageClassification(MessageTypes.TEXT, null, rawBody, false)
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
        if (SUMMARY_COUNT_LINE_REGEX.matches(lower)) return true
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
