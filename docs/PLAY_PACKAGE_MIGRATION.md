# 첫 Play 등록용 앱 ID 전환

사용자는 첫 Google Play 등록이라고 확인했고, LoopMuse 이름을 알아볼 수 있는 고유 ID를 원한다. 이어서 고유 번호 `7205`를 포함한 `loopmuse7205`를 지정했다. 작업 후보는 **`com.loopmuse7205.music`**이다. 앞서 안내한 `com.loopmuse.music`은 폐기된 후보이므로 Firebase에 그 이름으로 새 앱을 등록하지 않는다. 새 후보는 [Android 앱 ID 형식](https://developer.android.com/build/configure-app-module)에 맞지만 [Google Play에서 실제 사용 가능 여부](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en&rd=2)는 첫 번들 등록 과정에서 최종 확인한다.

**등록 대상 구분:** 기존 Firebase **프로젝트** `loopmuse-9991f`는 그대로 사용한다. 이 프로젝트 안의 기존 Android 앱 등록 `com.example.loopmuse`는 패키지 이름을 바꿀 수 없으므로, 새 패키지 `com.loopmuse7205.music`의 Android 앱 항목을 **추가**한다. 기존 앱 항목을 지울 필요는 없다. 같은 프로젝트의 Authentication과 Firestore를 계속 사용한다. 이는 Google Play에 지금 새 앱을 올리는 작업과 별개다. [Firebase 공식 안내](https://firebase.google.com/docs/android/setup)

## 사용자 데이터에 미치는 영향

현재 설치된 `com.example.loopmuse`와 새 `com.loopmuse7205.music`은 Android에서 서로 다른 앱이다. 기존 테스트 앱의 재생 목록·알람·백업 설정은 새 앱으로 자동 이동하지 않는다. 기존 앱을 지우기 전에 앱의 백업 기능으로 데이터를 내보내고 새 앱에서 복원되는지 확인해야 한다. 기존 Firebase 프로젝트와 Lounge 데이터는 유지할 수 있지만, 새 앱의 Google 로그인 설정을 별도로 추가해야 한다. 기존 Firebase Android 앱은 삭제하지 않는다.

## 적용 순서

1. Firebase 프로젝트 `loopmuse-9991f`에 Android 앱 `com.loopmuse7205.music`을 **추가**하고, 현재 디버그 인증서 SHA-1 `9C:13:30:AB:B8:09:40:00:F3:50:DE:0B:FC:C7:20:32:EE:A2:38:EC`을 등록한다. 같은 프로젝트의 Google 로그인 제공업체는 유지한다. [Firebase Android 앱 등록](https://firebase.google.com/docs/android/setup), [Google 로그인](https://firebase.google.com/docs/auth/android/google-signin)
2. Firebase에서 새 `google-services.json`을 다운로드해 `app/google-services.json`으로 교체한다. 파일에 새 패키지와 Android·웹 OAuth 클라이언트가 있는지 확인한다.
3. `app/build.gradle`의 `applicationId`를 `com.loopmuse7205.music`으로 바꾸고 새 디버그 APK를 빌드한다. 첫 로그인, Lounge, 앱 재실행을 확인한다. 코드의 `namespace`는 별개이므로 이 단계에서 바꿀 필요가 없다.
4. 전용 업로드 키를 안전한 위치에 생성하고 별도 백업한다. 출시용 AAB는 그 키로 서명한다. 업로드 키와 나중에 Play가 표시하는 앱 서명 인증서의 SHA 지문을 Firebase 새 Android 앱에 등록한다. 비밀번호·개인 키는 저장소나 대화 기록에 넣지 않는다. [Android 앱 서명 안내](https://developer.android.com/studio/publish/app-signing)
5. Play Console에서 고유 ID가 받아들여지는지 확인한다. 중복 등으로 사용할 수 없다면 Firebase 등록과 앱 ID를 함께 다시 조정한다.

2026-09-29 첫 복사 확인 때는 `app/google-services.json`에 기존 Android 패키지만 있었다. 이후 사용자가 Firebase 프로젝트에 `com.loopmuse7205.music`을 추가하고 파일을 다시 복사했다. 새 파일에는 기존·신규 앱 항목과 신규 앱의 웹 OAuth 클라이언트가 확인돼 `app/build.gradle`의 `applicationId`를 `com.loopmuse7205.music`으로 변경했다. `namespace`와 Kotlin 패키지는 기존 이름을 유지한다.

**Google 로그인 확인:** 사용자가 새 앱 항목에 디버그 SHA-1을 추가하고 `app/google-services.json`을 다시 복사했다. 현재 파일에는 `com.loopmuse7205.music`용 Android OAuth 클라이언트와 SHA-1 `9C:13:30:AB:B8:09:40:00:F3:50:DE:0B:FC:C7:20:32:EE:A2:38:EC`, 웹 OAuth 클라이언트가 있고 `applicationId`와 일치한다. Android Studio가 2026-09-29 20:08에 새 디버그 APK를 생성했고 APK의 패키지·서명 SHA-1·Google 로그인 리소스를 확인했다. 사용자는 가상폰에서 Google 로그인·앱 실행과 앱 재실행 시 바로 메인 화면 진입을 확인했다. 오프라인 재실행은 아직 확인되지 않았다. 업로드 키와 Play 앱 서명 SHA는 출시 준비 때 추가한다. [Firebase Google 로그인 안내](https://firebase.google.com/docs/auth/android/google-signin)

## 새 앱의 디버그 SHA-1 등록 방법

아래는 이번에 완료한 설정 절차의 기록이다.

1. [Firebase 프로젝트 설정](https://console.firebase.google.com/project/loopmuse-9991f/settings/general)을 열어 프로젝트 ID가 `loopmuse-9991f`인지 확인한다.
2. **일반** 탭 아래 **내 앱**에서 Android 앱 `com.loopmuse7205.music`을 선택한다. 기존 `com.example.loopmuse` 항목에 등록된 SHA-1은 새 앱으로 자동 이전되지 않는다.
3. 선택한 새 앱의 **SHA 인증서 지문**에서 **디지털 지문 추가**를 눌러 `9C:13:30:AB:B8:09:40:00:F3:50:DE:0B:FC:C7:20:32:EE:A2:38:EC`을 붙여 넣고 저장한다.
4. 같은 새 앱 항목에서 갱신된 `google-services.json`을 다운로드한다. Finder에서 `/Users/geumbogju/StudioProjects/loopmuse/app/`으로 이동해 기존 `google-services.json`을 대치한다. 파일 이름에 `(1)` 등의 접미사가 남지 않게 한다.
5. 새 파일의 `com.loopmuse7205.music` 클라이언트에 해당 SHA-1의 Android OAuth 클라이언트가 들어왔는지 확인하고 새 앱을 빌드·로그인 시험한다. [Firebase Google 로그인 안내](https://firebase.google.com/docs/auth/android/google-signin)

이 실행 환경에서는 첫 Gradle 시도에서 Java 위치를 찾지 못해 Android Studio 내장 Java를 지정했다. 다음 시도는 홈 폴더의 Gradle 배포 잠금 파일 쓰기가 거부돼 빌드에 이르지 못했다. 이후 사용자의 Android Studio 실행으로 신규 ID 디버그 APK가 만들어졌고 패키지·서명을 검사했다. Google 로그인·앱 실행은 사용자가 확인했다. 릴리스의 디버그 서명 지정은 제거했고 전용 업로드 키로 서명한 AAB는 아직 없다.
