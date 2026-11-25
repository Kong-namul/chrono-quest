# 크로노 퀘스트 A2a: 데이터 계층·로직 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 안드로이드 라이브러리 모듈 `core`에 Room 저장소와 그 위의 로직을 만든다. 로직은 일정 생성, 수동 출발·도착 기록 검증, 판정 엔진 연결, 연출 비교, 대표 칭호, 오늘 요약, 정리 필요 목록이다. 모두 폰 없이 맥에서 테스트로 검증한다.

**Architecture:** `android-core/core`(Android library)가 `rules`(A1 판정 엔진)를 `api`로 의존한다. 세 층으로 나눈다.
- `data`: Room 엔터티, DAO, DB, Room→`Ledger` 매핑
- `domain`: 순수 Kotlin 규칙. JUnit만으로 테스트한다
- `service`: Room과 엔진을 묶은 쓰기·조회 창구. Robolectric으로 테스트한다

화면, 알림, 실행용 앱은 A2b에서 만든다.

**Tech Stack:** Gradle 8.14.3 (기존 wrapper), Kotlin 2.2.20, AGP 8.13.2, KSP 2.2.20-2.0.4, Room 2.8.3 (+ Room Gradle 플러그인), kotlinx-coroutines 1.10.2, JUnit 4.13.2, Robolectric 4.16.1, androidx.test core 1.7.0 / ext-junit 1.3.0, Android SDK(platform 36, build-tools 35.0.0), JDK 21 (Temurin)

**스펙:** [`docs/superpowers/specs/2026-11-25-chrono-quest-design.md`](../specs/2026-11-25-chrono-quest-design.md) 4장(데이터 모델), 5.10(지급 키), 6.2(기록 흐름), 6.4(일정 소스)
**인계 메모:** [`docs/superpowers/notes/2026-11-25-a1-carryover.md`](../notes/2026-11-25-a1-carryover.md)의 "A2" 절

## Global Constraints

- 버전 고정: AGP `8.13.2`, Kotlin `2.2.20`, KSP `2.2.20-2.0.4`, Room `2.8.3`, coroutines `1.10.2`, Robolectric `4.16.1`, androidx.test core `1.7.0`, ext-junit `1.3.0`, JUnit `4.13.2`. Gradle wrapper는 `8.14.3`을 그대로 쓴다.
- `core` 모듈: namespace `com.chronoquest.core`, `compileSdk = 36`, `minSdk = 26`, Java/Kotlin 바이트코드 17.
- `core`는 `api(project(":rules"))`로 판정 엔진을 노출한다. `rules` 모듈 코드는 수정하지 않는다.
- 시각 저장: 로컬 벽시계 시각이다. `LocalDateTime`은 `yyyy-MM-dd'T'HH:mm:ss`(초까지), `LocalDate`는 `yyyy-MM-dd`, `LocalTime`은 `HH:mm` 문자열로 저장한다. 타임존 변환은 하지 않는다.
- 원장 계약(인계 메모 A2):
  - Room 기본키로 중복을 막는다(`quest.id`, `quest_record.questId`, `day_record.date`).
  - 미래 시각 기록은 거부한다.
  - 안전 여부는 도착과 함께만 저장한다.
  - `QuestEntity.date`는 항상 `dueAt.toLocalDate()`다.
- 퀘스트 ID: `QuestIds.of(date, kind, dueTime)` = `"yyyy-MM-dd-<KIND>-HH:mm"`. A1 테스트 픽스처와 같은 형식이다.
- 스펙 4장 테이블 중 `Place`(A4), `AlarmSetting`(A3), `Pending`(A4)은 쓰는 계획에서 Room 자동 마이그레이션(v1→v2)으로 추가한다. A2a 스키마는 v1이다. 엔터티의 `fromPlaceId`/`toPlaceId`는 외래키 없는 nullable 열로 미리 둔다.
- 시즌 시작 기본값: `2026-11-25`
- "정리 필요"는 예정 시각 +3시간이 지나도 판정이 안 된 일정이다(스펙 6.2).
- 연출 키: 엔진 지급 키를 그대로 쓰고, 티어 승급만 `tier:<TIER>`로 추가한다. 일일 XP 지급(`arr:`/`dep:`/`wake:`/`self:`의 `:xp`, `:early`)은 연출 대상이 아니다.
- 테스트:
  - `domain`과 `LedgerMapper`는 JUnit4 순수 테스트
  - DB와 `service`는 Robolectric(`@RunWith(AndroidJUnit4::class)`, `robolectric.properties`의 `sdk=35`)
  - 테스트 함수는 한국어 백틱 이름을 쓰고 `.` `:` `/` `<` `>` `[` `]`는 넣지 않는다
- 작업 브랜치는 `a2a-data-layer`이고 `main`에서 딴다. 커밋 메시지 끝에는 `Co-Authored-By` 트레일러를 붙인다.
- 네트워크가 느리다(약 400KB/s). Gradle은 처음 받을 때만 온라인으로 돌리고(최대 15분 대기), 그다음부터는 `--offline`을 쓴다.
- Gradle 명령은 `/Users/sang/Desktop/chrono-oath/android-core`에서 실행한다.

## 파일 구조

```
android-core/
  settings.gradle.kts            # :core 추가
  build.gradle.kts               # Android·KSP·Room 플러그인 선언 추가
  gradle.properties              # android.useAndroidX 추가
  local.properties               # sdk.dir (git 제외, 이미 .gitignore에 있음)
  core/
    build.gradle.kts
    schemas/com.chronoquest.core.data.ChronoDatabase/1.json   # Room 스키마 (커밋)
    src/main/AndroidManifest.xml
    src/main/kotlin/com/chronoquest/core/
      data/Converters.kt         # 시각 ↔ 문자열
      data/Entities.kt           # Room 엔터티 7개 + QuestSource, RecordSource
      data/Daos.kt               # DAO 7개
      data/ChronoDatabase.kt
      data/LedgerMapper.kt       # 엔터티 → rules.Ledger
      domain/QuestIds.kt
      domain/TemplateRules.kt    # 요일 마스크, 일정 검증, ScheduleProblem
      domain/QuestGenerator.kt   # 템플릿 → 퀘스트
      domain/RecordPolicy.kt     # 출발·도착·수정·정리 규칙, RejectReason
      domain/Celebrations.kt     # 연출 목록(지급 + 티어 승급)
      domain/RepresentativeTitle.kt
      domain/CleanupFilter.kt
      domain/Today.kt            # 오늘 요약
      service/Clocks.kt
      service/LedgerRepository.kt
      service/GameService.kt     # 기록 쓰기, 연출, 화면용 조회
      service/ScheduleService.kt # 템플릿, 일정 생성, 단발, 휴가
    src/test/resources/robolectric.properties
    src/test/kotlin/com/chronoquest/core/
      CoreFixtures.kt
      data/DatabaseTest.kt, data/LedgerMapperTest.kt
      domain/QuestIdsTest.kt, domain/TemplateRulesTest.kt, domain/QuestGeneratorTest.kt,
      domain/RecordPolicyTest.kt, domain/DerivedViewsTest.kt
      service/LedgerRepositoryTest.kt, service/GameServiceRecordTest.kt,
      service/ScheduleServiceTest.kt, service/GameServiceViewTest.kt
.github/workflows/ci.yml
```

---

### Task 1: 안드로이드 SDK 설치, `core` 모듈 셋업, 퀘스트 ID

**Files:**
- Create: `android-core/local.properties`
- Modify: `android-core/settings.gradle.kts`, `android-core/build.gradle.kts`, `android-core/gradle.properties`
- Create: `android-core/core/build.gradle.kts`, `android-core/core/src/main/AndroidManifest.xml`
- Create: `android-core/core/src/main/kotlin/com/chronoquest/core/domain/QuestIds.kt`
- Test: `android-core/core/src/test/kotlin/com/chronoquest/core/domain/QuestIdsTest.kt`

**Interfaces:**
- Consumes: `com.chronoquest.rules.model.QuestKind` (A1)
- Produces: `object QuestIds { fun of(date: LocalDate, kind: QuestKind, dueTime: LocalTime): String }`, 빌드 가능한 `:core` 모듈

- [ ] **Step 1: 안드로이드 SDK 설치 (컨트롤러가 사용자 승인 후 직접 수행)**

다운로드(약 315 MB)와 Android SDK 라이선스 동의가 들어 있다. 그래서 구현 서브에이전트가 아니라 **컨트롤러가 사용자에게 승인받은 뒤** 실행한다.

```bash
SDK="$HOME/Library/Android/sdk"
DL=/private/tmp/claude-501/-Users-sang--claude-spares-spare-NigNK3/896025d2-8efa-43ad-a190-a252c46db6a5/scratchpad/downloads
mkdir -p "$DL" "$SDK/cmdline-tools"
curl -fSL -C - --max-time 900 -o "$DL/cmdline-tools.zip" https://dl.google.com/android/repository/commandlinetools-mac_x86_64-16111833_latest.zip
echo "112cf9618794a997ff273537d55bee02c22abffe  $DL/cmdline-tools.zip" | shasum -a 1 -c -
rm -rf "$DL/ct" && mkdir -p "$DL/ct" && unzip -q "$DL/cmdline-tools.zip" -d "$DL/ct"
mv "$DL/ct/cmdline-tools" "$SDK/cmdline-tools/latest"
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses
"$SDK/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-36" "build-tools;35.0.0"
ls "$SDK/platforms" "$SDK/build-tools"
```

Expected: `cmdline-tools.zip: OK`, 마지막 줄에 `android-36`과 `35.0.0`이 보인다. 다운로드가 시간 제한에 걸리면 같은 `curl -C -` 명령을 다시 실행해 이어받는다.

- [ ] **Step 2: SDK 경로와 Gradle 설정**

`android-core/local.properties` (git 제외 파일):

```properties
sdk.dir=/Users/sang/Library/Android/sdk
```

`android-core/settings.gradle.kts`의 `include(":rules")`를 아래로 바꾼다.

```kotlin
include(":rules", ":core")
```

`android-core/build.gradle.kts` 전체:

```kotlin
plugins {
    kotlin("jvm") version "2.2.20" apply false
    kotlin("plugin.serialization") version "2.2.20" apply false
    kotlin("android") version "2.2.20" apply false
    id("com.android.library") version "8.13.2" apply false
    id("com.google.devtools.ksp") version "2.2.20-2.0.4" apply false
    id("androidx.room") version "2.8.3" apply false
}
```

`android-core/gradle.properties` 끝에 한 줄을 추가한다.

```properties
android.useAndroidX=true
```

`android-core/core/build.gradle.kts`:

```kotlin
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library")
    kotlin("android")
    id("com.google.devtools.ksp")
    id("androidx.room")
}

android {
    namespace = "com.chronoquest.core"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":rules"))
    implementation("androidx.room:room-runtime:2.8.3")
    implementation("androidx.room:room-ktx:2.8.3")
    ksp("androidx.room:room-compiler:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(kotlin("test-junit"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
```

`android-core/core/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" />
```

- [ ] **Step 3: 실패 테스트 작성**

`android-core/core/src/test/kotlin/com/chronoquest/core/domain/QuestIdsTest.kt`:

```kotlin
package com.chronoquest.core.domain

import com.chronoquest.rules.model.QuestKind
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.assertEquals
import org.junit.Test

class QuestIdsTest {
    @Test
    fun `날짜 종류 예정 시각으로 퀘스트 ID를 만든다`() {
        assertEquals(
            "2026-11-25-WORK-09:00",
            QuestIds.of(LocalDate.parse("2026-11-25"), QuestKind.WORK, LocalTime.parse("09:00")),
        )
        assertEquals(
            "2026-12-01-OTHER-19:30",
            QuestIds.of(LocalDate.parse("2026-12-01"), QuestKind.OTHER, LocalTime.parse("19:30")),
        )
    }

    @Test
    fun `예정 시각의 초는 버린다`() {
        assertEquals(
            "2026-11-25-WORK-09:00",
            QuestIds.of(LocalDate.parse("2026-11-25"), QuestKind.WORK, LocalTime.parse("09:00:45")),
        )
    }
}
```

- [ ] **Step 4: 실패 확인 (첫 실행은 AGP·Room 등 다운로드로 오래 걸림)**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :core:testDebugUnitTest`
Expected: FAIL, `Unresolved reference 'QuestIds'` 컴파일 오류. 다운로드가 끝나지 않아 실패하면 같은 명령을 다시 실행한다.

- [ ] **Step 5: 구현**

`android-core/core/src/main/kotlin/com/chronoquest/core/domain/QuestIds.kt`:

```kotlin
package com.chronoquest.core.domain

import com.chronoquest.rules.model.QuestKind
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** 퀘스트 ID = "날짜-종류-예정시각(HH:mm)". 룰북 03-01: 날짜·종류·예정 시각이 같으면 같은 일정이다. */
object QuestIds {
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun of(date: LocalDate, kind: QuestKind, dueTime: LocalTime): String =
        "$date-$kind-${dueTime.format(TIME)}"
}
```

- [ ] **Step 6: 통과 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :core:testDebugUnitTest :core:assembleDebug :rules:test --offline`
Expected: `BUILD SUCCESSFUL`. QuestIdsTest 2개가 통과하고, rules 72개도 그대로 통과한다.

- [ ] **Step 7: 커밋** (`local.properties`는 `.gitignore`로 제외된다)

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/settings.gradle.kts android-core/build.gradle.kts android-core/gradle.properties android-core/core
git status --short
git commit -m "feat(core): 안드로이드 core 모듈 셋업과 퀘스트 ID" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Room 스키마 (엔터티, 변환기, DAO, DB)

**Files:**
- Create: `android-core/core/src/main/kotlin/com/chronoquest/core/data/Converters.kt`
- Create: `android-core/core/src/main/kotlin/com/chronoquest/core/data/Entities.kt`
- Create: `android-core/core/src/main/kotlin/com/chronoquest/core/data/Daos.kt`
- Create: `android-core/core/src/main/kotlin/com/chronoquest/core/data/ChronoDatabase.kt`
- Create: `android-core/core/src/test/resources/robolectric.properties`
- Create: `android-core/core/src/test/kotlin/com/chronoquest/core/CoreFixtures.kt`
- Test: `android-core/core/src/test/kotlin/com/chronoquest/core/data/DatabaseTest.kt`
- Generated (commit): `android-core/core/schemas/com.chronoquest.core.data.ChronoDatabase/1.json`

**Interfaces:**
- Consumes: Task 1 `QuestIds`; A1 `QuestKind`, `QuestStatus`, `Resolution`
- Produces:
  - 엔터티: `ScheduleTemplateEntity`, `QuestEntity`, `QuestRecordEntity`, `DayRecordEntity`, `VacationEntity`, `CelebrationSeenEntity`, `AppSettingsEntity` (필드는 아래 코드 그대로), `enum QuestSource { TEMPLATE, CALENDAR, MANUAL }`, `enum RecordSource { GEOFENCE, BUTTON, EDIT }`
  - DAO: `TemplateDao`, `QuestDao`(`all`, `get`, `onDate`, `allIds`, `insertAll`, `update`, `deleteIds`, `unrecordedFutureIds`), `RecordDao`(`all`, `get`, `upsert`), `DayDao`(`all`, `get`, `upsert`), `VacationDao`(`all`, `add`, `remove`), `CelebrationDao`(`seenKeys`, `markSeen`), `SettingsDao`(`get`, `upsert`)
  - `abstract class ChronoDatabase : RoomDatabase` with `companion fun build(context): ChronoDatabase`
  - 테스트 픽스처(패키지 `com.chronoquest.core`): `SEOUL`, `WEEKDAYS_MASK`, `d()`, `dt()`, `clockAt()`, `questEntity()`, `weekdayTemplate()`, `inMemoryDb()`

- [ ] **Step 1: 테스트 픽스처와 Robolectric 설정**

`android-core/core/src/test/resources/robolectric.properties`:

```properties
sdk=35
```

`android-core/core/src/test/kotlin/com/chronoquest/core/CoreFixtures.kt`:

```kotlin
package com.chronoquest.core

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestSource
import com.chronoquest.core.data.ScheduleTemplateEntity
import com.chronoquest.core.domain.QuestIds
import com.chronoquest.rules.model.QuestKind
import com.chronoquest.rules.model.QuestStatus
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

/** 월~금 요일 마스크 (월 bit0 ~ 일 bit6). */
const val WEEKDAYS_MASK = 0b0011111

fun d(date: String): LocalDate = LocalDate.parse(date)

fun dt(date: String, time: String): LocalDateTime = LocalDateTime.parse("${date}T$time")

fun clockAt(date: String, time: String): Clock =
    Clock.fixed(dt(date, time).atZone(SEOUL).toInstant(), SEOUL)

fun questEntity(
    date: String,
    kind: QuestKind = QuestKind.WORK,
    due: String = "09:00",
    depart: String = "08:30",
    title: String = "출근",
    source: QuestSource = QuestSource.MANUAL,
    templateId: Long? = null,
    status: QuestStatus = QuestStatus.SCHEDULED,
): QuestEntity {
    val dueTime = LocalTime.parse(due)
    return QuestEntity(
        id = QuestIds.of(d(date), kind, dueTime),
        date = d(date),
        kind = kind,
        title = title,
        dueAt = d(date).atTime(dueTime),
        targetDepartAt = d(date).atTime(LocalTime.parse(depart)),
        source = source,
        status = status,
        templateId = templateId,
    )
}

fun weekdayTemplate(
    id: Long = 0,
    title: String = "출근",
    kind: QuestKind = QuestKind.WORK,
    due: String = "09:00",
    depart: String = "08:30",
    daysMask: Int = WEEKDAYS_MASK,
    active: Boolean = true,
): ScheduleTemplateEntity = ScheduleTemplateEntity(
    id = id,
    title = title,
    kind = kind,
    daysMask = daysMask,
    dueTime = LocalTime.parse(due),
    targetDepartTime = LocalTime.parse(depart),
    active = active,
)

/** Robolectric 테스트 전용 메모리 DB. */
fun inMemoryDb(): ChronoDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), ChronoDatabase::class.java)
        .allowMainThreadQueries()
        .build()
```

- [ ] **Step 2: 실패 테스트 작성**

`android-core/core/src/test/kotlin/com/chronoquest/core/data/DatabaseTest.kt`:

```kotlin
package com.chronoquest.core.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.core.d
import com.chronoquest.core.dt
import com.chronoquest.core.inMemoryDb
import com.chronoquest.core.questEntity
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseTest {
    private lateinit var db: ChronoDatabase

    @Before
    fun setUp() {
        db = inMemoryDb()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `퀘스트와 기록을 저장하고 그대로 읽는다`() = runTest {
        val q = questEntity("2026-11-30")
        db.questDao().insertAll(listOf(q))
        val r = QuestRecordEntity(
            questId = q.id,
            departedAt = dt("2026-11-30", "08:20"),
            departSource = RecordSource.BUTTON,
            arrivedAt = dt("2026-11-30", "08:55:30"),
            arriveSource = RecordSource.BUTTON,
            safe = true,
        )
        db.recordDao().upsert(r)
        assertEquals(q, db.questDao().get(q.id))
        assertEquals(r, db.recordDao().get(q.id))
    }

    @Test
    fun `같은 ID의 퀘스트는 두 번 넣어도 처음 것만 남는다`() = runTest {
        val q = questEntity("2026-11-30")
        db.questDao().insertAll(listOf(q))
        db.questDao().insertAll(listOf(q.copy(title = "다른 제목")))
        assertEquals(listOf(q), db.questDao().all())
    }

    @Test
    fun `시각은 초 단위까지만 저장한다`() = runTest {
        val q = questEntity("2026-11-30")
        db.questDao().insertAll(listOf(q))
        db.recordDao().upsert(
            QuestRecordEntity(questId = q.id, arrivedAt = LocalDateTime.parse("2026-11-30T08:55:30.987"), safe = true),
        )
        assertEquals(dt("2026-11-30", "08:55:30"), db.recordDao().get(q.id)?.arrivedAt)
    }

    @Test
    fun `하루 기록은 날짜를 키로 덮어쓴다`() = runTest {
        db.dayDao().upsert(DayRecordEntity(date = d("2026-11-30"), selfControl = true))
        db.dayDao().upsert(DayRecordEntity(date = d("2026-11-30"), selfControl = false))
        assertEquals(listOf(DayRecordEntity(date = d("2026-11-30"), selfControl = false)), db.dayDao().all())
    }

    @Test
    fun `날짜로 퀘스트를 예정 시각 순으로 읽는다`() = runTest {
        val evening = questEntity("2026-11-30", due = "19:00", depart = "18:30")
        val morning = questEntity("2026-11-30")
        val nextDay = questEntity("2026-12-01")
        db.questDao().insertAll(listOf(evening, nextDay, morning))
        assertEquals(listOf(morning, evening), db.questDao().onDate(d("2026-11-30")))
    }

    @Test
    fun `기록 없는 미래 템플릿 일정만 골라낸다`() = runTest {
        val past = questEntity("2026-11-30", source = QuestSource.TEMPLATE, templateId = 1)
        val recorded = questEntity("2026-12-01", source = QuestSource.TEMPLATE, templateId = 1)
        val future = questEntity("2026-12-02", source = QuestSource.TEMPLATE, templateId = 1)
        val otherTemplate = questEntity("2026-12-02", due = "10:00", source = QuestSource.TEMPLATE, templateId = 2)
        db.questDao().insertAll(listOf(past, recorded, future, otherTemplate))
        db.recordDao().upsert(QuestRecordEntity(questId = recorded.id, departedAt = dt("2026-12-01", "08:20")))
        assertEquals(listOf(future.id), db.questDao().unrecordedFutureIds(1, dt("2026-11-30", "12:00")))
    }

    @Test
    fun `본 연출 키는 중복 없이 쌓인다`() = runTest {
        val at = dt("2026-11-30", "09:00")
        db.celebrationDao().markSeen(listOf(CelebrationSeenEntity("title:1:1", at)))
        db.celebrationDao().markSeen(
            listOf(CelebrationSeenEntity("title:1:1", at), CelebrationSeenEntity("tier:SILVER", at)),
        )
        assertEquals(setOf("title:1:1", "tier:SILVER"), db.celebrationDao().seenKeys().toSet())
    }

    @Test
    fun `휴가와 설정을 저장한다`() = runTest {
        db.vacationDao().add(VacationEntity(d("2026-12-02")))
        db.vacationDao().add(VacationEntity(d("2026-12-02")))
        assertEquals(listOf(VacationEntity(d("2026-12-02"))), db.vacationDao().all())
        db.vacationDao().remove(d("2026-12-02"))
        assertEquals(emptyList(), db.vacationDao().all())

        db.settingsDao().upsert(AppSettingsEntity(seasonStart = d("2026-11-25"), representativeTitleId = 21))
        assertEquals(21, db.settingsDao().get()?.representativeTitleId)
    }
}
```

- [ ] **Step 3: 실패 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :core:testDebugUnitTest --tests "com.chronoquest.core.data.DatabaseTest"`
Expected: FAIL, `Unresolved reference 'ChronoDatabase'` 등 컴파일 오류. 첫 실행은 Robolectric android-all jar를 받느라 오래 걸릴 수 있다.

- [ ] **Step 4: 구현**

`android-core/core/src/main/kotlin/com/chronoquest/core/data/Converters.kt`:

```kotlin
package com.chronoquest.core.data

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 시각은 로컬 벽시계 시각으로 저장한다(타임존 변환 없음, 인계 메모 A2).
 * 고정 길이 형식이라 문자열 비교 순서와 시간 순서가 같다.
 */
class Converters {
    @TypeConverter
    fun fromDate(value: LocalDate?): String? = value?.format(DATE)

    @TypeConverter
    fun toDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it, DATE) }

    @TypeConverter
    fun fromDateTime(value: LocalDateTime?): String? = value?.truncatedTo(ChronoUnit.SECONDS)?.format(DATE_TIME)

    @TypeConverter
    fun toDateTime(value: String?): LocalDateTime? = value?.let { LocalDateTime.parse(it, DATE_TIME) }

    @TypeConverter
    fun fromTime(value: LocalTime?): String? = value?.truncatedTo(ChronoUnit.MINUTES)?.format(TIME)

    @TypeConverter
    fun toTime(value: String?): LocalTime? = value?.let { LocalTime.parse(it, TIME) }

    companion object {
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
```

`android-core/core/src/main/kotlin/com/chronoquest/core/data/Entities.kt`:

```kotlin
package com.chronoquest.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.chronoquest.rules.model.QuestKind
import com.chronoquest.rules.model.QuestStatus
import com.chronoquest.rules.model.Resolution
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

enum class QuestSource { TEMPLATE, CALENDAR, MANUAL }

enum class RecordSource { GEOFENCE, BUTTON, EDIT }

/** 반복 일정 템플릿 (스펙 4장). daysMask는 월(bit0)~일(bit6). */
@Entity(tableName = "schedule_template")
data class ScheduleTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val kind: QuestKind,
    val daysMask: Int,
    val dueTime: LocalTime,
    val targetDepartTime: LocalTime,
    val fromPlaceId: Long? = null,
    val toPlaceId: Long? = null,
    val active: Boolean = true,
)

/** 일정 인스턴스. id는 QuestIds.of(date, kind, dueTime), date는 항상 dueAt의 날짜다. */
@Entity(tableName = "quest", indices = [Index("date"), Index("templateId")])
data class QuestEntity(
    @PrimaryKey val id: String,
    val date: LocalDate,
    val kind: QuestKind,
    val title: String,
    val dueAt: LocalDateTime,
    val targetDepartAt: LocalDateTime,
    val source: QuestSource,
    val status: QuestStatus = QuestStatus.SCHEDULED,
    val templateId: Long? = null,
    val calendarEventId: Long? = null,
    val fromPlaceId: Long? = null,
    val toPlaceId: Long? = null,
)

@Entity(tableName = "quest_record")
data class QuestRecordEntity(
    @PrimaryKey val questId: String,
    val departedAt: LocalDateTime? = null,
    val departSource: RecordSource? = null,
    val arrivedAt: LocalDateTime? = null,
    val arriveSource: RecordSource? = null,
    val safe: Boolean? = null,
    val edited: Boolean = false,
    val resolution: Resolution = Resolution.NONE,
)

@Entity(tableName = "day_record")
data class DayRecordEntity(
    @PrimaryKey val date: LocalDate,
    val alarmRangAt: LocalDateTime? = null,
    val quizSolvedAt: LocalDateTime? = null,
    val sleptEnough: Boolean? = null,
    val recheckPassed: Boolean? = null,
    val selfControl: Boolean? = null,
)

@Entity(tableName = "vacation")
data class VacationEntity(@PrimaryKey val date: LocalDate)

@Entity(tableName = "celebration_seen")
data class CelebrationSeenEntity(
    @PrimaryKey val grantKey: String,
    val seenAt: LocalDateTime,
)

/** 설정은 행 하나(id = 0)다. */
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 0,
    val seasonStart: LocalDate,
    val representativeTitleId: Int? = null,
)
```

`android-core/core/src/main/kotlin/com/chronoquest/core/data/Daos.kt`:

```kotlin
package com.chronoquest.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import java.time.LocalDate
import java.time.LocalDateTime

@Dao
interface TemplateDao {
    @Query("SELECT * FROM schedule_template ORDER BY id")
    suspend fun all(): List<ScheduleTemplateEntity>

    @Insert
    suspend fun insert(template: ScheduleTemplateEntity): Long

    @Update
    suspend fun update(template: ScheduleTemplateEntity)

    @Query("DELETE FROM schedule_template WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface QuestDao {
    @Query("SELECT * FROM quest")
    suspend fun all(): List<QuestEntity>

    @Query("SELECT * FROM quest WHERE id = :id")
    suspend fun get(id: String): QuestEntity?

    @Query("SELECT * FROM quest WHERE date = :date ORDER BY dueAt, id")
    suspend fun onDate(date: LocalDate): List<QuestEntity>

    @Query("SELECT id FROM quest")
    suspend fun allIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(quests: List<QuestEntity>)

    @Update
    suspend fun update(quest: QuestEntity)

    @Query("DELETE FROM quest WHERE id IN (:ids)")
    suspend fun deleteIds(ids: List<String>)

    /** 템플릿이 만든 일정 중 [after]보다 뒤에 있고 기록도 없는 것. 템플릿을 고치거나 지울 때 다시 만든다. */
    @Query(
        "SELECT q.id FROM quest q LEFT JOIN quest_record r ON r.questId = q.id " +
            "WHERE q.templateId = :templateId AND q.dueAt > :after AND r.questId IS NULL",
    )
    suspend fun unrecordedFutureIds(templateId: Long, after: LocalDateTime): List<String>
}

@Dao
interface RecordDao {
    @Query("SELECT * FROM quest_record")
    suspend fun all(): List<QuestRecordEntity>

    @Query("SELECT * FROM quest_record WHERE questId = :questId")
    suspend fun get(questId: String): QuestRecordEntity?

    @Upsert
    suspend fun upsert(record: QuestRecordEntity)
}

@Dao
interface DayDao {
    @Query("SELECT * FROM day_record")
    suspend fun all(): List<DayRecordEntity>

    @Query("SELECT * FROM day_record WHERE date = :date")
    suspend fun get(date: LocalDate): DayRecordEntity?

    @Upsert
    suspend fun upsert(day: DayRecordEntity)
}

@Dao
interface VacationDao {
    @Query("SELECT * FROM vacation ORDER BY date")
    suspend fun all(): List<VacationEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun add(vacation: VacationEntity)

    @Query("DELETE FROM vacation WHERE date = :date")
    suspend fun remove(date: LocalDate)
}

@Dao
interface CelebrationDao {
    @Query("SELECT grantKey FROM celebration_seen")
    suspend fun seenKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSeen(rows: List<CelebrationSeenEntity>)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 0")
    suspend fun get(): AppSettingsEntity?

    @Upsert
    suspend fun upsert(settings: AppSettingsEntity)
}
```

`android-core/core/src/main/kotlin/com/chronoquest/core/data/ChronoDatabase.kt`:

```kotlin
package com.chronoquest.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ScheduleTemplateEntity::class,
        QuestEntity::class,
        QuestRecordEntity::class,
        DayRecordEntity::class,
        VacationEntity::class,
        CelebrationSeenEntity::class,
        AppSettingsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ChronoDatabase : RoomDatabase() {
    abstract fun templateDao(): TemplateDao
    abstract fun questDao(): QuestDao
    abstract fun recordDao(): RecordDao
    abstract fun dayDao(): DayDao
    abstract fun vacationDao(): VacationDao
    abstract fun celebrationDao(): CelebrationDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val NAME = "chrono.db"

        fun build(context: Context): ChronoDatabase =
            Room.databaseBuilder(context.applicationContext, ChronoDatabase::class.java, NAME).build()
    }
}
```

- [ ] **Step 5: 통과 확인과 스키마 파일 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :core:testDebugUnitTest --offline && ls core/schemas/com.chronoquest.core.data.ChronoDatabase/`
Expected: `BUILD SUCCESSFUL`(DatabaseTest 8개 포함), `1.json`이 보인다.

- [ ] **Step 6: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/core
git commit -m "feat(core): Room 스키마 - 엔터티, DAO, DB, 시각 변환" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Room → 판정 엔진 입력 (LedgerMapper, LedgerRepository)

**Files:**
- Create: `android-core/core/src/main/kotlin/com/chronoquest/core/data/LedgerMapper.kt`
- Create: `android-core/core/src/main/kotlin/com/chronoquest/core/service/LedgerRepository.kt`
- Test: `android-core/core/src/test/kotlin/com/chronoquest/core/data/LedgerMapperTest.kt`
- Test: `android-core/core/src/test/kotlin/com/chronoquest/core/service/LedgerRepositoryTest.kt`

**Interfaces:**
- Consumes: Task 2 엔터티·DAO·DB; A1 `Ledger`, `Quest`, `QuestRecord`, `DayRecord`, `Engine`
- Produces:
  - `object LedgerMapper { fun toLedger(settings: AppSettingsEntity, quests: List<QuestEntity>, records: List<QuestRecordEntity>, days: List<DayRecordEntity>, vacations: List<VacationEntity>): Ledger }`
  - `class LedgerRepository(db: ChronoDatabase) { suspend fun settings(): AppSettingsEntity; suspend fun loadLedger(): Ledger; companion val DEFAULT_SEASON_START: LocalDate }`

- [ ] **Step 1: 실패 테스트 작성**

`android-core/core/src/test/kotlin/com/chronoquest/core/data/LedgerMapperTest.kt`:

```kotlin
package com.chronoquest.core.data

import com.chronoquest.core.d
import com.chronoquest.core.dt
import com.chronoquest.core.questEntity
import com.chronoquest.rules.model.DayRecord
import com.chronoquest.rules.model.Quest
import com.chronoquest.rules.model.QuestRecord
import com.chronoquest.rules.model.QuestStatus
import com.chronoquest.rules.model.Resolution
import kotlin.test.assertEquals
import org.junit.Test

class LedgerMapperTest {
    @Test
    fun `엔터티를 판정 엔진 원장으로 옮긴다`() {
        val q = questEntity("2026-11-30", status = QuestStatus.CANCELED)
        val r = QuestRecordEntity(
            questId = q.id,
            departedAt = dt("2026-11-30", "08:20"),
            departSource = RecordSource.BUTTON,
            arrivedAt = dt("2026-11-30", "08:55"),
            arriveSource = RecordSource.EDIT,
            safe = true,
            edited = true,
            resolution = Resolution.NONE,
        )
        val day = DayRecordEntity(
            date = d("2026-11-30"),
            alarmRangAt = dt("2026-11-30", "07:00"),
            quizSolvedAt = dt("2026-11-30", "07:03"),
            sleptEnough = true,
            recheckPassed = true,
            selfControl = false,
        )
        val ledger = LedgerMapper.toLedger(
            settings = AppSettingsEntity(seasonStart = d("2026-11-25")),
            quests = listOf(q),
            records = listOf(r),
            days = listOf(day),
            vacations = listOf(VacationEntity(d("2026-12-02"))),
        )
        assertEquals(d("2026-11-25"), ledger.seasonStart)
        assertEquals(
            listOf(Quest(q.id, q.date, q.kind, q.title, q.dueAt, q.targetDepartAt, QuestStatus.CANCELED)),
            ledger.quests,
        )
        assertEquals(
            listOf(QuestRecord(q.id, dt("2026-11-30", "08:20"), dt("2026-11-30", "08:55"), true, Resolution.NONE)),
            ledger.records,
        )
        assertEquals(
            listOf(DayRecord(d("2026-11-30"), dt("2026-11-30", "07:00"), dt("2026-11-30", "07:03"), true, true, false)),
            ledger.days,
        )
        assertEquals(setOf(d("2026-12-02")), ledger.vacations)
    }
}
```

`android-core/core/src/test/kotlin/com/chronoquest/core/service/LedgerRepositoryTest.kt`:

```kotlin
package com.chronoquest.core.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.core.d
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.core.dt
import com.chronoquest.core.inMemoryDb
import com.chronoquest.core.questEntity
import com.chronoquest.rules.engine.Engine
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerRepositoryTest {
    private lateinit var db: ChronoDatabase

    @Before
    fun setUp() {
        db = inMemoryDb()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `설정이 없으면 시즌 시작일 기본값으로 만들고 저장한다`() = runTest {
        val repo = LedgerRepository(db)
        assertEquals(d("2026-11-25"), repo.settings().seasonStart)
        assertEquals(d("2026-11-25"), db.settingsDao().get()?.seasonStart)
    }

    @Test
    fun `저장된 기록으로 판정 엔진이 정산한다`() = runTest {
        val q = questEntity("2026-11-30")
        db.questDao().insertAll(listOf(q))
        db.recordDao().upsert(QuestRecordEntity(questId = q.id, arrivedAt = dt("2026-11-30", "08:55"), safe = true))
        val state = Engine().compute(LedgerRepository(db).loadLedger(), dt("2026-11-30", "12:00"))
        assertEquals(20, state.xp)
        assertEquals(1, state.inventory[1])
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :core:testDebugUnitTest --offline`
Expected: FAIL, `Unresolved reference 'LedgerMapper'` / `'LedgerRepository'`.

- [ ] **Step 3: 구현**

`android-core/core/src/main/kotlin/com/chronoquest/core/data/LedgerMapper.kt`:

```kotlin
package com.chronoquest.core.data

import com.chronoquest.rules.model.DayRecord
import com.chronoquest.rules.model.Ledger
import com.chronoquest.rules.model.Quest
import com.chronoquest.rules.model.QuestRecord

/** Room 엔터티를 판정 엔진 입력(Ledger)으로 옮긴다. 출처·수정 표시처럼 판정에 안 쓰는 필드는 버린다. */
object LedgerMapper {
    fun toLedger(
        settings: AppSettingsEntity,
        quests: List<QuestEntity>,
        records: List<QuestRecordEntity>,
        days: List<DayRecordEntity>,
        vacations: List<VacationEntity>,
    ): Ledger = Ledger(
        seasonStart = settings.seasonStart,
        quests = quests.map { it.toRules() },
        records = records.map { it.toRules() },
        days = days.map { it.toRules() },
        vacations = vacations.mapTo(HashSet()) { it.date },
    )

    private fun QuestEntity.toRules() = Quest(
        id = id,
        date = date,
        kind = kind,
        title = title,
        dueAt = dueAt,
        targetDepartAt = targetDepartAt,
        status = status,
    )

    private fun QuestRecordEntity.toRules() = QuestRecord(
        questId = questId,
        departedAt = departedAt,
        arrivedAt = arrivedAt,
        safe = safe,
        resolution = resolution,
    )

    private fun DayRecordEntity.toRules() = DayRecord(
        date = date,
        alarmRangAt = alarmRangAt,
        quizSolvedAt = quizSolvedAt,
        sleptEnough = sleptEnough,
        recheckPassed = recheckPassed,
        selfControl = selfControl,
    )
}
```

`android-core/core/src/main/kotlin/com/chronoquest/core/service/LedgerRepository.kt`:

```kotlin
package com.chronoquest.core.service

import androidx.room.withTransaction
import com.chronoquest.core.data.AppSettingsEntity
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.LedgerMapper
import com.chronoquest.rules.model.Ledger
import java.time.LocalDate

class LedgerRepository(private val db: ChronoDatabase) {
    /** 설정 행이 없으면 시즌 시작일 기본값으로 만들어 저장한다. */
    suspend fun settings(): AppSettingsEntity =
        db.settingsDao().get()
            ?: AppSettingsEntity(seasonStart = DEFAULT_SEASON_START).also { db.settingsDao().upsert(it) }

    /** 원장 전체를 한 트랜잭션에서 읽어 판정 엔진 입력으로 바꾼다. */
    suspend fun loadLedger(): Ledger = db.withTransaction {
        LedgerMapper.toLedger(
            settings = settings(),
            quests = db.questDao().all(),
            records = db.recordDao().all(),
            days = db.dayDao().all(),
            vacations = db.vacationDao().all(),
        )
    }

    companion object {
        val DEFAULT_SEASON_START: LocalDate = LocalDate.of(2026, 11, 25)
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :core:testDebugUnitTest --offline`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/core
git commit -m "feat(core): Room 원장을 판정 엔진 입력으로 변환" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: 템플릿 검증과 일정 생성 (TemplateRules, QuestGenerator)

**Files:**
- Create: `android-core/core/src/main/kotlin/com/chronoquest/core/domain/TemplateRules.kt`
- Create: `android-core/core/src/main/kotlin/com/chronoquest/core/domain/QuestGenerator.kt`
- Test: `android-core/core/src/test/kotlin/com/chronoquest/core/domain/TemplateRulesTest.kt`
- Test: `android-core/core/src/test/kotlin/com/chronoquest/core/domain/QuestGeneratorTest.kt`

**Interfaces:**
- Consumes: Task 1 `QuestIds`; Task 2 `ScheduleTemplateEntity`, `QuestEntity`, `QuestSource`
- Produces:
  - `enum class ScheduleProblem { BLANK_TITLE, NO_DAYS, DEPART_AFTER_DUE, DUPLICATE, BEFORE_SEASON }`
  - `object TemplateRules { const val ALL_DAYS_MASK; fun bit(day: DayOfWeek): Int; fun maskOf(days: Set<DayOfWeek>): Int; fun daysOf(mask: Int): Set<DayOfWeek>; fun problems(title: String, dueTime: LocalTime, departTime: LocalTime): List<ScheduleProblem>; fun validate(template: ScheduleTemplateEntity): List<ScheduleProblem> }`
  - `object QuestGenerator { const val HORIZON_DAYS = 7; fun generate(templates: List<ScheduleTemplateEntity>, from: LocalDate, days: Int = HORIZON_DAYS): List<QuestEntity> }`

- [ ] **Step 1: 실패 테스트 작성**

`android-core/core/src/test/kotlin/com/chronoquest/core/domain/TemplateRulesTest.kt`:

```kotlin
package com.chronoquest.core.domain

import com.chronoquest.core.WEEKDAYS_MASK
import com.chronoquest.core.weekdayTemplate
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.assertEquals
import org.junit.Test

class TemplateRulesTest {
    @Test
    fun `요일 마스크는 월요일이 첫 비트다`() {
        val weekdays = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        assertEquals(WEEKDAYS_MASK, TemplateRules.maskOf(weekdays))
        assertEquals(weekdays, TemplateRules.daysOf(WEEKDAYS_MASK))
        assertEquals(1, TemplateRules.bit(DayOfWeek.MONDAY))
        assertEquals(64, TemplateRules.bit(DayOfWeek.SUNDAY))
    }

    @Test
    fun `제목이 비었거나 출발이 예정보다 늦으면 문제`() {
        assertEquals(emptyList(), TemplateRules.problems("출근", LocalTime.parse("09:00"), LocalTime.parse("09:00")))
        assertEquals(
            listOf(ScheduleProblem.BLANK_TITLE, ScheduleProblem.DEPART_AFTER_DUE),
            TemplateRules.problems("  ", LocalTime.parse("09:00"), LocalTime.parse("09:01")),
        )
    }

    @Test
    fun `요일이 하나도 없는 템플릿은 문제`() {
        assertEquals(emptyList(), TemplateRules.validate(weekdayTemplate()))
        assertEquals(listOf(ScheduleProblem.NO_DAYS), TemplateRules.validate(weekdayTemplate(daysMask = 0)))
        assertEquals(
            listOf(ScheduleProblem.DEPART_AFTER_DUE, ScheduleProblem.NO_DAYS),
            TemplateRules.validate(weekdayTemplate(depart = "09:30", daysMask = 0)),
        )
    }
}
```

`android-core/core/src/test/kotlin/com/chronoquest/core/domain/QuestGeneratorTest.kt`:

```kotlin
package com.chronoquest.core.domain

import com.chronoquest.core.d
import com.chronoquest.core.dt
import com.chronoquest.core.data.QuestSource
import com.chronoquest.core.weekdayTemplate
import com.chronoquest.rules.model.QuestKind
import com.chronoquest.rules.model.QuestStatus
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class QuestGeneratorTest {
    private val from = d("2026-11-25") // 수요일

    @Test
    fun `평일 템플릿은 7일 중 평일에만 일정을 만든다`() {
        val quests = QuestGenerator.generate(listOf(weekdayTemplate(id = 1)), from)
        assertEquals(
            listOf(
                "2026-11-25-WORK-09:00",
                "2026-11-26-WORK-09:00",
                "2026-11-27-WORK-09:00",
                "2026-11-30-WORK-09:00",
                "2026-12-01-WORK-09:00",
            ),
            quests.map { it.id },
        )
    }

    @Test
    fun `만든 일정은 템플릿 시각과 출처를 그대로 가진다`() {
        val q = QuestGenerator.generate(listOf(weekdayTemplate(id = 7, title = "회사")), from, days = 1).single()
        assertEquals(d("2026-11-25"), q.date)
        assertEquals(QuestKind.WORK, q.kind)
        assertEquals("회사", q.title)
        assertEquals(dt("2026-11-25", "09:00"), q.dueAt)
        assertEquals(dt("2026-11-25", "08:30"), q.targetDepartAt)
        assertEquals(QuestSource.TEMPLATE, q.source)
        assertEquals(7L, q.templateId)
        assertEquals(QuestStatus.SCHEDULED, q.status)
    }

    @Test
    fun `비활성이거나 잘못된 템플릿은 건너뛴다`() {
        val templates = listOf(
            weekdayTemplate(id = 1, active = false),
            weekdayTemplate(id = 2, depart = "09:30"),
        )
        assertTrue(QuestGenerator.generate(templates, from).isEmpty())
    }

    @Test
    fun `같은 날 같은 종류 같은 시각이면 하나만 남는다`() {
        val templates = listOf(weekdayTemplate(id = 1), weekdayTemplate(id = 2, title = "다른 이름"))
        val quests = QuestGenerator.generate(templates, from)
        assertEquals(5, quests.size)
        assertTrue(quests.all { it.templateId == 1L })
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :core:testDebugUnitTest --offline`
Expected: FAIL, `Unresolved reference 'TemplateRules'` / `'QuestGenerator'` / `'ScheduleProblem'`.

- [ ] **Step 3: 구현**

`android-core/core/src/main/kotlin/com/chronoquest/core/domain/TemplateRules.kt`:

```kotlin
package com.chronoquest.core.domain

import com.chronoquest.core.data.ScheduleTemplateEntity
import java.time.DayOfWeek
import java.time.LocalTime

enum class ScheduleProblem { BLANK_TITLE, NO_DAYS, DEPART_AFTER_DUE, DUPLICATE, BEFORE_SEASON }

/** 반복 템플릿과 단발 일정의 입력 검증, 요일 마스크(월 bit0 ~ 일 bit6). */
object TemplateRules {
    const val ALL_DAYS_MASK = 0b1111111

    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun maskOf(days: Set<DayOfWeek>): Int = days.fold(0) { mask, day -> mask or bit(day) }

    fun daysOf(mask: Int): Set<DayOfWeek> =
        DayOfWeek.values().filterTo(LinkedHashSet()) { (mask and bit(it)) != 0 }

    fun problems(title: String, dueTime: LocalTime, departTime: LocalTime): List<ScheduleProblem> = buildList {
        if (title.isBlank()) add(ScheduleProblem.BLANK_TITLE)
        if (departTime.isAfter(dueTime)) add(ScheduleProblem.DEPART_AFTER_DUE)
    }

    fun validate(template: ScheduleTemplateEntity): List<ScheduleProblem> = buildList {
        addAll(problems(template.title, template.dueTime, template.targetDepartTime))
        if ((template.daysMask and ALL_DAYS_MASK) == 0) add(ScheduleProblem.NO_DAYS)
    }
}
```

`android-core/core/src/main/kotlin/com/chronoquest/core/domain/QuestGenerator.kt`:

```kotlin
package com.chronoquest.core.domain

import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestSource
import com.chronoquest.core.data.ScheduleTemplateEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 반복 템플릿 → 일정 인스턴스 (스펙 6.4). */
object QuestGenerator {
    const val HORIZON_DAYS = 7

    /** [from]부터 [days]일 동안 만들 일정. 비활성·잘못된 템플릿은 건너뛰고, 같은 ID는 처음 것만 남긴다. */
    fun generate(
        templates: List<ScheduleTemplateEntity>,
        from: LocalDate,
        days: Int = HORIZON_DAYS,
    ): List<QuestEntity> {
        val usable = templates.filter { it.active && TemplateRules.validate(it).isEmpty() }
        return (0 until days)
            .flatMap { offset ->
                val date = from.plusDays(offset.toLong())
                usable
                    .filter { (it.daysMask and TemplateRules.bit(date.dayOfWeek)) != 0 }
                    .map { questFor(it, date) }
            }
            .distinctBy { it.id }
    }

    private fun questFor(template: ScheduleTemplateEntity, date: LocalDate) = QuestEntity(
        id = QuestIds.of(date, template.kind, template.dueTime),
        date = date,
        kind = template.kind,
        title = template.title,
        dueAt = date.atTime(template.dueTime.truncatedTo(ChronoUnit.MINUTES)),
        targetDepartAt = date.atTime(template.targetDepartTime.truncatedTo(ChronoUnit.MINUTES)),
        source = QuestSource.TEMPLATE,
        templateId = template.id,
        fromPlaceId = template.fromPlaceId,
        toPlaceId = template.toPlaceId,
    )
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :core:testDebugUnitTest --offline`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/core
git commit -m "feat(core): 템플릿 검증과 7일치 일정 생성" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: 출발·도착 기록 규칙 (RecordPolicy)

**Files:**
- Create: `android-core/core/src/main/kotlin/com/chronoquest/core/domain/RecordPolicy.kt`
- Test: `android-core/core/src/test/kotlin/com/chronoquest/core/domain/RecordPolicyTest.kt`

**Interfaces:**
- Consumes: Task 2 `QuestEntity`, `QuestRecordEntity`, `RecordSource`; A1 `QuestStatus`, `Resolution`
- Produces:
  - `enum class RejectReason { QUEST_CANCELED, FUTURE_TIME, ALREADY_RECORDED, ARRIVAL_BEFORE_DEPARTURE, SAFE_WITHOUT_ARRIVAL, SAFE_REQUIRED }`
  - `sealed interface RecordResult { data class Ok(val record: QuestRecordEntity); data class Rejected(val reason: RejectReason) }`
  - `object RecordPolicy { fun depart(quest, current: QuestRecordEntity?, at: LocalDateTime, now: LocalDateTime, source: RecordSource): RecordResult; fun arrive(quest, current, at, safe: Boolean, now, source): RecordResult; fun edit(quest, current, departedAt: LocalDateTime?, arrivedAt: LocalDateTime?, safe: Boolean?, now): RecordResult; fun resolveNoRecord(quest, current): RecordResult }`

- [ ] **Step 1: 실패 테스트 작성**

`android-core/core/src/test/kotlin/com/chronoquest/core/domain/RecordPolicyTest.kt`:

```kotlin
package com.chronoquest.core.domain

import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.core.data.RecordSource
import com.chronoquest.core.dt
import com.chronoquest.core.questEntity
import com.chronoquest.rules.model.QuestStatus
import com.chronoquest.rules.model.Resolution
import java.time.LocalDateTime
import kotlin.test.assertEquals
import org.junit.Test

class RecordPolicyTest {
    private val quest = questEntity("2026-11-30")
    private val canceled = quest.copy(status = QuestStatus.CANCELED)
    private val now = dt("2026-11-30", "12:00")
    private fun at(time: String): LocalDateTime = dt("2026-11-30", time)

    private fun ok(result: RecordResult): QuestRecordEntity = (result as RecordResult.Ok).record

    @Test
    fun `출발을 초 단위로 기록한다`() {
        val record = ok(RecordPolicy.depart(quest, null, LocalDateTime.parse("2026-11-30T08:20:15.5"), now, RecordSource.BUTTON))
        assertEquals(QuestRecordEntity(questId = quest.id, departedAt = at("08:20:15"), departSource = RecordSource.BUTTON), record)
    }

    @Test
    fun `출발은 한 번만이고 미래 시각과 취소된 일정은 거부한다`() {
        val departed = QuestRecordEntity(questId = quest.id, departedAt = at("08:20"))
        assertEquals(
            RecordResult.Rejected(RejectReason.ALREADY_RECORDED),
            RecordPolicy.depart(quest, departed, at("08:25"), now, RecordSource.BUTTON),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.FUTURE_TIME),
            RecordPolicy.depart(quest, null, at("12:01"), now, RecordSource.BUTTON),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.QUEST_CANCELED),
            RecordPolicy.depart(canceled, null, at("08:20"), now, RecordSource.BUTTON),
        )
    }

    @Test
    fun `도착보다 늦은 출발은 거부한다`() {
        val arrived = QuestRecordEntity(questId = quest.id, arrivedAt = at("08:55"), safe = true)
        assertEquals(
            RecordResult.Rejected(RejectReason.ARRIVAL_BEFORE_DEPARTURE),
            RecordPolicy.depart(quest, arrived, at("09:00"), now, RecordSource.BUTTON),
        )
    }

    @Test
    fun `도착은 안전 여부와 함께 기록하고 출발 기록을 유지한다`() {
        val departed = QuestRecordEntity(questId = quest.id, departedAt = at("08:20"), departSource = RecordSource.GEOFENCE)
        val record = ok(RecordPolicy.arrive(quest, departed, at("08:55"), safe = false, now = now, source = RecordSource.BUTTON))
        assertEquals(
            departed.copy(arrivedAt = at("08:55"), arriveSource = RecordSource.BUTTON, safe = false),
            record,
        )
    }

    @Test
    fun `도착은 한 번만이고 기록 없음으로 정리된 뒤나 출발보다 이르면 거부한다`() {
        val arrived = QuestRecordEntity(questId = quest.id, arrivedAt = at("08:55"), safe = true)
        val noRecord = QuestRecordEntity(questId = quest.id, resolution = Resolution.NO_RECORD)
        val departed = QuestRecordEntity(questId = quest.id, departedAt = at("08:20"))
        assertEquals(
            RecordResult.Rejected(RejectReason.ALREADY_RECORDED),
            RecordPolicy.arrive(quest, arrived, at("09:00"), true, now, RecordSource.BUTTON),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.ALREADY_RECORDED),
            RecordPolicy.arrive(quest, noRecord, at("09:00"), true, now, RecordSource.BUTTON),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.ARRIVAL_BEFORE_DEPARTURE),
            RecordPolicy.arrive(quest, departed, at("08:10"), true, now, RecordSource.BUTTON),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.FUTURE_TIME),
            RecordPolicy.arrive(quest, null, at("12:30"), true, now, RecordSource.BUTTON),
        )
    }

    @Test
    fun `수정은 바뀐 시각만 EDIT로 표시하고 edited를 남긴다`() {
        val current = QuestRecordEntity(
            questId = quest.id,
            departedAt = at("08:20"),
            departSource = RecordSource.GEOFENCE,
            arrivedAt = at("09:10"),
            arriveSource = RecordSource.BUTTON,
            safe = true,
        )
        val record = ok(RecordPolicy.edit(quest, current, at("08:20"), at("08:58"), true, now))
        assertEquals(
            current.copy(arrivedAt = at("08:58"), arriveSource = RecordSource.EDIT, edited = true),
            record,
        )
    }

    @Test
    fun `수정 검증`() {
        assertEquals(
            RecordResult.Rejected(RejectReason.FUTURE_TIME),
            RecordPolicy.edit(quest, null, null, at("12:30"), true, now),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.ARRIVAL_BEFORE_DEPARTURE),
            RecordPolicy.edit(quest, null, at("09:00"), at("08:50"), true, now),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.SAFE_WITHOUT_ARRIVAL),
            RecordPolicy.edit(quest, null, at("08:20"), null, false, now),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.SAFE_REQUIRED),
            RecordPolicy.edit(quest, null, null, at("08:50"), null, now),
        )
        assertEquals(
            RecordResult.Rejected(RejectReason.QUEST_CANCELED),
            RecordPolicy.edit(canceled, null, null, at("08:50"), true, now),
        )
    }

    @Test
    fun `수정하면 기록 없음 정리를 되돌린다`() {
        val noRecord = QuestRecordEntity(questId = quest.id, resolution = Resolution.NO_RECORD)
        val record = ok(RecordPolicy.edit(quest, noRecord, null, at("08:58"), true, now))
        assertEquals(Resolution.NONE, record.resolution)
        assertEquals(at("08:58"), record.arrivedAt)
    }

    @Test
    fun `기록 없음 정리는 도착 기록이 없을 때만 된다`() {
        val record = ok(RecordPolicy.resolveNoRecord(quest, null))
        assertEquals(QuestRecordEntity(questId = quest.id, resolution = Resolution.NO_RECORD), record)
        assertEquals(
            RecordResult.Rejected(RejectReason.ALREADY_RECORDED),
            RecordPolicy.resolveNoRecord(quest, QuestRecordEntity(questId = quest.id, arrivedAt = at("08:55"), safe = true)),
        )
        assertEquals(RecordResult.Rejected(RejectReason.QUEST_CANCELED), RecordPolicy.resolveNoRecord(canceled, null))
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :core:testDebugUnitTest --offline`
Expected: FAIL, `Unresolved reference 'RecordPolicy'` / `'RecordResult'` / `'RejectReason'`.

- [ ] **Step 3: 구현**

`android-core/core/src/main/kotlin/com/chronoquest/core/domain/RecordPolicy.kt`:

```kotlin
package com.chronoquest.core.domain

import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.core.data.RecordSource
import com.chronoquest.rules.model.QuestStatus
import com.chronoquest.rules.model.Resolution
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class RejectReason {
    QUEST_CANCELED,
    FUTURE_TIME,
    ALREADY_RECORDED,
    ARRIVAL_BEFORE_DEPARTURE,
    SAFE_WITHOUT_ARRIVAL,
    SAFE_REQUIRED,
}

sealed interface RecordResult {
    data class Ok(val record: QuestRecordEntity) : RecordResult
    data class Rejected(val reason: RejectReason) : RecordResult
}

/**
 * 출발·도착 기록 규칙 (스펙 6.2, 인계 메모 A2 원장 계약).
 * - 미래 시각은 받지 않는다.
 * - 출발·도착은 각각 한 번만 기록하고 먼저 확정된 것이 이긴다. 바꾸려면 [edit]를 쓴다.
 * - 안전 여부는 도착과 함께만 저장한다.
 */
object RecordPolicy {
    fun depart(
        quest: QuestEntity,
        current: QuestRecordEntity?,
        at: LocalDateTime,
        now: LocalDateTime,
        source: RecordSource,
    ): RecordResult {
        val time = at.truncatedTo(ChronoUnit.SECONDS)
        basicRejection(quest, time, now)?.let { return it }
        if (current?.departedAt != null) return RecordResult.Rejected(RejectReason.ALREADY_RECORDED)
        val arrivedAt = current?.arrivedAt
        if (arrivedAt != null && time.isAfter(arrivedAt)) {
            return RecordResult.Rejected(RejectReason.ARRIVAL_BEFORE_DEPARTURE)
        }
        val base = current ?: QuestRecordEntity(questId = quest.id)
        return RecordResult.Ok(base.copy(departedAt = time, departSource = source))
    }

    fun arrive(
        quest: QuestEntity,
        current: QuestRecordEntity?,
        at: LocalDateTime,
        safe: Boolean,
        now: LocalDateTime,
        source: RecordSource,
    ): RecordResult {
        val time = at.truncatedTo(ChronoUnit.SECONDS)
        basicRejection(quest, time, now)?.let { return it }
        if (current?.arrivedAt != null || current?.resolution == Resolution.NO_RECORD) {
            return RecordResult.Rejected(RejectReason.ALREADY_RECORDED)
        }
        val departedAt = current?.departedAt
        if (departedAt != null && time.isBefore(departedAt)) {
            return RecordResult.Rejected(RejectReason.ARRIVAL_BEFORE_DEPARTURE)
        }
        val base = current ?: QuestRecordEntity(questId = quest.id)
        return RecordResult.Ok(base.copy(arrivedAt = time, arriveSource = source, safe = safe))
    }

    /** 기록 화면의 수정. 바뀐 시각의 출처만 EDIT로 바꾸고, 기록 없음 정리는 되돌린다. */
    fun edit(
        quest: QuestEntity,
        current: QuestRecordEntity?,
        departedAt: LocalDateTime?,
        arrivedAt: LocalDateTime?,
        safe: Boolean?,
        now: LocalDateTime,
    ): RecordResult {
        if (quest.status == QuestStatus.CANCELED) return RecordResult.Rejected(RejectReason.QUEST_CANCELED)
        val dep = departedAt?.truncatedTo(ChronoUnit.SECONDS)
        val arr = arrivedAt?.truncatedTo(ChronoUnit.SECONDS)
        if ((dep != null && dep.isAfter(now)) || (arr != null && arr.isAfter(now))) {
            return RecordResult.Rejected(RejectReason.FUTURE_TIME)
        }
        if (dep != null && arr != null && arr.isBefore(dep)) {
            return RecordResult.Rejected(RejectReason.ARRIVAL_BEFORE_DEPARTURE)
        }
        if (arr == null && safe != null) return RecordResult.Rejected(RejectReason.SAFE_WITHOUT_ARRIVAL)
        if (arr != null && safe == null) return RecordResult.Rejected(RejectReason.SAFE_REQUIRED)
        val base = current ?: QuestRecordEntity(questId = quest.id)
        return RecordResult.Ok(
            base.copy(
                departedAt = dep,
                departSource = sourceAfterEdit(dep, base.departedAt, base.departSource),
                arrivedAt = arr,
                arriveSource = sourceAfterEdit(arr, base.arrivedAt, base.arriveSource),
                safe = safe,
                edited = true,
                resolution = Resolution.NONE,
            ),
        )
    }

    /** "정리 필요" 카드의 [기록 없음 → 지각 처리]. 도착 기록이 있으면 할 수 없다. */
    fun resolveNoRecord(quest: QuestEntity, current: QuestRecordEntity?): RecordResult {
        if (quest.status == QuestStatus.CANCELED) return RecordResult.Rejected(RejectReason.QUEST_CANCELED)
        if (current?.arrivedAt != null) return RecordResult.Rejected(RejectReason.ALREADY_RECORDED)
        val base = current ?: QuestRecordEntity(questId = quest.id)
        return RecordResult.Ok(base.copy(resolution = Resolution.NO_RECORD))
    }

    private fun basicRejection(quest: QuestEntity, time: LocalDateTime, now: LocalDateTime): RecordResult.Rejected? =
        when {
            quest.status == QuestStatus.CANCELED -> RecordResult.Rejected(RejectReason.QUEST_CANCELED)
            time.isAfter(now) -> RecordResult.Rejected(RejectReason.FUTURE_TIME)
            else -> null
        }

    private fun sourceAfterEdit(
        value: LocalDateTime?,
        previous: LocalDateTime?,
        previousSource: RecordSource?,
    ): RecordSource? = when {
        value == null -> null
        value == previous -> previousSource
        else -> RecordSource.EDIT
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :core:testDebugUnitTest --offline`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/core
git commit -m "feat(core): 출발·도착·수정·정리 기록 규칙" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: 화면용 파생 정보 (연출, 대표 칭호, 정리 필요, 오늘 요약)

**Files:**
- Create: `android-core/core/src/main/kotlin/com/chronoquest/core/domain/Celebrations.kt`
- Create: `android-core/core/src/main/kotlin/com/chronoquest/core/domain/RepresentativeTitle.kt`
- Create: `android-core/core/src/main/kotlin/com/chronoquest/core/domain/CleanupFilter.kt`
- Create: `android-core/core/src/main/kotlin/com/chronoquest/core/domain/Today.kt`
- Test: `android-core/core/src/test/kotlin/com/chronoquest/core/domain/DerivedViewsTest.kt`

**Interfaces:**
- Consumes: Task 2 엔터티; Task 3 `LedgerMapper`; A1 `GameState`, `Grant`, `GrantType`, `Tier`, `QuestOutcome`, `TitleCatalog`, `Engine`
- Produces:
  - `enum class CelebrationKind { TITLE, PERIOD_BONUS, PET_EVOLVED, BOSS_RESULT, TIER_UP }`
  - `data class Celebration(val key: String, val kind: CelebrationKind, val titleId: Int? = null, val xp: Int = 0, val detail: String = "")`
  - `object Celebrations { fun all(state: GameState): List<Celebration>; fun unseen(state: GameState, seenKeys: Set<String>): List<Celebration> }`
  - `object RepresentativeTitle { const val NONE = "미각성자"; fun resolve(selectedId: Int?, state: GameState, catalog: TitleCatalog): String }`
  - `object CleanupFilter { const val GRACE_HOURS = 3L; fun needsCleanup(state: GameState, quests: List<QuestEntity>, now: LocalDateTime): List<QuestEntity> }`
  - `data class TodayQuest(val quest: QuestEntity, val record: QuestRecordEntity?, val outcome: QuestOutcome?)`, `data class TodaySummary(val date: LocalDate, val quests: List<TodayQuest>, val day: DayRecordEntity?)`, `object Today { fun summarize(date, state, quests, records, days): TodaySummary }`

- [ ] **Step 1: 실패 테스트 작성**

`android-core/core/src/test/kotlin/com/chronoquest/core/domain/DerivedViewsTest.kt`:

```kotlin
package com.chronoquest.core.domain

import com.chronoquest.core.d
import com.chronoquest.core.data.AppSettingsEntity
import com.chronoquest.core.data.DayRecordEntity
import com.chronoquest.core.data.LedgerMapper
import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.core.dt
import com.chronoquest.core.questEntity
import com.chronoquest.rules.engine.Engine
import com.chronoquest.rules.engine.GameState
import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.model.QuestKind
import com.chronoquest.rules.titles.TitleCatalog
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class DerivedViewsTest {
    private val engine = Engine()
    private val catalog = TitleCatalog.load()
    private val season = AppSettingsEntity(seasonStart = d("2026-11-25"))

    private fun stateOf(
        now: LocalDateTime,
        quests: List<QuestEntity>,
        records: List<QuestRecordEntity> = emptyList(),
    ): GameState = engine.compute(LedgerMapper.toLedger(season, quests, records, emptyList(), emptyList()), now)

    private fun onTime(q: QuestEntity) = QuestRecordEntity(questId = q.id, arrivedAt = q.dueAt.minusMinutes(2), safe = true)

    @Test
    fun `연출은 칭호 지급 키를 그대로 쓰고 일일 XP는 뺀다`() {
        val q = questEntity("2026-11-25")
        val state = stateOf(dt("2026-11-25", "12:00"), listOf(q), listOf(onTime(q)))
        val all = Celebrations.all(state)
        assertEquals(listOf("title:1:1", "title:21:1"), all.map { it.key })
        assertEquals(listOf(CelebrationKind.TITLE, CelebrationKind.TITLE), all.map { it.kind })
        assertEquals("최초의 각성", all.first().detail)
        assertEquals(1, all.first().titleId)
    }

    @Test
    fun `100 XP를 넘으면 티어 승급과 펫 진화가 연출에 들어간다`() {
        val quests = (0L until 5L).map { questEntity(d("2026-11-25").plusDays(it).toString(), kind = QuestKind.OTHER, due = "10:00", depart = "09:30") }
        val state = stateOf(dt("2026-11-29", "23:00"), quests, quests.map { onTime(it) })
        val keys = Celebrations.all(state).map { it.key }
        assertTrue("tier:SILVER" in keys)
        assertTrue("pet:evolve:BABY" in keys)
        assertTrue(keys.none { it == "tier:BRONZE" || it.startsWith("arr:") })
        assertEquals(CelebrationKind.TIER_UP, Celebrations.all(state).first { it.key == "tier:SILVER" }.kind)
    }

    @Test
    fun `완벽 주간 보너스는 기간 보너스 연출이다`() {
        val week = listOf("2026-11-30", "2026-12-01", "2026-12-02", "2026-12-03", "2026-12-04").map { questEntity(it) }
        val state = stateOf(dt("2026-12-07", "00:00"), week, week.map { onTime(it) })
        val bonus = Celebrations.all(state).single { it.key == "week:2026-11-30:xp" }
        assertEquals(CelebrationKind.PERIOD_BONUS, bonus.kind)
        assertEquals(100, bonus.xp)
    }

    @Test
    fun `이미 본 연출은 빠진다`() {
        val q = questEntity("2026-11-25")
        val state = stateOf(dt("2026-11-25", "12:00"), listOf(q), listOf(onTime(q)))
        assertEquals(listOf("title:21:1"), Celebrations.unseen(state, setOf("title:1:1")).map { it.key })
    }

    @Test
    fun `대표 칭호는 고른 것 그다음 최근 칭호 그다음 미각성자`() {
        val q = questEntity("2026-11-25")
        val empty = stateOf(dt("2026-11-25", "12:00"), listOf(q))
        val earned = stateOf(dt("2026-11-25", "12:00"), listOf(q), listOf(onTime(q)))
        assertEquals("미각성자", RepresentativeTitle.resolve(null, empty, catalog))
        assertEquals("최초의 각성", RepresentativeTitle.resolve(1, earned, catalog))
        assertEquals("운명의 첫걸음", RepresentativeTitle.resolve(null, earned, catalog))
        assertEquals("운명의 첫걸음", RepresentativeTitle.resolve(61, earned, catalog))
    }

    @Test
    fun `예정 3시간이 지나도 판정이 안 된 일정만 정리 필요`() {
        val late = questEntity("2026-11-30")
        val recorded = questEntity("2026-11-30", due = "08:00", depart = "07:30")
        val future = questEntity("2026-11-30", due = "18:00", depart = "17:30")
        val quests = listOf(late, recorded, future)
        val records = listOf(onTime(recorded))
        val before = stateOf(dt("2026-11-30", "11:59"), quests, records)
        val after = stateOf(dt("2026-11-30", "12:00"), quests, records)
        assertEquals(emptyList(), CleanupFilter.needsCleanup(before, quests, dt("2026-11-30", "11:59")))
        assertEquals(listOf(late), CleanupFilter.needsCleanup(after, quests, dt("2026-11-30", "12:00")))
    }

    @Test
    fun `오늘 요약은 그날 일정을 예정 순으로 기록과 판정과 함께 준다`() {
        val evening = questEntity("2026-11-30", kind = QuestKind.OTHER, due = "19:00", depart = "18:30", title = "약속")
        val morning = questEntity("2026-11-30")
        val tomorrow = questEntity("2026-12-01")
        val quests = listOf(evening, morning, tomorrow)
        val records = listOf(onTime(morning))
        val day = DayRecordEntity(date = d("2026-11-30"), selfControl = true)
        val now = dt("2026-11-30", "12:00")
        val summary = Today.summarize(d("2026-11-30"), stateOf(now, quests, records), quests, records, listOf(day))
        assertEquals(listOf(morning, evening), summary.quests.map { it.quest })
        assertEquals(records.single(), summary.quests[0].record)
        assertEquals(ArrivalVerdict.ON_TIME, summary.quests[0].outcome?.verdict)
        assertNull(summary.quests[1].record)
        assertNull(summary.quests[1].outcome?.verdict)
        assertEquals(day, summary.day)
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :core:testDebugUnitTest --offline`
Expected: FAIL, `Unresolved reference 'Celebrations'` 등.

- [ ] **Step 3: 구현**

`android-core/core/src/main/kotlin/com/chronoquest/core/domain/Celebrations.kt`:

```kotlin
package com.chronoquest.core.domain

import com.chronoquest.rules.engine.GameState
import com.chronoquest.rules.engine.GrantType
import com.chronoquest.rules.model.Tier

enum class CelebrationKind { TITLE, PERIOD_BONUS, PET_EVOLVED, BOSS_RESULT, TIER_UP }

/** "오늘의 결과"·알림으로 보여줄 연출 한 건. [key]는 CelebrationSeen과 비교하는 안정 키다. */
data class Celebration(
    val key: String,
    val kind: CelebrationKind,
    val titleId: Int? = null,
    val xp: Int = 0,
    val detail: String = "",
)

/**
 * 연출 목록 (스펙 5.10, 인계 메모 A2).
 * - 엔진 지급 키를 그대로 쓴다.
 * - 티어 승급은 엔진 지급이 없으므로 `tier:<TIER>` 키로 여기서 만든다.
 * - 일일 XP(도착·출발·기상·행동통제)는 연출 대상이 아니다.
 */
object Celebrations {
    private val PERIOD_BONUS_TYPES = setOf(
        GrantType.XP_PERFECT_WEEK,
        GrantType.XP_THREE_WEEKS,
        GrantType.XP_PERFECT_MONTH,
    )

    /** 지급 시간순 연출 다음에 티어 승급(낮은 티어부터). */
    fun all(state: GameState): List<Celebration> {
        val fromGrants = state.grants.mapNotNull { g ->
            when {
                g.type == GrantType.TITLE ->
                    Celebration(g.key, CelebrationKind.TITLE, titleId = g.titleId, detail = g.detail)
                g.type in PERIOD_BONUS_TYPES ->
                    Celebration(g.key, CelebrationKind.PERIOD_BONUS, xp = g.xp, detail = g.type.name)
                g.type == GrantType.PET_EVOLVED ->
                    Celebration(g.key, CelebrationKind.PET_EVOLVED, detail = g.detail)
                g.type == GrantType.BOSS_RESULT ->
                    Celebration(g.key, CelebrationKind.BOSS_RESULT, detail = g.detail)
                else -> null
            }
        }
        val tierUps = Tier.entries
            .filter { it != Tier.BRONZE && state.xp >= it.minXp }
            .map { Celebration("tier:${it.name}", CelebrationKind.TIER_UP, detail = it.title) }
        return fromGrants + tierUps
    }

    fun unseen(state: GameState, seenKeys: Set<String>): List<Celebration> =
        all(state).filter { it.key !in seenKeys }
}
```

`android-core/core/src/main/kotlin/com/chronoquest/core/domain/RepresentativeTitle.kt`:

```kotlin
package com.chronoquest.core.domain

import com.chronoquest.rules.engine.GameState
import com.chronoquest.rules.engine.GrantType
import com.chronoquest.rules.titles.TitleCatalog

/** 홈에 띄우는 대표 칭호 (스펙 5.7, 인계 메모 A2). */
object RepresentativeTitle {
    const val NONE = "미각성자"

    /** 고른 칭호를 아직 갖고 있으면 그것, 아니면 가장 최근에 얻은 칭호, 하나도 없으면 미각성자. */
    fun resolve(selectedId: Int?, state: GameState, catalog: TitleCatalog): String {
        if (selectedId != null && (state.inventory[selectedId] ?: 0) > 0) return catalog.title(selectedId).name
        val latest = state.grants.lastOrNull { it.type == GrantType.TITLE }?.titleId ?: return NONE
        return catalog.title(latest).name
    }
}
```

`android-core/core/src/main/kotlin/com/chronoquest/core/domain/CleanupFilter.kt`:

```kotlin
package com.chronoquest.core.domain

import com.chronoquest.core.data.QuestEntity
import com.chronoquest.rules.engine.GameState
import java.time.LocalDateTime

/**
 * 홈의 "정리 필요" 카드 대상 (스펙 6.2). 엔진의 pendingQuestIds에는 이동 중인 일정도 들어 있으므로,
 * 예정 +3시간이 지난 것만 거른다(인계 메모 A2).
 */
object CleanupFilter {
    const val GRACE_HOURS = 3L

    fun needsCleanup(state: GameState, quests: List<QuestEntity>, now: LocalDateTime): List<QuestEntity> {
        val pending = state.pendingQuestIds.toSet()
        return quests
            .filter { it.id in pending && !it.dueAt.plusHours(GRACE_HOURS).isAfter(now) }
            .sortedWith(compareBy<QuestEntity>({ it.dueAt }, { it.id }))
    }
}
```

`android-core/core/src/main/kotlin/com/chronoquest/core/domain/Today.kt`:

```kotlin
package com.chronoquest.core.domain

import com.chronoquest.core.data.DayRecordEntity
import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.rules.engine.GameState
import com.chronoquest.rules.engine.QuestOutcome
import java.time.LocalDate

data class TodayQuest(
    val quest: QuestEntity,
    val record: QuestRecordEntity?,
    val outcome: QuestOutcome?,
)

data class TodaySummary(
    val date: LocalDate,
    val quests: List<TodayQuest>,
    val day: DayRecordEntity?,
)

/** 홈 "오늘의 퀘스트"용 요약. 취소·휴가로 판정에서 빠진 일정은 outcome이 null이다. */
object Today {
    fun summarize(
        date: LocalDate,
        state: GameState,
        quests: List<QuestEntity>,
        records: List<QuestRecordEntity>,
        days: List<DayRecordEntity>,
    ): TodaySummary {
        val recordByQuest = records.associateBy { it.questId }
        val items = quests
            .filter { it.date == date }
            .sortedWith(compareBy<QuestEntity>({ it.dueAt }, { it.id }))
            .map { TodayQuest(it, recordByQuest[it.id], state.outcomes[it.id]) }
        return TodaySummary(date = date, quests = items, day = days.firstOrNull { it.date == date })
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :core:testDebugUnitTest --offline`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/core
git commit -m "feat(core): 연출 목록, 대표 칭호, 정리 필요, 오늘 요약" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: GameService — 기록 쓰기와 연출

**Files:**
- Create: `android-core/core/src/main/kotlin/com/chronoquest/core/service/Clocks.kt`
- Create: `android-core/core/src/main/kotlin/com/chronoquest/core/service/GameService.kt`
- Test: `android-core/core/src/test/kotlin/com/chronoquest/core/service/GameServiceRecordTest.kt`

**Interfaces:**
- Consumes: Task 2 DB·DAO·엔터티; Task 3 `LedgerRepository`; Task 5 `RecordPolicy`, `RecordResult`, `RejectReason`; Task 6 `Celebration`, `Celebrations`; A1 `Engine`, `GameState`
- Produces:
  - `fun Clock.nowLocal(): LocalDateTime` (초 단위), `fun Clock.today(): LocalDate`
  - `sealed interface ActionResult { data class Done(val xpGained: Int, val newCelebrations: List<Celebration>, val askSelfControl: Boolean = false); data class Rejected(val reason: RejectReason); data object NotFound }`
  - `class GameService(db: ChronoDatabase, clock: Clock, engine: Engine = Engine())`의 메서드: `suspend fun state(): GameState`, `depart(questId, at: LocalDateTime? = null, source: RecordSource = BUTTON): ActionResult`, `arrive(questId, safe: Boolean, at: LocalDateTime? = null, source = BUTTON)`, `editRecord(questId, departedAt, arrivedAt, safe)`, `resolveNoRecord(questId)`, `cancelQuest(questId)`, `answerSelfControl(date, value)`, `unseenCelebrations(): List<Celebration>`, `markSeen(keys: Collection<String>)`

- [ ] **Step 1: 실패 테스트 작성**

`android-core/core/src/test/kotlin/com/chronoquest/core/service/GameServiceRecordTest.kt`:

```kotlin
package com.chronoquest.core.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.core.clockAt
import com.chronoquest.core.d
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.RecordSource
import com.chronoquest.core.domain.RejectReason
import com.chronoquest.core.dt
import com.chronoquest.core.inMemoryDb
import com.chronoquest.core.questEntity
import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.model.QuestKind
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameServiceRecordTest {
    private lateinit var db: ChronoDatabase
    private val work = questEntity("2026-11-30")
    private val other = questEntity("2026-11-30", kind = QuestKind.OTHER, due = "19:00", depart = "18:30", title = "약속")

    @Before
    fun setUp() = runTest {
        db = inMemoryDb()
        db.questDao().insertAll(listOf(work, other))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun service(time: String) = GameService(db, clockAt("2026-11-30", time))

    private fun done(result: ActionResult): ActionResult.Done = result as ActionResult.Done

    @Test
    fun `정시 출발이면 10 XP와 정시 출발 칭호를 주고 그날 첫 출발이라 행동통제를 묻는다`() = runTest {
        val result = done(service("08:25").depart(work.id))
        assertEquals(10, result.xpGained)
        assertTrue(result.newCelebrations.any { it.key == "title:41:1" })
        assertTrue(result.askSelfControl)
        assertEquals(RecordSource.BUTTON, db.recordDao().get(work.id)?.departSource)
    }

    @Test
    fun `안전한 정시 도착이면 20 XP와 첫 미션 칭호`() = runTest {
        val result = done(service("08:55").arrive(work.id, safe = true))
        assertEquals(20, result.xpGained)
        assertEquals(listOf("title:1:1", "title:21:1"), result.newCelebrations.map { it.key })
    }

    @Test
    fun `이미 출발한 퀘스트와 미래 시각과 없는 퀘스트는 거부한다`() = runTest {
        service("08:25").depart(work.id)
        assertEquals(ActionResult.Rejected(RejectReason.ALREADY_RECORDED), service("08:30").depart(work.id))
        assertEquals(
            ActionResult.Rejected(RejectReason.FUTURE_TIME),
            service("18:00").depart(other.id, at = dt("2026-11-30", "18:20")),
        )
        assertEquals(ActionResult.NotFound, service("08:30").depart("없는-퀘스트"))
    }

    @Test
    fun `그날 두 번째 출발이면 행동통제를 묻지 않는다`() = runTest {
        service("08:25").depart(work.id)
        assertFalse(done(service("18:40").depart(other.id)).askSelfControl)
    }

    @Test
    fun `행동통제를 이미 답했으면 묻지 않는다`() = runTest {
        service("07:00").answerSelfControl(d("2026-11-30"), true)
        assertFalse(done(service("08:25").depart(work.id)).askSelfControl)
    }

    @Test
    fun `행동통제 답이 예면 5 XP와 행동통제 칭호`() = runTest {
        val result = done(service("08:25").answerSelfControl(d("2026-11-30"), true))
        assertEquals(5, result.xpGained)
        assertTrue(result.newCelebrations.any { it.key == "title:101:1" })
    }

    @Test
    fun `기록을 고치면 다시 정산하고 수정 표시를 남긴다`() = runTest {
        assertEquals(0, done(service("09:10").arrive(work.id, safe = true)).xpGained)
        val edited = done(service("09:20").editRecord(work.id, null, dt("2026-11-30", "08:55"), true))
        assertEquals(20, edited.xpGained)
        val record = db.recordDao().get(work.id)!!
        assertTrue(record.edited)
        assertEquals(RecordSource.EDIT, record.arriveSource)
    }

    @Test
    fun `기록 없음으로 정리하면 지각으로 판정되고 보류에서 빠진다`() = runTest {
        val svc = service("13:00")
        assertTrue(work.id in svc.state().pendingQuestIds)
        done(svc.resolveNoRecord(work.id))
        val state = svc.state()
        assertEquals(ArrivalVerdict.LATE, state.outcomes[work.id]?.verdict)
        assertFalse(work.id in state.pendingQuestIds)
    }

    @Test
    fun `취소하면 판정에서 빠지고 XP도 다시 계산된다`() = runTest {
        service("08:55").arrive(work.id, safe = true)
        val result = done(service("10:00").cancelQuest(work.id))
        assertEquals(-20, result.xpGained)
        assertFalse(work.id in service("10:00").state().outcomes)
    }

    @Test
    fun `본 연출은 다시 새 연출로 나오지 않는다`() = runTest {
        val svc = service("08:55")
        svc.arrive(work.id, safe = true)
        val unseen = svc.unseenCelebrations()
        assertEquals(listOf("title:1:1", "title:21:1"), unseen.map { it.key })
        svc.markSeen(unseen.map { it.key })
        assertEquals(emptyList(), svc.unseenCelebrations())
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :core:testDebugUnitTest --offline`
Expected: FAIL, `Unresolved reference 'GameService'` / `'ActionResult'`.

- [ ] **Step 3: 구현**

`android-core/core/src/main/kotlin/com/chronoquest/core/service/Clocks.kt`:

```kotlin
package com.chronoquest.core.service

import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** 기기 로컬 벽시계 시각(초 단위). */
fun Clock.nowLocal(): LocalDateTime = LocalDateTime.now(this).truncatedTo(ChronoUnit.SECONDS)

fun Clock.today(): LocalDate = LocalDate.now(this)
```

`android-core/core/src/main/kotlin/com/chronoquest/core/service/GameService.kt`:

```kotlin
package com.chronoquest.core.service

import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.CelebrationSeenEntity
import com.chronoquest.core.data.DayRecordEntity
import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.core.data.RecordSource
import com.chronoquest.core.domain.Celebration
import com.chronoquest.core.domain.Celebrations
import com.chronoquest.core.domain.RecordPolicy
import com.chronoquest.core.domain.RecordResult
import com.chronoquest.core.domain.RejectReason
import com.chronoquest.rules.engine.Engine
import com.chronoquest.rules.engine.GameState
import com.chronoquest.rules.model.QuestStatus
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ActionResult {
    /** 쓰기 성공. [newCelebrations]는 이번 쓰기로 새로 생겼고 아직 안 본 연출이다. */
    data class Done(
        val xpGained: Int,
        val newCelebrations: List<Celebration>,
        val askSelfControl: Boolean = false,
    ) : ActionResult

    data class Rejected(val reason: RejectReason) : ActionResult

    data object NotFound : ActionResult
}

/**
 * 기록 쓰기와 연출의 창구. 앱에서 하나만 만들어 재사용한다(엔진 인스턴스도 하나, 인계 메모 A2).
 * 쓰기는 직렬화하고, 쓰기 전후 상태를 비교해 새로 생긴 연출을 돌려준다.
 */
class GameService(
    private val db: ChronoDatabase,
    private val clock: Clock,
    private val engine: Engine = Engine(),
) {
    private val ledgers = LedgerRepository(db)
    private val mutex = Mutex()

    suspend fun state(): GameState = engine.compute(ledgers.loadLedger(), clock.nowLocal())

    suspend fun depart(
        questId: String,
        at: LocalDateTime? = null,
        source: RecordSource = RecordSource.BUTTON,
    ): ActionResult = mutex.withLock {
        val now = clock.nowLocal()
        val result = recordAction(questId) { quest, current ->
            RecordPolicy.depart(quest, current, at ?: now, now, source)
        }
        if (result is ActionResult.Done) result.copy(askSelfControl = shouldAskSelfControl(questId)) else result
    }

    suspend fun arrive(
        questId: String,
        safe: Boolean,
        at: LocalDateTime? = null,
        source: RecordSource = RecordSource.BUTTON,
    ): ActionResult = mutex.withLock {
        val now = clock.nowLocal()
        recordAction(questId) { quest, current -> RecordPolicy.arrive(quest, current, at ?: now, safe, now, source) }
    }

    suspend fun editRecord(
        questId: String,
        departedAt: LocalDateTime?,
        arrivedAt: LocalDateTime?,
        safe: Boolean?,
    ): ActionResult = mutex.withLock {
        val now = clock.nowLocal()
        recordAction(questId) { quest, current -> RecordPolicy.edit(quest, current, departedAt, arrivedAt, safe, now) }
    }

    suspend fun resolveNoRecord(questId: String): ActionResult = mutex.withLock {
        recordAction(questId) { quest, current -> RecordPolicy.resolveNoRecord(quest, current) }
    }

    /** 불가피한 취소(스펙 6.4). 판정에서 빠진다. */
    suspend fun cancelQuest(questId: String): ActionResult = mutex.withLock {
        val quest = db.questDao().get(questId) ?: return@withLock ActionResult.NotFound
        applyChange { db.questDao().update(quest.copy(status = QuestStatus.CANCELED)) }
    }

    suspend fun answerSelfControl(date: LocalDate, value: Boolean): ActionResult = mutex.withLock {
        applyChange {
            val current = db.dayDao().get(date) ?: DayRecordEntity(date = date)
            db.dayDao().upsert(current.copy(selfControl = value))
        }
    }

    suspend fun unseenCelebrations(): List<Celebration> =
        Celebrations.unseen(state(), db.celebrationDao().seenKeys().toSet())

    suspend fun markSeen(keys: Collection<String>) {
        val at = clock.nowLocal()
        db.celebrationDao().markSeen(keys.map { CelebrationSeenEntity(grantKey = it, seenAt = at) })
    }

    private suspend fun recordAction(
        questId: String,
        decide: (QuestEntity, QuestRecordEntity?) -> RecordResult,
    ): ActionResult {
        val quest = db.questDao().get(questId) ?: return ActionResult.NotFound
        return when (val result = decide(quest, db.recordDao().get(questId))) {
            is RecordResult.Rejected -> ActionResult.Rejected(result.reason)
            is RecordResult.Ok -> applyChange { db.recordDao().upsert(result.record) }
        }
    }

    private suspend fun applyChange(write: suspend () -> Unit): ActionResult.Done {
        val before = state()
        write()
        val after = state()
        val beforeKeys = Celebrations.all(before).mapTo(HashSet()) { it.key }
        val seen = db.celebrationDao().seenKeys().toSet()
        return ActionResult.Done(
            xpGained = after.xp - before.xp,
            newCelebrations = Celebrations.all(after).filter { it.key !in beforeKeys && it.key !in seen },
        )
    }

    /** 그날 첫 출발이고 행동통제를 아직 답하지 않았으면 물어본다(스펙 6.2). */
    private suspend fun shouldAskSelfControl(questId: String): Boolean {
        val quest = db.questDao().get(questId) ?: return false
        if (db.dayDao().get(quest.date)?.selfControl != null) return false
        return db.questDao().onDate(quest.date)
            .filter { it.id != questId }
            .none { db.recordDao().get(it.id)?.departedAt != null }
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :core:testDebugUnitTest --offline`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/core
git commit -m "feat(core): GameService 기록 쓰기와 새 연출 계산" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: ScheduleService — 템플릿, 일정 생성, 단발, 휴가

**Files:**
- Create: `android-core/core/src/main/kotlin/com/chronoquest/core/service/ScheduleService.kt`
- Test: `android-core/core/src/test/kotlin/com/chronoquest/core/service/ScheduleServiceTest.kt`

**Interfaces:**
- Consumes: Task 2 DB·DAO·엔터티; Task 3 `LedgerRepository`; Task 4 `TemplateRules`, `QuestGenerator`, `ScheduleProblem`; Task 1 `QuestIds`; Task 7 `Clock.nowLocal()`
- Produces:
  - `sealed interface ScheduleResult { data class Saved(val id: String); data class Rejected(val problems: List<ScheduleProblem>) }`
  - `class ScheduleService(db: ChronoDatabase, clock: Clock)`의 메서드: `templates()`, `saveTemplate(template): ScheduleResult`, `deleteTemplate(id: Long)`, `generateQuests(days: Int = 7): Int`, `addOneOffQuest(date, kind, title, dueTime, targetDepartTime): ScheduleResult`, `vacations(): List<LocalDate>`, `setVacation(date, on: Boolean)`

- [ ] **Step 1: 실패 테스트 작성**

시계는 2026-11-25(수) 07:00 기준이다.

`android-core/core/src/test/kotlin/com/chronoquest/core/service/ScheduleServiceTest.kt`:

```kotlin
package com.chronoquest.core.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.core.clockAt
import com.chronoquest.core.d
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.core.data.QuestSource
import com.chronoquest.core.domain.ScheduleProblem
import com.chronoquest.core.dt
import com.chronoquest.core.inMemoryDb
import com.chronoquest.core.weekdayTemplate
import com.chronoquest.rules.model.QuestKind
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScheduleServiceTest {
    private lateinit var db: ChronoDatabase

    @Before
    fun setUp() {
        db = inMemoryDb()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun service(date: String = "2026-11-25", time: String = "07:00") = ScheduleService(db, clockAt(date, time))

    private suspend fun questIds(): List<String> = db.questDao().all().map { it.id }.sorted()

    @Test
    fun `평일 템플릿을 저장하면 오늘부터 7일 중 평일 일정을 만든다`() = runTest {
        val result = service().saveTemplate(weekdayTemplate())
        assertTrue(result is ScheduleResult.Saved)
        assertEquals(
            listOf(
                "2026-11-25-WORK-09:00",
                "2026-11-26-WORK-09:00",
                "2026-11-27-WORK-09:00",
                "2026-11-30-WORK-09:00",
                "2026-12-01-WORK-09:00",
            ),
            questIds(),
        )
    }

    @Test
    fun `잘못된 템플릿은 저장하지 않는다`() = runTest {
        assertEquals(
            ScheduleResult.Rejected(listOf(ScheduleProblem.DEPART_AFTER_DUE)),
            service().saveTemplate(weekdayTemplate(depart = "09:30")),
        )
        assertEquals(emptyList(), service().templates())
    }

    @Test
    fun `이미 지난 시각의 일정은 만들지 않는다`() = runTest {
        service(time = "10:00").saveTemplate(weekdayTemplate())
        assertEquals(4, db.questDao().all().size)
        assertTrue("2026-11-25-WORK-09:00" !in questIds())
    }

    @Test
    fun `일정 생성은 여러 번 해도 같다`() = runTest {
        service().saveTemplate(weekdayTemplate())
        assertEquals(0, service().generateQuests())
        assertEquals(5, db.questDao().all().size)
    }

    @Test
    fun `템플릿을 고치면 기록 없는 미래 일정만 새 시각으로 바뀐다`() = runTest {
        val id = (service().saveTemplate(weekdayTemplate()) as ScheduleResult.Saved).id.toLong()
        db.recordDao().upsert(
            QuestRecordEntity(questId = "2026-11-25-WORK-09:00", departedAt = dt("2026-11-25", "06:50")),
        )
        service().saveTemplate(weekdayTemplate(id = id, due = "09:30", depart = "09:00"))
        assertEquals(
            listOf(
                "2026-11-25-WORK-09:00",
                "2026-11-26-WORK-09:30",
                "2026-11-27-WORK-09:30",
                "2026-11-30-WORK-09:30",
                "2026-12-01-WORK-09:30",
            ),
            questIds(),
        )
    }

    @Test
    fun `템플릿을 지우면 기록 없는 미래 일정만 지운다`() = runTest {
        val id = (service().saveTemplate(weekdayTemplate()) as ScheduleResult.Saved).id.toLong()
        db.recordDao().upsert(
            QuestRecordEntity(questId = "2026-11-25-WORK-09:00", departedAt = dt("2026-11-25", "06:50")),
        )
        service().deleteTemplate(id)
        assertEquals(listOf("2026-11-25-WORK-09:00"), questIds())
        assertEquals(emptyList(), service().templates())
    }

    @Test
    fun `단발 일정을 추가한다`() = runTest {
        val result = service().addOneOffQuest(
            d("2026-11-28"), QuestKind.OTHER, "보드게임", LocalTime.parse("14:00"), LocalTime.parse("13:20"),
        )
        assertEquals(ScheduleResult.Saved("2026-11-28-OTHER-14:00"), result)
        val quest = db.questDao().get("2026-11-28-OTHER-14:00")!!
        assertEquals(QuestSource.MANUAL, quest.source)
        assertEquals(dt("2026-11-28", "13:20"), quest.targetDepartAt)
    }

    @Test
    fun `단발 일정 검증`() = runTest {
        val svc = service()
        svc.addOneOffQuest(d("2026-11-28"), QuestKind.OTHER, "보드게임", LocalTime.parse("14:00"), LocalTime.parse("13:20"))
        assertEquals(
            ScheduleResult.Rejected(listOf(ScheduleProblem.DUPLICATE)),
            svc.addOneOffQuest(d("2026-11-28"), QuestKind.OTHER, "다른 약속", LocalTime.parse("14:00"), LocalTime.parse("13:00")),
        )
        assertEquals(
            ScheduleResult.Rejected(listOf(ScheduleProblem.BEFORE_SEASON)),
            svc.addOneOffQuest(d("2026-11-24"), QuestKind.OTHER, "약속", LocalTime.parse("14:00"), LocalTime.parse("13:00")),
        )
        assertEquals(
            ScheduleResult.Rejected(listOf(ScheduleProblem.BLANK_TITLE, ScheduleProblem.DEPART_AFTER_DUE)),
            svc.addOneOffQuest(d("2026-11-29"), QuestKind.OTHER, " ", LocalTime.parse("14:00"), LocalTime.parse("14:10")),
        )
    }

    @Test
    fun `휴가를 켜고 끈다`() = runTest {
        val svc = service()
        svc.setVacation(d("2026-11-26"), true)
        svc.setVacation(d("2026-11-26"), true)
        assertEquals(listOf(d("2026-11-26")), svc.vacations())
        svc.setVacation(d("2026-11-26"), false)
        assertEquals(emptyList(), svc.vacations())
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :core:testDebugUnitTest --offline`
Expected: FAIL, `Unresolved reference 'ScheduleService'` / `'ScheduleResult'`.

- [ ] **Step 3: 구현**

`android-core/core/src/main/kotlin/com/chronoquest/core/service/ScheduleService.kt`:

```kotlin
package com.chronoquest.core.service

import androidx.room.withTransaction
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestSource
import com.chronoquest.core.data.ScheduleTemplateEntity
import com.chronoquest.core.data.VacationEntity
import com.chronoquest.core.domain.QuestGenerator
import com.chronoquest.core.domain.QuestIds
import com.chronoquest.core.domain.ScheduleProblem
import com.chronoquest.core.domain.TemplateRules
import com.chronoquest.rules.model.QuestKind
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

sealed interface ScheduleResult {
    data class Saved(val id: String) : ScheduleResult
    data class Rejected(val problems: List<ScheduleProblem>) : ScheduleResult
}

/** 일정 소스 관리 (스펙 6.4): 반복 템플릿, 7일치 생성, 단발 추가, 휴가. */
class ScheduleService(
    private val db: ChronoDatabase,
    private val clock: Clock,
) {
    private val ledgers = LedgerRepository(db)

    suspend fun templates(): List<ScheduleTemplateEntity> = db.templateDao().all()

    /** 템플릿을 저장하고, 그 템플릿의 기록 없는 미래 일정을 새 내용으로 다시 만든다. */
    suspend fun saveTemplate(template: ScheduleTemplateEntity): ScheduleResult {
        val problems = TemplateRules.validate(template)
        if (problems.isNotEmpty()) return ScheduleResult.Rejected(problems)
        val id = db.withTransaction {
            val id = if (template.id == 0L) {
                db.templateDao().insert(template)
            } else {
                db.templateDao().update(template)
                template.id
            }
            dropFutureUnrecorded(id)
            generateQuests()
            id
        }
        return ScheduleResult.Saved(id.toString())
    }

    /** 템플릿을 지우고 그 템플릿의 기록 없는 미래 일정도 지운다. 지난 일정과 기록은 남긴다. */
    suspend fun deleteTemplate(id: Long) {
        db.withTransaction {
            dropFutureUnrecorded(id)
            db.templateDao().delete(id)
        }
    }

    /**
     * 오늘부터 [days]일치 템플릿 일정을 만들고 새로 만든 개수를 돌려준다.
     * 이미 있는 ID, 같은 템플릿이 그날 이미 만든 일정, 이미 지난 일정은 만들지 않는다.
     */
    suspend fun generateQuests(days: Int = QuestGenerator.HORIZON_DAYS): Int {
        val now = clock.nowLocal()
        val existing = db.questDao().all()
        val existingIds = existing.mapTo(HashSet()) { it.id }
        val templateDays = existing.filter { it.templateId != null }.mapTo(HashSet()) { it.templateId to it.date }
        val fresh = QuestGenerator.generate(db.templateDao().all(), now.toLocalDate(), days).filter {
            it.id !in existingIds && (it.templateId to it.date) !in templateDays && it.dueAt.isAfter(now)
        }
        db.questDao().insertAll(fresh)
        return fresh.size
    }

    suspend fun addOneOffQuest(
        date: LocalDate,
        kind: QuestKind,
        title: String,
        dueTime: LocalTime,
        targetDepartTime: LocalTime,
    ): ScheduleResult {
        val problems = TemplateRules.problems(title, dueTime, targetDepartTime).toMutableList()
        if (date.isBefore(ledgers.settings().seasonStart)) problems += ScheduleProblem.BEFORE_SEASON
        val id = QuestIds.of(date, kind, dueTime)
        if (problems.isEmpty() && db.questDao().get(id) != null) problems += ScheduleProblem.DUPLICATE
        if (problems.isNotEmpty()) return ScheduleResult.Rejected(problems)
        db.questDao().insertAll(
            listOf(
                QuestEntity(
                    id = id,
                    date = date,
                    kind = kind,
                    title = title,
                    dueAt = date.atTime(dueTime.truncatedTo(ChronoUnit.MINUTES)),
                    targetDepartAt = date.atTime(targetDepartTime.truncatedTo(ChronoUnit.MINUTES)),
                    source = QuestSource.MANUAL,
                ),
            ),
        )
        return ScheduleResult.Saved(id)
    }

    suspend fun vacations(): List<LocalDate> = db.vacationDao().all().map { it.date }

    suspend fun setVacation(date: LocalDate, on: Boolean) {
        if (on) db.vacationDao().add(VacationEntity(date)) else db.vacationDao().remove(date)
    }

    private suspend fun dropFutureUnrecorded(templateId: Long) {
        val ids = db.questDao().unrecordedFutureIds(templateId, clock.nowLocal())
        if (ids.isNotEmpty()) db.questDao().deleteIds(ids)
    }
}
```

- [ ] **Step 4: 통과 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :core:testDebugUnitTest --offline`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/core
git commit -m "feat(core): ScheduleService 템플릿·일정 생성·단발·휴가" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: GameService — 화면용 조회 (오늘 요약, 정리 필요, 대표 칭호)

**Files:**
- Modify: `android-core/core/src/main/kotlin/com/chronoquest/core/service/GameService.kt`
- Test: `android-core/core/src/test/kotlin/com/chronoquest/core/service/GameServiceViewTest.kt`

**Interfaces:**
- Consumes: Task 6 `Today`, `TodaySummary`, `CleanupFilter`, `RepresentativeTitle`; Task 7 `GameService`; A1 `TitleCatalog`
- Produces: `GameService` 생성자가 `(db, clock, catalog: TitleCatalog = TitleCatalog.load(), engine: Engine = Engine(catalog))`로 바뀌고, 메서드 `suspend fun today(): TodaySummary`, `needsCleanup(): List<QuestEntity>`, `representativeTitle(): String`, `setRepresentativeTitle(titleId: Int?): Boolean`가 추가된다.

- [ ] **Step 1: 실패 테스트 작성**

`android-core/core/src/test/kotlin/com/chronoquest/core/service/GameServiceViewTest.kt`:

```kotlin
package com.chronoquest.core.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.core.clockAt
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.inMemoryDb
import com.chronoquest.core.questEntity
import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.model.QuestKind
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameServiceViewTest {
    private lateinit var db: ChronoDatabase
    private val work = questEntity("2026-11-30")
    private val other = questEntity("2026-11-30", kind = QuestKind.OTHER, due = "19:00", depart = "18:30", title = "약속")
    private val tomorrow = questEntity("2026-12-01")

    @Before
    fun setUp() = runTest {
        db = inMemoryDb()
        db.questDao().insertAll(listOf(other, tomorrow, work))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun service(time: String) = GameService(db, clockAt("2026-11-30", time))

    @Test
    fun `오늘 일정은 예정 시각 순으로 기록과 판정을 함께 준다`() = runTest {
        service("08:55").arrive(work.id, safe = true)
        val today = service("12:00").today()
        assertEquals(listOf(work.id, other.id), today.quests.map { it.quest.id })
        assertEquals(ArrivalVerdict.ON_TIME, today.quests[0].outcome?.verdict)
        assertNull(today.quests[1].record)
        assertNull(today.day)
    }

    @Test
    fun `예정 3시간이 지나도 기록이 없으면 정리 필요`() = runTest {
        assertEquals(emptyList(), service("11:59").needsCleanup())
        assertEquals(listOf(work), service("12:00").needsCleanup())
    }

    @Test
    fun `대표 칭호는 가진 칭호만 고를 수 있고 사라지면 최근 칭호로 돌아간다`() = runTest {
        assertEquals("미각성자", service("08:00").representativeTitle())
        service("08:55").arrive(work.id, safe = true)
        val svc = service("09:00")
        assertEquals("운명의 첫걸음", svc.representativeTitle())
        assertTrue(svc.setRepresentativeTitle(1))
        assertEquals("최초의 각성", svc.representativeTitle())
        assertFalse(svc.setRepresentativeTitle(61))
        assertEquals("최초의 각성", svc.representativeTitle())
        svc.cancelQuest(work.id)
        assertEquals("미각성자", svc.representativeTitle())
    }
}
```

- [ ] **Step 2: 실패 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :core:testDebugUnitTest --offline`
Expected: FAIL, `Unresolved reference 'today'` / `'needsCleanup'` / `'representativeTitle'`.

- [ ] **Step 3: GameService 수정**

`GameService.kt` import에 아래를 추가한다.

```kotlin
import com.chronoquest.core.domain.CleanupFilter
import com.chronoquest.core.domain.RepresentativeTitle
import com.chronoquest.core.domain.Today
import com.chronoquest.core.domain.TodaySummary
import com.chronoquest.rules.titles.TitleCatalog
```

생성자를 바꾼다.

변경 전:
```kotlin
class GameService(
    private val db: ChronoDatabase,
    private val clock: Clock,
    private val engine: Engine = Engine(),
) {
```
변경 후:
```kotlin
class GameService(
    private val db: ChronoDatabase,
    private val clock: Clock,
    private val catalog: TitleCatalog = TitleCatalog.load(),
    private val engine: Engine = Engine(catalog),
) {
```

`markSeen` 함수 바로 아래에 추가한다.

```kotlin
    /** 홈 "오늘의 퀘스트"용 요약. */
    suspend fun today(): TodaySummary {
        val now = clock.nowLocal()
        val state = engine.compute(ledgers.loadLedger(), now)
        return Today.summarize(now.toLocalDate(), state, db.questDao().all(), db.recordDao().all(), db.dayDao().all())
    }

    /** 홈 "정리 필요" 카드 대상. */
    suspend fun needsCleanup(): List<QuestEntity> {
        val now = clock.nowLocal()
        return CleanupFilter.needsCleanup(engine.compute(ledgers.loadLedger(), now), db.questDao().all(), now)
    }

    suspend fun representativeTitle(): String =
        RepresentativeTitle.resolve(ledgers.settings().representativeTitleId, state(), catalog)

    /** 가진 칭호만 대표로 고를 수 있다. null이면 선택을 지운다. 고를 수 없으면 false. */
    suspend fun setRepresentativeTitle(titleId: Int?): Boolean {
        if (titleId != null && (state().inventory[titleId] ?: 0) == 0) return false
        db.settingsDao().upsert(ledgers.settings().copy(representativeTitleId = titleId))
        return true
    }
```

- [ ] **Step 4: 통과 확인**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :core:testDebugUnitTest --offline`
Expected: `BUILD SUCCESSFUL` (GameServiceRecordTest도 그대로 통과)

- [ ] **Step 5: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add android-core/core
git commit -m "feat(core): 오늘 요약·정리 필요·대표 칭호 조회" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: CI, README, 인계 메모 갱신

**Files:**
- Create: `.github/workflows/ci.yml`
- Modify: `android-core/README.md`
- Modify: `docs/superpowers/notes/2026-11-25-a1-carryover.md`

**Interfaces:**
- Consumes: 모든 이전 태스크
- Produces: PR과 main 푸시마다 `:rules:test`, `:core:testDebugUnitTest`를 도는 GitHub Actions

- [ ] **Step 1: CI 워크플로 작성**

`.github/workflows/ci.yml`:

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-java@v6
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v6
      - name: Rules and core unit tests
        working-directory: android-core
        run: ./gradlew :rules:test :core:testDebugUnitTest
```

ubuntu 러너에는 Android SDK가 깔려 있고(`ANDROID_HOME`) 라이선스도 동의된 상태다. 그래서 `local.properties` 없이 빌드된다. 필요한 플랫폼이 없으면 AGP가 받는다.

- [ ] **Step 2: README 갱신**

`android-core/README.md` 전체:

````markdown
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
````

- [ ] **Step 3: 인계 메모 갱신**

`docs/superpowers/notes/2026-11-25-a1-carryover.md`의 `## A2 (데이터 계층 + 수동 플레이 + Compose 임시 화면)` 절 바로 아래, 기존 내용 위에 다음 블록을 넣는다.

```markdown
> **A2a에서 처리함 (2026-11-25)**
> - 중복 금지: Room 기본키
> - 미래 시각 금지, `safe`는 도착 때만: `RecordPolicy`
> - `date == dueAt.toLocalDate()`: `QuestGenerator`, `ScheduleService.addOneOffQuest`
> - 시각 저장: `Converters`(로컬 벽시계 시각, 초 단위)
> - 대표 칭호: `RepresentativeTitle`, `GameService.representativeTitle()`
> - 오늘 요약: `Today`, `GameService.today()`
> - 티어 승급 연출 키 `tier:<TIER>`: `Celebrations`
> - "정리 필요" +3시간 필터: `CleanupFilter`
> - 엔진 인스턴스 하나: `GameService`가 생성자로 받음
>
> **A2b·Phase B로 남은 것**
> - grants 범위 제한(bridge)
> - 보스 `endsAt`과 데미지 출처별 내역
> - pet/boss 키 재표시 정책 문서화
```

- [ ] **Step 4: 전체 테스트**

Run: `cd /Users/sang/Desktop/chrono-oath/android-core && ./gradlew :rules:test :core:testDebugUnitTest --offline`
Expected: `BUILD SUCCESSFUL`. rules 72개, core의 QuestIds 2, Database 8, LedgerMapper 1, LedgerRepository 2, TemplateRules 3, QuestGenerator 4, RecordPolicy 9, DerivedViews 7, GameServiceRecord 10, ScheduleService 9, GameServiceView 3이 모두 통과한다.

- [ ] **Step 5: 커밋**

```bash
cd /Users/sang/Desktop/chrono-oath
git add .github/workflows/ci.yml android-core/README.md docs/superpowers/notes/2026-11-25-a1-carryover.md
git commit -m "ci: GitHub Actions로 rules·core 테스트, README와 인계 메모 갱신" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## 완료 기준

- `./gradlew :rules:test :core:testDebugUnitTest`가 로컬과 GitHub Actions(PR) 양쪽에서 통과한다.
- 폰 없이 JVM 테스트로 아래 흐름이 검증된다.
  - 템플릿 저장 → 7일치 일정 생성
  - 수동 출발·도착 → 정산과 새 연출
  - 행동통제 질문
  - 수정·정리·취소
  - 대표 칭호, 오늘 요약, 정리 필요
- Room 스키마 v1이 `core/schemas/`에 커밋돼 있다.
- 다음: A2b 계획(실행용 앱, 디자인 토큰 → Compose 테마, 임시 화면, 정산 알림, 매일 일정 생성, 폰 설치).
