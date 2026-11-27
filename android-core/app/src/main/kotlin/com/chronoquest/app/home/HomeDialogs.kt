package com.chronoquest.app.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties
import com.chronoquest.app.ui.theme.ChronoTokens
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val VACATION_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN)

@Composable
fun HomeDialogHost(dialog: HomeDialog?, actions: HomeActions) {
    when (dialog) {
        null -> Unit
        is HomeDialog.AskSafety -> AlertDialog(
            onDismissRequest = actions.onDismissDialog,
            title = { Text("안전하게 왔어?") },
            text = { Text("신호를 지키고 뛰지 않고 왔으면 안전이야.") },
            confirmButton = { TextButton(onClick = { actions.onSafetyAnswered(dialog.questId, true) }) { Text("안전하게 옴") } },
            dismissButton = { TextButton(onClick = { actions.onSafetyAnswered(dialog.questId, false) }) { Text("위험했어") } },
        )
        is HomeDialog.ConfirmEarly -> if (dialog.canReschedule) {
            AlertDialog(
                onDismissRequest = actions.onDismissDialog,
                title = { Text("예정보다 ${dialog.minutesEarly}분 일러") },
                text = { Text("일정 시각이 바뀌었으면 일정을 고치고 기록해. 아니면 그대로 기록할 수 있어.") },
                confirmButton = { TextButton(onClick = { actions.onEditSchedule(dialog) }) { Text("일정 수정") } },
                dismissButton = { TextButton(onClick = { actions.onRecordAnyway(dialog) }) { Text("그대로 기록") } },
            )
        } else {
            AlertDialog(
                onDismissRequest = actions.onDismissDialog,
                title = { Text("예정보다 ${dialog.minutesEarly}분 일러") },
                text = { Text("이미 출발을 기록해서 일정 시각은 못 바꿔. 그대로 기록할까?") },
                confirmButton = { TextButton(onClick = { actions.onRecordAnyway(dialog) }) { Text("그대로 기록") } },
                dismissButton = { TextButton(onClick = actions.onDismissDialog) { Text("닫기") } },
            )
        }
        is HomeDialog.Reschedule -> RescheduleDialog(dialog, actions)
        is HomeDialog.AskSelfControl -> AlertDialog(
            onDismissRequest = actions.onDismissDialog,
            properties = DialogProperties(dismissOnClickOutside = false),
            title = { Text("행동통제") },
            text = { Text("출발 전에 필수 준비 말고 딴짓 안 했지?") },
            confirmButton = { TextButton(onClick = { actions.onSelfControlAnswered(dialog.date, true) }) { Text("응") } },
            dismissButton = { TextButton(onClick = { actions.onSelfControlAnswered(dialog.date, false) }) { Text("했어") } },
        )
        is HomeDialog.AddArrival -> AddArrivalDialog(dialog, actions)
        is HomeDialog.ConfirmVacation -> AlertDialog(
            onDismissRequest = actions.onDismissDialog,
            title = { Text("그날 휴가였어?") },
            text = { Text("${dialog.date.format(VACATION_DATE)}에는 기록이 하나도 없어. 휴가였으면 그날 출근은 판정에서 빠져.") },
            confirmButton = { TextButton(onClick = { actions.onVacationConfirmed(dialog) }) { Text("휴가였어") } },
            dismissButton = { TextButton(onClick = actions.onDismissDialog) { Text("아니야") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RescheduleDialog(dialog: HomeDialog.Reschedule, actions: HomeActions) {
    val due = rememberTimePickerState(dialog.due.hour, dialog.due.minute, is24Hour = true)
    val depart = rememberTimePickerState(dialog.depart.hour, dialog.depart.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = actions.onDismissDialog,
        title = { Text("일정 시각 바꾸기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Md)) {
                Text("도착 예정", style = MaterialTheme.typography.labelLarge, color = ChronoTokens.Colors.Text2)
                TimeInput(state = due)
                Text("목표 출발", style = MaterialTheme.typography.labelLarge, color = ChronoTokens.Colors.Text2)
                TimeInput(state = depart)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                actions.onRescheduleConfirmed(dialog, LocalTime.of(due.hour, due.minute), LocalTime.of(depart.hour, depart.minute))
            }) { Text("저장하고 기록") }
        },
        dismissButton = { TextButton(onClick = actions.onDismissDialog) { Text("닫기") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddArrivalDialog(dialog: HomeDialog.AddArrival, actions: HomeActions) {
    val time = rememberTimePickerState(dialog.due.hour, dialog.due.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = actions.onDismissDialog,
        title = { Text("도착 시각 넣기") },
        text = { TimeInput(state = time) },
        confirmButton = {
            TextButton(onClick = { actions.onArrivalEntered(dialog, LocalTime.of(time.hour, time.minute), true) }) {
                Text("안전하게 도착")
            }
        },
        dismissButton = {
            TextButton(onClick = { actions.onArrivalEntered(dialog, LocalTime.of(time.hour, time.minute), false) }) {
                Text("위험했어")
            }
        },
    )
}
