package com.example.smshook.data

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.BufferedWriter
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class SmsLogManager private constructor(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "SMS_LOG_PREFS"
        private const val KEY_SMS_LOG = "sms_log_entries"
        private const val KEY_NEXT_ID = "next_id"
        private const val MAX_LOG_ENTRIES = 500
        private const val BACKUP_FILE_NAME = "sms_log_backup.json"
        private const val AUTO_BACKUP_INTERVAL_MINUTES = 30L
        
        @Volatile
        private var INSTANCE: SmsLogManager? = null
        
        fun getInstance(context: Context): SmsLogManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SmsLogManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val nextId = AtomicLong(sharedPreferences.getLong(KEY_NEXT_ID, 1))
    
    private val logEntries = mutableListOf<SmsLogEntry>()
    private val rwLock = ReentrantReadWriteLock()
    private val _smsLogLiveData = MutableLiveData<List<SmsLogEntry>>()
    val smsLogLiveData: LiveData<List<SmsLogEntry>> = _smsLogLiveData
    
    private val _statsLiveData = MutableLiveData<SmsLogStats>()
    val statsLiveData: LiveData<SmsLogStats> = _statsLiveData
    
    private val backupExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val backupFile = File(context.filesDir, BACKUP_FILE_NAME)
    
    init {
        loadLogEntries()
        updateLiveData()
        schedulePeriodicBackup()
    }
    
    private fun loadLogEntries() {
        rwLock.write {
            try {
                val json = sharedPreferences.getString(KEY_SMS_LOG, null)
                if (json != null) {
                    val type = object : TypeToken<MutableList<SmsLogEntry>>() {}.type
                    val loadedEntries: MutableList<SmsLogEntry> = gson.fromJson(json, type)
                    logEntries.clear()
                    logEntries.addAll(loadedEntries.takeLast(MAX_LOG_ENTRIES))
                } else {
                    // Try to load from backup if primary fails
                    loadFromBackup()
                }
            } catch (e: Exception) {
                Log.e("SmsLogManager", "Error loading log entries", e)
                // Try backup before clearing
                try {
                    loadFromBackup()
                } catch (backupError: Exception) {
                    Log.e("SmsLogManager", "Backup also failed, starting fresh", backupError)
                    logEntries.clear()
                }
            }
        }
    }
    
    private fun saveLogEntries() {
        rwLock.read {
            try {
                val entriesToSave = logEntries.takeLast(MAX_LOG_ENTRIES)
                val json = gson.toJson(entriesToSave)
                
                // Atomic save operation
                sharedPreferences.edit()
                    .putString(KEY_SMS_LOG, json)
                    .putLong(KEY_NEXT_ID, nextId.get())
                    .apply()
                    
                updateLiveData()
            } catch (e: Exception) {
                Log.e("SmsLogManager", "Error saving log entries", e)
            }
        }
    }
    
    private fun updateLiveData() {
        rwLock.read {
            val sortedEntries = logEntries.sortedByDescending { it.timestamp }.toList()
            _smsLogLiveData.postValue(sortedEntries)
            _statsLiveData.postValue(getStatsInternal())
        }
    }
    
    fun addSmsEntry(
        sender: String,
        message: String,
        timestamp: Long,
        subscriptionId: Int,
        webhookUrl: String?,
        isTest: Boolean = false
    ): Long {
        val id = nextId.getAndIncrement()
        val entry = SmsLogEntry(
            id = id,
            sender = sender,
            message = message,
            timestamp = timestamp,
            subscriptionId = subscriptionId,
            status = ForwardingStatus.PENDING,
            webhookUrl = webhookUrl,
            isTest = isTest
        )
        
        rwLock.write {
            logEntries.add(entry)
            // Keep only the most recent entries
            if (logEntries.size > MAX_LOG_ENTRIES) {
                logEntries.removeAt(0)
            }
        }
        
        saveLogEntries()
        return id
    }
    
    fun updateSmsStatus(
        id: Long,
        status: ForwardingStatus,
        errorMessage: String? = null
    ) {
        rwLock.write {
            val entry = logEntries.find { it.id == id }
            entry?.let {
                it.status = status
                it.errorMessage = errorMessage
                it.lastAttemptTime = System.currentTimeMillis()
                if (status == ForwardingStatus.RETRYING) {
                    it.retryCount++
                }
            }
        }
        saveLogEntries()
    }

    fun recordAttemptStart(id: Long): Int {
        rwLock.write {
            val entry = logEntries.find { it.id == id } ?: return -1
            entry.attempts.add(ForwardAttempt(startedAt = System.currentTimeMillis()))
            saveLogEntries()
            return entry.attempts.lastIndex
        }
    }

    fun recordAttemptFinish(id: Long, attemptIndex: Int, httpStatus: Int?, success: Boolean, errorSnippet: String?, durationMs: Long?) {
        rwLock.write {
            val entry = logEntries.find { it.id == id } ?: return@write
            if (attemptIndex in entry.attempts.indices) {
                val att = entry.attempts[attemptIndex]
                att.finishedAt = System.currentTimeMillis()
                att.httpStatus = httpStatus
                att.success = success
                att.errorSnippet = errorSnippet
                att.durationMs = durationMs
                entry.lastHttpStatus = httpStatus
                entry.lastDurationMs = durationMs
            }
        }
        saveLogEntries()
    }
    
    fun getRecentSmsLogs(): List<SmsLogEntry> {
        rwLock.read {
            return logEntries.sortedByDescending { it.timestamp }.toList()
        }
    }

    // Force observers to refresh from current in-memory state
    fun refresh() {
        updateLiveData()
    }
    
    fun getSmsLogById(id: Long): SmsLogEntry? {
        rwLock.read {
            return logEntries.find { it.id == id }
        }
    }
    
    fun clearAllLogs() {
        rwLock.write {
            logEntries.clear()
        }
        sharedPreferences.edit().clear().apply()
        // Also clear backup
        backupFile.delete()
        updateLiveData()
    }
    
    fun getStats(): SmsLogStats {
        rwLock.read {
            return getStatsInternal()
        }
    }
    
    private fun getStatsInternal(): SmsLogStats {
        val total = logEntries.size
        val successful = logEntries.count { it.status == ForwardingStatus.SUCCESS }
        val failed = logEntries.count { it.status == ForwardingStatus.FAILED }
        val pending = logEntries.count { it.status == ForwardingStatus.PENDING || it.status == ForwardingStatus.RETRYING }
        
        return SmsLogStats(total, successful, failed, pending)
    }
    
    private fun schedulePeriodicBackup() {
        backupExecutor.scheduleAtFixedRate({
            createBackup()
        }, AUTO_BACKUP_INTERVAL_MINUTES, AUTO_BACKUP_INTERVAL_MINUTES, TimeUnit.MINUTES)
    }
    
    private fun createBackup() {
        try {
            rwLock.read {
                val json = gson.toJson(logEntries)
                BufferedWriter(FileWriter(backupFile)).use { writer ->
                    writer.write(json)
                }
                Log.d("SmsLogManager", "Backup created successfully")
            }
        } catch (e: Exception) {
            Log.e("SmsLogManager", "Failed to create backup", e)
        }
    }
    
    private fun loadFromBackup() {
        if (backupFile.exists()) {
            val json = backupFile.readText()
            val type = object : TypeToken<MutableList<SmsLogEntry>>() {}.type
            val backupEntries: MutableList<SmsLogEntry> = gson.fromJson(json, type)
            logEntries.clear()
            logEntries.addAll(backupEntries.takeLast(MAX_LOG_ENTRIES))
            Log.d("SmsLogManager", "Loaded ${logEntries.size} entries from backup")
        }
    }
    
    /**
     * Cleanup resources when the manager is no longer needed
     */
    fun cleanup() {
        backupExecutor.shutdown()
        try {
            if (!backupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                backupExecutor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            backupExecutor.shutdownNow()
        }
    }
}

data class SmsLogStats(
    val total: Int,
    val successful: Int,
    val failed: Int,
    val pending: Int
)
