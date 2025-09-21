# Simple USSD App

A simplified Android application for executing predefined USSD commands automatically.

## Features

- Execute predefined USSD commands with one tap
- Automatic handling of USSD responses using Accessibility Service
- Simple, clean UI with 4 predefined commands
- Dual SIM support
- No complex scheduling or CSV imports

## Predefined USSD Commands

The app comes with 4 predefined USSD commands that you can modify:

1. **Check Balance** - `*123#` with option `1`
2. **Check Data** - `*131#` with option `2`
3. **Recharge** - `*555#` with option `1`
4. **Customer Care** - `*100#` (no options)

## How to Customize

To change the USSD commands, edit the `USSD_COMMANDS` array in `MainActivity.java`:

```java
private final String[][] USSD_COMMANDS = {
    {"Command Name", "*USSD_CODE#", "option"},
    // Add more commands as needed
};
```

## Setup Instructions

1. Build and install the APK
2. Grant phone permissions when prompted
3. Enable the accessibility service:
   - Go to Settings > Accessibility
   - Find "Simple USSD" service
   - Enable it
4. Return to the app and tap any USSD command

## Permissions Required

- `CALL_PHONE` - To execute USSD codes
- `READ_PHONE_STATE` - To access SIM information
- `SYSTEM_ALERT_WINDOW` - For overlay functionality
- `BIND_ACCESSIBILITY_SERVICE` - To automatically handle USSD dialogs

## Technical Notes

- Built for Android API 26+ (Android 8.0+)
- Uses Android's TelecomManager for USSD execution
- Accessibility Service intercepts system USSD dialogs
- Simplified architecture compared to the full Automatu app

## Build Requirements

- Android Studio 2023.1.1+
- Android SDK 35
- Gradle 8.1.0

## Installation

1. Open the project in Android Studio
2. Sync Gradle files
3. Build and run on device (emulator won't work for USSD)

## Limitations

- Requires physical device with SIM card
- USSD codes vary by carrier/country
- Accessibility service must be manually enabled
- No transaction history or advanced features

