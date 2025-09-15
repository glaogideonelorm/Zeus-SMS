package com.example.smshook.data

import java.text.SimpleDateFormat
import java.util.*

data class SmsLogEntry(
    val id: Long,
    val sender: String,
    val message: String,
    val timestamp: Long,
    val subscriptionId: Int,
    var status: ForwardingStatus,
    var errorMessage: String? = null,
    var webhookUrl: String? = null,
    var retryCount: Int = 0,
    var lastAttemptTime: Long = timestamp,
    val isTest: Boolean = false,
    var attempts: MutableList<ForwardAttempt> = mutableListOf(),
    var lastHttpStatus: Int? = null,
    var lastDurationMs: Long? = null
) {
    fun getFormattedTimestamp(): String {
        val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
    
    fun getFormattedLastAttempt(): String {
        val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(lastAttemptTime))
    }
    
    fun getShortMessage(): String {
        return if (message.length > 50) {
            message.take(47) + "..."
        } else message
    }
    
    fun getSimInfo(): String {
        return when (subscriptionId) {
            -1 -> "Unknown"
            0 -> "SIM 1"
            1 -> "SIM 2"
            else -> "SIM ${subscriptionId + 1}"
        }
    }
}

enum class ForwardingStatus(val displayName: String, val colorRes: Int) {
    SUCCESS("Sent Successfully", android.R.color.holo_green_dark),
    FAILED("Failed", android.R.color.holo_red_dark),
    PENDING("Sending...", android.R.color.holo_orange_dark),
    RETRYING("Retrying...", android.R.color.holo_blue_dark)
}

data class ForwardAttempt(
    val startedAt: Long,
    var finishedAt: Long? = null,
    var httpStatus: Int? = null,
    var success: Boolean = false,
    var errorSnippet: String? = null,
    var durationMs: Long? = null
)
