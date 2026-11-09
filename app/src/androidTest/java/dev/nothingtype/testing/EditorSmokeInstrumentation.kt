package dev.nothingtype.testing

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.text.InputType
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import dev.nothingtype.R
import dev.nothingtype.keyboard.Language
import dev.nothingtype.settings.LanguageSwitch
import dev.nothingtype.settings.Preferences

/** Runs the actual installed IME against native text, password and decimal editors. */
class EditorSmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        var code = Activity.RESULT_CANCELED
        val packageName = targetContext.packageName
        val component = ComponentName(packageName, "dev.nothingtype.ime.NothingTypeIme")
        val ime = component.flattenToString()
        fun selectedIme() = shell("settings get secure default_input_method").trim()
        fun listedImes(command: String) = shell(command).lineSequence()
            .mapNotNull { ComponentName.unflattenFromString(it.trim()) }
        var oldIme: String? = null
        var oldHardwareIme: String? = null
        var wasEnabled = true
        var activity: EditorFixtureActivity? = null
        var externalStarted = false
        try {
            uiAutomation.serviceInfo = uiAutomation.serviceInfo.apply {
                flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            oldIme = selectedIme()
            await("Android registers $ime") { component in listedImes("ime list -a -s") }
            wasEnabled = component in listedImes("ime list -s")
            oldHardwareIme = shell("settings get secure show_ime_with_hard_keyboard").trim()
            shell("settings put secure show_ime_with_hard_keyboard 1")
            val enableResult = shell("ime enable $ime 2>&1").trim()
            await("Android enables $ime ($enableResult)") { component in listedImes("ime list -s") }
            val selectResult = shell("ime set $ime 2>&1").trim()
            await("Android selects $ime ($selectResult)") {
                ComponentName.unflattenFromString(selectedIme()) == component
            }
            val editorActivity = startActivitySync(Intent(targetContext, EditorFixtureActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as EditorFixtureActivity
            activity = editorActivity
            val manager = editorActivity.getSystemService(InputMethodManager::class.java)
            fun focus(editor: EditText) {
                await("editor window receives focus") {
                    var focused = false
                    runOnMainSync { focused = editor.hasWindowFocus() && editor.requestFocus() }
                    focused
                }
                await("Android connects the input method to the focused editor") {
                    var active = false
                    runOnMainSync { active = manager.isActive(editor) }
                    active
                }
                // Android 8 can keep the previous editor configuration in the visible IME.
                if (android.os.Build.VERSION.SDK_INT == 26) {
                    runOnMainSync { manager.restartInput(editor) }
                }
                await("Android shows the keyboard for the editor") {
                    runOnMainSync { manager.showSoftInput(editor, 0) }
                    imeRoot() != null
                }
            }
            focus(editorActivity.editor)
            await("capital letter in sentence field") { key("Q", "Й")?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true }
            await("editor receives text") { read(editorActivity.editor).let { it == "Q" || it == "Й" } }
            val initial = read(editorActivity.editor)
            val english = initial == "Q"
            val letter = if (english) "e" else "е"
            val space = targetContext.getString(R.string.space_language_help, if (english) "English" else "Русский")
            val next = initial.lowercase()
            await("keys for rapid typing") { key(letter) != null && key(next) != null && key(space) != null }
            tapSequence(letter, letter, space, next, ".", space, next)
            val expected = "$initial$letter$letter $next. $initial"
            await("rapid typing preserves order, repeated letters and sentence capitalization") {
                read(editorActivity.editor) == expected
            }
            tapWithOverlap(letter, next)
            tapWithOverlap(next, next, secondLiftsFirst = true)
            tapWithOverlap(space, next)
            val overlapping = expected + letter + next.repeat(3) + " " + next
            await("overlapping taps preserve every character in touch order") {
                read(editorActivity.editor) == overlapping
            }
            runOnMainSync {
                editorActivity.editor.setText("")
                editorActivity.editor.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
                manager.restartInput(editorActivity.editor)
            }
            val typo = if (english) "helllo" else "превет"
            val corrected = if (english) "hello " else "привет "
            await("lowercase keys for T9") { key(typo.first().toString()) != null }
            tapSequence(*(typo.map(Char::toString) + space).toTypedArray())
            await("T9 corrects the word on space") { read(editorActivity.editor) == corrected }
            tapSequence(targetContext.getString(R.string.delete_character))
            await("Backspace undoes the T9 correction") { read(editorActivity.editor) == typo }
            tapSequence(space)
            await("space keeps the explicitly restored word") { read(editorActivity.editor) == "$typo " }
            val accurate = if (english) "hello" else "привет"
            // Deliberately land in the visual spacing, then slide slightly before lifting.
            tapSequenceAtEdges(*(accurate.map(Char::toString) + space).toTypedArray())
            await("key spacing accepts taps without missing or reordering characters") {
                read(editorActivity.editor) == "$typo $accurate "
            }
            focus(editorActivity.privateEditor)
            await("password layout") {
                key("q", "й") != null && key("Voice input", "Голосовой ввод", prefix = true) == null
            }
            focus(editorActivity.numberEditor)
            await("decimal layout without a minus key") { key(".") != null && key("-") == null && key("q", "й") == null }
            await("decimal key inserts text") { key("1")?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true }
            await("number editor receives text") { read(editorActivity.numberEditor) == "1" }
            runOnMainSync { manager.hideSoftInputFromWindow(editorActivity.numberEditor.windowToken, 0) }
            await("IME is hidden") { imeRoot() == null }
            focus(editorActivity.editor)
            await("IME restarts in a text editor") { key("q", "Q", "й", "Й") != null }
            runMultilingualDictionarySmoke(editorActivity.editor)
            result.putString("multilingual_dictionaries", "French, Hindi and Arabic")
            runTypingBenchmark(editorActivity.editor, result)
            val external = "${context.packageName}/${ExternalEditorActivity::class.java.name}"
            check("Error" !in shell("am start -W -f 0x10008000 -n $external"))
            externalStarted = true
            await("web editor in the test app") {
                webEditor()?.let { node ->
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    if (bounds.isEmpty) false else {
                        shell("input tap ${bounds.centerX()} ${bounds.centerY()}")
                        true
                    }
                } ?: false
            }
            await("web editor is focused and the keyboard is ready") {
                webEditor()?.isFocused == true && key(next) != null
            }
            check(checkNotNull(key(next)).performAction(AccessibilityNodeInfo.ACTION_CLICK))
            await("web editor receives text") {
                val expectedWebText = "Typed [$next]"
                uiAutomation.windows.asSequence().mapNotNull { it.root }.any { root ->
                    root.packageName?.toString() == context.packageName &&
                        find(root) { it.text?.toString() == expectedWebText } != null
                }
            }
            await("settings button opens the app") {
                key("Settings", "Настройки")?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
            }
            await("Nothing Type settings activity is visible") {
                val label = targetContext.getString(R.string.keyboard_languages)
                uiAutomation.windows.asSequence().mapNotNull { it.root }.any { root ->
                    root.packageName?.toString() == packageName && find(root) { it.text?.toString() == label } != null
                }
            }
            result.putString("stream", "PASS: key gaps and finger drift, T9 and undo, overlapping and rapid taps, capitalization, native and web editors, hide and restart, settings opens app")
            code = Activity.RESULT_OK
        } catch (failure: Exception) {
            val windows = uiAutomation.windows.joinToString { "${it.type}:${it.root?.packageName}" }
            val labels = mutableListOf<String>()
            imeRoot()?.let { root ->
                find(root) { node ->
                    if (node.isClickable) labels.add((node.contentDescription ?: node.text).toString())
                    false
                }
            }
            result.putString("stream", "${failure.stackTraceToString()}\nWindows: $windows\nIME keys: $labels")
        } finally {
            if (externalStarted && webEditor() != null) {
                shell("input keyevent BACK")
                if (webEditor() != null) shell("input keyevent BACK")
            }
            activity?.let { runOnMainSync { it.finish() } }
            if (!oldIme.isNullOrBlank() && oldIme != "null" && oldIme != ime) shell("ime set $oldIme")
            if (!wasEnabled) shell("ime disable $ime")
            oldHardwareIme?.let {
                if (it == "null") shell("settings delete secure show_ime_with_hard_keyboard")
                else shell("settings put secure show_ime_with_hard_keyboard $it")
            }
            finish(code, result)
        }
    }

    private fun read(editor: EditText): String {
        var value = ""
        runOnMainSync { value = editor.text.toString() }
        return value
    }

    private fun runMultilingualDictionarySmoke(editor: EditText) {
        val prefs = Preferences(targetContext)
        val enabled = prefs.enabledLanguages
        val selected = prefs.language
        val mode = prefs.languageSwitch
        val suggestions = prefs.suggestions
        val manager = editor.context.getSystemService(InputMethodManager::class.java)
        fun clearEditor() = runOnMainSync {
            editor.setText("")
            editor.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            manager.restartInput(editor)
        }

        fun press(label: String) = await("press " + label) {
            key(label)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        }

        try {
            prefs.enabledLanguages = listOf(Language.EN, Language.FR, Language.HI, Language.AR)
            prefs.language = Language.EN
            prefs.languageSwitch = LanguageSwitch.BUTTON
            prefs.suggestions = true
            clearEditor()
            val words = listOf(
                Triple(Language.FR, "bonjou", "bonjour"),
                Triple(Language.HI, "नमस्त", "नमस्ते"),
                Triple(Language.AR, "مرحب", "مرحبا")
            )
            for ((language, prefix, word) in words) {
                val switch = targetContext.getString(R.string.switch_to_language, language.title)
                press(switch)
                val activeLayout = targetContext.getString(
                    R.string.switch_to_language,
                    prefs.enabledLanguages[(prefs.enabledLanguages.indexOf(language) + 1) % prefs.enabledLanguages.size].title
                )
                await("new layout for " + language.name) { key(activeLayout) != null }
                prefix.forEach { press(it.toString()) }
                val completion = targetContext.getString(R.string.suggestion_label, word)
                try {
                    await("editor receives prefix for " + language.name) { read(editor) == prefix }
                    await("dictionary completion for " + language.name) {
                        key(completion)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                    }
                } catch (failure: IllegalStateException) {
                    val labels = mutableListOf<String>()
                    imeRoot()?.let { root ->
                        find(root) { node ->
                            node.contentDescription?.toString()?.takeIf { it.startsWith("Complete") }?.let(labels::add)
                            false
                        }
                    }
                    throw IllegalStateException("Dictionary check failed: " + language.name +
                        ", editor=" + read(editor) + ", suggestions=" + labels, failure)
                }
                await("editor receives the suggestion for " + language.name) { read(editor) == "$word " }
                clearEditor()
                if (language == Language.FR) {
                    val space = targetContext.getString(R.string.space_button_help)
                    await("French layout restarts") { key("b") != null && key(space) != null }
                    "bonjjour".forEach { press(it.toString()) }
                    press(space)
                    await("French autocorrect") { read(editor) == "bonjour " }
                    tapSequence(targetContext.getString(R.string.delete_character))
                    await("French correction undo") { read(editor) == "bonjjour" }
                    clearEditor()
                }
            }
        } finally {
            prefs.enabledLanguages = enabled
            prefs.language = selected
            prefs.languageSwitch = mode
            prefs.suggestions = suggestions
            clearEditor()
        }
    }

    private fun runTypingBenchmark(editor: EditText, result: Bundle) {
        runOnMainSync {
            editor.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            editor.setText("")
            editor.requestFocus()
            editor.context.getSystemService(InputMethodManager::class.java).restartInput(editor)
        }
        await("plain editor for typing benchmark") { key("q", "й") != null && key("w", "ц") != null }
        waitForIdleSync()
        val labels = listOf(if (key("q") != null) "q" else "й", if (key("w") != null) "w" else "ц")
        val positions = labels.map { label -> Rect().also { checkNotNull(key(label)).getBoundsInScreen(it) } }
        fun inject(index: Int, sync: Boolean) {
            val rect = positions[index]
            val down = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, rect.exactCenterX(), rect.exactCenterY(), 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try { check(uiAutomation.injectInputEvent(event, sync)) } finally { event.recycle() }
            }
        }
        val expected = StringBuilder()
        val burstStart = SystemClock.elapsedRealtimeNanos()
        repeat(1000) { index -> expected.append(labels[index % 2]); inject(index % 2, false) }
        try {
            await("1000 taps arrive in the original order") { read(editor) == expected.toString() }
        } catch (failure: IllegalStateException) {
            val actual = read(editor)
            val firstMismatch = (0 until minOf(actual.length, expected.length)).firstOrNull { actual[it] != expected[it] }
            error("Burst mismatch: expected=${expected.length}, actual=${actual.length}, firstMismatch=$firstMismatch")
        }
        result.putLong("burst_1000_ms", (SystemClock.elapsedRealtimeNanos() - burstStart) / 1_000_000)
        val timings = mutableListOf<Long>()
        repeat(50) { index ->
            val start = SystemClock.elapsedRealtimeNanos()
            expected.append(labels[index % 2])
            inject(index % 2, true)
            await("sample $index reaches the editor") { read(editor) == expected.toString() }
            timings.add((SystemClock.elapsedRealtimeNanos() - start) / 1_000_000)
        }
        timings.sort()
        result.putInt("verified_taps", expected.length)
        result.putLong("dispatch_to_editor_p50_ms", timings[timings.size / 2])
        result.putLong("dispatch_to_editor_p95_ms", timings[(timings.size * 95 / 100).coerceAtMost(timings.lastIndex)])
    }

    private fun tapSequence(vararg labels: String) {
        val positions = labels.map { label ->
            Rect().also { checkNotNull(key(label)).getBoundsInScreen(it) }
        }
        positions.forEach { bounds ->
            val downTime = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                    bounds.exactCenterX(), bounds.exactCenterY(), 0)
                try {
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    check(uiAutomation.injectInputEvent(event, false))
                } finally {
                    event.recycle()
                }
            }
        }
    }

    private fun tapWithOverlap(first: String, second: String, secondLiftsFirst: Boolean = false) {
        val bounds = listOf(first, second).map { label ->
            Rect().also { checkNotNull(key(label)).getBoundsInScreen(it) }
        }
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, vararg ids: Int) {
            val properties = ids.map { id ->
                MotionEvent.PointerProperties().apply { this.id = id; toolType = MotionEvent.TOOL_TYPE_FINGER }
            }.toTypedArray()
            val coords = ids.map { id ->
                MotionEvent.PointerCoords().apply {
                    x = bounds[id].exactCenterX(); y = bounds[id].exactCenterY()
                    pressure = 1f; size = 1f
                }
            }.toTypedArray()
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, ids.size,
                properties, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            try { check(uiAutomation.injectInputEvent(event, false)) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, 0)
        send(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 0, 1)
        val firstUp = if (secondLiftsFirst) 1 else 0
        send(MotionEvent.ACTION_POINTER_UP or (firstUp shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 0, 1)
        send(MotionEvent.ACTION_UP, 1 - firstUp)
    }

    private fun tapSequenceAtEdges(vararg labels: String) {
        val positions = labels.map { label ->
            Rect().also { checkNotNull(key(label)).getBoundsInScreen(it) }
        }
        positions.forEach { bounds ->
            val downTime = SystemClock.uptimeMillis()
            val x = bounds.exactCenterX()
            val y = bounds.top - 1f
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                    x + if (action == MotionEvent.ACTION_DOWN) 0f else 3f, y, 0)
                try {
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    check(uiAutomation.injectInputEvent(event, false))
                } finally {
                    event.recycle()
                }
            }
        }
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }

    private fun imeRoot(): AccessibilityNodeInfo? = uiAutomation.windows
        .firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }?.root

    private fun webEditor(): AccessibilityNodeInfo? {
        val root = uiAutomation.windows.asSequence().mapNotNull { it.root }
            .firstOrNull { it.packageName?.toString() == context.packageName } ?: return null
        return find(root) { it.isEditable }
    }

    private fun key(vararg names: String, prefix: Boolean = false): AccessibilityNodeInfo? =
        imeRoot()?.let { root ->
            find(root) { node ->
                val label = (node.contentDescription ?: node.text)?.toString().orEmpty()
                node.isClickable && names.any { if (prefix) label.startsWith(it) else label == it }
            }
        }

    private fun find(node: AccessibilityNodeInfo, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (matches(node)) return node
        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            find(child, matches)?.let { return it }
        }
        return null
    }

    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        error("Timed out waiting for $description")
    }
}
