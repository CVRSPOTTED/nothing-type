package dev.keyloom.keyboard

import android.content.Context
import android.view.MotionEvent
import android.widget.Button

/** Optional touch-down dispatch; accessibility still uses the normal performClick path. */
class InstantKey(context: Context) : Button(context) {
    var instant = false
    var toolbarIcon: android.graphics.drawable.Drawable? = null
    var spaceLegend = false
    var repeatDelete = false
    private val legendPaint by lazy { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    private val repeatAction = object : Runnable {
        override fun run() {
            if (!isPressed || !isShown || !isEnabled) return
            repeated = true
            performClick()
            postDelayed(this, 65)
        }
    }
    private var repeated = false

    override fun onDraw(canvas: android.graphics.Canvas) {
        toolbarIcon?.let { icon ->
            val size = context.dp(22)
            val left = (width - size) / 2
            val top = (height - size) / 2
            if (isActivated) {
                legendPaint.color = currentTextColor
                legendPaint.alpha = 45
                canvas.drawCircle(width / 2f, height / 2f, context.dp(19).toFloat(), legendPaint)
            }
            icon.setBounds(left, top, left + size, top + size)
            icon.draw(canvas)
            return
        }
        if (!spaceLegend) {
            super.onDraw(canvas)
            return
        }
        val word = text.toString()
        val brush = legendPaint
        brush.color = currentTextColor
        brush.alpha = 175
        brush.typeface = android.graphics.Typeface.DEFAULT
        brush.textSize = context.dp(16).toFloat()
        val spacing = brush.textSize / 8
        val wordWidth = brush.measureText(word)
        val mark = spaceMark
        val total = wordWidth + spacing * (mark[0].length + 2)
        val scale = minOf(1f, (width - context.dp(4)).coerceAtLeast(1) / total)
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        canvas.scale(scale, scale)
        canvas.drawText(word, -total / 2, -(brush.ascent() + brush.descent()) / 2, brush)
        val left = -total / 2 + wordWidth + spacing * 2
        mark.forEachIndexed { y, row ->
            row.forEachIndexed { x, dot ->
                if (dot == '1') canvas.drawCircle(left + x * spacing, (y - 2.5f) * spacing, spacing * 0.35f, brush)
            }
        }
        canvas.restore()
    }

    private companion object {
        val spaceMark = listOf("0101110010", "1001001001", "1001110001", "1001010001", "1001001001", "0100000010")
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(repeatAction)
        isPressed = false
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (repeatDelete && isEnabled) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    removeCallbacks(repeatAction)
                    repeated = false
                    isPressed = true
                    if (instant) performClick()
                    postDelayed(repeatAction, android.view.ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> if (event.x < 0 || event.y < 0 || event.x >= width || event.y >= height) {
                    isPressed = false
                    removeCallbacks(repeatAction)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    removeCallbacks(repeatAction)
                    val click = event.actionMasked == MotionEvent.ACTION_UP && isPressed && !instant && !repeated
                    isPressed = false
                    if (click) performClick()
                }
            }
            return true
        }
        if (!instant || !isEnabled) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { isPressed = true; performClick() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isPressed = false
        }
        return true
    }
    override fun performClick(): Boolean = super.performClick()
}
