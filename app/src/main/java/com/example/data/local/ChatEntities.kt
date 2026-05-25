package com.example.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "sender_id") val senderId: String,
    val text: String,
    val timestamp: String,
    @ColumnInfo(name = "conversation_id") val conversationId: String,
    @ColumnInfo(name = "image_url") val imageUrl: String? = null,
    val status: String = "sent",
    @ColumnInfo(name = "is_read") val isRead: Boolean = false
)

@Entity(tableName = "profiles")
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val username: String,
    val email: String,
    val avatarUrl: String? = null,
    val fcmToken: String? = null
)
