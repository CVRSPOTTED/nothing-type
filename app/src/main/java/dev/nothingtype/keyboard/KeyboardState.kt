package dev.nothingtype.keyboard

enum class Layer { LETTERS, SYMBOLS, SYMBOLS_MORE, EMOJI, NUMBER, PHONE }

/** Pure state: independent of Android so transitions can be unit tested. */
class KeyboardState(var language: Language = Language.EN) {
    internal var importedLayout: ImportedLayout? = null
    var layer = Layer.LETTERS
    var signedNumber = false
    var decimalNumber = false
    var compactRussian = false
    private var manualShift = false
    var shift = false
    var capsLock = false

    internal val layoutTitle: String get() = if (language == Language.CUSTOM) importedLayout?.title.orEmpty() else language.title
    internal val languageTag: String get() = if (language == Language.CUSTOM) importedLayout?.tag ?: "und" else language.tag

    fun toggleLanguage(enabled: List<Language> = listOf(Language.EN, Language.RU)) {
        language = enabled[(enabled.indexOf(language) + 1) % enabled.size]
    }

    fun afterCommit() {
        manualShift = false
        if (!capsLock) shift = false
    }

    fun updateAutomaticShift(enabled: Boolean) {
        if (!manualShift && !capsLock) shift = enabled && language.usesCase && language != Language.CUSTOM
    }

    fun toggleShift() { manualShift = true; capsLock = false; shift = !shift }
    fun lockCaps() { manualShift = true; capsLock = !capsLock; shift = capsLock }

    internal fun importedAlternatives(row: Int, column: Int): List<String> =
        importedLayout?.rows?.getOrNull(row)?.getOrNull(column)?.alternatives.orEmpty()

    private fun letters(): List<List<String>> {
        if (language == Language.CUSTOM) {
            importedLayout?.let { return it.rows.map { row -> row.map { key -> if (shift) key.shifted else key.base } } }
        }
        if (language == Language.HI) return if (shift) hindiShift else hindi
        if (language == Language.AR) return if (shift) arabicShift else arabic
        val rows = when (language) {
            Language.RU -> if (compactRussian) listOf("йцукенгшщзх", "фывапролджэ", "ячсмитьбю")
                else listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбюё")
            Language.FR -> listOf("azertyuiop", "qsdfghjklm", "wxcvbn")
            Language.DE -> listOf("qwertzuiopü", "asdfghjklöä", "yxcvbnmß")
            Language.TR -> listOf("qwertyuıopğü", "asdfghjklşi", "zxcvbnmöç")
            Language.ES -> listOf("qwertyuiop", "asdfghjklñ", "zxcvbnm")
            else -> listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        }
        return rows.map { row -> row.map { char -> if (shift) upper(char.toString()) else char.toString() } }
    }

    internal fun upper(value: String): String =
        if (language == Language.DE && value == "ß") "ẞ" else value.uppercase(language.locale)

    fun rows(): List<List<String>> = when (layer) {
        Layer.LETTERS -> letters()
        Layer.SYMBOLS -> listOf("1234567890", "@#€%&*()-+", "!?/:;\"'=_").map { it.map(Char::toString) }
        Layer.SYMBOLS_MORE -> listOf(
            listOf("~", "\u0060", "|", "•", "√", "π", "÷", "×", "§", "∆"),
            listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\"),
            listOf("%", "©", "®", "™", "✓", "[", "]", "<", ">", "$")
        )
        Layer.NUMBER -> listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOfNotNull("-".takeIf { signedNumber }, "0", ".".takeIf { decimalNumber }))
        Layer.PHONE -> listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("+", "0", "#", "*"))
        Layer.EMOJI -> emptyList()
    }

    private companion object {
        val hindi = listOf("ौ ै ा ी ू ब ह ग द ज ड ़", "ो े ् ि ु प र क त च ट", "ं म न व ल स य").map { it.split(' ') }
        val hindiShift = listOf("औ ऐ आ ई ऊ भ ङ घ ध झ ढ ञ", "ओ ए अ इ उ फ ऋ ख थ छ ठ", "ँ ण ः श ळ ष य़").map { it.split(' ') }
        val arabic = listOf("ض ص ث ق ف غ ع ه خ ح ج د", "ش س ي ب ل ا ت ن م ك ط", "ئ ء ؤ ر لا ى ة و ز ظ ذ").map { it.split(' ') }
        val arabicShift = listOf("َ ً ُ ٌ لإ إ ‘ ÷ × ؛ < >", "ِ ٍ ] [ لأ أ ـ ، / : \"", "~ ْ } { لآ آ ’ , . ؟ ّ").map { it.split(' ') }
    }
}
