package com.deep.focus

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** The full-screen "Is this getting you closer to <goal>?" check-in. */
class FocusActivity : Activity() {

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_OPENS = "opens"
        const val EXTRA_STREAK = "streak"
    }

    private var pkg = "com.instagram.android"
    private var countdown: CountdownButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FocusState.popupShowing = true
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 28) {
            val attrs = window.attributes
            attrs.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = attrs
        }
        render(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        FocusState.popupShowing = true
        render(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    // Leaving the popup any other way (Home, recents, screen off) simply dismisses it.
    override fun onStop() {
        super.onStop()
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        countdown?.cancel()
        FocusState.popupShowing = false
        super.onDestroy()
    }

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() {
        closeApp()
    }

    // ---------------------------------------------------------------------------------------

    private fun render(i: Intent) {
        countdown?.cancel()
        pkg = i.getStringExtra(EXTRA_PKG) ?: "com.instagram.android"
        val seconds = i.getLongExtra(EXTRA_SECONDS, 0L)
        val opens = i.getIntExtra(EXTRA_OPENS, 1)
        val streak = i.getIntExtra(EXTRA_STREAK, 0)
        setContentView(buildUi(TrackedApps.label(pkg), seconds, opens, streak))
        hideSystemBars()
    }

    private fun buildUi(appName: String, seconds: Long, opens: Int, streak: Int): View {
        val root = FrameLayout(this)
        root.addView(AuroraBackgroundView(this), FrameLayout.LayoutParams(MATCH, MATCH))

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(20), dp(28), dp(20), dp(24))
        root.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val b = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            content.setPadding(dp(20) + b.left, dp(28) + b.top, dp(20) + b.right, dp(24) + b.bottom)
            insets
        }

        // ---- Header: logo + name on the left, streak pill on the right ------------------------
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.addView(LogoView(this), LinearLayout.LayoutParams(dp(30), dp(30)))
        val name = label("Focus", 24f, Color.WHITE, 2)
        name.setPadding(dp(12), 0, 0, 0)
        header.addView(name, LinearLayout.LayoutParams(WRAP, WRAP))
        header.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))

        val pill = LinearLayout(this)
        pill.orientation = LinearLayout.HORIZONTAL
        pill.gravity = Gravity.CENTER_VERTICAL
        pill.setPadding(dp(16), dp(10), dp(18), dp(10))
        pill.background = rounded(Color.parseColor("#47121218"), dpf(40f), Color.parseColor("#33FFFFFF"), dp(1))
        val dot = View(this)
        dot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (streak > 0) C.GREEN else C.ORANGE)
        }
        pill.addView(dot, LinearLayout.LayoutParams(dp(9), dp(9)))
        val pillText = label(if (streak > 0) "Day $streak streak" else "Streak reset", 15f, Color.WHITE, 1)
        pillText.setPadding(dp(10), 0, 0, 0)
        pill.addView(pillText, LinearLayout.LayoutParams(WRAP, WRAP))
        header.addView(pill, LinearLayout.LayoutParams(WRAP, WRAP))
        content.addView(header, lp(MATCH, WRAP))

        content.addView(View(this), lp(MATCH, 0, 0, 1f))

        // ---- Glass card -----------------------------------------------------------------------
        val card = LinearLayout(this)
        card.orientation = LinearLayout.VERTICAL
        card.setPadding(dp(26), dp(26), dp(26), dp(26))
        card.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#38FFFFFF"), Color.parseColor("#E61C1C21"), Color.parseColor("#F0141417"))
        ).apply {
            cornerRadius = dpf(34f)
            setStroke(dp(1), Color.parseColor("#33FFFFFF"))
        }

        card.addView(sectionLabel("Today on $appName"), lp(WRAP, WRAP))

        // Big number
        val totalMin = ((seconds + 30L) / 60L).toInt()
        val bigText: String
        val unit: String
        if (totalMin < 60) {
            bigText = totalMin.toString()
            unit = "min"
        } else {
            bigText = (totalMin / 60).toString() + ":" + String.format("%02d", totalMin % 60)
            unit = "hr"
        }
        val bigRow = LinearLayout(this)
        bigRow.orientation = LinearLayout.HORIZONTAL
        val big = label(bigText, 92f, Color.WHITE, 2)
        big.letterSpacing = -0.03f
        bigRow.addView(big, LinearLayout.LayoutParams(WRAP, WRAP))
        val unitTv = label(unit, 30f, C.MUTED, 1)
        val unitLp = LinearLayout.LayoutParams(WRAP, WRAP)
        unitLp.marginStart = dp(10)
        bigRow.addView(unitTv, unitLp)
        card.addView(bigRow, lp(WRAP, WRAP, dp(10)))

        if (totalMin in 1..59) {
            big.text = "0"
            ValueAnimator.ofInt(0, totalMin).apply {
                duration = 750
                startDelay = 250
                interpolator = DecelerateInterpolator()
                addUpdateListener { big.text = (it.animatedValue as Int).toString() }
                start()
            }
        }

        card.addView(divider(), lp(MATCH, dp(1), dp(14)))

        // Stats row
        val yearHours = Math.round(seconds * 365.0 / 3600.0)
        val stats = LinearLayout(this)
        stats.orientation = LinearLayout.HORIZONTAL
        stats.addView(statColumn("$yearHours hrs", "a year at this pace", 0), lp(0, WRAP, 0, 1f))
        val vDivider = View(this)
        vDivider.setBackgroundColor(C.DIVIDER)
        stats.addView(vDivider, LinearLayout.LayoutParams(dp(1), MATCH))
        val opensWord = if (opens == 1) "time" else "times"
        stats.addView(statColumn("$opens $opensWord", "opened today", dp(20)), lp(0, WRAP, 0, 1f))
        card.addView(stats, lp(MATCH, WRAP, dp(18)))

        card.addView(divider(), lp(MATCH, dp(1), dp(18)))

        // Goal question
        card.addView(label("Is this getting you closer to", 20f, Color.parseColor("#B8B8C0")), lp(WRAP, WRAP, dp(20)))
        val goalTv = label(Prefs.goal(this) + "?", 36f, C.PEACH, 2)
        val gw = goalTv.paint.measureText(goalTv.text.toString())
        goalTv.paint.shader = LinearGradient(
            0f, 0f, maxOf(gw, 1f), 0f,
            intArrayOf(Color.parseColor("#FFE2B8"), Color.parseColor("#F7A95B")),
            null, Shader.TileMode.CLAMP
        )
        card.addView(goalTv, lp(WRAP, WRAP, dp(6)))

        // Buttons
        val close = label("Close $appName", 20f, C.INK, 2)
        close.gravity = Gravity.CENTER
        close.background = rounded(Color.WHITE, dpf(40f))
        close.isClickable = true
        close.pressFeedback()
        close.setOnClickListener { closeApp() }
        card.addView(close, lp(MATCH, dp(64), dp(22)))

        val cont = CountdownButton(this)
        cont.readyText = "Continue to $appName"
        cont.onContinue = { continueApp() }
        card.addView(cont, lp(MATCH, dp(58), dp(12)))
        countdown = cont

        val cardLp = lp(MATCH, WRAP)
        content.addView(card, cardLp)

        // Entrance animation
        card.alpha = 0f
        card.translationY = dpf(56f)
        card.animate().alpha(1f).translationY(0f).setDuration(480).setInterpolator(DecelerateInterpolator()).start()
        header.alpha = 0f
        header.animate().alpha(1f).setDuration(480).start()

        cont.start(Prefs.countdownSeconds(this))
        return root
    }

    private fun divider(): View {
        val v = View(this)
        v.setBackgroundColor(C.DIVIDER)
        return v
    }

    private fun statColumn(value: String, caption: String, startPad: Int): LinearLayout {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(startPad, 0, 0, 0)
        col.addView(label(value, 28f, Color.WHITE, 2), lp(WRAP, WRAP))
        col.addView(label(caption, 15f, C.MUTED), lp(WRAP, WRAP, dp(6)))
        return col
    }

    private fun closeApp() {
        FocusState.sessionPackage = null
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(home)
        finish()
    }

    private fun continueApp() {
        FocusState.sessionPackage = pkg
        finish()
    }
}
