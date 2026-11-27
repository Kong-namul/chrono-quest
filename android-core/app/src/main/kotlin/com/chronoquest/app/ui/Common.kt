package com.chronoquest.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.chronoquest.app.ui.theme.ChronoTokens

/** 작은 상태 표시. 배경·글자색은 토큰만 넘긴다. */
@Composable
fun Chip(label: String, background: Color, foreground: Color) {
    Text(
        label,
        modifier = Modifier
            .background(background, RoundedCornerShape(ChronoTokens.Radius.Sm))
            .padding(horizontal = ChronoTokens.Space.Md, vertical = ChronoTokens.Space.Xs),
        style = MaterialTheme.typography.labelLarge,
        color = foreground,
    )
}

/**
 * 처음 불러오는 동안 보여주는 글자.
 * 무한 애니메이션 표시는 화면 사진 테스트의 대기(waitForIdle)를 끝나지 않게 할 수 있어서 쓰지 않는다.
 */
@Composable
fun LoadingText(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(ChronoTokens.Colors.Bg1), contentAlignment = Alignment.Center) {
        Text("불러오는 중…", style = MaterialTheme.typography.bodyLarge, color = ChronoTokens.Colors.Text2)
    }
}

/** 한 줄 안내 창. */
@Composable
fun MessageDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } },
    )
}
