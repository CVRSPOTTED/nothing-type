package dev.nothingtype.ime

import android.graphics.Bitmap
import android.os.Build
import dev.nothingtype.BuildConfig

internal interface GlyphSync {
    fun start()
    fun show(text: String, preview: Boolean)
    fun clear()
    fun dispose()

    companion object {
        const val PREVIEW_EDITOR = "dev.nothingtype.glyph_preview"
        const val MATRIX_SIZE = 25

        val isAvailable: Boolean
            get() = BuildConfig.GLYPH_SYNC && Build.VERSION.SDK_INT >= 33 &&
                Build.MANUFACTURER.equals("Nothing", ignoreCase = true) && Build.MODEL == "A024"
    }
}

// Accessed on the main thread. Only the test editors in Nothing Type opt in.
internal object GlyphPreviewFrames {
    private val listeners = mutableSetOf<(Bitmap?) -> Unit>()

    fun observe(listener: (Bitmap?) -> Unit) {
        listeners.add(listener)
    }

    fun remove(listener: (Bitmap?) -> Unit) {
        listeners.remove(listener)
    }

    fun display(frame: Bitmap?) {
        listeners.forEach { it(frame) }
    }
}
