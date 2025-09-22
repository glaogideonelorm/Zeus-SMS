#!/bin/bash

# Zeus SMS Installation Script
# This script helps install the Zeus SMS APK on your Android device

echo "Zeus SMS Installation Script"
echo "============================"

# Check if adb is available
if ! command -v adb &> /dev/null; then
    echo "❌ ADB not found. Please install Android SDK platform-tools."
    echo "   Download from: https://developer.android.com/studio/releases/platform-tools"
    exit 1
fi

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "❌ No Android device found. Please:"
    echo "   1. Connect your device via USB"
    echo "   2. Enable USB Debugging in Developer Options"
    echo "   3. Allow USB Debugging when prompted"
    exit 1
fi

echo "✅ Device found: $(adb devices | grep "device$" | head -1 | cut -f1)"

# Check if APK file exists
APK_FILE="zeus-sms-v1.0-debug.apk"
if [ ! -f "$APK_FILE" ]; then
    echo "❌ APK file not found: $APK_FILE"
    echo "   Please make sure you're running this script from the releases directory"
    exit 1
fi

echo "✅ APK file found: $APK_FILE"

# Install the APK
echo "📱 Installing Zeus SMS..."
if adb install -r "$APK_FILE"; then
    echo "✅ Installation successful!"
    echo ""
    echo "Next steps:"
    echo "1. Open the Zeus SMS app on your device"
    echo "2. Grant required permissions (SMS, Phone, Accessibility)"
    echo "3. Configure your webhook URLs in the Configuration tab"
    echo "4. Enable Accessibility Service for USSD automation"
    echo "5. Test the functionality with the built-in test features"
    echo ""
    echo "For detailed setup instructions, see RELEASE_NOTES_v1.0.md"
else
    echo "❌ Installation failed. Please check the error messages above."
    exit 1
fi
