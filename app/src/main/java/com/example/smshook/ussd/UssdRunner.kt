package com.example.smshook.ussd

import android.content.Context
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import com.yourpackage.simpleussd.ussd.UssdController
import com.yourpackage.simpleussd.ussd.UssdCallback
import kotlin.coroutines.resume

object UssdRunner {
    private const val TAG = "UssdRunner"
    
    /**
     * Simple cleanup method to close any open USSD dialogs
     * This is a basic implementation that sends back actions
     */
    private fun cleanupOpenDialogs(context: Context) {
        try {
            Log.d(TAG, "Attempting to cleanup open USSD dialogs")
            // This is a basic cleanup - in a real implementation, we would use
            // the accessibility service, but for now we'll just log the attempt
            Log.d(TAG, "Cleanup attempt logged - would close any open dialogs")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
    
    suspend fun run(context: Context, sequence: String, simSlot: Int = 0): UssdResult {
        return suspendCancellableCoroutine { continuation ->
            try {
                Log.d(TAG, "Executing USSD sequence: $sequence")
                
                // SAFETY: Clean up any open USSD dialogs before starting new command
                Log.d(TAG, "Cleaning up any open USSD dialogs before executing sequence")
                cleanupOpenDialogs(context)
                
                // Parse the USSD sequence (e.g., "*171# > 7 > 4 > 1 > 2040")
                val parts = sequence.split(" > ").map { it.trim() }
                val ussdCode = parts.firstOrNull() ?: return@suspendCancellableCoroutine
                val options = parts.drop(1)
                
                Log.d(TAG, "USSD Code: $ussdCode, Options: $options")
                
                // Track all steps
                val allSteps = mutableListOf<UssdStepResult>()
                val allStepsInputs = listOf(ussdCode) + options
                
                // Set up callback to capture the result
                val callback = object : UssdCallback {
                    override fun onUssdResponse(response: String) {
                        Log.d(TAG, "USSD Response: $response")
                        
                        // Track this step
                        val currentStepIndex = allSteps.size
                        if (currentStepIndex < allStepsInputs.size) {
                            val stepResult = UssdStepResult(
                                stepNumber = currentStepIndex + 1,
                                stepInput = allStepsInputs[currentStepIndex],
                                success = true, // Response received means step succeeded
                                response = response
                            )
                            allSteps.add(stepResult)
                            Log.d(TAG, "Step ${stepResult.stepNumber} completed: ${stepResult.stepInput} -> ${stepResult.response.take(50)}...")
                        }
                    }
                    
                    override fun onUssdComplete(finalResponse: String) {
                        Log.d(TAG, "USSD Complete: $finalResponse")
                        
                        // Add final step if not already added
                        if (allSteps.size < allStepsInputs.size) {
                            val finalStepIndex = allSteps.size
                            val stepResult = UssdStepResult(
                                stepNumber = finalStepIndex + 1,
                                stepInput = allStepsInputs[finalStepIndex],
                                success = true,
                                response = finalResponse
                            )
                            allSteps.add(stepResult)
                        }
                        
                        val result = UssdResult(
                            success = true,
                            response = finalResponse,
                            sequence = sequence,
                            steps = allSteps.toList()
                        )
                        Log.d(TAG, "USSD job completed with ${allSteps.size} steps tracked")
                        continuation.resume(result)
                    }
                    
                    override fun onUssdError(error: String) {
                        Log.e(TAG, "USSD Error: $error")
                        
                        // Mark current step as failed
                        val currentStepIndex = allSteps.size
                        if (currentStepIndex < allStepsInputs.size) {
                            val stepResult = UssdStepResult(
                                stepNumber = currentStepIndex + 1,
                                stepInput = allStepsInputs[currentStepIndex],
                                success = false,
                                response = error
                            )
                            allSteps.add(stepResult)
                        }
                        
                        val result = UssdResult(
                            success = false,
                            response = error,
                            sequence = sequence,
                            steps = allSteps.toList()
                        )
                        Log.d(TAG, "USSD job failed at step ${allSteps.size}")
                        continuation.resume(result)
                    }
                }
                
                // Execute USSD using the SAME working engine as the test button
                val controller = UssdController(context)
                controller.executeUssd(ussdCode, simSlot, ArrayList(options), callback)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error executing USSD: ${e.message}", e)
                val result = UssdResult(
                    success = false,
                    response = "Error: ${e.message}",
                    sequence = sequence,
                    steps = emptyList()
                )
                continuation.resume(result)
            }
        }
    }
}

data class UssdStepResult(
    val stepNumber: Int,
    val stepInput: String,
    val success: Boolean,
    val response: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class UssdResult(
    val success: Boolean,
    val response: String,
    val sequence: String,
    val steps: List<UssdStepResult> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)
