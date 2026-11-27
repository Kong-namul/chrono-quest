package com.chronoquest.app

import android.Manifest
import android.graphics.Color
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import com.chronoquest.app.home.HomeActions
import com.chronoquest.app.home.HomeScreen
import com.chronoquest.app.home.HomeViewModel
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
                val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
                ChronoMainScreen(homeViewModel)
            }
        }
    }
}

/** 앱이 보이는 동안 1분마다 새로고침한다(시간이 지나서 생기는 보류·보너스를 반영, 인계 메모 A2b). */
@Composable
fun ChronoMainScreen(viewModel: HomeViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
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
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                viewModel.refresh()
                delay(60_000)
            }
        }
    }
    ChronoScaffold(selectedTab = tab, onTabSelected = { tab = it }) {
        when (tab) {
            MainTab.HOME -> HomeScreen(state = state, actions = remember(viewModel) { homeActions(viewModel) })
            else -> ComingSoon(tab)
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
