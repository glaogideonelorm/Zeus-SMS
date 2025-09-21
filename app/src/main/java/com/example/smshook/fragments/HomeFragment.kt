package com.example.smshook.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.smshook.MainActivity
import com.example.smshook.ZeusUssdActivity
import com.example.smshook.api.ZeusIds
import com.example.smshook.logs.LogManager
import com.google.firebase.messaging.FirebaseMessaging

class HomeFragment : Fragment() {
    
    private lateinit var tvFcmStatus: TextView
    private lateinit var fcmStatusIndicator: View
    private lateinit var tvFcmToken: TextView
    private lateinit var tvDeviceId: TextView
    private lateinit var btnZeusSms: Button
    private lateinit var btnZeusUssd: Button

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(com.example.smshook.R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialize views
        tvFcmStatus = view.findViewById(com.example.smshook.R.id.tv_fcm_status)
        fcmStatusIndicator = view.findViewById(com.example.smshook.R.id.fcm_status_indicator)
        tvFcmToken = view.findViewById(com.example.smshook.R.id.tv_fcm_token)
        tvDeviceId = view.findViewById(com.example.smshook.R.id.tv_device_id)
        btnZeusSms = view.findViewById(com.example.smshook.R.id.btn_zeus_sms)
        btnZeusUssd = view.findViewById(com.example.smshook.R.id.btn_zeus_ussd)
        
        // Initialize FCM
        initializeFcm()
        
        // Set up device info
        setupDeviceInfo()
        
        // Set up button click listeners
        setupButtonListeners()
        
        // Log home fragment creation
        LogManager.addLog(com.example.smshook.logs.LogLevel.INFO, "HomeFragment", "Home fragment created")
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
                
                LogManager.addLog(com.example.smshook.logs.LogLevel.FCM, "HomeFragment", "FCM token received", "Token: ${token.take(20)}...")
            } else {
                tvFcmStatus.text = "FCM Error"
                tvFcmStatus.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark))
                fcmStatusIndicator.setBackgroundResource(com.example.smshook.R.drawable.circle_red)
                tvFcmToken.text = "Error: ${task.exception?.message}"
                
                LogManager.addLog(com.example.smshook.logs.LogLevel.ERROR, "HomeFragment", "FCM token error", task.exception?.message)
            }
        }
    }
    
    private fun setupDeviceInfo() {
        val deviceId = ZeusIds.deviceId(requireContext())
        tvDeviceId.text = "Device ID: $deviceId"
        
        LogManager.addLog(com.example.smshook.logs.LogLevel.INFO, "HomeFragment", "Device info loaded", "Device ID: $deviceId")
    }
    
    private fun setupButtonListeners() {
        btnZeusSms.setOnClickListener {
            LogManager.addLog(com.example.smshook.logs.LogLevel.INFO, "HomeFragment", "Zeus SMS button clicked")
            // Navigate to SMS logs instead of starting new MainActivity
            val navController = findNavController()
            navController.navigate(com.example.smshook.R.id.nav_logs)
        }
        
        btnZeusUssd.setOnClickListener {
            LogManager.addLog(com.example.smshook.logs.LogLevel.INFO, "HomeFragment", "Zeus USSD button clicked")
            startActivity(Intent(requireContext(), ZeusUssdActivity::class.java))
        }
    }
}



