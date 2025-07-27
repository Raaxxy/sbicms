package com.span.sbicms

import android.annotation.SuppressLint
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

/**
 * Invisible launcher activity that maintains kiosk mode.
 * This activity stays running in the background to maintain immersive mode
 * and handle system UI enforcement.
 */
class KioskLauncherActivity : AppCompatActivity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var deviceAdminComponent: ComponentName
    private lateinit var sharedPrefs: SharedPreferences
    private var immersiveReceiver: BroadcastReceiver? = null

    companion object {
        private const val PREFS_NAME = "kiosk_prefs"
        private const val KEY_KIOSK_ACTIVE = "kiosk_active"
        private const val CHROME_PACKAGE_NAME = "com.android.chrome"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        android.util.Log.d("KioskApp", "KioskLauncherActivity onCreate")

        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        deviceAdminComponent = ComponentName(this, KioskDeviceAdminReceiver::class.java)
        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Make this activity invisible
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        )

        setupImmersiveMode()
        setupImmersiveReceiver()

        // Check if we should be in kiosk mode
        if (isKioskModeActive()) {
            android.util.Log.d("KioskApp", "Kiosk mode active, enforcing immersive mode")
            enforceKioskMode()
        } else {
            android.util.Log.d("KioskApp", "Kiosk mode not active, finishing launcher")
            finish()
        }
    }

    private fun isKioskModeActive(): Boolean {
        val overlayGranted = Settings.canDrawOverlays(this)
        val adminGranted = devicePolicyManager.isAdminActive(deviceAdminComponent)
        val kioskFlagSet = sharedPrefs.getBoolean(KEY_KIOSK_ACTIVE, false)

        android.util.Log.d("KioskApp", "Launcher kiosk status - Overlay: $overlayGranted, Admin: $adminGranted, Flag: $kioskFlagSet")

        return overlayGranted && kioskFlagSet
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun setupImmersiveReceiver() {
        immersiveReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "com.span.sbicms.ENFORCE_IMMERSIVE") {
                    android.util.Log.d("KioskApp", "Received immersive mode enforcement request")
                    setupImmersiveMode()
                }
            }
        }

        val filter = IntentFilter("com.span.sbicms.ENFORCE_IMMERSIVE")
        registerReceiver(immersiveReceiver, filter)
    }

    private fun setupImmersiveMode() {
        try {
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
            )

            android.util.Log.d("KioskApp", "Immersive mode applied")
        } catch (e: Exception) {
            android.util.Log.e("KioskApp", "Error setting up immersive mode", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun enforceKioskMode() {
        // Ensure overlay service is running
        if (!isServiceRunning(OverlayService::class.java)) {
            android.util.Log.d("KioskApp", "Starting overlay service from launcher")
            val serviceIntent = Intent(this, OverlayService::class.java)
            startForegroundService(serviceIntent)
        }

        // Launch Chrome if not running
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            launchChrome()
        }, 2000)
    }

    private fun launchChrome() {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(CHROME_PACKAGE_NAME)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(launchIntent)
                android.util.Log.d("KioskApp", "Chrome launched from launcher")
            }
        } catch (e: Exception) {
            android.util.Log.e("KioskApp", "Failed to launch Chrome from launcher", e)
        }
    }

    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    override fun onResume() {
        super.onResume()
        setupImmersiveMode()

        if (isKioskModeActive()) {
            // Hide this activity and ensure Chrome is running
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                launchChrome()
                moveTaskToBack(true)
            }, 500)
        }
    }

    override fun onBackPressed() {
        // Disable back button completely in kiosk mode
        if (isKioskModeActive()) {
            android.util.Log.d("KioskApp", "Back button blocked in kiosk launcher")
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        immersiveReceiver?.let {
            unregisterReceiver(it)
        }
        android.util.Log.d("KioskApp", "KioskLauncherActivity destroyed")
    }
}