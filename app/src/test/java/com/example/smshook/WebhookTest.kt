package com.example.smshook

import com.example.smshook.fragments.WebhookConfig
import com.example.smshook.fragments.ConfigurationFragment
import org.junit.Test
import org.junit.Assert.*

/**
 * Test class to demonstrate and verify multiple webhook functionality
 */
class WebhookTest {

    @Test
    fun testMultipleWebhookConfigurations() {
        // Create two different webhook configurations
        val webhook1 = WebhookConfig(
            id = "webhook1",
            name = "Primary Server",
            url = "https://primary-server.com/api/sms/webhook",
            secret = "secret1",
            enabled = true,
            priority = 0
        )

        val webhook2 = WebhookConfig(
            id = "webhook2",
            name = "Backup Server",
            url = "https://backup-server.com/api/sms/webhook",
            secret = "secret2",
            enabled = true,
            priority = 1
        )

        val webhooks = listOf(webhook1, webhook2)

        // Verify webhooks are configured correctly
        assertEquals(2, webhooks.size)
        assertTrue(webhook1.enabled)
        assertTrue(webhook2.enabled)
        assertEquals(0, webhook1.priority)
        assertEquals(1, webhook2.priority)

        // Verify webhook URLs are different
        assertNotEquals(webhook1.url, webhook2.url)
        assertNotEquals(webhook1.secret, webhook2.secret)

        println("✅ Multiple webhook configuration test passed")
        println("   - Primary Server: ${webhook1.url}")
        println("   - Backup Server: ${webhook2.url}")
    }

    @Test
    fun testWebhookPriorityOrdering() {
        val webhooks = listOf(
            WebhookConfig("1", "Low Priority", "https://low.com", enabled = true, priority = 2),
            WebhookConfig("2", "High Priority", "https://high.com", enabled = true, priority = 0),
            WebhookConfig("3", "Medium Priority", "https://medium.com", enabled = true, priority = 1)
        )

        // Sort by priority (lower number = higher priority)
        val sortedWebhooks = webhooks.sortedBy { it.priority }

        assertEquals("High Priority", sortedWebhooks[0].name)
        assertEquals("Medium Priority", sortedWebhooks[1].name)
        assertEquals("Low Priority", sortedWebhooks[2].name)

        println("✅ Webhook priority ordering test passed")
        println("   - Processing order: ${sortedWebhooks.map { it.name }}")
    }

    @Test
    fun testEnabledWebhookFiltering() {
        val webhooks = listOf(
            WebhookConfig("1", "Enabled 1", "https://enabled1.com", enabled = true, priority = 0),
            WebhookConfig("2", "Disabled", "https://disabled.com", enabled = false, priority = 1),
            WebhookConfig("3", "Enabled 2", "https://enabled2.com", enabled = true, priority = 2)
        )

        // Filter only enabled webhooks
        val enabledWebhooks = webhooks.filter { it.enabled }

        assertEquals(2, enabledWebhooks.size)
        assertTrue(enabledWebhooks.all { it.enabled })
        assertFalse(enabledWebhooks.any { it.name == "Disabled" })

        println("✅ Enabled webhook filtering test passed")
        println("   - Enabled webhooks: ${enabledWebhooks.map { it.name }}")
    }

    @Test
    fun testWebhookDataStructure() {
        val webhook = WebhookConfig(
            id = "test-id",
            name = "Test Webhook",
            url = "https://test.example.com/webhook",
            secret = "test-secret",
            enabled = true,
            priority = 5
        )

        // Test all properties
        assertEquals("test-id", webhook.id)
        assertEquals("Test Webhook", webhook.name)
        assertEquals("https://test.example.com/webhook", webhook.url)
        assertEquals("test-secret", webhook.secret)
        assertTrue(webhook.enabled)
        assertEquals(5, webhook.priority)

        println("✅ Webhook data structure test passed")
        println("   - Webhook: ${webhook.name} -> ${webhook.url}")
    }
}




