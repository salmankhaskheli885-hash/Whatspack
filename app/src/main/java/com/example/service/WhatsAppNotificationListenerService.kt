package com.example.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.ChatReaderApp
import com.example.data.repository.MessageTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WhatsAppNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerConnected() {
        super.onListenerConnected()
        ListenerStatusTracker.setServiceBound(true)
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
        // Intentionally a no-op: when WhatsApp clears chats, deletes messages,
        // or expires View Once notifications, ChatReader keeps all saved data permanently.
    }

    private fun processNotification(sbn: StatusBarNotification) {
        if (sbn.packageName in WhatsAppNotificationParser.SUPPORTED_PACKAGES) {
            ListenerStatusTracker.markRawNotificationSeen(
                sbn.postTime.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
        }
        val result = WhatsAppNotificationParser.parseEventsFromStatusBarNotification(sbn)
        if (result.messages.isEmpty() && result.deletedSignals.isEmpty()) return

        val repository = (application as? ChatReaderApp)?.repository ?: return
        serviceScope.launch {
            result.messages.forEach { msg ->
                val savedMediaPath = NotificationMediaSaver.captureMediaIfAvailable(
                    context = applicationContext,
                    sbn = sbn,
                    parsed = msg,
                )
                val enriched = if (savedMediaPath != null) {
                    msg.copy(mediaFilePath = savedMediaPath)
                } else {
                    msg
                }
                val inserted = repository.saveIncomingMessage(enriched)
                if (inserted) {
                    ListenerStatusTracker.markMessageCaptured(enriched.timestamp)
                }

                val needsSafMedia = enriched.mediaFilePath == null &&
                    enriched.messageType in setOf(
                        MessageTypes.IMAGE,
                        MessageTypes.VIDEO,
                        MessageTypes.VOICE,
                        MessageTypes.DOCUMENT,
                    )
                if (needsSafMedia &&
                    WhatsAppStorageImporter.getVaultFolderInfo(applicationContext).treeUriString != null
                ) {
                    launch {
                        delay(1_200L)
                        var captured = WhatsAppStorageImporter.captureLatestMediaForNotification(
                            context = applicationContext,
                            messageType = enriched.messageType,
                            packageName = enriched.packageName,
                            timestamp = enriched.timestamp,
                        )
                        if (captured == null) {
                            delay(2_200L)
                            captured = WhatsAppStorageImporter.captureLatestMediaForNotification(
                                context = applicationContext,
                                messageType = enriched.messageType,
                                packageName = enriched.packageName,
                                timestamp = enriched.timestamp,
                            )
                        }
                        if (captured != null) {
                            repository.attachMediaToMessage(
                                dedupKey = enriched.dedupKey,
                                mediaFilePath = captured.filePath,
                                mediaDuration = captured.duration,
                            )
                        }
                    }
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
