// Intentionally no root-level plugin declarations.
// This sandbox's network proxy cannot reach dl.google.com (Android/AndroidX artifacts),
// so Android/Compose plugin versions are declared directly in app/build.gradle.kts instead —
// that keeps `./gradlew :core:test` buildable here without ever touching the :app project.
// Android Studio (with full network access) resolves app/build.gradle.kts normally.
