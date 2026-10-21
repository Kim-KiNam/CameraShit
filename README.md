# CameraShit

삼성 갤럭시 카메라 셔터음을 **PC·케이블·USB 디버깅 없이** 끄는 앱.

## 설치
1. [최신 APK 다운로드](https://github.com/Kim-KiNam/CameraShit/raw/main/releases/CameraShit-v0.2.1.apk)
2. 폰에서 APK를 열어 설치 ("출처를 알 수 없는 앱" 허용 필요)
3. 앱을 열고 화면 안내를 따라 1회 설정 (개발자 옵션 → 무선 디버깅 → 알림창에 6자리 코드 입력)

설정이 끝나면 무선 디버깅은 자동으로 꺼지고, 이후 켜기/끄기와 재부팅·업데이트 후 재적용은 코드 입력 없이 동작한다.

## 원리
셔터음은 삼성 설정 `csc_pref_camera_forced_shuttersound_key` (0 = 끔, 1 = 켬) 로 제어된다.
이 키는 일반 앱이 쓸 수 없어서(`WRITE_SECURE_SETTINGS` 가 있어도 거부), 폰 **자기 자신의 무선 디버깅**에
앱이 직접 접속(libadb-android)해서 shell 권한으로 값을 쓴다. 페어링은 한 번만 필요하다.

## 시스템 업데이트 후
재부팅 때 값을 확인해서 풀렸으면 자동으로 다시 적용한다. 페어링이 풀렸거나 설정 키가 막힌 경우에는
알림으로 알려준다.

## 빌드
Android Studio 에서 열거나 `./gradlew assembleDebug`

## 한계
- 삼성 One UI 전용, 시스템 소리 모드가 진동/무음일 때 동작
- Wi-Fi 연결 필요(무선 디버깅)
- 제조사 업데이트로 막힐 수 있음
