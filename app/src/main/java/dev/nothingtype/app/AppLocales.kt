package dev.nothingtype.app

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import dev.nothingtype.keyboard.Language

/** App locale preferences are independent of the active typing layout. */
internal object AppLocales {
    val languages = Language.entries.filter { it != Language.CUSTOM }
    private const val LEGACY_LOCALE = "locale"
    private const val LANGUAGE_CHOSEN = "language_chosen"

    private fun storage(context: Context) = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    fun selected(context: Context): Language? {
        val tag = if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales[0]?.language
        } else {
            storage(context).getString(LEGACY_LOCALE, null)
        }
        return languages.firstOrNull { it.locale.language == tag }
    }

    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val language = selected(context) ?: return context
        return context.createConfigurationContext(Configuration().apply { setLocale(language.locale) })
    }

    @Suppress("DEPRECATION") // This overload supports every Android version the app runs on.
    fun shouldOfferOnLaunch(context: Context): Boolean {
        if (storage(context).getBoolean(LANGUAGE_CHOSEN, false) || selected(context) != null) return false
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return info.firstInstallTime == info.lastUpdateTime
    }

    fun choose(activity: Activity, language: Language?) {
        val current = selected(activity)
        val tag = language?.locale?.language.orEmpty()
        val edit = storage(activity).edit().putBoolean(LANGUAGE_CHOSEN, true)
        if (Build.VERSION.SDK_INT >= 33) {
            edit.apply()
            if (current != language) {
                activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
            }
        } else {
            edit.putString(LEGACY_LOCALE, tag).apply()
            if (current != language) activity.recreate()
        }
    }

    fun migrateLegacyChoice(context: Context) {
        if (Build.VERSION.SDK_INT < 33) return
        val prefs = storage(context)
        val legacyTag = prefs.getString(LEGACY_LOCALE, null) ?: return
        val manager = context.getSystemService(LocaleManager::class.java)
        if (manager.applicationLocales.isEmpty && legacyTag.isNotEmpty()) {
            manager.applicationLocales = LocaleList.forLanguageTags(legacyTag)
        }
        prefs.edit().remove(LEGACY_LOCALE).apply()
    }
}
