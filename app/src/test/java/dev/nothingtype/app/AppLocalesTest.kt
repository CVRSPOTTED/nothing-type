package dev.nothingtype.app

import android.content.Intent
import android.widget.ListView
import dev.nothingtype.R
import dev.nothingtype.keyboard.Language
import dev.nothingtype.settings.Preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "en")
class AppLocalesTest {
    @Test fun firstLaunchChoicePersistsOnAndroid12AndPreservesTheTypingLayout() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, Intent(Intent.ACTION_MAIN)).setup()
        val activity = controller.get()
        Preferences(activity).language = Language.RU
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        assertNotNull(dialog)
        val list: ListView = dialog.listView
        val french = (0 until list.count).first { list.getItemAtPosition(it).toString() == "Français" }
        list.performItemClick(null, french, list.getItemIdAtPosition(french))

        val reopened = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertEquals("Langue de l’application", reopened.getString(R.string.app_language))
        assertEquals(Language.FR, AppLocales.selected(reopened))
        assertEquals(Language.RU, Preferences(reopened).language)
        assertFalse(AppLocales.shouldOfferOnLaunch(reopened))
    }
}
