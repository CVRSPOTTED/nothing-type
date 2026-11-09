package dev.nothingtype.keyboard

import java.lang.Character.UnicodeScript
import java.text.Normalizer

/** Text rules shared by composition, lookup and explicitly saved words. */
internal object WordText {
    private val word = Regex("[\\p{L}][\\p{L}\\p{M}]*(?:['’][\\p{L}][\\p{L}\\p{M}]*)*")

    fun normalize(text: String, language: Language): String =
        canonical(text.replace('’', '\'').lowercase(language.locale))

    fun canonical(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)

    fun isWord(text: String, language: Language): Boolean {
        val script = script(language)
        return word.matches(text) && (script == UnicodeScript.LATIN || text.none(::isApostrophe)) &&
            text.codePoints().allMatch { !Character.isLetter(it) || UnicodeScript.of(it) == script }
    }

    fun canAppend(text: String, language: Language, last: Char?): Boolean {
        val script = script(language)
        if (text.length == 1 && isApostrophe(text[0])) {
            return script == UnicodeScript.LATIN && last != null && !isApostrophe(last)
        }
        if ((last == null || isApostrophe(last)) && text.firstOrNull()?.isLetter() != true) return false
        return text.isNotEmpty() && text.codePoints().allMatch {
            if (Character.isLetter(it)) UnicodeScript.of(it) == script else isMark(it)
        }
    }

    private fun script(language: Language): UnicodeScript = when (language) {
        Language.RU -> UnicodeScript.CYRILLIC
        Language.HI -> UnicodeScript.DEVANAGARI
        Language.AR -> UnicodeScript.ARABIC
        else -> UnicodeScript.LATIN
    }

    private fun isApostrophe(char: Char) = char == '\'' || char == '’'

    private fun isMark(codePoint: Int) = when (Character.getType(codePoint)) {
        Character.NON_SPACING_MARK.toInt(), Character.COMBINING_SPACING_MARK.toInt(),
        Character.ENCLOSING_MARK.toInt() -> true
        else -> false
    }
}
