package com.chronoquest.rules.engine

import com.chronoquest.rules.at
import com.chronoquest.rules.judge.ArrivalVerdict
import com.chronoquest.rules.close
import com.chronoquest.rules.dailyOthers
import com.chronoquest.rules.day
import com.chronoquest.rules.early
import com.chronoquest.rules.other
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
    fun `정시와 아슬아슬이 섞이면 보통 아슬아슬만 있어도 보통`() {
        val a = work("2026-11-25")
        val b = other("2026-11-25", "19:00")
        val mixed = engine.compute(ledger(listOf(a, b), listOf(a.onTime(), b.close())), at("2026-11-25", "20:00"))
        val closeOnly = engine.compute(ledger(listOf(a), listOf(a.close())), at("2026-11-25", "18:00"))
        assertEquals(PetMood.NORMAL, mixed.pet.mood)
        assertEquals(PetMood.NORMAL, closeOnly.pet.mood)
        assertEquals(PetMood.NORMAL, PetTracker.moodFor(listOf(ArrivalVerdict.ON_TIME, ArrivalVerdict.CLOSE)))
        assertEquals(PetMood.NORMAL, PetTracker.moodFor(emptyList()))
    }

    @Test
    fun `조기만 있으면 신남`() {
        val q = work("2026-11-25")
        val s = engine.compute(ledger(listOf(q), listOf(q.early())), at("2026-11-25", "18:00"))
        assertEquals(PetMood.HAPPY, s.pet.mood)
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
