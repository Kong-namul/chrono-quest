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
    /** 기록이 있으면 취소할 수 없어 [취소]를 숨긴다(스펙 D13). */
    val canCancel: Boolean = true,
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
