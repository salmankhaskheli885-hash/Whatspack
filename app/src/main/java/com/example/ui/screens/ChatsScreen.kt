package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MarkChatRead
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ChatEntity
import com.example.ui.ChatFilter
import com.example.ui.ChatsUiState
import com.example.ui.components.ContactAvatar
import com.example.ui.components.DeletedPreservedBadge
import com.example.ui.components.PermissionBannerCard
import com.example.ui.components.ReadOnlyBadge
import com.example.ui.components.WhatsAppTimeFormatter
import com.example.ui.theme.UnreadBadgeGreen
import com.example.ui.theme.UnreadBadgeText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    uiState: ChatsUiState,
    onOpenChat: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSelectFilter: (ChatFilter) -> Unit,
    onGrantPermissionClick: () -> Unit,
    onOpenPermissionTab: () -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onMarkRead: (String) -> Unit,
    onDeleteChat: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchExpanded by remember { mutableStateOf(false) }

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
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    ReadOnlyBadge(onDarkSurface = true)
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        searchExpanded = !searchExpanded
                        if (!searchExpanded) onSearchQueryChange("")
                    },
                    modifier = Modifier.testTag("chats_search_toggle"),
                ) {
                    Icon(
                        imageVector = if (searchExpanded) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = "Search chats",
                    )
                }
                IconButton(
                    onClick = onOpenPermissionTab,
                    modifier = Modifier.testTag("chats_permission_status_icon"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.VerifiedUser,
                        contentDescription = "Permission status",
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )

        AnimatedVisibility(visible = searchExpanded) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search contacts, groups, or messages...") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = MaterialTheme.colorScheme.surface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.surface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("chats_search_input"),
                )
            }
        }

        // Filter Chips row (WhatsApp style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChatFilter.entries.forEach { filter ->
                val selected = uiState.selectedFilter == filter
                FilterChip(
                    selected = selected,
                    onClick = { onSelectFilter(filter) },
                    label = {
                        val labelText = if (filter == ChatFilter.UNREAD && uiState.totalUnreadCount > 0) {
                            "${filter.label} (${uiState.totalUnreadCount})"
                        } else {
                            filter.label
                        }
                        Text(labelText, style = MaterialTheme.typography.labelMedium)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = Modifier.testTag("filter_chip_${filter.name.lowercase()}"),
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
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (!uiState.systemState.isNotificationAccessGranted) {
                    item(key = "permission_banner") {
                        PermissionBannerCard(
                            onGrantClick = onGrantPermissionClick,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }

                if (uiState.chats.isEmpty()) {
                    item(key = "empty_state") {
                        EmptyChatsState(
                            hasAnyChats = uiState.totalChatCount > 0,
                            permissionGranted = uiState.systemState.isNotificationAccessGranted,
                            onGrantPermissionClick = onGrantPermissionClick,
                            onResetFilter = {
                                onSearchQueryChange("")
                                onSelectFilter(ChatFilter.ALL)
                            },
                        )
                    }
                } else {
                    items(
                        items = uiState.chats,
                        key = { it.chatId },
                    ) { chat ->
                        WhatsAppChatRow(
                            chat = chat,
                            onClick = { onOpenChat(chat.chatId) },
                            onTogglePin = { onTogglePin(chat.chatId, chat.isPinned) },
                            onMarkRead = { onMarkRead(chat.chatId) },
                            onDelete = { onDeleteChat(chat.chatId) },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 82.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WhatsAppChatRow(
    chat: ChatEntity,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onMarkRead: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
            .testTag("chat_row_${chat.chatId}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(
            title = chat.title,
            isGroup = chat.isGroup,
            isBusiness = chat.packageName == "com.whatsapp.w4b",
            size = 52.dp,
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = chat.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (chat.isPinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = WhatsAppTimeFormatter.formatChatListTime(chat.lastTimestamp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (chat.unreadCount > 0) FontWeight.Bold else FontWeight.Medium,
                    ),
                    color = if (chat.unreadCount > 0) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val preview = if (chat.isGroup && chat.lastSenderName.isNotBlank() &&
                    !chat.lastSenderName.equals(chat.title, ignoreCase = true)
                ) {
                    "${chat.lastSenderName}: ${chat.lastMessageText}"
                } else {
                    chat.lastMessageText
                }

                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (chat.deletedCount > 0) {
                        DeletedPreservedBadge(
                            label = if (chat.deletedCount == 1) "1 deleted saved" else "${chat.deletedCount} deleted saved",
                        )
                    }
                    if (chat.unreadCount > 0) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(UnreadBadgeGreen)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (chat.unreadCount > 99) "99+" else chat.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = UnreadBadgeText,
                            )
                        }
                    }
                }
            }
        }

        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.testTag("chat_menu_${chat.chatId}"),
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Chat actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text(if (chat.isPinned) "Unpin chat" else "Pin chat") },
                    leadingIcon = {
                        Icon(
                            imageVector = if (chat.isPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onTogglePin()
                    },
                )
                if (chat.unreadCount > 0) {
                    DropdownMenuItem(
                        text = { Text("Mark as read") },
                        leadingIcon = {
                            Icon(imageVector = Icons.Filled.MarkChatRead, contentDescription = null)
                        },
                        onClick = {
                            menuExpanded = false
                            onMarkRead()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete saved chat") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyChatsState(
    hasAnyChats: Boolean,
    permissionGranted: Boolean,
    onGrantPermissionClick: () -> Unit,
    onResetFilter: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_chats),
            contentDescription = "No captured WhatsApp chats yet",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(20.dp)),
        )

        Text(
            text = if (hasAnyChats) {
                "No Matching Chats"
            } else {
                stringResource(R.string.empty_chats_title)
            },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )

        Text(
            text = when {
                hasAnyChats -> "Try clearing your search query or switching back to the All filter."
                permissionGranted -> stringResource(R.string.empty_chats_desc_granted)
                else -> stringResource(R.string.empty_chats_desc_denied)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        if (hasAnyChats) {
            Button(
                onClick = onResetFilter,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Show All Chats")
            }
        } else if (!permissionGranted) {
            Button(
                onClick = onGrantPermissionClick,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("empty_grant_permission_button"),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Allow WhatsApp Notification Access")
            }
        }
    }
}
