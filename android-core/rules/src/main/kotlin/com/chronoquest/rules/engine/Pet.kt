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
            todayVerdicts.isNotEmpty() && todayVerdicts.all { it.isOnTime } -> PetMood.HAPPY
            else -> PetMood.NORMAL
        }
    }
}
