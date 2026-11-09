package dev.nothingtype.settings

import android.content.Context
import android.graphics.Color
import dev.nothingtype.keyboard.Language
import dev.nothingtype.keyboard.ImportedLayout
import dev.nothingtype.keyboard.LayoutKey
import dev.nothingtype.keyboard.WordText
import org.json.JSONArray
import org.json.JSONObject

enum class Palette(val canvas: Int, val key: Int, val ink: Int, val accent: Int) {
    DARK(Color.BLACK, Color.rgb(32, 32, 32), Color.rgb(242, 242, 242), Color.WHITE),
    LIGHT(Color.rgb(235, 235, 232), Color.WHITE, Color.rgb(22, 22, 22), Color.rgb(30, 30, 30))
}

enum class LanguageSwitch { SPACE_HOLD, BUTTON }

class Preferences(context: Context) {
    private val data = context.getSharedPreferences("keyboard", Context.MODE_PRIVATE)

    /** Explicitly entered words only. Never learn from an editor or microphone. */
    var personalWords: Set<String>
        get() = data.getStringSet("personal_words", emptySet()).orEmpty().toSet()
        set(value) {
            val words = value.asSequence().map { WordText.canonical(it.trim()) }
                .filter { word -> word.length in 2..48 && Language.entries.any { it.hasDictionary && WordText.isWord(word, it) } }
                .distinct().take(500).toSet()
            data.edit().putStringSet("personal_words", words).apply()
        }

    var palette: Palette
        get() = if (data.getString("palette", null) == "LIGHT") Palette.LIGHT else Palette.DARK
        set(value) {
            // Preserve the former SIGNAL accent when the user changes the base palette.
            data.edit().putBoolean("red_enter", redEnter).putString("palette", value.name).apply()
        }

    var redEnter: Boolean
        get() = data.getBoolean("red_enter", data.getString("palette", null) == "SIGNAL")
        set(value) { data.edit().putBoolean("red_enter", value).apply() }

    var keyboardFont: KeyboardFont
        get() = KeyboardFont.entries.firstOrNull { it.name == data.getString("font_family", null) }
            ?: KeyboardFont.ROBOTO
        set(value) { data.edit().putString("font_family", value.name).apply() }
    var enabledLanguages: List<Language>
        get() {
            val names = data.getStringSet("enabled_languages", setOf("EN", "RU")).orEmpty()
            return Language.entries.filter {
                it.name in names && (it != Language.CUSTOM || data.contains("imported_layout"))
            }.ifEmpty { listOf(Language.EN) }
        }
        set(value) {
            require(value.isNotEmpty())
            val editor = data.edit().putStringSet("enabled_languages", value.map { it.name }.toSet())
            if (language !in value) editor.putString("language", value.first().name)
            editor.apply()
        }

    var language: Language
        get() {
            val name = data.getString("language", "EN")
            val enabled = enabledLanguages
            return enabled.firstOrNull { it.name == name } ?: enabled.first()
        }
        set(value) { data.edit().putString("language", value.name).apply() }

    internal fun layoutTitle(language: Language): String =
        if (language == Language.CUSTOM) data.getString("imported_layout_title", "").orEmpty() else language.title

    internal fun loadImportedLayout(): ImportedLayout? {
        val source = data.getString("imported_layout", null) ?: return null
        return runCatching {
            val json = JSONObject(source)
            val rows = json.getJSONArray("rows")
            ImportedLayout(layoutTitle(Language.CUSTOM), json.getString("tag"), List(rows.length()) { rowIndex ->
                val row = rows.getJSONArray(rowIndex)
                List(row.length()) { columnIndex ->
                    val key = row.getJSONArray(columnIndex)
                    LayoutKey(key.getString(0), key.getString(1), (2 until key.length()).map(key::getString))
                }
            })
        }.getOrNull()
    }

    internal fun importLayout(layout: ImportedLayout) {
        val rows = JSONArray()
        layout.rows.forEach { row ->
            val keys = JSONArray()
            row.forEach { key ->
                keys.put(JSONArray(listOf(key.base, key.shifted) + key.alternatives))
            }
            rows.put(keys)
        }
        data.edit()
            .putString("imported_layout", JSONObject().put("tag", layout.tag).put("rows", rows).toString())
            .putString("imported_layout_title", layout.title)
            .putStringSet("enabled_languages", (enabledLanguages + Language.CUSTOM).map { it.name }.toSet())
            .apply()
    }

    internal fun removeImportedLayout() {
        val remaining = (enabledLanguages - Language.CUSTOM).ifEmpty { listOf(Language.EN) }
        val next = language.takeIf { it in remaining } ?: remaining.first()
        data.edit().remove("imported_layout").remove("imported_layout_title")
            .putStringSet("enabled_languages", remaining.map { it.name }.toSet())
            .putString("language", next.name).apply()
    }
    var languageSwitch: LanguageSwitch
        get() = LanguageSwitch.entries.firstOrNull { it.name == data.getString("language_switch", null) }
            ?: LanguageSwitch.SPACE_HOLD
        set(value) { data.edit().putString("language_switch", value.name).apply() }
    var keyHeight: Int
        get() = data.getInt("height", 48).coerceIn(40, 60)
        set(value) { data.edit().putInt("height", value.coerceIn(40, 60)).apply() }
    var radius: Int
        get() = data.getInt("radius", 12).coerceIn(0, 24)
        set(value) { data.edit().putInt("radius", value.coerceIn(0, 24)).apply() }
    var numbers: Boolean
        get() = data.getBoolean("numbers", true)
        set(value) { data.edit().putBoolean("numbers", value).apply() }
    var compactRussian: Boolean
        get() = data.getBoolean("compact_russian", true)
        set(value) { data.edit().putBoolean("compact_russian", value).apply() }
    var haptics: Boolean
        get() = data.getBoolean("haptics", true)
        set(value) { data.edit().putBoolean("haptics", value).apply() }
    var offlineVoiceOnly: Boolean
        get() = data.getBoolean("offline_voice_only", false)
        set(value) { data.edit().putBoolean("offline_voice_only", value).apply() }

    private fun number(key: String, default: Int, range: IntRange) = data.getInt(key, default).coerceIn(range)
    private fun save(key: String, value: Int, range: IntRange) { data.edit().putInt(key, value.coerceIn(range)).apply() }
    var navigationReserve: Int
        get() = if (data.contains("bottom_reserve")) number("bottom_reserve", 56, 0..180)
            else number("nav_reserve", 48, 0..80) + number("lift", 8, 0..100)
        set(value) { data.edit().putInt("bottom_reserve", value.coerceIn(0, 180)).remove("nav_reserve").remove("lift").apply() }
    var widthPercent: Int
        get() = number("width", 100, 75..100)
        set(value) = save("width", value, 75..100)
    var sideMargin: Int
        get() = number("side", 0, 0..24)
        set(value) = save("side", value, 0..24)
    var alignment: Int
        get() = number("alignment", 0, -1..1)
        set(value) = save("alignment", value, -1..1)
    var landscapeHeight: Int
        get() = number("land_height", 36, 30..48)
        set(value) = save("land_height", value, 30..48)
    var keyGap: Int
        get() = number("key_gap", 2, 0..4)
        set(value) = save("key_gap", value, 0..4)
    var fontSize: Int
        get() = number("font_size", 18, 14..24)
        set(value) = save("font_size", value, 14..24)
    var toolbar: Boolean
        get() = data.getBoolean("toolbar", true)
        set(value) { data.edit().putBoolean("toolbar", value).apply() }
    var instantInput: Boolean
        get() = data.getBoolean("instant", true)
        set(value) { data.edit().putBoolean("instant", value).apply() }
    var suggestions: Boolean
        get() = data.getBoolean("suggestions", true)
        set(value) { data.edit().putBoolean("suggestions", value).apply() }
    var autocorrect: Boolean
        get() = data.getBoolean("autocorrect", true)
        set(value) { data.edit().putBoolean("autocorrect", value).apply() }
    var ambient: Boolean
        get() = data.getBoolean("ambient", false)
        set(value) { data.edit().putBoolean("ambient", value).apply() }
    fun resetPlacement() {
        data.edit().remove("bottom_reserve").remove("lift").remove("nav_reserve").remove("width").remove("side")
            .remove("alignment").remove("land_height").apply()
    }
}
