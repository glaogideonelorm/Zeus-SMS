package com.example.ussdcore;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.view.accessibility.AccessibilityManager;

import java.util.ArrayList;
import java.util.List;

public class UssdController {
    private final Context context;

    public UssdController(Context context) {
        this.context = context.getApplicationContext();
    }

    public void executeUssd(String ussdCode, int simSlot, ArrayList<String> options, UssdCallback callback) {
        UssdAccessibilityService.setUssdCallback(callback);
        UssdAccessibilityService.setPendingOptions(options);
        dialUssd(ussdCode, simSlot);
    }

    private void dialUssd(String ussdCode, int simSlot) {
        TelecomManager telecomManager = (TelecomManager) context.getSystemService(Context.TELECOM_SERVICE);
        if (telecomManager == null) return;

        Uri uri = Uri.fromParts("tel", ussdCode, null);
        Bundle extras = new Bundle();

        List<PhoneAccountHandle> accounts = telecomManager.getCallCapablePhoneAccounts();
        if (accounts != null && simSlot >= 0 && simSlot < accounts.size()) {
            extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, accounts.get(simSlot));
        }

        telecomManager.placeCall(uri, extras);
    }

    public boolean isAccessibilityEnabled() {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        return am.isEnabled();
    }
}


