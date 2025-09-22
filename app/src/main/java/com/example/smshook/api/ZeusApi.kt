package com.example.smshook.api

import android.content.Context
import android.util.Log
import com.example.smshook.config.ServerConfig
import com.example.smshook.logs.LogLevel
import com.example.smshook.logs.LogManager
import com.example.smshook.ussd.UssdStepResult
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

object ZeusApi {
    private const val TAG = "ZeusApi"
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS) // Increased from 30 to 60 seconds
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    // Get base URL dynamically from configuration
    private fun getBaseUrl(context: Context): String {
        val baseUrl = ServerConfig.getApiBaseUrl(context)
        Log.d(TAG, "Base URL from config: $baseUrl")
        return baseUrl
    }

    fun registerFcm(context: Context, deviceId: String, token: String) {
        try {
            val body = gson.toJson(mapOf("token" to token))
            Log.d(TAG, "Attempting FCM token registration for device: $deviceId")
            Log.d(TAG, "Token length: ${token.length}")
            post(context, "${getBaseUrl(context)}/register", body)
            Log.d(TAG, "FCM token registered for device: $deviceId")
            LogManager.addLog(LogLevel.API, TAG, "FCM token registered successfully", "Device: $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register FCM token: ${e.message}", e)
            LogManager.addLog(LogLevel.ERROR, TAG, "Failed to register FCM token", "Error: ${e.message}, Type: ${e.javaClass.simpleName}")
        }
    }

    fun registerSimSlots(context: Context, deviceId: String, simSlots: List<SimSlotInfo>) {
        try {
            val body = gson.toJson(mapOf(
                "deviceId" to deviceId,
                "simSlots" to simSlots
            ))
            Log.d(TAG, "Registering SIM slots: $body")
            post(context, "${getBaseUrl(context)}/sim-slots", body)
            Log.d(TAG, "SIM slots registered for device: $deviceId")
            LogManager.addLog(LogLevel.API, TAG, "SIM slots registered successfully", "Device: $deviceId, Slots: ${simSlots.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register SIM slots: ${e.message}", e)
            LogManager.addLog(LogLevel.ERROR, TAG, "Failed to register SIM slots", "Error: ${e.message}, Type: ${e.javaClass.simpleName}")
        }
    }

    fun getJob(context: Context, jobId: String): Job {
        return getJobWithRetry(context, jobId, maxRetries = 2)
    }
    
    private fun getJobWithRetry(context: Context, jobId: String, maxRetries: Int): Job {
        var lastException: Exception? = null
        
        repeat(maxRetries + 1) { attempt ->
            try {
                LogManager.addLog(LogLevel.API, TAG, "Fetching job details (attempt ${attempt + 1})", "Job ID: $jobId")
                val response = get(context, "${getBaseUrl(context)}/jobs/$jobId")
                val job = gson.fromJson(response, Job::class.java)
                LogManager.addLog(
                    LogLevel.API,
                    TAG,
                    "Job details fetched successfully",
                    "Operator: ${job.operator}, SIM: ${job.simSlot}, Code: ${job.code}, Steps: ${job.steps}, Seq: ${job.seq}"
                )
                return job
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "Attempt ${attempt + 1} failed to fetch job details: ${e.message}", e)
                
                if (attempt < maxRetries) {
                    LogManager.addLog(LogLevel.API, TAG, "Retrying job fetch", "Attempt ${attempt + 1} failed, retrying...")
                    // Wait before retry (exponential backoff)
                    Thread.sleep(1000L * (attempt + 1))
                }
            }
        }
        
        // If all retries failed, throw the last exception
        LogManager.addLog(LogLevel.ERROR, TAG, "All attempts failed to fetch job details", "Job ID: $jobId, Error: ${lastException?.message}")
        throw lastException ?: Exception("Failed to fetch job details after $maxRetries retries")
    }


    fun completeJob(context: Context, jobId: String, outcome: Any) {
        try {
            val body = gson.toJson(outcome)
            post(context, "${getBaseUrl(context)}/jobs/$jobId/complete", body)
            Log.d(TAG, "Job completed: $jobId")
            LogManager.addLog(LogLevel.API, TAG, "Job completed successfully", "Job ID: $jobId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to complete job: ${e.message}")
            LogManager.addLog(LogLevel.ERROR, TAG, "Failed to complete job", e.message)
        }
    }

    fun sendUssdResponse(context: Context, jobId: String, finalResponse: String, success: Boolean, steps: List<UssdStepResult>) {
        try {
            val responseData = mapOf(
                "jobId" to jobId,
                "finalResponse" to finalResponse,
                "success" to success,
                "steps" to steps,
                "timestamp" to System.currentTimeMillis()
            )
            val body = gson.toJson(responseData)
            post(context, "${getBaseUrl(context)}/jobs/$jobId/response", body)
            Log.d(TAG, "USSD response sent: $jobId")
            LogManager.addLog(LogLevel.API, TAG, "USSD response sent successfully", "Job ID: $jobId, Success: $success")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send USSD response: ${e.message}")
            LogManager.addLog(LogLevel.ERROR, TAG, "Failed to send USSD response", e.message)
        }
    }

    private fun get(context: Context, url: String): String {
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("ngrok-skip-browser-warning", "true")
            .build()
        
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            response.body?.string() ?: throw IOException("Empty response body")
        }
    }

    private fun post(context: Context, url: String, body: String) {
        Log.d(TAG, "POST request to URL: $url")
        Log.d(TAG, "POST request body: $body")
        
        val requestBody = body.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("ngrok-skip-browser-warning", "true")
            .build()
        
        client.newCall(request).execute().use { response ->
            Log.d(TAG, "Response code: ${response.code}")
            Log.d(TAG, "Response message: ${response.message}")
            if (!response.isSuccessful) {
                val responseBody = response.body?.string() ?: "No response body"
                Log.e(TAG, "Response body: $responseBody")
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
        }
    }
}

data class Job(
    val id: String,
    val operator: String?,
    val simSlot: Int?,
    val code: String?,
    val steps: List<String>?,
    val seq: String?
)

data class SimSlotInfo(
    val slotIndex: Int,
    val carrierName: String?,
    val displayName: String?,
    val isActive: Boolean,
    val mcc: String?,
    val mnc: String?
)
