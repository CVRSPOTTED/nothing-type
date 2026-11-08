package dev.nothingtype.keyboard

import android.app.Activity
import dev.nothingtype.settings.Preferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExpandedDictionaryTest {
    private val context = Robolectric.buildActivity(Activity::class.java).setup().get()
    @Test fun bundledDictionariesCompleteUncommonWordsAndPreserveManualTerms() {
        val entries = Language.entries.filter(Language::hasDictionary).associateWith { language ->
            context.assets.open("dictionaries/${language.name.lowercase()}.txt").bufferedReader().useLines { lines ->
                lines.map { it.substringBefore(' ').removePrefix("\uFEFF") }.toList()
            }
        }
        assertTrue(entries.values.all { it.size == 10000 })
        val suggestions = Suggestions(entries, mapOf(Language.EN to listOf("nothingtype")))
        assertTrue(suggestions.complete("nothingt", Language.EN).contains("nothingtype"))
        assertNull(suggestions.correction("nothingtype", Language.EN))
        assertTrue(suggestions.complete("elephan", Language.EN).contains("elephant"))
        assertTrue(suggestions.complete("университе", Language.RU).contains("университет"))
        assertEquals("hello", suggestions.correction("helllo", Language.EN))
        assertEquals("привет", suggestions.correction("превет", Language.RU))
        assertEquals("offline", suggestions.correction("offlne", Language.EN))
    }
    @Test fun manualWordsAreNormalizedBoundedAndSurviveReload() {
        Preferences(context).personalWords = setOf("NothingType", "Ёжик", "two words", "secret123", "")
        assertEquals(setOf("nothingtype", "ёжик"), Preferences(context).personalWords)
        Preferences(context).personalWords = emptySet()
        assertTrue(Preferences(context).personalWords.isEmpty())
    }
    @Test fun compactRussianKeepsTheCommonRowsWider() {
        val compact = KeyboardState(Language.RU).apply { compactRussian = true }.rows()
        assertEquals(listOf(11, 11, 9), compact.map { it.size })
        assertEquals(31, compact.flatten().toSet().size)
        assertFalse("ё" in compact.flatten())
        assertFalse("ъ" in compact.flatten())
    }
}
