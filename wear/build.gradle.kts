import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.droid.nanitroutines.wear"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.droid.nanitroutines"
        minSdk = 30
        targetSdk = 35
        versionCode = 1002
        versionName = "1.3.2-wear"

        setProperty("archivesBaseName", "NanitRoutines-wear")

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        val homeDir = System.getProperty("user.home")
        val primaryDir = File(homeDir, ".android")
        val keystoreDir = if (primaryDir.exists() && primaryDir.canWrite() || (!primaryDir.exists() && File(homeDir).canWrite())) {
            primaryDir
        } else {
            File(homeDir, ".cache/android-keystore")
        }
        val keystoreFile = File(keystoreDir, "persistent.keystore")

        getByName("debug") {
            storeFile = keystoreFile
            storePassword = "android"
            keyAlias = "appkey"
            keyPassword = "android"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }

        create("release") {
            storeFile = keystoreFile
            storePassword = "android"
            keyAlias = "appkey"
            keyPassword = "android"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // Wear OS Tiles & ProtoLayout Material
    implementation("androidx.wear.tiles:tiles:1.4.0")
    implementation("androidx.wear.protolayout:protolayout:1.2.0")
    implementation("androidx.wear.protolayout:protolayout-material:1.2.0")
    implementation("androidx.wear.protolayout:protolayout-expression:1.2.0")
    implementation("com.google.guava:guava:32.1.3-android")

    // Google Play Services Wearable Data Layer + Direct Cloud Fallback
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
