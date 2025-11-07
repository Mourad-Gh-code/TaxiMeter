// This file assumes you have defined the necessary aliases (like google.maps, easypermissions, etc.)
// in your project's 'libs.versions.toml' file to follow the standard Kotlin DSL project structure.

plugins {
    // Applying the standard Android application plugin and Kotlin Android plugin
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    // Configuration details adopted from the requested modern structure
    namespace = "com.example.taximeter"
    compileSdk = 36 // Updated to 36

    defaultConfig {
        applicationId = "com.example.taximeter"
        minSdk = 29 // Updated to 29
        targetSdk = 36 // Updated to 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // Setting Java compatibility to 11
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    // Retaining view binding feature from the original file
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Core AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    // Original dependency: CardView
    implementation(libs.androidx.cardview)

    // Original dependency: Google Maps & Location Services
    // These require specific aliases for their 'play-services' modules
    implementation(libs.google.maps) // Assumed alias for play-services-maps
    implementation(libs.google.location) // Assumed alias for play-services-location

    // Original dependency: Easy Permissions
    implementation(libs.easypermissions)

    // Original dependency: QR Code Generation (Zxing)
    implementation(libs.zxing.core)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}