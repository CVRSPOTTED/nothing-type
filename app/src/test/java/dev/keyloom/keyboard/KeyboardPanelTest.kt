package dev.keyloom.keyboard

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyboardPanelTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val typed = mutableListOf<String>()
    private val commands = mutableListOf<Command>()
    private val selected = mutableListOf<String>()
    private fun panel(privateField: Boolean = false): KeyboardPanel = KeyboardPanel(activity, Preferences(activity),
        KeyboardState(), { typed.add(it) }, { commands.add(it) }, sensitive = privateField,
        chooseSuggestion = { selected.add(it) }).also(activity::setContentView)

    private fun buttons(view: View): List<Button> = when {
        view.visibility != View.VISIBLE -> emptyList()
        view is Button -> listOf(view)
        view is ViewGroup -> (0 until view.childCount).flatMap { buttons(view.getChildAt(it)) }
        else -> emptyList()
    }
    private fun tap(view: View, action: Int) {
        val event = MotionEvent.obtain(0, 0, action, 1f, 1f, 0)
        try { view.dispatchTouchEvent(event) } finally { event.recycle() }
    }
    private fun measurePanel(panel: KeyboardPanel): Int {
        panel.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        return panel.height
    }
    @Test fun mixedLabelSizesShareTopAndBottomEdgesIncludingQuickMenu() {
        val panel = panel()
        repeat(2) {
            measurePanel(panel)
            val toolbar = buttons(panel).first().parent as ViewGroup
            val bounds = (0 until toolbar.childCount).map { toolbar.getChildAt(it).run { top to bottom } }
            assertEquals(1, bounds.distinct().size)
            buttons(panel).first().performClick()
        }
    }
    @Test fun symbolsFillExactlyTheLetterHeightWithOrWithoutNumberRow() {
        val prefs = Preferences(activity)
        for (numbers in listOf(true, false)) {
            prefs.numbers = numbers
            val panel = panel()
            val before = measurePanel(panel)
            val letter = buttons(panel).first { it.text == "q" }.height
            buttons(panel).first { it.text == "?123" }.performClick()
            assertEquals(before, measurePanel(panel))
            val symbol = buttons(panel).first { it.text == "@" }.height
            if (numbers) assertTrue(symbol > letter) else assertEquals(letter, symbol)
            buttons(panel).first { it.text == "ABC" }.performClick()
            assertEquals(before, measurePanel(panel))
        }
    }
    @Test fun catalogFlagIsCommittedAsOneSequence() {
        val catalog = EmojiCatalog.load(activity)
        assertEquals(9, catalog.size)
        assertEquals(3781, catalog.values.flatten().distinctBy { it.glyph }.size)
        val panel = panel()
        buttons(panel).first { it.contentDescription == "Эмодзи" }.performClick()
        val tab = buttons(panel).first { it.text == "Флаги" }
        tab.performClick()
        val picker = tab.parent.parent.parent as ViewGroup
        val grid = picker.getChildAt(1) as android.widget.GridView
        val index = (0 until grid.count).first { (grid.getItemAtPosition(it) as EmojiEntry).glyph == "🇷🇺" }
        grid.performItemClick(View(activity), index, index.toLong())
        assertEquals(listOf("🇷🇺"), typed)
        buttons(panel).first { it.text == "ABC" }.performClick()
        assertTrue(buttons(panel).any { it.text == "q" })
    }

    @Test fun pixelSpaceCommitsInBothLanguages() {
        val panel = panel()
        for (label in listOf("space", "пробел")) {
            val space = buttons(panel).first { it.text == label } as InstantKey
            assertTrue(space.spaceLegend)
            val row = space.parent as ViewGroup
            val index = row.indexOfChild(space)
            assertEquals(",", (row.getChildAt(index - 1) as Button).text)
            assertEquals(".", (row.getChildAt(index + 1) as Button).text)
            space.performClick()
            if (label == "space") {
                space.performLongClick()
                org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu().menu.performIdentifierAction(2, 0)
            }
        }
        assertEquals(listOf(" ", " "), typed)
    }

    @Test fun heldDeleteRepeatsAndStopsOnReleaseInBothModes() {
        val clock = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        for (instant in listOf(true, false)) {
            Preferences(activity).instantInput = instant
            val key = buttons(panel()).first { it.text == "⌫" }
            commands.clear()
            tap(key, MotionEvent.ACTION_DOWN)
            clock.idleFor(java.time.Duration.ofMillis(android.view.ViewConfiguration.getLongPressTimeout() + 200L))
            val count = commands.size
            assertTrue(count >= 3)
            assertTrue(commands.all { it == Command.DELETE })
            tap(key, MotionEvent.ACTION_UP)
            clock.idleFor(java.time.Duration.ofMillis(200))
            assertEquals(count, commands.size)
            tap(key, MotionEvent.ACTION_DOWN)
            tap(key, MotionEvent.ACTION_UP)
            assertEquals(count + 1, commands.size)
        }
    }

    @Test fun spaceHoldChoosesLanguageWithoutInsertingAndTapStillInserts() {
        val panel = panel()
        measurePanel(panel)
        assertFalse(buttons(panel).any { it.text == "EN" || it.text == "RU" })
        val space = buttons(panel).first { it.text == "space" }
        tap(space, MotionEvent.ACTION_DOWN)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(android.view.ViewConfiguration.getLongPressTimeout() + 100L))
        val menu = org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu()
        assertNotNull(menu)
        tap(space, MotionEvent.ACTION_UP)
        assertTrue(typed.isEmpty())
        menu.menu.performIdentifierAction(2, 0)
        assertEquals(Language.RU, Preferences(activity).language)
        assertTrue(buttons(panel).any { it.text == "й" })
        assertEquals(listOf(Command.LANGUAGE), commands)
        measurePanel(panel)
        val russianSpace = buttons(panel).first { it.text == "пробел" }
        tap(russianSpace, MotionEvent.ACTION_DOWN)
        tap(russianSpace, MotionEvent.ACTION_UP)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(listOf(" "), typed)
    }

    @Test fun fastKeyCommitsBeforeReleaseAndOnlyOnce() {
        val key = buttons(panel()).first { it.text == "q" }
        tap(key, MotionEvent.ACTION_DOWN); assertEquals(listOf("q"), typed)
        tap(key, MotionEvent.ACTION_UP); assertEquals(listOf("q"), typed)
    }
    @Test fun shiftKeepsSameLetterViewsAndResetsAfterOneKey() {
        val panel = panel(); val q = buttons(panel).first { it.text == "q" }
        buttons(panel).first { it.text == "↑" }.performClick()
        assertEquals("Q", q.text); q.performClick(); assertEquals(listOf("Q"), typed)
        assertSame(q, buttons(panel).first { it.text == "q" })
    }
    @Test fun candidatesReplaceToolsAndAreSelectable() {
        val panel = panel()
        panel.updateSuggestions(listOf("hello", "help"))
        assertFalse(buttons(panel).any { it.contentDescription == "Вставить" })
        buttons(panel).first { it.text == "hello" }.performClick()
        assertEquals(listOf("hello"), selected)
        panel.updateSuggestions(emptyList()); assertTrue(buttons(panel).any { it.contentDescription == "Вставить" })
    }
    @Test fun privateFieldHidesMicrophoneClipboardAndCandidates() {
        val panel = panel(true); panel.updateSuggestions(listOf("hello"))
        assertFalse(buttons(panel).any { it.contentDescription?.startsWith("Голосовой ввод") == true || it.contentDescription == "Вставить" || it.text == "hello" })
    }
    @Test fun quickMenuChangesPreferenceWithoutLeavingKeyboard() {
        val panel = panel()
        buttons(panel).first { it.contentDescription == "Быстрые настройки" }.performClick()
        buttons(panel).first { it.text.startsWith("Живой фон") }.performClick()
        assertTrue(Preferences(activity).ambient); assertEquals(listOf(Command.REFRESH), commands)
        buttons(panel).first { it.contentDescription == "Вернуться к клавиатуре" }.performClick(); assertTrue(buttons(panel).any { it.text == "q" })
    }
}
