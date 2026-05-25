package com.example.data

import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    // Auth
    suspend fun signUp(email: String, password: String, username: String): Result<UserProfile>
    suspend fun signIn(email: String, password: String): Result<UserProfile>
    suspend fun signOut(): Result<Unit>
    suspend fun getCurrentUser(): UserProfile?
    suspend fun updateProfile(username: String, avatarUrl: String?, fcmToken: String?): Result<UserProfile>

    // Contacts
    suspend fun getContacts(): List<UserProfile>
    suspend fun addContact(username: String): Result<UserProfile>

    // Conversations & Messages
    suspend fun getOrCreateConversation(contactUserId: String): String // Returns conversation_id
    fun getMessages(conversationId: String): Flow<List<Message>>
    suspend fun sendPrivateMessage(conversationId: String, senderId: String, text: String): Result<Unit>
    suspend fun sendPrivateMessage(conversationId: String, senderId: String, text: String, imageUrl: String?): Result<Unit>
    suspend fun markMessagesAsRead(conversationId: String, currentUserId: String): Result<Unit>
    suspend fun syncMessages(conversationId: String): Result<Unit>
    fun getLastMessage(conversationId: String): Flow<Message?>
    fun getUnreadCount(conversationId: String, currentUserId: String): Flow<Int>

    // Presence & Typing
    suspend fun setTypingStatus(conversationId: String, userId: String, isTyping: Boolean)
    fun observePresence(conversationId: String): Flow<List<UserPresence>>
    suspend fun trackGlobalPresence(isOnline: Boolean)

    // Realtime Events
    val realtimeMessageFlow: Flow<Message>
    suspend fun startGlobalMessageListener(currentUserId: String) {}

    // Storage
    suspend fun uploadAvatar(userId: String, bytes: ByteArray, mimeType: String): Result<String>
    suspend fun uploadAttachment(bytes: ByteArray, fileName: String, mimeType: String): Result<String>

    // Fallbacks
    fun getMessages(): Flow<List<Message>>
    suspend fun sendMessage(senderId: String, text: String): Result<Unit>

    // Message actions & edit/delete
    suspend fun editMessage(messageId: String, newText: String): Result<Unit>
    suspend fun deleteMessage(messageId: String): Result<Unit>
    suspend fun clearConversationMessages(conversationId: String): Result<Unit>
}
