package com.yourpackage.simpleussd.ussd;

public interface UssdCallback {
    void onUssdResponse(String response);
    void onUssdComplete(String finalResponse);
}

