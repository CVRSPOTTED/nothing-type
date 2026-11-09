package dev.nothingtype.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
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
        // Inflate the decor first, then size it before WindowManager attaches it.
        create()
        val available = (context.resources.configuration.screenWidthDp - 32).coerceAtLeast(1)
        window?.setLayout(context.dp(available.coerceAtMost(520)), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private companion object {
        val ink = 0xFFF2F2F2.toInt()
    }
}

internal fun EditText.styleDialogInput() {
    setTextColor(0xFFF2F2F2.toInt())
    setHintTextColor(0xFFB0B0B0.toInt())
    background = rounded(0xFF303030.toInt(), context.dp(16).toFloat())
    setPadding(context.dp(14), context.dp(12), context.dp(14), context.dp(12))
}
