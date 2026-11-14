package com.deep.focus

import android.content.Context
import android.content.SharedPreferences

data class TrackedApp(val pkg: String, val label: String)

object TrackedApps {
    val all = listOf(
        TrackedApp("com.instagram.android", "Instagram"),
        TrackedApp("com.google.android.youtube", "YouTube"),
        TrackedApp("com.facebook.katana", "Facebook"),
        TrackedApp("com.snapchat.android", "Snapchat")
    )

    val defaultPackages: Set<String> = all.map { it.pkg }.toSet()

    fun label(pkg: String): String = all.firstOrNull { it.pkg == pkg }?.label ?: pkg
}

object Prefs {
    private const val DEFAULT_GOAL = "Mahindra Scorpio"

    private fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences("focus_prefs", Context.MODE_PRIVATE)

    fun goal(c: Context): String {
        val g = sp(c).getString("goal", DEFAULT_GOAL)
        return if (g.isNullOrBlank()) DEFAULT_GOAL else g
    }

    fun setGoal(c: Context, value: String) {
        sp(c).edit().putString("goal", value).apply()
    }

    /** Daily limit (minutes on guarded apps) that keeps your streak alive. */
    fun limitMinutes(c: Context): Int = sp(c).getInt("limit", 60)

    fun setLimitMinutes(c: Context, value: Int) {
        sp(c).edit().putInt("limit", value).apply()
    }

    /** How long the "Continue" button stays locked on the popup. */
    fun countdownSeconds(c: Context): Int = sp(c).getInt("countdown", 3)

    fun setCountdownSeconds(c: Context, value: Int) {
        sp(c).edit().putInt("countdown", value).apply()
    }

    fun monitoring(c: Context): Boolean = sp(c).getBoolean("monitoring", false)

    fun setMonitoring(c: Context, value: Boolean) {
        sp(c).edit().putBoolean("monitoring", value).apply()
    }

    fun trackedPackages(c: Context): Set<String> {
        val stored = sp(c).getStringSet("tracked", null) ?: return TrackedApps.defaultPackages
        return HashSet(stored)
    }

    fun setTracked(c: Context, value: Set<String>) {
        sp(c).edit().putStringSet("tracked", HashSet(value)).apply()
    }

    fun setDayMinutes(c: Context, dayKey: String, minutes: Int) {
        sp(c).edit().putInt("day_$dayKey", minutes).apply()
    }

    fun dayMinutes(c: Context, dayKey: String): Int? {
        val s = sp(c)
        val k = "day_$dayKey"
        return if (s.contains(k)) s.getInt(k, 0) else null
    }
}

/** Shared in-process state between the monitor service and the popup. */
object FocusState {
    @Volatile var popupShowing: Boolean = false

    /** Package the user already chose to continue in. The popup will not reappear until they leave it. */
    @Volatile var sessionPackage: String? = null
}
