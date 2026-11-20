package dev.nothingtype.settings

import dev.nothingtype.R

internal enum class GlyphMode(val label: Int) {
    SYMBOLS(R.string.glyph_mode_symbols),
    ANIMATION(R.string.glyph_mode_animation);

    fun effect(selected: GlyphEffect) = if (this == SYMBOLS) GlyphEffect.SPARKS else selected
}

internal enum class GlyphEffect(val label: Int, val durationMs: Long) {
    PULSE(R.string.glyph_mode_pulse, 540),
    SCANNER(R.string.glyph_mode_scanner, 530),
    SPARKS(R.string.glyph_mode_sparks, 343);

    companion object {
        val animations = listOf(PULSE, SCANNER)
    }
}

internal class GlyphBrightness(percent: Int, mode: GlyphMode) {
    val peak = (percent.coerceIn(range) * 255 + 50) / 100
    private val dim = peak * (if (mode == GlyphMode.SYMBOLS) 26 else 55) / 100
    private val bright = peak * (if (mode == GlyphMode.SYMBOLS) 47 else 100) / 100

    fun effect(level: Byte): Int = when (level.toInt()) {
        1 -> dim.coerceAtLeast(1)
        2 -> bright.coerceAtLeast(1)
        else -> 0
    }

    companion object {
        val range = 1..100
        const val DEFAULT_PERCENT = 63
    }
}
