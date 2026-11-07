package dev.nothingtype.ui

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.Button

/** Optional touch-down dispatch; accessibility still uses the normal performClick path. */
class InstantKey(context: Context) : Button(context) {
    var instant = false
    var immediateRelease = false
    var onTypingTouch: ((InstantKey) -> Unit)? = null
    var moveCursor: ((Int) -> Unit)? = null
    private var activePointer = -1
    private var gestureOrigin = 0f
    private var gestureY = 0f
    private var cursorDragging = false
    private var held = false
    private val holdAction = Runnable {
        if (isPressed && isEnabled && isShown) held = performLongClick()
    }

    private fun clearPress() {
        removeCallbacks(holdAction)
        removeCallbacks(repeatAction)
        isPressed = false
    }

    fun confirmPendingTap() {
        if (!isPressed || !isEnabled || instant || held || repeated) return
        clearPress()
        performClick()
    }

    private fun movePointer(x: Float, y: Float) {
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        val move = moveCursor
        if (move != null && (isPressed || cursorDragging)) {
            val dx = x - gestureOrigin
            val step = maxOf(context.dp(16), slop)
            val count = (dx / step).toInt().coerceIn(-12, 12)
            if (count != 0 && (cursorDragging || kotlin.math.abs(dx) > kotlin.math.abs(y - gestureY))) {
                clearPress()
                cursorDragging = true
                gestureOrigin = x
                repeat(kotlin.math.abs(count)) { move(if (count < 0) -1 else 1) }
                return
            }
        }
        val margin = if (repeatDelete) 0 else slop
        if (x < -margin || y < -margin || x >= width + margin || y >= height + margin) {
            if (onTypingTouch != null && !repeatDelete) {
                // Keep the initial letter when a quick tap rolls off its visual bounds.
                removeCallbacks(holdAction)
            } else {
                clearPress()
            }
        }
    }
    var toolbarIcon: android.graphics.drawable.Drawable? = null
    var iconSizeDp = 22
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
            val size = minOf(context.dp(iconSizeDp), width, height)
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
        brush.typeface = typeface
        brush.textSize = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
        val spacing = brush.textSize / 8
        val wordWidth = brush.measureText(word)
        val mark = spaceMark
        val total = wordWidth + spacing * (mark[0].length + 2)
        val scale = minOf(1f, (width - context.dp(4)).coerceAtLeast(1) / total,
            height / (brush.descent() - brush.ascent()))
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
        clearPress()
        activePointer = -1
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if ((!instant && !immediateRelease && !repeatDelete) || !isEnabled) return super.onTouchEvent(event)
        val index = event.actionIndex
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val x = event.getX(index)
                val y = event.getY(index)
                // Android can deliver a finger that landed in a gap to the existing target.
                if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN &&
                    (onTypingTouch == null || x < 0 || y < 0 || x >= width || y >= height)) return true
                onTypingTouch?.invoke(this)
                clearPress()
                activePointer = event.getPointerId(index)
                gestureOrigin = x
                gestureY = y
                cursorDragging = false
                held = false
                repeated = false
                isPressed = true
                if (instant) performClick()
                val timeout = ViewConfiguration.getLongPressTimeout().toLong()
                if (repeatDelete) postDelayed(repeatAction, timeout)
                else if (isLongClickable) postDelayed(holdAction, timeout)
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = event.findPointerIndex(activePointer)
                if (pointerIndex >= 0) movePointer(event.getX(pointerIndex), event.getY(pointerIndex))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(index) == activePointer) {
                confirmPendingTap()
                clearPress()
                activePointer = -1
            }
            MotionEvent.ACTION_CANCEL -> {
                clearPress()
                activePointer = -1
            }
        }
        return true
    }
    override fun performClick(): Boolean = super.performClick()
}
