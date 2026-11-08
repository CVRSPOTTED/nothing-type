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
        buttons(root).single { it.text == activity.getString(R.string.tab_style) }.performClick()
        root.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        val choices = buttons(root).filter { it.text.toString() in listOf("Roboto", "Geist", "Ndot ↗", "Dark", "Light") }
        assertEquals(5, choices.size)
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
        buttons(root).single { it.text == activity.getString(R.string.tab_style) }.performClick()
        buttons(root).single { it.text == "Light" }.performClick()
        val saved = Bundle()
        controller.saveInstanceState(saved).pause().stop().destroy()
        val next = Robolectric.buildActivity(MainActivity::class.java).create(saved).start().resume().get()
        assertEquals(Palette.LIGHT, Preferences(next).palette)
        val light = buttons(next.findViewById(android.R.id.content)).single { it.text == "Light" }
        assertTrue(light.isSelected)
        next.finish()
    }
}
