# chrono-core

크로노 퀘스트 네이티브 코어 Gradle 프로젝트.

| 모듈 | 내용 |
|---|---|
| `rules` | 순수 Kotlin 판정 엔진 (XP, 티어, 칭호 200종, 기간 보너스, 복귀, 펫, 주간 보스) |
| `core` | Android 라이브러리. Room 저장소, 일정 생성, 기록 규칙, `GameService`, `ScheduleService` |

## 준비

- JDK 21: Eclipse Temurin 21을 `~/Library/Java/JavaVirtualMachines/temurin-21.jdk`에 설치한다 (Intel macOS 26에서는 Homebrew openjdk 병 파일이 없어 소스 빌드가 필요하다).
- Android SDK: command-line tools를 `~/Library/Android/sdk/cmdline-tools/latest`에 풀고 `sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.0"`로 설치한다. `local.properties`에 `sdk.dir=/Users/<사용자>/Library/Android/sdk`를 둔다(git 제외).

## 테스트

```bash
cd android-core
./gradlew :rules:test :core:testDebugUnitTest
```

- `core`의 DB·서비스 테스트는 Robolectric(SDK 35)으로 JVM에서 돈다. 폰이나 에뮬레이터가 필요 없다.
- Room 스키마는 `core/schemas/`에 커밋한다. 스키마를 바꾸면 버전을 올리고 마이그레이션을 추가한다.

설계: `../docs/superpowers/specs/2026-11-25-chrono-quest-design.md`
