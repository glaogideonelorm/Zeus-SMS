package com.example.smshook.fragments

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import com.example.smshook.ZeusUssdActivity
import com.example.smshook.api.ZeusIds
import com.example.smshook.api.ZeusApi
import com.example.smshook.api.SimSlotInfo
import com.example.smshook.logs.LogManager
import com.yourpackage.simpleussd.ussd.UssdCallback
import com.yourpackage.simpleussd.ussd.UssdController
import com.google.firebase.messaging.FirebaseMessaging
import java.io.IOException
import java.util.*
import okhttp3.MediaType.Companion.toMediaType

class UssdHomeFragment : Fragment(), UssdCallback {
    
    private lateinit var tvFcmStatus: TextView
    private lateinit var fcmStatusIndicator: View
    private lateinit var tvFcmToken: TextView
    private lateinit var tvDeviceId: TextView
    private lateinit var btnTestSimpleBalance: Button
    private lateinit var btnTestMiniStatement: Button
    private lateinit var btnFcmStatus: Button
    private lateinit var btnSim0: Button
    private lateinit var btnSim1: Button

    private var selectedSimSlot: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(com.example.smshook.R.layout.fragment_ussd_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize views
        tvFcmStatus = view.findViewById(com.example.smshook.R.id.tv_fcm_status)
        fcmStatusIndicator = view.findViewById(com.example.smshook.R.id.fcm_status_indicator)
        tvFcmToken = view.findViewById(com.example.smshook.R.id.tv_fcm_token)
        tvDeviceId = view.findViewById(com.example.smshook.R.id.tv_device_id)
        btnTestSimpleBalance = view.findViewById(com.example.smshook.R.id.btn_test_simple_balance)
        btnTestMiniStatement = view.findViewById(com.example.smshook.R.id.btn_test_mini_statement)
        btnFcmStatus = view.findViewById(com.example.smshook.R.id.btn_fcm_status)
        btnSim0 = view.findViewById(com.example.smshook.R.id.btn_sim_slot_0)
        btnSim1 = view.findViewById(com.example.smshook.R.id.btn_sim_slot_1)
        
        // Initialize FCM
        initializeFcm()
        
        // Set up device info
        setupDeviceInfo()
        
        // Set up button click listeners
        setupButtonListeners()

        // Detect SIM/operator labels and default selection
        detectAndRenderSimInfo()
        
        // Log USSD home fragment creation
        LogManager.addLog(com.example.smshook.logs.LogLevel.INFO, "UssdHomeFragment", "USSD home fragment created")
    }
    
    private fun initializeFcm() {
        // Get FCM token
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                tvFcmStatus.text = "FCM Ready"
                tvFcmStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark))
                fcmStatusIndicator.setBackgroundResource(com.example.smshook.R.drawable.circle_green)
                tvFcmToken.text = "Token: ${token.take(20)}..."
                
                LogManager.addLog(com.example.smshook.logs.LogLevel.FCM, "UssdHomeFragment", "FCM token received", "Token: ${token.take(20)}...")
                
                // Manually trigger token registration to server
                registerTokenToServer(token)
            } else {
                tvFcmStatus.text = "FCM Error"
                tvFcmStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                fcmStatusIndicator.setBackgroundResource(com.example.smshook.R.drawable.circle_red)
                tvFcmToken.text = "Error: ${task.exception?.message}"
                
                LogManager.addLog(com.example.smshook.logs.LogLevel.ERROR, "UssdHomeFragment", "FCM token error", task.exception?.message)
            }
        }
    }
    
    private fun registerTokenToServer(token: String) {
        try {
            val deviceId = ZeusIds.deviceId(requireContext())
            
            // Move FCM registration to background thread to avoid NetworkOnMainThreadException
            Thread {
                try {
                    ZeusApi.registerFcm(requireContext(), deviceId, token)
                    
                    // Run UI updates on main thread
                    requireActivity().runOnUiThread {
                        LogManager.addLog(com.example.smshook.logs.LogLevel.FCM, "UssdHomeFragment", "FCM token registered successfully", "Device: $deviceId")
                        
                        // AUTOMATICALLY REGISTER SIM SLOTS AFTER FCM TOKEN SUCCESS
                        LogManager.addLog(com.example.smshook.logs.LogLevel.INFO, "UssdHomeFragment", "FCM token success - triggering SIM slot registration")
                        registerSimSlotsToServer()
                    }
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        LogManager.addLog(com.example.smshook.logs.LogLevel.ERROR, "UssdHomeFragment", "Error registering FCM token", e.message)
                    }
                }
            }.start()
            
        } catch (e: Exception) {
            LogManager.addLog(com.example.smshook.logs.LogLevel.ERROR, "UssdHomeFragment", "Error starting FCM registration thread", e.message)
        }
    }
    
    /**
     * Register SIM slots to server - called automatically after FCM token registration
     */
    private fun registerSimSlotsToServer() {
        try {
            LogManager.addLog(com.example.smshook.logs.LogLevel.INFO, "UssdHomeFragment", "Starting automatic SIM slot registration")
            
            val subscriptionManager = requireContext().getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val infos = subscriptionManager.activeSubscriptionInfoList
            
            val simSlots = mutableListOf<SimSlotInfo>()
            
            infos?.forEach { info ->
                val carrierName = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                val displayName = info.displayName?.toString()
                
                val simSlotInfo = SimSlotInfo(
                    slotIndex = info.simSlotIndex,
                    carrierName = carrierName,
                    displayName = displayName,
                    isActive = true,
                    mcc = info.mcc?.toString(),
                    mnc = info.mnc?.toString()
                )
                simSlots.add(simSlotInfo)
            }
            
            // Add inactive slots if not detected
            if (simSlots.none { it.slotIndex == 0 }) {
                simSlots.add(SimSlotInfo(0, null, "SIM 1", false, null, null))
            }
            if (simSlots.none { it.slotIndex == 1 }) {
                simSlots.add(SimSlotInfo(1, null, "SIM 2", false, null, null))
            }
            
            // Send SIM slot information to server (in background thread)
            val deviceId = ZeusIds.deviceId(requireContext())
            Thread {
                try {
                    ZeusApi.registerSimSlots(requireContext(), deviceId, simSlots)
                    LogManager.addLog(com.example.smshook.logs.LogLevel.INFO, "UssdHomeFragment", "SIM slots registered successfully after FCM token")
                } catch (e: Exception) {
                    LogManager.addLog(com.example.smshook.logs.LogLevel.ERROR, "UssdHomeFragment", "SIM slot registration failed after FCM token", e.message)
                }
            }.start()
            
        } catch (e: Exception) {
            LogManager.addLog(com.example.smshook.logs.LogLevel.ERROR, "UssdHomeFragment", "Error in automatic SIM slot registration", e.message)
        }
    }
    
    private fun setupDeviceInfo() {
        val deviceId = ZeusIds.deviceId(requireContext())
        tvDeviceId.text = "Device ID: $deviceId"
        
        LogManager.addLog(com.example.smshook.logs.LogLevel.INFO, "UssdHomeFragment", "Device info loaded", "Device ID: $deviceId")
    }
    
    private fun setupButtonListeners() {
        btnSim0.setOnClickListener {
            selectSimSlot(0)
        }
        btnSim1.setOnClickListener {
            selectSimSlot(1)
        }

        btnTestSimpleBalance.setOnClickListener {
            if (checkCallPhonePermission()) {
                LogManager.addLog(com.example.smshook.logs.LogLevel.INFO, "UssdHomeFragment", "Testing simple balance check")
                // SAFETY: Clean up any open USSD dialogs before starting test
                LogManager.addLog(com.example.smshook.logs.LogLevel.INFO, "UssdHomeFragment", "Cleaning up any open USSD dialogs before test")
                val controller = UssdController(requireContext())
                cleanupOpenDialogs()
                // Test simple balance check directly
                val options = ArrayList<String>()
                controller.executeUssd("*124#", selectedSimSlot, options, this)
            }
        }
        
        btnTestMiniStatement.setOnClickListener {
            if (checkCallPhonePermission()) {
                LogManager.addLog(com.example.smshook.logs.LogLevel.INFO, "UssdHomeFragment", "Testing mini statement")
                // SAFETY: Clean up any open USSD dialogs before starting test
                LogManager.addLog(com.example.smshook.logs.LogLevel.INFO, "UssdHomeFragment", "Cleaning up any open USSD dialogs before test")
                val controller = UssdController(requireContext())
                cleanupOpenDialogs()
                // Test mini statement directly
                val options = ArrayList<String>()
                options.add("7")
                options.add("4")
                options.add("1")
                options.add("2040")
                controller.executeUssd("*171#", selectedSimSlot, options, this)
            }
        }
        
        btnFcmStatus.setOnClickListener {
            LogManager.addLog(com.example.smshook.logs.LogLevel.INFO, "UssdHomeFragment", "FCM status button clicked - triggering registration")
            // Show FCM status info
            android.widget.Toast.makeText(requireContext(), "FCM is ready for Zeus Cloud commands!", android.widget.Toast.LENGTH_LONG).show()
            
            // MANUALLY TRIGGER SIM SLOT REGISTRATION
            LogManager.addLog(com.example.smshook.logs.LogLevel.INFO, "UssdHomeFragment", "Manual SIM slot registration triggered")
            registerSimSlotsToServer()
        }
    }

    private fun detectAndRenderSimInfo() {
        try {
            val subMgr = requireContext().getSystemService(SubscriptionManager::class.java)
            val infos: List<SubscriptionInfo>? = subMgr?.activeSubscriptionInfoList
            var label0 = "SIM 1"
            var label1 = "SIM 2"
            val simSlots = mutableListOf<SimSlotInfo>()
            
            infos?.forEach { info ->
                val carrierName = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
                val displayName = info.displayName?.toString()
                val name = carrierName ?: displayName
                
                val simSlotInfo = SimSlotInfo(
                    slotIndex = info.simSlotIndex,
                    carrierName = carrierName,
                    displayName = displayName,
                    isActive = true,
                    mcc = info.mcc?.toString(),
                    mnc = info.mnc?.toString()
                )
                simSlots.add(simSlotInfo)
                
                when (info.simSlotIndex) {
                    0 -> label0 = name ?: label0
                    1 -> label1 = name ?: label1
                }
            }
            
            // Add inactive slots if not detected
            if (simSlots.none { it.slotIndex == 0 }) {
                simSlots.add(SimSlotInfo(0, null, "SIM 1", false, null, null))
            }
            if (simSlots.none { it.slotIndex == 1 }) {
                simSlots.add(SimSlotInfo(1, null, "SIM 2", false, null, null))
            }
            
            btnSim0.text = label0
            btnSim1.text = label1
            
            // Send SIM slot information to server (in background thread)
            val deviceId = ZeusIds.deviceId(requireContext())
            Thread {
                try {
                    ZeusApi.registerSimSlots(requireContext(), deviceId, simSlots)
                } catch (e: Exception) {
                    LogManager.addLog(com.example.smshook.logs.LogLevel.ERROR, "UssdHomeFragment", "SIM slot registration failed", e.message)
                }
            }.start()
            
            LogManager.addLog(com.example.smshook.logs.LogLevel.INFO, "UssdHomeFragment", "SIM slots detected", 
                "Slot 0: $label0, Slot 1: $label1")
                
        } catch (e: Exception) {
            LogManager.addLog(com.example.smshook.logs.LogLevel.WARN, "UssdHomeFragment", "SIM detect failed", e.message)
        }
        // Default selection
        selectSimSlot(selectedSimSlot)
    }

    private fun selectSimSlot(slot: Int) {
        selectedSimSlot = if (slot == 0 || slot == 1) slot else 0
        val selectedColor = ContextCompat.getColor(requireContext(), android.R.color.holo_blue_dark)
        val unselectedColor = ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
        val white = ContextCompat.getColor(requireContext(), android.R.color.white)

        btnSim0.backgroundTintList = android.content.res.ColorStateList.valueOf(if (selectedSimSlot == 0) selectedColor else unselectedColor)
        btnSim1.backgroundTintList = android.content.res.ColorStateList.valueOf(if (selectedSimSlot == 1) selectedColor else unselectedColor)
        btnSim0.setTextColor(white)
        btnSim1.setTextColor(white)

        LogManager.addLog(com.example.smshook.logs.LogLevel.INFO, "UssdHomeFragment", "SIM slot selected", selectedSimSlot.toString())
    }
    
    // UssdCallback implementation
    override fun onUssdResponse(response: String) {
        activity?.runOnUiThread {
            LogManager.addLog(com.example.smshook.logs.LogLevel.USSD, "UssdHomeFragment", "USSD Response", response)
        }
    }
    
    override fun onUssdComplete(finalResponse: String) {
        activity?.runOnUiThread {
            LogManager.addLog(com.example.smshook.logs.LogLevel.USSD, "UssdHomeFragment", "USSD Complete", finalResponse)
            android.widget.Toast.makeText(requireContext(), "USSD Complete: $finalResponse", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onUssdError(error: String) {
        activity?.runOnUiThread {
            LogManager.addLog(com.example.smshook.logs.LogLevel.ERROR, "UssdHomeFragment", "USSD Error", error)
            android.widget.Toast.makeText(requireContext(), "USSD Error: $error", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkCallPhonePermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE) 
                != PackageManager.PERMISSION_GRANTED) {
            // Permission not granted, request it
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.CALL_PHONE),
                CALL_PHONE_PERMISSION_REQUEST_CODE
            )
            false
        } else {
            true
        }
    }
    
    /**
     * Simple cleanup method to close any open USSD dialogs
     * This is a basic implementation that sends back actions
     */
    private fun cleanupOpenDialogs() {
        try {
            LogManager.addLog(com.example.smshook.logs.LogLevel.INFO, "UssdHomeFragment", "Attempting to cleanup open USSD dialogs")
            // Send back action to close any open dialogs
            activity?.onBackPressed()
            // Small delay
            Thread.sleep(200)
            // Send another back action for stubborn dialogs
            activity?.onBackPressed()
            LogManager.addLog(com.example.smshook.logs.LogLevel.INFO, "UssdHomeFragment", "Cleanup actions sent")
        } catch (e: Exception) {
            LogManager.addLog(com.example.smshook.logs.LogLevel.ERROR, "UssdHomeFragment", "Error during cleanup", e.message)
        }
    }

    companion object {
        private const val CALL_PHONE_PERMISSION_REQUEST_CODE = 1001
    }
}


