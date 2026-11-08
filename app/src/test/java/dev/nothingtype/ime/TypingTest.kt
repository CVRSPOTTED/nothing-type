package dev.nothingtype.ime

import android.text.InputType
import android.text.Selection
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.widget.Button
import dev.nothingtype.keyboard.Language
import dev.nothingtype.settings.Preferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TypingTest {
    class EditorIme : NothingTypeIme() {
        val connection by lazy { BaseInputConnection(View(this), true) }
        override fun getCurrentInputConnection() = connection
    }

    private fun buttons(view: View): List<Button> = when (view) {
        is Button -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun editor(
        language: Language = Language.EN,
        type: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT,
        autocorrect: Boolean = true,
        suggestions: Boolean = true
    ): Pair<EditorIme, View> {
        val ime = Robolectric.buildService(EditorIme::class.java).create().get()
        Preferences(ime).apply {
            this.language = language
            this.autocorrect = autocorrect
            this.suggestions = suggestions
        }
        ime.onStartInput(EditorInfo().apply { inputType = type; initialSelStart = 0; initialSelEnd = 0 }, false)
        return ime to ime.onCreateInputView()
    }

    private fun type(view: View, text: String) {
        text.forEach { letter ->
            val label = if (letter == ' ') setOf("space", "пробел") else setOf(letter.toString())
            buttons(view).first { it.text.toString() in label }.performClick()
        }
    }

    @Test fun correctsEnglishAndRussianTyposAndKeepsCapitalization() {
        for ((language, typo, expected) in listOf(
            Triple(Language.EN, "helllo ", "hello "),
            Triple(Language.RU, "превет.", "привет.")
        )) {
            val (ime, view) = editor(language)
            type(view, typo)
            assertEquals(expected, ime.connection.editable.toString())
        }
    }

    @Test fun deletionRestoresTheOriginalAndSpaceAcceptsItWithoutCorrectingAgain() {
        val (ime, view) = editor()
        type(view, "helllo ")
        assertEquals("hello ", ime.connection.editable.toString())
        buttons(view).first { it.text == "⌫" }.performClick()
        assertEquals("helllo", ime.connection.editable.toString())
        type(view, " ")
        assertEquals("helllo ", ime.connection.editable.toString())
    }

    @Test fun correctionWorksWithSuggestionStripDisabled() {
        val (ime, view) = editor(suggestions = false)
        type(view, "helllo ")
        assertEquals("hello ", ime.connection.editable.toString())
    }
    @Test fun manualExceptionsApplyImmediatelyBeforeTheLargeDictionaryIsReady() {
        val (ime, view) = editor()
        Preferences(ime).personalWords = setOf("helllo")
        type(view, "helllo ")
        assertEquals("helllo ", ime.connection.editable.toString())
        ime.onDestroy()
    }

    @Test fun correctionCanBeDisabledAndNeverChangesSensitiveOrAddressFields() {
        val cases = listOf(
            (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT) to false,
            InputType.TYPE_CLASS_TEXT to true,
            (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT) to true,
            (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD) to true,
            (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS) to true,
            (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI) to true,
            (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) to true
        )
        for ((type, enabled) in cases) {
            val (ime, view) = editor(type = type, autocorrect = enabled)
            type(view, "helllo ")
            assertEquals("helllo ", ime.connection.editable.toString())
        }
    }

    @Test fun movingTheCursorCancelsUndoInsteadOfReplacingAnotherWord() {
        val (ime, view) = editor()
        type(view, "helllo ")
        ime.onUpdateSelection(0, 0, 6, 6, -1, -1)
        Selection.setSelection(ime.connection.editable, 2)
        ime.onUpdateSelection(6, 6, 2, 2, -1, -1)
        buttons(view).first { it.text == "⌫" }.performClick()
        assertEquals("hllo ", ime.connection.editable.toString())
    }
}
