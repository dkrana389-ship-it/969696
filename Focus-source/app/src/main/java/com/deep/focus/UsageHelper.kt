package com.deep.focus

import android.Manifest
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class AppStats(val seconds: Long, val opens: Int)

object UsageHelper {

    // UsageEvents.Event.ACTIVITY_RESUMED / MOVE_TO_FOREGROUND == 1, ACTIVITY_PAUSED / MOVE_TO_BACKGROUND == 2
    const val EV_RESUMED = 1
    const val EV_PAUSED = 2

    fun hasUsageAccess(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val uid = android.os.Process.myUid()
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, c.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, c.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlay(c: Context): Boolean = Settings.canDrawOverlays(c)

    fun hasNotifications(c: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

    fun ignoresBatteryOptimizations(c: Context): Boolean {
        val pm = c.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(c.packageName)
    }

    fun startOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun dayKey(ts: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ts))

    /**
     * Foreground time and number of fresh opens today, per guarded package.
     * Built from usage events (no Accessibility needed).
     */
    fun todayStats(c: Context, packages: Set<String>): Map<String, AppStats> {
        val result = HashMap<String, AppStats>()
        if (packages.isEmpty()) return result
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(startOfToday(), now)
        val ev = UsageEvents.Event()

        val resumedAt = HashMap<String, Long>()   // "pkg/class" -> start time
        val millis = HashMap<String, Long>()
        val opens = HashMap<String, Int>()
        var lastFg: String? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(ev)
            val pkg = ev.packageName ?: continue
            if (pkg == c.packageName) continue // our own popup must not break a session
            val key = pkg + "/" + ev.className
            if (ev.eventType == EV_RESUMED) {
                if (pkg in packages) {
                    resumedAt[key] = ev.timeStamp
                    if (lastFg != pkg) opens[pkg] = (opens[pkg] ?: 0) + 1
                }
                lastFg = pkg
            } else if (ev.eventType == EV_PAUSED) {
                if (pkg in packages) {
                    val start = resumedAt.remove(key)
                    if (start != null && ev.timeStamp >= start) {
                        millis[pkg] = (millis[pkg] ?: 0L) + (ev.timeStamp - start)
                    }
                }
            }
        }
        // Still on screen right now
        for ((key, start) in resumedAt) {
            val pkg = key.substringBefore('/')
            if (pkg == lastFg && now >= start) {
                millis[pkg] = (millis[pkg] ?: 0L) + (now - start)
            }
        }
        for (p in packages) {
            result[p] = AppStats((millis[p] ?: 0L) / 1000L, opens[p] ?: 0)
        }
        return result
    }

    /** Saves today's total guarded-app minutes (used for the streak). */
    fun recordToday(c: Context, stats: Map<String, AppStats>) {
        var totalSeconds = 0L
        for (s in stats.values) totalSeconds += s.seconds
        Prefs.setDayMinutes(c, dayKey(), (totalSeconds / 60L).toInt())
    }

    /** Consecutive days (ending today) on which guarded-app time stayed within the daily limit. */
    fun streak(c: Context): Int {
        val limit = Prefs.limitMinutes(c)
        val cal = Calendar.getInstance()
        var n = 0
        for (i in 0 until 365) {
            val m = Prefs.dayMinutes(c, dayKey(cal.timeInMillis)) ?: break
            if (m > limit) break
            n++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return n
    }
}
