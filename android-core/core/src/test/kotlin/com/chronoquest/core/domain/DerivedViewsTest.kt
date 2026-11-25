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
