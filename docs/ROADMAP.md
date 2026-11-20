# Roadmap

## 0.8.0 — personalization and Italian

- Includes the planned 0.7.1 work. Ndot import accepts ZIP, TTF and OTF, and the setup guide and FAQ are available in English and Russian.
- Adds Italian to the app, keyboard and offline dictionaries. Adds NP2(a)SE, four accent choices, the globe key, and themed long-press menus.
- Adds coverage for multilingual input, autocorrect, dictation handling, and native and web editors. The Android 8 editor transition remains under investigation.

## 0.9.0 — swipe typing

The decoder lives in `swipe-research`. The remaining work is to connect it to the keyboard, display candidates, support undo, and measure speed and accuracy.

## Glyph Sync — parallel experiment

The separate Glyph screen supports letters and digits with sparks, animation-only Pulse and Scanner, and adjustable brightness. The IME integration and private-field protections have been exercised on Phone (3). Phone (4a) Pro and a distributable SDK build remain to be validated. See the [local build guide](BUILD.md).

## 1.0.0 — stable release

Test upgrades across real devices, finish accessibility and dictation checks, and resolve compatibility issues. Release an owner-signed APK with verified upgrade instructions and validate the F-Droid recipe.
