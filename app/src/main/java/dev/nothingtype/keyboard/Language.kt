package dev.nothingtype.keyboard

import java.util.Locale

enum class Language(val title: String, val tag: String, val space: String) {
    EN("English", "en-US", "space"),
    RU("Русский", "ru-RU", "пробел"),
    FR("Français", "fr-FR", "espace"),
    DE("Deutsch", "de-DE", "Leerzeichen"),
    HI("हिन्दी", "hi-IN", "स्पेस"),
    AR("العربية", "ar", "مسافة"),
    TR("Türkçe", "tr-TR", "boşluk"),
    ES("Español", "es-ES", "espacio"),
    CUSTOM("", "und", "space");

    val locale: Locale get() = Locale.forLanguageTag(tag)
    val hasDictionary: Boolean get() = this == EN || this == RU
    val usesCase: Boolean get() = this != AR && this != HI
}
