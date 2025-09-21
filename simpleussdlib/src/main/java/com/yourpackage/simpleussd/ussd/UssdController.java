package com.yourpackage.simpleussd.ussd;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.util.Log;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class UssdController {
    private static final String TAG = "UssdController";
    private final Context context;

    public UssdController(Context context) {
        this.context = context;
    }

    public void executeUssd(String ussdCode, int simSlot, ArrayList<String> options, UssdCallback callback) {
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

            String encoded = ussdCode.replace("#", Uri.encode("#"));
            Uri uri = Uri.parse("tel:" + encoded);
            Bundle extras = new Bundle();

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
}
