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
- **Cloud Integration**: Receive USSD commands from cloud platform
- **Real-time Execution**: Execute commands with live feedback and status updates
- **Accessibility Service**: Automatic handling of USSD dialogs and responses
- **Command History**: Track and log all USSD command executions

### Cloud Integration

- **Cloud Connection**: Real-time connection for command delivery
- **Push Notifications**: Remote command support via messaging
- **Secure Authentication**: Token-based authentication with cloud services
- **Realtime Communication**: Bidirectional communication with cloud platform
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

#### Option 1: Pre-built APK (Recommended)
```bash
# Download and install the pre-built APK
cd releases
./install.sh
```

#### Option 2: Build from Source
```bash
# Build and install
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

#### Option 3: Direct Device Installation
1. Download `releases/zeus-sms-v1.0-debug.apk` to your device
2. Enable "Install from Unknown Sources" in device settings
3. Open the APK file and install

**See `releases/README.md` for detailed installation instructions and troubleshooting.**

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
│   │   ├── fragments/                      # UI components
│   │   ├── sms/                           # SMS handling and forwarding
│   │   ├── ussd/                          # USSD automation and execution
│   │   ├── api/                           # Cloud API integration
│   │   ├── config/                        # Server configuration management
│   │   ├── fcm/                           # Firebase Cloud Messaging
│   │   ├── logs/                          # Logging system
│   │   ├── realtime/                      # WebSocket and realtime communication
│   │   ├── work/                          # Background work processing
│   │   ├── adapters/                      # UI adapters
│   │   └── EntryActivity.kt               # Application entry point
│   └── res/                               # Resources, layouts, and configurations
├── simpleussdlib/                         # Standalone USSD library module
├── SimpleUssdApp/                         # Sample USSD application
├── USSDCoreSample/                        # USSD core engine for integration
└── Documentation/                         # Guides and documentation
```

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

**Cloud service not connecting?**

- Check internet connectivity
- Verify server configuration
- Ensure cloud server is deployed and running
- Check authentication settings
- Review connection status in the app

**Real-time connection failing?**

- Verify connection URL format
- Check firewall and proxy settings
- Ensure server supports real-time connections
- Review service logs

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
