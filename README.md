# Nothing Type

[Русский](README.md) · [English](README.en.md)

[![Android build](https://github.com/CVRSPOTTED/nothing-type/actions/workflows/android.yml/badge.svg)](https://github.com/CVRSPOTTED/nothing-type/actions/workflows/android.yml)
[![Android 8+](https://img.shields.io/badge/Android-8%2B-3DDC84)](app/build.gradle.kts)
[![MIT](https://img.shields.io/badge/license-MIT-lightgrey)](LICENSE)

Клавиатура для Android в стиле Nothing OS. Русская и английская раскладки,
офлайн-подсказки и настройка внешнего вида.

[Скачать APK 0.5.0](https://github.com/CVRSPOTTED/nothing-type/releases/download/v0.5.0/nothing-type-0.5.0-debug.apk) · [Все версии](https://github.com/CVRSPOTTED/nothing-type/releases)

**0.5.0 — тестовая версия.** APK подписан отладочным ключом.

| Настройки | Клавиатура |
| --- | --- |
| <img src="screenshots/style.png" alt="Настройки Nothing Type" width="280"> | <img src="screenshots/keyboard.png" alt="Клавиатура Nothing Type" width="280"> |

## Возможности

- RU/EN. Смена языка удержанием пробела или отдельной кнопкой.
- Локальные подсказки и T9. Backspace возвращает слово после автоисправления.
- 3 781 эмодзи, дополнительные символы и голосовой ввод.
- Темы Dark и Light, красный Enter, шрифты Roboto, Geist и импорт Ndot.
- Настройка высоты, ширины, отступов и скругления клавиш.
- Перемещение курсора по пробелу и цветное свечение с реакцией на диктовку.

## Установка

Нужен Android 8.0 или новее.

1. Скачайте и установите APK.
2. Откройте Nothing Type и нажмите **«Включить Nothing Type»**.
3. Нажмите **«Выбрать клавиатуру»** и выберите Nothing Type.

Во вкладке **«Стиль»** находятся настройки. Кнопка **«Проверить ввод»** открывает
поле для проверки клавиатуры.

## Приватность

Подсказки и T9 работают на устройстве. История набранных текстов не сохраняется.
Диктовку обрабатывает системная служба распознавания; ей может потребоваться
интернет. [Подробнее](PRIVACY.md)

## Что ещё в работе

В исходниках 0.6.1 добавлены словари по 10 000 слов для RU/EN и ручной раздел «Мои слова». Полноценный свайп-набор и предсказание следующего слова ещё в работе.
[План развития и сравнение с Gboard](docs/GBOARD_COMPARISON.md)

## Для разработчиков

Kotlin, JDK 17, Android SDK 36.

```bash
git clone https://github.com/CVRSPOTTED/nothing-type.git
cd nothing-type
bash ./gradlew testDebugUnitTest :swipe-research:test lintDebug assembleDebug
```

В Windows используйте `gradlew.bat`.
[Сборка](docs/BUILD.md) · [Проверки](docs/VALIDATION.md) · [Подпись и публикация](docs/RELEASE.md) · [История изменений](CHANGELOG.md)

Исходники распространяются по [MIT](LICENSE). Лицензии шрифтов и других
компонентов перечислены в [THIRD_PARTY.md](THIRD_PARTY.md).

В разработке: 0.6.1 — расширенные локальные словари, свои слова, исправления ввода и диктовки. [План проверок](docs/STABILITY_PLAN.md). Ссылка на скачивание выше ведёт к опубликованной 0.5.0.
