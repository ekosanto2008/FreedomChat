package com.example.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChatRepository
import com.example.data.Message
import com.example.data.MockChatRepository
import com.example.data.SupabaseChatRepository
import com.example.data.SupabaseService
import com.example.data.UserProfile
import com.example.data.UserPresence
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch

sealed interface AuthState {
    object Idle : AuthState
    object Loading : AuthState
    data class Authenticated(val user: UserProfile) : AuthState
    object Unauthenticated : AuthState
    data class Error(val message: String) : AuthState
}

sealed interface ContactsState {
    object Loading : ContactsState
    data class Success(val contacts: List<UserProfile>) : ContactsState
    data class Error(val message: String) : ContactsState
}

sealed interface ChatState {
    object Idle : ChatState
    object Loading : ChatState
    data class Success(val messages: List<Message>) : ChatState
    data class Error(val message: String) : ChatState
}

class ChatViewModel : ViewModel() {
    private val TAG = "ChatViewModel"

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _contactsState = MutableStateFlow<ContactsState>(ContactsState.Loading)
    val contactsState: StateFlow<ContactsState> = _contactsState.asStateFlow()

    private val _chatState = MutableStateFlow<ChatState>(ChatState.Idle)
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    private val _isSupabaseMode = MutableStateFlow(false)
    val isSupabaseMode: StateFlow<Boolean> = _isSupabaseMode.asStateFlow()

    private var currentRepo: ChatRepository = MockChatRepository()
    private var collectMessagesJob: Job? = null
    private var collectRealtimeMessagesJob: Job? = null
    private var presenceJob: Job? = null

    private val _activeRoomId = MutableStateFlow<String?>(null)
    val activeRoomId: StateFlow<String?> = _activeRoomId.asStateFlow()

    private val _activeContact = MutableStateFlow<UserProfile?>(null)
    val activeContact: StateFlow<UserProfile?> = _activeContact.asStateFlow()

    private val _navigateToChatEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToChatEvent = _navigateToChatEvent.asSharedFlow()

    private val _presenceList = MutableStateFlow<List<UserPresence>>(emptyList())
    val presenceList: StateFlow<List<UserPresence>> = _presenceList.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _conversationIds = MutableStateFlow<Map<String, String>>(emptyMap())
    val conversationIds: StateFlow<Map<String, String>> = _conversationIds.asStateFlow()

    fun getLastMessageFlow(conversationId: String): Flow<Message?> {
        return currentRepo.getLastMessage(conversationId)
    }

    fun getUnreadCountFlow(conversationId: String?): Flow<Int> {
        if (conversationId == null) return kotlinx.coroutines.flow.flowOf(0)
        val currentUserId = getCurrentUserId()
        return currentRepo.getUnreadCount(conversationId, currentUserId)
    }

    fun getCurrentUserId(): String {
        return when (val auth = _authState.value) {
            is AuthState.Authenticated -> auth.user.id
            else -> "demo"
        }
    }

    fun markRoomAsRead(conversationId: String) = viewModelScope.launch {
        val currentUserId = getCurrentUserId()
        try {
            currentRepo.markMessagesAsRead(conversationId, currentUserId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to mark room messages as read: ${e.message}")
        }
    }

    fun markMessagesAsRead(conversationId: String) {
        markRoomAsRead(conversationId)
    }

    private val _snackbarEvent = MutableStateFlow<Pair<String, String>?>(null)
    val snackbarEvent: StateFlow<Pair<String, String>?> = _snackbarEvent.asStateFlow()

    fun showSnackbar(message: String, type: String = "success") {
        _snackbarEvent.value = Pair(message, type)
    }

    fun clearSnackbar() {
        _snackbarEvent.value = null
    }

    init {
        initializeRepository()
    }

    fun initializeRepository() {
        collectMessagesJob?.cancel()
        presenceJob?.cancel()
        _chatState.value = ChatState.Idle
        _authState.value = AuthState.Idle
        _presenceList.value = emptyList()

        if (SupabaseService.isConfigured) {
            Log.d(TAG, "Initializing Supabase Chat Repository...")
            currentRepo = SupabaseChatRepository()
            _isSupabaseMode.value = true
            
            // Explicitly connect to realtime web socket during initial repository setup
            viewModelScope.launch {
                try {
                    SupabaseService.client.realtime.connect()
                    Log.d(TAG, "Successfully connected to Supabase Realtime explicitly on startup.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed connecting explicitly to Supabase Realtime on startup", e)
                }
            }
        } else {
            Log.d(TAG, "Initializing Demo Mock Chat Repository...")
            currentRepo = MockChatRepository()
            _isSupabaseMode.value = false
        }

        checkCurrentUser()
    }

    private fun checkCurrentUser() = viewModelScope.launch {
        _authState.value = AuthState.Loading
        try {
            if (_isSupabaseMode.value) {
                val client = SupabaseService.client
                client.auth.sessionStatus.collect { status ->
                    Log.d(TAG, "Observed Auth SessionStatus: $status")
                    when (status) {
                        is SessionStatus.Authenticated -> {
                            val user = currentRepo.getCurrentUser()
                            if (user != null) {
                                _authState.value = AuthState.Authenticated(user)
                                loadContacts()
                                syncFcmTokenAndStore()
                                
                                // Explicitly ensure we are connected to Realtime upon successful authentication check
                                launch {
                                    try {
                                        client.realtime.connect()
                                        Log.d(TAG, "Successfully connected/verified Supabase Realtime session check.")
                                        currentRepo.startGlobalMessageListener(user.id)
                                    } catch (e: java.lang.Exception) {
                                        Log.e(TAG, "Failed ensuring realtime connection on auth check status", e)
                                    }
                                }
                            } else {
                                _authState.value = AuthState.Unauthenticated
                            }
                        }
                        is SessionStatus.NotAuthenticated -> {
                            _authState.value = AuthState.Unauthenticated
                        }
                        else -> {
                            // Still initializing or loading
                            _authState.value = AuthState.Loading
                        }
                    }
                }
            } else {
                val user = currentRepo.getCurrentUser()
                if (user != null) {
                    _authState.value = AuthState.Authenticated(user)
                    loadContacts()
                    syncFcmTokenAndStore()
                } else {
                    _authState.value = AuthState.Unauthenticated
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Check user failed", e)
            _authState.value = AuthState.Unauthenticated
        }
    }

    fun signUp(email: String, password: String, username: String, onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) = viewModelScope.launch {
        _authState.value = AuthState.Loading
        val res = currentRepo.signUp(email.trim(), password.trim(), username.trim())
        if (res.isSuccess) {
            val profile = res.getOrThrow()
            _authState.value = AuthState.Authenticated(profile)
            loadContacts()
            syncFcmTokenAndStore()
            
            // Explicitly connect/reconnect to Realtime WebSocket upon successful sign up
            launch {
                try {
                    SupabaseService.client.realtime.connect()
                    Log.d(TAG, "Explicitly connected to Supabase Realtime after Sign Up.")
                    currentRepo.startGlobalMessageListener(profile.id)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed explicit Realtime connection after Sign Up", e)
                }
            }
            onSuccess()
        } else {
            val err = res.exceptionOrNull()?.message ?: "Gagal registrasi"
            _authState.value = AuthState.Error(err)
            onFailure(err)
        }
    }

    fun signIn(email: String, password: String, onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) = viewModelScope.launch {
        _authState.value = AuthState.Loading
        val res = currentRepo.signIn(email.trim(), password.trim())
        if (res.isSuccess) {
            val profile = res.getOrThrow()
            _authState.value = AuthState.Authenticated(profile)
            loadContacts()
            syncFcmTokenAndStore()
            
            // Explicitly connect/reconnect to Realtime WebSocket upon successful sign in
            launch {
                try {
                    SupabaseService.client.realtime.connect()
                    Log.d(TAG, "Explicitly connected to Supabase Realtime after Sign In.")
                    currentRepo.startGlobalMessageListener(profile.id)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed explicit Realtime connection after Sign In", e)
                }
            }
            onSuccess()
        } else {
            val err = res.exceptionOrNull()?.message ?: "Gagal masuk"
            _authState.value = AuthState.Error(err)
            onFailure(err)
        }
    }

    fun signOut(onSuccess: () -> Unit = {}) = viewModelScope.launch {
        try {
            currentRepo.signOut()
        } catch (e: Exception) {
            Log.e(TAG, "SignOut error", e)
        }
        _authState.value = AuthState.Unauthenticated
        _activeRoomId.value = null
        _activeContact.value = null
        _chatState.value = ChatState.Idle
        _presenceList.value = emptyList()
        collectMessagesJob?.cancel()
        presenceJob?.cancel()
        onSuccess()
    }

    fun loadContacts() = viewModelScope.launch {
        _contactsState.value = ContactsState.Loading
        try {
            val list = currentRepo.getContacts()
            _contactsState.value = ContactsState.Success(list)
            
            // Resolve conversation IDs for existing contacts asynchronously
            launch {
                val newMap = mutableMapOf<String, String>()
                list.forEach { contact ->
                    try {
                        val convId = currentRepo.getOrCreateConversation(contact.id)
                        newMap[contact.id] = convId
                        // Pull message history on startup to populate lastMessage and unreadCounts
                        launch {
                            currentRepo.syncMessages(convId)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load conversation ID/sync for ${contact.username}", e)
                    }
                }
                _conversationIds.value = newMap
            }
        } catch (e: Exception) {
            _contactsState.value = ContactsState.Error(e.message ?: "Gagal memuat kontak.")
        }
    }

    fun addContactByUsername(username: String, onSuccess: (String) -> Unit = {}, onFailure: (String) -> Unit = {}) = viewModelScope.launch {
        val result = currentRepo.addContact(username.trim())
        if (result.isSuccess) {
            val contact = result.getOrThrow()
            loadContacts()
            onSuccess("Berhasil menambahkan ${contact.username}")
        } else {
            val errMsg = result.exceptionOrNull()?.message ?: "Gagal mencari user"
            onFailure(errMsg)
        }
    }

    fun updateProfileAndAvatar(
        newUsername: String,
        imageBytes: ByteArray?,
        mimeType: String?,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) = viewModelScope.launch {
        val currentUser = when (val auth = _authState.value) {
            is AuthState.Authenticated -> auth.user
            else -> null
        } ?: return@launch onFailure("Harap login terlebih dahulu")

        try {
            var finalAvatarUrl = currentUser.avatarUrl
            if (imageBytes != null && mimeType != null) {
                val uploadRes = currentRepo.uploadAvatar(currentUser.id, imageBytes, mimeType)
                if (uploadRes.isSuccess) {
                    finalAvatarUrl = uploadRes.getOrThrow()
                } else {
                    return@launch onFailure("Gagal upload avatar: " + (uploadRes.exceptionOrNull()?.message ?: "error"))
                }
            }

            val updateRes = currentRepo.updateProfile(newUsername.trim(), finalAvatarUrl, currentUser.fcmToken)
            if (updateRes.isSuccess) {
                val newProfile = updateRes.getOrThrow()
                _authState.value = AuthState.Authenticated(newProfile)
                onSuccess()
            } else {
                onFailure(updateRes.exceptionOrNull()?.message ?: "Gagal update profil")
            }
        } catch (e: Exception) {
            onFailure("Error: " + e.localizedMessage)
        }
    }

    fun startPrivateChat(contact: UserProfile) = viewModelScope.launch {
        _activeContact.value = contact
        _chatState.value = ChatState.Loading

        try {
            val conversationId = currentRepo.getOrCreateConversation(contact.id)
            _activeRoomId.value = conversationId

            // Auto-mark previous messages as Read when opening the conversation
            val currentUserId = when (val auth = _authState.value) {
                is AuthState.Authenticated -> auth.user.id
                else -> "user_demo"
            }
            currentRepo.markMessagesAsRead(conversationId, currentUserId)

            collectMessagesJob?.cancel()
            val stateFlowStream = currentRepo.getMessages(conversationId)
                .map<List<Message>, ChatState> { list ->
                    // Copy list to force recomposition
                    ChatState.Success(list.toList())
                }
                .catch { exception ->
                    Log.e(TAG, "Private message stream failed", exception)
                    emit(ChatState.Error(exception.localizedMessage ?: "Gagal memuat pesan live."))
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = ChatState.Loading
                )

            collectMessagesJob = viewModelScope.launch {
                stateFlowStream.collect { state ->
                    _chatState.value = state
                    if (state is ChatState.Success) {
                        val hasUnreadFromPartner = state.messages.any { it.senderId != currentUserId && !it.isRead }
                        if (hasUnreadFromPartner) {
                            launch {
                                currentRepo.markMessagesAsRead(conversationId, currentUserId)
                            }
                        }
                    }
                }
            }

            collectRealtimeMessagesJob?.cancel()
            collectRealtimeMessagesJob = viewModelScope.launch {
                currentRepo.realtimeMessageFlow.collect { newMessage ->
                    if (newMessage.conversationId == conversationId) {
                        val state = _chatState.value
                        if (state is ChatState.Success) {
                            val updated = (state.messages + newMessage).distinctBy { it.id }
                            _chatState.value = ChatState.Success(updated.toList())
                        }
                        if (newMessage.senderId != currentUserId) {
                            launch {
                                currentRepo.markMessagesAsRead(conversationId, currentUserId)
                            }
                        }
                    }
                }
            }

            // Observe typing status & presence signals
            presenceJob?.cancel()
            presenceJob = viewModelScope.launch {
                currentRepo.observePresence(conversationId)
                    .catch { Log.e(TAG, "Presence registration error", it) }
                    .collect { list ->
                        _presenceList.value = list
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed start private chat", e)
            _chatState.value = ChatState.Error(e.message ?: "Gagal menginisialisasi kamar.")
        }
    }

    fun trackPresence(isOnline: Boolean) = viewModelScope.launch {
        try {
            currentRepo.trackGlobalPresence(isOnline)
        } catch (e: Exception) {
            Log.e(TAG, "Failed monitoring global presence", e)
        }
    }

    fun clearActiveConversation() {
        _activeRoomId.value = null
        _activeContact.value = null
        SupabaseChatRepository.activeConversationId = null
        collectMessagesJob?.cancel()
        collectMessagesJob = null
        presenceJob?.cancel()
        presenceJob = null
        _chatState.value = ChatState.Idle
        Log.d("ActiveRoom", "Active conversation berhasil dikosongkan")
    }

    fun setTyping(isTyping: Boolean) = viewModelScope.launch {
        val roomId = _activeRoomId.value ?: return@launch
        val sender = when (val auth = _authState.value) {
            is AuthState.Authenticated -> auth.user.id
            else -> return@launch
        }
        currentRepo.setTypingStatus(roomId, sender, isTyping)
    }

    fun editMessage(messageId: String, newText: String, onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) = viewModelScope.launch {
        val result = currentRepo.editMessage(messageId, newText)
        if (result.isSuccess) {
            onSuccess()
        } else {
            onFailure(result.exceptionOrNull()?.message ?: "Gagal mengubah pesan")
        }
    }

    fun deleteMessage(messageId: String, onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) = viewModelScope.launch {
        val result = currentRepo.deleteMessage(messageId)
        if (result.isSuccess) {
            onSuccess()
        } else {
            onFailure(result.exceptionOrNull()?.message ?: "Gagal menghapus pesan")
        }
    }

    fun clearConversationMessages(conversationId: String, onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) = viewModelScope.launch {
        val result = currentRepo.clearConversationMessages(conversationId)
        if (result.isSuccess) {
            onSuccess()
        } else {
            onFailure(result.exceptionOrNull()?.message ?: "Gagal membersihkan percakapan")
        }
    }

    fun sendPrivateMessage(text: String, onSuccess: () -> Unit = {}, onFailure: (String) -> Unit = {}) = viewModelScope.launch {
        val roomId = _activeRoomId.value ?: return@launch
        val sender = when (val auth = _authState.value) {
            is AuthState.Authenticated -> auth.user.id
            else -> "user_demo"
        }
        if (text.isBlank()) return@launch

        _isSending.value = true
        try {
            val result = currentRepo.sendPrivateMessage(roomId, sender, text.trim())
            if (result.isSuccess) {
                onSuccess()
            } else {
                val err = result.exceptionOrNull()?.message ?: "Pesan gagal terkirim"
                onFailure(err)
            }
        } catch (e: Exception) {
            onFailure(e.message ?: "Gagal mengirim pesan")
        } finally {
            _isSending.value = false
        }
    }

    fun sendAttachment(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        text: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit
    ) = viewModelScope.launch {
        val roomId = _activeRoomId.value ?: return@launch onFailure("Tidak ada percakapan aktif")
        val sender = when (val auth = _authState.value) {
            is AuthState.Authenticated -> auth.user.id
            else -> "user_demo"
        }

        _isSending.value = true
        try {
            val uploadRes = currentRepo.uploadAttachment(bytes, fileName, mimeType)
            if (uploadRes.isSuccess) {
                val imageUrl = uploadRes.getOrThrow()
                val msgResult = currentRepo.sendPrivateMessage(roomId, sender, text.trim(), imageUrl)
                if (msgResult.isSuccess) {
                    onSuccess()
                } else {
                    onFailure(msgResult.exceptionOrNull()?.message ?: "Gagal mengirim pesan attachment")
                }
            } else {
                onFailure(uploadRes.exceptionOrNull()?.message ?: "Gagal mengunggah file ke storage")
            }
        } catch (e: Exception) {
            onFailure("Terjadi kesalahan: ${e.localizedMessage}")
        } finally {
            _isSending.value = false
        }
    }

    private fun syncFcmTokenAndStore() {
        try {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    Log.w(TAG, "Fetching FCM registration token failed", task.exception)
                    return@addOnCompleteListener
                }
                val token = task.result
                Log.d(TAG, "FCM token retrieved successfully: $token")
                
                viewModelScope.launch {
                    val auth = _authState.value
                    if (auth is AuthState.Authenticated) {
                        currentRepo.updateProfile(
                            username = auth.user.username,
                            avatarUrl = auth.user.avatarUrl,
                            fcmToken = token
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firebase messaging initialization skipped or failed: ${e.message}")
        }
    }

    fun updateSupabaseCredentials(url: String, key: String) {
        SupabaseService.customUrl = url.trim()
        SupabaseService.customAnonKey = key.trim()
        SupabaseService.resetClient()
        initializeRepository()
    }

    fun resetToDemoMode() {
        SupabaseService.customUrl = null
        SupabaseService.customAnonKey = null
        SupabaseService.resetClient()
        initializeRepository()
    }

    fun openChatFromNotification(conversationId: String?, senderId: String) = viewModelScope.launch {
        Log.d(TAG, "openChatFromNotification called: conversationId=$conversationId, senderId=$senderId")
        _chatState.value = ChatState.Loading
        var contact: UserProfile? = null

        val state = _contactsState.value
        if (state is ContactsState.Success) {
            contact = state.contacts.find { it.id == senderId }
        }

        if (contact == null) {
            try {
                val list = currentRepo.getContacts()
                contact = list.find { it.id == senderId }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load contacts for notification", e)
            }
        }

        if (contact == null) {
            contact = UserProfile(id = senderId, username = "Teman", email = "", avatarUrl = null)
        }

        startPrivateChat(contact).join()
        _navigateToChatEvent.tryEmit(Unit)
    }
}
