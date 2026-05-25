package com.example.data

import android.content.Context
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.storage.Storage
import com.example.BuildConfig
import com.example.MyApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

object SupabaseService {
    private const val TAG = "SupabaseService"

    var customUrl: String? = null
    var customAnonKey: String? = null

    private fun cleanUrl(url: String): String {
        var cleaned = url.trim()
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length - 1)
        }
        if (cleaned.endsWith("/rest/v1")) {
            cleaned = cleaned.substring(0, cleaned.length - "/rest/v1".length)
        }
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length - 1)
        }
        return cleaned
    }

    val isConfigured: Boolean
        get() {
            val url = getActiveUrl()
            val key = getActiveKey()
            return !url.isNullOrBlank() && url != "YOUR_SUPABASE_URL" &&
                   !key.isNullOrBlank() && key != "YOUR_SUPABASE_KEY"
        }

    fun getActiveUrl(): String? {
        if (!customUrl.isNullOrBlank()) return cleanUrl(customUrl!!)
        return try {
            val url = BuildConfig.SUPABASE_URL
            if (url.isEmpty() || url == "YOUR_SUPABASE_URL") null else cleanUrl(url)
        } catch (e: Exception) {
            null
        }
    }

    fun getActiveKey(): String? {
        if (!customAnonKey.isNullOrBlank()) return customAnonKey
        return try {
            val key = BuildConfig.SUPABASE_ANON_KEY
            if (key.isEmpty() || key == "YOUR_SUPABASE_KEY") null else key
        } catch (e: Exception) {
            null
        }
    }

    private var _supabaseClient: SupabaseClient? = null

    val client: SupabaseClient
        get() {
            val current = _supabaseClient
            if (current != null) return current

            val url = getActiveUrl() ?: throw IllegalStateException("Supabase URL is not configured")
            val key = getActiveKey() ?: throw IllegalStateException("Supabase Anon Key is not configured")

            Log.d(TAG, "Initializing SupabaseClient with URL: $url")
            val newClient = createSupabaseClient(
                supabaseUrl = url,
                supabaseKey = key
            ) {
                install(Postgrest)
                install(Realtime)
                install(Auth) {
                    sessionManager = AndroidSessionManager()
                }
                install(Storage)
            }
            _supabaseClient = newClient
            return newClient
        }

    fun resetClient() {
        _supabaseClient = null
    }
}

class AndroidSessionManager : SessionManager {
    private val key = "current_session_v2"
    private val sharedPrefs by lazy {
        MyApplication.instance.getSharedPreferences("supabase_session", Context.MODE_PRIVATE)
    }

    override suspend fun saveSession(session: UserSession) {
        try {
            val json = Json.encodeToString(UserSession.serializer(), session)
            sharedPrefs.edit().putString(key, json).apply()
            Log.d("AndroidSessionManager", "Session successfully persisted to SharedPreferences.")
        } catch (e: Exception) {
            Log.e("AndroidSessionManager", "Failed to save session", e)
        }
    }

    override suspend fun loadSession(): UserSession? {
        val json = sharedPrefs.getString(key, null) ?: return null
        return try {
            val session = Json.decodeFromString(UserSession.serializer(), json)
            Log.d("AndroidSessionManager", "Session successfully loaded from SharedPreferences.")
            session
        } catch (e: Exception) {
            Log.e("AndroidSessionManager", "Failed to decode loaded session", e)
            null
        }
    }

    override suspend fun deleteSession() {
        try {
            sharedPrefs.edit().remove(key).apply()
            Log.d("AndroidSessionManager", "Session cleared/deleted from SharedPreferences.")
        } catch (e: Exception) {
            Log.e("AndroidSessionManager", "Failed to delete session", e)
        }
    }
}
