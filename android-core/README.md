# chrono-core

크로노 퀘스트 네이티브 코어 Gradle 프로젝트.

| 모듈 | 내용 |
|---|---|
| `rules` | 순수 Kotlin 판정 엔진 (XP, 티어, 칭호 200종, 기간 보너스, 복귀, 펫, 주간 보스) |
| `core` | Android 라이브러리. Room 저장소, 일정 생성, 기록 규칙, `GameService`, `ScheduleService` |
| `app` | 실행용 앱. Compose 홈 화면, 확인 창, 정산 알림. 디자인 토큰은 루트 `design-tokens.json`에서 `tools/gen-tokens/gen_tokens.py`로 생성 |

## 준비

- JDK 21: Eclipse Temurin 21을 `~/Library/Java/JavaVirtualMachines/temurin-21.jdk`에 설치한다 (Intel macOS 26에서는 Homebrew openjdk 병 파일이 없어 소스 빌드가 필요하다).
- Android SDK: command-line tools를 `~/Library/Android/sdk/cmdline-tools/latest`에 풀고 `sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.0"`로 설치한다. `local.properties`에 `sdk.dir=/Users/<사용자>/Library/Android/sdk`를 둔다(git 제외).

## 테스트

```bash
cd android-core
./gradlew :rules:test :core:testDebugUnitTest :app:testDebugUnitTest
```

- `core`의 DB·서비스 테스트는 Robolectric(SDK 35)으로 JVM에서 돈다. 폰이나 에뮬레이터가 필요 없다.
- Room 스키마는 `core/schemas/`에 커밋한다. 스키마를 바꾸면 버전을 올리고 마이그레이션을 추가한다.

설계: `../docs/superpowers/specs/2026-11-25-chrono-quest-design.md`

## 화면 사진 (갤럭시 Z 플립5 크기)

```bash
cd android-core
./gradlew :app:recordRoborazziDebug   # app/screenshots/*.png 갱신
```

## 디자인 토큰

`design-tokens.json`을 고친 뒤 저장소 루트에서 `python3 tools/gen-tokens/gen_tokens.py`를 실행한다. `Tokens.kt`는 직접 고치지 않는다.

## 폰에 설치 (USB 디버깅)

```bash
cd android-core
./gradlew :app:assembleDebug
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```
