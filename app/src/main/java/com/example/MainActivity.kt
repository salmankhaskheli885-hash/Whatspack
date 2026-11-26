package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.ChatReaderNavHost
import com.example.ui.MainViewModel
import com.example.ui.theme.ChatReaderTheme

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingShareIntent(intent)
        setContent {
            ChatReaderTheme {
                ChatReaderNavHost(viewModel = mainViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val streamUri = extractStreamUri(intent)
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                val uris = listOfNotNull(streamUri)
                if (uris.isNotEmpty() || !sharedText.isNullOrBlank()) {
                    mainViewModel.importDocuments(uris, sharedText)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = extractMultipleStreamUris(intent)
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (uris.isNotEmpty() || !sharedText.isNullOrBlank()) {
                    mainViewModel.importDocuments(uris, sharedText)
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
