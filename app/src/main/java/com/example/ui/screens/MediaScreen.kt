package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.MessageEntity
import com.example.data.repository.MessageTypes
import com.example.ui.MediaFilter
import com.example.ui.MediaUiState
import com.example.ui.components.DeletedPreservedBadge
import com.example.ui.components.FullScreenMediaDialog
import com.example.ui.components.MessageMetadataSheet
import com.example.ui.components.ReadOnlyBadge
import com.example.ui.components.SavedMediaPreviewCard
import com.example.ui.components.VoiceMessageBubbleContent
import com.example.ui.components.WhatsAppTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaScreen(
    uiState: MediaUiState,
    onSelectFilter: (MediaFilter) -> Unit,
    onOpenChat: (String) -> Unit,
    onConnectFolderClick: () -> Unit,
    onSyncFolderClick: () -> Unit,
    onImportFilesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var inspectedMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var fullScreenMedia by remember { mutableStateOf<Pair<String, String>?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Saved Media",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    ReadOnlyBadge(onDarkSurface = true)
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        if (uiState.systemState.connectedFolderLabel != null) {
                            onSyncFolderClick()
                        } else {
                            onConnectFolderClick()
                        }
                    },
                    modifier = Modifier.testTag("media_sync_folder_button"),
                ) {
                    Icon(
                        imageVector = if (uiState.systemState.connectedFolderLabel != null) {
                            Icons.Filled.Sync
                        } else {
                            Icons.Filled.FolderOpen
                        },
                        contentDescription = "Sync or connect WhatsApp media folder",
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MediaFilter.entries.forEach { filter ->
                val selected = uiState.selectedFilter == filter
                FilterChip(
                    selected = selected,
                    onClick = { onSelectFilter(filter) },
                    label = {
                        Text(filter.label, style = MaterialTheme.typography.labelMedium)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.testTag("media_filter_${filter.name.lowercase()}"),
                )
            }
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (uiState.systemState.isImporting) {
                    item(key = "media_importing_bar") {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                if (uiState.items.isEmpty()) {
                    item(key = "media_empty_state") {
                        EmptyMediaVaultState(
                            hasAnyMedia = uiState.totalMediaCount > 0,
                            hasConnectedFolder = uiState.systemState.connectedFolderLabel != null,
                            onConnectFolderClick = onConnectFolderClick,
                            onSyncFolderClick = onSyncFolderClick,
                            onImportFilesClick = onImportFilesClick,
                            onResetFilter = { onSelectFilter(MediaFilter.ALL) },
                        )
                    }
                } else {
                    items(
                        items = uiState.items,
                        key = { it.id },
                    ) { message ->
                        val chatTitle = uiState.chatLookup[message.chatId]?.title ?: message.senderName
                        SavedMediaVaultCard(
                            message = message,
                            chatTitle = chatTitle,
                            onOpenChat = { onOpenChat(message.chatId) },
                            onInspect = { inspectedMessage = message },
                            onOpenFullScreen = { path, type -> fullScreenMedia = path to type },
                        )
                    }
                }

                item(key = "media_bottom_spacer") {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    inspectedMessage?.let { msg ->
        val chatTitle = uiState.chatLookup[msg.chatId]?.title ?: msg.senderName
        MessageMetadataSheet(
            message = msg,
            chatTitle = chatTitle,
            onDismiss = { inspectedMessage = null },
        )
    }

    fullScreenMedia?.let { (path, type) ->
        FullScreenMediaDialog(
            filePath = path,
            messageType = type,
            onDismiss = { fullScreenMedia = null },
        )
    }
}

@Composable
private fun SavedMediaVaultCard(
    message: MessageEntity,
    chatTitle: String,
    onOpenChat: () -> Unit,
    onInspect: () -> Unit,
    onOpenFullScreen: (String, String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("media_vault_card_${message.id}"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = message.appLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Text(
                            text = chatTitle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = "${message.senderName} • ${WhatsAppTimeFormatter.formatFullDateTime(message.timestamp)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onInspect,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = "Inspect media details",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = onOpenChat,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open conversation",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            if (message.deletedInWhatsApp) {
                DeletedPreservedBadge(label = "Deleted in WhatsApp • Saved permanently")
            }

            if (message.messageType == MessageTypes.VOICE) {
                VoiceMessageBubbleContent(
                    duration = message.mediaDuration,
                    body = message.body,
                    mediaFilePath = message.mediaFilePath,
                )
            } else {
                SavedMediaPreviewCard(
                    filePath = message.mediaFilePath,
                    isViewOnce = message.isViewOnce,
                    messageType = message.messageType,
                    body = message.body,
                    duration = message.mediaDuration,
                    onOpenFullScreen = onOpenFullScreen,
                )
            }
        }
    }
}

@Composable
private fun EmptyMediaVaultState(
    hasAnyMedia: Boolean,
    hasConnectedFolder: Boolean,
    onConnectFolderClick: () -> Unit,
    onSyncFolderClick: () -> Unit,
    onImportFilesClick: () -> Unit,
    onResetFilter: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
            }

            Text(
                text = if (hasAnyMedia) {
                    "No Saved Media In This Filter"
                } else {
                    "Saved Media Vault Is Empty"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Text(
                text = if (hasAnyMedia) {
                    "Switch back to All to view your saved photos, videos, voice notes, and View Once records."
                } else {
                    "Connect your WhatsApp Media folder once so incoming photos, videos, voice notes, and deleted items save locally inside ChatReader for unlimited offline viewing."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (hasAnyMedia) {
                Button(
                    onClick = onResetFilter,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Show All Media")
                }
            } else {
                Button(
                    onClick = if (hasConnectedFolder) onSyncFolderClick else onConnectFolderClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("media_empty_connect_folder_button"),
                ) {
                    Icon(
                        imageVector = if (hasConnectedFolder) Icons.Filled.Sync else Icons.Filled.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (hasConnectedFolder) {
                            "Sync Connected WhatsApp Folder"
                        } else {
                            "Connect Phone WhatsApp Folder"
                        }
                    )
                }

                OutlinedButton(
                    onClick = onImportFilesClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("media_empty_import_files_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.UploadFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Photos, Videos, Voice or .zip")
                }
            }
        }
    }
}
