package com.chronoquest.app.home

import com.chronoquest.core.domain.Celebration
import com.chronoquest.rules.model.Tier
import java.time.LocalDate
import java.time.LocalTime

enum class CardStatus { WAITING, DEPARTED, EARLY, ON_TIME, CLOSE, LATE, CANCELED }

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
)

sealed interface PendingAction {
    data object Depart : PendingAction
    data class Arrive(val safe: Boolean) : PendingAction
}

sealed interface HomeDialog {
    data class AskSafety(val questId: String) : HomeDialog
    data class ConfirmEarly(val questId: String, val action: PendingAction, val minutesEarly: Long) : HomeDialog
    data class Reschedule(val questId: String, val action: PendingAction, val due: LocalTime, val depart: LocalTime) : HomeDialog
    data class AskSelfControl(val date: LocalDate) : HomeDialog
    data class AddArrival(val questId: String, val date: LocalDate) : HomeDialog
}

data class HomeUiState(
    val loading: Boolean = true,
    val tier: Tier = Tier.BRONZE,
    val representativeTitle: String = "미각성자",
    val xp: Int = 0,
    val nextTierXp: Int? = Tier.SILVER.minXp,
    val quests: List<QuestCard> = emptyList(),
    val cleanup: List<CleanupCard> = emptyList(),
    val celebrations: List<Celebration> = emptyList(),
    val dialog: HomeDialog? = null,
    val message: String? = null,
)
