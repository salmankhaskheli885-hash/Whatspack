package com.example.service

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.example.data.repository.ChatRepository
import com.example.data.repository.MessageTypes
import com.example.data.repository.ParsedWhatsAppMessage
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream

data class StorageImportSummary(
    val importedCount: Int,
    val skippedDuplicates: Int,
    val summaryMessage: String,
)

data class VaultFolderInfo(
    val treeUriString: String?,
    val folderLabel: String?,
    val lastSyncTimestamp: Long?,
)

object WhatsAppStorageImporter {
    private const val PREFS_NAME = "chat_reader_vault_prefs"
    private const val KEY_TREE_URI = "connected_whatsapp_tree_uri"
    private const val KEY_FOLDER_LABEL = "connected_whatsapp_folder_label"
    private const val KEY_LAST_SYNC_TS = "last_vault_sync_ts"
    private const val MEDIA_DIR_NAME = "captured_media"
    private const val MAX_FILES_PER_SYNC = 250

    fun getVaultFolderInfo(context: Context): VaultFolderInfo {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriStr = prefs.getString(KEY_TREE_URI, null)
        val label = prefs.getString(KEY_FOLDER_LABEL, null)
        val lastSync = prefs.getLong(KEY_LAST_SYNC_TS, 0L).takeIf { it > 0L }
        return VaultFolderInfo(uriStr, label, lastSync)
    }

    fun saveConnectedTreeUri(context: Context, treeUri: Uri): String {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        val label = resolveTreeDisplayName(context, treeUri)
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TREE_URI, treeUri.toString())
            .putString(KEY_FOLDER_LABEL, label)
            .apply()
        return label
    }

    suspend fun syncConnectedFolder(
        context: Context,
        repository: ChatRepository,
    ): StorageImportSummary {
        val info = getVaultFolderInfo(context)
        val uriStr = info.treeUriString
            ?: return StorageImportSummary(0, 0, "No WhatsApp folder connected yet.")
        return importFromTreeUri(context, Uri.parse(uriStr), repository)
    }

    suspend fun importFromTreeUri(
        context: Context,
        treeUri: Uri,
        repository: ChatRepository,
    ): StorageImportSummary {
        saveConnectedTreeUri(context, treeUri)
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val collected = mutableListOf<DiscoveredDocFile>()
        traverseTree(
            context = context,
            treeUri = treeUri,
            parentDocId = rootDocId,
            relativePath = "",
            depth = 0,
            out = collected,
        )

        val sorted = collected
            .sortedByDescending { it.lastModified }
            .take(MAX_FILES_PER_SYNC)

        var imported = 0
        var skipped = 0
        val uriLower = treeUri.toString().lowercase()
        val rootIsBusiness = uriLower.contains("w4b") || uriLower.contains("business")

        for (item in sorted) {
            val mime = item.mimeType.lowercase()
            val nameLower = item.displayName.lowercase()
            if (nameLower == ".nomedia" || nameLower.startsWith("tmp") || item.size <= 0L) {
                continue
            }

            if (nameLower.endsWith(".txt") &&
                (nameLower.contains("whatsapp") || nameLower.contains("chat"))
            ) {
                val txtSummary = importSingleTextExport(context, item.uri, item.displayName, repository)
                imported += txtSummary.importedCount
                skipped += txtSummary.skippedDuplicates
                continue
            }

            val messageType = classifyFileType(mime, nameLower, item.relativePath) ?: continue
            val isBusiness = rootIsBusiness || item.relativePath.lowercase().contains("business")
            val packageName = if (isBusiness) "com.whatsapp.w4b" else "com.whatsapp"
            val appLabel = WhatsAppNotificationParser.appLabelForPackage(packageName)
            val (vaultTitle, isStatus) = resolveVaultChatTitle(item.relativePath, messageType, isBusiness)

            val dedupKey = sha256("saf|${item.relativePath}|${item.displayName}|${item.size}|${item.lastModified}")
            val copiedPath = copyUriToVault(context, item.uri, item.displayName) ?: continue
            val duration = if (messageType == MessageTypes.VOICE || messageType == MessageTypes.VIDEO) {
                extractMediaDuration(copiedPath)
            } else {
                null
            }

            val ts = item.lastModified.takeIf { it > 0L } ?: System.currentTimeMillis()
            val chatId = "$packageName|vault|${vaultTitle.lowercase().replace(' ', '_')}"
            val parsed = ParsedWhatsAppMessage(
                chatId = chatId,
                chatTitle = vaultTitle,
                senderName = if (isStatus) "WhatsApp Status" else "Phone Storage",
                body = item.displayName,
                timestamp = ts,
                packageName = packageName,
                appLabel = appLabel,
                isGroup = false,
                notificationKey = "vault|$dedupKey",
                dedupKey = dedupKey,
                messageType = messageType,
                mediaDuration = duration,
                isViewOnce = false,
                mediaFilePath = copiedPath,
            )

            val saved = repository.saveIncomingMessage(parsed, checkRecentDuplicate = false)
            if (saved) {
                imported++
            } else {
                runCatching { File(copiedPath).delete() }
                skipped++
            }
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_SYNC_TS, System.currentTimeMillis())
            .apply()

        val message = when {
            imported > 0 -> "Saved $imported WhatsApp file(s) to ChatReader local vault."
            skipped > 0 -> "All $skipped file(s) are already saved in ChatReader."
            else -> "No supported WhatsApp photos, videos, voice notes, or exports found in this folder."
        }
        return StorageImportSummary(imported, skipped, message)
    }

    suspend fun importDocumentUris(
        context: Context,
        uris: List<Uri>,
        repository: ChatRepository,
        sharedText: String? = null,
    ): StorageImportSummary {
        var imported = 0
        var skipped = 0

        if (!sharedText.isNullOrBlank() && uris.isEmpty()) {
            val parsedList = WhatsAppExportParser.parseExportText(
                rawText = sharedText,
                chatTitle = "Shared WhatsApp Chat",
            )
            if (parsedList.isNotEmpty()) {
                parsedList.forEach { msg ->
                    if (repository.saveIncomingMessage(msg, checkRecentDuplicate = false)) imported++ else skipped++
                }
            } else {
                val ts = System.currentTimeMillis()
                val msg = ParsedWhatsAppMessage(
                    chatId = "com.whatsapp|shared_notes",
                    chatTitle = "Shared WhatsApp Notes",
                    senderName = "Shared from WhatsApp",
                    body = sharedText.trim(),
                    timestamp = ts,
                    packageName = "com.whatsapp",
                    appLabel = "WhatsApp",
                    isGroup = false,
                    notificationKey = "shared|$ts",
                    dedupKey = sha256("shared_text|${sharedText.trim()}|$ts"),
                )
                if (repository.saveIncomingMessage(msg, checkRecentDuplicate = false)) imported++ else skipped++
            }
        }

        for (uri in uris) {
            val name = queryDisplayName(context, uri) ?: "whatsapp_item_${System.currentTimeMillis()}"
            val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
            val nameLower = name.lowercase()

            when {
                nameLower.endsWith(".zip") || mime.contains("zip") -> {
                    val zipSummary = importZipExport(context, uri, name, repository)
                    imported += zipSummary.importedCount
                    skipped += zipSummary.skippedDuplicates
                }
                nameLower.endsWith(".txt") || mime.startsWith("text/") -> {
                    val txtSummary = importSingleTextExport(context, uri, name, repository)
                    imported += txtSummary.importedCount
                    skipped += txtSummary.skippedDuplicates
                }
                else -> {
                    val type = classifyFileType(mime, nameLower, "") ?: MessageTypes.DOCUMENT
                    val copiedPath = copyUriToVault(context, uri, name) ?: continue
                    val duration = if (type == MessageTypes.VOICE || type == MessageTypes.VIDEO) {
                        extractMediaDuration(copiedPath)
                    } else {
                        null
                    }
                    val ts = System.currentTimeMillis()
                    val (title, chatId) = when (type) {
                        MessageTypes.VOICE -> "Saved Voice Notes" to "com.whatsapp|vault|saved_voice_notes"
                        MessageTypes.VIDEO -> "Saved Videos" to "com.whatsapp|vault|saved_videos"
                        MessageTypes.IMAGE -> "Saved Photos" to "com.whatsapp|vault|saved_photos"
                        else -> "Saved Documents" to "com.whatsapp|vault|saved_documents"
                    }
                    val parsed = ParsedWhatsAppMessage(
                        chatId = chatId,
                        chatTitle = title,
                        senderName = "Imported File",
                        body = name,
                        timestamp = ts,
                        packageName = "com.whatsapp",
                        appLabel = "WhatsApp",
                        isGroup = false,
                        notificationKey = "import|$name|$ts",
                        dedupKey = sha256("doc|$uri|$name|${File(copiedPath).length()}"),
                        messageType = type,
                        mediaDuration = duration,
                        mediaFilePath = copiedPath,
                    )
                    if (repository.saveIncomingMessage(parsed, checkRecentDuplicate = false)) {
                        imported++
                    } else {
                        runCatching { File(copiedPath).delete() }
                        skipped++
                    }
                }
            }
        }

        val summaryText = when {
            imported > 0 -> "Saved $imported item(s) permanently inside ChatReader."
            skipped > 0 -> "Items already saved in ChatReader ($skipped duplicate skipped)."
            else -> "Could not read any WhatsApp chat or media from the selected file."
        }
        return StorageImportSummary(imported, skipped, summaryText)
    }

    private suspend fun importSingleTextExport(
        context: Context,
        uri: Uri,
        fileName: String,
        repository: ChatRepository,
    ): StorageImportSummary {
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull().orEmpty()
        if (text.isBlank()) return StorageImportSummary(0, 0, "Empty chat file.")

        val title = WhatsAppExportParser.inferChatTitleFromFileName(fileName)
        val parsed = WhatsAppExportParser.parseExportText(rawText = text, chatTitle = title)
        var imported = 0
        var skipped = 0
        parsed.forEach { msg ->
            if (repository.saveIncomingMessage(msg, checkRecentDuplicate = false)) imported++ else skipped++
        }
        return StorageImportSummary(imported, skipped, "Imported $imported messages.")
    }

    private suspend fun importZipExport(
        context: Context,
        uri: Uri,
        zipName: String,
        repository: ChatRepository,
    ): StorageImportSummary {
        val mediaDir = ensureMediaDir(context)
        val extractedMedia = mutableMapOf<String, String>()
        var chatText: String? = null
        var chatFileName: String = zipName

        runCatching {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val entryName = File(entry.name).name
                            val lower = entryName.lowercase()
                            if (lower.endsWith(".txt") && (chatText == null || lower.contains("chat"))) {
                                chatText = zis.bufferedReader().readText()
                                chatFileName = entryName
                            } else if (classifyFileType("", lower, "") != null) {
                                val outFile = File(mediaDir, "zip_${UUID.randomUUID()}_${sanitizeName(entryName)}")
                                FileOutputStream(outFile).use { fos ->
                                    zis.copyTo(fos)
                                }
                                extractedMedia[lower] = outFile.absolutePath
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        }

        val text = chatText ?: return StorageImportSummary(0, 0, "No chat text found in zip.")
        val chatTitle = WhatsAppExportParser.inferChatTitleFromFileName(
            if (chatFileName.equals("_chat.txt", ignoreCase = true)) zipName else chatFileName
        )
        val parsed = WhatsAppExportParser.parseExportText(
            rawText = text,
            chatTitle = chatTitle,
            attachedMediaByName = extractedMedia,
        )
        var imported = 0
        var skipped = 0
        parsed.forEach { msg ->
            if (repository.saveIncomingMessage(msg, checkRecentDuplicate = false)) imported++ else skipped++
        }
        return StorageImportSummary(imported, skipped, "Imported $imported messages from $zipName.")
    }

    private data class DiscoveredDocFile(
        val uri: Uri,
        val displayName: String,
        val mimeType: String,
        val lastModified: Long,
        val size: Long,
        val relativePath: String,
    )

    private fun traverseTree(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        relativePath: String,
        depth: Int,
        out: MutableList<DiscoveredDocFile>,
    ) {
        if (depth > 5 || out.size >= 1000) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                while (cursor.moveToNext() && out.size < 1000) {
                    val docId = cursor.getString(idIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: continue
                    val mime = cursor.getString(mimeIdx) ?: ""
                    val nextRel = if (relativePath.isBlank()) name else "$relativePath/$name"

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val lower = name.lowercase()
                        if (lower == "sent" || lower == "private" || lower == "backups" || lower == "databases") {
                            if (lower == "backups" || lower == "databases") continue
                        }
                        traverseTree(context, treeUri, docId, nextRel, depth + 1, out)
                    } else {
                        val mod = if (modIdx >= 0) cursor.getLong(modIdx) else 0L
                        val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                        out += DiscoveredDocFile(docUri, name, mime, mod, size, nextRel)
                    }
                }
            }
        }
    }

    private fun resolveVaultChatTitle(
        relativePath: String,
        messageType: String,
        isBusiness: Boolean,
    ): Pair<String, Boolean> {
        val lower = relativePath.lowercase()
        val prefix = if (isBusiness) "WA Business" else "WhatsApp"
        if (lower.contains(".statuses") || lower.contains("statuses")) {
            return "$prefix Statuses" to true
        }
        val title = when (messageType) {
            MessageTypes.VOICE -> "$prefix Voice Notes"
            MessageTypes.IMAGE -> "$prefix Images Vault"
            MessageTypes.VIDEO -> "$prefix Videos Vault"
            MessageTypes.DOCUMENT -> "$prefix Documents Vault"
            else -> "$prefix Media Vault"
        }
        return title to false
    }

    private fun classifyFileType(
        mime: String,
        nameLower: String,
        relPath: String,
    ): String? {
        val pathLower = relPath.lowercase()
        return when {
            mime.startsWith("audio/") ||
                pathLower.contains("voice notes") ||
                nameLower.endsWith(".opus") ||
                nameLower.endsWith(".ogg") ||
                nameLower.endsWith(".m4a") ||
                nameLower.endsWith(".mp3") ||
                nameLower.endsWith(".aac") -> MessageTypes.VOICE

            mime.startsWith("image/") ||
                nameLower.endsWith(".jpg") ||
                nameLower.endsWith(".jpeg") ||
                nameLower.endsWith(".png") ||
                nameLower.endsWith(".webp") ||
                nameLower.endsWith(".gif") -> MessageTypes.IMAGE

            mime.startsWith("video/") ||
                nameLower.endsWith(".mp4") ||
                nameLower.endsWith(".mkv") ||
                nameLower.endsWith(".3gp") -> MessageTypes.VIDEO

            mime == "application/pdf" ||
                mime.startsWith("application/vnd") ||
                mime.startsWith("application/msword") ||
                nameLower.endsWith(".pdf") ||
                nameLower.endsWith(".doc") ||
                nameLower.endsWith(".docx") ||
                nameLower.endsWith(".xls") ||
                nameLower.endsWith(".xlsx") -> MessageTypes.DOCUMENT

            else -> null
        }
    }

    private fun copyUriToVault(context: Context, uri: Uri, displayName: String): String? {
        val dir = ensureMediaDir(context)
        val outFile = File(dir, "vault_${UUID.randomUUID()}_${sanitizeName(displayName)}")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null
            if (outFile.length() > 0L) outFile.absolutePath else {
                outFile.delete()
                null
            }
        }.getOrNull()
    }

    private fun extractMediaDuration(filePath: String): String? {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            retriever.release()
            if (ms != null && ms > 0L) {
                val totalSec = ms / 1000L
                val min = totalSec / 60L
                val sec = totalSec % 60L
                String.format(Locale.US, "%d:%02d", min, sec)
            } else {
                null
            }
        }.getOrNull()
    }

    private fun resolveTreeDisplayName(context: Context, treeUri: Uri): String {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        if (!docId.isNullOrBlank()) {
            val last = docId.substringAfterLast(':').substringAfterLast('/')
            if (last.isNotBlank()) return last
        }
        return queryDisplayName(context, treeUri) ?: "Connected WhatsApp Folder"
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull()
    }

    private fun ensureMediaDir(context: Context): File {
        return File(context.filesDir, MEDIA_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    private fun sanitizeName(name: String): String {
        return name.replace(Regex("""[^A-Za-z0-9._-]"""), "_").take(64)
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
