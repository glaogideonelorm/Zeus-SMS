package com.example.smshook

import android.app.AlertDialog
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.smshook.adapter.SmsLogAdapter
import com.example.smshook.data.ForwardingStatus
import com.example.smshook.data.SmsLogEntry
import com.example.smshook.data.SmsLogManager
import com.example.smshook.sms.ForwardWorker

class SmsLogActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var smsLogAdapter: SmsLogAdapter
    private lateinit var smsLogManager: SmsLogManager
    private lateinit var layoutEmptyState: LinearLayout
    
    // Stats views
    private lateinit var textTotalSms: TextView
    private lateinit var textSuccessfulSms: TextView
    private lateinit var textFailedSms: TextView
    private lateinit var textPendingSms: TextView
    
    // Action buttons
    private lateinit var buttonRefresh: Button
    private lateinit var buttonClearLog: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms_log)
        
        // Enable back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "SMS Log"

        initializeViews()
        setupRecyclerView()
        setupEventListeners()
        loadSmsLog()
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recyclerViewSmsLog)
        layoutEmptyState = findViewById(R.id.layoutEmptyState)
        
        // Stats views
        textTotalSms = findViewById(R.id.textTotalSms)
        textSuccessfulSms = findViewById(R.id.textSuccessfulSms)
        textFailedSms = findViewById(R.id.textFailedSms)
        textPendingSms = findViewById(R.id.textPendingSms)
        
        // Action buttons
        buttonRefresh = findViewById(R.id.buttonRefresh)
        buttonClearLog = findViewById(R.id.buttonClearLog)
        
        // Initialize SMS log manager
        smsLogManager = SmsLogManager.getInstance(this)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        smsLogAdapter = SmsLogAdapter(
            mutableListOf(),
            onRetryClick = { smsLogEntry -> retryForwarding(smsLogEntry) },
            onDetailsClick = { smsLogEntry -> showSmsDetails(smsLogEntry) }
        )
        recyclerView.adapter = smsLogAdapter
    }

    private fun setupEventListeners() {
        buttonRefresh.setOnClickListener {
            loadSmsLog()
            Toast.makeText(this, "Log refreshed", Toast.LENGTH_SHORT).show()
        }

        buttonClearLog.setOnClickListener {
            showClearLogConfirmation()
        }
    }

    private fun loadSmsLog() {
        val smsLogs = smsLogManager.getRecentSmsLogs()
        smsLogAdapter.updateData(smsLogs)
        
        // Update stats
        val stats = smsLogManager.getStats()
        textTotalSms.text = "Total: ${stats.total}"
        textSuccessfulSms.text = "✅ ${stats.successful}"
        textFailedSms.text = "❌ ${stats.failed}"
        textPendingSms.text = "⏳ ${stats.pending}"
        
        // Show/hide empty state
        if (smsLogs.isEmpty()) {
            recyclerView.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
        }
    }

    private fun retryForwarding(smsLogEntry: SmsLogEntry) {
        if (smsLogEntry.status != ForwardingStatus.FAILED) {
            Toast.makeText(this, "Can only retry failed messages", Toast.LENGTH_SHORT).show()
            return
        }

        // Update status to retrying
        smsLogManager.updateSmsStatus(smsLogEntry.id, ForwardingStatus.RETRYING)
        
        // Create retry work request
        val retryWorkRequest = OneTimeWorkRequestBuilder<ForwardWorker>()
            .setInputData(
                workDataOf(
                    "from" to smsLogEntry.sender,
                    "body" to smsLogEntry.message,
                    "timestamp" to smsLogEntry.timestamp,
                    "subscriptionId" to smsLogEntry.subscriptionId,
                    "isTest" to smsLogEntry.isTest,
                    "logId" to smsLogEntry.id
                )
            )
            .build()

        WorkManager.getInstance(this).enqueue(retryWorkRequest)
        
        // Update the entry in adapter
        val updatedEntry = smsLogEntry.copy(status = ForwardingStatus.RETRYING)
        smsLogAdapter.updateEntry(updatedEntry)
        
        Toast.makeText(this, "Retrying SMS forwarding...", Toast.LENGTH_SHORT).show()
        
        // Refresh stats
        loadSmsLog()
    }

    private fun showSmsDetails(smsLogEntry: SmsLogEntry) {
        val details = buildString {
            appendLine("📱 SMS Details")
            appendLine()
            appendLine("ID: ${smsLogEntry.id}")
            appendLine("From: ${smsLogEntry.sender}")
            appendLine("SIM: ${smsLogEntry.getSimInfo()}")
            appendLine("Received: ${smsLogEntry.getFormattedTimestamp()}")
            appendLine("Status: ${smsLogEntry.status.displayName}")
            if (smsLogEntry.retryCount > 0) {
                appendLine("Retry Count: ${smsLogEntry.retryCount}")
                appendLine("Last Attempt: ${smsLogEntry.getFormattedLastAttempt()}")
            }
            if (!smsLogEntry.webhookUrl.isNullOrEmpty()) {
                appendLine("Webhook: ${smsLogEntry.webhookUrl}")
            }
            if (smsLogEntry.isTest) {
                appendLine("Type: Test Message")
            }
            if (!smsLogEntry.errorMessage.isNullOrEmpty()) {
                appendLine()
                appendLine("Error: ${smsLogEntry.errorMessage}")
            }
            appendLine()
            appendLine("Message:")
            appendLine("\"${smsLogEntry.message}\"")
        }

        AlertDialog.Builder(this)
            .setTitle("SMS Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .setNeutralButton("Retry") { _, _ ->
                if (smsLogEntry.status == ForwardingStatus.FAILED) {
                    retryForwarding(smsLogEntry)
                }
            }
            .show()
    }

    private fun showClearLogConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Clear SMS Log")
            .setMessage("Are you sure you want to clear all SMS log entries? This action cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                smsLogManager.clearAllLogs()
                loadSmsLog()
                Toast.makeText(this, "SMS log cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Refresh the log when returning to this activity
        loadSmsLog()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
