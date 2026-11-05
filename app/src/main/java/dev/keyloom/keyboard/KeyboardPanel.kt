package dev.keyloom.keyboard

import android.content.Context
import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import android.widget.Button
import android.widget.LinearLayout

enum class Command { DELETE, ENTER, PASTE, LEFT, RIGHT, SETTINGS, NEXT_IME, REFRESH, VOICE, LANGUAGE }

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
    private val chooseSuggestion: (String) -> Unit = {},
    var quickOpen: Boolean = false
) : LinearLayout(context) {
    private val letterButtons = mutableListOf<Button>()
    private val candidateButtons = mutableListOf<Button>()
    private var shiftButton: Button? = null
    private var voiceButton: Button? = null
    private var toolsRow: LinearLayout? = null
    private var candidatesRow: LinearLayout? = null
    private var cachedCandidates = emptyList<String>()
    private var voiceStatus = "Голос"
    private var ambientDrawable: AmbientDrawable? = null
    private var rowsHost: LinearLayout? = null
    init { orientation = VERTICAL; roundedOutline(24); render() }

    private fun row(): LinearLayout = LinearLayout(context).also {
        it.orientation = HORIZONTAL
        it.isBaselineAligned = false
        it.gravity = android.view.Gravity.CENTER_VERTICAL
        (rowsHost ?: this).addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    private fun key(row: LinearLayout, text: String, weight: Float = 1f, accent: Boolean = false,
                    description: String = text, immediate: Boolean = false, onPress: () -> Unit): InstantKey {
        val theme = prefs.palette
        val button = context.action(text, if (accent) theme.accent else if (prefs.ambient) (theme.key and 0x00FFFFFF) or 0xD9000000.toInt() else theme.key,
            if (accent && theme != Palette.MONO) android.graphics.Color.WHITE
            else if (accent) android.graphics.Color.BLACK else theme.ink, prefs.radius)
        button.contentDescription = description
        button.instant = immediate && prefs.instantInput
        button.stateListAnimator = null
        button.isSoundEffectsEnabled = prefs.sound
        button.textSize = if (text.length > 4) 12f else prefs.fontSize.toFloat()
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val height = if (landscape) prefs.landscapeHeight else prefs.keyHeight
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
        button.background = android.graphics.drawable.RippleDrawable(
            android.content.res.ColorStateList.valueOf(0x337F7F7F),
            rounded(fill, context.dp(prefs.radius).toFloat()), null)
    }

    private fun tool(line: LinearLayout, icon: Int, label: String, onPress: () -> Unit): InstantKey {
        return key(line, "", description = label, onPress = onPress).apply {
            toolbarIcon = context.getDrawable(icon)?.mutate()?.also { it.setTint(prefs.palette.ink) }
            background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x337F7F7F), null,
                rounded(android.graphics.Color.WHITE, context.dp(24).toFloat()))
            layoutParams.height = context.dp(40)
        }
    }

    private fun showLanguages(anchor: android.view.View) {
        android.widget.PopupMenu(context, anchor).apply {
            menu.add(0, 1, 0, "English").isChecked = state.language == Language.EN
            menu.add(0, 2, 1, "Русский").isChecked = state.language == Language.RU
            menu.setGroupCheckable(0, true, true)
            setOnMenuItemClickListener {
                state.language = if (it.itemId == 1) Language.EN else Language.RU
                prefs.language = state.language
                render()
                command(Command.LANGUAGE)
                true
            }
            show()
        }
    }

    private fun addDeleteKey(line: LinearLayout) {
        key(line, "⌫", 1.2f, description = "Удалить символ", immediate = true) {
            command(Command.DELETE)
        }.apply { repeatDelete = true; highlight(this) }
    }

    fun updateSuggestions(values: List<String>) {
        cachedCandidates = values
        val show = values.isNotEmpty() && candidatesRow != null && !quickOpen
        candidatesRow?.visibility = if (show) VISIBLE else GONE
        toolsRow?.visibility = if (show) GONE else VISIBLE
        candidateButtons.forEachIndexed { index, button ->
            val text = values.getOrNull(index).orEmpty()
            if (button.text != text) button.text = text
            button.isEnabled = index < values.size
            button.contentDescription = values.getOrNull(index)?.let { "Дополнить: $it" } ?: "Нет предложения"
        }
    }
    fun updateVoice(status: String) {
        voiceStatus = status
        voiceButton?.apply {
            contentDescription = "Голосовой ввод: $status"
            isActivated = status != "Голос"
            (this as InstantKey).toolbarIcon?.setTint(
                if (status == "Голос") prefs.palette.ink else prefs.palette.accent)
            invalidate()
        }
    }
    private fun updateCase() {
        if (state.layer != Layer.LETTERS) return
        state.rows().flatten().forEachIndexed { i, text -> letterButtons.getOrNull(i)?.let { it.text = text; it.contentDescription = text } }
        shiftButton?.text = when { state.capsLock -> "⇪"; state.shift -> "⬆"; else -> "↑" }
    }
    private fun effectVisibility() {
        ambientDrawable?.running = isAttachedToWindow && windowVisibility == VISIBLE && isShown && android.animation.ValueAnimator.areAnimatorsEnabled()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); effectVisibility() }
    override fun onDetachedFromWindow() { ambientDrawable?.running = false; super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); effectVisibility() }
    override fun onVisibilityChanged(changedView: android.view.View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); effectVisibility() }

    private fun quickSettings() {
        val content = LinearLayout(context).apply { orientation = VERTICAL }
        val scroll = android.widget.ScrollView(context).apply { addView(content) }
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(
            if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 150 else 260)))
        rowsHost = content
        fun option(title: String, change: () -> Unit) {
            key(row(), title) { change(); command(Command.REFRESH) }
        }
        option("Тема · ${prefs.palette.title}") { prefs.palette = Palette.entries[(prefs.palette.ordinal + 1) % Palette.entries.size] }
        option("Быстрый ввод · ${if (prefs.instantInput) "вкл" else "выкл"}") { prefs.instantInput = !prefs.instantInput }
        option("Подсказки · ${if (prefs.suggestions) "вкл" else "выкл"}") { prefs.suggestions = !prefs.suggestions }
        option("Живой фон · ${if (prefs.ambient) "вкл" else "выкл"}") { prefs.ambient = !prefs.ambient }
        val editing = row()
        key(editing, "← Курсор") { command(Command.LEFT) }
        key(editing, "Курсор →") { command(Command.RIGHT) }
        val positions = row()
        key(positions, "Левая рука") { prefs.widthPercent = 80; prefs.alignment = -1; command(Command.REFRESH) }
        key(positions, "Полная") { prefs.widthPercent = 100; prefs.alignment = 0; command(Command.REFRESH) }
        key(positions, "Правая рука") { prefs.widthPercent = 80; prefs.alignment = 1; command(Command.REFRESH) }
        val lift = row()
        key(lift, "Ниже −4") { prefs.bottomLift -= 4; command(Command.REFRESH) }
        key(lift, "Выше +4") { prefs.bottomLift += 4; command(Command.REFRESH) }
        key(row(), "Все настройки ↗") { command(Command.SETTINGS) }
        key(row(), "Выбрать другую клавиатуру") { command(Command.NEXT_IME) }
        rowsHost = null
    }

    fun render() {
        removeAllViews()
        letterButtons.clear(); candidateButtons.clear(); shiftButton = null; voiceButton = null; toolsRow = null; candidatesRow = null
        ambientDrawable?.running = false
        setPadding(context.dp(4), context.dp(6), context.dp(4), context.dp(8))
        ambientDrawable = if (prefs.ambient) AmbientDrawable(prefs.palette.canvas) else null
        background = ambientDrawable ?: rounded(prefs.palette.canvas, context.dp(20).toFloat())
        effectVisibility()
        if (prefs.toolbar || quickOpen) {
            val tools = row().also { toolsRow = it }
            tool(tools, R.drawable.tool_menu, if (quickOpen) "Вернуться к клавиатуре" else "Быстрые настройки") {
                quickOpen = !quickOpen; render()
            }
            if (!sensitive) {
                tool(tools, R.drawable.tool_emoji, "Эмодзи") {
                    quickOpen = false
                    state.layer = if (state.layer == Layer.EMOJI) Layer.LETTERS else Layer.EMOJI
                    render()
                }
                tool(tools, R.drawable.tool_paste, "Вставить") { command(Command.PASTE) }
            }
            tool(tools, R.drawable.tool_settings, "Настройки") { command(Command.SETTINGS) }
            if (!sensitive) {
                voiceButton = tool(tools, R.drawable.tool_mic, "Голосовой ввод") { command(Command.VOICE) }
                updateVoice(voiceStatus)
            }
            if (quickOpen) { quickSettings(); return }
        }
        if (prefs.suggestions && !sensitive && state.layer != Layer.EMOJI) {
            val candidates = row().also { candidatesRow = it }
            tool(candidates, R.drawable.tool_menu, "Быстрые настройки") { quickOpen = true; render() }
            repeat(3) { index ->
                val button = key(candidates, "") { cachedCandidates.getOrNull(index)?.let(chooseSuggestion) }
                button.textSize = 14f
                button.layoutParams.height = context.dp(40)
                button.background = rounded(android.graphics.Color.TRANSPARENT, 0f)
                candidateButtons.add(button)
            }
            updateSuggestions(cachedCandidates)
        }
        if (prefs.numbers && state.layer == Layer.LETTERS) {
            val numbers = row()
            "1234567890".forEach { char -> key(numbers, char.toString(), immediate = true) { commit(char.toString()) } }
        }
        val bodyStart = childCount
        if (state.layer == Layer.EMOJI) {
            val height = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) prefs.landscapeHeight else prefs.keyHeight
            val rowHeight = context.dp(height) + 2 * context.dp(3)
            addView(emojiPicker(context, prefs.palette, rowHeight * (if (prefs.numbers) 4 else 3), commit))
        }
        state.rows().forEachIndexed { index, chars ->
            val line = row()
            if (index == 2 && state.layer == Layer.LETTERS) {
                shiftButton = key(line, if (state.capsLock) "⇪" else "↑", 1.3f, false, "Shift; удерживайте для Caps Lock") {
                    state.toggleShift(); updateCase()
                }.also { highlight(it); it.setOnLongClickListener { state.lockCaps(); updateCase(); true } }
            }
            chars.forEach { char ->
                lateinit var button: Button
                button = key(line, char, immediate = true) {
                commit(button.text.toString())
                val previousShift = state.shift
                state.afterCommit()
                if (previousShift != state.shift) updateCase()
                }
                letterButtons.add(button)
            }
        }
        if (state.layer != Layer.EMOJI) {
            addDeleteKey(getChildAt(childCount - 1) as LinearLayout)
        }
        val bottom = row()
        key(bottom, if (state.layer == Layer.LETTERS) "?123" else "ABC", 1.2f) {
            state.layer = if (state.layer == Layer.LETTERS) Layer.SYMBOLS else Layer.LETTERS; render()
        }.setOnLongClickListener { quickOpen = true; render(); true }
        key(bottom, ",", immediate = true) { commit(",") }
        val space = key(bottom, if (state.language == Language.EN) "space" else "пробел", 3.6f,
            description = if (state.language == Language.EN) "Space; hold to change language" else "Пробел; удерживайте для смены языка", immediate = false) { commit(" ") }
        space.spaceLegend = true
        space.setOnLongClickListener { showLanguages(space); true }
        key(bottom, ".", immediate = true) { commit(".") }
        if (state.layer == Layer.EMOJI) addDeleteKey(bottom)
        key(bottom, enterLabel, 1.3f, true, "Ввод: $enterLabel") { command(Command.ENTER) }
        if (state.layer == Layer.SYMBOLS && prefs.numbers) {
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
