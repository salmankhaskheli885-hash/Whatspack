package com.example.ui.screens

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.AdminPromoConfig
import com.example.data.local.AdminPromoManager
import com.example.util.AppShareHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPanelScreen(
    onBack: () -> Unit,
    onPublishOnlinePromo: suspend (AdminPromoConfig, ByteArray?) -> Result<Unit>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var promoConfig by remember { mutableStateOf(AdminPromoManager.loadConfig(context)) }
    var promoTextDraft by remember { mutableStateOf(promoConfig.promoText) }
    var isUploadingFile by remember { mutableStateOf(false) }
    var isSharingApk by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            isUploadingFile = true
            statusMessage = null
            coroutineScope.launch {
                try {
                    val updated = AdminPromoManager.savePromoFile(context, uri)
                    if (updated != null) {
                        promoConfig = updated
                        val bytes = AdminPromoManager.readPromoFileBytes(context)
                        val cloudResult = onPublishOnlinePromo(updated, bytes)
                        statusMessage = if (cloudResult.isSuccess) {
                            "File uploaded & online sync ho gayi (${updated.promoFileName})!"
                        } else {
                            "File local save ho gayi (${updated.promoFileName}). Online sync: ${cloudResult.exceptionOrNull()?.localizedMessage ?: "Admin Google account required"}"
                        }
                    } else {
                        statusMessage = "File read nahi ho saki. Dobara try karein."
                    }
                } finally {
                    isUploadingFile = false
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("admin_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Admin Panel",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
                    ) {
                        Text(
                            text = "Admin Mode",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primary,
                titleContentColor = MaterialTheme.colorScheme.onPrimary,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
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
                statusMessage?.let { msg ->
                    item(key = "admin_status_banner") {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = msg,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }

                item(key = "upload_file_card") {
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
                                        imageVector = Icons.Filled.UploadFile,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Upload a File",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "Koi bhi file (image, PDF, video, zip, APK) upload karein — app share karte waqt user ke sath forward hogi.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            if (isUploadingFile) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }

                            if (promoConfig.hasPromoFile) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AttachFile,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = promoConfig.promoFileName ?: "Uploaded File",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            val sizeLabel = Formatter.formatShortFileSize(
                                                context,
                                                promoConfig.promoFileSizeBytes,
                                            )
                                            Text(
                                                text = "$sizeLabel • ${promoConfig.promoFileMimeType ?: "File"}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val updated = AdminPromoManager.clearPromoFile(context)
                                                promoConfig = updated
                                                coroutineScope.launch {
                                                    val cloudResult = onPublishOnlinePromo(updated, null)
                                                    statusMessage = if (cloudResult.isSuccess) {
                                                        "Uploaded file online & local remove ho gayi."
                                                    } else {
                                                        "Local file remove ho gayi."
                                                    }
                                                }
                                            },
                                            modifier = Modifier.testTag("admin_remove_file_button"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = "Remove uploaded file",
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = "Abhi koi file upload nahi hai.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            Button(
                                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                                enabled = !isUploadingFile,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_upload_file_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.UploadFile,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (promoConfig.hasPromoFile) "Change Uploaded File" else "Upload a File")
                            }
                        }
                    }
                }

                item(key = "promo_text_card") {
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
                                        imageVector = Icons.Filled.Campaign,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Upload Promotional Text",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "Jo text aap yahan save karenge woh app share karne par user har kisi ko forward kar sakta hai.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = promoTextDraft,
                                onValueChange = { promoTextDraft = it },
                                label = { Text("Promotional Message") },
                                placeholder = {
                                    Text("e.g. Aap bhi kisi kism ka app banwa sakte ho — Dev Salman Khan: 03112765988")
                                },
                                minLines = 4,
                                maxLines = 8,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_promo_text_input"),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Button(
                                    onClick = {
                                        val updated = AdminPromoManager.savePromoText(context, promoTextDraft)
                                        promoConfig = updated
                                        promoTextDraft = updated.promoText
                                        coroutineScope.launch {
                                            val bytes = AdminPromoManager.readPromoFileBytes(context)
                                            val cloudResult = onPublishOnlinePromo(updated, bytes)
                                            statusMessage = if (cloudResult.isSuccess) {
                                                "Promotional text online sync ho gaya! Har user forward kar sakta hai."
                                            } else {
                                                "Promotional text local save ho gaya. Online sync: ${cloudResult.exceptionOrNull()?.localizedMessage ?: "Admin Google account required"}"
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("admin_save_promo_text_button"),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Save,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Save Text")
                                }

                                if (promoTextDraft.isNotBlank() || promoConfig.hasPromoText) {
                                    OutlinedButton(
                                        onClick = {
                                            promoTextDraft = ""
                                            val updated = AdminPromoManager.savePromoText(context, "")
                                            promoConfig = updated
                                            coroutineScope.launch {
                                                val bytes = AdminPromoManager.readPromoFileBytes(context)
                                                onPublishOnlinePromo(updated, bytes)
                                                statusMessage = "Promotional text remove ho gaya."
                                            }
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error,
                                        ),
                                        modifier = Modifier.testTag("admin_clear_promo_text_button"),
                                    ) {
                                        Text("Clear")
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "share_preview_card") {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "Forward Preview (User Share)",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = AppShareHelper.buildShareMessage(promoConfig),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = if (promoConfig.hasPromoFile) {
                                            "Attachments: ChatReader.apk + ${promoConfig.promoFileName}"
                                        } else {
                                            "Attachment: ChatReader.apk"
                                        },
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (!isSharingApk) {
                                        isSharingApk = true
                                        coroutineScope.launch {
                                            try {
                                                AppShareHelper.shareApkFile(context)
                                            } finally {
                                                isSharingApk = false
                                            }
                                        }
                                    }
                                },
                                enabled = !isSharingApk,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_share_all_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isSharingApk) "Preparing..." else "Forward App + Promo Now")
                            }

                            OutlinedButton(
                                onClick = { AppShareHelper.shareDirectDownloadLink(context) },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_share_link_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Forward Download Link + Promo")
                            }
                        }
                    }
                }
            }
        }
    }
}
