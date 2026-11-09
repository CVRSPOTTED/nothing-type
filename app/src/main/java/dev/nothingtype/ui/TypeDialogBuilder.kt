package dev.nothingtype.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import dev.nothingtype.R

/** Uses the platform dialog behavior with Nothing Type's surfaces and controls. */
internal class TypeDialogBuilder(context: Context) : AlertDialog.Builder(context, R.style.TypeDialogTheme) {
    override fun setTitle(titleId: Int): AlertDialog.Builder = setTitle(context.getText(titleId))

    override fun setTitle(title: CharSequence?): AlertDialog.Builder {
        super.setTitle(title)
        return setCustomTitle(context.label(title?.toString().orEmpty(), 22f, ink).apply {
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.START
            setPadding(context.dp(24), context.dp(20), context.dp(24), context.dp(10))
            if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        })
    }

    override fun create(): AlertDialog = super.create().apply {
        setOnShowListener {
            window?.setBackgroundDrawable(rounded(panel, context.dp(28).toFloat()))
            window?.setLayout(
                minOf(context.dp((context.resources.configuration.screenWidthDp - 32).coerceAtLeast(1)), context.dp(520)),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)
                .forEach { which ->
                    getButton(which)?.apply {
                        val primary = which == AlertDialog.BUTTON_POSITIVE
                        isAllCaps = false
                        setTextColor(if (primary) panel else ink)
                        minWidth = 0
                        minimumWidth = 0
                        minHeight = context.dp(48)
                        stateListAnimator = null
                        elevation = 0f
                        val fill = RippleDrawable(
                            ColorStateList.valueOf(0x337F7F7F),
                            rounded(if (primary) ink else control, context.dp(24).toFloat()),
                            null
                        )
                        background = InsetDrawable(fill, context.dp(3), context.dp(2), context.dp(3), context.dp(2))
                        setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(8))
                    }
                }
            (getButton(AlertDialog.BUTTON_POSITIVE)?.parent as? ViewGroup)?.apply {
                setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom + context.dp(6))
            }
        }
    }

    private companion object {
        val ink = 0xFFF2F2F2.toInt()
        val panel = 0xFF1C1C1C.toInt()
        val control = 0xFF303030.toInt()
    }
}

internal fun EditText.styleDialogInput() {
    setTextColor(0xFFF2F2F2.toInt())
    setHintTextColor(0xFFB0B0B0.toInt())
    background = rounded(0xFF303030.toInt(), context.dp(16).toFloat())
    setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
}
