package dev.nothingtype.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
fun Context.label(value: String, size: Float = 16f, color: Int = Color.WHITE) = TextView(this).apply {
    text = value; textSize = size; setTextColor(color)
    setPadding(dp(2), dp(8), dp(2), dp(8))
}
fun Context.action(value: String, fill: Int, ink: Int, radius: Int = 12) = InstantKey(this).apply {
    text = value; textSize = 14f; isAllCaps = false
    setTextColor(ink); gravity = Gravity.CENTER
    minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
    setPadding(dp(2), 0, dp(2), 0)
    background = RippleDrawable(ColorStateList.valueOf(0x337F7F7F), rounded(fill, dp(radius).toFloat()), null)
    stateListAnimator = null
    elevation = 0f
    translationZ = 0f
    isSoundEffectsEnabled = false
}
fun LinearLayout.gap(height: Int = 12) { addView(android.view.View(context), LinearLayout.LayoutParams(1, context.dp(height))) }

fun android.view.View.roundedOutline(radius: Int) {
    clipToOutline = true
    outlineProvider = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, context.dp(radius).toFloat())
        }
    }
}
