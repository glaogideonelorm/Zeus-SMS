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
import androidx.work.BackoffPolicy
import java.util.concurrent.TimeUnit
import android.util.Log
import kotlinx.coroutines.*
import androidx.lifecycle.lifecycleScope

class ActivityLogFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var smsLogAdapter: SmsLogAdapter
    private lateinit var smsLogManager: SmsLogManager
    private lateinit var layoutEmptyState: LinearLayout
    
    private var refreshJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
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
            onRetryClick = { smsLogEntry -> retryOrResend(smsLogEntry) },
            onDetailsClick = { smsLogEntry -> showSmsDetails(smsLogEntry) }
        )
        recyclerView.adapter = smsLogAdapter
    }

    private fun setupEventListeners() {
        buttonRefresh.setOnClickListener {
            smsLogManager.refresh()
            Toast.makeText(requireContext(), "Zeus SMS log refreshed", Toast.LENGTH_SHORT).show()
        }

        buttonClearLog.setOnClickListener {
            showClearLogConfirmation()
        }
    }
    
    private fun performRefresh() {
        // Cancel any ongoing refresh
        refreshJob?.cancel()
        
        refreshJob = coroutineScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    // Force a refresh from data source
                    smsLogManager.getRecentSmsLogs()
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "Zeus SMS log refreshed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("ActivityLogFragment", "Error refreshing log", e)
                    Toast.makeText(requireContext(), "Failed to refresh log", Toast.LENGTH_SHORT).show()
                }
            }
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

    private fun retryOrResend(smsLogEntry: SmsLogEntry) {
        when (smsLogEntry.status) {
            ForwardingStatus.FAILED -> retryForwarding(smsLogEntry)
            ForwardingStatus.SUCCESS -> resendMessage(smsLogEntry)
            else -> Toast.makeText(requireContext(), "Only failed or successful messages can be actioned", Toast.LENGTH_SHORT).show()
        }
    }

    private fun retryForwarding(smsLogEntry: SmsLogEntry) {
        // Keep retry semantics: update existing entry to RETRYING
        enqueueWorkForEntry(smsLogEntry, updateExisting = true)
        Toast.makeText(requireContext(), "Retrying Zeus SMS forwarding...", Toast.LENGTH_SHORT).show()
    }

    private fun resendMessage(smsLogEntry: SmsLogEntry) {
        // Resend semantics: create a NEW log entry to preserve history
        val newId = smsLogManager.addSmsEntry(
            smsLogEntry.sender,
            smsLogEntry.message,
            System.currentTimeMillis(),
            smsLogEntry.subscriptionId,
            smsLogManager.getSmsLogById(smsLogEntry.id)?.webhookUrl,
            smsLogEntry.isTest
        )
        val newEntry = smsLogManager.getSmsLogById(newId) ?: return
        enqueueWorkForEntry(newEntry, updateExisting = true)
        Toast.makeText(requireContext(), "Resending Zeus SMS...", Toast.LENGTH_SHORT).show()
    }

    private fun enqueueWorkForEntry(entry: SmsLogEntry, updateExisting: Boolean) {
        if (updateExisting) {
            smsLogManager.updateSmsStatus(entry.id, ForwardingStatus.RETRYING)
        }

        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val work = OneTimeWorkRequestBuilder<ForwardWorker>()
            .setInputData(
                workDataOf(
                    "from" to entry.sender,
                    "body" to entry.message,
                    "timestamp" to entry.timestamp,
                    "subscriptionId" to entry.subscriptionId,
                    "isTest" to entry.isTest,
                    "logId" to entry.id
                )
            )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("zeus-sms-forward")
            .build()

        val wm = WorkManager.getInstance(requireContext())
        wm.enqueue(work)
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
                // Sanitize URL for display (remove secrets)
                val sanitizedUrl = sanitizeUrlForDisplay(smsLogEntry.webhookUrl!!)
                appendLine("Zeus Server: $sanitizedUrl")
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
            // Truncate very long messages for display
            val displayMessage = if (smsLogEntry.message.length > 500) {
                smsLogEntry.message.take(497) + "..."
            } else smsLogEntry.message
            appendLine("\"$displayMessage\"")
        }

        val retryButtonText = when (smsLogEntry.status) {
            ForwardingStatus.SUCCESS -> "Resend"
            ForwardingStatus.FAILED -> "Retry"
            else -> "Retry"
        }
        
        val canRetryResend = smsLogEntry.status == ForwardingStatus.FAILED || 
                           smsLogEntry.status == ForwardingStatus.SUCCESS
        
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Zeus SMS Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
        
        if (canRetryResend) {
            dialog.setNeutralButton(retryButtonText) { _, _ ->
                retryForwarding(smsLogEntry)
            }
        }
        
        dialog.show()
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

    private fun sanitizeUrlForDisplay(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            "${uri.scheme}://${uri.host}${uri.path}***"
        } catch (e: Exception) {
            "[URL display error]"
        }
    }
    
    override fun onResume() {
        super.onResume()
        // LiveData will automatically update when fragment resumes
        // No manual refresh needed
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // Cancel any ongoing operations
        refreshJob?.cancel()
        coroutineScope.cancel()
    }
}
