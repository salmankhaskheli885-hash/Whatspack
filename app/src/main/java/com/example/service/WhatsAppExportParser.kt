package com.example.service

import com.example.data.repository.MessageTypes
import com.example.data.repository.ParsedWhatsAppMessage
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale

object WhatsAppExportParser {

    private val BRACKETED_LINE_REGEX = Regex(
        """^\[(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}),\s+(\d{1,2}:\d{2}(?::\d{2})?(?:[\s\u202F\u00A0]*[APMapm]{2})?)]\s+(.+)$"""
    )

    private val DASH_LINE_REGEX = Regex(
        """^(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}),\s+(\d{1,2}:\d{2}(?::\d{2})?(?:[\s\u202F\u00A0]*[APMapm]{2})?)\s+-\s+(.+)$"""
    )

    private val ATTACHED_BRACKET_REGEX = Regex("""<attached:\s*([^>]+)>""", RegexOption.IGNORE_CASE)
    private val ATTACHED_ANDROID_REGEX = Regex("""([^\s/\\]+\.[A-Za-z0-9]{2,5})\s+\(file attached\)""", RegexOption.IGNORE_CASE)

    private val DATE_FORMATS = listOf(
        "dd/MM/yyyy HH:mm:ss",
        "dd/MM/yy HH:mm:ss",
        "dd/MM/yyyy HH:mm",
        "dd/MM/yy HH:mm",
        "dd/MM/yyyy hh:mm:ss a",
        "dd/MM/yy hh:mm:ss a",
        "dd/MM/yyyy hh:mm a",
        "dd/MM/yy hh:mm a",
        "MM/dd/yyyy hh:mm:ss a",
        "MM/dd/yy hh:mm:ss a",
        "MM/dd/yyyy hh:mm a",
        "MM/dd/yy hh:mm a",
        "dd-MM-yyyy HH:mm:ss",
        "dd-MM-yy HH:mm:ss",
        "dd-MM-yyyy HH:mm",
        "dd-MM-yy HH:mm",
    )

    private data class DraftEntry(
        val senderName: String,
        val rawBody: String,
        val timestamp: Long,
    )

    fun inferChatTitleFromFileName(fileName: String?): String {
        val base = fileName
            ?.substringBeforeLast('.')
            ?.trim()
            .orEmpty()
        if (base.isBlank() || base.equals("_chat", ignoreCase = true)) {
            return "Imported WhatsApp Chat"
        }
        val cleaned = base
            .replace(Regex("""^WhatsApp Chat with\s+""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""^WhatsApp Chat -\s+""", RegexOption.IGNORE_CASE), "")
            .trim()
        return cleaned.ifBlank { "Imported WhatsApp Chat" }
    }

    fun parseExportText(
        rawText: String,
        chatTitle: String,
        packageName: String = "com.whatsapp",
        attachedMediaByName: Map<String, String> = emptyMap(),
    ): List<ParsedWhatsAppMessage> {
        val drafts = mutableListOf<DraftEntry>()
        val normalizedText = rawText.replace("\u200E", "").replace("\u200F", "")

        normalizedText.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd('\r')
            val match = BRACKETED_LINE_REGEX.matchEntire(line) ?: DASH_LINE_REGEX.matchEntire(line)
            if (match != null) {
                val datePart = match.groupValues[1]
                val timePart = match.groupValues[2]
                    .replace('\u202F', ' ')
                    .replace('\u00A0', ' ')
                    .trim()
                val rest = match.groupValues[3]

                val colonIdx = rest.indexOf(':')
                if (colonIdx > 0) {
                    val sender = rest.substring(0, colonIdx).trim()
                    val body = rest.substring(colonIdx + 1).trim()
                    if (sender.isNotBlank() && body.isNotBlank() && !isSystemNotice(body)) {
                        val ts = parseTimestamp(datePart, timePart)
                        drafts += DraftEntry(sender, body, ts)
                    }
                }
            } else if (drafts.isNotEmpty() && line.isNotBlank()) {
                val last = drafts.removeAt(drafts.lastIndex)
                drafts += last.copy(rawBody = "${last.rawBody}\n$line")
            }
        }

        if (drafts.isEmpty()) return emptyList()

        val distinctSenders = drafts.map { it.senderName.lowercase() }.toSet()
        val isGroup = distinctSenders.size > 2 ||
            (distinctSenders.size == 2 && distinctSenders.none { it == "you" || it == chatTitle.lowercase() })

        val appLabel = WhatsAppNotificationParser.appLabelForPackage(packageName)
        val chatId = "$packageName|${chatTitle.lowercase().trim()}|export"

        return drafts.mapIndexed { idx, entry ->
            val attachedFileName = extractAttachmentFileName(entry.rawBody)
            val savedPath = attachedFileName?.let { name ->
                attachedMediaByName[name.lowercase()]
                    ?: attachedMediaByName.entries.firstOrNull { (k, _) -> k.endsWith(name.lowercase()) }?.value
            }

            val isDeleted = WhatsAppNotificationParser.isDeletedMessageMarker(entry.rawBody)
            val (msgType, isViewOnce, cleanBody) = classifyExportBody(entry.rawBody, attachedFileName)
            val stableTs = entry.timestamp + idx

            ParsedWhatsAppMessage(
                chatId = chatId,
                chatTitle = chatTitle,
                senderName = entry.senderName,
                body = if (isDeleted) "Deleted message recorded in export" else cleanBody,
                timestamp = stableTs,
                packageName = packageName,
                appLabel = appLabel,
                isGroup = isGroup,
                notificationKey = "export|$chatId|$idx",
                dedupKey = sha256("export|$chatId|${entry.senderName}|$cleanBody|$stableTs|$idx"),
                messageType = msgType,
                mediaDuration = null,
                isViewOnce = isViewOnce,
                mediaFilePath = savedPath,
            )
        }
    }

    private fun extractAttachmentFileName(body: String): String? {
        ATTACHED_BRACKET_REGEX.find(body)?.groupValues?.getOrNull(1)?.trim()?.let { return it }
        ATTACHED_ANDROID_REGEX.find(body)?.groupValues?.getOrNull(1)?.trim()?.let { return it }
        return null
    }

    private fun classifyExportBody(
        body: String,
        attachedFileName: String?,
    ): Triple<String, Boolean, String> {
        val lower = body.lowercase()
        val fileLower = attachedFileName?.lowercase().orEmpty()
        val isViewOnce = lower.contains("view once") || body.contains("1️⃣")

        return when {
            lower.contains("audio omitted") ||
                fileLower.endsWith(".opus") ||
                fileLower.endsWith(".ogg") ||
                fileLower.endsWith(".m4a") ||
                fileLower.endsWith(".mp3") ||
                fileLower.startsWith("ptt-") ->
                Triple(MessageTypes.VOICE, isViewOnce, attachedFileName ?: "Voice message (exported)")

            lower.contains("image omitted") ||
                lower.contains("sticker omitted") ||
                lower.contains("gif omitted") ||
                fileLower.endsWith(".jpg") ||
                fileLower.endsWith(".jpeg") ||
                fileLower.endsWith(".png") ||
                fileLower.endsWith(".webp") ->
                Triple(MessageTypes.IMAGE, isViewOnce, attachedFileName ?: body)

            lower.contains("video omitted") ||
                fileLower.endsWith(".mp4") ||
                fileLower.endsWith(".mkv") ||
                fileLower.endsWith(".3gp") ->
                Triple(MessageTypes.VIDEO, isViewOnce, attachedFileName ?: body)

            lower.contains("document omitted") ||
                fileLower.endsWith(".pdf") ||
                fileLower.endsWith(".doc") ||
                fileLower.endsWith(".docx") ||
                fileLower.endsWith(".xlsx") ->
                Triple(MessageTypes.DOCUMENT, false, attachedFileName ?: body)

            else -> Triple(MessageTypes.TEXT, isViewOnce, body)
        }
    }

    private fun isSystemNotice(body: String): Boolean {
        val lower = body.lowercase()
        return lower.contains("messages and calls are end-to-end encrypted")
    }

    private fun parseTimestamp(datePart: String, timePart: String): Long {
        val candidate = "$datePart ${timePart.uppercase(Locale.US)}"
        for (pattern in DATE_FORMATS) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply { isLenient = true }.parse(candidate)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return System.currentTimeMillis()
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
