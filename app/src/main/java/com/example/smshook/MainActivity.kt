package com.example.smshook

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.smshook.data.SmsLogManager
import com.example.smshook.utils.WorkManagerObserver
import android.widget.ImageView
import coil.load
import androidx.work.WorkManager

class MainActivity : AppCompatActivity() {

    private val REQUEST_SMS_PERMISSION = 1001
    private val LOGO_URL = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Use SMS UI with bottom navigation (Home + History/Logs)
        setContentView(R.layout.activity_main)

        setupBottomNavigation()
        setupWorkManagerObserver()
        checkAndRequestPermissions()
        loadLogo()
    }

    private fun setupBottomNavigation() {
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val bottomNavigationView = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        
        bottomNavigationView.setupWithNavController(navController)
    }

    private fun setupWorkManagerObserver() {
        val smsLogManager = SmsLogManager.getInstance(this)
        val workManagerObserver = WorkManagerObserver(this, smsLogManager)
        workManagerObserver.observeWorkCompletion(this)
    }

    private fun loadLogo() {
        val imageLogo: ImageView? = findViewById(R.id.imageLogo)
        // Prefer local drawable if available; fall back to URL if provided
        imageLogo?.setImageResource(R.drawable.zeus_logo)
    }

    // Battery monitor scheduling removed per request

    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                REQUEST_SMS_PERMISSION
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == REQUEST_SMS_PERMISSION) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Toast.makeText(this, "Zeus SMS permissions granted!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Zeus SMS requires these permissions to function", Toast.LENGTH_LONG).show()
            }
        }
    }
}
