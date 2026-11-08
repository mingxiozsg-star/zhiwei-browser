package com.java.myapplication.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.java.myapplication.BrowserViewModel.PageCue

/**
 * 网页提示层：命中「英文页面 / 长文页面」时弹出的确认卡片。
 *
 * 读屏开启时改走系统 AlertDialog —— 焦点、朗读、按钮顺序交给平台接管，
 * 不会像自绘卡片那样让 TalkBack 焦点停滞在背景页面上。
 */
@Composable
fun PageCueHost(
    cue: PageCue,
    screenReader: Boolean,
    onConfirm: () -> Unit,
    onMute: () -> Unit,
    onDismiss: () -> Unit,
) {
    val view = LocalView.current
    // 读屏用户：弹出时主动播报一次，防止焦点落在别处听不到
    LaunchedEffect(cue) {
        if (screenReader) A11y.announce(view, "${cue.title}，${cue.body} 确认：${cue.confirm}")
    }

    if (screenReader) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(cue.title) },
            text = { Text(cue.body) },
            confirmButton = { TextButton(onClick = onConfirm) { Text(cue.confirm) } },
            dismissButton = {
                Row {
                    TextButton(onClick = onMute) { Text("本网站不再提示") }
                    TextButton(onClick = onDismiss) { Text("先不用") }
                }
            }
        )
    } else {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth().padding(12.dp).a11yLive()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(cue.title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        cue.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onConfirm) { Text(cue.confirm) }
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = onMute) { Text("本网站不再提示") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("先不用") }
                    }
                }
            }
        }
    }
}
