package com.span.sbicms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class HomeButtonReceiver : BroadcastReceiver() {

    companion object {
        private const val PREFS_NAME = "kiosk_prefs"
        private const val KEY_KIOSK_ACTIVE = "kiosk_active"
        private const val CHROME_PACKAGE_NAME = "com.android.chrome"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS) {
            val reason = intent.getStringExtra("reason")
            Log.d("KioskApp", "HomeButtonReceiver: reason = $reason")

            // Check if kiosk mode is active
            val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val kioskActive = sharedPrefs.getBoolean(KEY_KIOSK_ACTIVE, false)

            if (kioskActive && (reason == "homekey" || reason == "recentapps")) {
                Log.d("KioskApp", "Home/Recent button pressed in kiosk mode, redirecting to Chrome")

                // Redirect back to Chrome
                try {
                    val chromeIntent = context.packageManager.getLaunchIntentForPackage(CHROME_PACKAGE_NAME)
                    if (chromeIntent != null) {
                        chromeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        chromeIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        chromeIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        context.startActivity(chromeIntent)
                        Log.d("KioskApp", "Redirected to Chrome from home button")
                    } else {
                        // Fallback to MainActivity if Chrome not available
                        val mainIntent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        context.startActivity(mainIntent)
                        Log.d("KioskApp", "Redirected to MainActivity (Chrome not found)")
                    }
                } catch (e: Exception) {
                    Log.e("KioskApp", "Failed to redirect from home button", e)
                }
            }
        }
    }
}