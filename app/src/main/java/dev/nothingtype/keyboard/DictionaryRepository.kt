package dev.nothingtype.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.Executors

/** Keep recent lexicons ready without indexing eight languages at keyboard startup. */
internal object DictionaryRepository {
    val fallback = Suggestions()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private data class Entry(val personal: Set<String>, val engine: Suggestions)
    private val recent = LinkedHashMap<Language, Entry>(4, 0.75f, true)

    fun load(context: Context, language: Language, personal: Set<String>, ready: (Suggestions) -> Unit) {
        val assets = context.applicationContext.assets
        worker.execute {
            val engine = runCatching {
                recent[language]?.takeIf { it.personal == personal }?.engine ?: run {
                    val file = "dictionaries/" + language.name.lowercase(Locale.ROOT) + ".txt"
                    val words = assets.open(file).bufferedReader().useLines { lines ->
                        lines.map { it.substringBefore(' ').removePrefix("\uFEFF") }
                            .filter { it.isNotBlank() }.toList()
                    }
                    val own = personal.filter { WordText.isWord(it, language) }.sorted()
                    Suggestions(mapOf(language to words), mapOf(language to own)).also {
                        recent[language] = Entry(personal, it)
                        if (recent.size > 3) recent.remove(recent.keys.first())
                    }
                }
            }.getOrDefault(fallback)
            main.post { ready(engine) }
        }
    }
}
