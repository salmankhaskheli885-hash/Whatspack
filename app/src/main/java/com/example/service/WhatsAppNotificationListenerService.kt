package com.example.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.ChatReaderApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WhatsAppNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        ListenerStatusTracker.setServiceBound(true)
        // Capture any currently active WhatsApp notifications immediately on bind
        runCatching {
            activeNotifications?.forEach { sbn ->
                processNotification(sbn)
            }
        }
    }

    override fun onListenerDisconnected() {
        ListenerStatusTracker.setServiceBound(false)
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn != null) {
            processNotification(sbn)
        }
    }

    override fun onNotificationRemoved(
        sbn: StatusBarNotification?,
        rankingMap: RankingMap?,
        reason: Int,
    ) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        // Intentionally a no-op: when WhatsApp cancels, clears, or deletes a notification,
        // ChatReader keeps every captured message permanently preserved locally.
    }

    private fun processNotification(sbn: StatusBarNotification) {
        val result = WhatsAppNotificationParser.parseEventsFromStatusBarNotification(sbn)
        if (result.messages.isEmpty() && result.deletedSignals.isEmpty()) return

        val repository = (application as? ChatReaderApp)?.repository ?: return
        serviceScope.launch {
            result.messages.forEach { msg ->
                val inserted = repository.saveIncomingMessage(msg)
                if (inserted) {
                    ListenerStatusTracker.markMessageCaptured(msg.timestamp)
                }
            }
            result.deletedSignals.forEach { signal ->
                repository.markDeletedInWhatsApp(signal)
            }
        }
    }

    override fun onDestroy() {
        ListenerStatusTracker.setServiceBound(false)
        serviceScope.cancel()
        super.onDestroy()
    }
}
