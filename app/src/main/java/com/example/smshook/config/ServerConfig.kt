package com.example.smshook.config

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object ServerConfig {
    private const val TAG = "ServerConfig"
    private const val PREFS_NAME = "ZeusServerConfig"
    
    // Server URL keys
    private const val KEY_API_BASE_URL = "api_base_url"
    private const val KEY_FCM_BASE_URL = "fcm_base_url"
    private const val KEY_WEBSOCKET_URL = "websocket_url"
    private const val KEY_TOKEN_ENDPOINT_URL = "token_endpoint_url"
    
    // Default URLs (fallback to current hardcoded values)
    private const val DEFAULT_API_BASE = "https://stenochoric-sororially-fredric.ngrok-free.app/fcm"
    private const val DEFAULT_FCM_BASE = "https://stenochoric-sororially-fredric.ngrok-free.app"
    private const val DEFAULT_WEBSOCKET = "wss://localhost:8080/rt"
    private const val DEFAULT_TOKEN_ENDPOINT = "https://zeus.cloud/token"
    
    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    // API Base URL (for ZeusApi.kt)
    fun getApiBaseUrl(context: Context): String {
        val prefs = getSharedPreferences(context)
        return prefs.getString(KEY_API_BASE_URL, DEFAULT_API_BASE) ?: DEFAULT_API_BASE
    }
    
    fun setApiBaseUrl(context: Context, url: String) {
        val prefs = getSharedPreferences(context)
        prefs.edit().putString(KEY_API_BASE_URL, url).apply()
        Log.d(TAG, "API Base URL updated to: $url")
        Log.d(TAG, "API Base URL length: ${url.length}, contains spaces: ${url.contains(" ")}")
    }
    
    // FCM Base URL (for ZeusFcmService.java)
    fun getFcmBaseUrl(context: Context): String {
        val prefs = getSharedPreferences(context)
        return prefs.getString(KEY_FCM_BASE_URL, DEFAULT_FCM_BASE) ?: DEFAULT_FCM_BASE
    }
    
    fun setFcmBaseUrl(context: Context, url: String) {
        val prefs = getSharedPreferences(context)
        prefs.edit().putString(KEY_FCM_BASE_URL, url).apply()
        Log.d(TAG, "FCM Base URL updated to: $url")
    }
    
    // WebSocket URL (for RealtimeService.kt)
    fun getWebSocketUrl(context: Context): String {
        val prefs = getSharedPreferences(context)
        return prefs.getString(KEY_WEBSOCKET_URL, DEFAULT_WEBSOCKET) ?: DEFAULT_WEBSOCKET
    }
    
    fun setWebSocketUrl(context: Context, url: String) {
        val prefs = getSharedPreferences(context)
        prefs.edit().putString(KEY_WEBSOCKET_URL, url).apply()
        Log.d(TAG, "WebSocket URL updated to: $url")
    }
    
    // Token Endpoint URL (for ZeusTokenProvider.kt)
    fun getTokenEndpointUrl(context: Context): String {
        val prefs = getSharedPreferences(context)
        return prefs.getString(KEY_TOKEN_ENDPOINT_URL, DEFAULT_TOKEN_ENDPOINT) ?: DEFAULT_TOKEN_ENDPOINT
    }
    
    fun setTokenEndpointUrl(context: Context, url: String) {
        val prefs = getSharedPreferences(context)
        prefs.edit().putString(KEY_TOKEN_ENDPOINT_URL, url).apply()
        Log.d(TAG, "Token Endpoint URL updated to: $url")
    }
    
    // Helper method to set all URLs from a base server URL
    fun setServerBaseUrl(context: Context, baseUrl: String) {
        val cleanBaseUrl = baseUrl.trimEnd('/')
        
        // Set API base URL (append /fcm)
        setApiBaseUrl(context, "$cleanBaseUrl/fcm")
        
        // Set FCM base URL (same as base)
        setFcmBaseUrl(context, cleanBaseUrl)
        
        // Set WebSocket URL (convert http/https to ws/wss)
        val wsUrl = if (cleanBaseUrl.startsWith("https://")) {
            cleanBaseUrl.replace("https://", "wss://") + "/rt"
        } else {
            cleanBaseUrl.replace("http://", "ws://") + "/rt"
        }
        setWebSocketUrl(context, wsUrl)
        
        // Set Token endpoint URL (append /token)
        setTokenEndpointUrl(context, "$cleanBaseUrl/token")
        
        Log.d(TAG, "All server URLs updated from base: $baseUrl")
    }
    
    // Get all current URLs for display
    fun getAllUrls(context: Context): Map<String, String> {
        return mapOf(
            "API Base" to getApiBaseUrl(context),
            "FCM Base" to getFcmBaseUrl(context),
            "WebSocket" to getWebSocketUrl(context),
            "Token Endpoint" to getTokenEndpointUrl(context)
        )
    }
    
    // Reset to defaults
    fun resetToDefaults(context: Context) {
        val prefs = getSharedPreferences(context)
        prefs.edit()
            .putString(KEY_API_BASE_URL, DEFAULT_API_BASE)
            .putString(KEY_FCM_BASE_URL, DEFAULT_FCM_BASE)
            .putString(KEY_WEBSOCKET_URL, DEFAULT_WEBSOCKET)
            .putString(KEY_TOKEN_ENDPOINT_URL, DEFAULT_TOKEN_ENDPOINT)
            .apply()
        Log.d(TAG, "Server URLs reset to defaults")
    }
    
    // Validate URL format
    fun isValidUrl(url: String): Boolean {
        return try {
            val cleanUrl = url.trim()
            cleanUrl.isNotEmpty() && 
            (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://") || 
             cleanUrl.startsWith("ws://") || cleanUrl.startsWith("wss://"))
        } catch (e: Exception) {
            false
        }
    }
}

