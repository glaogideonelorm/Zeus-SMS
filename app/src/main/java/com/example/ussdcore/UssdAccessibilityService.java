package com.example.ussdcore;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayList;

public class UssdAccessibilityService extends AccessibilityService {
    private static final String TAG = "UssdAccessibility";
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
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
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

        CharSequence cls = event.getClassName();
        String className = cls != null ? cls.toString() : "";
        if (className.contains("AlertDialog") || className.contains("Dialog")) {
            handleUssdDialog(source);
        }
    }

    private void handleUssdDialog(AccessibilityNodeInfo root) {
        try {
            String text = extractDialogText(root);
            if (ussdCallback != null) ussdCallback.onUssdResponse(text);

            AccessibilityNodeInfo inputNode = findInputNode(root);
            if (inputNode != null && pendingOptions != null && currentOptionIndex < pendingOptions.size()) {
                String option = pendingOptions.get(currentOptionIndex);
                // Focus first for OEMs that require it
                inputNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, option);
                boolean set = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);

                AccessibilityNodeInfo ok = findSubmitButton(root);
                if (ok != null) ok.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                currentOptionIndex++;
            } else {
                if (ussdCallback != null && isFinalResponse(text)) {
                    ussdCallback.onUssdComplete(text);
                    
                    // Auto-press Send button to close the USSD dialog
                    pressSendButtonToClose(root);
                }
            }
        } catch (Exception e) {
            if (ussdCallback != null) ussdCallback.onUssdError(e.getMessage() != null ? e.getMessage() : "unknown");
        }
    }

    private String extractDialogText(AccessibilityNodeInfo node) {
        StringBuilder sb = new StringBuilder();
        extractTextRecursive(node, sb);
        return sb.toString().trim();
    }

    private void extractTextRecursive(AccessibilityNodeInfo node, StringBuilder out) {
        if (node == null) return;
        CharSequence t = node.getText();
        if (t != null && t.length() > 0) {
            if (out.length() > 0) out.append(' ');
            out.append(t);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                extractTextRecursive(child, out);
                child.recycle();
            }
        }
    }

    private AccessibilityNodeInfo findEditText(AccessibilityNodeInfo node) {
        if (node == null) return null;
        CharSequence cls = node.getClassName();
        if (cls != null && cls.toString().contains("EditText")) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo res = findEditText(child);
                if (res != null) { child.recycle(); return res; }
                child.recycle();
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findButtonByText(AccessibilityNodeInfo node, String... texts) {
        if (node == null) return null;
        CharSequence cls = node.getClassName();
        CharSequence txt = node.getText();
        if (cls != null && cls.toString().contains("Button") && txt != null) {
            String t = txt.toString().toLowerCase();
            for (String s : texts) if (t.contains(s.toLowerCase())) return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo res = findButtonByText(child, texts);
                if (res != null) { child.recycle(); return res; }
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
                if (idStr.equals(target) || idStr.endsWith(":" + target)) return node;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo res = findNodeById(child, ids);
                if (res != null) { child.recycle(); return res; }
                child.recycle();
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findFirstClickableButton(AccessibilityNodeInfo node) {
        if (node == null) return null;
        CharSequence cls = node.getClassName();
        if (cls != null && cls.toString().contains("Button") && node.isClickable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo res = findFirstClickableButton(child);
                if (res != null) { child.recycle(); return res; }
                child.recycle();
            }
        }
        return null;
    }

    private AccessibilityNodeInfo findSubmitButton(AccessibilityNodeInfo node) {
        if (node == null) return null;
        AccessibilityNodeInfo byText = findButtonByText(node,
                "OK", "Ok", "ok",
                "Send", "SEND", "send",
                "Continue", "CONTINUE", "continue",
                "Submit", "SUBMIT", "submit",
                "Proceed", "PROCEED", "proceed",
                "Reply", "REPLY", "reply");
        if (byText != null) return byText;
        AccessibilityNodeInfo byId = findNodeById(node,
                "android:id/button1",
                "com.android.phone:id/button1",
                "com.samsung.android.telephonyui:id/button1");
        if (byId != null) return byId;
        return findFirstClickableButton(node);
    }

    private AccessibilityNodeInfo findInputNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        AccessibilityNodeInfo std = findEditText(node);
        if (std != null) return std;
        if (supportsSetText(node) || node.isEditable()) return node;
        AccessibilityNodeInfo byId = findNodeById(node,
                "android:id/input",
                "com.android.phone:id/input_field",
                "com.android.phone:id/message",
                "com.samsung.android.telephonyui:id/input",
                "com.samsung.android.telephonyui:id/edittext");
        if (byId != null) return byId;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo res = findInputNode(child);
                if (res != null) { child.recycle(); return res; }
                child.recycle();
            }
        }
        return null;
    }

    private boolean isFinalResponse(String response) {
        String lower = response.toLowerCase();
        return lower.contains("thank you") || lower.contains("transaction") || lower.contains("balance") ||
                lower.contains("successful") || lower.contains("failed") ||
                (!lower.contains("enter") && !lower.contains("select") && !lower.contains("choose"));
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

    @Override public void onInterrupt() { }
}


