package dev.keyloom.keyboard

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

internal data class EmojiEntry(val glyph: String, val description: String)

internal object EmojiCatalog {
    private var cached: Map<String, List<EmojiEntry>>? = null
    fun load(context: Context): Map<String, List<EmojiEntry>> {
        cached?.let { return it }
        val groups = linkedMapOf<String, MutableList<EmojiEntry>>()
        context.resources.openRawResource(R.raw.emoji_catalog).bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val (group, glyph, description) = line.split('\t', limit = 3)
                groups.getOrPut(group) { mutableListOf() }.add(EmojiEntry(glyph, description))
            }
        }
        cached = groups
        return groups
    }
}

internal fun emojiPicker(context: Context, palette: Palette, height: Int, choose: (String) -> Unit): View {
    val names = listOf("Смайлы", "Люди", "Природа", "Еда", "Путешествия", "Занятия", "Предметы", "Символы", "Флаги")
    val groups = EmojiCatalog.load(context).values.toList()
    val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val tabs = LinearLayout(context)
    val strip = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false; addView(tabs) }
    column.addView(strip, LinearLayout.LayoutParams(-1, context.dp(36)))
    val grid = GridView(context).apply {
        columnWidth = context.dp(46)
        numColumns = GridView.AUTO_FIT
        stretchMode = GridView.STRETCH_COLUMN_WIDTH
    }
    column.addView(grid, LinearLayout.LayoutParams(-1, (height - context.dp(36)).coerceAtLeast(context.dp(40))))
    fun select(index: Int) {
        val entries = groups[index]
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = entries.size
            override fun getItem(position: Int) = entries[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val cell = convertView as? TextView ?: TextView(context).apply {
                    gravity = Gravity.CENTER; textSize = 24f; setTextColor(palette.ink)
                    layoutParams = AbsListView.LayoutParams(-1, context.dp(44))
                }
                val entry = entries[position]
                cell.text = entry.glyph
                cell.contentDescription = entry.description
                return cell
            }
        }
        grid.setOnItemClickListener { _, _, position, _ -> choose(entries[position].glyph) }
        for (i in names.indices) {
            val tab = tabs.getChildAt(i) as Button
            tab.isSelected = i == index
            tab.text = if (i == index) "● " + names[i] else names[i]
        }
        grid.setSelection(0)
    }
    names.forEachIndexed { index, name ->
        val tab = context.action(name, palette.key, palette.ink).apply {
            setPadding(context.dp(10), 0, context.dp(10), 0)
            setOnClickListener { select(index) }
        }
        tabs.addView(tab, LinearLayout.LayoutParams(-2, -1).apply { rightMargin = context.dp(3) })
    }
    select(0)
    return column
}
