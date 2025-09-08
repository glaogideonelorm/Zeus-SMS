package com.example.smshook.sms

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters
import okhttp3.MediaType.Companion.toMediaType
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.telephony.SubscriptionManager
import com.example.smshook.fragments.ConfigurationFragment
import com.example.smshook.data.ForwardingStatus
import com.example.smshook.data.SmsLogManager
import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class ForwardWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    private val context = ctx
    private val smsLogManager = SmsLogManager.getInstance(ctx)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun doWork(): Result {
        Log.d("ZeusSMS", "ForwardWorker started (runAttemptCount=$runAttemptCount)")
        val from = inputData.getString("from") ?: return Result.failure()
        val body = inputData.getString("body") ?: return Result.failure()
        val timestamp = inputData.getLong("timestamp", System.currentTimeMillis())
        var subId = inputData.getInt("subscriptionId", -1)
        val isTest = inputData.getBoolean("isTest", false)
        val existingLogId = inputData.getLong("logId", -1L)

        // Get webhook URL from SharedPreferences
        val webhookUrl = ConfigurationFragment.getWebhookUrl(context)
        if (webhookUrl.isNullOrEmpty()) {
            return Result.failure() // No URL configured
        }

        // Create or get existing log entry
        val logId = if (existingLogId != -1L) {
            // This is a retry, update existing entry
            smsLogManager.updateSmsStatus(existingLogId, ForwardingStatus.RETRYING)
            existingLogId
        } else {
            // New SMS, create log entry
            smsLogManager.addSmsEntry(from, body, timestamp, subId, webhookUrl, isTest)
        }

        // Fallback: try resolving active default SMS subscription if missing
        if (subId <= 0) {
            try {
                subId = SubscriptionManager.getDefaultSmsSubscriptionId()
            } catch (_: Throwable) { /* ignore */ }
        }

        // Compose payload to match other SMS forwarder format
        val payload = createWebhookPayload(from, body, timestamp, subId, isTest)

        // Build final URL with secret if provided
        val finalUrl = buildFinalUrl(webhookUrl, ConfigurationFragment.getWebhookSecret(context))

        val media = "application/json; charset=utf-8".toMediaType()
        val req = Request.Builder()
            .url(finalUrl)
            .post(payload.toRequestBody(media))
            .addHeader("User-Agent", "Zeus-SMS-Microservice/1.0")
            .addHeader("Content-Type", "application/json")
            .build()

        return try {
            Log.d("ZeusSMS", "POSTing to $finalUrl bodyLength=${payload.length}")
            client.newCall(req).execute().use { resp ->
                Log.d("ZeusSMS", "Response code=${resp.code} success=${resp.isSuccessful}")
                when {
                    resp.isSuccessful -> {
                        // Success - update log
                        smsLogManager.updateSmsStatus(logId, ForwardingStatus.SUCCESS)
                        Result.success()
                    }
                    resp.code in 400..499 -> {
                        // Client error - don't retry
                        val errorMsg = "HTTP ${resp.code}: ${resp.message}"
                        Log.w("ZeusSMS", "Client error: $errorMsg")
                        smsLogManager.updateSmsStatus(logId, ForwardingStatus.FAILED, errorMsg)
                        Result.failure()
                    }
                    runAttemptCount < 6 -> {
                        // Server error - retry
                        val errorMsg = "HTTP ${resp.code}: ${resp.message} (attempt ${runAttemptCount + 1})"
                        Log.w("ZeusSMS", "Server error (will retry): $errorMsg")
                        smsLogManager.updateSmsStatus(logId, ForwardingStatus.RETRYING, errorMsg)
                        Result.retry()
                    }
                    else -> {
                        // Max retries reached
                        val errorMsg = "Max retries reached. Last error: HTTP ${resp.code}: ${resp.message}"
                        Log.e("ZeusSMS", errorMsg)
                        smsLogManager.updateSmsStatus(logId, ForwardingStatus.FAILED, errorMsg)
                        Result.failure()
                    }
                }
            }
        } catch (e: Exception) {
            // Network error
            val errorMsg = "Network error: ${e.message}"
            Log.e("ZeusSMS", errorMsg, e)
            if (runAttemptCount < 6) {
                smsLogManager.updateSmsStatus(logId, ForwardingStatus.RETRYING, "$errorMsg (attempt ${runAttemptCount + 1})")
                Result.retry()
            } else {
                smsLogManager.updateSmsStatus(logId, ForwardingStatus.FAILED, "$errorMsg (max retries reached)")
                Result.failure()
            }
        }
    }

    private fun buildFinalUrl(baseUrl: String, secret: String?): String {
        return if (secret.isNullOrEmpty()) {
            baseUrl
        } else {
            val uri = android.net.Uri.parse(baseUrl)
            val builder = uri.buildUpon()
            
            // Add secret as query parameter if not already present
            if (uri.getQueryParameter("secret") == null) {
                builder.appendQueryParameter("secret", secret)
            }
            
            builder.build().toString()
        }
    }

    private fun createWebhookPayload(sender: String, message: String, timestamp: Long, subscriptionId: Int, isTest: Boolean): String {
        // Generate unique message ID
        val msgId = generateUniqueId()
        
        // Format timestamp in ISO 8601 format (UTC)
        val isoTimestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(timestamp))

        // Create RCSMessage object
        val rcsMessage = JSONObject().apply {
            put("msgId", msgId)
            put("textMessage", message)
            put("timestamp", isoTimestamp)
        }

        // Create messageContact object
        val messageContact = JSONObject().apply {
            put("userContact", sender)
        }

        // Create main payload matching the other SMS forwarder format
        val payload = JSONObject().apply {
            put("RCSMessage", rcsMessage)
            put("messageContact", messageContact)
            put("event", "message")
            
            // Add Zeus-specific metadata (optional, can be used by server for identification)
            put("zeus_metadata", JSONObject().apply {
                put("service", "zeus-sms-microservice")
                put("subscription_id", subscriptionId)
                put("is_test", isTest)
                put("device", JSONObject().apply {
                    put("manufacturer", Build.MANUFACTURER)
                    put("model", Build.MODEL)
                    put("sdk", Build.VERSION.SDK_INT)
                })
                put("microservice_version", "1.0.0")
            })
        }

        return payload.toString()
    }

    private fun generateUniqueId(): String {
        // Generate a unique ID using timestamp and UUID
        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString().substring(0, 8)
        return "zeus_${timestamp}_$uuid"
    }
}
