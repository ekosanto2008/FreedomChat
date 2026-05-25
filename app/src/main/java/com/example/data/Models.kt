package com.example.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    @SerialName("id") val id: String,
    @SerialName("username") val username: String,
    @SerialName("email") val email: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("fcm_token") val fcmToken: String? = null
)

@Serializable
data class DbUserProfile(
    @SerialName("id") val id: String,
    @SerialName("username") val username: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("fcm_token") val fcmToken: String? = null
)

@Serializable
data class Conversation(
    @SerialName("id") val id: String,
    @SerialName("user1_id") val user1Id: String,
    @SerialName("user2_id") val user2Id: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class Contact(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id_1") val userId1: String,
    @SerialName("user_id_2") val userId2: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class UserPresence(
    @SerialName("user_id") val userId: String,
    @SerialName("is_online") val isOnline: Boolean,
    @SerialName("is_typing") val isTyping: Boolean,
    @SerialName("last_seen") val lastSeen: Long = System.currentTimeMillis()
)

@Serializable
data class UserStatus(
    @SerialName("is_online") val isOnline: Boolean
)
