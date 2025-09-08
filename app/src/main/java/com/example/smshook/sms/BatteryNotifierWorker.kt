package com.example.smshook.sms

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.smshook.fragments.ConfigurationFragment
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BatteryNotifierWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    override fun doWork(): Result {
        val level = inputData.getInt(KEY_LEVEL, -1)
        val status = inputData.getString(KEY_STATUS) ?: "unknown"
        val isCharging = inputData.getBoolean(KEY_IS_CHARGING, false)
        val note = inputData.getString(KEY_NOTE) ?: ""
        val timestamp = System.currentTimeMillis()

        val webhookUrl = ConfigurationFragment.getWebhookUrl(applicationContext) ?: return Result.failure()
        val finalUrl = buildFinalUrl(webhookUrl, ConfigurationFragment.getWebhookSecret(applicationContext))

        val payload = JSONObject().apply {
            put("event", "device.battery")
            put("level", level)
            put("status", status) // low|critical|okay|info
            put("is_charging", isCharging)
            put("timestamp", timestamp)
            if (note.isNotEmpty()) put("note", note)
            put("device", JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER)
                put("model", Build.MODEL)
                put("sdk", Build.VERSION.SDK_INT)
            })
        }.toString()

        val media = "application/json; charset=utf-8".toMediaType()
        val req = Request.Builder()
            .url(finalUrl)
            .post(payload.toRequestBody(media))
            .addHeader("User-Agent", "Zeus-SMS-Microservice/1.0")
            .addHeader("Content-Type", "application/json")
            .build()

        return try {
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) Result.success() else Result.retry()
            }
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun buildFinalUrl(baseUrl: String, secret: String?): String {
        return if (secret.isNullOrEmpty()) {
            baseUrl
        } else {
            val uri = android.net.Uri.parse(baseUrl)
            val builder = uri.buildUpon()
            if (uri.getQueryParameter("secret") == null) {
                builder.appendQueryParameter("secret", secret)
            }
            builder.build().toString()
        }
    }

    companion object {
        const val KEY_LEVEL = "battery_level"
        const val KEY_STATUS = "battery_status"
        const val KEY_IS_CHARGING = "battery_is_charging"
        const val KEY_NOTE = "battery_note"
    }
}
