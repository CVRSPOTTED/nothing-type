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
    @Test fun allNewLexiconsCompleteAndCorrectInTheirOwnLanguage() {
        val examples = listOf(
            Triple(Language.FR, "bonjjour", "bonjour"),
            Triple(Language.DE, "halllo", "hallo"),
            Triple(Language.HI, "नममस्ते", "नमस्ते"),
            Triple(Language.AR, "مررحبا", "مرحبا"),
            Triple(Language.TR, "merhaaba", "merhaba"),
            Triple(Language.ES, "graacias", "gracias")
        )
        for ((language, typo, word) in examples) {
            val name = language.name.lowercase(java.util.Locale.ROOT)
            val words = context.assets.open("dictionaries/" + name + ".txt").bufferedReader().readLines()
            assertEquals(language.name, 10000, words.toSet().size)
            assertTrue(language.name, words.all { WordText.isWord(it, language) })
            val suggestions = Suggestions(mapOf(language to words))
            assertTrue(language.name, word in suggestions.complete(word.dropLast(1), language))
            assertEquals(language.name, word, suggestions.correction(typo, language))
        }
    }

    @Test fun acceptsCombiningMarksAndUsesTurkishCasing() {
        assertFalse(WordText.canAppend("ा", Language.HI, null))
        assertTrue(WordText.canAppend("ा", Language.HI, 'क'))
        assertTrue(WordText.canAppend("َ", Language.AR, 'ب'))
        assertEquals("ışık", WordText.normalize("IŞIK", Language.TR))
        val suggestions = Suggestions(personalWords = mapOf(
            Language.TR to listOf("İstanbulca"),
            Language.FR to listOf("l’équipe")
        ))
        assertTrue("İstanbulca" in suggestions.complete("İstan", Language.TR))
        assertTrue("l'équipe" in suggestions.complete("l’équi", Language.FR))
    }

    @Test fun manualWordsKeepSpellingAndSurviveReload() {
        Preferences(context).personalWords = setOf("NothingType", "Ёжик", "İstanbul", "équipe", "नमस्ते", "مَرْحَبًا", "two words", "secret123", "")
        assertEquals(setOf("NothingType", "Ёжик", "İstanbul", "équipe", "नमस्ते", "مَرْحَبًا"), Preferences(context).personalWords)
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
