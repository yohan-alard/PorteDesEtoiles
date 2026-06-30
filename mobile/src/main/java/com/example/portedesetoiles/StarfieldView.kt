package com.example.portedesetoiles

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class StarfieldView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Star(
        val x: Float, val y: Float, val r: Float,
        val minAlpha: Float, val maxAlpha: Float,
        val speed: Float, val phase: Float
    )

    private val stars = ArrayList<Star>(200)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var nebulaPaint: Paint? = null
    private val rng = Random(0x57A5)

    private val ticker = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 12_000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { invalidate() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        stars.clear()
        // Small dim background stars
        repeat(170) {
            stars += Star(
                x = rng.nextFloat() * w,
                y = rng.nextFloat() * h,
                r = rng.nextFloat() * 1.4f + 0.3f,
                minAlpha = 0.08f, maxAlpha = 0.5f,
                speed = rng.nextFloat() * 1.5f + 0.3f,
                phase = rng.nextFloat() * 2f * PI.toFloat()
            )
        }
        // Bright foreground stars
        repeat(18) {
            stars += Star(
                x = rng.nextFloat() * w,
                y = rng.nextFloat() * h,
                r = rng.nextFloat() * 2f + 1.2f,
                minAlpha = 0.55f, maxAlpha = 1.0f,
                speed = rng.nextFloat() * 0.8f + 0.15f,
                phase = rng.nextFloat() * 2f * PI.toFloat()
            )
        }
        // Subtle teal nebula off-center
        nebulaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                w * 0.68f, h * 0.22f,
                maxOf(w, h) * 0.65f,
                intArrayOf(Color.argb(28, 0, 190, 210), Color.TRANSPARENT),
                null,
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        val t = System.currentTimeMillis() / 1000f
        nebulaPaint?.let { canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), it) }
        for (s in stars) {
            val alpha = s.minAlpha + (s.maxAlpha - s.minAlpha) *
                (0.5f + 0.5f * sin((t * s.speed + s.phase).toDouble()).toFloat())
            paint.color = Color.WHITE
            paint.alpha = (alpha * 255).toInt()
            canvas.drawCircle(s.x, s.y, s.r, paint)
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); ticker.start() }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); ticker.cancel() }
}
