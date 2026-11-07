package dev.nothingtype.ime

import android.text.InputType
import android.view.KeyEvent
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorContractTest {
    class EditorIme : NothingTypeIme() {
        val actions = mutableListOf<Int>()
        val keys = mutableListOf<KeyEvent>()
        val connection by lazy {
            object : BaseInputConnection(View(this), true) {
                override fun performEditorAction(actionCode: Int): Boolean {
                    actions.add(actionCode)
                    return true
                }
                override fun sendKeyEvent(event: KeyEvent): Boolean {
                    keys.add(event)
                    return true
                }
            }
        }
        override fun getCurrentInputConnection() = connection
    }

    private fun buttons(view: View): List<Button> = when (view) {
        is Button -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test fun customEnterLabelSendsTheEditorsOwnActionId() {
        val ime = Robolectric.buildService(EditorIme::class.java).create().get()
        ime.onStartInput(EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            actionId = 42
            actionLabel = "Approve"
        }, false)
        buttons(ime.onCreateInputView()).single { it.text == "Approve" }.performClick()
        assertEquals(listOf(42), ime.actions)
        assertEquals("", ime.connection.editable.toString())
        ime.onDestroy()
    }

    @Test fun noEnterActionPreservesNewlineEvenWithACustomLabel() {
        val ime = Robolectric.buildService(EditorIme::class.java).create().get()
        ime.onStartInput(EditorInfo().apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION
            actionId = 42
            actionLabel = "Send custom"
        }, false)
        buttons(ime.onCreateInputView()).single { it.text == "↵" }.performClick()
        assertTrue(ime.actions.isEmpty())
        assertEquals("\n", ime.connection.editable.toString())
        ime.onDestroy()
    }

    @Test fun legacyRawEditorsReceiveKeyboardEventsForTextDeleteAndEnter() {
        val ime = Robolectric.buildService(EditorIme::class.java).create().get()
        ime.onStartInput(EditorInfo().apply { inputType = InputType.TYPE_NULL }, false)
        val keys = buttons(ime.onCreateInputView())
        for (text in listOf("q", "space", "⌫", "↵")) keys.single { it.text == text }.performClick()
        assertEquals(listOf(KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ENTER),
            ime.keys.filter { it.action == KeyEvent.ACTION_DOWN }.map { it.keyCode })
        assertTrue(ime.keys.all { it.flags and KeyEvent.FLAG_SOFT_KEYBOARD != 0 })
        assertEquals("", ime.connection.editable.toString())
        ime.onDestroy()
    }

    @Test fun restartingInputDoesNotReplaceTheWordAlreadyBeingComposed() {
        val ime = Robolectric.buildService(EditorIme::class.java).create().get()
        val info = EditorInfo().apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT }
        ime.onStartInput(info, false)
        buttons(ime.onCreateInputView()).single { it.text == "h" }.performClick()
        assertEquals("h", ime.connection.editable.toString())
        ime.onStartInput(info, true)
        buttons(ime.onCreateInputView()).single { it.text == "e" }.performClick()
        assertEquals("he", ime.connection.editable.toString())
        ime.onDestroy()
    }
}
