# Nothing Type 0.7.0 candidate validation

Package: dev.nothingtype.keyboard · Version code: 11 · Minimum Android: API 26

- 86 app tests and 2 swipe research tests passed. The dictionary tests use the packaged word lists, cover completions and corrections in all eight languages, and check personal words, combining marks, and Turkish casing.
- Android Lint reported no issues. Debug and unsigned release APKs built successfully.
- Android 37 emulator instrumentation passed. The installed IME suggested and inserted French, Hindi, and Arabic words after switching layouts. It corrected a French typo and restored the original on Backspace. The editor and touch checks passed with 1,050 injected taps.
- The eight bundled dictionaries contain 10,000 entries each. The [source and license](../app/src/main/assets/dictionaries/SOURCE.md) ship with the APK.

Before publishing, sign with the existing owner key and verify an upgrade from the previous owner-signed build. Review translations and correction quality with native speakers and run the device checks in [TESTING.md](TESTING.md).
