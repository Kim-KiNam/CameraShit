plugins {
    id("com.android.application")
}

// 화면 없는 초소형 도우미. targetSdk 22 라서 설정 키(csc_pref_camera_forced_shuttersound_key)에 직접 쓸 수 있다.
// 본 앱이 자기 shell 로 `pm install --bypass-low-target-sdk-block` 해서 설치한다.
android {
    namespace = "com.kimkinam.camerashit.helper"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kimkinam.camerashit.helper"
        minSdk = 22
        @Suppress("ExpiredTargetSdkVersion")
        targetSdk = 22
        versionCode = 2
        versionName = "0.2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
