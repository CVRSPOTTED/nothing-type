package dev.nothingtype.ime

import dev.nothingtype.settings.GlyphBrightness
import dev.nothingtype.settings.GlyphEffect
import dev.nothingtype.settings.GlyphMode
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphEffectsTest {
    @Test fun eachEffectMovesAcrossSeveralFramesAndEnds() {
        val frames = mutableListOf<ByteArray>()
        val effects = GlyphEffects()
        for (mode in GlyphEffect.entries) {
            effects.clear()
            effects.tap(100)
            val first = checkNotNull(effects.levels(mode, 150))
            val second = checkNotNull(effects.levels(mode, 260))
            assertTrue("${mode.name} must light some pixels", first.any { it > 0 })
            assertTrue(first.all { it in 0..2 })
            assertFalse("${mode.name} must change as the animation runs", first.contentEquals(second))
            frames.add(second)
            assertNull(effects.levels(mode, 100 + mode.durationMs))
        }
        assertFalse(frames[0].contentEquals(frames[1]))
        assertFalse(frames[1].contentEquals(frames[2]))
        assertFalse(frames[0].contentEquals(frames[2]))
    }

    @Test fun rapidTapsCanOverlapAndClearingDropsTheirHistory() {
        val effects = GlyphEffects()
        effects.tap(0)
        effects.tap(150)
        val overlapping = checkNotNull(effects.levels(GlyphEffect.PULSE, 200))
        effects.clear()
        assertNull(effects.levels(GlyphEffect.PULSE, 200))
        effects.tap(150)
        val latestOnly = checkNotNull(effects.levels(GlyphEffect.PULSE, 200))
        assertTrue(overlapping.count { it > 0 } > latestOnly.count { it > 0 })

        effects.clear()
        effects.tap(150)
        assertArrayEquals(latestOnly, effects.levels(GlyphEffect.PULSE, 200))
    }

    @Test fun sparksAreReservedForSymbolsAndBrightnessScalesPixelsInBothModes() {
        assertEquals(GlyphEffect.SPARKS, GlyphMode.SYMBOLS.effect(GlyphEffect.SCANNER))
        assertEquals(GlyphEffect.SCANNER, GlyphMode.ANIMATION.effect(GlyphEffect.SCANNER))
        assertFalse(GlyphEffect.SPARKS in GlyphEffect.animations)
        for (mode in GlyphMode.entries) {
            val low = GlyphBrightness(25, mode)
            val high = GlyphBrightness(75, mode)
            assertTrue(low.peak < high.peak)
            assertEquals(0, high.effect(0))
            assertTrue(low.effect(1) < high.effect(1))
            assertTrue(low.effect(2) < high.effect(2))
            assertTrue(high.effect(1) < high.effect(2))
        }
        val symbols = GlyphBrightness(100, GlyphMode.SYMBOLS)
        assertTrue(symbols.effect(2) < symbols.peak)
        assertEquals(255, GlyphBrightness(100, GlyphMode.ANIMATION).effect(2))
    }
}
