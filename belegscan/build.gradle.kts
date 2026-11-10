plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.anja.belegscan"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.anja.belegscan"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // Dokumentenscanner (Kamera, Kantenerkennung, Zuschnitt) – läuft über Google Play-Dienste
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    // Texterkennung, komplett auf dem Gerät
    implementation("com.google.mlkit:text-recognition:16.0.1")
}
