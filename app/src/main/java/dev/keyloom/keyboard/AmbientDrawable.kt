package dev.keyloom.keyboard

import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.SystemClock
import kotlin.math.sin

/** Soft radial shapes: no per-frame bitmap allocation or full-surface blur. */
class AmbientDrawable(private val base: Int) : Drawable(), Runnable {
    private val paints = intArrayOf(0x9ADD3838.toInt(), 0x9A2DCE8B.toInt(), 0x9AE6B63B.toInt()).map { color ->
        Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(0f, 0f, 1f, color, Color.TRANSPARENT, Shader.TileMode.CLAMP) }
    }
    private val matrix = Matrix()
    var running = false
        set(value) { field = value; unscheduleSelf(this); if (value) scheduleSelf(this, SystemClock.uptimeMillis() + 33) }
    override fun draw(canvas: Canvas) {
        canvas.drawColor(base)
        val time = SystemClock.uptimeMillis() / 7000.0
        val radius = bounds.width() * 0.46f
        paints.forEachIndexed { i, paint ->
            val x = bounds.width() * (0.5f + 0.32f * sin(time + i * 2.1).toFloat())
            val y = bounds.height() * (0.5f + 0.3f * sin(time * 0.7 + i * 2.7).toFloat())
            matrix.setScale(radius, radius); matrix.postTranslate(x, y)
            paint.shader.setLocalMatrix(matrix)
            canvas.drawRect(bounds, paint)
        }
    }
    override fun run() { if (running) { invalidateSelf(); scheduleSelf(this, SystemClock.uptimeMillis() + 33) } }
    override fun setAlpha(alpha: Int) { paints.forEach { it.alpha = alpha }; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paints.forEach { it.colorFilter = colorFilter }; invalidateSelf() }
    @Deprecated("Drawable opacity is deprecated")
    override fun getOpacity() = PixelFormat.OPAQUE
}
