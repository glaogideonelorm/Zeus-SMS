package com.yourpackage.simpleussd;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.yourpackage.simpleussd.ussd.UssdAccessibilityService;
import com.yourpackage.simpleussd.ussd.UssdCallback;
import com.yourpackage.simpleussd.ussd.UssdController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SimpleMainActivity extends Activity implements UssdCallback {

    private static final int REQUEST_PHONE_PERMISSION = 1;
    private static final int MAX_COMMANDS = 6; // Max 6 buttons
    private LinearLayout mainLayout;
    private UssdController ussdController;
    private SharedPreferences prefs;
    private int selectedSimSlot = 0; // Default to SIM 1
    private Button sim1Button, sim2Button;
    private String sim1Name = "SIM 1", sim2Name = "SIM 2";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ussdController = new UssdController(this);
        prefs = getSharedPreferences("ussd_commands", MODE_PRIVATE);
        initializeDefaultCommands();
        detectSimNetworks();
        createLayout();
        checkPermissions();
    }

    private void initializeDefaultCommands() {
        SharedPreferences.Editor editor = prefs.edit();

        if (!prefs.contains("command_0_name")) {
            editor.putString("command_0_name", "Check Balance");
            editor.putString("command_0_code", "*110#"); // Updated code
            editor.putString("command_0_options", "7,1,1514,0915,1"); // Updated options
        }

        if (!prefs.contains("command_1_name")) {
            editor.putString("command_1_name", "Check Data");
            editor.putString("command_1_code", "*131#");
            editor.putString("command_1_options", "");
        }

        if (!prefs.contains("command_2_name")) {
            editor.putString("command_2_name", "Customer Care");
            editor.putString("command_2_code", "*100#");
            editor.putString("command_2_options", "");
        }
        editor.apply();
    }
    
    private void detectSimNetworks() {
        try {
            if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                SubscriptionManager subscriptionManager = (SubscriptionManager) getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                if (subscriptionManager != null) {
                    List<SubscriptionInfo> subscriptionInfos = subscriptionManager.getActiveSubscriptionInfoList();
                    if (subscriptionInfos != null && subscriptionInfos.size() > 0) {
                        // Get SIM 1 info
                        if (subscriptionInfos.size() > 0) {
                            SubscriptionInfo sim1Info = subscriptionInfos.get(0);
                            String carrierName = sim1Info.getCarrierName() != null ? sim1Info.getCarrierName().toString() : "Unknown";
                            String simSerial = sim1Info.getIccId() != null ? sim1Info.getIccId().substring(Math.max(0, sim1Info.getIccId().length() - 4)) : "";
                            sim1Name = "SIM 1: " + carrierName + (simSerial.isEmpty() ? "" : " (..." + simSerial + ")");
                        }
                        
                        // Get SIM 2 info
                        if (subscriptionInfos.size() > 1) {
                            SubscriptionInfo sim2Info = subscriptionInfos.get(1);
                            String carrierName = sim2Info.getCarrierName() != null ? sim2Info.getCarrierName().toString() : "Unknown";
                            String simSerial = sim2Info.getIccId() != null ? sim2Info.getIccId().substring(Math.max(0, sim2Info.getIccId().length() - 4)) : "";
                            sim2Name = "SIM 2: " + carrierName + (simSerial.isEmpty() ? "" : " (..." + simSerial + ")");
                        } else {
                            sim2Name = "SIM 2: Not Available";
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Fallback to basic names if detection fails
            sim1Name = "SIM 1";
            sim2Name = "SIM 2";
        }
    }

    private void createLayout() {
        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(50, 50, 50, 50);

        TextView title = new TextView(this);
        title.setText("Simple USSD App (Dual SIM)");
        title.setTextSize(22);
        title.setPadding(0, 0, 0, 20);
        mainLayout.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Tap to execute • Long press to edit commands");
        subtitle.setTextSize(13);
        subtitle.setTextColor(0xFF666666);
        subtitle.setPadding(0, 0, 0, 20);
        mainLayout.addView(subtitle);
        
        // SIM Selection Section
        TextView simLabel = new TextView(this);
        simLabel.setText("Select SIM for USSD Commands:");
        simLabel.setTextSize(16);
        simLabel.setTextColor(0xFF333333);
        simLabel.setPadding(0, 0, 0, 10);
        mainLayout.addView(simLabel);
        
        // SIM Selection Buttons Container
        LinearLayout simButtonsLayout = new LinearLayout(this);
        simButtonsLayout.setOrientation(LinearLayout.HORIZONTAL);
        simButtonsLayout.setPadding(0, 0, 0, 30);
        
        // SIM 1 Button
        sim1Button = new Button(this);
        sim1Button.setText(sim1Name);
        sim1Button.setPadding(15, 25, 15, 25);
        sim1Button.setTextSize(14);
        LinearLayout.LayoutParams sim1Params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        sim1Params.setMargins(0, 0, 10, 0);
        sim1Button.setLayoutParams(sim1Params);
        sim1Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectSim(0);
            }
        });
        
        // SIM 2 Button
        sim2Button = new Button(this);
        sim2Button.setText(sim2Name);
        sim2Button.setPadding(15, 25, 15, 25);
        sim2Button.setTextSize(14);
        LinearLayout.LayoutParams sim2Params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
        sim2Params.setMargins(10, 0, 0, 0);
        sim2Button.setLayoutParams(sim2Params);
        sim2Button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectSim(1);
            }
        });
        
        simButtonsLayout.addView(sim1Button);
        simButtonsLayout.addView(sim2Button);
        mainLayout.addView(simButtonsLayout);
        
        // Initialize SIM selection
        selectSim(selectedSimSlot);

        // Create command buttons
        for (int i = 0; i < MAX_COMMANDS; i++) {
            String name = prefs.getString("command_" + i + "_name", null);
            String code = prefs.getString("command_" + i + "_code", null);
            String options = prefs.getString("command_" + i + "_options", null);

            if (name != null && code != null) {
                addCommandButton(i, name, code, options);
            } else if (i < 3) { // Ensure default buttons are always there if not customized
                // This block is mostly for initial setup, after first run, commands will be in prefs
            } else {
                // Add "Add New Command" button if there are fewer than MAX_COMMANDS and no more saved commands
                if (countExistingCommands() == i) {
                    addNewCommandButton(i);
                    break;
                }
            }
        }

        // Settings button
        Button settingsBtn = new Button(this);
        settingsBtn.setText("Open Accessibility Settings");
        settingsBtn.setPadding(20, 30, 20, 30);
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAccessibilitySettingsDialog();
            }
        });
        mainLayout.addView(settingsBtn);

        setContentView(mainLayout);
    }

    private int countExistingCommands() {
        int count = 0;
        for (int i = 0; i < MAX_COMMANDS; i++) {
            if (prefs.getString("command_" + i + "_name", null) != null) {
                count++;
            }
        }
        return count;
    }
    
    private void selectSim(int simSlot) {
        selectedSimSlot = simSlot;
        
        // Update button appearances
        if (simSlot == 0) {
            sim1Button.setBackgroundColor(0xFF2196F3); // Blue for selected
            sim1Button.setTextColor(0xFFFFFFFF); // White text
            sim2Button.setBackgroundColor(0xFFE0E0E0); // Gray for unselected
            sim2Button.setTextColor(0xFF333333); // Dark text
        } else {
            sim1Button.setBackgroundColor(0xFFE0E0E0); // Gray for unselected
            sim1Button.setTextColor(0xFF333333); // Dark text
            sim2Button.setBackgroundColor(0xFF2196F3); // Blue for selected
            sim2Button.setTextColor(0xFFFFFFFF); // White text
        }
        
        String simName = (simSlot == 0) ? sim1Name : sim2Name;
        Toast.makeText(this, "Selected: " + simName, Toast.LENGTH_SHORT).show();
    }

    private void addCommandButton(final int id, final String name, final String code, final String options) {
        Button button = new Button(this);
        button.setText(name);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16); // 16dp bottom margin
        button.setLayoutParams(params);
        button.setBackgroundColor(0xFF2196F3); // Blue background
        button.setTextColor(0xFFFFFFFF); // White text
        button.setPadding(20, 30, 20, 30);
        button.setOnClickListener(v -> executeUssdCommand(code, options));
        button.setOnLongClickListener(v -> {
            showEditCommandDialog(id, name, code, options);
            return true;
        });
        mainLayout.addView(button);
    }

    private void addNewCommandButton(final int id) {
        Button addButton = new Button(this);
        addButton.setText("+ Add New Command");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, 16);
        addButton.setLayoutParams(params);
        addButton.setBackgroundColor(0xFFE0E0E0); // Gray background
        addButton.setTextColor(0xFF333333); // Dark text
        addButton.setPadding(20, 30, 20, 30);
        addButton.setOnClickListener(v -> showEditCommandDialog(id, "", "", ""));
        mainLayout.addView(addButton);
    }

    private void showEditCommandDialog(final int id, String currentName, String currentCode, String currentOptions) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit USSD Command");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        final EditText nameInput = new EditText(this);
        nameInput.setHint("Command Name");
        nameInput.setText(currentName);
        layout.addView(nameInput);

        final EditText codeInput = new EditText(this);
        codeInput.setHint("USSD Code (e.g., *123#)");
        codeInput.setText(currentCode);
        codeInput.setInputType(InputType.TYPE_CLASS_PHONE);
        layout.addView(codeInput);

        final EditText optionsInput = new EditText(this);
        optionsInput.setHint("Options (comma-separated, e.g., 1,2,1234)");
        optionsInput.setText(currentOptions);
        layout.addView(optionsInput);

        builder.setView(layout);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String newName = nameInput.getText().toString().trim();
            String newCode = codeInput.getText().toString().trim();
            String newOptions = optionsInput.getText().toString().trim();

            if (newName.isEmpty() || newCode.isEmpty()) {
                Toast.makeText(this, "Name and USSD Code cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newCode.startsWith("*") || !newCode.endsWith("#")) {
                Toast.makeText(this, "USSD Code must start with * and end with #", Toast.LENGTH_SHORT).show();
                return;
            }

            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("command_" + id + "_name", newName);
            editor.putString("command_" + id + "_code", newCode);
            editor.putString("command_" + id + "_options", newOptions);
            editor.apply();
            createLayout(); // Refresh layout
            Toast.makeText(this, "Command saved!", Toast.LENGTH_SHORT).show();
        });

        if (currentName != null && !currentName.isEmpty()) { // Only show delete for existing commands
            builder.setNegativeButton("Delete", (dialog, which) -> {
                SharedPreferences.Editor editor = prefs.edit();
                editor.remove("command_" + id + "_name");
                editor.remove("command_" + id + "_code");
                editor.remove("command_" + id + "_options");
                editor.apply();
                createLayout(); // Refresh layout
                Toast.makeText(this, "Command deleted!", Toast.LENGTH_SHORT).show();
            });
        }

        builder.setNeutralButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void executeUssdCommand(String ussdCode, String options) {
        String simName = (selectedSimSlot == 0) ? sim1Name : sim2Name;
        Toast.makeText(this, "Executing: " + ussdCode + " on " + simName, Toast.LENGTH_SHORT).show();
        
        ArrayList<String> ussdOptions = new ArrayList<>();
        if (options != null && !options.isEmpty()) {
            String[] opts = options.split(",");
            for (String opt : opts) {
                ussdOptions.add(opt.trim());
            }
        }
        
        ussdController.executeUssd(ussdCode, selectedSimSlot, ussdOptions, this);
    }

    private void checkPermissions() {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE}, REQUEST_PHONE_PERMISSION);
        }
        if (!isAccessibilityServiceEnabled()) {
            showAccessibilitySettingsDialog();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PHONE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Phone permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Phone permissions required for USSD functionality", Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<android.accessibilityservice.AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC);
        for (android.accessibilityservice.AccessibilityServiceInfo service : enabledServices) {
            if (service.getId().contains(getPackageName() + "/" + UssdAccessibilityService.class.getSimpleName())) {
                return true;
            }
        }
        return false;
    }

    private void showAccessibilitySettingsDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Enable Accessibility Service")
                .setMessage("To automate USSD responses, please enable the 'Simple USSD' accessibility service.")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.cancel())
                .show();
    }

    @Override
    public void onUssdResponse(String response) {
        runOnUiThread(() -> {
            Toast.makeText(this, "USSD Response: " + response, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onUssdComplete(String finalResponse) {
        runOnUiThread(() -> {
            Toast.makeText(this, "USSD Complete: " + finalResponse, Toast.LENGTH_SHORT).show();
        });
    }
}