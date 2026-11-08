package dev.nothingtype.ime

import android.app.Activity
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.Button
import dev.nothingtype.ime.NothingTypeIme
import dev.nothingtype.keyboard.KeyboardPanel
import dev.nothingtype.keyboard.KeyboardState
import dev.nothingtype.settings.Preferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class StabilityRegressionTest {
    class RejectCompositionIme : NothingTypeIme() {
        val connection by lazy { object : BaseInputConnection(View(this), true) {
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean = false
        } }
        override fun getCurrentInputConnection() = connection
    }
    class TextIme : NothingTypeIme() {
        val connection by lazy { BaseInputConnection(View(this), true) }
        override fun getCurrentInputConnection() = connection
    }
    private fun buttons(view: View): List<Button> = when (view) {
        is Button -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
        else -> emptyList()
    }
    @Test fun typingSurvivesEditorRejectingComposition() {
        val ime = Robolectric.buildService(RejectCompositionIme::class.java).create().get()
        Preferences(ime).apply { autocorrect = false; suggestions = true }
        ime.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false)
        val view = ime.onCreateInputView()
        "hello".forEach { c -> buttons(view).single { it.text.toString() == c.toString() }.performClick() }
        buttons(view).single { it.text == "space" }.performClick()
        try { assertEquals("hello ", ime.connection.editable.toString()) } finally { ime.onDestroy() }
    }
    @Config(sdk = [26, 34])
    @Test fun backspaceDeletesAFlagAsOneVisibleCharacter() {
        val ime = Robolectric.buildService(TextIme::class.java).create().get()
        ime.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT }, false)
        val view = ime.onCreateInputView()
        try {
            for (glyph in listOf("🇷🇺", "👨‍👩‍👧‍👦", "👍🏽", "e\u0301", "1️⃣")) {
                ime.connection.commitText("a" + glyph, 1)
                buttons(view).single { it.text == "⌫" }.performClick()
                assertEquals("Deleting $glyph", "a", ime.connection.editable.toString())
                requireNotNull(ime.connection.editable).clear()
            }
        } finally { ime.onDestroy() }
    }
    @Test fun suggestionsDoNotChangeKeyboardHeightWithToolbarDisabled() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val prefs = Preferences(activity).apply { toolbar = false; suggestions = true }
        val panel = KeyboardPanel(activity, prefs, KeyboardState(), {}, {})
        activity.setContentView(panel)
        fun height(): Int {
            panel.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
            return panel.height
        }
        val before = height()
        panel.updateSuggestions(listOf("hello"))
        assertEquals(before, height())
    }
}
