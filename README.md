# LoopMuse 🎧

**AI 기반 감성 오프라인 MP3 플레이어**

LoopMuse는 당신의 감정을 이해하고 그에 맞는 음악을 추천하는 스마트한 오프라인 음악 앱입니다. AI 추천, 스마트 랜덤 재생, 재생 이력 관리 등 꼭 필요한 기능만 담았습니다.

---

## ✨ 주요 기능

- ✅ **스마트 랜덤 재생** - 이미 들은 곡은 제외하고 랜덤 선택
- 🎧 **AI 음악 추천** - 기분과 감정에 따라 음악 추천 (출시 예정)
- 📁 **선택적 폴더 재생** - 특정 음악 폴더를 선택하여 타겟 재생
- 🔄 **재생 이력 관리** - 재생 이력 초기화 및 정렬
- 🎵 **백그라운드 재생** - 앱을 최소화해도 지속적인 음악 재생
- 📱 **알림 제어** - 알림 패널에서 재생/일시정지/다음곡 제어
- 🔒 **잠금 화면 제어** - 잠금 화면에서 미디어 제어 가능

---

## 📋 사전 요구사항

LoopMuse를 빌드하기 전에 다음 항목들이 설치되어 있는지 확인하세요:

### 필수 소프트웨어

1. **Android Studio** (최신 안정 버전 권장)
   - 다운로드: https://developer.android.com/studio
   - 최소 버전: Android Studio Electric Eel (2022.1.1) 이상

2. **Java Development Kit (JDK)**
   - JDK 11 이상
   - 일반적으로 Android Studio에 포함됨

3. **Android SDK**
   - API Level 24 (Android 7.0) 이상
   - 타겟 SDK: API Level 34 (Android 14)

### 시스템 요구사항

- **Windows**: Windows 10/11 (64비트)
- **macOS**: macOS 10.14 (Mojave) 이상
- **Linux**: KDE, GNOME 또는 Unity를 실행할 수 있는 64비트 배포판
- **RAM**: 최소 8GB, 16GB 권장
- **저장공간**: 최소 4GB 여유 공간

---

## 🔧 빌드 방법

Android Studio를 사용하여 LoopMuse를 빌드하는 단계별 방법:

### 1단계: 저장소 클론

```bash
git clone https://github.com/your-username/LoopMuse.git
cd LoopMuse
```

### 2단계: Android Studio에서 프로젝트 열기

1. **Android Studio 실행**
2. **기존 프로젝트 열기**:
   - "Open" 또는 "Open an Existing Project" 클릭
   - `LoopMuse` 폴더로 이동
   - 폴더를 선택하고 "OK" 클릭

### 3단계: 프로젝트 설정 구성

1. **SDK 설정**:
   - `File` → `Project Structure` → `Project`로 이동
   - **Gradle Version**: 8.2 이상으로 설정
   - **Android Gradle Plugin Version**: 8.1.2 이상으로 설정

2. **SDK Manager**:
   - `Tools` → `SDK Manager`로 이동
   - **Android 14 (API 34)**가 설치되어 있는지 확인
   - **Android SDK Build-Tools 34.0.0** 설치

### 4단계: 프로젝트 동기화

1. **Gradle 동기화**:
   - Android Studio에서 자동으로 Gradle 동기화를 요청함
   - 그렇지 않으면 알림 표시줄에서 "Sync Now" 클릭
   - 또는 `File` → `Sync Project with Gradle Files`로 이동

2. **의존성 대기**:
   - 초기 동기화는 5-10분 소요될 수 있음
   - 의존성 다운로드를 위해 안정적인 인터넷 연결 필요

### 5단계: 기기/에뮬레이터 구성

#### 옵션 A: 실제 기기
1. **개발자 옵션 활성화**:
   - `설정` → `휴대전화 정보`로 이동
   - "빌드 번호"를 7번 탭
   - `설정` → `개발자 옵션`으로 돌아가기
   - "USB 디버깅" 활성화

2. **기기 연결**:
   - USB 케이블로 연결
   - 기기에서 USB 디버깅 권한 허용

#### 옵션 B: Android 에뮬레이터
1. **가상 기기 생성**:
   - `Tools` → `AVD Manager`로 이동
   - "Create Virtual Device" 클릭
   - 기기 선택 (예: Pixel 6)
   - 시스템 이미지 선택 (API 34 권장)
   - 설정 완료

### 6단계: 빌드 및 실행

#### 디버그 빌드 (개발용)
1. **타겟 선택**:
   - 드롭다운에서 기기/에뮬레이터 선택
   - 타겟이 "Connected"로 표시되는지 확인

2. **애플리케이션 실행**:
   - 녹색 ▶️ **Run** 버튼 클릭
   - 또는 `Shift + F10` (Windows/Linux) / `Ctrl + R` (macOS) 누르기
   - 또는 `Run` → `Run 'app'`로 이동

#### 릴리스 빌드 (배포용)
1. **서명된 APK 생성**:
   - `Build` → `Generate Signed Bundle / APK`로 이동
   - "APK"를 선택하고 "Next" 클릭
   - 키스토어 생성 또는 기존 키스토어 사용
   - 키스토어 세부 정보 입력
   - "release" 빌드 변형 선택
   - "Finish" 클릭

2. **APK 빌드**:
   - `Build` → `Build Bundle(s) / APK(s)` → `Build APK(s)`로 이동
   - APK는 `app/build/outputs/apk/`에 생성됨

### 앱 버전 관리

- 앱 버전의 기준은 `app/version.properties`의 `VERSION_MAJOR`와 `VERSION_CODE`입니다. `VERSION_CODE=38`이면 화면에 표시되는 `versionName`은 `1.0038`입니다.
- 빌드만으로 버전 값이 바뀌지 않습니다. 사용자에게 전달할 새 APK를 만들 때 `VERSION_CODE`를 이전 배포본보다 높인 뒤, 변경 사항과 함께 커밋합니다.
- APK 이름에는 버전 이름, 버전 코드, 빌드 종류가 들어갑니다. 예: `loopmuse-1.0038-c38-debug.apk`.
- 확인한 커밋에 같은 이름의 Git 태그를 붙입니다. 예: `git tag v1.0038` 다음 `git push origin main v1.0038`. 태그를 푸시하면 GitHub Actions가 버전 일치를 확인하고 debug APK를 빌드해 아티팩트로 올립니다.
- 앱 홈에는 설치된 앱의 버전 이름이, 설정의 앱 정보에는 버전 이름과 코드가 표시됩니다. 홈의 날짜는 앱 설치·업데이트 날짜입니다.

---

## 📱 설치

### Android Studio에서
- **Run** 버튼을 클릭하여 연결된 기기에 직접 설치

### 수동 APK 설치
```bash
# ADB (Android Debug Bridge) 사용
adb install app/build/outputs/apk/debug/loopmuse-1.0038-c38-debug.apk

# 또는 APK를 기기에 복사하여 수동 설치
```

---

## 🔧 문제 해결

### 일반적인 빌드 문제

1. **Gradle 동기화 실패**:
   ```bash
   # 정리 및 재빌드
   ./gradlew clean
   ./gradlew build
   ```

2. **SDK를 찾을 수 없음**:
   - `File` → `Project Structure` → `SDK Location`으로 이동
   - 올바른 Android SDK 경로 설정

3. **빌드 도구 누락**:
   - `Tools` → `SDK Manager` 열기
   - 필요한 빌드 도구 버전 설치

4. **메모리 문제**:
   - `gradle.properties`에서 힙 크기 증가:
   ```properties
   org.gradle.jvmargs=-Xmx4096m
   ```

### 런타임 문제

1. **권한 거부**:
   - 기기 설정에서 저장소 권한 수동 부여
   - `설정` → `앱` → `LoopMuse` → `권한`으로 이동

2. **음악을 찾을 수 없음**:
   - 음악 파일이 기기 저장소에 있는지 확인
   - "음악 폴더 선택"을 사용하여 특정 디렉토리 선택

---

## 📁 프로젝트 구조

```
LoopMuse/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/loopmuse/
│   │   │   ├── MainActivity.kt
│   │   │   ├── data/           # 데이터 모델
│   │   │   ├── service/        # 백그라운드 서비스
│   │   │   └── ui/             # UI 컴포넌트
│   │   ├── AndroidManifest.xml
│   │   └── res/                # 리소스
│   └── build.gradle            # 앱 빌드 구성
├── gradle/                     # Gradle 래퍼
├── build.gradle               # 프로젝트 빌드 구성
├── settings.gradle            # 프로젝트 설정
└── README.md                  # 이 파일
```

---

## 🛠️ 사용된 기술

- **언어**: Kotlin
- **UI 프레임워크**: Jetpack Compose with Material 3
- **아키텍처**: MVVM with StateFlow
- **백그라운드 처리**: Foreground Services
- **미디어**: Android MediaPlayer
- **권한**: Accompanist Permissions
- **빌드 시스템**: Gradle with Kotlin DSL

---

## 📋 최소 요구사항

- **Android**: 7.0 (API Level 24) 이상
- **저장공간**: 50MB 여유 공간
- **권한**: 음악 파일을 위한 저장소 접근
- **하드웨어**: 오디오 출력이 가능한 표준 Android 기기

---

## 🎵 사용법

1. **권한 부여**: 요청 시 저장소 접근 허용
2. **폴더 선택**: "음악 폴더 선택"을 사용하여 음악 디렉토리 선택
3. **재생 시작**: "랜덤 재생" 버튼을 눌러 시작
4. **백그라운드 제어**: 앱이 최소화되었을 때 알림 제어 사용
5. **이력 관리**: 재생 이력을 초기화하여 이전 곡들을 다시 들을 수 있음

---

## 👨‍💻 개발자

- **이름**: @sigollo, @hun_meta
- **프로젝트**: LoopMuse
- **설명**: 감정에 귀 기울이는 음악 플레이어 🎶

---

## 📄 라이선스

이 프로젝트는 오픈 소스입니다. 자세한 정보는 라이선스 파일을 확인해주세요.

---

## 🤝 기여하기

1. 저장소 포크
2. 기능 브랜치 생성
3. 변경사항 작성
4. 철저한 테스트
5. 풀 리퀘스트 제출

버그 및 기능 요청은 GitHub에서 이슈를 열어주세요.

---

*스마트하고 감정을 인식하는 음악 경험을 좋아하는 음악 애호가들을 위해 ❤️로 제작되었습니다.*
