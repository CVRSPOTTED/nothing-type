package dev.keyloom.keyboard

import org.junit.Assert.*
import org.junit.Test

class SuggestionsTest {
    private val suggestions = Suggestions()
    @Test fun russianPrefixCompletes() { assertTrue(suggestions.complete("прив", Language.RU).contains("привет")) }
    @Test fun englishPrefixCompletes() { assertTrue(suggestions.complete("hel", Language.EN).contains("hello")) }
    @Test fun respectsTitleCase() { assertTrue(suggestions.complete("Прив", Language.RU).contains("Привет")) }
    @Test fun respectsCapsLock() { assertTrue(suggestions.complete("HEL", Language.EN).contains("HELLO")) }
    @Test fun noPredictionsForEmptyInput() { assertTrue(suggestions.complete("", Language.RU).isEmpty()) }
    @Test fun noPredictionsForNonWords() { assertTrue(suggestions.complete("a@", Language.EN).isEmpty()) }
    @Test fun noResultsAcrossLanguages() { assertTrue(suggestions.complete("при", Language.EN).isEmpty()) }
    @Test fun candidatesAreBoundedAndUnique() {
        val words = suggestions.complete("п", Language.RU)
        assertTrue(words.size <= 3); assertEquals(words.size, words.distinct().size)
    }
    @Test fun doesNotSuggestShorterWords() { assertTrue(suggestions.complete("hello", Language.EN).all { it.length > 5 }) }
    @Test fun unknownPrefixDoesNotInventWords() { assertTrue(suggestions.complete("zzzz", Language.EN).isEmpty()) }
}
