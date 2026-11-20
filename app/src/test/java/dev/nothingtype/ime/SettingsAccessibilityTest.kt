package dev.nothingtype.ime

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import dev.nothingtype.R
import dev.nothingtype.app.MainActivity
import dev.nothingtype.settings.Palette
import dev.nothingtype.settings.Preferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-w320dp-h640dp")
class SettingsAccessibilityTest {
    private fun buttons(view: View): List<Button> = when (view) {
        is Button -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
        else -> emptyList()
    }
    @Test fun themeAndFontChoicesGrowAtTwoHundredPercentAndKeepFlatBackgrounds() {
        val resources = RuntimeEnvironment.getApplication().resources
        val config = Configuration(resources.configuration).apply { fontScale = 2f }
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val tryTyping = buttons(root).single { it.text == activity.getString(R.string.test_keyboard) }
        assertEquals(View.GONE, tryTyping.visibility)
        buttons(root).single { it.contentDescription == activity.getString(R.string.tab_style) }.performClick()
        assertEquals(View.VISIBLE, tryTyping.visibility)
        root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val dockButtons = buttons(root).filter {
            it.contentDescription in listOf(R.string.tab_setup, R.string.tab_settings, R.string.tab_style).map(activity::getString)
        }
        assertEquals(3, dockButtons.size)
        assertEquals(activity.getString(R.string.tab_style), dockButtons.single { it.isSelected }.text)
        assertTrue(dockButtons.all { it.width >= 48 && it.height >= 48 })
        assertTrue(dockButtons.filterNot { it.isSelected }.all { it.text.isEmpty() })
        val choices = buttons(root).filter { it.text.toString() in listOf("Roboto", "Geist", "Ndot ↗", "Dark", "Light", "NP2(a)SE") }
        assertEquals(6, choices.size)
        choices.forEach { button ->
            assertTrue("${button.text} must fit", button.height >= button.layout.height + button.compoundPaddingTop + button.compoundPaddingBottom)
            assertTrue(button.height >= 48)
            assertEquals(0f, button.elevation)
        }
        activity.finish()
    }
    @Test fun settingsAndSelectedTabSurviveActivityRecreation() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        buttons(root).single { it.contentDescription == activity.getString(R.string.tab_style) }.performClick()
        buttons(root).single { it.text == "NP2(a)SE" }.performClick()
        val saved = Bundle()
        controller.saveInstanceState(saved).pause().stop().destroy()
        val next = Robolectric.buildActivity(MainActivity::class.java).create(saved).start().resume().get()
        assertEquals(Palette.DESSAU, Preferences(next).palette)
        val thirdTheme = buttons(next.findViewById(android.R.id.content)).single { it.text == "NP2(a)SE" }
        assertTrue(thirdTheme.isSelected)
        next.finish()
    }
}
