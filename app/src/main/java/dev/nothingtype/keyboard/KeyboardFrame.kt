package dev.nothingtype.keyboard

import android.content.Context
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import dev.nothingtype.settings.Preferences
import dev.nothingtype.ui.dp
import dev.nothingtype.ui.rounded
import dev.nothingtype.ui.roundedOutline

/** The reserved navigation area is outside the keys, and is not a clickable overlay. */
// Left/right refer to the user's physical hand, not reading direction.
@android.annotation.SuppressLint("ViewConstructor", "RtlHardcoded")
class KeyboardFrame(context: Context, private val prefs: Preferences, panel: View) : FrameLayout(context) {
    private var navigationBottom = 0
    init {
        isMotionEventSplittingEnabled = true
        background = rounded(prefs.palette.canvas, context.dp(24).toFloat())
        roundedOutline(24)
        addView(panel, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
            when (prefs.alignment) { -1 -> Gravity.LEFT; 1 -> Gravity.RIGHT; else -> Gravity.CENTER_HORIZONTAL }))
        applySpacing(0, 0, 0)
        setOnApplyWindowInsetsListener { _, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                // Never use Type.ime(): it includes this keyboard's own height.
                val bars = insets.getInsets(WindowInsets.Type.navigationBars() or WindowInsets.Type.displayCutout())
                applySpacing(bars.left, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                applySpacing(insets.systemWindowInsetLeft, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
    }
    private fun applySpacing(left: Int, right: Int, bottom: Int) {
        navigationBottom = bottom
        setPadding(left + context.dp(prefs.sideMargin), 0, right + context.dp(prefs.sideMargin),
            PlacementPolicy.bottomPadding(navigationBottom, context.dp(prefs.navigationReserve)))
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); requestApplyInsets() }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        getChildAt(0).layoutParams.width = PlacementPolicy.keyboardWidth(available, 0, prefs.widthPercent)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
