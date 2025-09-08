package com.example.smshook.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class BatteryEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BATTERY_LOW -> {
                val percent = readBatteryPercent(context)
                enqueueNotify(context, percent, false, "low")
            }
            Intent.ACTION_BATTERY_OKAY -> {
                val percent = readBatteryPercent(context)
                enqueueNotify(context, percent, false, "okay")
            }
        }
    }

    private fun readBatteryPercent(context: Context): Int {
        val iFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val battery = context.registerReceiver(null, iFilter)
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        return if (level >= 0 && scale > 0) (level * 100) / scale else -1
    }

    private fun enqueueNotify(context: Context, percent: Int, isCharging: Boolean, status: String) {
        val work = OneTimeWorkRequestBuilder<BatteryNotifierWorker>()
            .setInputData(workDataOf(
                BatteryNotifierWorker.KEY_LEVEL to percent,
                BatteryNotifierWorker.KEY_STATUS to status,
                BatteryNotifierWorker.KEY_IS_CHARGING to isCharging,
                BatteryNotifierWorker.KEY_NOTE to noteFor(status)
            ))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("zeus-battery-event")
            .build()
        WorkManager.getInstance(context).enqueue(work)
    }

    private fun noteFor(status: String): String = when (status) {
        "low" -> "System reported ACTION_BATTERY_LOW. Device battery is low."
        "okay" -> "System reported ACTION_BATTERY_OKAY. Battery level recovered."
        else -> ""
    }
}
