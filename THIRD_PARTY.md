# Third-party components

Gradle Wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`)
is from Gradle 8.11.1, licensed under Apache License 2.0.

- Source: https://github.com/gradle/gradle/tree/v8.11.1
- License: https://github.com/gradle/gradle/blob/v8.11.1/LICENSE
- Wrapper SHA-256: `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`

Build dependencies are downloaded by Gradle and are not relicensed by this
project. Kotlin is Apache-2.0, Android Gradle Plugin is Apache-2.0, and JUnit 4
is EPL-1.0. No Typebase code or assets are included.

Emoji sequences and English names from [Unicode Emoji 16.0](https://www.unicode.org/Public/emoji/16.0/emoji-test.txt), under the [Unicode License](app/src/main/assets/UNICODE-LICENSE.txt). Glyphs are rendered by Android.

- Roboto Regular uses the [SIL Open Font License 1.1](app/src/main/assets/fonts/Roboto-OFL.txt).
- Geist Regular uses the [SIL Open Font License 1.1](app/src/main/assets/fonts/Geist-OFL.txt).
- Ndot is imported from a file selected by the user and stored locally.

Frequency dictionary data (10,000 words per language) is adapted from
[Hermit Dave / FrequencyWords](https://github.com/hermitdave/FrequencyWords),
based on the OpenSubtitles 2018 corpus. It is licensed separately under
[CC BY-SA 4.0](app/src/main/assets/dictionaries/CC-BY-SA-4.0.txt).
The pinned revision, filtering changes and attribution are recorded in
[the dataset source note](app/src/main/assets/dictionaries/SOURCE.md).
The application code remains MIT. No dictionary data is downloaded at runtime.
