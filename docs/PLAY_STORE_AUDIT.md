# Google Play 첫 등록 점검

기준일: 2026-09-29. 사용자는 첫 등록이라고 확인했고, 기존 이름을 알아볼 수 있는 고유 앱 ID를 원한다. 현재 저장소와 공식 Google Play·Android 문서를 대조했다. **이 문서는 제출 준비 상태를 기록한다. 실제 제출·공개 상태를 뜻하지 않는다.** 재생 로직은 사용자 확인 전 변경하지 않는다.

## 제출을 막거나 심사에서 문제 될 수 있는 항목

| 우선 | 항목 | 현재 증거 | 완료 기준 |
| --- | --- | --- | --- |
| 필수 | 목표 API | `app/build.gradle`을 `compileSdk 37`, `targetSdk 36`으로 변경했다. 2026-09-29 21:19에 Android Studio가 새 디버그 APK를 생성했고 APK 내부의 목표 API 36, 앱 ID, ZIP 무결성, 서명을 확인했다. | Android 16에서 로그인·알람·전체 화면·뒤로 가기·화면 배치를 시험하고 릴리스 AAB를 별도로 검증한다. 현재 가상폰은 Android 37.1이다. [Play 공식 기준](https://support.google.com/googleplay/android-developer/answer/11926878?hl=ko), [Android 16 변경사항](https://developer.android.com/about/versions/16/behavior-changes-16), [AGP 지원 버전](https://developer.android.com/build/releases/about-agp) |
| 필수 | 앱 ID와 서명 | `applicationId`를 `com.loopmuse7205.music`으로 변경했다. Firebase 새 Android 앱의 디버그 SHA-1, Android·웹 OAuth 클라이언트와 새 디버그 APK의 패키지·서명을 확인했다. 사용자는 새 앱에서 Google 로그인·실행·재실행 시 로그인 유지를 확인했다. 기존 release의 디버그 서명 지정은 제거했고 업로드 키 서명은 없다. | Play Console 사용 가능 여부 확인, 전용 업로드 키·Play App Signing 준비, Firebase 새 앱의 업로드/Play 앱 서명 SHA 등록. [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=en), [Firebase Google 로그인](https://firebase.google.com/docs/auth/android/google-signin) |
| 필수 | 개인정보·계정 삭제 | 이전 `legal/privacy_policy.html`은 실제 HTML이 아니며 개인정보·서버 통신이 없다고 적어 실제 기능과 맞지 않았다. 이 파일을 사실 관계 중심의 HTML **초안**으로 교체하고 사용자 지정 연락처 두 곳을 적었다. 앱 안의 처리방침·계정 삭제 요청 경로와 웹 삭제 요청 주소는 아직 없다. | 보존·삭제 기준과 수신 확인을 마치고 처리방침을 앱 안과 공개 웹에 게시하며 Data safety를 일치시킨다. 앱 안과 웹에서 계정 삭제를 요청할 수 있게 하고 연결된 서버 데이터를 처리한다. [사용자 데이터 정책](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en-GB), [계정 삭제 요건](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en) |
| 필수 | Lounge 사용자 게시물 | 글 신고는 있지만 사용자 신고·일반 사용자의 사용자 차단·게시 전 운영 규칙 동의는 없다. 관리자 신고 처리·차단 해제도 미완성이다. `firestore.rules`는 로컬에서만 검증됐고 서버에 적용되지 않았다. | 앱 안의 약관 동의, 글·사용자 신고와 사용자 차단, 운영 검토·조치, 서버 접근 규칙을 완성하고 실제 계정으로 확인한다. [Play 사용자 게시물 정책](https://support.google.com/googleplay/android-developer/answer/9876937?hl=en-GB) |
| 필수 | 로그인 심사 접근 | 앱 전체가 Google 로그인 뒤에 있다. Play 심사팀용 접근 안내·시험 계정이 아직 없다. | Play Console에 영어로 재사용 가능한 테스트 접근 방법을 제공한다. 시험 계정의 비밀번호는 저장소에 넣지 않는다. [Play 로그인 정보 요건](https://support.google.com/googleplay/android-developer/answer/15748846?hl=en) |
| 신고 | 알람·서비스 권한 | Manifest에 `USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`가 있다. 매일 쓰는 알람과 음악 재생은 사용자 설명의 핵심 기능이지만 Play 심사 승인 여부는 확인되지 않았다. | 정확한 알람 허용 용도와 기기 동작을 검증하고, 전체 화면 알람 및 미디어 재생 서비스의 Play Console 신고·시연 자료를 준비한다. [Android 알람 권한](https://developer.android.com/develop/background-work/services/alarms), [Play 서비스·전체 화면 신고](https://support.google.com/googleplay/android-developer/answer/13392821?hl=en) |
| 완료·재검토 | 스토어 소개 | 기존 한국어·영어 파일에는 AI 감정 분석, 로그인 없음, 완전 오프라인이라는 실제와 다른 설명이 있었다. 사용자는 개인 취향·들은 노래 기록·알람을 소개의 중심으로 지정했다. | 두 설명 파일을 이 방향과 현재 기능에 맞게 고쳤다. 공개 전 앱 화면·스크린샷·최종 동작과 다시 대조한다. [Play 스토어 정보 정확성 안내](https://support.google.com/googleplay/android-developer/answer/15191715?hl=en) |

## 첫 등록에 따른 추가 확인

- 출시 앱 ID `com.loopmuse7205.music`은 LoopMuse 이름과 사용자가 지정한 `7205`를 포함한다. **Play Console이 사용 가능 여부를 최종 판단**한다. Firebase 같은 프로젝트에 기존 앱과 새 앱이 등록됐고, 새 앱의 디버그 SHA-1과 Android OAuth 클라이언트를 확인했다. 단계별 절차와 기존 테스트 앱 데이터 영향은 [앱 ID 전환](PLAY_PACKAGE_MIGRATION.md)에 기록했다. 이전 후보 `com.loopmuse.music`은 사용하지 않는다.
- 새 개인 개발자 계정이 2023-11-13 이후 생성된 경우, 공개 배포 신청 전에 최소 12명이 14일 연속 참여하는 비공개 테스트가 필요하다. 계정 유형과 생성일은 아직 확인되지 않았다. [Play 테스트 요건](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en-GB)
- 새 앱의 제출물은 Android App Bundle을 사용한다. 기존 디버그 키 릴리스 설정은 제거했으며, 업로드 키로 서명한 AAB는 아직 없다. [Play 앱 생성 안내](https://support.google.com/googleplay/android-developer/answer/9859152?hl=en&rd=2)
- Play Console의 대상 연령, 콘텐츠 등급, 광고 유무, Data safety, 앱 액세스, 권한 신고는 실제 기능과 일치하게 작성해야 한다. Console 입력은 이 저장소만으로 완료 여부를 확인할 수 없다.

## 이번 점검에서 수정한 것과 남은 의존성

- 수정: `app/build.gradle`의 목표 API를 36으로, 컴파일 API를 이 컴퓨터에 설치된 37.0에 맞춰 37로 올렸다. `MainActivity`는 이미 `enableEdgeToEdge()`를 호출하고 화면의 뒤로 가기는 Compose `BackHandler`를 사용한다. 터미널 Gradle은 소켓 제한으로 시작하지 못했지만 Android Studio가 새 디버그 APK를 생성했다. `aapt`로 `targetSdkVersion: 36`과 앱 ID를, `unzip`과 `apksigner`로 파일 무결성과 서명을 확인했다. 사용자는 가상폰에서 앱을 열고 로그인 화면 없이 음악 메인 화면에 진입했으며, 비행기 모드에서 다시 열어도 같은 결과라고 확인했다. 같은 오프라인 상태에서 음악 소리와 가사 표시, 앱을 홈으로 보낸 뒤 시험용 알람 실행이 정상이라고 답했다. **Android 16 동작, 알람의 반복·재부팅·잠금 화면 세부 동작, 제출용 릴리스 빌드는 아직 검증하지 않았다.**
- 수정: `play-store/description_ko.txt`, `play-store/description_en.txt`에서 확인되지 않은 AI 주장과 사실과 다른 로그인·오프라인 설명을 제거하고, 사용자가 지정한 개인 취향·청취 기록·알람 중심으로 다시 썼다. 앱 이름은 별도 `title_*.txt`에 `LoopMuse`로, 80자 제한의 간단한 설명은 `short_description_*.txt`에 분리했다. [등록정보 글자 수 기준](https://support.google.com/googleplay/android-developer/answer/9859152?hl=ko)
- 수정: `app/build.gradle`에서 릴리스의 디버그 키 서명을 제거했다. 전용 업로드 키를 준비하기 전에는 서명된 제출용 AAB가 없다.
- 문서화: [데이터 흐름 확인표](PRIVACY_DATA_MAP.md)에 개인정보 처리방침 작성을 위한 사실과 미정 항목을 분리했다. 잘못된 기존 처리방침 파일을 현재 데이터 흐름에 맞는 HTML 초안으로 교체하고 사용자가 지정한 기본·추가 운영 이메일을 반영했다.
- 대기: 릴리스 업로드 키, 공개 개인정보·삭제 요청 웹 주소, 운영 이메일 수신 시험, Play Console 접근 정보, 목표 API 36 설정의 릴리스 빌드와 Android 16 실제 기기 검증.

재생 구현의 읽기 전용 조사는 [재생 검토](PLAYBACK_REVIEW.md)에 따로 보존한다. 이 점검을 이유로 기존 재생 순서·주기·이력을 임의 변경하지 않는다.
