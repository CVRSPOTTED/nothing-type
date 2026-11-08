# Подпись и выпуск

Версия в разработке: 0.6.1, versionCode 10. JDK 17, Android SDK 36.

## Ключ владельца

Если ключа ещё нет: Android Studio → открыть проект → Build → Generate Signed
Bundle / APK → APK → Create new. Сохраните JKS вне проекта, например
`D:\NothingType-signing\nothing-type-release.jks`. Срок — минимум 25 лет.
Сохраните ключ и пароли в резервной копии. Не добавляйте их в GitHub.

```powershell
.\scripts\build-release.ps1 -Keystore 'D:\NothingType-signing\nothing-type-release.jks' -Alias 'nothing-type'
```

Пароли вводятся скрыто в вашем терминале и передаются Gradle только на время
процесса. Скрипт восстанавливает прежнее окружение. Подписанный результат:
`app/build/outputs/apk/release/app-release.apk`.
Без переменных подписи `assembleRelease` создаёт только unsigned APK.

Проверка Android SDK:

```powershell
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

Проверьте отпечаток сертификата и установку обновления с тем же ключом.
Release с новым сертификатом не установится поверх debug APK; удаление старой
debug-версии сбросит её настройки. Не переименовывайте applicationId ради подписи.

## Перед публикацией

1. Пройти автоматические и ручные проверки из TESTING.md.
2. Проверить лицензию кода, шрифтов, эмодзи и словарей в THIRD_PARTY.md.
3. Зафиксировать исходники, результаты проверок и тег версии.
4. Собрать и проверить подписанный APK, приложить SHA-256.
5. Обновить снимки и метаданные магазина.

## F-Droid

Рецепт `fdroid/metadata/dev.nothingtype.keyboard.yml` — черновик. Сборку рецепта
нужно отдельно выполнить в актуальной среде fdroidserver после публикации тега.
Обычный F-Droid подписывает сборку своим ключом; это не подпись владельца.
Заявка в каталог и её одобрение не выполняются автоматически этим проектом.
Слова FrequencyWords поставляются под CC BY-SA 4.0 отдельно от MIT-кода.
Источник, изменения и полный текст лицензии находятся в assets/dictionaries.

Документация:
- https://developer.android.com/studio/publish/app-signing
- https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/

С версии 0.6.1 используется dev.nothingtype.keyboard. После установки включите и выберите новый IME в Android. Прежнее приложение можно удалить после проверки нового; его настройки не переносятся автоматически.
