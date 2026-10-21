plugins {
    id("com.android.application")
}

android {
    namespace = "com.kimkinam.camerashit"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kimkinam.camerashit"
        minSdk = 26
        // 높게 둔다: 설정 쓰기는 앱이 아니라 온디바이스 adb(shell)가 하므로 targetSdk 제한과 무관하고,
        // 낮은 targetSdk 는 Android 14+ 에서 일반 설치가 막힌다.
        targetSdk = 36
        versionCode = 5
        versionName = "0.2.3"
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

dependencies {
    // 온디바이스 무선 디버깅(adb) 클라이언트. GPL-3.0-or-later OR Apache-2.0 이중 라이선스 중 Apache-2.0 로 사용.
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    // adb TLS 인증서 생성
    implementation("com.github.MuntashirAkon:sun-security-android:1.1")
    // TLS 키 재료 내보내기(페어링)에 필요한 Conscrypt
    implementation("org.conscrypt:conscrypt-android:2.5.3")
}
