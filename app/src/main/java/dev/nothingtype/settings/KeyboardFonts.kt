package dev.nothingtype.settings

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.system.Os
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.File
import java.util.zip.ZipInputStream

enum class KeyboardFont(val title: String) {
    ROBOTO("Roboto"), GEIST("Geist"), NDOT("Ndot")
}

internal object KeyboardFonts {
    private const val MAX_BYTES = 4 * 1024 * 1024
    private val bundled = mutableMapOf<KeyboardFont, Typeface>()
    private var imported: Pair<File, Typeface>? = null

    private fun localFile(context: Context) = File(context.filesDir, "ndot-font")

    @Synchronized
    fun hasNdot(context: Context) = localFile(context).isFile

    @Synchronized
    fun resolve(context: Context, font: KeyboardFont): Typeface = when (font) {
        KeyboardFont.ROBOTO, KeyboardFont.GEIST -> bundled.getOrPut(font) {
            Typeface.createFromAsset(context.assets, "fonts/${font.title}-Regular.ttf")
        }
        KeyboardFont.NDOT -> {
            val file = localFile(context)
            imported?.takeIf { it.first == file }?.second
                ?: if (file.isFile) {
                    runCatching { Typeface.createFromFile(file) }
                        .onSuccess { imported = file to it }
                        .getOrDefault(Typeface.DEFAULT)
                } else Typeface.DEFAULT
        }
    }

    fun importNdot(context: Context, uri: Uri) {
        val data = requireNotNull(context.contentResolver.openInputStream(uri)).use { source ->
            val buffered = BufferedInputStream(source)
            buffered.mark(2)
            val zip = buffered.read() == 0x50 && buffered.read() == 0x4b
            buffered.reset()
            if (zip) readArchive(buffered) else readBounded(buffered, MAX_BYTES)
        }
        val temporary = File.createTempFile("ndot-", ".font", context.filesDir)
        try {
            temporary.writeBytes(data)
            val typeface = Typeface.Builder(temporary).build() ?: error("Invalid font")
            synchronized(this) {
                val destination = localFile(context)
                Os.rename(temporary.path, destination.path)
                imported = destination to typeface
            }
        } finally {
            temporary.delete()
        }
    }

    private fun readArchive(input: InputStream): ByteArray = ZipInputStream(input).use { zip ->
        var remaining = MAX_BYTES
        repeat(64) {
            val entry = zip.nextEntry ?: error("No font in ZIP")
            val data = readBounded(zip, remaining)
            if (!entry.isDirectory && entry.name.substringAfterLast('.').lowercase() in setOf("ttf", "otf")) {
                return@use data
            }
            remaining -= data.size
        }
        error("No font in ZIP")
    }

    private fun readBounded(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) return output.toByteArray()
            require(count <= limit - output.size()) { "Font exceeds size limit" }
            output.write(buffer, 0, count)
        }
    }
}