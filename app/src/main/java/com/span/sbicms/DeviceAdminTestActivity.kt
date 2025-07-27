package com.span.sbicms

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Simple isolated test activity for device admin functionality
 * This version avoids any compilation issues with newer Android APIs
 */
class DeviceAdminTestActivity : Activity() {

    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var deviceAdminComponent: ComponentName
    private lateinit var statusText: TextView

    companion object {
        private const val REQUEST_CODE_ENABLE_ADMIN = 2001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create simple layout programmatically
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
        }

        // Title
        val titleText = TextView(this).apply {
            text = "Device Admin Test"
            textSize = 24f
            setPadding(0, 0, 0, 30)
        }
        layout.addView(titleText)

        // Status text
        statusText = TextView(this).apply {
            text = "Click button to test device admin request"
            textSize = 16f
            setPadding(0, 0, 0, 30)
        }
        layout.addView(statusText)

        // Test button
        val testButton = Button(this).apply {
            text = "Request Device Admin"
            textSize = 18f
            setPadding(20, 20, 20, 20)
            setOnClickListener { testDeviceAdmin() }
        }
        layout.addView(testButton)

        // Check status button
        val checkButton = Button(this).apply {
            text = "Check Admin Status"
            textSize = 18f
            setPadding(20, 20, 20, 20)
            setOnClickListener { checkAdminStatus() }
        }
        layout.addView(checkButton)

        setContentView(layout)

        // Initialize device admin components
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        deviceAdminComponent = ComponentName(this, KioskDeviceAdminReceiver::class.java)

        android.util.Log.d("DeviceAdminTest", "=== Device Admin Test Activity Created ===")
        android.util.Log.d("DeviceAdminTest", "Component: $deviceAdminComponent")

        checkAdminStatus()
    }

    private fun testDeviceAdmin() {
        android.util.Log.d("DeviceAdminTest", "=== Testing Device Admin Request ===")
        statusText.text = "Requesting device admin..."

        try {
            // Create device admin intent
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdminComponent)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Test device admin for kiosk mode\n\nThis will enable:\n• App pinning\n• System control")

            android.util.Log.d("DeviceAdminTest", "Created intent with action: ${intent.action}")
            android.util.Log.d("DeviceAdminTest", "Component: $deviceAdminComponent")

            // Try to start the activity
            startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN)
            android.util.Log.d("DeviceAdminTest", "Started device admin activity successfully")

        } catch (e: Exception) {
            android.util.Log.e("DeviceAdminTest", "CRITICAL ERROR requesting device admin", e)
            statusText.text = "ERROR: ${e.message}"
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun checkAdminStatus() {
        try {
            val isAdmin = devicePolicyManager.isAdminActive(deviceAdminComponent)
            android.util.Log.d("DeviceAdminTest", "Current admin status: $isAdmin")

            val activeAdmins = devicePolicyManager.activeAdmins
            val adminCount = activeAdmins?.size ?: 0
            android.util.Log.d("DeviceAdminTest", "Total active admins: $adminCount")

            statusText.text = """
                Device Admin Status: ${if (isAdmin) "✓ ACTIVE" else "❌ INACTIVE"}
                
                Total Active Admins: $adminCount
                
                Component: 
                ${deviceAdminComponent.className}
                
                Package: ${deviceAdminComponent.packageName}
            """.trimIndent()

            // Log all active admins
            activeAdmins?.forEachIndexed { index, admin ->
                android.util.Log.d("DeviceAdminTest", "Active admin $index: $admin")
            }

        } catch (e: Exception) {
            android.util.Log.e("DeviceAdminTest", "Error checking admin status", e)
            statusText.text = "Error checking status: ${e.message}"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        android.util.Log.d("DeviceAdminTest", "=== Activity Result Received ===")
        android.util.Log.d("DeviceAdminTest", "Request Code: $requestCode")
        android.util.Log.d("DeviceAdminTest", "Result Code: $resultCode")
        android.util.Log.d("DeviceAdminTest", "Data: $data")

        if (requestCode == REQUEST_CODE_ENABLE_ADMIN) {
            val resultText = when (resultCode) {
                Activity.RESULT_OK -> "✓ RESULT_OK"
                Activity.RESULT_CANCELED -> "❌ RESULT_CANCELED"
                else -> "? UNKNOWN($resultCode)"
            }

            android.util.Log.d("DeviceAdminTest", "Device admin request result: $resultText")

            // Wait a moment then check actual status
            statusText.text = "Processing result...\n$resultText"

            // Check status after a short delay to ensure the system has processed
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val isNowAdmin = devicePolicyManager.isAdminActive(deviceAdminComponent)
                android.util.Log.d("DeviceAdminTest", "Admin status after result: $isNowAdmin")

                val finalMessage = """
                    Request Result: $resultText
                    
                    Admin Status: ${if (isNowAdmin) "✓ GRANTED" else "❌ NOT GRANTED"}
                    
                    ${if (isNowAdmin) "SUCCESS! Device Admin is now active." else "FAILED! Device Admin was not granted."}
                """.trimIndent()

                statusText.text = finalMessage

                val toastMessage = if (isNowAdmin) "✓ Device Admin Granted!" else "❌ Device Admin Denied"
                Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show()

            }, 1000) // Wait 1 second for system to process
        }
    }
}