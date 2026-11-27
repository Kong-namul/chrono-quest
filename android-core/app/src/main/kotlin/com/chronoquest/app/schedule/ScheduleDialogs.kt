package com.chronoquest.app.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chronoquest.app.ui.theme.ChronoTokens
import com.chronoquest.rules.model.QuestKind
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * 이름 입력칸 너비. Robolectric에서 `AlertDialog` 안의 `OutlinedTextField`가 기본 최소 너비(280dp)와
 * 창 너비가 맞지 않아 측정이 끝나지 않는(waitForIdle 시간 초과) 문제를 피하려고 고정한다.
 * 실기기에서 다시 확인해 `fillMaxWidth()`로 바꿀 수 있는지 본다.
 */
private val NAME_FIELD_WIDTH = 232.dp

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
                NameField(title) { title = it }
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
                TimeFields(due, depart)
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
                        due = due.toLocalTime(),
                        depart = depart.toLocalTime(),
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
    var date by remember(dialog) { mutableStateOf(maxOf(initial.date, today)) }
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
                    NameField(title) { title = it }
                    KindPicker(kind) { kind = it }
                    FieldLabel("날짜")
                    TextButton(onClick = { choosingDate = true }) { Text(date.format(SchedulePresenter.DATE)) }
                    TimeFields(due, depart)
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
                            due = due.toLocalTime(),
                            depart = depart.toLocalTime(),
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

/** 날짜 고르기. 오늘 이전은 고를 수 없다(스펙 D13). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateChooser(initial: LocalDate, today: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = initial.toUtcMillis(),
        selectableDates = FromToday(today),
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

@Composable
private fun NameField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.width(NAME_FIELD_WIDTH),
        label = { Text("이름") },
        singleLine = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeFields(due: TimePickerState, depart: TimePickerState) {
    FieldLabel("도착 예정")
    TimeInput(state = due)
    FieldLabel("목표 출발")
    TimeInput(state = depart)
}

@OptIn(ExperimentalMaterial3Api::class)
private fun TimePickerState.toLocalTime(): LocalTime = LocalTime.of(hour, minute)
