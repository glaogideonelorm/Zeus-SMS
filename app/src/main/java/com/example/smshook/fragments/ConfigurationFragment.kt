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
import com.google.android.material.switchmaterial.SwitchMaterial
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
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.security.MessageDigest
import java.net.MalformedURLException
import java.util.regex.Pattern

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
    private lateinit var switchForwarding: SwitchMaterial

    companion object {
        const val PREFS_NAME = "ZeusSMSPrefs"
        const val KEY_WEBHOOK_URL = "zeus_webhook_url"
        const val KEY_WEBHOOK_SECRET = "zeus_webhook_secret"
        const val KEY_FORWARDING_ENABLED = "zeus_forwarding_enabled"
        const val KEY_RETRY_MAX_ATTEMPTS = "zeus_retry_max_attempts"
        const val KEY_RETRY_BASE_SECONDS = "zeus_retry_base_seconds"
        // Rules config
        const val KEY_RULE_SENDER_CONTAINS = "zeus_rule_sender_contains" // CSV
        const val KEY_RULE_BODY_INCLUDES = "zeus_rule_body_includes" // CSV
        const val KEY_RULE_BODY_EXCLUDES = "zeus_rule_body_excludes" // CSV
        const val KEY_RULE_OVERRIDE_URL = "zeus_rule_override_url"
        private const val REQUEST_SMS_PERMISSION = 1001
        private const val ENCRYPTION_KEY = "ZeusSMSEncryptionKey2024!"
        
        // URL validation patterns
        private val VALID_HOSTNAME_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?" +
            "(\\.[a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?)*$"
        )

        fun getWebhookUrl(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_WEBHOOK_URL, null)
        }

        fun getWebhookSecret(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val encryptedSecret = prefs.getString(KEY_WEBHOOK_SECRET, null)
            return if (encryptedSecret != null) {
                try {
                    decryptSecret(encryptedSecret)
                } catch (e: Exception) {
                    null // Return null if decryption fails
                }
            } else null
        }
        
        private fun encryptSecret(secret: String): String {
            val cipher = Cipher.getInstance("AES/ECB/PKCS1Padding")
            val keySpec = SecretKeySpec(ENCRYPTION_KEY.toByteArray().take(16).toByteArray(), "AES")
            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            val encrypted = cipher.doFinal(secret.toByteArray())
            return Base64.encodeToString(encrypted, Base64.DEFAULT)
        }
        
        private fun decryptSecret(encryptedSecret: String): String {
            val cipher = Cipher.getInstance("AES/ECB/PKCS1Padding")
            val keySpec = SecretKeySpec(ENCRYPTION_KEY.toByteArray().take(16).toByteArray(), "AES")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decrypted = cipher.doFinal(Base64.decode(encryptedSecret, Base64.DEFAULT))
            return String(decrypted)
        }

        fun getRetryMaxAttempts(context: Context): Int {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getInt(KEY_RETRY_MAX_ATTEMPTS, 5)
        }

        fun getRetryBaseSeconds(context: Context): Long {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getLong(KEY_RETRY_BASE_SECONDS, 30L)
        }

        fun getRuleSenderContains(context: Context): List<String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_RULE_SENDER_CONTAINS, "") ?: ""
            return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }

        fun getRuleBodyIncludes(context: Context): List<String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_RULE_BODY_INCLUDES, "") ?: ""
            return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }

        fun getRuleBodyExcludes(context: Context): List<String> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val raw = prefs.getString(KEY_RULE_BODY_EXCLUDES, "") ?: ""
            return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        }

        fun getRuleOverrideUrl(context: Context): String? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_RULE_OVERRIDE_URL, null)
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
        switchForwarding = view.findViewById(R.id.switchForwarding)
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

        switchForwarding.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_FORWARDING_ENABLED, isChecked).apply()
            updateStatus()
        }
    }

    private fun loadSavedConfiguration() {
        val savedUrl = sharedPreferences.getString(KEY_WEBHOOK_URL, "")
        val savedSecret = getWebhookSecret(requireContext()) ?: ""
        val forwardingEnabled = sharedPreferences.getBoolean(KEY_FORWARDING_ENABLED, true)
        
        // Set default URL if none saved
        if (savedUrl.isNullOrEmpty()) {
            editTextUrl.setText("https://your-zeus-server.com/api/sms/webhook")
            editTextSecret.setText("")
        } else {
            // Show saved configuration
            displaySavedConfiguration(savedUrl, savedSecret)
        }

        switchForwarding.isChecked = forwardingEnabled
    }

    private fun saveConfiguration() {
        val url = editTextUrl.text.toString().trim()
        val secret = editTextSecret.text.toString().trim()

        if (url.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a Zeus server URL", Toast.LENGTH_SHORT).show()
            return
        }

        val urlValidation = validateUrl(url)
        if (!urlValidation.isValid) {
            Toast.makeText(requireContext(), urlValidation.errorMessage, Toast.LENGTH_LONG).show()
            return
        }

        // Save to SharedPreferences with encryption for secret
        val encryptedSecret = if (secret.isNotEmpty()) {
            try {
                encryptSecret(secret)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Failed to encrypt secret", Toast.LENGTH_SHORT).show()
                return
            }
        } else ""
        
        sharedPreferences.edit()
            .putString(KEY_WEBHOOK_URL, url)
            .putString(KEY_WEBHOOK_SECRET, encryptedSecret)
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
        val savedSecret = getWebhookSecret(requireContext()) ?: ""
        
        editTextUrl.setText(savedUrl)
        editTextSecret.setText(savedSecret)
        cardSavedConfig.visibility = View.GONE
    }

    private fun deleteConfiguration() {
        sharedPreferences.edit()
            .remove(KEY_WEBHOOK_URL)
            .remove(KEY_WEBHOOK_SECRET)
            .apply()
        
        editTextUrl.setText("https://your-zeus-server.com/api/sms/webhook")
        editTextSecret.setText("")
        cardSavedConfig.visibility = View.GONE
        updateStatus()
        
        Toast.makeText(requireContext(), "Zeus configuration deleted", Toast.LENGTH_SHORT).show()
    }

    data class UrlValidationResult(val isValid: Boolean, val errorMessage: String = "")
    
    private fun validateUrl(url: String): UrlValidationResult {
        try {
            val parsedUrl = URL(url)
            
            // Protocol validation
            if (parsedUrl.protocol != "https" && parsedUrl.protocol != "http") {
                return UrlValidationResult(false, "URL must use HTTP or HTTPS protocol")
            }
            
            // Require HTTPS for production
            if (parsedUrl.protocol != "https") {
                return UrlValidationResult(false, "HTTPS is required for security")
            }
            
            // Host validation
            val host = parsedUrl.host?.lowercase() ?: return UrlValidationResult(false, "Invalid hostname")
            
            // Block dangerous hosts
            when {
                host == "localhost" || host == "127.0.0.1" -> 
                    return UrlValidationResult(false, "Localhost URLs are not allowed")
                host.startsWith("192.168.") || host.startsWith("10.") || 
                host.matches(Regex("172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) ->
                    return UrlValidationResult(false, "Private IP addresses are not allowed")
                host.startsWith("169.254.") ->
                    return UrlValidationResult(false, "Link-local addresses are not allowed")
                host.length > 253 ->
                    return UrlValidationResult(false, "Hostname too long")
                !VALID_HOSTNAME_PATTERN.matcher(host).matches() ->
                    return UrlValidationResult(false, "Invalid hostname format")
            }
            
            // Port validation
            val port = parsedUrl.port
            if (port != -1 && (port < 1 || port > 65535)) {
                return UrlValidationResult(false, "Invalid port number")
            }
            
            // Path validation
            if (parsedUrl.path.contains("..")) {
                return UrlValidationResult(false, "Path traversal not allowed")
            }
            
            return UrlValidationResult(true)
            
        } catch (e: MalformedURLException) {
            return UrlValidationResult(false, "Malformed URL: ${e.message}")
        } catch (e: Exception) {
            return UrlValidationResult(false, "URL validation error: ${e.message}")
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
            !sharedPreferences.getBoolean(KEY_FORWARDING_ENABLED, true) -> "⏸️ Forwarding is OFF"
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
