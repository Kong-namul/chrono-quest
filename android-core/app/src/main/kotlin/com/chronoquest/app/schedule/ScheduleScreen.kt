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
