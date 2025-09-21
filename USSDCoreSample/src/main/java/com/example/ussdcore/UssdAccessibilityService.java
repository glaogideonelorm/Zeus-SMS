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

            AccessibilityNodeInfo edit = findEditText(root);
            if (edit != null && pendingOptions != null && currentOptionIndex < pendingOptions.size()) {
                String option = pendingOptions.get(currentOptionIndex);
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, option);
                edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);

                AccessibilityNodeInfo ok = findButtonByText(root, "OK", "Send", "Continue");
                if (ok != null) ok.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                currentOptionIndex++;
            } else {
                if (ussdCallback != null && isFinalResponse(text)) {
                    ussdCallback.onUssdComplete(text);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling USSD dialog", e);
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

    private boolean isFinalResponse(String response) {
        String lower = response.toLowerCase();
        return lower.contains("thank you") || lower.contains("transaction") || lower.contains("balance") ||
                lower.contains("successful") || lower.contains("failed") ||
                (!lower.contains("enter") && !lower.contains("select") && !lower.contains("choose"));
    }

    @Override public void onInterrupt() { }
}


