package com.deep.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (Prefs.monitoring(context) && UsageHelper.hasUsageAccess(context)) {
            try {
                MonitorService.start(context)
            } catch (e: Exception) {
                // Some OEMs block this; the user can reopen the app to restart Focus.
            }
        }
    }
}
