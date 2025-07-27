package com.span.sbicms

import android.app.Activity
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var deviceAdminComponent: ComponentName
    private var isKioskActive = false
    private var immersiveEnforcer: Runnable? = null
    private var deviceAdminChecksDone = false

    companion object {
        private const val REQUEST_CODE_ENABLE_ADMIN = 1001
        private const val REQUEST_CODE_OVERLAY_PERMISSION = 1002
        private const val CHROME_PACKAGE_NAME = "com.android.chrome"
        private const val PREFS_NAME = "kiosk_prefs"
        private const val KEY_KIOSK_ACTIVE = "kiosk_active"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        android.util.Log.d("KioskApp", "=== MainActivity onCreate ===")

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        deviceAdminComponent = ComponentName(this, KioskDeviceAdminReceiver::class.java)

        android.util.Log.d("KioskApp", "Device Policy Manager: $devicePolicyManager")
        android.util.Log.d("KioskApp", "Device Admin Component: $deviceAdminComponent")

        // Extensive device admin debugging
        debugDeviceAdminSetup()

        // Always show the permission UI initially
        setContentView(R.layout.activity_main)
        setupFullscreen()

        // Setup manual test buttons
        setupTestButtons()

        // Check permissions and start flow
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            checkPermissionsAndStart()
        }, 1000)
    }

    private fun debugDeviceAdminSetup() {
        android.util.Log.d("KioskApp", "=== DEVICE ADMIN DEBUG ===")

        try {
            // Check if receiver is registered
            val deviceAdminInfo = packageManager.getReceiverInfo(deviceAdminComponent, PackageManager.GET_META_DATA)
            android.util.Log.d("KioskApp", "✓ Device admin receiver found: ${deviceAdminInfo.name}")
            android.util.Log.d("KioskApp", "✓ Receiver enabled: ${deviceAdminInfo.isEnabled}")
            android.util.Log.d("KioskApp", "✓ Receiver exported: ${deviceAdminInfo.exported}")
        } catch (e: Exception) {
            android.util.Log.e("KioskApp", "❌ Device admin receiver NOT found", e)
        }

        // Check current admin status
        val isCurrentlyAdmin = devicePolicyManager.isAdminActive(deviceAdminComponent)
        android.util.Log.d("KioskApp", "Current admin status: $isCurrentlyAdmin")

        // List all active admins
        val activeAdmins = devicePolicyManager.activeAdmins
        android.util.Log.d("KioskApp", "All active admins: ${activeAdmins?.size ?: 0}")
        activeAdmins?.forEach { admin ->
            android.util.Log.d("KioskApp", "  - Active admin: $admin")
        }

        // Check if our admin is in the list
        val ourAdminInList = activeAdmins?.contains(deviceAdminComponent) ?: false
        android.util.Log.d("KioskApp", "Our admin in active list: $ourAdminInList")

        // Test creating the intent
        try {
            val testIntent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for kiosk mode")
            }
            android.util.Log.d("KioskApp", "✓ Device admin intent created successfully")

            // Check if there's an activity to handle this intent
            val resolveInfo = packageManager.resolveActivity(testIntent, PackageManager.MATCH_DEFAULT_ONLY)
            if (resolveInfo != null) {
                android.util.Log.d("KioskApp", "✓ Device admin intent can be handled by: ${resolveInfo.activityInfo.name}")
            } else {
                android.util.Log.e("KioskApp", "❌ No activity found to handle device admin intent")
            }
        } catch (e: Exception) {
            android.util.Log.e("KioskApp", "❌ Error creating device admin intent", e)
        }
    }

    private fun setupFullscreen() {
        // Force fullscreen immediately
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        android.util.Log.d("KioskApp", "Fullscreen setup complete")
    }

    private fun setupTestButtons() {
        findViewById<android.widget.Button>(R.id.btn_test_admin)?.setOnClickListener {
            android.util.Log.d("KioskApp", "=== MANUAL DEVICE ADMIN TEST CLICKED ===")
            updateStatus("Manual Test: Requesting Device Admin...\n\nForcing device admin request")

            // Force device admin request regardless of current state
            forceRequestDeviceAdmin()
        }

        findViewById<android.widget.Button>(R.id.btn_force_limited)?.setOnClickListener {
            android.util.Log.d("KioskApp", "=== MANUAL LIMITED MODE CLICKED ===")
            updateStatus("Manual Test: Starting Limited Kiosk...\n\nSkipping device admin")
            startKioskModeWithoutAdmin()
        }
    }

    private fun checkPermissionsAndStart() {
        val overlayGranted = Settings.canDrawOverlays(this)
        val adminGranted = devicePolicyManager.isAdminActive(deviceAdminComponent)

        android.util.Log.d("KioskApp", "=== PERMISSION CHECK ===")
        android.util.Log.d("KioskApp", "Overlay granted: $overlayGranted")
        android.util.Log.d("KioskApp", "Admin granted: $adminGranted")
        android.util.Log.d("KioskApp", "Device admin checks done: $deviceAdminChecksDone")

        updateStatus("Checking permissions...\n\nOverlay: ${if (overlayGranted) "✓" else "⏳"}\nDevice Admin: ${if (adminGranted) "✓" else "⏳"}")

        when {
            !overlayGranted -> {
                android.util.Log.d("KioskApp", ">>> Requesting overlay permission")
                updateStatus("Step 1/2: Requesting Overlay Permission\n\nPlease allow 'Display over other apps'")
                requestOverlayPermission()
            }
            !adminGranted && !deviceAdminChecksDone -> {
                android.util.Log.d("KioskApp", ">>> Requesting device admin permission")
                updateStatus("Step 2/2: Requesting Device Admin\n\nPlease tap 'Activate this device admin app'")
                deviceAdminChecksDone = true // Prevent infinite loop
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    forceRequestDeviceAdmin()
                }, 2000)
            }
            adminGranted -> {
                android.util.Log.d("KioskApp", ">>> All permissions granted, starting full kiosk")
                updateStatus("✓ All permissions granted!\n\nStarting Full Kiosk Mode...")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    startKioskMode()
                }, 2000)
            }
            else -> {
                android.util.Log.d("KioskApp", ">>> Device admin not granted, starting limited mode")
                updateStatus("Device Admin not available\n\nStarting Limited Kiosk Mode...")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    startKioskModeWithoutAdmin()
                }, 2000)
            }
        }
    }

    private fun updateStatus(message: String) {
        findViewById<TextView>(R.id.status_text)?.text = message
        android.util.Log.d("KioskApp", "Status: $message")
    }

    private fun requestOverlayPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivityForResult(intent, REQUEST_CODE_OVERLAY_PERMISSION)
    }

    private fun forceRequestDeviceAdmin() {
        android.util.Log.d("KioskApp", "=== FORCE REQUESTING DEVICE ADMIN ===")

        // Re-run debug before attempting
        debugDeviceAdminSetup()

        try {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Required for kiosk mode:\n• Prevents app exit\n• Enables app pinning\n• Disables system navigation")
            }

            android.util.Log.d("KioskApp", "Starting device admin activity with intent: $intent")

            // Get the component with proper type specification
            val componentInIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN)
            }
            android.util.Log.d("KioskApp", "Device admin component in intent: $componentInIntent")

            startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN)
            android.util.Log.d("KioskApp", "Device admin activity started successfully")

        } catch (e: Exception) {
            android.util.Log.e("KioskApp", "CRITICAL ERROR requesting device admin", e)
            Toast.makeText(this, "Error requesting device admin: ${e.message}", Toast.LENGTH_LONG).show()

            updateStatus("❌ Device Admin Error\n\n${e.message}\n\nStarting limited mode...")

            // Fallback to limited mode
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startKioskModeWithoutAdmin()
            }, 3000)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        android.util.Log.d("KioskApp", "=== ACTIVITY RESULT ===")
        android.util.Log.d("KioskApp", "Request code: $requestCode")
        android.util.Log.d("KioskApp", "Result code: $resultCode")
        android.util.Log.d("KioskApp", "Data: $data")

        when (requestCode) {
            REQUEST_CODE_OVERLAY_PERMISSION -> {
                val overlayGranted = Settings.canDrawOverlays(this)
                android.util.Log.d("KioskApp", "Overlay permission result: $overlayGranted")

                if (overlayGranted) {
                    updateStatus("✓ Overlay permission granted!\n\nChecking remaining permissions...")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        checkPermissionsAndStart()
                    }, 1000)
                } else {
                    updateStatus("❌ Overlay permission required\n\nApp cannot function without this permission")
                    Toast.makeText(this, "Overlay permission required", Toast.LENGTH_LONG).show()
                    // Give user another chance
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        checkPermissionsAndStart()
                    }, 3000)
                }
            }
            REQUEST_CODE_ENABLE_ADMIN -> {
                android.util.Log.d("KioskApp", "=== DEVICE ADMIN RESULT ===")

                // Re-check admin status after the result
                val adminGranted = devicePolicyManager.isAdminActive(deviceAdminComponent)
                android.util.Log.d("KioskApp", "Device admin result - isActive: $adminGranted")
                android.util.Log.d("KioskApp", "Result code meaning: ${if (resultCode == Activity.RESULT_OK) "OK" else if (resultCode == Activity.RESULT_CANCELED) "CANCELED" else "OTHER($resultCode)"}")

                if (adminGranted) {
                    android.util.Log.d("KioskApp", "✓ Device Admin successfully granted!")
                    updateStatus("✓ Device Admin granted!\n\nAll permissions ready!\n\nStarting Full Kiosk Mode...")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        startKioskMode()
                    }, 2000)
                } else {
                    android.util.Log.d("KioskApp", "❌ Device admin was not granted")
                    if (resultCode == Activity.RESULT_CANCELED) {
                        updateStatus("❌ Device Admin was denied\n\nStarting Limited Kiosk Mode...")
                    } else {
                        updateStatus("❌ Device Admin setup failed\n\nStarting Limited Kiosk Mode...")
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        startKioskModeWithoutAdmin()
                    }, 2000)
                }
            }
        }
    }

    private fun startKioskMode() {
        android.util.Log.d("KioskApp", "=== Starting FULL Kiosk Mode ===")
        isKioskActive = true

        // Save kiosk state
        val sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean(KEY_KIOSK_ACTIVE, true).apply()

        updateStatus("🚀 Starting Full Kiosk Mode...\n\nSetting up device admin features...")

        try {
            // Set lock task packages - INCLUDE CHROME!
            val packages = arrayOf(packageName, CHROME_PACKAGE_NAME)
            devicePolicyManager.setLockTaskPackages(deviceAdminComponent, packages)
            android.util.Log.d("KioskApp", "✓ Lock task packages set: ${packages.joinToString()}")

            // Disable keyguard
            devicePolicyManager.setKeyguardDisabled(deviceAdminComponent, true)
            android.util.Log.d("KioskApp", "✓ Keyguard disabled")

            updateStatus("🚀 Starting Full Kiosk Mode...\n\nStarting overlay...")

            // Start overlay service
            startOverlayService()

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                updateStatus("🚀 Starting Full Kiosk Mode...\n\nPinning apps...")

                // Start lock task FIRST
                try {
                    startLockTask()
                    android.util.Log.d("KioskApp", "✓ Lock task started successfully")

                    // Start aggressive immersive mode enforcement
                    startAggressiveImmersiveMode()

                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        updateStatus("🚀 Starting Full Kiosk Mode...\n\nLaunching Chrome...")
                        launchChromeInKioskMode()

                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            updateStatus("✅ FULL KIOSK MODE ACTIVE\n\nChrome Browser Running\nOverlay Active\nApps Pinned\nAdmin Enabled")
                        }, 3000)
                    }, 2000)

                } catch (e: Exception) {
                    android.util.Log.e("KioskApp", "❌ Lock task failed", e)
                    // Continue without lock task
                    startAggressiveImmersiveMode()
                    launchChromeInKioskMode()
                }

            }, 2000)

        } catch (e: Exception) {
            android.util.Log.e("KioskApp", "Error in full kiosk setup", e)
            updateStatus("❌ Full kiosk setup error\n\nStarting limited mode...")
            startKioskModeWithoutAdmin()
        }
    }

    private fun startKioskModeWithoutAdmin() {
        android.util.Log.d("KioskApp", "=== Starting LIMITED Kiosk Mode ===")
        isKioskActive = true

        // Save kiosk state
        val sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean(KEY_KIOSK_ACTIVE, true).apply()

        updateStatus("🚀 Starting Limited Kiosk...\n\n(Double-tap back to exit)")

        // Start overlay service
        startOverlayService()

        // Start aggressive immersive mode
        startAggressiveImmersiveMode()

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            updateStatus("🚀 Starting Limited Kiosk...\n\nLaunching Chrome...")
            launchChromeInKioskMode()

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                updateStatus("✅ LIMITED KIOSK ACTIVE\n\nChrome Browser Running\nOverlay Active\nDouble-tap back to exit")
            }, 2000)
        }, 2000)
    }

    private fun startAggressiveImmersiveMode() {
        // Stop any existing enforcer
        immersiveEnforcer?.let {
            android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(it)
        }

        // Create new aggressive enforcer
        immersiveEnforcer = object : Runnable {
            override fun run() {
                if (isKioskActive) {
                    try {
                        // Force immersive mode continuously
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            window.insetsController?.hide(
                                android.view.WindowInsets.Type.statusBars() or
                                        android.view.WindowInsets.Type.navigationBars()
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            window.decorView.systemUiVisibility = (
                                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    )
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("KioskApp", "Error in immersive enforcement", e)
                    }

                    // Schedule next enforcement
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 1000)
                }
            }
        }

        // Start the enforcer
        android.os.Handler(android.os.Looper.getMainLooper()).post(immersiveEnforcer!!)
        android.util.Log.d("KioskApp", "Aggressive immersive mode enforcer started")
    }

    private fun startOverlayService() {
        val serviceIntent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        android.util.Log.d("KioskApp", "Overlay service started")
    }

    private fun launchChromeInKioskMode() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(CHROME_PACKAGE_NAME)
            if (intent != null) {
                // Clear any existing Chrome tasks and start fresh
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

                startActivity(intent)
                android.util.Log.d("KioskApp", "Chrome launched in kiosk mode")

                // Don't move to back immediately - let Chrome settle first
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    // Keep this activity alive but in background
                    moveTaskToBack(false)
                }, 5000)

            } else {
                android.util.Log.e("KioskApp", "Chrome not found")
                updateStatus("❌ Chrome browser not found\n\nPlease install Chrome")
            }
        } catch (e: Exception) {
            android.util.Log.e("KioskApp", "Failed to launch Chrome", e)
            updateStatus("❌ Failed to launch Chrome\n\nError: ${e.message}")
        }
    }

    private var lastBackPress: Long = 0

    override fun onBackPressed() {
        if (isKioskActive) {
            if (devicePolicyManager.isAdminActive(deviceAdminComponent)) {
                // Full kiosk - completely disable back button
                android.util.Log.d("KioskApp", "Back button blocked - full kiosk mode")
                Toast.makeText(this, "Kiosk mode active - back button disabled", Toast.LENGTH_SHORT).show()
                return
            } else {
                // Limited kiosk - require double tap
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPress < 2000) {
                    android.util.Log.d("KioskApp", "Double back press - exiting kiosk")
                    exitKioskMode()
                } else {
                    lastBackPress = currentTime
                    Toast.makeText(this, "Press back again to exit kiosk mode", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
        super.onBackPressed()
    }

    private fun exitKioskMode() {
        isKioskActive = false

        // Clear kiosk state
        val sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean(KEY_KIOSK_ACTIVE, false).apply()

        // Stop enforcer
        immersiveEnforcer?.let {
            android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(it)
        }

        // Stop services
        stopService(Intent(this, OverlayService::class.java))

        // Stop lock task if active
        if (devicePolicyManager.isAdminActive(deviceAdminComponent)) {
            try {
                stopLockTask()
            } catch (e: Exception) {
                android.util.Log.e("KioskApp", "Error stopping lock task", e)
            }
        }

        finish()
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("KioskApp", "=== MainActivity onResume ===")
        android.util.Log.d("KioskApp", "isKioskActive: $isKioskActive")

        // Always enforce fullscreen when resuming
        setupFullscreen()

        // Debug current permissions state
        val overlayGranted = Settings.canDrawOverlays(this)
        val adminGranted = devicePolicyManager.isAdminActive(deviceAdminComponent)
        android.util.Log.d("KioskApp", "onResume - Overlay: $overlayGranted, Admin: $adminGranted")

        if (isKioskActive) {
            android.util.Log.d("KioskApp", "Kiosk is active, restarting immersive enforcement")
            // Restart immersive enforcement
            if (immersiveEnforcer == null) {
                startAggressiveImmersiveMode()
            }
        } else {
            android.util.Log.d("KioskApp", "Kiosk not active, normal resume behavior")
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        android.util.Log.d("KioskApp", "MainActivity window focus changed: $hasFocus")

        if (hasFocus && isKioskActive) {
            setupFullscreen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("KioskApp", "MainActivity onDestroy")

        // Stop enforcer
        immersiveEnforcer?.let {
            android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(it)
        }
    }
}