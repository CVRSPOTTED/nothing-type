package dev.keyloom.keyboard

enum class Language { EN, RU }
enum class Layer { LETTERS, SYMBOLS, EMOJI, NUMBER, PHONE }

/** Pure state: independent of Android so transitions can be unit tested. */
class KeyboardState(var language: Language = Language.EN) {
    var layer = Layer.LETTERS
    var shift = false
    var capsLock = false

    fun toggleLanguage() { language = if (language == Language.EN) Language.RU else Language.EN }
    fun afterCommit() { if (!capsLock) shift = false }
    fun toggleShift() { capsLock = false; shift = !shift }
    fun lockCaps() { capsLock = !capsLock; shift = capsLock }
    fun rows(): List<List<String>> = when (layer) {
        Layer.LETTERS -> (if (language == Language.EN)
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        else listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбюё"))
            .map { row -> row.map { if (shift) it.uppercaseChar().toString() else it.toString() } }
        Layer.SYMBOLS -> listOf("1234567890", "@#€%&*()-+", "!?/:;\"'=_").map { it.map(Char::toString) }
        Layer.NUMBER -> listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("-", "0", "."))
        Layer.PHONE -> listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("+", "0", "#", "*"))
        Layer.EMOJI -> emptyList()
    }
}
