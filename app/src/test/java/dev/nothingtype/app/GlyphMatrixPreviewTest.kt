package dev.nothingtype.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.widget.FrameLayout
import dev.nothingtype.ime.GlyphPreviewFrames
import dev.nothingtype.ime.GlyphSync.Companion.MATRIX_SIZE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GlyphMatrixPreviewTest {
    @Test fun previewLightsTheFramePixelsAndClearsThemWhenTheFrameCloses() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val preview = GlyphMatrixPreview(activity)
        val container = FrameLayout(activity).apply { addView(preview) }
        activity.setContentView(container)
        preview.layout(0, 0, 112, 112)
        val frame = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888).apply {
            setPixel(MATRIX_SIZE / 2, MATRIX_SIZE / 2, Color.WHITE)
        }
        fun centerBrightness(): Int {
            val screenshot = Bitmap.createBitmap(112, 112, Bitmap.Config.ARGB_8888)
            preview.draw(Canvas(screenshot))
            return Color.red(screenshot.getPixel(56, 56))
        }
        try {
            val idle = centerBrightness()
            GlyphPreviewFrames.display(frame)
            assertTrue(centerBrightness() > idle)
            GlyphPreviewFrames.display(null)
            assertEquals(idle, centerBrightness())

            container.removeView(preview)
            GlyphPreviewFrames.display(frame)
            assertEquals(idle, centerBrightness())
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
