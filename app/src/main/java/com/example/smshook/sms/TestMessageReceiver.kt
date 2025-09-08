package com.example.smshook.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

class TestMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TEST_SEND_MESSAGE) return
        val body = intent.getStringExtra(EXTRA_BODY) ?: DEFAULT_BODY
        val from = intent.getStringExtra(EXTRA_FROM) ?: DEFAULT_FROM
        val ts = System.currentTimeMillis()
        val subId = intent.getIntExtra(EXTRA_SUB_ID, DEFAULT_SUB_ID)

        Log.d("ZeusSMS", "TestMessageReceiver enqueue ForwardWorker body='${body}' from='${from}'")
        val work = OneTimeWorkRequestBuilder<ForwardWorker>()
            .setInputData(
                workDataOf(
                    "from" to from,
                    "body" to body,
                    "timestamp" to ts,
                    "subscriptionId" to subId,
                    "isTest" to false
                )
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag("zeus-test-forward")
            .build()

        WorkManager.getInstance(context).enqueue(work)
    }

    companion object {
        const val ACTION_TEST_SEND_MESSAGE = "com.example.smshook.ACTION_TEST_SEND_MESSAGE"
        const val EXTRA_BODY = "body"
        const val EXTRA_FROM = "from"
        const val EXTRA_SUB_ID = "subId"
        private const val DEFAULT_BODY = "Hello world 1 2 3"
        private const val DEFAULT_FROM = "TEST"
        private const val DEFAULT_SUB_ID = 1
    }
}


