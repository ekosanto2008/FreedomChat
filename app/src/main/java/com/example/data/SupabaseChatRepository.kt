package com.example.data

import android.util.Log
import com.example.MyApplication
import com.example.data.local.MessageEntity
import com.example.data.local.UserProfileEntity
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.PresenceAction
import io.github.jan.supabase.realtime.Presence
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
data class UpdateReadStatusRequest(
    @SerialName("is_read") val isRead: Boolean = true
)

class SupabaseChatRepository : ChatRepository {
    private val TAG = "SupabaseChatRepo"

    private val _realtimeMessageFlow = kotlinx.coroutines.flow.MutableSharedFlow<Message>()
    override val realtimeMessageFlow: Flow<Message> = _realtimeMessageFlow.asSharedFlow()

    private var globalPresenceChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    companion object {
        var activeConversationId: String? = null
    }

    private var globalListenerJob: kotlinx.coroutines.Job? = null
    private var isRealtimeStarted = false

    override suspend fun startGlobalMessageListener(currentUserId: String) {
        if (isRealtimeStarted) {
            Log.d(TAG, "[Repository] startGlobalMessageListener already started, ignoring duplicate request.")
            return
        }
        Log.d(TAG, "[Repository] startGlobalMessageListener called for user: $currentUserId")
        isRealtimeStarted = true
        globalListenerJob?.cancel()
        
        globalListenerJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Uncaught exception in globalListenerJob scope", throwable)
        }).launch {
            try {
                if (!SupabaseService.isConfigured) return@launch
                val client = SupabaseService.client
                try {
                    client.realtime.connect()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed connecting realtime on startGlobalMessageListener: ${e.message}")
                }
                
                // Keep the channel uniquely named per user to avoid multi-user conflicts or socket channel name clashes
                val globalChannel = client.realtime.channel("global_messages_listener_${currentUserId}")
                
                val insertFlow = globalChannel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "messages"
                }
                
                val insertJob = launch {
                    try {
                        insertFlow.collect { action ->
                            Log.d("SupabaseRealtime", "Global message insert caught: $action")
                            val newMessage = try {
                                action.decodeRecord<Message>()
                            } catch (ex: Exception) {
                                Json.decodeFromString<Message>(action.record.toString())
                            }
                            
                            // 1. Cek siapa pengirimnya dan apakah room sedang dibuka
                            val isMyOwnMessage = newMessage.senderId == currentUserId
                            val isRoomActive = activeConversationId == newMessage.conversationId

                            // 2. Tentukan status baca dengan logika yang benar
                            val messageToSave = if (!isMyOwnMessage && isRoomActive) {
                                // A. Pesan dari ORANG LAIN & kita sedang BUKA ROOM -> Tandai sudah kita baca (Centang 2)
                                newMessage.copy(isRead = true, status = "read")
                            } else {
                                // B. Pesan KITA SENDIRI, atau pesan orang lain tapi room TERTUTUP -> Biarkan status aslinya (Centang 1 / False)
                                newMessage.copy(isRead = newMessage.isRead) 
                            }
                            
                            try {
                                MyApplication.database.messageDao().insertMessage(messageToSave.toEntity())
                                Log.d("SupabaseRealtime", "Cached global real-time insert message: text='${newMessage.text}', isRead=${messageToSave.isRead}")
                            } catch (e: Exception) {
                                Log.e("SupabaseRealtime", "Error caching global real-time message insert to Room DB", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SupabaseRealtime", "Global insert collector exception", e)
                    }
                }
                
                val updateFlow = globalChannel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                    table = "messages"
                }
                
                val updateJob = launch {
                    try {
                        updateFlow.collect { action ->
                            Log.d("SupabaseRealtime", "Global message update caught: $action")
                            val updatedMessage = try {
                                action.decodeRecord<Message>()
                            } catch (ex: Exception) {
                                Json.decodeFromString<Message>(action.record.toString())
                            }
                            
                            // 1. Cek siapa pengirimnya dan apakah room sedang dibuka
                            val isMyOwnMessage = updatedMessage.senderId == currentUserId
                            val isRoomActive = activeConversationId == updatedMessage.conversationId

                            // 2. Tentukan status baca dengan logika yang benar
                            val messageToSave = if (!isMyOwnMessage && isRoomActive) {
                                updatedMessage.copy(isRead = true, status = "read")
                            } else {
                                updatedMessage.copy(isRead = updatedMessage.isRead)
                            }
                            
                            try {
                                MyApplication.database.messageDao().insertMessage(messageToSave.toEntity())
                                Log.d("SupabaseRealtime", "Cached global real-time update message")
                            } catch (e: Exception) {
                                Log.e("SupabaseRealtime", "Error caching global real-time message update to Room DB", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SupabaseRealtime", "Global update collector exception", e)
                    }
                }
                
                try {
                    globalChannel.subscribe()
                    Log.d(TAG, "Global Realtime Channel for messages subscribed successfully for user $currentUserId.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed subscribing global message channel: ${e.message}")
                }
                
                // Auto cleanup on cancel/finish
                try {
                    kotlinx.coroutines.awaitCancellation()
                } finally {
                    insertJob.cancel()
                    updateJob.cancel()
                    try {
                        globalChannel.unsubscribe()
                    } catch (e: Exception) {}
                    Log.d(TAG, "Global message channel cleaned up successfully.")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in global listener flow", e)
            }
        }
    }

    private fun getIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    override suspend fun signUp(email: String, password: String, username: String): Result<UserProfile> = runCatching {
        val client = SupabaseService.client
        Log.d(TAG, "Signing up user with email: $email and username: $username")
        
        val authResult = client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        
        val userId = client.auth.currentUserOrNull()?.id 
            ?: throw IllegalStateException("Registrasi sukses, harap verifikasi email atau login.")

        val profile = UserProfile(
            id = userId,
            username = username,
            email = email
        )
        
        try {
            val dbProfile = DbUserProfile(
                id = userId,
                username = username,
                avatarUrl = null,
                fcmToken = null
            )
            client.from("profiles").insert(dbProfile)
            Log.d(TAG, "User profile successfully created in public.profiles")
        } catch (e: Exception) {
            Log.w(TAG, "Could not insert profile to public.profiles table: $profile", e)
        }

        // Cache profile locally
        try {
            MyApplication.database.userProfileDao().insertProfile(profile.toEntity())
        } catch (e: Exception) {
            Log.w(TAG, "Failed caching profile locally: ${e.message}")
        }

        profile
    }

    override suspend fun signIn(email: String, password: String): Result<UserProfile> = runCatching {
        val client = SupabaseService.client
        Log.d(TAG, "Signing in user with email: $email")
        
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }

        val authUser = client.auth.currentUserOrNull() 
            ?: throw IllegalStateException("Sign in failed. No user found.")

        // Retrieve profile from network
        val userProfileResult = try {
            val remote = client.from("profiles")
                .select {
                    filter {
                        eq("id", authUser.id)
                    }
                }
                .decodeSingle<DbUserProfile>()
            UserProfile(
                id = remote.id,
                username = remote.username,
                email = authUser.email ?: "",
                avatarUrl = remote.avatarUrl,
                fcmToken = remote.fcmToken
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch profile from DB. Using fallback profile.", e)
            UserProfile(id = authUser.id, username = authUser.email?.substringBefore("@") ?: "User", email = authUser.email ?: "")
        }

        // Cache locally to Room
        try {
            MyApplication.database.userProfileDao().insertProfile(userProfileResult.toEntity())
        } catch (e: Exception) {
            Log.w(TAG, "Failed caching profile locally: ${e.message}")
        }

        userProfileResult
    }

    override suspend fun signOut(): Result<Unit> = runCatching {
        val client = SupabaseService.client
        Log.d(TAG, "Signing out current session")
        client.auth.signOut()
        
        // Clear Room database on logout to protect data privacy
        try {
            MyApplication.database.messageDao().clearAll()
            MyApplication.database.userProfileDao().clearAll()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing local cache during logout", e)
        }
    }

    override suspend fun getCurrentUser(): UserProfile? {
        val client = SupabaseService.client
        val authUser = client.auth.currentUserOrNull() ?: return null

        // Try getting remote profile from public.profiles table first to keep it always updated
        try {
            val remote = client.from("profiles")
                .select {
                    filter {
                        eq("id", authUser.id)
                    }
                }
                .decodeSingle<DbUserProfile>()
            
            val remoteWithEmail = UserProfile(
                id = remote.id,
                username = remote.username,
                email = authUser.email ?: "",
                avatarUrl = remote.avatarUrl,
                fcmToken = remote.fcmToken
            )
            // Cache locally (updates Room DB)
            MyApplication.database.userProfileDao().insertProfile(remoteWithEmail.toEntity())
            Log.d(TAG, "getCurrentUser: Remote profile successfully fetched and cached locally: $remoteWithEmail")
            return remoteWithEmail
        } catch (e: Exception) {
            Log.w(TAG, "getCurrentUser: failed to load user profile from remote. Attempting local cache fallback.", e)
            
            // Try fallback to local cache
            val cached = try {
                MyApplication.database.userProfileDao().getProfile(authUser.id)
            } catch (ec: Exception) {
                null
            }
            if (cached != null) {
                Log.d(TAG, "getCurrentUser: Returning cached profile fallback: $cached")
                return cached.toDomain()
            }
            
            val fallback = UserProfile(id = authUser.id, username = authUser.email?.substringBefore("@") ?: "User", email = authUser.email ?: "")
            return fallback
        }
    }

    override suspend fun updateProfile(username: String, avatarUrl: String?, fcmToken: String?): Result<UserProfile> = runCatching {
        val client = SupabaseService.client
        val userId = client.auth.currentUserOrNull()?.id ?: throw IllegalStateException("Not authenticated")
        
        val current = getCurrentUser() ?: UserProfile(id = userId, username = username, email = "")
        val updated = current.copy(
            username = username,
            avatarUrl = avatarUrl ?: current.avatarUrl,
            fcmToken = fcmToken ?: current.fcmToken
        )
        
        val payload = buildJsonObject {
            put("id", updated.id)
            put("username", updated.username)
            if (updated.avatarUrl != null) {
                put("avatar_url", updated.avatarUrl)
            } else {
                put("avatar_url", null as String?)
            }
            if (updated.fcmToken != null) {
                put("fcm_token", updated.fcmToken)
            } else {
                put("fcm_token", null as String?)
            }
        }
        
        // c. WAJIB LAKUKAN UPDATE KE TABEL PROFILES:
        client.from("profiles").update(payload) {
            filter {
                eq("id", userId)
            }
        }
        
        // d. Hanya jika langkah (c) sukses, barulah perbarui database Room/SQLite lokal.
        MyApplication.database.userProfileDao().insertProfile(updated.toEntity())
        
        updated
    }

    override suspend fun getContacts(): List<UserProfile> {
        val client = SupabaseService.client
        val currentUserId = client.auth.currentUserOrNull()?.id ?: return emptyList()

        try {
            // Fetch contact relations from network
            val rels1 = try {
                client.from("contacts")
                    .select { filter { eq("user_id_1", currentUserId) } }
                    .decodeList<Contact>()
            } catch (e: Exception) { emptyList() }

            val rels2 = try {
                client.from("contacts")
                    .select { filter { eq("user_id_2", currentUserId) } }
                    .decodeList<Contact>()
            } catch (e: Exception) { emptyList() }

            val friendIds = (rels1.map { it.userId2 } + rels2.map { it.userId1 }).distinct()
            
            val remoteList = if (friendIds.isEmpty()) {
                client.from("profiles")
                    .select()
                    .decodeList<DbUserProfile>()
                    .filter { it.id != currentUserId }
                    .map { dbp ->
                        UserProfile(
                            id = dbp.id,
                            username = dbp.username,
                            email = "",
                            avatarUrl = dbp.avatarUrl,
                            fcmToken = dbp.fcmToken
                        )
                    }
            } else {
                friendIds.mapNotNull { friendId ->
                    try {
                        val dbp = client.from("profiles")
                            .select { filter { eq("id", friendId) } }
                            .decodeSingleOrNull<DbUserProfile>()
                        if (dbp != null) {
                            UserProfile(
                                id = dbp.id,
                                username = dbp.username,
                                email = "",
                                avatarUrl = dbp.avatarUrl,
                                fcmToken = dbp.fcmToken
                            )
                        } else null
                    } catch (e: Exception) { null }
                }
            }

            if (remoteList.isNotEmpty()) {
                MyApplication.database.userProfileDao().insertProfiles(remoteList.map { it.toEntity() })
                return remoteList
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network contacts fetch failed, falling back to local cache", e)
        }

        // Fallback to local Room cache
        return try {
            MyApplication.database.userProfileDao().getAllContactsDirect(currentUserId).map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun addContact(username: String): Result<UserProfile> = runCatching {
        val client = SupabaseService.client
        val currentUserId = client.auth.currentUserOrNull()?.id ?: throw IllegalStateException("Not authenticated")
        
        val found = client.from("profiles")
            .select {
                filter {
                    eq("username", username)
                }
            }
            .decodeList<DbUserProfile>()
        
        val dbUser = found.firstOrNull() ?: throw NoSuchElementException("User '$username' tidak ditemukan.")
        val targetUser = UserProfile(
            id = dbUser.id,
            username = dbUser.username,
            email = "",
            avatarUrl = dbUser.avatarUrl,
            fcmToken = dbUser.fcmToken
        )
        if (targetUser.id == currentUserId) {
            throw IllegalArgumentException("Tidak dapat menambahkan diri sendiri.")
        }

        // Add to contacts relation table
        val relation = Contact(
            userId1 = currentUserId,
            userId2 = targetUser.id,
            createdAt = getIsoTimestamp()
        )
        try {
            client.from("contacts").insert(relation)
            Log.d(TAG, "Contact relationship added successfully to contacts relation table.")
        } catch (e: Exception) {
            Log.w(TAG, "Insert contact details error (might exist already): ${e.message}")
        }

        // Sync target details to Room Database
        try {
            MyApplication.database.userProfileDao().insertProfile(targetUser.toEntity())
        } catch (e: Exception) {
            Log.w(TAG, "Failed caching added contact details Locally: ${e.message}")
        }

        targetUser
    }

    override suspend fun getOrCreateConversation(contactUserId: String): String {
        val client = SupabaseService.client
        val currentUserId = client.auth.currentUserOrNull()?.id ?: throw IllegalStateException("Not authenticated")

        return try {
            val conv1 = client.from("conversations")
                .select {
                    filter {
                        eq("user1_id", currentUserId)
                        eq("user2_id", contactUserId)
                    }
                }
                .decodeList<Conversation>()

            if (conv1.isNotEmpty()) {
                return conv1.first().id
            }

            val conv2 = client.from("conversations")
                .select {
                    filter {
                        eq("user1_id", contactUserId)
                        eq("user2_id", currentUserId)
                    }
                }
                .decodeList<Conversation>()

            if (conv2.isNotEmpty()) {
                return conv2.first().id
            }

            val newId = java.util.UUID.randomUUID().toString()
            val newConv = Conversation(
                id = newId,
                user1Id = currentUserId,
                user2Id = contactUserId,
                createdAt = getIsoTimestamp()
            )
            client.from("conversations").insert(newConv)
            newId
        } catch (e: Exception) {
            Log.w(TAG, "Conversations query failed, using deterministic composite ID fallback", e)
            val sorted = listOf(currentUserId, contactUserId).sorted()
            "room_${sorted[0]}_${sorted[1]}"
        }
    }

    override fun getMessages(conversationId: String): Flow<List<Message>> = channelFlow {
        activeConversationId = conversationId

        // Exposable flow mapping from local cache (Single Source of Truth)
        val localJob = launch {
            MyApplication.database.messageDao()
                .getMessagesForConversation(conversationId)
                .map { entities -> entities.map { it.toDomain() } }
                .collect { list ->
                    send(list)
                }
        }

        // Launch historical network fetch
        val client = SupabaseService.client
        launch {
            try {
                Log.d(TAG, "Syncing history from Supabase for $conversationId")
                val initial = client.from("messages")
                    .select {
                        filter {
                            eq("conversation_id", conversationId)
                        }
                    }
                    .decodeList<Message>()
                
                // Save immediately to Room cache
                MyApplication.database.messageDao().insertMessages(initial.map { it.toEntity() })
            } catch (e: Exception) {
                Log.e(TAG, "Failed retrieving historical messages", e)
            }
        }

        // Setup WebSocket realtime observer
        var activeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null
        try {
            val token = client.auth.currentSessionOrNull()?.accessToken
            if (token != null) {
                Log.d(TAG, "Auth Token found: Ready to connect realtime socket transparently.")
            }
            client.realtime.connect()
            
            // 2. GUNAKAN POLA CHANNEL.SUBSCRIBE/JOIN MUTLAK
            val chatChannel = client.realtime.channel("chat_${conversationId}")
            activeChannel = chatChannel
            
            // 1. Setup Listener
            val changeFlow = chatChannel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "messages"
                filter("conversation_id", FilterOperator.EQ, conversationId)
            }

            // 2. Collect Data
            launch {
                changeFlow.collect { action ->
                    Log.d("SupabaseRealtime", "Pesan Masuk (Insert): $action")
                    val newMessage = try {
                        action.decodeRecord<Message>()
                    } catch (ex: Exception) {
                        Json.decodeFromString<Message>(action.record.toString())
                    }
                    
                    if (newMessage.conversationId == conversationId) {
                        val currentUserId = client.auth.currentUserOrNull()?.id ?: ""
                        val isMyOwnMessage = newMessage.senderId == currentUserId
                        val isRoomActive = activeConversationId == conversationId
                        
                        val messageToSave = if (!isMyOwnMessage && isRoomActive) {
                            newMessage.copy(isRead = true, status = "read")
                        } else {
                            newMessage.copy(isRead = newMessage.isRead)
                        }

                        // Insert to Room cache
                        try {
                            MyApplication.database.messageDao().insertMessage(messageToSave.toEntity())
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to cache incoming realtime message to local Room: ${e.message}")
                        }
                        
                        // Emit for immediate UI updates (Bypass Room cache reloading delays)
                        _realtimeMessageFlow.emit(messageToSave)
                        
                        // Trigger notification if not active in this room
                        if (!isRoomActive) {
                            triggerForegroundNotification(messageToSave)
                        }
                    }
                }
            }

            val updateFlow = chatChannel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "messages"
                filter("conversation_id", FilterOperator.EQ, conversationId)
            }
            launch {
                updateFlow.collect { action ->
                    Log.d("SupabaseRealtime", "Pesan Update Masuk: $action")
                    val updatedMessage = try {
                        action.decodeRecord<Message>()
                    } catch (ex: Exception) {
                        Json.decodeFromString<Message>(action.record.toString())
                    }
                    if (updatedMessage.conversationId == conversationId) {
                        val currentUserId = client.auth.currentUserOrNull()?.id ?: ""
                        val isMyOwnMessage = updatedMessage.senderId == currentUserId
                        val isRoomActive = activeConversationId == conversationId
                        
                        val messageToSave = if (!isMyOwnMessage && isRoomActive) {
                            updatedMessage.copy(isRead = true, status = "read")
                        } else {
                            updatedMessage.copy(isRead = updatedMessage.isRead)
                        }
                        try {
                            MyApplication.database.messageDao().insertMessage(messageToSave.toEntity())
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to update local Room cache: ${e.message}")
                        }
                    }
                }
            }

            // 4. WAJIB DI-SUBSCRIBE/JOIN (Pada Supabase-kt, fungsi ini bernama subscribe())
            launch {
                try {
                    chatChannel.subscribe()
                    Log.d(TAG, "Joined/Subscribed successfully to realtime updates for conversation: $conversationId")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed subscribing to realtime channel chat_${conversationId}", e)
                }
            }
        } catch (realEx: Exception) {
            Log.e(TAG, "Realtime background watcher exception", realEx)
        }

        awaitClose {
            Log.d(TAG, "getMessages flow closed for $conversationId - cleaner unsubscribing active Channel.")
            localJob.cancel()
            if (activeConversationId == conversationId) {
                activeConversationId = null
            }
            launch {
                try {
                    activeChannel?.unsubscribe()
                } catch (e: Exception) {
                    Log.w(TAG, "Error unsubscribing channel: ${e.message}")
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun triggerForegroundNotification(message: Message) {
        try {
            val context = MyApplication.instance
            val sharedPref = context.getSharedPreferences("settings_pref", android.content.Context.MODE_PRIVATE)
            val isEnabled = sharedPref.getBoolean("notifications_enabled", true)
            if (!isEnabled) {
                Log.d(TAG, "[Repository] Notifications disabled in preferences. Skipping notification.")
                return
            }

            val ringtoneUriStr = sharedPref.getString("ringtone_uri", "") ?: ""
            val ringtoneUriString = if (ringtoneUriStr.isNotEmpty()) ringtoneUriStr else null

            // C. PLAY MANUAL NADA DERING MENGGUNAKAN MEDIAPLAYER (ANTI-GAGAL)
            try {
                val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
                
                // Pastikan hanya berbunyi jika HP TIDAK SEDANG di-Silent atau Vibrate
                if (audioManager.ringerMode == android.media.AudioManager.RINGER_MODE_NORMAL) {
                    
                    val uri = if (ringtoneUriString != null) {
                        android.net.Uri.parse(ringtoneUriString)
                    } else {
                        android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                    }
                    
                    val mediaPlayer = android.media.MediaPlayer().apply {
                        setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT) // Atribut prioritas chat
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build()
                        )
                        setDataSource(context, uri)
                        prepare()
                        start()
                        
                        // Bersihkan memori secara otomatis setelah lagu selesai
                        setOnCompletionListener {
                            it.release()
                        }
                    }
                } else {
                    Log.d("Notification", "HP sedang Silent/Vibrate, suara dilewati.")
                }
            } catch (e: Exception) {
                Log.e("Notification", "MediaPlayer gagal memutar suara: ${e.message}")
            }

            val channelId = "chat_enterprise_channel"
            val notificationId = message.hashCode()

            val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId,
                    "Freedom Messages",
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Freedom chat messages notification"
                    setSound(null, null)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra("conversationId", message.conversationId)
                putExtra("senderId", message.senderId)
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Pesan Realtime Baru")
                .setContentText(message.text)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            notificationManager.notify(notificationId, builder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed constructing system notification", e)
        }
    }

    override suspend fun sendPrivateMessage(conversationId: String, senderId: String, text: String): Result<Unit> {
        return sendPrivateMessage(conversationId, senderId, text, null)
    }

    override suspend fun sendPrivateMessage(
        conversationId: String,
        senderId: String,
        text: String,
        imageUrl: String?
    ): Result<Unit> = runCatching {
        val client = SupabaseService.client
        val now = getIsoTimestamp()
        val nextId = java.util.UUID.randomUUID().toString()
        val message = Message(
            id = nextId,
            senderId = senderId,
            text = text,
            timestamp = now,
            conversationId = conversationId,
            imageUrl = imageUrl,
            status = "sent"
        )

        // 1. Cek siapa pengirimnya dan apakah room sedang dibuka
        val isMyOwnMessage = message.senderId == senderId
        val isRoomActive = activeConversationId == message.conversationId

        // 2. Tentukan status baca dengan logika yang benar
        val toSave = if (!isMyOwnMessage && isRoomActive) {
            // A. Pesan dari ORANG LAIN & kita sedang BUKA ROOM -> Tandai sudah kita baca (Centang 2)
            message.copy(isRead = true, status = "read")
        } else {
            // B. Pesan KITA SENDIRI, atau pesan orang lain tapi room TERTUTUP -> Biarkan status aslinya (Centang 1 / False)
            message.copy(isRead = message.isRead) 
        }

        // 3. Simpan ke database lokal
        try {
            MyApplication.database.messageDao().insertMessage(toSave.toEntity())
        } catch (e: Exception) {
            Log.w(TAG, "Could not cache message locally first: ${e.message}")
        }

        // Send to remote
        client.from("messages").insert(message)
    }

    override suspend fun markMessagesAsRead(conversationId: String, currentUserId: String): Result<Unit> = runCatching {
        // 1. Update lokal (Room DB) agar UI langsung bersih
        val localRows = MyApplication.database.messageDao().markAsReadLocal(conversationId, currentUserId)
        Log.d("MarkRead", "Room lokal berhasil update: $localRows baris")

        // 2. Update server menggunakan Raw JSON Object (Bypass error Data Class)
        val client = SupabaseService.client
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // Gunakan buildJsonObject persis seperti di fungsi updateProfile
                val payload = buildJsonObject {
                    put("is_read", true)
                }
                
                client.from("messages").update(payload) {
                    filter {
                        eq("conversation_id", conversationId)
                        neq("sender_id", currentUserId)
                        eq("is_read", false)
                    }
                }
                Log.d("SupabaseUpdate", "AKHIRNYA SUKSES UPDATE SERVER DENGAN JSON OBJECT!")
            } catch (e: Exception) {
                Log.e("SupabaseUpdate", "MASIH ERROR SERVER: ${e.message}")
            }
        }
    }

    override suspend fun syncMessages(conversationId: String): Result<Unit> = runCatching {
        val client = SupabaseService.client
        Log.d(TAG, "Syncing history from Supabase for $conversationId")
        try {
            val initial = client.from("messages")
                .select {
                    filter {
                        eq("conversation_id", conversationId)
                    }
                }
                .decodeList<Message>()
            
            // Save immediately to Room cache
            MyApplication.database.messageDao().insertMessages(initial.map { it.toEntity() })
        } catch (e: Exception) {
            Log.e(TAG, "Failed retrieving historical messages for background sync", e)
        }
    }

    override fun getLastMessage(conversationId: String): Flow<Message?> {
        return MyApplication.database.messageDao()
            .getLastMessageForConversation(conversationId)
            .map { it?.toDomain() }
    }

    override fun getUnreadCount(conversationId: String, currentUserId: String): Flow<Int> {
        return MyApplication.database.messageDao()
            .getUnreadCountForConversation(conversationId, currentUserId)
    }

    override suspend fun setTypingStatus(conversationId: String, userId: String, isTyping: Boolean) {
        try {
            val client = SupabaseService.client
            val cleanConvId = conversationId.replace("-", "_")
            val channel = client.realtime.channel("chan_room_$cleanConvId")
            
            channel.broadcast(
                event = "typing",
                message = buildJsonObject {
                    put("userId", userId)
                    put("isTyping", isTyping)
                }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed broadcasting typing status: ${e.message}")
        }
    }

    override fun observePresence(conversationId: String): Flow<List<UserPresence>> = channelFlow {
        val client = SupabaseService.client
        val cleanConvId = conversationId.replace("-", "_")
        val channel = client.realtime.channel("chan_room_$cleanConvId")
        val globalChannel = client.realtime.channel("global_presence")
        
        val activePresenceMap = mutableMapOf<String, UserPresence>()
        
        fun emitStatus() {
            val list = activePresenceMap.values.toList()
            launch {
                try {
                    send(list)
                } catch (e: Exception) {
                    // channel already closed
                }
            }
        }

        val myId = client.auth.currentUserOrNull()?.id ?: "unknown"

        val pingJob = launch {
            while (isActive) {
                try {
                    channel.broadcast(
                        event = "online_ping",
                        message = buildJsonObject {
                            put("userId", myId)
                        }
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Presence ping failed: ${e.message}")
                }
                delay(4000)
            }
        }

        val typingFlow = channel.broadcastFlow<TypingEvent>(event = "typing")
        val pingFlow = channel.broadcastFlow<PingEvent>(event = "online_ping")

        val typingJob = launch {
            try {
                typingFlow.collect { event ->
                    val cur = activePresenceMap[event.userId] ?: UserPresence(userId = event.userId, isOnline = true, isTyping = false)
                    activePresenceMap[event.userId] = cur.copy(isTyping = event.isTyping, isOnline = true, lastSeen = System.currentTimeMillis())
                    emitStatus()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Typing real-time collector failed", e)
            }
        }

        val pingJobCollector = launch {
            try {
                pingFlow.collect { event ->
                    val cur = activePresenceMap[event.userId] ?: UserPresence(userId = event.userId, isOnline = true, isTyping = false)
                    activePresenceMap[event.userId] = cur.copy(isOnline = true, lastSeen = System.currentTimeMillis())
                    emitStatus()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ping real-time collector failed", e)
            }
        }

        val globalPresenceJob = launch {
            try {
                globalChannel.presenceChangeFlow().collect { action ->
                    action.joins.forEach { (userId, presence) ->
                        val isOnline = try {
                            val stateObj = presence.state
                            val element = stateObj["is_online"] ?: stateObj["isOnline"]
                            val primitive = element as? kotlinx.serialization.json.JsonPrimitive
                            primitive?.content?.toBooleanStrictOrNull() ?: true
                        } catch (e: Exception) {
                            true
                        }
                        val cur = activePresenceMap[userId] ?: UserPresence(userId = userId, isOnline = isOnline, isTyping = false)
                        activePresenceMap[userId] = cur.copy(isOnline = isOnline, lastSeen = System.currentTimeMillis())
                    }
                    action.leaves.forEach { (userId, _) ->
                        if (userId != "ai" && userId != myId) {
                            val cur = activePresenceMap[userId]
                            if (cur != null) {
                                activePresenceMap[userId] = cur.copy(isOnline = false, isTyping = false)
                            }
                        }
                    }
                    emitStatus()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Global presence flow collection exception", e)
            }
        }

        val roomCleaner = launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                var changed = false
                activePresenceMap.forEach { (id, presence) ->
                    if (now - presence.lastSeen > 10000 && presence.isOnline) {
                        activePresenceMap[id] = presence.copy(isOnline = false, isTyping = false)
                        changed = true
                    }
                }
                if (changed) {
                    emitStatus()
                }
                delay(5000)
            }
        }

        try {
            channel.subscribe()
            globalChannel.subscribe()
        } catch (e: Exception) {
            Log.e(TAG, "Presence channel subscribe failed", e)
        }

        awaitClose {
            pingJob.cancel()
            typingJob.cancel()
            pingJobCollector.cancel()
            globalPresenceJob.cancel()
            roomCleaner.cancel()
            launch {
                try {
                    channel.unsubscribe()
                } catch (e: Exception) {
                    Log.w(TAG, "Error unsubscribing channel: ${e.message}")
                }
                try {
                    globalChannel.unsubscribe()
                } catch (e: Exception) {
                    Log.w(TAG, "Error unsubscribing global presence: ${e.message}")
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun trackGlobalPresence(isOnline: Boolean) {
        try {
            val client = SupabaseService.client
            val myId = client.auth.currentUserOrNull()?.id ?: return
            
            if (globalPresenceChannel == null) {
                val chan = client.realtime.channel("global_presence")
                chan.subscribe() // Join/subscribe first is mandatory in Supabase realtime!
                globalPresenceChannel = chan
            }
            
            if (isOnline) {
                globalPresenceChannel?.subscribe()
                globalPresenceChannel?.track(buildJsonObject {
                    put("is_online", true)
                })
                Log.d(TAG, "Tracked global presence: online for $myId")
            } else {
                globalPresenceChannel?.untrack()
                Log.d(TAG, "Untracked/Offline global presence for $myId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed tracking global presence in repo", e)
        }
    }

    override suspend fun uploadAvatar(userId: String, bytes: ByteArray, mimeType: String): Result<String> = runCatching {
        val client = SupabaseService.client
        val path = "avatar_$userId.jpg"
        val bucket = client.storage.from("avatars")
        bucket.upload(path, bytes) {
            upsert = true
        }
        bucket.publicUrl(path)
    }

    override suspend fun uploadAttachment(bytes: ByteArray, fileName: String, mimeType: String): Result<String> = runCatching {
        val client = SupabaseService.client
        val path = "${java.util.UUID.randomUUID()}_$fileName"
        val bucket = client.storage.from("chat_attachments")
        bucket.upload(path, bytes) {
            upsert = true
        }
        bucket.publicUrl(path)
    }

    override fun getMessages(): Flow<List<Message>> {
        return getMessages("lobby")
    }

    override suspend fun editMessage(messageId: String, newText: String): Result<Unit> = runCatching {
        val client = SupabaseService.client
        val payload = buildJsonObject {
            put("text", newText)
        }
        client.from("messages").update(payload) {
            filter {
                eq("id", messageId)
            }
        }
        MyApplication.database.messageDao().updateMessageText(messageId, newText)
    }

    override suspend fun deleteMessage(messageId: String): Result<Unit> = runCatching {
        val client = SupabaseService.client
        client.from("messages").delete {
            filter {
                eq("id", messageId)
            }
        }
        MyApplication.database.messageDao().deleteMessageById(messageId)
    }

    override suspend fun clearConversationMessages(conversationId: String): Result<Unit> = runCatching {
        val client = SupabaseService.client
        client.from("messages").delete {
            filter {
                eq("conversation_id", conversationId)
            }
        }
        MyApplication.database.messageDao().deleteMessagesByConversation(conversationId)
    }

    override suspend fun sendMessage(senderId: String, text: String): Result<Unit> {
        return sendPrivateMessage("lobby", senderId, text)
    }
}

// Serializable payloads for realtime presence broadcasts
@kotlinx.serialization.Serializable
data class TypingEvent(
    @kotlinx.serialization.SerialName("userId") val userId: String,
    @kotlinx.serialization.SerialName("isTyping") val isTyping: Boolean
)

@kotlinx.serialization.Serializable
data class PingEvent(
    @kotlinx.serialization.SerialName("userId") val userId: String
)

private fun MessageEntity.toDomain() = Message(
    id = id,
    senderId = senderId,
    text = text,
    timestamp = timestamp,
    conversationId = conversationId,
    imageUrl = imageUrl,
    status = status,
    isRead = isRead
)

private fun Message.toEntity() = MessageEntity(
    id = id ?: java.util.UUID.randomUUID().toString(),
    senderId = senderId,
    text = text,
    timestamp = timestamp,
    conversationId = conversationId,
    imageUrl = imageUrl,
    status = status,
    isRead = isRead
)

private fun UserProfileEntity.toDomain() = UserProfile(
    id = id,
    username = username,
    email = email,
    avatarUrl = avatarUrl,
    fcmToken = fcmToken
)

private fun UserProfile.toEntity() = UserProfileEntity(
    id = id,
    username = username,
    email = email,
    avatarUrl = avatarUrl,
    fcmToken = fcmToken
)
