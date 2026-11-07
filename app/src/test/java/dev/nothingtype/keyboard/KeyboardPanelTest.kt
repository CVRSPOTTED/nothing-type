package dev.nothingtype.keyboard

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
import dev.nothingtype.settings.Palette
import dev.nothingtype.settings.Preferences
import dev.nothingtype.settings.LanguageSwitch
import dev.nothingtype.ui.dp
import dev.nothingtype.ui.InstantKey

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ru", sdk = [34])
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
    private fun tap(view: View, action: Int, x: Float = 1f) {
        val event = MotionEvent.obtain(0, 0, action, x, 1f, 0)
        try { view.dispatchTouchEvent(event) } finally { event.recycle() }
    }
    private fun measurePanel(panel: KeyboardPanel): Int {
        panel.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        panel.layout(0, 0, panel.measuredWidth, panel.measuredHeight)
        return panel.height
    }
    @Test fun toolbarAndSuggestionButtonsShareTopAndBottomEdges() {
        val panel = panel()
        repeat(2) {
            measurePanel(panel)
            val toolbar = buttons(panel).first().parent as ViewGroup
            val bounds = (0 until toolbar.childCount).map { toolbar.getChildAt(it).run { top to bottom } }
            assertEquals(1, bounds.distinct().size)
            panel.updateSuggestions(listOf("hello"))
        }
    }
    @Config(qualifiers = "ar-ldrtl")
    @Test fun rtlSystemLanguageDoesNotReverseEnglishKeyPositions() {
        val panel = panel()
        measurePanel(panel)
        assertEquals(View.LAYOUT_DIRECTION_LTR, panel.layoutDirection)
        val row = buttons(panel).first { it.text == "q" }.parent as ViewGroup
        val q = buttons(panel).first { it.text == "q" }
        val p = buttons(panel).first { it.text == "p" }
        assertSame(row, p.parent)
        assertTrue(q.left < p.left)
    }
    @Test fun bothSymbolPagesKeepTheSameHeightAndExposeAdditionalPasswordCharacters() {
        val panel = panel()
        val initialHeight = measurePanel(panel)
        buttons(panel).first { it.text == "?123" }.performClick()
        assertEquals(initialHeight, measurePanel(panel))
        buttons(panel).first { it.text.toString().startsWith("=") && it.text.length > 1 }.performClick()
        assertEquals(initialHeight, measurePanel(panel))
        for (symbol in listOf("$", "[", "]", "{", "}", "\\", "|", "~")) {
            assertTrue(buttons(panel).any { it.text == symbol })
        }
        buttons(panel).first { it.text == "ABC" }.performClick()
        assertTrue(buttons(panel).any { it.text == "q" })
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

    @Test fun languageButtonSwitchesImmediatelyAndDisablesHoldingSpace() {
        val prefs = Preferences(activity)
        prefs.languageSwitch = LanguageSwitch.BUTTON
        assertEquals(LanguageSwitch.BUTTON, Preferences(activity).languageSwitch)
        val panel = panel()
        measurePanel(panel)
        val language = buttons(panel).single { it.text == "EN" }
        val bottom = language.parent as ViewGroup
        assertEquals("space", (bottom.getChildAt(bottom.indexOfChild(language) + 1) as Button).text)
        tap(language, MotionEvent.ACTION_DOWN)
        assertEquals(Language.RU, prefs.language)
        tap(language, MotionEvent.ACTION_UP)
        assertEquals(listOf(Command.LANGUAGE), commands)
        assertTrue(typed.isEmpty())
        measurePanel(panel)
        val space = buttons(panel).single { it.text == "пробел" }
        assertFalse(space.isLongClickable)
        tap(space, MotionEvent.ACTION_DOWN)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(android.view.ViewConfiguration.getLongPressTimeout() + 100L))
        tap(space, MotionEvent.ACTION_UP)
        assertEquals(Language.RU, prefs.language)
        assertEquals(listOf(" "), typed)
        buttons(panel).single { it.text == "RU" }.performClick()
        assertEquals(Language.EN, prefs.language)
        assertEquals(listOf(Command.LANGUAGE, Command.LANGUAGE), commands)
    }

    @Test fun returningToSpaceHoldRemovesTheButtonAndKeepsCursorGestures() {
        val prefs = Preferences(activity)
        prefs.languageSwitch = LanguageSwitch.BUTTON
        val panel = panel()
        measurePanel(panel)
        val space = buttons(panel).single { it.text == "space" }
        tap(space, MotionEvent.ACTION_DOWN, 20f)
        tap(space, MotionEvent.ACTION_MOVE, 80f)
        tap(space, MotionEvent.ACTION_UP, 80f)
        assertTrue(commands.isNotEmpty())
        assertTrue(commands.all { it == Command.RIGHT })
        assertTrue(typed.isEmpty())
        prefs.languageSwitch = LanguageSwitch.SPACE_HOLD
        panel.render()
        assertFalse(buttons(panel).any { it.text == "EN" || it.text == "RU" })
        assertTrue(buttons(panel).single { it.text == "space" }.isLongClickable)
        assertEquals(LanguageSwitch.SPACE_HOLD, Preferences(activity).languageSwitch)
    }

    @Test fun tapsInKeyGapsReachTheNearestKey() {
        val panel = panel()
        measurePanel(panel)
        val keys = buttons(panel).associateBy { it.text.toString() }
        for (label in listOf("q", "e", "a", "w", "space")) {
            val key = keys.getValue(label)
            val bounds = android.graphics.Rect(0, 0, key.width, key.height)
            panel.offsetDescendantRectToMyCoords(key, bounds)
            // Land just outside the visible key, inside its surrounding spacing.
            val x = bounds.left - 0.5f
            val y = bounds.exactCenterY()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(0, 1, action, x, y, 0)
                try { panel.dispatchTouchEvent(event) } finally { event.recycle() }
            }
        }
        assertEquals(listOf("q", "e", "a", "w", " "), typed)
    }

    @Test fun aSmallFingerSlideDoesNotCancelALetterWithAlternatives() {
        val panel = panel()
        measurePanel(panel)
        val key = buttons(panel).first { it.text == "e" }
        val slop = android.view.ViewConfiguration.get(activity).scaledTouchSlop
        tap(key, MotionEvent.ACTION_DOWN, key.width / 2f)
        tap(key, MotionEvent.ACTION_MOVE, key.width + slop + 1f)
        tap(key, MotionEvent.ACTION_UP, key.width + slop + 1f)
        assertEquals(listOf("e"), typed)
    }

    @Test fun bottomPaddingBelongsToTheSpaceKeyWithoutCoveringTheNavigationArea() {
        val panel = panel()
        measurePanel(panel)
        val space = buttons(panel).first { it.text == "space" }
        val bounds = android.graphics.Rect(0, 0, space.width, space.height)
        panel.offsetDescendantRectToMyCoords(space, bounds)
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(0, 1, action, bounds.exactCenterX(), panel.height - 0.5f, 0)
            try { panel.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        assertEquals(listOf(" "), typed)
    }

    @Test fun fastTapsWithHoldActionsCommitInOrderWithoutWaitingForPostedClicks() {
        val panel = panel()
        measurePanel(panel)
        val keys = buttons(panel).associateBy { it.text.toString() }
        val sequence = listOf("e", "q", "space", "a", "a", ".", "q")
        sequence.forEach { label ->
            val key = keys.getValue(label)
            tap(key, MotionEvent.ACTION_DOWN)
            tap(key, MotionEvent.ACTION_UP)
        }
        val expected = listOf("e", "q", " ", "a", "a", ".", "q")
        assertEquals(expected, typed)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofSeconds(1))
        assertEquals(expected, typed)
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
    @Test fun settingsDispatchesAppLaunchFromToolbarAndSuggestions() {
        val panel = panel()
        buttons(panel).single { it.contentDescription == "Настройки" }.performClick()
        panel.updateSuggestions(listOf("hello"))
        buttons(panel).single { it.contentDescription == "Настройки" }.performClick()
        assertEquals(listOf(Command.SETTINGS, Command.SETTINGS), commands)
        assertTrue(buttons(panel).any { it.text == "q" })
        assertFalse(buttons(panel).first { it.text == "?123" }.isLongClickable)
    }
    @Test fun draggingSpaceMovesCursorWithoutTypingOrOpeningLanguageMenu() {
        val panel = panel()
        measurePanel(panel)
        val space = buttons(panel).first { it.text == "space" }
        tap(space, MotionEvent.ACTION_DOWN, 20f)
        tap(space, MotionEvent.ACTION_MOVE, 80f)
        tap(space, MotionEvent.ACTION_UP, 80f)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofSeconds(1))
        assertTrue(commands.isNotEmpty())
        assertTrue(commands.all { it == Command.RIGHT })
        assertTrue(typed.isEmpty())
    }

    @Test fun holdingPunctuationInsertsOnlyTheChosenAlternative() {
        val panel = panel()
        measurePanel(panel)
        val period = buttons(panel).first { it.text == "." }
        tap(period, MotionEvent.ACTION_DOWN)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(android.view.ViewConfiguration.getLongPressTimeout() + 100L))
        tap(period, MotionEvent.ACTION_UP)
        assertTrue(typed.isEmpty())
        val menu = org.robolectric.shadows.ShadowPopupMenu.getLatestPopupMenu().menu
        assertEquals("…", menu.getItem(0).title)
        menu.performIdentifierAction(menu.getItem(0).itemId, 0)
        assertEquals(listOf("…"), typed)
    }

    @Config(qualifiers = "en-sw720dp-land")
    @Test fun tabletUsesFullHeightKeysAtIncreasedFontScale() {
        val config = android.content.res.Configuration(activity.resources.configuration).apply { fontScale = 1.6f }
        val context = activity.createConfigurationContext(config)
        val prefs = Preferences(context).apply { keyHeight = 52; landscapeHeight = 32 }
        val panel = KeyboardPanel(context, prefs, KeyboardState(), {}, {})
        measurePanel(panel)
        val q = buttons(panel).first { it.text == "q" }
        assertEquals(context.dp(52), q.height)
        assertTrue(q.textSize > context.dp(prefs.fontSize))
        assertTrue(buttons(panel).any { it.contentDescription == "Settings" })
    }
    @Test fun oldRedPaletteKeepsItsAccentWhenTheBasePaletteChanges() {
        activity.getSharedPreferences("keyboard", android.content.Context.MODE_PRIVATE)
            .edit().putString("palette", "SIGNAL").apply()
        val prefs = Preferences(activity)
        assertEquals(Palette.DARK, prefs.palette)
        assertTrue(prefs.redEnter)

        prefs.palette = Palette.LIGHT
        assertTrue(prefs.redEnter)
        val reopened = Preferences(activity)
        assertEquals(Palette.LIGHT, reopened.palette)
        assertTrue(reopened.redEnter)
    }

    @Test fun redAccentChangesOnlyEnterInBothPalettes() {
        val prefs = Preferences(activity)
        fun fill(button: Button): Int? {
            return (button.background.current as android.graphics.drawable.GradientDrawable).color?.defaultColor
        }
        for (palette in Palette.entries) {
            prefs.palette = palette
            prefs.redEnter = false
            val keyboard = panel()
            fun key(text: String) = buttons(keyboard).single { it.text == text }
            val normalKey = fill(key("q"))
            assertEquals(palette.accent, fill(key("↵")))
            prefs.redEnter = true
            keyboard.render()
            assertEquals(0xFFD72D28.toInt(), fill(key("↵")))
            assertEquals(normalKey, fill(key("q")))
        }
    }
}
