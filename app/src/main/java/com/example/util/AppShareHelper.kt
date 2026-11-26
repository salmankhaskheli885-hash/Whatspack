package com.example.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.local.AdminPromoConfig
import com.example.data.local.AdminPromoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object AppShareHelper {
    const val DIRECT_APK_DOWNLOAD_URL =
        "https://github.com/salmankhaskheli885-hash/Whatspack/releases/latest/download/ChatReader.apk"
    const val DOWNLOAD_WEBSITE_URL =
        "https://salmankhaskheli885-hash.github.io/Whatspack/"

    fun buildShareMessage(config: AdminPromoConfig): String {
        return buildString {
            if (config.hasPromoText) {
                appendLine(config.promoText.trim())
                appendLine()
            } else {
                appendLine("ChatReader — WhatsApp & WA Business Messages & Media Saver")
                appendLine()
            }
            appendLine("Direct APK Download (tap karte hi file download hogi):")
            appendLine(DIRECT_APK_DOWNLOAD_URL)
            appendLine()
            append("Website: ")
            append(DOWNLOAD_WEBSITE_URL)
        }
    }

    suspend fun shareApkFile(context: Context): Boolean = withContext(Dispatchers.IO) {
        val config = AdminPromoManager.loadConfig(context)
        val copiedApk = runCatching {
            val sourcePath = context.applicationInfo.publicSourceDir
                ?: context.applicationInfo.sourceDir
                ?: return@runCatching null
            val sourceFile = File(sourcePath)
            if (!sourceFile.exists() || !sourceFile.canRead() || sourceFile.length() <= 0L) {
                return@runCatching null
            }

            val shareDir = File(context.cacheDir, "shared_apk").apply { mkdirs() }
            val targetApk = File(shareDir, "ChatReader.apk")
            sourceFile.inputStream().use { input ->
                targetApk.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetApk.takeIf { it.exists() && it.length() > 0L }
        }.getOrNull()

        withContext(Dispatchers.Main) {
            val shareText = buildShareMessage(config)
            val authority = "${context.packageName}.fileprovider"

            val uris = ArrayList<Uri>()
            if (copiedApk != null) {
                runCatching {
                    FileProvider.getUriForFile(context, authority, copiedApk)
                }.getOrNull()?.let { uris.add(it) }
            }
            if (config.hasPromoFile && !config.promoFilePath.isNullOrBlank()) {
                runCatching {
                    FileProvider.getUriForFile(context, authority, File(config.promoFilePath))
                }.getOrNull()?.let { uris.add(it) }
            }

            if (uris.size > 1) {
                val multiLaunched = runCatching {
                    val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                        putExtra(Intent.EXTRA_SUBJECT, "ChatReader APK")
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        clipData = ClipData.newRawUri("ChatReader", uris[0]).apply {
                            for (i in 1 until uris.size) {
                                addItem(ClipData.Item(uris[i]))
                            }
                        }
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(sendIntent, "Share ChatReader APK & Promo"),
                    )
                }.isSuccess
                if (multiLaunched) return@withContext true
            }

            if (uris.isNotEmpty()) {
                val singleUri = uris.first()
                val mime = if (copiedApk != null) {
                    "application/vnd.android.package-archive"
                } else {
                    config.promoFileMimeType ?: "*/*"
                }
                val singleLaunched = runCatching {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, singleUri)
                        putExtra(Intent.EXTRA_SUBJECT, "ChatReader APK")
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(
                        Intent.createChooser(sendIntent, "Share ChatReader APK"),
                    )
                }.isSuccess
                if (singleLaunched) return@withContext true
            }

            shareDirectDownloadLink(context)
            false
        }
    }

    fun shareDirectDownloadLink(context: Context) {
        val config = AdminPromoManager.loadConfig(context)
        val shareText = buildShareMessage(config)
        val authority = "${context.packageName}.fileprovider"

        if (config.hasPromoFile && !config.promoFilePath.isNullOrBlank()) {
            val fileShared = runCatching {
                val promoUri = FileProvider.getUriForFile(
                    context,
                    authority,
                    File(config.promoFilePath),
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = config.promoFileMimeType ?: "*/*"
                    putExtra(Intent.EXTRA_STREAM, promoUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Download ChatReader APK")
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(
                    Intent.createChooser(intent, "Share App Link & Promo File"),
                )
            }.isSuccess
            if (fileShared) return
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Download ChatReader APK")
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Share Direct APK Download Link"))
        }
    }
}
