# Customization Guide for Simple USSD App

## How to Add/Modify USSD Commands

### Step 1: Edit MainActivity.java

Open `app/src/main/java/com/yourpackage/simpleussd/MainActivity.java` and find the `USSD_COMMANDS` array:

```java
private final String[][] USSD_COMMANDS = {
    {"Check Balance", "*123#", "1"},
    {"Check Data", "*131#", "2"},
    {"Recharge", "*555#", "1"},
    {"Customer Care", "*100#", ""}
};
```

### Step 2: Modify Commands

Each command has 3 parts:

- **Display Name**: What shows on the button
- **USSD Code**: The actual code to dial (e.g., `*123#`)
- **Options**: Automatic responses (comma-separated for multiple)

Examples:

```java
// Single option
{"Balance Check", "*123#", "1"}

// Multiple options
{"Transfer Money", "*456#", "1,2,100,1234"}

// No options (just dial)
{"Customer Service", "*100#", ""}
```

### Step 3: Update UI Layout

If you add more than 4 commands, edit `activity_main.xml`:

```xml
<Button
    android:id="@+id/btn_ussd_4"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:text="New Command" />
```

### Step 4: Common USSD Codes by Region

**Nigeria:**

- Balance: `*556#`
- Data: `*131*4#`
- Airtime transfer: `*777*PIN*Amount*Number#`

**Ghana:**

- Balance: `*124#`
- Data: `*138#`

**Kenya:**

- Balance: `*144#`
- M-Pesa: `*334#`

**India:**

- Balance: `*123#`
- Data: `*121#`

## Advanced Customization

### Adding Multi-Step USSD Flows

For complex USSD flows with multiple steps:

```java
// Example: Transfer money with PIN
{"Send Money", "*777#", "1,100,1234567890,1234,1"}
```

This sends:

1. `1` (select transfer)
2. `100` (amount)
3. `1234567890` (recipient number)
4. `1234` (PIN)
5. `1` (confirm)

### Customizing Response Handling

Edit `UssdAccessibilityService.java` to modify how responses are handled:

```java
private boolean isFinalResponse(String response) {
    String lower = response.toLowerCase();
    return lower.contains("successful") ||
           lower.contains("completed") ||
           lower.contains("your_custom_keyword");
}
```

### Changing App Appearance

1. **Colors**: Edit `res/values/styles.xml`
2. **Button styles**: Modify `res/drawable/button_primary.xml`
3. **App name**: Change in `res/values/strings.xml`

### Package Name Change

1. Update package in `AndroidManifest.xml`
2. Rename Java package folders
3. Update imports in all Java files
4. Change `applicationId` in `app/build.gradle`

## Testing Your Changes

1. Always test on a real device with SIM card
2. Test each USSD command manually first
3. Check accessibility service is enabled
4. Verify permissions are granted

## Troubleshooting

**USSD not executing:**

- Check phone permissions
- Verify accessibility service is enabled
- Ensure USSD code is valid for your carrier

**App crashes:**

- Check Android logs for errors
- Verify all button IDs exist in layout
- Ensure USSD commands array is properly formatted

**Accessibility not working:**

- Go to Settings > Accessibility
- Find and enable "Simple USSD" service
- Grant all requested permissions

