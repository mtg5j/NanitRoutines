import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlinx.kover")
}

android {
    namespace = "com.droid.nanitroutines"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.droid.nanitroutines"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "1.3.1"

        setProperty("archivesBaseName", "NanitRoutines")

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
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

        if (!keystoreFile.exists()) {
            keystoreDir.mkdirs()
            val keytoolBin = File(System.getProperty("java.home"), "bin/keytool").let {
                if (it.exists()) it.absolutePath else "keytool"
            }
            val process = ProcessBuilder(
                keytoolBin, "-genkey", "-v",
                "-keystore", keystoreFile.absolutePath,
                "-alias", "appkey",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "10000",
                "-storepass", "android",
                "-keypass", "android",
                "-dname", "CN=Android, OU=Development, O=Local, L=Local, S=Local, C=US"
            ).redirectErrorStream(true).start()
            process.waitFor()
        }

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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
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
    testImplementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.json:json:20240303")
    testImplementation("org.robolectric:robolectric:4.12.2")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
