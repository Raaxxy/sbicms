package com.span.sbicms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val PREFS_NAME = "kiosk_prefs"
        private const val KEY_KIOSK_ACTIVE = "kiosk_active"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                android.util.Log.d("KioskApp", "Boot/replacement received")

                // Check if kiosk mode was active
                val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val kioskActive = sharedPrefs.getBoolean(KEY_KIOSK_ACTIVE, false)
                val overlayGranted = Settings.canDrawOverlays(context)

                android.util.Log.d("KioskApp", "Boot check - Kiosk active: $kioskActive, Overlay: $overlayGranted")

                if (kioskActive && overlayGranted) {
                    // Delay the start to ensure system is fully loaded
                    Handler(Looper.getMainLooper()).postDelayed({
                        startKioskMode(context)
                    }, 10000) // 10 second delay for system stability
                }
            }
        }
    }

    private fun startKioskMode(context: Context) {
        try {
            // Start overlay service
            val serviceIntent = Intent(context, OverlayService::class.java)
            context.startForegroundService(serviceIntent)

            // Start kiosk launcher
            val launcherIntent = Intent(context, KioskLauncherActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(launcherIntent)

            android.util.Log.d("KioskApp", "Kiosk mode started from boot")

        } catch (e: Exception) {
            android.util.Log.e("KioskApp", "Failed to start kiosk mode from boot", e)

            // Fallback: start main activity
            val fallbackIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(fallbackIntent)
        }
    }
}