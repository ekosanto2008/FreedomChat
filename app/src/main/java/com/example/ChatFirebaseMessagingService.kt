package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.SupabaseChatRepository
import com.example.data.SupabaseService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ChatFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("ChatFCMService", "Refreshed token: $token")
        
        // Update user profile token if user is signed in
        if (SupabaseService.isConfigured) {
            val scope = CoroutineScope(Dispatchers.IO)
            scope.launch {
                try {
                    val repo = SupabaseChatRepository()
                    val currentUser = repo.getCurrentUser()
                    if (currentUser != null) {
                        val result = repo.updateProfile(
                            username = currentUser.username,
                            avatarUrl = currentUser.avatarUrl,
                            fcmToken = token
                        )
                        if (result.isSuccess) {
                            Log.d("ChatFCMService", "FCM Token successfully synced for ${currentUser.username}")
                        } else {
                            Log.e("ChatFCMService", "Failed to sync FCM Token: ${result.exceptionOrNull()?.message}")
                        }
                    } else {
                        Log.d("ChatFCMService", "No active user logged in. FCM Token cannot be updated.")
                    }
                } catch (e: Exception) {
                    Log.e("ChatFCMService", "Error during FCM token updates: ${e.message}")
                }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("ChatFCMService", "Message received from: ${remoteMessage.from}")

        // 1. Ambil Room ID dari pesan yang masuk
        val incomingRoomId = remoteMessage.data["conversationId"]
        val senderId = remoteMessage.data["senderId"]
        val senderName = remoteMessage.data["senderName"]

        // 2. CEK STATUS LAYAR: Apakah user sedang asyik chat di room ini?
        if (incomingRoomId != null && incomingRoomId == ChatSessionManager.activeConversationId) {
            Log.d("ChatFCMService", "Sstt! User sedang di dalam room $incomingRoomId. Notifikasi pop-up dibisukan.")
            return // Stop di sini! Jangan bunyikan suara atau tampilkan pop-up
        }

        // 3. Jika tidak sedang di room tersebut (atau aplikasi ditutup), tampilkan notifikasinya!
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "Pesan Baru"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "Anda menerima pesan privat."

        sendNotification(title, body, incomingRoomId, senderId, senderName)
    }

    private fun sendNotification(title: String, messageBody: String, conversationId: String?, senderId: String?, senderName: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("conversationId", conversationId)
            putExtra("senderId", senderId)
            putExtra("senderName", senderName)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 1. BACA PENGATURAN USER
        val prefs = getSharedPreferences("settings_pref", Context.MODE_PRIVATE)
        val isNotifEnabled = prefs.getBoolean("notifications_enabled", true)
        val ringtoneUriStr = prefs.getString("ringtone_uri", "") ?: ""
        val ringtoneUriString = if (ringtoneUriStr.isNotEmpty()) ringtoneUriStr else null

        if (!isNotifEnabled) {
            Log.d("ChatFCMService", "User mematikan notifikasi. Abort.")
            return
        }

        // 2. PLAY SUARA SECARA MANUAL (BYPASS CHANNEL)
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            if (audioManager.ringerMode == android.media.AudioManager.RINGER_MODE_NORMAL) {
                val uri = if (ringtoneUriString != null) {
                    android.net.Uri.parse(ringtoneUriString)
                } else {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }
                
                val mediaPlayer = android.media.MediaPlayer().apply {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(this@ChatFirebaseMessagingService, uri)
                    prepare()
                    start()
                    setOnCompletionListener { it.release() }
                }
            }
        } catch (e: Exception) {
            Log.e("ChatFCMService", "Gagal play suara manual: ${e.message}")
        }

        // 3. BUAT NOTIFIKASI HEADS-UP (WAJIB CHANNEL BARU)
        val channelId = "chat_vip_channel_v1" // ID BARU! Jangan diubah ke yang lama!
        
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Ganti dengan R.drawable.ikon_app_anda nanti
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // Syarat melayang
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE) // Pemicu melayang

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Pesan Masuk (Pop-up)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifikasi prioritas tinggi agar melayang di layar"
                enableVibration(true)
                // Hapus suara bawaan channel agar tidak bentrok dengan MediaPlayer kita
                setSound(null, null) 
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
