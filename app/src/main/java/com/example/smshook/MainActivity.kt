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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.example.smshook.sms.BatteryMonitorWorker

class MainActivity : AppCompatActivity() {

    private val REQUEST_SMS_PERMISSION = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupBottomNavigation()
        setupWorkManagerObserver()
        scheduleBatteryMonitor()
        checkAndRequestPermissions()
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

    private fun scheduleBatteryMonitor() {
        val work = PeriodicWorkRequestBuilder<BatteryMonitorWorker>(
            15, TimeUnit.MINUTES
        )
            .addTag("zeus-battery-monitor")
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "zeus-battery-monitor",
            ExistingPeriodicWorkPolicy.UPDATE,
            work
        )
    }

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
