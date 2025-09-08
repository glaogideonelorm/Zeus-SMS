package com.example.smshook.data

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.atomic.AtomicLong

class SmsLogManager private constructor(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "SMS_LOG_PREFS"
        private const val KEY_SMS_LOG = "sms_log_entries"
        private const val KEY_NEXT_ID = "next_id"
        private const val MAX_LOG_ENTRIES = 100
        
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
    private val _smsLogLiveData = MutableLiveData<List<SmsLogEntry>>()
    val smsLogLiveData: LiveData<List<SmsLogEntry>> = _smsLogLiveData
    
    private val _statsLiveData = MutableLiveData<SmsLogStats>()
    val statsLiveData: LiveData<SmsLogStats> = _statsLiveData
    
    init {
        loadLogEntries()
        updateLiveData()
    }
    
    private fun loadLogEntries() {
        val json = sharedPreferences.getString(KEY_SMS_LOG, null)
        if (json != null) {
            try {
                val type = object : TypeToken<MutableList<SmsLogEntry>>() {}.type
                val loadedEntries: MutableList<SmsLogEntry> = gson.fromJson(json, type)
                logEntries.clear()
                logEntries.addAll(loadedEntries.takeLast(MAX_LOG_ENTRIES))
            } catch (e: Exception) {
                // If there's an error loading, start fresh
                logEntries.clear()
            }
        }
    }
    
    private fun saveLogEntries() {
        val json = gson.toJson(logEntries.takeLast(MAX_LOG_ENTRIES))
        sharedPreferences.edit()
            .putString(KEY_SMS_LOG, json)
            .putLong(KEY_NEXT_ID, nextId.get())
            .apply()
        updateLiveData()
    }
    
    private fun updateLiveData() {
        val sortedEntries = logEntries.sortedByDescending { it.timestamp }.toList()
        _smsLogLiveData.postValue(sortedEntries)
        _statsLiveData.postValue(getStats())
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
        
        synchronized(logEntries) {
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
        synchronized(logEntries) {
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
    
    fun getRecentSmsLogs(): List<SmsLogEntry> {
        synchronized(logEntries) {
            return logEntries.sortedByDescending { it.timestamp }.toList()
        }
    }
    
    fun getSmsLogById(id: Long): SmsLogEntry? {
        synchronized(logEntries) {
            return logEntries.find { it.id == id }
        }
    }
    
    fun clearAllLogs() {
        synchronized(logEntries) {
            logEntries.clear()
        }
        sharedPreferences.edit().clear().apply()
        updateLiveData()
    }
    
    fun getStats(): SmsLogStats {
        synchronized(logEntries) {
            val total = logEntries.size
            val successful = logEntries.count { it.status == ForwardingStatus.SUCCESS }
            val failed = logEntries.count { it.status == ForwardingStatus.FAILED }
            val pending = logEntries.count { it.status == ForwardingStatus.PENDING || it.status == ForwardingStatus.RETRYING }
            
            return SmsLogStats(total, successful, failed, pending)
        }
    }
}

data class SmsLogStats(
    val total: Int,
    val successful: Int,
    val failed: Int,
    val pending: Int
)
