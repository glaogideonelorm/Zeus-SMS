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
     * Enhanced cleanup method to close any open USSD dialogs before starting new commands
     * This prevents conflicts from dormant dialogs, including OK-only dialogs
     */
    public static void cleanupOpenDialogs() {
        Log.d(TAG, "Enhanced cleanup: Closing any open USSD dialogs for safety");
        
        // Clear any existing callback and options to prevent interference
        ussdCallback = null;
        pendingOptions = null;
        currentOptionIndex = 0;
        
        if (instance != null) {
            try {
                // Step 1: Try to find and click any OK/Close buttons first
                Log.d(TAG, "Step 1: Looking for OK/Close buttons to click");
                boolean buttonClicked = instance.findAndClickCloseButtons();
                
                if (buttonClicked) {
                    Log.d(TAG, "Successfully clicked close button");
                    Thread.sleep(300); // Allow dialog to close
                } else {
                    Log.d(TAG, "No close button found, using back action");
                }
                
                // Step 2: Use global back action as fallback
                Log.d(TAG, "Step 2: Sending global back action");
                instance.performGlobalAction(GLOBAL_ACTION_BACK);
                Thread.sleep(300);
                
                // Step 3: Send another back action for stubborn dialogs
                Log.d(TAG, "Step 3: Sending second back action for stubborn dialogs");
                instance.performGlobalAction(GLOBAL_ACTION_BACK);
                
                Log.d(TAG, "Enhanced cleanup completed");
                
            } catch (Exception e) {
                Log.e(TAG, "Error during enhanced dialog cleanup", e);
            }
        } else {
            Log.w(TAG, "Accessibility service instance not available for cleanup");
        }
    }
    
    /**
     * Find and click any close buttons in the current dialog
     * Returns true if a button was found and clicked
     */
    private boolean findAndClickCloseButtons() {
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) {
                Log.d(TAG, "No root node available for button search");
                return false;
            }
            
            // Look for buttons with various close-related texts
            String[] closeButtonTexts = {
                "OK", "Ok", "ok", "OKAY", "Okay", "okay",
                "Close", "close", "CLOSE", "Dismiss", "dismiss", "DISMISS",
                "Done", "done", "DONE", "End", "end", "END",
                "Cancel", "cancel", "CANCEL", "Send", "send", "SEND"
            };
            
            AccessibilityNodeInfo closeButton = findButtonByText(rootNode, closeButtonTexts);
            if (closeButton != null) {
                Log.d(TAG, "Found close button: " + closeButton.getText());
                boolean clicked = closeButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                closeButton.recycle();
                rootNode.recycle();
                Log.d(TAG, "Close button clicked: " + clicked);
                return clicked;
            } else {
                Log.d(TAG, "No close button found in dialog");
                rootNode.recycle();
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error finding close buttons", e);
            return false;
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
        CharSequence contentDescription = node.getContentDescription();
        
        // Check if this is a clickable button-like element
        boolean isButton = className.contains("Button") || 
                          className.contains("TextView") || 
                          node.isClickable();
        
        if (isButton && (text != null || contentDescription != null)) {
            String textStr = text != null ? text.toString().trim() : "";
            String descStr = contentDescription != null ? contentDescription.toString().trim() : "";
            
            // Check both text and content description
            for (String buttonText : buttonTexts) {
                String lowerButtonText = buttonText.toLowerCase();
                if ((!textStr.isEmpty() && textStr.toLowerCase().contains(lowerButtonText)) ||
                    (!descStr.isEmpty() && descStr.toLowerCase().contains(lowerButtonText))) {
                    Log.d(TAG, "Found button: '" + textStr + "' (desc: '" + descStr + "')");
                    return node;
                }
            }
        }

        // Recursively search children
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
            Log.d(TAG, "Enhanced dialog closing: Looking for close buttons");
            
            // Enhanced button text list for better coverage
            String[] closeButtonTexts = {
                "OK", "Ok", "ok", "OKAY", "Okay", "okay",
                "Send", "send", "SEND", "Submit", "submit", "SUBMIT",
                "Close", "close", "CLOSE", "Dismiss", "dismiss", "DISMISS",
                "Done", "done", "DONE", "End", "end", "END",
                "Cancel", "cancel", "CANCEL", "Exit", "exit", "EXIT"
            };
            
            AccessibilityNodeInfo closeButton = findButtonByText(rootNode, closeButtonTexts);
            
            if (closeButton != null) {
                String buttonText = closeButton.getText() != null ? closeButton.getText().toString() : "unknown";
                Log.d(TAG, "Found close button: '" + buttonText + "'");
                
                boolean clicked = closeButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                Log.d(TAG, "Close button clicked: " + clicked);
                closeButton.recycle();
                
                if (clicked) {
                    // Give time for dialog to close
                    Thread.sleep(200);
                }
            } else {
                Log.d(TAG, "No close button found, using global back action");
                // Fallback: use global back action
                performGlobalAction(GLOBAL_ACTION_BACK);
                Thread.sleep(200);
            }
            
            // Clear callback and options to prevent interference with next job
            ussdCallback = null;
            pendingOptions = null;
            currentOptionIndex = 0;
            
            Log.d(TAG, "Dialog closing completed, ready for next job");
            
        } catch (Exception e) {
            Log.e(TAG, "Error in enhanced dialog closing", e);
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }
}
