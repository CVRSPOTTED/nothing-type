package dev.keyloom.keyboard

import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.InputMethodSubtype
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

open class KeyloomIme : InputMethodService() {
    private lateinit var prefs: Preferences
    private lateinit var state: KeyboardState
    private var privateField = false
    private var actionId = EditorInfo.IME_ACTION_NONE
    private var actionLabel = "↵"
    private var keyboard: KeyboardPanel? = null
    private val dictionary = Suggestions()
    private val composing = StringBuilder()
    private var allowSuggestions = false
    private var recognizer: SpeechRecognizer? = null
    private var voiceGeneration = 0
    private val voiceHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val voiceTimeout = Runnable {
        stopVoice()
        Toast.makeText(this, "Служба распознавания не ответила. Проверьте микрофон и службу голосового ввода на телефоне.", Toast.LENGTH_LONG).show()
    }

    override fun onCreate() { super.onCreate(); prefs = Preferences(this); state = KeyboardState(prefs.language) }
    override fun onEvaluateFullscreenMode() = false
    override fun onCreateInputView(): View = panel()

    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        super.onStartInput(info, restarting)
        stopVoice()
        composing.clear()
        state = KeyboardState(prefs.language)
        val type = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        privateField = (type == InputType.TYPE_CLASS_TEXT && variation in listOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (type == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD) ||
            (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0)
        allowSuggestions = !privateField && type == InputType.TYPE_CLASS_TEXT &&
            (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS == 0) &&
            variation !in listOf(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI)
        state.layer = when (type) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> Layer.NUMBER
            InputType.TYPE_CLASS_PHONE -> Layer.PHONE
            else -> Layer.LETTERS
        }
        actionId = if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) EditorInfo.IME_ACTION_NONE
                   else info.imeOptions and EditorInfo.IME_MASK_ACTION
        actionLabel = when (actionId) {
            EditorInfo.IME_ACTION_SEARCH -> "Поиск"
            EditorInfo.IME_ACTION_SEND -> "Отпр."
            EditorInfo.IME_ACTION_NEXT -> "Далее"
            EditorInfo.IME_ACTION_DONE -> "Готово"
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
        prefs.language = if (newSubtype?.languageTag?.startsWith("ru") == true) Language.RU else Language.EN
        state.language = prefs.language
        if (isInputViewShown) setInputView(panel())
    }

    private fun panel(quick: Boolean = false): View {
        val keys = KeyboardPanel(this, prefs, state, commit = ::typeText, command = ::command,
            sensitive = privateField, enterLabel = actionLabel, chooseSuggestion = ::acceptSuggestion, quickOpen = quick)
        keyboard = keys
        updateSuggestions()
        return KeyboardFrame(this, prefs, keys)
    }

    private fun updateSuggestions() {
        keyboard?.updateSuggestions(if (allowSuggestions && prefs.suggestions) dictionary.complete(composing.toString(), state.language) else emptyList())
    }
    private fun finishWord() {
        if (composing.isNotEmpty()) currentInputConnection?.finishComposingText()
        composing.clear(); updateSuggestions()
    }
    private fun typeText(text: String) {
        stopVoice()
        val connection = currentInputConnection ?: return
        if (allowSuggestions && prefs.suggestions && text.all(Char::isLetter) && composing.length < 48) {
            composing.append(text)
            connection.setComposingText(composing.toString(), 1)
            updateSuggestions()
        } else {
            finishWord(); connection.commitText(text, 1)
        }
    }
    private fun acceptSuggestion(word: String) {
        if (!allowSuggestions || !prefs.suggestions || composing.isEmpty()) return
        if (word !in dictionary.complete(composing.toString(), state.language)) return
        val connection = currentInputConnection ?: return
        // Selection callbacks can lag behind a tap in the editor. Never replace stale text.
        if (!connection.getSelectedText(0).isNullOrEmpty() || connection.getTextBeforeCursor(composing.length, 0)?.toString() != composing.toString()) {
            finishWord(); return
        }
        connection.commitText("$word ", 1)
        composing.clear(); updateSuggestions()
    }
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        // Cursor notifications may arrive after finishComposingText. A new editor or
        // manual input cancels dictation through its own lifecycle callback.
        if (composing.isNotEmpty() && (newSelStart != newSelEnd || newSelEnd != candidatesEnd)) finishWord()
    }
    override fun onFinishInputView(finishingInput: Boolean) { stopVoice(); finishWord(); super.onFinishInputView(finishingInput) }
    override fun onFinishInput() { stopVoice(); composing.clear(); keyboard?.updateSuggestions(emptyList()); super.onFinishInput() }
    override fun onDestroy() { stopVoice(); super.onDestroy() }

    private fun stopVoice() {
        val speech = recognizer ?: return
        voiceHandler.removeCallbacks(voiceTimeout)
        voiceGeneration++
        recognizer = null
        speech.cancel(); speech.destroy()
        keyboard?.updateVoice("Голос")
    }

    private fun toggleVoice() {
        if (privateField) return
        if (recognizer != null) {
            recognizer?.stopListening(); keyboard?.updateVoice("Жду…"); return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, VoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val local = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        if (!local && !SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "На устройстве нет службы распознавания речи", Toast.LENGTH_LONG).show(); return
        }
        finishWord()
        val target = currentInputConnection ?: return
        startVoiceAttempt(target, local)
    }

    private fun startVoiceAttempt(target: android.view.inputmethod.InputConnection, local: Boolean) {
        val generation = ++voiceGeneration
        fun retrySystem(): Boolean {
            if (!local || currentInputConnection !== target || !isInputViewShown || privateField ||
                !SpeechRecognizer.isRecognitionAvailable(this)) return false
            stopVoice()
            startVoiceAttempt(target, false)
            return true
        }
        try {
            val speech = if (local && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
                         else SpeechRecognizer.createSpeechRecognizer(this)
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                private fun active() = generation == voiceGeneration && currentInputConnection === target && isInputViewShown
                override fun onReadyForSpeech(params: Bundle?) { if (active()) keyboard?.updateVoice("Стоп ■") }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { if (active()) keyboard?.updateVoice("Жду…") }
                override fun onError(error: Int) {
                    if (generation != voiceGeneration) return
                    if (error in setOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                            SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED)
                        && retrySystem()) return
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Не удалось открыть микрофон. Проверьте доступ к микрофону в быстрых настройках телефона."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Микрофон занят. Завершите запись в другом приложении и повторите."
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Речь не распознана. Попробуйте ещё раз."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Разрешите доступ к микрофону в настройках Android."
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Язык недоступен. Установите языковой пакет в службе распознавания."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Службе распознавания требуется соединение с сетью."
                        else -> "Служба распознавания недоступна ($error). Попробуйте ещё раз."
                    }
                    stopVoice(); Toast.makeText(this@KeyloomIme, message, Toast.LENGTH_LONG).show()
                }
                override fun onResults(results: Bundle?) {
                    if (generation != voiceGeneration) return
                    if (active()) {
                        val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull { it.isNotBlank() }
                        if (phrase == null || !target.commitText(phrase, 1)) {
                            Toast.makeText(this@KeyloomIme, if (phrase == null) "Служба не вернула текст. Повторите фразу." else "Поле не приняло распознанный текст.", Toast.LENGTH_LONG).show()
                        }
                    }
                    stopVoice()
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            keyboard?.updateVoice("Слушаю…")
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (state.language == Language.RU) "ru-RU" else "en-US")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
            voiceHandler.postDelayed(voiceTimeout, 30_000)
        } catch (_: Exception) {
            if (retrySystem()) return
            stopVoice(); Toast.makeText(this, "Не удалось запустить голосовой ввод", Toast.LENGTH_LONG).show()
        }
    }

    private fun keyEvent(code: Int) {
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun command(command: Command) {
        val connection = currentInputConnection
        if (command != Command.VOICE) stopVoice()
        if (command !in listOf(Command.DELETE, Command.VOICE)) finishWord()
        when (command) {
            Command.DELETE -> {
                if (composing.isNotEmpty()) {
                    composing.deleteCharAt(composing.lastIndex)
                    connection?.setComposingText(composing.toString(), 1)
                    if (composing.isEmpty()) connection?.finishComposingText()
                    updateSuggestions()
                } else if (!connection?.getSelectedText(0).isNullOrEmpty()) connection?.commitText("", 1)
                else if (connection?.deleteSurroundingTextInCodePoints(1, 0) != true) keyEvent(KeyEvent.KEYCODE_DEL)
            }
            Command.ENTER -> if (actionId in listOf(EditorInfo.IME_ACTION_UNSPECIFIED, EditorInfo.IME_ACTION_NONE)) {
                connection?.commitText("\n", 1)
            } else connection?.performEditorAction(actionId)
            Command.LEFT -> keyEvent(KeyEvent.KEYCODE_DPAD_LEFT)
            Command.RIGHT -> keyEvent(KeyEvent.KEYCODE_DPAD_RIGHT)
            Command.PASTE -> if (!privateField) {
                val clip = getSystemService(ClipboardManager::class.java).primaryClip
                // Explicit action only. Never subscribe to clipboard changes or keep history.
                val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text else null
                if (text != null) connection?.commitText(text, 1)
                else Toast.makeText(this, "В буфере нет текста", Toast.LENGTH_SHORT).show()
            }
            Command.SETTINGS -> startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Command.NEXT_IME -> getSystemService(InputMethodManager::class.java).showInputMethodPicker()
            Command.REFRESH -> setInputView(panel(keyboard?.quickOpen == true))
            Command.VOICE -> toggleVoice()
            Command.LANGUAGE -> updateSuggestions()
        }
    }
}
