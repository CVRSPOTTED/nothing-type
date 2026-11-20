# Сборка Nothing Type

Нужны JDK 17, Android SDK Platform 36 и Build Tools 36.0.0.
Задайте `ANDROID_HOME` или укажите SDK в локальном файле `local.properties`.

В Windows используйте путь без кириллицы, например `C:\dev\nothing-type`.
Откройте эту папку в Android Studio и дождитесь Gradle Sync.

```powershell
.\gradlew.bat testDebugUnitTest :swipe-research:test lintDebug assembleDebug
```

В Linux и macOS ту же команду можно запустить через `bash ./gradlew`.
Первый запуск скачивает зависимости.

APK появится в `app/build/outputs/apk/debug/app-debug.apk`.
В GitHub Actions его можно скачать в артефакте `nothing-type-debug`
после успешного прогона. Для просмотра артефактов нужен вход в GitHub.

## Glyph Sync на Nothing Phone (3)

Для локальной проверки скачайте [Glyph Matrix SDK 2.0](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/blob/999b1143a6bff0dd79db11d21b92c15c56a346b1/glyph-matrix-sdk-2.0.aar) у Nothing и ознакомьтесь с [его лицензией](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/blob/999b1143a6bff0dd79db11d21b92c15c56a346b1/LICENSE.md). Укажите путь к AAR при сборке.

```powershell
.\gradlew.bat "-PglyphMatrixSdk=D:\sdk\glyph-matrix-sdk-2.0.aar" assembleDebug
```

После установки откройте вкладку Glyph, включите Glyph Sync и нажмите букву или цифру в поле «Проверить ввод». Режим «Буквы и цифры» выводит последний символ с «Искрами». Режим «Анимация» показывает «Импульс» или «Сканер» без текста. Кнопки под превью проигрывают выбранный эффект на экране. Ползунок яркости также регулирует заднюю матрицу при наборе. Интеграция доступна в локальной отладочной сборке для Phone (3). Обычная сборка и релиз собираются без SDK. Для автоматической проверки на подключённом Phone (3) соберите и установите также `assembleDebugAndroidTest`, затем запустите:

```powershell
adb shell am instrument -w -e glyph_sync_probe true dev.nothingtype.testing/dev.nothingtype.testing.EditorSmokeInstrumentation
```

## Структура

| Папка | Содержимое |
| --- | --- |
| `app/src/main/java/dev/nothingtype/app` | Экраны приложения |
| `app/src/main/java/dev/nothingtype/ime` | Служба ввода и диктовка |
| `app/src/main/java/dev/nothingtype/keyboard` | Клавиатура, раскладки и подсказки |
| `app/src/main/java/dev/nothingtype/settings` | Параметры и шрифты |
| `app/src/main/java/dev/nothingtype/ui` | Общие элементы и обработка касаний |
| `app/src/test` | Тесты JVM и Robolectric |
| `app/src/androidTest` | Проверки на Android |
| `swipe-research` | Экспериментальный свайп-декодер |
| `docs` | Проверки, аудит и выпуск |
| `fastlane`, `fdroid` | Метаданные публикации |

Начиная с 0.6.1 идентификатор Android — `dev.nothingtype.keyboard`. Пакеты кода — `dev.nothingtype`. Android считает это отдельным приложением: старые настройки и выбор клавиатуры автоматически не переносятся.

[Проверка на устройстве](TESTING.md) · [Подпись релиза](RELEASE.md) · [Аудит готовности](ANDROID_QUALITY_AUDIT.md)

Для текущей Windows-машины рабочий проект находится в D:\Projects\NothingType; JDK 17 — в D:\DevTools\jdk-17. Папки .idea и .gradle с местными путями не входят в Git. Новые модули: ime/EditorDeletion — графемы, ime/DictationController — жизненный цикл диктовки, keyboard/DictionaryRepository — фоновая загрузка словарей.
