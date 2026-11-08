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

Идентификатор Android `dev.keyloom.keyboard` и старые системные точки входа
сохранены для совместимости обновлений и ярлыков. Основной код находится
в пакетах `dev.nothingtype`.

[Проверка на устройстве](TESTING.md) · [Подпись релиза](RELEASE.md) · [Аудит готовности](ANDROID_QUALITY_AUDIT.md)

Для текущей Windows-машины рабочий проект находится в D:\Projects\NothingType; JDK 17 — в D:\DevTools\jdk-17. Папки .idea и .gradle с местными путями не входят в Git. Новые модули: ime/EditorDeletion — графемы, ime/DictationController — жизненный цикл диктовки, keyboard/DictionaryRepository — фоновая загрузка словарей.
