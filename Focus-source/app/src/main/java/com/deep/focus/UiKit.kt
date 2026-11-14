package com.deep.focus

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.LinearLayout
import android.widget.TextView

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

object C {
    val ORANGE: Int = Color.parseColor("#F5A524")
    val PEACH: Int = Color.parseColor("#FFD3A1")
    val MUTED: Int = Color.parseColor("#A3A3AD")
    val DIVIDER: Int = Color.parseColor("#26FFFFFF")
    val GREEN: Int = Color.parseColor("#34D86A")
    val INK: Int = Color.parseColor("#0B0B10")
    val CARD: Int = Color.parseColor("#D916161B")
    val CARD_STROKE: Int = Color.parseColor("#26FFFFFF")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun rounded(color: Int, radius: Float, strokeColor: Int = 0, strokeWidth: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
        if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
    }

fun lp(w: Int, h: Int, top: Int = 0, weight: Float = 0f): LinearLayout.LayoutParams =
    LinearLayout.LayoutParams(w, h, weight).apply { topMargin = top }

/** style: 0 = regular, 1 = medium, 2 = bold */
fun Context.label(text: String, sizeSp: Float, color: Int, style: Int = 0): TextView =
    TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
        includeFontPadding = false
        typeface = when (style) {
            2 -> Typeface.create("sans-serif", Typeface.BOLD)
            1 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            else -> Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

/** Small orange, letter-spaced section label. */
fun Context.sectionLabel(text: String): TextView =
    label(text.uppercase(), 13f, C.ORANGE, 2).apply { letterSpacing = 0.16f }

@Suppress("ClickableViewAccessibility")
fun View.pressFeedback() {
    setOnTouchListener { v, e ->
        when (e.action) {
            MotionEvent.ACTION_DOWN -> v.alpha = 0.82f
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.alpha = 1f
        }
        false
    }
}

/** Orange ring + dot logo. */
class LogoView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val s = minOf(width, height).toFloat()
        val cx = width / 2f
        val cy = height / 2f
        p.color = C.ORANGE
        p.style = Paint.Style.STROKE
        p.strokeWidth = s * 0.12f
        canvas.drawCircle(cx, cy, s / 2f - p.strokeWidth / 2f, p)
        p.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, s * 0.17f, p)
    }
}

/** Deep blue / purple top, warm orange glow lower down, near-black bottom. */
class AuroraBackgroundView(c: Context) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun glow(canvas: Canvas, cx: Float, cy: Float, r: Float, color: Int, w: Float, h: Float) {
        paint.shader = RadialGradient(cx, cy, r, color, color and 0x00FFFFFF, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, paint)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        paint.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(
                Color.parseColor("#1B1F66"),
                Color.parseColor("#1A1646"),
                Color.parseColor("#22162E"),
                Color.parseColor("#0B0A12")
            ),
            floatArrayOf(0f, 0.35f, 0.62f, 0.92f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)

        glow(canvas, w * 0.10f, h * 0.04f, w * 1.00f, Color.parseColor("#CC3A4BC4"), w, h)
        glow(canvas, w * 0.98f, h * 0.02f, w * 0.85f, Color.parseColor("#AA6B2FA8"), w, h)
        glow(canvas, w * 0.80f, h * 0.50f, w * 0.95f, Color.parseColor("#CCB4622F"), w, h)
        glow(canvas, w * 0.76f, h * 0.45f, w * 0.30f, Color.parseColor("#99E3A869"), w, h)

        paint.shader = LinearGradient(
            0f, h * 0.55f, 0f, h,
            Color.parseColor("#000A0A10"), Color.parseColor("#FF0A0A10"),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, paint)
    }
}

/** "Continue in 3s" pill with an orange progress bar; becomes tappable when the countdown ends. */
class CountdownButton(c: Context) : View(c) {
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val clip = Path()

    private var progress = 0f
    private var ready = false
    private var text = ""
    private var animator: ValueAnimator? = null

    var readyText: String = "Continue"
    var onContinue: (() -> Unit)? = null

    init {
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = context.dpf(1f)
        strokePaint.color = Color.parseColor("#33FFFFFF")
        barPaint.color = C.ORANGE
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = context.dpf(18f)
        textPaint.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        isClickable = true
        setOnClickListener { if (ready) onContinue?.invoke() }
        pressFeedback()
    }

    fun start(seconds: Int) {
        animator?.cancel()
        ready = false
        progress = 0f
        if (seconds <= 0) {
            markReady()
            return
        }
        text = "Continue in ${seconds}s"
        invalidate()
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = seconds * 1000L
            interpolator = LinearInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                if (progress >= 1f) {
                    markReady()
                } else {
                    val left = Math.ceil(((1f - progress) * seconds).toDouble()).toInt()
                    text = "Continue in ${maxOf(1, left)}s"
                    invalidate()
                }
            }
            start()
        }
    }

    fun cancel() {
        animator?.cancel()
    }

    private fun markReady() {
        ready = true
        progress = 1f
        text = readyText
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val inset = strokePaint.strokeWidth / 2f
        rect.set(inset, inset, w - inset, h - inset)
        val r = rect.height() / 2f

        bgPaint.color = if (ready) Color.parseColor("#34343B") else Color.parseColor("#26262B")
        canvas.drawRoundRect(rect, r, r, bgPaint)

        if (!ready) {
            canvas.save()
            clip.reset()
            clip.addRoundRect(rect, r, r, Path.Direction.CW)
            canvas.clipPath(clip)
            canvas.drawRect(0f, h - context.dpf(3.5f), w * progress, h, barPaint)
            canvas.restore()
        }
        canvas.drawRoundRect(rect, r, r, strokePaint)

        textPaint.color = if (ready) Color.WHITE else Color.parseColor("#8E8E97")
        val y = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(text, w / 2f, y, textPaint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
