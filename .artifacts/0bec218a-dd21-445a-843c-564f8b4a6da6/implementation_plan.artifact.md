# LoopMuse: 스마트 큐 및 이중 재생 환경 구현 계획

기존의 단순 랜덤 재생 방식을 넘어, 사용자님이 요청하신 **독립된 재생 범위(전체곡/최근곡)**와 **지능형 큐 관리 시스템**을 도입합니다.

## User Review Required

> [!IMPORTANT]
> **영속성 데이터 관리:** 큐 리스트(ID 목록)를 저장하기 위해 `SharedPreferences`를 확장하거나 내부 파일을 사용합니다. 곡 수가 많아질 경우 초기 로딩 속도에 영향을 줄 수 있으므로 최적화된 직렬화를 적용합니다.

> [!NOTE]
> **UI 디자인:** 재생 종료 시 나타나는 '옵션 창'은 Compose의 `AlertDialog` 또는 커스텀 `ModalBottomSheet`를 사용하여 구현할 예정입니다.

## Proposed Changes

### 1. 데이터 레이어 및 모델 업데이트

#### [MODIFY] [MusicFile.kt](file:///Users/geumbogju/StudioProjects/loopmuse/app/src/main/java/com/example/loopmuse/data/MusicFile.kt)
- `dateAdded: Long` 필드 추가 (최근 추가 곡 정렬용).

#### [NEW] [PlaybackState.kt](file:///Users/geumbogju/StudioProjects/loopmuse/app/src/main/java/com/example/loopmuse/data/PlaybackState.kt)
- `RepeatMode` (SHUFFLE, SEQUENTIAL, SINGLE_REPEAT) 정의.
- `PlaybackScope` (ALL, RECENT) 정의.
- 각 스코프별 큐 상태(리스트, 현재 인덱스)를 담는 데이터 클래스.

### 2. 비즈니스 로직 (큐 관리)

#### [NEW] [PlaybackQueueManager.kt](file:///Users/geumbogju/StudioProjects/loopmuse/app/src/main/java/com/example/loopmuse/service/PlaybackQueueManager.kt)
- **이중 큐 관리:** `allSongsQueue`와 `recentSongsQueue`를 독립적으로 유지.
- **스마트 셔플(4-2):** 기존 순서 보존 및 새 곡 끼워넣기 로직 구현.
- **최근곡 필터링:** 10, 30, 50개 기준 처리 및 범위 변경 시 리셋 로직.
- **영속성:** 상태 저장 및 불러오기 (`saveState()`, `loadState()`).

#### [MODIFY] [MusicScanner.kt](file:///Users/geumbogju/StudioProjects/loopmuse/app/src/main/java/com/example/loopmuse/service/MusicScanner.kt)
- 파일 스캔 시 `lastModified` 값을 추출하여 `dateAdded`에 할당.

### 3. 서비스 계층 고도화

#### [MODIFY] [MusicPlaybackService.kt](file:///Users/geumbogju/StudioProjects/loopmuse/app/src/main/java/com/example/loopmuse/service/MusicPlaybackService.kt)
- `PlaybackQueueManager`를 주입받아 재생 흐름 제어.
- 리스트 종료(End of Queue) 시 UI에 콜백/이벤트 전달.
- 재생 모드 변경 명령(`setRepeatMode`, `setScope`) 처리.

### 4. UI 레이어 개선

#### [MODIFY] [HomeScreen.kt](file:///Users/geumbogju/StudioProjects/loopmuse/app/src/main/java/com/example/loopmuse/ui/HomeScreen.kt)
- **상단/사이드 메뉴:** 재생 범위(전체/최근) 및 최근곡 개수(10/30/50) 선택 UI.
- **재생 컨트롤:** 셔플/순차/1곡 반복 토글 버튼 추가.
- **옵션 다이얼로그:** 재생 종료 시 `New Shuffle`, `Sequential`, `Search` 버튼이 있는 팝업 구현.
- **검색 UI:** 곡 목록에서 검색 및 선택 재생 기능.

## Verification Plan

### Automated Tests
- `PlaybackQueueManager` 유닛 테스트:
    - 4-2 방식의 셔플 삽입 로직 검증.
    - 스코프 전환 시 상태 보존 여부 확인.
    - 최근곡 범위 변경 시 초기화 확인.

### Manual Verification
1. 전체곡 재생 중 최근곡 모드로 전환 후 다시 돌아왔을 때 원래 곡이 나오는지 확인.
2. 새 노래 파일을 저장소에 추가한 뒤, 셔플 리스트의 미재생 구간에 잘 들어가는지 확인.
3. 리스트의 마지막 곡 재생 완료 후 대기 창이 정상적으로 뜨는지 확인.
4. 앱 강제 종료 후 다시 켰을 때 재생 모드와 큐가 복구되는지 확인.
