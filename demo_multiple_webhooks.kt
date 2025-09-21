/**
 * Demo script showing Zeus SMS multiple webhook functionality
 * 
 * This script demonstrates how Zeus SMS can send SMS to multiple URLs simultaneously
 */

import com.example.smshook.fragments.WebhookConfig
import com.example.smshook.fragments.ConfigurationFragment

fun main() {
    println("🚀 Zeus SMS Multiple Webhook Demo")
    println("==================================")
    
    // Create two different webhook configurations
    val primaryWebhook = WebhookConfig(
        id = "primary-server",
        name = "Primary Server",
        url = "https://primary-api.example.com/sms/webhook",
        secret = "primary-secret-key",
        enabled = true,
        priority = 0  // Highest priority
    )
    
    val backupWebhook = WebhookConfig(
        id = "backup-server", 
        name = "Backup Server",
        url = "https://backup-api.example.com/sms/webhook",
        secret = "backup-secret-key",
        enabled = true,
        priority = 1  // Lower priority
    )
    
    // Create a third webhook that's disabled
    val disabledWebhook = WebhookConfig(
        id = "disabled-server",
        name = "Disabled Server",
        url = "https://disabled-api.example.com/sms/webhook",
        secret = "disabled-secret",
        enabled = false,
        priority = 2
    )
    
    val allWebhooks = listOf(primaryWebhook, backupWebhook, disabledWebhook)
    
    println("\n📋 Configured Webhooks:")
    allWebhooks.forEach { webhook ->
        val status = if (webhook.enabled) "✅ Enabled" else "❌ Disabled"
        println("   ${webhook.name}: ${webhook.url} [$status] (Priority: ${webhook.priority})")
    }
    
    // Filter only enabled webhooks
    val enabledWebhooks = allWebhooks.filter { it.enabled }
    println("\n🔄 Processing enabled webhooks (${enabledWebhooks.size} total):")
    
    // Sort by priority (lower number = higher priority)
    val sortedWebhooks = enabledWebhooks.sortedBy { it.priority }
    
    sortedWebhooks.forEachIndexed { index, webhook ->
        println("   ${index + 1}. ${webhook.name} (Priority: ${webhook.priority})")
        println("      URL: ${webhook.url}")
        println("      Secret: ${if (webhook.secret.isNotEmpty()) "Set" else "Not set"}")
    }
    
    // Simulate SMS forwarding process
    println("\n📱 Simulating SMS forwarding process:")
    println("   SMS received: 'Hello from Zeus SMS!'")
    println("   Sender: +1234567890")
    println("   Timestamp: ${System.currentTimeMillis()}")
    
    var successCount = 0
    var failureCount = 0
    
    sortedWebhooks.forEach { webhook ->
        println("\n   🔗 Sending to ${webhook.name}...")
        
        // Simulate webhook call (in real implementation, this would be HTTP POST)
        val success = simulateWebhookCall(webhook)
        
        if (success) {
            println("      ✅ Success! Response: 200 OK")
            successCount++
        } else {
            println("      ❌ Failed! Response: 500 Internal Server Error")
            failureCount++
        }
    }
    
    // Final result
    println("\n📊 Final Result:")
    println("   Total webhooks: ${sortedWebhooks.size}")
    println("   Successful: $successCount")
    println("   Failed: $failureCount")
    
    val overallSuccess = successCount > 0
    println("   Overall Status: ${if (overallSuccess) "✅ SUCCESS" else "❌ FAILURE"}")
    
    if (overallSuccess) {
        println("\n🎉 SMS successfully forwarded to at least one webhook!")
        println("   Zeus SMS considers this operation successful.")
    } else {
        println("\n💥 All webhook calls failed.")
        println("   Zeus SMS will retry based on retry configuration.")
    }
    
    println("\n📝 Key Features Demonstrated:")
    println("   ✅ Multiple webhook URLs supported")
    println("   ✅ Priority-based processing")
    println("   ✅ Individual enable/disable control")
    println("   ✅ Individual secrets per webhook")
    println("   ✅ Success if ANY webhook succeeds")
    println("   ✅ Comprehensive error handling")
    println("   ✅ Backward compatibility maintained")
    
    println("\n🔧 How to Use in Zeus SMS App:")
    println("   1. Open Zeus SMS app")
    println("   2. Go to Configuration tab")
    println("   3. Click 'Add Webhook' button")
    println("   4. Enter webhook details and save")
    println("   5. Repeat for additional webhooks")
    println("   6. Use toggle switches to enable/disable")
    println("   7. Send SMS to test all enabled webhooks!")
}

/**
 * Simulates a webhook call to demonstrate the process
 */
fun simulateWebhookCall(webhook: WebhookConfig): Boolean {
    // Simulate different success rates for demo purposes
    return when (webhook.name) {
        "Primary Server" -> true   // Primary always succeeds
        "Backup Server" -> true    // Backup also succeeds in this demo
        else -> false              // Others fail
    }
}

/**
 * Example of how the ForwardWorker processes webhooks
 */
fun processWebhooksInWorker(webhooks: List<WebhookConfig>): String {
    val enabledWebhooks = webhooks.filter { it.enabled }.sortedBy { it.priority }
    var hasSuccess = false
    var lastError: String? = null
    
    for (webhook in enabledWebhooks) {
        try {
            // In real implementation: HTTP POST to webhook.url
            val success = simulateWebhookCall(webhook)
            if (success) {
                hasSuccess = true
                println("✅ Successfully sent to ${webhook.name}")
            } else {
                lastError = "Failed to send to ${webhook.name}"
            }
        } catch (e: Exception) {
            lastError = "Error sending to ${webhook.name}: ${e.message}"
        }
    }
    
    return if (hasSuccess) {
        "SUCCESS - At least one webhook succeeded"
    } else {
        "FAILURE - All webhooks failed. Last error: $lastError"
    }
}




