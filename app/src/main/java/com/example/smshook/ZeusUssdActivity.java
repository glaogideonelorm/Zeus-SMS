package com.example.smshook;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.yourpackage.simpleussd.ussd.UssdCallback;
import com.yourpackage.simpleussd.ussd.UssdController;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class ZeusUssdActivity extends AppCompatActivity implements UssdCallback {
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zeus_ussd_with_nav);
        
        setupBottomNavigation();
        
        // Handle auto-execution from Zeus Cloud FCM
        handleAutoExecution();
    }
    
    private void setupBottomNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            androidx.navigation.NavController navController = navHostFragment.getNavController();
            BottomNavigationView bottomNavigationView = findViewById(R.id.bottom_navigation);
            NavigationUI.setupWithNavController(bottomNavigationView, navController);
        }
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Handle auto-execution when activity is already running
        handleAutoExecution();
    }
    
    private void handleAutoExecution() {
        Intent intent = getIntent();
        if (intent != null && intent.getBooleanExtra("auto_execute", false)) {
            String ussdCode = intent.getStringExtra("ussd_code");
            String optionsString = intent.getStringExtra("options");
            String commandId = intent.getStringExtra("command_id");
            String commandName = intent.getStringExtra("command_name");
            String networkOperator = intent.getStringExtra("network_operator");
            int simSlot = intent.getIntExtra("sim_slot", 0);
            long startTime = intent.getLongExtra("start_time", System.currentTimeMillis());
            
            if (ussdCode != null) {
                // Store command metadata for later use
                SharedPreferences prefs = getSharedPreferences("zeus_cloud", MODE_PRIVATE);
                prefs.edit()
                    .putString("current_command_id", commandId)
                    .putString("current_command_name", commandName)
                    .putString("current_network_operator", networkOperator)
                    .putInt("current_sim_slot", simSlot)
                    .putLong("current_start_time", startTime)
                    .apply();
                
                // Parse options
                ArrayList<String> options = new ArrayList<>();
                if (optionsString != null && !optionsString.isEmpty()) {
                    String[] optionArray = optionsString.split(",");
                    for (String option : optionArray) {
                        options.add(option.trim());
                    }
                }
                
                // Execute USSD
                UssdController controller = new UssdController(this);
                controller.executeUssd(ussdCode, simSlot, options, this);
                
                Toast.makeText(this, "Executing: " + commandName, Toast.LENGTH_SHORT).show();
            }
        }
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
            
            // FCM-based responses are handled automatically by WorkManager
        });
    }
    
    @Override
    public void onUssdError(String error) {
        runOnUiThread(() -> {
            Toast.makeText(this, "USSD Error: " + error, Toast.LENGTH_SHORT).show();
        });
    }
}