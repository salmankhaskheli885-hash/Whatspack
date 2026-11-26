package com.example.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.SystemPermissionState
import com.example.ui.components.ReadOnlyBadge
import com.example.ui.components.WhatsAppTimeFormatter
import com.example.ui.theme.WarningAmber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionScreen(
    systemState: SystemPermissionState,
    onOpenNotificationSettings: () -> Unit,
    onVoicePermissionChanged: () -> Unit,
    onOpenAppNotificationSettings: (String) -> Unit,
    onOpenOwnAppDetails: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onConnectWhatsAppFolder: () -> Unit,
    onSyncWhatsAppFolder: () -> Unit,
    onImportChatOrMediaFiles: () -> Unit,
    onSaveBackupToStorage: () -> Unit,
    onClearAllData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearDialog by remember { mutableStateOf(false) }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        onVoicePermissionChanged()
    }

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
                        text = "Permission & Privacy",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    ReadOnlyBadge(onDarkSurface = true)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "hero_banner") {
                    Image(
                        painter = painterResource(id = R.drawable.img_permission_hero),
                        contentDescription = "Notification Access Privacy Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(170.dp)
                            .clip(RoundedCornerShape(20.dp)),
                    )
                }

                item(key = "permission_status_card") {
                    val granted = systemState.isNotificationAccessGranted
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (granted) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (granted) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                            } else {
                                                WarningAmber.copy(alpha = 0.18f)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = if (granted) {
                                            Icons.Filled.CheckCircle
                                        } else {
                                            Icons.Filled.NotificationsActive
                                        },
                                        contentDescription = null,
                                        tint = if (granted) MaterialTheme.colorScheme.primary else WarningAmber,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (granted) {
                                            stringResource(R.string.permission_granted_title)
                                        } else {
                                            stringResource(R.string.permission_required_title)
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    val statusText = when {
                                        granted && systemState.isServiceBound ->
                                            "Listener bound & reading WhatsApp notifications"
                                        granted ->
                                            "Permission granted • Listener ready"
                                        else ->
                                            "Permission disabled • Tap below to allow"
                                    }
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            systemState.lastCapturedTimestamp?.let { ts ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                ) {
                                    Text(
                                        text = "Last message saved: ${WhatsAppTimeFormatter.formatFullDateTime(ts)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                            } ?: systemState.lastRawNotificationTimestamp?.let { ts ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                ) {
                                    Text(
                                        text = "Last WhatsApp ping seen: ${WhatsAppTimeFormatter.formatFullDateTime(ts)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                            }

                            Button(
                                onClick = onOpenNotificationSettings,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("open_permission_settings_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (granted) {
                                        "Manage Notification Access"
                                    } else {
                                        "Open Notification Access Settings"
                                    },
                                )
                            }
                        }
                    }
                }

                item(key = "storage_vault_card") {
                    val folderLabel = systemState.connectedFolderLabel
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (folderLabel != null) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FolderOpen,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (folderLabel != null) {
                                            "Storage Access Active ($folderLabel)"
                                        } else {
                                            "WhatsApp Storage Folder Permission"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = if (folderLabel != null) {
                                            "Auto-syncing photos, videos, voice notes & statuses into ChatReader local storage."
                                        } else {
                                            "Tap below and allow WhatsApp/Media folder access to save existing files locally. Note: WhatsApp encrypts past text DBs, so past text chats require Export Chat."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            Text(
                                text = "Choose Android/media/com.whatsapp/WhatsApp/Media (or WhatsApp/Media) and tap 'Use this folder' → 'Allow'. For full old text chats, open WhatsApp → ⋮ → More → Export chat → Share to ChatReader.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            systemState.lastVaultSyncTimestamp?.let { syncTs ->
                                Text(
                                    text = "Last folder sync: ${WhatsAppTimeFormatter.formatFullDateTime(syncTs)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }

                            if (systemState.isImporting) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }

                            systemState.importStatusMessage?.let { msg ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                ) {
                                    Text(
                                        text = msg,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                            }

                            if (folderLabel != null) {
                                Button(
                                    onClick = onSyncWhatsAppFolder,
                                    enabled = !systemState.isImporting,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("sync_whatsapp_folder_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sync Connected WhatsApp Folder")
                                }
                            }

                            Button(
                                onClick = onConnectWhatsAppFolder,
                                enabled = !systemState.isImporting,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("connect_whatsapp_folder_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    if (folderLabel != null) "Change Connected Folder" else "Connect Phone WhatsApp Folder",
                                )
                            }

                            OutlinedButton(
                                onClick = onImportChatOrMediaFiles,
                                enabled = !systemState.isImporting,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("import_chat_files_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.UploadFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import Chat (.txt / .zip / .json) or Media")
                            }

                            OutlinedButton(
                                onClick = onSaveBackupToStorage,
                                enabled = !systemState.isImporting,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("save_backup_storage_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SaveAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Save Permanent Backup to Phone Storage")
                            }
                        }
                    }
                }

                item(key = "voice_search_permission_card") {
                    val voiceGranted = systemState.isVoiceSearchPermissionGranted
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (voiceGranted) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            },
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = if (voiceGranted) Icons.Filled.CheckCircle else Icons.Filled.Mic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (voiceGranted) {
                                            "Voice Search Permission Active"
                                        } else {
                                            "Voice Search Permission (Microphone)"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = if (voiceGranted) {
                                            "Microphone access granted • Tap Mic in Chats to search by voice"
                                        } else {
                                            "Allow microphone permission to search saved WhatsApp & WA Business chats by voice."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            if (!voiceGranted) {
                                Button(
                                    onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("grant_voice_permission_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Mic,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Allow Voice Search Permission")
                                }
                            }
                        }
                    }
                }

                item(key = "whatsapp_business_check_card") {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Business,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    text = "WhatsApp & WA Business Check",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Text(
                                text = "If WhatsApp Business messages do not appear, confirm its notifications are enabled and allow restricted settings for ChatReader on Android 13+.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { onOpenAppNotificationSettings("com.whatsapp.w4b") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("WA Business", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { onOpenAppNotificationSettings("com.whatsapp") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text("WhatsApp", maxLines = 1)
                                }
                            }
                            OutlinedButton(
                                onClick = onOpenOwnAppDetails,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Open ChatReader App Info (Restricted Settings)")
                            }
                            OutlinedButton(
                                onClick = onOpenBatterySettings,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Keep Service Running (Battery Optimization)")
                            }
                        }
                    }
                }

                item(key = "how_to_enable") {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "How WhatsApp Reading Works",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            StepRow(
                                number = "1",
                                title = "Open Notification Access",
                                description = "Tap the button above to open Android's Notification Access settings.",
                            )
                            StepRow(
                                number = "2",
                                title = "Allow ChatReader",
                                description = "Select ChatReader in the list and toggle Allow notification access.",
                            )
                            StepRow(
                                number = "3",
                                title = "Read Incoming Messages",
                                description = "Keep WhatsApp notifications turned on. Every incoming chat message is captured here automatically.",
                            )
                        }
                    }
                }

                item(key = "privacy_guarantees") {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = "Read-Only Privacy Guarantee",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            GuaranteeRow(
                                icon = Icons.Outlined.Visibility,
                                title = "Strictly Read-Only",
                                description = "You can view WhatsApp message details without sending replies or triggering read receipts.",
                            )
                            GuaranteeRow(
                                icon = Icons.Filled.History,
                                title = "Permanent Record Until You Delete",
                                description = "Even if all messages or chats are deleted from original WhatsApp, every saved record, voice note log, and captured image preview stays saved here until you delete it.",
                            )
                            GuaranteeRow(
                                icon = Icons.Outlined.Lock,
                                title = "View Once & Media Records",
                                description = "View Once records and any image previews provided by Android notifications are saved for unlimited viewing here. Note: WhatsApp blocks third-party apps from reading encrypted View Once video files directly.",
                            )
                            GuaranteeRow(
                                icon = Icons.Outlined.Shield,
                                title = "Scoped to WhatsApp Only",
                                description = "ChatReader only reads notifications from WhatsApp (com.whatsapp) and WhatsApp Business (com.whatsapp.w4b).",
                            )
                        }
                    }
                }

                item(key = "clear_data") {
                    OutlinedButton(
                        onClick = { showClearDialog = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("clear_all_data_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear All Saved WhatsApp Data")
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear Saved Data?") },
            text = {
                Text("This permanently deletes all WhatsApp chats and message logs stored locally inside ChatReader. Your actual WhatsApp app will not be affected.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClearAllData()
                    },
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun StepRow(
    number: String,
    title: String,
    description: String,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GuaranteeRow(
    icon: ImageVector,
    title: String,
    description: String,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
