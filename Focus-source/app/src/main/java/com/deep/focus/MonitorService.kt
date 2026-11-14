package com.deep.focus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat

/**
 * Watches usage events (Usage Access permission, NOT Accessibility) and opens the full-screen
 * [FocusActivity] whenever a guarded app comes to the foreground.
 */
class MonitorService : Service() {

    companion object {
        const val CHANNEL_ID = "focus_monitor"
        const val ALERT_CHANNEL_ID = "focus_checkin"
        const val NOTIF_ID = 1001
        const val ALERT_NOTIF_ID = 1002
        private const val POLL_MS = 500L
        private const val LEAVE_GRACE_MS = 2500L

        @Volatile
        var running: Boolean = false

        fun start(c: Context) {
            Prefs.setMonitoring(c, true)
            ContextCompat.startForegroundService(c, Intent(c, MonitorService::class.java))
        }

        fun stop(c: Context) {
            Prefs.setMonitoring(c, false)
            c.stopService(Intent(c, MonitorService::class.java))
        }
    }

    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var lastEventTs = 0L
    private var otherSince = 0L
    private var ticks = 0

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // New screen-on counts as a fresh open
            FocusState.sessionPackage = null
            otherSince = 0L
        }
    }

    private val loop = object : Runnable {
        override fun run() {
            try {
                poll()
            } catch (e: Throwable) {
                // keep the monitor alive no matter what
            }
            handler?.postDelayed(this, POLL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        createChannels()
        val t = HandlerThread("focus-monitor")
        t.start()
        thread = t
        handler = Handler(t.looper)
        registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
        lastEventTs = System.currentTimeMillis()
        handler?.removeCallbacks(loop)
        handler?.post(loop)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running = false
        handler?.removeCallbacks(loop)
        thread?.quitSafely()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    // ---------------------------------------------------------------------------------------

    private fun poll() {
        if (!UsageHelper.hasUsageAccess(this)) return
        val tracked = Prefs.trackedPackages(this)
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(lastEventTs + 1, now + 1000)
        val ev = UsageEvents.Event()
        var launchPkg: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            if (ev.timeStamp > lastEventTs) lastEventTs = ev.timeStamp
            if (ev.eventType != UsageHelper.EV_RESUMED) continue
            val pkg = ev.packageName ?: continue
            if (pkg == packageName) continue

            if (pkg in tracked) {
                otherSince = 0L
                launchPkg = if (pkg != FocusState.sessionPackage && !FocusState.popupShowing) pkg else null
            } else {
                launchPkg = null
                if (otherSince == 0L) otherSince = SystemClock.elapsedRealtime()
            }
        }

        // User really left the guarded app (not just a quick system dialog)
        if (otherSince != 0L && SystemClock.elapsedRealtime() - otherSince >= LEAVE_GRACE_MS) {
            FocusState.sessionPackage = null
            otherSince = 0L
        }

        launchPkg?.let { showPopup(it, tracked) }

        ticks++
        if (ticks % 60 == 1) {
            UsageHelper.recordToday(this, UsageHelper.todayStats(this, tracked))
        }
    }

    private fun showPopup(pkg: String, tracked: Set<String>) {
        val stats = UsageHelper.todayStats(this, tracked)
        UsageHelper.recordToday(this, stats)
        val s = stats[pkg]

        val intent = Intent(this, FocusActivity::class.java)
            .putExtra(FocusActivity.EXTRA_PKG, pkg)
            .putExtra(FocusActivity.EXTRA_SECONDS, s?.seconds ?: 0L)
            .putExtra(FocusActivity.EXTRA_OPENS, maxOf(1, s?.opens ?: 1))
            .putExtra(FocusActivity.EXTRA_STREAK, UsageHelper.streak(this))
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )

        if (UsageHelper.hasOverlay(this)) {
            FocusState.popupShowing = true
            try {
                startActivity(intent)
            } catch (e: Exception) {
                FocusState.popupShowing = false
            }
        } else {
            postFullScreenNotification(intent)
        }
    }

    private fun postFullScreenNotification(intent: Intent) {
        val pi = PendingIntent.getActivity(
            this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("Focus check-in")
            .setContentText("Tap to take a moment before you scroll")
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java).notify(ALERT_NOTIF_ID, n)
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Focus monitoring", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(ALERT_CHANNEL_ID, "Focus check-in", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val names = Prefs.trackedPackages(this).joinToString(", ") { TrackedApps.label(it) }
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_focus)
            .setContentTitle("Focus is on")
            .setContentText(if (names.isEmpty()) "No apps selected" else "Guarding $names")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }
}
