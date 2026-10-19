# CameraShit

삼성 갤럭시 카메라 셔터음을 **USB/무선 디버깅 없이** 끄는 앱.

## 원리
`Settings.System` 의 `csc_pref_camera_forced_shuttersound_key` 값을 바꾼다 (0 = 끔, 1 = 켬).
이 키는 System 테이블이라 `WRITE_SETTINGS` 권한(설정에서 사용자가 허용)만으로 쓸 수 있다.
재부팅 시 초기화될 수 있어 `BOOT_COMPLETED` 에서 다시 적용한다.

## 빌드
Android Studio에서 폴더를 열고 Run, 또는 CLI: `./gradlew assembleDebug`
(APK: `app/build/outputs/apk/debug/app-debug.apk`)

## 한계
- 삼성 One UI 전용, 시스템 소리 모드가 진동/무음일 때 동작
- 제조사 업데이트로 막힐 수 있음
