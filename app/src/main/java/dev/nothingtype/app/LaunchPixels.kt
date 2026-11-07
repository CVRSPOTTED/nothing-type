package dev.nothingtype.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin
import dev.nothingtype.ui.dp

/** A black curtain with a dot grid that clears from the bottom in a rolling wave. */
@android.annotation.SuppressLint("ViewConstructor") // Only created programmatically with a completion callback.
internal class LaunchPixels(context: Context, private val onFinished: (LaunchPixels) -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var progress = 0f
    private val animation = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 860L
        interpolator = LinearInterpolator()
        addUpdateListener {
            progress = it.animatedFraction
            invalidate()
        }
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onFinished(this@LaunchPixels)
            }
        })
    }

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animation.start()
    }

    override fun onDetachedFromWindow() {
        animation.removeAllListeners()
        animation.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val columns = ceil(width / context.dp(12).coerceAtLeast(1).toFloat()).toInt()
        val cell = width.toFloat() / columns
        val radius = cell * 0.14f
        val reveal = ((progress - 0.30f) / 0.70f).coerceIn(0f, 1f)
        val sweep = reveal * reveal * (2f - reveal)
        val waveStrength = sin(reveal * PI).toFloat()
        val brightness = (progress / 0.28f).coerceIn(0f, 1f)
        val dotColor = Color.argb((brightness * 255).toInt(), 255, 255, 255)

        for (column in 0 until columns) {
            val left = column * cell
            val x = left + cell / 2f
            val ripple = sin(column * 0.37 - reveal * 4.0).toFloat()
            val edge = (height * (1f - sweep + ripple * waveStrength * 0.085f))
                .coerceIn(0f, height.toFloat())
            paint.color = Color.BLACK
            canvas.drawRect(left, 0f, (column + 1) * cell, edge, paint)
            paint.color = dotColor
            var y = cell / 2f
            while (y + radius <= edge) {
                canvas.drawCircle(x, y, radius, paint)
                y += cell
            }
        }
    }
}
