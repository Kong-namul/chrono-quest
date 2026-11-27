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
