package com.span.sbicms

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

class KioskDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.d("KioskDeviceAdmin", "=== DEVICE ADMIN ENABLED ===")
        Log.d("KioskDeviceAdmin", "Device Admin enabled successfully")
        Log.d("KioskDeviceAdmin", "Intent: $intent")
        Log.d("KioskDeviceAdmin", "Context: $context")

        Toast.makeText(context, "✓ Device Admin enabled for Kiosk Mode", Toast.LENGTH_LONG).show()

        // Verify the admin is actually active
        val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val component = ComponentName(context, KioskDeviceAdminReceiver::class.java)
        val isActive = devicePolicyManager.isAdminActive(component)
        Log.d("KioskDeviceAdmin", "Admin verification after enable: $isActive")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.d("KioskDeviceAdmin", "=== DEVICE ADMIN DISABLED ===")
        Log.d("KioskDeviceAdmin", "Device Admin disabled")
        Toast.makeText(context, "Device Admin disabled", Toast.LENGTH_SHORT).show()

        // Stop overlay service when admin is disabled
        try {
            val serviceIntent = Intent(context, OverlayService::class.java)
            context.stopService(serviceIntent)
            Log.d("KioskDeviceAdmin", "Overlay service stopped due to admin disable")
        } catch (e: Exception) {
            Log.e("KioskDeviceAdmin", "Error stopping overlay service", e)
        }
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.d("KioskDeviceAdmin", "=== DEVICE ADMIN DISABLE REQUESTED ===")
        Log.d("KioskDeviceAdmin", "Device Admin disable requested")
        return "Warning: Disabling device admin will exit kiosk mode and disable app pinning"
    }

    override fun onPasswordChanged(context: Context, intent: Intent, user: android.os.UserHandle) {
        super.onPasswordChanged(context, intent, user)
        Log.d("KioskDeviceAdmin", "Password changed for user: $user")
    }

    override fun onPasswordFailed(context: Context, intent: Intent, user: android.os.UserHandle) {
        super.onPasswordFailed(context, intent, user)
        Log.d("KioskDeviceAdmin", "Password failed for user: $user")
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        super.onLockTaskModeEntering(context, intent, pkg)
        Log.d("KioskDeviceAdmin", "=== LOCK TASK MODE ENTERING ===")
        Log.d("KioskDeviceAdmin", "Package entering lock task: $pkg")
        Toast.makeText(context, "App Pinning Activated", Toast.LENGTH_SHORT).show()
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        super.onLockTaskModeExiting(context, intent)
        Log.d("KioskDeviceAdmin", "=== LOCK TASK MODE EXITING ===")
        Log.d("KioskDeviceAdmin", "Exiting lock task mode")
        Toast.makeText(context, "App Pinning Deactivated", Toast.LENGTH_SHORT).show()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d("KioskDeviceAdmin", "=== DEVICE ADMIN RECEIVER ===")
        Log.d("KioskDeviceAdmin", "Received intent: ${intent.action}")
        Log.d("KioskDeviceAdmin", "Intent extras: ${intent.extras}")
    }
}