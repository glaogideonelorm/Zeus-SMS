package com.example.smshook.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION != intent.action) return

        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val from = msgs.firstOrNull()?.displayOriginatingAddress ?: "unknown"
        val timestamp = msgs.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
        val body = msgs.joinToString(separator = "") { it.messageBody ?: "" }

        // Dual-SIM: subscription id on which this SMS arrived (per-message)
        // (available via SmsMessage on modern Android; fallback handled in Worker)
        val subId = try {
            // Use reflection to access subscriptionId safely (API 22+)
            val message = msgs.first()
            val field = message.javaClass.getMethod("getSubscriptionId")
            field.invoke(message) as Int
        } catch (_: Throwable) { -1 }

        val req = OneTimeWorkRequestBuilder<ForwardWorker>()
            .setInputData(
                workDataOf(
                    "from" to from,
                    "body" to body,
                    "timestamp" to timestamp,
                    "subscriptionId" to subId,
                    "isTest" to false
                )
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("zeus-sms-forward")
            .build()

        WorkManager.getInstance(context).enqueue(req)
    }
}
