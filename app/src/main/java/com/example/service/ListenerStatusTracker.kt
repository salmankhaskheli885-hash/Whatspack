package com.example.service

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WhatsAppAppStatus(
    val packageName: String,
    val displayName: String,
    val isInstalled: Boolean,
    val versionName: String?,
)

object ListenerStatusTracker {
    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

    private val _lastCapturedTimestamp = MutableStateFlow<Long?>(null)
    val lastCapturedTimestamp: StateFlow<Long?> = _lastCapturedTimestamp.asStateFlow()

    private val _lastRawNotificationTimestamp = MutableStateFlow<Long?>(null)
    val lastRawNotificationTimestamp: StateFlow<Long?> = _lastRawNotificationTimestamp.asStateFlow()

    fun setServiceBound(bound: Boolean) {
        _isServiceBound.value = bound
    }

    fun markMessageCaptured(timestamp: Long) {
        _lastCapturedTimestamp.value = timestamp
    }

    fun markRawNotificationSeen(timestamp: Long) {
        _lastRawNotificationTimestamp.value = timestamp
    }

    fun isNotificationAccessGranted(context: Context): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        if (enabledPackages.contains(context.packageName)) {
            return true
        }
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        val componentName = ComponentName(
            context,
            WhatsAppNotificationListenerService::class.java,
        ).flattenToString()
        return flat.split(":").any {
            it.equals(componentName, ignoreCase = true) || it.contains(context.packageName)
        }
    }

    fun isVoiceSearchPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestRebindIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            isNotificationAccessGranted(context) &&
            !_isServiceBound.value
        ) {
            runCatching {
                NotificationListenerService.requestRebind(
                    ComponentName(context, WhatsAppNotificationListenerService::class.java),
                )
            }
        }
    }

    fun queryWhatsAppApps(context: Context): List<WhatsAppAppStatus> {
        val targets = listOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
        )
        return targets.map { (pkg, name) ->
            val version = runCatching {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        pkg,
                        PackageManager.PackageInfoFlags.of(0L),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(pkg, 0)
                }
                info.versionName ?: "Installed"
            }.getOrNull()

            WhatsAppAppStatus(
                packageName = pkg,
                displayName = name,
                isInstalled = version != null,
                versionName = version,
            )
        }
    }

    fun openNotificationAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            val fallback = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }

    fun openAppNotificationSettings(context: Context, packageName: String) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            openOwnAppDetailsSettings(context)
        }
    }

    fun openOwnAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
