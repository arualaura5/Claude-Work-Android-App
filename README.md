# Royal Miles

A training dashboard for the Royal Parks Half Marathon — Sunday 11 October 2026 — built to schedule
the comeback and gamify actually doing the sessions.

## The plan this generates

Starting point: returning endurance athlete (Ironman 2023, London Marathon 2024), largely sedentary
since last year, most recent run a 5k about five weeks before training restarts. Race is 7 weeks out.

The generator (`core/.../plan/TrainingPlanGenerator.kt`) builds Monday-to-Sunday weeks, numbered 1..N,
working backward from race day:

- **Weeks 1–2 (Base):** 0–5km easy runs only, plus strength x2, yoga, and an optional easy cycle.
  No long run stress while the aerobic base rebuilds.
- **Weeks 3–5 (Build):** a weekend long run is introduced and grows each week (roughly 8 → 11 → 14km),
  easy runs stay short and easy, strength/yoga continue.
- **Week 6 (Peak):** long run peaks at 17km, one week before race day — exactly matching the "peak the
  week before" plan. This is deliberately aggressive for a 7-week runway; if week 5's long run feels
  rough, dial the peak back (edit that session directly in the app — nothing about the structure breaks).
- **Week 7 (Taper):** short easy runs and rest, a shakeout run with strides, then race day.

This split (2 base / 3 build / 1 peak / 1 taper) and the long-run progression are covered by unit tests
in `core/src/test/.../TrainingPlanGeneratorTest.kt` — run them with `./gradlew :core:test`.

## Gamification

`core/.../gamification/Gamification.kt`:
- **XP** per completed session, weighted so long runs and the race itself are worth more than an easy
  run of the same distance.
- **Levels** from "Couch to Comeback" up to "Race Ready".
- **Streaks** track consecutive real-world days with something logged.
- **Badges** for milestones like clearing the base phase, conquering the peak long run, a 10km+ run,
  and finishing the race.

Also covered by unit tests in `GamificationEngineTest.kt`.

## Why it's built this way

The point isn't a leaderboard or a competitive score — it's staying accountable to the plan and
having something that notices you rebuilding fitness that was hard to lose. That shaped a few
concrete choices:

- **A gentle evening nudge, not a nag.** `notifications/TrainingReminderWorker.kt` runs once a day
  around 7pm and stays completely silent unless today has an unlogged, non-optional session — no
  notification just to remind you the app exists.
- **Affirmations, not just a checkbox flip.** Marking a session complete surfaces a small, varied,
  low-key message ("Logged. That's rebuilding.") rather than a generic "Task complete."
- **An effort/soreness tag, not just done/not-done.** Completing a session asks "how did that feel?"
  (Rough → Great). The dashboard plots the last 10 as a small trend — an early, honest signal if
  training is trending sore rather than fresh, which matters given how aggressive the 7-week runway
  already is.
- **Sync from Strava/Garmin/Google Fit via Health Connect**, so a real run doesn't also have to be
  retyped by hand. See the dedicated section below — this piece needed a scope trade-off.

## Editing the plan

Every session is a row in the database, not a fixed template — the generator only seeds the initial
plan on first launch. From the app you can edit a session's type, date, distance, duration, notes, mark
it optional, reassign it to a different week, delete it, or add a brand new one. The week/phase
structure (1..7, base → build → peak → taper) stays as scaffolding underneath so the app still knows
what "the peak long run" or "the base phase" means for badges even after you've moved things around.

## Project structure

- **`core/`** — pure Kotlin/JVM module: the plan generator and gamification engine, with no Android
  dependency. Fully unit tested and verified to build with `./gradlew :core:test`.
- **`app/`** — the Android app: Room persistence (`data/`), Jetpack Compose UI (`ui/`), navigation
  (`navigation/`). Dashboard shows today's session(s), XP/level, streak, and badges; Calendar lists
  every week; tapping a session opens an editable detail screen.

## Health Connect sync — scope and caveats

True Strava/Garmin API integration needs *you* to register a developer app with each of them and get
a client ID/secret — not something I can do on your behalf. The buildable, secret-free alternative is
**Health Connect**, Android's on-device health data hub: if Strava, Garmin Connect, or Google Fit are
set to write to it (a toggle inside each of those apps), Royal Miles can read the resulting workouts
and offer to match one to a planned session (`ui/sync/`, reachable from the sync icon on the
dashboard).

Two deliberate simplifications, both worth knowing about:
- **Distance isn't auto-filled**, only date, duration, and a guessed activity type. Health Connect
  stores distance as a separate, route-shaped record with a fussier API; matching already removes
  most of the "did I actually log today's run" friction, and you confirm distance in the normal edit
  screen afterward.
- **The Health Connect client library version in `app/build.gradle.kts` is pinned from memory** — this
  sandbox couldn't reach `dl.google.com` to confirm the current release. Check/bump it in Android
  Studio if it fails to resolve. For a sideloaded personal app (not Play Store distribution) the
  permission flow used here is sufficient; publishing to Play would additionally need a permissions-
  rationale activity, which isn't built here since it isn't needed for your use case.

### Nutrition (Cronometer)

The dashboard also reads today's logged calories/protein/carbs/fat straight from Health Connect if
Cronometer is set to write there (`HealthConnectRepository.nutritionSummaryForToday()`). This is
**deliberately kept separate from everything else in the app**: it's not wired into XP, streaks, or
badges, has no calorie target or "remaining" framing, and isn't color-coded as good/bad — it's just a
plain readout of what was logged, shown only when something has actually been logged that day. That
was an explicit request, not an oversight — worth respecting if this gets extended later.

### A note on verification

This was built in a sandbox without an Android SDK and without network access to `dl.google.com`
(where Android/AndroidX artifacts live), so the `app` module's Compose/Room code could not actually be
compiled or run here — only hand-reviewed. The `core` module has no such dependency and every claim
about the plan's structure above is backed by a passing test. Open `app/` in Android Studio to build,
resolve dependencies, and run it on a device or emulator before trusting the UI layer.

## Design

Purple, gold, pink, silver, navy — dark, shimmering, celebratory rather than clinical. Session types
each get a consistent accent color (long runs = purple, strength = pink, yoga = green, cycle = gold,
race = orange) used across the dashboard and calendar. The XP bar and unlocked badges get an animated
shimmer sweep.

## Building

Open the project root in Android Studio (Ladybird/Koala or newer). It will resolve the Android Gradle
Plugin, Compose, and Room dependencies from Google's Maven repo and build normally. The race date and
peak long run distance are set in `app/src/main/java/com/laurasheehan/royalmiles/RaceConfig.kt`.
