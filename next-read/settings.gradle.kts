// Next Read: its own Gradle build inside the monorepo, so it never touches Royal Miles (repo root)
// or the flashcards app, and their builds never touch it.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "NextRead"
include(":app")
