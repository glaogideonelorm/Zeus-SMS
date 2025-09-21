package com.example.ussdcore;

public interface UssdCallback {
    void onUssdResponse(String response);
    void onUssdComplete(String finalResponse);
    void onUssdError(String error);
}


