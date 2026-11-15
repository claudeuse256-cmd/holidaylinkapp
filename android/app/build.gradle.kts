plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
    id("app.cash.paparazzi") version "1.3.5"
}

// Single source of truth for the backend address used by the Android app.
// Override without touching any Kotlin file:
//   ./gradlew assembleDebug -PHL_API_BASE_URL=http://192.168.1.20:8080
// or edit HL_API_BASE_URL in android/gradle.properties (also set at runtime in Settings).
val apiBaseUrl: String = (project.findProperty("HL_API_BASE_URL") as String?) ?: "http://127.0.0.1:8080"

// TURN relay for calls. STUN alone cannot cross carrier-grade NAT, which is why a call
// could say "accepted" on both phones and still carry no audio or video. Fill these in
// from a TURN provider (Metered's free tier is enough) in android/gradle.properties —
// no Kotlin change needed. Blank = STUN-only, which only works on the same Wi-Fi.
val turnHost: String = (project.findProperty("HL_TURN_HOST") as String?) ?: ""
val turnUsername: String = (project.findProperty("HL_TURN_USERNAME") as String?) ?: ""
val turnPassword: String = (project.findProperty("HL_TURN_PASSWORD") as String?) ?: ""

android {
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    namespace = "org.holidaylink.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.holidaylink.app"
        minSdk = 26
        targetSdk = 35
        // CI supplies a monotonically increasing code for every GitHub Actions build.
        // Local builds retain the checked-in fallback values.
        versionCode = (project.findProperty("HL_VERSION_CODE") as String?)?.toIntOrNull() ?: 19
        versionName = (project.findProperty("HL_VERSION_NAME") as String?) ?: "2.4.6"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        buildConfigField("String", "TURN_HOST", "\"$turnHost\"")
        buildConfigField("String", "TURN_USERNAME", "\"$turnUsername\"")
        buildConfigField("String", "TURN_PASSWORD", "\"$turnPassword\"")
    }

    // The keystore is intentionally git-ignored. Local builds use it so updates install
    // over the previous HolidayLink APK; CI builds fall back to the standard debug key.
    val holidaylinkKeystore = rootProject.file("holidaylink.jks")
    signingConfigs {
        if (holidaylinkKeystore.exists()) {
            create("holidaylink") {
                storeFile = holidaylinkKeystore
                storePassword = "holidaylink"
                keyAlias = "holidaylink"
                keyPassword = "holidaylink"
            }
        }
    }

    buildTypes {
        debug {
            if (holidaylinkKeystore.exists()) {
                signingConfig = signingConfigs.getByName("holidaylink")
            }
        }
        release {
            if (holidaylinkKeystore.exists()) {
                signingConfig = signingConfigs.getByName("holidaylink")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.biometric:biometric:1.1.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.airbnb.android:lottie-compose:6.2.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("io.github.webrtc-sdk:android:150.7871.01")

    testImplementation("junit:junit:4.13.2")
}


kotlin {
    jvmToolchain(17)
}
