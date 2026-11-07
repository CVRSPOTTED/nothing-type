# Сборка и подпись релиза

Для 0.5.0 код версии — 8. Из корня проекта запустите:

```powershell
.\gradlew.bat testDebugUnitTest :swipe-research:test lintDebug assembleRelease
```

Gradle создаёт `app/build/outputs/apk/release/app-release-unsigned.apk`. Подписывайте его ключом владельца приложения. Утилита Android SDK запросит пароли в терминале:

```powershell
apksigner sign --ks "C:\path\to\release.p12" --ks-key-alias "your-alias" --out nothing-type-0.5.0-release.apk app/build/outputs/apk/release/app-release-unsigned.apk
apksigner verify --verbose --print-certs nothing-type-0.5.0-release.apk
```

Если сертификат отличается от сертификата установленного debug APK, Android потребует удалить debug-версию перед первой установкой release. При удалении сбрасываются локальные настройки клавиатуры. Для обновлений release сохраняйте тот же ключ.

## F-Droid

Тексты карточки и список изменений лежат в `fastlane/metadata/android`. Для подачи добавьте иконку и актуальные снимки с Android, опубликуйте исходники с тегом `v0.5.0` и проверьте сборку через `fdroid build`. Черновик рецепта для fdroiddata находится в [fdroid/metadata/dev.keyloom.keyboard.yml](../fdroid/metadata/dev.keyloom.keyboard.yml).

Стандартный процесс F-Droid собирает APK из исходников и подписывает его своим ключом. Для пользовательского ключа в каталоге нужен отдельный процесс воспроизводимой сборки. Перед подачей проверьте актуальную политику включения, лицензии кода и ресурсов, метаданные и рецепт сборки.

Текущий проект использует compileSdk/targetSdk 36. На 7 ноября 2026 года
это требуемый target для нового приложения или обновления в Google Play.
Перед публикацией требуется также закрыть ручные проверки, подготовить
подпись владельца и выполнить требования выбранного канала распространения.
[Требования Google Play](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en), [аудит готовности](ANDROID_QUALITY_AUDIT.md)

[Руководство по подаче](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/) · [Условия включения](https://f-droid.org/docs/Inclusion_Policy/)
