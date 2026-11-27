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
