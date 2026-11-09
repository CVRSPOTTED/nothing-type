package dev.nothingtype.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import dev.nothingtype.ime.VoicePermissionActivity
import dev.nothingtype.keyboard.KlcLayout
import dev.nothingtype.keyboard.Language
import dev.nothingtype.R
import dev.nothingtype.BuildConfig
import dev.nothingtype.settings.KeyboardFont
import dev.nothingtype.settings.KeyboardFonts
import dev.nothingtype.settings.LanguageSwitch
import dev.nothingtype.settings.Palette
import dev.nothingtype.settings.Preferences
import dev.nothingtype.ui.NothingSwitch
import dev.nothingtype.ui.TypeDialogBuilder
import dev.nothingtype.ui.styleDialogInput
import dev.nothingtype.ui.action
import dev.nothingtype.ui.dp
import dev.nothingtype.ui.gap
import dev.nothingtype.ui.label
import dev.nothingtype.ui.rounded

class MainActivity : Activity() {
    private lateinit var prefs: Preferences
    private enum class Tab(val title: Int, val icon: Int) {
        SETUP(R.string.tab_setup, R.drawable.dock_setup),
        SETTINGS(R.string.tab_settings, R.drawable.tool_settings),
        STYLE(R.string.tab_style, R.drawable.dock_style)
    }

    private lateinit var root: FrameLayout
    private var launchOverlay: LaunchPixels? = null
    private var animateOnStart = false
    private var returningFromExternalScreen = false
    private lateinit var page: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var dock: LinearLayout
    private lateinit var testInputButton: Button
    private var tab = Tab.SETUP
    private var importingFont = false
    private var ndotHint: TextView? = null
    private var status: TextView? = null
    private var activationButton: Button? = null
    private var appLanguageButton: Button? = null
    private var offerAppLanguage = false
    private var languageDialog: android.app.AlertDialog? = null
    private val ink = 0xFFF2F2F2.toInt()
    private val muted = 0xFF969696.toInt()
    private val surface = 0xFF1C1C1C.toInt()
    private val canvasColor = android.graphics.Color.BLACK
    private val accent = 0xFFC82230.toInt()
    private val onAccent = android.graphics.Color.WHITE

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLocales.wrap(newBase))

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Preferences(this)
        super.onCreate(savedInstanceState)
        AppLocales.migrateLegacyChoice(this)
        offerAppLanguage = intent.action == Intent.ACTION_MAIN && AppLocales.shouldOfferOnLaunch(this)
        val savedTab = savedInstanceState?.getString("selected_tab")
        tab = Tab.entries.firstOrNull { it.name == savedTab } ?: when {
            savedInstanceState?.getInt("tab") == 1 -> Tab.STYLE
            intent.action == ACTION_OPEN_SETTINGS -> Tab.SETTINGS
            else -> Tab.SETUP
        }
        buildScreen()
        animateOnStart = savedInstanceState == null && intent.action == Intent.ACTION_MAIN
        savedInstanceState?.getInt("scroll_y")?.let { y -> scroll.post { scroll.scrollTo(0, y) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_SETTINGS && tab != Tab.SETTINGS) showTab(Tab.SETTINGS)
    }

    private fun buildScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(canvasColor)
        }
        scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(canvasColor) }
        page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(32)) }
        scroll.addView(page)
        layout.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        testInputButton = action(getString(R.string.test_keyboard), accent, onAccent, 20).apply {
            setOnClickListener { showInputTest() }
        }
        layout.addView(testInputButton, LinearLayout.LayoutParams(-1, dp(48)).apply {
            setMargins(dp(12), dp(4), dp(12), dp(4))
        })
        dock = LinearLayout(this).apply {
            setPadding(dp(6), dp(6), dp(6), dp(6))
            background = rounded(surface, dp(26).toFloat())
        }
        layout.addView(dock, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(12), dp(2), dp(12), dp(8))
        })
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            layout.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        root = FrameLayout(this).apply { addView(layout, FrameLayout.LayoutParams(-1, -1)) }
        setContentView(root)
        showTab(tab)
    }

    override fun onStart() {
        super.onStart()
        if (animateOnStart && !returningFromExternalScreen && !offerAppLanguage) playLaunch()
        animateOnStart = true
    }

    override fun onStop() {
        launchOverlay?.let(root::removeView)
        launchOverlay = null
        super.onStop()
    }

    override fun onPostResume() {
        super.onPostResume()
        if (offerAppLanguage && languageDialog == null) showAppLanguages(firstLaunch = true)
    }

    override fun onDestroy() {
        languageDialog?.dismiss()
        super.onDestroy()
    }

    private fun playLaunch() {
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return
        launchOverlay?.let(root::removeView)
        val overlay = LaunchPixels(this) { finished ->
            if (launchOverlay === finished) {
                root.removeView(finished)
                launchOverlay = null
            }
        }
        launchOverlay = overlay
        root.addView(overlay, FrameLayout.LayoutParams(-1, -1))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected_tab", tab.name)
        outState.putInt("scroll_y", scroll.scrollY)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        returningFromExternalScreen = false
        updateStatus()
    }

    private fun updateStatus() {
        val label = status ?: return
        val manager = getSystemService(InputMethodManager::class.java)
        val enabled = manager.enabledInputMethodList.any { it.packageName == packageName }
        activationButton?.setText(if (enabled) R.string.disable_keyboard else R.string.enable_keyboard)
        label.setText(if (enabled) R.string.status_enabled else R.string.status_setup)
        label.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (enabled) R.drawable.keyboard_status_on else R.drawable.keyboard_status_off, 0, 0, 0
        )
    }

    private fun showTab(destination: Tab) {
        tab = destination
        page.removeAllViews()
        page.gravity = if (destination == Tab.SETUP) android.view.Gravity.CENTER_VERTICAL else android.view.Gravity.TOP
        status = null
        activationButton = null
        appLanguageButton = null
        ndotHint = null
        when (destination) {
            Tab.SETUP -> buildSetup()
            Tab.SETTINGS -> {
                heading(getString(R.string.tab_settings))
                appLanguageButton = button(page, appLanguageLabel()) { showAppLanguages() }
                buildLanguages()
                buildPlacement()
                buildInput()
                heading(getString(R.string.privacy_heading), getString(R.string.privacy_detail))
                page.addView(label(getString(R.string.footer, BuildConfig.VERSION_NAME), 12f, muted))
            }
            Tab.STYLE -> buildStyle()
        }
        testInputButton.visibility = if (destination == Tab.SETUP) View.GONE else View.VISIBLE
        scroll.scrollTo(0, 0)
        updateDock()
    }

    private fun updateDock() {
        dock.removeAllViews()
        Tab.entries.forEach { item ->
            val current = tab == item
            val tint = if (current) onAccent else ink
            val glyph = getDrawable(item.icon)?.mutate()?.apply { setTint(tint) }
            dock.addView(action(getString(item.title), if (current) accent else surface, tint, 20).apply {
                textSize = 11f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                minHeight = dp(58)
                setPadding(dp(3), dp(7), dp(3), dp(6))
                compoundDrawablePadding = dp(4)
                setCompoundDrawablesWithIntrinsicBounds(null, glyph, null, null)
                isSelected = current
                setOnClickListener { if (tab != item) showTab(item) }
            }, LinearLayout.LayoutParams(0, -1, 1f).apply {
                setMargins(dp(2), 0, dp(2), 0)
            })
        }
    }

    private fun heading(title: String, detail: String? = null) {
        page.gap(20)
        page.addView(label(title, 23f, ink).apply { typeface = Typeface.DEFAULT_BOLD })
        if (detail != null) page.addView(label(detail, 13f, muted))
    }

    private fun button(parent: LinearLayout, text: String, primary: Boolean = false, onClick: () -> Unit): Button {
        val control = action(text, if (primary) accent else surface, if (primary) onAccent else ink, 28).apply {
            minHeight = dp(50)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setOnClickListener { onClick() }
        }
        parent.addView(control, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        return control
    }

    private fun buildSetup() {
        page.addView(DotTitle(this, ink))
        status = label("", 13f, ink).apply {
            compoundDrawablePadding = dp(6)
        }.also(page::addView)
        activationButton = button(page, getString(R.string.enable_keyboard), true) {
            openExternalScreen(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        button(page, getString(R.string.choose_keyboard)) { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }
        updateStatus()
    }

    private fun buildStyle() {
        heading(getString(R.string.tab_style))
        val fonts = LinearLayout(this)
        KeyboardFont.entries.forEach { font ->
            val installed = font != KeyboardFont.NDOT || KeyboardFonts.hasNdot(this)
            val selected = font == prefs.keyboardFont && installed
            fonts.addView(action(
                if (!installed) "Ndot ↗" else font.title,
                if (selected) ink else surface,
                if (selected) canvasColor else ink,
                20
            ).apply {
                isSelected = selected
                minHeight = dp(48)
                setPadding(dp(4), dp(10), dp(4), dp(10))
                if (installed) typeface = KeyboardFonts.resolve(this@MainActivity, font)
                else contentDescription = getString(R.string.import_ndot)
                setOnClickListener {
                    if (installed) {
                        prefs.keyboardFont = font
                        updateChoices(fonts, font.ordinal)
                        updateNdotHint()
                    } else pickNdot()
                }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        page.addView(label(getString(R.string.keyboard_font), 16f, ink))
        page.addView(fonts)
        ndotHint = label("", 12f, muted).also(page::addView)
        updateNdotHint()
        page.gap(8)
        page.addView(label(getString(R.string.keyboard_themes), 16f, ink))
        val themes = LinearLayout(this)
        Palette.entries.forEach { palette ->
            val title = getString(if (palette == Palette.DARK) R.string.theme_dark else R.string.theme_light)
            themes.addView(action(title, surface, ink, 20).apply {
                minHeight = dp(48)
                setPadding(dp(4), dp(10), dp(4), dp(10))
                contentDescription = getString(R.string.keyboard_palette_option, title)
                setOnClickListener {
                    prefs.palette = palette
                    updateChoices(themes, palette.ordinal)
                }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        page.addView(themes)
        updateChoices(themes, prefs.palette.ordinal)
        page.gap(8)
        toggle(getString(R.string.red_enter), prefs.redEnter) { prefs.redEnter = it }
        page.addView(label(getString(R.string.red_enter_help), 12f, muted))
        page.gap(12)
        slider(getString(R.string.radius), 0, 24, prefs.radius) { prefs.radius = it }
        slider(getString(R.string.character_size), 14, 24, prefs.fontSize, "sp") { prefs.fontSize = it }
        slider(getString(R.string.key_gap), 0, 4, prefs.keyGap) { prefs.keyGap = it }
        toggle(getString(R.string.live_background), prefs.ambient) { prefs.ambient = it }
        page.addView(label(getString(R.string.glow_help), 12f, muted))
        page.addView(label(getString(R.string.background_help), 12f, muted))
    }

    private fun buildPlacement() {
        heading(getString(R.string.placement), getString(R.string.placement_detail))
        slider(getString(R.string.navigation_reserve), 0, 180, prefs.navigationReserve) { prefs.navigationReserve = it }
        slider(getString(R.string.keyboard_width), 75, 100, prefs.widthPercent, "%") { prefs.widthPercent = it }
        slider(getString(R.string.side_padding), 0, 24, prefs.sideMargin) { prefs.sideMargin = it }
        val positions = LinearLayout(this).apply { layoutDirection = View.LAYOUT_DIRECTION_LTR }
        val positionNames = listOf(getString(R.string.align_left), getString(R.string.align_center), getString(R.string.align_right))
        positionNames.forEachIndexed { index, name ->
            positions.addView(action(name, surface, ink, 28).apply {
                setOnClickListener { prefs.alignment = index - 1; updateChoices(positions, index) }
            }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        page.addView(positions)
        updateChoices(positions, prefs.alignment + 1)
        page.addView(label(getString(R.string.placement_preview), 12f, muted))
        button(page, getString(R.string.reset_placement)) {
            prefs.resetPlacement()
            val y = scroll.scrollY
            showTab(Tab.SETTINGS)
            scroll.post { scroll.scrollTo(0, y) }
        }
    }

    private fun buildLanguages() {
        button(page, getString(R.string.keyboard_languages)) { showKeyboardLanguages() }
        page.addView(label(getString(R.string.language_switch)))
        val languageChoices = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val languageLabels = listOf(R.string.language_switch_hold, R.string.language_switch_button)
        LanguageSwitch.entries.forEachIndexed { index, mode ->
            languageChoices.addView(action(getString(languageLabels[index]), surface, ink, 24).apply {
                minHeight = dp(52)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                setOnClickListener {
                    prefs.languageSwitch = mode
                    updateChoices(languageChoices, index)
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
        page.addView(languageChoices)
        updateChoices(languageChoices, prefs.languageSwitch.ordinal)
        if (resources.configuration.locales[0]?.language == Language.RU.locale.language) {
            toggle(getString(R.string.compact_russian), prefs.compactRussian) { prefs.compactRussian = it }
            page.addView(label(getString(R.string.compact_russian_help), 12f, muted))
        }
    }

    private fun buildInput() {
        heading(getString(R.string.input_heading), getString(R.string.input_detail))
        slider(getString(R.string.key_height), 40, 60, prefs.keyHeight) { prefs.keyHeight = it }
        slider(getString(R.string.landscape_height), 30, 48, prefs.landscapeHeight) { prefs.landscapeHeight = it }
        page.addView(label(getString(R.string.key_size_help), 12f, muted))
        toggle(getString(R.string.number_row), prefs.numbers) { prefs.numbers = it }
        toggle(getString(R.string.toolbar), prefs.toolbar) { prefs.toolbar = it }
        toggle(getString(R.string.instant_input), prefs.instantInput) { prefs.instantInput = it }
        page.addView(label(getString(R.string.input_help), 12f, muted))
        toggle(getString(R.string.local_suggestions), prefs.suggestions) { prefs.suggestions = it }
        page.addView(label(getString(R.string.suggestions_help), 12f, muted))
        toggle(getString(R.string.autocorrect), prefs.autocorrect) { prefs.autocorrect = it }
        page.addView(label(getString(R.string.autocorrect_help), 12f, muted))
        button(page, getString(R.string.personal_dictionary)) { editPersonalDictionary() }
        page.addView(label(getString(R.string.personal_dictionary_help), 12f, muted))
        heading(getString(R.string.feedback_heading), getString(R.string.feedback_detail))
        toggle(getString(R.string.haptic_feedback), prefs.haptics) { prefs.haptics = it }
        toggle(getString(R.string.key_sound), prefs.sound) { prefs.sound = it }
        button(page, getString(R.string.configure_microphone)) { openExternalScreen(Intent(this, VoicePermissionActivity::class.java)) }
        toggle(getString(R.string.offline_voice_only), prefs.offlineVoiceOnly) { prefs.offlineVoiceOnly = it }
        page.addView(label(getString(R.string.voice_help), 12f, muted))
    }

    private fun appLanguageLabel(): String {
        val selected = AppLocales.selected(this)?.title ?: getString(R.string.language_system)
        return getString(R.string.app_language_current, selected)
    }

    private fun showAppLanguages(firstLaunch: Boolean = false) {
        val choices = listOf(null) + AppLocales.languages
        val labels = choices.map { it?.title ?: getString(R.string.language_system) }.toTypedArray()
        languageDialog = TypeDialogBuilder(this)
            .setTitle(R.string.app_language)
            .setCancelable(!firstLaunch)
            .setSingleChoiceItems(labels, choices.indexOf(AppLocales.selected(this))) { dialog, index ->
                offerAppLanguage = false
                dialog.dismiss()
                AppLocales.choose(this, choices[index])
                appLanguageButton?.text = appLanguageLabel()
            }
            .apply { if (!firstLaunch) setNegativeButton(android.R.string.cancel, null) }
            .create().apply {
                setOnDismissListener { languageDialog = null }
                show()
            }
    }

    private fun showKeyboardLanguages() {
        val languages = Language.entries.filter { prefs.layoutTitle(it).isNotEmpty() }
        val chosen = languages.map { it in prefs.enabledLanguages }.toBooleanArray()
        TypeDialogBuilder(this)
            .setTitle(R.string.keyboard_languages)
            .setMultiChoiceItems(languages.map(prefs::layoutTitle).toTypedArray(), chosen) { _, index, checked ->
                chosen[index] = checked
            }
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.manage_layout_import) { _, _ -> showLayoutImport() }
            .show().apply {
                getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val enabled = languages.filterIndexed { index, _ -> chosen[index] }
                    if (enabled.isEmpty()) {
                        Toast.makeText(this@MainActivity, R.string.keep_one_language, Toast.LENGTH_SHORT).show()
                    } else {
                        prefs.enabledLanguages = enabled
                        dismiss()
                    }
                }
            }
    }

    @Suppress("DEPRECATION")
    private fun showLayoutImport() {
        val imported = prefs.layoutTitle(Language.CUSTOM)
        val dialog = TypeDialogBuilder(this)
            .setTitle(R.string.manage_layout_import)
            .setMessage(getString(R.string.layout_import_help) +
                if (imported.isEmpty()) "" else "\n\n" + getString(R.string.imported_layout_name, imported))
            .setPositiveButton(R.string.choose_layout_file) { _, _ ->
                val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }
                startActivityForResult(picker, IMPORT_KLC)
            }
            .setNeutralButton(R.string.open_layout_catalog) { _, _ ->
                val site = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://kbdlayout.info/"))
                try {
                    openExternalScreen(site)
                } catch (_: android.content.ActivityNotFoundException) {
                    Toast.makeText(this, R.string.open_layout_catalog_error, Toast.LENGTH_LONG).show()
                }
            }
        if (imported.isNotEmpty()) {
            dialog.setNegativeButton(R.string.remove_imported_layout) { _, _ ->
                prefs.removeImportedLayout()
                showKeyboardLanguages()
            }
        }
        dialog.show()
    }

    private fun showInputTest() {
        val active = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        if (active?.startsWith("$packageName/") != true) {
            TypeDialogBuilder(this)
                .setTitle(R.string.test_keyboard_title)
                .setMessage(R.string.test_keyboard_enable_first)
                .setPositiveButton(R.string.tab_setup) { _, _ -> showTab(Tab.SETUP) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            return
        }
        val editor = EditText(this).apply {
            styleDialogInput()
            hint = getString(R.string.test_keyboard_hint)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            minLines = 2
        }
        val container = FrameLayout(this).apply {
            setPadding(dp(20), dp(6), dp(20), 0)
            addView(editor)
        }
        val dialog = TypeDialogBuilder(this)
            .setTitle(R.string.test_keyboard_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        editor.requestFocus()
        dialog.show()
    }

    private fun editPersonalDictionary() {
        val editor = EditText(this).apply {
            styleDialogInput()
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            setText(prefs.personalWords.sorted().joinToString("\n"))
            minLines = 3
            maxLines = 8
            filters = arrayOf(android.text.InputFilter.LengthFilter(24500))
            hint = getString(R.string.personal_dictionary_hint)
        }
        val container = FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), dp(8)); addView(editor) }
        TypeDialogBuilder(this).setTitle(R.string.personal_dictionary).setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                prefs.personalWords = editor.text.toString().split(Regex("[\\s,;]+")).toSet()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun updateNdotHint() {
        val hint = ndotHint ?: return
        val message = when {
            !KeyboardFonts.hasNdot(this) -> R.string.ndot_hint
            prefs.keyboardFont == KeyboardFont.NDOT -> R.string.ndot_cyrillic
            else -> 0
        }
        hint.visibility = if (message == 0) View.GONE else View.VISIBLE
        if (message != 0) hint.setText(message)
    }

    private fun updateChoices(row: LinearLayout, selectedIndex: Int) {
        for (index in 0 until row.childCount) {
            val selected = index == selectedIndex
            (row.getChildAt(index) as Button).apply {
                isSelected = selected
                setTextColor(if (selected) canvasColor else ink)
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x337F7F7F),
                    rounded(if (selected) ink else surface, dp(20).toFloat()), null
                )
            }
        }
    }

    private fun openExternalScreen(intent: Intent) {
        returningFromExternalScreen = true
        startActivity(intent)
    }

    private fun pickNdot() {
        if (importingFont) return
        returningFromExternalScreen = true
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }, IMPORT_NDOT)
    }

    companion object {
        private const val IMPORT_NDOT = 22
        private const val IMPORT_KLC = 23
        private const val ACTION_OPEN_SETTINGS = "dev.nothingtype.action.OPEN_SETTINGS"

        internal fun settingsIntent(context: Context) =
            Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    @Deprecated("Uses framework Activity result dispatch")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMPORT_KLC && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val app = applicationContext
            Thread {
                val result = runCatching {
                    val input = requireNotNull(app.contentResolver.openInputStream(uri))
                    input.use(KlcLayout::read).also { Preferences(app).importLayout(it) }
                }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (result.isSuccess) showKeyboardLanguages()
                    else Toast.makeText(this, R.string.layout_import_error, Toast.LENGTH_LONG).show()
                }
            }.start()
            return
        }
        if (requestCode == IMPORT_NDOT && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            importingFont = true
            val context = applicationContext
            Thread {
                val imported = runCatching { KeyboardFonts.importNdot(context, uri) }.isSuccess
                if (imported) Preferences(context).keyboardFont = KeyboardFont.NDOT
                runOnUiThread {
                    importingFont = false
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (imported && tab == Tab.STYLE) showTab(Tab.STYLE)
                    if (!imported) Toast.makeText(this, R.string.ndot_import_error, Toast.LENGTH_LONG).show()
                }
            }.start()
            return
        }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(10), dp(16), dp(10))
        background = rounded(surface, dp(24).toFloat())
        page.addView(this, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) })
    }

    private fun slider(title: String, minimum: Int, maximum: Int, initial: Int, unit: String = "dp", save: (Int) -> Unit) {
        val group = card()
        val caption = label(getString(R.string.setting_value, title, initial, unit), 16f, ink)
        group.addView(caption)
        group.addView(SeekBar(this).apply {
            min = minimum; max = maximum; progress = initial
            contentDescription = caption.text
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val value = progress
                    save(value)
                    caption.text = getString(R.string.setting_value, title, value, unit)
                    contentDescription = caption.text
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
    }

    private fun toggle(title: String, value: Boolean, save: (Boolean) -> Unit) {
        card().addView(NothingSwitch(this).apply {
            text = title; textSize = 16f; setTextColor(ink); isChecked = value
            setPadding(dp(2), dp(12), dp(2), dp(12)); minHeight = dp(56)
            setOnCheckedChangeListener { _, checked -> save(checked) }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
}
