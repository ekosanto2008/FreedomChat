package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status, is_read = 1 WHERE conversation_id = :conversationId AND sender_id != :currentUserId")
    suspend fun updateMessageStatus(conversationId: String, currentUserId: String, status: String)

    @Query("UPDATE messages SET is_read = 1 WHERE conversation_id = :conversationId AND sender_id != :currentUserId AND is_read = 0")
    suspend fun markAsReadLocal(conversationId: String, currentUserId: String): Int

    @Query("SELECT * FROM messages WHERE conversation_id = :conversationId ORDER BY timestamp DESC LIMIT 1")
    fun getLastMessageForConversation(conversationId: String): Flow<MessageEntity?>

    @Query("SELECT COUNT(id) FROM messages WHERE conversation_id = :conversationId AND is_read = 0 AND sender_id != :currentUserId")
    fun getUnreadCountForConversation(conversationId: String, currentUserId: String): Flow<Int>

    @Query("DELETE FROM messages")
    suspend fun clearAll()

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("DELETE FROM messages WHERE conversation_id = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: String)

    @Query("UPDATE messages SET text = :newText WHERE id = :messageId")
    suspend fun updateMessageText(messageId: String, newText: String)
}

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM profiles WHERE id = :id")
    suspend fun getProfile(id: String): UserProfileEntity?

    @Query("SELECT * FROM profiles")
    fun getAllProfilesFlow(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id != :currentUserId")
    fun getAllContacts(currentUserId: String): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id != :currentUserId")
    suspend fun getAllContactsDirect(currentUserId: String): List<UserProfileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfiles(profiles: List<UserProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileEntity)

    @Query("DELETE FROM profiles")
    suspend fun clearAll()
}
