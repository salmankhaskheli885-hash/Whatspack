package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LooksOne
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.R
import com.example.data.local.MessageEntity
import com.example.data.repository.MessageTypes
import com.example.ui.theme.AvatarPalette
import com.example.ui.theme.WarningAmber
import com.example.ui.theme.WarningContainerDark
import com.example.ui.theme.WarningContainerLight
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object WhatsAppTimeFormatter {
    fun formatChatListTime(timestamp: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }
        return when {
            isSameDay(now, target) ->
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
            isYesterday(now, target) -> "Yesterday"
            now.get(Calendar.YEAR) == target.get(Calendar.YEAR) ->
                SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(timestamp))
            else ->
                SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestamp))
        }
    }

    fun formatDateDivider(timestamp: Long): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = timestamp }
        return when {
            isSameDay(now, target) -> "Today"
            isYesterday(now, target) -> "Yesterday"
            else -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }

    fun formatBubbleTime(timestamp: Long): String {
        return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
    }

    fun formatFullDateTime(timestamp: Long): String {
        return SimpleDateFormat("dd MMM yyyy, h:mm:ss a", Locale.getDefault()).format(Date(timestamp))
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(now: Calendar, target: Calendar): Boolean {
        val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return isSameDay(yesterday, target)
    }
}

fun avatarColorFor(seed: String): Color {
    val index = abs(seed.hashCode()) % AvatarPalette.size
    return AvatarPalette[index]
}

fun initialsFor(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase(Locale.getDefault())
        else -> "${parts.first().first()}${parts.last().first()}".uppercase(Locale.getDefault())
    }
}

@Composable
fun ContactAvatar(
    title: String,
    isGroup: Boolean,
    isBusiness: Boolean,
    size: Dp = 50.dp,
    modifier: Modifier = Modifier,
) {
    val bgColor = avatarColorFor(title)
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(bgColor.copy(alpha = 0.16f))
                .border(1.5.dp, bgColor.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isGroup) {
                Icon(
                    imageVector = Icons.Filled.Groups,
                    contentDescription = "Group chat avatar",
                    tint = bgColor,
                    modifier = Modifier.size(size * 0.52f),
                )
            } else {
                Text(
                    text = initialsFor(title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                    ),
                    color = bgColor,
                    maxLines = 1,
                )
            }
        }

        if (isBusiness) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Business,
                    contentDescription = "WhatsApp Business",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

@Composable
fun ReadOnlyBadge(
    modifier: Modifier = Modifier,
    onDarkSurface: Boolean = false,
) {
    val bg = if (onDarkSurface) {
        Color.White.copy(alpha = 0.16f)
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val fg = if (onDarkSurface) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Visibility,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = stringResource(R.string.read_only_badge),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = fg,
            )
        }
    }
}

@Composable
fun DeletedPreservedBadge(
    label: String = "Deleted in WA • Saved",
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.red < 0.2f
    val bg = if (isDark) WarningContainerDark else WarningContainerLight

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                tint = WarningAmber,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = WarningAmber,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun VoiceMessageBubbleContent(
    duration: String?,
    body: String,
    mediaFilePath: String? = null,
    modifier: Modifier = Modifier,
) {
    val barHeights = listOf(8, 16, 12, 22, 18, 10, 24, 15, 20, 11, 17, 9, 21, 14, 10)
    val context = LocalContext.current
    val hasPlayableAudio = remember(mediaFilePath) {
        !mediaFilePath.isNullOrBlank() && File(mediaFilePath).exists()
    }
    var isPlaying by remember(mediaFilePath) { mutableStateOf(false) }
    var mediaPlayer by remember(mediaFilePath) { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(mediaFilePath) {
        onDispose {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
            mediaPlayer = null
            isPlaying = false
        }
    }

    val togglePlayback = {
        if (hasPlayableAudio && mediaFilePath != null) {
            if (isPlaying) {
                runCatching { mediaPlayer?.pause() }
                isPlaying = false
            } else {
                val player = mediaPlayer
                if (player != null) {
                    runCatching {
                        player.start()
                        isPlaying = true
                    }.onFailure {
                        Toast.makeText(context, "Unable to play audio", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    runCatching {
                        MediaPlayer().apply {
                            setDataSource(mediaFilePath)
                            setOnCompletionListener { isPlaying = false }
                            prepare()
                            start()
                            mediaPlayer = this
                            isPlaying = true
                        }
                    }.onFailure {
                        Toast.makeText(context, "Unsupported audio format on this device", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .then(if (hasPlayableAudio) Modifier.clickable { togglePlayback() } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when {
                            hasPlayableAudio && isPlaying -> Icons.Filled.Pause
                            hasPlayableAudio -> Icons.Filled.PlayArrow
                            else -> Icons.Filled.Mic
                        },
                        contentDescription = if (hasPlayableAudio) "Play voice note" else "Voice message",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (hasPlayableAudio) "Saved Voice Audio" else "Voice Message",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        if (!duration.isNullOrBlank()) {
                            Text(
                                text = duration,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        barHeights.forEach { heightVal ->
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(heightVal.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(
                                            alpha = if (isPlaying) 1f else 0.72f,
                                        )
                                    ),
                            )
                        }
                    }
                }
            }

            Text(
                text = if (hasPlayableAudio) {
                    "$body • Tap play to listen offline"
                } else {
                    "$body • Voice note detected"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun MediaTypeBadge(
    messageType: String,
    modifier: Modifier = Modifier,
) {
    val (icon, label) = when (messageType) {
        MessageTypes.VOICE -> Icons.Filled.Mic to "Voice"
        MessageTypes.IMAGE -> Icons.Filled.Image to "Photo"
        MessageTypes.VIDEO -> Icons.Filled.Videocam to "Video"
        MessageTypes.DOCUMENT -> Icons.Filled.Description to "Document"
        MessageTypes.CALL -> Icons.Filled.Call to "Call"
        else -> return
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
fun ViewOnceBadge(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.tertiaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.LooksOne,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = "View Once • Saved Permanently",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@Composable
fun SavedMediaPreviewCard(
    filePath: String?,
    isViewOnce: Boolean,
    messageType: String,
    body: String,
    duration: String? = null,
    onOpenFullScreen: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val fileExists = remember(filePath) {
        !filePath.isNullOrBlank() && File(filePath).exists()
    }
    val bitmap = remember(filePath, messageType, fileExists) {
        if (fileExists && messageType == MessageTypes.IMAGE && filePath != null) {
            runCatching { BitmapFactory.decodeFile(filePath) }.getOrNull()
        } else {
            null
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaTypeBadge(messageType = messageType)
            if (isViewOnce) {
                ViewOnceBadge()
            }
        }

        when {
            bitmap != null && filePath != null -> {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Saved WhatsApp media",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 240.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenFullScreen(filePath, MessageTypes.IMAGE) },
                )
                Text(
                    text = "$body • Tap to view anytime",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            fileExists && messageType == MessageTypes.VIDEO && filePath != null -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF131D24),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpenFullScreen(filePath, MessageTypes.VIDEO) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Play saved video",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                            Text(
                                text = if (!duration.isNullOrBlank()) "$body ($duration)" else body,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Text(
                    text = "Saved video • Tap to play offline",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            fileExists && messageType == MessageTypes.DOCUMENT && filePath != null -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { openSavedDocument(context, filePath) },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = "Open document",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = body,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "Saved locally • Tap to open",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            else -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (isViewOnce) {
                                "View Once record saved permanently here. Note: WhatsApp hides raw View Once photo/video files from system notifications unless a preview bitmap was included."
                            } else {
                                "Media notification saved permanently in ChatReader. Connect your WhatsApp Media folder in Permissions to import full files."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenMediaDialog(
    filePath: String,
    messageType: String = MessageTypes.IMAGE,
    onDismiss: () -> Unit,
) {
    val bitmap = remember(filePath, messageType) {
        if (messageType != MessageTypes.VIDEO) {
            runCatching { BitmapFactory.decodeFile(filePath) }.getOrNull()
        } else {
            null
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            if (messageType == MessageTypes.VIDEO) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val controller = MediaController(ctx)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setVideoPath(filePath)
                            setOnPreparedListener { mp ->
                                mp.isLooping = false
                                start()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Full screen saved media",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close viewer",
                    tint = Color.White,
                )
            }
        }
    }
}

private fun openSavedDocument(context: Context, filePath: String) {
    runCatching {
        val file = File(filePath)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open saved file"))
    }.onFailure {
        Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun PermissionBannerCard(
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.red < 0.2f
    val containerColor = if (isDark) WarningContainerDark else WarningContainerLight

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(WarningAmber.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.permission_required_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.permission_required_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(
                onClick = onGrantClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("banner_grant_permission_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.permission_grant_button))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageMetadataSheet(
    message: MessageEntity,
    chatTitle: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "WhatsApp Message Details",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ReadOnlyBadge()
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(14.dp),
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline)

            MetadataRow("Conversation", chatTitle)
            MetadataRow("Sender", message.senderName)
            MetadataRow("Message Type", message.messageType)
            MetadataRow("Source App", "${message.appLabel} (${message.packageName})")
            MetadataRow("Chat Type", if (message.isGroupMessage) "Group Chat" else "Direct Message")
            MetadataRow("Received At", WhatsAppTimeFormatter.formatFullDateTime(message.timestamp))
            if (message.isViewOnce) {
                MetadataRow("View Once", "Yes • Saved permanently locally")
            }
            if (message.deletedInWhatsApp) {
                val deletedTime = message.deletedAtTimestamp?.let {
                    " (${WhatsAppTimeFormatter.formatFullDateTime(it)})"
                }.orEmpty()
                MetadataRow("WhatsApp Status", "Deleted in WhatsApp • Saved locally$deletedTime")
            } else {
                MetadataRow("WhatsApp Status", "Active • Saved locally")
            }
            if (message.notificationKey.isNotBlank()) {
                MetadataRow("Notification Key", message.notificationKey)
            }

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(
                onClick = {
                    copyTextToClipboard(context, message.senderName, message.body)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("copy_message_button"),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Copy Message Text")
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.62f),
        )
    }
}

fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}
