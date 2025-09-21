package com.example.smshook

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Entry Activity - Launcher activity that redirects to MainActivity
 * This serves as the main entry point for the app
 */
class EntryActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Redirect to MainActivity
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        
        // Finish this activity so it doesn't stay in the back stack
        finish()
    }
}