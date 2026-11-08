package dev.nothingtype.ime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.inputmethod.InputConnection
import android.widget.Toast
import dev.nothingtype.R


/** A cancellable dictation session. Partial phrases are UI-only; only a final result reaches the editor. */
internal class DictationController(
    private val context: Context,
    private val connection: () -> InputConnection?,
    private val shown: () -> Boolean,
    private val sensitive: () -> Boolean,
    private val language: () -> String,
    private val offlineOnly: () -> Boolean,
    private val finishWord: () -> Unit,
    private val status: (String) -> Unit,
    private val level: (Float) -> Unit,
    private val preview: (String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var voiceGeneration = 0
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val voiceTimeout = Runnable {
        stop()
        Toast.makeText(context, context.getString(R.string.voice_timeout), Toast.LENGTH_LONG).show()
    }
    fun stop() {
        val speech = recognizer ?: return
        handler.removeCallbacks(voiceTimeout)
        voiceGeneration++
        recognizer = null
        runCatching { speech.cancel() }
        runCatching { speech.destroy() }
        preview("")
        status(context.getString(R.string.voice_idle))
    }

    fun toggle() {
        if (sensitive()) return
        if (recognizer != null) {
            runCatching { recognizer?.stopListening() }
                .onSuccess { status(context.getString(R.string.voice_wait)) }
                .onFailure { stop(); Toast.makeText(context, R.string.voice_error, Toast.LENGTH_LONG).show() }
            return
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            context.startActivity(Intent(context, VoicePermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val local = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        if (offlineOnly() && !local) {
            Toast.makeText(context, context.getString(R.string.voice_local_missing), Toast.LENGTH_LONG).show(); return
        }
        if (!local && !SpeechRecognizer.isRecognitionAvailable(context)) {
            Toast.makeText(context, context.getString(R.string.voice_missing), Toast.LENGTH_LONG).show(); return
        }
        finishWord()
        val target = connection() ?: return
        startVoiceAttempt(target, local)
    }

    private fun startVoiceAttempt(target: android.view.inputmethod.InputConnection, local: Boolean) {
        val generation = ++voiceGeneration
        fun retrySystem(): Boolean {
            if (!local || offlineOnly() || connection() !== target || !shown() || sensitive() ||
                !SpeechRecognizer.isRecognitionAvailable(context)) return false
            stop()
            startVoiceAttempt(target, false)
            return true
        }
        try {
            val speech = if (local && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                         else SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = speech
            speech.setRecognitionListener(object : RecognitionListener {
                private var listening = false
                private fun active() = generation == voiceGeneration && connection() === target && shown()
                override fun onReadyForSpeech(params: Bundle?) {
                    if (active()) {
                        listening = true
                        status(context.getString(R.string.voice_stop))
                    }
                }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) {
                    if (active() && listening) level(rmsdB)
                }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    listening = false
                    if (active()) status(context.getString(R.string.voice_wait))
                }
                override fun onError(error: Int) {
                    if (generation != voiceGeneration) return
                    if (error in setOf(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
                            SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED)
                        && retrySystem()) return
                    val message = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> context.getString(R.string.voice_audio)
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> context.getString(R.string.voice_busy)
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> context.getString(R.string.voice_no_match)
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> context.getString(R.string.voice_permission)
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> context.getString(R.string.voice_language)
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> context.getString(R.string.voice_network)
                        else -> context.getString(R.string.voice_error_code, error)
                    }
                    stop(); Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                override fun onResults(results: Bundle?) {
                    if (generation != voiceGeneration) return
                    if (active()) {
                        val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull { it.isNotBlank() }
                        if (phrase == null || !target.commitText(phrase, 1)) {
                            Toast.makeText(context, if (phrase == null) context.getString(R.string.voice_empty) else context.getString(R.string.voice_rejected), Toast.LENGTH_LONG).show()
                        }
                    }
                    stop()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (active()) preview(partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            status(context.getString(R.string.voice_listen))
            speech.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            })
            handler.postDelayed(voiceTimeout, 30_000)
        } catch (_: Exception) {
            if (retrySystem()) return
            stop(); Toast.makeText(context, context.getString(R.string.voice_error), Toast.LENGTH_LONG).show()
        }
    }

}
