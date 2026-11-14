package com.deep.focus

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
    }

    override fun onResume() {
        super.onResume()
        setContentView(buildUi())
    }

    // ---------------------------------------------------------------------------------------

    private fun buildUi(): View {
        val root = FrameLayout(this)
        root.addView(AuroraBackgroundView(this), FrameLayout.LayoutParams(MATCH, MATCH))

        val scroll = ScrollView(this)
        scroll.overScrollMode = View.OVER_SCROLL_NEVER
        scroll.isVerticalScrollBarEnabled = false
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(24), dp(20), dp(36))
        scroll.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        root.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            col.setPadding(dp(20) + b.left, dp(24) + b.top, dp(20) + b.right, dp(36) + b.bottom)
            insets
        }

        val usage = UsageHelper.hasUsageAccess(this)
        val overlay = UsageHelper.hasOverlay(this)
        val monitoring = Prefs.monitoring(this)

        // ---- Header --------------------------------------------------------------------------
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.addView(LogoView(this), LinearLayout.LayoutParams(dp(34), dp(34)))
        val titles = LinearLayout(this)
        titles.orientation = LinearLayout.VERTICAL
        titles.setPadding(dp(12), 0, 0, 0)
        titles.addView(label("Focus", 26f, Color.WHITE, 2), lp(WRAP, WRAP))
        titles.addView(label("Be intentional with your scroll time", 14f, C.MUTED), lp(WRAP, WRAP, dp(3)))
        header.addView(titles, LinearLayout.LayoutParams(WRAP, WRAP))
        col.addView(header, lp(MATCH, WRAP))

        // ---- Status --------------------------------------------------------------------------
        col.addView(card {
            addView(sectionLabel("Status"), lp(WRAP, WRAP))
            val row = LinearLayout(this@MainActivity)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val dot = View(this@MainActivity)
            dot.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (monitoring) C.GREEN else Color.parseColor("#6B6B75"))
            }
            row.addView(dot, LinearLayout.LayoutParams(dp(11), dp(11)))
            val st = label(if (monitoring) "Focus is on" else "Focus is paused", 26f, Color.WHITE, 2)
            st.setPadding(dp(12), 0, 0, 0)
            row.addView(st, LinearLayout.LayoutParams(WRAP, WRAP))
            addView(row, lp(MATCH, WRAP, dp(14)))
            addView(
                label(
                    "Every time you open a guarded app, you'll get a full-screen check-in with today's time and your goal.",
                    15f, C.MUTED
                ).apply { setLineSpacing(0f, 1.15f) },
                lp(MATCH, WRAP, dp(10))
            )
            val ready = usage && overlay
            val btn = label(if (monitoring) "Stop Focus" else "Start Focus", 18f, C.INK, 2)
            btn.gravity = Gravity.CENTER
            btn.background = rounded(Color.WHITE, dpf(40f))
            btn.alpha = if (ready || monitoring) 1f else 0.55f
            btn.isClickable = true
            btn.pressFeedback()
            btn.setOnClickListener { onPrimary() }
            addView(btn, lp(MATCH, dp(56), dp(20)))
        }, lp(MATCH, WRAP, dp(22)))

        // ---- Setup ---------------------------------------------------------------------------
        col.addView(card {
            addView(sectionLabel("Setup"), lp(WRAP, WRAP))
            addView(
                label("No Accessibility service needed.", 14f, C.MUTED),
                lp(WRAP, WRAP, dp(8))
            )
            addView(
                permRow("Usage access", "Detects when a guarded app opens and measures your time.", usage, true) {
                    openUsageAccess()
                },
                lp(MATCH, WRAP, dp(14))
            )
            addView(
                permRow("Display over other apps", "Lets the check-in cover the app in full screen.", overlay, true) {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                    )
                },
                lp(MATCH, WRAP, dp(16))
            )
            addView(
                permRow("Notifications", "Recommended. Keeps the background service visible.", UsageHelper.hasNotifications(this@MainActivity), false) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
                    }
                },
                lp(MATCH, WRAP, dp(16))
            )
            addView(
                permRow("Unrestricted battery", "Recommended. Stops the phone from pausing Focus.", UsageHelper.ignoresBatteryOptimizations(this@MainActivity), false) {
                    openBattery()
                },
                lp(MATCH, WRAP, dp(16))
            )
        }, lp(MATCH, WRAP, dp(14)))

        // ---- Goal ----------------------------------------------------------------------------
        col.addView(card {
            addView(sectionLabel("Your goal"), lp(WRAP, WRAP))
            addView(
                label("This is what the check-in asks you about.", 14f, C.MUTED),
                lp(WRAP, WRAP, dp(8))
            )
            val et = EditText(this@MainActivity)
            et.setText(Prefs.goal(this@MainActivity))
            et.hint = "e.g. Mahindra Scorpio"
            et.setTextColor(Color.WHITE)
            et.setHintTextColor(Color.parseColor("#6B6B75"))
            et.textSize = 18f
            et.setSingleLine()
            et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            et.background = rounded(Color.parseColor("#22FFFFFF"), dpf(16f))
            et.setPadding(dp(16), dp(14), dp(16), dp(14))
            et.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    Prefs.setGoal(this@MainActivity, s?.toString()?.trim() ?: "")
                }
            })
            addView(et, lp(MATCH, WRAP, dp(14)))
        }, lp(MATCH, WRAP, dp(14)))

        // ---- Preferences ---------------------------------------------------------------------
        col.addView(card {
            addView(sectionLabel("Preferences"), lp(WRAP, WRAP))
            addView(
                stepper("Daily limit", "Minutes on guarded apps that keep your streak alive.",
                    Prefs.limitMinutes(this@MainActivity), "min", 5, 480, 5) {
                    Prefs.setLimitMinutes(this@MainActivity, it)
                },
                lp(MATCH, WRAP, dp(14))
            )
            addView(
                stepper("Pause before continuing", "How long the Continue button stays locked.",
                    Prefs.countdownSeconds(this@MainActivity), "s", 0, 15, 1) {
                    Prefs.setCountdownSeconds(this@MainActivity, it)
                },
                lp(MATCH, WRAP, dp(18))
            )
        }, lp(MATCH, WRAP, dp(14)))

        // ---- Guarded apps --------------------------------------------------------------------
        col.addView(card {
            addView(sectionLabel("Guarded apps"), lp(WRAP, WRAP))
            val tracked = Prefs.trackedPackages(this@MainActivity).toMutableSet()
            for (app in TrackedApps.all) {
                val row = LinearLayout(this@MainActivity)
                row.orientation = LinearLayout.HORIZONTAL
                row.gravity = Gravity.CENTER_VERTICAL
                row.addView(label(app.label, 17f, Color.WHITE, 1), lp(0, WRAP, 0, 1f))
                val sw = Switch(this@MainActivity)
                sw.isChecked = app.pkg in tracked
                val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
                sw.thumbTintList = ColorStateList(states, intArrayOf(C.ORANGE, Color.parseColor("#9A9AA3")))
                sw.trackTintList = ColorStateList(states, intArrayOf(Color.parseColor("#66F5A524"), Color.parseColor("#33FFFFFF")))
                sw.setOnCheckedChangeListener { _, on ->
                    if (on) tracked.add(app.pkg) else tracked.remove(app.pkg)
                    Prefs.setTracked(this@MainActivity, tracked)
                }
                row.addView(sw, LinearLayout.LayoutParams(WRAP, WRAP))
                addView(row, lp(MATCH, dp(48), dp(8)))
            }
        }, lp(MATCH, WRAP, dp(14)))

        // ---- Preview -------------------------------------------------------------------------
        val preview = label("Preview the check-in", 17f, Color.WHITE, 1)
        preview.gravity = Gravity.CENTER
        preview.background = rounded(Color.parseColor("#14FFFFFF"), dpf(40f), Color.parseColor("#33FFFFFF"), dp(1))
        preview.isClickable = true
        preview.pressFeedback()
        preview.setOnClickListener {
            startActivity(
                Intent(this, FocusActivity::class.java)
                    .putExtra(FocusActivity.EXTRA_PKG, "com.instagram.android")
                    .putExtra(FocusActivity.EXTRA_SECONDS, 15L * 60L)
                    .putExtra(FocusActivity.EXTRA_OPENS, 4)
                    .putExtra(FocusActivity.EXTRA_STREAK, 3)
            )
        }
        col.addView(preview, lp(MATCH, dp(54), dp(20)))

        val foot = label(
            "Focus reads app-usage stats on your phone to know when a guarded app opens. Nothing leaves your device.",
            13f, Color.parseColor("#7C7C86")
        )
        foot.gravity = Gravity.CENTER
        foot.setLineSpacing(0f, 1.15f)
        col.addView(foot, lp(MATCH, WRAP, dp(18)))

        return root
    }

    // ---- Building blocks ---------------------------------------------------------------------

    private fun card(block: LinearLayout.() -> Unit): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(20), dp(20), dp(20), dp(20))
        c.background = rounded(C.CARD, dpf(28f), C.CARD_STROKE, dp(1))
        c.block()
        return c
    }

    private fun permRow(
        title: String,
        desc: String,
        granted: Boolean,
        required: Boolean,
        onGrant: () -> Unit
    ): View {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(label(title, 17f, Color.WHITE, 1), lp(WRAP, WRAP))
        texts.addView(label(desc, 13f, C.MUTED).apply { setLineSpacing(0f, 1.1f) }, lp(WRAP, WRAP, dp(3)))
        val tl = lp(0, WRAP, 0, 1f)
        tl.marginEnd = dp(12)
        row.addView(texts, tl)

        val pill: TextView = if (granted) {
            label("Granted", 14f, C.GREEN, 1).apply {
                background = rounded(Color.parseColor("#2234D86A"), dpf(30f))
            }
        } else {
            label(if (required) "Grant" else "Allow", 14f, C.INK, 2).apply {
                background = rounded(C.ORANGE, dpf(30f))
                isClickable = true
                pressFeedback()
                setOnClickListener { onGrant() }
            }
        }
        pill.gravity = Gravity.CENTER
        pill.setPadding(dp(16), dp(9), dp(16), dp(9))
        row.addView(pill, LinearLayout.LayoutParams(WRAP, WRAP))
        return row
    }

    private fun stepper(
        title: String,
        desc: String,
        initial: Int,
        unit: String,
        min: Int,
        max: Int,
        step: Int,
        onChange: (Int) -> Unit
    ): View {
        var value = initial
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(label(title, 17f, Color.WHITE, 1), lp(WRAP, WRAP))
        texts.addView(label(desc, 13f, C.MUTED).apply { setLineSpacing(0f, 1.1f) }, lp(WRAP, WRAP, dp(3)))
        val tl = lp(0, WRAP, 0, 1f)
        tl.marginEnd = dp(10)
        row.addView(texts, tl)

        val valueTv = label("$value $unit", 16f, Color.WHITE, 1)
        valueTv.gravity = Gravity.CENTER
        valueTv.minWidth = dp(64)

        fun round(sign: String, delta: Int): TextView {
            val b = label(sign, 22f, Color.WHITE, 1)
            b.gravity = Gravity.CENTER
            b.background = rounded(Color.parseColor("#26FFFFFF"), dpf(40f))
            b.isClickable = true
            b.pressFeedback()
            b.setOnClickListener {
                value = (value + delta).coerceIn(min, max)
                valueTv.text = "$value $unit"
                onChange(value)
            }
            return b
        }
        row.addView(round("−", -step), LinearLayout.LayoutParams(dp(38), dp(38)))
        row.addView(valueTv, LinearLayout.LayoutParams(WRAP, WRAP))
        row.addView(round("+", step), LinearLayout.LayoutParams(dp(38), dp(38)))
        return row
    }

    // ---- Actions -----------------------------------------------------------------------------

    private fun onPrimary() {
        if (Prefs.monitoring(this)) {
            MonitorService.stop(this)
        } else {
            if (!UsageHelper.hasUsageAccess(this) || !UsageHelper.hasOverlay(this)) {
                Toast.makeText(this, "Allow Usage access and Display over other apps first", Toast.LENGTH_LONG).show()
                return
            }
            MonitorService.start(this)
        }
        setContentView(buildUi())
    }

    private fun openUsageAccess() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:$packageName")))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun openBattery() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
