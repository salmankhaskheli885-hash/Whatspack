package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ChatReaderApp
import com.example.data.local.ChatEntity
import com.example.data.local.MessageEntity
import com.example.data.repository.MessageTypes
import com.example.service.ListenerStatusTracker
import com.example.service.WhatsAppAppStatus
import com.example.service.WhatsAppStorageImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ChatFilter(val label: String) {
    ALL("All"),
    VOICE("Voice"),
    MEDIA("Media / Once"),
    UNREAD("Unread"),
    DELETED("Deleted Saved"),
    GROUPS("Groups"),
    WHATSAPP("WhatsApp"),
    BUSINESS("WA Business"),
}

enum class MediaFilter(val label: String) {
    ALL("All"),
    PHOTOS("Photos"),
    VIDEOS("Videos"),
    VOICE("Voice"),
    ONCE("View Once"),
    DELETED("Deleted"),
}

data class SystemPermissionState(
    val isNotificationAccessGranted: Boolean = false,
    val isVoiceSearchPermissionGranted: Boolean = false,
    val isServiceBound: Boolean = false,
    val lastCapturedTimestamp: Long? = null,
    val lastRawNotificationTimestamp: Long? = null,
    val whatsAppApps: List<WhatsAppAppStatus> = emptyList(),
    val connectedFolderLabel: String? = null,
    val lastVaultSyncTimestamp: Long? = null,
    val isImporting: Boolean = false,
    val importStatusMessage: String? = null,
)

data class ChatsUiState(
    val chats: List<ChatEntity> = emptyList(),
    val totalChatCount: Int = 0,
    val totalUnreadCount: Int = 0,
    val searchQuery: String = "",
    val selectedFilter: ChatFilter = ChatFilter.ALL,
    val systemState: SystemPermissionState = SystemPermissionState(),
)

data class DetailsUiState(
    val totalChats: Int = 0,
    val totalMessages: Int = 0,
    val voiceMessages: Int = 0,
    val mediaSavedMessages: Int = 0,
    val deletedPreservedMessages: Int = 0,
    val groupChats: Int = 0,
    val totalUnread: Int = 0,
    val recentMessages: List<MessageEntity> = emptyList(),
    val chatLookup: Map<String, ChatEntity> = emptyMap(),
    val systemState: SystemPermissionState = SystemPermissionState(),
)

data class ChatDetailUiState(
    val chat: ChatEntity? = null,
    val messages: List<MessageEntity> = emptyList(),
    val isNotificationAccessGranted: Boolean = false,
)

data class MediaUiState(
    val items: List<MessageEntity> = emptyList(),
    val totalMediaCount: Int = 0,
    val selectedFilter: MediaFilter = MediaFilter.ALL,
    val chatLookup: Map<String, ChatEntity> = emptyMap(),
    val systemState: SystemPermissionState = SystemPermissionState(),
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as ChatReaderApp).repository

    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(ChatFilter.ALL)
    private val selectedMediaFilter = MutableStateFlow(MediaFilter.ALL)
    private val permissionGranted = MutableStateFlow(
        ListenerStatusTracker.isNotificationAccessGranted(application)
    )
    private val voicePermissionGranted = MutableStateFlow(
        ListenerStatusTracker.isVoiceSearchPermissionGranted(application)
    )
    private val whatsAppApps = MutableStateFlow(
        ListenerStatusTracker.queryWhatsAppApps(application)
    )
    private val vaultInfo = MutableStateFlow(
        WhatsAppStorageImporter.getVaultFolderInfo(application)
    )
    private val isImporting = MutableStateFlow(false)
    private val importStatusMessage = MutableStateFlow<String?>(null)

    private val permissionTuple = combine(
        permissionGranted,
        voicePermissionGranted,
        ListenerStatusTracker.isServiceBound,
        ListenerStatusTracker.lastCapturedTimestamp,
        ListenerStatusTracker.lastRawNotificationTimestamp,
    ) { notifGranted, voiceGranted, bound, lastTs, rawTs ->
        SystemPermissionState(
            isNotificationAccessGranted = notifGranted,
            isVoiceSearchPermissionGranted = voiceGranted,
            isServiceBound = bound,
            lastCapturedTimestamp = lastTs,
            lastRawNotificationTimestamp = rawTs,
        )
    }

    val systemState: StateFlow<SystemPermissionState> = combine(
        permissionTuple,
        whatsAppApps,
        vaultInfo,
        isImporting,
        importStatusMessage,
    ) { base, apps, vault, importing, msg ->
        base.copy(
            whatsAppApps = apps,
            connectedFolderLabel = vault.folderLabel,
            lastVaultSyncTimestamp = vault.lastSyncTimestamp,
            isImporting = importing,
            importStatusMessage = msg,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SystemPermissionState(
            isNotificationAccessGranted = permissionGranted.value,
            isVoiceSearchPermissionGranted = voicePermissionGranted.value,
            whatsAppApps = whatsAppApps.value,
            connectedFolderLabel = vaultInfo.value.folderLabel,
            lastVaultSyncTimestamp = vaultInfo.value.lastSyncTimestamp,
        ),
    )

    val chatsUiState: StateFlow<ChatsUiState> = combine(
        repository.allChats,
        repository.totalUnreadCount,
        searchQuery,
        selectedFilter,
        systemState,
    ) { allChats, unreadCount, query, filter, sys ->
        val filtered = allChats.filter { chat ->
            val matchesFilter = when (filter) {
                ChatFilter.ALL -> true
                ChatFilter.VOICE -> chat.lastMessageType == MessageTypes.VOICE
                ChatFilter.MEDIA -> chat.lastMessageType in setOf(
                    MessageTypes.IMAGE,
                    MessageTypes.VIDEO,
                    MessageTypes.DOCUMENT,
                )
                ChatFilter.UNREAD -> chat.unreadCount > 0
                ChatFilter.DELETED -> chat.deletedCount > 0
                ChatFilter.GROUPS -> chat.isGroup
                ChatFilter.WHATSAPP -> chat.packageName == "com.whatsapp"
                ChatFilter.BUSINESS -> chat.packageName == "com.whatsapp.w4b"
            }
            val q = query.trim()
            val matchesQuery = q.isEmpty() ||
                chat.title.contains(q, ignoreCase = true) ||
                chat.lastMessageText.contains(q, ignoreCase = true) ||
                chat.lastSenderName.contains(q, ignoreCase = true)
            matchesFilter && matchesQuery
        }
        ChatsUiState(
            chats = filtered,
            totalChatCount = allChats.size,
            totalUnreadCount = unreadCount,
            searchQuery = query,
            selectedFilter = filter,
            systemState = sys,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatsUiState(systemState = systemState.value),
    )

    val detailsUiState: StateFlow<DetailsUiState> = combine(
        repository.allChats,
        repository.totalMessageCount,
        repository.voiceMessageCount,
        repository.mediaSavedCount,
        repository.deletedMessageCount,
        repository.totalUnreadCount,
        repository.recentMessages,
        systemState,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val allChats = values[0] as List<ChatEntity>
        val msgCount = values[1] as Int
        val voiceCount = values[2] as Int
        val mediaCount = values[3] as Int
        val deletedCount = values[4] as Int
        val unread = values[5] as Int
        @Suppress("UNCHECKED_CAST")
        val recent = values[6] as List<MessageEntity>
        val sys = values[7] as SystemPermissionState

        DetailsUiState(
            totalChats = allChats.size,
            totalMessages = msgCount,
            voiceMessages = voiceCount,
            mediaSavedMessages = mediaCount,
            deletedPreservedMessages = deletedCount,
            groupChats = allChats.count { it.isGroup },
            totalUnread = unread,
            recentMessages = recent,
            chatLookup = allChats.associateBy { it.chatId },
            systemState = sys,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailsUiState(systemState = systemState.value),
    )

    val mediaUiState: StateFlow<MediaUiState> = combine(
        repository.allMediaMessages,
        repository.allChats,
        selectedMediaFilter,
        systemState,
    ) { allMedia, allChats, filter, sys ->
        val filtered = allMedia.filter { msg ->
            when (filter) {
                MediaFilter.ALL -> true
                MediaFilter.PHOTOS -> msg.messageType == MessageTypes.IMAGE
                MediaFilter.VIDEOS -> msg.messageType == MessageTypes.VIDEO
                MediaFilter.VOICE -> msg.messageType == MessageTypes.VOICE
                MediaFilter.ONCE -> msg.isViewOnce
                MediaFilter.DELETED -> msg.deletedInWhatsApp
            }
        }
        MediaUiState(
            items = filtered,
            totalMediaCount = allMedia.size,
            selectedFilter = filter,
            chatLookup = allChats.associateBy { it.chatId },
            systemState = sys,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MediaUiState(systemState = systemState.value),
    )

    fun observeChatDetail(chatId: String): Flow<ChatDetailUiState> {
        return combine(
            repository.observeChat(chatId),
            repository.observeMessagesForChat(chatId),
            permissionGranted,
        ) { chat, messages, granted ->
            ChatDetailUiState(
                chat = chat,
                messages = messages,
                isNotificationAccessGranted = granted,
            )
        }
    }

    fun refreshSystemStatus() {
        val context = getApplication<Application>()
        permissionGranted.value = ListenerStatusTracker.isNotificationAccessGranted(context)
        voicePermissionGranted.value = ListenerStatusTracker.isVoiceSearchPermissionGranted(context)
        whatsAppApps.value = ListenerStatusTracker.queryWhatsAppApps(context)
        val latestVault = WhatsAppStorageImporter.getVaultFolderInfo(context)
        vaultInfo.value = latestVault
        ListenerStatusTracker.requestRebindIfNeeded(context)

        val lastSync = latestVault.lastSyncTimestamp ?: 0L
        if (latestVault.treeUriString != null && !isImporting.value) {
            viewModelScope.launch {
                val savedCount = repository.totalMessageCount.first()
                if (savedCount == 0 || System.currentTimeMillis() - lastSync > 60_000L) {
                    syncConnectedFolder()
                }
            }
        }
    }

    fun importFromTreeUri(treeUri: Uri) {
        if (isImporting.value) return
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            isImporting.value = true
            importStatusMessage.value = "Scanning WhatsApp storage folder..."
            val result = runCatching {
                WhatsAppStorageImporter.importFromTreeUri(context, treeUri, repository)
            }.getOrElse { err ->
                com.example.service.StorageImportSummary(0, 0, "Import error: ${err.localizedMessage ?: "Unknown"}")
            }
            vaultInfo.value = WhatsAppStorageImporter.getVaultFolderInfo(context)
            importStatusMessage.value = result.summaryMessage
            isImporting.value = false
        }
    }

    fun syncConnectedFolder() {
        if (isImporting.value) return
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            isImporting.value = true
            importStatusMessage.value = "Syncing connected WhatsApp folder..."
            val result = runCatching {
                WhatsAppStorageImporter.syncConnectedFolder(context, repository)
            }.getOrElse { err ->
                com.example.service.StorageImportSummary(0, 0, "Sync error: ${err.localizedMessage ?: "Unknown"}")
            }
            vaultInfo.value = WhatsAppStorageImporter.getVaultFolderInfo(context)
            importStatusMessage.value = result.summaryMessage
            isImporting.value = false
        }
    }

    fun importDocuments(uris: List<Uri>, sharedText: String? = null) {
        if (uris.isEmpty() && sharedText.isNullOrBlank()) return
        if (isImporting.value) return
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            isImporting.value = true
            importStatusMessage.value = "Saving WhatsApp data into local vault..."
            val result = runCatching {
                WhatsAppStorageImporter.importDocumentUris(context, uris, repository, sharedText)
            }.getOrElse { err ->
                com.example.service.StorageImportSummary(0, 0, "Import error: ${err.localizedMessage ?: "Unknown"}")
            }
            importStatusMessage.value = result.summaryMessage
            isImporting.value = false
        }
    }

    fun clearImportStatusMessage() {
        importStatusMessage.value = null
    }

    fun exportBackupToUri(targetUri: Uri) {
        if (isImporting.value) return
        val context = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            isImporting.value = true
            importStatusMessage.value = "Saving backup to phone storage..."
            val msg = runCatching {
                com.example.service.ChatBackupManager.exportBackupToUri(context, targetUri, repository)
            }.getOrElse { err ->
                "Backup failed: ${err.localizedMessage ?: "Unknown error"}"
            }
            importStatusMessage.value = msg
            isImporting.value = false
        }
    }

    fun openNotificationSettings() {
        ListenerStatusTracker.openNotificationAccessSettings(getApplication())
    }

    fun openWhatsAppNotificationSettings(packageName: String) {
        ListenerStatusTracker.openAppNotificationSettings(getApplication(), packageName)
    }

    fun openOwnAppDetailsSettings() {
        ListenerStatusTracker.openOwnAppDetailsSettings(getApplication())
    }

    fun openBatteryOptimizationSettings() {
        ListenerStatusTracker.openBatteryOptimizationSettings(getApplication())
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectFilter(filter: ChatFilter) {
        selectedFilter.value = filter
    }

    fun selectMediaFilter(filter: MediaFilter) {
        selectedMediaFilter.value = filter
    }

    fun markChatAsRead(chatId: String) {
        viewModelScope.launch {
            repository.markChatAsRead(chatId)
        }
    }

    fun togglePin(chatId: String, currentlyPinned: Boolean) {
        viewModelScope.launch {
            repository.setPinned(chatId, !currentlyPinned)
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            repository.deleteChat(chatId)
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.clearAll()
            WhatsAppStorageImporter.clearCapturedSafTracking(getApplication())
        }
    }
}
