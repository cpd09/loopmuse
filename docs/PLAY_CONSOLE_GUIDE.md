# Google Play Console 제출 및 비공개 테스트 가이드 (LoopMuse)

이 문서는 LoopMuse 앱(`com.loopmuse7205.music`)을 **Google Play Console**에 등록하고, 심사 설문(앱 콘텐츠) 작성 및 개인 개발자 비공개 테스트(12명/14일)를 완료하기 위한 실무 가이드입니다.

---

## 1. 앱 생성 및 기본 정보 입력

Google Play Console([play.google.com/console](https://play.google.com/console))에 로그인한 후 우측 상단의 **[앱 만들기]**를 클릭합니다.

| 항목 | 입력값 | 설명 |
| :--- | :--- | :--- |
| **앱 이름** | `LoopMuse` | 8자 (최대 30자 이내) |
| **기본 언어** | 한국어 (ko-KR) | 추후 영어 등 추가 가능 |
| **앱 또는 게임** | **앱** (App) | |
| **유료 또는 무료** | **무료** (Free) | 출시 후 변경 불가하므로 주의 |
| **선언 사항** | 개발자 프로그램 정책 및 미국 수출법 동의 | 체크박스 모두 선택 후 [앱 만들기] 클릭 |

---

## 2. 스토어 등록 정보 설정 (대시보드 > 스토어 등록정보)

저장소의 `play-store/` 폴더에 준비된 텍스트와 그래픽 자산을 그대로 업로드합니다.

- **간단한 설명 (80자 한도):**
  ```text
  취향과 들은 곡을 기록하고 알람에도 음악을 쓰는 플레이어
  ```
- **자세한 설명 (4,000자 한도):**
  `play-store/description_ko.txt` 파일의 전문을 복사하여 붙여넣습니다.
- **앱 아이콘:**
  `play-store/icon_512.png` 업로드 (512 x 512, PNG)
- **그래픽 이미지 (피처 그래픽):**
  `play-store/feature_graphic.png` 업로드 (1024 x 500, PNG)
- **휴대전화 스크린샷:**
  `play-store/screenshots/` 폴더 내의 스크린샷 6종 모두 업로드:
  1. `screenshot_1_player_main.png` (메인 플레이어)
  2. `screenshot_2_lyrics.png` (가사 팝업 뷰어)
  3. `screenshot_3_alarm_settings.png` (모닝 알람 및 설정)
  4. `screenshot_4_lounge.png` (라운지 공감 게시판)
  5. `screenshot_5_privacy_policy.png` (앱 정보 및 정책 링크)
  6. `screenshot_6_recommend_dialog.png` (곡 추천 모달)

---

## 3. 정책 및 [앱 콘텐츠] 설문 작성 가이드

Google Play Console 좌측 메뉴의 **정책 및 프로그램 > [앱 콘텐츠]**로 이동하여 각 필수 작업을 작성합니다.

### ① 개인정보처리방침 (Privacy Policy)
- **개인정보처리방침 URL:**
  ```text
  https://cpd09.github.io/loopmuse/privacy.html
  ```

### ② 광고 (Ads)
- **질문:** 앱에 광고가 포함되어 있나요?
- **답변:** **아니요, 앱에 광고가 포함되어 있지 않습니다.**

### ③ 앱 액세스 권한 (App Access)
LoopMuse는 첫 실행 시 Google 로그인이 필수이므로 심사관이 앱 전체 기능을 테스트할 수 있도록 안내해야 합니다.
- **선택:** **일부 또는 모든 기능이 제한됨** 선택 후 **[새 안내 추가]** 클릭
- **안내 이름:** `Google Sign-in Access`
- **사용자 이름/전화번호:** `Reviewer Google Account`
- **비밀번호:** *(공란 또는 N/A)*
- **설명/지침 (Instructions):**
  ```text
  LoopMuse requires Google Sign-In upon launch to sync user profiles and access the community Lounge.
  Reviewers can sign in with any standard Google account on their test device.
  Once signed in, local audio playback and alarms work completely offline.
  Demo posts and features in the Lounge are freely accessible.
  ```

### ④ 콘텐츠 등급 (Content Rating - IARC 설문)
- **이메일 주소:** 개발자 연락용 이메일 입력 (예: `cpd3040@gmail.com`)
- **카테고리:** **유틸리티, 생산성, 통신 또는 기타**
- **설문 응답:**
  - 폭력, 성적 콘텐츠, 욕설, 약물 등: **모두 "아니요"**
  - **사용자 간 상호작용 (Lounge UGC 관련):**
    - "앱을 통해 사용자가 다른 사용자와 소통하거나 상호작용할 수 있나요?" -> **예 (Yes)**
    - "부적절하거나 불쾌감을 주는 콘텐츠를 신고하거나 필터링할 수 있는 메커니즘이 있나요?" -> **예 (Yes)**
  - 위치 공유: 아니요
  - 디지털 상품 구매: 아니요
- **예상 등급:** 전체이용가 또는 만 12세 이상 (IARC 발급)

### ⑤ 타겟층 및 콘텐츠 (Target Audience)
- **대상 연령층:** **만 13~15세, 만 16~17세, 만 18세 이상** 체크
  > ⚠️ **주의:** 만 13세 미만(어린이)을 포함하면 까다로운 Google 가족 정책(Designed for Families) 대상이 되므로, 일반 음악 플레이어/소셜 앱은 만 13세 이상으로 설정하는 것이 안전합니다.
- **의도치 않게 어린이의 관심을 끌 수 있나요?:** **아니요**

### ⑥ 데이터 보안 (Data Safety)
- **앱에서 사용자 데이터를 수집하거나 공유하나요?:** **예**
- **수집된 데이터가 전송 중에 암호화되나요?:** **예** (HTTPS / SSL 보안 전송)
- **사용자가 계정 및 데이터 삭제를 요청할 수 있는 방법을 제공하나요?:** **예**
- **데이터 삭제 요청 웹페이지 URL:**
  ```text
  https://cpd09.github.io/loopmuse/delete-account.html
  ```
- **수집하는 데이터 유형 체크:**
  1. **개인정보 (Personal info):**
     - 이름 (Name): 계정 관리/앱 기능 목적 (Google 프로필 이름)
     - 이메일 주소 (Email): 계정 관리/식별 목적
     - 사용자 ID (User IDs): Firebase Auth UID 식별 목적
  2. **앱 활동 (App activity):**
     - 사용자 생성 콘텐츠 (User generated content): Lounge 게시글, 댓글, 곡 추천 링크
     - 기타 사용자 액션: 게시글 공감(좋아요/싫어요), 사용자 차단 목록
  3. **기기 또는 기타 식별자 (Device or other IDs):**
     - Firebase 기본 분석 및 인증 식별자
  4. **오디오/미디어 파일:**
     - **체크 안 함 (수집 안 함):** 사용자의 로컬 음악 파일은 기기 내에서만 재생되며 외부 서버로 전송/수집되지 않음을 명시.

### ⑦ 뉴스 앱 / 정부 앱 / 금융 기능
- 모두 **"아니요"** 또는 **"해당 사항 없음"** 선택

### ⑧ 알람 및 포그라운드 서비스 권한 선언
- **USE_EXACT_ALARM:**
  - 사용 목적: **알람 시계 및 타이머 (Alarm Clock / Timer)** 선택
  - 사유: 사용자가 지정한 시각에 정확히 음악 알람을 울리기 위해 필수적임.
- **FOREGROUND_SERVICE_MEDIA_PLAYBACK:**
  - 사용 목적: **미디어 재생 (Media playback)**
  - 사유: 화면이 꺼지거나 다른 앱을 사용할 때도 끊김 없이 로컬 음악을 연속 재생하기 위함.

---

## 4. 비공개 테스트(Closed Testing) 12명 / 14일 진행 로드맵

2023년 11월 13일 이후 생성된 신규 개인 개발자 계정은 프로덕션(정식 공개) 출시 전 **최소 12명의 테스터가 14일간 연속으로 비공개 테스트에 참여**해야 합니다.

### Step 1: 테스터 그룹 만들기
1. Google Play Console > 좌측 메뉴 **[테스트 및 출시] > [비공개 테스트]** 클릭.
2. [테스터] 탭에서 **[이메일 목록 만들기]** 클릭 (또는 Google 그룹스 생성).
3. 함께 테스트해 줄 지인/동료 12~15명의 구글 계정 이메일을 입력하고 저장.

### Step 2: 릴리스 번들 업로드 및 검토 요청
1. [비공개 테스트] 트랙에서 **[새 버전 만들기]** 클릭.
2. App Bundle 업로드:
   - 파일: `app/build/outputs/bundle/release/app-release.aab` (17MB)
3. 버전 이름: `1.0051 (51)`
4. 출시 노트(한국어):
   ```text
   LoopMuse 첫 비공개 테스트 버전입니다.
   - 로컬 플레이리스트 음악 재생 및 스마트/순차 재생 기능
   - 음악 모닝 알람 기능
   - 라운지 공감 게시판 및 곡 추천 기능
   ```
5. [버전 검토 및 출시] 클릭 (구글에서 보통 1~2일 내에 비공개 테스트 빌드 승인).

### Step 3: 테스터 참여 링크 배포 (중요!)
1. 구글 승인이 완료되면 [테스터] 탭 하단에 **테스트 참여 링크(Web link 및 Android link)**가 활성화됩니다.
2. 테스터 12명 이상에게 링크를 전달합니다.
3. **각 테스터가 해야 할 일:**
   - 링크를 열어 **[테스트 참여(Become a Tester)]** 버튼 클릭.
   - Google Play 스토어에서 앱을 다운로드 및 설치.
   - 최소 14일 동안 앱을 삭제하지 않고 유지하며 가끔 실행.

### Step 4: 14일 달성 후 프로덕션(정식 출시) 신청
- 대시보드에 14일 카운트다운이 표시됩니다.
- 14일이 경과하면 대시보드에 **[프로덕션 액세스 신청(Apply for production)]** 버튼이 활성화됩니다.
- 간단한 피드백 설문(테스터 피드백 요약 및 개선 사항)을 작성하여 제출하면 구글의 최종 프로덕션 심사를 거쳐 일반 공개 배포가 완료됩니다.
