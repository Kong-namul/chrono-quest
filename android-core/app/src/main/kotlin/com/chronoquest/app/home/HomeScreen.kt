package com.chronoquest.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chronoquest.app.ui.Chip
import com.chronoquest.app.ui.LoadingText
import com.chronoquest.app.ui.MessageDialog
import com.chronoquest.app.ui.theme.ChronoTokens
import com.chronoquest.app.ui.theme.onTierColor
import com.chronoquest.app.ui.theme.tierColor
import com.chronoquest.core.domain.Celebration
import com.chronoquest.core.domain.CelebrationKind
import java.time.LocalDate
import java.time.LocalTime

/** 홈 화면 부품 고유 크기. 간격·모서리는 ChronoTokens.Space·Radius를 쓴다. */
private val EMBLEM_SIZE = 56.dp
private val XP_BAR_HEIGHT = 8.dp
private val BORDER_WIDTH = 1.dp

/** 홈 화면이 위로 올리는 동작들. 기본값은 아무것도 안 해서 화면 사진·미리보기에 그대로 쓴다. */
data class HomeActions(
    val onDepart: (String) -> Unit = {},
    val onArrive: (String) -> Unit = {},
    val onSafetyAnswered: (String, Boolean) -> Unit = { _, _ -> },
    val onRecordAnyway: (HomeDialog.ConfirmEarly) -> Unit = {},
    val onEditSchedule: (HomeDialog.ConfirmEarly) -> Unit = {},
    val onRescheduleConfirmed: (HomeDialog.Reschedule, LocalTime, LocalTime) -> Unit = { _, _, _ -> },
    val onSelfControlAnswered: (LocalDate, Boolean) -> Unit = { _, _ -> },
    val onCleanupLate: (String) -> Unit = {},
    val onCleanupCancel: (String) -> Unit = {},
    val onCleanupAddArrival: (String) -> Unit = {},
    val onArrivalEntered: (HomeDialog.AddArrival, LocalTime, Boolean) -> Unit = { _, _, _ -> },
    val onCleanupVacation: (String) -> Unit = {},
    val onVacationConfirmed: (HomeDialog.ConfirmVacation) -> Unit = {},
    val onDismissDialog: () -> Unit = {},
    val onDismissMessage: () -> Unit = {},
    val onCelebrationsSeen: (List<String>) -> Unit = {},
)

@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    if (state.loading) {
        LoadingText(modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(ChronoTokens.Colors.Bg1),
        contentPadding = PaddingValues(horizontal = ChronoTokens.Space.Lg, vertical = ChronoTokens.Space.Xl),
        verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Md),
    ) {
        item { Header(state) }
        item { CompanionRow(state.pet, state.boss) }
        if (state.celebrations.isNotEmpty()) {
            item { CelebrationCard(state.celebrations) { actions.onCelebrationsSeen(state.celebrations.map { it.key }) } }
        }
        if (state.cleanup.isNotEmpty()) {
            item { SectionTitle("정리 필요") }
            items(state.cleanup, key = { "cleanup-${it.id}" }) { CleanupRow(it, actions) }
        }
        item { SectionTitle("오늘의 퀘스트") }
        if (state.quests.isEmpty()) {
            item {
                Text(
                    "오늘은 일정이 없어.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChronoTokens.Colors.Text2,
                )
            }
        } else {
            items(state.quests, key = { "quest-${it.id}" }) { QuestRow(it, actions) }
        }
    }
    HomeDialogHost(state.dialog, actions)
    state.message?.let { MessageDialog(it, actions.onDismissMessage) }
}

@Composable
private fun Header(state: HomeUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Md)) {
        Text("SEASON 01", style = MaterialTheme.typography.labelLarge, color = ChronoTokens.Colors.Text2)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(EMBLEM_SIZE).background(tierColor(state.tier), RoundedCornerShape(ChronoTokens.Radius.Lg)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    state.tier.name.take(1),
                    style = MaterialTheme.typography.headlineMedium,
                    color = onTierColor(state.tier),
                )
            }
            Spacer(Modifier.width(ChronoTokens.Space.Md))
            Column {
                Text(state.tier.name, style = MaterialTheme.typography.labelLarge, color = ChronoTokens.Colors.Text2)
                Text(state.representativeTitle, style = MaterialTheme.typography.titleLarge, color = ChronoTokens.Colors.Text1)
            }
        }
        val next = state.nextTierXp
        val progress = if (next == null) 1f else (state.xp.toFloat() / next).coerceIn(0f, 1f)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(XP_BAR_HEIGHT),
            color = ChronoTokens.Colors.Gold500,
            trackColor = ChronoTokens.Colors.Yellow50,
            drawStopIndicator = {},
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${state.xp} XP", style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Text2)
            if (next != null) {
                Text("다음 티어 $next XP", style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Text2)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = ChronoTokens.Space.Sm),
        style = MaterialTheme.typography.titleMedium,
        color = ChronoTokens.Colors.Text1,
    )
}

@Composable
private fun QuestRow(card: QuestCard, actions: HomeActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BORDER_WIDTH, ChronoTokens.Colors.Border, RoundedCornerShape(ChronoTokens.Radius.Lg))
            .padding(ChronoTokens.Space.Lg),
        verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(card.title, style = MaterialTheme.typography.titleMedium, color = ChronoTokens.Colors.Text1)
                Text(
                    "출발 ${card.departLabel} · 도착 ${card.dueLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChronoTokens.Colors.Text2,
                )
            }
            StatusChip(card.status)
        }
        if (card.canDepart || card.canArrive) {
            Row(horizontalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Sm)) {
                if (card.canDepart) Button(onClick = { actions.onDepart(card.id) }) { Text("출발") }
                if (card.canArrive) OutlinedButton(onClick = { actions.onArrive(card.id) }) { Text("도착") }
            }
        }
    }
}

@Composable
private fun StatusChip(status: CardStatus) {
    val (label, background, foreground) = when (status) {
        CardStatus.WAITING -> Triple("대기", ChronoTokens.Colors.Bg2, ChronoTokens.Colors.Text2)
        CardStatus.DEPARTED -> Triple("이동 중", ChronoTokens.Colors.Blue50, ChronoTokens.Colors.Blue700)
        CardStatus.EARLY -> Triple("조기", ChronoTokens.Colors.Blue50, ChronoTokens.Colors.Blue700)
        CardStatus.ON_TIME -> Triple("정시", ChronoTokens.Colors.Blue50, ChronoTokens.Colors.Blue700)
        CardStatus.CLOSE -> Triple("아슬아슬", ChronoTokens.Colors.Yellow50, ChronoTokens.Colors.Gold700)
        CardStatus.LATE -> Triple("지각", ChronoTokens.Colors.Bg2, ChronoTokens.Colors.Text1)
        CardStatus.CANCELED -> Triple("취소", ChronoTokens.Colors.Bg2, ChronoTokens.Colors.Text2)
        CardStatus.VACATION -> Triple("휴가", ChronoTokens.Colors.Bg2, ChronoTokens.Colors.Text2)
    }
    Chip(label, background, foreground)
}

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
            if (card.canCancel) {
                TextButton(onClick = { actions.onCleanupCancel(card.id) }) { Text("취소") }
            }
            if (card.canBeVacation) {
                TextButton(onClick = { actions.onCleanupVacation(card.id) }) { Text("휴가였어") }
            }
        }
    }
}

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

@Composable
private fun CelebrationCard(celebrations: List<Celebration>, onSeen: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChronoTokens.Colors.Yellow50, RoundedCornerShape(ChronoTokens.Radius.Lg))
            .padding(ChronoTokens.Space.Lg),
        verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Xs),
    ) {
        Text("새로 얻은 것", style = MaterialTheme.typography.titleMedium, color = ChronoTokens.Colors.Text1)
        celebrations.forEach {
            Text(celebrationText(it), style = MaterialTheme.typography.bodyLarge, color = ChronoTokens.Colors.Text1)
        }
        TextButton(onClick = onSeen) { Text("확인") }
    }
}

/** 연출 한 줄 문구. */
fun celebrationText(c: Celebration): String = when (c.kind) {
    CelebrationKind.TITLE -> "새 칭호: ${c.detail}"
    CelebrationKind.PERIOD_BONUS -> "기간 보너스 +${c.xp} XP"
    CelebrationKind.PET_EVOLVED -> "펫 진화: ${c.detail}"
    CelebrationKind.BOSS_RESULT -> "주간 보스 결과: ${c.detail}"
    CelebrationKind.TIER_UP -> "티어 승급: ${c.key.removePrefix("tier:")}"
}
