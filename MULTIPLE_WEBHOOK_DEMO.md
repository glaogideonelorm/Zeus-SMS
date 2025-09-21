# Multiple Webhook URLs Demo

This document demonstrates how Zeus SMS now supports sending SMS to multiple webhook URLs simultaneously.

## ✅ Implementation Complete

### What's New

1. **Multiple Webhook Management UI**

   - Added RecyclerView for managing multiple webhook configurations
   - Individual webhook cards with enable/disable, test, edit, and delete functionality
   - Add new webhook dialog with name, URL, and secret fields

2. **Backend Support**

   - `WebhookConfig` data class supports multiple configurations
   - Priority-based processing (lower number = higher priority)
   - Individual secrets and enable/disable per webhook
   - Backward compatibility with single webhook configurations

3. **SMS Forwarding Logic**
   - SMS are sent to ALL enabled webhooks in priority order
   - Success is achieved if ANY webhook succeeds
   - Individual logging and error handling per webhook

### How It Works

When an SMS is received, the `ForwardWorker` processes webhooks in this order:

1. **Get Enabled Webhooks**: Retrieves all webhooks where `enabled = true`
2. **Sort by Priority**: Processes webhooks with lower priority numbers first
3. **Send to Each**: Attempts to send SMS payload to each webhook URL
4. **Track Success**: Returns success if at least one webhook succeeds
5. **Individual Logging**: Each webhook attempt is logged separately

### Example Configuration

```kotlin
val webhooks = listOf(
    WebhookConfig(
        id = "primary",
        name = "Primary Server",
        url = "https://api1.example.com/sms/webhook",
        secret = "secret1",
        enabled = true,
        priority = 0  // Highest priority
    ),
    WebhookConfig(
        id = "backup",
        name = "Backup Server",
        url = "https://api2.example.com/sms/webhook",
        secret = "secret2",
        enabled = true,
        priority = 1  // Lower priority
    )
)
```

### SMS Forwarding Flow

```
SMS Received
     ↓
Get Enabled Webhooks (sorted by priority)
     ↓
For each webhook:
  ├── Validate URL
  ├── Create HTTP request with secret
  ├── Send POST request
  ├── Log result
  └── Continue to next webhook
     ↓
Return SUCCESS if any webhook succeeded
Return FAILURE if all webhooks failed
```

### Testing Multiple Webhooks

To test the functionality:

1. **Add Webhooks**: Use the "Add Webhook" button in the Configuration screen
2. **Configure URLs**: Set different webhook URLs (can be test endpoints)
3. **Test Individual**: Use "Test" button on each webhook card
4. **Test All**: Send a real SMS to see it forwarded to all enabled webhooks

### Example Test URLs

For testing purposes, you can use these webhook testing services:

- **Primary**: `https://webhook.site/your-unique-id-1`
- **Backup**: `https://webhook.site/your-unique-id-2`
- **Local**: `https://httpbin.org/post` (for basic testing)

### Key Features

- ✅ **Multiple URLs**: Send to 2+ webhook endpoints simultaneously
- ✅ **Priority Ordering**: Process webhooks in priority order
- ✅ **Individual Control**: Enable/disable each webhook independently
- ✅ **Individual Secrets**: Each webhook can have its own authentication
- ✅ **Success Tracking**: Success if ANY webhook succeeds
- ✅ **Comprehensive Logging**: Individual logs for each webhook attempt
- ✅ **Backward Compatibility**: Still supports single webhook configurations
- ✅ **UI Management**: Full UI for adding, editing, and managing webhooks

### Usage Instructions

1. Open Zeus SMS app
2. Go to Configuration tab
3. Click "Add Webhook" button
4. Enter webhook details:
   - Name: "Primary Server"
   - URL: "https://your-server.com/api/sms/webhook"
   - Secret: "your-api-secret" (optional)
5. Click "Save Webhook"
6. Repeat for additional webhooks
7. Use toggle switches to enable/disable individual webhooks
8. Test individual webhooks or send real SMS to test all enabled webhooks

The SMS will now be sent to ALL enabled webhooks simultaneously! 🎉




