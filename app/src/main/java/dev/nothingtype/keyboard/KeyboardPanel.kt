package dev.nothingtype.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.StateListDrawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import dev.nothingtype.R
import dev.nothingtype.settings.KeyboardFonts
import dev.nothingtype.settings.LanguageSwitch
import dev.nothingtype.settings.Palette
import dev.nothingtype.settings.Preferences
import dev.nothingtype.ui.action
import dev.nothingtype.ui.dp
import dev.nothingtype.ui.InstantKey
import dev.nothingtype.ui.KeyboardTouchRouter
import dev.nothingtype.ui.rounded
import dev.nothingtype.ui.roundedOutline

enum class Command { DELETE, ENTER, PASTE, LEFT, RIGHT, SETTINGS, VOICE, LANGUAGE }

/** Shared renderer for the real IME and the interactive settings preview. */
@android.annotation.SuppressLint("ViewConstructor") // Programmatic-only view: requires explicit input callbacks.
class KeyboardPanel(
    context: Context,
    private val prefs: Preferences,
    private val state: KeyboardState,
    private val commit: (String) -> Unit,
    private val command: (Command) -> Unit,
    private val sensitive: Boolean = false,
    private val enterLabel: String = "↵",
    private val chooseSuggestion: (String) -> Unit = {}
) : LinearLayout(context) {
    private var keyTypeface = android.graphics.Typeface.DEFAULT
    private val numeric get() = state.layer == Layer.NUMBER || state.layer == Layer.PHONE
    private val letterButtons = mutableListOf<Button>()
    private val candidateButtons = mutableListOf<Button>()
    private var shiftButton: InstantKey? = null
    private var voiceButton: Button? = null
    private var toolsRow: LinearLayout? = null
    private var candidatesRow: LinearLayout? = null
    private var cachedCandidates = emptyList<String>()
    private var renderedShift = false
    private var voiceStatus = context.getString(R.string.voice_idle)
    private var ambientDrawable: AmbientDrawable? = null
    private var lastTypingKey: InstantKey? = null
    private val typingRows = mutableListOf<LinearLayout>()
    private val touchRouter = KeyboardTouchRouter(this, ::keyAt) { lastTypingKey?.confirmPendingTap() }

    init {
        orientation = VERTICAL
        // RU/EN key positions stay physical even when the surrounding system UI is RTL.
        layoutDirection = LAYOUT_DIRECTION_LTR
        isMotionEventSplittingEnabled = true
        roundedOutline(24)
        render()
    }

    private fun row(typing: Boolean = false): LinearLayout = LinearLayout(context).also {
        it.orientation = HORIZONTAL
        it.isMotionEventSplittingEnabled = true
        it.isBaselineAligned = false
        it.gravity = android.view.Gravity.CENTER_VERTICAL
        addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        if (typing) typingRows.add(it)
    }

    private fun keyAt(x: Float, y: Float): InstantKey? {
        if (x < 0 || x >= width || y < 0 || y >= height || typingRows.isEmpty()) return null
        val first = typingRows.first()
        if (indexOfChild(first) > 0 && y < first.top) return null
        val row = typingRows.minByOrNull { maxOf(it.top - y, 0f, y - it.bottom) } ?: return null
        return (0 until row.childCount).map { row.getChildAt(it) }.filterIsInstance<InstantKey>()
            .filter { it.isEnabled && it.visibility == VISIBLE }
            .minByOrNull { maxOf(row.left + it.left - x, 0f, x - row.left - it.right) }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        touchRouter.dispatch(event) ?: super.dispatchTouchEvent(event)

    private fun key(row: LinearLayout, text: String, weight: Float = 1f, accent: Boolean = false,
                    description: String = text, immediate: Boolean = false, typing: Boolean = immediate,
                    onPress: () -> Unit): InstantKey {
        val theme = prefs.palette
        val redEnter = accent && prefs.redEnter
        val fill = when {
            redEnter -> 0xFFD72D28.toInt()
            accent -> theme.accent
            prefs.ambient -> (theme.key and 0x00FFFFFF) or 0xD9000000.toInt()
            else -> theme.key
        }
        val ink = when {
            redEnter || (accent && theme == Palette.LIGHT) -> android.graphics.Color.WHITE
            accent -> android.graphics.Color.BLACK
            else -> theme.ink
        }
        val button = context.action(text, fill, ink, prefs.radius)
        button.background = keyBackground(fill)
        button.typeface = keyTypeface
        button.contentDescription = description
        button.instant = immediate && prefs.instantInput
        button.immediateRelease = !button.instant && (typing || prefs.instantInput)
        if (typing) button.onTypingTouch = {
            // Resolve the earlier tap before a second finger can type ahead of it.
            lastTypingKey?.confirmPendingTap()
            lastTypingKey = it
        }
        button.isSoundEffectsEnabled = prefs.sound
        button.textSize = if (text.length > 4) 12f else prefs.fontSize.toFloat()
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val height = if (landscape && resources.configuration.smallestScreenWidthDp < 600) prefs.landscapeHeight else prefs.keyHeight
        row.addView(button, LayoutParams(0, context.dp(height), weight).apply { setMargins(context.dp(prefs.keyGap), context.dp(3), context.dp(prefs.keyGap), context.dp(3)) })
        val haptics = prefs.haptics
        button.setOnClickListener {
            onPress()
            if (haptics) button.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        return button
    }

    private fun highlight(button: Button) {
        val fill = if (prefs.palette == Palette.LIGHT) 0xFFD0D0CE.toInt() else 0xFF484848.toInt()
        button.background = keyBackground(fill)
    }

    private fun keyBackground(fill: Int): StateListDrawable {
        val radius = context.dp(prefs.radius).toFloat()
        fun pressedChannel(channel: Int) = (channel * 4 + 127) / 5
        val pressed = Color.argb(Color.alpha(fill), pressedChannel(Color.red(fill)),
            pressedChannel(Color.green(fill)), pressedChannel(Color.blue(fill)))
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rounded(pressed, radius))
            addState(intArrayOf(), rounded(fill, radius))
        }
    }

    private fun tool(line: LinearLayout, icon: Int, label: String, onPress: () -> Unit): InstantKey {
        return key(line, "", description = label, onPress = onPress).apply {
            toolbarIcon = context.getDrawable(icon)?.mutate()?.also { it.setTint(prefs.palette.ink) }
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x337F7F7F), null,
                rounded(android.graphics.Color.WHITE, context.dp(24).toFloat()))
            layoutParams.height = context.dp(48)
        }
    }

    private fun showLanguages(anchor: android.view.View) {
        android.widget.PopupMenu(context, anchor).apply {
            menu.add(0, 1, 0, "English").isChecked = state.language == Language.EN
            menu.add(0, 2, 1, "Русский").isChecked = state.language == Language.RU
            menu.setGroupCheckable(0, true, true)
            setOnMenuItemClickListener {
                changeLanguage(if (it.itemId == 1) Language.EN else Language.RU)
                true
            }
            show()
        }
    }

    private fun changeLanguage(language: Language) {
        state.language = language
        prefs.language = language
        render()
        command(Command.LANGUAGE)
    }

    private fun alternateSymbols(char: String): List<String> {
        if (state.layer != Layer.LETTERS) return emptyList()
        return when (char.lowercase()) {
            "e" -> listOf("é", "è", "ê", "ë")
            "a" -> listOf("á", "à", "â", "ä", "æ")
            "i" -> listOf("í", "ì", "î", "ï")
            "o" -> listOf("ó", "ò", "ô", "ö", "ø")
            "u" -> listOf("ú", "ù", "û", "ü")
            "n" -> listOf("ñ")
            "c" -> listOf("ç")
            "е" -> listOf("ё")
            "ь" -> listOf("ъ")
            else -> emptyList()
        }
    }

    private fun addAlternatives(button: InstantKey, values: List<String>) {
        if (values.isEmpty()) return
        button.setOnLongClickListener {
            android.widget.PopupMenu(context, button).apply {
                values.forEach { value -> menu.add(if (button.text.first().isUpperCase()) value.uppercase() else value) }
                setOnMenuItemClickListener {
                    state.afterCommit()
                    commit(it.title.toString())
                    updateCase()
                    true
                }
                show()
            }
            true
        }
    }

    private fun addDeleteKey(line: LinearLayout) {
        key(line, "⌫", 1.2f, description = context.getString(R.string.delete_character), immediate = true) {
            command(Command.DELETE)
        }.apply {
            repeatDelete = true
            highlight(this)
            iconSizeDp = 28
            toolbarIcon = context.getDrawable(R.drawable.key_backspace)?.mutate()?.apply { setTint(prefs.palette.ink) }
        }
    }

    fun updateSuggestions(values: List<String>) {
        cachedCandidates = values
        val show = values.isNotEmpty() && candidatesRow != null
        candidatesRow?.visibility = if (show) VISIBLE else GONE
        toolsRow?.visibility = if (show) GONE else VISIBLE
        candidateButtons.forEachIndexed { index, button ->
            val text = values.getOrNull(index).orEmpty()
            if (button.text != text) button.text = text
            val enabled = index < values.size
            if (button.isEnabled != enabled) button.isEnabled = enabled
            val description = if (enabled) context.getString(R.string.suggestion_label, text)
                else context.getString(R.string.no_suggestion)
            if (button.contentDescription != description) button.contentDescription = description
        }
    }
    fun updateVoice(status: String) {
        voiceStatus = status
        if (status == context.getString(R.string.voice_idle) || status == context.getString(R.string.voice_wait)) {
            ambientDrawable?.clearVoiceLevel()
        }
        voiceButton?.apply {
            contentDescription = context.getString(R.string.voice_status, status)
            isActivated = status != context.getString(R.string.voice_idle)
            (this as InstantKey).toolbarIcon?.setTint(
                if (status == context.getString(R.string.voice_idle)) prefs.palette.ink else prefs.palette.accent)
            invalidate()
        }
    }
    fun updateVoiceLevel(rmsDb: Float) { ambientDrawable?.updateVoiceLevel(rmsDb) }
    fun updateCase() {
        if (state.layer != Layer.LETTERS) return
        if (renderedShift != state.shift) {
            renderedShift = state.shift
            state.rows().flatten().forEachIndexed { i, text ->
                letterButtons.getOrNull(i)?.let { it.text = text; it.contentDescription = text }
            }
        }
        updateShiftIcon()
    }

    private fun updateShiftIcon() {
        val button = shiftButton ?: return
        val icon = when {
            state.capsLock -> R.drawable.shift_caps
            state.shift -> R.drawable.shift_on
            else -> R.drawable.shift_outline
        }
        if (button.tag == icon) return
        button.tag = icon
        button.text = when { state.capsLock -> "⇪"; state.shift -> "⬆"; else -> "↑" }
        button.contentDescription = context.getString(when {
            state.capsLock -> R.string.caps_active
            state.shift -> R.string.shift_active
            else -> R.string.shift_help
        })
        button.toolbarIcon = context.getDrawable(icon)?.mutate()?.apply { setTint(prefs.palette.ink) }
        button.invalidate()
    }
    private fun effectVisibility() {
        ambientDrawable?.running = isAttachedToWindow && windowVisibility == VISIBLE && isShown && android.animation.ValueAnimator.areAnimatorsEnabled()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); effectVisibility() }
    override fun onDetachedFromWindow() {
        touchRouter.cancel()
        ambientDrawable?.running = false
        super.onDetachedFromWindow()
    }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); effectVisibility() }
    override fun onVisibilityChanged(changedView: android.view.View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); effectVisibility() }

    fun render() {
        touchRouter.cancel()
        typingRows.clear()
        lastTypingKey = null
        renderedShift = state.shift
        keyTypeface = KeyboardFonts.resolve(context, prefs.keyboardFont)
        removeAllViews()
        letterButtons.clear(); candidateButtons.clear(); shiftButton = null; voiceButton = null; toolsRow = null; candidatesRow = null
        ambientDrawable?.running = false
        setPadding(context.dp(4), context.dp(6), context.dp(4), context.dp(8))
        ambientDrawable = if (prefs.ambient) AmbientDrawable(prefs.palette.canvas) else null
        background = ambientDrawable ?: rounded(prefs.palette.canvas, context.dp(20).toFloat())
        effectVisibility()
        if (prefs.toolbar) {
            val tools = row().also { toolsRow = it }
            if (!sensitive && !numeric) {
                tool(tools, R.drawable.tool_emoji, context.getString(R.string.emoji)) {
                    state.layer = if (state.layer == Layer.EMOJI) Layer.LETTERS else Layer.EMOJI
                    render()
                }
                tool(tools, R.drawable.tool_paste, context.getString(R.string.paste)) { command(Command.PASTE) }
            }
            tool(tools, R.drawable.tool_settings, context.getString(R.string.settings)) { command(Command.SETTINGS) }
            if (!sensitive && !numeric) {
                voiceButton = tool(tools, R.drawable.tool_mic, context.getString(R.string.voice_input)) { command(Command.VOICE) }
                updateVoice(voiceStatus)
            }
        }
        if (prefs.suggestions && !sensitive && !numeric && state.layer != Layer.EMOJI) {
            val candidates = row().also { candidatesRow = it }
            tool(candidates, R.drawable.tool_settings, context.getString(R.string.settings)) { command(Command.SETTINGS) }
            repeat(3) { index ->
                val button = key(candidates, "") { cachedCandidates.getOrNull(index)?.let(chooseSuggestion) }
                button.textSize = 14f
                button.layoutParams.height = context.dp(48)
                button.background = rounded(android.graphics.Color.TRANSPARENT, 0f)
                candidateButtons.add(button)
            }
            updateSuggestions(cachedCandidates)
        }
        if (prefs.numbers && state.layer == Layer.LETTERS) {
            val numbers = row(typing = true)
            "1234567890".forEach { char -> key(numbers, char.toString(), immediate = true) { commit(char.toString()) } }
        }
        val bodyStart = childCount
        if (state.layer == Layer.EMOJI) {
            val height = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && resources.configuration.smallestScreenWidthDp < 600) prefs.landscapeHeight else prefs.keyHeight
            val rowHeight = context.dp(height) + 2 * context.dp(3)
            addView(emojiPicker(context, prefs.palette, rowHeight * (if (prefs.numbers) 4 else 3), commit))
        }
        state.rows().forEachIndexed { index, chars ->
            val line = row(typing = true)
            if (index == 2 && state.layer == Layer.LETTERS) {
                shiftButton = key(line, "↑", 1.3f, typing = true) {
                    state.toggleShift(); updateCase()
                }.apply {
                    highlight(this)
                    iconSizeDp = 28
                    setOnLongClickListener { state.lockCaps(); updateCase(); true }
                }
                updateShiftIcon()
            }
            if (index == 2 && state.layer in listOf(Layer.SYMBOLS, Layer.SYMBOLS_MORE)) {
                val more = state.layer == Layer.SYMBOLS
                key(line, if (more) "=\\<" else "?123", 1.3f,
                    description = context.getString(if (more) R.string.more_symbols else R.string.common_symbols)) {
                    state.layer = if (more) Layer.SYMBOLS_MORE else Layer.SYMBOLS
                    render()
                }.apply { highlight(this) }
            }
            chars.forEach { char ->
                lateinit var button: InstantKey
                val extras = alternateSymbols(char)
                button = key(line, char, immediate = extras.isEmpty(), typing = true) {
                    val value = button.text.toString()
                    state.afterCommit()
                    commit(value)
                    updateCase()
                }
                addAlternatives(button, extras)
                letterButtons.add(button)
            }
        }
        if (state.layer != Layer.EMOJI) {
            addDeleteKey(getChildAt(childCount - 1) as LinearLayout)
        }
        val bottom = row(typing = true)
        if (numeric) {
            key(bottom, enterLabel, accent = true, typing = true) { command(Command.ENTER) }
            return
        }
        key(bottom, if (state.layer == Layer.LETTERS) "?123" else "ABC", 1.2f) {
            state.layer = if (state.layer == Layer.LETTERS) Layer.SYMBOLS else Layer.LETTERS; render()
        }
        addAlternatives(key(bottom, ",", typing = true) { commit(",") }, listOf(";", ":", "«", "»"))
        val languageButton = prefs.languageSwitch == LanguageSwitch.BUTTON
        if (languageButton) {
            val next = if (state.language == Language.EN) Language.RU else Language.EN
            key(bottom, state.language.name, 0.9f,
                description = context.getString(R.string.switch_to_language, if (next == Language.RU) "Русский" else "English"),
                immediate = true) { changeLanguage(next) }
        }
        val spaceDescription = when {
            languageButton -> R.string.space_button_help
            state.language == Language.EN -> R.string.space_en_help
            else -> R.string.space_ru_help
        }
        val space = key(bottom, if (state.language == Language.EN) "space" else "пробел",
            if (languageButton) 2.7f else 3.6f,
            description = context.getString(spaceDescription), typing = true) { commit(" ") }
        space.spaceLegend = true
        space.moveCursor = { command(if (it < 0) Command.LEFT else Command.RIGHT) }
        if (!languageButton) space.setOnLongClickListener { showLanguages(space); true }
        space.accessibilityDelegate = object : android.view.View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(
                host: android.view.View,
                info: android.view.accessibility.AccessibilityNodeInfo
            ) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                    R.id.action_cursor_left, context.getString(R.string.cursor_left)))
                info.addAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(
                    R.id.action_cursor_right, context.getString(R.string.cursor_right)))
            }

            override fun performAccessibilityAction(host: android.view.View, action: Int, args: android.os.Bundle?): Boolean {
                when (action) {
                    R.id.action_cursor_left -> command(Command.LEFT)
                    R.id.action_cursor_right -> command(Command.RIGHT)
                    else -> return super.performAccessibilityAction(host, action, args)
                }
                return true
            }
        }
        addAlternatives(key(bottom, ".", typing = true) { commit(".") }, listOf("…", "!", "?", "—", "/"))
        if (state.layer == Layer.EMOJI) addDeleteKey(bottom)
        key(bottom, enterLabel, 1.3f, true, context.getString(R.string.enter_label, enterLabel), typing = true) { command(Command.ENTER) }
        if (state.layer in listOf(Layer.SYMBOLS, Layer.SYMBOLS_MORE) && prefs.numbers) {
            // Distribute the missing letter-mode number row, including its margins, in pixels.
            // Keep the toolbar and navigation area at their original sizes.
            val layout = bottom.getChildAt(0).layoutParams as LayoutParams
            val extra = layout.height + layout.topMargin + layout.bottomMargin
            val rowCount = childCount - bodyStart
            for (index in 0 until rowCount) {
                val line = getChildAt(bodyStart + index) as LinearLayout
                val increase = extra * (index + 1) / rowCount - extra * index / rowCount
                for (column in 0 until line.childCount) line.getChildAt(column).layoutParams.height += increase
            }
        }
    }
}
