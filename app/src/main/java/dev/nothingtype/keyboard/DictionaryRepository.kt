package dev.nothingtype.keyboard

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** App-wide immutable lexicons; asset I/O and indexing never run in a key callback. */
internal object DictionaryRepository {
    val fallback = Suggestions()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var assets: Map<Language, List<String>>? = null
    private var cached: Pair<Set<String>, Suggestions>? = null

    fun load(context: Context, personal: Set<String>, ready: (Suggestions) -> Unit) {
        val app = context.applicationContext
        val snapshot = personal.toSet()
        worker.execute {
            val engine = runCatching {
                cached?.takeIf { it.first == snapshot }?.second ?: run {
                    val words = assets ?: Language.entries.filter(Language::hasDictionary).associateWith { language ->
                        app.assets.open("dictionaries/${language.name.lowercase()}.txt").bufferedReader().useLines { lines ->
                            lines.map { it.substringBefore(' ').removePrefix("\uFEFF") }.filter { it.isNotBlank() }.toList()
                        }
                    }.also { assets = it }
                    val own = Language.entries.filter(Language::hasDictionary).associateWith { language ->
                        snapshot.filter { word -> if (language == Language.RU) word.all { it in 'а'..'я' || it == 'ё' } else word.all { it in 'a'..'z' } }.sorted()
                    }
                    Suggestions(words, own).also { cached = snapshot to it }
                }
            }.getOrDefault(fallback)
            main.post { ready(engine) }
        }
    }
}
