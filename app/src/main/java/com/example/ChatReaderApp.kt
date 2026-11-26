package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.remote.CloudSyncRepository
import com.example.data.repository.ChatRepository

class ChatReaderApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: ChatRepository by lazy { ChatRepository(database.chatDao()) }
    val cloudSyncRepository: CloudSyncRepository by lazy {
        CloudSyncRepository(this, database.chatDao())
    }
}
