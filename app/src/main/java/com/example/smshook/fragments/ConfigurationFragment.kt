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
import com.example.smshook.config.ServerConfig
import com.example.smshook.sms.ForwardWorker
import com.example.smshook.adapters.WebhookConfigAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.security.MessageDigest
import java.net.MalformedURLException
import java.util.regex.Pattern
import java.util.UUID
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class WebhookConfig(
    val id: String,
    val name: String,
    val url: String,
    val secret: String = "",
    val enabled: Boolean = true,
    val priority: Int = 0 // Lower number = higher priority
)

class ConfigurationFragment : Fragment() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var switchForwarding: SwitchMaterial
    
    // Multiple webhook management
    private lateinit var recyclerViewWebhooks: RecyclerView
    private lateinit var webhookAdapter: WebhookConfigAdapter
    private lateinit var buttonAddWebhook: Button
    
    // Add/Edit webhook dialog
    private lateinit var cardAddWebhook: androidx.cardview.widget.CardView
    private lateinit var editTextWebhookName: EditText
    private lateinit var editTextWebhookUrl: EditText
    private lateinit var editTextWebhookSecret: EditText
    private lateinit var buttonSaveWebhook: Button
    private lateinit var buttonCancelWebhook: Button
    
    // Server URL configuration
    private lateinit var cardServerConfig: androidx.cardview.widget.CardView
    private lateinit var editTextServerBaseUrl: EditText
    private lateinit var buttonSaveServerUrl: Button
    private lateinit var buttonTestServerConnection: Button
    private lateinit var buttonResetServerUrls: Button
    private lateinit var textCurrentServerUrls: TextView
    
    // Legacy single webhook support (for backward compatibility)
    private lateinit var editTextUrl: EditText
    private lateinit var editTextSecret: EditText
    private lateinit var buttonSaveUrl: Button
    private lateinit var textStatus: TextView
    private lateinit var cardSavedConfig: androidx.cardview.widget.CardView
    private lateinit var textSavedUrl: TextView
    private lateinit var textSavedSecret: TextView
    private lateinit var buttonTestSavedUrl: Button
    private lateinit var buttonEditConfig: Button
    private lateinit var buttonDeleteConfig: Button
    
    private var currentEditingWebhook: WebhookConfig? = null

    companion object {
        const val PREFS_NAME = "ZeusSMSPrefs"
        const val KEY_WEBHOOK_URL = "zeus_webhook_url"
        const val KEY_WEBHOOK_SECRET = "zeus_webhook_secret"
        const val KEY_FORWARDING_ENABLED = "zeus_forwarding_enabled"
        const val KEY_RETRY_MAX_ATTEMPTS = "zeus_retry_max_attempts"
        const val KEY_RETRY_BASE_SECONDS = "zeus_retry_base_seconds"
        // Multiple webhook URLs
        const val KEY_WEBHOOK_URLS = "zeus_webhook_urls" // JSON array of webhook configs
        const val KEY_WEBHOOK_SECRETS = "zeus_webhook_secrets" // JSON array of encrypted secrets
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

        // Multiple webhook URLs support
        fun getWebhookConfigs(context: Context): List<WebhookConfig> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val urlsJson = prefs.getString(KEY_WEBHOOK_URLS, null)
            val secretsJson = prefs.getString(KEY_WEBHOOK_SECRETS, null)
            
            if (urlsJson.isNullOrEmpty()) {
                // Fallback to single webhook URL for backward compatibility
                val singleUrl = getWebhookUrl(context)
                val singleSecret = getWebhookSecret(context)
                return if (singleUrl != null) {
                    listOf(WebhookConfig(
                        id = "default",
                        name = "Default Webhook",
                        url = singleUrl,
                        secret = singleSecret ?: ""
                    ))
                } else emptyList()
            }
            
            try {
                val gson = Gson()
                val urls = gson.fromJson<List<WebhookConfig>>(urlsJson, object : TypeToken<List<WebhookConfig>>() {}.type)
                val secrets = if (secretsJson.isNullOrEmpty()) emptyList() else {
                    gson.fromJson<List<String>>(secretsJson, object : TypeToken<List<String>>() {}.type)
                }
                
                return urls.mapIndexed { index, config ->
                    val secret = if (index < secrets.size) {
                        try {
                            decryptSecret(secrets[index])
                        } catch (e: Exception) {
                            ""
                        }
                    } else ""
                    config.copy(secret = secret)
                }.sortedBy { it.priority }
            } catch (e: Exception) {
                return emptyList()
            }
        }

        fun saveWebhookConfigs(context: Context, configs: List<WebhookConfig>) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val gson = Gson()
            
            // Encrypt secrets
            val encryptedSecrets = configs.map { config ->
                if (config.secret.isNotEmpty()) {
                    try {
                        encryptSecret(config.secret)
                    } catch (e: Exception) {
                        ""
                    }
                } else ""
            }
            
            // Save configs without secrets
            val configsWithoutSecrets = configs.map { it.copy(secret = "") }
            
            prefs.edit()
                .putString(KEY_WEBHOOK_URLS, gson.toJson(configsWithoutSecrets))
                .putString(KEY_WEBHOOK_SECRETS, gson.toJson(encryptedSecrets))
                .apply()
        }

        fun getEnabledWebhookConfigs(context: Context): List<WebhookConfig> {
            return getWebhookConfigs(context).filter { it.enabled }
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
        // updateStatus() // commented out for now
    }

    private fun initializeViews(view: View) {
        sharedPreferences = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Multiple webhook management
        recyclerViewWebhooks = view.findViewById(R.id.recyclerViewWebhooks)
        buttonAddWebhook = view.findViewById(R.id.buttonAddWebhook)
        switchForwarding = view.findViewById(R.id.switchForwarding)
        
        // Add/Edit webhook dialog
        cardAddWebhook = view.findViewById(R.id.cardAddWebhook)
        editTextWebhookName = view.findViewById(R.id.editTextWebhookName)
        editTextWebhookUrl = view.findViewById(R.id.editTextWebhookUrl)
        editTextWebhookSecret = view.findViewById(R.id.editTextWebhookSecret)
        buttonSaveWebhook = view.findViewById(R.id.buttonSaveWebhook)
        buttonCancelWebhook = view.findViewById(R.id.buttonCancelWebhook)
        
        // Server URL configuration
        cardServerConfig = view.findViewById(R.id.cardServerConfig)
        editTextServerBaseUrl = view.findViewById(R.id.editTextServerBaseUrl)
        buttonSaveServerUrl = view.findViewById(R.id.buttonSaveServerUrl)
        buttonTestServerConnection = view.findViewById(R.id.buttonTestServerConnection)
        buttonResetServerUrls = view.findViewById(R.id.buttonResetServerUrls)
        textCurrentServerUrls = view.findViewById(R.id.textCurrentServerUrls)
        
        // Legacy single webhook support (for backward compatibility) - commented out for now
        // editTextUrl = view.findViewById(R.id.editTextUrl)
        // editTextSecret = view.findViewById(R.id.editTextSecret)
        // buttonSaveUrl = view.findViewById(R.id.buttonSaveUrl)
        // textStatus = view.findViewById(R.id.textStatus)
        // cardSavedConfig = view.findViewById(R.id.cardSavedConfig)
        // textSavedUrl = view.findViewById(R.id.textSavedUrl)
        // textSavedSecret = view.findViewById(R.id.textSavedSecret)
        // buttonTestSavedUrl = view.findViewById(R.id.buttonTestSavedUrl)
        // buttonEditConfig = view.findViewById(R.id.buttonEditConfig)
        // buttonDeleteConfig = view.findViewById(R.id.buttonDeleteConfig)
        
        // Setup RecyclerView
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        webhookAdapter = WebhookConfigAdapter(
            webhooks = mutableListOf(),
            onTestClick = { webhook -> testWebhook(webhook) },
            onEditClick = { webhook -> editWebhook(webhook) },
            onDeleteClick = { webhook -> deleteWebhook(webhook) },
            onToggleEnabled = { webhook, enabled -> toggleWebhookEnabled(webhook, enabled) }
        )
        
        recyclerViewWebhooks.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewWebhooks.adapter = webhookAdapter
    }

    private fun setupEventListeners() {
        // Multiple webhook management
        buttonAddWebhook.setOnClickListener {
            showAddWebhookDialog()
        }
        
        buttonSaveWebhook.setOnClickListener {
            saveWebhook()
        }
        
        buttonCancelWebhook.setOnClickListener {
            hideAddWebhookDialog()
        }
        
        // Server URL configuration event listeners
        buttonSaveServerUrl.setOnClickListener {
            saveServerUrl()
        }
        
        buttonTestServerConnection.setOnClickListener {
            testServerConnection()
        }
        
        buttonResetServerUrls.setOnClickListener {
            resetServerUrls()
        }

        // Legacy single webhook support - commented out for now
        // buttonSaveUrl.setOnClickListener {
        //     saveConfiguration()
        // }

        // buttonTestSavedUrl.setOnClickListener {
        //     testWebhookWithSavedConfig()
        // }

        // buttonEditConfig.setOnClickListener {
        //     editConfiguration()
        // }

        // buttonDeleteConfig.setOnClickListener {
        //     deleteConfiguration()
        // }

        switchForwarding.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_FORWARDING_ENABLED, isChecked).apply()
            // updateStatus() // commented out for now
        }
    }

    private fun loadSavedConfiguration() {
        val forwardingEnabled = sharedPreferences.getBoolean(KEY_FORWARDING_ENABLED, true)
        
        // Load multiple webhook configurations
        val webhooks = getWebhookConfigs(requireContext())
        webhookAdapter.updateWebhooks(webhooks)
        
        // Load server URL configuration
        loadServerUrlConfiguration()
        
        // Legacy single webhook support - commented out for now
        // val savedUrl = sharedPreferences.getString(KEY_WEBHOOK_URL, "")
        // val savedSecret = getWebhookSecret(requireContext()) ?: ""
        
        // Set default URL if none saved
        // if (savedUrl.isNullOrEmpty()) {
        //     editTextUrl.setText("https://your-zeus-server.com/api/sms/webhook")
        //     editTextSecret.setText("")
        // } else {
        //     // Show saved configuration
        //     displaySavedConfiguration(savedUrl, savedSecret)
        // }

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
        // editTextUrl.setText("")
        // editTextSecret.setText("")
        // displaySavedConfiguration(url, secret)
        // updateStatus() // commented out for now
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
        
        // editTextUrl.setText("https://your-zeus-server.com/api/sms/webhook")
        // editTextSecret.setText("")
        // cardSavedConfig.visibility = View.GONE
        // updateStatus() // commented out for now
        
        Toast.makeText(requireContext(), "Zeus configuration deleted", Toast.LENGTH_SHORT).show()
    }

    // Multiple webhook management methods
    private fun showAddWebhookDialog() {
        currentEditingWebhook = null
        editTextWebhookName.setText("")
        editTextWebhookUrl.setText("")
        editTextWebhookSecret.setText("")
        cardAddWebhook.visibility = View.VISIBLE
    }

    private fun hideAddWebhookDialog() {
        cardAddWebhook.visibility = View.GONE
        currentEditingWebhook = null
    }

    private fun saveWebhook() {
        val name = editTextWebhookName.text.toString().trim()
        val url = editTextWebhookUrl.text.toString().trim()
        val secret = editTextWebhookSecret.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a webhook name", Toast.LENGTH_SHORT).show()
            return
        }

        if (url.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a webhook URL", Toast.LENGTH_SHORT).show()
            return
        }

        val urlValidation = validateUrl(url)
        if (!urlValidation.isValid) {
            Toast.makeText(requireContext(), urlValidation.errorMessage, Toast.LENGTH_LONG).show()
            return
        }

        val webhookId = currentEditingWebhook?.id ?: UUID.randomUUID().toString()
        val priority = currentEditingWebhook?.priority ?: webhookAdapter.itemCount

        val newWebhook = WebhookConfig(
            id = webhookId,
            name = name,
            url = url,
            secret = secret,
            enabled = currentEditingWebhook?.enabled ?: true,
            priority = priority
        )

        if (currentEditingWebhook != null) {
            // Update existing webhook
            val updatedWebhooks = getWebhookConfigs(requireContext()).toMutableList()
            val index = updatedWebhooks.indexOfFirst { it.id == currentEditingWebhook!!.id }
            if (index != -1) {
                updatedWebhooks[index] = newWebhook
                saveWebhookConfigs(requireContext(), updatedWebhooks)
                webhookAdapter.updateWebhook(currentEditingWebhook!!, newWebhook)
                Toast.makeText(requireContext(), "Webhook updated successfully!", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Add new webhook
            val updatedWebhooks = getWebhookConfigs(requireContext()).toMutableList()
            updatedWebhooks.add(newWebhook)
            saveWebhookConfigs(requireContext(), updatedWebhooks)
            webhookAdapter.addWebhook(newWebhook)
            Toast.makeText(requireContext(), "Webhook added successfully!", Toast.LENGTH_SHORT).show()
        }

        hideAddWebhookDialog()
    }

    private fun editWebhook(webhook: WebhookConfig) {
        currentEditingWebhook = webhook
        editTextWebhookName.setText(webhook.name)
        editTextWebhookUrl.setText(webhook.url)
        editTextWebhookSecret.setText(webhook.secret)
        cardAddWebhook.visibility = View.VISIBLE
    }

    private fun deleteWebhook(webhook: WebhookConfig) {
        val updatedWebhooks = getWebhookConfigs(requireContext()).toMutableList()
        updatedWebhooks.removeAll { it.id == webhook.id }
        saveWebhookConfigs(requireContext(), updatedWebhooks)
        webhookAdapter.removeWebhook(webhook)
        Toast.makeText(requireContext(), "Webhook deleted successfully!", Toast.LENGTH_SHORT).show()
    }

    private fun testWebhook(webhook: WebhookConfig) {
        // Create a test SMS message for this specific webhook
        val testWorkRequest = OneTimeWorkRequestBuilder<ForwardWorker>()
            .setInputData(
                workDataOf(
                    "from" to "ZEUS_TEST",
                    "body" to "Test message from Zeus SMS microservice to ${webhook.name}",
                    "timestamp" to System.currentTimeMillis(),
                    "subscriptionId" to 1,
                    "isTest" to true,
                    "overrideUrl" to webhook.url
                )
            )
            .addTag("zeus-sms-forward")
            .build()

        WorkManager.getInstance(requireContext()).enqueue(testWorkRequest)
        Toast.makeText(requireContext(), "Test message sent to ${webhook.name}...", Toast.LENGTH_SHORT).show()
    }

    private fun toggleWebhookEnabled(webhook: WebhookConfig, enabled: Boolean) {
        val updatedWebhook = webhook.copy(enabled = enabled)
        val updatedWebhooks = getWebhookConfigs(requireContext()).toMutableList()
        val index = updatedWebhooks.indexOfFirst { it.id == webhook.id }
        if (index != -1) {
            updatedWebhooks[index] = updatedWebhook
            saveWebhookConfigs(requireContext(), updatedWebhooks)
            webhookAdapter.updateWebhook(webhook, updatedWebhook)
        }
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
            // updateStatus() // commented out for now
        }
    }

    override fun onResume() {
        super.onResume()
        // updateStatus() // commented out for now
    }
    
    // Server URL configuration methods
    private fun loadServerUrlConfiguration() {
        val currentUrls = ServerConfig.getAllUrls(requireContext())
        val urlDisplay = currentUrls.entries.joinToString("\n") { "${it.key}: ${it.value}" }
        textCurrentServerUrls.text = urlDisplay
        
        // Set current base URL in edit text (extract from API base URL)
        val apiBaseUrl = ServerConfig.getApiBaseUrl(requireContext())
        val baseUrl = apiBaseUrl.replace("/fcm", "")
        editTextServerBaseUrl.setText(baseUrl)
        
        // Debug: Log the actual URLs being stored
        android.util.Log.d("ConfigurationFragment", "Current URLs: $currentUrls")
        android.util.Log.d("ConfigurationFragment", "API Base URL: $apiBaseUrl")
        android.util.Log.d("ConfigurationFragment", "Base URL: $baseUrl")
    }
    
    private fun saveServerUrl() {
        val baseUrl = editTextServerBaseUrl.text.toString().trim()
        
        if (baseUrl.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a server base URL", Toast.LENGTH_SHORT).show()
            return
        }
        
        val urlValidation = validateUrl(baseUrl)
        if (!urlValidation.isValid) {
            Toast.makeText(requireContext(), urlValidation.errorMessage, Toast.LENGTH_LONG).show()
            return
        }
        
        // Save the base URL and update all related URLs
        ServerConfig.setServerBaseUrl(requireContext(), baseUrl)
        
        // Refresh the display
        loadServerUrlConfiguration()
        
        Toast.makeText(requireContext(), "Server URLs updated successfully!", Toast.LENGTH_SHORT).show()
    }
    
    private fun testServerConnection() {
        val baseUrl = editTextServerBaseUrl.text.toString().trim()
        
        if (baseUrl.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a server base URL first", Toast.LENGTH_SHORT).show()
            return
        }
        
        Toast.makeText(requireContext(), "Testing server connection...", Toast.LENGTH_SHORT).show()
        
        // TODO: Implement actual server connection test
        // For now, just show a message
        Toast.makeText(requireContext(), "Server connection test not yet implemented", Toast.LENGTH_LONG).show()
    }
    
    private fun resetServerUrls() {
        ServerConfig.resetToDefaults(requireContext())
        loadServerUrlConfiguration()
        Toast.makeText(requireContext(), "Server URLs reset to defaults", Toast.LENGTH_SHORT).show()
    }
}
