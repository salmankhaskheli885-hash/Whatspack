package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.MessageEntity
import com.example.data.repository.MessageTypes
import com.example.ui.ChatDetailUiState
import com.example.ui.components.ContactAvatar
import com.example.ui.components.DeletedPreservedBadge
import com.example.ui.components.FullScreenMediaDialog
import com.example.ui.components.MediaTypeBadge
import com.example.ui.components.MessageMetadataSheet
import com.example.ui.components.ReadOnlyBadge
import com.example.ui.components.SavedMediaPreviewCard
import com.example.ui.components.VoiceMessageBubbleContent
import com.example.ui.components.WhatsAppTimeFormatter
import com.example.ui.components.avatarColorFor
import com.example.ui.components.copyTextToClipboard
import com.example.ui.theme.ChatWallpaperTintDark
import com.example.ui.theme.ChatWallpaperTintLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    uiState: ChatDetailUiState,
    onBack: () -> Unit,
    onMarkRead: () -> Unit,
    onDeleteChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val chat = uiState.chat
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var inspectedMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var fullScreenMediaPath by remember { mutableStateOf<String?>(null) }
    var showDeleteChatConfirm by remember { mutableStateOf(false) }

    val filteredMessages = remember(uiState.messages, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            uiState.messages
        } else {
            uiState.messages.filter {
                it.body.contains(q, ignoreCase = true) ||
                    it.senderName.contains(q, ignoreCase = true)
            }
        }
    }

    val listState = rememberLazyListState()

    LaunchedEffect(chat?.chatId, chat?.unreadCount) {
        if (chat != null && chat.unreadCount > 0) {
            onMarkRead()
        }
    }

    LaunchedEffect(filteredMessages.size) {
        if (filteredMessages.isNotEmpty() && searchQuery.isBlank()) {
            listState.scrollToItem(filteredMessages.lastIndex)
        }
    }

    val isDark = isSystemInDarkTheme()
    val wallpaperBg = if (isDark) ChatWallpaperTintDark else ChatWallpaperTintLight

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("chat_detail_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to chats",
                            )
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ContactAvatar(
                                title = chat?.title ?: "WhatsApp",
                                isGroup = chat?.isGroup ?: false,
                                isBusiness = chat?.packageName == "com.whatsapp.w4b",
                                size = 38.dp,
                            )
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    text = chat?.title ?: "Conversation",
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val subtitle = buildString {
                                    append(chat?.appLabel ?: "WhatsApp")
                                    if (chat?.isGroup == true) append(" • Group")
                                    append(" • ${uiState.messages.size} msgs")
                                }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                searchExpanded = !searchExpanded
                                if (!searchExpanded) searchQuery = ""
                            },
                            modifier = Modifier.testTag("chat_detail_search_button"),
                        ) {
                            Icon(
                                imageVector = if (searchExpanded) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = "Search inside chat",
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.testTag("chat_detail_more_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "More options",
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Copy entire chat") },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Filled.ContentCopy, contentDescription = null)
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        val transcript = uiState.messages.joinToString("\n") { msg ->
                                            "[${WhatsAppTimeFormatter.formatFullDateTime(msg.timestamp)}] ${msg.senderName}: ${msg.body}"
                                        }
                                        copyTextToClipboard(context, chat?.title ?: "Chat", transcript)
                                    },
                                )
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
                                        showDeleteChatConfirm = true
                                    },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )

                AnimatedVisibility(visible = searchExpanded) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Find messages in this chat...") },
                            singleLine = true,
                            shape = RoundedCornerShape(22.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        },
        bottomBar = {
            // Strict Read-Only Footer: No compose field or send button
            Surface(
                color = wallpaperBg,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("read_only_footer_bar"),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = stringResource(R.string.read_only_footer),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ReadOnlyBadge()
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(wallpaperBg),
            contentAlignment = Alignment.TopCenter,
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_chat_wallpaper),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (isDark) 0.08f else 0.45f),
            )

            if (filteredMessages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                "No messages match \"$searchQuery\""
                            } else {
                                "No messages in this conversation yet."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(
                        items = filteredMessages,
                        key = { _, msg -> msg.id },
                    ) { index, message ->
                        val dateLabel = WhatsAppTimeFormatter.formatDateDivider(message.timestamp)
                        val prevDateLabel = if (index > 0) {
                            WhatsAppTimeFormatter.formatDateDivider(filteredMessages[index - 1].timestamp)
                        } else {
                            null
                        }

                        if (dateLabel != prevDateLabel) {
                            DateDividerPill(
                                label = dateLabel,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                            )
                        }

                        IncomingMessageBubble(
                            message = message,
                            showSenderName = chat?.isGroup == true,
                            onClick = { inspectedMessage = message },
                            onOpenFullScreenMedia = { path -> fullScreenMediaPath = path },
                        )
                    }
                }
            }
        }
    }

    inspectedMessage?.let { msg ->
        MessageMetadataSheet(
            message = msg,
            chatTitle = chat?.title ?: msg.senderName,
            onDismiss = { inspectedMessage = null },
        )
    }

    fullScreenMediaPath?.let { path ->
        FullScreenMediaDialog(
            filePath = path,
            onDismiss = { fullScreenMediaPath = null },
        )
    }

    if (showDeleteChatConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteChatConfirm = false },
            title = { Text("Delete Saved Chat?") },
            text = {
                Text("This permanently deletes all saved messages and media for this chat from ChatReader. Saved records stay intact unless you confirm deletion.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteChatConfirm = false
                        onDeleteChat()
                        onBack()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteChatConfirm = false }) {
                    Text("Keep Saved")
                }
            },
        )
    }
}

@Composable
private fun DateDividerPill(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            tonalElevation = 1.dp,
            shadowElevation = 1.dp,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
    }
}

@Composable
private fun IncomingMessageBubble(
    message: MessageEntity,
    showSenderName: Boolean,
    onClick: () -> Unit,
    onOpenFullScreenMedia: (String) -> Unit,
) {
    val bubbleShape = RoundedCornerShape(
        topStart = 4.dp,
        topEnd = 16.dp,
        bottomEnd = 16.dp,
        bottomStart = 16.dp,
    )
    val bubbleColor = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            shadowElevation = 1.dp,
            modifier = Modifier
                .widthIn(min = 120.dp, max = 330.dp)
                .clickable(onClick = onClick)
                .testTag("message_bubble_${message.id}"),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showSenderName) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = avatarColorFor(message.senderName),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (message.deletedInWhatsApp) {
                    DeletedPreservedBadge(
                        label = "Deleted in WhatsApp • Saved here",
                        modifier = Modifier.testTag("deleted_preserved_badge_${message.id}"),
                    )
                }

                when {
                    message.messageType == MessageTypes.VOICE -> {
                        VoiceMessageBubbleContent(
                            duration = message.mediaDuration,
                            body = message.body,
                            modifier = Modifier.testTag("voice_message_bubble_${message.id}"),
                        )
                    }
                    message.mediaFilePath != null || message.isViewOnce -> {
                        SavedMediaPreviewCard(
                            filePath = message.mediaFilePath,
                            isViewOnce = message.isViewOnce,
                            messageType = message.messageType,
                            body = message.body,
                            onOpenFullScreen = onOpenFullScreenMedia,
                            modifier = Modifier.testTag("media_message_bubble_${message.id}"),
                        )
                    }
                    else -> {
                        if (message.messageType != MessageTypes.TEXT) {
                            MediaTypeBadge(messageType = message.messageType)
                        }
                        SelectionContainer {
                            Text(
                                text = message.body,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = message.appLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    )
                    Text(
                        text = WhatsAppTimeFormatter.formatBubbleTime(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
