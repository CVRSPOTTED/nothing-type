package dev.nothingtype.keyboard

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
    private var voiceTarget = 0f
    private var voiceEnvelope = 0f
    private var voiceSampleAt = 0L
    private var previousFrame = 0L

    fun updateVoiceLevel(rmsDb: Float) {
        if (!rmsDb.isFinite()) return
        voiceTarget = ((rmsDb + 2f) / 12f).coerceIn(0f, 1f)
        voiceSampleAt = SystemClock.uptimeMillis()
    }

    fun clearVoiceLevel() { voiceTarget = 0f }

    var running = false
        set(value) {
            field = value
            unscheduleSelf(this)
            if (value) {
                previousFrame = SystemClock.uptimeMillis()
                scheduleSelf(this, previousFrame + 33)
            } else {
                voiceTarget = 0f
                voiceEnvelope = 0f
            }
        }
    override fun draw(canvas: Canvas) {
        canvas.drawColor(base)
        val now = SystemClock.uptimeMillis()
        val target = if (running && now - voiceSampleAt < 250) voiceTarget else 0f
        val elapsed = (now - previousFrame).coerceIn(0L, 100L).toFloat()
        previousFrame = now
        val smoothing = 1f - kotlin.math.exp(-elapsed / if (target > voiceEnvelope) 65f else 220f)
        voiceEnvelope += (target - voiceEnvelope) * smoothing
        if (target == 0f && voiceEnvelope < 0.001f) voiceEnvelope = 0f
        val time = now / 7000.0
        val radius = bounds.width() * (0.46f + voiceEnvelope * 0.18f)
        paints.forEachIndexed { i, paint ->
            val x = bounds.width() * (0.5f + (0.32f + voiceEnvelope * 0.05f) * sin(time + i * 2.1).toFloat())
            val y = bounds.height() * (0.5f + (0.3f + voiceEnvelope * 0.06f) * sin(time * 0.7 + i * 2.7).toFloat())
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
