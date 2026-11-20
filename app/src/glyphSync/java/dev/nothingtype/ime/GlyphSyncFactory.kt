package dev.nothingtype.ime

import android.annotation.TargetApi
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.icu.text.BreakIterator
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixFrame
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphMatrixObject
import dev.nothingtype.ime.GlyphSync.Companion.MATRIX_SIZE
import dev.nothingtype.settings.GlyphBrightness
import dev.nothingtype.settings.GlyphEffect
import dev.nothingtype.settings.GlyphMode
import dev.nothingtype.settings.Preferences
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

internal fun createGlyphSync(context: Context): GlyphSync = MatrixGlyphSync(context.applicationContext)

@TargetApi(33)
private class MatrixGlyphSync(private val context: Context) : GlyphSync {
    private data class Tap(
        val text: String?, val session: Int, val preview: Boolean, val expiresAt: Long,
        val mode: GlyphMode, val effect: GlyphEffect
    )

    private data class Display(
        val session: Int, val preview: Boolean, val mode: GlyphMode, val effect: GlyphEffect, val shownAt: Long,
        val bitmap: Bitmap?, val symbolPixels: IntArray?
    ) {
        val durationMs = if (mode == GlyphMode.SYMBOLS) SYMBOL_LIFETIME_MS else effect.durationMs
    }

    private val thread = HandlerThread("NothingTypeGlyph").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val prefs = Preferences(context)
    private val pending = AtomicReference<Tap>()
    @Volatile private var session = 0
    @Volatile private var active = false
    @Volatile private var disposed = false

    // The SDK and frame rendering run off the keyboard's touch thread.
    private var manager: GlyphMatrixManager? = null
    private var connectionAttempt = 0
    private var connected = false
    private var frameVisible = false
    private var previewVisible = false
    private var display: Display? = null
    private val effects = GlyphEffects()
    private val expireFrame = Runnable { closeFrame() }
    private val drawFrame = Runnable { drawPendingLetter() }
    private val retryConnect = Runnable { drawPendingLetter() }
    private val connectionTimeout = Runnable { reconnect() }
    private val graphemes = BreakIterator.getCharacterInstance(Locale.ROOT)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun start() {
        active = true
        worker.post {
            if (active && manager == null) connect()
        }
    }

    override fun show(text: String, preview: Boolean) {
        if (!active || text.isEmpty()) return
        val mode = prefs.glyphMode
        if (mode == GlyphMode.SYMBOLS && (text.length > 12 || !Character.isLetterOrDigit(text.codePointAt(0)))) return
        val character = text.takeIf { mode == GlyphMode.SYMBOLS }
        pending.set(Tap(character, session, preview, SystemClock.uptimeMillis() + PENDING_LIFETIME_MS,
            mode, mode.effect(prefs.glyphAnimation)))
        scheduleFrame()
    }

    private fun scheduleFrame() {
        if (!worker.hasCallbacks(drawFrame)) worker.postDelayed(drawFrame, FRAME_INTERVAL_MS)
    }

    override fun clear() {
        active = false
        session++
        pending.set(null)
        worker.removeCallbacks(drawFrame)
        worker.removeCallbacks(expireFrame)
        GlyphPreviewFrames.display(null)
        worker.post { closeFrame() }
    }

    override fun dispose() {
        clear()
        disposed = true
        worker.post {
            disconnect()
            worker.removeCallbacksAndMessages(null)
            thread.quitSafely()
        }
    }

    private fun connect() {
        val attempt = ++connectionAttempt
        val initialized = callSdk {
            val sdk = GlyphMatrixManager.getInstance(context)
            manager = sdk
            sdk.init(object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(componentName: ComponentName) {
                    worker.post {
                        if (disposed || attempt != connectionAttempt) return@post
                        if (!callSdk { sdk.register(Glyph.DEVICE_23112) }) {
                            reconnect()
                            return@post
                        }
                        connected = true
                        worker.removeCallbacks(connectionTimeout)
                        Log.d(TAG, "Glyph Matrix service registered")
                        if (pending.get() != null) scheduleFrame()
                    }
                }

                override fun onServiceDisconnected(componentName: ComponentName) {
                    worker.post {
                        if (attempt == connectionAttempt) reconnect()
                    }
                }
            })
        }
        if (initialized) worker.postDelayed(connectionTimeout, CONNECT_TIMEOUT_MS)
        else reconnect()
    }

    private fun disconnect() {
        worker.removeCallbacks(connectionTimeout)
        connectionAttempt++
        closeFrame()
        connected = false
        val sdk = manager
        manager = null
        if (sdk != null) callSdk { sdk.unInit() }
    }

    private fun reconnect() {
        disconnect()
        worker.removeCallbacks(retryConnect)
        if (active && !disposed && pending.get() != null) worker.postDelayed(retryConnect, RETRY_INTERVAL_MS)
    }

    private fun drawPendingLetter() {
        if (!active || disposed) return
        val now = SystemClock.uptimeMillis()
        if (!connected) {
            val next = pending.get() ?: return
            if (next.session != session || now >= next.expiresAt) pending.compareAndSet(next, null)
            else if (manager == null && !worker.hasCallbacks(retryConnect)) connect()
            return
        }
        val sdk = manager ?: return
        val tap = pending.getAndSet(null)?.takeIf {
            it.session == session && now < it.expiresAt && (it.text == null || isSingleGrapheme(it.text))
        }
        if (tap != null) startDisplay(tap, now)
        val current = display ?: return
        val age = now - current.shownAt
        if (current.session != session || age >= current.durationMs) return
        val mode = prefs.glyphMode
        if (!prefs.glyphSync || mode != current.mode || mode.effect(prefs.glyphAnimation) != current.effect) {
            closeFrame()
            return
        }

        val levels = effects.levels(current.effect, now)
        val brightness = GlyphBrightness(prefs.glyphBrightness, current.mode)
        if (current.bitmap != null || levels?.any { it > 0 } == true) {
            val submitted = callSdk {
                val builder = GlyphMatrixFrame.Builder()
                if (levels != null) addEffectLayers(builder, current.symbolPixels, levels, brightness)
                current.bitmap?.let { builder.addTop(layer(it, brightness.peak)) }
                val frame = builder.build(context)
                if (!active || current.session != session) return
                sdk.setAppMatrixFrame(frame)
            }
            if (!submitted) {
                if (tap != null && tap.session == session) pending.compareAndSet(null, tap)
                reconnect()
                return
            }

            frameVisible = true
            if (current.preview) {
                previewVisible = true
                val preview = previewBitmap(current.symbolPixels, levels, brightness)
                main.post {
                    if (active && current.session == session) GlyphPreviewFrames.display(preview)
                }
            }
            Log.d(TAG, "Glyph Matrix frame submitted")
        } else hideOutput()

        if (age < current.effect.durationMs) scheduleFrame()
    }

    private fun isSingleGrapheme(text: String): Boolean {
        graphemes.setText(text)
        val single = graphemes.next() == text.length
        graphemes.setText("")
        return single
    }

    private fun startDisplay(tap: Tap, now: Long) {
        val previous = display
        if (previous?.effect != tap.effect || previous.mode != tap.mode || previous.session != tap.session) effects.clear()
        effects.tap(now)
        val bitmap = tap.text?.let(::renderLetter)
        val pixels = bitmap?.let {
            IntArray(MATRIX_SIZE * MATRIX_SIZE).apply {
                bitmap.getPixels(this, 0, MATRIX_SIZE, 0, 0, MATRIX_SIZE, MATRIX_SIZE)
            }
        }
        val current = Display(tap.session, tap.preview, tap.mode, tap.effect, now, bitmap, pixels)
        display = current
        worker.removeCallbacks(expireFrame)
        worker.postAtTime(expireFrame, current.shownAt + current.durationMs)
    }

    private fun addEffectLayers(
        builder: GlyphMatrixFrame.Builder, symbolPixels: IntArray?, levels: ByteArray, brightness: GlyphBrightness
    ) {
        val dim = IntArray(levels.size)
        val bright = IntArray(levels.size)
        for (index in levels.indices) {
            if (symbolPixels != null && Color.alpha(symbolPixels[index]) >= INK_THRESHOLD) continue
            when (levels[index].toInt()) {
                1 -> dim[index] = Color.WHITE
                2 -> bright[index] = Color.WHITE
            }
        }
        if (dim.any { it != 0 }) builder.addLow(layer(pixelBitmap(dim), brightness.effect(1)))
        if (bright.any { it != 0 }) builder.addMid(layer(pixelBitmap(bright), brightness.effect(2)))
    }

    private fun previewBitmap(symbolPixels: IntArray?, levels: ByteArray?, brightness: GlyphBrightness): Bitmap {
        val pixels = IntArray(MATRIX_SIZE * MATRIX_SIZE)
        for (index in pixels.indices) {
            val letterAlpha = if (symbolPixels != null) Color.alpha(symbolPixels[index]) else 0
            val alpha = if (letterAlpha >= INK_THRESHOLD) letterAlpha * brightness.peak / 255
                        else maxOf(letterAlpha * brightness.peak / 255, brightness.effect(levels?.get(index) ?: 0))
            pixels[index] = Color.argb(alpha, 255, 255, 255)
        }
        return pixelBitmap(pixels)
    }

    private fun pixelBitmap(pixels: IntArray) =
        Bitmap.createBitmap(pixels, MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888)

    private fun layer(bitmap: Bitmap, brightness: Int) =
        GlyphMatrixObject.Builder().setImageSource(bitmap).setBrightness(brightness).build()

    private fun renderLetter(text: String): Bitmap {
        val bitmap = Bitmap.createBitmap(MATRIX_SIZE, MATRIX_SIZE, Bitmap.Config.ARGB_8888)
        val bounds = Rect()
        paint.textSize = LETTER_SIZE
        paint.getTextBounds(text, 0, text.length, bounds)
        val available = MATRIX_SIZE - 4f
        if (bounds.width() > available || bounds.height() > available) {
            paint.textSize *= available / maxOf(bounds.width(), bounds.height())
            paint.getTextBounds(text, 0, text.length, bounds)
        }
        Canvas(bitmap).drawText(text, MATRIX_SIZE / 2f - bounds.exactCenterX(),
            MATRIX_SIZE / 2f - bounds.exactCenterY(), paint)
        return bitmap
    }

    private fun closeFrame() {
        display = null
        effects.clear()
        hideOutput()
    }

    private fun hideOutput() {
        if (previewVisible) {
            previewVisible = false
            main.post { GlyphPreviewFrames.display(null) }
        }
        if (!frameVisible) return
        frameVisible = false
        if (callSdk { manager?.closeAppMatrix() }) Log.d(TAG, "Glyph Matrix frame closed")
    }

    private inline fun callSdk(action: () -> Unit): Boolean = try {
        action()
        true
    } catch (error: Exception) {
        Log.w(TAG, "Glyph Matrix API call failed", error)
        false
    }

    companion object {
        private const val TAG = "NothingTypeGlyph"
        private const val LETTER_SIZE = 17f
        private const val INK_THRESHOLD = 95
        private const val FRAME_INTERVAL_MS = 50L
        private const val SYMBOL_LIFETIME_MS = 650L
        private const val PENDING_LIFETIME_MS = 1800L
        private const val CONNECT_TIMEOUT_MS = 900L
        private const val RETRY_INTERVAL_MS = 160L
    }
}
