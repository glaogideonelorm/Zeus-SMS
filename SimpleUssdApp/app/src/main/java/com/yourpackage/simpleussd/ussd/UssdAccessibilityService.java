package com.yourpackage.simpleussd.ussd;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;
import java.util.List;

public class UssdAccessibilityService extends AccessibilityService {
    private static final String TAG = "UssdAccessibilityService";
    private static UssdCallback ussdCallback;
    private static ArrayList<String> pendingOptions;
    private static int currentOptionIndex = 0;
    private static UssdAccessibilityService instance;

    public static void setUssdCallback(UssdCallback callback) {
        ussdCallback = callback;
        currentOptionIndex = 0;
    }

    public static void setPendingOptions(ArrayList<String> options) {
        pendingOptions = options;
        currentOptionIndex = 0;
    }

    /**
     * Cleanup method to close any open USSD dialogs before starting new commands
     * This prevents conflicts from dormant dialogs
     */
    public static void cleanupOpenDialogs() {
        Log.d(TAG, "Cleaning up any open USSD dialogs for safety");
        
        // Clear any existing callback and options to prevent interference
        ussdCallback = null;
        pendingOptions = null;
        currentOptionIndex = 0;
        
        // Try to close any open dialogs by sending back action
        if (instance != null) {
            try {
                // Use global back action to close any open dialogs
                instance.performGlobalAction(GLOBAL_ACTION_BACK);
                Log.d(TAG, "Sent global back action to close any open dialogs");
                
                // Small delay to allow dialog to close
                Thread.sleep(500);
                
                // Send another back action for stubborn dialogs
                instance.performGlobalAction(GLOBAL_ACTION_BACK);
                Log.d(TAG, "Sent second global back action for stubborn dialogs");
                
            } catch (Exception e) {
                Log.e(TAG, "Error during dialog cleanup", e);
            }
        } else {
            Log.w(TAG, "Accessibility service instance not available for cleanup");
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Accessibility service connected");
        
        // Set the instance for static access
        instance = this;
        
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED | 
                         AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.packageNames = new String[]{"com.android.phone"};
        
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (ussdCallback == null) return;

        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return;

        String className = event.getClassName() != null ? event.getClassName().toString() : "";
        
        // Check if this is a USSD dialog
        if (className.contains("AlertDialog") || className.contains("Dialog")) {
            handleUssdDialog(source);
        }
    }

    private void handleUssdDialog(AccessibilityNodeInfo rootNode) {
        try {
            // Get dialog text
            String dialogText = extractDialogText(rootNode);
            Log.d(TAG, "Dialog text: " + dialogText);
            
            if (ussdCallback != null) {
                ussdCallback.onUssdResponse(dialogText);
            }

            // Look for input field and send option if available
            AccessibilityNodeInfo editText = findEditText(rootNode);
            if (editText != null && pendingOptions != null && currentOptionIndex < pendingOptions.size()) {
                String option = pendingOptions.get(currentOptionIndex);
                Log.d(TAG, "Sending option: " + option);
                
                // Input the option
                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, option);
                editText.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                
                // Find and click OK/Send button
                AccessibilityNodeInfo okButton = findButtonByText(rootNode, "OK", "Send", "Continue");
                if (okButton != null) {
                    okButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                }
                
                currentOptionIndex++;
            } else {
                // No more options or no input field - this might be the final response
                if (ussdCallback != null && isFinalResponse(dialogText)) {
                    ussdCallback.onUssdComplete(dialogText);
                    
                    // Auto-press Send button to close the USSD dialog
                    pressSendButtonToClose(rootNode);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling USSD dialog", e);
        }
    }

    private String extractDialogText(AccessibilityNodeInfo node) {
        StringBuilder text = new StringBuilder();
        extractTextRecursive(node, text);
        return text.toString().trim();
    }

    private void extractTextRecursive(AccessibilityNodeInfo node, StringBuilder text) {
        if (node == null) return;

        CharSequence nodeText = node.getText();
        if (nodeText != null && nodeText.length() > 0) {
            if (text.length() > 0) text.append(" ");
            text.append(nodeText);
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                extractTextRecursive(child, text);
                child.recycle();
            }
        }
    }

    private AccessibilityNodeInfo findEditText(AccessibilityNodeInfo node) {
        if (node == null) return null;

        if (node.getClassName() != null && node.getClassName().toString().contains("EditText")) {
            return node;
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findEditText(child);
                if (result != null) {
                    child.recycle();
                    return result;
                }
                child.recycle();
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findButtonByText(AccessibilityNodeInfo node, String... buttonTexts) {
        if (node == null) return null;

        String className = node.getClassName() != null ? node.getClassName().toString() : "";
        CharSequence text = node.getText();
        
        if (className.contains("Button") && text != null) {
            String textStr = text.toString().toLowerCase();
            for (String buttonText : buttonTexts) {
                if (textStr.contains(buttonText.toLowerCase())) {
                    return node;
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findButtonByText(child, buttonTexts);
                if (result != null) {
                    child.recycle();
                    return result;
                }
                child.recycle();
            }
        }
        return null;
    }

    private boolean isFinalResponse(String response) {
        // Simple heuristic to detect final USSD responses
        String lower = response.toLowerCase();
        return lower.contains("thank you") || 
               lower.contains("transaction") ||
               lower.contains("balance") ||
               lower.contains("successful") ||
               lower.contains("failed") ||
               !lower.contains("enter") && !lower.contains("select") && !lower.contains("choose");
    }

    private void pressSendButtonToClose(AccessibilityNodeInfo rootNode) {
        try {
            Log.d(TAG, "Attempting to press Send button to close USSD dialog");
            
            // Look for Send button with various possible texts
            AccessibilityNodeInfo sendButton = findButtonByText(rootNode, 
                "Send", "OK", "Done", "Close", "Dismiss", "Cancel", "End");
            
            if (sendButton != null) {
                boolean clicked = sendButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d(TAG, "Send button clicked: " + clicked);
                sendButton.recycle();
            } else {
                Log.d(TAG, "No Send button found, trying global back action");
                // Fallback: use global back action
                performGlobalAction(GLOBAL_ACTION_BACK);
            }
            
            // Clear callback and options to prevent interference with next job
            ussdCallback = null;
            pendingOptions = null;
            currentOptionIndex = 0;
            
        } catch (Exception e) {
            Log.e(TAG, "Error pressing Send button", e);
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }
}
