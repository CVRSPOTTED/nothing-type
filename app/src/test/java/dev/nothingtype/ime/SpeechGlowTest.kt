package dev.nothingtype.ime

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Looper
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import dev.nothingtype.keyboard.AmbientDrawable
import dev.nothingtype.keyboard.KeyboardPanel
import dev.nothingtype.settings.Preferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSpeechRecognizer
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en", sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SpeechGlowTest {
    private fun descendants(view: View): List<View> =
        listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun glowRespondsToSpeechLevelsAndIgnoresTypingAndLateCallbacks() {
        val ime = Robolectric.buildService(DictationTest.EditorIme::class.java).create().get()
        Preferences(ime).ambient = true
        shadowOf(ime.application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        ime.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false)
        val views = descendants(ime.onCreateInputView())
        val panel = views.filterIsInstance<KeyboardPanel>().single()
        val glow = panel.background as AmbientDrawable
        val baseline = AmbientDrawable(Preferences(ime).palette.canvas)
        listOf(glow, baseline).forEach { it.setBounds(0, 0, 320, 180); it.running = true }
        fun draw(drawable: AmbientDrawable): Bitmap {
            val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)
            drawable.draw(Canvas(bitmap))
            return bitmap
        }
        fun sameFrame(): Boolean {
            val actual = draw(glow)
            val expected = draw(baseline)
            return try { actual.sameAs(expected) } finally { actual.recycle(); expected.recycle() }
        }
        views.filterIsInstance<Button>().first { it.text == "q" }.performClick()
        ShadowSystemClock.advanceBy(Duration.ofMillis(33))
        assertTrue(sameFrame())

        views.filterIsInstance<Button>().first { it.contentDescription?.startsWith("Voice input") == true }.performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val speech = Shadow.extract<ShadowSpeechRecognizer>(ShadowSpeechRecognizer.getLatestSpeechRecognizer())
        speech.triggerOnReadyForSpeech(Bundle())
        speech.triggerOnRmsChanged(10f)
        ShadowSystemClock.advanceBy(Duration.ofMillis(33))
        assertFalse(sameFrame())

        speech.triggerOnEndOfSpeech()
        // A recognizer can deliver delayed levels after the listening phase ends.
        repeat(100) {
            speech.triggerOnRmsChanged(10f)
            ShadowSystemClock.advanceBy(Duration.ofMillis(33))
            draw(glow).recycle()
            draw(baseline).recycle()
        }
        assertTrue(sameFrame())
        speech.triggerOnResults(Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("hello"))
        })
        ime.onDestroy()
    }
}
