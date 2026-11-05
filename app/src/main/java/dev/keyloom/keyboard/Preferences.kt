package dev.keyloom.keyboard

import android.content.Context
import android.graphics.Color

enum class Palette(val title: String, val canvas: Int, val key: Int, val ink: Int, val accent: Int) {
    MONO("Mono", Color.BLACK, Color.rgb(32, 32, 32), Color.rgb(242, 242, 242), Color.WHITE),
    SIGNAL("Signal", Color.BLACK, Color.rgb(32, 32, 32), Color.rgb(242, 242, 242), Color.rgb(215, 45, 40)),
    LIGHT("Light", Color.rgb(235, 235, 232), Color.WHITE, Color.rgb(22, 22, 22), Color.rgb(30, 30, 30))
}

class Preferences(context: Context) {
    private val data = context.getSharedPreferences("keyboard", Context.MODE_PRIVATE)
    init {
        // One-time visual migration; keep language, key dimensions and input preferences.
        if (!data.getBoolean("design_v2", false)) {
            data.edit().putString("palette", "MONO").putBoolean("design_v2", true).apply()
        }
    }
    var palette: Palette
        get() = Palette.entries.firstOrNull { it.name == data.getString("palette", "MONO") } ?: Palette.MONO
        set(value) { data.edit().putString("palette", value.name).apply() }
    var language: Language
        get() = if (data.getString("language", "EN") == "RU") Language.RU else Language.EN
        set(value) { data.edit().putString("language", value.name).apply() }
    var keyHeight: Int
        get() = data.getInt("height", 48).coerceIn(40, 60)
        set(value) { data.edit().putInt("height", value.coerceIn(40, 60)).apply() }
    var radius: Int
        get() = data.getInt("radius", 12).coerceIn(0, 24)
        set(value) { data.edit().putInt("radius", value.coerceIn(0, 24)).apply() }
    var numbers: Boolean
        get() = data.getBoolean("numbers", true)
        set(value) { data.edit().putBoolean("numbers", value).apply() }
    var haptics: Boolean
        get() = data.getBoolean("haptics", true)
        set(value) { data.edit().putBoolean("haptics", value).apply() }
    var sound: Boolean
        get() = data.getBoolean("sound", false)
        set(value) { data.edit().putBoolean("sound", value).apply() }

    private fun number(key: String, default: Int, range: IntRange) = data.getInt(key, default).coerceIn(range)
    private fun save(key: String, value: Int, range: IntRange) { data.edit().putInt(key, value.coerceIn(range)).apply() }
    var bottomLift: Int
        get() = number("lift", 8, 0..100)
        set(value) = save("lift", value, 0..100)
    var navigationReserve: Int
        get() = number("nav_reserve", 48, 0..80)
        set(value) = save("nav_reserve", value, 0..80)
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
    var ambient: Boolean
        get() = data.getBoolean("ambient", false)
        set(value) { data.edit().putBoolean("ambient", value).apply() }
    fun resetPlacement() {
        data.edit().remove("lift").remove("nav_reserve").remove("width").remove("side")
            .remove("alignment").remove("land_height").apply()
    }
}
