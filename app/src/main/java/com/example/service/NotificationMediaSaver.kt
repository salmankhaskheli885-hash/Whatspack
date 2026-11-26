package com.example.service

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.core.os.BundleCompat
import com.example.data.repository.MessageTypes
import com.example.data.repository.ParsedWhatsAppMessage
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object NotificationMediaSaver {
    private const val MEDIA_DIR_NAME = "captured_media"

    fun captureMediaIfAvailable(
        context: Context,
        sbn: StatusBarNotification,
        parsed: ParsedWhatsAppMessage,
    ): String? {
        if (parsed.messageType != MessageTypes.IMAGE && parsed.messageType != MessageTypes.VIDEO) {
            return null
        }

        val extras: Bundle = sbn.notification?.extras ?: Bundle.EMPTY
        val mediaDir = File(context.filesDir, MEDIA_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }

        // 1. Try MessagingStyle dataUri (if WhatsApp attached a readable content URI)
        parsed.mediaUriString?.let { uriStr ->
            val savedFromUri = runCatching {
                val uri = Uri.parse(uriStr)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input) ?: return@use null
                    val outFile = File(mediaDir, "media_${UUID.randomUUID()}.jpg")
                    FileOutputStream(outFile).use { fos ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, fos)
                    }
                    bitmap.recycle()
                    outFile.absolutePath
                }
            }.getOrNull()
            if (savedFromUri != null) return savedFromUri
        }

        // 2. Try Notification.EXTRA_PICTURE (BigPictureStyle image preview if attached)
        val pictureBitmap = runCatching {
            BundleCompat.getParcelable(extras, Notification.EXTRA_PICTURE, Bitmap::class.java)
        }.getOrNull()

        if (pictureBitmap != null && !pictureBitmap.isRecycled) {
            return runCatching {
                val outFile = File(mediaDir, "pic_${UUID.randomUUID()}.jpg")
                FileOutputStream(outFile).use { fos ->
                    pictureBitmap.compress(Bitmap.CompressFormat.JPEG, 88, fos)
                }
                outFile.absolutePath
            }.getOrNull()
        }

        return null
    }
}
