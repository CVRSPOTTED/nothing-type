package dev.nothingtype.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.View
import dev.nothingtype.ime.GlyphEffects
import dev.nothingtype.ime.GlyphPreviewFrames
import dev.nothingtype.ime.GlyphSync.Companion.MATRIX_SIZE
import dev.nothingtype.settings.GlyphBrightness
import dev.nothingtype.settings.GlyphEffect
import dev.nothingtype.settings.GlyphMode

internal class GlyphMatrixPreview(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pixels = IntArray(MATRIX_SIZE * MATRIX_SIZE)
    private var demo: Demo? = null
    private val demoEffects by lazy { GlyphEffects() }
    private val demoFrame = Runnable { drawDemo() }
    private val observer: (Bitmap?) -> Unit = { bitmap ->
        if (bitmap != null) {
            stopDemo()
            bitmap.getPixels(pixels, 0, MATRIX_SIZE, 0, 0, MATRIX_SIZE, MATRIX_SIZE)
        } else if (demo == null) pixels.fill(Color.TRANSPARENT)
        invalidate()
    }

    private data class Demo(
        val effect: GlyphEffect, val brightness: GlyphBrightness, val count: Int, val startedAt: Long,
        var tapsPlayed: Int = 0
    )

    fun playDemo(effect: GlyphEffect, percent: Int, count: Int) {
        stopDemo()
        demo = Demo(effect, GlyphBrightness(percent, GlyphMode.ANIMATION), count, SystemClock.uptimeMillis())
        drawDemo()
    }

    fun stopDemo() {
        if (demo == null) return
        demo = null
        removeCallbacks(demoFrame)
        demoEffects.clear()
        pixels.fill(Color.TRANSPARENT)
        invalidate()
    }

    private fun drawDemo() {
        val current = demo ?: return
        val now = SystemClock.uptimeMillis()
        while (current.tapsPlayed < current.count && now >= current.startedAt + current.tapsPlayed * 125L) {
            demoEffects.tap(current.startedAt + current.tapsPlayed * 125L)
            current.tapsPlayed++
        }
        val levels = demoEffects.levels(current.effect, now)
        if (levels == null && current.tapsPlayed == current.count) {
            stopDemo()
            return
        }
        for (index in pixels.indices) {
            val alpha = levels?.let { current.brightness.effect(it[index]) } ?: 0
            pixels[index] = Color.argb(alpha, 255, 255, 255)
        }
        invalidate()
        postDelayed(demoFrame, 50L)
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        GlyphPreviewFrames.observe(observer)
    }

    override fun onDetachedFromWindow() {
        GlyphPreviewFrames.remove(observer)
        stopDemo()
        pixels.fill(Color.TRANSPARENT)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val step = size / (MATRIX_SIZE + 3)
        val center = (MATRIX_SIZE - 1) / 2f
        val halfDot = step * 0.36f
        paint.color = 0xFF0C0C0C.toInt()
        canvas.drawCircle(centerX, centerY, size / 2f, paint)

        for (y in 0 until MATRIX_SIZE) {
            val dy = y - center
            for (x in 0 until MATRIX_SIZE) {
                val dx = x - center
                if (dx * dx + dy * dy > 153f) continue
                val glow = Color.alpha(pixels[y * MATRIX_SIZE + x])
                val shade = 40 + glow * 204 / 255
                paint.color = Color.rgb(shade, shade, shade)
                val left = centerX + dx * step - halfDot
                val top = centerY + dy * step - halfDot
                val radius = step * 0.13f
                canvas.drawRoundRect(left, top, left + 2 * halfDot, top + 2 * halfDot, radius, radius, paint)
            }
        }
    }
}
