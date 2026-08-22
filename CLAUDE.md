# Egyptian Flashcards — project rules

## Canonical rule: protect the user's flashcard data

The user's flashcard notebook (cards, tags, study progress, gamification
stats) lives in on-device storage (Jetpack DataStore). It is the entire
point of this app, it is hand-entered by the user, and there is no cloud
copy — the only backup that exists is one the user explicitly created with
the in-app Export feature. Losing it is a real loss for the user, not just
an inconvenience. **This has already happened once** (an uninstall the
assistant advised wiped the user's cards with no working backup) — treat
every future uninstall recommendation as a live risk of repeating that,
not a formality.

**Always instruct the user to install updates over the existing app —
never to uninstall first — unless there is a specific, unavoidable reason,
and if there is, say so explicitly and warn them their data will be wiped
before they do it.**

**Before ever advising an uninstall, for any reason, this is a hard gate,
not a suggestion:**

1. Tell the user to open the app and tap **Export** (top bar, download
   icon), pick a save location, and confirm the file was actually written
   (check it exists, check it's non-trivial in size if they have cards).
2. Only after that export is confirmed to exist may you tell them to
   uninstall.
3. Do not assume the notebook is empty or that "there's probably nothing
   in there yet" — verify, or have the user confirm, before skipping the
   export step. Do not skip this step because the conversation is moving
   fast or the fix seems urgent.

Android only preserves an app's private storage across an **update**
(same `applicationId`, same signing key). Uninstalling — or the OS
refusing to install because the signing key changed — wipes it.

Before telling the user to install any new build, actively check:

1. **Signing key is unchanged.** `app/build.gradle.kts` pins the debug
   signing config to the committed `debug.keystore` at the repo root
   (fixed after an incident where CI generated a fresh random debug
   keystore on every run, silently breaking updates and forcing repeated
   uninstalls). Do not remove this, regenerate `debug.keystore`, or let a
   build fall back to an auto-generated keystore.
2. **`applicationId` is unchanged** (`com.arabicflashcards.app`). Changing
   it makes Android treat the new build as a different app entirely — a
   fresh install, with the old app's data left behind and inaccessible.
3. **Storage keys/format are backward-compatible.** Data is stored as a
   JSON-serialized list under the `cards_json` DataStore key (see
   `AppRepository.kt` / `UserCard.kt`). When adding fields, make them
   optional-with-default on read (see how `tags` was added: missing key →
   empty list) so older saved cards still parse. Never rename or remove
   an existing DataStore key without writing an explicit migration that
   reads the old key and rewrites it under the new one first.

If a change genuinely requires a storage-format migration, write and test
the migration path (read old format → write new format) before shipping —
don't ask the user to re-enter their cards.

## Transliteration convention (card content)

When writing card text (rule cards, vocab, examples), use this hybrid
system consistently — it's what the bundled Tajawal font's custom glyphs
and the app's existing cards are built around:

- Latin diacritics **only for sounds that would otherwise collide with a
  plain English letter already in use**: **ḥ, ṣ, ḍ, ū, ġ, ṭ**. Each of
  these exists specifically to distinguish an emphatic/throaty sound from
  its plain counterpart already spelled with an ordinary letter (ḥ vs h,
  ṣ vs s, ḍ vs d) — that collision risk is the only reason a diacritic
  belongs here. Always the single diacritic glyph, never a plain-English
  substitute for these specific sounds.
- Plain English digraph **`sh`** for ش — deliberately *not* a diacritic
  (`š`). Unlike ḥ/ṣ/ḍ, ش has no competing plain sound in Arabic that "sh"
  could be confused with, and real Arabizi (what Egyptians actually text)
  writes it as "sh" too — a dedicated mark would just be inventing
  complexity a genuinely ambiguous sound doesn't need. (This app used š
  for a while; it was walked back for exactly this reason — see git
  history around the "sh-fix" commits if the reasoning is needed again.)
- Digit **3** for ع (borrowed from Arabizi).
- Apostrophe **'** for the glottal stop/hamza.
- This is distinct from full Arabizi chat-alphabet (`2,3,5,6,7,8/9`),
  which the app's own cards don't use, though the Reading Guide lesson
  teaches it separately as a bridge/recognition skill.

Before finalizing new card text, scan it for any word that shares a root
with another card and check both use the same spelling for that root's
sounds — this exact mistake (mixing `š`/`sh` or a diacritic/digraph pair
for the same sound within one set of cards) has happened before.

## Card content fixes — test small first

Import upserts by id: re-importing a card with an id already on-device
now updates its `english`/`egyptian`/`tags` in place while preserving
`timesReviewed`/`timesCorrect`/`mastered`/`createdAt` (see
`AppRepository.importBackup`). This makes bulk content fixes (e.g. a
transliteration correction across many cards) practical via a single
re-import instead of manual per-card edits — but before generating a
large batch file, generate and send a **one-card test file first** using
the same id/fix pattern, and have the user confirm it actually updated
the live card correctly after installing the build that carries the fix.
Only then generate the full batch. This catches a wrong id, a bad field
name, or an unbuilt app version before it's applied notebook-wide.

## Where things live

- `app/src/main/java/com/arabicflashcards/app/data/` — `UserCard`,
  `AppRepository` (DataStore-backed persistence), `GameStats`,
  `StudyDirection`, `NotebookBackup` (export/import JSON format).
- `app/src/main/java/com/arabicflashcards/app/ui/` — `FlashcardApp.kt`
  (top-level scaffold, stats bar, export/import wiring), `StudyScreen.kt`,
  `ManageCardsScreen.kt`, `TagPill.kt`, and `FlashcardViewModel.kt`.
- Debug builds are auto-published on every push to a stable link:
  `https://github.com/arualaura5/Claude-Work-Android-App/releases/download/latest-debug/app-debug.apk`
  (see `.github/workflows/android-build.yml`).
