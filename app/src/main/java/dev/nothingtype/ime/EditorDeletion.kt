package dev.nothingtype.ime

import android.icu.text.BreakIterator
import android.os.Build
import android.view.inputmethod.InputConnection
import java.util.Locale

/** Deletes what the user sees as one character, including a whole emoji sequence. */
internal object EditorDeletion {
    private const val CONTEXT = 2048
    fun delete(connection: InputConnection): Boolean {
        val surrounding = if (Build.VERSION.SDK_INT >= 31) connection.getSurroundingText(CONTEXT, 0, 0) else null
        val before = if (Build.VERSION.SDK_INT >= 31 && surrounding != null) {
            if (surrounding.selectionStart != surrounding.selectionEnd) return connection.commitText("", 1)
            if (surrounding.selectionStart !in 0..surrounding.text.length) return false
            surrounding.text.subSequence(0, surrounding.selectionStart).toString()
        } else {
            if (!connection.getSelectedText(0).isNullOrEmpty()) return connection.commitText("", 1)
            connection.getTextBeforeCursor(CONTEXT, 0)?.toString() ?: return false
        }
        if (before.isEmpty()) return false
        val breaks = BreakIterator.getCharacterInstance(Locale.ROOT)
        breaks.setText(before)
        val start = breaks.preceding(before.length)
        if (start == BreakIterator.DONE) return false
        // A very long cluster may begin outside the bounded context; let the editor handle it.
        if (start == 0 && before.length == CONTEXT) return false
        return connection.deleteSurroundingText(before.length - start, 0)
    }
}
