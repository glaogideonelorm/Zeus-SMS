package com.example.smshook.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.smshook.R
import com.example.smshook.adapter.SmsLogAdapter
import com.example.smshook.data.ForwardingStatus
import com.example.smshook.data.SmsLogEntry
import com.example.smshook.data.SmsLogManager
import com.example.smshook.data.SmsLogStats
import com.example.smshook.sms.ForwardWorker

class ActivityLogFragment : Fragment() {

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_activity_log, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeViews(view)
        setupRecyclerView()
        setupEventListeners()
        observeLiveData()
    }

    private fun initializeViews(view: View) {
        recyclerView = view.findViewById(R.id.recyclerViewSmsLog)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
        
        // Stats views
        textTotalSms = view.findViewById(R.id.textTotalSms)
        textSuccessfulSms = view.findViewById(R.id.textSuccessfulSms)
        textFailedSms = view.findViewById(R.id.textFailedSms)
        textPendingSms = view.findViewById(R.id.textPendingSms)
        
        // Action buttons
        buttonRefresh = view.findViewById(R.id.buttonRefresh)
        buttonClearLog = view.findViewById(R.id.buttonClearLog)
        
        // Initialize SMS log manager
        smsLogManager = SmsLogManager.getInstance(requireContext())
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        smsLogAdapter = SmsLogAdapter(
            mutableListOf(),
            onRetryClick = { smsLogEntry -> retryForwarding(smsLogEntry) },
            onDetailsClick = { smsLogEntry -> showSmsDetails(smsLogEntry) }
        )
        recyclerView.adapter = smsLogAdapter
    }

    private fun setupEventListeners() {
        buttonRefresh.setOnClickListener {
            // Force refresh from data source
            smsLogManager.getRecentSmsLogs() // This will trigger LiveData update
            Toast.makeText(requireContext(), "Zeus SMS log refreshed", Toast.LENGTH_SHORT).show()
        }

        buttonClearLog.setOnClickListener {
            showClearLogConfirmation()
        }
    }

    private fun observeLiveData() {
        // Observe SMS log data changes
        smsLogManager.smsLogLiveData.observe(viewLifecycleOwner, Observer { smsLogs ->
            updateSmsLogDisplay(smsLogs)
        })

        // Observe stats changes
        smsLogManager.statsLiveData.observe(viewLifecycleOwner, Observer { stats ->
            updateStatsDisplay(stats)
        })
    }

    private fun updateSmsLogDisplay(smsLogs: List<SmsLogEntry>) {
        smsLogAdapter.updateData(smsLogs)
        
        // Show/hide empty state
        if (smsLogs.isEmpty()) {
            recyclerView.visibility = View.GONE
            layoutEmptyState.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            layoutEmptyState.visibility = View.GONE
        }
    }

    private fun updateStatsDisplay(stats: SmsLogStats) {
        textTotalSms.text = "Total: ${stats.total}"
        textSuccessfulSms.text = "✅ ${stats.successful}"
        textFailedSms.text = "❌ ${stats.failed}"
        textPendingSms.text = "⏳ ${stats.pending}"
    }

    private fun retryForwarding(smsLogEntry: SmsLogEntry) {
        if (smsLogEntry.status != ForwardingStatus.FAILED) {
            Toast.makeText(requireContext(), "Can only retry failed messages", Toast.LENGTH_SHORT).show()
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
            .addTag("zeus-sms-forward")
            .build()

        WorkManager.getInstance(requireContext()).enqueue(retryWorkRequest)
        
        // Update the entry in adapter
        val updatedEntry = smsLogEntry.copy(status = ForwardingStatus.RETRYING)
        smsLogAdapter.updateEntry(updatedEntry)
        
        Toast.makeText(requireContext(), "Retrying Zeus SMS forwarding...", Toast.LENGTH_SHORT).show()
        
        // LiveData will automatically update the UI when status changes
    }

    private fun showSmsDetails(smsLogEntry: SmsLogEntry) {
        val details = buildString {
            appendLine("⚡ Zeus SMS Details")
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
                appendLine("Zeus Server: ${smsLogEntry.webhookUrl}")
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

        AlertDialog.Builder(requireContext())
            .setTitle("Zeus SMS Details")
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
        AlertDialog.Builder(requireContext())
            .setTitle("Clear Zeus SMS Log")
            .setMessage("Are you sure you want to clear all Zeus SMS log entries? This action cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                smsLogManager.clearAllLogs()
                Toast.makeText(requireContext(), "Zeus SMS log cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // LiveData will automatically update when fragment resumes
        // No manual refresh needed
    }
}
