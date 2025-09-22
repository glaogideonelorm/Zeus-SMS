# Zeus SMS v1.0 - Release Notes

## Release Date
September 22, 2025

## Installation
```bash
# Install via ADB
adb install zeus-sms-v1.0-debug.apk

# Or download and install directly on device
```

## New Features

### Enhanced Error Handling
- **Robust Job Failure Handling**: Jobs that fail due to network timeouts or other issues now send detailed error responses back to the server
- **No More Silent Failures**: The server will always be notified when a job fails, improving monitoring and debugging capabilities
- **Structured Error Responses**: Failed jobs send comprehensive error data including error type, message, and timestamp

### Improved Network Resilience
- **Extended Timeout**: HTTP read timeout increased from 30 to 60 seconds to handle slow server responses
- **Automatic Retry Logic**: Network failures now trigger automatic retry with exponential backoff (up to 2 retries)
- **Better Connection Handling**: Improved handling of temporary network issues and server unavailability

### Enhanced Logging and Debugging
- **Detailed Error Logging**: More comprehensive error information in logs for easier debugging
- **Retry Attempt Tracking**: Clear logging of retry attempts and failure reasons
- **Better Error Context**: Enhanced error messages with exception types and detailed context

## Technical Improvements

### SMS Management
- Dual-SIM support for capturing SMS from both SIM cards
- Real-time webhook delivery with retry mechanisms
- Multiple webhook configuration support
- Live monitoring with detailed activity logs

### USSD Automation
- Automated USSD execution via accessibility service
- Dual-SIM USSD support for running commands on both SIM slots
- Cloud integration for receiving USSD commands
- Real-time execution with live feedback and status updates
- Command history tracking and logging

### Cloud Integration
- Real-time connection for command delivery
- Push notification support for remote commands
- Secure token-based authentication
- Bidirectional communication with cloud platform
- Auto-reconnection on network changes
- Dynamic token refresh and validation

## Bug Fixes
- Fixed timeout issues where jobs failed silently without notifying the server
- Improved handling of HTTP timeout errors during job fetching
- Enhanced error recovery for network connectivity issues
- Better handling of malformed job configurations

## Requirements
- Android 7.0+ (API level 24+)
- SMS and Phone permissions
- Accessibility Service access (for USSD automation)
- Internet connection for webhook delivery and cloud integration

## Installation Instructions

### Prerequisites
- Android device with API 24+ (Android 7.0)
- Internet connection for webhook delivery and cloud integration
- SMS and Phone permissions
- Accessibility Service access (for USSD automation)

### Setup
1. Install the APK on your device
2. Grant required permissions when prompted
3. Configure webhook URLs in the Configuration tab
4. Enable Accessibility Service for USSD automation
5. Test with the built-in test functions

### Permissions Required
- **SMS**: Allow SMS permissions for message capture
- **Phone State**: Allow Phone State permissions for dual-SIM support
- **Phone Calls**: Allow for USSD code execution
- **Accessibility**: Enable for automated USSD dialog handling
- **Internet**: Required for webhook delivery and cloud integration

## Changelog from Previous Version
- Added comprehensive error handling and server response mechanisms
- Implemented retry logic with exponential backoff
- Increased HTTP timeout from 30 to 60 seconds
- Enhanced logging with detailed error context
- Added structured error response format for server communication
- Improved network resilience and connection handling

## Support
For issues or questions, please refer to the main README.md file or check the troubleshooting section.
