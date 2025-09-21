package com.example.smshook.realtime

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.smshook.ZeusUssdActivity

/**
 * Simulator for Zeus Cloud commands - for development and testing
 * Remove this when connecting to real Zeus Cloud server
 */
object ZeusCloudSimulator {
    private const val TAG = "ZeusCloudSimulator"
    
    fun sendTestUssdCommand(context: Context, code: String, options: String = "", simSlot: Int = 0) {
        Log.d(TAG, "Simulating Zeus Cloud USSD command: $code")
        
        val intent = Intent(context, ZeusUssdActivity::class.java).apply {
            putExtra("auto_execute", true)
            putExtra("ussd_code", code)
            putExtra("options", options)
            putExtra("sim_slot", simSlot)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        context.startActivity(intent)
    }
    
    fun sendBalanceCheckCommand(context: Context, simSlot: Int = 0) {
        sendTestUssdCommand(context, "*171#", "7,1,2040", simSlot)
    }
    
    fun sendDataCheckCommand(context: Context, simSlot: Int = 0) {
        sendTestUssdCommand(context, "*131#", "", simSlot)
    }
    
    fun sendCustomCommand(context: Context, code: String, options: List<String>, simSlot: Int = 0) {
        sendTestUssdCommand(context, code, options.joinToString(","), simSlot)
    }
}
