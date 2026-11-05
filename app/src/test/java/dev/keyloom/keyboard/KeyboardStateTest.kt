package dev.keyloom.keyboard

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
}
