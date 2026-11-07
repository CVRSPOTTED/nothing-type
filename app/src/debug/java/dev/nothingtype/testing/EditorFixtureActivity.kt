package dev.nothingtype.testing

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout

/** Editor hosts used only by the device smoke test. */
class EditorFixtureActivity : Activity() {
    lateinit var editor: EditText
    lateinit var privateEditor: EditText
    lateinit var numberEditor: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editor = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
        }
        privateEditor = AutoCompleteTextView(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        numberEditor = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(editor)
            addView(privateEditor)
            addView(numberEditor)
        })
    }
}
