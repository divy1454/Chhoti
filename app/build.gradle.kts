plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// GitHub Actions sets these: every push becomes version 1.0.<run number>, and the app's
// updater compares that number with the latest GitHub Release.
val runNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
val repo = System.getenv("GITHUB_REPOSITORY") ?: "divy1454/chhoti"
val keystorePath: String? = System.getenv("CHHOTI_KEYSTORE")

android {
    namespace = "com.divy.chhoti"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.divy.chhoti"
        minSdk = 26
        targetSdk = 35
        versionCode = runNumber
        versionName = "1.0.$runNumber"
        buildConfigField("String", "GITHUB_REPO", "\"$repo\"")
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("CHHOTI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CHHOTI_KEY_ALIAS")
                keyPassword = System.getenv("CHHOTI_KEY_PASSWORD")
            }
        }
    }

    // lite: installs past Play Protect's sideloading fraud block (no notification reading).
    // full: everything, including reading WhatsApp/Instagram/Telegram notifications.
    flavorDimensions += "edition"
    productFlavors {
        create("lite") {
            dimension = "edition"
            buildConfigField("boolean", "HAS_MESSAGES", "false")
        }
        create("full") {
            dimension = "edition"
            buildConfigField("boolean", "HAS_MESSAGES", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Updates only install if every APK is signed with the same key, so CI must
            // always use the release key. Local builds without it fall back to debug signing.
            signingConfig = if (keystorePath != null) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
