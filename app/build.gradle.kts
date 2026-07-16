plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "in.ac.amuonline.idverifier"
    // compileSdk/targetSdk 35 (Android 15) — apps targeting 35 run correctly, with full
    // OS back-compat handling, on Android 14, 15 and 16 (the current last-3 releases).
    // minSdk 24 keeps the app installable on older phones too without affecting that.
    compileSdk = 35

    defaultConfig {
        applicationId = "in.ac.amuonline.idverifier"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf("-opt-in=androidx.camera.core.ExperimentalGetImage")
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // CameraX
    val cameraxVersion = "1.3.4"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ML Kit on-device barcode scanning (model bundled in APK, works fully offline)
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
}
