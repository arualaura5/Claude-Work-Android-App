# Arabic Flashcards

A native Android app for building your own Egyptian Arabic ⇄ English flashcard
notebook and drilling it with active recall.

## Features

- **Your own notebook, not a fixed phrasebook.** The app starts empty — add a
  card whenever you come across a new word or phrase, with English on one
  side and Egyptian Arabic on the other. Edit or delete any card any time.
- **Reversible study direction.** Toggle between "Egyptian → English" (see
  the Egyptian phrase, recall the English) and "English → Egyptian" (see
  English, force yourself to recall the Egyptian) — the setting persists
  between sessions.
- **Active recall flow.** Flip a card to check yourself, then grade it
  "Knew it" or "Didn't know it." A card is marked mastered after 3 correct
  reviews.
- **Light gamification.** Points for every card you get right, a daily
  streak counter, a level that climbs with your points, and a little
  "+10 ⭐" pop animation on each correct answer.
- Swipe left/right (or use the Prev/Next buttons) to browse the deck
  without grading.

Built with Kotlin and Jetpack Compose (Material 3). Your cards and progress
are stored on-device via Jetpack DataStore — nothing is uploaded anywhere.

## Design

A warm Egyptian-inspired palette (Nile teal, terracotta, sand/gold) and the
[Tajawal](https://fonts.google.com/specimen/Tajawal) typeface (SIL Open Font
License, see `licenses/tajawal_OFL.txt`), which renders both Arabic and
Latin text natively. Tajawal itself doesn't include glyphs for some
transliteration diacritics used in Egyptian Arabic transliteration (ū, ḥ,
ġ, ṣ, ṭ, etc.), so the bundled font files add them by composing Tajawal's
own base letters with Tajawal's own accent marks (its macron, dot, and
quote-mark shapes) — no outside typeface involved, so they stay visually
consistent with the rest of the font.

## Getting the app onto your phone

Every push to this repo builds automatically and publishes the APK to a
stable release. Download it directly (no login needed):

**https://github.com/arualaura5/Claude-Work-Android-App/releases/download/latest-debug/app-debug.apk**

1. Open that link on your phone.
2. Tap the downloaded `app-debug.apk`.
3. If prompted "installation blocked," tap **Settings** on that prompt and
   turn on **Allow from this source**, then tap the file again.
4. Tap **Install**, then open **Arabic Flashcards** from your home screen.

### Building from Android Studio instead

1. Open this project folder in [Android Studio](https://developer.android.com/studio).
2. Let it sync Gradle.
3. Connect your phone (USB debugging enabled) or use an emulator, then click **Run**.

### Command line

```bash
./gradlew assembleDebug
# APK will be at app/build/outputs/apk/debug/app-debug.apk
```

Requires a local Android SDK (`ANDROID_HOME` or `local.properties` with
`sdk.dir=/path/to/Android/sdk`).

## Project structure

```
app/src/main/java/com/arabicflashcards/app/
  data/               UserCard model, JSON (de)serialization, DataStore-backed
                       repository (cards, study direction, gamification stats)
  ui/                 Compose screens (Study, My Cards) + view model
  ui/theme/           Material 3 theme (colors, typography)
  MainActivity.kt     App entry point
```
