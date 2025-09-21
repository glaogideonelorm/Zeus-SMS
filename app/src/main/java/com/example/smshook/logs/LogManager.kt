package com.example.smshook.logs

import android.util.Log
import java.util.concurrent.CopyOnWriteArrayList

object LogManager {
    private val logs = CopyOnWriteArrayList<LogEntry>()
    private val listeners = CopyOnWriteArrayList<LogListener>()
    private const val MAX_LOGS = 1000 // Limit the number of logs to prevent OOM

    interface LogListener {
        fun onLogAdded(logEntry: LogEntry)
    }

    fun addLog(level: LogLevel, tag: String, message: String, details: String? = null) {
        val logEntry = LogEntry(level = level, tag = tag, message = message, details = details)
        if (logs.size >= MAX_LOGS) {
            logs.removeAt(0) // Remove the oldest log
        }
        logs.add(logEntry)
        
        // Also log to Android system logs
        when (level) {
            LogLevel.DEBUG -> Log.d("ZeusAppLog", "${logEntry.formattedTime} [${logEntry.level.displayName}] ${logEntry.tag}: ${logEntry.message} ${logEntry.details ?: ""}")
            LogLevel.INFO -> Log.i("ZeusAppLog", "${logEntry.formattedTime} [${logEntry.level.displayName}] ${logEntry.tag}: ${logEntry.message} ${logEntry.details ?: ""}")
            LogLevel.WARN -> Log.w("ZeusAppLog", "${logEntry.formattedTime} [${logEntry.level.displayName}] ${logEntry.tag}: ${logEntry.message} ${logEntry.details ?: ""}")
            LogLevel.ERROR -> Log.e("ZeusAppLog", "${logEntry.formattedTime} [${logEntry.level.displayName}] ${logEntry.tag}: ${logEntry.message} ${logEntry.details ?: ""}")
            else -> Log.i("ZeusAppLog", "${logEntry.formattedTime} [${logEntry.level.displayName}] ${logEntry.tag}: ${logEntry.message} ${logEntry.details ?: ""}")
        }
        
        listeners.forEach { it.onLogAdded(logEntry) }
    }

    fun getLogs(): List<LogEntry> {
        return logs.toList()
    }

    fun clearLogs() {
        logs.clear()
    }

    fun addLogListener(listener: LogListener) {
        listeners.add(listener)
    }

    fun removeLogListener(listener: LogListener) {
        listeners.remove(listener)
    }
}







