package dev.nothingtype.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import dev.nothingtype.ui.dp

/** Original 5×7 dot lettering. No proprietary font or brand assets. */
class DotTitle @JvmOverloads constructor(context: Context, ink: Int = Color.WHITE) : View(context) {
    private val glyphs = mapOf(
        'N' to listOf("10001","11001","11001","10101","10011","10011","10001"),
        'T' to listOf("11111","00100","00100","00100","00100","00100","00100"),
        'H' to listOf("10001","10001","10001","11111","10001","10001","10001"),
        'I' to listOf("01110","00100","00100","00100","00100","00100","01110"),
        'G' to listOf("01110","10001","10000","10111","10001","10001","01111"),
        'P' to listOf("11110","10001","10001","11110","10000","10000","10000"),
        ' ' to emptyList(),
        'K' to listOf("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
        'E' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
        'Y' to listOf("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
        'L' to listOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
        'O' to listOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
        'M' to listOf("10001", "11011", "10101", "10101", "10001", "10001", "10001")
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink }
    init { contentDescription = "Nothing Type"; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(resolveSize(context.dp(320), widthMeasureSpec), resolveSize(context.dp(70), heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val step = minOf(width / 72f, height / 9f)
        val top = (height - step * 7) / 2
        val left = (width - step * 71) / 2
        "NOTHING TYPE".forEachIndexed { index, char ->
            glyphs.getValue(char).forEachIndexed { y, row ->
                row.forEachIndexed { x, bit ->
                    if (bit == '1') canvas.drawCircle(left + (index * 6 + x + 0.5f) * step, top + (y + 0.5f) * step, step * 0.32f, paint)
                }
            }
        }
    }
}
