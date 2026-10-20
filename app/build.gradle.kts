plugins {
    id("com.android.application")
}

android {
    namespace = "com.kimkinam.camerashit"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kimkinam.camerashit"
        minSdk = 22
        // targetSdk 23+ 는 비공개 System 키(csc_pref_camera_forced_shuttersound_key)에 쓰면
        // 설정 제공자가 IllegalArgumentException 을 던진다(WRITE_SECURE_SETTINGS 가 있어도).
        // 대신 Android 14+ 에서는 `adb install --bypass-low-target-sdk-block` 로 설치해야 한다.
        @Suppress("ExpiredTargetSdkVersion")
        targetSdk = 22
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
}
