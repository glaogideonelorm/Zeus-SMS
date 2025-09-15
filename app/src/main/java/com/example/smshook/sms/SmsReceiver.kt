package com.example.smshook.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import android.content.SharedPreferences
import android.util.Log
import com.example.smshook.fragments.ConfigurationFragment

class SmsReceiver : BroadcastReceiver() {
    private val recentFingerprints = ArrayDeque<Pair<String, Long>>()
    private val maxCache = 100
    private val windowMs = 30_000L
    override fun onReceive(context: Context, intent: Intent) {
        try {
            if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) {
                Log.d("ZeusSMS", "Ignoring non-SMS intent: ${intent.action}")
                return
            }

            // Respect global forwarding toggle
            val prefs: SharedPreferences = context.getSharedPreferences("ZeusSMSPrefs", Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("zeus_forwarding_enabled", true)
            if (!enabled) {
                Log.d("ZeusSMS", "SMS forwarding is disabled")
                return
            }

            val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (msgs == null || msgs.isEmpty()) {
                Log.w("ZeusSMS", "No SMS messages found in intent")
                return
            }

            val from = msgs.firstOrNull()?.displayOriginatingAddress?.takeIf { it.isNotBlank() } ?: "unknown"
            val timestamp = msgs.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
            val body = msgs.joinToString(separator = "") { it.messageBody ?: "" }
            // Duplicate suppression within window
            val fingerprint = (from + "|" + body.take(64)).lowercase()
            val now = System.currentTimeMillis()
            while (recentFingerprints.isNotEmpty() && now - recentFingerprints.first().second > windowMs) {
                recentFingerprints.removeFirst()
            }
            if (recentFingerprints.any { it.first == fingerprint }) {
                Log.d("ZeusSMS", "Suppressed duplicate SMS within window")
                return
            }
            if (recentFingerprints.size >= maxCache) recentFingerprints.removeFirst()
            recentFingerprints.addLast(fingerprint to now)

            // Simple rules evaluation
            val senders = ConfigurationFragment.getRuleSenderContains(context)
            val includes = ConfigurationFragment.getRuleBodyIncludes(context)
            val excludes = ConfigurationFragment.getRuleBodyExcludes(context)
            val overrideUrl = ConfigurationFragment.getRuleOverrideUrl(context)

            val normalizedFrom = from.lowercase()
            val normalizedBody = body.lowercase()

            val senderPass = senders.isEmpty() || senders.any { normalizedFrom.contains(it.lowercase()) }
            val includePass = includes.isEmpty() || includes.any { normalizedBody.contains(it.lowercase()) }
            val excludeFail = excludes.any { normalizedBody.contains(it.lowercase()) }
            if (!senderPass || !includePass || excludeFail) {
                Log.d("ZeusSMS", "Rules blocked SMS; not forwarding")
                return
            }
            
            if (body.isBlank()) {
                Log.w("ZeusSMS", "Empty SMS body received")
                return
            }

            // Dual-SIM: subscription id on which this SMS arrived (per-message)
            // (available via SmsMessage on modern Android; fallback handled in Worker)
            val subId = try {
                // Use reflection to access subscriptionId safely (API 22+)
                val message = msgs.first()
                val field = message.javaClass.getMethod("getSubscriptionId")
                val result = field.invoke(message)
                if (result is Int) result else -1
            } catch (e: Exception) {
                Log.d("ZeusSMS", "Failed to get subscription ID: ${e.message}")
                -1
            }

            Log.d("ZeusSMS", "Processing SMS from: $from, length: ${body.length}")
            
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val req = OneTimeWorkRequestBuilder<ForwardWorker>()
                .setInputData(
                    workDataOf(
                        "from" to from,
                        "body" to body,
                        "timestamp" to timestamp,
                        "subscriptionId" to subId,
                        "isTest" to false,
                        "overrideUrl" to (overrideUrl ?: "")
                    )
                )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    ConfigurationFragment.getRetryBaseSeconds(context),
                    TimeUnit.SECONDS
                )
                .addTag("zeus-sms-forward")
                .build()

            // Enqueue normal work to avoid coalescing different messages
            WorkManager.getInstance(context).enqueue(req)
            
        } catch (e: Exception) {
            Log.e("ZeusSMS", "Error processing SMS", e)
        }
    }
}
