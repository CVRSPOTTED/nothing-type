package dev.nothingtype.keyboard

import org.junit.Assert.*
import org.junit.Test

class KeyboardStateTest {
    @Test fun russianLayoutContainsEveryLetter() {
        val keys = KeyboardState(Language.RU).rows().flatten()
        assertEquals(33, keys.size)
        assertEquals(33, keys.toSet().size)
        assertTrue(keys.containsAll(listOf("ё", "й", "ъ", "ь")))
    }
    @Test fun englishLayoutHas26UniqueLetters() {
        val keys = KeyboardState().rows().flatten()
        assertEquals(('a'..'z').map(Char::toString).toSet(), keys.toSet())
    }
    @Test fun shiftResetsAfterOneCharacter() {
        val state = KeyboardState()
        state.toggleShift()
        assertEquals("Q", state.rows()[0][0])
        state.afterCommit()
        assertEquals("q", state.rows()[0][0])
    }
    @Test fun capsLockSurvivesInputAndCanBeDisabled() {
        val state = KeyboardState(Language.RU)
        state.lockCaps(); state.afterCommit()
        assertEquals("Й", state.rows()[0][0])
        state.toggleShift()
        assertFalse(state.capsLock)
        assertFalse(state.shift)
    }
    @Test fun languageRoundTripPreservesCaps() {
        val state = KeyboardState()
        state.lockCaps(); state.toggleLanguage()
        assertEquals(Language.RU, state.language)
        assertEquals("Й", state.rows()[0][0])
        state.toggleLanguage()
        assertEquals(Language.EN, state.language)
    }
    @Test fun numericLayoutContainsAllDigits() {
        val state = KeyboardState().apply { layer = Layer.NUMBER }
        assertTrue(state.rows().flatten().containsAll((0..9).map(Int::toString)))
        assertFalse(state.rows().flatten().contains("q"))
    }
    @Test fun emojiPickerReplacesLetterRows() {
        val state = KeyboardState().apply { layer = Layer.EMOJI }
        assertTrue(state.rows().isEmpty())
    }
    @Test fun allPrintableAsciiCharactersAreReachableForPasswords() {
        val state = KeyboardState()
        val characters = mutableSetOf(' ', ',', '.')
        for (layer in listOf(Layer.LETTERS, Layer.SYMBOLS, Layer.SYMBOLS_MORE)) {
            state.layer = layer
            characters.addAll(state.rows().flatten().joinToString("").toSet())
        }
        characters.addAll(('A'..'Z'))
        assertTrue((' '..'~').all { it in characters })
    }
    @Test fun numberSeparatorsFollowIndependentEditorFlags() {
        val state = KeyboardState().apply { layer = Layer.NUMBER }
        for (signed in listOf(false, true)) for (decimal in listOf(false, true)) {
            state.signedNumber = signed
            state.decimalNumber = decimal
            val keys = state.rows().flatten()
            assertEquals(signed, "-" in keys)
            assertEquals(decimal, "." in keys)
        }
    }

    @Test fun automaticShiftRespectsManualOverrideUntilInput() {
        val state = KeyboardState()
        state.updateAutomaticShift(true)
        assertEquals("Q", state.rows()[0][0])
        state.toggleShift()
        state.updateAutomaticShift(true)
        assertEquals("q", state.rows()[0][0])
        state.afterCommit()
        state.updateAutomaticShift(true)
        assertEquals("Q", state.rows()[0][0])
        state.lockCaps()
        state.updateAutomaticShift(false)
        assertTrue(state.shift)
    }
}
