# 크로노 퀘스트 A2b-2 (일정·칭호 화면, 홈 펫·보스 카드) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 이 계획이 끝나면 다음이 된다.
- 하단 탭의 **일정**·**칭호** 화면이 생긴다.
- 홈에 펫·이번 주 보스 글자 카드와 휴가 처리가 들어간다.
- 홈에 남아 있던 안정성 문제를 고친다.

**Architecture:** A2b-1과 같은 구조다.
- 화면마다 `XxxPresenter`가 상태와 동작을 갖는다. StateFlow를 쓰고, compute 디스패처에서 돌고, 서비스는 처음 쓸 때 만든다.
- 얇은 `XxxViewModel`은 presenter를 viewModelScope에 연결만 한다.
- 상태만 받는 Compose `XxxScreen`이 화면을 그린다.
- 쓰기는 `GameService`·`ScheduleService`만 거친다.
- 취소·휴가 규칙(스펙 D13)은 core에 둔다.

**Tech Stack:** Kotlin 2.2.20, AGP 8.13.2, Room 2.8.3, Compose BOM 2025.10.01(material3 1.4.0), Robolectric 4.16.1, Roborazzi 1.51.0. 새 라이브러리는 없다.

## Global Constraints

**결정의 출처**
- 결정 원본은 다음 두 문서다.
  - 스펙 `docs/superpowers/specs/2026-11-25-chrono-quest-design.md`. 특히 D13, D14, 6.4, 7.2를 본다.
  - 인계 메모 `docs/superpowers/notes/2026-11-25-a1-carryover.md`.
- 사용자 결정(2026-11-27):
  - D13: 취소·휴가는 오늘 이후만 바꾼다. 기록이 있으면 막는다. 지난 날은 활동이 전혀 없을 때만 "휴가였어?"라고 묻는다.
  - D14: Compose 화면은 기능 확인용이다.
  - 펫·보스는 홈에 글자 카드로 보여준다.
  - 설정은 홈 오른쪽 위 톱니로 연다(A2b-3).

**디자인 규칙**
- 색·크기는 `ChronoTokens`만 쓴다. 생성 파일은 `Tokens.kt`다.
  - 화면 코드에 색 hex나 sp 값을 직접 쓰지 않는다.
  - 부품 고유 크기(dp)는 파일 위에 `private val`로 이름을 붙인다. A2b-1 `HomeScreen.kt`의 `EMBLEM_SIZE`와 같은 방식이다.
- **새 디자인 토큰을 만들지 않는다.** 필요해 보이면 구현을 멈추고 보고한다.
- 글자 크기는 72/48/32/24/22/18/14만 쓴다. `MaterialTheme.typography`가 이미 이 스케일에 맞춰져 있으니 typography 스타일만 쓴다.
- 제목·헤딩은 `Text1` 단색이다. 보라색은 티어 MASTER 말고는 쓰지 않는다.
- 색은 아래 쓰임만 쓴다.

| 쓰임 | 배경 | 글자·막대 |
|---|---|---|
| 상태 칩 | `Blue50` | `Blue700` |
| 주의 | `Yellow50` | `Gold700` |
| XP·펫 진행 막대 | 트랙 `Yellow50` | `Gold500` |
| 중립 | `Bg2` | `Text2` |
| 보스 HP 막대 | 트랙 `Blue50` | `Blue500` |

**문구**
- 화면 문구는 반말이다.
- enum 상수 이름을 화면에 그대로 보여주지 않는다. 기존 티어 이름(BRONZE 등) 표시는 예외다.

**구현 규칙**
- 앱 코드는 DAO로 직접 쓰지 않는다. 쓰기는 `GameService`·`ScheduleService`로 한다. 테스트에서 데이터를 준비할 때만 예외다.
- presenter의 DB·엔진 작업은 `compute` 디스패처(기본값 `Dispatchers.Default`)에서 돈다.
- 서비스는 생성자에서 받은 공급 함수로, 처음 쓸 때 만든다(`by lazy`).
- 새 Gradle 의존성을 추가하지 않는다. Gradle은 `--offline`으로 돌린다.

**확인과 커밋**
- 확인 명령(`android-core`에서 실행):
  - 테스트와 빌드: `./gradlew :rules:test :core:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --offline`
  - 화면 사진: `./gradlew :app:recordRoborazziDebug --offline`
- 커밋 트레일러는 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`이다.
- `.superpowers/`와 `local.properties`는 커밋하지 않는다.

**범위 밖 (A2b-3에서 한다)**
- 화면: 기록 탭, 설정 화면(홈 톱니)
- 백그라운드와 정책: 매일 00:05 워커, 연출을 "본 것"으로 치는 시점 문서화
- 출시 준비: 앱 아이콘, 릴리스 빌드, 폰 설치, 실기기 확인, 커버 화면 위젯
- 디자인 확인 항목(배지 대비, enum 문구, tertiary/error 색, 줄 간격)은 D14에 따라 Phase B에서 본다.

## File Structure

```
android-core/
  core/src/main/kotlin/com/chronoquest/core/
    domain/RecordPolicy.kt       # RejectReason 추가 (T1)
    domain/TemplateRules.kt      # ScheduleProblem 추가 (T1)
    domain/DayActivity.kt        # 새 파일: 기록·활동 판단 (T1)
    domain/Today.kt              # TodaySummary.vacation (T2)
    domain/Upcoming.kt           # 새 파일: 일정 탭 7일 묶음 (T2)
    data/Daos.kt                 # VacationDao.isVacation (T1)
    service/GameService.kt       # 취소 규칙, restoreQuest, 휴가일 출근 기록 막기 (T1) / today 휴가, representative() (T2)
    service/ScheduleService.kt   # setVacation 규칙, canMarkMissedVacation (T1) / upcoming, reschedule 지난 날짜 (T2)
  app/src/main/kotlin/com/chronoquest/app/
    ui/Messages.kt               # 새 파일: 거부·문제 문구 (T1, T4에서 FAILURE 추가)
    ui/ActionGate.kt             # 새 파일: 연타 막기 (T4)
    ui/SafeLaunch.kt             # 새 파일: ViewModel.launchSafely (T4)
    ui/Common.kt                 # 새 파일: Chip, LoadingText, MessageDialog (T4)
    home/CompanionCards.kt       # 새 파일: 펫·보스 상태 → 카드 문구 (T3)
    home/HomeUiState.kt, HomePresenter.kt, HomeViewModel.kt, HomeScreen.kt, HomeDialogs.kt  # (T1, T3, T4)
    schedule/ScheduleUiState.kt, SchedulePresenter.kt, ScheduleViewModel.kt  # (T5)
    schedule/ScheduleScreen.kt, ScheduleDialogs.kt  # (T6)
    codex/CodexUiState.kt, CodexLabels.kt, CodexPresenter.kt, CodexViewModel.kt, CodexScreen.kt  # (T7)
    MainActivity.kt              # (T3 홈 동작 연결, T8 탭 전체 연결)
  app/screenshots/               # 화면 사진 (T3, T4, T6, T7에서 추가)
```

---

### Task 1: 취소·되돌리기·휴가 규칙 (core)과 공통 문구

**Files:**
- Modify: `android-core/core/src/main/kotlin/com/chronoquest/core/domain/RecordPolicy.kt` (`RejectReason`)
- Modify: `android-core/core/src/main/kotlin/com/chronoquest/core/domain/TemplateRules.kt` (`ScheduleProblem`)
- Create: `android-core/core/src/main/kotlin/com/chronoquest/core/domain/DayActivity.kt`
- Modify: `android-core/core/src/main/kotlin/com/chronoquest/core/data/Daos.kt` (`VacationDao`)
- Modify: `android-core/core/src/main/kotlin/com/chronoquest/core/service/GameService.kt`
- Modify: `android-core/core/src/main/kotlin/com/chronoquest/core/service/ScheduleService.kt`
- Create: `android-core/app/src/main/kotlin/com/chronoquest/app/ui/Messages.kt`
- Modify: `android-core/app/src/main/kotlin/com/chronoquest/app/home/HomePresenter.kt` (문구를 `Messages`로 옮김)
- Test:
  - Create `android-core/core/src/test/kotlin/com/chronoquest/core/domain/DayActivityTest.kt`
  - Modify `core/.../service/GameServiceRecordTest.kt`, `core/.../service/GameServiceViewTest.kt`, `core/.../service/ScheduleServiceTest.kt`
  - Modify `app/.../home/HomePresenterTest.kt`

**Interfaces:**
- Consumes: 기존 `GameService`, `ScheduleService`, `QuestRecordEntity`, `DayRecordEntity`, `Clock.today()`(`core/service/Clocks.kt`)
- Produces:
  - `RejectReason`에 `HAS_RECORD`, `PAST_DATE`, `ON_VACATION`, `NOT_CANCELED`
  - `ScheduleProblem`에 `PAST_DATE`, `DAY_HAS_RECORD`
  - `object DayActivity { fun hasRecord(record: QuestRecordEntity?): Boolean; fun hasDayRecord(day: DayRecordEntity?): Boolean }`
  - `VacationDao.isVacation(date: LocalDate): Boolean`
  - `GameService.cancelQuest(questId: String): ActionResult`: 기록이 있으면 `Rejected(HAS_RECORD)`
  - `GameService.restoreQuest(questId: String): ActionResult`
  - `ScheduleService.setVacation(date: LocalDate, on: Boolean): ScheduleResult`: 반환형이 `Unit`에서 바뀐다
  - `ScheduleService.canMarkMissedVacation(date: LocalDate): Boolean`
  - `com.chronoquest.app.ui.Messages`: `const val NOT_FOUND`, `fun reject(reason: RejectReason): String`, `fun problem(problems: List<ScheduleProblem>): String`

- [ ] **Step 1: DayActivity 테스트 작성**

`android-core/core/src/test/kotlin/com/chronoquest/core/domain/DayActivityTest.kt`:

```kotlin
package com.chronoquest.core.domain

import com.chronoquest.core.d
import com.chronoquest.core.data.DayRecordEntity
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.core.dt
import com.chronoquest.rules.model.Resolution
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class DayActivityTest {
    @Test
    fun `출발·도착·기록 없음 정리 중 하나라도 있으면 기록이 있다`() {
        assertFalse(DayActivity.hasRecord(null))
        assertFalse(DayActivity.hasRecord(QuestRecordEntity(questId = "q")))
        assertTrue(DayActivity.hasRecord(QuestRecordEntity(questId = "q", departedAt = dt("2026-11-30", "08:20"))))
        assertTrue(DayActivity.hasRecord(QuestRecordEntity(questId = "q", arrivedAt = dt("2026-11-30", "08:55"), safe = true)))
        assertTrue(DayActivity.hasRecord(QuestRecordEntity(questId = "q", resolution = Resolution.NO_RECORD)))
    }

    @Test
    fun `알람만 울린 날은 활동한 날이 아니다`() {
        assertFalse(DayActivity.hasDayRecord(null))
        assertFalse(DayActivity.hasDayRecord(DayRecordEntity(date = d("2026-11-30"), alarmRangAt = dt("2026-11-30", "07:00"))))
        assertTrue(DayActivity.hasDayRecord(DayRecordEntity(date = d("2026-11-30"), selfControl = false)))
        assertTrue(DayActivity.hasDayRecord(DayRecordEntity(date = d("2026-11-30"), quizSolvedAt = dt("2026-11-30", "07:03"))))
        assertTrue(DayActivity.hasDayRecord(DayRecordEntity(date = d("2026-11-30"), sleptEnough = true)))
        assertTrue(DayActivity.hasDayRecord(DayRecordEntity(date = d("2026-11-30"), recheckPassed = false)))
    }
}
```

- [ ] **Step 2: GameServiceRecordTest 고치기**

`android-core/core/src/test/kotlin/com/chronoquest/core/service/GameServiceRecordTest.kt`에서 다음 두 가지를 한다.
- 기존 테스트 `` `취소하면 판정에서 빠지고 XP도 다시 계산된다` ``를 통째로 지운다. 기록 있는 일정을 취소해 XP를 빼던 동작은 D13으로 막히기 때문이다.
- 그 자리에 아래 4개를 넣는다.

import는 아래 목록 중 없는 것만 추가한다.
- `com.chronoquest.core.d`
- `com.chronoquest.core.data.VacationEntity`
- `com.chronoquest.core.domain.RejectReason`
- `com.chronoquest.rules.model.QuestStatus`
- `kotlin.test.assertNull`

```kotlin
    @Test
    fun `기록이 있는 일정은 취소할 수 없다`() = runTest {
        service("08:55").arrive(work.id, safe = true)
        assertEquals(ActionResult.Rejected(RejectReason.HAS_RECORD), service("10:00").cancelQuest(work.id))
        assertEquals(QuestStatus.SCHEDULED, db.questDao().get(work.id)?.status)
    }

    @Test
    fun `기록 없는 일정을 취소하면 판정에서 빠지고 되돌리면 돌아온다`() = runTest {
        val svc = service("10:00")
        assertTrue(work.id in svc.state().pendingQuestIds)
        done(svc.cancelQuest(work.id))
        assertFalse(work.id in svc.state().pendingQuestIds)
        assertEquals(ActionResult.Rejected(RejectReason.QUEST_CANCELED), svc.cancelQuest(work.id))
        done(svc.restoreQuest(work.id))
        assertEquals(QuestStatus.SCHEDULED, db.questDao().get(work.id)?.status)
        assertTrue(work.id in svc.state().pendingQuestIds)
        assertEquals(ActionResult.Rejected(RejectReason.NOT_CANCELED), svc.restoreQuest(work.id))
        assertEquals(ActionResult.NotFound, svc.restoreQuest("없는-일정"))
    }

    @Test
    fun `지난 날짜의 취소는 되돌릴 수 없다`() = runTest {
        done(service("10:00").cancelQuest(work.id))
        val nextDay = GameService(db, clockAt("2026-12-01", "08:00"))
        assertEquals(ActionResult.Rejected(RejectReason.PAST_DATE), nextDay.restoreQuest(work.id))
        assertEquals(QuestStatus.CANCELED, db.questDao().get(work.id)?.status)
    }

    @Test
    fun `휴가인 날의 출근은 기록하지 않지만 다른 일정은 기록한다`() = runTest {
        db.vacationDao().add(VacationEntity(d("2026-11-30")))
        assertEquals(ActionResult.Rejected(RejectReason.ON_VACATION), service("08:25").depart(work.id))
        assertNull(db.recordDao().get(work.id))
        assertTrue(service("18:25").depart(other.id) is ActionResult.Done)
    }
```

- [ ] **Step 3: GameServiceViewTest 고치기**

`android-core/core/src/test/kotlin/com/chronoquest/core/service/GameServiceViewTest.kt`의 대표 칭호 테스트 69번째 줄 근처에 `svc.cancelQuest(work.id)`가 있다. 이 테스트는 칭호를 잃는 상황을 만들려고 취소를 썼는데, 기록 있는 일정은 이제 취소할 수 없다. 그래서 도착 기록을 지우는 수정으로 바꾼다.

```kotlin
        svc.editRecord(work.id, departedAt = null, arrivedAt = null, safe = null)
```

- [ ] **Step 4: ScheduleServiceTest에 휴가 규칙 테스트 추가**

`android-core/core/src/test/kotlin/com/chronoquest/core/service/ScheduleServiceTest.kt`의 클래스 끝에 아래 2개를 추가한다.

import는 아래 목록 중 없는 것만 추가한다.
- `com.chronoquest.core.data.DayRecordEntity`
- `com.chronoquest.core.questEntity`
- `kotlin.test.assertFalse`

```kotlin
    @Test
    fun `지난 휴가는 끌 수 없고 기록이 있는 날은 휴가로 켤 수 없다`() = runTest {
        val svc = service("2026-11-27", "10:00")
        db.questDao().insertAll(listOf(questEntity("2026-11-26"), questEntity("2026-11-27")))
        db.recordDao().upsert(QuestRecordEntity(questId = "2026-11-27-WORK-09:00", departedAt = dt("2026-11-27", "08:20")))
        assertEquals(ScheduleResult.Rejected(listOf(ScheduleProblem.DAY_HAS_RECORD)), svc.setVacation(d("2026-11-27"), true))
        // 지난 날이지만 활동이 전혀 없으면 깜빡한 휴가로 넣을 수 있다.
        assertEquals(ScheduleResult.Saved("2026-11-26"), svc.setVacation(d("2026-11-26"), true))
        assertEquals(ScheduleResult.Rejected(listOf(ScheduleProblem.PAST_DATE)), svc.setVacation(d("2026-11-26"), false))
        assertEquals(ScheduleResult.Rejected(listOf(ScheduleProblem.BEFORE_SEASON)), svc.setVacation(d("2026-11-24"), true))
        assertEquals(listOf(d("2026-11-26")), svc.vacations())
    }

    @Test
    fun `깜빡한 휴가는 활동이 전혀 없는 지난 날만 묻는다`() = runTest {
        val svc = service("2026-11-28", "10:00")
        db.questDao().insertAll(listOf(questEntity("2026-11-26"), questEntity("2026-11-27"), questEntity("2026-11-28")))
        db.dayDao().upsert(DayRecordEntity(date = d("2026-11-27"), selfControl = true))
        assertTrue(svc.canMarkMissedVacation(d("2026-11-26")))
        assertFalse(svc.canMarkMissedVacation(d("2026-11-27")))   // 행동통제 답이 있다
        assertFalse(svc.canMarkMissedVacation(d("2026-11-28")))   // 오늘은 일정 탭에서 바꾼다
        assertFalse(svc.canMarkMissedVacation(d("2026-11-24")))   // 시즌 시작 전
        svc.setVacation(d("2026-11-26"), true)
        assertFalse(svc.canMarkMissedVacation(d("2026-11-26")))   // 이미 휴가
    }
```

기존 테스트 `` `휴가를 켜고 끈다` ``는 고치지 않는다. 미래 날짜라 새 규칙에서도 통과해야 한다.

- [ ] **Step 5: 테스트가 실패하는지 확인 (RED)**

Run: `cd android-core && ./gradlew :core:testDebugUnitTest --offline`

Expected: 컴파일 실패. 아직 없는 것을 참조하기 때문이다.
- `DayActivity`
- `RejectReason.HAS_RECORD` 등 새 거부 사유
- `restoreQuest`, `canMarkMissedVacation`
- `ScheduleProblem.DAY_HAS_RECORD`

- [ ] **Step 6: 거부 사유와 일정 문제 추가**

`RecordPolicy.kt`의 `RejectReason`을 아래로 바꾼다.

```kotlin
enum class RejectReason {
    QUEST_CANCELED,
    FUTURE_TIME,
    ALREADY_RECORDED,
    ARRIVAL_BEFORE_DEPARTURE,
    SAFE_WITHOUT_ARRIVAL,
    SAFE_REQUIRED,
    WRONG_DAY,
    NOT_DUE_YET,
    /** 기록(출발·도착·기록 없음 정리)이 있어서 취소할 수 없다(스펙 D13). */
    HAS_RECORD,
    /** 지난 날짜라 바꿀 수 없다(스펙 D13). */
    PAST_DATE,
    /** 휴가인 날의 출근 일정은 기록하지 않는다. 엔진도 판정에서 뺀다. */
    ON_VACATION,
    /** 취소한 일정이 아니라서 되돌릴 게 없다. */
    NOT_CANCELED,
}
```

`TemplateRules.kt`의 `ScheduleProblem`을 아래로 바꾼다.

```kotlin
enum class ScheduleProblem {
    BLANK_TITLE,
    NO_DAYS,
    DEPART_AFTER_DUE,
    DUPLICATE,
    BEFORE_SEASON,
    NOT_FOUND,
    HAS_RECORD,
    /** 지난 날짜라 바꿀 수 없다(스펙 D13). */
    PAST_DATE,
    /** 그날 기록이 있어서 휴가로 바꿀 수 없다(스펙 D13). */
    DAY_HAS_RECORD,
}
```

- [ ] **Step 7: DayActivity 만들기**

`android-core/core/src/main/kotlin/com/chronoquest/core/domain/DayActivity.kt`:

```kotlin
package com.chronoquest.core.domain

import com.chronoquest.core.data.DayRecordEntity
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.rules.model.Resolution

/**
 * 사용자가 남긴 기록이 있는지 판단한다(스펙 D13, 2026-11-27 사용자 결정).
 * - 기록이 있는 일정은 취소할 수 없다.
 * - 기록이 있는 날은 휴가로 바꿀 수 없다.
 * - 지난 날은 활동이 전혀 없을 때만 휴가로 넣는다.
 */
object DayActivity {
    /** 출발·도착·기록 없음 정리 중 하나라도 있으면 기록이 있는 일정이다. */
    fun hasRecord(record: QuestRecordEntity?): Boolean =
        record != null &&
            (record.departedAt != null || record.arrivedAt != null || record.resolution != Resolution.NONE)

    /**
     * 퀴즈·기상 확인·행동통제 답이 하나라도 있으면 활동한 날이다.
     * 알람이 울린 것은 자동으로 일어난 일이라서 활동으로 치지 않는다.
     */
    fun hasDayRecord(day: DayRecordEntity?): Boolean =
        day != null &&
            (day.quizSolvedAt != null || day.sleptEnough != null || day.recheckPassed != null || day.selfControl != null)
}
```

- [ ] **Step 8: VacationDao에 조회 추가**

`Daos.kt`의 `VacationDao` 안, `remove` 아래에 추가한다.

```kotlin
    @Query("SELECT EXISTS(SELECT 1 FROM vacation WHERE date = :date)")
    suspend fun isVacation(date: LocalDate): Boolean
```

- [ ] **Step 9: GameService 취소·되돌리기·휴가 막기**

`GameService.kt`에서 import를 추가한다.
- `com.chronoquest.core.domain.DayActivity`
- `com.chronoquest.rules.model.QuestKind`

`cancelQuest`를 아래로 바꾸고, 바로 아래에 `restoreQuest`를 추가한다.

```kotlin
    /**
     * 불가피한 취소(스펙 6.4, D13). 판정에서 빠진다.
     * 기록(출발·도착·기록 없음 정리)이 있는 일정은 취소할 수 없다. 실수로 XP를 날리지 않게 하려는 것이다.
     * 기록 없는 지난 일정은 홈 "정리 필요"에서 취소할 수 있다.
     */
    suspend fun cancelQuest(questId: String): ActionResult = write { now ->
        val quest = db.questDao().get(questId) ?: return@write ActionResult.NotFound
        if (quest.status == QuestStatus.CANCELED) return@write ActionResult.Rejected(RejectReason.QUEST_CANCELED)
        if (DayActivity.hasRecord(db.recordDao().get(questId))) {
            return@write ActionResult.Rejected(RejectReason.HAS_RECORD)
        }
        applyChange(now) { db.questDao().update(quest.copy(status = QuestStatus.CANCELED)) }
    }

    /** 취소 되돌리기. 오늘과 그 뒤 일정만 된다(스펙 D13). */
    suspend fun restoreQuest(questId: String): ActionResult = write { now ->
        val quest = db.questDao().get(questId) ?: return@write ActionResult.NotFound
        if (quest.status != QuestStatus.CANCELED) return@write ActionResult.Rejected(RejectReason.NOT_CANCELED)
        if (quest.date.isBefore(now.toLocalDate())) return@write ActionResult.Rejected(RejectReason.PAST_DATE)
        applyChange(now) { db.questDao().update(quest.copy(status = QuestStatus.SCHEDULED)) }
    }
```

`recordAction`의 첫 줄 다음에 휴가 확인을 넣는다. 함수 전체는 아래와 같다.

```kotlin
    private suspend fun recordAction(
        questId: String,
        now: LocalDateTime,
        decide: (QuestEntity, QuestRecordEntity?) -> RecordResult,
    ): ActionResult {
        val quest = db.questDao().get(questId) ?: return ActionResult.NotFound
        // 엔진은 휴가일의 출근을 판정에서 빼므로, 그날 출근은 기록 자체를 받지 않는다.
        if (quest.kind == QuestKind.WORK && db.vacationDao().isVacation(quest.date)) {
            return ActionResult.Rejected(RejectReason.ON_VACATION)
        }
        return when (val result = decide(quest, db.recordDao().get(questId))) {
            is RecordResult.Rejected -> ActionResult.Rejected(result.reason)
            is RecordResult.NeedsEarlyConfirmation -> ActionResult.ConfirmEarly(result.minutesEarly)
            is RecordResult.Ok -> applyChange(now) { db.recordDao().upsert(result.record) }
        }
    }
```

- [ ] **Step 10: ScheduleService 휴가 규칙**

`ScheduleService.kt`에서 import를 추가한다.
- `com.chronoquest.core.domain.DayActivity`

`setVacation`을 아래로 바꾸고, 그 아래에 `canMarkMissedVacation`와 `hasActivity`를 추가한다. `hasActivity`는 `dropFutureUnrecorded` 근처의 private 영역에 둔다.

```kotlin
    /**
     * 휴가 켜기·끄기 (스펙 6.4, D13).
     * - 오늘과 그 뒤 날짜는 켜고 끌 수 있다. 단, 그날 기록이 있으면 켤 수 없다(그날 출근 XP가 빠지므로).
     * - 지난 날짜는 그날 활동이 전혀 없을 때만 켤 수 있다(깜빡한 휴가). 끌 수는 없다.
     * - 시즌 시작 전 날짜는 안 된다.
     */
    suspend fun setVacation(date: LocalDate, on: Boolean): ScheduleResult = db.withTransaction {
        val problem = when {
            date.isBefore(ledgers.settings().seasonStart) -> ScheduleProblem.BEFORE_SEASON
            !on && date.isBefore(clock.today()) -> ScheduleProblem.PAST_DATE
            on && hasActivity(date) -> ScheduleProblem.DAY_HAS_RECORD
            else -> null
        }
        if (problem != null) return@withTransaction ScheduleResult.Rejected(listOf(problem))
        if (on) db.vacationDao().add(VacationEntity(date)) else db.vacationDao().remove(date)
        ScheduleResult.Saved(date.toString())
    }

    /** 홈 "정리 필요"의 [휴가였어] 대상인지 본다. 지난 날이고, 아직 휴가가 아니고, 그날 활동이 전혀 없어야 한다. */
    suspend fun canMarkMissedVacation(date: LocalDate): Boolean = db.withTransaction {
        date.isBefore(clock.today()) &&
            !date.isBefore(ledgers.settings().seasonStart) &&
            !db.vacationDao().isVacation(date) &&
            !hasActivity(date)
    }
```

```kotlin
    /** 그날 일정 기록이나 하루 기록이 하나라도 있는지 본다. */
    private suspend fun hasActivity(date: LocalDate): Boolean =
        DayActivity.hasDayRecord(db.dayDao().get(date)) ||
            db.questDao().onDate(date).any { DayActivity.hasRecord(db.recordDao().get(it.id)) }
```

`clock.today()`는 같은 패키지의 `Clocks.kt` 확장 함수라 import가 필요 없다.

- [ ] **Step 11: core 테스트 통과 확인**

Run: `cd android-core && ./gradlew :core:testDebugUnitTest --offline`

Expected: BUILD SUCCESSFUL, 실패 0.

`:app` 모듈은 아직 컴파일이 깨져 있을 수 있다. `HomePresenter`의 `when`이 새 enum 값을 다 다루지 않기 때문이고, 다음 단계에서 고친다.

- [ ] **Step 12: 공통 문구 Messages 만들기**

`android-core/app/src/main/kotlin/com/chronoquest/app/ui/Messages.kt`:

```kotlin
package com.chronoquest.app.ui

import com.chronoquest.core.domain.RejectReason
import com.chronoquest.core.domain.ScheduleProblem

/** 화면 공통 안내 문구(반말). 거부 사유와 일정 문제를 사람이 읽는 말로 바꾼다. */
object Messages {
    const val NOT_FOUND = "일정을 찾을 수 없어."

    fun reject(reason: RejectReason): String = when (reason) {
        RejectReason.QUEST_CANCELED -> "취소한 일정이야."
        RejectReason.FUTURE_TIME -> "아직 오지 않은 시각은 기록할 수 없어."
        RejectReason.ALREADY_RECORDED -> "이미 기록했어."
        RejectReason.ARRIVAL_BEFORE_DEPARTURE -> "도착이 출발보다 빠를 수는 없어."
        RejectReason.SAFE_WITHOUT_ARRIVAL -> "도착 시각 없이 안전 여부만 넣을 수는 없어."
        RejectReason.SAFE_REQUIRED -> "안전하게 왔는지 알려줘."
        RejectReason.WRONG_DAY -> "그 일정 날짜에만 기록할 수 있어."
        RejectReason.NOT_DUE_YET -> "아직 예정 시각이 안 됐어."
        RejectReason.HAS_RECORD -> "기록이 있는 일정은 취소할 수 없어."
        RejectReason.PAST_DATE -> "지난 날짜는 바꿀 수 없어."
        RejectReason.ON_VACATION -> "휴가인 날은 출근을 기록하지 않아."
        RejectReason.NOT_CANCELED -> "취소한 일정이 아니야."
    }

    fun problem(problems: List<ScheduleProblem>): String = when (problems.firstOrNull()) {
        ScheduleProblem.DEPART_AFTER_DUE -> "출발 시각이 도착 시각보다 늦어."
        ScheduleProblem.DUPLICATE -> "같은 시각에 같은 종류 일정이 이미 있어."
        ScheduleProblem.HAS_RECORD -> "이미 기록이 있는 일정은 시각을 바꿀 수 없어."
        ScheduleProblem.NOT_FOUND -> NOT_FOUND
        ScheduleProblem.BLANK_TITLE -> "일정 이름이 비어 있어."
        ScheduleProblem.BEFORE_SEASON -> "시즌 시작 전 날짜야."
        ScheduleProblem.NO_DAYS -> "요일을 하나 이상 골라줘."
        ScheduleProblem.PAST_DATE -> "지난 날짜는 바꿀 수 없어."
        ScheduleProblem.DAY_HAS_RECORD -> "그날 기록이 있어서 휴가로 바꿀 수 없어."
        null -> "일정을 바꾸지 못했어."
    }
}
```

- [ ] **Step 13: HomePresenter가 Messages를 쓰게 바꾸기**

`HomePresenter.kt`에서 아래를 한다.
- `import com.chronoquest.app.ui.Messages`를 추가한다.
- `companion object`에서 `NOT_FOUND_TEXT`, `rejectText`, `problemText`를 지운다. `TIME`, `CLEANUP_WHEN`은 남긴다.
- 파일 안의 호출을 모두 바꾼다.
  - `NOT_FOUND_TEXT` → `Messages.NOT_FOUND`
  - `rejectText(` → `Messages.reject(`
  - `problemText(` → `Messages.problem(`
- 쓰이지 않게 된 import(`RejectReason`, `ScheduleProblem`)를 지운다.

- [ ] **Step 14: HomePresenterTest 고치기**

`HomePresenterTest.kt`의 `` `XP가 줄면 음수로 알리고 변화가 없으면 알리지 않는다` ``를 통째로 아래로 바꾼다. 기록 있는 일정의 취소는 이제 막히므로, 화면에서 음수 XP가 나오는 길은 A2b-3 기록 편집뿐이다. 음수 알림 문구는 `SettlementNotifierTest`가 이미 다룬다.

```kotlin
    @Test
    fun `기록이 있는 일정은 취소하지 못하고 XP 변화가 없으면 알리지 않는다`() = runTest {
        clock.now = dt("2026-11-30", "08:55")
        presenter.onSafetyAnswered(work.id, true)
        val before = notified.size
        presenter.onCleanupCancel(work.id)
        assertEquals("기록이 있는 일정은 취소할 수 없어.", state.message)
        assertEquals(CardStatus.ON_TIME, card(work.id).status)
        presenter.dismissMessage()
        presenter.onCleanupCancel(other.id)
        assertEquals(before, notified.size)
        assertEquals(CardStatus.CANCELED, card(other.id).status)
    }
```

- [ ] **Step 15: 전체 확인**

Run: `cd android-core && ./gradlew :rules:test :core:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --offline`

Expected: BUILD SUCCESSFUL, 실패 0.

- [ ] **Step 16: 커밋**

```bash
git add android-core/core android-core/app/src/main/kotlin/com/chronoquest/app/ui/Messages.kt android-core/app/src/main/kotlin/com/chronoquest/app/home/HomePresenter.kt android-core/app/src/test/kotlin/com/chronoquest/app/home/HomePresenterTest.kt
git commit -m "feat(core): 취소·휴가는 오늘 이후만, 기록 있으면 막기, 깜빡한 휴가 판단" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: 일정 탭·홈에서 읽을 것 (core)

**Files:**
- Modify: `android-core/core/src/main/kotlin/com/chronoquest/core/domain/Today.kt`
- Create: `android-core/core/src/main/kotlin/com/chronoquest/core/domain/Upcoming.kt`
- Modify: `android-core/core/src/main/kotlin/com/chronoquest/core/service/GameService.kt` (`today`, `representative`, `representativeTitle`)
- Modify: `android-core/core/src/main/kotlin/com/chronoquest/core/service/ScheduleService.kt` (`upcoming`, `reschedule`)
- Test:
  - Create `android-core/core/src/test/kotlin/com/chronoquest/core/domain/UpcomingTest.kt`
  - Modify `core/.../service/GameServiceViewTest.kt`, `core/.../service/ScheduleServiceTest.kt`

**Interfaces:**
- Consumes: `DayActivity` (Task 1), `QuestGenerator.HORIZON_DAYS` (= 7), `TitleChoice`(`core/domain/RepresentativeTitle.kt`)
- Produces:
  - `TodaySummary.vacation: Boolean`
  - `Today.summarize(date, state, quests, records, days, vacations: Set<LocalDate> = emptySet())`
  - `data class UpcomingQuest(val quest: QuestEntity, val hasRecord: Boolean)`
  - `data class UpcomingDay(val date: LocalDate, val vacation: Boolean, val hasActivity: Boolean, val quests: List<UpcomingQuest>)`
  - `object Upcoming { fun build(from: LocalDate, days: Int, quests: List<QuestEntity>, records: List<QuestRecordEntity>, dayRecords: List<DayRecordEntity>, vacations: Set<LocalDate>): List<UpcomingDay> }`
  - `ScheduleService.upcoming(days: Int = QuestGenerator.HORIZON_DAYS): List<UpcomingDay>`
  - `ScheduleService.reschedule`: 지난 날짜 일정이면 `Rejected([PAST_DATE])`
  - `GameService.representative(): TitleChoice`. `representativeTitle(): String`은 그대로 남는다.

- [ ] **Step 1: Upcoming 테스트 작성**

`android-core/core/src/test/kotlin/com/chronoquest/core/domain/UpcomingTest.kt`:

```kotlin
package com.chronoquest.core.domain

import com.chronoquest.core.d
import com.chronoquest.core.data.DayRecordEntity
import com.chronoquest.core.data.QuestRecordEntity
import com.chronoquest.core.dt
import com.chronoquest.core.questEntity
import com.chronoquest.rules.model.QuestKind
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class UpcomingTest {
    private val work = questEntity("2026-11-30")
    private val other = questEntity("2026-11-30", kind = QuestKind.OTHER, due = "19:00", depart = "18:30", title = "약속")
    private val next = questEntity("2026-12-01")
    private val old = questEntity("2026-11-29")

    @Test
    fun `오늘부터 7일을 날짜별로 묶고 기록과 휴가를 표시한다`() {
        val days = Upcoming.build(
            from = d("2026-11-30"),
            days = 7,
            quests = listOf(other, next, work, old),
            records = listOf(QuestRecordEntity(questId = work.id, departedAt = dt("2026-11-30", "08:20"))),
            dayRecords = emptyList(),
            vacations = setOf(d("2026-12-01")),
        )
        assertEquals(7, days.size)
        assertEquals(d("2026-11-30"), days[0].date)
        assertEquals(d("2026-12-06"), days[6].date)
        assertEquals(listOf(work.id, other.id), days[0].quests.map { it.quest.id })
        assertEquals(listOf(true, false), days[0].quests.map { it.hasRecord })
        assertTrue(days[0].hasActivity)
        assertFalse(days[0].vacation)
        assertTrue(days[1].vacation)
        assertFalse(days[1].hasActivity)
        assertTrue(days[6].quests.isEmpty())
        assertTrue(days.none { day -> day.quests.any { it.quest.id == old.id } })
    }

    @Test
    fun `하루 기록만 있어도 활동한 날이다`() {
        val days = Upcoming.build(
            from = d("2026-11-30"),
            days = 1,
            quests = emptyList(),
            records = emptyList(),
            dayRecords = listOf(DayRecordEntity(date = d("2026-11-30"), selfControl = true)),
            vacations = emptySet(),
        )
        assertTrue(days.single().hasActivity)
    }
}
```

- [ ] **Step 2: 서비스 테스트 추가**

`GameServiceViewTest.kt` 클래스 끝에 추가한다. import 중 없는 것만 넣는다.
- `com.chronoquest.core.d`
- `com.chronoquest.core.data.VacationEntity`
- `com.chronoquest.core.domain.TitleChoice`

```kotlin
    @Test
    fun `오늘이 휴가면 오늘 요약에 표시한다`() = runTest {
        assertFalse(service("08:00").today().vacation)
        db.vacationDao().add(VacationEntity(d("2026-11-30")))
        assertTrue(service("08:00").today().vacation)
    }

    @Test
    fun `대표 칭호는 ID와 이름을 같이 준다`() = runTest {
        assertEquals(TitleChoice(null, "미각성자"), service("08:00").representative())
        service("08:55").arrive(work.id, safe = true)
        assertEquals(TitleChoice(21, "운명의 첫걸음"), service("09:00").representative())
    }
```

`ScheduleServiceTest.kt` 클래스 끝에 추가한다. 필요하면 `java.time.LocalTime`을 import한다.

```kotlin
    @Test
    fun `지난 날짜 일정은 시각을 바꿀 수 없다`() = runTest {
        db.questDao().insertAll(listOf(questEntity("2026-11-26")))
        val result = service("2026-11-27", "10:00")
            .reschedule("2026-11-26-WORK-09:00", LocalTime.of(10, 0), LocalTime.of(9, 30))
        assertEquals(ScheduleResult.Rejected(listOf(ScheduleProblem.PAST_DATE)), result)
    }

    @Test
    fun `일정 탭은 오늘부터 7일을 휴가와 함께 보여준다`() = runTest {
        val svc = service("2026-11-25", "07:00")
        svc.saveTemplate(weekdayTemplate())
        svc.setVacation(d("2026-11-27"), true)
        val days = svc.upcoming()
        assertEquals(7, days.size)
        assertEquals(d("2026-11-25"), days.first().date)
        assertEquals(listOf("2026-11-25-WORK-09:00"), days.first().quests.map { it.quest.id })
        assertTrue(days[2].vacation)
        assertTrue(days[3].quests.isEmpty())   // 11/28 토요일
        assertTrue(days[4].quests.isEmpty())   // 11/29 일요일
    }
```

- [ ] **Step 3: 실패 확인 (RED)**

Run: `cd android-core && ./gradlew :core:testDebugUnitTest --offline`

Expected: 컴파일 실패. `Upcoming`, `TodaySummary.vacation`, `representative`, `upcoming`이 아직 없다.

- [ ] **Step 4: Today에 휴가 표시 추가**

`Today.kt`를 아래로 바꾼다.

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
    /** 오늘이 휴가면 출근 일정은 판정에서 빠진다. 다른 일정은 그대로 판정한다. */
    val vacation: Boolean = false,
)

/** 홈 "오늘의 퀘스트"용 요약. 취소·휴가로 판정에서 빠진 일정은 outcome이 null이다. */
object Today {
    fun summarize(
        date: LocalDate,
        state: GameState,
        quests: List<QuestEntity>,
        records: List<QuestRecordEntity>,
        days: List<DayRecordEntity>,
        vacations: Set<LocalDate> = emptySet(),
    ): TodaySummary {
        val recordByQuest = records.associateBy { it.questId }
        val items = quests
            .filter { it.date == date }
            .sortedWith(compareBy<QuestEntity>({ it.dueAt }, { it.id }))
            .map { TodayQuest(it, recordByQuest[it.id], state.outcomes[it.id]) }
        return TodaySummary(
            date = date,
            quests = items,
            day = days.firstOrNull { it.date == date },
            vacation = date in vacations,
        )
    }
}
```

- [ ] **Step 5: Upcoming 만들기**

`android-core/core/src/main/kotlin/com/chronoquest/core/domain/Upcoming.kt`:

```kotlin
package com.chronoquest.core.domain

import com.chronoquest.core.data.DayRecordEntity
import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.QuestRecordEntity
import java.time.LocalDate

/** 일정 탭 "다가오는 일정"의 한 줄. */
data class UpcomingQuest(val quest: QuestEntity, val hasRecord: Boolean)

/** 일정 탭의 하루. [hasActivity]면 그날은 휴가로 바꿀 수 없다(스펙 D13). */
data class UpcomingDay(
    val date: LocalDate,
    val vacation: Boolean,
    val hasActivity: Boolean,
    val quests: List<UpcomingQuest>,
)

/** 오늘부터 [build]의 days일 동안 일정을 날짜별로 묶는다. 일정이 없는 날도 넣는다(휴가 지정용). */
object Upcoming {
    fun build(
        from: LocalDate,
        days: Int,
        quests: List<QuestEntity>,
        records: List<QuestRecordEntity>,
        dayRecords: List<DayRecordEntity>,
        vacations: Set<LocalDate>,
    ): List<UpcomingDay> {
        val recordByQuest = records.associateBy { it.questId }
        val dayByDate = dayRecords.associateBy { it.date }
        val questsByDate = quests.groupBy { it.date }
        return (0 until days).map { offset ->
            val date = from.plusDays(offset.toLong())
            val items = questsByDate[date].orEmpty()
                .sortedWith(compareBy<QuestEntity>({ it.dueAt }, { it.id }))
                .map { UpcomingQuest(it, DayActivity.hasRecord(recordByQuest[it.id])) }
            UpcomingDay(
                date = date,
                vacation = date in vacations,
                hasActivity = DayActivity.hasDayRecord(dayByDate[date]) || items.any { it.hasRecord },
                quests = items,
            )
        }
    }
}
```

- [ ] **Step 6: ScheduleService에 upcoming 추가, reschedule에 지난 날짜 확인 추가**

`ScheduleService.kt`에서 import를 추가한다.
- `com.chronoquest.core.domain.Upcoming`
- `com.chronoquest.core.domain.UpcomingDay`

`templates()` 아래에 추가한다.

```kotlin
    /** 일정 탭에 보여줄 것. 오늘부터 [days]일의 일정과 휴가다. */
    suspend fun upcoming(days: Int = QuestGenerator.HORIZON_DAYS): List<UpcomingDay> = db.withTransaction {
        Upcoming.build(
            from = clock.today(),
            days = days,
            quests = db.questDao().all(),
            records = db.recordDao().all(),
            dayRecords = db.dayDao().all(),
            vacations = db.vacationDao().all().mapTo(HashSet()) { it.date },
        )
    }
```

`reschedule`에서는 `NOT_FOUND`를 돌려주는 줄 바로 다음(기록 확인 전)에 넣는다. 날짜는 바뀌지 않으므로 시즌 확인은 필요 없다.

```kotlin
            if (quest.date.isBefore(clock.today())) {
                return@withTransaction ScheduleResult.Rejected(listOf(ScheduleProblem.PAST_DATE))
            }
```

KDoc 첫 문장 끝에 "지난 날짜 일정도 바꿀 수 없다."를 덧붙인다.

- [ ] **Step 7: GameService의 today에 휴가 반영, 대표 칭호를 잠금 없이 먼저 판단**

`GameService.kt`에서 import를 추가한다.
- `com.chronoquest.core.domain.TitleChoice`

`today()`를 아래로 바꾼다.

```kotlin
    /** 홈 "오늘의 퀘스트"용 요약. 오늘이 휴가인지도 같이 준다. */
    suspend fun today(): TodaySummary {
        val now = clock.nowLocal()
        val state = engine.compute(ledgers.loadLedger(), now)
        return Today.summarize(
            now.toLocalDate(),
            state,
            db.questDao().all(),
            db.recordDao().all(),
            db.dayDao().all(),
            db.vacationDao().all().mapTo(HashSet()) { it.date },
        )
    }
```

`representativeTitle()`을 아래 두 함수로 바꾼다. 인계 메모 A2b-2에 "1분마다 쓰기 잠금을 잡는다"는 문제가 있어서 고치는 것이다.

```kotlin
    /** 홈에 띄우는 대표 칭호 이름. */
    suspend fun representativeTitle(): String = representative().name

    /**
     * 대표 칭호(ID와 이름). 저장된 것이 없거나 더는 갖고 있지 않으면 가장 최근 칭호를 골라 저장한다.
     * 칭호가 하나도 없으면 저장값을 지운다. 새 칭호를 얻었다고 저절로 바꾸지는 않는다.
     * 홈이 1분마다 부르므로 먼저 잠금 없이 판단하고, 바꿔야 할 때만 쓰기 잠금 안에서 다시 판단해 저장한다.
     */
    suspend fun representative(): TitleChoice {
        val stored = ledgers.settings().representativeTitleId
        val peek = RepresentativeTitle.resolve(stored, state(), catalog)
        if (peek.titleId == stored) return peek
        return write { now ->
            val current = ledgers.settings().representativeTitleId
            val choice = RepresentativeTitle.resolve(current, stateAt(now), catalog)
            if (choice.titleId != current) {
                db.settingsDao().upsert(ledgers.settings().copy(representativeTitleId = choice.titleId))
            }
            choice
        }
    }
```

- [ ] **Step 8: 통과 확인**

Run: `cd android-core && ./gradlew :core:testDebugUnitTest :app:testDebugUnitTest --offline`

Expected: BUILD SUCCESSFUL, 실패 0. 기존 대표 칭호 테스트들도 그대로 통과해야 한다.

- [ ] **Step 9: 커밋**

```bash
git add android-core/core
git commit -m "feat(core): 일정 탭 7일 묶음, 오늘 휴가 표시, 지난 일정 시각 변경 막기, 대표 칭호 잠금 줄이기" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: 홈 펫·보스 카드, 휴가 표시, 정리 [휴가였어]

**Files:**
- Create: `android-core/app/src/main/kotlin/com/chronoquest/app/home/CompanionCards.kt`
- Modify (app `home/`): `HomeUiState.kt` (전체 교체), `HomePresenter.kt`, `HomeViewModel.kt`, `HomeScreen.kt`, `HomeDialogs.kt`
- Modify: `android-core/app/src/main/kotlin/com/chronoquest/app/MainActivity.kt` (`homeActions`)
- Test:
  - Create `android-core/app/src/test/kotlin/com/chronoquest/app/home/CompanionCardsTest.kt`
  - Modify `app/.../home/HomePresenterTest.kt`, `app/.../home/HomeScreenshotTest.kt`
- 화면 사진: `home_vacation_day.png`, `home_confirm_vacation.png`를 새로 찍는다. 기존 홈 사진도 다시 찍는다.

**Interfaces:**
- Consumes:
  - Task 1: `ScheduleService.setVacation`, `ScheduleService.canMarkMissedVacation`, `Messages`
  - Task 2: `TodaySummary.vacation`
  - 엔진: `PetState(stage, form, mood, nextEvolveXp, history)`, `PetStage.minXp`, `BossState(current: BossWeek?, history, defeats: Map<String, Int>)`, `BossWeek(weekStart, boss: BossDef, maxHp, damage, status)`
- Produces:
  - `CardStatus.VACATION`
  - `CleanupCard(id, title, whenLabel, date, due: LocalTime, canBeVacation: Boolean = false)`
  - `PetCard(stage, form, mood, xpToEvolve, evolveProgress)`, `CurrentBoss(name, hp, maxHp, status)`, `BossCard(current, defeatedKinds)`
  - `HomeUiState.pet`, `HomeUiState.boss`
  - `HomeDialog.AddArrival(questId, date, due)`, `HomeDialog.ConfirmVacation(date)`
  - `HomePresenter.onCleanupVacation(questId: String)`, `HomePresenter.onVacationConfirmed(dialog: HomeDialog.ConfirmVacation)`
  - `HomeActions.onCleanupVacation: (String) -> Unit`, `HomeActions.onVacationConfirmed: (HomeDialog.ConfirmVacation) -> Unit`
  - `object CompanionCards { fun pet(PetState, xp: Int): PetCard; fun boss(BossState): BossCard; fun stageLabel; fun formLabel; fun moodLabel; fun statusLabel }`

- [ ] **Step 1: CompanionCards 테스트 작성**

`android-core/app/src/test/kotlin/com/chronoquest/app/home/CompanionCardsTest.kt`:

```kotlin
package com.chronoquest.app.home

import com.chronoquest.app.d
import com.chronoquest.rules.engine.BossDef
import com.chronoquest.rules.engine.BossState
import com.chronoquest.rules.engine.BossStatus
import com.chronoquest.rules.engine.BossWeek
import com.chronoquest.rules.engine.PetForm
import com.chronoquest.rules.engine.PetMood
import com.chronoquest.rules.engine.PetStage
import com.chronoquest.rules.engine.PetState
import kotlin.test.assertEquals
import org.junit.Test

class CompanionCardsTest {
    @Test
    fun `펫 단계·모습·기분과 다음 진화까지 남은 XP`() {
        val baby = PetState(PetStage.BABY, PetForm.GOOD, PetMood.HAPPY, nextEvolveXp = 700, history = emptyList())
        assertEquals(
            PetCard(stage = "아기", form = "멋짐", mood = "신남", xpToEvolve = 580, evolveProgress = 20f / 600f),
            CompanionCards.pet(baby, 120),
        )
        val egg = PetState(PetStage.EGG, null, PetMood.NORMAL, nextEvolveXp = 100, history = emptyList())
        assertEquals(PetCard(stage = "알", form = null, mood = "보통", xpToEvolve = 100, evolveProgress = 0f), CompanionCards.pet(egg, 0))
        val adult = PetState(PetStage.ADULT, PetForm.UGLY, PetMood.SAD, nextEvolveXp = null, history = emptyList())
        assertEquals(
            PetCard(stage = "성체", form = "못생김", mood = "시무룩", xpToEvolve = null, evolveProgress = 1f),
            CompanionCards.pet(adult, 4000),
        )
        assertEquals("청소년", CompanionCards.stageLabel(PetStage.TEEN))
        assertEquals("평범", CompanionCards.formLabel(PetForm.NORMAL))
    }

    @Test
    fun `보스 남은 HP와 처치한 보스 종류 수`() {
        val week = BossWeek(d("2026-11-30"), BossDef("snooze_slime", "스누즈 슬라임"), maxHp = 500, damage = 620, status = BossStatus.ACTIVE)
        val card = CompanionCards.boss(BossState(current = week, history = emptyList(), defeats = mapOf("a" to 2, "b" to 0)))
        assertEquals(BossCard(CurrentBoss("스누즈 슬라임", hp = 0, maxHp = 500, status = "진행 중"), defeatedKinds = 1), card)
        assertEquals(BossCard(current = null, defeatedKinds = 0), CompanionCards.boss(BossState(null, emptyList(), emptyMap())))
        assertEquals(listOf("진행 중", "처치", "도망", "결과 대기"), BossStatus.entries.map(CompanionCards::statusLabel))
    }
}
```

- [ ] **Step 2: HomePresenterTest에 테스트 추가**

`HomePresenterTest.kt`에 import 중 없는 것을 추가한다.
- `com.chronoquest.core.data.QuestRecordEntity`
- `com.chronoquest.core.data.VacationEntity`

기존 테스트 `` `정리 필요 일정에 도착 시각을 넣는다` ``에서 `val dialog = state.dialog as HomeDialog.AddArrival` 바로 다음 줄에 추가한다.

```kotlin
        assertEquals(LocalTime.parse("09:00"), dialog.due)
```

클래스 끝에 아래 4개를 추가한다.

```kotlin
    @Test
    fun `펫과 이번 주 보스를 글자 카드로 보여준다`() = runTest {
        presenter.refresh()
        assertEquals(PetCard(stage = "알", form = null, mood = "보통", xpToEvolve = 100, evolveProgress = 0f), state.pet)
        assertEquals(CurrentBoss("스누즈 슬라임", hp = 100, maxHp = 100, status = "진행 중"), state.boss.current)
        assertEquals(0, state.boss.defeatedKinds)
        clock.now = dt("2026-11-30", "08:55")
        presenter.onSafetyAnswered(work.id, true)
        assertEquals("신남", state.pet.mood)
        assertEquals(80, state.pet.xpToEvolve)
        assertEquals(0, state.boss.current?.hp)
    }

    @Test
    fun `휴가인 날의 출근 카드는 휴가로 보이고 버튼이 없다`() = runTest {
        db.vacationDao().add(VacationEntity(d("2026-11-30")))
        presenter.refresh()
        assertEquals(CardStatus.VACATION, card(work.id).status)
        assertFalse(card(work.id).canDepart)
        assertFalse(card(work.id).canArrive)
        assertEquals(CardStatus.WAITING, card(other.id).status)
    }

    @Test
    fun `활동이 없는 지난 날의 출근 정리는 휴가였는지 묻고 휴가로 넣는다`() = runTest {
        val friday = questEntity("2026-11-27")
        db.questDao().insertAll(listOf(friday))
        presenter.refresh()
        assertTrue(state.cleanup.single { it.id == friday.id }.canBeVacation)
        presenter.onCleanupVacation(friday.id)
        assertEquals(HomeDialog.ConfirmVacation(d("2026-11-27")), state.dialog)
        presenter.onVacationConfirmed(HomeDialog.ConfirmVacation(d("2026-11-27")))
        assertNull(state.dialog)
        assertTrue(state.cleanup.none { it.id == friday.id })
    }

    @Test
    fun `그날 기록이 있으면 휴가였는지 묻지 않는다`() = runTest {
        val thuWork = questEntity("2026-11-26")
        val thuOther = questEntity("2026-11-26", kind = QuestKind.OTHER, due = "19:00", depart = "18:30", title = "약속")
        db.questDao().insertAll(listOf(thuWork, thuOther))
        db.recordDao().upsert(QuestRecordEntity(questId = thuOther.id, arrivedAt = dt("2026-11-26", "18:55"), safe = true))
        presenter.refresh()
        assertFalse(state.cleanup.single { it.id == thuWork.id }.canBeVacation)
        presenter.onCleanupVacation(thuWork.id)
        assertNull(state.dialog)
    }
```

`` `펫과 이번 주 보스를...` `` 테스트의 기대값은 스펙 5.8·5.9를 따른 것이다.
- 11/23 주에는 출근 일정이 없어서 보스 순서가 넘어가지 않는다. 그래서 11/30 주 보스는 로스터 첫 번째인 스누즈 슬라임이다.
- 최대 HP는 출근 1개 × 100이다.
- 출근 정시 도착 데미지는 100이다.

실제 엔진 값이 다르면 기대값을 바꾸지 않는다. 먼저 `rules/engine/Boss.kt`·`Pet.kt`에서 이유를 확인하고 보고한다.

- [ ] **Step 3: 실패 확인 (RED)**

Run: `cd android-core && ./gradlew :app:testDebugUnitTest --offline`

Expected: 컴파일 실패. `CompanionCards`, `PetCard`, `HomeDialog.ConfirmVacation` 등이 아직 없다.

- [ ] **Step 4: HomeUiState 전체 교체**

`android-core/app/src/main/kotlin/com/chronoquest/app/home/HomeUiState.kt`:

```kotlin
package com.chronoquest.app.home

import com.chronoquest.core.domain.Celebration
import com.chronoquest.rules.model.Tier
import java.time.LocalDate
import java.time.LocalTime

enum class CardStatus { WAITING, DEPARTED, EARLY, ON_TIME, CLOSE, LATE, CANCELED, VACATION }

data class QuestCard(
    val id: String,
    val title: String,
    val departLabel: String,
    val dueLabel: String,
    val status: CardStatus,
    val canDepart: Boolean,
    val canArrive: Boolean,
)

data class CleanupCard(
    val id: String,
    val title: String,
    val whenLabel: String,
    val date: LocalDate,
    /** 예정 도착 시각. [기록 추가] 창의 기본값이다. */
    val due: LocalTime,
    /** 그날 활동이 전혀 없는 지난 출근이면 [휴가였어]를 보여준다(스펙 D13). */
    val canBeVacation: Boolean = false,
)

/** 홈 펫 카드. 지금은 글자만 보여주고, 그림은 Phase B(Unity)에서 붙인다(스펙 D14). */
data class PetCard(
    val stage: String = "알",
    /** 알이면 아직 모습이 정해지지 않았다. */
    val form: String? = null,
    val mood: String = "보통",
    /** 다음 진화까지 남은 XP. 성체면 null이다. */
    val xpToEvolve: Int? = 100,
    /** 지금 단계 안에서 다음 진화까지의 진행률(0~1). */
    val evolveProgress: Float = 0f,
)

/** 이번 주 보스. [hp]는 남은 HP이고 0보다 작아지지 않는다. */
data class CurrentBoss(val name: String, val hp: Int, val maxHp: Int, val status: String)

/** 홈 보스 카드. 출근이 없는 주면 [current]가 null이다. */
data class BossCard(
    val current: CurrentBoss? = null,
    /** 처치해서 보스 도감에 오른 보스 종류 수. */
    val defeatedKinds: Int = 0,
)

sealed interface PendingAction {
    data object Depart : PendingAction
    data class Arrive(val safe: Boolean) : PendingAction
}

sealed interface HomeDialog {
    data class AskSafety(val questId: String) : HomeDialog
    data class ConfirmEarly(val questId: String, val action: PendingAction, val minutesEarly: Long, val canReschedule: Boolean = true) : HomeDialog
    data class Reschedule(val questId: String, val action: PendingAction, val due: LocalTime, val depart: LocalTime) : HomeDialog
    data class AskSelfControl(val date: LocalDate) : HomeDialog
    data class AddArrival(val questId: String, val date: LocalDate, val due: LocalTime) : HomeDialog

    /** 활동이 전혀 없는 지난 날을 휴가로 넣을지 묻는다(스펙 D13). */
    data class ConfirmVacation(val date: LocalDate) : HomeDialog
}

data class HomeUiState(
    val loading: Boolean = true,
    val tier: Tier = Tier.BRONZE,
    val representativeTitle: String = "미각성자",
    val xp: Int = 0,
    val nextTierXp: Int? = Tier.SILVER.minXp,
    val pet: PetCard = PetCard(),
    val boss: BossCard = BossCard(),
    val quests: List<QuestCard> = emptyList(),
    val cleanup: List<CleanupCard> = emptyList(),
    val celebrations: List<Celebration> = emptyList(),
    val dialog: HomeDialog? = null,
    val message: String? = null,
)
```

- [ ] **Step 5: CompanionCards 만들기**

`android-core/app/src/main/kotlin/com/chronoquest/app/home/CompanionCards.kt`:

```kotlin
package com.chronoquest.app.home

import com.chronoquest.rules.engine.BossState
import com.chronoquest.rules.engine.BossStatus
import com.chronoquest.rules.engine.PetForm
import com.chronoquest.rules.engine.PetMood
import com.chronoquest.rules.engine.PetStage
import com.chronoquest.rules.engine.PetState

/** 엔진의 펫·보스 상태를 홈 글자 카드로 바꾼다(스펙 5.8·5.9). 용어는 스펙 표를 따른다. */
object CompanionCards {
    fun pet(pet: PetState, xp: Int): PetCard {
        val next = pet.nextEvolveXp
        val progress = if (next == null) {
            1f
        } else {
            ((xp - pet.stage.minXp).toFloat() / (next - pet.stage.minXp)).coerceIn(0f, 1f)
        }
        return PetCard(
            stage = stageLabel(pet.stage),
            form = pet.form?.let(::formLabel),
            mood = moodLabel(pet.mood),
            xpToEvolve = next?.let { (it - xp).coerceAtLeast(0) },
            evolveProgress = progress,
        )
    }

    fun boss(boss: BossState): BossCard = BossCard(
        current = boss.current?.let {
            CurrentBoss(
                name = it.boss.name,
                hp = (it.maxHp - it.damage).coerceAtLeast(0),
                maxHp = it.maxHp,
                status = statusLabel(it.status),
            )
        },
        defeatedKinds = boss.defeats.count { it.value > 0 },
    )

    fun stageLabel(stage: PetStage): String = when (stage) {
        PetStage.EGG -> "알"
        PetStage.BABY -> "아기"
        PetStage.TEEN -> "청소년"
        PetStage.ADULT -> "성체"
    }

    fun formLabel(form: PetForm): String = when (form) {
        PetForm.GOOD -> "멋짐"
        PetForm.NORMAL -> "평범"
        PetForm.UGLY -> "못생김"
    }

    fun moodLabel(mood: PetMood): String = when (mood) {
        PetMood.HAPPY -> "신남"
        PetMood.NORMAL -> "보통"
        PetMood.SAD -> "시무룩"
    }

    fun statusLabel(status: BossStatus): String = when (status) {
        BossStatus.ACTIVE -> "진행 중"
        BossStatus.DEFEATED -> "처치"
        BossStatus.ESCAPED -> "도망"
        BossStatus.PENDING -> "결과 대기"
    }
}
```

- [ ] **Step 6: HomePresenter 고치기**

`HomePresenter.kt`에 import를 추가한다.
- `com.chronoquest.rules.model.QuestKind`

`refresh()`를 통째로 아래로 바꾼다.

```kotlin
    /** 일정 생성 → 오늘 요약·정리 필요·펫·보스·대표 칭호·안 본 연출을 다시 읽는다. 앱 복귀와 1분 주기로 부른다. */
    suspend fun refresh() = withContext(compute) {
        refreshLock.withLock {
            schedule.generateQuests()
            val today = game.today()
            val gameState = game.state()
            val cleanup = game.needsCleanup()
            // 정리 필요한 지난 출근 중 그날 활동이 전혀 없는 날만 [휴가였어]를 보여준다(스펙 D13).
            val vacationDates = cleanup.filter { it.kind == QuestKind.WORK }.map { it.date }.distinct()
                .filterTo(HashSet()) { schedule.canMarkMissedVacation(it) }
            val title = game.representativeTitle()
            val celebrations = game.unseenCelebrations()
            // 행동통제는 답할 때까지 새로고침마다 다시 묻는다(창을 닫아도 그날 사라지지 않게).
            val askSelfControl = today.day?.selfControl == null && today.quests.any { q -> q.record?.departedAt != null }
            _state.update {
                it.copy(
                    loading = false,
                    tier = gameState.tier,
                    xp = gameState.xp,
                    nextTierXp = gameState.nextTierXp,
                    representativeTitle = title,
                    pet = CompanionCards.pet(gameState.pet, gameState.xp),
                    boss = CompanionCards.boss(gameState.boss),
                    quests = today.quests.map { q -> toCard(q, today.vacation) },
                    cleanup = cleanup.map { q -> toCleanup(q, canBeVacation = q.kind == QuestKind.WORK && q.date in vacationDates) },
                    celebrations = celebrations,
                    dialog = if (askSelfControl && it.dialog == null) HomeDialog.AskSelfControl(today.date) else it.dialog,
                )
            }
        }
    }
```

`onCleanupAddArrival`를 아래로 바꾼다.

```kotlin
    fun onCleanupAddArrival(questId: String) {
        val card = _state.value.cleanup.firstOrNull { it.id == questId } ?: return
        _state.update { it.copy(dialog = HomeDialog.AddArrival(questId, card.date, card.due)) }
    }
```

`onArrivalEntered` 바로 아래에 추가한다.

```kotlin
    /** 정리 필요 카드의 [휴가였어]. 그날 활동이 없는 지난 출근에만 창을 연다. */
    fun onCleanupVacation(questId: String) {
        val card = _state.value.cleanup.firstOrNull { it.id == questId && it.canBeVacation } ?: return
        _state.update { it.copy(dialog = HomeDialog.ConfirmVacation(card.date)) }
    }

    suspend fun onVacationConfirmed(dialog: HomeDialog.ConfirmVacation) = withContext(compute) {
        val result = schedule.setVacation(dialog.date, true)
        _state.update {
            it.copy(
                dialog = closeIfOrigin(it.dialog, dialog),
                message = if (result is ScheduleResult.Rejected) Messages.problem(result.problems) else it.message,
            )
        }
        refresh()
    }
```

`toCard`와 `toCleanup`을 아래로 바꾼다.

```kotlin
    private fun toCard(item: TodayQuest, vacation: Boolean): QuestCard {
        val status = when {
            item.quest.status == QuestStatus.CANCELED -> CardStatus.CANCELED
            vacation && item.quest.kind == QuestKind.WORK -> CardStatus.VACATION
            item.outcome?.verdict == ArrivalVerdict.EARLY -> CardStatus.EARLY
            item.outcome?.verdict == ArrivalVerdict.ON_TIME -> CardStatus.ON_TIME
            item.outcome?.verdict == ArrivalVerdict.CLOSE -> CardStatus.CLOSE
            item.outcome?.verdict == ArrivalVerdict.LATE -> CardStatus.LATE
            item.record?.departedAt != null -> CardStatus.DEPARTED
            else -> CardStatus.WAITING
        }
        return QuestCard(
            id = item.quest.id,
            title = item.quest.title,
            departLabel = item.quest.targetDepartAt.format(TIME),
            dueLabel = item.quest.dueAt.format(TIME),
            status = status,
            canDepart = status == CardStatus.WAITING,
            canArrive = status == CardStatus.WAITING || status == CardStatus.DEPARTED,
        )
    }

    private fun toCleanup(quest: QuestEntity, canBeVacation: Boolean) = CleanupCard(
        id = quest.id,
        title = quest.title,
        whenLabel = quest.dueAt.format(CLEANUP_WHEN),
        date = quest.date,
        due = quest.dueAt.toLocalTime(),
        canBeVacation = canBeVacation,
    )
```

- [ ] **Step 7: HomeViewModel과 MainActivity에 동작 연결**

`HomeViewModel.kt`의 `onArrivalEntered` 아래에 추가한다.

```kotlin
    fun onCleanupVacation(questId: String) = presenter.onCleanupVacation(questId)
    fun onVacationConfirmed(dialog: HomeDialog.ConfirmVacation) = launch { presenter.onVacationConfirmed(dialog) }
```

`MainActivity.kt`의 `homeActions(...)`에서 `onArrivalEntered = vm::onArrivalEntered,` 다음 줄에 추가한다.

```kotlin
    onCleanupVacation = vm::onCleanupVacation,
    onVacationConfirmed = vm::onVacationConfirmed,
```

- [ ] **Step 8: HomeScreen에 펫·보스 카드, 휴가 칩, [휴가였어] 추가**

`HomeScreen.kt`를 아래 순서로 고친다.

1. import를 추가한다.
   - `androidx.compose.foundation.layout.ColumnScope`
   - `androidx.compose.foundation.layout.ExperimentalLayoutApi`
   - `androidx.compose.foundation.layout.FlowRow`
   - `androidx.compose.foundation.layout.IntrinsicSize`
   - `androidx.compose.foundation.layout.fillMaxHeight`
2. `HomeActions`에서 `onArrivalEntered` 다음에 필드 두 개를 추가한다.

```kotlin
    val onCleanupVacation: (String) -> Unit = {},
    val onVacationConfirmed: (HomeDialog.ConfirmVacation) -> Unit = {},
```

3. `HomeScreen`의 `LazyColumn`에서 `item { Header(state) }` 바로 다음에 넣는다.

```kotlin
        item { CompanionRow(state.pet, state.boss) }
```

4. `StatusChip`의 `when`에 한 줄을 추가한다.

```kotlin
        CardStatus.VACATION -> Triple("휴가", ChronoTokens.Colors.Bg2, ChronoTokens.Colors.Text2)
```

5. `CleanupRow`를 통째로 아래로 바꾼다. 버튼이 4개가 되어서 줄바꿈이 되도록 `FlowRow`를 쓴다.

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CleanupRow(card: CleanupCard, actions: HomeActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChronoTokens.Colors.Yellow50, RoundedCornerShape(ChronoTokens.Radius.Lg))
            .padding(ChronoTokens.Space.Lg),
    ) {
        Text("${card.whenLabel} ${card.title}", style = MaterialTheme.typography.titleMedium, color = ChronoTokens.Colors.Text1)
        Text("도착 기록이 없어. 어떻게 정리할까?", style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Gold700)
        FlowRow {
            TextButton(onClick = { actions.onCleanupAddArrival(card.id) }) { Text("기록 추가") }
            TextButton(onClick = { actions.onCleanupLate(card.id) }) { Text("지각 처리") }
            TextButton(onClick = { actions.onCleanupCancel(card.id) }) { Text("취소") }
            if (card.canBeVacation) {
                TextButton(onClick = { actions.onCleanupVacation(card.id) }) { Text("휴가였어") }
            }
        }
    }
}
```

6. 파일 끝(`celebrationText` 위)에 아래 composable들을 추가한다.

```kotlin
/** 홈 위쪽의 펫·보스 카드 한 줄. 두 카드 높이를 맞춘다. */
@Composable
private fun CompanionRow(pet: PetCard, boss: BossCard) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Md),
    ) {
        PetPanel(pet, Modifier.weight(1f).fillMaxHeight())
        BossPanel(boss, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun Panel(label: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .background(ChronoTokens.Colors.Bg2, RoundedCornerShape(ChronoTokens.Radius.Lg))
            .padding(ChronoTokens.Space.Lg),
        verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Xs),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = ChronoTokens.Colors.Text2)
        content()
    }
}

@Composable
private fun PetPanel(pet: PetCard, modifier: Modifier) {
    Panel("펫", modifier) {
        Text(listOfNotNull(pet.stage, pet.form).joinToString(" · "), style = MaterialTheme.typography.titleMedium, color = ChronoTokens.Colors.Text1)
        Text("기분: ${pet.mood}", style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Text2)
        LinearProgressIndicator(
            progress = { pet.evolveProgress },
            modifier = Modifier.fillMaxWidth().height(XP_BAR_HEIGHT),
            color = ChronoTokens.Colors.Gold500,
            trackColor = ChronoTokens.Colors.Yellow50,
            drawStopIndicator = {},
        )
        Text(
            pet.xpToEvolve?.let { "진화까지 $it XP" } ?: "다 컸어",
            style = MaterialTheme.typography.bodyMedium,
            color = ChronoTokens.Colors.Text2,
        )
    }
}

@Composable
private fun BossPanel(boss: BossCard, modifier: Modifier) {
    Panel("이번 주 보스", modifier) {
        val current = boss.current
        if (current == null) {
            Text("이번 주는 출근이 없어.", style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Text2)
        } else {
            Text(current.name, style = MaterialTheme.typography.titleMedium, color = ChronoTokens.Colors.Text1)
            Text("HP ${current.hp}/${current.maxHp} · ${current.status}", style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Text2)
            LinearProgressIndicator(
                progress = { if (current.maxHp == 0) 0f else current.hp.toFloat() / current.maxHp },
                modifier = Modifier.fillMaxWidth().height(XP_BAR_HEIGHT),
                color = ChronoTokens.Colors.Blue500,
                trackColor = ChronoTokens.Colors.Blue50,
                drawStopIndicator = {},
            )
        }
        Text("처치한 보스 ${boss.defeatedKinds}종", style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Text2)
    }
}
```

- [ ] **Step 9: HomeDialogs에 기본 시각과 휴가 확인 창 추가**

`HomeDialogs.kt`를 고친다.

1. import를 추가한다.
   - `java.time.format.DateTimeFormatter`
   - `java.util.Locale`
2. 파일 위쪽(`HomeDialogHost` 위)에 추가한다.

```kotlin
private val VACATION_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN)
```

3. `HomeDialogHost`의 `when`에서 `is HomeDialog.AddArrival -> ...` 다음에 추가한다.

```kotlin
        is HomeDialog.ConfirmVacation -> AlertDialog(
            onDismissRequest = actions.onDismissDialog,
            title = { Text("그날 휴가였어?") },
            text = { Text("${dialog.date.format(VACATION_DATE)}에는 기록이 하나도 없어. 휴가였으면 그날 출근은 판정에서 빠져.") },
            confirmButton = { TextButton(onClick = { actions.onVacationConfirmed(dialog) }) { Text("휴가였어") } },
            dismissButton = { TextButton(onClick = actions.onDismissDialog) { Text("아니야") } },
        )
```

4. `AddArrivalDialog`의 첫 줄을 예정 시각 기본값으로 바꾼다(인계 메모 A2b-2).

```kotlin
    val time = rememberTimePickerState(dialog.due.hour, dialog.due.minute, is24Hour = true)
```

- [ ] **Step 10: 화면 사진 테스트 고치기**

`HomeScreenshotTest.kt`의 `Samples`를 통째로 아래로 바꾼다. 테스트 클래스에는 2개를 추가한다.

```kotlin
private object Samples {
    val firstDay = HomeUiState(
        loading = false,
        tier = Tier.BRONZE,
        representativeTitle = "미각성자",
        xp = 0,
        nextTierXp = 100,
        pet = PetCard(stage = "알", form = null, mood = "보통", xpToEvolve = 100, evolveProgress = 0f),
        boss = BossCard(CurrentBoss("스누즈 슬라임", hp = 500, maxHp = 500, status = "진행 중"), defeatedKinds = 0),
        quests = listOf(
            QuestCard("w", "출근", "08:30", "09:00", CardStatus.WAITING, canDepart = true, canArrive = true),
            QuestCard("o", "보드게임", "13:20", "14:00", CardStatus.WAITING, canDepart = true, canArrive = true),
        ),
    )

    val inProgress = firstDay.copy(
        representativeTitle = "운명의 첫걸음",
        xp = 50,
        pet = PetCard(stage = "알", form = null, mood = "신남", xpToEvolve = 50, evolveProgress = 0.5f),
        boss = BossCard(CurrentBoss("스누즈 슬라임", hp = 380, maxHp = 500, status = "진행 중"), defeatedKinds = 0),
        quests = listOf(
            QuestCard("w", "출근", "08:30", "09:00", CardStatus.ON_TIME, canDepart = false, canArrive = false),
            QuestCard("o", "보드게임", "13:20", "14:00", CardStatus.DEPARTED, canDepart = false, canArrive = true),
        ),
    )

    val cleanupAndCelebration = inProgress.copy(
        tier = Tier.SILVER,
        xp = 120,
        nextTierXp = 300,
        pet = PetCard(stage = "아기", form = "멋짐", mood = "신남", xpToEvolve = 580, evolveProgress = 20f / 600f),
        boss = BossCard(CurrentBoss("5분만 더 골렘", hp = 0, maxHp = 400, status = "진행 중"), defeatedKinds = 1),
        cleanup = listOf(CleanupCard("c", "출근", "11/27 (금) 09:00", d("2026-11-27"), LocalTime.of(9, 0), canBeVacation = true)),
        celebrations = listOf(
            Celebration("title:1:1", CelebrationKind.TITLE, titleId = 1, detail = "최초의 각성"),
            Celebration("tier:SILVER", CelebrationKind.TIER_UP, detail = "시간의 수련자"),
        ),
    )

    val confirmEarly = inProgress.copy(dialog = HomeDialog.ConfirmEarly("o", PendingAction.Arrive(true), 45))

    val confirmEarlyLocked = inProgress.copy(
        dialog = HomeDialog.ConfirmEarly("o", PendingAction.Arrive(true), 45, canReschedule = false),
    )

    val reschedule = inProgress.copy(
        dialog = HomeDialog.Reschedule("o", PendingAction.Arrive(true), LocalTime.of(14, 0), LocalTime.of(13, 20)),
    )

    val vacationDay = firstDay.copy(
        quests = listOf(
            QuestCard("w", "출근", "08:30", "09:00", CardStatus.VACATION, canDepart = false, canArrive = false),
            QuestCard("o", "보드게임", "13:20", "14:00", CardStatus.WAITING, canDepart = true, canArrive = true),
        ),
    )

    val confirmVacation = cleanupAndCelebration.copy(dialog = HomeDialog.ConfirmVacation(d("2026-11-27")))
}
```

```kotlin
    @Test
    fun `휴가인 날`() = shoot("home_vacation_day", Samples.vacationDay)

    @Test
    fun `휴가였는지 묻는 창`() = shoot("home_confirm_vacation", Samples.confirmVacation)
```

- [ ] **Step 11: 테스트와 화면 사진**

Run:
- `cd android-core && ./gradlew :app:testDebugUnitTest :app:assembleDebug --offline`
- `./gradlew :app:recordRoborazziDebug --offline`

Expected: 실패 0. `app/screenshots/`에 `home_vacation_day.png`, `home_confirm_vacation.png`가 새로 생기고, 기존 홈 사진도 바뀐다.

**바뀐 홈 PNG를 전부 Read로 직접 본다.** 아래를 확인한다.
- 펫·보스 카드가 헤더 아래에 나란히 있고, 두 카드 높이가 같다.
- 보스 이름이나 HP 글자가 잘리지 않는다.
- 정리 카드 버튼 4개가 줄바꿈되어 다 보인다.
- 보라색은 없다.

문제가 있으면 고친 뒤 다시 찍고, 보고서에 적는다.

- [ ] **Step 12: 커밋**

```bash
git add android-core/app
git commit -m "feat(app): 홈에 펫·이번 주 보스 카드, 휴가 표시, 정리 [휴가였어]" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: 홈 안정성 (연타 막기, 오류 처리, 로딩, 연출 키, 행동통제 창)

**Files:**
- Create (app `ui/`): `ActionGate.kt`, `SafeLaunch.kt`, `Common.kt`
- Modify:
  - `app/.../ui/Messages.kt` (`FAILURE`)
  - `app/.../home/HomePresenter.kt` (전체 교체)
  - `app/.../home/HomeViewModel.kt` (전체 교체)
  - `app/.../home/HomeScreen.kt`, `app/.../home/HomeDialogs.kt`
- Test:
  - Create `android-core/app/src/test/kotlin/com/chronoquest/app/ui/SafeLaunchTest.kt`
  - Modify `app/.../home/HomePresenterTest.kt`, `app/.../home/HomeScreenshotTest.kt`
- 화면 사진: `home_loading.png`를 새로 찍는다.

**Interfaces:**
- Consumes: Task 1~3의 모든 홈 API
- Produces (Task 5~8이 쓴다):
  - `class ActionGate { suspend fun run(block: suspend () -> Unit): Boolean }`
  - `fun ViewModel.launchSafely(onFailure: (Throwable) -> Unit, block: suspend () -> Unit): Job`
  - `@Composable fun Chip(label: String, background: Color, foreground: Color)`
  - `@Composable fun LoadingText(modifier: Modifier = Modifier)`
  - `@Composable fun MessageDialog(message: String, onDismiss: () -> Unit)` (패키지 `com.chronoquest.app.ui`로 옮김)
  - `Messages.FAILURE = "문제가 생겼어. 잠시 뒤에 다시 해줘."`
  - `HomePresenter.onFailure(error: Throwable)`
  - `HomePresenter.onCelebrationsSeen(keys: List<String>)`
  - `HomeActions.onCelebrationsSeen: (List<String>) -> Unit`

- [ ] **Step 1: 테스트 작성**

`HomePresenterTest.kt`를 고친다. 먼저 테스트용 알림을 필드로 꺼낸다. `setUp` 안의 `val notifier = ...`를 지우고, 클래스 필드로 아래를 둔다. 위치는 `notified` 선언 아래다.

```kotlin
    private val notifier = SettlementNotifier { xp, celebrations -> notified += xp to celebrations }
```

`setUp`은 아래와 같아진다.

```kotlin
    @Before
    fun setUp() = runTest {
        db = inMemoryDb()
        db.questDao().insertAll(listOf(other, work))
        presenter = HomePresenter({ GameService(db, clock) }, { ScheduleService(db, clock) }, notifier, Dispatchers.Unconfined)
    }
```

기존 테스트 `` `연출을 확인하면 다시 나오지 않는다` ``의 `presenter.onCelebrationsSeen()`을 아래로 바꾼다.

```kotlin
        presenter.onCelebrationsSeen(state.celebrations.map { it.key })
```

import 중 없는 것을 추가한다.
- `kotlinx.coroutines.launch`
- `kotlinx.coroutines.test.StandardTestDispatcher`

클래스 끝에 아래 5개를 추가한다.

```kotlin
    @Test
    fun `동작이 끝나기 전에 다시 누른 것은 무시한다`() = runTest {
        // compute를 테스트 디스패처로 두면 첫 동작이 문(gate)을 잡은 채 멈춘 사이에 두 번째 동작이 들어온다.
        val gated = HomePresenter(
            { GameService(db, clock) },
            { ScheduleService(db, clock) },
            notifier,
            StandardTestDispatcher(testScheduler),
        )
        clock.now = dt("2026-11-30", "08:25")
        val first = launch { gated.onDepart(work.id) }
        val second = launch { gated.onDepart(work.id) }
        first.join()
        second.join()
        assertNull(gated.state.value.message)
        assertEquals(1, notified.size)
    }

    @Test
    fun `새로고침이 실패하면 로딩을 풀고 알려준다`() = runTest {
        val broken = HomePresenter({ error("DB 고장") }, { ScheduleService(db, clock) }, notifier, Dispatchers.Unconfined)
        val failure = runCatching { broken.refresh() }.exceptionOrNull()
        broken.onFailure(failure!!)
        assertFalse(broken.state.value.loading)
        assertEquals("문제가 생겼어. 잠시 뒤에 다시 해줘.", broken.state.value.message)
    }

    @Test
    fun `연출 확인은 화면에 보인 것만 본 것으로 한다`() = runTest {
        clock.now = dt("2026-11-30", "08:25")
        presenter.onDepart(work.id)
        presenter.dismissDialog()
        val shown = state.celebrations.map { it.key }
        clock.now = dt("2026-11-30", "08:55")
        presenter.onSafetyAnswered(work.id, true)
        presenter.onCelebrationsSeen(shown)
        assertEquals(listOf("title:1:1", "title:21:1"), state.celebrations.map { it.key })
    }

    @Test
    fun `다른 곳에서 행동통제를 답했으면 새로고침 때 질문 창을 닫는다`() = runTest {
        clock.now = dt("2026-11-30", "08:25")
        presenter.onDepart(work.id)
        assertEquals(HomeDialog.AskSelfControl(d("2026-11-30")), state.dialog)
        GameService(db, clock).answerSelfControl(d("2026-11-30"), true)
        presenter.refresh()
        assertNull(state.dialog)
    }

    @Test
    fun `서비스는 처음 새로고침할 때 한 번만 만든다`() = runTest {
        var made = 0
        val lazyPresenter = HomePresenter({ made++; GameService(db, clock) }, { ScheduleService(db, clock) }, notifier, Dispatchers.Unconfined)
        assertEquals(0, made)
        lazyPresenter.refresh()
        lazyPresenter.refresh()
        assertEquals(1, made)
    }
```

`android-core/app/src/test/kotlin/com/chronoquest/app/ui/SafeLaunchTest.kt`:

```kotlin
package com.chronoquest.app.ui

import androidx.lifecycle.ViewModel
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SafeLaunchTest {
    @Test
    fun `예외가 나면 onFailure로 넘기고 앱은 멈추지 않는다`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val vm = object : ViewModel() {}
            var caught: Throwable? = null
            vm.launchSafely({ caught = it }) { error("고장") }
            advanceUntilIdle()
            assertEquals("고장", caught?.message)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
```

- [ ] **Step 2: 실패 확인 (RED)**

Run: `cd android-core && ./gradlew :app:testDebugUnitTest --offline`

Expected: 컴파일 실패. `onFailure`, `onCelebrationsSeen(List)`, `launchSafely`가 아직 없다.

- [ ] **Step 3: 공통 부품 만들기**

`android-core/app/src/main/kotlin/com/chronoquest/app/ui/ActionGate.kt`:

```kotlin
package com.chronoquest.app.ui

import kotlinx.coroutines.sync.Mutex

/**
 * 버튼 연타 막기(인계 메모 A2b-2). 동작 하나가 끝나기 전에 들어온 동작은 버린다.
 * 버린 동작에는 아무 메시지도 띄우지 않는다.
 */
class ActionGate {
    private val mutex = Mutex()

    /** 다른 동작이 진행 중이면 [block]을 부르지 않고 false를 돌려준다. */
    suspend fun run(block: suspend () -> Unit): Boolean {
        if (!mutex.tryLock()) return false
        try {
            block()
        } finally {
            mutex.unlock()
        }
        return true
    }
}
```

`android-core/app/src/main/kotlin/com/chronoquest/app/ui/SafeLaunch.kt`:

```kotlin
package com.chronoquest.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 화면 동작을 viewModelScope에서 실행한다. 예외가 나면 [onFailure]로 넘겨서 화면이 멈추거나
 * 로딩에 갇히지 않게 한다(인계 메모 A2b-2). 취소(CancellationException)는 그대로 던진다.
 */
fun ViewModel.launchSafely(onFailure: (Throwable) -> Unit, block: suspend () -> Unit): Job =
    viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onFailure(e)
        }
    }
```

`android-core/app/src/main/kotlin/com/chronoquest/app/ui/Common.kt`:

```kotlin
package com.chronoquest.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.chronoquest.app.ui.theme.ChronoTokens

/** 작은 상태 표시. 배경·글자색은 토큰만 넘긴다. */
@Composable
fun Chip(label: String, background: Color, foreground: Color) {
    Text(
        label,
        modifier = Modifier
            .background(background, RoundedCornerShape(ChronoTokens.Radius.Sm))
            .padding(horizontal = ChronoTokens.Space.Md, vertical = ChronoTokens.Space.Xs),
        style = MaterialTheme.typography.labelLarge,
        color = foreground,
    )
}

/**
 * 처음 불러오는 동안 보여주는 글자.
 * 무한 애니메이션 표시는 화면 사진 테스트의 대기(waitForIdle)를 끝나지 않게 할 수 있어서 쓰지 않는다.
 */
@Composable
fun LoadingText(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(ChronoTokens.Colors.Bg1), contentAlignment = Alignment.Center) {
        Text("불러오는 중…", style = MaterialTheme.typography.bodyLarge, color = ChronoTokens.Colors.Text2)
    }
}

/** 한 줄 안내 창. */
@Composable
fun MessageDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } },
    )
}
```

`Messages.kt`의 `NOT_FOUND` 아래에 추가한다.

```kotlin
    const val FAILURE = "문제가 생겼어. 잠시 뒤에 다시 해줘."
```

- [ ] **Step 4: HomePresenter 전체 교체**

`android-core/app/src/main/kotlin/com/chronoquest/app/home/HomePresenter.kt`:

```kotlin
package com.chronoquest.app.home

import com.chronoquest.app.notify.SettlementNotifier
import com.chronoquest.app.ui.ActionGate
import com.chronoquest.app.ui.Messages
import com.chronoquest.core.data.QuestEntity
import com.chronoquest.core.data.RecordSource
import com.chronoquest.core.domain.TodayQuest
import com.chronoquest.core.service.ActionResult
import com.chronoquest.core.service.GameService
import com.chronoquest.core.service.ScheduleResult
import com.chronoquest.core.service.ScheduleService
import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.model.QuestKind
import com.chronoquest.rules.model.QuestStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 홈 화면의 상태와 동작. 쓰기는 전부 GameService·ScheduleService를 거친다.
 * 엔진 계산이 있으므로 [compute] 디스패처(기본 Default)에서 돈다(인계 메모 A2b "스레드").
 * 쓰기 동작은 [gate]로 한 번에 하나만 돈다. 연타한 두 번째 동작은 조용히 버린다.
 */
class HomePresenter(
    game: () -> GameService,
    schedule: () -> ScheduleService,
    private val notifier: SettlementNotifier,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) {
    // 서비스(칭호표 로딩 포함)는 compute 디스패처 안에서 처음 만든다.
    private val game by lazy(game)
    private val schedule by lazy(schedule)
    private val refreshLock = Mutex()
    private val gate = ActionGate()
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /** 일정 생성 → 오늘 요약·정리 필요·펫·보스·대표 칭호·안 본 연출을 다시 읽는다. 앱 복귀와 1분 주기로 부른다. */
    suspend fun refresh() = withContext(compute) {
        refreshLock.withLock {
            schedule.generateQuests()
            val today = game.today()
            val gameState = game.state()
            val cleanup = game.needsCleanup()
            // 정리 필요한 지난 출근 중 그날 활동이 전혀 없는 날만 [휴가였어]를 보여준다(스펙 D13).
            val vacationDates = cleanup.filter { it.kind == QuestKind.WORK }.map { it.date }.distinct()
                .filterTo(HashSet()) { schedule.canMarkMissedVacation(it) }
            val title = game.representativeTitle()
            val celebrations = game.unseenCelebrations()
            // 행동통제는 답할 때까지 새로고침마다 다시 묻는다(창을 닫아도 그날 사라지지 않게).
            val askSelfControl = today.day?.selfControl == null && today.quests.any { q -> q.record?.departedAt != null }
            val selfControlDialog = HomeDialog.AskSelfControl(today.date)
            _state.update {
                it.copy(
                    loading = false,
                    tier = gameState.tier,
                    xp = gameState.xp,
                    nextTierXp = gameState.nextTierXp,
                    representativeTitle = title,
                    pet = CompanionCards.pet(gameState.pet, gameState.xp),
                    boss = CompanionCards.boss(gameState.boss),
                    quests = today.quests.map { q -> toCard(q, today.vacation) },
                    cleanup = cleanup.map { q -> toCleanup(q, canBeVacation = q.kind == QuestKind.WORK && q.date in vacationDates) },
                    celebrations = celebrations,
                    dialog = when {
                        askSelfControl && it.dialog == null -> selfControlDialog
                        // 다른 곳에서 이미 답했으면 남아 있는 질문 창을 닫는다(인계 메모 A2b-2).
                        !askSelfControl && it.dialog == selfControlDialog -> null
                        else -> it.dialog
                    },
                )
            }
        }
    }

    suspend fun onDepart(questId: String) {
        gate.run { withContext(compute) { handle(questId, PendingAction.Depart, origin = null) { game.depart(questId) } } }
    }

    /** 도착은 먼저 안전 여부를 묻는다(스펙 6.2). */
    fun onArrive(questId: String) {
        _state.update { it.copy(dialog = HomeDialog.AskSafety(questId)) }
    }

    suspend fun onSafetyAnswered(questId: String, safe: Boolean) {
        gate.run {
            withContext(compute) {
                handle(questId, PendingAction.Arrive(safe), origin = HomeDialog.AskSafety(questId)) { game.arrive(questId, safe) }
            }
        }
    }

    suspend fun onRecordAnyway(dialog: HomeDialog.ConfirmEarly) {
        gate.run {
            withContext(compute) {
                handle(dialog.questId, dialog.action, origin = dialog) { perform(dialog.questId, dialog.action, allowEarly = true) }
            }
        }
    }

    /** 창만 바꾸고 쓰지 않으므로 gate를 쓰지 않는다. */
    suspend fun onEditScheduleRequested(dialog: HomeDialog.ConfirmEarly) = withContext(compute) {
        if (!dialog.canReschedule) return@withContext
        val quest = game.today().quests.firstOrNull { it.quest.id == dialog.questId }?.quest
        if (quest == null) {
            _state.update { it.copy(dialog = null, message = Messages.NOT_FOUND) }
        } else {
            _state.update {
                it.copy(
                    dialog = HomeDialog.Reschedule(
                        questId = quest.id,
                        action = dialog.action,
                        due = quest.dueAt.toLocalTime(),
                        depart = quest.targetDepartAt.toLocalTime(),
                    ),
                )
            }
        }
    }

    suspend fun onRescheduleConfirmed(dialog: HomeDialog.Reschedule, due: LocalTime, depart: LocalTime) {
        gate.run {
            withContext(compute) {
                when (val result = schedule.reschedule(dialog.questId, due, depart)) {
                    is ScheduleResult.Saved ->
                        handle(result.id, dialog.action, origin = dialog) { perform(result.id, dialog.action, allowEarly = false) }
                    is ScheduleResult.Rejected ->
                        _state.update { it.copy(message = Messages.problem(result.problems)) }
                }
            }
        }
    }

    suspend fun onSelfControlAnswered(date: LocalDate, value: Boolean) {
        gate.run { withContext(compute) { applyResult(game.answerSelfControl(date, value), HomeDialog.AskSelfControl(date)) } }
    }

    suspend fun onCleanupLate(questId: String) {
        gate.run { withContext(compute) { applyResult(game.resolveNoRecord(questId), origin = null) } }
    }

    suspend fun onCleanupCancel(questId: String) {
        gate.run { withContext(compute) { applyResult(game.cancelQuest(questId), origin = null) } }
    }

    fun onCleanupAddArrival(questId: String) {
        val card = _state.value.cleanup.firstOrNull { it.id == questId } ?: return
        _state.update { it.copy(dialog = HomeDialog.AddArrival(questId, card.date, card.due)) }
    }

    suspend fun onArrivalEntered(dialog: HomeDialog.AddArrival, time: LocalTime, safe: Boolean) {
        gate.run {
            withContext(compute) {
                val result = game.arrive(
                    dialog.questId,
                    safe,
                    at = dialog.date.atTime(time),
                    source = RecordSource.EDIT,
                    allowEarly = true,
                )
                applyResult(result, dialog)
            }
        }
    }

    /** 정리 필요 카드의 [휴가였어]. 그날 활동이 없는 지난 출근에만 창을 연다. */
    fun onCleanupVacation(questId: String) {
        val card = _state.value.cleanup.firstOrNull { it.id == questId && it.canBeVacation } ?: return
        _state.update { it.copy(dialog = HomeDialog.ConfirmVacation(card.date)) }
    }

    suspend fun onVacationConfirmed(dialog: HomeDialog.ConfirmVacation) {
        gate.run {
            withContext(compute) {
                val result = schedule.setVacation(dialog.date, true)
                _state.update {
                    it.copy(
                        dialog = closeIfOrigin(it.dialog, dialog),
                        message = if (result is ScheduleResult.Rejected) Messages.problem(result.problems) else it.message,
                    )
                }
                refresh()
            }
        }
    }

    /** 화면에 보인 연출만 본 것으로 표시한다. 그사이 새로 생긴 연출은 다음에 보여준다(인계 메모 A2b-2). */
    suspend fun onCelebrationsSeen(keys: List<String>) {
        gate.run {
            withContext(compute) {
                game.markSeen(keys)
                refresh()
            }
        }
    }

    fun dismissDialog() {
        _state.update { it.copy(dialog = null) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    /** 동작 중 예외. 로딩을 풀고 알려준다. 자세한 오류는 화면에 보이지 않는다. */
    fun onFailure(error: Throwable) {
        _state.update { it.copy(loading = false, message = Messages.FAILURE) }
    }

    private suspend fun perform(questId: String, action: PendingAction, allowEarly: Boolean): ActionResult =
        when (action) {
            PendingAction.Depart -> game.depart(questId, allowEarly = allowEarly)
            is PendingAction.Arrive -> game.arrive(questId, action.safe, allowEarly = allowEarly)
        }

    /**
     * 30분 이상 이르면 확인 창을 띄우고, 아니면 결과를 반영한다.
     * [origin]은 이 동작을 시작한 창이다(카드 버튼이면 null). 결과가 거부여도 다른 동작이 연 창은 닫지 않는다.
     */
    private suspend fun handle(
        questId: String,
        action: PendingAction,
        origin: HomeDialog?,
        block: suspend () -> ActionResult,
    ) {
        when (val result = block()) {
            is ActionResult.ConfirmEarly -> {
                // 기록이 있는 일정은 ScheduleService가 시각 변경을 거부하므로 [일정 수정]을 숨긴다.
                val canReschedule = game.today().quests.firstOrNull { it.quest.id == questId }?.let { it.record == null } ?: false
                _state.update {
                    it.copy(dialog = HomeDialog.ConfirmEarly(questId, action, result.minutesEarly, canReschedule))
                }
            }
            else -> applyResult(result, origin)
        }
    }

    private suspend fun applyResult(result: ActionResult, origin: HomeDialog?) {
        when (result) {
            is ActionResult.Done -> {
                if (result.xpGained != 0 || result.newCelebrations.isNotEmpty()) {
                    notifier.notify(result.xpGained, result.newCelebrations)
                }
                _state.update { it.copy(dialog = closeIfOrigin(it.dialog, origin)) }
            }
            is ActionResult.Rejected -> _state.update {
                it.copy(dialog = closeIfOrigin(it.dialog, origin), message = Messages.reject(result.reason))
            }
            is ActionResult.ConfirmEarly -> _state.update { it.copy(dialog = closeIfOrigin(it.dialog, origin)) }
            ActionResult.NotFound -> _state.update {
                it.copy(dialog = closeIfOrigin(it.dialog, origin), message = Messages.NOT_FOUND)
            }
        }
        refresh()
    }

    private fun closeIfOrigin(current: HomeDialog?, origin: HomeDialog?): HomeDialog? =
        if (current == origin) null else current

    private fun toCard(item: TodayQuest, vacation: Boolean): QuestCard {
        val status = when {
            item.quest.status == QuestStatus.CANCELED -> CardStatus.CANCELED
            vacation && item.quest.kind == QuestKind.WORK -> CardStatus.VACATION
            item.outcome?.verdict == ArrivalVerdict.EARLY -> CardStatus.EARLY
            item.outcome?.verdict == ArrivalVerdict.ON_TIME -> CardStatus.ON_TIME
            item.outcome?.verdict == ArrivalVerdict.CLOSE -> CardStatus.CLOSE
            item.outcome?.verdict == ArrivalVerdict.LATE -> CardStatus.LATE
            item.record?.departedAt != null -> CardStatus.DEPARTED
            else -> CardStatus.WAITING
        }
        return QuestCard(
            id = item.quest.id,
            title = item.quest.title,
            departLabel = item.quest.targetDepartAt.format(TIME),
            dueLabel = item.quest.dueAt.format(TIME),
            status = status,
            canDepart = status == CardStatus.WAITING,
            canArrive = status == CardStatus.WAITING || status == CardStatus.DEPARTED,
        )
    }

    private fun toCleanup(quest: QuestEntity, canBeVacation: Boolean) = CleanupCard(
        id = quest.id,
        title = quest.title,
        whenLabel = quest.dueAt.format(CLEANUP_WHEN),
        date = quest.date,
        due = quest.dueAt.toLocalTime(),
        canBeVacation = canBeVacation,
    )

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val CLEANUP_WHEN: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d (E) HH:mm", Locale.KOREAN)
    }
}
```

- [ ] **Step 5: HomeViewModel 전체 교체**

`android-core/app/src/main/kotlin/com/chronoquest/app/home/HomeViewModel.kt`:

```kotlin
package com.chronoquest.app.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chronoquest.app.AppContainer
import com.chronoquest.app.ui.launchSafely
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.StateFlow

/** presenter를 viewModelScope에 연결만 한다. 로직은 HomePresenter에 있다. 예외는 presenter.onFailure로 넘긴다. */
class HomeViewModel(private val presenter: HomePresenter) : ViewModel() {
    val state: StateFlow<HomeUiState> = presenter.state

    fun refresh() = act { presenter.refresh() }
    fun onDepart(questId: String) = act { presenter.onDepart(questId) }
    fun onArrive(questId: String) = presenter.onArrive(questId)
    fun onSafetyAnswered(questId: String, safe: Boolean) = act { presenter.onSafetyAnswered(questId, safe) }
    fun onRecordAnyway(dialog: HomeDialog.ConfirmEarly) = act { presenter.onRecordAnyway(dialog) }
    fun onEditSchedule(dialog: HomeDialog.ConfirmEarly) = act { presenter.onEditScheduleRequested(dialog) }
    fun onRescheduleConfirmed(dialog: HomeDialog.Reschedule, due: LocalTime, depart: LocalTime) =
        act { presenter.onRescheduleConfirmed(dialog, due, depart) }
    fun onSelfControlAnswered(date: LocalDate, value: Boolean) = act { presenter.onSelfControlAnswered(date, value) }
    fun onCleanupLate(questId: String) = act { presenter.onCleanupLate(questId) }
    fun onCleanupCancel(questId: String) = act { presenter.onCleanupCancel(questId) }
    fun onCleanupAddArrival(questId: String) = presenter.onCleanupAddArrival(questId)
    fun onArrivalEntered(dialog: HomeDialog.AddArrival, time: LocalTime, safe: Boolean) =
        act { presenter.onArrivalEntered(dialog, time, safe) }
    fun onCleanupVacation(questId: String) = presenter.onCleanupVacation(questId)
    fun onVacationConfirmed(dialog: HomeDialog.ConfirmVacation) = act { presenter.onVacationConfirmed(dialog) }
    fun onCelebrationsSeen(keys: List<String>) = act { presenter.onCelebrationsSeen(keys) }
    fun dismissDialog() = presenter.dismissDialog()
    fun dismissMessage() = presenter.dismissMessage()

    private fun act(block: suspend () -> Unit) {
        launchSafely(presenter::onFailure, block)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    HomePresenter({ container.gameService }, { container.scheduleService }, container.notifier),
                )
            }
        }
    }
}
```

- [ ] **Step 6: HomeScreen·HomeDialogs 정리**

`HomeScreen.kt`:
- import를 추가한다.
  - `com.chronoquest.app.ui.Chip`
  - `com.chronoquest.app.ui.LoadingText`
  - `com.chronoquest.app.ui.MessageDialog`
- 파일 안의 `private fun Chip(...)`을 지운다. `Common.kt`로 옮겼다.
- `HomeActions`의 `onCelebrationsSeen`을 아래로 바꾼다.

```kotlin
    val onCelebrationsSeen: (List<String>) -> Unit = {},
```

`HomeScreen` 함수의 맨 앞에 넣는다.

```kotlin
    if (state.loading) {
        LoadingText(modifier)
        return
    }
```

`CelebrationCard(state.celebrations, actions.onCelebrationsSeen)` 호출을 아래로 바꾼다.

```kotlin
            item { CelebrationCard(state.celebrations) { actions.onCelebrationsSeen(state.celebrations.map { it.key }) } }
```

`HomeDialogs.kt`:
- 파일 끝의 `MessageDialog` 함수를 지운다. `Common.kt`로 옮겼다.
- 쓰이지 않게 된 import는 지운다.

`MainActivity.kt`는 고치지 않는다. `vm::onCelebrationsSeen`의 타입이 `(List<String>) -> Unit`으로 바뀌었지만, 새 `HomeActions` 필드 타입과 맞는다.

- [ ] **Step 7: 로딩 화면 사진 추가**

`HomeScreenshotTest.kt`에 추가한다.

```kotlin
    @Test
    fun `불러오는 중`() = shoot("home_loading", HomeUiState())
```

- [ ] **Step 8: 테스트와 화면 사진**

Run:
- `cd android-core && ./gradlew :app:testDebugUnitTest :app:assembleDebug --offline`
- `./gradlew :app:recordRoborazziDebug --offline`

Expected: 실패 0. 새 파일 `home_loading.png`가 생긴다. Read로 열어서 가운데에 "불러오는 중…"이 보이는지 확인한다.

- [ ] **Step 9: 커밋**

```bash
git add android-core/app
git commit -m "fix(app): 홈 연타 막기, 오류 처리, 로딩 표시, 보인 연출만 확인, 답한 행동통제 창 닫기" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: 일정 탭 상태와 동작 (SchedulePresenter)

**Files:**
- Create (`android-core/app/src/main/kotlin/com/chronoquest/app/schedule/`): `ScheduleUiState.kt`, `SchedulePresenter.kt`, `ScheduleViewModel.kt`
- Test: Create `android-core/app/src/test/kotlin/com/chronoquest/app/schedule/SchedulePresenterTest.kt`

**Interfaces:**
- Consumes:
  - Task 1: `GameService.cancelQuest/restoreQuest`, `ScheduleService.setVacation`
  - Task 2: `ScheduleService.upcoming(): List<UpcomingDay>`
  - Task 4: `ActionGate`, `launchSafely`, `Messages`
  - 기존: `ScheduleService.templates/saveTemplate/deleteTemplate/addOneOffQuest/vacations/generateQuests`, `TemplateRules.maskOf/daysOf`
- Produces (Task 6·8이 쓴다):
  - 상태: `ScheduleUiState(loading, today, templates, days, laterVacations, dialog, message)`
  - 줄 데이터: `TemplateRow`, `UpcomingQuestRow`, `UpcomingDayRow`, `VacationRow`
  - 입력 값: `TemplateForm` (+ `TemplateForm.WEEKDAYS`), `OneOffForm`
  - 창: `sealed interface ScheduleDialog { EditTemplate(form), ConfirmDeleteTemplate(id, title), AddOneOff(form), PickVacationDate }`
  - `SchedulePresenter`의 함수
    - `refresh()`, `onFailure(error)`
    - 반복 일정: `onAddTemplate()`, `onEditTemplate(id)`, `onTemplateSaved(dialog, form)`, `onDeleteTemplateRequested(id, title)`, `onDeleteTemplateConfirmed(dialog)`
    - 단발 일정: `onAddOneOff()`, `onOneOffSaved(dialog, form)`
    - 취소·휴가: `onCancelQuest(id)`, `onRestoreQuest(id)`, `onVacationToggled(date, on)`, `onPickVacationDate()`, `onVacationDatePicked(date)`
    - 창 닫기: `dismissDialog()`, `dismissMessage()`
  - 문구 함수: `SchedulePresenter.kindLabel(QuestKind)`, `SchedulePresenter.dayLabel(DayOfWeek)`, `SchedulePresenter.DATE`(`M/d (E)`)
  - `ScheduleViewModel`: 위 동작을 그대로 노출하고 `factory(container)`를 가진다

- [ ] **Step 1: 테스트 작성**

`android-core/app/src/test/kotlin/com/chronoquest/app/schedule/SchedulePresenterTest.kt`:

```kotlin
package com.chronoquest.app.schedule

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.app.MutableClock
import com.chronoquest.app.d
import com.chronoquest.app.dt
import com.chronoquest.app.inMemoryDb
import com.chronoquest.app.notify.SettlementNotifier
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.data.ScheduleTemplateEntity
import com.chronoquest.core.domain.TemplateRules
import com.chronoquest.core.service.GameService
import com.chronoquest.core.service.ScheduleService
import com.chronoquest.rules.model.QuestKind
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SchedulePresenterTest {
    private lateinit var db: ChronoDatabase
    private lateinit var presenter: SchedulePresenter
    private val clock = MutableClock(dt("2026-11-30", "08:00"))
    private val notified = mutableListOf<Int>()

    @Before
    fun setUp() {
        db = inMemoryDb()
        presenter = SchedulePresenter(
            { GameService(db, clock) },
            { ScheduleService(db, clock) },
            SettlementNotifier { xp, _ -> notified += xp },
            Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val state get() = presenter.state.value

    private suspend fun saveWeekdayWork() {
        ScheduleService(db, clock).saveTemplate(
            ScheduleTemplateEntity(
                title = "출근",
                kind = QuestKind.WORK,
                daysMask = TemplateRules.maskOf(TemplateForm.WEEKDAYS),
                dueTime = LocalTime.of(9, 0),
                targetDepartTime = LocalTime.of(8, 30),
            ),
        )
    }

    @Test
    fun `새로고침하면 반복 일정과 오늘부터 7일을 보여준다`() = runTest {
        saveWeekdayWork()
        presenter.refresh()
        assertFalse(state.loading)
        assertEquals(d("2026-11-30"), state.today)
        assertEquals(listOf(TemplateRow(1, "출근", "출근", "평일", "출발 08:30 · 도착 09:00")), state.templates)
        assertEquals(7, state.days.size)
        assertEquals("오늘 · 11/30 (월)", state.days[0].dateLabel)
        assertEquals("내일 · 12/1 (화)", state.days[1].dateLabel)
        assertEquals("12/2 (수)", state.days[2].dateLabel)
        assertEquals(
            listOf(UpcomingQuestRow("2026-11-30-WORK-09:00", "출근", "출발 08:30 · 도착 09:00", canceled = false, canCancel = true, canRestore = false)),
            state.days[0].quests,
        )
        assertTrue(state.days[5].quests.isEmpty())   // 12/5 토요일
        assertTrue(state.days.all { it.canToggleVacation && !it.vacation })
    }

    @Test
    fun `반복 일정을 추가하고 고치고 지운다`() = runTest {
        presenter.refresh()
        presenter.onAddTemplate()
        val add = state.dialog as ScheduleDialog.EditTemplate
        assertEquals(TemplateForm(), add.form)
        presenter.onTemplateSaved(
            add,
            TemplateForm(
                title = "헬스",
                kind = QuestKind.OTHER,
                days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                due = LocalTime.of(19, 0),
                depart = LocalTime.of(18, 30),
            ),
        )
        assertNull(state.dialog)
        val row = state.templates.single()
        assertEquals(TemplateRow(row.id, "헬스", "약속", "화 목", "출발 18:30 · 도착 19:00"), row)

        presenter.onEditTemplate(row.id)
        val edit = state.dialog as ScheduleDialog.EditTemplate
        assertEquals("헬스", edit.form.title)
        assertEquals(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY), edit.form.days)
        presenter.onTemplateSaved(edit, edit.form.copy(due = LocalTime.of(20, 0), depart = LocalTime.of(19, 30)))
        assertEquals(listOf("출발 19:30 · 도착 20:00"), state.templates.map { it.timeLabel })

        presenter.onDeleteTemplateRequested(row.id, "헬스")
        val confirm = state.dialog as ScheduleDialog.ConfirmDeleteTemplate
        assertEquals(ScheduleDialog.ConfirmDeleteTemplate(row.id, "헬스"), confirm)
        presenter.onDeleteTemplateConfirmed(confirm)
        assertNull(state.dialog)
        assertTrue(state.templates.isEmpty())
    }

    @Test
    fun `잘못된 반복 일정은 창을 두고 이유를 알려준다`() = runTest {
        presenter.refresh()
        presenter.onAddTemplate()
        val add = state.dialog as ScheduleDialog.EditTemplate
        presenter.onTemplateSaved(add, TemplateForm(title = "  "))
        assertEquals(add, state.dialog)
        assertEquals("일정 이름이 비어 있어.", state.message)
        presenter.onTemplateSaved(add, TemplateForm(title = "출근", days = emptySet()))
        assertEquals("요일을 하나 이상 골라줘.", state.message)
        assertTrue(state.templates.isEmpty())
    }

    @Test
    fun `단발 일정을 추가하고 같은 것은 막는다`() = runTest {
        presenter.refresh()
        presenter.onAddOneOff()
        val add = state.dialog as ScheduleDialog.AddOneOff
        assertEquals(OneOffForm(date = d("2026-11-30")), add.form)
        val form = OneOffForm(date = d("2026-12-02"), title = "보드게임", due = LocalTime.of(14, 0), depart = LocalTime.of(13, 20))
        presenter.onOneOffSaved(add, form)
        assertNull(state.dialog)
        assertEquals(listOf("보드게임"), state.days[2].quests.map { it.title })
        presenter.onAddOneOff()
        val again = state.dialog as ScheduleDialog.AddOneOff
        presenter.onOneOffSaved(again, form)
        assertEquals(again, state.dialog)
        assertEquals("같은 시각에 같은 종류 일정이 이미 있어.", state.message)
    }

    @Test
    fun `일정을 취소하고 되돌린다`() = runTest {
        saveWeekdayWork()
        presenter.refresh()
        presenter.onCancelQuest("2026-12-01-WORK-09:00")
        val canceled = state.days[1].quests.single()
        assertTrue(canceled.canceled)
        assertTrue(canceled.canRestore)
        assertFalse(canceled.canCancel)
        presenter.onRestoreQuest("2026-12-01-WORK-09:00")
        assertFalse(state.days[1].quests.single().canceled)
        assertTrue(notified.isEmpty())
    }

    @Test
    fun `기록이 있는 일정과 그날은 바꿀 수 없다`() = runTest {
        saveWeekdayWork()
        clock.now = dt("2026-11-30", "08:25")
        GameService(db, clock).depart("2026-11-30-WORK-09:00")
        presenter.refresh()
        assertFalse(state.days[0].quests.single().canCancel)
        assertFalse(state.days[0].canToggleVacation)
        presenter.onCancelQuest("2026-11-30-WORK-09:00")
        assertEquals("기록이 있는 일정은 취소할 수 없어.", state.message)
        presenter.dismissMessage()
        presenter.onVacationToggled(d("2026-11-30"), true)
        assertEquals("그날 기록이 있어서 휴가로 바꿀 수 없어.", state.message)
    }

    @Test
    fun `휴가를 켜고 끄고 7일 뒤 휴가는 날짜를 골라 넣는다`() = runTest {
        presenter.refresh()
        presenter.onVacationToggled(d("2026-12-01"), true)
        assertTrue(state.days[1].vacation)
        presenter.onVacationToggled(d("2026-12-01"), false)
        assertFalse(state.days[1].vacation)
        presenter.onPickVacationDate()
        assertEquals(ScheduleDialog.PickVacationDate, state.dialog)
        presenter.onVacationDatePicked(d("2026-12-24"))
        assertNull(state.dialog)
        assertEquals(listOf(VacationRow(d("2026-12-24"), "12/24 (목)")), state.laterVacations)
        presenter.onVacationToggled(d("2026-12-24"), false)
        assertTrue(state.laterVacations.isEmpty())
    }

    @Test
    fun `실패하면 로딩을 풀고 알려준다`() = runTest {
        presenter.onFailure(IllegalStateException("고장"))
        assertFalse(state.loading)
        assertEquals("문제가 생겼어. 잠시 뒤에 다시 해줘.", state.message)
    }
}
```

- [ ] **Step 2: 실패 확인 (RED)**

Run: `cd android-core && ./gradlew :app:testDebugUnitTest --offline`

Expected: 컴파일 실패. `schedule` 패키지가 아직 없다.

- [ ] **Step 3: ScheduleUiState 만들기**

`android-core/app/src/main/kotlin/com/chronoquest/app/schedule/ScheduleUiState.kt`:

```kotlin
package com.chronoquest.app.schedule

import com.chronoquest.rules.model.QuestKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** 반복 일정 한 줄. */
data class TemplateRow(
    val id: Long,
    val title: String,
    val kindLabel: String,
    val daysLabel: String,
    val timeLabel: String,
)

/** 다가오는 일정 한 줄. 기록이 있으면 취소할 수 없다(스펙 D13). */
data class UpcomingQuestRow(
    val id: String,
    val title: String,
    val timeLabel: String,
    val canceled: Boolean,
    val canCancel: Boolean,
    val canRestore: Boolean,
)

/** 다가오는 하루. 그날 기록이 있으면 휴가로 켤 수 없다(이미 휴가면 끌 수는 있다). */
data class UpcomingDayRow(
    val date: LocalDate,
    val dateLabel: String,
    val vacation: Boolean,
    val canToggleVacation: Boolean,
    val quests: List<UpcomingQuestRow>,
)

/** 7일 뒤로 미리 잡아 둔 휴가. */
data class VacationRow(val date: LocalDate, val label: String)

/** 반복 일정 편집 값. [id]가 0이면 새로 만든다. */
data class TemplateForm(
    val id: Long = 0,
    val title: String = "",
    val kind: QuestKind = QuestKind.WORK,
    val days: Set<DayOfWeek> = WEEKDAYS,
    val due: LocalTime = LocalTime.of(9, 0),
    val depart: LocalTime = LocalTime.of(8, 30),
) {
    companion object {
        val WEEKDAYS: Set<DayOfWeek> = setOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
        )
    }
}

/** 단발 일정 추가 값. */
data class OneOffForm(
    val date: LocalDate,
    val title: String = "",
    val kind: QuestKind = QuestKind.OTHER,
    val due: LocalTime = LocalTime.of(14, 0),
    val depart: LocalTime = LocalTime.of(13, 30),
)

sealed interface ScheduleDialog {
    /** 반복 일정 추가·편집. [form]은 창을 열 때의 값이다. */
    data class EditTemplate(val form: TemplateForm) : ScheduleDialog
    data class ConfirmDeleteTemplate(val id: Long, val title: String) : ScheduleDialog
    data class AddOneOff(val form: OneOffForm) : ScheduleDialog

    /** 7일 뒤 휴가 날짜 고르기. 오늘 이전은 고를 수 없다. */
    data object PickVacationDate : ScheduleDialog
}

data class ScheduleUiState(
    val loading: Boolean = true,
    /** 기기 기준 오늘. 날짜 고르기의 최솟값이다. */
    val today: LocalDate? = null,
    val templates: List<TemplateRow> = emptyList(),
    val days: List<UpcomingDayRow> = emptyList(),
    val laterVacations: List<VacationRow> = emptyList(),
    val dialog: ScheduleDialog? = null,
    val message: String? = null,
)
```

- [ ] **Step 4: SchedulePresenter 만들기**

`android-core/app/src/main/kotlin/com/chronoquest/app/schedule/SchedulePresenter.kt`:

```kotlin
package com.chronoquest.app.schedule

import com.chronoquest.app.notify.SettlementNotifier
import com.chronoquest.app.ui.ActionGate
import com.chronoquest.app.ui.Messages
import com.chronoquest.core.data.ScheduleTemplateEntity
import com.chronoquest.core.domain.TemplateRules
import com.chronoquest.core.domain.UpcomingDay
import com.chronoquest.core.service.ActionResult
import com.chronoquest.core.service.GameService
import com.chronoquest.core.service.ScheduleResult
import com.chronoquest.core.service.ScheduleService
import com.chronoquest.rules.model.QuestKind
import com.chronoquest.rules.model.QuestStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 일정 탭(스펙 6.4·7.2): 반복 일정, 오늘부터 7일 일정의 취소·되돌리기, 단발 일정, 휴가.
 * 취소·휴가는 오늘 이후만 바꾼다(스펙 D13). 쓰기는 GameService·ScheduleService만 거친다.
 * 저장이 거부되면 창을 그대로 두고 이유만 알려준다.
 */
class SchedulePresenter(
    game: () -> GameService,
    schedule: () -> ScheduleService,
    private val notifier: SettlementNotifier,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) {
    private val game by lazy(game)
    private val schedule by lazy(schedule)
    private val refreshLock = Mutex()
    private val gate = ActionGate()
    private val _state = MutableStateFlow(ScheduleUiState())
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()

    suspend fun refresh() = withContext(compute) {
        refreshLock.withLock {
            schedule.generateQuests()
            val templates = schedule.templates()
            val days = schedule.upcoming()
            val lastShown = days.last().date
            val later = schedule.vacations().filter { it.isAfter(lastShown) }
            _state.update {
                it.copy(
                    loading = false,
                    today = days.first().date,
                    templates = templates.map(::toTemplateRow),
                    days = days.mapIndexed(::toDayRow),
                    laterVacations = later.map { date -> VacationRow(date, date.format(DATE)) },
                )
            }
        }
    }

    fun onAddTemplate() {
        _state.update { it.copy(dialog = ScheduleDialog.EditTemplate(TemplateForm())) }
    }

    suspend fun onEditTemplate(id: Long) = withContext(compute) {
        val template = schedule.templates().firstOrNull { it.id == id }
        _state.update {
            if (template == null) {
                it.copy(message = Messages.NOT_FOUND)
            } else {
                it.copy(dialog = ScheduleDialog.EditTemplate(template.toForm()))
            }
        }
    }

    suspend fun onTemplateSaved(dialog: ScheduleDialog.EditTemplate, form: TemplateForm) {
        gate.run {
            withContext(compute) {
                // 고칠 때는 장소·활성 같은 다른 필드를 그대로 둔다.
                val existing = if (form.id == 0L) null else schedule.templates().firstOrNull { it.id == form.id }
                val base = existing ?: ScheduleTemplateEntity(
                    title = form.title,
                    kind = form.kind,
                    daysMask = 0,
                    dueTime = form.due,
                    targetDepartTime = form.depart,
                )
                val entity = base.copy(
                    title = form.title.trim(),
                    kind = form.kind,
                    daysMask = TemplateRules.maskOf(form.days),
                    dueTime = form.due,
                    targetDepartTime = form.depart,
                )
                when (val result = schedule.saveTemplate(entity)) {
                    is ScheduleResult.Saved -> _state.update { it.copy(dialog = closeIfOrigin(it.dialog, dialog)) }
                    is ScheduleResult.Rejected -> _state.update { it.copy(message = Messages.problem(result.problems)) }
                }
                refresh()
            }
        }
    }

    fun onDeleteTemplateRequested(id: Long, title: String) {
        _state.update { it.copy(dialog = ScheduleDialog.ConfirmDeleteTemplate(id, title)) }
    }

    suspend fun onDeleteTemplateConfirmed(dialog: ScheduleDialog.ConfirmDeleteTemplate) {
        gate.run {
            withContext(compute) {
                schedule.deleteTemplate(dialog.id)
                _state.update { it.copy(dialog = closeIfOrigin(it.dialog, dialog)) }
                refresh()
            }
        }
    }

    fun onAddOneOff() {
        val today = _state.value.today ?: return
        _state.update { it.copy(dialog = ScheduleDialog.AddOneOff(OneOffForm(date = today))) }
    }

    suspend fun onOneOffSaved(dialog: ScheduleDialog.AddOneOff, form: OneOffForm) {
        gate.run {
            withContext(compute) {
                val result = schedule.addOneOffQuest(form.date, form.kind, form.title.trim(), form.due, form.depart)
                when (result) {
                    is ScheduleResult.Saved -> _state.update { it.copy(dialog = closeIfOrigin(it.dialog, dialog)) }
                    is ScheduleResult.Rejected -> _state.update { it.copy(message = Messages.problem(result.problems)) }
                }
                refresh()
            }
        }
    }

    suspend fun onCancelQuest(questId: String) {
        gate.run { withContext(compute) { applyResult(game.cancelQuest(questId)) } }
    }

    suspend fun onRestoreQuest(questId: String) {
        gate.run { withContext(compute) { applyResult(game.restoreQuest(questId)) } }
    }

    suspend fun onVacationToggled(date: LocalDate, on: Boolean) {
        gate.run {
            withContext(compute) {
                val result = schedule.setVacation(date, on)
                if (result is ScheduleResult.Rejected) {
                    _state.update { it.copy(message = Messages.problem(result.problems)) }
                }
                refresh()
            }
        }
    }

    fun onPickVacationDate() {
        _state.update { it.copy(dialog = ScheduleDialog.PickVacationDate) }
    }

    suspend fun onVacationDatePicked(date: LocalDate) {
        gate.run {
            withContext(compute) {
                val result = schedule.setVacation(date, true)
                _state.update {
                    it.copy(
                        dialog = closeIfOrigin(it.dialog, ScheduleDialog.PickVacationDate),
                        message = if (result is ScheduleResult.Rejected) Messages.problem(result.problems) else it.message,
                    )
                }
                refresh()
            }
        }
    }

    fun dismissDialog() {
        _state.update { it.copy(dialog = null) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    /** 동작 중 예외. 로딩을 풀고 알려준다. */
    fun onFailure(error: Throwable) {
        _state.update { it.copy(loading = false, message = Messages.FAILURE) }
    }

    private suspend fun applyResult(result: ActionResult) {
        when (result) {
            is ActionResult.Done ->
                if (result.xpGained != 0 || result.newCelebrations.isNotEmpty()) {
                    notifier.notify(result.xpGained, result.newCelebrations)
                }
            is ActionResult.Rejected -> _state.update { it.copy(message = Messages.reject(result.reason)) }
            ActionResult.NotFound -> _state.update { it.copy(message = Messages.NOT_FOUND) }
            // 취소·되돌리기는 이른 기록 확인을 돌려주지 않는다.
            is ActionResult.ConfirmEarly -> Unit
        }
        refresh()
    }

    private fun closeIfOrigin(current: ScheduleDialog?, origin: ScheduleDialog): ScheduleDialog? =
        if (current == origin) null else current

    private fun toTemplateRow(t: ScheduleTemplateEntity) = TemplateRow(
        id = t.id,
        title = t.title,
        kindLabel = kindLabel(t.kind),
        daysLabel = daysLabel(TemplateRules.daysOf(t.daysMask)),
        timeLabel = "출발 ${t.targetDepartTime.format(TIME)} · 도착 ${t.dueTime.format(TIME)}",
    )

    private fun toDayRow(index: Int, day: UpcomingDay) = UpcomingDayRow(
        date = day.date,
        dateLabel = when (index) {
            0 -> "오늘 · "
            1 -> "내일 · "
            else -> ""
        } + day.date.format(DATE),
        vacation = day.vacation,
        canToggleVacation = day.vacation || !day.hasActivity,
        quests = day.quests.map { item ->
            val canceled = item.quest.status == QuestStatus.CANCELED
            UpcomingQuestRow(
                id = item.quest.id,
                title = item.quest.title,
                timeLabel = "출발 ${item.quest.targetDepartAt.format(TIME)} · 도착 ${item.quest.dueAt.format(TIME)}",
                canceled = canceled,
                canCancel = !canceled && !item.hasRecord,
                canRestore = canceled,
            )
        },
    )

    private fun ScheduleTemplateEntity.toForm() = TemplateForm(
        id = id,
        title = title,
        kind = kind,
        days = TemplateRules.daysOf(daysMask),
        due = dueTime,
        depart = targetDepartTime,
    )

    companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN)
        private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

        fun kindLabel(kind: QuestKind): String = when (kind) {
            QuestKind.WORK -> "출근"
            QuestKind.OTHER -> "약속"
        }

        fun dayLabel(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, Locale.KOREAN)

        fun daysLabel(days: Set<DayOfWeek>): String = when (days) {
            DayOfWeek.values().toSet() -> "매일"
            TemplateForm.WEEKDAYS -> "평일"
            WEEKEND -> "주말"
            else -> days.sorted().joinToString(" ") { dayLabel(it) }
        }
    }
}
```

- [ ] **Step 5: ScheduleViewModel 만들기**

`android-core/app/src/main/kotlin/com/chronoquest/app/schedule/ScheduleViewModel.kt`:

```kotlin
package com.chronoquest.app.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chronoquest.app.AppContainer
import com.chronoquest.app.ui.launchSafely
import java.time.LocalDate
import kotlinx.coroutines.flow.StateFlow

/** presenter를 viewModelScope에 연결만 한다. 로직은 SchedulePresenter에 있다. */
class ScheduleViewModel(private val presenter: SchedulePresenter) : ViewModel() {
    val state: StateFlow<ScheduleUiState> = presenter.state

    fun refresh() = act { presenter.refresh() }
    fun onAddTemplate() = presenter.onAddTemplate()
    fun onEditTemplate(id: Long) = act { presenter.onEditTemplate(id) }
    fun onTemplateSaved(dialog: ScheduleDialog.EditTemplate, form: TemplateForm) = act { presenter.onTemplateSaved(dialog, form) }
    fun onDeleteTemplateRequested(id: Long, title: String) = presenter.onDeleteTemplateRequested(id, title)
    fun onDeleteTemplateConfirmed(dialog: ScheduleDialog.ConfirmDeleteTemplate) = act { presenter.onDeleteTemplateConfirmed(dialog) }
    fun onAddOneOff() = presenter.onAddOneOff()
    fun onOneOffSaved(dialog: ScheduleDialog.AddOneOff, form: OneOffForm) = act { presenter.onOneOffSaved(dialog, form) }
    fun onCancelQuest(questId: String) = act { presenter.onCancelQuest(questId) }
    fun onRestoreQuest(questId: String) = act { presenter.onRestoreQuest(questId) }
    fun onVacationToggled(date: LocalDate, on: Boolean) = act { presenter.onVacationToggled(date, on) }
    fun onPickVacationDate() = presenter.onPickVacationDate()
    fun onVacationDatePicked(date: LocalDate) = act { presenter.onVacationDatePicked(date) }
    fun dismissDialog() = presenter.dismissDialog()
    fun dismissMessage() = presenter.dismissMessage()

    private fun act(block: suspend () -> Unit) {
        launchSafely(presenter::onFailure, block)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ScheduleViewModel(
                    SchedulePresenter({ container.gameService }, { container.scheduleService }, container.notifier),
                )
            }
        }
    }
}
```

- [ ] **Step 6: 통과 확인**

Run: `cd android-core && ./gradlew :app:testDebugUnitTest :app:assembleDebug --offline`

Expected: 실패 0.

기대 문구 중 날짜·요일 문구는 JDK 한국어 로케일에 따라 정해진다(`M/d (E)` → `11/30 (월)`, `TextStyle.SHORT` → `월`). 다르게 나오면 테스트를 바꾸지 말고 보고한다.

- [ ] **Step 7: 커밋**

```bash
git add android-core/app
git commit -m "feat(app): 일정 탭 상태와 동작 (반복·단발 일정, 취소·되돌리기, 휴가)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: 일정 탭 화면

**Files:**
- Create: `android-core/app/src/main/kotlin/com/chronoquest/app/schedule/ScheduleScreen.kt`
- Create: `android-core/app/src/main/kotlin/com/chronoquest/app/schedule/ScheduleDialogs.kt`
- Test: Create `android-core/app/src/test/kotlin/com/chronoquest/app/schedule/ScheduleScreenshotTest.kt`
- 화면 사진: `schedule_overview.png`, `schedule_template_dialog.png`, `schedule_one_off_dialog.png`

**Interfaces:**
- Consumes:
  - Task 5의 상태 타입과 `SchedulePresenter.kindLabel/dayLabel/DATE`
  - Task 4: `Chip`, `LoadingText`, `MessageDialog`
- Produces (Task 8이 쓴다):
  - `data class ScheduleActions(...)` (필드는 아래 코드)
  - `@Composable fun ScheduleScreen(state: ScheduleUiState, actions: ScheduleActions, modifier: Modifier = Modifier)`

- [ ] **Step 1: 화면 사진 테스트 작성**

`android-core/app/src/test/kotlin/com/chronoquest/app/schedule/ScheduleScreenshotTest.kt`:

```kotlin
package com.chronoquest.app.schedule

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.app.FLIP5
import com.chronoquest.app.d
import com.chronoquest.app.ui.ChronoScaffold
import com.chronoquest.app.ui.MainTab
import com.chronoquest.app.ui.theme.ChronoTheme
import com.chronoquest.rules.model.QuestKind
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private object Samples {
    private const val WORK_TIME = "출발 08:30 · 도착 09:00"

    val overview = ScheduleUiState(
        loading = false,
        today = d("2026-11-30"),
        templates = listOf(
            TemplateRow(1, "출근", "출근", "평일", WORK_TIME),
            TemplateRow(2, "헬스", "약속", "화 목", "출발 18:30 · 도착 19:00"),
        ),
        days = listOf(
            UpcomingDayRow(
                d("2026-11-30"), "오늘 · 11/30 (월)", vacation = false, canToggleVacation = false,
                quests = listOf(UpcomingQuestRow("a", "출근", WORK_TIME, canceled = false, canCancel = false, canRestore = false)),
            ),
            UpcomingDayRow(
                d("2026-12-01"), "내일 · 12/1 (화)", vacation = false, canToggleVacation = true,
                quests = listOf(
                    UpcomingQuestRow("b", "출근", WORK_TIME, canceled = true, canCancel = false, canRestore = true),
                    UpcomingQuestRow("c", "헬스", "출발 18:30 · 도착 19:00", canceled = false, canCancel = true, canRestore = false),
                ),
            ),
            UpcomingDayRow(
                d("2026-12-02"), "12/2 (수)", vacation = true, canToggleVacation = true,
                quests = listOf(UpcomingQuestRow("d", "출근", WORK_TIME, canceled = false, canCancel = true, canRestore = false)),
            ),
            UpcomingDayRow(d("2026-12-05"), "12/5 (토)", vacation = false, canToggleVacation = true, quests = emptyList()),
        ),
        laterVacations = listOf(VacationRow(d("2026-12-24"), "12/24 (목)")),
    )

    val templateDialog = overview.copy(
        dialog = ScheduleDialog.EditTemplate(
            TemplateForm(
                id = 2,
                title = "헬스",
                kind = QuestKind.OTHER,
                days = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                due = LocalTime.of(19, 0),
                depart = LocalTime.of(18, 30),
            ),
        ),
    )

    val oneOffDialog = overview.copy(
        dialog = ScheduleDialog.AddOneOff(
            OneOffForm(date = d("2026-12-02"), title = "보드게임", due = LocalTime.of(14, 0), depart = LocalTime.of(13, 20)),
        ),
    )
}

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = FLIP5)
class ScheduleScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: ScheduleUiState) {
        compose.setContent {
            ChronoTheme {
                ChronoScaffold(selectedTab = MainTab.SCHEDULE, onTabSelected = {}) {
                    ScheduleScreen(state = state, actions = ScheduleActions())
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("screenshots/$name.png")
    }

    @Test
    fun `일정 탭`() = shoot("schedule_overview", Samples.overview)

    @Test
    fun `반복 일정 편집 창`() = shoot("schedule_template_dialog", Samples.templateDialog)

    @Test
    fun `단발 일정 추가 창`() = shoot("schedule_one_off_dialog", Samples.oneOffDialog)
}
```

- [ ] **Step 2: 실패 확인 (RED)**

Run: `cd android-core && ./gradlew :app:testDebugUnitTest --offline`

Expected: 컴파일 실패. `ScheduleScreen`, `ScheduleActions`가 아직 없다.

- [ ] **Step 3: ScheduleScreen 만들기**

`android-core/app/src/main/kotlin/com/chronoquest/app/schedule/ScheduleScreen.kt`:

```kotlin
package com.chronoquest.app.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.chronoquest.app.ui.Chip
import com.chronoquest.app.ui.LoadingText
import com.chronoquest.app.ui.MessageDialog
import com.chronoquest.app.ui.theme.ChronoTokens
import java.time.LocalDate

/** 일정 화면 부품 고유 크기. 간격·모서리는 ChronoTokens.Space·Radius를 쓴다. */
private val BORDER_WIDTH = 1.dp

/** 일정 화면이 위로 올리는 동작들. 기본값은 아무것도 안 해서 화면 사진에 그대로 쓴다. */
data class ScheduleActions(
    val onAddTemplate: () -> Unit = {},
    val onEditTemplate: (Long) -> Unit = {},
    val onTemplateSaved: (ScheduleDialog.EditTemplate, TemplateForm) -> Unit = { _, _ -> },
    val onDeleteTemplateRequested: (Long, String) -> Unit = { _, _ -> },
    val onDeleteTemplateConfirmed: (ScheduleDialog.ConfirmDeleteTemplate) -> Unit = {},
    val onAddOneOff: () -> Unit = {},
    val onOneOffSaved: (ScheduleDialog.AddOneOff, OneOffForm) -> Unit = { _, _ -> },
    val onCancelQuest: (String) -> Unit = {},
    val onRestoreQuest: (String) -> Unit = {},
    val onVacationToggled: (LocalDate, Boolean) -> Unit = { _, _ -> },
    val onPickVacationDate: () -> Unit = {},
    val onVacationDatePicked: (LocalDate) -> Unit = {},
    val onDismissDialog: () -> Unit = {},
    val onDismissMessage: () -> Unit = {},
)

@Composable
fun ScheduleScreen(state: ScheduleUiState, actions: ScheduleActions, modifier: Modifier = Modifier) {
    if (state.loading) {
        LoadingText(modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(ChronoTokens.Colors.Bg1),
        contentPadding = PaddingValues(horizontal = ChronoTokens.Space.Lg, vertical = ChronoTokens.Space.Xl),
        verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Sm),
    ) {
        item { SectionHeader("반복 일정", "추가", actions.onAddTemplate) }
        if (state.templates.isEmpty()) {
            item { Hint("출근처럼 매주 같은 일정을 넣어 둬.") }
        }
        items(state.templates, key = { "t-${it.id}" }) { TemplateItem(it, actions) }

        item { SectionHeader("다가오는 일정", "단발 일정", actions.onAddOneOff) }
        state.days.forEach { day ->
            item(key = "d-${day.date}") { DayHeader(day, actions) }
            if (day.quests.isEmpty()) {
                item(key = "e-${day.date}") { Hint("일정 없음") }
            }
            items(day.quests, key = { "q-${it.id}" }) { QuestItem(it, actions) }
        }

        item { SectionHeader("나중 휴가", "날짜 고르기", actions.onPickVacationDate) }
        if (state.laterVacations.isEmpty()) {
            item { Hint("7일 뒤의 휴가는 여기서 미리 넣어 둬.") }
        }
        items(state.laterVacations, key = { "v-${it.date}" }) { VacationItem(it, actions) }
    }
    ScheduleDialogHost(state, actions)
    state.message?.let { MessageDialog(it, actions.onDismissMessage) }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = ChronoTokens.Space.Md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = ChronoTokens.Colors.Text1)
        TextButton(onClick = onAction) { Text("+ $action") }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Text2)
}

@Composable
private fun TemplateItem(row: TemplateRow, actions: ScheduleActions) {
    val shape = RoundedCornerShape(ChronoTokens.Radius.Lg)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(BORDER_WIDTH, ChronoTokens.Colors.Border, shape)
            .clickable { actions.onEditTemplate(row.id) }
            .padding(ChronoTokens.Space.Lg),
        verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = ChronoTokens.Colors.Text1)
            Chip(row.kindLabel, ChronoTokens.Colors.Bg2, ChronoTokens.Colors.Text2)
        }
        Text("${row.daysLabel} · ${row.timeLabel}", style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Text2)
    }
}

@Composable
private fun DayHeader(day: UpcomingDayRow, actions: ScheduleActions) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = ChronoTokens.Space.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Sm),
    ) {
        Text(day.dateLabel, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = ChronoTokens.Colors.Text1)
        if (day.vacation) Chip("휴가", ChronoTokens.Colors.Yellow50, ChronoTokens.Colors.Gold700)
        if (day.canToggleVacation) {
            TextButton(onClick = { actions.onVacationToggled(day.date, !day.vacation) }) {
                Text(if (day.vacation) "휴가 빼기" else "휴가로")
            }
        }
    }
}

@Composable
private fun QuestItem(row: UpcomingQuestRow, actions: ScheduleActions) {
    val shape = RoundedCornerShape(ChronoTokens.Radius.Lg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(BORDER_WIDTH, ChronoTokens.Colors.Border, shape)
            .padding(horizontal = ChronoTokens.Space.Lg, vertical = ChronoTokens.Space.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Sm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (row.canceled) ChronoTokens.Colors.Text2 else ChronoTokens.Colors.Text1,
            )
            Text(row.timeLabel, style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Text2)
        }
        if (row.canceled) Chip("취소됨", ChronoTokens.Colors.Bg2, ChronoTokens.Colors.Text2)
        if (row.canCancel) TextButton(onClick = { actions.onCancelQuest(row.id) }) { Text("취소") }
        if (row.canRestore) TextButton(onClick = { actions.onRestoreQuest(row.id) }) { Text("되돌리기") }
    }
}

@Composable
private fun VacationItem(row: VacationRow, actions: ScheduleActions) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(row.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = ChronoTokens.Colors.Text1)
        TextButton(onClick = { actions.onVacationToggled(row.date, false) }) { Text("빼기") }
    }
}
```

- [ ] **Step 4: ScheduleDialogs 만들기**

`android-core/app/src/main/kotlin/com/chronoquest/app/schedule/ScheduleDialogs.kt`:

```kotlin
package com.chronoquest.app.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chronoquest.app.ui.theme.ChronoTokens
import com.chronoquest.rules.model.QuestKind
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@Composable
fun ScheduleDialogHost(state: ScheduleUiState, actions: ScheduleActions) {
    when (val dialog = state.dialog) {
        null -> Unit
        is ScheduleDialog.EditTemplate -> TemplateDialog(dialog, actions)
        is ScheduleDialog.ConfirmDeleteTemplate -> AlertDialog(
            onDismissRequest = actions.onDismissDialog,
            title = { Text("‘${dialog.title}’ 반복 일정을 지울까?") },
            text = { Text("지난 기록은 남아. 아직 기록 없는 앞으로의 일정만 지워져.") },
            confirmButton = { TextButton(onClick = { actions.onDeleteTemplateConfirmed(dialog) }) { Text("지우기") } },
            dismissButton = { TextButton(onClick = actions.onDismissDialog) { Text("닫기") } },
        )
        is ScheduleDialog.AddOneOff -> OneOffDialog(dialog, state.today ?: dialog.form.date, actions)
        ScheduleDialog.PickVacationDate -> state.today?.let { today ->
            DateChooser(
                initial = today,
                today = today,
                onPicked = actions.onVacationDatePicked,
                onDismiss = actions.onDismissDialog,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TemplateDialog(dialog: ScheduleDialog.EditTemplate, actions: ScheduleActions) {
    val initial = dialog.form
    var title by remember(dialog) { mutableStateOf(initial.title) }
    var kind by remember(dialog) { mutableStateOf(initial.kind) }
    var days by remember(dialog) { mutableStateOf(initial.days) }
    val due = rememberTimePickerState(initial.due.hour, initial.due.minute, is24Hour = true)
    val depart = rememberTimePickerState(initial.depart.hour, initial.depart.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = actions.onDismissDialog,
        title = { Text(if (initial.id == 0L) "반복 일정 추가" else "반복 일정 고치기") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Md),
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("이름") }, singleLine = true)
                KindPicker(kind) { kind = it }
                FieldLabel("요일")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Sm)) {
                    DayOfWeek.values().forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = { days = if (day in days) days - day else days + day },
                            label = { Text(SchedulePresenter.dayLabel(day)) },
                        )
                    }
                }
                FieldLabel("도착 예정")
                TimeInput(state = due)
                FieldLabel("목표 출발")
                TimeInput(state = depart)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                actions.onTemplateSaved(
                    dialog,
                    initial.copy(
                        title = title,
                        kind = kind,
                        days = days,
                        due = LocalTime.of(due.hour, due.minute),
                        depart = LocalTime.of(depart.hour, depart.minute),
                    ),
                )
            }) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (initial.id != 0L) {
                    TextButton(onClick = { actions.onDeleteTemplateRequested(initial.id, initial.title) }) { Text("지우기") }
                }
                TextButton(onClick = actions.onDismissDialog) { Text("닫기") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OneOffDialog(dialog: ScheduleDialog.AddOneOff, today: LocalDate, actions: ScheduleActions) {
    val initial = dialog.form
    var title by remember(dialog) { mutableStateOf(initial.title) }
    var kind by remember(dialog) { mutableStateOf(initial.kind) }
    var date by remember(dialog) { mutableStateOf(initial.date) }
    var choosingDate by remember(dialog) { mutableStateOf(false) }
    val due = rememberTimePickerState(initial.due.hour, initial.due.minute, is24Hour = true)
    val depart = rememberTimePickerState(initial.depart.hour, initial.depart.minute, is24Hour = true)
    if (choosingDate) {
        DateChooser(
            initial = date,
            today = today,
            onPicked = {
                date = it
                choosingDate = false
            },
            onDismiss = { choosingDate = false },
        )
    } else {
        AlertDialog(
            onDismissRequest = actions.onDismissDialog,
            title = { Text("단발 일정 추가") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Md),
                ) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("이름") }, singleLine = true)
                    KindPicker(kind) { kind = it }
                    FieldLabel("날짜")
                    TextButton(onClick = { choosingDate = true }) { Text(date.format(SchedulePresenter.DATE)) }
                    FieldLabel("도착 예정")
                    TimeInput(state = due)
                    FieldLabel("목표 출발")
                    TimeInput(state = depart)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    actions.onOneOffSaved(
                        dialog,
                        OneOffForm(
                            date = date,
                            title = title,
                            kind = kind,
                            due = LocalTime.of(due.hour, due.minute),
                            depart = LocalTime.of(depart.hour, depart.minute),
                        ),
                    )
                }) { Text("추가") }
            },
            dismissButton = { TextButton(onClick = actions.onDismissDialog) { Text("닫기") } },
        )
    }
}

@Composable
private fun KindPicker(kind: QuestKind, onChange: (QuestKind) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Sm)) {
        QuestKind.entries.forEach { option ->
            FilterChip(
                selected = option == kind,
                onClick = { onChange(option) },
                label = { Text(SchedulePresenter.kindLabel(option)) },
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = ChronoTokens.Colors.Text2)
}

/** 날짜 고르기. 오늘 이전은 고를 수 없다(스펙 D13). DatePicker는 UTC 자정 기준 밀리초를 쓴다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateChooser(initial: LocalDate, today: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = initial.toUtcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= today.toUtcMillis()
            override fun isSelectableYear(year: Int): Boolean = year >= today.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { picker.selectedDateMillis?.let { onPicked(it.toUtcDate()) } }) { Text("고르기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    ) {
        DatePicker(state = picker)
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
```

- [ ] **Step 5: 테스트와 화면 사진**

Run:
- `cd android-core && ./gradlew :app:testDebugUnitTest :app:assembleDebug --offline`
- `./gradlew :app:recordRoborazziDebug --offline`

Expected: 실패 0. 세 PNG가 생긴다. 세 장을 모두 Read로 직접 보고 아래를 확인한다.
- 반복 일정 카드, 날짜별 일정, 휴가 칩, 취소됨 칩, [되돌리기]·[취소]·[휴가로]·[휴가 빼기] 버튼이 보인다.
- 편집 창의 요일 칩 7개가 줄바꿈되어 다 보인다.
- 시간 입력 라벨이 한국어다.
- 창 배경은 흰색이고, 보라색은 없다.

`FlowRow`가 이 Compose 버전에서 더 이상 실험 API가 아니어서 opt-in 경고가 나면 그대로 둔다(경고일 뿐 오류가 아니다). 그 경우 보고서에 적는다.

- [ ] **Step 6: 커밋**

```bash
git add android-core/app
git commit -m "feat(app): 일정 탭 화면 (반복 일정 편집, 단발 일정, 휴가 날짜 고르기)과 플립5 화면 사진" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: 칭호 탭 (도감, NEW, 대표 칭호 고르기)

**Files:**
- Create (`android-core/app/src/main/kotlin/com/chronoquest/app/codex/`): `CodexUiState.kt`, `CodexLabels.kt`, `CodexPresenter.kt`, `CodexViewModel.kt`, `CodexScreen.kt`
- Test (Create, `android-core/app/src/test/kotlin/com/chronoquest/app/codex/`): `CodexLabelsTest.kt`, `CodexPresenterTest.kt`, `CodexScreenshotTest.kt`
- 화면 사진: `codex_overview.png`, `codex_detail.png`

**Interfaces:**
- Consumes:
  - Task 2: `GameService.representative(): TitleChoice`
  - Task 4: `ActionGate`, `launchSafely`, `Chip`, `LoadingText`, `MessageDialog`, `Messages.FAILURE`
  - 기존 `GameService`: `state()`, `newTitleIds()`, `markTitlesViewed(ids)`, `setRepresentativeTitle(id): Boolean`
  - 기존 `TitleCatalog`: `titles`, `series(Series)`. `Title(id, series, threshold, name)`, `Series.kind: SeriesKind`
  - 기존 `GameState`: `counters`, `arriveStreak`, `perfectWeekStreak`, `inventory`, `codexCount`
- Produces (Task 8이 쓴다):
  - `TitleRow`, `SeriesSection`, `CodexUiState`
  - `object CodexLabels { fun info(Series): SeriesInfo; fun counter(Series, GameState): Int; fun progress(kind, threshold, value, owned, unit): String }`
  - `CodexPresenter(game: () -> GameService, catalog: () -> TitleCatalog, compute)`
    - 함수: `onEnter()`, `refresh()`, `onTitleSelected(id)`, `onSetRepresentative(id)`, `dismissDetail()`, `dismissMessage()`, `onFailure(error)`
  - `CodexViewModel`
  - `CodexActions`와 `@Composable fun CodexScreen(state: CodexUiState, actions: CodexActions, modifier: Modifier = Modifier)`

**표시 규칙** (스펙 5.4):
- **누적 계열**: 카운터가 문턱값의 배수가 될 때마다 칭호를 받는다.
  - 다음 획득까지 남은 횟수 = `threshold - value % threshold`
- **연속 계열**: 연속 값이 문턱값에 닿으면 받는다.
  - 아직 못 닿았으면 `threshold - value`가 남은 횟수다.
  - 이번 연속에서 이미 닿았으면 "이번 연속에서 받음"이라고 쓴다.
- **NEW 표시**: 탭을 열 때(`onEnter`) 정하고, 그 자리에서 바로 본 것으로 표시한다.
  - 같은 방문 동안에는 1분 새로고침이 와도 NEW가 남는다.
  - 다음에 탭을 열면 NEW가 사라진다.

- [ ] **Step 1: 테스트 작성**

`android-core/app/src/test/kotlin/com/chronoquest/app/codex/CodexLabelsTest.kt`:

```kotlin
package com.chronoquest.app.codex

import com.chronoquest.rules.titles.Series
import com.chronoquest.rules.titles.SeriesKind
import kotlin.test.assertEquals
import org.junit.Test

class CodexLabelsTest {
    @Test
    fun `계열 이름과 조건 문구`() {
        assertEquals(
            listOf("누적 도착", "연속 도착", "정시 출발", "조기 도착", "기상 성공", "행동통제", "누적 완벽 주간", "연속 완벽 주간", "복귀", "월간 정복"),
            Series.entries.map { CodexLabels.info(it).name },
        )
        assertEquals("도착 5회마다", CodexLabels.info(Series.ARRIVE_TOTAL).condition(5))
        assertEquals("연속 도착 3회", CodexLabels.info(Series.ARRIVE_STREAK).condition(3))
        assertEquals("연속 완벽 주간 4주", CodexLabels.info(Series.PERFECT_WEEK_STREAK).condition(4))
        assertEquals("완벽한 달 2회마다", CodexLabels.info(Series.PERFECT_MONTH_TOTAL).condition(2))
    }

    @Test
    fun `누적 계열은 다음 배수까지 남은 횟수를 보여준다`() {
        assertEquals("앞으로 5회", CodexLabels.progress(SeriesKind.CUMULATIVE, threshold = 5, value = 0, owned = 0, unit = "회"))
        assertEquals("보유 1개 · 다음까지 3회", CodexLabels.progress(SeriesKind.CUMULATIVE, threshold = 5, value = 7, owned = 1, unit = "회"))
        assertEquals("보유 1개 · 다음까지 1회", CodexLabels.progress(SeriesKind.CUMULATIVE, threshold = 1, value = 1, owned = 1, unit = "회"))
    }

    @Test
    fun `연속 계열은 이번 연속에서 받았으면 그렇게 보여준다`() {
        assertEquals("앞으로 3회", CodexLabels.progress(SeriesKind.STREAK, threshold = 5, value = 2, owned = 0, unit = "회"))
        assertEquals("보유 2개 · 다음까지 3주", CodexLabels.progress(SeriesKind.STREAK, threshold = 5, value = 2, owned = 2, unit = "주"))
        assertEquals("보유 1개 · 이번 연속에서 받음", CodexLabels.progress(SeriesKind.STREAK, threshold = 5, value = 6, owned = 1, unit = "회"))
    }
}
```

`android-core/app/src/test/kotlin/com/chronoquest/app/codex/CodexPresenterTest.kt`:

```kotlin
package com.chronoquest.app.codex

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.app.MutableClock
import com.chronoquest.app.dt
import com.chronoquest.app.inMemoryDb
import com.chronoquest.app.questEntity
import com.chronoquest.core.data.ChronoDatabase
import com.chronoquest.core.service.GameService
import com.chronoquest.rules.titles.TitleCatalog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CodexPresenterTest {
    private lateinit var db: ChronoDatabase
    private lateinit var presenter: CodexPresenter
    private val clock = MutableClock(dt("2026-11-30", "08:00"))
    private val work = questEntity("2026-11-30")

    @Before
    fun setUp() = runTest {
        db = inMemoryDb()
        db.questDao().insertAll(listOf(work))
        presenter = CodexPresenter({ GameService(db, clock) }, { TitleCatalog.load() }, Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val state get() = presenter.state.value

    private fun row(id: Int): TitleRow = state.sections.flatMap { it.titles }.single { it.id == id }

    private suspend fun arriveOnTime() {
        clock.now = dt("2026-11-30", "08:55")
        GameService(db, clock).arrive(work.id, safe = true)
    }

    @Test
    fun `처음엔 모두 잠겨 있고 얼마나 남았는지 보여준다`() = runTest {
        presenter.onEnter()
        assertFalse(state.loading)
        assertEquals(0, state.collected)
        assertEquals(200, state.total)
        assertEquals("미각성자", state.representative)
        assertEquals(10, state.sections.size)
        assertTrue(state.sections.all { it.titles.size == 20 && it.owned == 0 })
        assertEquals(
            TitleRow(1, "최초의 각성", "도착 1회마다", owned = 0, progress = "앞으로 1회", isNew = false, isRepresentative = false),
            row(1),
        )
        assertEquals("도착 5회마다", row(5).condition)
        assertEquals("앞으로 5회", row(5).progress)
        assertEquals("연속 도착 1회", row(21).condition)
    }

    @Test
    fun `칭호를 얻으면 보유 수와 NEW를 보여주고 다음에 열면 NEW가 사라진다`() = runTest {
        arriveOnTime()
        presenter.onEnter()
        assertEquals(2, state.collected)
        assertEquals("운명의 첫걸음", state.representative)
        assertEquals(
            TitleRow(1, "최초의 각성", "도착 1회마다", owned = 1, progress = "보유 1개 · 다음까지 1회", isNew = true, isRepresentative = false),
            row(1),
        )
        assertTrue(row(21).isNew)
        assertTrue(row(21).isRepresentative)
        assertEquals("보유 1개 · 이번 연속에서 받음", row(21).progress)
        assertEquals("앞으로 1회", row(2).progress)
        assertEquals(1, state.sections[0].owned)

        presenter.refresh()
        assertTrue(row(1).isNew)   // 같은 방문 동안은 NEW가 남는다

        presenter.onEnter()
        assertFalse(row(1).isNew)
        assertFalse(row(21).isNew)
    }

    @Test
    fun `가진 칭호만 대표로 고른다`() = runTest {
        arriveOnTime()
        presenter.onEnter()
        presenter.onTitleSelected(1)
        assertEquals(1, state.detail?.id)
        presenter.onSetRepresentative(1)
        assertNull(state.detail)
        assertEquals("최초의 각성", state.representative)
        assertTrue(row(1).isRepresentative)
        assertFalse(row(21).isRepresentative)
        presenter.onSetRepresentative(61)
        assertEquals("가진 칭호만 대표로 고를 수 있어.", state.message)
    }
}
```

`android-core/app/src/test/kotlin/com/chronoquest/app/codex/CodexScreenshotTest.kt`:

```kotlin
package com.chronoquest.app.codex

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.chronoquest.app.FLIP5
import com.chronoquest.app.ui.ChronoScaffold
import com.chronoquest.app.ui.MainTab
import com.chronoquest.app.ui.theme.ChronoTheme
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private object Samples {
    private val arrive = listOf(
        TitleRow(1, "최초의 각성", "도착 1회마다", owned = 3, progress = "보유 3개 · 다음까지 1회", isNew = false, isRepresentative = false),
        TitleRow(2, "시간의 여행자", "도착 2회마다", owned = 1, progress = "보유 1개 · 다음까지 1회", isNew = true, isRepresentative = false),
        TitleRow(3, "시계탑의 방문자", "도착 3회마다", owned = 1, progress = "보유 1개 · 다음까지 3회", isNew = true, isRepresentative = false),
        TitleRow(4, "초침의 추적자", "도착 4회마다", owned = 0, progress = "앞으로 1회", isNew = false, isRepresentative = false),
        TitleRow(5, "시간의 사냥꾼", "도착 5회마다", owned = 0, progress = "앞으로 2회", isNew = false, isRepresentative = false),
    )
    private val streak = listOf(
        TitleRow(21, "운명의 첫걸음", "연속 도착 1회", owned = 1, progress = "보유 1개 · 이번 연속에서 받음", isNew = false, isRepresentative = true),
        TitleRow(22, "연속 도착 2", "연속 도착 2회", owned = 1, progress = "보유 1개 · 이번 연속에서 받음", isNew = true, isRepresentative = false),
        TitleRow(23, "연속 도착 3", "연속 도착 3회", owned = 0, progress = "앞으로 1회", isNew = false, isRepresentative = false),
    )

    val overview = CodexUiState(
        loading = false,
        collected = 5,
        total = 200,
        representative = "운명의 첫걸음",
        sections = listOf(
            SeriesSection("누적 도착", owned = 3, titles = arrive),
            SeriesSection("연속 도착", owned = 2, titles = streak),
        ),
    )

    val detail = overview.copy(detail = arrive[1])
}

@OptIn(ExperimentalRoborazziApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = FLIP5)
class CodexScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private fun shoot(name: String, state: CodexUiState) {
        compose.setContent {
            ChronoTheme {
                ChronoScaffold(selectedTab = MainTab.CODEX, onTabSelected = {}) {
                    CodexScreen(state = state, actions = CodexActions())
                }
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("screenshots/$name.png")
    }

    @Test
    fun `칭호 탭`() = shoot("codex_overview", Samples.overview)

    @Test
    fun `칭호 자세히`() = shoot("codex_detail", Samples.detail)
}
```

화면 사진 샘플의 22·23번 이름("연속 도착 2" 등)은 그림용 자리 글자다. 실제 칭호 이름과 달라도 된다.

- [ ] **Step 2: 실패 확인 (RED)**

Run: `cd android-core && ./gradlew :app:testDebugUnitTest --offline`

Expected: 컴파일 실패. `codex` 패키지가 아직 없다.

- [ ] **Step 3: 상태와 문구 만들기**

`android-core/app/src/main/kotlin/com/chronoquest/app/codex/CodexUiState.kt`:

```kotlin
package com.chronoquest.app.codex

/** 칭호 한 줄. [progress]는 보유 수와 다음 획득까지 남은 정도를 한 줄로 쓴 것이다. */
data class TitleRow(
    val id: Int,
    val name: String,
    val condition: String,
    val owned: Int,
    val progress: String,
    val isNew: Boolean,
    val isRepresentative: Boolean,
)

/** 칭호 계열 하나(20종). [owned]는 하나 이상 가진 칭호 수다. */
data class SeriesSection(val name: String, val owned: Int, val titles: List<TitleRow>)

data class CodexUiState(
    val loading: Boolean = true,
    /** 도감에 오른 고유 칭호 수(스펙 5.4). */
    val collected: Int = 0,
    val total: Int = 0,
    val representative: String = "미각성자",
    val sections: List<SeriesSection> = emptyList(),
    /** 눌러서 자세히 보는 칭호. */
    val detail: TitleRow? = null,
    val message: String? = null,
)
```

`android-core/app/src/main/kotlin/com/chronoquest/app/codex/CodexLabels.kt`:

```kotlin
package com.chronoquest.app.codex

import com.chronoquest.rules.engine.GameState
import com.chronoquest.rules.titles.Series
import com.chronoquest.rules.titles.SeriesKind

/** 계열 이름, 조건 문구, 남은 횟수의 단위. 계열 이름은 스펙 5.4 표를 따른다. */
data class SeriesInfo(val name: String, val condition: (Int) -> String, val unit: String)

/** 칭호 탭 문구(스펙 5.4). */
object CodexLabels {
    fun info(series: Series): SeriesInfo = when (series) {
        Series.ARRIVE_TOTAL -> SeriesInfo("누적 도착", { "도착 ${it}회마다" }, "회")
        Series.ARRIVE_STREAK -> SeriesInfo("연속 도착", { "연속 도착 ${it}회" }, "회")
        Series.DEPART_TOTAL -> SeriesInfo("정시 출발", { "정시 출발 ${it}회마다" }, "회")
        Series.EARLY_TOTAL -> SeriesInfo("조기 도착", { "조기 도착 ${it}회마다" }, "회")
        Series.WAKE_TOTAL -> SeriesInfo("기상 성공", { "기상 성공 ${it}회마다" }, "회")
        Series.SELF_CONTROL_TOTAL -> SeriesInfo("행동통제", { "행동통제 ${it}회마다" }, "회")
        Series.PERFECT_WEEK_TOTAL -> SeriesInfo("누적 완벽 주간", { "완벽 주간 ${it}회마다" }, "회")
        Series.PERFECT_WEEK_STREAK -> SeriesInfo("연속 완벽 주간", { "연속 완벽 주간 ${it}주" }, "주")
        Series.COMEBACK_TOTAL -> SeriesInfo("복귀", { "복귀 ${it}회마다" }, "회")
        Series.PERFECT_MONTH_TOTAL -> SeriesInfo("월간 정복", { "완벽한 달 ${it}회마다" }, "회")
    }

    /** 계열의 지금 카운터 값. 연속 계열은 지금 이어지는 연속 값이다. */
    fun counter(series: Series, state: GameState): Int = when (series) {
        Series.ARRIVE_TOTAL -> state.counters.arrive
        Series.ARRIVE_STREAK -> state.arriveStreak.current
        Series.DEPART_TOTAL -> state.counters.depart
        Series.EARLY_TOTAL -> state.counters.early
        Series.WAKE_TOTAL -> state.counters.wake
        Series.SELF_CONTROL_TOTAL -> state.counters.selfControl
        Series.PERFECT_WEEK_TOTAL -> state.counters.perfectWeeks
        Series.PERFECT_WEEK_STREAK -> state.perfectWeekStreak.current
        Series.COMEBACK_TOTAL -> state.counters.comebacks
        Series.PERFECT_MONTH_TOTAL -> state.counters.perfectMonths
    }

    /**
     * 보유 수와 다음 획득까지 남은 정도.
     * - 누적 계열은 다음 배수까지 남은 횟수를 쓴다.
     * - 연속 계열은 문턱값까지 남은 횟수를 쓴다. 이번 연속에서 이미 받았으면 그렇게 쓴다.
     */
    fun progress(kind: SeriesKind, threshold: Int, value: Int, owned: Int, unit: String): String {
        val next = when (kind) {
            SeriesKind.CUMULATIVE -> "${threshold - value % threshold}$unit"
            SeriesKind.STREAK -> if (value < threshold) "${threshold - value}$unit" else null
        }
        return when {
            next == null -> if (owned > 0) "보유 ${owned}개 · 이번 연속에서 받음" else "이번 연속에서 받음"
            owned > 0 -> "보유 ${owned}개 · 다음까지 $next"
            else -> "앞으로 $next"
        }
    }
}
```

- [ ] **Step 4: CodexPresenter·CodexViewModel 만들기**

`android-core/app/src/main/kotlin/com/chronoquest/app/codex/CodexPresenter.kt`:

```kotlin
package com.chronoquest.app.codex

import com.chronoquest.app.ui.ActionGate
import com.chronoquest.app.ui.Messages
import com.chronoquest.core.service.GameService
import com.chronoquest.rules.engine.GameState
import com.chronoquest.rules.titles.Series
import com.chronoquest.rules.titles.TitleCatalog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 칭호 탭(스펙 5.4·5.7): 10계열 × 20종, 보유 수, 다음 획득까지 남은 정도, NEW, 대표 칭호 고르기.
 * NEW는 탭을 열 때 정하고 바로 본 것으로 표시한다. 같은 방문 동안은 남고, 다음에 열면 사라진다.
 */
class CodexPresenter(
    game: () -> GameService,
    catalog: () -> TitleCatalog,
    private val compute: CoroutineDispatcher = Dispatchers.Default,
) {
    private val game by lazy(game)
    private val catalog by lazy(catalog)
    private val refreshLock = Mutex()
    private val gate = ActionGate()

    /** 이번 방문에서 NEW로 보여줄 칭호. [onEnter]에서만 바꾼다. */
    private var newBadges: Set<Int> = emptySet()
    private val _state = MutableStateFlow(CodexUiState())
    val state: StateFlow<CodexUiState> = _state.asStateFlow()

    /** 칭호 탭을 열 때 부른다. */
    suspend fun onEnter() = withContext(compute) {
        refreshLock.withLock {
            newBadges = game.newTitleIds()
            game.markTitlesViewed(newBadges)
        }
        refresh()
    }

    suspend fun refresh() = withContext(compute) {
        refreshLock.withLock {
            val gameState = game.state()
            val representative = game.representative()
            val sections = Series.entries.map { series -> section(series, gameState, representative.titleId) }
            _state.update {
                it.copy(
                    loading = false,
                    collected = gameState.codexCount,
                    total = catalog.titles.size,
                    representative = representative.name,
                    sections = sections,
                    detail = it.detail?.let { shown -> sections.flatMap { s -> s.titles }.firstOrNull { row -> row.id == shown.id } },
                )
            }
        }
    }

    fun onTitleSelected(titleId: Int) {
        _state.update { it.copy(detail = it.sections.flatMap { s -> s.titles }.firstOrNull { row -> row.id == titleId }) }
    }

    suspend fun onSetRepresentative(titleId: Int) {
        gate.run {
            withContext(compute) {
                if (game.setRepresentativeTitle(titleId)) {
                    _state.update { it.copy(detail = null) }
                } else {
                    _state.update { it.copy(message = NOT_OWNED_TEXT) }
                }
                refresh()
            }
        }
    }

    fun dismissDetail() {
        _state.update { it.copy(detail = null) }
    }

    fun dismissMessage() {
        _state.update { it.copy(message = null) }
    }

    /** 동작 중 예외. 로딩을 풀고 알려준다. */
    fun onFailure(error: Throwable) {
        _state.update { it.copy(loading = false, message = Messages.FAILURE) }
    }

    private fun section(series: Series, state: GameState, representativeId: Int?): SeriesSection {
        val info = CodexLabels.info(series)
        val value = CodexLabels.counter(series, state)
        val rows = catalog.series(series).sortedBy { it.id }.map { title ->
            val owned = state.inventory[title.id] ?: 0
            TitleRow(
                id = title.id,
                name = title.name,
                condition = info.condition(title.threshold),
                owned = owned,
                progress = CodexLabels.progress(series.kind, title.threshold, value, owned, info.unit),
                isNew = title.id in newBadges,
                isRepresentative = title.id == representativeId,
            )
        }
        return SeriesSection(info.name, rows.count { it.owned > 0 }, rows)
    }

    private companion object {
        const val NOT_OWNED_TEXT = "가진 칭호만 대표로 고를 수 있어."
    }
}
```

`android-core/app/src/main/kotlin/com/chronoquest/app/codex/CodexViewModel.kt`:

```kotlin
package com.chronoquest.app.codex

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.chronoquest.app.AppContainer
import com.chronoquest.app.ui.launchSafely
import kotlinx.coroutines.flow.StateFlow

/** presenter를 viewModelScope에 연결만 한다. 로직은 CodexPresenter에 있다. */
class CodexViewModel(private val presenter: CodexPresenter) : ViewModel() {
    val state: StateFlow<CodexUiState> = presenter.state

    fun onEnter() = act { presenter.onEnter() }
    fun refresh() = act { presenter.refresh() }
    fun onTitleSelected(titleId: Int) = presenter.onTitleSelected(titleId)
    fun onSetRepresentative(titleId: Int) = act { presenter.onSetRepresentative(titleId) }
    fun dismissDetail() = presenter.dismissDetail()
    fun dismissMessage() = presenter.dismissMessage()

    private fun act(block: suspend () -> Unit) {
        launchSafely(presenter::onFailure, block)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { CodexViewModel(CodexPresenter({ container.gameService }, { container.catalog })) }
        }
    }
}
```

- [ ] **Step 5: CodexScreen 만들기**

`android-core/app/src/main/kotlin/com/chronoquest/app/codex/CodexScreen.kt`:

```kotlin
package com.chronoquest.app.codex

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.chronoquest.app.ui.Chip
import com.chronoquest.app.ui.LoadingText
import com.chronoquest.app.ui.MessageDialog
import com.chronoquest.app.ui.theme.ChronoTokens

/** 칭호 화면 부품 고유 크기. 간격·모서리는 ChronoTokens.Space·Radius를 쓴다. */
private val LOCK_ICON_SIZE = 18.dp
private val BORDER_WIDTH = 1.dp

/** 칭호 화면이 위로 올리는 동작들. 기본값은 아무것도 안 해서 화면 사진에 그대로 쓴다. */
data class CodexActions(
    val onTitleSelected: (Int) -> Unit = {},
    val onSetRepresentative: (Int) -> Unit = {},
    val onDismissDetail: () -> Unit = {},
    val onDismissMessage: () -> Unit = {},
)

@Composable
fun CodexScreen(state: CodexUiState, actions: CodexActions, modifier: Modifier = Modifier) {
    if (state.loading) {
        LoadingText(modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(ChronoTokens.Colors.Bg1),
        contentPadding = PaddingValues(horizontal = ChronoTokens.Space.Lg, vertical = ChronoTokens.Space.Xl),
        verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Sm),
    ) {
        item { CodexHeader(state) }
        state.sections.forEach { section ->
            item(key = "s-${section.name}") { SectionHeader(section) }
            items(section.titles, key = { "t-${it.id}" }) { TitleItem(it, actions) }
        }
    }
    state.detail?.let { TitleDetailDialog(it, actions) }
    state.message?.let { MessageDialog(it, actions.onDismissMessage) }
}

@Composable
private fun CodexHeader(state: CodexUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Xs)) {
        Text("칭호 도감 ${state.collected}/${state.total}", style = MaterialTheme.typography.headlineSmall, color = ChronoTokens.Colors.Text1)
        Text("대표 칭호: ${state.representative}", style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Text2)
    }
}

@Composable
private fun SectionHeader(section: SeriesSection) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = ChronoTokens.Space.Lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(section.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = ChronoTokens.Colors.Text1)
        Text("${section.owned}/${section.titles.size}", style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Text2)
    }
}

@Composable
private fun TitleItem(row: TitleRow, actions: CodexActions) {
    val owned = row.owned > 0
    val shape = RoundedCornerShape(ChronoTokens.Radius.Md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(BORDER_WIDTH, ChronoTokens.Colors.Border, shape)
            .clickable { actions.onTitleSelected(row.id) }
            .padding(horizontal = ChronoTokens.Space.Lg, vertical = ChronoTokens.Space.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Sm),
    ) {
        if (!owned) {
            Icon(Icons.Filled.Lock, contentDescription = "잠김", modifier = Modifier.size(LOCK_ICON_SIZE), tint = ChronoTokens.Colors.Text2)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (owned) ChronoTokens.Colors.Text1 else ChronoTokens.Colors.Text2,
            )
            Text("${row.condition} · ${row.progress}", style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Text2)
        }
        if (row.isNew) Chip("NEW", ChronoTokens.Colors.Blue50, ChronoTokens.Colors.Blue700)
        if (row.isRepresentative) Chip("대표", ChronoTokens.Colors.Yellow50, ChronoTokens.Colors.Gold700)
    }
}

@Composable
private fun TitleDetailDialog(row: TitleRow, actions: CodexActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissDetail,
        title = { Text(row.name) },
        text = { Text("${row.condition}\n${row.progress}") },
        confirmButton = {
            if (row.owned > 0 && !row.isRepresentative) {
                TextButton(onClick = { actions.onSetRepresentative(row.id) }) { Text("대표 칭호로") }
            }
        },
        dismissButton = { TextButton(onClick = actions.onDismissDetail) { Text("닫기") } },
    )
}
```

- [ ] **Step 6: 테스트와 화면 사진**

Run:
- `cd android-core && ./gradlew :app:testDebugUnitTest :app:assembleDebug --offline`
- `./gradlew :app:recordRoborazziDebug --offline`

Expected: 실패 0. `codex_overview.png`와 `codex_detail.png`가 생긴다. 두 장을 Read로 직접 보고 아래를 확인한다.
- 잠긴 칭호에는 자물쇠 아이콘과 회색 이름이 보인다.
- NEW 칩과 대표 칩이 보인다.
- 제목 "칭호 도감 5/200"은 검정 단색이다.
- 보라색은 없다.

- [ ] **Step 7: 커밋**

```bash
git add android-core/app
git commit -m "feat(app): 칭호 탭 (계열별 도감, 남은 횟수, NEW, 대표 칭호 고르기)" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: 탭 연결, 문서, 전체 확인

**Files:**
- Modify: `android-core/app/src/main/kotlin/com/chronoquest/app/MainActivity.kt` (전체 교체)
- Modify: `android-core/app/src/main/kotlin/com/chronoquest/app/ui/MainScaffold.kt` (`ComingSoon` KDoc)
- Modify: `android-core/README.md`
- Modify: `docs/superpowers/notes/2026-11-25-a1-carryover.md`

**Interfaces:**
- Consumes: Task 3~7의 ViewModel·Screen·Actions 전부
- Produces: 앱에서 하단 탭 홈·일정·칭호가 동작한다. 기록 탭은 A2b-3까지 "다음 단계" 자리로 남는다.

- [ ] **Step 1: MainActivity 전체 교체**

`android-core/app/src/main/kotlin/com/chronoquest/app/MainActivity.kt`:

```kotlin
package com.chronoquest.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chronoquest.app.codex.CodexActions
import com.chronoquest.app.codex.CodexScreen
import com.chronoquest.app.codex.CodexViewModel
import com.chronoquest.app.home.HomeActions
import com.chronoquest.app.home.HomeScreen
import com.chronoquest.app.home.HomeViewModel
import com.chronoquest.app.schedule.ScheduleActions
import com.chronoquest.app.schedule.ScheduleScreen
import com.chronoquest.app.schedule.ScheduleViewModel
import com.chronoquest.app.ui.ChronoScaffold
import com.chronoquest.app.ui.ComingSoon
import com.chronoquest.app.ui.MainTab
import com.chronoquest.app.ui.theme.ChronoTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val container = (application as ChronoApp).container
        setContent {
            ChronoTheme {
                val home: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
                val schedule: ScheduleViewModel = viewModel(factory = ScheduleViewModel.factory(container))
                val codex: CodexViewModel = viewModel(factory = CodexViewModel.factory(container))
                ChronoMainScreen(home, schedule, codex)
            }
        }
    }
}

/**
 * 하단 탭 화면. 앱이 보이는 동안 지금 보는 탭만 1분마다 새로고침한다(인계 메모 A2b).
 * 탭을 바꾸면 그 탭을 바로 한 번 읽는다. 칭호 탭은 열 때 NEW를 정한다.
 */
@Composable
fun ChronoMainScreen(home: HomeViewModel, schedule: ScheduleViewModel, codex: CodexViewModel) {
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, tab) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var entering = true
            while (true) {
                when (tab) {
                    MainTab.HOME -> home.refresh()
                    MainTab.SCHEDULE -> schedule.refresh()
                    MainTab.CODEX -> if (entering) codex.onEnter() else codex.refresh()
                    MainTab.LOG -> Unit
                }
                entering = false
                delay(60_000)
            }
        }
    }
    ChronoScaffold(selectedTab = tab, onTabSelected = { tab = it }) {
        when (tab) {
            MainTab.HOME -> {
                val state by home.state.collectAsStateWithLifecycle()
                HomeScreen(state = state, actions = remember(home) { homeActions(home) })
            }
            MainTab.SCHEDULE -> {
                val state by schedule.state.collectAsStateWithLifecycle()
                ScheduleScreen(state = state, actions = remember(schedule) { scheduleActions(schedule) })
            }
            MainTab.CODEX -> {
                val state by codex.state.collectAsStateWithLifecycle()
                CodexScreen(state = state, actions = remember(codex) { codexActions(codex) })
            }
            MainTab.LOG -> ComingSoon(tab)
        }
    }
}

private fun homeActions(vm: HomeViewModel) = HomeActions(
    onDepart = vm::onDepart,
    onArrive = vm::onArrive,
    onSafetyAnswered = vm::onSafetyAnswered,
    onRecordAnyway = vm::onRecordAnyway,
    onEditSchedule = vm::onEditSchedule,
    onRescheduleConfirmed = vm::onRescheduleConfirmed,
    onSelfControlAnswered = vm::onSelfControlAnswered,
    onCleanupLate = vm::onCleanupLate,
    onCleanupCancel = vm::onCleanupCancel,
    onCleanupAddArrival = vm::onCleanupAddArrival,
    onArrivalEntered = vm::onArrivalEntered,
    onCleanupVacation = vm::onCleanupVacation,
    onVacationConfirmed = vm::onVacationConfirmed,
    onDismissDialog = vm::dismissDialog,
    onDismissMessage = vm::dismissMessage,
    onCelebrationsSeen = vm::onCelebrationsSeen,
)

private fun scheduleActions(vm: ScheduleViewModel) = ScheduleActions(
    onAddTemplate = vm::onAddTemplate,
    onEditTemplate = vm::onEditTemplate,
    onTemplateSaved = vm::onTemplateSaved,
    onDeleteTemplateRequested = vm::onDeleteTemplateRequested,
    onDeleteTemplateConfirmed = vm::onDeleteTemplateConfirmed,
    onAddOneOff = vm::onAddOneOff,
    onOneOffSaved = vm::onOneOffSaved,
    onCancelQuest = vm::onCancelQuest,
    onRestoreQuest = vm::onRestoreQuest,
    onVacationToggled = vm::onVacationToggled,
    onPickVacationDate = vm::onPickVacationDate,
    onVacationDatePicked = vm::onVacationDatePicked,
    onDismissDialog = vm::dismissDialog,
    onDismissMessage = vm::dismissMessage,
)

private fun codexActions(vm: CodexViewModel) = CodexActions(
    onTitleSelected = vm::onTitleSelected,
    onSetRepresentative = vm::onSetRepresentative,
    onDismissDetail = vm::dismissDetail,
    onDismissMessage = vm::dismissMessage,
)
```

`MainScaffold.kt`의 `ComingSoon` KDoc을 `/** A2b-3에서 만들 화면 자리(기록 탭). */`로 바꾼다.

- [ ] **Step 2: README 고치기**

`android-core/README.md`의 모듈 표에서 `app` 줄을 아래로 바꾼다.

```markdown
| `app` | 실행용 앱. 하단 탭 홈(오늘의 퀘스트, 펫·보스 카드, 정리 필요)·일정(반복·단발 일정, 취소·되돌리기, 휴가)·칭호(도감, NEW, 대표 칭호). 기록 탭은 A2b-3. 정산 알림. 디자인 토큰은 루트 `design-tokens.json`에서 `tools/gen-tokens/gen_tokens.py`로 생성 |
```

"화면 사진" 절의 코드 블록 아래에 추가한다.

```markdown
- 이름 앞부분으로 화면을 나눈다. `home_*`(홈), `schedule_*`(일정), `codex_*`(칭호), `tier_palettes`(티어 시안).
- 화면 코드를 고치면 다시 찍고, 바뀐 PNG를 눈으로 확인한 뒤 같이 커밋한다.
```

- [ ] **Step 3: 인계 메모 갱신**

`docs/superpowers/notes/2026-11-25-a1-carryover.md`의 "A2b-2로 남은 것" 목록 바로 앞에 아래 블록을 넣는다.

```markdown
> **A2b-2에서 처리함 (2026-11-27)**
> - 일정 탭: 반복 일정 추가·편집·삭제, 단발 일정, 7일 일정 취소·되돌리기, 휴가 켜기·끄기, 7일 뒤 휴가 날짜 고르기
> - 칭호 탭: 10계열 도감, 보유 수와 남은 횟수, NEW(탭을 열 때 정하고 본 것으로 표시), 대표 칭호 고르기
> - 홈: 펫·이번 주 보스 글자 카드, 휴가일 출근 카드(버튼 없음), 정리 필요 [휴가였어](활동이 없는 지난 날만)
> - 취소·휴가 규칙(스펙 D13): 오늘 이후만. 기록 있는 일정은 취소 불가, 기록 있는 날은 휴가 불가, 휴가일 출근 기록 막기
> - 대표 칭호 판단의 쓰기 잠금 줄이기, 지난 날짜 일정 시각 변경 막기
> - 오류 처리(launchSafely, 실패 시 로딩 해제와 메시지), 로딩 표시, 연타 막기(ActionGate)
> - 연출 확인은 화면에 보인 키만, 기록 추가 창 기본 시각은 예정 시각, 다른 곳에서 답한 행동통제 창 닫기, 서비스 지연 생성 테스트
```

그다음 "A2b-2로 남은 것" 제목을 "A2b-3로 남은 것"으로 바꾼다. 목록에서 위 블록에서 처리한 항목은 지우고, 아래 항목만 남긴다.

```markdown
> **A2b-3로 남은 것**
> - 기록 탭(날짜별 원장, 기록 편집 → 음수 XP 알림 경로), 설정 화면(홈 오른쪽 위 톱니: 대표 칭호, 알림 권한, 삼성 배터리 최적화 예외 안내)
> - 매일 00:05 워커(일정 생성 + 안 본 연출 알림), 연출 "본 것" 시점 확정과 티어·펫·보스 키 정책 문서화
> - 폰 설치(USB-C 케이블, 상자에 든 C-to-C로 됨), 실기기 확인(상태바·내비게이션 바 아이콘 색, 알림 권한, 알림 눌러 앱 열기)
> - 갤럭시 Z 플립5 커버 화면 위젯 아이디어
> - 앱 아이콘(런처) 에셋, 서명된 릴리스 빌드(R8)
> - 화면 사진 검증(verifyRoborazzi)을 CI에서 돌리려면 같은 OS 렌더링이나 허용 오차가 필요
> - Android 8.0(API 26)에서 내비게이션 바 아이콘 대비(`SystemBarStyle.light`의 darkScrim)
> - 관찰: 지금은 presenter StateFlow + 쓰기 뒤·1분 새로고침. 탭 사이 변경(일정 탭 → 홈)은 탭을 열 때 새로고침으로 반영한다. Room 무효화 관찰이 필요한지 판단
> - `ChronoDatabase.build()` 이름 검토
> - 디자인 확인 항목(배지 대비, enum 문구, tertiary/error 색, 줄 간격)은 스펙 D14에 따라 Phase B에서 본다
```

- [ ] **Step 4: 전체 확인**

Run:
- `cd android-core && ./gradlew :rules:test :core:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --offline`
- `./gradlew :app:recordRoborazziDebug --offline`
- `cd .. && python3 tools/gen-tokens/gen_tokens.py && git diff --exit-code -- android-core/app/src/main/kotlin/com/chronoquest/app/ui/theme/Tokens.kt`

Expected:
- 테스트 실패 0, 디버그 APK가 만들어진다.
- 토큰 생성 파일에 차이가 없다.
- `app/screenshots/`에 PNG가 15장 있다.
  - 홈 9장: `home_first_day`, `home_in_progress`, `home_cleanup_celebration`, `home_confirm_early`, `home_confirm_early_locked`, `home_reschedule`, `home_vacation_day`, `home_confirm_vacation`, `home_loading`
  - 일정 3장, 칭호 2장, `tier_palettes` 1장

화면 사진을 다시 찍었을 때 git 차이가 생기면, 해당 PNG를 Read로 보고 의도한 변화인지 확인한다.

- [ ] **Step 5: 커밋**

```bash
git add android-core/app android-core/README.md docs/superpowers/notes/2026-11-25-a1-carryover.md
git commit -m "feat(app): 하단 탭에 일정·칭호 연결, README와 인계 메모 갱신" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## 스펙 대조 (자체 점검)

| 스펙·메모 항목 | 작업 |
|---|---|
| 7.2 일정(반복 템플릿 목록·편집, 단발 추가, 휴가) | T5, T6 (캘린더 가져오기는 스펙 13 M4라 A4) |
| 5.4·5.7 칭호창 NEW, 대표 칭호 직접 고르기 | T7 |
| 5.8·5.9 펫·보스 (사용자 결정: 홈 글자 카드) | T3 |
| D13 취소·휴가 규칙, 깜빡한 휴가 | T1, T3, T5 |
| 인계 메모 A2b-2: 휴가일 카드 버튼, 지난 일정 시각 변경, 대표 칭호 잠금 | T1, T2, T3 |
| 인계 메모 A2b-2: 오류 처리, 로딩, 연타, 연출 키, 기록 추가 기본 시각, 행동통제 창, 지연 생성 테스트 | T3, T4 |
| 설정 화면, 기록 탭, 워커, 아이콘, 릴리스, 폰 설치 | A2b-3 (범위 밖) |
