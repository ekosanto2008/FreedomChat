package com.example.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Message(
    @SerialName("id") val id: String? = null,
    @SerialName("sender_id") val senderId: String,
    @SerialName("text") val text: String,
    @SerialName("created_at") val timestamp: String,
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("status") val status: String = "sent",
    @SerialName("is_read") val isRead: Boolean = false
)
