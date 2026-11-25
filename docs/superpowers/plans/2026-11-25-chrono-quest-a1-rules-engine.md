# 크로노 퀘스트 A1: 셋업 + 판정 엔진 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 룰북 규칙(XP, 티어, 칭호 200종, 기간 보너스, 복귀)과 펫·주간 보스를 계산하는 순수 Kotlin 판정 엔진 `rules`를 만들고, 시나리오 테스트로 검증한다.

**Architecture:** `android-core/` Gradle 프로젝트에 JVM 라이브러리 모듈 `rules`를 둔다. 엔진은 원장(`Ledger`)과 기준 시각(`now`)을 받아 `GameState`를 돌려주는 순수 함수다. 내부적으로는 원장에서 시간순 이벤트를 뽑아 `Accumulator`로 접어 계산한다. 펫과 보스는 각각 별도 파일(`PetTracker`, `Bosses`)로 분리한다.

**Tech Stack:** Kotlin 2.2.20 (JVM 17 바이트코드), Gradle 8.14.3 (wrapper), kotlinx-serialization-json 1.9.0, JUnit 5.13.4 + kotlin-test, JDK 21 (Homebrew `openjdk@21`)

**스펙:** [`docs/superpowers/specs/2026-11-25-chrono-quest-design.md`](../specs/2026-11-25-chrono-quest-design.md) 5장 전체

## Global Constraints

- 시각 비교: 모든 시각은 기기 로컬 기준, **분 단위로 내림**해서 비교한다 (스펙 5.1).
- 주는 월요일~일요일, 월은 달력 기준. 시즌 시작은 `2026-11-25`. 그 전 일정은 집계하지 않는다.
- 도착 판정: `EARLY` ≤ 예정−10분 / `ON_TIME` ≤ 예정 / `CLOSE` ≤ 예정+5분 / 그 외 `LATE`. ¬safe와 `NO_RECORD`는 `LATE`다.
- XP: 정시 도착 +20, 조기 추가 +10, 정시 출발 +10, 기상 +5, 행동통제 +5, 완벽 주간 +100, 연속 완벽 주간 3의 배수마다 +300, 완벽 월 +500. 차감은 없다.
- 티어 최소 XP: BRONZE 0 / SILVER 100 / GOLD 300 / PLATINUM 700 / DIAMOND 1500 / MASTER 3000 / LEGEND 6000 / MYTHIC 10000
- 펫 진화 XP: 알 0 → 아기 100 → 청소년 700 → 성체 3000. 정시율 ≥ 0.9 멋짐, ≥ 0.6 평범, 그 미만 못생김, 도착 0건이면 평범.
- 보스: HP = 출근 수 × 100. 데미지는 출근 정시 100 / 기타 정시 50 / 조기 +30 / 정시 출발 +20 / 기상 +10 / 행동통제 +10. XP는 없다.
- `rules` 모듈은 안드로이드·Unity에 의존하지 않는다(순수 Kotlin/JVM). 바이트코드는 JVM 17.
- 무료 도구만 쓴다. 버전은 위 Tech Stack 값으로 고정한다.
- 테스트 함수 이름은 한국어 백틱 이름을 쓰고, `.` `:` `/` `<` `>` `[` `]` 문자는 넣지 않는다(JVM 메서드 이름 제약).
- 모든 커밋 메시지 끝에 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` 줄을 붙인다.
- 작업 디렉터리는 저장소 루트 `/Users/sang/Desktop/chrono-oath`다. Gradle 명령은 `android-core/`에서 실행한다.

## 계획 로드맵 (Phase A)

| 계획 | 범위 | 상태 |
|---|---|---|
| **A1 (이 문서)** | 저장소·빌드 셋업, 칭호 카탈로그, 판정 엔진, 펫, 보스 | 실행 대기 |
| A2 | Android SDK 셋업, `core` 라이브러리 + 실행용 `app` 모듈, Room(스펙 4장), Room→`Ledger` 매핑, 반복 템플릿·단발·휴가, 수동 출발·도착, 즉시 정산 알림, Compose 임시 홈·일정·장소·기록 편집·설정 화면, `design-tokens.json` → Compose 생성 | A1 완료 후 작성 |
| A3 | 기상 알람: 예약, 전체화면 퀴즈, 문제은행, 기상 재확인 | A2 완료 후 작성 |
| A4 | 장소·지오펜스, 확인 알림, 리마인더, 놓침 대비, 보류 정리, 캘린더 가져오기 | A3 완료 후 작성 |

## 파일 구조

```
.gitignore
android-core/
  settings.gradle.kts            # 루트 설정, :rules 포함
  build.gradle.kts               # 플러그인 버전 선언
  gradle.properties
  gradlew, gradlew.bat, gradle/wrapper/*   # wrapper (생성)
  README.md
  rules/
    build.gradle.kts
    src/main/resources/com/chronoquest/rules/titles.json   # 칭호 200종 데이터
    src/main/kotlin/com/chronoquest/rules/
      titles/Title.kt            # Series, SeriesKind, Title
      titles/TitleCatalog.kt     # JSON 로딩 + 누적/연속 지급 규칙
      model/Ledger.kt            # 입력: Quest, QuestRecord, DayRecord, Ledger
      model/Tier.kt              # 티어 표
      judge/Judges.kt            # 도착·출발·기상 판정 (순수 함수)
      engine/GameState.kt        # 출력 타입
      engine/Timeline.kt         # 이벤트 타입과 정렬 순서
      engine/Periods.kt          # 주·월 계산, 기간 결과 판정
      engine/Accumulator.kt      # 이벤트를 접어 XP·카운터·지급 계산
      engine/ComebackTracker.kt  # 복귀
      engine/Pet.kt              # 펫 타입 + PetTracker
      engine/Boss.kt             # 보스 타입 + Bosses
      engine/Engine.kt           # 진입점: compute(ledger, now)
    src/test/kotlin/com/chronoquest/rules/
      Fixtures.kt                # 테스트용 원장 빌더
      titles/TitleCatalogTest.kt
      judge/JudgesTest.kt
      model/TierTest.kt
      engine/EngineCoreTest.kt
      engine/PeriodTest.kt
      engine/ComebackTest.kt
      engine/PetTest.kt
      engine/BossTest.kt
      engine/DeterminismTest.kt
```

---

### Task 1: 저장소·빌드 셋업 + 칭호 카탈로그

**Files:**
- Create: `.gitignore`
- Create: `android-core/settings.gradle.kts`, `android-core/build.gradle.kts`, `android-core/gradle.properties`, `android-core/README.md`
- Create: `android-core/rules/build.gradle.kts`
- Create: `android-core/rules/src/main/kotlin/com/chronoquest/rules/titles/Title.kt`
- Create: `android-core/rules/src/main/kotlin/com/chronoquest/rules/titles/TitleCatalog.kt`
- Create: `android-core/rules/src/main/resources/com/chronoquest/rules/titles.json`
- Test: `android-core/rules/src/test/kotlin/com/chronoquest/rules/titles/TitleCatalogTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `enum class SeriesKind { CUMULATIVE, STREAK }`
  - `enum class Series(val kind: SeriesKind) { ARRIVE_TOTAL, ARRIVE_STREAK, DEPART_TOTAL, EARLY_TOTAL, WAKE_TOTAL, SELF_CONTROL_TOTAL, PERFECT_WEEK_TOTAL, PERFECT_WEEK_STREAK, COMEBACK_TOTAL, PERFECT_MONTH_TOTAL }`
  - `data class Title(val id: Int, val series: Series, val threshold: Int, val name: String)`
  - `class TitleCatalog(val titles: List<Title>)` with `title(id: Int): Title`, `series(series: Series): List<Title>`, `cumulativeAwards(series: Series, count: Int): List<Title>`, `streakAwards(series: Series, streak: Int): List<Title>`, `companion fun load(): TitleCatalog`, `companion fun parse(text: String): TitleCatalog`

- [ ] **Step 1: JDK 21과 Gradle 설치 (sudo 없이)**

```bash
brew install openjdk@21 gradle
mkdir -p ~/Library/Java/JavaVirtualMachines
ln -sfn "$(brew --prefix openjdk@21)/libexec/openjdk.jdk" ~/Library/Java/JavaVirtualMachines/openjdk-21.jdk
/usr/libexec/java_home -v 21 && java -version
```

Expected: 마지막 줄들에 `openjdk version "21.` 이 보인다. `java -version`이 여전히 "Unable to locate a Java Runtime"이면 `export JAVA_HOME="$(/usr/libexec/java_home -v 21)"`을 셸에 넣고 다시 확인한다.

- [ ] **Step 2: git 저장소 초기화**

`.gitignore` 작성:

```gitignore
# Gradle / Kotlin
.gradle/
build/
.kotlin/
local.properties

# IDE / OS
.idea/
*.iml
.DS_Store
```

```bash
cd /Users/sang/Desktop/chrono-oath
git init -b main
git add .gitignore docs
git commit -m "docs: 크로노 퀘스트 설계 스펙, 룰북, A1 계획" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

Expected: `[main (root-commit) ...]` 출력.

- [ ] **Step 3: Gradle 프로젝트 뼈대와 wrapper 생성**

```bash
mkdir -p /Users/sang/Desktop/chrono-oath/android-core/rules
cd /Users/sang/Desktop/chrono-oath/android-core
printf 'rootProject.name = "chrono-core"\n' > settings.gradle.kts
gradle wrapper --gradle-version 8.14.3 --distribution-type bin
```

이어서 아래 파일들을 작성한다(위에서 만든 `settings.gradle.kts`는 덮어쓴다).

`android-core/settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "chrono-core"
include(":rules")
```

`android-core/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20" apply false
    kotlin("plugin.serialization") version "2.2.20" apply false
}
```

`android-core/gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
kotlin.code.style=official
```

`android-core/rules/build.gradle.kts`:

```kotlin
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation(kotlin("test"))
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
```

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew --version`
Expected: `Gradle 8.14.3`이 보인다.

- [ ] **Step 4: 칭호 카탈로그 실패 테스트 작성**

`android-core/rules/src/test/kotlin/com/chronoquest/rules/titles/TitleCatalogTest.kt`:

```kotlin
package com.chronoquest.rules.titles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TitleCatalogTest {
    private val catalog = TitleCatalog.load()

    @Test
    fun `칭호는 200종이고 ID는 1부터 200까지다`() {
        assertEquals((1..200).toList(), catalog.titles.map { it.id }.sorted())
    }

    @Test
    fun `계열별 첫 칭호와 마지막 칭호가 룰북과 같다`() {
        assertEquals("최초의 각성", catalog.title(1).name)
        assertEquals("시간축의 지배자", catalog.title(20).name)
        assertEquals("운명의 첫걸음", catalog.title(21).name)
        assertEquals("경계를 넘는 자", catalog.title(41).name)
        assertEquals("미래의 방문자", catalog.title(61).name)
        assertEquals("잠에서 깨어난 자", catalog.title(81).name)
        assertEquals("유혹을 지나친 자", catalog.title(101).name)
        assertEquals("일곱 날의 승리", catalog.title(121).name)
        assertEquals("첫 번째 왕관", catalog.title(141).name)
        assertEquals("완벽의 신화, 크로노스", catalog.title(160).name)
        assertEquals("잿더미에서 일어난 자", catalog.title(161).name)
        assertEquals("달빛의 개척자", catalog.title(181).name)
        assertEquals("크로노스의 화신", catalog.title(200).name)
    }

    @Test
    fun `칭호 계열과 단계 값이 룰북 표와 같다`() {
        assertEquals(Series.ARRIVE_STREAK, catalog.title(30).series)
        assertEquals(200, catalog.title(20).threshold)
        assertEquals(12, catalog.title(31).threshold)
        assertEquals(14, catalog.title(132).threshold)
        assertEquals(52, catalog.title(140).threshold)
        assertEquals(20, catalog.title(180).threshold)
        assertEquals(11, catalog.title(191).threshold)
    }

    @Test
    fun `누적 계열은 카운트가 단계의 배수일 때마다 지급한다`() {
        assertEquals(listOf(1), catalog.cumulativeAwards(Series.ARRIVE_TOTAL, 1).map { it.id })
        assertEquals(listOf(1, 5), catalog.cumulativeAwards(Series.ARRIVE_TOTAL, 5).map { it.id })
        assertEquals(listOf(1, 2, 5, 10), catalog.cumulativeAwards(Series.ARRIVE_TOTAL, 10).map { it.id })
        assertEquals(listOf(1, 2, 3, 4, 6, 11), catalog.cumulativeAwards(Series.ARRIVE_TOTAL, 12).map { it.id })
    }

    @Test
    fun `연속 계열은 정확히 그 단계에서만 지급한다`() {
        assertEquals(listOf(21), catalog.streakAwards(Series.ARRIVE_STREAK, 1).map { it.id })
        assertEquals(listOf(31), catalog.streakAwards(Series.ARRIVE_STREAK, 12).map { it.id })
        assertEquals(emptyList<Int>(), catalog.streakAwards(Series.ARRIVE_STREAK, 11).map { it.id })
    }

    @Test
    fun `0 이하 카운트는 지급하지 않는다`() {
        assertEquals(emptyList<Int>(), catalog.cumulativeAwards(Series.WAKE_TOTAL, 0).map { it.id })
        assertEquals(emptyList<Int>(), catalog.streakAwards(Series.PERFECT_WEEK_STREAK, 0).map { it.id })
    }

    @Test
    fun `계열 종류가 맞지 않으면 예외`() {
        assertFailsWith<IllegalArgumentException> { catalog.cumulativeAwards(Series.ARRIVE_STREAK, 1) }
        assertFailsWith<IllegalArgumentException> { catalog.streakAwards(Series.ARRIVE_TOTAL, 1) }
    }
}
```

- [ ] **Step 5: 테스트가 실패하는지 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :rules:test`
Expected: FAIL, `Unresolved reference 'TitleCatalog'` 컴파일 오류.

- [ ] **Step 6: 칭호 타입과 카탈로그 구현**

`android-core/rules/src/main/kotlin/com/chronoquest/rules/titles/Title.kt`:

```kotlin
package com.chronoquest.rules.titles

enum class SeriesKind { CUMULATIVE, STREAK }

/** 칭호 계열 10종. 이름은 titles.json의 key와 같다. */
enum class Series(val kind: SeriesKind) {
    ARRIVE_TOTAL(SeriesKind.CUMULATIVE),
    ARRIVE_STREAK(SeriesKind.STREAK),
    DEPART_TOTAL(SeriesKind.CUMULATIVE),
    EARLY_TOTAL(SeriesKind.CUMULATIVE),
    WAKE_TOTAL(SeriesKind.CUMULATIVE),
    SELF_CONTROL_TOTAL(SeriesKind.CUMULATIVE),
    PERFECT_WEEK_TOTAL(SeriesKind.CUMULATIVE),
    PERFECT_WEEK_STREAK(SeriesKind.STREAK),
    COMEBACK_TOTAL(SeriesKind.CUMULATIVE),
    PERFECT_MONTH_TOTAL(SeriesKind.CUMULATIVE),
}

data class Title(
    val id: Int,
    val series: Series,
    val threshold: Int,
    val name: String,
)
```

`android-core/rules/src/main/kotlin/com/chronoquest/rules/titles/TitleCatalog.kt`:

```kotlin
package com.chronoquest.rules.titles

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class CatalogFile(
    val thresholdSets: Map<String, List<Int>>,
    val series: List<SeriesEntry>,
)

@Serializable
private data class SeriesEntry(
    val key: String,
    val firstId: Int,
    val thresholds: String,
    val names: List<String>,
)

/** 룰북 칭호 200종과 지급 규칙(룰북 03-02). */
class TitleCatalog(val titles: List<Title>) {
    private val byId: Map<Int, Title> = titles.associateBy { it.id }
    private val bySeries: Map<Series, List<Title>> = titles.groupBy { it.series }

    fun title(id: Int): Title = byId[id] ?: error("Unknown title id: $id")

    fun series(series: Series): List<Title> = bySeries[series].orEmpty()

    /** 누적 계열: 카운터가 [count]가 된 순간 지급할 칭호. 단계의 배수마다 지급한다. */
    fun cumulativeAwards(series: Series, count: Int): List<Title> {
        require(series.kind == SeriesKind.CUMULATIVE) { "$series is not cumulative" }
        if (count <= 0) return emptyList()
        return series(series).filter { count % it.threshold == 0 }
    }

    /** 연속 계열: 연속 값이 [streak]가 된 순간 지급할 칭호. 정확히 그 단계만 지급한다. */
    fun streakAwards(series: Series, streak: Int): List<Title> {
        require(series.kind == SeriesKind.STREAK) { "$series is not a streak series" }
        if (streak <= 0) return emptyList()
        return series(series).filter { streak == it.threshold }
    }

    companion object {
        private const val RESOURCE = "/com/chronoquest/rules/titles.json"

        fun load(): TitleCatalog {
            val text = TitleCatalog::class.java.getResourceAsStream(RESOURCE)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { it.readText() }
                ?: error("Missing resource $RESOURCE")
            return parse(text)
        }

        fun parse(text: String): TitleCatalog {
            val file = Json.decodeFromString(CatalogFile.serializer(), text)
            val titles = file.series.flatMap { entry ->
                val series = Series.valueOf(entry.key)
                val thresholds = file.thresholdSets[entry.thresholds]
                    ?: error("Unknown threshold set ${entry.thresholds}")
                require(entry.names.size == 20) { "${entry.key} must have 20 names" }
                require(thresholds.size == 20) { "${entry.thresholds} must have 20 thresholds" }
                entry.names.mapIndexed { i, name ->
                    Title(id = entry.firstId + i, series = series, threshold = thresholds[i], name = name)
                }
            }
            require(titles.map { it.id }.toSet().size == titles.size) { "Duplicate title ids" }
            return TitleCatalog(titles)
        }
    }
}
```

`android-core/rules/src/main/resources/com/chronoquest/rules/titles.json` (룰북 02장 전사):

```json
{
  "thresholdSets": {
    "COUNT": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 20, 25, 30, 40, 50, 75, 100, 200],
    "WEEKS": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 16, 20, 24, 28, 32, 40, 48, 52],
    "ONE_TO_TWENTY": [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20]
  },
  "series": [
    {
      "key": "ARRIVE_TOTAL", "firstId": 1, "thresholds": "COUNT",
      "names": ["최초의 각성", "시간의 여행자", "시계탑의 방문자", "초침의 추적자", "시간의 사냥꾼", "시간의 수집가", "새벽의 기사", "시공의 방랑자", "황혼의 감시자", "시간의 연금술사", "열두 시의 군주", "크로노스의 사도", "차원의 항해자", "시간의 수호자", "시공의 정복자", "시계탑의 군주", "시간의 집행자", "영겁의 개척자", "백 개의 시간을 지닌 자", "시간축의 지배자"]
    },
    {
      "key": "ARRIVE_STREAK", "firstId": 21, "thresholds": "COUNT",
      "names": ["운명의 첫걸음", "끊어지지 않는 실", "삼연성", "불굴의 행진", "철의 규율", "무결점의 궤적", "일곱 번째 맹세", "영원의 연결고리", "무패의 선봉장", "십전무패", "열두 개의 봉인", "불멸의 행진", "무결의 기사단장", "운명을 거스르는 자", "절대영역", "단절을 초월한 자", "무패의 군왕", "영원의 맹세", "백전불패", "영겁불멸"]
    },
    {
      "key": "DEPART_TOTAL", "firstId": 41, "thresholds": "COUNT",
      "names": ["경계를 넘는 자", "첫 번째 관문", "여정의 개척자", "문을 여는 자", "출발의 기사", "여명의 발걸음", "길을 만드는 자", "망설임의 파괴자", "정체를 깨는 자", "관문의 수호자", "경계의 집행자", "새벽의 개척왕", "불퇴전의 기사", "행동의 연금술사", "관문의 정복자", "정적을 가르는 검", "경계의 군주", "시작을 지배하는 자", "백 개의 관문을 연 자", "태초의 문을 연 자"]
    },
    {
      "key": "EARLY_TOTAL", "firstId": 61, "thresholds": "COUNT",
      "names": ["미래의 방문자", "한발 앞선 그림자", "선견자", "시간을 앞지른 자", "선행의 기사", "미래를 읽는 자", "예언자의 눈", "초침의 선구자", "미래의 정찰병", "시간의 예언자", "여유의 연금술사", "미래의 설계자", "운명의 선봉장", "시간을 선점한 자", "미래를 걷는 자", "시공의 선구자", "예측의 지배자", "앞선 세계의 군주", "미래의 찬탈자", "시간보다 먼저 존재하는 자"]
    },
    {
      "key": "WAKE_TOTAL", "firstId": 81, "thresholds": "COUNT",
      "names": ["잠에서 깨어난 자", "새벽의 불씨", "꿈의 탈출자", "밤을 벗어난 자", "여명의 전사", "침묵의 파수꾼", "새벽을 여는 검", "어둠의 추격자", "몽환의 파괴자", "태양의 사도", "여명의 수호신", "꿈의 감옥을 부순 자", "새벽의 군왕", "밤을 정복한 자", "태양을 맞이하는 자", "빛의 계승자", "어둠을 초월한 자", "여명의 지배자", "백 번의 태양을 깨운 자", "태양보다 먼저 깨어난 자"]
    },
    {
      "key": "SELF_CONTROL_TOTAL", "firstId": 101, "thresholds": "COUNT",
      "names": ["유혹을 지나친 자", "선택의 시작", "집중의 씨앗", "혼돈의 관찰자", "질서의 개척자", "유혹의 봉인자", "산만함의 추방자", "의지의 대장장이", "선택의 수호자", "집중의 기사", "혼돈의 조율자", "충동의 연금술사", "절제의 집행자", "의지의 설계자", "혼돈을 지배하는 자", "정신의 방벽", "불요불굴의 의지", "정신세계의 군주", "백 가지 유혹을 봉인한 자", "혼돈 위에 군림하는 자"]
    },
    {
      "key": "PERFECT_WEEK_TOTAL", "firstId": 121, "thresholds": "WEEKS",
      "names": ["일곱 날의 승리", "쌍월의 기사", "삼위일체", "달력의 정복자", "기록의 수호자", "시간의 세공사", "일곱 왕국의 기사", "두 달의 연금술사", "연대기의 기록자", "십주의 군주", "계절의 지배자", "시간의 건축가", "네 개의 달을 삼킨 자", "황금 달력의 주인", "반년의 개척자", "연대기의 군왕", "세월의 설계자", "역사를 기록하는 자", "영겁의 기록관", "한 해를 정복한 자"]
    },
    {
      "key": "PERFECT_WEEK_STREAK", "firstId": 141, "thresholds": "WEEKS",
      "names": ["첫 번째 왕관", "흔들리지 않는 왕좌", "삼주무결", "완벽의 첫 번째 장", "황금의 연대기", "불멸의 계율", "칠성의 계승자", "무결의 계승자", "무패의 궤도", "절대 시간권", "무결의 성역", "불멸의 왕좌", "사중결계", "영원으로 향하는 자", "시간의 성좌", "무패의 신화", "불변의 법칙", "시간의 절대자", "영원의 통치자", "완벽의 신화, 크로노스"]
    },
    {
      "key": "COMEBACK_TOTAL", "firstId": 161, "thresholds": "ONE_TO_TWENTY",
      "names": ["잿더미에서 일어난 자", "다시 검을 든 자", "세 번째 불꽃", "패배를 삼킨 자", "불사조의 깃털", "재기의 기사", "운명에 저항하는 자", "부활의 성기사", "추락을 초월한 자", "패배의 정복자", "꺼지지 않는 불꽃", "불사조의 계승자", "운명의 파괴자", "영혼의 재건자", "죽지 않는 의지", "잿빛 왕관의 군주", "종말을 견딘 자", "영원히 부활하는 자", "패배마저 지배하는 자", "불멸의 피닉스"]
    },
    {
      "key": "PERFECT_MONTH_TOTAL", "firstId": 181, "thresholds": "ONE_TO_TWENTY",
      "names": ["달빛의 개척자", "쌍월의 수호자", "삼월의 군주", "계절의 기사", "달의 연금술사", "반년의 왕관", "칠월의 성좌", "시간의 왕국", "아홉 달의 지배자", "열 개의 달을 거느린 자", "열한 번째 왕좌", "열두 달의 군왕", "윤회의 설계자", "세월의 수호신", "역법의 창조자", "시공의 황제", "영겁의 왕관", "시간의 신격", "만월의 초월자", "크로노스의 화신"]
    }
  ]
}
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :rules:test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: 모듈 README 작성**

`android-core/README.md`:

````markdown
# chrono-core

크로노 퀘스트 네이티브 코어 Gradle 프로젝트. 지금은 `rules` 모듈(순수 Kotlin 판정 엔진)만 있다.

## 준비

- JDK 21: `brew install openjdk@21` 후 `~/Library/Java/JavaVirtualMachines/openjdk-21.jdk`로 링크한다.

## 테스트

```bash
cd android-core
./gradlew :rules:test
```

설계: `../docs/superpowers/specs/2026-11-25-chrono-quest-design.md`
````

- [ ] **Step 9: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core
git commit -m "feat(rules): Gradle 셋업과 칭호 카탈로그 200종" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: 원장 모델, 판정 함수, 티어

**Files:**
- Create: `android-core/rules/src/main/kotlin/com/chronoquest/rules/model/Ledger.kt`
- Create: `android-core/rules/src/main/kotlin/com/chronoquest/rules/model/Tier.kt`
- Create: `android-core/rules/src/main/kotlin/com/chronoquest/rules/judge/Judges.kt`
- Create: `android-core/rules/src/test/kotlin/com/chronoquest/rules/Fixtures.kt`
- Test: `android-core/rules/src/test/kotlin/com/chronoquest/rules/judge/JudgesTest.kt`
- Test: `android-core/rules/src/test/kotlin/com/chronoquest/rules/model/TierTest.kt`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `enum class QuestKind { WORK, OTHER }`, `enum class QuestStatus { SCHEDULED, CANCELED }`, `enum class Resolution { NONE, NO_RECORD }`
  - `data class Quest(id: String, date: LocalDate, kind: QuestKind, title: String, dueAt: LocalDateTime, targetDepartAt: LocalDateTime, status: QuestStatus = SCHEDULED)`
  - `data class QuestRecord(questId: String, departedAt: LocalDateTime? = null, arrivedAt: LocalDateTime? = null, safe: Boolean? = null, resolution: Resolution = NONE)`
  - `data class DayRecord(date: LocalDate, alarmRangAt: LocalDateTime? = null, quizSolvedAt: LocalDateTime? = null, sleptEnough: Boolean? = null, recheckPassed: Boolean? = null, selfControl: Boolean? = null)`
  - `data class Ledger(seasonStart: LocalDate, quests: List<Quest> = emptyList(), records: List<QuestRecord> = emptyList(), days: List<DayRecord> = emptyList(), vacations: Set<LocalDate> = emptySet())`
  - `enum class Tier(val minXp: Int, val title: String)` with `val next: Tier?`, `companion fun of(xp: Int): Tier`
  - `enum class ArrivalVerdict { EARLY, ON_TIME, CLOSE, LATE }` with `val isOnTime: Boolean`
  - `object Judges { fun arrival(dueAt, arrivedAt: LocalDateTime?, safe: Boolean?, resolution: Resolution): ArrivalVerdict?; fun onTimeDeparture(targetDepartAt, departedAt: LocalDateTime?): Boolean; fun wakeSuccess(day: DayRecord): Boolean }`
  - 테스트 픽스처(패키지 `com.chronoquest.rules`): `SEASON_START`, `day()`, `at()`, `work()`, `other()`, `Quest.arrived()`, `Quest.onTime()`, `Quest.early()`, `Quest.close()`, `Quest.late()`, `Quest.noRecord()`, `wakeDay()`, `weekdays()`, `dailyOthers()`, `onTimeAll()`, `ledger()`

- [ ] **Step 1: 원장 모델과 테스트 픽스처 작성 (테스트 컴파일에 필요)**

`android-core/rules/src/main/kotlin/com/chronoquest/rules/model/Ledger.kt`:

```kotlin
package com.chronoquest.rules.model

import java.time.LocalDate
import java.time.LocalDateTime

enum class QuestKind { WORK, OTHER }

enum class QuestStatus { SCHEDULED, CANCELED }

/** 도착 기록이 없을 때 사용자가 고른 정리 방식. NO_RECORD는 지각으로 판정한다. */
enum class Resolution { NONE, NO_RECORD }

/** 일정 인스턴스. id는 "날짜-kind-예정시각" 형식으로 유일하다. */
data class Quest(
    val id: String,
    val date: LocalDate,
    val kind: QuestKind,
    val title: String,
    val dueAt: LocalDateTime,
    val targetDepartAt: LocalDateTime,
    val status: QuestStatus = QuestStatus.SCHEDULED,
)

data class QuestRecord(
    val questId: String,
    val departedAt: LocalDateTime? = null,
    val arrivedAt: LocalDateTime? = null,
    val safe: Boolean? = null,
    val resolution: Resolution = Resolution.NONE,
)

data class DayRecord(
    val date: LocalDate,
    val alarmRangAt: LocalDateTime? = null,
    val quizSolvedAt: LocalDateTime? = null,
    val sleptEnough: Boolean? = null,
    val recheckPassed: Boolean? = null,
    val selfControl: Boolean? = null,
)

/** 판정 엔진 입력. 데이터 계층(Room)에서 이 형태로 매핑해서 넘긴다. */
data class Ledger(
    val seasonStart: LocalDate,
    val quests: List<Quest> = emptyList(),
    val records: List<QuestRecord> = emptyList(),
    val days: List<DayRecord> = emptyList(),
    val vacations: Set<LocalDate> = emptySet(),
)
```

`android-core/rules/src/test/kotlin/com/chronoquest/rules/Fixtures.kt`:

```kotlin
package com.chronoquest.rules

import com.chronoquest.rules.model.DayRecord
import com.chronoquest.rules.model.Ledger
import com.chronoquest.rules.model.Quest
import com.chronoquest.rules.model.QuestKind
import com.chronoquest.rules.model.QuestRecord
import com.chronoquest.rules.model.Resolution
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

val SEASON_START: LocalDate = LocalDate.parse("2026-11-25")

fun day(date: String): LocalDate = LocalDate.parse(date)

fun at(date: String, time: String): LocalDateTime = LocalDateTime.parse("${date}T$time")

/** 출근 일정. 목표 출발 기본값은 예정 30분 전. */
fun work(date: String, due: String = "09:00", depart: String? = null): Quest =
    quest(date, QuestKind.WORK, "출근", due, depart)

/** 출근 외 일정. 목표 출발 기본값은 예정 30분 전. */
fun other(date: String, due: String = "19:00", depart: String? = null): Quest =
    quest(date, QuestKind.OTHER, "약속", due, depart)

private fun quest(date: String, kind: QuestKind, title: String, due: String, depart: String?): Quest {
    val dueAt = at(date, due)
    return Quest(
        id = "$date-$kind-$due",
        date = day(date),
        kind = kind,
        title = title,
        dueAt = dueAt,
        targetDepartAt = depart?.let { at(date, it) } ?: dueAt.minusMinutes(30),
    )
}

fun Quest.arrived(time: String, safe: Boolean = true, departed: String? = null): QuestRecord =
    QuestRecord(
        questId = id,
        departedAt = departed?.let { at(date.toString(), it) },
        arrivedAt = at(date.toString(), time),
        safe = safe,
    )

private fun Quest.arrivedOffset(minutes: Long): QuestRecord =
    QuestRecord(questId = id, arrivedAt = dueAt.plusMinutes(minutes), safe = true)

/** 예정 2분 전 안전 도착 (ON_TIME) */
fun Quest.onTime(): QuestRecord = arrivedOffset(-2)

/** 예정 15분 전 안전 도착 (EARLY) */
fun Quest.early(): QuestRecord = arrivedOffset(-15)

/** 예정 3분 후 안전 도착 (CLOSE) */
fun Quest.close(): QuestRecord = arrivedOffset(3)

/** 예정 20분 후 안전 도착 (LATE) */
fun Quest.late(): QuestRecord = arrivedOffset(20)

fun Quest.noRecord(): QuestRecord = QuestRecord(questId = id, resolution = Resolution.NO_RECORD)

fun wakeDay(
    date: String,
    rang: String = "07:00",
    solved: String = "07:03",
    slept: Boolean = true,
    recheck: Boolean = true,
    selfControl: Boolean? = null,
): DayRecord = DayRecord(
    date = day(date),
    alarmRangAt = at(date, rang),
    quizSolvedAt = at(date, solved),
    sleptEnough = slept,
    recheckPassed = recheck,
    selfControl = selfControl,
)

/** [from]부터 [toInclusive]까지의 평일(월~금) 날짜 문자열. */
fun weekdays(from: String, toInclusive: String): List<String> =
    generateSequence(day(from)) { it.plusDays(1) }
        .takeWhile { !it.isAfter(day(toInclusive)) }
        .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
        .map { it.toString() }
        .toList()

/** [from]부터 하루에 하나씩 [count]개의 출근 외 일정. */
fun dailyOthers(from: String, count: Int, due: String = "10:00"): List<Quest> =
    (0 until count).map { other(day(from).plusDays(it.toLong()).toString(), due) }

fun onTimeAll(quests: List<Quest>): List<QuestRecord> = quests.map { it.onTime() }

fun ledger(
    quests: List<Quest>,
    records: List<QuestRecord> = emptyList(),
    days: List<DayRecord> = emptyList(),
    vacations: Set<LocalDate> = emptySet(),
): Ledger = Ledger(
    seasonStart = SEASON_START,
    quests = quests,
    records = records,
    days = days,
    vacations = vacations,
)
```

- [ ] **Step 2: 판정 함수·티어 실패 테스트 작성**

`android-core/rules/src/test/kotlin/com/chronoquest/rules/judge/JudgesTest.kt`:

```kotlin
package com.chronoquest.rules.judge

import com.chronoquest.rules.at
import com.chronoquest.rules.day
import com.chronoquest.rules.model.DayRecord
import com.chronoquest.rules.model.Resolution
import com.chronoquest.rules.wakeDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JudgesTest {
    private val due = at("2026-11-25", "09:00")

    private fun judge(time: String, safe: Boolean? = true): ArrivalVerdict? =
        Judges.arrival(due, at("2026-11-25", time), safe, Resolution.NONE)

    @Test
    fun `예정 10분 전 이하는 조기`() {
        assertEquals(ArrivalVerdict.EARLY, judge("07:00"))
        assertEquals(ArrivalVerdict.EARLY, judge("08:50"))
        assertEquals(ArrivalVerdict.EARLY, judge("08:50:59"))
    }

    @Test
    fun `예정 시각까지는 정시이고 초는 버린다`() {
        assertEquals(ArrivalVerdict.ON_TIME, judge("08:51"))
        assertEquals(ArrivalVerdict.ON_TIME, judge("09:00"))
        assertEquals(ArrivalVerdict.ON_TIME, judge("09:00:59"))
    }

    @Test
    fun `5분 유예 안은 아슬아슬`() {
        assertEquals(ArrivalVerdict.CLOSE, judge("09:01"))
        assertEquals(ArrivalVerdict.CLOSE, judge("09:05:59"))
    }

    @Test
    fun `유예를 넘기면 지각`() {
        assertEquals(ArrivalVerdict.LATE, judge("09:06"))
    }

    @Test
    fun `위험 이동은 시간과 관계없이 지각`() {
        assertEquals(ArrivalVerdict.LATE, judge("08:30", safe = false))
    }

    @Test
    fun `안전 여부나 도착 시각이 없으면 판정 보류`() {
        assertNull(judge("08:55", safe = null))
        assertNull(Judges.arrival(due, null, true, Resolution.NONE))
    }

    @Test
    fun `기록 없음으로 정리하면 지각`() {
        assertEquals(ArrivalVerdict.LATE, Judges.arrival(due, null, null, Resolution.NO_RECORD))
    }

    @Test
    fun `조기와 정시만 정시로 친다`() {
        assertTrue(ArrivalVerdict.EARLY.isOnTime)
        assertTrue(ArrivalVerdict.ON_TIME.isOnTime)
        assertFalse(ArrivalVerdict.CLOSE.isOnTime)
        assertFalse(ArrivalVerdict.LATE.isOnTime)
    }

    @Test
    fun `목표 출발 시각까지 나가면 정시 출발`() {
        val target = at("2026-11-25", "08:20")
        assertTrue(Judges.onTimeDeparture(target, at("2026-11-25", "08:20:59")))
        assertFalse(Judges.onTimeDeparture(target, at("2026-11-25", "08:21")))
        assertFalse(Judges.onTimeDeparture(target, null))
    }

    @Test
    fun `알람 후 5분 안에 풀고 잠 충분 재확인 통과면 기상 성공`() {
        assertTrue(Judges.wakeSuccess(wakeDay("2026-11-25", solved = "07:05:59")))
        assertFalse(Judges.wakeSuccess(wakeDay("2026-11-25", solved = "07:06")))
        assertFalse(Judges.wakeSuccess(wakeDay("2026-11-25", slept = false)))
        assertFalse(Judges.wakeSuccess(wakeDay("2026-11-25", recheck = false)))
        assertFalse(Judges.wakeSuccess(DayRecord(date = day("2026-11-25"))))
    }
}
```

`android-core/rules/src/test/kotlin/com/chronoquest/rules/model/TierTest.kt`:

```kotlin
package com.chronoquest.rules.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TierTest {
    @Test
    fun `누적 XP로 티어가 정해진다`() {
        assertEquals(Tier.BRONZE, Tier.of(0))
        assertEquals(Tier.BRONZE, Tier.of(99))
        assertEquals(Tier.SILVER, Tier.of(100))
        assertEquals(Tier.DIAMOND, Tier.of(2999))
        assertEquals(Tier.MASTER, Tier.of(3000))
        assertEquals(Tier.MYTHIC, Tier.of(10000))
        assertEquals(Tier.MYTHIC, Tier.of(99999))
    }

    @Test
    fun `다음 티어와 대표 칭호`() {
        assertEquals(Tier.SILVER, Tier.BRONZE.next)
        assertNull(Tier.MYTHIC.next)
        assertEquals("시간의 지배자", Tier.MASTER.title)
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :rules:test`
Expected: FAIL, `Unresolved reference 'Judges'`, `Unresolved reference 'Tier'` 컴파일 오류.

- [ ] **Step 4: 티어와 판정 함수 구현**

`android-core/rules/src/main/kotlin/com/chronoquest/rules/model/Tier.kt`:

```kotlin
package com.chronoquest.rules.model

/** 룰북 01 티어 표. [title]은 티어 대표 칭호다. */
enum class Tier(val minXp: Int, val title: String) {
    BRONZE(0, "시간의 개척자"),
    SILVER(100, "시간의 수련자"),
    GOLD(300, "시간의 정복자"),
    PLATINUM(700, "시간의 설계자"),
    DIAMOND(1500, "시간의 초월자"),
    MASTER(3000, "시간의 지배자"),
    LEGEND(6000, "시간의 전설"),
    MYTHIC(10000, "시간의 신");

    val next: Tier? get() = entries.getOrNull(ordinal + 1)

    companion object {
        fun of(xp: Int): Tier = entries.last { xp >= it.minXp }
    }
}
```

`android-core/rules/src/main/kotlin/com/chronoquest/rules/judge/Judges.kt`:

```kotlin
package com.chronoquest.rules.judge

import com.chronoquest.rules.model.DayRecord
import com.chronoquest.rules.model.Resolution
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class ArrivalVerdict {
    EARLY, ON_TIME, CLOSE, LATE;

    val isOnTime: Boolean get() = this == EARLY || this == ON_TIME
}

/** 개별 기록 판정 (스펙 5.1~5.3). 모든 비교는 분 단위로 내린 시각으로 한다. */
object Judges {
    const val EARLY_MINUTES = 10L
    const val GRACE_MINUTES = 5L
    const val WAKE_LIMIT_MINUTES = 5L

    /** 도착 판정. 판정할 수 없으면(도착 시각이나 안전 여부 없음) null. */
    fun arrival(
        dueAt: LocalDateTime,
        arrivedAt: LocalDateTime?,
        safe: Boolean?,
        resolution: Resolution,
    ): ArrivalVerdict? {
        if (resolution == Resolution.NO_RECORD) return ArrivalVerdict.LATE
        if (arrivedAt == null || safe == null) return null
        if (!safe) return ArrivalVerdict.LATE
        val due = dueAt.toMinute()
        val arr = arrivedAt.toMinute()
        return when {
            !arr.isAfter(due.minusMinutes(EARLY_MINUTES)) -> ArrivalVerdict.EARLY
            !arr.isAfter(due) -> ArrivalVerdict.ON_TIME
            !arr.isAfter(due.plusMinutes(GRACE_MINUTES)) -> ArrivalVerdict.CLOSE
            else -> ArrivalVerdict.LATE
        }
    }

    fun onTimeDeparture(targetDepartAt: LocalDateTime, departedAt: LocalDateTime?): Boolean =
        departedAt != null && !departedAt.toMinute().isAfter(targetDepartAt.toMinute())

    /** 알람 후 5분 안에 퀴즈를 다 풀고, 잠이 충분했고, 재확인을 통과했으면 성공. */
    fun wakeSuccess(day: DayRecord): Boolean {
        val rang = day.alarmRangAt?.toMinute() ?: return false
        val solved = day.quizSolvedAt?.toMinute() ?: return false
        if (solved.isBefore(rang)) return false
        return Duration.between(rang, solved) <= Duration.ofMinutes(WAKE_LIMIT_MINUTES) &&
            day.sleptEnough == true &&
            day.recheckPassed == true
    }
}

internal fun LocalDateTime.toMinute(): LocalDateTime = truncatedTo(ChronoUnit.MINUTES)
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :rules:test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/rules
git commit -m "feat(rules): 원장 모델, 도착·출발·기상 판정, 티어" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: 엔진 코어 (도착·출발·기상·행동통제, XP, 누적·연속 칭호, 보류)

**Files:**
- Create: `android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/GameState.kt`
- Create: `android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/Timeline.kt`
- Create: `android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/Accumulator.kt`
- Create: `android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/Engine.kt`
- Test: `android-core/rules/src/test/kotlin/com/chronoquest/rules/engine/EngineCoreTest.kt`

**Interfaces:**
- Consumes: Task 1 `TitleCatalog`, `Series`, `Title` / Task 2 `Ledger`, `Quest`, `QuestRecord`, `DayRecord`, `QuestKind`, `QuestStatus`, `Resolution`, `Tier`, `ArrivalVerdict`, `Judges`
- Produces:
  - `enum class GrantType { XP_ARRIVE, XP_EARLY, XP_DEPART, XP_WAKE, XP_SELF_CONTROL, XP_PERFECT_WEEK, XP_THREE_WEEKS, XP_PERFECT_MONTH, TITLE, PET_EVOLVED, BOSS_RESULT }`
  - `data class Grant(key: String, at: LocalDateTime, type: GrantType, xp: Int = 0, titleId: Int? = null, detail: String = "")`
  - `data class Streak(current: Int = 0, best: Int = 0)` with `advance()`, `reset()`
  - `data class Counters(arrive, depart, early, wake, selfControl, perfectWeeks, comebacks, perfectMonths: Int = 0)`
  - `data class QuestOutcome(questId: String, verdict: ArrivalVerdict?, onTimeDeparture: Boolean)`
  - `data class GameState(xp, tier, nextTierXp, counters, arriveStreak, perfectWeekStreak, inventory: Map<Int, Int>, codexCount, outcomes: Map<String, QuestOutcome>, pendingQuestIds: List<String>, grants: List<Grant>)`
  - `class Engine(catalog: TitleCatalog = TitleCatalog.load()) { fun compute(ledger: Ledger, now: LocalDateTime): GameState }`
  - 내부: `internal sealed class Event`, `internal class Accumulator`. 지급 키 형식: `arr:<questId>:xp`, `arr:<questId>:early`, `arr:<questId>:title:<id>`, `dep:<questId>:xp`, `wake:<date>:xp`, `self:<date>:xp`

- [ ] **Step 1: 실패 테스트 작성**

모든 테스트는 시즌 첫 불완전 주(11/25~11/29) 안의 날짜와 `now = 2026-11-29 23:00`을 쓴다. 그래서 나중 Task에서 붙는 기간 보너스나 보스 결과가 이 테스트에 끼어들지 않는다.

`android-core/rules/src/test/kotlin/com/chronoquest/rules/engine/EngineCoreTest.kt`:

```kotlin
package com.chronoquest.rules.engine

import com.chronoquest.rules.arrived
import com.chronoquest.rules.at
import com.chronoquest.rules.day
import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.ledger
import com.chronoquest.rules.model.QuestStatus
import com.chronoquest.rules.model.Tier
import com.chronoquest.rules.noRecord
import com.chronoquest.rules.onTime
import com.chronoquest.rules.other
import com.chronoquest.rules.wakeDay
import com.chronoquest.rules.work
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EngineCoreTest {
    private val engine = Engine()
    private val now = at("2026-11-29", "23:00")

    @Test
    fun `첫 미션 안전한 정시 도착 1회면 최초의 각성과 운명의 첫걸음`() {
        val q = work("2026-11-25")
        val s = engine.compute(ledger(listOf(q), listOf(q.arrived("08:55"))), now)
        assertEquals(20, s.xp)
        assertEquals(Tier.BRONZE, s.tier)
        assertEquals(100, s.nextTierXp)
        assertEquals(1, s.inventory[1])
        assertEquals(1, s.inventory[21])
        assertEquals(2, s.codexCount)
        assertEquals(Streak(1, 1), s.arriveStreak)
        assertEquals(ArrivalVerdict.ON_TIME, s.outcomes.getValue(q.id).verdict)
        assertTrue(s.grants.any { it.key == "arr:${q.id}:title:1" && it.titleId == 1 })
    }

    @Test
    fun `10분 이상 일찍 오면 조기 보너스와 조기 칭호`() {
        val q = work("2026-11-25")
        val s = engine.compute(ledger(listOf(q), listOf(q.arrived("08:50"))), now)
        assertEquals(30, s.xp)
        assertEquals(1, s.counters.early)
        assertEquals(1, s.inventory[61])
    }

    @Test
    fun `아슬아슬 도착은 XP 없이 연속을 유지한다`() {
        val qs = listOf(work("2026-11-25"), work("2026-11-26"), work("2026-11-27"))
        val rs = listOf(qs[0].arrived("08:55"), qs[1].arrived("09:03"), qs[2].arrived("08:55"))
        val s = engine.compute(ledger(qs, rs), now)
        assertEquals(40, s.xp)
        assertEquals(Streak(2, 2), s.arriveStreak)
        assertEquals(1, s.inventory[22])
        assertEquals(ArrivalVerdict.CLOSE, s.outcomes.getValue(qs[1].id).verdict)
    }

    @Test
    fun `지각하면 연속이 끊기고 다시 쌓으면 연속 칭호를 또 준다`() {
        val qs = listOf(work("2026-11-25"), work("2026-11-26"), work("2026-11-27"))
        val rs = listOf(qs[0].arrived("08:55"), qs[1].arrived("09:10"), qs[2].arrived("08:55"))
        val s = engine.compute(ledger(qs, rs), now)
        assertEquals(Streak(1, 1), s.arriveStreak)
        assertEquals(2, s.inventory[21])
        assertNull(s.inventory[22])
    }

    @Test
    fun `위험하게 왔으면 제시간이어도 지각이다`() {
        val q = work("2026-11-25")
        val s = engine.compute(ledger(listOf(q), listOf(q.arrived("08:55", safe = false))), now)
        assertEquals(ArrivalVerdict.LATE, s.outcomes.getValue(q.id).verdict)
        assertEquals(0, s.xp)
        assertTrue(s.inventory.isEmpty())
    }

    @Test
    fun `기록 없음으로 정리하면 지각이고 보류가 아니다`() {
        val q = work("2026-11-25")
        val s = engine.compute(ledger(listOf(q), listOf(q.noRecord())), now)
        assertEquals(ArrivalVerdict.LATE, s.outcomes.getValue(q.id).verdict)
        assertTrue(s.pendingQuestIds.isEmpty())
    }

    @Test
    fun `예정이 지났는데 기록이 없으면 보류이고 미래 일정은 보류가 아니다`() {
        val past = work("2026-11-25")
        val future = work("2026-11-30")
        val s = engine.compute(ledger(listOf(past, future)), now)
        assertEquals(listOf(past.id), s.pendingQuestIds)
        assertNull(s.outcomes.getValue(future.id).verdict)
    }

    @Test
    fun `누적 칭호는 배수마다 지급되고 연속 칭호는 단계마다 한 번`() {
        val qs = listOf("2026-11-25", "2026-11-26", "2026-11-27", "2026-11-28", "2026-11-29")
            .flatMap { d -> listOf(other(d, "10:00"), other(d, "15:00")) }
        val s = engine.compute(ledger(qs, qs.map { it.onTime() }), now)
        assertEquals(200, s.xp)
        assertEquals(Tier.SILVER, s.tier)
        assertEquals(
            mapOf(1 to 10, 2 to 5, 3 to 3, 4 to 2, 5 to 2, 6 to 1, 7 to 1, 8 to 1, 9 to 1, 10 to 1),
            s.inventory.filterKeys { it in 1..20 },
        )
        assertEquals((21..30).associateWith { 1 }, s.inventory.filterKeys { it in 21..40 })
    }

    @Test
    fun `목표 시각까지 출발하면 정시 출발 보너스`() {
        val q1 = work("2026-11-25")
        val q2 = work("2026-11-26")
        val rs = listOf(q1.arrived("08:55", departed = "08:30"), q2.arrived("08:55", departed = "08:31"))
        val s = engine.compute(ledger(listOf(q1, q2), rs), now)
        assertEquals(20 + 10 + 20, s.xp)
        assertEquals(1, s.counters.depart)
        assertEquals(1, s.inventory[41])
        assertTrue(s.outcomes.getValue(q1.id).onTimeDeparture)
        assertFalse(s.outcomes.getValue(q2.id).onTimeDeparture)
    }

    @Test
    fun `알람 후 5분 안에 풀고 재확인까지 통과한 날만 기상 성공`() {
        val days = listOf(
            wakeDay("2026-11-25"),
            wakeDay("2026-11-26", solved = "07:06"),
            wakeDay("2026-11-27", recheck = false),
        )
        val s = engine.compute(ledger(emptyList(), days = days), now)
        assertEquals(5, s.xp)
        assertEquals(1, s.counters.wake)
        assertEquals(1, s.inventory[81])
    }

    @Test
    fun `행동통제는 기상과 별개로 하루 한 번 5 XP`() {
        val days = listOf(wakeDay("2026-11-25", solved = "07:30", selfControl = true))
        val s = engine.compute(ledger(emptyList(), days = days), now)
        assertEquals(5, s.xp)
        assertEquals(0, s.counters.wake)
        assertEquals(1, s.counters.selfControl)
        assertEquals(1, s.inventory[101])
    }

    @Test
    fun `취소 휴가 시즌 전 일정은 판정에서 빠진다`() {
        val canceled = work("2026-11-25").copy(status = QuestStatus.CANCELED)
        val onVacation = work("2026-11-26")
        val otherOnVacation = other("2026-11-26")
        val beforeSeason = work("2026-11-24")
        val quests = listOf(canceled, onVacation, otherOnVacation, beforeSeason)
        val s = engine.compute(
            ledger(quests, quests.map { it.onTime() }, vacations = setOf(day("2026-11-26"))),
            now,
        )
        assertEquals(20, s.xp)
        assertEquals(setOf(otherOnVacation.id), s.outcomes.keys)
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :rules:test --tests "com.chronoquest.rules.engine.EngineCoreTest"`
Expected: FAIL, `Unresolved reference 'Engine'` 컴파일 오류.

- [ ] **Step 3: 출력 타입 작성**

`android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/GameState.kt`:

```kotlin
package com.chronoquest.rules.engine

import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.model.Tier
import java.time.LocalDateTime

enum class GrantType {
    XP_ARRIVE, XP_EARLY, XP_DEPART, XP_WAKE, XP_SELF_CONTROL,
    XP_PERFECT_WEEK, XP_THREE_WEEKS, XP_PERFECT_MONTH,
    TITLE, PET_EVOLVED, BOSS_RESULT,
}

/**
 * 지급 내역 한 줄. [key]는 같은 원장이면 항상 같은 값이라
 * 연출을 이미 보여줬는지(CelebrationSeen) 비교하는 데 쓴다.
 */
data class Grant(
    val key: String,
    val at: LocalDateTime,
    val type: GrantType,
    val xp: Int = 0,
    val titleId: Int? = null,
    val detail: String = "",
)

data class Streak(val current: Int = 0, val best: Int = 0) {
    fun advance(): Streak = Streak(current + 1, maxOf(best, current + 1))
    fun reset(): Streak = Streak(0, best)
}

data class Counters(
    val arrive: Int = 0,
    val depart: Int = 0,
    val early: Int = 0,
    val wake: Int = 0,
    val selfControl: Int = 0,
    val perfectWeeks: Int = 0,
    val comebacks: Int = 0,
    val perfectMonths: Int = 0,
)

data class QuestOutcome(
    val questId: String,
    val verdict: ArrivalVerdict?,
    val onTimeDeparture: Boolean,
)

data class GameState(
    val xp: Int,
    val tier: Tier,
    val nextTierXp: Int?,
    val counters: Counters,
    val arriveStreak: Streak,
    val perfectWeekStreak: Streak,
    val inventory: Map<Int, Int>,
    val codexCount: Int,
    val outcomes: Map<String, QuestOutcome>,
    val pendingQuestIds: List<String>,
    val grants: List<Grant>,
)
```

- [ ] **Step 4: 이벤트 타임라인 작성**

`android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/Timeline.kt`:

```kotlin
package com.chronoquest.rules.engine

import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.model.Quest
import java.time.LocalDate
import java.time.LocalDateTime

/** 원장에서 뽑은 시간순 이벤트. 같은 시각이면 [order], 그다음 [tieKey] 순으로 처리한다. */
internal sealed class Event(val at: LocalDateTime, val order: Int, val tieKey: String) {
    class Depart(at: LocalDateTime, val quest: Quest) : Event(at, 0, quest.id)
    class Arrive(at: LocalDateTime, val quest: Quest, val verdict: ArrivalVerdict) : Event(at, 1, quest.id)
    class Wake(at: LocalDateTime, val date: LocalDate) : Event(at, 2, date.toString())
    class SelfControl(at: LocalDateTime, val date: LocalDate) : Event(at, 3, date.toString())

    companion object {
        val ORDER: Comparator<Event> = compareBy<Event>({ it.at }, { it.order }, { it.tieKey })
    }
}
```

- [ ] **Step 5: 누산기 작성**

`android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/Accumulator.kt`:

```kotlin
package com.chronoquest.rules.engine

import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.titles.Series
import com.chronoquest.rules.titles.Title
import com.chronoquest.rules.titles.TitleCatalog
import java.time.LocalDateTime

/** 시간순 이벤트를 하나씩 받아 XP·카운터·연속 기록·지급 내역을 쌓는다. */
internal class Accumulator(private val catalog: TitleCatalog) {
    var xp: Int = 0
        private set
    var counters: Counters = Counters()
        private set
    var arriveStreak: Streak = Streak()
        private set
    var perfectWeekStreak: Streak = Streak()
        private set
    private val grants = mutableListOf<Grant>()

    fun grants(): List<Grant> = grants.toList()

    fun handle(event: Event) {
        when (event) {
            is Event.Depart -> onDepart(event)
            is Event.Arrive -> onArrive(event)
            is Event.Wake -> onWake(event)
            is Event.SelfControl -> onSelfControl(event)
        }
    }

    private fun onDepart(e: Event.Depart) {
        val key = "dep:${e.quest.id}"
        counters = counters.copy(depart = counters.depart + 1)
        addXp("$key:xp", e.at, GrantType.XP_DEPART, XP_DEPART)
        award(key, e.at, catalog.cumulativeAwards(Series.DEPART_TOTAL, counters.depart))
    }

    private fun onArrive(e: Event.Arrive) {
        val key = "arr:${e.quest.id}"
        when (e.verdict) {
            ArrivalVerdict.EARLY, ArrivalVerdict.ON_TIME -> {
                val early = e.verdict == ArrivalVerdict.EARLY
                counters = counters.copy(
                    arrive = counters.arrive + 1,
                    early = counters.early + if (early) 1 else 0,
                )
                arriveStreak = arriveStreak.advance()
                addXp("$key:xp", e.at, GrantType.XP_ARRIVE, XP_ARRIVE)
                if (early) addXp("$key:early", e.at, GrantType.XP_EARLY, XP_EARLY_BONUS)
                award(key, e.at, catalog.cumulativeAwards(Series.ARRIVE_TOTAL, counters.arrive))
                if (early) award(key, e.at, catalog.cumulativeAwards(Series.EARLY_TOTAL, counters.early))
                award(key, e.at, catalog.streakAwards(Series.ARRIVE_STREAK, arriveStreak.current))
            }
            ArrivalVerdict.CLOSE -> Unit
            ArrivalVerdict.LATE -> arriveStreak = arriveStreak.reset()
        }
    }

    private fun onWake(e: Event.Wake) {
        val key = "wake:${e.date}"
        counters = counters.copy(wake = counters.wake + 1)
        addXp("$key:xp", e.at, GrantType.XP_WAKE, XP_WAKE)
        award(key, e.at, catalog.cumulativeAwards(Series.WAKE_TOTAL, counters.wake))
    }

    private fun onSelfControl(e: Event.SelfControl) {
        val key = "self:${e.date}"
        counters = counters.copy(selfControl = counters.selfControl + 1)
        addXp("$key:xp", e.at, GrantType.XP_SELF_CONTROL, XP_SELF_CONTROL)
        award(key, e.at, catalog.cumulativeAwards(Series.SELF_CONTROL_TOTAL, counters.selfControl))
    }

    private fun addXp(key: String, at: LocalDateTime, type: GrantType, amount: Int) {
        xp += amount
        grants += Grant(key = key, at = at, type = type, xp = amount)
    }

    private fun award(eventKey: String, at: LocalDateTime, titles: List<Title>) {
        titles.forEach { t ->
            grants += Grant(
                key = "$eventKey:title:${t.id}",
                at = at,
                type = GrantType.TITLE,
                titleId = t.id,
                detail = t.name,
            )
        }
    }

    companion object {
        const val XP_ARRIVE = 20
        const val XP_EARLY_BONUS = 10
        const val XP_DEPART = 10
        const val XP_WAKE = 5
        const val XP_SELF_CONTROL = 5
        const val XP_PERFECT_WEEK = 100
        const val XP_THREE_WEEKS = 300
        const val XP_PERFECT_MONTH = 500
    }
}
```

- [ ] **Step 6: 엔진 진입점 작성**

`android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/Engine.kt`:

```kotlin
package com.chronoquest.rules.engine

import com.chronoquest.rules.judge.Judges
import com.chronoquest.rules.model.DayRecord
import com.chronoquest.rules.model.Ledger
import com.chronoquest.rules.model.Quest
import com.chronoquest.rules.model.QuestKind
import com.chronoquest.rules.model.QuestRecord
import com.chronoquest.rules.model.QuestStatus
import com.chronoquest.rules.model.Resolution
import com.chronoquest.rules.model.Tier
import com.chronoquest.rules.titles.TitleCatalog
import java.time.LocalDateTime

/**
 * 판정 엔진 진입점. 원장 전체를 매번 처음부터 다시 계산한다(스펙 D9).
 * 같은 (ledger, now)면 항상 같은 [GameState]를 돌려준다.
 */
class Engine(private val catalog: TitleCatalog = TitleCatalog.load()) {

    fun compute(ledger: Ledger, now: LocalDateTime): GameState {
        val quests = activeQuests(ledger)
        val records = ledger.records.distinctBy { it.questId }.associateBy { it.questId }
        val days = ledger.days.distinctBy { it.date }.filter { !it.date.isBefore(ledger.seasonStart) }
        val outcomes = judgeAll(quests, records)

        val acc = Accumulator(catalog)
        buildEvents(quests, records, days, outcomes)
            .sortedWith(Event.ORDER)
            .forEach(acc::handle)

        val pending = quests
            .filter { outcomes.getValue(it.id).verdict == null && !it.dueAt.isAfter(now) }
            .sortedWith(compareBy<Quest>({ it.dueAt }, { it.id }))
            .map { it.id }
        val grants = acc.grants()
        val inventory = grants.mapNotNull { it.titleId }.groupingBy { it }.eachCount().toSortedMap()
        val tier = Tier.of(acc.xp)
        return GameState(
            xp = acc.xp,
            tier = tier,
            nextTierXp = tier.next?.minXp,
            counters = acc.counters,
            arriveStreak = acc.arriveStreak,
            perfectWeekStreak = acc.perfectWeekStreak,
            inventory = inventory,
            codexCount = inventory.size,
            outcomes = outcomes,
            pendingQuestIds = pending,
            grants = grants,
        )
    }

    /** 취소, 시즌 전, 휴가일 출근을 뺀 판정 대상 일정. */
    private fun activeQuests(ledger: Ledger): List<Quest> =
        ledger.quests
            .distinctBy { it.id }
            .filter { it.status == QuestStatus.SCHEDULED }
            .filter { !it.date.isBefore(ledger.seasonStart) }
            .filter { !(it.kind == QuestKind.WORK && it.date in ledger.vacations) }

    private fun judgeAll(quests: List<Quest>, records: Map<String, QuestRecord>): Map<String, QuestOutcome> =
        quests.associate { q ->
            val r = records[q.id]
            q.id to QuestOutcome(
                questId = q.id,
                verdict = Judges.arrival(q.dueAt, r?.arrivedAt, r?.safe, r?.resolution ?: Resolution.NONE),
                onTimeDeparture = Judges.onTimeDeparture(q.targetDepartAt, r?.departedAt),
            )
        }

    private fun buildEvents(
        quests: List<Quest>,
        records: Map<String, QuestRecord>,
        days: List<DayRecord>,
        outcomes: Map<String, QuestOutcome>,
    ): List<Event> {
        val events = mutableListOf<Event>()
        for (q in quests) {
            val record = records[q.id]
            val outcome = outcomes.getValue(q.id)
            val departedAt = record?.departedAt
            if (departedAt != null && outcome.onTimeDeparture) events += Event.Depart(departedAt, q)
            val verdict = outcome.verdict
            if (verdict != null) events += Event.Arrive(record?.arrivedAt ?: q.dueAt, q, verdict)
        }
        for (d in days) {
            val solvedAt = d.quizSolvedAt
            if (solvedAt != null && Judges.wakeSuccess(d)) events += Event.Wake(solvedAt, d.date)
            if (d.selfControl == true) {
                val firstDeparture = quests
                    .filter { it.date == d.date }
                    .mapNotNull { records[it.id]?.departedAt }
                    .minOrNull()
                events += Event.SelfControl(firstDeparture ?: d.date.atTime(23, 59), d.date)
            }
        }
        return events
    }
}
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :rules:test`
Expected: `BUILD SUCCESSFUL` (EngineCoreTest 12개 포함 전체 통과)

- [ ] **Step 8: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/rules
git commit -m "feat(rules): 엔진 코어 - XP, 누적·연속 칭호, 보류" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: 기간 보너스 (완벽 주간·월, 3주 보너스, 연속 완벽 주간)

**Files:**
- Create: `android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/Periods.kt`
- Modify: `android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/Timeline.kt`
- Modify: `android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/Accumulator.kt`
- Modify: `android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/Engine.kt`
- Test: `android-core/rules/src/test/kotlin/com/chronoquest/rules/engine/PeriodTest.kt`

**Interfaces:**
- Consumes: Task 3 `Engine`, `Accumulator`, `Event`, `GameState`, `Streak`, `Counters`
- Produces:
  - `internal object Periods { fun weekStart(date: LocalDate): LocalDate; fun firstFullWeek(seasonStart: LocalDate): LocalDate; fun firstFullMonth(seasonStart: LocalDate): YearMonth; fun closedWeeks(seasonStart: LocalDate, now: LocalDateTime): List<LocalDate>; fun closedMonths(seasonStart: LocalDate, now: LocalDateTime): List<YearMonth> }`
  - `internal enum class PeriodResult { NO_WORK, PERFECT, CLOSE_ONLY, HAS_LATE, PENDING }`, `internal fun judgePeriod(verdicts: List<ArrivalVerdict?>): PeriodResult`
  - `Event.WeekClose(weekStart, result)`, `Event.MonthClose(month, result)`
  - 지급 키: `week:<weekStart>:xp`, `week:<weekStart>:3w`, `week:<weekStart>:title:<id>`, `month:<yyyy-MM>:xp`, `month:<yyyy-MM>:title:<id>`

- [ ] **Step 1: 실패 테스트 작성**

날짜 기준: 2026-11-30(월)이 시즌 첫 완전한 주이고, 2026년 12월이 첫 완전한 달이다.

`android-core/rules/src/test/kotlin/com/chronoquest/rules/engine/PeriodTest.kt`:

```kotlin
package com.chronoquest.rules.engine

import com.chronoquest.rules.arrived
import com.chronoquest.rules.at
import com.chronoquest.rules.day
import com.chronoquest.rules.ledger
import com.chronoquest.rules.onTimeAll
import com.chronoquest.rules.weekdays
import com.chronoquest.rules.work
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PeriodTest {
    private val engine = Engine()
    private val week1 = weekdays("2026-11-30", "2026-12-04").map { work(it) }
    private val week2 = weekdays("2026-12-07", "2026-12-11").map { work(it) }
    private val week3 = weekdays("2026-12-14", "2026-12-18").map { work(it) }

    @Test
    fun `출근 전부 정시인 주는 완벽 주간`() {
        val s = engine.compute(ledger(week1, onTimeAll(week1)), at("2026-12-07", "00:00"))
        assertEquals(1, s.counters.perfectWeeks)
        assertEquals(Streak(1, 1), s.perfectWeekStreak)
        assertEquals(5 * 20 + 100, s.xp)
        assertEquals(1, s.inventory[121])
        assertEquals(1, s.inventory[141])
        assertTrue(s.grants.any { it.key == "week:2026-11-30:xp" && it.xp == 100 })
    }

    @Test
    fun `주가 끝나기 전에는 주간 보너스가 없다`() {
        val s = engine.compute(ledger(week1, onTimeAll(week1)), at("2026-12-06", "23:59"))
        assertEquals(0, s.counters.perfectWeeks)
        assertEquals(5 * 20, s.xp)
    }

    @Test
    fun `시즌 첫 불완전 주는 보너스에서 뺀다`() {
        val first = weekdays("2026-11-25", "2026-11-27").map { work(it) }
        val s = engine.compute(ledger(first, onTimeAll(first)), at("2026-11-30", "00:00"))
        assertEquals(0, s.counters.perfectWeeks)
        assertEquals(3 * 20, s.xp)
    }

    @Test
    fun `아슬아슬이 낀 주는 보너스 없이 연속 완벽 주간을 유지한다`() {
        val records = onTimeAll(week1) +
            week2.mapIndexed { i, q -> if (i == 2) q.arrived("09:03") else q.arrived("08:55") } +
            onTimeAll(week3)
        val s = engine.compute(ledger(week1 + week2 + week3, records), at("2026-12-21", "00:00"))
        assertEquals(2, s.counters.perfectWeeks)
        assertEquals(Streak(2, 2), s.perfectWeekStreak)
        assertEquals(2, s.inventory[121])
        assertEquals(1, s.inventory[122])
        assertEquals(1, s.inventory[142])
    }

    @Test
    fun `지각이 낀 주는 연속 완벽 주간을 끊는다`() {
        val records = onTimeAll(week1) +
            week2.mapIndexed { i, q -> if (i == 2) q.arrived("09:20") else q.arrived("08:55") } +
            onTimeAll(week3)
        val s = engine.compute(ledger(week1 + week2 + week3, records), at("2026-12-21", "00:00"))
        assertEquals(Streak(1, 1), s.perfectWeekStreak)
        assertEquals(2, s.inventory[141])
        assertNull(s.inventory[142])
    }

    @Test
    fun `출근이 없는 휴가 주는 건너뛰고 연속을 유지한다`() {
        val vacations = week2.map { it.date }.toSet()
        val s = engine.compute(
            ledger(week1 + week2 + week3, onTimeAll(week1 + week3), vacations = vacations),
            at("2026-12-21", "00:00"),
        )
        assertEquals(Streak(2, 2), s.perfectWeekStreak)
    }

    @Test
    fun `연속 완벽 주간 3주마다 300 XP`() {
        val all = week1 + week2 + week3
        val s = engine.compute(ledger(all, onTimeAll(all)), at("2026-12-21", "00:00"))
        assertEquals(15 * 20 + 3 * 100 + 300, s.xp)
        assertTrue(s.grants.any { it.key == "week:2026-12-14:3w" && it.xp == 300 })
        assertEquals(1, s.inventory[143])
    }

    @Test
    fun `보류 일정이 있는 주에서 주간 판정이 멈춘다`() {
        val records = onTimeAll(week1.drop(1)) + onTimeAll(week2)
        val s = engine.compute(ledger(week1 + week2, records), at("2026-12-14", "00:00"))
        assertEquals(0, s.counters.perfectWeeks)
        assertEquals(listOf(week1[0].id), s.pendingQuestIds)
        assertEquals(9 * 20, s.xp)
    }

    @Test
    fun `출근 전부 정시인 달은 완벽 월`() {
        val month = weekdays("2026-11-30", "2026-12-31").map { work(it) }
        val s = engine.compute(ledger(month, onTimeAll(month)), at("2027-01-01", "00:00"))
        assertEquals(1, s.counters.perfectMonths)
        assertEquals(1, s.inventory[181])
        assertTrue(s.grants.any { it.key == "month:2026-12:xp" && it.xp == 500 })
    }

    @Test
    fun `아슬아슬이 낀 달은 완벽 월이 아니다`() {
        val month = weekdays("2026-11-30", "2026-12-31").map { work(it) }
        val records = month.map { if (it.date == day("2026-12-15")) it.arrived("09:03") else it.arrived("08:55") }
        val s = engine.compute(ledger(month, records), at("2027-01-01", "00:00"))
        assertEquals(0, s.counters.perfectMonths)
        assertNull(s.inventory[181])
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :rules:test --tests "com.chronoquest.rules.engine.PeriodTest"`
Expected: FAIL. 컴파일은 되고, `출근 전부 정시인 주는 완벽 주간` 등이 `expected:<1> but was:<0>`로 실패한다.

- [ ] **Step 3: 기간 계산 작성**

`android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/Periods.kt`:

```kotlin
package com.chronoquest.rules.engine

import com.chronoquest.rules.judge.ArrivalVerdict
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** 주(월~일)·월(달력) 계산 (스펙 5.5). */
internal object Periods {
    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** 시즌 시작이 월요일이 아니면 그다음 월요일부터 보너스 대상이다. */
    fun firstFullWeek(seasonStart: LocalDate): LocalDate =
        if (seasonStart.dayOfWeek == DayOfWeek.MONDAY) seasonStart
        else seasonStart.with(TemporalAdjusters.next(DayOfWeek.MONDAY))

    /** 시즌 시작이 1일이 아니면 그다음 달부터 보너스 대상이다. */
    fun firstFullMonth(seasonStart: LocalDate): YearMonth {
        val month = YearMonth.from(seasonStart)
        return if (seasonStart.dayOfMonth == 1) month else month.plusMonths(1)
    }

    /** [now] 시점에 이미 끝난 보너스 대상 주의 시작일. */
    fun closedWeeks(seasonStart: LocalDate, now: LocalDateTime): List<LocalDate> =
        generateSequence(firstFullWeek(seasonStart)) { it.plusWeeks(1) }
            .takeWhile { !it.plusDays(7).atStartOfDay().isAfter(now) }
            .toList()

    /** [now] 시점에 이미 끝난 보너스 대상 달. */
    fun closedMonths(seasonStart: LocalDate, now: LocalDateTime): List<YearMonth> =
        generateSequence(firstFullMonth(seasonStart)) { it.plusMonths(1) }
            .takeWhile { !it.plusMonths(1).atDay(1).atStartOfDay().isAfter(now) }
            .toList()
}

internal enum class PeriodResult { NO_WORK, PERFECT, CLOSE_ONLY, HAS_LATE, PENDING }

/** 한 기간의 출근 판정 목록으로 기간 결과를 정한다. null은 보류다. */
internal fun judgePeriod(verdicts: List<ArrivalVerdict?>): PeriodResult = when {
    verdicts.isEmpty() -> PeriodResult.NO_WORK
    verdicts.any { it == null } -> PeriodResult.PENDING
    verdicts.all { it!!.isOnTime } -> PeriodResult.PERFECT
    verdicts.any { it == ArrivalVerdict.LATE } -> PeriodResult.HAS_LATE
    else -> PeriodResult.CLOSE_ONLY
}
```

- [ ] **Step 4: 타임라인에 기간 종료 이벤트 추가**

`Timeline.kt`를 아래 내용으로 바꾼다.

```kotlin
package com.chronoquest.rules.engine

import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.model.Quest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

/** 원장에서 뽑은 시간순 이벤트. 같은 시각이면 [order], 그다음 [tieKey] 순으로 처리한다. */
internal sealed class Event(val at: LocalDateTime, val order: Int, val tieKey: String) {
    class Depart(at: LocalDateTime, val quest: Quest) : Event(at, 0, quest.id)
    class Arrive(at: LocalDateTime, val quest: Quest, val verdict: ArrivalVerdict) : Event(at, 1, quest.id)
    class Wake(at: LocalDateTime, val date: LocalDate) : Event(at, 2, date.toString())
    class SelfControl(at: LocalDateTime, val date: LocalDate) : Event(at, 3, date.toString())
    class WeekClose(val weekStart: LocalDate, val result: PeriodResult) :
        Event(weekStart.plusDays(7).atStartOfDay(), 4, weekStart.toString())
    class MonthClose(val month: YearMonth, val result: PeriodResult) :
        Event(month.plusMonths(1).atDay(1).atStartOfDay(), 5, month.toString())

    companion object {
        val ORDER: Comparator<Event> = compareBy<Event>({ it.at }, { it.order }, { it.tieKey })
    }
}
```

- [ ] **Step 5: 누산기에 기간 처리 추가**

`Accumulator.kt`에서 `handle`을 아래로 바꾼다.

```kotlin
    fun handle(event: Event) {
        when (event) {
            is Event.Depart -> onDepart(event)
            is Event.Arrive -> onArrive(event)
            is Event.Wake -> onWake(event)
            is Event.SelfControl -> onSelfControl(event)
            is Event.WeekClose -> onWeekClose(event)
            is Event.MonthClose -> onMonthClose(event)
        }
    }
```

`private val grants = mutableListOf<Grant>()` 바로 아래에 필드 두 개를 추가한다.

```kotlin
    /** 보류된 주가 나오면 그 뒤 주들은 판정하지 않는다(스펙 5.2 보류 규칙). */
    private var weeksBlocked = false
    private var monthsBlocked = false
```

`onSelfControl` 함수 바로 아래에 두 함수를 추가한다.

```kotlin
    private fun onWeekClose(e: Event.WeekClose) {
        if (weeksBlocked) return
        val key = "week:${e.weekStart}"
        when (e.result) {
            PeriodResult.NO_WORK, PeriodResult.CLOSE_ONLY -> Unit
            PeriodResult.PENDING -> weeksBlocked = true
            PeriodResult.HAS_LATE -> perfectWeekStreak = perfectWeekStreak.reset()
            PeriodResult.PERFECT -> {
                counters = counters.copy(perfectWeeks = counters.perfectWeeks + 1)
                perfectWeekStreak = perfectWeekStreak.advance()
                addXp("$key:xp", e.at, GrantType.XP_PERFECT_WEEK, XP_PERFECT_WEEK)
                if (perfectWeekStreak.current % 3 == 0) {
                    addXp("$key:3w", e.at, GrantType.XP_THREE_WEEKS, XP_THREE_WEEKS)
                }
                award(key, e.at, catalog.cumulativeAwards(Series.PERFECT_WEEK_TOTAL, counters.perfectWeeks))
                award(key, e.at, catalog.streakAwards(Series.PERFECT_WEEK_STREAK, perfectWeekStreak.current))
            }
        }
    }

    private fun onMonthClose(e: Event.MonthClose) {
        if (monthsBlocked) return
        val key = "month:${e.month}"
        when (e.result) {
            PeriodResult.PENDING -> monthsBlocked = true
            PeriodResult.PERFECT -> {
                counters = counters.copy(perfectMonths = counters.perfectMonths + 1)
                addXp("$key:xp", e.at, GrantType.XP_PERFECT_MONTH, XP_PERFECT_MONTH)
                award(key, e.at, catalog.cumulativeAwards(Series.PERFECT_MONTH_TOTAL, counters.perfectMonths))
            }
            PeriodResult.NO_WORK, PeriodResult.CLOSE_ONLY, PeriodResult.HAS_LATE -> Unit
        }
    }
```

- [ ] **Step 6: 엔진이 기간 종료 이벤트를 만들도록 수정**

`Engine.kt` 상단 import에 `import java.time.YearMonth`를 추가한다.

`compute` 안의 이벤트 생성 호출을 바꾼다.

변경 전:
```kotlin
        buildEvents(quests, records, days, outcomes)
```
변경 후:
```kotlin
        buildEvents(quests, records, days, outcomes, ledger.seasonStart, now)
```

`buildEvents` 함수 전체를 아래로 바꾼다.

```kotlin
    private fun buildEvents(
        quests: List<Quest>,
        records: Map<String, QuestRecord>,
        days: List<DayRecord>,
        outcomes: Map<String, QuestOutcome>,
        seasonStart: java.time.LocalDate,
        now: LocalDateTime,
    ): List<Event> {
        val events = mutableListOf<Event>()
        for (q in quests) {
            val record = records[q.id]
            val outcome = outcomes.getValue(q.id)
            val departedAt = record?.departedAt
            if (departedAt != null && outcome.onTimeDeparture) events += Event.Depart(departedAt, q)
            val verdict = outcome.verdict
            if (verdict != null) events += Event.Arrive(record?.arrivedAt ?: q.dueAt, q, verdict)
        }
        for (d in days) {
            val solvedAt = d.quizSolvedAt
            if (solvedAt != null && Judges.wakeSuccess(d)) events += Event.Wake(solvedAt, d.date)
            if (d.selfControl == true) {
                val firstDeparture = quests
                    .filter { it.date == d.date }
                    .mapNotNull { records[it.id]?.departedAt }
                    .minOrNull()
                events += Event.SelfControl(firstDeparture ?: d.date.atTime(23, 59), d.date)
            }
        }
        val work = quests.filter { it.kind == QuestKind.WORK }
        val workByWeek = work.groupBy { Periods.weekStart(it.date) }
        for (week in Periods.closedWeeks(seasonStart, now)) {
            val verdicts = workByWeek[week].orEmpty().map { outcomes.getValue(it.id).verdict }
            events += Event.WeekClose(week, judgePeriod(verdicts))
        }
        val workByMonth = work.groupBy { YearMonth.from(it.date) }
        for (month in Periods.closedMonths(seasonStart, now)) {
            val verdicts = workByMonth[month].orEmpty().map { outcomes.getValue(it.id).verdict }
            events += Event.MonthClose(month, judgePeriod(verdicts))
        }
        return events
    }
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :rules:test`
Expected: `BUILD SUCCESSFUL` (EngineCoreTest 포함 전체 통과)

- [ ] **Step 8: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/rules
git commit -m "feat(rules): 완벽 주간·월, 3주 보너스, 연속 완벽 주간" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: 복귀

**Files:**
- Create: `android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/ComebackTracker.kt`
- Modify: `android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/Accumulator.kt`
- Test: `android-core/rules/src/test/kotlin/com/chronoquest/rules/engine/ComebackTest.kt`

**Interfaces:**
- Consumes: Task 3 `Accumulator.onArrive`, `ArrivalVerdict`
- Produces: `internal class ComebackTracker { fun onArrival(verdict: ArrivalVerdict): Boolean }` (복귀 성립 시 true). 복귀 칭호 지급 키는 해당 도착의 `arr:<questId>:title:<161~180>`.

- [ ] **Step 1: 실패 테스트 작성**

`android-core/rules/src/test/kotlin/com/chronoquest/rules/engine/ComebackTest.kt`:

```kotlin
package com.chronoquest.rules.engine

import com.chronoquest.rules.at
import com.chronoquest.rules.judge.ArrivalVerdict.CLOSE
import com.chronoquest.rules.judge.ArrivalVerdict.EARLY
import com.chronoquest.rules.judge.ArrivalVerdict.LATE
import com.chronoquest.rules.judge.ArrivalVerdict.ON_TIME
import com.chronoquest.rules.late
import com.chronoquest.rules.ledger
import com.chronoquest.rules.onTime
import com.chronoquest.rules.other
import kotlin.test.Test
import kotlin.test.assertEquals

class ComebackTest {
    private fun run(vararg verdicts: com.chronoquest.rules.judge.ArrivalVerdict): List<Boolean> {
        val tracker = ComebackTracker()
        return verdicts.map { tracker.onArrival(it) }
    }

    @Test
    fun `지각 후 정시 3연속이면 복귀`() {
        assertEquals(listOf(false, false, false, true), run(LATE, ON_TIME, EARLY, ON_TIME))
    }

    @Test
    fun `아슬아슬은 복귀 카운트를 늘리지도 끊지도 않는다`() {
        assertEquals(listOf(false, false, false, false, true), run(LATE, ON_TIME, CLOSE, ON_TIME, ON_TIME))
    }

    @Test
    fun `복귀 대기 중 다시 지각하면 처음부터 센다`() {
        assertEquals(
            listOf(false, false, false, false, false, true),
            run(LATE, ON_TIME, LATE, ON_TIME, ON_TIME, ON_TIME),
        )
    }

    @Test
    fun `지각 없이 정시만 하면 복귀가 없다`() {
        assertEquals(listOf(false, false, false), run(ON_TIME, ON_TIME, ON_TIME))
    }

    @Test
    fun `지각 하나당 복귀는 한 번뿐이다`() {
        val results = run(LATE, ON_TIME, ON_TIME, ON_TIME, ON_TIME, ON_TIME, ON_TIME)
        assertEquals(1, results.count { it })
    }

    @Test
    fun `엔진이 복귀 횟수와 복귀 칭호를 지급한다`() {
        val qs = (0 until 4).map { other("2026-11-25", "%02d:00".format(10 + it)) }
        val rs = listOf(qs[0].late()) + qs.drop(1).map { it.onTime() }
        val s = Engine().compute(ledger(qs, rs), at("2026-11-29", "23:00"))
        assertEquals(1, s.counters.comebacks)
        assertEquals(1, s.inventory[161])
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :rules:test --tests "com.chronoquest.rules.engine.ComebackTest"`
Expected: FAIL, `Unresolved reference 'ComebackTracker'` 컴파일 오류.

- [ ] **Step 3: 복귀 추적기 구현**

`android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/ComebackTracker.kt`:

```kotlin
package com.chronoquest.rules.engine

import com.chronoquest.rules.judge.ArrivalVerdict

/**
 * 복귀 (스펙 5.6): 지각 후 정시 도착 3연속이면 1회.
 * 지각 하나당 최대 1회, CLOSE는 중립, 대기 중 재지각이면 처음부터 센다.
 */
internal class ComebackTracker {
    private var armed = false
    private var run = 0

    /** 이번 도착으로 복귀가 성립하면 true. */
    fun onArrival(verdict: ArrivalVerdict): Boolean {
        when (verdict) {
            ArrivalVerdict.LATE -> {
                armed = true
                run = 0
            }
            ArrivalVerdict.CLOSE -> Unit
            ArrivalVerdict.EARLY, ArrivalVerdict.ON_TIME -> if (armed) {
                run += 1
                if (run == REQUIRED) {
                    armed = false
                    run = 0
                    return true
                }
            }
        }
        return false
    }

    companion object {
        const val REQUIRED = 3
    }
}
```

- [ ] **Step 4: 누산기에 연결**

`Accumulator.kt`에서 `private var monthsBlocked = false` 바로 아래에 추가한다.

```kotlin
    private val comeback = ComebackTracker()
```

`onArrive` 함수의 `when (e.verdict) { ... }` 블록이 끝난 바로 다음(함수 닫는 중괄호 앞)에 추가한다.

```kotlin
        if (comeback.onArrival(e.verdict)) {
            counters = counters.copy(comebacks = counters.comebacks + 1)
            award(key, e.at, catalog.cumulativeAwards(Series.COMEBACK_TOTAL, counters.comebacks))
        }
```

- [ ] **Step 5: 테스트 통과 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :rules:test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/rules
git commit -m "feat(rules): 지각 후 정시 3연속 복귀" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: 펫 (진화와 기분)

**Files:**
- Create: `android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/Pet.kt`
- Modify: `android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/GameState.kt`
- Modify: `android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/Accumulator.kt`
- Modify: `android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/Engine.kt`
- Test: `android-core/rules/src/test/kotlin/com/chronoquest/rules/engine/PetTest.kt`

**Interfaces:**
- Consumes: Task 3 `Accumulator.addXp`, `Accumulator.onArrive`, `Engine.compute`
- Produces:
  - `enum class PetStage(val minXp: Int) { EGG(0), BABY(100), TEEN(700), ADULT(3000) }` with `val next: PetStage?`
  - `enum class PetForm { GOOD, NORMAL, UGLY }`, `enum class PetMood { HAPPY, NORMAL, SAD }`
  - `data class PetEvolution(stage: PetStage, form: PetForm, onTimeRate: Double?, at: LocalDateTime)`
  - `data class PetState(stage: PetStage, form: PetForm?, mood: PetMood, nextEvolveXp: Int?, history: List<PetEvolution>)`
  - `internal class PetTracker { fun onArrival(verdict); fun onXp(xp: Int, at: LocalDateTime): List<PetEvolution>; fun state(mood: PetMood): PetState; companion fun formFor(rate: Double?): PetForm; companion fun moodFor(todayVerdicts: List<ArrivalVerdict>): PetMood }`
  - `GameState.pet: PetState`. 진화 지급 키: `pet:evolve:<STAGE>`, detail `"<STAGE>:<FORM>"`

- [ ] **Step 1: 실패 테스트 작성**

XP가 출근 외 일정 도착(+20)에서만 나오게 해서 기간 보너스가 끼지 않게 한다.

`android-core/rules/src/test/kotlin/com/chronoquest/rules/engine/PetTest.kt`:

```kotlin
package com.chronoquest.rules.engine

import com.chronoquest.rules.at
import com.chronoquest.rules.dailyOthers
import com.chronoquest.rules.day
import com.chronoquest.rules.late
import com.chronoquest.rules.ledger
import com.chronoquest.rules.onTime
import com.chronoquest.rules.wakeDay
import com.chronoquest.rules.work
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PetTest {
    private val engine = Engine()

    @Test
    fun `시작은 알이다`() {
        val s = engine.compute(ledger(emptyList()), at("2026-11-25", "12:00"))
        assertEquals(PetStage.EGG, s.pet.stage)
        assertNull(s.pet.form)
        assertEquals(100, s.pet.nextEvolveXp)
        assertEquals(PetMood.NORMAL, s.pet.mood)
    }

    @Test
    fun `정시만 하고 100 XP가 되면 멋진 아기로 진화`() {
        val qs = dailyOthers("2026-11-25", 5)
        val s = engine.compute(ledger(qs, qs.map { it.onTime() }), at("2026-12-31", "23:00"))
        assertEquals(PetStage.BABY, s.pet.stage)
        assertEquals(PetForm.GOOD, s.pet.form)
        assertEquals(1.0, s.pet.history.single().onTimeRate)
        assertTrue(s.grants.any { it.key == "pet:evolve:BABY" && it.type == GrantType.PET_EVOLVED && it.detail == "BABY:GOOD" })
    }

    @Test
    fun `정시율이 60퍼센트 미만이면 못생긴 펫`() {
        val qs = dailyOthers("2026-11-25", 9)
        val rs = qs.take(4).map { it.late() } + qs.drop(4).map { it.onTime() }
        val s = engine.compute(ledger(qs, rs), at("2026-12-31", "23:00"))
        assertEquals(PetStage.BABY, s.pet.stage)
        assertEquals(PetForm.UGLY, s.pet.form)
    }

    @Test
    fun `정시율이 60에서 90퍼센트 사이면 평범한 펫`() {
        val qs = dailyOthers("2026-11-25", 8)
        val rs = qs.take(3).map { it.late() } + qs.drop(3).map { it.onTime() }
        val s = engine.compute(ledger(qs, rs), at("2026-12-31", "23:00"))
        assertEquals(PetForm.NORMAL, s.pet.form)
    }

    @Test
    fun `도착 기록 없이 진화하면 평범한 펫`() {
        val days = (0L until 20L).map { wakeDay(day("2026-11-25").plusDays(it).toString()) }
        val s = engine.compute(ledger(emptyList(), days = days), at("2026-12-31", "23:00"))
        assertEquals(PetStage.BABY, s.pet.stage)
        assertEquals(PetForm.NORMAL, s.pet.form)
        assertNull(s.pet.history.single().onTimeRate)
    }

    @Test
    fun `진화는 단계마다 따로 평가한다`() {
        val qs = dailyOthers("2026-11-25", 45)
        val rs = qs.take(5).map { it.onTime() } +
            qs.subList(5, 15).map { it.late() } +
            qs.drop(15).map { it.onTime() }
        val s = engine.compute(ledger(qs, rs), at("2027-01-31", "23:00"))
        assertEquals(700, s.xp)
        assertEquals(PetStage.TEEN, s.pet.stage)
        assertEquals(listOf(PetForm.GOOD, PetForm.NORMAL), s.pet.history.map { it.form })
        assertEquals(0.75, s.pet.history[1].onTimeRate)
        assertEquals(3000, s.pet.nextEvolveXp)
    }

    @Test
    fun `오늘 지각이 있으면 시무룩 정시만 있으면 신남 없으면 보통`() {
        val q = work("2026-11-25")
        val sad = engine.compute(ledger(listOf(q), listOf(q.late())), at("2026-11-25", "18:00"))
        val happy = engine.compute(ledger(listOf(q), listOf(q.onTime())), at("2026-11-25", "18:00"))
        val nextDay = engine.compute(ledger(listOf(q), listOf(q.late())), at("2026-11-26", "08:00"))
        assertEquals(PetMood.SAD, sad.pet.mood)
        assertEquals(PetMood.HAPPY, happy.pet.mood)
        assertEquals(PetMood.NORMAL, nextDay.pet.mood)
    }

    @Test
    fun `정시율 경계값`() {
        assertEquals(PetForm.GOOD, PetTracker.formFor(0.9))
        assertEquals(PetForm.NORMAL, PetTracker.formFor(0.8999))
        assertEquals(PetForm.NORMAL, PetTracker.formFor(0.6))
        assertEquals(PetForm.UGLY, PetTracker.formFor(0.5999))
        assertEquals(PetForm.NORMAL, PetTracker.formFor(null))
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :rules:test --tests "com.chronoquest.rules.engine.PetTest"`
Expected: FAIL, `Unresolved reference 'pet'` / `'PetStage'` 컴파일 오류.

- [ ] **Step 3: 펫 구현**

`android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/Pet.kt`:

```kotlin
package com.chronoquest.rules.engine

import com.chronoquest.rules.judge.ArrivalVerdict
import java.time.LocalDateTime

enum class PetStage(val minXp: Int) {
    EGG(0), BABY(100), TEEN(700), ADULT(3000);

    val next: PetStage? get() = entries.getOrNull(ordinal + 1)
}

enum class PetForm { GOOD, NORMAL, UGLY }

enum class PetMood { HAPPY, NORMAL, SAD }

data class PetEvolution(
    val stage: PetStage,
    val form: PetForm,
    val onTimeRate: Double?,
    val at: LocalDateTime,
)

data class PetState(
    val stage: PetStage,
    val form: PetForm?,
    val mood: PetMood,
    val nextEvolveXp: Int?,
    val history: List<PetEvolution>,
)

/**
 * 펫 (스펙 5.8): 누적 XP로 진화하고, 진화 순간 직전 단계의 정시율로 모습이 정해진다.
 * 성장은 줄어들지 않는다.
 */
internal class PetTracker {
    private var stage = PetStage.EGG
    private var onTime = 0
    private var total = 0
    private val history = mutableListOf<PetEvolution>()

    fun onArrival(verdict: ArrivalVerdict) {
        total += 1
        if (verdict.isOnTime) onTime += 1
    }

    /** XP가 바뀔 때마다 호출한다. 이번에 일어난 진화를 돌려준다. */
    fun onXp(xp: Int, at: LocalDateTime): List<PetEvolution> {
        val evolved = mutableListOf<PetEvolution>()
        while (true) {
            val next = stage.next ?: break
            if (xp < next.minXp) break
            val rate = if (total == 0) null else onTime.toDouble() / total
            val evolution = PetEvolution(next, formFor(rate), rate, at)
            history += evolution
            evolved += evolution
            stage = next
            onTime = 0
            total = 0
        }
        return evolved
    }

    fun state(mood: PetMood): PetState = PetState(
        stage = stage,
        form = history.lastOrNull()?.form,
        mood = mood,
        nextEvolveXp = stage.next?.minXp,
        history = history.toList(),
    )

    companion object {
        const val GOOD_RATE = 0.9
        const val NORMAL_RATE = 0.6

        fun formFor(rate: Double?): PetForm = when {
            rate == null -> PetForm.NORMAL
            rate >= GOOD_RATE -> PetForm.GOOD
            rate >= NORMAL_RATE -> PetForm.NORMAL
            else -> PetForm.UGLY
        }

        fun moodFor(todayVerdicts: List<ArrivalVerdict>): PetMood = when {
            todayVerdicts.any { it == ArrivalVerdict.LATE } -> PetMood.SAD
            todayVerdicts.any { it.isOnTime } -> PetMood.HAPPY
            else -> PetMood.NORMAL
        }
    }
}
```

- [ ] **Step 4: GameState에 펫 추가**

`GameState.kt`의 `GameState`에서 `val grants: List<Grant>,` 바로 위에 한 줄을 추가한다.

```kotlin
    val pet: PetState,
```

- [ ] **Step 5: 누산기에 펫 연결**

`Accumulator.kt`에서 `private val comeback = ComebackTracker()` 바로 아래에 추가한다.

```kotlin
    val pet = PetTracker()
```

`onArrive` 함수 첫 줄(`val key = "arr:${e.quest.id}"` 위)에 추가한다.

```kotlin
        pet.onArrival(e.verdict)
```

`addXp` 함수 전체를 아래로 바꾼다.

```kotlin
    private fun addXp(key: String, at: LocalDateTime, type: GrantType, amount: Int) {
        xp += amount
        grants += Grant(key = key, at = at, type = type, xp = amount)
        pet.onXp(xp, at).forEach { ev ->
            grants += Grant(
                key = "pet:evolve:${ev.stage}",
                at = at,
                type = GrantType.PET_EVOLVED,
                detail = "${ev.stage}:${ev.form}",
            )
        }
    }
```

- [ ] **Step 6: 엔진에서 펫 상태 조립**

`Engine.kt`의 `compute`에서 `val tier = Tier.of(acc.xp)` 바로 위에 추가한다.

```kotlin
        val today = now.toLocalDate()
        val mood = PetTracker.moodFor(
            quests.filter { it.date == today }.mapNotNull { outcomes.getValue(it.id).verdict },
        )
```

`GameState(...)` 생성자 호출에서 `grants = grants,` 바로 위에 추가한다.

```kotlin
            pet = acc.pet.state(mood),
```

- [ ] **Step 7: 테스트 통과 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :rules:test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/rules
git commit -m "feat(rules): 펫 진화(정시율 분기)와 기분" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: 주간 보스

**Files:**
- Create: `android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/Boss.kt`
- Modify: `android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/GameState.kt`
- Modify: `android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/Engine.kt`
- Test: `android-core/rules/src/test/kotlin/com/chronoquest/rules/engine/BossTest.kt`

**Interfaces:**
- Consumes: Task 3 `QuestOutcome`, `Grant`, `GrantType` / Task 4 `Periods.weekStart` / Task 2 `Judges.wakeSuccess`
- Produces:
  - `data class BossDef(id: String, name: String)`, `enum class BossStatus { ACTIVE, DEFEATED, ESCAPED, PENDING }`
  - `data class BossWeek(weekStart: LocalDate, boss: BossDef, maxHp: Int, damage: Int, status: BossStatus)`
  - `data class BossState(current: BossWeek?, history: List<BossWeek>, defeats: Map<String, Int>)`
  - `internal object Bosses { val ROSTER: List<BossDef>; fun compute(quests, outcomes, wakeDays: Set<LocalDate>, selfControlDays: Set<LocalDate>, seasonStart: LocalDate, now: LocalDateTime): BossState; fun resultGrants(state: BossState): List<Grant> }`
  - `GameState.boss: BossState`. 결과 지급 키: `boss:<weekStart>:result`, detail `"<STATUS>:<bossId>"`

- [ ] **Step 1: 실패 테스트 작성**

`android-core/rules/src/test/kotlin/com/chronoquest/rules/engine/BossTest.kt`:

```kotlin
package com.chronoquest.rules.engine

import com.chronoquest.rules.arrived
import com.chronoquest.rules.at
import com.chronoquest.rules.day
import com.chronoquest.rules.ledger
import com.chronoquest.rules.onTime
import com.chronoquest.rules.onTimeAll
import com.chronoquest.rules.other
import com.chronoquest.rules.wakeDay
import com.chronoquest.rules.weekdays
import com.chronoquest.rules.work
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BossTest {
    private val engine = Engine()
    private val week1 = weekdays("2026-11-30", "2026-12-04").map { work(it) }
    private val afterWeek1 = at("2026-12-07", "00:00")

    @Test
    fun `출근 정시와 정시 출발로 HP 이상 때리면 처치`() {
        val s = engine.compute(
            ledger(week1, week1.map { it.arrived("08:55", departed = "08:15") }),
            afterWeek1,
        )
        val w = s.boss.history.single()
        assertEquals("snooze_slime", w.boss.id)
        assertEquals(500, w.maxHp)
        assertEquals(600, w.damage)
        assertEquals(BossStatus.DEFEATED, w.status)
        assertEquals(mapOf("snooze_slime" to 1), s.boss.defeats)
        assertNull(s.boss.current)
        assertTrue(s.grants.any { it.key == "boss:2026-11-30:result" && it.detail == "DEFEATED:snooze_slime" })
    }

    @Test
    fun `지각이 하나 있어도 다른 퀘스트로 만회할 수 있다`() {
        val withDeparture = week1.mapIndexed { i, q ->
            if (i == 0) q.arrived("09:30", departed = "08:15") else q.arrived("08:55", departed = "08:15")
        }
        val withoutDeparture = week1.mapIndexed { i, q ->
            if (i == 0) q.arrived("09:30") else q.arrived("08:55")
        }
        assertEquals(
            BossStatus.DEFEATED,
            engine.compute(ledger(week1, withDeparture), afterWeek1).boss.history.single().status,
        )
        assertEquals(
            BossStatus.ESCAPED,
            engine.compute(ledger(week1, withoutDeparture), afterWeek1).boss.history.single().status,
        )
    }

    @Test
    fun `주가 진행 중이면 현재 보스가 활성 상태`() {
        val rs = listOf(week1[0].arrived("08:55"), week1[1].arrived("08:55"))
        val s = engine.compute(ledger(week1, rs), at("2026-12-02", "12:00"))
        val current = assertNotNull(s.boss.current)
        assertEquals(BossStatus.ACTIVE, current.status)
        assertEquals(200, current.damage)
    }

    @Test
    fun `끝난 주에 보류가 있으면 결과도 보류`() {
        val s = engine.compute(ledger(week1, onTimeAll(week1.drop(1))), afterWeek1)
        assertEquals(BossStatus.PENDING, s.boss.history.single().status)
        assertTrue(s.grants.none { it.type == GrantType.BOSS_RESULT })
    }

    @Test
    fun `휴가로 빠진 출근만큼 HP가 줄어든다`() {
        val s = engine.compute(
            ledger(week1, onTimeAll(week1), vacations = setOf(day("2026-12-02"))),
            afterWeek1,
        )
        assertEquals(400, s.boss.history.single().maxHp)
    }

    @Test
    fun `다른 일정 조기 기상 행동통제도 데미지를 준다`() {
        val w = work("2026-11-30")
        val o = other("2026-11-30")
        val s = engine.compute(
            ledger(
                listOf(w, o),
                listOf(w.arrived("08:45"), o.onTime()),
                days = listOf(wakeDay("2026-11-30", selfControl = true)),
            ),
            afterWeek1,
        )
        val b = s.boss.history.single()
        assertEquals(100, b.maxHp)
        assertEquals(100 + 30 + 50 + 10 + 10, b.damage)
    }

    @Test
    fun `출근 없는 주는 보스가 없고 순서도 넘어가지 않는다`() {
        val o = other("2026-11-30")
        val w = work("2026-12-07")
        val s = engine.compute(ledger(listOf(o, w), listOf(o.onTime(), w.onTime())), at("2026-12-14", "00:00"))
        assertEquals(listOf("snooze_slime"), s.boss.history.map { it.boss.id })
        assertEquals(day("2026-12-07"), s.boss.history.single().weekStart)
    }

    @Test
    fun `보스는 로스터 순서대로 돈다`() {
        val qs = listOf(work("2026-11-30"), work("2026-12-07"))
        val s = engine.compute(ledger(qs, onTimeAll(qs)), at("2026-12-14", "00:00"))
        assertEquals(listOf("snooze_slime", "five_more_golem"), s.boss.history.map { it.boss.id })
    }

    @Test
    fun `시즌 첫 주에도 남은 출근으로 보스가 나온다`() {
        val q = work("2026-11-26")
        val s = engine.compute(ledger(listOf(q), listOf(q.onTime())), at("2026-11-30", "00:00"))
        val b = s.boss.history.single()
        assertEquals(day("2026-11-23"), b.weekStart)
        assertEquals(100, b.maxHp)
        assertEquals(BossStatus.DEFEATED, b.status)
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :rules:test --tests "com.chronoquest.rules.engine.BossTest"`
Expected: FAIL, `Unresolved reference 'boss'` / `'BossStatus'` 컴파일 오류.

- [ ] **Step 3: 보스 구현**

`android-core/rules/src/main/kotlin/com/chronoquest/rules/engine/Boss.kt`:

```kotlin
package com.chronoquest.rules.engine

import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.model.Quest
import com.chronoquest.rules.model.QuestKind
import java.time.LocalDate
import java.time.LocalDateTime

data class BossDef(val id: String, val name: String)

enum class BossStatus { ACTIVE, DEFEATED, ESCAPED, PENDING }

data class BossWeek(
    val weekStart: LocalDate,
    val boss: BossDef,
    val maxHp: Int,
    val damage: Int,
    val status: BossStatus,
)

data class BossState(
    val current: BossWeek?,
    val history: List<BossWeek>,
    val defeats: Map<String, Int>,
)

/** 주간 보스 (스펙 5.9). XP를 주지 않는 수집용 레이어다. */
internal object Bosses {
    val ROSTER = listOf(
        BossDef("snooze_slime", "스누즈 슬라임"),
        BossDef("five_more_golem", "5분만 더 골렘"),
        BossDef("blanket_dragon", "이불 드래곤"),
        BossDef("traffic_worm", "교통체증 지렁이"),
        BossDef("elevator_ghost", "엘리베이터 유령"),
        BossDef("sock_fairy", "양말 실종 요정"),
        BossDef("signal_octopus", "신호등 문어"),
        BossDef("tardy_king", "지각마왕"),
    )

    const val HP_PER_WORK = 100
    const val DMG_WORK = 100
    const val DMG_OTHER = 50
    const val DMG_EARLY = 30
    const val DMG_DEPART = 20
    const val DMG_WAKE = 10
    const val DMG_SELF_CONTROL = 10

    fun compute(
        quests: List<Quest>,
        outcomes: Map<String, QuestOutcome>,
        wakeDays: Set<LocalDate>,
        selfControlDays: Set<LocalDate>,
        seasonStart: LocalDate,
        now: LocalDateTime,
    ): BossState {
        val nowWeek = Periods.weekStart(now.toLocalDate())
        val byWeek = quests.groupBy { Periods.weekStart(it.date) }
        val history = mutableListOf<BossWeek>()
        var index = 0
        val weeks = generateSequence(Periods.weekStart(seasonStart)) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(nowWeek) }
        for (week in weeks) {
            val weekQuests = byWeek[week].orEmpty()
            val workCount = weekQuests.count { it.kind == QuestKind.WORK }
            if (workCount == 0) continue
            val boss = ROSTER[index % ROSTER.size]
            index += 1
            val maxHp = workCount * HP_PER_WORK
            var damage = 0
            for (q in weekQuests) {
                val o = outcomes.getValue(q.id)
                if (o.verdict?.isOnTime == true) {
                    damage += if (q.kind == QuestKind.WORK) DMG_WORK else DMG_OTHER
                    if (o.verdict == ArrivalVerdict.EARLY) damage += DMG_EARLY
                }
                if (o.onTimeDeparture) damage += DMG_DEPART
            }
            val days = (0L..6L).map { week.plusDays(it) }
            damage += days.count { it in wakeDays } * DMG_WAKE
            damage += days.count { it in selfControlDays } * DMG_SELF_CONTROL
            val ended = !week.plusDays(7).atStartOfDay().isAfter(now)
            val status = when {
                !ended -> BossStatus.ACTIVE
                weekQuests.any { outcomes.getValue(it.id).verdict == null } -> BossStatus.PENDING
                damage >= maxHp -> BossStatus.DEFEATED
                else -> BossStatus.ESCAPED
            }
            history += BossWeek(week, boss, maxHp, damage, status)
        }
        val current = history.lastOrNull()?.takeIf { it.weekStart == nowWeek }
        val defeats = history
            .filter { it.status == BossStatus.DEFEATED }
            .groupingBy { it.boss.id }
            .eachCount()
        return BossState(current = current, history = history.toList(), defeats = defeats)
    }

    fun resultGrants(state: BossState): List<Grant> =
        state.history
            .filter { it.status == BossStatus.DEFEATED || it.status == BossStatus.ESCAPED }
            .map {
                Grant(
                    key = "boss:${it.weekStart}:result",
                    at = it.weekStart.plusDays(7).atStartOfDay(),
                    type = GrantType.BOSS_RESULT,
                    detail = "${it.status}:${it.boss.id}",
                )
            }
}
```

- [ ] **Step 4: GameState에 보스 추가**

`GameState.kt`의 `GameState`에서 `val pet: PetState,` 바로 아래에 한 줄을 추가한다.

```kotlin
    val boss: BossState,
```

- [ ] **Step 5: 엔진에서 보스 계산과 지급 병합**

`Engine.kt`의 `compute`에서 아래 줄을 바꾼다.

변경 전:
```kotlin
        val grants = acc.grants()
```
변경 후:
```kotlin
        val boss = Bosses.compute(
            quests = quests,
            outcomes = outcomes,
            wakeDays = days.filter(Judges::wakeSuccess).map { it.date }.toSet(),
            selfControlDays = days.filter { it.selfControl == true }.map { it.date }.toSet(),
            seasonStart = ledger.seasonStart,
            now = now,
        )
        val grants = (acc.grants() + Bosses.resultGrants(boss)).sortedBy { it.at }
```

`GameState(...)` 생성자 호출에서 `pet = acc.pet.state(mood),` 바로 아래에 추가한다.

```kotlin
            boss = boss,
```

- [ ] **Step 6: 테스트 통과 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :rules:test`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/rules
git commit -m "feat(rules): 주간 보스 HP·데미지·로스터·결과" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: 결정성·재계산 검증

**Files:**
- Test: `android-core/rules/src/test/kotlin/com/chronoquest/rules/engine/DeterminismTest.kt`

**Interfaces:**
- Consumes: Task 3~7의 `Engine.compute`, `GameState` 전체
- Produces: 없음 (스펙 D9 "같은 입력이면 같은 결과" 보증 테스트)

- [ ] **Step 1: 테스트 작성**

`android-core/rules/src/test/kotlin/com/chronoquest/rules/engine/DeterminismTest.kt`:

```kotlin
package com.chronoquest.rules.engine

import com.chronoquest.rules.at
import com.chronoquest.rules.close
import com.chronoquest.rules.dailyOthers
import com.chronoquest.rules.day
import com.chronoquest.rules.early
import com.chronoquest.rules.late
import com.chronoquest.rules.ledger
import com.chronoquest.rules.onTime
import com.chronoquest.rules.wakeDay
import com.chronoquest.rules.weekdays
import com.chronoquest.rules.work
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeterminismTest {
    private val engine = Engine()
    private val now = at("2027-01-01", "00:00")
    private val quests = weekdays("2026-11-25", "2026-12-31").map { work(it) } +
        dailyOthers("2026-11-25", 30, due = "19:00")
    private val records = quests.mapIndexed { i, q ->
        when (i % 7) {
            3 -> q.close()
            5 -> q.late()
            else -> if (i % 2 == 0) q.onTime() else q.early()
        }
    }
    private val days = (0L until 30L).map {
        wakeDay(day("2026-11-25").plusDays(it).toString(), selfControl = it % 3 == 0L)
    }

    @Test
    fun `입력 순서를 섞어도 결과가 같다`() {
        val base = engine.compute(ledger(quests, records, days), now)
        val random = Random(42)
        repeat(5) {
            val shuffled = ledger(quests.shuffled(random), records.shuffled(random), days.shuffled(random))
            assertEquals(base, engine.compute(shuffled, now))
        }
    }

    @Test
    fun `기록을 지우면 그 기록으로 받은 지급이 사라진다`() {
        val q = work("2026-11-25")
        val with = engine.compute(ledger(listOf(q), listOf(q.onTime())), now)
        val without = engine.compute(ledger(listOf(q), emptyList()), now)
        assertEquals(1, with.inventory[1])
        assertNull(without.inventory[1])
        assertEquals(0, without.xp)
        assertEquals(listOf(q.id), without.pendingQuestIds)
    }
}
```

- [ ] **Step 2: 테스트 실행**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :rules:test`
Expected: `BUILD SUCCESSFUL`. 실패하면 `Event.ORDER`의 동률 처리(`tieKey`)나 `pendingQuestIds` 정렬처럼 입력 순서에 의존하는 곳을 찾아 고친 뒤 다시 실행한다.

- [ ] **Step 3: 전체 테스트 수 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :rules:test --rerun-tasks && find rules/build/test-results/test -name '*.xml' | xargs grep -h -o 'tests="[0-9]*"' | awk -F'"' '{s+=$2} END {print s}'`
Expected: `66` (TitleCatalog 7 + Judges 10 + Tier 2 + EngineCore 12 + Period 10 + Comeback 6 + Pet 8 + Boss 9 + Determinism 2)

- [ ] **Step 4: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/rules
git commit -m "test(rules): 입력 순서 결정성과 재계산 검증" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## 완료 기준

- `./gradlew :rules:test`가 66개 테스트 모두 통과한다.
- 스펙 5장의 판정 규칙(5.1~5.10)이 각각 최소 하나의 테스트로 덮여 있다.
- `rules` 모듈은 안드로이드·Unity 의존 없이 JVM에서만 돈다.
- 다음 단계: A2 계획(데이터 계층 + 수동 플레이 + Compose 임시 화면) 작성.
