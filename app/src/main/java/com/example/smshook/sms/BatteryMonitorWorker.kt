package com.example.smshook.sms

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class BatteryMonitorWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    private val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun doWork(): Result {
        val battery = applicationContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return Result.retry()

        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else -1

        if (percent < 0) return Result.retry()

        // Debounce logic to avoid spamming
        val last5 = prefs.getBoolean(KEY_SENT_5, false)
        val last2 = prefs.getBoolean(KEY_SENT_2, false)

        var sent = false
        if (percent <= 2 && !last2) {
            enqueueNotify(percent, isCharging, "critical")
            prefs.edit().putBoolean(KEY_SENT_2, true).apply()
            sent = true
        } else if (percent <= 5 && !last5) {
            enqueueNotify(percent, isCharging, "low")
            prefs.edit().putBoolean(KEY_SENT_5, true).apply()
            sent = true
        }

        // Reset flags when battery goes above thresholds
        if (percent >= 6 && (last5 || last2)) {
            prefs.edit().putBoolean(KEY_SENT_5, false).putBoolean(KEY_SENT_2, false).apply()
        }

        return if (sent) Result.success() else Result.success()
    }

    private fun enqueueNotify(percent: Int, isCharging: Boolean, status: String) {
        val work = OneTimeWorkRequestBuilder<BatteryNotifierWorker>()
            .setInputData(workDataOf(
                BatteryNotifierWorker.KEY_LEVEL to percent,
                BatteryNotifierWorker.KEY_STATUS to status,
                BatteryNotifierWorker.KEY_IS_CHARGING to isCharging,
                BatteryNotifierWorker.KEY_NOTE to noteFor(status)
            ))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("zeus-battery-notify")
            .build()
        WorkManager.getInstance(applicationContext).enqueue(work)
    }

    private fun noteFor(status: String): String = when (status) {
        "low" -> "Battery is below 5%. Please charge the device to continue forwarding SMS."
        "critical" -> "Battery is below 2%. Device may shut down; SMS forwarding may stop."
        else -> ""
    }

    companion object {
        private const val PREFS = "ZEUS_BATTERY_PREFS"
        private const val KEY_SENT_5 = "sent_5"
        private const val KEY_SENT_2 = "sent_2"
    }
}
