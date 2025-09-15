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
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.net.URL
import java.net.MalformedURLException
import okhttp3.CertificatePinner
import okhttp3.ConnectionPool
import okhttp3.Protocol

class ForwardWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    private val context = ctx
    private val smsLogManager = SmsLogManager.getInstance(ctx)
    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(45, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            // Add response validation
            if (!response.isSuccessful && response.code >= 500) {
                Log.w("ZeusSMS", "Server error detected: ${response.code}")
            }
            response
        }
        .build()

    override fun doWork(): Result {
        Log.d("ZeusSMS", "ForwardWorker started (runAttemptCount=$runAttemptCount)")
        val from = inputData.getString("from") ?: return Result.failure()
        val body = inputData.getString("body") ?: return Result.failure()
        val timestamp = inputData.getLong("timestamp", System.currentTimeMillis())
        var subId = inputData.getInt("subscriptionId", -1)
        val isTest = inputData.getBoolean("isTest", false)
        val existingLogId = inputData.getLong("logId", -1L)

        // Get webhook URL from SharedPreferences with validation
        val webhookUrl = (inputData.getString("overrideUrl")?.takeIf { it.isNotBlank() })
            ?: ConfigurationFragment.getWebhookUrl(context)
        if (webhookUrl.isNullOrEmpty()) {
            // For new SMS without webhook URL, create a failed entry
            if (existingLogId == -1L) {
                val failedLogId = smsLogManager.addSmsEntry(from, body, timestamp, subId, null, isTest)
                smsLogManager.updateSmsStatus(failedLogId, ForwardingStatus.FAILED, "No webhook URL configured")
            } else {
                smsLogManager.updateSmsStatus(existingLogId, ForwardingStatus.FAILED, "No webhook URL configured")
            }
            return Result.failure()
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

        // Validate URL for security
        if (!isSecureUrl(webhookUrl)) {
            val errorMsg = "Invalid or potentially unsafe URL detected"
            Log.e("ZeusSMS", errorMsg)
            smsLogManager.updateSmsStatus(logId, ForwardingStatus.FAILED, errorMsg)
            return Result.failure()
        }

        // Fallback: try resolving active default SMS subscription if missing
        if (subId <= 0) {
            try {
                subId = SubscriptionManager.getDefaultSmsSubscriptionId()
            } catch (_: Throwable) { /* ignore */ }
        }

        // Compose payload to match other SMS forwarder format
        val payload = createWebhookPayload(from, body, timestamp, subId, isTest)

        // Build request and move secret to header instead of query param
        val secret = ConfigurationFragment.getWebhookSecret(context)
        val media = "application/json; charset=utf-8".toMediaType()
        val reqBuilder = Request.Builder()
            .url(webhookUrl)
            .post(payload.toRequestBody(media))
            .addHeader("User-Agent", "Zeus-SMS-Microservice/1.0")
            .addHeader("Content-Type", "application/json")
        if (!secret.isNullOrEmpty()) {
            reqBuilder.addHeader("X-Webhook-Secret", secret)
        }
        val req = reqBuilder.build()

        return try {
            val attemptIndex = smsLogManager.recordAttemptStart(logId)
            val startedAt = System.currentTimeMillis()
            // Log without exposing the full URL (which may contain secrets)
            val sanitizedUrl = sanitizeUrlForLogging(webhookUrl)
            Log.d("ZeusSMS", "POSTing to $sanitizedUrl bodyLength=${payload.length}")
            client.newCall(req).execute().use { resp ->
                Log.d("ZeusSMS", "Response code=${resp.code} success=${resp.isSuccessful}")
                when {
                    resp.isSuccessful -> {
                        // Success - update log
                        smsLogManager.recordAttemptFinish(
                            logId,
                            attemptIndex,
                            httpStatus = resp.code,
                            success = true,
                            errorSnippet = null,
                            durationMs = System.currentTimeMillis() - startedAt
                        )
                        smsLogManager.updateSmsStatus(logId, ForwardingStatus.SUCCESS)
                        Result.success()
                    }
                    resp.code in 400..499 -> {
                        // Client error - don't retry
                        val errorMsg = "HTTP ${resp.code}: ${resp.message}"
                        Log.w("ZeusSMS", "Client error: $errorMsg")
                        smsLogManager.recordAttemptFinish(
                            logId,
                            attemptIndex,
                            httpStatus = resp.code,
                            success = false,
                            errorSnippet = errorMsg.take(200),
                            durationMs = System.currentTimeMillis() - startedAt
                        )
                        smsLogManager.updateSmsStatus(logId, ForwardingStatus.FAILED, errorMsg)
                        Result.failure()
                    }
                    runAttemptCount < ConfigurationFragment.getRetryMaxAttempts(context) -> {
                        // Server error - rely on WorkManager backoff
                        val errorMsg = "HTTP ${resp.code}: ${resp.message} (attempt ${runAttemptCount + 1})"
                        Log.w("ZeusSMS", "Server error (will retry): $errorMsg")
                        smsLogManager.recordAttemptFinish(
                            logId,
                            attemptIndex,
                            httpStatus = resp.code,
                            success = false,
                            errorSnippet = errorMsg.take(200),
                            durationMs = System.currentTimeMillis() - startedAt
                        )
                        smsLogManager.updateSmsStatus(logId, ForwardingStatus.RETRYING, errorMsg)
                        Result.retry()
                    }
                    else -> {
                        // Max retries reached
                        val errorMsg = "Max retries reached. Last error: HTTP ${resp.code}: ${resp.message}"
                        Log.e("ZeusSMS", errorMsg)
                        smsLogManager.recordAttemptFinish(
                            logId,
                            attemptIndex,
                            httpStatus = resp.code,
                            success = false,
                            errorSnippet = errorMsg.take(200),
                            durationMs = System.currentTimeMillis() - startedAt
                        )
                        smsLogManager.updateSmsStatus(logId, ForwardingStatus.FAILED, errorMsg)
                        Result.failure()
                    }
                }
            }
        } catch (e: Exception) {
            // Network error
            val errorMsg = "Network error: ${e.message}"
            Log.e("ZeusSMS", errorMsg, e)
            val attemptIndex = smsLogManager.recordAttemptStart(logId)
            smsLogManager.recordAttemptFinish(
                logId,
                attemptIndex,
                httpStatus = null,
                success = false,
                errorSnippet = errorMsg.take(200),
                durationMs = null
            )
            if (runAttemptCount < ConfigurationFragment.getRetryMaxAttempts(context)) {
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

    /**
     * Validates URL for security - prevents SSRF and other attacks
     */
    private fun isSecureUrl(url: String): Boolean {
        return try {
            val parsedUrl = URL(url)
            
            // Must use HTTPS for production
            if (parsedUrl.protocol != "https" && parsedUrl.protocol != "http") {
                return false
            }
            
            // Block localhost, private IPs, and internal networks
            val host = parsedUrl.host.lowercase()
            when {
                host == "localhost" || host == "127.0.0.1" -> false
                host.startsWith("192.168.") -> false
                host.startsWith("10.") -> false
                host.matches(Regex("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) -> false
                host.startsWith("169.254.") -> false // Link-local
                host.startsWith("0.") -> false
                host == "::1" -> false // IPv6 localhost
                host.startsWith("fc") || host.startsWith("fd") -> false // IPv6 private
                else -> true
            }
        } catch (e: MalformedURLException) {
            false
        }
    }

    /**
     * Removes sensitive information from URLs for logging
     */
    private fun sanitizeUrlForLogging(url: String): String {
        return try {
            val parsedUrl = URL(url)
            "${parsedUrl.protocol}://${parsedUrl.host}${parsedUrl.path}***"
        } catch (e: Exception) {
            "[URL parsing error]"
        }
    }
}
