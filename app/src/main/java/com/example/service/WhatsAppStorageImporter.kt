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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

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

data class CapturedSafMedia(
    val filePath: String,
    val duration: String?,
    val fileName: String,
)

object WhatsAppStorageImporter {
    private const val PREFS_NAME = "chat_reader_vault_prefs"
    private const val KEY_TREE_URI = "connected_whatsapp_tree_uri"
    private const val KEY_FOLDER_LABEL = "connected_whatsapp_folder_label"
    private const val KEY_LAST_SYNC_TS = "last_vault_sync_ts"
    private const val KEY_CAPTURED_SAF_SIGNATURES = "captured_saf_signatures"
    private const val MEDIA_DIR_NAME = "captured_media"
    private const val MAX_FILES_PER_CATEGORY = 90
    private const val MAX_TOTAL_FILES_PER_SYNC = 450
    private val safCaptureMutex = Mutex()

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
            maxFilesPerDirectory = 120,
        )

        val sorted = collected
            .sortedByDescending { it.lastModified }
            .groupBy { item ->
                val cat = classifyFileType(
                    item.mimeType.lowercase(),
                    item.displayName.lowercase(),
                    item.relativePath,
                ) ?: "other"
                val biz = if (item.relativePath.lowercase().let { it.contains("business") || it.contains("w4b") }) {
                    "w4b"
                } else {
                    "wa"
                }
                "$biz|$cat"
            }
            .values
            .flatMap { group -> group.take(MAX_FILES_PER_CATEGORY) }
            .sortedByDescending { it.lastModified }
            .take(MAX_TOTAL_FILES_PER_SYNC)

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
            val relLower = item.relativePath.lowercase()
            val isBusiness = rootIsBusiness || relLower.contains("business") || relLower.contains("w4b")
            val packageName = if (isBusiness) "com.whatsapp.w4b" else "com.whatsapp"
            val appLabel = WhatsAppNotificationParser.appLabelForPackage(packageName)
            val (vaultTitle, isStatus) = resolveVaultChatTitle(item.relativePath, messageType, isBusiness)

            val dedupKey = sha256("saf|${item.relativePath}|${item.displayName}|${item.size}|${item.lastModified}")
            if (isSafSignatureCaptured(context, dedupKey)) {
                skipped++
                continue
            }
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
                markSafSignatureCaptured(context, dedupKey)
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

    suspend fun captureLatestMediaForNotification(
        context: Context,
        messageType: String,
        packageName: String,
        timestamp: Long,
    ): CapturedSafMedia? = safCaptureMutex.withLock {
        val treeUriStr = getVaultFolderInfo(context).treeUriString ?: return@withLock null
        val treeUri = runCatching { Uri.parse(treeUriStr) }.getOrNull() ?: return@withLock null
        val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return@withLock null

        val collected = mutableListOf<DiscoveredDocFile>()
        traverseTree(
            context = context,
            treeUri = treeUri,
            parentDocId = rootDocId,
            relativePath = "",
            depth = 0,
            out = collected,
            skipSentFolder = true,
            targetMessageType = messageType,
            maxFilesPerDirectory = 120,
        )

        val windowStart = timestamp - 30_000L
        val windowEnd = timestamp + 90_000L
        val preferBusiness = packageName == "com.whatsapp.w4b"

        val candidates = collected.filter { item ->
            val nameLower = item.displayName.lowercase()
            if (item.size <= 0L || nameLower == ".nomedia" || nameLower.startsWith("tmp")) {
                return@filter false
            }
            if (item.lastModified !in windowStart..windowEnd) {
                return@filter false
            }
            val classified = classifyFileType(item.mimeType.lowercase(), nameLower, item.relativePath)
            if (classified != messageType) return@filter false
            val sig = sha256("saf|${item.relativePath}|${item.displayName}|${item.size}|${item.lastModified}")
            !isSafSignatureCaptured(context, sig)
        }

        val bestMatch = candidates.minByOrNull { item ->
            val relLower = item.relativePath.lowercase()
            val isBizPath = relLower.contains("business") || relLower.contains("w4b")
            val pkgPenalty = if (preferBusiness == isBizPath) 0L else 100_000L
            pkgPenalty + abs(item.lastModified - timestamp)
        } ?: return@withLock null

        val sig = sha256("saf|${bestMatch.relativePath}|${bestMatch.displayName}|${bestMatch.size}|${bestMatch.lastModified}")
        val copiedPath = copyUriToVault(context, bestMatch.uri, bestMatch.displayName) ?: return@withLock null
        markSafSignatureCaptured(context, sig)
        val duration = if (messageType == MessageTypes.VOICE || messageType == MessageTypes.VIDEO) {
            extractMediaDuration(copiedPath)
        } else {
            null
        }
        CapturedSafMedia(
            filePath = copiedPath,
            duration = duration,
            fileName = bestMatch.displayName,
        )
    }

    fun clearCapturedSafTracking(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_CAPTURED_SAF_SIGNATURES)
            .apply()
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
                nameLower.endsWith(".json") || mime.contains("json") -> {
                    val backupSummary = ChatBackupManager.restoreBackupFromUri(context, uri, repository)
                    imported += backupSummary.importedCount
                    skipped += backupSummary.skippedDuplicates
                }
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

    private data class DiscoveredSubDir(
        val docId: String,
        val name: String,
        val lastModified: Long,
        val relativePath: String,
    )

    private fun traverseTree(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        relativePath: String,
        depth: Int,
        out: MutableList<DiscoveredDocFile>,
        skipSentFolder: Boolean = false,
        targetMessageType: String? = null,
        maxFilesPerDirectory: Int = 120,
    ) {
        if (depth > 7 || out.size >= 4000) return
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
        val subDirs = mutableListOf<DiscoveredSubDir>()
        val filesHere = mutableListOf<DiscoveredDocFile>()

        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idIdx) ?: continue
                    val name = cursor.getString(nameIdx) ?: continue
                    val mime = cursor.getString(mimeIdx) ?: ""
                    val mod = if (modIdx >= 0) cursor.getLong(modIdx) else 0L
                    val nextRel = if (relativePath.isBlank()) name else "$relativePath/$name"

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val lower = name.lowercase()
                        val isUnrelatedAppPkg = depth == 0 &&
                            (lower.startsWith("com.") || lower.startsWith("org.") ||
                                lower.startsWith("net.") || lower.startsWith("io.")) &&
                            !lower.contains("whatsapp")
                        if (isUnrelatedAppPkg ||
                            lower == "backups" ||
                            lower == "databases" ||
                            lower == ".thumbs" ||
                            lower == ".links" ||
                            lower.contains("stickers") ||
                            (skipSentFolder && lower == "sent")
                        ) {
                            continue
                        }
                        if (targetMessageType != null && !shouldEnterFolderForType(lower, targetMessageType)) {
                            continue
                        }
                        subDirs += DiscoveredSubDir(docId, name, mod, nextRel)
                    } else {
                        val size = if (sizeIdx >= 0) cursor.getLong(sizeIdx) else 0L
                        val nameLower = name.lowercase()
                        if (size > 0L && nameLower != ".nomedia" && !nameLower.startsWith("tmp")) {
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                            filesHere += DiscoveredDocFile(docUri, name, mime, mod, size, nextRel)
                        }
                    }
                }
            }
        }

        if (filesHere.isNotEmpty()) {
            out += filesHere
                .sortedByDescending { it.lastModified }
                .take(maxFilesPerDirectory)
        }

        val orderedDirs = subDirs.sortedWith(
            compareByDescending<DiscoveredSubDir> { it.lastModified }
                .thenByDescending { it.name }
        )
        for (dir in orderedDirs) {
            if (out.size >= 4000) break
            traverseTree(
                context = context,
                treeUri = treeUri,
                parentDocId = dir.docId,
                relativePath = dir.relativePath,
                depth = depth + 1,
                out = out,
                skipSentFolder = skipSentFolder,
                targetMessageType = targetMessageType,
                maxFilesPerDirectory = maxFilesPerDirectory,
            )
        }
    }

    private fun shouldEnterFolderForType(folderLower: String, targetType: String): Boolean {
        val isImagesFolder = folderLower.contains("images") || folderLower.contains("profile photos") || folderLower.contains("wallpaper")
        val isVideoFolder = folderLower.contains("video") || folderLower.contains("animated gifs")
        val isVoiceFolder = folderLower.contains("voice notes") || folderLower.contains("whatsapp audio")
        val isDocFolder = folderLower.contains("documents")
        val isStatusFolder = folderLower.contains("statuses")
        val isCategoryFolder = isImagesFolder || isVideoFolder || isVoiceFolder || isDocFolder || isStatusFolder
        if (!isCategoryFolder) return true

        return when (targetType) {
            MessageTypes.IMAGE -> isImagesFolder || isStatusFolder
            MessageTypes.VIDEO -> isVideoFolder || isStatusFolder
            MessageTypes.VOICE -> isVoiceFolder
            MessageTypes.DOCUMENT -> isDocFolder
            else -> true
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

    private fun isSafSignatureCaptured(context: Context, signature: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_CAPTURED_SAF_SIGNATURES, emptySet()) ?: emptySet()
        return signature in existing
    }

    private fun markSafSignatureCaptured(context: Context, signature: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet(KEY_CAPTURED_SAF_SIGNATURES, emptySet()).orEmpty()
        val updated = if (existing.size >= 1500) {
            (existing.drop(existing.size - 1200) + signature).toSet()
        } else {
            existing + signature
        }
        prefs.edit().putStringSet(KEY_CAPTURED_SAF_SIGNATURES, updated).apply()
    }
}
