package com.java.myapplication.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.java.myapplication.DlStatus
import com.java.myapplication.DlTask
import com.java.myapplication.Downloader
import com.java.myapplication.Settings
import com.java.myapplication.Store
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun humanSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val units = arrayOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble(); var i = 0
    while (v >= 1024 && i < units.size - 1) { v /= 1024; i++ }
    return String.format(Locale.US, "%.1f %s", v, units[i])
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    val active = Downloader.tasks.filter { !it.finished && it.status != DlStatus.CANCELED }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("下载") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    if (active.isNotEmpty()) {
                        TextButton(onClick = { Downloader.clearFinished() }) { Text("清理已完成") }
                    } else if (Store.downloads.isNotEmpty()) {
                        TextButton(onClick = { Store.clearDownloads() }) { Text("清空") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                when (Settings.dlEngine) {
                    1 -> "下载引擎：系统下载器"
                    2 -> "下载引擎：${Settings.dlExternalLabel.ifBlank { "第三方下载器" }}"
                    else -> "下载引擎：内置（保存到 内部存储/下载/知微浏览器/）"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (active.isNotEmpty()) {
                Text(
                    "进行中（${active.size}）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 6.dp)
                )
                LazyColumn {
                    items(active, key = { it.id }) { task ->
                        DownloadTaskCard(task, context)
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            LazyColumn(Modifier.weight(1f)) {
                item {
                    Text(
                        "下载记录",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp)
                    )
                }
                if (Store.downloads.isEmpty() && active.isEmpty()) {
                    item {
                        Text(
                            "暂无下载记录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
                items(Store.downloads, key = { it.time }) { d ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable {
                                runCatching {
                                    context.startActivity(android.content.Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS))
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(d.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                fmt.format(Date(d.time)) + "  ·  " + d.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { Store.removeDownload(d) }) {
                            Icon(Icons.Filled.Delete, "删除记录", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** 进行中/已暂停任务卡片：进度条 + 速度 + 暂停/继续/安装 */
@Composable
fun DownloadTaskCard(task: DlTask, context: android.content.Context, compact: Boolean = false) {
    val animated by animateFloatAsState(
        targetValue = task.progress,
        animationSpec = tween(220),
        label = "dlProgress"
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (compact) 0.dp else 14.dp)
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        task.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val statusText = when (task.status) {
                        DlStatus.DONE -> "已完成"
                        DlStatus.FAILED -> "失败：${task.message ?: "未知错误"}"
                        DlStatus.PAUSED -> "已暂停"
                        DlStatus.CANCELED -> "已取消"
                        DlStatus.PENDING -> "连接中…"
                        else -> "${humanSize(task.done)} / ${humanSize(task.total)}  ·  ${humanSize(task.speed)}/s"
                    }
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                when {
                    task.running -> IconButton(onClick = { Downloader.pause(task) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, "暂停", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    task.status == DlStatus.PAUSED ->
                        TextButton(onClick = { Downloader.resume(context, task) }) { Text("继续") }
                    task.status == DlStatus.FAILED ->
                        TextButton(onClick = { Downloader.retry(context, task) }) { Text("重试") }
                    task.finished -> {
                        if (task.displayName.endsWith(".apk", true)) {
                            TextButton(onClick = { Downloader.installApk(context, task) }) { Text("安装") }
                        }
                        TextButton(onClick = { Downloader.remove(task) }) { Text("移除") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (task.total > 0) {
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                )
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        "${(animated * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                )
            }
        }
    }
}