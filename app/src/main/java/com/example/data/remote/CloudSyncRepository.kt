package com.example.data.remote

import android.content.Context
import android.util.Base64
import androidx.annotation.Keep
import com.example.R
import com.example.data.local.AdminPromoConfig
import com.example.data.local.AdminPromoManager
import com.example.data.local.ChatDao
import com.example.data.local.ChatEntity
import com.example.data.local.MessageEntity
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

@Keep
@IgnoreExtraProperties
data class CloudChat(
    @DocumentId val id: String = "",
    val chatKey: String = "",
    val rawChatId: String = "",
    val userId: String = "",
    val title: String = "",
    val packageName: String = "",
    val appLabel: String = "",
    val groupChat: Boolean = false,
    val pinned: Boolean = false,
    val lastMessageText: String = "",
    val lastSenderName: String = "",
    val lastMessageType: String = "TEXT",
    val lastTimestamp: Long = 0L,
    val unreadCount: Long = 0L,
    val deletedCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class CloudMessage(
    @DocumentId val id: String = "",
    val userId: String = "",
    val chatKey: String = "",
    val rawChatId: String = "",
    val senderName: String = "",
    val body: String = "",
    val timestamp: Long = 0L,
    val packageName: String = "",
    val messageType: String = "TEXT",
    val deletedInWhatsApp: Boolean = false,
    val viewOnce: Boolean = false,
    val mediaDuration: String? = null,
    val dedupKey: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class CloudPromoDoc(
    @DocumentId val id: String = "",
    val promoText: String = "",
    val updatedBy: String = "",
    val promoFileName: String? = null,
    val promoFileMimeType: String? = null,
    val promoFileSizeBytes: Long? = null,
    val promoFileBase64: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

class CloudSyncRepository(
    private val db: FirebaseFirestore,
    private val chatDao: ChatDao,
    private val appContext: Context,
    private val auth: FirebaseAuth = Firebase.auth,
) {
    constructor(context: Context, chatDao: ChatDao) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id),
        ),
        chatDao = chatDao,
        appContext = context.applicationContext,
    )

    companion object {
        const val MAX_ONLINE_PROMO_FILE_BYTES = 524_288L // 512 KB so Base64 fits well under 1MB doc limit
        private const val MAX_INITIAL_MESSAGE_SYNC = 150
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun toSafeDocId(raw: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun ensureUserProfile(user: FirebaseUser): Result<Unit> {
        val uid = user.uid
        val path = "users/$uid"
        return try {
            val docRef = db.collection("users").document(uid)
            val displayName = (user.displayName ?: "ChatReader User")
                .trim()
                .ifBlank { "ChatReader User" }
                .take(120)
            val email = (user.email ?: "user@chatreader.app")
                .trim()
                .ifBlank { "user@chatreader.app" }
                .take(200)

            val snap = docRef.get().await()
            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "displayName" to displayName,
                        "email" to email,
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to displayName,
                        "email" to email,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeCloudPromo(): Flow<AdminPromoConfig?> = flow {
        requireUserId()
        val path = "app_config/promo"
        emitAll(
            db.collection("app_config").document("promo")
                .snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) return@map null
                    val doc = snapshot.toObject(
                        CloudPromoDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    ) ?: return@map null
                    AdminPromoManager.applyCloudPromo(
                        context = appContext,
                        promoText = doc.promoText,
                        fileName = doc.promoFileName,
                        mimeType = doc.promoFileMimeType,
                        fileSizeBytes = doc.promoFileSizeBytes,
                        fileBase64 = doc.promoFileBase64,
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser != null) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                },
        )
    }

    suspend fun publishAdminPromo(
        config: AdminPromoConfig,
        fileBytes: ByteArray?,
    ): Result<Unit> {
        val path = "app_config/promo"
        return try {
            val uid = requireUserId()
            if (fileBytes != null && fileBytes.size > MAX_ONLINE_PROMO_FILE_BYTES) {
                throw IllegalArgumentException(
                    "Online cloud sync supports promo files up to 512 KB (local share still works for any size).",
                )
            }
            val promoText = config.promoText.trim().take(2000)
            val base64Payload = fileBytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }?.take(720_000)
            val docRef = db.collection("app_config").document("promo")
            val exists = docRef.get().await().exists()

            val updateFields = mutableMapOf<String, Any>(
                "promoText" to promoText,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (fileBytes != null && !config.promoFileName.isNullOrBlank()) {
                updateFields["promoFileName"] = config.promoFileName.take(160)
                updateFields["promoFileMimeType"] = (config.promoFileMimeType ?: "application/octet-stream").take(120)
                updateFields["promoFileSizeBytes"] = fileBytes.size.toLong()
                if (base64Payload != null) {
                    updateFields["promoFileBase64"] = base64Payload
                }
            } else if (exists) {
                updateFields["promoFileName"] = FieldValue.delete()
                updateFields["promoFileMimeType"] = FieldValue.delete()
                updateFields["promoFileSizeBytes"] = FieldValue.delete()
                updateFields["promoFileBase64"] = FieldValue.delete()
            }

            if (exists) {
                docRef.update(updateFields).await()
            } else {
                val createPayload = updateFields + ("createdAt" to FieldValue.serverTimestamp())
                docRef.set(createPayload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeCloudChats(userId: String): Flow<List<CloudChat>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch for cloud chats." }
        val path = "users/$uid/chats"
        emitAll(
            db.collection("users").document(uid).collection("chats")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudChat::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                },
        )
    }

    fun observeCloudMessages(userId: String): Flow<List<CloudMessage>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch for cloud messages." }
        val path = "users/$uid/messages"
        emitAll(
            db.collection("users").document(uid).collection("messages")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudMessage::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                },
        )
    }

    suspend fun restoreCloudMessagesToLocal(
        cloudChats: List<CloudChat>,
        cloudMessages: List<CloudMessage>,
    ): Int {
        val chatLookup = cloudChats.associateBy { it.rawChatId }
        var restored = 0
        for (msg in cloudMessages.sortedBy { it.timestamp }) {
            val parentChat = chatLookup[msg.rawChatId]
            val inserted = chatDao.recordIncomingMessage(
                chatId = msg.rawChatId,
                chatTitle = parentChat?.title ?: msg.senderName,
                packageName = msg.packageName,
                appLabel = parentChat?.appLabel ?: if (msg.packageName == "com.whatsapp.w4b") "WA Business" else "WhatsApp",
                isGroup = parentChat?.groupChat ?: false,
                senderName = msg.senderName,
                body = msg.body,
                timestamp = msg.timestamp,
                notificationKey = "cloud_${msg.id}",
                dedupKey = msg.dedupKey,
                messageType = msg.messageType,
                mediaDuration = msg.mediaDuration,
                isViewOnce = msg.viewOnce,
                mediaFilePath = null,
                checkRecentDuplicate = false,
            )
            if (inserted) restored++
            if (msg.deletedInWhatsApp) {
                chatDao.markLatestMessageAsDeletedInWhatsApp(
                    chatId = msg.rawChatId,
                    notificationKey = "cloud_${msg.id}",
                    deletedAt = msg.timestamp,
                    chatTitle = parentChat?.title ?: msg.senderName,
                    packageName = msg.packageName,
                    appLabel = parentChat?.appLabel ?: "WhatsApp",
                    isGroup = parentChat?.groupChat ?: false,
                )
            }
        }
        return restored
    }

    suspend fun syncLocalChatToCloud(chat: ChatEntity): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/chats"
        return try {
            val uid = requireUserId()
            val chatKey = toSafeDocId(chat.chatId)
            val docRef = db.collection("users").document(uid).collection("chats").document(chatKey)
            path = docRef.path
            val existingSnap = docRef.get().await()

            val updateMap = mapOf(
                "title" to chat.title.ifBlank { "WhatsApp Chat" }.take(160),
                "packageName" to chat.packageName.ifBlank { "com.whatsapp" }.take(80),
                "appLabel" to chat.appLabel.ifBlank { "WhatsApp" }.take(60),
                "groupChat" to chat.isGroup,
                "pinned" to chat.isPinned,
                "lastMessageText" to chat.lastMessageText.take(2000),
                "lastSenderName" to chat.lastSenderName.take(120),
                "lastMessageType" to chat.lastMessageType.ifBlank { "TEXT" }.take(32),
                "lastTimestamp" to chat.lastTimestamp.coerceAtLeast(0L),
                "unreadCount" to chat.unreadCount.coerceAtLeast(0),
                "deletedCount" to chat.deletedCount.coerceAtLeast(0),
                "updatedAt" to FieldValue.serverTimestamp(),
            )

            if (existingSnap.exists()) {
                docRef.update(updateMap).await()
            } else {
                val createMap = updateMap + mapOf(
                    "chatKey" to chatKey,
                    "rawChatId" to chat.chatId.take(256),
                    "userId" to uid,
                    "createdAt" to FieldValue.serverTimestamp(),
                )
                docRef.set(createMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun syncLocalMessageToCloud(message: MessageEntity): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/messages"
        return try {
            val uid = requireUserId()
            val chatKey = toSafeDocId(message.chatId)
            val dedup = message.dedupKey.ifBlank { "${message.chatId}_${message.timestamp}_${message.id}" }.take(300)
            val messageId = toSafeDocId(dedup)
            val docRef = db.collection("users").document(uid).collection("messages").document(messageId)
            path = docRef.path
            val existingSnap = docRef.get().await()

            if (existingSnap.exists()) {
                val updateMap = mapOf<String, Any?>(
                    "body" to message.body.ifBlank { "Message" }.take(4000),
                    "deletedInWhatsApp" to message.deletedInWhatsApp,
                    "mediaDuration" to message.mediaDuration?.take(32),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null }
                docRef.update(updateMap).await()
            } else {
                val createMap = mapOf<String, Any?>(
                    "userId" to uid,
                    "chatKey" to chatKey,
                    "rawChatId" to message.chatId.take(256),
                    "senderName" to message.senderName.ifBlank { "WhatsApp Contact" }.take(120),
                    "body" to message.body.ifBlank { "Message" }.take(4000),
                    "timestamp" to message.timestamp.coerceAtLeast(0L),
                    "packageName" to message.packageName.ifBlank { "com.whatsapp" }.take(80),
                    "messageType" to message.messageType.ifBlank { "TEXT" }.take(32),
                    "deletedInWhatsApp" to message.deletedInWhatsApp,
                    "viewOnce" to message.isViewOnce,
                    "mediaDuration" to message.mediaDuration?.take(32),
                    "dedupKey" to dedup,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ).filterValues { it != null }
                docRef.set(createMap).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun syncLatestChatAndMessageToCloud(chatId: String): Result<Unit> {
        if (auth.currentUser?.uid.isNullOrBlank()) {
            return Result.success(Unit)
        }
        return try {
            val chat = chatDao.getChatOnce(chatId)
            if (chat != null) {
                syncLocalChatToCloud(chat).getOrThrow()
            }
            val latestMsg = chatDao.getLatestMessageForChat(chatId)
            if (latestMsg != null) {
                syncLocalMessageToCloud(latestMsg).getOrThrow()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncLocalSnapshotToCloud(
        chats: List<ChatEntity>,
        messages: List<MessageEntity>,
    ): Result<Int> {
        return try {
            requireUserId()
            var synced = 0
            for (chat in chats) {
                if (syncLocalChatToCloud(chat).isSuccess) synced++
            }
            for (msg in messages.take(MAX_INITIAL_MESSAGE_SYNC)) {
                if (syncLocalMessageToCloud(msg).isSuccess) synced++
            }
            Result.success(synced)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCloudChat(rawChatId: String): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}/chats"
        return try {
            val uid = requireUserId()
            val chatKey = toSafeDocId(rawChatId)
            val chatRef = db.collection("users").document(uid).collection("chats").document(chatKey)
            path = chatRef.path
            chatRef.delete().await()

            val msgSnap = db.collection("users").document(uid).collection("messages")
                .whereEqualTo("chatKey", chatKey)
                .get()
                .await()
            for (doc in msgSnap.documents) {
                doc.reference.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun clearAllCloudData(): Result<Unit> {
        var path = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            path = "users/$uid/chats"
            val chatsSnap = db.collection("users").document(uid).collection("chats").get().await()
            for (doc in chatsSnap.documents) {
                doc.reference.delete().await()
            }
            path = "users/$uid/messages"
            val msgsSnap = db.collection("users").document(uid).collection("messages").get().await()
            for (doc in msgsSnap.documents) {
                doc.reference.delete().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }
}
