# Zeus SMS

A comprehensive Android application that provides SMS forwarding and USSD automation capabilities with cloud integration. Built for the Zeus application ecosystem with modern UI, real-time monitoring, and automated USSD command execution.

## Features

### SMS Management
- **Dual-SIM Support**: Captures SMS from both SIM cards simultaneously
- **Real-time Forwarding**: Instant webhook delivery to configured endpoints
- **Multiple Webhook Support**: Configure and manage multiple webhook endpoints
- **Live Monitoring**: Real-time status updates with detailed activity logs
- **Retry Mechanism**: Automatic and manual retry for failed message deliveries
- **Battery Optimized**: Efficient background processing with WorkManager

### USSD Automation
- **Automated USSD Execution**: Execute USSD codes automatically via accessibility service
- **Dual-SIM USSD Support**: Run USSD commands on both SIM slots
- **Cloud Integration**: Receive USSD commands from Zeus Cloud via WebSocket
- **Real-time Execution**: Execute commands with live feedback and status updates
- **Accessibility Service**: Automatic handling of USSD dialogs and responses
- **Command History**: Track and log all USSD command executions

### Cloud Integration
- **Zeus Cloud Connection**: Real-time WebSocket connection for command delivery
- **Firebase Cloud Messaging**: Push notification support for remote commands
- **JWT Authentication**: Secure token-based authentication with cloud services
- **Realtime Communication**: Bidirectional communication with Zeus Cloud platform
- **Auto-reconnection**: Automatic reconnection on network changes
- **Token Management**: Dynamic token refresh and validation

### User Interface
- **Modern Navigation**: Bottom navigation with Home, Configuration, and Logs
- **Entry Activity**: Unified entry point for SMS and USSD functionalities
- **Configuration Management**: Easy setup of webhook URLs and server endpoints
- **Live Status Indicators**: Real-time connection and service status
- **Comprehensive Logging**: Detailed logs for debugging and monitoring
- **Responsive Design**: Optimized for various screen sizes and orientations

## Setup Instructions

### Prerequisites
- Android device with API 24+ (Android 7.0)
- Internet connection for webhook delivery and cloud integration
- SMS and Phone permissions
- Accessibility Service access (for USSD automation)

### Installation
```bash
# Build and install
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### SMS Configuration
1. Open Zeus SMS app
2. Go to **Configuration** tab
3. Enter your webhook URL
4. Add optional secret key for authentication
5. Configure multiple webhooks if needed
6. Tap **Save Configuration**
7. Test with **Test Webhook** button

### USSD Setup
1. Navigate to **Zeus USSD** from the home screen
2. Enable Accessibility Service:
   - Go to Settings > Accessibility
   - Find "Zeus USSD" or "Simple USSD" service
   - Enable the service
3. Grant Phone permissions when prompted
4. Test USSD execution with predefined commands

### Grant Permissions
- **SMS**: Allow SMS permissions for message capture
- **Phone State**: Allow Phone State permissions for dual-SIM support
- **Phone Calls**: Allow for USSD code execution
- **Accessibility**: Enable for automated USSD dialog handling
- **Internet**: Required for webhook delivery and cloud integration

### Monitor Activity
- **SMS Logs**: View real-time SMS forwarding status and retry failed messages
- **USSD Logs**: Monitor USSD command execution history and results
- **Connection Status**: Check cloud connectivity and service status

## Data Formats

### SMS Webhook Format
Messages are sent as POST requests:

```json
{
  "RCSMessage": {
    "msgId": "zeus_1757325681000_a1b2c3d4",
    "textMessage": "SMS content here",
    "timestamp": "2025-01-08T10:01:21.000Z"
  },
  "messageContact": {
    "userContact": "+1234567890"
  },
  "event": "message"
}
```

### USSD Command Format (Zeus Cloud)
Commands received from Zeus Cloud:

```json
{
  "id": "msg_123",
  "type": "data",
  "body": {
    "commandName": "Mini statement check",
    "networkOperator": "MTN",
    "code": "*171#",
    "options": ["7", "4", "1", "2040"],
    "commandId": "cmd_456"
  }
}
```

### USSD Response Format
Results sent back to Zeus Cloud:

```json
{
  "type": "ussd_result",
  "body": {
    "commandId": "cmd_456",
    "commandName": "Mini statement check",
    "networkOperator": "MTN",
    "simSlot": 1,
    "success": true,
    "result": "Your account balance is ₦5,250...",
    "executionTimeMs": 15430
  }
}
```

## Development

### Requirements
- Android Studio Arctic Fox+
- Android SDK API 34
- Kotlin 1.9.20+
- Java 8+ for USSD modules

### Build
```bash
git clone [repository-url]
cd Zeus-SMS
./gradlew assembleDebug
```

### Project Structure
```
Zeus-SMS/
├── app/                                    # Main application module
│   ├── src/main/java/com/example/smshook/
│   │   ├── fragments/                      # UI components (Home, Configuration, Logs)
│   │   ├── sms/                           # SMS handling and forwarding
│   │   ├── ussd/                          # USSD automation and execution
│   │   ├── api/                           # Cloud API integration (ZeusApi)
│   │   ├── config/                        # Server configuration management
│   │   ├── fcm/                           # Firebase Cloud Messaging
│   │   ├── logs/                          # Logging system
│   │   ├── realtime/                      # WebSocket and realtime communication
│   │   ├── work/                          # Background work processing
│   │   ├── adapters/                      # RecyclerView adapters
│   │   └── EntryActivity.kt               # Application entry point
│   └── res/                               # Resources, layouts, and configurations
├── simpleussdlib/                         # Standalone USSD library module
├── SimpleUssdApp/                         # Sample USSD application
├── USSDCoreSample/                        # USSD core engine for integration
└── Documentation/                         # Guides and documentation
```

### Key Components

#### SMS Management
- **SmsReceiver**: Captures incoming SMS messages
- **ForwardWorker**: Handles webhook delivery with retry logic
- **ConfigurationFragment**: Manages webhook URLs and settings

#### USSD Automation
- **UssdController**: Manages USSD code execution
- **UssdAccessibilityService**: Handles USSD dialog automation
- **ZeusUssdActivity**: Main USSD interface with navigation

#### Cloud Integration
- **ZeusApi**: REST API client for cloud communication
- **RealtimeService**: WebSocket connection to Zeus Cloud
- **ZeusFcmService**: Firebase Cloud Messaging integration
- **ServerConfig**: Dynamic server configuration management

## Troubleshooting

### SMS Issues
**SMS not forwarding?**
- Check SMS permissions are granted
- Verify webhook URL is accessible
- Use Test Webhook to verify connection
- Check network connectivity
- Review Activity Log for specific error messages

**App not receiving SMS?**
- Disable battery optimization for the app
- Check dual-SIM settings if applicable
- Ensure SMS permissions are properly granted
- Verify the app is set as default SMS handler (if required)

### USSD Issues
**USSD commands not executing?**
- Enable Accessibility Service in Settings > Accessibility
- Grant Phone and Phone State permissions
- Check if USSD codes are valid for your carrier
- Verify SIM card is active and has network signal
- Review USSD logs for execution details

**Accessibility Service not working?**
- Go to Settings > Accessibility
- Find "Zeus USSD" or "Simple USSD" service
- Enable the service and grant all permissions
- Restart the app after enabling the service

### Cloud Connection Issues
**Zeus Cloud not connecting?**
- Check internet connectivity
- Verify server URLs in configuration
- Ensure Zeus Cloud server is deployed and running
- Check JWT token authentication
- Review connection status in the app

**WebSocket connection failing?**
- Verify WebSocket URL format (wss:// or ws://)
- Check firewall and proxy settings
- Ensure server supports WebSocket connections
- Review realtime service logs

### General Issues
**App crashes or behaves unexpectedly?**
- Clear app data and reconfigure
- Check device compatibility (Android 7.0+)
- Review crash logs in the Activity Log
- Ensure all required permissions are granted

**Performance issues?**
- Disable battery optimization for the app
- Check available storage space
- Close other resource-intensive apps
- Restart the device if necessary

## Additional Resources

- **Crash Fix Summary**: See `CRASH_FIX_SUMMARY.md` for known issues and solutions
- **Multiple Webhook Demo**: See `MULTIPLE_WEBHOOK_DEMO.md` for advanced webhook configuration
- **Zeus Cloud Connection Guide**: See `ZEUS_CLOUD_CONNECTION_GUIDE.md` for cloud integration setup
- **Simple USSD Customization**: See `SimpleUssdApp/CUSTOMIZATION_GUIDE.md` for USSD command customization

## License

Private repository - Zeus application ecosystem
