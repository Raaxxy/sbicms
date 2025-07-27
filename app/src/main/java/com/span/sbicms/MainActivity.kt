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

    companion object {
        private const val REQUEST_CODE_ENABLE_ADMIN = 1001
        private const val REQUEST_CODE_OVERLAY_PERMISSION = 1002
        private const val CHROME_PACKAGE_NAME = "com.android.chrome"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        android.util.Log.d("KioskApp", "=== MainActivity onCreate ===")

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        deviceAdminComponent = ComponentName(this, KioskDeviceAdminReceiver::class.java)

        // Always show the permission UI initially
        setContentView(R.layout.activity_main)
        setupFullscreen()

        // Check permissions and start flow
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            checkPermissionsAndStart()
        }, 1000)
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

    private fun checkPermissionsAndStart() {
        val overlayGranted = Settings.canDrawOverlays(this)
        val adminGranted = devicePolicyManager.isAdminActive(deviceAdminComponent)

        android.util.Log.d("KioskApp", "Permission check - Overlay: $overlayGranted, Admin: $adminGranted")

        updateStatus("Checking permissions...\n\nOverlay: ${if (overlayGranted) "✓" else "⏳"}\nDevice Admin: ${if (adminGranted) "✓" else "⏳"}")

        when {
            !overlayGranted -> {
                updateStatus("Step 1/2: Requesting Overlay Permission\n\nPlease allow 'Display over other apps'")
                requestOverlayPermission()
            }
            !adminGranted -> {
                updateStatus("Step 2/2: Requesting Device Admin\n\nPlease tap 'Activate this device admin app'")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    requestDeviceAdmin()
                }, 2000)
            }
            else -> {
                updateStatus("✓ All permissions granted!\n\nStarting Kiosk Mode...")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    startKioskMode()
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

    private fun requestDeviceAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for kiosk mode - prevents app exit")
        }
        startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        android.util.Log.d("KioskApp", "onActivityResult - request: $requestCode, result: $resultCode")

        when (requestCode) {
            REQUEST_CODE_OVERLAY_PERMISSION -> {
                if (Settings.canDrawOverlays(this)) {
                    updateStatus("✓ Overlay permission granted!\n\nChecking remaining permissions...")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        checkPermissionsAndStart()
                    }, 1000)
                } else {
                    updateStatus("❌ Overlay permission required\n\nApp cannot function without this permission")
                    Toast.makeText(this, "Overlay permission required", Toast.LENGTH_LONG).show()
                }
            }
            REQUEST_CODE_ENABLE_ADMIN -> {
                if (devicePolicyManager.isAdminActive(deviceAdminComponent)) {
                    updateStatus("✓ Device Admin granted!\n\nAll permissions ready!")
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        checkPermissionsAndStart()
                    }, 1000)
                } else {
                    updateStatus("❌ Device Admin required for kiosk\n\nStarting without admin privileges...")
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

        updateStatus("🚀 Starting Kiosk Mode...\n\nSetting up device admin...")

        try {
            // Set lock task packages
            devicePolicyManager.setLockTaskPackages(deviceAdminComponent, arrayOf(packageName))
            android.util.Log.d("KioskApp", "Lock task packages set")

            // Disable keyguard
            devicePolicyManager.setKeyguardDisabled(deviceAdminComponent, true)
            android.util.Log.d("KioskApp", "Keyguard disabled")

            updateStatus("🚀 Starting Kiosk Mode...\n\nStarting overlay...")

            // Start overlay service
            startOverlayService()

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                updateStatus("🚀 Starting Kiosk Mode...\n\nPinning app (may show dialog)...")

                // Start lock task
                try {
                    startLockTask()
                    android.util.Log.d("KioskApp", "Lock task started successfully")

                    // Auto-dismiss any dialog
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        dismissPinningDialog()
                    }, 2000)

                } catch (e: Exception) {
                    android.util.Log.e("KioskApp", "Lock task failed", e)
                }

                // Launch Chrome regardless of lock task result
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    updateStatus("🚀 Starting Kiosk Mode...\n\nLaunching Chrome...")
                    launchChrome()

                    // Update UI to show kiosk is active
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        updateStatus("✅ KIOSK MODE ACTIVE\n\nChrome Browser Running\nOverlay Active\nApp Pinned")

                        // Keep refreshing fullscreen mode
                        startFullscreenEnforcer()

                    }, 2000)
                }, 3000)

            }, 2000)

        } catch (e: Exception) {
            android.util.Log.e("KioskApp", "Error in kiosk setup", e)
            updateStatus("❌ Kiosk setup error\n\nStarting basic mode...")
            startKioskModeWithoutAdmin()
        }
    }

    private fun startKioskModeWithoutAdmin() {
        android.util.Log.d("KioskApp", "=== Starting LIMITED Kiosk Mode ===")
        isKioskActive = true

        updateStatus("🚀 Starting Limited Kiosk...\n\n(Double-tap back to exit)")

        // Start overlay service
        startOverlayService()

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            updateStatus("🚀 Starting Limited Kiosk...\n\nLaunching Chrome...")
            launchChrome()

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                updateStatus("✅ LIMITED KIOSK ACTIVE\n\nChrome Browser Running\nOverlay Active\nDouble-tap back to exit")

                // Keep refreshing fullscreen mode
                startFullscreenEnforcer()

            }, 2000)
        }, 2000)
    }

    private fun dismissPinningDialog() {
        try {
            android.util.Log.d("KioskApp", "Attempting to dismiss pinning dialog")

            // Send ENTER key
            val enterDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
            val enterUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
            dispatchKeyEvent(enterDown)
            dispatchKeyEvent(enterUp)

            // Also try DPAD_CENTER
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val centerDown = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)
                val centerUp = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)
                dispatchKeyEvent(centerDown)
                dispatchKeyEvent(centerUp)
            }, 500)

        } catch (e: Exception) {
            android.util.Log.e("KioskApp", "Error dismissing dialog", e)
        }
    }

    private fun startFullscreenEnforcer() {
        // Continuously enforce fullscreen mode
        val enforcer = object : Runnable {
            override fun run() {
                if (isKioskActive) {
                    setupFullscreen()
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this, 2000)
                }
            }
        }
        android.os.Handler(android.os.Looper.getMainLooper()).post(enforcer)
        android.util.Log.d("KioskApp", "Fullscreen enforcer started")
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

    private fun launchChrome() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(CHROME_PACKAGE_NAME)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(intent)
                android.util.Log.d("KioskApp", "Chrome launched successfully")
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
                    isKioskActive = false
                    stopService(Intent(this, OverlayService::class.java))
                    super.onBackPressed()
                } else {
                    lastBackPress = currentTime
                    Toast.makeText(this, "Press back again to exit kiosk mode", Toast.LENGTH_SHORT).show()
                }
                return
            }
        }
        super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("KioskApp", "MainActivity onResume - isKioskActive: $isKioskActive")
        setupFullscreen()

        // If kiosk is active, hide MainActivity and show Chrome
        if (isKioskActive) {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                launchChrome()
                moveTaskToBack(true)
            }, 1000)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && isKioskActive) {
            setupFullscreen()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("KioskApp", "MainActivity onDestroy")

        if (!isKioskActive) {
            // Only stop services if we're not in kiosk mode
            stopService(Intent(this, OverlayService::class.java))

            if (devicePolicyManager.isAdminActive(deviceAdminComponent)) {
                try {
                    stopLockTask()
                } catch (e: Exception) {
                    android.util.Log.e("KioskApp", "Error stopping lock task", e)
                }
            }
        }
    }
}