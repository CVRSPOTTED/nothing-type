package dev.keyloom.keyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*

class MainActivity : Activity() {
    private lateinit var prefs: Preferences
    private lateinit var page: LinearLayout
    private lateinit var previewHost: FrameLayout
    private lateinit var sample: TextView
    private lateinit var status: TextView
    private val ink = 0xFFF2F2F2.toInt()
    private val muted = 0xFF969696.toInt()
    private val accent = 0xFFFFFFFF.toInt()
    private val surface = 0xFF1C1C1C.toInt()
    private var previewText = "Привет, мир!"
    private var previewPanel: KeyboardPanel? = null
    private val previewDictionary = Suggestions()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Preferences(this)
        previewText = savedInstanceState?.getString("preview") ?: previewText
        val scroll = ScrollView(this).apply { isFillViewport = true; setBackgroundColor(android.graphics.Color.BLACK) }
        page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(32)) }
        scroll.addView(page)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            scroll.setOnApplyWindowInsetsListener { view, insets ->
                val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        // Older Android versions fit the content below system bars automatically.
        setContentView(scroll)
        buildPage()
    }

    override fun onSaveInstanceState(outState: Bundle) { outState.putString("preview", previewText); super.onSaveInstanceState(outState) }
    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) {
            val manager = getSystemService(InputMethodManager::class.java)
            val enabled = manager.enabledInputMethodList.any { it.packageName == packageName }
            val selected = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith("$packageName/") == true
            status.text = when { selected -> "●  Nothing Type выбрана · можно печатать"; enabled -> "●  Включена · осталось выбрать клавиатуру"; else -> "○  Настройте клавиатуру за два шага" }
        }
    }

    private fun heading(title: String, detail: String) {
        page.gap(20)
        page.addView(label(title, 23f, ink).apply { typeface = Typeface.DEFAULT_BOLD })
        page.addView(label(detail, 13f, muted))
    }

    private fun button(parent: LinearLayout, text: String, primary: Boolean = false, onClick: () -> Unit) {
        parent.addView(action(text, if (primary) accent else surface, if (primary) android.graphics.Color.BLACK else ink, 28),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(50)).apply { bottomMargin = dp(8) })
        parent.getChildAt(parent.childCount - 1).setOnClickListener { onClick() }
    }

    private fun buildPage() {
        page.gap(12)
        page.addView(DotTitle(this))
        status = label("", 13f, accent)
        page.addView(status)
        button(page, "01   Включить Nothing Type", true) { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        button(page, "02   Выбрать клавиатуру") { getSystemService(InputMethodManager::class.java).showInputMethodPicker() }

        heading("Почувствуйте клавиши", "Интерактивный предпросмотр · нажмите на клавиши ниже")
        sample = label(previewText, 20f, ink).apply {
            setPadding(dp(18), dp(20), dp(18), dp(20))
            background = rounded(surface, dp(16).toFloat())
            minHeight = dp(84)
            maxLines = 3
            contentDescription = "Текст предпросмотра"
        }
        page.addView(sample)
        page.gap(10)
        previewHost = FrameLayout(this)
        page.addView(previewHost)
        refreshPreview()

        heading("01 / Тема и стиль", "Mono · чёрное и белое. Signal · красный акцент. Light · светлая тема.")
        val themes = LinearLayout(this)
        Palette.entries.forEach { palette ->
            val choice = action(palette.title, palette.canvas, palette.accent)
            themes.addView(choice, LinearLayout.LayoutParams(dp(100), dp(60)).apply { setMargins(dp(3), 0, dp(3), 0) })
            choice.setOnClickListener {
                prefs.palette = palette
                updateThemeChoices(themes)
                refreshPreview()
            }
        }
        page.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(themes) })
        updateThemeChoices(themes)
        slider("Скругление", 0, 24, prefs.radius) { prefs.radius = it }
        slider("Размер символов", 14, 24, prefs.fontSize, "sp") { prefs.fontSize = it }
        slider("Зазор между клавишами", 0, 4, prefs.keyGap) { prefs.keyGap = it }
        toggle("Живой фон · размытые фигуры", prefs.ambient) { prefs.ambient = it }
        heading("02 / Положение на экране", "Системные отступы учитываются автоматически. Запас снизу защищает клавиши от кнопок навигации и переключения языка.")
        slider("Запас под системную панель", 0, 80, prefs.navigationReserve) { prefs.navigationReserve = it }
        slider("Дополнительный подъём", 0, 100, prefs.bottomLift) { prefs.bottomLift = it }
        slider("Ширина клавиатуры", 75, 100, prefs.widthPercent, "%") { prefs.widthPercent = it }
        slider("Боковые поля", 0, 24, prefs.sideMargin) { prefs.sideMargin = it }
        val positions = LinearLayout(this)
        val positionNames = listOf("Слева", "По центру", "Справа")
        fun updatePositions() {
            positionNames.forEachIndexed { index, name ->
                (positions.getChildAt(index) as Button).text = if (prefs.alignment == index - 1) "● $name" else name
            }
        }
        positionNames.forEachIndexed { index, name ->
            positions.addView(action(name, surface, ink, 28).apply {
                setOnClickListener { prefs.alignment = index - 1; updatePositions(); refreshPreview() }
            }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        page.addView(positions); updatePositions()
        page.addView(label("Выравнивание заметно при ширине меньше 100%.\nПустая область под предпросмотром показывает резерв и подъём; системные кнопки в ней не имитируются.", 12f, muted))
        button(page, "Сбросить положение") { prefs.resetPlacement(); recreate() }
        heading("03 / Клавиши и ввод", "Высота для двух ориентаций, цифровой ряд и панель инструментов.")
        slider("Высота клавиш", 40, 60, prefs.keyHeight) { prefs.keyHeight = it }
        slider("Высота в альбомной ориентации", 30, 48, prefs.landscapeHeight) { prefs.landscapeHeight = it }
        toggle("Цифровой ряд", prefs.numbers) { prefs.numbers = it }
        toggle("Панель инструментов", prefs.toolbar) { prefs.toolbar = it }
        toggle("Мгновенный ввод при касании", prefs.instantInput) { prefs.instantInput = it }
        page.addView(label("В быстром режиме символ вводится при касании: отменить его отведением пальца нельзя. Удерживайте пробел для выбора языка, ?123 — для быстрых настроек.", 12f, muted))
        toggle("Локальные подсказки слов", prefs.suggestions) { prefs.suggestions = it }
        page.addView(label("Начните слово и выберите продолжение над клавишами. Словарь RU/EN встроен, без обучения и сети.", 12f, muted))
        heading("04 / Звук и отклик", "Используются системные настройки звука и вибрации.")
        toggle("Тактильный отклик", prefs.haptics) { prefs.haptics = it }
        toggle("Звук нажатия", prefs.sound) { prefs.sound = it }
        button(page, "Настроить доступ к микрофону") { startActivity(Intent(this, VoicePermissionActivity::class.java)) }
        page.addView(label("Нажмите «Голос» в клавиатуре. Распознавание выполняет системная служба; ей может потребоваться интернет. В паролях голосовой ввод скрыт.", 12f, muted))
        heading("Личное остаётся личным", "Нет разрешения на интернет, аналитики и истории ввода. Буфер читается только по нажатию «Вставить»; в полях паролей эта кнопка скрыта.")
        page.addView(label("NOTHING TYPE  /  0.4.2\nНезависимый проект · не продукт Nothing", 12f, muted))
    }

    private fun updateThemeChoices(row: LinearLayout) {
        Palette.entries.forEachIndexed { index, palette ->
            (row.getChildAt(index) as Button).apply {
                text = if (palette == prefs.palette) "✓ ${palette.title}" else palette.title
                contentDescription = "${palette.title}${if (palette == prefs.palette) ", выбрана" else ""}"
            }
        }
    }

    private fun refreshPreview() {
        previewHost.removeAllViews()
        val keepQuick = previewPanel?.quickOpen == true
        val panel = KeyboardPanel(this, prefs, KeyboardState(prefs.language), commit = {
            previewText = (previewText + it).takeLast(240); sample.text = previewText
            updatePreviewSuggestions()
        }, command = {
            when (it) {
                Command.DELETE -> {
                    if (previewText.isNotEmpty()) previewText = previewText.substring(0, previewText.offsetByCodePoints(previewText.length, -1))
                    sample.text = previewText
                }
                Command.ENTER -> { previewText = ""; sample.text = "" }
                Command.REFRESH -> refreshPreview()
                Command.LANGUAGE -> updatePreviewSuggestions()
                Command.VOICE -> {
                    val request = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, if (prefs.language == Language.RU) "ru-RU" else "en-US")
                    }
                    try { startActivityForResult(request, 21) } catch (_: android.content.ActivityNotFoundException) {
                        Toast.makeText(this, "На телефоне нет приложения для голосового ввода в предпросмотре", Toast.LENGTH_LONG).show()
                    }
                }
                else -> Toast.makeText(this, "Эта функция доступна в системной клавиатуре", Toast.LENGTH_SHORT).show()
            }
            updatePreviewSuggestions()
        }, chooseSuggestion = { word ->
            previewText = previewText.dropLast(previewText.takeLastWhile(Char::isLetter).length) + word + " "
            sample.text = previewText; updatePreviewSuggestions()
        }, quickOpen = keepQuick)
        previewPanel = panel
        previewHost.addView(KeyboardFrame(this, prefs, panel, preview = true))
        updatePreviewSuggestions()
    }

    private fun updatePreviewSuggestions() {
        previewPanel?.updateSuggestions(if (prefs.suggestions) previewDictionary.complete(previewText.takeLastWhile(Char::isLetter), prefs.language) else emptyList())
    }

    @Deprecated("Uses framework Activity result dispatch")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 21 && resultCode == RESULT_OK) {
            val words = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull { it.isNotBlank() } ?: return
            previewText = (previewText.trimEnd() + " " + words).trimStart().takeLast(240)
            sample.text = previewText
            updatePreviewSuggestions()
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
            max = maximum - minimum; progress = initial - minimum
            contentDescription = title
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) { val value = progress + minimum; save(value); caption.text = getString(R.string.setting_value, title, value, unit); refreshPreview() }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48)))
    }

    private fun toggle(title: String, value: Boolean, save: (Boolean) -> Unit) {
        card().addView(Switch(this).apply {
            text = title; textSize = 16f; setTextColor(ink); isChecked = value
            setPadding(dp(2), dp(12), dp(2), dp(12)); minHeight = dp(56)
            setOnCheckedChangeListener { _, checked -> save(checked); refreshPreview() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }
}
