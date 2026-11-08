package dev.nothingtype.keyboard

import org.junit.Assert.*
import org.junit.Test

class KlcLayoutTest {
    private val source = """
        KBD TEST "Custom French"
        LOCALENAME "fr-FR"
        SHIFTSTATE
        0
        1
        2
        6
        LAYOUT // unordered scan codes and optional layers
        20 D 1 d D
        12 E 1 e E -1 20ac
        11 Z 1 z Z
        10 A 1 a A
        02 1 1 00e9 1
        1e Q 1 q Q
        1f S 1 s S
        21 F 1 f F
        2c W 1 w W
        2d X 1 x X
        2e C 1 c C
        2f V 1 v V
        30 B 1 b B
        ENDKBD
    """.trimIndent()

    @Test fun parsesWindowsEncodingAndUsesPhysicalOrderAndShiftLayers() {
        val bytes = ("\uFEFF" + source).toByteArray(Charsets.UTF_16LE)
        val layout = KlcLayout.read(bytes.inputStream())
        val state = KeyboardState(Language.CUSTOM).apply { importedLayout = layout }
        assertEquals("Custom French", state.layoutTitle)
        assertEquals("fr-FR", state.languageTag)
        assertEquals(listOf("a", "z", "e"), state.rows()[1])
        assertEquals(listOf("€"), state.importedAlternatives(1, 2))
        state.updateAutomaticShift(true)
        assertEquals("é", state.rows()[0][0])
        state.toggleShift()
        assertEquals("1", state.rows()[0][0])
        assertEquals(listOf("A", "Z", "E"), state.rows()[1])
    }

    @Test fun rejectsUnsupportedTruncatedAndOversizedFiles() {
        listOf("<html>404</html>".toByteArray(), source.substringBefore("ENDKBD").toByteArray(),
            ByteArray(256 * 1024 + 1)).forEach {
            assertThrows(IllegalArgumentException::class.java) { KlcLayout.read(it.inputStream()) }
        }
    }

    @Test fun builtinLayoutsKeepLocaleSpecificCharactersAndShiftLayers() {
        val french = KeyboardState(Language.FR)
        assertEquals("azertyuiop", french.rows().first().joinToString(""))
        assertTrue("ß" in KeyboardState(Language.DE).rows().flatten())
        assertTrue("ñ" in KeyboardState(Language.ES).rows().flatten())
        val turkish = KeyboardState(Language.TR).apply { toggleShift() }
        assertTrue(turkish.rows().flatten().containsAll(listOf("I", "İ", "Ğ", "Ş")))
        for ((language, base, shifted) in listOf(Triple(Language.AR, "ض", "أ"), Triple(Language.HI, "क", "ख"))) {
            val state = KeyboardState(language)
            state.updateAutomaticShift(true)
            assertTrue(base in state.rows().flatten())
            state.toggleShift()
            assertTrue(shifted in state.rows().flatten())
        }
    }
}
