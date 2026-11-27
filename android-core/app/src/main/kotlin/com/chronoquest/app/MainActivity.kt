package com.chronoquest.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chronoquest.app.codex.CodexActions
import com.chronoquest.app.codex.CodexScreen
import com.chronoquest.app.codex.CodexViewModel
import com.chronoquest.app.home.HomeActions
import com.chronoquest.app.home.HomeScreen
import com.chronoquest.app.home.HomeViewModel
import com.chronoquest.app.schedule.ScheduleActions
import com.chronoquest.app.schedule.ScheduleScreen
import com.chronoquest.app.schedule.ScheduleViewModel
import com.chronoquest.app.ui.ChronoScaffold
import com.chronoquest.app.ui.ComingSoon
import com.chronoquest.app.ui.MainTab
import com.chronoquest.app.ui.theme.ChronoTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        val container = (application as ChronoApp).container
        setContent {
            ChronoTheme {
                val home: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
                val schedule: ScheduleViewModel = viewModel(factory = ScheduleViewModel.factory(container))
                val codex: CodexViewModel = viewModel(factory = CodexViewModel.factory(container))
                ChronoMainScreen(home, schedule, codex)
            }
        }
    }
}

/**
 * 하단 탭 화면. 앱이 보이는 동안 지금 보는 탭만 1분마다 새로고침한다(인계 메모 A2b).
 * 탭을 바꾸면 그 탭을 바로 한 번 읽는다. 칭호 탭은 열 때 NEW를 정한다.
 */
@Composable
fun ChronoMainScreen(home: HomeViewModel, schedule: ScheduleViewModel, codex: CodexViewModel) {
    var tab by rememberSaveable { mutableStateOf(MainTab.HOME) }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner, tab) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var entering = true
            while (true) {
                when (tab) {
                    MainTab.HOME -> home.refresh()
                    MainTab.SCHEDULE -> schedule.refresh()
                    MainTab.CODEX -> if (entering) codex.onEnter() else codex.refresh()
                    MainTab.LOG -> Unit
                }
                entering = false
                delay(60_000)
            }
        }
    }
    ChronoScaffold(selectedTab = tab, onTabSelected = { tab = it }) {
        when (tab) {
            MainTab.HOME -> {
                val state by home.state.collectAsStateWithLifecycle()
                HomeScreen(state = state, actions = remember(home) { homeActions(home) })
            }
            MainTab.SCHEDULE -> {
                val state by schedule.state.collectAsStateWithLifecycle()
                ScheduleScreen(state = state, actions = remember(schedule) { scheduleActions(schedule) })
            }
            MainTab.CODEX -> {
                val state by codex.state.collectAsStateWithLifecycle()
                CodexScreen(state = state, actions = remember(codex) { codexActions(codex) })
            }
            MainTab.LOG -> ComingSoon(tab)
        }
    }
}

private fun homeActions(vm: HomeViewModel) = HomeActions(
    onDepart = vm::onDepart,
    onArrive = vm::onArrive,
    onSafetyAnswered = vm::onSafetyAnswered,
    onRecordAnyway = vm::onRecordAnyway,
    onEditSchedule = vm::onEditSchedule,
    onRescheduleConfirmed = vm::onRescheduleConfirmed,
    onSelfControlAnswered = vm::onSelfControlAnswered,
    onCleanupLate = vm::onCleanupLate,
    onCleanupCancel = vm::onCleanupCancel,
    onCleanupAddArrival = vm::onCleanupAddArrival,
    onArrivalEntered = vm::onArrivalEntered,
    onCleanupVacation = vm::onCleanupVacation,
    onVacationConfirmed = vm::onVacationConfirmed,
    onDismissDialog = vm::dismissDialog,
    onDismissMessage = vm::dismissMessage,
    onCelebrationsSeen = vm::onCelebrationsSeen,
)

private fun scheduleActions(vm: ScheduleViewModel) = ScheduleActions(
    onAddTemplate = vm::onAddTemplate,
    onEditTemplate = vm::onEditTemplate,
    onTemplateSaved = vm::onTemplateSaved,
    onDeleteTemplateRequested = vm::onDeleteTemplateRequested,
    onDeleteTemplateConfirmed = vm::onDeleteTemplateConfirmed,
    onAddOneOff = vm::onAddOneOff,
    onOneOffSaved = vm::onOneOffSaved,
    onCancelQuest = vm::onCancelQuest,
    onRestoreQuest = vm::onRestoreQuest,
    onVacationToggled = vm::onVacationToggled,
    onPickVacationDate = vm::onPickVacationDate,
    onVacationDatePicked = vm::onVacationDatePicked,
    onDismissDialog = vm::dismissDialog,
    onDismissMessage = vm::dismissMessage,
)

private fun codexActions(vm: CodexViewModel) = CodexActions(
    onTitleSelected = vm::onTitleSelected,
    onSetRepresentative = vm::onSetRepresentative,
    onDismissDetail = vm::dismissDetail,
    onDismissMessage = vm::dismissMessage,
)
