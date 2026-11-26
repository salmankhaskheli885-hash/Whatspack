package com.example.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class AdminPromoConfig(
    val promoText: String = "",
    val promoFilePath: String? = null,
    val promoFileName: String? = null,
    val promoFileSizeBytes: Long = 0L,
    val promoFileMimeType: String? = null,
) {
    val hasPromoText: Boolean
        get() = promoText.isNotBlank()

    val hasPromoFile: Boolean
        get() = !promoFilePath.isNullOrBlank() && File(promoFilePath).let { it.exists() && it.length() > 0L }

    val hasAnyPromo: Boolean
        get() = hasPromoText || hasPromoFile
}

object AdminPromoManager {
    private const val PREFS_NAME = "admin_promo_prefs"
    private const val KEY_PROMO_TEXT = "promo_text"
    private const val KEY_PROMO_FILE_PATH = "promo_file_path"
    private const val KEY_PROMO_FILE_NAME = "promo_file_name"
    private const val KEY_PROMO_FILE_SIZE = "promo_file_size"
    private const val KEY_PROMO_FILE_MIME = "promo_file_mime"
    private const val ADMIN_PIN = "2124"

    fun verifyPin(input: String): Boolean {
        return input.trim() == ADMIN_PIN
    }

    fun loadConfig(context: Context): AdminPromoConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val rawPath = prefs.getString(KEY_PROMO_FILE_PATH, null)
        val fileExists = !rawPath.isNullOrBlank() && File(rawPath).let { it.exists() && it.length() > 0L }
        return AdminPromoConfig(
            promoText = prefs.getString(KEY_PROMO_TEXT, "").orEmpty(),
            promoFilePath = if (fileExists) rawPath else null,
            promoFileName = if (fileExists) prefs.getString(KEY_PROMO_FILE_NAME, null) else null,
            promoFileSizeBytes = if (fileExists) prefs.getLong(KEY_PROMO_FILE_SIZE, 0L) else 0L,
            promoFileMimeType = if (fileExists) prefs.getString(KEY_PROMO_FILE_MIME, null) else null,
        )
    }

    fun savePromoText(context: Context, text: String): AdminPromoConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PROMO_TEXT, text.trim()).apply()
        return loadConfig(context)
    }

    suspend fun savePromoFile(context: Context, uri: Uri): AdminPromoConfig? = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val rawName = queryDisplayName(context, uri)
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.takeIf { it.isNotBlank() }
            ?: "promo_file_${System.currentTimeMillis()}"
        val mimeType = resolver.getType(uri) ?: "application/octet-stream"

        val dir = File(context.filesDir, "admin_promo").apply { mkdirs() }
        // Remove previous uploaded file so local storage stays tidy
        dir.listFiles()?.forEach { existing ->
            runCatching { existing.delete() }
        }

        val targetFile = File(dir, rawName)
        val copied = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.exists() && targetFile.length() > 0L
        }.getOrDefault(false)

        if (!copied) {
            runCatching { targetFile.delete() }
            return@withContext null
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_PROMO_FILE_PATH, targetFile.absolutePath)
            .putString(KEY_PROMO_FILE_NAME, rawName)
            .putLong(KEY_PROMO_FILE_SIZE, targetFile.length())
            .putString(KEY_PROMO_FILE_MIME, mimeType)
            .apply()

        loadConfig(context)
    }

    fun clearPromoFile(context: Context): AdminPromoConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existingPath = prefs.getString(KEY_PROMO_FILE_PATH, null)
        if (!existingPath.isNullOrBlank()) {
            runCatching { File(existingPath).delete() }
        }
        prefs.edit()
            .remove(KEY_PROMO_FILE_PATH)
            .remove(KEY_PROMO_FILE_NAME)
            .remove(KEY_PROMO_FILE_SIZE)
            .remove(KEY_PROMO_FILE_MIME)
            .apply()
        return loadConfig(context)
    }

    fun readPromoFileBytes(context: Context): ByteArray? {
        val config = loadConfig(context)
        val path = config.promoFilePath ?: return null
        val file = File(path)
        if (!file.exists() || !file.canRead() || file.length() <= 0L) return null
        return runCatching { file.readBytes() }.getOrNull()
    }

    fun applyCloudPromo(
        context: Context,
        promoText: String,
        fileName: String?,
        mimeType: String?,
        fileSizeBytes: Long?,
        fileBase64: String?,
    ): AdminPromoConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit().putString(KEY_PROMO_TEXT, promoText.trim())

        if (!fileName.isNullOrBlank() && !fileBase64.isNullOrBlank()) {
            val decoded = runCatching {
                android.util.Base64.decode(fileBase64, android.util.Base64.DEFAULT)
            }.getOrNull()
            if (decoded != null && decoded.isNotEmpty()) {
                val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
                    .ifBlank { "promo_file" }
                val dir = File(context.filesDir, "admin_promo").apply { mkdirs() }
                dir.listFiles()?.forEach { runCatching { it.delete() } }
                val targetFile = File(dir, safeName)
                runCatching { targetFile.writeBytes(decoded) }
                if (targetFile.exists() && targetFile.length() > 0L) {
                    editor
                        .putString(KEY_PROMO_FILE_PATH, targetFile.absolutePath)
                        .putString(KEY_PROMO_FILE_NAME, safeName)
                        .putLong(KEY_PROMO_FILE_SIZE, fileSizeBytes ?: targetFile.length())
                        .putString(KEY_PROMO_FILE_MIME, mimeType ?: "application/octet-stream")
                }
            }
        } else {
            val existingPath = prefs.getString(KEY_PROMO_FILE_PATH, null)
            if (!existingPath.isNullOrBlank()) {
                runCatching { File(existingPath).delete() }
            }
            editor
                .remove(KEY_PROMO_FILE_PATH)
                .remove(KEY_PROMO_FILE_NAME)
                .remove(KEY_PROMO_FILE_SIZE)
                .remove(KEY_PROMO_FILE_MIME)
        }

        editor.apply()
        return loadConfig(context)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull()
    }
}
