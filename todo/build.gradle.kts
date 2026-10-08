plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.laura.royaltasks"
    compileSdk = 34

    defaultConfig {
        // Never change this: Android would treat a new id as a different app,
        // and the installed app's tasks and XP would be left behind.
        applicationId = "com.laura.royaltasks"
        minSdk = 26
        targetSdk = 34
        // CI's run number, so every published build is newer than the last and
        // the in-app updater can tell (it compares against update.json's version_code).
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0"

        // Short commit and date shown at the bottom of the screen, so it's easy
        // to confirm which build is actually installed.
        val gitSha = System.getenv("GITHUB_SHA")?.take(7) ?: "local"
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
        val buildDate = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString()
        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Same committed keystore as the flashcards app, so every new build
    // installs as an update over the old one and keeps your tasks.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for unit tests (Android's copy is stubbed off-device).
    testImplementation("org.json:json:20240303")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
