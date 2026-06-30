package com.example.portedesetoiles

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class StargateView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    companion object {
        private const val CHEVRON_COUNT = 9
        private val CHEVRON_ANGLE_RADS = Array(CHEVRON_COUNT) { i -> Math.toRadians(i * 40.0) }
    }

    var onOpenClicked: (() -> Unit)? = null

    private var ringRotation = 0f
    private var activeChevrons = 0
    private var horizonPulse = 0f

    private var rotationAnimator: ValueAnimator? = null
    private var chevronAnimator: ValueAnimator? = null
    private var pulseAnimator: ValueAnimator? = null

    // Geometry – computed in onSizeChanged
    private var cx = 0f
    private var cy = 0f
    private var outerR = 0f
    private var innerR = 0f
    private var ringMidR = 0f
    private var horizonR = 0f

    // Reusable Path objects
    private val annulusPath = Path()
    private val chevronBuf = Path()

    // Paints
    private val annulusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(255, 32, 32, 50)
    }
    private val outerEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(180, 100, 100, 160)
    }
    private val innerEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(120, 80, 80, 130)
    }
    private val ringDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(60, 150, 150, 200)
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(50, 120, 120, 180)
        strokeWidth = 2f
    }
    private val chevronOffPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(200, 60, 55, 30)
    }
    private val chevronOnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(255, 255, 140, 0)
    }
    private val chevronGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(120, 255, 160, 0)
        maskFilter = BlurMaskFilter(30f, BlurMaskFilter.Blur.NORMAL)
    }
    private val outerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(55, 0, 200, 220)
        maskFilter = BlurMaskFilter(40f, BlurMaskFilter.Blur.NORMAL)
    }
    private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val horizonRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 180, 230, 255)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.MONOSPACE,
            android.graphics.Typeface.BOLD
        )
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 180, 50)
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.MONOSPACE
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val size = min(w, h).toFloat()
        cx = w / 2f
        cy = h / 2f
        outerR = size * 0.47f
        innerR = size * 0.315f
        ringMidR = (outerR + innerR) / 2f
        horizonR = innerR * 0.94f

        outerEdgePaint.strokeWidth = size * 0.007f
        innerEdgePaint.strokeWidth = size * 0.004f
        ringDetailPaint.strokeWidth = size * 0.003f
        outerGlowPaint.strokeWidth = outerR * 0.12f
        textPaint.textSize = size * 0.09f
        subTextPaint.textSize = size * 0.038f

        // Precompute annulus path
        annulusPath.reset()
        annulusPath.addCircle(cx, cy, outerR, Path.Direction.CW)
        annulusPath.addCircle(cx, cy, innerR, Path.Direction.CCW)
        annulusPath.fillType = Path.FillType.EVEN_ODD
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Always square: use the constrained width as both dimensions
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w)
    }

    override fun onDraw(canvas: Canvas) {
        // 1. Outer ambient glow
        canvas.drawCircle(cx, cy, outerR + outerGlowPaint.strokeWidth / 2f, outerGlowPaint)

        // 2. Ring annulus (donut)
        canvas.drawPath(annulusPath, annulusPaint)

        // 3. Ring edges & groove lines
        canvas.drawCircle(cx, cy, outerR, outerEdgePaint)
        canvas.drawCircle(cx, cy, innerR, innerEdgePaint)
        canvas.drawCircle(cx, cy, outerR * 0.91f, ringDetailPaint)
        canvas.drawCircle(cx, cy, innerR * 1.09f, ringDetailPaint)

        // 4. Rotating tick marks
        canvas.save()
        canvas.rotate(ringRotation, cx, cy)
        drawTickMarks(canvas)
        canvas.restore()

        // 5. Chevrons
        drawChevrons(canvas)

        // 6. Event horizon (center)
        drawEventHorizon(canvas)

        // 7. Center label
        drawCenterText(canvas)
    }

    private fun drawTickMarks(canvas: Canvas) {
        val innerTick = outerR * 0.86f
        val outerTick = outerR * 0.93f
        repeat(36) { i ->
            val angle = Math.toRadians(i * 10.0)
            canvas.drawLine(
                (cx + innerTick * sin(angle)).toFloat(), (cy - innerTick * cos(angle)).toFloat(),
                (cx + outerTick * sin(angle)).toFloat(), (cy - outerTick * cos(angle)).toFloat(),
                tickPaint
            )
        }
    }

    private fun drawChevrons(canvas: Canvas) {
        CHEVRON_ANGLE_RADS.forEachIndexed { i, rad ->
            val on = i < activeChevrons
            if (on) canvas.drawPath(buildChevronPath(rad), chevronGlowPaint)
            canvas.drawPath(buildChevronPath(rad), if (on) chevronOnPaint else chevronOffPaint)
        }
    }

    private fun buildChevronPath(rad: Double): Path {
        val sinA = sin(rad).toFloat()
        val cosA = cos(rad).toFloat()
        val px = cx + ringMidR * sinA
        val py = cy - ringMidR * cosA
        val h = outerR * 0.065f
        val w = outerR * 0.040f

        chevronBuf.reset()
        // Apex: points outward
        chevronBuf.moveTo(px + sinA * h * 0.55f, py - cosA * h * 0.55f)
        // Base corners: inward ± perpendicular
        chevronBuf.lineTo(px - sinA * h * 0.45f + cosA * w, py + cosA * h * 0.45f + sinA * w)
        chevronBuf.lineTo(px - sinA * h * 0.45f - cosA * w, py + cosA * h * 0.45f - sinA * w)
        chevronBuf.close()
        return chevronBuf
    }

    private fun drawEventHorizon(canvas: Canvas) {
        val pulse = horizonPulse
        val coreG = (150 + (pulse * 80f).toInt()).coerceAtMost(255)
        val coreColor = Color.argb(255, 0, coreG, 200)
        val midColor = Color.argb(255, 0, 80, 160)
        val edgeColor = Color.argb(255, 0, 15, 45)

        horizonPaint.shader = RadialGradient(
            cx, cy, horizonR,
            intArrayOf(coreColor, midColor, Color.argb(255, 0, 45, 110), edgeColor),
            floatArrayOf(0f, 0.30f + pulse * 0.10f, 0.70f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, horizonR, horizonPaint)

        // Rim glow
        horizonRimPaint.strokeWidth = horizonR * 0.07f
        horizonRimPaint.color = Color.argb((60 + (pulse * 80f).toInt()), 0, 200, 255)
        horizonRimPaint.maskFilter = BlurMaskFilter(horizonR * 0.08f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(cx, cy, horizonR * 0.93f, horizonRimPaint)
    }

    private fun drawCenterText(canvas: Canvas) {
        val midY = cy + textPaint.textSize * 0.35f
        canvas.drawText(context.getString(R.string.open), cx, midY, textPaint)
        if (activeChevrons in 1 until CHEVRON_COUNT) {
            canvas.drawText(
                context.getString(R.string.activating, activeChevrons, CHEVRON_COUNT),
                cx, midY + textPaint.textSize * 0.85f, subTextPaint
            )
        }
    }

    // ── Touch ───────────────────────────────────────────────────────────────

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val dx = event.x - cx
            val dy = event.y - cy
            if (sqrt(dx * dx + dy * dy) <= horizonR) onOpenClicked?.invoke()
        }
        return true
    }

    // ── Animation API ────────────────────────────────────────────────────────

    fun startActivation(onDone: (Boolean) -> Unit) {
        cancelAnimations()
        activeChevrons = 0
        ringRotation = 0f
        horizonPulse = 0f

        rotationAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 3_600
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { ringRotation = it.animatedValue as Float; invalidate() }
            start()
        }

        chevronAnimator = ValueAnimator.ofInt(0, CHEVRON_COUNT).apply {
            duration = 3_500
            interpolator = LinearInterpolator()
            addUpdateListener { activeChevrons = it.animatedValue as Int; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    startPulse()
                    onDone(true)
                }
            })
            start()
        }
    }

    private fun startPulse() {
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f, 0.25f).apply {
            duration = 1_200
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { horizonPulse = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun showError() {
        cancelAnimations()
        activeChevrons = 0
        horizonPulse = 0f
        // Brief red flash on outer edge
        ValueAnimator.ofArgb(Color.argb(255, 220, 30, 30), Color.argb(0, 220, 30, 30)).apply {
            duration = 900
            addUpdateListener { outerEdgePaint.color = it.animatedValue as Int; invalidate() }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    outerEdgePaint.color = Color.argb(180, 100, 100, 160)
                    invalidate()
                }
            })
            start()
        }
    }

    fun reset() {
        cancelAnimations()
        activeChevrons = 0
        ringRotation = 0f
        horizonPulse = 0f
        outerEdgePaint.color = Color.argb(180, 100, 100, 160)
        invalidate()
    }

    private fun cancelAnimations() {
        rotationAnimator?.cancel()
        chevronAnimator?.cancel()
        pulseAnimator?.cancel()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAnimations()
    }
}
