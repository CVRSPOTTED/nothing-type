package dev.nothingtype.ime

import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.text.TextUtils
import android.view.KeyEvent
import android.view.KeyCharacterMap
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast
import java.util.concurrent.Executors
import dev.nothingtype.app.MainActivity
import dev.nothingtype.app.AppLocales
import dev.nothingtype.keyboard.Command
import dev.nothingtype.keyboard.KeyboardFrame
import dev.nothingtype.keyboard.KeyboardPanel
import dev.nothingtype.keyboard.KeyboardState
import dev.nothingtype.keyboard.Language
import dev.nothingtype.keyboard.Layer
import dev.nothingtype.keyboard.DictionaryRepository
import dev.nothingtype.keyboard.WordText
import dev.nothingtype.R
import dev.nothingtype.settings.Preferences

open class NothingTypeIme : InputMethodService() {
    private lateinit var prefs: Preferences
    private lateinit var state: KeyboardState
    private var privateField = false
    private var actionId = EditorInfo.IME_ACTION_NONE
    private var actionLabel = "↵"
    private var customAction = false
    private var rawInput = false
    private var keyboard: KeyboardPanel? = null
    private var dictionary = DictionaryRepository.fallback
    private var dictionaryRequest = 0
    private var dictionaryKey: Pair<Language, Set<String>>? = null
    private var destroyed = false
    private val composing = StringBuilder()
    private data class Correction(val original: String, val replacement: String, var cursorEnd: Int? = null)
    private var undoCorrection: Correction? = null
    private var keepOriginal = false
    private var allowSuggestions = false
    private var allowAutocorrect = false
    private var compositionSupported = true
    private var capsMode = 0
    private val capsExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var capsRequest = 0
    private var recentTyping = ""
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val dictation by lazy {
        DictationController(this, { currentInputConnection }, { isInputViewShown }, { privateField || rawInput },
            { state.languageTag }, { prefs.offlineVoiceOnly }, ::finishWord,
            { keyboard?.updateVoice(it) }, { keyboard?.updateVoiceLevel(it) }, { keyboard?.updateVoicePreview(it) },
            { keyboard?.context ?: AppLocales.forKeyboard(this) })
    }

    override fun onCreate() { super.onCreate(); prefs = Preferences(this); state = KeyboardState(prefs.language); loadDictionary() }
    private fun loadDictionary() {
        val language = state.language
        val key = language to prefs.personalWords
        if (key == dictionaryKey) return
        if (dictionaryKey?.first != language) dictionary = DictionaryRepository.fallback
        dictionaryKey = key
        val request = ++dictionaryRequest
        if (!language.hasDictionary) return
        DictionaryRepository.load(this, language, key.second) { loaded ->
            if (!destroyed && request == dictionaryRequest) { dictionary = loaded; updateSuggestions() }
        }
    }
    override fun onEvaluateFullscreenMode() = false
    override fun onCreateInputView(): View = panel()

    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        super.onStartInput(info, restarting)
        capsRequest++
        recentTyping = ""
        stopVoice()
        if (restarting) currentInputConnection?.finishComposingText()
        composing.clear()
        compositionSupported = true
        undoCorrection = null
        keepOriginal = false
        state = KeyboardState(prefs.language)
        val type = info.inputType and InputType.TYPE_MASK_CLASS
        rawInput = type == InputType.TYPE_NULL
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        privateField = (type == InputType.TYPE_CLASS_TEXT && variation in listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (type == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) ||
            (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
        allowSuggestions = !privateField && type == InputType.TYPE_CLASS_TEXT &&
            (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0) &&
            (info.inputType and InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE == 0) &&
            variation !in listOf(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI)
        allowAutocorrect = allowSuggestions && (info.inputType and InputType.TYPE_TEXT_FLAG_AUTO_CORRECT != 0)
        capsMode = if (!privateField && type == InputType.TYPE_CLASS_TEXT && variation in listOf(
            InputType.TYPE_TEXT_VARIATION_NORMAL, InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE,
            InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE, InputType.TYPE_TEXT_VARIATION_WEB_EDIT_TEXT)) info.inputType and
            (InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_CAP_WORDS or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
            else 0
        state.updateAutomaticShift(alwaysCapitalize || (capsMode != 0 && info.initialCapsMode != 0))
        state.signedNumber = type == InputType.TYPE_CLASS_DATETIME ||
            info.inputType and InputType.TYPE_NUMBER_FLAG_SIGNED != 0
        state.decimalNumber = type == InputType.TYPE_CLASS_DATETIME ||
            info.inputType and InputType.TYPE_NUMBER_FLAG_DECIMAL != 0
        state.layer = when (type) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> Layer.NUMBER
            InputType.TYPE_CLASS_PHONE -> Layer.PHONE
            else -> Layer.LETTERS
        }
        val noEnterAction = info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        customAction = !rawInput && !noEnterAction && !info.actionLabel.isNullOrBlank()
        actionId = when {
            noEnterAction || rawInput -> EditorInfo.IME_ACTION_NONE
            customAction -> info.actionId
            else -> info.imeOptions and EditorInfo.IME_MASK_ACTION
        }
        actionLabel = if (customAction) info.actionLabel.toString() else when (actionId) {
            EditorInfo.IME_ACTION_SEARCH -> getString(R.string.editor_search)
            EditorInfo.IME_ACTION_SEND -> getString(R.string.editor_send)
            EditorInfo.IME_ACTION_NEXT -> getString(R.string.editor_next)
            EditorInfo.IME_ACTION_DONE -> getString(R.string.editor_done)
            EditorInfo.IME_ACTION_GO -> "→"
            else -> "↵"
        }
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        state.language = prefs.language
        setInputView(panel())
    }

    override fun onCurrentInputMethodSubtypeChanged(newSubtype: InputMethodSubtype?) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype)
        stopVoice(); finishWord()
        val tag = newSubtype?.languageTag?.substringBefore('-')
        val selected = prefs.enabledLanguages.firstOrNull { it.tag.substringBefore('-') == tag }
        if (selected != null) prefs.language = selected
        state.language = prefs.language
        if (isInputViewShown) setInputView(panel())
    }

    private fun panel(): View {
        state.importedLayout = if (state.language == Language.CUSTOM) prefs.loadImportedLayout() else null
        if (state.language == Language.CUSTOM && state.importedLayout == null) state.language = Language.EN
        loadDictionary()
        val keys = KeyboardPanel(AppLocales.forKeyboard(this), prefs, state, commit = ::typeText, command = ::command,
            sensitive = privateField || rawInput, enterLabel = actionLabel, chooseSuggestion = ::acceptSuggestion)
        keyboard = keys
        updateSuggestions()
        return KeyboardFrame(this, prefs, keys)
    }

    private fun updateSuggestions() {
        keyboard?.updateSuggestions(if (state.language.hasDictionary && allowSuggestions && prefs.suggestions) dictionary.candidates(composing.toString(), state.language) else emptyList())
    }
    private fun finishWord() {
        undoCorrection = null
        keepOriginal = false
        if (composing.isNotEmpty()) currentInputConnection?.finishComposingText()
        composing.clear(); updateSuggestions()
    }
    private val alwaysCapitalize get() = capsMode and InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS != 0

    private fun setAutomaticShift(enabled: Boolean) {
        state.updateAutomaticShift(enabled)
        keyboard?.updateCase()
    }

    private fun refreshCapsFromEditor() {
        if (destroyed) return
        val request = ++capsRequest
        val mode = capsMode
        if (mode == 0 || alwaysCapitalize) return
        val connection = currentInputConnection ?: return
        // A remote editor can take time to answer. Keep its reads off the touch thread.
        capsExecutor.execute {
            if (request != capsRequest) return@execute
            val enabled = runCatching { connection.getCursorCapsMode(mode) != 0 }.getOrDefault(false)
            mainHandler.post {
                if (!destroyed && request == capsRequest && currentInputConnection === connection) setAutomaticShift(enabled)
            }
        }
    }

    private fun updateCapsAfterTyping(text: String) {
        capsRequest++
        if (capsMode == 0 || alwaysCapitalize) {
            setAutomaticShift(alwaysCapitalize)
            return
        }
        // Predict the next key's case from local typing while selection updates catch up.
        recentTyping = (recentTyping + text.takeLast(128)).takeLast(128)
        if (text.lastOrNull()?.isWhitespace() == true) {
            setAutomaticShift(TextUtils.getCapsMode(recentTyping, recentTyping.length, capsMode) != 0)
            refreshCapsFromEditor()
        } else {
            setAutomaticShift(false)
        }
    }

    private fun typeText(text: String) {
        stopVoice()
        undoCorrection = null
        val connection = currentInputConnection ?: return
        if (rawInput) {
            val flags = KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE
            val events = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray())
            if (events != null) {
                events.forEach { connection.sendKeyEvent(KeyEvent.changeFlags(it, it.flags or flags)) }
            } else {
                connection.sendKeyEvent(KeyEvent(android.os.SystemClock.uptimeMillis(), text,
                    KeyCharacterMap.VIRTUAL_KEYBOARD, flags))
            }
            return
        }
        if (state.language.hasDictionary && compositionSupported && allowSuggestions && (prefs.suggestions || (prefs.autocorrect && allowAutocorrect)) &&
            composing.length + text.length <= 48 && WordText.canAppend(text, state.language, composing.lastOrNull())) {
            keepOriginal = false
            composing.append(text)
            if (!connection.setComposingText(composing.toString(), 1)) {
                // Some custom editors accept commitText but reject composing text.
                // Preserve the whole pending word once and stop composing for this session.
                connection.commitText(composing.toString(), 1)
                composing.clear()
                compositionSupported = false
            }
            updateSuggestions()
        } else {
            if (composing.isEmpty()) {
                connection.commitText(text, 1)
            } else {
                connection.beginBatchEdit()
                try {
                    commitWord(text)
                } finally {
                    connection.endBatchEdit()
                }
            }
        }
        updateCapsAfterTyping(text)
    }
    private fun commitWord(separator: String) {
        val original = composing.toString()
        val correction = if (prefs.autocorrect && allowAutocorrect && !keepOriginal &&
            (separator.isEmpty() || (separator.length == 1 && separator[0] in " \n.,!?;:،؛؟।॥"))) {
            dictionary.correction(original, state.language)?.takeUnless {
                // A manual entry also wins while its new dictionary index is being built.
                val normalized = WordText.normalize(original, state.language)
                prefs.personalWords.any { WordText.normalize(it, state.language) == normalized }
            }
        } else null
        val connection = currentInputConnection ?: return
        if (correction != null && connection.commitText(correction + separator, 1)) {
            undoCorrection = Correction(original, correction + separator)
            composing.clear()
            keepOriginal = false
            updateSuggestions()
        } else {
            finishWord()
            connection.commitText(separator, 1)
        }
    }

    private fun restoreCorrection(): Boolean {
        val correction = undoCorrection ?: return false
        undoCorrection = null
        val connection = currentInputConnection ?: return false
        if (!connection.getSelectedText(0).isNullOrEmpty() ||
            connection.getTextBeforeCursor(correction.replacement.length, 0)?.toString() != correction.replacement) return false
        connection.beginBatchEdit()
        try {
            if (!connection.deleteSurroundingText(correction.replacement.length, 0)) return false
            if (connection.setComposingText(correction.original, 1)) {
                composing.append(correction.original)
                recentTyping = correction.original
                keepOriginal = true
                updateSuggestions()
            } else {
                connection.commitText(correction.original, 1)
            }
        } finally {
            connection.endBatchEdit()
        }
        return true
    }
    private fun acceptSuggestion(word: String) {
        if (!allowSuggestions || !prefs.suggestions || composing.isEmpty()) return
        if (word !in dictionary.candidates(composing.toString(), state.language)) return
        val connection = currentInputConnection ?: return
        // Selection callbacks can lag behind a tap in the editor. Never replace stale text.
        if (!connection.getSelectedText(0).isNullOrEmpty() || connection.getTextBeforeCursor(composing.length, 0)?.toString() != composing.toString()) {
            finishWord(); return
        }
        connection.commitText("$word ", 1)
        undoCorrection = null
        keepOriginal = false
        recentTyping = ""
        updateCapsAfterTyping("$word ")
        composing.clear(); updateSuggestions()
    }
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Cursor notifications may arrive after finishComposingText. A new editor or
        // manual input cancels dictation through its own lifecycle callback.
        if (composing.isNotEmpty()) {
            if (newSelStart == newSelEnd && newSelEnd == candidatesEnd) return
            finishWord()
            recentTyping = ""
        }
        undoCorrection?.let { correction ->
            if (newSelStart != newSelEnd || correction.cursorEnd?.let { it != newSelEnd } == true) {
                undoCorrection = null
            } else {
                correction.cursorEnd = newSelEnd
            }
        }
        refreshCapsFromEditor()
    }
    override fun onFinishInputView(finishingInput: Boolean) {
        capsRequest++; recentTyping = ""; stopVoice(); finishWord()
        super.onFinishInputView(finishingInput)
    }
    override fun onFinishInput() {
        capsRequest++; recentTyping = ""; stopVoice(); undoCorrection = null; keepOriginal = false
        composing.clear(); keyboard?.updateSuggestions(emptyList())
        super.onFinishInput()
    }
    override fun onDestroy() {
        destroyed = true; dictionaryRequest++; capsRequest++
        mainHandler.removeCallbacksAndMessages(null)
        capsExecutor.shutdownNow()
        stopVoice()
        super.onDestroy()
    }

    private fun stopVoice() = dictation.stop()
    private fun toggleVoice() = dictation.toggle()

    private fun keyEvent(code: Int) {
        sendDownUpKeyEvents(code)
    }

    private fun command(command: Command) {
        val connection = currentInputConnection
        capsRequest++
        recentTyping = ""
        if (command != Command.VOICE) stopVoice()
        if (command !in listOf(Command.DELETE, Command.VOICE, Command.ENTER)) finishWord()
        when (command) {
            Command.DELETE -> {
                if (rawInput) { keyEvent(KeyEvent.KEYCODE_DEL); return }
                if (restoreCorrection()) return
                if (composing.isNotEmpty()) {
                    keepOriginal = false
                    composing.deleteCharAt(composing.lastIndex)
                    recentTyping = composing.toString()
                    connection?.setComposingText(composing.toString(), 1)
                    if (composing.isEmpty()) connection?.finishComposingText()
                    updateSuggestions()
                } else if (connection == null || !EditorDeletion.delete(connection)) keyEvent(KeyEvent.KEYCODE_DEL)
            }
            Command.ENTER -> if (rawInput) {
                keyEvent(KeyEvent.KEYCODE_ENTER)
            } else if (!customAction && actionId in listOf(EditorInfo.IME_ACTION_UNSPECIFIED, EditorInfo.IME_ACTION_NONE)) {
                if (composing.isNotEmpty()) commitWord("\n") else connection?.commitText("\n", 1)
                updateCapsAfterTyping("\n")
            } else {
                if (composing.isNotEmpty()) commitWord("") else finishWord()
                connection?.performEditorAction(actionId)
            }
            Command.LEFT -> keyEvent(KeyEvent.KEYCODE_DPAD_LEFT)
            Command.RIGHT -> keyEvent(KeyEvent.KEYCODE_DPAD_RIGHT)
            Command.PASTE -> if (!privateField) {
                val clip = getSystemService(ClipboardManager::class.java).primaryClip
                // Explicit action only. Never subscribe to clipboard changes or keep history.
                val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text else null
                if (text != null) connection?.commitText(text, 1)
                else Toast.makeText(this, getString(R.string.clipboard_empty), Toast.LENGTH_SHORT).show()
            }
            Command.SETTINGS -> startActivity(MainActivity.settingsIntent(this))
            Command.VOICE -> toggleVoice()
            Command.LANGUAGE -> {
                stopVoice()
                finishWord()
                loadDictionary()
            }
        }
    }
}
