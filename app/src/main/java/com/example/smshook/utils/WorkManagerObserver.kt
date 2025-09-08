package com.example.smshook.utils

import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.smshook.data.ForwardingStatus
import com.example.smshook.data.SmsLogManager

class WorkManagerObserver(
    private val context: Context,
    private val smsLogManager: SmsLogManager
) {
    
    fun observeWorkCompletion(lifecycleOwner: LifecycleOwner) {
        // Observe all work with the ForwardWorker tag
        WorkManager.getInstance(context)
            .getWorkInfosByTagLiveData("zeus-sms-forward")
            .observe(lifecycleOwner, Observer { workInfos ->
                workInfos?.forEach { workInfo ->
                    handleWorkCompletion(workInfo)
                }
            })
    }
    
    private fun handleWorkCompletion(workInfo: WorkInfo) {
        // Since ForwardWorker already handles status updates directly,
        // this observer is mainly for monitoring work completion
        // The LiveData in SmsLogManager will automatically trigger UI updates
        
        when (workInfo.state) {
            WorkInfo.State.SUCCEEDED -> {
                // SMS forwarding completed successfully
                // Status is already updated by ForwardWorker
            }
            WorkInfo.State.FAILED -> {
                // SMS forwarding failed
                // Status is already updated by ForwardWorker
            }
            WorkInfo.State.RUNNING -> {
                // SMS forwarding is in progress
                // Status updates are handled by ForwardWorker
            }
            else -> {
                // Other states (BLOCKED, CANCELLED, etc.)
            }
        }
    }
}
