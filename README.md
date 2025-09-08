# ⚡ Zeus SMS

A specialized Android microservice that captures all incoming SMS messages from both SIM cards and forwards them to your server in real-time. Built for the Zeus application ecosystem with modern UI and comprehensive monitoring.

## 🚀 Features

- **Dual-SIM Support**: Captures SMS from both SIM cards
- **Real-time Forwarding**: Instant webhook delivery 
- **Modern UI**: Bottom navigation with Configuration and Activity Log
- **Live Monitoring**: Real-time status updates without refresh
- **Retry Mechanism**: Automatic and manual retry for failed messages
- **Battery Optimized**: Efficient background processing

## 📱 Setup Instructions

### 1. Prerequisites
- Android device with API 24+ (Android 7.0)
- Internet connection
- SMS permissions

### 2. Installation
```bash
# Build and install
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 3. Configuration
1. Open Zeus SMS app
2. Go to **Configuration** tab
3. Enter your webhook URL
4. Add optional secret key
5. Tap **Save Configuration**
6. Test with **Test Webhook** button

### 4. Grant Permissions
- Allow SMS permissions when prompted
- Allow Phone State permissions for dual-SIM support

### 5. Monitor Activity
- Switch to **Activity Log** tab
- View real-time SMS forwarding status
- Retry failed messages if needed

## 🔗 Webhook Format

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

## 🛠️ Development

### Requirements
- Android Studio Arctic Fox+
- Android SDK API 34
- Kotlin 1.9.20+

### Build
```bash
git clone [repository-url]
cd zeus-sms
./gradlew assembleDebug
```

### Project Structure
```
app/
├── src/main/java/com/example/smshook/
│   ├── fragments/      # UI components
│   ├── sms/           # SMS handling
│   ├── data/          # Data models & managers
│   └── adapter/       # RecyclerView adapters
└── res/               # Resources & layouts
```

## 🔧 Troubleshooting

**SMS not forwarding?**
- Check SMS permissions are granted
- Verify webhook URL is accessible
- Use Test Webhook to verify connection

**App not receiving SMS?**
- Disable battery optimization for the app
- Check dual-SIM settings if applicable

**View detailed logs in Activity Log tab for specific error messages**

---

*Private repository - Zeus application ecosystem*
