# Arabic Flashcards

A native Android app for learning common Arabic phrases with flashcards.

## Features

- 55 everyday Arabic phrases (Arabic script + transliteration + English), grouped
  into categories: Greetings, Basics, Questions, Numbers, Food & Drink,
  Directions, Time, and Emergency.
- Tap a card to flip between the English side and the Arabic side.
- Swipe left/right (or use the Previous/Next buttons) to move through the deck.
- Filter by category with the chip row at the top.
- Mark cards as "mastered" — progress is saved on-device (via Jetpack
  DataStore) so it persists between app launches.
- Shuffle the deck, and reset progress, from the top bar.

Built with Kotlin and Jetpack Compose (Material 3).

## Getting the app onto your phone

### Option A: Download a prebuilt APK (easiest)

Every push to this repo triggers a GitHub Actions build. Once it finishes:

1. Go to the repo's **Actions** tab → the latest **Android Build** run.
2. Download the `arabic-flashcards-debug-apk` artifact and unzip it to get
   `app-debug.apk`.
3. Transfer the APK to your phone (email it to yourself, use a cloud drive,
   or `adb install app-debug.apk` if your phone is plugged in).
4. On your phone, open the APK file. If prompted, allow your file/browser
   app to "install unknown apps" — this is normal for an app not from the
   Play Store.

### Option B: Build and run from Android Studio

1. Open this project folder in [Android Studio](https://developer.android.com/studio).
2. Let it sync Gradle (first sync downloads dependencies).
3. Connect your phone via USB with USB debugging enabled (Settings →
   Developer options), or use an emulator.
4. Click **Run**.

### Option C: Command line

```bash
./gradlew assembleDebug
# APK will be at app/build/outputs/apk/debug/app-debug.apk
```

Requires a local Android SDK (set `ANDROID_HOME` or create
`local.properties` with `sdk.dir=/path/to/Android/sdk`).

## Project structure

```
app/src/main/java/com/arabicflashcards/app/
  data/               Flashcard model, phrase bank, on-device progress store
  ui/                 Compose screen + view model
  ui/theme/           Material 3 theme (colors, typography)
  MainActivity.kt     App entry point
```

## Adding more phrases

Add entries to the `phrases` list in
`app/src/main/java/com/arabicflashcards/app/data/Flashcard.kt`. Each entry
needs a unique `id`, the Arabic text, a transliteration, an English
translation, and a `Category`.
