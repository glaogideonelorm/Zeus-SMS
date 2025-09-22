package com.example.smshook.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.smshook.api.ZeusApi
import com.example.smshook.logs.LogLevel
import com.example.smshook.logs.LogManager
import com.example.smshook.ussd.UssdRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RunJobWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val TAG = "RunJobWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val jobId = inputData.getString("jobId") ?: return@withContext Result.failure()
        
        try {
            Log.d(TAG, "Starting USSD job: $jobId")
            LogManager.addLog(LogLevel.USSD, TAG, "Starting USSD job", "Job ID: $jobId")
            
            // 1) Pull full job details from server with timeout handling
            val job = try {
                ZeusApi.getJob(applicationContext, jobId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch job details: ${e.message}", e)
                LogManager.addLog(LogLevel.ERROR, TAG, "Failed to fetch job details", "Error: ${e.message}, Type: ${e.javaClass.simpleName}")
                
                // Send error response back to server
                sendJobFailureResponse(jobId, "Failed to fetch job details: ${e.message}", e.javaClass.simpleName)
                return@withContext Result.failure()
            }
            
            Log.d(TAG, "Job details: ${job.seq}")
            LogManager.addLog(LogLevel.USSD, TAG, "Job details received", "Sequence: ${job.seq}")
            
            // 2) Build sequence string the same way as the test button
            val sequence: String = when {
                job.seq != null -> job.seq
                job.code != null && job.steps != null -> {
                    // Build sequence like "*171# > 7 > 4 > 1 > 2040"
                    val steps = job.steps.joinToString(" > ")
                    "${job.code} > $steps"
                }
                else -> {
                    val error = "Missing USSD sequence: code/steps or seq required"
                    Log.e(TAG, error)
                    LogManager.addLog(LogLevel.ERROR, TAG, "Invalid job configuration", error)
                    sendJobFailureResponse(jobId, error, "IllegalArgumentException")
                    return@withContext Result.failure()
                }
            }

            Log.d(TAG, "Built sequence: $sequence")
            LogManager.addLog(LogLevel.USSD, TAG, "Built USSD sequence", "Sequence: $sequence")

            // Use the SIM slot from the job, default to 0 if not specified
            val simSlot = job.simSlot ?: 0
            Log.d(TAG, "Using SIM slot: $simSlot")
            LogManager.addLog(LogLevel.USSD, TAG, "Using SIM slot", "Slot: $simSlot")

            val outcome = UssdRunner.run(applicationContext, sequence, simSlot)
            Log.d(TAG, "USSD execution result: $outcome")
            
            // Log step-by-step results
            outcome.steps.forEach { step ->
                val stepStatus = if (step.success) "✅" else "❌"
                LogManager.addLog(LogLevel.USSD, TAG, "Step ${step.stepNumber}: $stepStatus", 
                    "Input: ${step.stepInput} -> Response: ${step.response.take(100)}...")
            }
            
            LogManager.addLog(LogLevel.USSD, TAG, "USSD execution completed", 
                "Overall Success: ${outcome.success}, Steps: ${outcome.steps.size}, Final Response: ${outcome.response.take(50)}...")
            
            // 3) Send result back to server
            ZeusApi.completeJob(applicationContext, jobId, outcome)
            
            // 4) Send detailed USSD response to server
            ZeusApi.sendUssdResponse(applicationContext, jobId, outcome.response, outcome.success, outcome.steps)
            
            Log.d(TAG, "Job completed successfully: $jobId")
            LogManager.addLog(LogLevel.USSD, TAG, "Job completed successfully", "Job ID: $jobId")
            Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Job failed: ${e.message}", e)
            LogManager.addLog(LogLevel.ERROR, TAG, "Job failed", "Error: ${e.message}, Type: ${e.javaClass.simpleName}")
            
            // Send error response back to server
            sendJobFailureResponse(jobId, e.message ?: "Unknown error", e.javaClass.simpleName)
            
            Result.failure()
        }
    }
    
    /**
     * Send failure response back to server when job cannot be completed
     */
    private suspend fun sendJobFailureResponse(jobId: String, errorMessage: String, errorType: String) {
        try {
            Log.d(TAG, "Sending job failure response for job: $jobId")
            LogManager.addLog(LogLevel.API, TAG, "Sending job failure response", "Job ID: $jobId, Error: $errorMessage")
            
            // Create a failure outcome
            val failureOutcome = mapOf(
                "jobId" to jobId,
                "success" to false,
                "error" to errorMessage,
                "errorType" to errorType,
                "timestamp" to System.currentTimeMillis()
            )
            
            // Send failure response to server
            ZeusApi.completeJob(applicationContext, jobId, failureOutcome)
            
            // Also send as USSD response for consistency
            ZeusApi.sendUssdResponse(
                context = applicationContext,
                jobId = jobId,
                finalResponse = "Job failed: $errorMessage",
                success = false,
                steps = emptyList()
            )
            
            Log.d(TAG, "Job failure response sent successfully: $jobId")
            LogManager.addLog(LogLevel.API, TAG, "Job failure response sent successfully", "Job ID: $jobId")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send job failure response: ${e.message}", e)
            LogManager.addLog(LogLevel.ERROR, TAG, "Failed to send job failure response", "Error: ${e.message}")
        }
    }
}
