package com.example.smshook.fragments

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.smshook.R
import com.example.smshook.sms.ForwardWorker
import java.net.URL

class ConfigurationFragment : Fragment() {

    private lateinit var editTextUrl: EditText
    private lateinit var editTextSecret: EditText
    private lateinit var buttonSaveUrl: Button
    private lateinit var textStatus: TextView
    private lateinit var sharedPreferences: SharedPreferences
    
    // Saved configuration views
    private lateinit var cardSavedConfig: androidx.cardview.widget.CardView
    private lateinit var textSavedUrl: TextView
    private lateinit var textSavedSecret: TextView
    private lateinit var buttonTestSavedUrl: Button
    private lateinit var buttonEditConfig: Button
    private lateinit var buttonDeleteConfig: Button

    companion object {
        const val PREFS_NAME = "ZeusSMSPrefs"
        const val KEY_WEBHOOK_URL = "zeus_webhook_url"
        const val KEY_WEBHOOK_SECRET = "zeus_webhook_secret"
        private const val REQUEST_SMS_PERMISSION = 1001

        fun getWebhookUrl(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_WEBHOOK_URL, null)
        }

        fun getWebhookSecret(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_WEBHOOK_SECRET, null)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_configuration, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeViews(view)
        setupEventListeners()
        loadSavedConfiguration()
        checkAndRequestPermissions()
        updateStatus()
    }

    private fun initializeViews(view: View) {
        editTextUrl = view.findViewById(R.id.editTextUrl)
        editTextSecret = view.findViewById(R.id.editTextSecret)
        buttonSaveUrl = view.findViewById(R.id.buttonSaveUrl)
        textStatus = view.findViewById(R.id.textStatus)
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Saved configuration views
        cardSavedConfig = view.findViewById(R.id.cardSavedConfig)
        textSavedUrl = view.findViewById(R.id.textSavedUrl)
        textSavedSecret = view.findViewById(R.id.textSavedSecret)
        buttonTestSavedUrl = view.findViewById(R.id.buttonTestSavedUrl)
        buttonEditConfig = view.findViewById(R.id.buttonEditConfig)
        buttonDeleteConfig = view.findViewById(R.id.buttonDeleteConfig)
    }

    private fun setupEventListeners() {
        buttonSaveUrl.setOnClickListener {
            saveConfiguration()
        }

        buttonTestSavedUrl.setOnClickListener {
            testWebhookWithSavedConfig()
        }

        buttonEditConfig.setOnClickListener {
            editConfiguration()
        }

        buttonDeleteConfig.setOnClickListener {
            deleteConfiguration()
        }
    }

    private fun loadSavedConfiguration() {
        val savedUrl = sharedPreferences.getString(KEY_WEBHOOK_URL, "")
        val savedSecret = sharedPreferences.getString(KEY_WEBHOOK_SECRET, "")
        
        // Set default URL if none saved
        if (savedUrl.isNullOrEmpty()) {
            editTextUrl.setText("https://zeus-server.example.com/api/sms/webhook")
            editTextSecret.setText("")
        } else {
            // Show saved configuration
            displaySavedConfiguration(savedUrl, savedSecret ?: "")
        }
    }

    private fun saveConfiguration() {
        val url = editTextUrl.text.toString().trim()
        val secret = editTextSecret.text.toString().trim()

        if (url.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a Zeus server URL", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isValidUrl(url)) {
            Toast.makeText(requireContext(), "Please enter a valid Zeus server URL", Toast.LENGTH_SHORT).show()
            return
        }

        // Save to SharedPreferences
        sharedPreferences.edit()
            .putString(KEY_WEBHOOK_URL, url)
            .putString(KEY_WEBHOOK_SECRET, secret)
            .apply()

        Toast.makeText(requireContext(), "Zeus configuration saved successfully!", Toast.LENGTH_SHORT).show()
        
        // Clear input fields and show saved configuration
        editTextUrl.setText("")
        editTextSecret.setText("")
        displaySavedConfiguration(url, secret)
        updateStatus()
    }

    private fun displaySavedConfiguration(url: String, secret: String) {
        textSavedUrl.text = url
        textSavedSecret.text = if (secret.isNotEmpty()) "••••••••" else "Not set"
        cardSavedConfig.visibility = View.VISIBLE
    }

    private fun testWebhookWithSavedConfig() {
        val savedUrl = sharedPreferences.getString(KEY_WEBHOOK_URL, "")
        val savedSecret = sharedPreferences.getString(KEY_WEBHOOK_SECRET, "")

        if (savedUrl.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "No Zeus configuration found", Toast.LENGTH_SHORT).show()
            return
        }

        // Create a test SMS message for Zeus
        val testWorkRequest = OneTimeWorkRequestBuilder<ForwardWorker>()
            .setInputData(
                workDataOf(
                    "from" to "ZEUS_TEST",
                    "body" to "Test message from Zeus SMS microservice",
                    "timestamp" to System.currentTimeMillis(),
                    "subscriptionId" to 1,
                    "isTest" to true
                )
            )
            .addTag("zeus-sms-forward")
            .build()

        WorkManager.getInstance(requireContext()).enqueue(testWorkRequest)
        Toast.makeText(requireContext(), "Test message sent to Zeus server...", Toast.LENGTH_SHORT).show()
    }

    private fun editConfiguration() {
        val savedUrl = sharedPreferences.getString(KEY_WEBHOOK_URL, "")
        val savedSecret = sharedPreferences.getString(KEY_WEBHOOK_SECRET, "")
        
        editTextUrl.setText(savedUrl)
        editTextSecret.setText(savedSecret)
        cardSavedConfig.visibility = View.GONE
    }

    private fun deleteConfiguration() {
        sharedPreferences.edit()
            .remove(KEY_WEBHOOK_URL)
            .remove(KEY_WEBHOOK_SECRET)
            .apply()
        
        editTextUrl.setText("https://zeus-server.example.com/api/sms/webhook")
        editTextSecret.setText("")
        cardSavedConfig.visibility = View.GONE
        updateStatus()
        
        Toast.makeText(requireContext(), "Zeus configuration deleted", Toast.LENGTH_SHORT).show()
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            val parsedUrl = URL(url)
            Patterns.WEB_URL.matcher(url).matches() && 
            (parsedUrl.protocol == "http" || parsedUrl.protocol == "https")
        } catch (e: Exception) {
            false
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissions(permissionsToRequest.toTypedArray(), REQUEST_SMS_PERMISSION)
        }
    }

    private fun updateStatus() {
        val hasUrl = !sharedPreferences.getString(KEY_WEBHOOK_URL, "").isNullOrEmpty()
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            requireContext(), 
            Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasPhonePermission = ContextCompat.checkSelfPermission(
            requireContext(), 
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val status = when {
            !hasUrl -> "⚠️ Zeus server not configured"
            !hasSmsPermission -> "⚠️ SMS permission required"
            !hasPhonePermission -> "⚠️ Phone state permission required"
            else -> "✅ Zeus SMS microservice is ready!"
        }

        textStatus.text = status
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_SMS_PERMISSION) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Toast.makeText(requireContext(), "Permissions granted! Zeus SMS is ready.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Permissions required for Zeus SMS to work", Toast.LENGTH_LONG).show()
            }
            updateStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }
}
