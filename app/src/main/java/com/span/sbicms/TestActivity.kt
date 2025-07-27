// OPTIONAL: Simple test activity to verify overlay works
// Add this to your project to test overlay functionality alone
// You can create this as TestActivity.kt if you want to test step by step

package com.span.sbicms

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity

class TestActivity : AppCompatActivity() {

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple layout with just a test button
        val button = Button(this).apply {
            text = "Test Overlay Only"
            setOnClickListener {
                testOverlayOnly()
            }
        }

        setContentView(button)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun testOverlayOnly() {
        if (!Settings.canDrawOverlays(this)) {
            // Request overlay permission
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            startActivity(intent)
            return
        }

        // Start just the overlay service
        val serviceIntent = Intent(this, OverlayService::class.java)
        startForegroundService(serviceIntent)

        // Launch Chrome
        val chromeIntent = packageManager.getLaunchIntentForPackage("com.android.chrome")
        if (chromeIntent != null) {
            chromeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(chromeIntent)
            Toast.makeText(this, "Overlay started! Check Chrome", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Chrome not found", Toast.LENGTH_SHORT).show()
        }
    }
}

// To use this test activity, add it to your AndroidManifest.xml:
/*
<activity android:name=".TestActivity" android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
*/