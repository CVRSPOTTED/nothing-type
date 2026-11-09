package dev.nothingtype.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.widget.CompoundButton
import android.widget.Switch

/** A monochrome switch with the native checked state and accessibility actions. */
internal class NothingSwitch(context: Context) : CompoundButton(context) {
    private val indicator = Indicator()

    init {
        buttonDrawable = null
        background = null
        stateListAnimator = null
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        isAllCaps = false
        isFocusable = true
        isClickable = true
        compoundDrawablePadding = context.dp(12)
        setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, indicator, null)
    }

    override fun getAccessibilityClassName(): CharSequence = Switch::class.java.name

    override fun onDetachedFromWindow() {
        indicator.jumpToCurrentState()
        super.onDetachedFromWindow()
    }

    private inner class Indicator : Drawable() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var position = 0f
        private var target = 0f
        private var animator: ValueAnimator? = null

        override fun getIntrinsicWidth() = context.dp(36)
        override fun getIntrinsicHeight() = context.dp(25)
        override fun isStateful() = true

        override fun onStateChange(state: IntArray): Boolean {
            val next = if (android.R.attr.state_checked in state) 1f else 0f
            if (next == target) return false
            target = next
            if (!isShown || bounds.isEmpty || !ValueAnimator.areAnimatorsEnabled()) {
                jumpToCurrentState()
                return true
            }
            animator?.cancel()
            animator = ValueAnimator.ofFloat(position, target).apply {
                duration = 150
                addUpdateListener {
                    position = it.animatedValue as Float
                    invalidateSelf()
                }
                start()
            }
            return true
        }

        override fun jumpToCurrentState() {
            animator?.cancel()
            animator = null
            position = target
            invalidateSelf()
        }

        override fun draw(canvas: Canvas) {
            val trackRadius = bounds.height() / 2f
            val shade = (71 + (226 - 71) * position).toInt()
            paint.color = Color.rgb(shade, shade, shade)
            canvas.drawRoundRect(
                bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat(),
                trackRadius, trackRadius, paint
            )
            val direction = if (this@NothingSwitch.layoutDirection == View.LAYOUT_DIRECTION_RTL) 1f - position else position
            val center = bounds.left + trackRadius + (bounds.width() - 2 * trackRadius) * direction
            paint.color = 0xFF1B1B1B.toInt()
            canvas.drawCircle(center, bounds.exactCenterY(), trackRadius * (22f / 25f), paint)
        }

        override fun setAlpha(alpha: Int) = Unit
        override fun setColorFilter(colorFilter: ColorFilter?) = Unit
        @Deprecated("Required by Drawable")
        override fun getOpacity() = PixelFormat.TRANSLUCENT
    }
}
