package com.yourpackage.simpleussd.ussd;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;

public class UssdAccessibilityService extends AccessibilityService {
    private static final String TAG = "UssdAccessibilityService";
    private static UssdCallback ussdCallback;
    private static ArrayList<String> pendingOptions;
    private static int currentOptionIndex = 0;

    public static void setUssdCallback(UssdCallback callback) {
        ussdCallback = callback;
        currentOptionIndex = 0;
    }

    public static void setPendingOptions(ArrayList<String> options) {
        pendingOptions = options;
        currentOptionIndex = 0;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Accessibility service connected");

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED |
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS |
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        // Listen only to the system phone app's USSD dialogs
        info.packageNames = new String[]{"com.android.phone"};
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (ussdCallback == null) return;
        if (event == null) return;

        AccessibilityNodeInfo source = event.getSource();
        if (source == null) return;

        int type = event.getEventType();
        CharSequence cls = event.getClassName();
        String className = cls != null ? cls.toString() : "";
        Log.d(TAG, "Event type=" + type + ", class=" + className);

        if (className.contains("AlertDialog") || className.contains("Dialog")) {
            handleUssdDialog(source);
        } else {
            // Some OEMs use custom dialog classes; try anyway if node tree has relevant text
            handleUssdDialog(source);
        }
    }

    private void handleUssdDialog(AccessibilityNodeInfo rootNode) {
        try {
            String dialogText = extractDialogText(rootNode);
            Log.d(TAG, "Dialog text: " + dialogText);

            if (ussdCallback != null && dialogText.length() > 0) {
                ussdCallback.onUssdResponse(dialogText);
            }

            AccessibilityNodeInfo inputNode = findInputNode(rootNode);
            Log.d(TAG, "HasInputNode=" + (inputNode != null) + ", pendingOptions=" + (pendingOptions != null ? pendingOptions.size() : 0) + ", index=" + currentOptionIndex);
            if (inputNode != null && pendingOptions != null && currentOptionIndex < pendingOptions.size()) {
                String option = pendingOptions.get(currentOptionIndex);
                Log.d(TAG, "Sending option: " + option);

                // Try to focus first (helps on some OEM dialogs)
                inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);

                Bundle arguments = new Bundle();
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, option);
                boolean set = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                Log.d(TAG, "Set text performed=" + set);

                AccessibilityNodeInfo okButton = findSubmitButton(rootNode);
                if (okButton != null) {
                    boolean clicked = okButton.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    Log.d(TAG, "Click submit performed=" + clicked + ", nodeId=" + okButton.getViewIdResourceName() + ", text=" + okButton.getText());
                } else {
                    Log.w(TAG, "Submit button not found in dialog tree");
                }

                currentOptionIndex++;
            } else {
                if (ussdCallback != null && isFinalResponse(dialogText)) {
                    // Save final response in cache and trigger callback
                    ussdCallback.onUssdComplete(dialogText);
                    
                    // Close dialog immediately while server communication happens in background
                    pressSendButtonToClose(rootNode);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling USSD dialog", e);
            if (ussdCallback != null) {
                ussdCallback.onUssdError(e.getMessage() != null ? e.getMessage() : "unknown");
            }
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

        CharSequence cls = node.getClassName();
        if (cls != null && cls.toString().contains("EditText")) {
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

    private boolean supportsSetText(AccessibilityNodeInfo node) {
        if (node == null) return false;
        for (AccessibilityNodeInfo.AccessibilityAction action : node.getActionList()) {
            if (action != null && action.getId() == AccessibilityNodeInfo.ACTION_SET_TEXT) return true;
        }
        return false;
    }

    private AccessibilityNodeInfo findNodeById(AccessibilityNodeInfo node, String... ids) {
        if (node == null) return null;
        CharSequence id = node.getViewIdResourceName();
        if (id != null) {
            String idStr = id.toString();
            for (String target : ids) {
                if (idStr.equals(target) || idStr.endsWith(":" + target)) {
                    return node;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findNodeById(child, ids);
                if (result != null) {
                    child.recycle();
                    return result;
                }
                child.recycle();
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findInputNode(AccessibilityNodeInfo node) {
        if (node == null) return null;

        // 1) Standard EditText
        AccessibilityNodeInfo std = findEditText(node);
        if (std != null) return std;

        // 2) Nodes supporting ACTION_SET_TEXT or editable
        if (supportsSetText(node) || node.isEditable()) return node;

        // 3) Common OEM ids for USSD input fields
        AccessibilityNodeInfo byId = findNodeById(node,
                "android:id/input",
                "com.android.phone:id/input_field",
                "com.android.phone:id/message",
                "com.samsung.android.telephonyui:id/input",
                "com.samsung.android.telephonyui:id/edittext");
        if (byId != null) return byId;

        // 4) Recurse
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findInputNode(child);
                if (result != null) {
                    child.recycle();
                    return result;
                }
                child.recycle();
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findSubmitButton(AccessibilityNodeInfo node) {
        if (node == null) return null;

        // 1) Match by text (various locales/cases)
        AccessibilityNodeInfo byText = findButtonByText(node,
                "OK", "Ok", "ok",
                "Send", "SEND", "send",
                "Continue", "CONTINUE", "continue",
                "Submit", "SUBMIT", "submit",
                "Proceed", "PROCEED", "proceed",
                "Reply", "REPLY", "reply");
        if (byText != null) return byText;

        // 2) Match by resource id
        AccessibilityNodeInfo byId = findButtonById(node, "android:id/button1", "com.android.phone:id/button1", "com.samsung.android.telephonyui:id/button1");
        if (byId != null) return byId;

        // 3) Fallback: first clickable Button
        AccessibilityNodeInfo clickable = findFirstClickableButton(node);
        if (clickable != null) return clickable;

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

    private AccessibilityNodeInfo findButtonById(AccessibilityNodeInfo node, String... ids) {
        if (node == null) return null;

        CharSequence id = node.getViewIdResourceName();
        if (id != null) {
            String idStr = id.toString();
            for (String target : ids) {
                if (idStr.equals(target) || idStr.endsWith(":" + target)) {
                    return node;
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findButtonById(child, ids);
                if (result != null) {
                    child.recycle();
                    return result;
                }
                child.recycle();
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findFirstClickableButton(AccessibilityNodeInfo node) {
        if (node == null) return null;
        String className = node.getClassName() != null ? node.getClassName().toString() : "";
        if (className.contains("Button") && node.isClickable()) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findFirstClickableButton(child);
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
        String lower = response.toLowerCase();
        if (lower.contains("thank you") ||
                lower.contains("transaction") ||
                lower.contains("successful") ||
                lower.contains("failed") ||
                lower.contains("error") ||
                lower.contains("invalid") ||
                lower.contains("expired")) {
            return true;
        }
        if (lower.contains("balance") || lower.contains("main ac")) {
            return lower.contains("ok") || lower.contains("till") || lower.contains("expiry");
        }
        boolean hasInputPrompts = lower.contains("enter") ||
                lower.contains("select") ||
                lower.contains("choose") ||
                lower.contains("press") ||
                lower.contains("dial");
        return !hasInputPrompts && response.length() > 20;
    }

    private void pressSendButtonToClose(AccessibilityNodeInfo rootNode) {
        try {
            Log.d(TAG, "Closing USSD dialog immediately (server communication in background)");
            
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
            
            Log.d(TAG, "USSD dialog closed immediately, ready for next job");
            
        } catch (Exception e) {
            Log.e(TAG, "Error pressing Send button", e);
        }
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }
}
