package dev.keyloom.keyboard

import android.Manifest
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.Button
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSpeechRecognizer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DictationTest {
    class EditorIme : KeyloomIme() {
        val words = mutableListOf<String>()
        private val editor by lazy { object : BaseInputConnection(View(this), true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                words.add(text.toString())
                return true
            }
        } }
        override fun getCurrentInputConnection() = editor as android.view.inputmethod.InputConnection
        override fun isInputViewShown() = true
    }
    private fun microphone(view: View): Button? {
        if (view is Button && view.contentDescription?.startsWith("Голосовой ввод") == true) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) microphone(view.getChildAt(i))?.let { return it }
        return null
    }
    @Test fun missingLocalModelRetriesSystemAndCursorNotificationAllowsResult() {
        val ime = Robolectric.buildService(EditorIme::class.java).create().get()
        shadowOf(ime.application).grantPermissions(Manifest.permission.RECORD_AUDIO)
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        shadowOf(ime.packageManager).addResolveInfoForIntent(Intent(RecognitionService.SERVICE_INTERFACE), ResolveInfo().apply {
            serviceInfo = ServiceInfo().apply { name = "Recognizer"; packageName = "test.speech" }
        })
        ime.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false)
        requireNotNull(microphone(ime.onCreateInputView())).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        val local = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        Shadow.extract<ShadowSpeechRecognizer>(local).triggerOnError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        shadowOf(Looper.getMainLooper()).idle()
        val system = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
        assertNotSame(local, system)
        ime.onUpdateSelection(0, 0, 1, 1, -1, -1)
        Shadow.extract<ShadowSpeechRecognizer>(system).triggerOnResults(Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("проверка"))
        })
        assertEquals(listOf("проверка"), ime.words)
    }
}
