package com.chronoquest.app.codex

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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

/** 칭호 화면 부품 고유 크기. 간격·모서리는 ChronoTokens.Space·Radius를 쓴다. */
private val LOCK_ICON_SIZE = 18.dp
private val BORDER_WIDTH = 1.dp

/** 칭호 화면이 위로 올리는 동작들. 기본값은 아무것도 안 해서 화면 사진에 그대로 쓴다. */
data class CodexActions(
    val onTitleSelected: (Int) -> Unit = {},
    val onSetRepresentative: (Int) -> Unit = {},
    val onDismissDetail: () -> Unit = {},
    val onDismissMessage: () -> Unit = {},
)

@Composable
fun CodexScreen(state: CodexUiState, actions: CodexActions, modifier: Modifier = Modifier) {
    if (state.loading) {
        LoadingText(modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(ChronoTokens.Colors.Bg1),
        contentPadding = PaddingValues(horizontal = ChronoTokens.Space.Lg, vertical = ChronoTokens.Space.Xl),
        verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Sm),
    ) {
        item { CodexHeader(state) }
        state.sections.forEach { section ->
            item(key = "s-${section.name}") { SectionHeader(section) }
            items(section.titles, key = { "t-${it.id}" }) { TitleItem(it, actions) }
        }
    }
    state.detail?.let { TitleDetailDialog(it, actions) }
    state.message?.let { MessageDialog(it, actions.onDismissMessage) }
}

@Composable
private fun CodexHeader(state: CodexUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Xs)) {
        Text("칭호 도감 ${state.collected}/${state.total}", style = MaterialTheme.typography.headlineSmall, color = ChronoTokens.Colors.Text1)
        Text("대표 칭호: ${state.representative}", style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Text2)
    }
}

@Composable
private fun SectionHeader(section: SeriesSection) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = ChronoTokens.Space.Lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(section.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = ChronoTokens.Colors.Text1)
        Text("${section.owned}/${section.titles.size}", style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Text2)
    }
}

@Composable
private fun TitleItem(row: TitleRow, actions: CodexActions) {
    val owned = row.owned > 0
    val shape = RoundedCornerShape(ChronoTokens.Radius.Md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(BORDER_WIDTH, ChronoTokens.Colors.Border, shape)
            .clickable { actions.onTitleSelected(row.id) }
            .padding(horizontal = ChronoTokens.Space.Lg, vertical = ChronoTokens.Space.Md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ChronoTokens.Space.Sm),
    ) {
        if (!owned) {
            Icon(Icons.Filled.Lock, contentDescription = "잠김", modifier = Modifier.size(LOCK_ICON_SIZE), tint = ChronoTokens.Colors.Text2)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (owned) ChronoTokens.Colors.Text1 else ChronoTokens.Colors.Text2,
            )
            Text("${row.condition} · ${row.progress}", style = MaterialTheme.typography.bodyMedium, color = ChronoTokens.Colors.Text2)
        }
        if (row.isNew) Chip("NEW", ChronoTokens.Colors.Blue50, ChronoTokens.Colors.Blue700)
        if (row.isRepresentative) Chip("대표", ChronoTokens.Colors.Yellow50, ChronoTokens.Colors.Gold700)
    }
}

@Composable
private fun TitleDetailDialog(row: TitleRow, actions: CodexActions) {
    AlertDialog(
        onDismissRequest = actions.onDismissDetail,
        title = { Text(row.name) },
        text = { Text("${row.condition}\n${row.progress}") },
        confirmButton = {
            if (row.owned > 0 && !row.isRepresentative) {
                TextButton(onClick = { actions.onSetRepresentative(row.id) }) { Text("대표 칭호로") }
            }
        },
        dismissButton = { TextButton(onClick = actions.onDismissDetail) { Text("닫기") } },
    )
}
