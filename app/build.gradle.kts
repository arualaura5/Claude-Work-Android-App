import java.time.LocalDate

/** Short commit of the source this APK was built from, or "unknown" outside a git checkout. */
fun gitSha(): String = try {
    val process = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
        .directory(rootDir)
        .redirectErrorStream(true)
        .start()
    process.inputStream.bufferedReader().readText().trim().take(12).ifEmpty { "unknown" }
} catch (e: Exception) {
    "unknown"
}

fun buildDate(): String = LocalDate.now().toString()

plugins {
    id("com.android.application") version "8.7.0"
    id("org.jetbrains.kotlin.android") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
}

android {
    namespace = "com.laurasheehan.royalmiles"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.laurasheehan.royalmiles"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // Stamped so the running app can say which build it is. Every APK is called
        // app-debug.apk, and without this there is no way to tell one install from the next.
        buildConfigField("String", "GIT_SHA", "\"${gitSha()}\"")
        buildConfigField("String", "BUILD_DATE", "\"${buildDate()}\"")
    }

    // Pinned so every build — including CI, which runs on a fresh machine every time — signs
    // with the same key. Without this, AGP auto-generates a new random debug key per machine,
    // and Android refuses to install an update whose signature doesn't match what's already
    // installed under the same applicationId ("App not installed").
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Version pinned as of this writing — this sandbox couldn't reach Google's Maven repo to
    // confirm the current release, so double-check/bump this in Android Studio if it fails to resolve.
    implementation("androidx.health.connect:connect-client:1.1.0-alpha07")

    val composeBom = platform("androidx.compose:compose-bom:2024.10.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.2")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation(kotlin("test"))
}
