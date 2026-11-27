package com.chronoquest.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.chronoquest.app.ui.theme.ChronoTokens

enum class MainTab(val label: String, val icon: ImageVector) {
    HOME("홈", Icons.Filled.Home),
    SCHEDULE("일정", Icons.Filled.DateRange),
    CODEX("칭호", Icons.Filled.Star),
    LOG("기록", Icons.Filled.Edit),
}

@Composable
fun ChronoScaffold(selectedTab: MainTab, onTabSelected: (MainTab) -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        containerColor = ChronoTokens.Colors.Bg1,
        bottomBar = {
            NavigationBar(containerColor = ChronoTokens.Colors.Bg1) {
                MainTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ChronoTokens.Colors.Blue500,
                            selectedTextColor = ChronoTokens.Colors.Blue500,
                            indicatorColor = ChronoTokens.Colors.Blue50,
                            unselectedIconColor = ChronoTokens.Colors.Text2,
                            unselectedTextColor = ChronoTokens.Colors.Text2,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) { content() }
    }
}

/** A2b-3에서 만들 화면 자리(기록 탭). */
@Composable
fun ComingSoon(tab: MainTab) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("${tab.label} 화면은 다음 단계에서 만들어.", style = MaterialTheme.typography.bodyLarge, color = ChronoTokens.Colors.Text2)
    }
}
