package com.example.ui

import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.ui.screens.ChatDetailScreen
import com.example.ui.screens.ChatsScreen
import com.example.ui.screens.DetailsScreen
import com.example.ui.screens.MediaScreen
import com.example.ui.screens.PermissionScreen

object AppRoutes {
    const val ROUTE_CHATS = "chats"
    const val ROUTE_MEDIA = "media"
    const val ROUTE_DETAILS = "details"
    const val ROUTE_PERMISSION = "permission"
    const val ROUTE_CHAT_DETAIL = "chat_detail/{chatId}"

    fun chatDetail(chatId: String): String = "chat_detail/${Uri.encode(chatId)}"
}

private data class TopDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
)

private val TOP_DESTINATIONS = listOf(
    TopDestination(
        route = AppRoutes.ROUTE_CHATS,
        labelRes = R.string.nav_chats,
        selectedIcon = Icons.Filled.Chat,
        unselectedIcon = Icons.Outlined.ChatBubbleOutline,
        testTag = "nav_tab_chats",
    ),
    TopDestination(
        route = AppRoutes.ROUTE_MEDIA,
        labelRes = R.string.nav_media,
        selectedIcon = Icons.Filled.PhotoLibrary,
        unselectedIcon = Icons.Outlined.PhotoLibrary,
        testTag = "nav_tab_media",
    ),
    TopDestination(
        route = AppRoutes.ROUTE_DETAILS,
        labelRes = R.string.nav_details,
        selectedIcon = Icons.Filled.Analytics,
        unselectedIcon = Icons.Outlined.Analytics,
        testTag = "nav_tab_details",
    ),
    TopDestination(
        route = AppRoutes.ROUTE_PERMISSION,
        labelRes = R.string.nav_permission,
        selectedIcon = Icons.Filled.VerifiedUser,
        unselectedIcon = Icons.Outlined.VerifiedUser,
        testTag = "nav_tab_permission",
    ),
)

@Composable
fun ChatReaderNavHost(
    viewModel: MainViewModel = viewModel(),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route

    val chatsUiState by viewModel.chatsUiState.collectAsStateWithLifecycle()
    val mediaUiState by viewModel.mediaUiState.collectAsStateWithLifecycle()
    val detailsUiState by viewModel.detailsUiState.collectAsStateWithLifecycle()
    val systemState by viewModel.systemState.collectAsStateWithLifecycle()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null) {
            viewModel.importFromTreeUri(treeUri)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importDocuments(uris)
        }
    }

    val launchFolderPicker = {
        val initialUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:Android/media/com.whatsapp/WhatsApp/Media",
                )
            }.getOrNull()
        } else {
            null
        }
        folderPickerLauncher.launch(initialUri)
    }

    val launchFilePicker = {
        filePickerLauncher.launch(
            arrayOf(
                "text/plain",
                "application/zip",
                "application/json",
                "image/*",
                "video/*",
                "audio/*",
                "application/pdf",
            )
        )
    }

    val backupCreateLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { targetUri ->
        if (targetUri != null) {
            viewModel.exportBackupToUri(targetUri)
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshSystemStatus()
        onPauseOrDispose {}
    }

    val showTopLevelChrome = currentRoute in setOf(
        AppRoutes.ROUTE_CHATS,
        AppRoutes.ROUTE_MEDIA,
        AppRoutes.ROUTE_DETAILS,
        AppRoutes.ROUTE_PERMISSION,
    )

    val navigateToTopLevel: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showTopLevelChrome && !useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ) {
                        TOP_DESTINATIONS.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == dest.route
                            } == true

                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(dest.route) },
                                icon = {
                                    DestinationIcon(
                                        dest = dest,
                                        selected = selected,
                                        unreadCount = chatsUiState.totalUnreadCount,
                                        permissionGranted = systemState.isNotificationAccessGranted,
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }
            },
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                if (showTopLevelChrome && useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ) {
                        TOP_DESTINATIONS.forEach { dest ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == dest.route
                            } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTopLevel(dest.route) },
                                icon = {
                                    DestinationIcon(
                                        dest = dest,
                                        selected = selected,
                                        unreadCount = chatsUiState.totalUnreadCount,
                                        permissionGranted = systemState.isNotificationAccessGranted,
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag),
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = AppRoutes.ROUTE_CHATS,
                    modifier = Modifier.weight(1f),
                ) {
                    composable(AppRoutes.ROUTE_CHATS) {
                        ChatsScreen(
                            uiState = chatsUiState,
                            onOpenChat = { chatId ->
                                navController.navigate(AppRoutes.chatDetail(chatId))
                            },
                            onSearchQueryChange = viewModel::updateSearchQuery,
                            onSelectFilter = viewModel::selectFilter,
                            onGrantPermissionClick = viewModel::openNotificationSettings,
                            onOpenPermissionTab = {
                                navigateToTopLevel(AppRoutes.ROUTE_PERMISSION)
                            },
                            onVoicePermissionChanged = viewModel::refreshSystemStatus,
                            onImportFolderClick = launchFolderPicker,
                            onSyncFolderClick = viewModel::syncConnectedFolder,
                            onImportFilesClick = launchFilePicker,
                            onDismissImportMessage = viewModel::clearImportStatusMessage,
                            onTogglePin = viewModel::togglePin,
                            onMarkRead = viewModel::markChatAsRead,
                            onDeleteChat = viewModel::deleteChat,
                        )
                    }

                    composable(AppRoutes.ROUTE_MEDIA) {
                        MediaScreen(
                            uiState = mediaUiState,
                            onSelectFilter = viewModel::selectMediaFilter,
                            onOpenChat = { chatId ->
                                navController.navigate(AppRoutes.chatDetail(chatId))
                            },
                            onConnectFolderClick = launchFolderPicker,
                            onSyncFolderClick = viewModel::syncConnectedFolder,
                            onImportFilesClick = launchFilePicker,
                        )
                    }

                    composable(AppRoutes.ROUTE_DETAILS) {
                        DetailsScreen(
                            uiState = detailsUiState,
                            onOpenChat = { chatId ->
                                navController.navigate(AppRoutes.chatDetail(chatId))
                            },
                            onOpenAppNotificationSettings = viewModel::openWhatsAppNotificationSettings,
                        )
                    }

                    composable(AppRoutes.ROUTE_PERMISSION) {
                        PermissionScreen(
                            systemState = systemState,
                            onOpenNotificationSettings = viewModel::openNotificationSettings,
                            onVoicePermissionChanged = viewModel::refreshSystemStatus,
                            onOpenAppNotificationSettings = viewModel::openWhatsAppNotificationSettings,
                            onOpenOwnAppDetails = viewModel::openOwnAppDetailsSettings,
                            onOpenBatterySettings = viewModel::openBatteryOptimizationSettings,
                            onConnectWhatsAppFolder = launchFolderPicker,
                            onSyncWhatsAppFolder = viewModel::syncConnectedFolder,
                            onImportChatOrMediaFiles = launchFilePicker,
                            onSaveBackupToStorage = { backupCreateLauncher.launch("ChatReader_Backup.json") },
                            onClearAllData = viewModel::clearAllData,
                        )
                    }

                    composable(
                        route = AppRoutes.ROUTE_CHAT_DETAIL,
                        arguments = listOf(
                            navArgument("chatId") { type = NavType.StringType },
                        ),
                    ) { entry ->
                        val chatId = entry.arguments?.getString("chatId").orEmpty()
                        val detailState by remember(chatId) {
                            viewModel.observeChatDetail(chatId)
                        }.collectAsStateWithLifecycle(
                            initialValue = ChatDetailUiState(
                                isNotificationAccessGranted = systemState.isNotificationAccessGranted,
                            ),
                        )

                        ChatDetailScreen(
                            uiState = detailState,
                            onBack = { navController.popBackStack() },
                            onMarkRead = { viewModel.markChatAsRead(chatId) },
                            onDeleteChat = { viewModel.deleteChat(chatId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DestinationIcon(
    dest: TopDestination,
    selected: Boolean,
    unreadCount: Int,
    permissionGranted: Boolean,
) {
    val iconVector = if (selected) dest.selectedIcon else dest.unselectedIcon
    when {
        dest.route == AppRoutes.ROUTE_CHATS && unreadCount > 0 -> {
            BadgedBox(
                badge = {
                    Badge {
                        Text(if (unreadCount > 99) "99+" else unreadCount.toString())
                    }
                },
            ) {
                Icon(imageVector = iconVector, contentDescription = null)
            }
        }
        dest.route == AppRoutes.ROUTE_PERMISSION && !permissionGranted -> {
            BadgedBox(
                badge = { Badge() },
            ) {
                Icon(imageVector = iconVector, contentDescription = null)
            }
        }
        else -> {
            Icon(imageVector = iconVector, contentDescription = null)
        }
    }
}
