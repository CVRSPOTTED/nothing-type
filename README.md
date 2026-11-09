# Nothing Type

English · [Русский](README.ru.md)

[![Android build](https://github.com/CVRSPOTTED/nothing-type/actions/workflows/android.yml/badge.svg)](https://github.com/CVRSPOTTED/nothing-type/actions/workflows/android.yml)
[![Android 8+](https://img.shields.io/badge/Android-8%2B-3DDC84)](app/build.gradle.kts)
[![MIT](https://img.shields.io/badge/license-MIT-lightgrey)](LICENSE)

An independent Android keyboard inspired by Nothing OS.

[GitHub releases](https://github.com/CVRSPOTTED/nothing-type/releases)

## Features

- English, Russian, French, German, Hindi, Arabic, Turkish and Spanish. Choose the app language and typing layouts separately.
- Offline suggestions and autocorrect in all eight built-in layouts, with 10,000 dictionary entries per language. Add your own words and use Backspace to undo a correction.
- Switch layouts by holding Space or tapping a dedicated key. Import a custom KLC layout for typing.
- Dark and light keyboard themes, an optional red Enter key, fonts, key sizing and placement.
- Emoji, cursor control on Space and optional dictation through Android’s speech service.

## Setup

Requires Android 8.0 or later. Install the APK, open Nothing Type, then enable and select it in Android. Use **Settings** to choose languages and change typing behavior. Use **Style** for appearance.

Suggestions run on the device. The keyboard keeps no history of what you type. Dictation depends on the installed speech service and may need internet access. [Privacy policy](PRIVACY.md)

## Development

Use JDK 17 and Android SDK 36. Run the Gradle wrapper with testDebugUnitTest, :swipe-research:test, lintDebug and assembleDebug.

[Build](docs/BUILD.md) · [Release and signing](docs/RELEASE.md) · [Validation](docs/VALIDATION_0.7.0.md) · [Changelog](CHANGELOG.md) · [Third-party licenses](THIRD_PARTY.md)

The app code is [MIT](LICENSE). Bundled word lists are [CC BY-SA 4.0](app/src/main/assets/dictionaries/SOURCE.md).
