# Android identity 0.6.1

Application ID: dev.nothingtype.keyboard. Visible name: Nothing Type.
versionName 0.6.1, versionCode 10; minSdk 26, targetSdk 36.

Android application IDs require dotted segments containing letters, digits or
underscores; `nothing-type` is not a valid application ID.

The previous package and compatibility shim are no longer registered in the
application. The launcher is dev.nothingtype.app.MainActivity and the IME is
dev.nothingtype.ime.NothingTypeIme. Both are under the new installed package.

This is a separate application for Android. Existing keyboard preferences and
imported fonts remain in the previous app. Enable and select the new keyboard
before removing the previous installation.

The build, tests and lint were rerun after the change. The APK package was
confirmed with aapt. Owner-key signing must be repeated for this new APK;
a signed 0.6.0 APK still contains the previous package identity.
