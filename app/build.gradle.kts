plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.kimkinam.camerashit"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kimkinam.camerashit"
        minSdk = 26
        // 34 이상이어야 Android 14+ 에서 ADB 없이 일반 설치가 가능하다.
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
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
