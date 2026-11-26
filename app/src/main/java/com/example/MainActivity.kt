package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.ChatReaderNavHost
import com.example.ui.MainViewModel
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOut
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.ChatReaderTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth

private data class PendingSharePayload(
    val uris: List<Uri>,
    val sharedText: String?,
)

class MainActivity : ComponentActivity() {
    private var pendingSharePayload by mutableStateOf<PendingSharePayload?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        captureShareIntent(intent)

        setContent {
            ChatReaderTheme {
                val auth = remember { Firebase.auth }
                val currentUser by auth.authStateFlow()
                    .collectAsStateWithLifecycle(initialValue = auth.currentUser)
                val scope = rememberCoroutineScope()
                val credentialManager = remember { CredentialManager.create(this@MainActivity) }

                LaunchedEffect(Unit) {
                    if (auth.currentUser == null) {
                        attemptAutoSignIn(
                            context = this@MainActivity,
                            credentialManager = credentialManager,
                            onAuthSuccess = {},
                            onUnauthenticated = {},
                            scope = scope,
                        )
                    }
                }

                val user = currentUser
                if (user == null) {
                    AuthScreen(onAuthSuccess = {})
                } else {
                    val uid = user.uid
                    key(uid) {
                        val mainViewModel: MainViewModel = viewModel(
                            key = uid,
                            factory = MainViewModel.factory(application, uid),
                        )

                        val payload = pendingSharePayload
                        LaunchedEffect(payload, uid) {
                            if (payload != null) {
                                mainViewModel.importDocuments(payload.uris, payload.sharedText)
                                pendingSharePayload = null
                            }
                        }

                        ChatReaderNavHost(
                            viewModel = mainViewModel,
                            userDisplayName = user.displayName,
                            userEmail = user.email,
                            onSignOut = {
                                signOut(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureShareIntent(intent)
    }

    private fun captureShareIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val streamUri = extractStreamUri(intent)
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                val uris = listOfNotNull(streamUri)
                if (uris.isNotEmpty() || !sharedText.isNullOrBlank()) {
                    pendingSharePayload = PendingSharePayload(uris, sharedText)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = extractMultipleStreamUris(intent)
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (uris.isNotEmpty() || !sharedText.isNullOrBlank()) {
                    pendingSharePayload = PendingSharePayload(uris, sharedText)
                }
            }
        }
    }

    private fun extractStreamUri(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun extractMultipleStreamUris(intent: Intent): List<Uri> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
        }
    }
}
