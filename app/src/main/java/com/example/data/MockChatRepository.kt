package com.example.data

import com.example.MyApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MockChatRepository : ChatRepository {
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    private val scope = CoroutineScope(Dispatchers.Default)

    private var currentUser: UserProfile? = UserProfile("user_demo", "DimasChat", "demo@example.com")
    private val _contacts = MutableStateFlow<List<UserProfile>>(
        listOf(
            UserProfile("ai", "Gemini AI Assistant", "ai@gmail.com"),
            UserProfile("admin", "Dukungan Teknis", "admin@telegram.org")
        )
    )

    private fun getIsoTimestamp(minusSeconds: Long = 0): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val date = if (minusSeconds > 0) Date(System.currentTimeMillis() - minusSeconds * 1000) else Date()
        return sdf.format(date)
    }

    init {
        val now = getIsoTimestamp(60)
        _messages.value = listOf(
            Message(
                id = "1",
                senderId = "ai",
                text = "Welcome to Demo Private Messaging! 👋 Click any contact to start chatting. Search and add contacts like 'admin' or create your own test.",
                timestamp = now,
                conversationId = "demo_conv"
            )
        )
    }

    override suspend fun signUp(email: String, password: String, username: String): Result<UserProfile> {
        delay(500)
        val profile = UserProfile(id = "user_${username.lowercase()}", username = username, email = email)
        currentUser = profile
        return Result.success(profile)
    }

    override suspend fun signIn(email: String, password: String): Result<UserProfile> {
        delay(500)
        val username = email.substringBefore("@")
        val profile = UserProfile(id = "user_${username.lowercase()}", username = username, email = email)
        currentUser = profile
        return Result.success(profile)
    }

    override suspend fun signOut(): Result<Unit> {
        currentUser = null
        return Result.success(Unit)
    }

    override suspend fun getCurrentUser(): UserProfile? {
        return currentUser
    }

    override suspend fun getContacts(): List<UserProfile> {
        return _contacts.value
    }

    override suspend fun addContact(username: String): Result<UserProfile> {
        delay(500)
        val lower = username.lowercase()
        val existing = _contacts.value.find { it.username.equals(username, ignoreCase = true) }
        if (existing != null) {
            return Result.success(existing)
        }
        val newProfile = UserProfile(
            id = "user_$lower",
            username = username,
            email = "$lower@example.com"
        )
        _contacts.value = _contacts.value + newProfile
        return Result.success(newProfile)
    }

    override suspend fun getOrCreateConversation(contactUserId: String): String {
        return "conv_${contactUserId}"
    }

    override fun getMessages(conversationId: String): Flow<List<Message>> {
        return _messages.asStateFlow().map { list ->
            list.filter { it.conversationId == conversationId || it.conversationId == "demo_conv" }
        }
    }

    override suspend fun sendPrivateMessage(conversationId: String, senderId: String, text: String): Result<Unit> {
        val now = getIsoTimestamp()
        val nextId = java.util.UUID.randomUUID().toString()
        val newMessage = Message(
            id = nextId,
            senderId = senderId,
            text = text,
            timestamp = now,
            conversationId = conversationId
        )

        _messages.value = _messages.value + newMessage

        // Simulate reply if talking to AI
        if (conversationId == "conv_ai") {
            simulateBotReply(text, conversationId)
        }

        return Result.success(Unit)
    }

    override fun getMessages(): Flow<List<Message>> {
        return getMessages("demo_conv")
    }

    override suspend fun sendMessage(senderId: String, text: String): Result<Unit> {
        return sendPrivateMessage("demo_conv", senderId, text)
    }

    override suspend fun sendPrivateMessage(conversationId: String, senderId: String, text: String, imageUrl: String?): Result<Unit> {
        val now = getIsoTimestamp()
        val nextId = java.util.UUID.randomUUID().toString()
        val newMessage = Message(
            id = nextId,
            senderId = senderId,
            text = text,
            timestamp = now,
            conversationId = conversationId,
            imageUrl = imageUrl,
            status = "read"
        )
        _messages.value = _messages.value + newMessage
        if (conversationId == "conv_ai") {
            simulateBotReply(text, conversationId)
        }
        return Result.success(Unit)
    }

    override suspend fun updateProfile(username: String, avatarUrl: String?, fcmToken: String?): Result<UserProfile> {
        val old = currentUser ?: UserProfile("user_demo", "DimasChat", "demo@example.com")
        val updated = old.copy(username = username, avatarUrl = avatarUrl, fcmToken = fcmToken)
        currentUser = updated
        return Result.success(updated)
    }

    override suspend fun markMessagesAsRead(conversationId: String, currentUserId: String): Result<Unit> {
        _messages.value = _messages.value.map {
            if (it.conversationId == conversationId && it.senderId != currentUserId) {
                it.copy(status = "read", isRead = true)
            } else {
                it
            }
        }
        return Result.success(Unit)
    }

    override suspend fun syncMessages(conversationId: String): Result<Unit> {
        return Result.success(Unit)
    }

    override fun getLastMessage(conversationId: String): Flow<Message?> {
        return _messages.map { list ->
            list.filter { it.conversationId == conversationId }.maxByOrNull { it.timestamp }
        }
    }

    override fun getUnreadCount(conversationId: String, currentUserId: String): Flow<Int> {
        return _messages.map { list ->
            list.count { it.conversationId == conversationId && it.senderId != currentUserId && !it.isRead }
        }
    }

    override suspend fun uploadAvatar(userId: String, bytes: ByteArray, mimeType: String): Result<String> {
        return Result.success("https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?auto=format&fit=crop&w=120")
    }

    override suspend fun uploadAttachment(bytes: ByteArray, fileName: String, mimeType: String): Result<String> {
        return Result.success("https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=300")
    }

    private val _presenceFlow = MutableStateFlow<List<UserPresence>>(emptyList())
    private val _realtimeMessageFlow = kotlinx.coroutines.flow.MutableSharedFlow<Message>()
    override val realtimeMessageFlow: Flow<Message> = _realtimeMessageFlow

    override suspend fun trackGlobalPresence(isOnline: Boolean) {
        // No-op mock implementation
    }

    override suspend fun setTypingStatus(conversationId: String, userId: String, isTyping: Boolean) {
        if (conversationId == "conv_ai" && isTyping) {
            _presenceFlow.value = listOf(UserPresence("ai", isOnline = true, isTyping = true))
        } else {
            _presenceFlow.value = listOf(UserPresence("ai", isOnline = true, isTyping = false))
        }
    }

    override fun observePresence(conversationId: String): Flow<List<UserPresence>> {
        if (_presenceFlow.value.isEmpty()) {
            _presenceFlow.value = listOf(UserPresence("ai", isOnline = true, isTyping = false))
        }
        return _presenceFlow
    }

    override suspend fun editMessage(messageId: String, newText: String): Result<Unit> = runCatching {
        _messages.value = _messages.value.map {
            if (it.id == messageId) it.copy(text = newText) else it
        }
        MyApplication.database.messageDao().updateMessageText(messageId, newText)
    }

    override suspend fun deleteMessage(messageId: String): Result<Unit> = runCatching {
        _messages.value = _messages.value.filter { it.id != messageId }
        MyApplication.database.messageDao().deleteMessageById(messageId)
    }

    override suspend fun clearConversationMessages(conversationId: String): Result<Unit> = runCatching {
        _messages.value = _messages.value.filter { it.conversationId != conversationId }
        MyApplication.database.messageDao().deleteMessagesByConversation(conversationId)
    }

    private fun simulateBotReply(userText: String, conversationId: String) {
        scope.launch {
            delay(1000)
            val replyText = when {
                userText.contains("halo", ignoreCase = true) || userText.contains("hi", ignoreCase = true) -> "Halo! Saya simulator asisten AI anda. Ada yang bisa saya bantu?"
                userText.contains("whatsapp", ignoreCase = true) || userText.contains("private", ignoreCase = true) -> "Aplikasi ini sekarang memakai arsitektur private chat full 1-on-1!"
                else -> "Pesan private diterima! Simsalabim, ini balasan bot otomatis ✨"
            }
            val replyMessage = Message(
                id = java.util.UUID.randomUUID().toString(),
                senderId = "ai",
                text = replyText,
                timestamp = getIsoTimestamp(),
                conversationId = conversationId
            )
            _messages.value = _messages.value + replyMessage
        }
    }
}
