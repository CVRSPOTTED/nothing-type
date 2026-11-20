package dev.nothingtype.settings

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class KeyboardFontsTest {
    @Test fun readsActualFontWhenZipStartsWithMacMetadata() {
        val font = byteArrayOf(0, 1, 0, 0, 0, 1)
        val archive = ByteArrayOutputStream()
        ZipOutputStream(archive).use { zip ->
            zip.putNextEntry(ZipEntry("__MACOSX/Font/._Ndot.ttf"))
            zip.write(byteArrayOf(0, 5, 22, 7))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("Font/Ndot.ttf"))
            zip.write(font)
            zip.closeEntry()
        }

        val extracted = archive.toByteArray().inputStream().use(KeyboardFonts::readFont)

        assertArrayEquals(font, extracted)
    }
}
