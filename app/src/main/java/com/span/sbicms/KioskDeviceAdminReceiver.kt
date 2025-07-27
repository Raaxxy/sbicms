package com.span.sbicms

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class KioskDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("KioskApp", "Device Admin enabled successfully")
        Toast.makeText(context, "Device Admin enabled for Kiosk Mode", Toast.LENGTH_SHORT).show()
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("KioskApp", "Device Admin disabled")
        Toast.makeText(context, "Device Admin disabled", Toast.LENGTH_SHORT).show()

        // Stop overlay service when admin is disabled
        val serviceIntent = Intent(context, OverlayService::class.java)
        context.stopService(serviceIntent)
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.d("KioskApp", "Device Admin disable requested")
        return "Warning: Disabling device admin will exit kiosk mode"
    }

    override fun onPasswordChanged(context: Context, intent: Intent, user: android.os.UserHandle) {
        super.onPasswordChanged(context, intent, user)
        Log.d("KioskApp", "Password changed")
    }

    override fun onPasswordFailed(context: Context, intent: Intent, user: android.os.UserHandle) {
        super.onPasswordFailed(context, intent, user)
        Log.d("KioskApp", "Password failed")
    }
}