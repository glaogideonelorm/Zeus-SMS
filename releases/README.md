# Zeus SMS - Pre-built Releases

This directory contains pre-built APK files for easy installation of the Zeus SMS application.

## Quick Installation

### Option 1: Using the Installation Script (Recommended)

```bash
cd releases
./install.sh
```

### Option 2: Manual ADB Installation

```bash
adb install zeus-sms-v1.0-debug.apk
```

### Option 3: Direct Device Installation

1. Download `zeus-sms-v1.0-debug.apk` to your Android device
2. Enable "Install from Unknown Sources" in your device settings
3. Open the APK file and install

## Available Releases

### Zeus SMS v1.0 (Latest)

- **File**: `zeus-sms-v1.0-debug.apk`
- **Release Date**: September 22, 2025
- **Features**: Enhanced error handling, improved network resilience, comprehensive logging
- **Requirements**: Android 7.0+ (API level 24+)

## What's Included

### Core Features

- **SMS Management**: Dual-SIM support, real-time forwarding, multiple webhook configuration
- **USSD Automation**: Automated USSD execution, cloud integration, command history
- **Cloud Integration**: Real-time connection, push notifications, secure authentication
- **Enhanced Error Handling**: Robust failure handling with server notifications

### Technical Improvements

- HTTP timeout increased to 60 seconds
- Automatic retry logic with exponential backoff
- Structured error responses to server
- Comprehensive logging and debugging

## Prerequisites

### Device Requirements

- Android 7.0+ (API level 24+)
- Internet connection
- SMS and Phone permissions
- Accessibility Service access (for USSD automation)

### Installation Requirements

- USB Debugging enabled (for ADB installation)
- OR "Install from Unknown Sources" enabled (for direct installation)

## Setup Instructions

### 1. Install the APK

Use one of the installation methods above.

### 2. Grant Permissions

When you first open the app, grant the following permissions:

- **SMS**: For message capture
- **Phone State**: For dual-SIM support
- **Phone Calls**: For USSD execution
- **Accessibility**: For automated USSD handling
- **Internet**: For webhook delivery and cloud integration

### 3. Configure Webhooks

1. Go to the **Configuration** tab
2. Enter your webhook URL
3. Add optional secret key
4. Test the connection

### 4. Enable USSD Automation

1. Go to **Settings > Accessibility**
2. Find "Zeus USSD" or "Simple USSD" service
3. Enable the service
4. Grant all required permissions

### 5. Test Functionality

- Use the built-in test features to verify SMS forwarding
- Test USSD execution with predefined commands
- Check logs for any issues

## Troubleshooting

### Installation Issues

- **ADB not found**: Install Android SDK platform-tools
- **Device not detected**: Enable USB Debugging and allow USB Debugging when prompted
- **Installation failed**: Check device storage space and permissions

### Permission Issues

- **SMS not forwarding**: Ensure SMS permissions are granted
- **USSD not working**: Enable Accessibility Service in Settings > Accessibility
- **Webhook failures**: Check internet connection and webhook URL

### Performance Issues

- **Slow responses**: Check network connectivity
- **Battery drain**: Disable battery optimization for the app
- **Crashes**: Clear app data and reconfigure

## Support

For detailed information about features and troubleshooting, see:

- Main README.md in the project root
- RELEASE_NOTES_v1.0.md for version-specific information
- Troubleshooting section in the main documentation

## Version History

### v1.0 (September 22, 2025)

- Initial release with comprehensive SMS and USSD functionality
- Enhanced error handling and server communication
- Improved network resilience and retry logic
- Dual-SIM support and cloud integration
