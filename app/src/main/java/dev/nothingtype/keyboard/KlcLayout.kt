package dev.nothingtype.keyboard

import java.io.InputStream

internal data class ImportedLayout(val title: String, val tag: String, val rows: List<List<LayoutKey>>)
internal data class LayoutKey(val base: String, val shifted: String, val alternatives: List<String>)

/** Reads the printable layers of a Windows Keyboard Layout Creator file. */
internal object KlcLayout {
    private const val MAX_BYTES = 256 * 1024
    private val scanRows = listOf(
        listOf(0x29) + (0x02..0x0d),
        (0x10..0x1b).toList(),
        (0x1e..0x28).toList() + 0x2b,
        listOf(0x56) + (0x2c..0x35)
    )

    fun read(input: InputStream): ImportedLayout {
        val bytes = ByteArray(MAX_BYTES + 1)
        var count = 0
        while (count < bytes.size) {
            val read = input.read(bytes, count, bytes.size - count)
            if (read <= 0) break
            count += read
        }
        require(count in 1..MAX_BYTES)
        val charset = when {
            bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> Charsets.UTF_16LE
            bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        val source = String(bytes, 0, count, charset).removePrefix("\uFEFF")
        val lines = source.lineSequence().map { it.substringBefore("//").trim() }.filter {
            it.isNotEmpty() && !it.startsWith(';')
        }.toList()
        val header = lines.firstOrNull { it.startsWith("KBD\t") || it.startsWith("KBD ") }
        val title = requireNotNull(header?.let { quoted(it) })
        require(title.isNotBlank() && title.none(Char::isISOControl))
        val tag = lines.firstOrNull { it.startsWith("LOCALENAME") }?.let(::quoted)
            ?.takeIf { it.length <= 35 && it.matches(Regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")) } ?: "und"
        val shiftStart = lines.indexOf("SHIFTSTATE")
        val layoutStart = lines.indexOf("LAYOUT")
        require(shiftStart >= 0 && layoutStart > shiftStart && lines.lastOrNull() == "ENDKBD")
        val states = lines.subList(shiftStart + 1, layoutStart).map { it.toInt() }
        require(states.size in 1..16 && states.distinct().size == states.size && 0 in states)
        val positions = mutableMapOf<Int, LayoutKey>()
        for (line in lines.drop(layoutStart + 1)) {
            val columns = line.split(Regex("\\s+"))
            val scan = columns.first().takeIf { it.length == 2 }?.toIntOrNull(16)
            if (scan == null) {
                if (columns.first().startsWith("-")) continue // Windows Caps Lock override.
                break
            }
            if (scanRows.none { scan in it }) continue
            fun value(state: Int): String? {
                val column = states.indexOf(state)
                return if (column < 0) null else columns.getOrNull(3 + column)?.let(::printable)
            }
            val base = value(0) ?: continue
            val shifted = value(1) ?: base
            val alternatives = listOfNotNull(value(6), value(7)).distinct().filter { it != base && it != shifted }
            positions[scan] = LayoutKey(base, shifted, alternatives)
        }
        val rows = scanRows.map { row -> row.mapNotNull(positions::get) }
        require(rows.drop(1).all { it.isNotEmpty() } && rows.drop(1).sumOf { it.size } >= 12)
        return ImportedLayout(title.take(72), tag, rows.filter { it.isNotEmpty() })
    }

    private fun quoted(line: String): String? = Regex("\"([^\"]+)\"").find(line)?.groupValues?.get(1)

    private fun printable(token: String): String? {
        // A dead key remains available as a literal accent on the touch layout.
        val code = token.removeSuffix("@")
        val value = if (code.length == 1) code[0].code else if (code.length in 4..6) code.toIntOrNull(16) else null
        return value?.takeIf {
            Character.isValidCodePoint(it) && it !in 0xd800..0xdfff &&
                !Character.isISOControl(it) && !Character.isWhitespace(it) &&
                (Character.getType(it) != Character.FORMAT.toInt() || it == 0x200c || it == 0x200d)
        }?.let { String(Character.toChars(it)) }
    }
}
