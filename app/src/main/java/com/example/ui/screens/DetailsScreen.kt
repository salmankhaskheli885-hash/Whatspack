package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.MessageEntity
import com.example.data.repository.MessageTypes
import com.example.service.WhatsAppAppStatus
import com.example.ui.DetailsUiState
import com.example.ui.components.DeletedPreservedBadge
import com.example.ui.components.MediaTypeBadge
import com.example.ui.components.MessageMetadataSheet
import com.example.ui.components.ReadOnlyBadge
import com.example.ui.components.ViewOnceBadge
import com.example.ui.components.WhatsAppTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    uiState: DetailsUiState,
    onOpenChat: (String) -> Unit,
    onOpenAppNotificationSettings: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var inspectedMessage by remember { mutableStateOf<MessageEntity?>(null) }

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
                        text = "WhatsApp Details",
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
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item(key = "stats_grid") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Captured Data Overview",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            StatCard(
                                label = "Total Chats",
                                value = uiState.totalChats.toString(),
                                icon = Icons.Filled.Forum,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stat_total_chats"),
                            )
                            StatCard(
                                label = "Messages Read",
                                value = uiState.totalMessages.toString(),
                                icon = Icons.Filled.Message,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stat_total_messages"),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            StatCard(
                                label = "Voice Notes",
                                value = uiState.voiceMessages.toString(),
                                icon = Icons.Filled.Mic,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stat_voice_messages"),
                            )
                            StatCard(
                                label = "Deleted Saved",
                                value = uiState.deletedPreservedMessages.toString(),
                                icon = Icons.Filled.History,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stat_deleted_saved"),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            StatCard(
                                label = "Media / Once",
                                value = uiState.mediaSavedMessages.toString(),
                                icon = Icons.Filled.PhotoLibrary,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stat_media_saved"),
                            )
                            StatCard(
                                label = "Group Chats",
                                value = uiState.groupChats.toString(),
                                icon = Icons.Filled.Groups,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("stat_group_chats"),
                            )
                        }
                    }
                }

                item(key = "whatsapp_apps") {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Installed WhatsApp Clients",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                uiState.systemState.whatsAppApps.forEachIndexed { idx, app ->
                                    WhatsAppAppRow(
                                        app = app,
                                        onCheckNotifications = { onOpenAppNotificationSettings(app.packageName) },
                                    )
                                    if (idx < uiState.systemState.whatsAppApps.lastIndex) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            color = MaterialTheme.colorScheme.outline,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "recent_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Recent Message Log",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${uiState.recentMessages.size} latest",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                if (uiState.recentMessages.isEmpty()) {
                    item(key = "empty_log") {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "No Captured Message Details",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Incoming WhatsApp notifications will appear here with timestamps, sender details, and source app tags.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = uiState.recentMessages,
                        key = { it.id },
                    ) { msg ->
                        val chatTitle = uiState.chatLookup[msg.chatId]?.title ?: msg.senderName
                        RecentMessageDetailCard(
                            message = msg,
                            chatTitle = chatTitle,
                            onOpenChat = { onOpenChat(msg.chatId) },
                            onInspect = { inspectedMessage = msg },
                        )
                    }
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
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WhatsAppAppRow(
    app: WhatsAppAppStatus,
    onCheckNotifications: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (app.isInstalled) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (app.isInstalled) Icons.Filled.CheckCircle else Icons.Outlined.PhoneAndroid,
                        contentDescription = null,
                        tint = if (app.isInstalled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(
                        text = app.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = if (app.isInstalled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ) {
                Text(
                    text = if (app.isInstalled) "v${app.versionName}" else "Not Installed",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = if (app.isInstalled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        if (app.isInstalled) {
            TextButton(
                onClick = onCheckNotifications,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "  Check ${app.displayName} Notifications",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun RecentMessageDetailCard(
    message: MessageEntity,
    chatTitle: String,
    onOpenChat: () -> Unit,
    onInspect: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenChat),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
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

                IconButton(
                    onClick = onInspect,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "Inspect message metadata",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (message.isGroupMessage && !message.senderName.equals(chatTitle, ignoreCase = true)) {
                Text(
                    text = "From ${message.senderName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (message.messageType != MessageTypes.TEXT) {
                    MediaTypeBadge(messageType = message.messageType)
                }
                if (message.isViewOnce) {
                    ViewOnceBadge()
                }
                if (message.deletedInWhatsApp) {
                    DeletedPreservedBadge(label = "Deleted in WhatsApp • Saved")
                }
            }

            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = WhatsAppTimeFormatter.formatFullDateTime(message.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
