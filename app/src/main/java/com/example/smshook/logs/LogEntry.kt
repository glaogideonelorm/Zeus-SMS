package com.example.smshook.logs

import java.text.SimpleDateFormat
import java.util.*

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val details: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

enum class LogLevel(val displayName: String, val color: String) {
    DEBUG("DEBUG", "#808080"), // Gray
    INFO("INFO", "#000000"),   // Black
    WARN("WARN", "#FFA500"),   // Orange
    ERROR("ERROR", "#FF0000"), // Red
    FCM("FCM", "#4CAF50"),     // Green
    USSD("USSD", "#2196F3"),   // Blue
    API("API", "#9C27B0")      // Purple
}







