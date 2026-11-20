package dev.nothingtype.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import dev.nothingtype.ui.dp

internal class SetupArtwork(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keys = listOf(
        "111111111111111",
        "111111111111111",
        "011111111111110",
        "001011111110100"
    )

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = minOf(context.dp(232), MeasureSpec.getSize(widthMeasureSpec))
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val diameter = minOf(width, height).toFloat()
        val centerX = width / 2f
        val centerY = height / 2f
        val step = diameter / 27f
        paint.color = 0xFF292929.toInt()
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.dp(1).toFloat()
        canvas.drawCircle(centerX, centerY, diameter / 2f - paint.strokeWidth, paint)
        paint.style = Paint.Style.FILL

        for (y in -11..11) {
            for (x in -11..11) {
                if (x * x + y * y > 126) continue
                val lit = y in -3..3 && y % 2 != 0 &&
                    keys[(y + 3) / 2].getOrNull(x + 7) == '1'
                paint.color = if (lit) 0xFFF2F2F2.toInt() else 0xFF171717.toInt()
                val left = centerX + (x - 0.36f) * step
                val top = centerY + (y - 0.36f) * step
                val edge = step * 0.72f
                val radius = step * 0.11f
                canvas.drawRoundRect(left, top, left + edge, top + edge, radius, radius, paint)
            }
        }
    }
}
