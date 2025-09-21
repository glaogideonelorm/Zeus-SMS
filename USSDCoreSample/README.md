# USSD Core Sample (drop-in engine)

This folder contains a minimal, reusable USSD automation engine you can drop into any Android app.

Contents:

- Java:
  - `com.example.ussdcore.UssdCallback`
  - `com.example.ussdcore.UssdController`
  - `com.example.ussdcore.UssdAccessibilityService`
- XML:
  - `res/xml/accessibility_service_config.xml`

## How it works

- Dials a USSD code via `TelecomManager` (supports dual SIM with `PhoneAccountHandle`).
- An `AccessibilityService` observes USSD dialogs from `com.android.phone`, extracts text, fills options/PINs, and taps OK/Send/Continue.
- Reports intermediate and final responses via `UssdCallback`.

## Integrate into your app

1. Copy files

- Copy `src/main/java/com/example/ussdcore/*` into your app module under `app/src/main/java/com/example/ussdcore/`.
- Copy `res/xml/accessibility_service_config.xml` to your app’s `app/src/main/res/xml/`.

2. Register service in `AndroidManifest.xml`

```xml
<application>
  <service
      android:name="com.example.ussdcore.UssdAccessibilityService"
      android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
      android:exported="true">
    <intent-filter>
      <action android:name="android.accessibilityservice.AccessibilityService"/>
    </intent-filter>
    <meta-data
      android:name="android.accessibilityservice"
      android:resource="@xml/accessibility_service_config"/>
  </service>
  <!-- If you need internet for logging or callbacks -->
  <!-- <uses-permission android:name="android.permission.INTERNET"/> -->
</application>
```

3. Permissions (in manifest root)

```xml
<uses-permission android:name="android.permission.CALL_PHONE"/>
<uses-permission android:name="android.permission.READ_PHONE_STATE"/>
```

4. Enable the service

- On the device: Settings → Accessibility → enable your app’s “USSD Core Service”.

5. Execute a USSD flow

```java
ArrayList<String> options = new ArrayList<>();
Collections.addAll(options, "7","1","1514","0915","1");
UssdController controller = new UssdController(context);
controller.executeUssd("*110#", /*simSlot=*/0, options, new UssdCallback() {
  @Override public void onUssdResponse(String response) {
    Log.d("USSD", "Step: " + response);
  }
  @Override public void onUssdComplete(String finalResponse) {
    Log.d("USSD", "Done: " + finalResponse);
  }
});
```

6. Dual SIM selection (optional)

- Choose `simSlot` (0 or 1). The controller maps it to a `PhoneAccountHandle` via `TelecomManager.getCallCapablePhoneAccounts()`.

## Notes

- Button text matching includes "OK", "Send", "Continue". Add more synonyms if your carrier uses different labels.
- Final response detection is heuristic. Tweak `isFinalResponse()` for your locale/carrier.
- OEM/Android variations may require adjusting the `packageNames` or dialog class checks.
