package com.yourpackage.simpleussd.ussd;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;

import java.util.ArrayList;
import java.util.List;

public class UssdController {
    private static final String TAG = "UssdController";
    private Context context;
    private UssdCallback callback;
    private ArrayList<String> pendingOptions;
    private int currentOptionIndex = 0;

    public UssdController(Context context) {
        this.context = context;
    }

    public void executeUssd(String ussdCode, int simSlot, ArrayList<String> options, UssdCallback callback) {
        this.callback = callback;
        this.pendingOptions = options;
        this.currentOptionIndex = 0;
        
        // Set the callback in the accessibility service
        UssdAccessibilityService.setUssdCallback(callback);
        UssdAccessibilityService.setPendingOptions(options);
        
        Log.d(TAG, "Executing USSD: " + ussdCode + " with options: " + options);
        dialUssd(ussdCode, simSlot);
    }

    private void dialUssd(String ussdCode, int simSlot) {
        try {
            TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
            if (telecomManager == null) {
                Log.e(TAG, "TelecomManager not available");
                return;
            }

            Uri uri = Uri.fromParts("tel", ussdCode, null);
            Bundle extras = new Bundle();
            
            // Handle dual SIM if needed
            List<PhoneAccountHandle> phoneAccounts = telecomManager.getCallCapablePhoneAccounts();
            if (phoneAccounts != null && phoneAccounts.size() > simSlot && simSlot >= 0) {
                extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, phoneAccounts.get(simSlot));
            }

            telecomManager.placeCall(uri, extras);
            Log.d(TAG, "USSD call placed: " + ussdCode);
            
        } catch (Exception e) {
            Log.e(TAG, "Error placing USSD call", e);
        }
    }

    public boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;

        String packageName = context.getPackageName();
        String className = UssdAccessibilityService.class.getName();
        String serviceId = packageName + "/" + className;

        return am.getInstalledAccessibilityServiceList().stream()
                .anyMatch(serviceInfo -> serviceInfo.getId().equals(serviceId));
    }

    /**
     * Public static method to cleanup any open USSD dialogs for safety
     * This can be called from the main app
     */
    public static void cleanupOpenDialogs() {
        UssdAccessibilityService.cleanupOpenDialogs();
    }
}

