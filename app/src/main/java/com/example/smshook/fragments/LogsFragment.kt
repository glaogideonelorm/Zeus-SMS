package com.example.smshook.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smshook.R
import com.example.smshook.logs.LogEntry
import com.example.smshook.logs.LogLevel
import com.example.smshook.logs.LogManager
import com.example.smshook.logs.LogsAdapter
import com.example.smshook.data.SmsLogManager
import com.example.smshook.data.SmsLogEntry
import com.example.smshook.data.ForwardingStatus
import com.example.smshook.adapter.SmsLogAdapter
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.smshook.sms.ForwardWorker
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class LogsFragment : Fragment(), LogManager.LogListener {

    private lateinit var logsRecyclerView: RecyclerView
    private lateinit var logsAdapter: LogsAdapter
    private lateinit var logCountText: TextView
    private lateinit var clearLogsButton: Button
    private lateinit var exportLogsButton: Button
    
    // SMS log components
    private lateinit var smsLogsRecyclerView: RecyclerView
    private lateinit var smsLogAdapter: SmsLogAdapter
    private lateinit var smsLogManager: SmsLogManager
    private lateinit var smsStatsText: TextView
    private lateinit var clearSmsLogsButton: Button

    private val REQUEST_WRITE_EXTERNAL_STORAGE = 101

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_logs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize FCM/USSD log views
        logsRecyclerView = view.findViewById(R.id.logsRecyclerView)
        logCountText = view.findViewById(R.id.logCountText)
        clearLogsButton = view.findViewById(R.id.clearLogsButton)
        exportLogsButton = view.findViewById(R.id.exportLogsButton)
        
        // Initialize SMS log views
        smsLogsRecyclerView = view.findViewById(R.id.smsLogsRecyclerView)
        smsStatsText = view.findViewById(R.id.smsStatsText)
        clearSmsLogsButton = view.findViewById(R.id.clearSmsLogsButton)
        
        // Set up FCM/USSD logs RecyclerView
        logsAdapter = LogsAdapter(LogManager.getLogs().toMutableList())
        logsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        logsRecyclerView.adapter = logsAdapter
        
        // Set up SMS logs RecyclerView
        smsLogManager = SmsLogManager.getInstance(requireContext())
        smsLogAdapter = SmsLogAdapter(
            mutableListOf(),
            onRetryClick = { smsLogEntry -> retrySmsForwarding(smsLogEntry) },
            onDetailsClick = { smsLogEntry -> showSmsDetails(smsLogEntry) }
        )
        smsLogsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        smsLogsRecyclerView.adapter = smsLogAdapter
        
        // Set up button listeners
        clearLogsButton.setOnClickListener {
            LogManager.clearLogs()
            logsAdapter.clearLogs()
            updateLogCount()
            Toast.makeText(requireContext(), "FCM/USSD logs cleared", Toast.LENGTH_SHORT).show()
            LogManager.addLog(LogLevel.INFO, "LogsFragment", "FCM/USSD logs cleared by user")
        }
        
        clearSmsLogsButton.setOnClickListener {
            showClearSmsLogConfirmation()
        }
        
        exportLogsButton.setOnClickListener {
            checkStoragePermissionAndExportLogs()
        }
        
        updateLogCount()
        loadSmsLogs()
        
        // Log logs fragment creation
        LogManager.addLog(LogLevel.INFO, "LogsFragment", "Logs fragment created")
    }

    override fun onResume() {
        super.onResume()
        LogManager.addLogListener(this)
        // Refresh logs in case new ones arrived while fragment was paused
        logsAdapter.clearLogs()
        logsAdapter.logs.addAll(LogManager.getLogs())
        logsAdapter.notifyDataSetChanged()
        logsRecyclerView.scrollToPosition(logsAdapter.itemCount - 1)
        updateLogCount()
        loadSmsLogs()
    }

    override fun onPause() {
        super.onPause()
        LogManager.removeLogListener(this)
    }

    override fun onLogAdded(logEntry: LogEntry) {
        activity?.runOnUiThread {
            logsAdapter.addLog(logEntry)
            logsRecyclerView.scrollToPosition(logsAdapter.itemCount - 1)
            updateLogCount()
        }
    }

    private fun updateLogCount() {
        logCountText.text = "Logs: ${logsAdapter.itemCount}"
    }

    private fun checkStoragePermissionAndExportLogs() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // For Android 10 (API 29) and above, use MediaStore or request MANAGE_EXTERNAL_STORAGE
            exportLogsToDownloads()
        } else {
            // For Android 9 (API 28) and below, request WRITE_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_WRITE_EXTERNAL_STORAGE)
            } else {
                exportLogsToFile()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportLogsToFile()
            } else {
                Toast.makeText(requireContext(), "Permission denied. Cannot export logs.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exportLogsToFile() {
        val logs = LogManager.getLogs()
        if (logs.isEmpty()) {
            Toast.makeText(requireContext(), "No logs to export", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "zeus_cloud_logs_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName)

        try {
            FileOutputStream(file).use { fos ->
                fos.write(buildLogText(logs).toByteArray())
            }
            Toast.makeText(requireContext(), "Logs exported to ${file.absolutePath}", Toast.LENGTH_LONG).show()
            LogManager.addLog(LogLevel.INFO, "LogsFragment", "Logs exported to file", file.absolutePath)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to export logs: ${e.message}", Toast.LENGTH_LONG).show()
            LogManager.addLog(LogLevel.ERROR, "LogsFragment", "Failed to export logs", e.message)
        }
    }

    private fun exportLogsToDownloads() {
        val logs = LogManager.getLogs()
        if (logs.isEmpty()) {
            Toast.makeText(requireContext(), "No logs to export", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "zeus_cloud_logs_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.txt"
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)

        try {
            FileOutputStream(file).use { fos ->
                fos.write(buildLogText(logs).toByteArray())
            }
            Toast.makeText(requireContext(), "Logs exported to Downloads/$fileName", Toast.LENGTH_LONG).show()
            LogManager.addLog(LogLevel.INFO, "LogsFragment", "Logs exported to Downloads", fileName)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Failed to export logs: ${e.message}", Toast.LENGTH_LONG).show()
            LogManager.addLog(LogLevel.ERROR, "LogsFragment", "Failed to export logs (Downloads)", e.message)
        }
    }

    private fun buildLogText(logs: List<LogEntry>): String {
        val sb = StringBuilder()
        sb.appendLine("Zeus Cloud FCM & USSD Logs")
        sb.appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
        sb.appendLine("=".repeat(50))
        sb.appendLine()

        logs.forEach { log ->
            sb.appendLine("${log.formattedTime} [${log.level.displayName}] ${log.tag}: ${log.message}")
            log.details?.let { details ->
                sb.appendLine("  Details: $details")
            }
        }
        return sb.toString()
    }
    
    private fun loadSmsLogs() {
        val smsLogs = smsLogManager.getRecentSmsLogs()
        smsLogAdapter.updateData(smsLogs)
        
        // Update SMS stats
        val stats = smsLogManager.getStats()
        smsStatsText.text = "SMS: Total ${stats.total} | ✅ ${stats.successful} | ❌ ${stats.failed} | ⏳ ${stats.pending}"
    }
    
    private fun retrySmsForwarding(smsLogEntry: SmsLogEntry) {
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
            .build()

        WorkManager.getInstance(requireContext()).enqueue(retryWorkRequest)
        
        // Update the entry in adapter
        val updatedEntry = smsLogEntry.copy(status = ForwardingStatus.RETRYING)
        smsLogAdapter.updateEntry(updatedEntry)
        
        Toast.makeText(requireContext(), "Retrying SMS forwarding...", Toast.LENGTH_SHORT).show()
        
        // Refresh stats
        loadSmsLogs()
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

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("SMS Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .setNeutralButton("Retry") { _, _ ->
                if (smsLogEntry.status == ForwardingStatus.FAILED) {
                    retrySmsForwarding(smsLogEntry)
                }
            }
            .show()
    }
    
    private fun showClearSmsLogConfirmation() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Clear SMS Log")
            .setMessage("Are you sure you want to clear all SMS log entries? This action cannot be undone.")
            .setPositiveButton("Clear") { _, _ ->
                smsLogManager.clearAllLogs()
                loadSmsLogs()
                Toast.makeText(requireContext(), "SMS log cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}



