package com.example.smshook.realtime

import android.content.Context
import android.util.Log
import com.example.smshook.config.ServerConfig
import com.google.gson.Gson
import okhttp3.*
import java.io.IOException
import java.util.*
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ZeusTokenProvider {
    private const val TAG = "ZeusTokenProvider"
    private val gson = Gson()
    
    // Token endpoint will be retrieved from ServerConfig
    
    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences("zeus_realtime", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", deviceId).apply()
            Log.d(TAG, "Generated new device ID: $deviceId")
        }
        return deviceId
    }
    
    suspend fun getToken(context: Context): String = suspendCoroutine { continuation ->
        val deviceId = getDeviceId(context)
        val client = OkHttpClient()
        
        // Get token endpoint URL from configuration
        val tokenEndpointUrl = ServerConfig.getTokenEndpointUrl(context)
        val url = HttpUrl.Builder()
            .scheme("https")
            .host(extractHostFromUrl(tokenEndpointUrl))
            .addPathSegment("token")
            .addQueryParameter("deviceId", deviceId)
            .build()
            
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
            
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to fetch token: ${e.message}")
                continuation.resumeWithException(e)
            }
            
            override fun onResponse(call: Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        throw IOException("HTTP ${response.code}: ${response.message}")
                    }
                    
                    val responseBody = response.body?.string()
                    if (responseBody.isNullOrEmpty()) {
                        throw IOException("Empty response body")
                    }
                    
                    val tokenResponse = gson.fromJson(responseBody, TokenResponse::class.java)
                    if (tokenResponse.token.isNullOrEmpty()) {
                        throw IOException("No token in response")
                    }
                    
                    Log.d(TAG, "Successfully fetched token for device: $deviceId")
                    continuation.resume(tokenResponse.token)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing token response: ${e.message}")
                    continuation.resumeWithException(e)
                }
            }
        })
    }
    
    // For development/testing - returns a dummy token
    fun getDummyToken(context: Context): String {
        val deviceId = getDeviceId(context)
        Log.w(TAG, "Using dummy token for development. Device ID: $deviceId")
        // In production, remove this and ensure getToken() always calls the real server
        return "dummy-jwt-token-for-device-$deviceId"
    }
    
    private fun extractHostFromUrl(url: String): String {
        return try {
            val cleanUrl = url.replace("https://", "").replace("http://", "")
            cleanUrl.split("/")[0]
        } catch (e: Exception) {
            "zeus.cloud" // fallback
        }
    }
    
    private data class TokenResponse(
        val token: String? = null,
        val error: String? = null
    )
}
