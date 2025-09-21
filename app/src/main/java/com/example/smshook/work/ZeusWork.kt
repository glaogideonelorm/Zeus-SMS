package com.example.smshook.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf

object ZeusWork {
    fun enqueueRunJob(ctx: Context, jobId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<RunJobWorker>()
            .setInputData(workDataOf("jobId" to jobId))
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(ctx).enqueueUniqueWork(
            "ussd_job_$jobId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
