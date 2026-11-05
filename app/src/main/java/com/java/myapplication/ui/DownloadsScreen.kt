package com.java.myapplication.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.java.myapplication.DlStatus
import com.java.myapplication.DlTask
import com.java.myapplication.DownloadItem
import com.java.myapplication.Downloader
import com.java.myapplication.Settings
import com.java.myapplication.Store
import com.java.myapplication.UpdateChecker
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

private fun copyToClipboard(context: Context, text: String, tip: String) {
    runCatching {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("text", text))
        Toast.makeText(context, tip, Toast.LENGTH_SHORT).show()
    }
}

/** 内置查看：有落点就直接交给内置查看器，没有则提示重新下载 */
private fun openInternal(
    context: Context,
    uri: String?,
    path: String?,
    name: String,
    mime: String?,
    onOpenFile: (FileTarget) -> Unit
) {
    val target = uri?.takeIf { it.isNotBlank() }
        ?: path?.takeIf { it.isNotBlank() }?.let { Downloader.uriOf(context, null, it)?.toString() }
    if (target == null) {
        Toast.makeText(context, "文件已被移动或删除，请重新下载", Toast.LENGTH_SHORT).show()
        return
    }
    // 落点已在下载完成时回填进下载记录
    onOpenFile(FileTarget(target, name, mime))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit, onOpenFile: (FileTarget) -> Unit = {}) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    val active = Downloader.tasks.filter { !it.finished && it.status != DlStatus.CANCELED }
    val finished = Downloader.tasks.filter { it.finished }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("下载") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    if (finished.isNotEmpty()) {
                        TextButton(onClick = { Downloader.clearFinished() }) { Text("清理已完成") }
                    } else if (Store.downloads.isNotEmpty()) {
                        TextButton(onClick = { Store.clearDownloads() }) { Text("清空记录") }
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

            LazyColumn(Modifier.weight(1f)) {
                if (active.isNotEmpty()) {
                    item {
                        SectionTitle("进行中（${active.size}）")
                    }
                    items(active, key = { "a${it.id}" }) { task ->
                        TaskRow(task, context, onOpenFile)
                        Spacer(Modifier.height(10.dp))
                    }
                }
                if (finished.isNotEmpty()) {
                    item { SectionTitle("本次已完成") }
                    items(finished, key = { "f${it.id}" }) { task ->
                        TaskRow(task, context, onOpenFile)
                        Spacer(Modifier.height(10.dp))
                    }
                }
                item { SectionTitle("下载记录（点击查看 / 长按操作）") }
                if (Store.downloads.isEmpty() && active.isEmpty() && finished.isEmpty()) {
                    item {
                        Text(
                            "暂无下载记录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
                items(Store.downloads, key = { "d${it.time}" }) { d ->
                    RecordRow(d, context, fmt, onOpenFile)
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp)
    )
}

/** 任务行：点击查看/安装，长按出快捷菜单 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(task: DlTask, context: Context, onOpenFile: (FileTarget) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier.fillMaxWidth().combinedClickable(
                onClick = {
                    when {
                        task.running || task.status == DlStatus.PAUSED -> menu = true
                        task.isApk -> Downloader.installApk(context, task)
                        else -> openInternal(context, task.finalUri, task.finalPath, task.displayName, task.mime, onOpenFile)
                    }
                },
                onLongClick = { menu = true }
            )
        ) {
            DownloadTaskCard(task, context)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            when {
                task.running || task.status == DlStatus.PAUSED -> {
                    DropdownMenuItem(text = { Text("复制下载链接") }, onClick = {
                        menu = false; copyToClipboard(context, task.resolvedUrl ?: task.url, "链接已复制")
                    })
                    DropdownMenuItem(
                        text = { Text(if (task.status == DlStatus.PAUSED) "继续下载" else "暂停下载") },
                        onClick = {
                            menu = false
                            if (task.status == DlStatus.PAUSED) Downloader.resume(context, task) else Downloader.pause(task)
                        }
                    )
                    DropdownMenuItem(text = { Text("取消") }, onClick = {
                        menu = false; Downloader.cancel(task)
                    })
                }
                else -> {
                    if (task.isApk) {
                        DropdownMenuItem(text = { Text("安装") }, onClick = {
                            menu = false; Downloader.installApk(context, task)
                        })
                    } else {
                        DropdownMenuItem(text = { Text("打开") }, onClick = {
                            menu = false
                            openInternal(context, task.finalUri, task.finalPath, task.displayName, task.mime, onOpenFile)
                        })
                    }
                    DropdownMenuItem(text = { Text("复制下载链接") }, onClick = {
                        menu = false; copyToClipboard(context, task.resolvedUrl ?: task.url, "链接已复制")
                    })
                    DropdownMenuItem(text = { Text("重新下载") }, onClick = {
                        menu = false
                        Downloader.enqueue(
                            context, task.url, task.displayName, mime = task.mime,
                            mirrors = UpdateChecker.mirrorUrls(task.url)
                        )
                    })
                    DropdownMenuItem(text = { Text("使用其他应用打开") }, onClick = {
                        menu = false
                        if (!Downloader.openExternal(context, task.finalUri, task.finalPath, task.displayName, task.mime)) {
                            Toast.makeText(context, "没有可打开该文件的应用", Toast.LENGTH_SHORT).show()
                        }
                    })
                    DropdownMenuItem(text = { Text("删除文件") }, onClick = {
                        menu = false
                        Downloader.deleteFile(context, task)
                        Downloader.remove(task)
                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                    })
                }
            }
        }
    }
}

/** 历史记录行：点击查看/安装，长按出快捷菜单 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecordRow(
    d: DownloadItem,
    context: Context,
    fmt: SimpleDateFormat,
    onOpenFile: (FileTarget) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.fillMaxWidth().combinedClickable(
                onClick = {
                    if (d.name.endsWith(".apk", true) && !d.uri.isNullOrBlank()) {
                        Downloader.installApkByUri(context, d.uri!!, d.name)
                    } else {
                        openInternal(context, d.uri, d.path, d.name, d.mime, onOpenFile)
                    }
                },
                onLongClick = { menu = true }
            ).padding(horizontal = 16.dp, vertical = 12.dp),
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
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, "更多", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("复制下载链接") }, onClick = {
                menu = false; copyToClipboard(context, d.url, "链接已复制")
            })
            DropdownMenuItem(text = { Text("重新下载") }, onClick = {
                menu = false
                Downloader.enqueue(
                    context, d.url, d.name, mime = d.mime,
                    mirrors = UpdateChecker.mirrorUrls(d.url)
                )
            })
            DropdownMenuItem(text = { Text("使用其他应用打开") }, onClick = {
                menu = false
                if (!Downloader.openExternal(context, d.uri, d.path, d.name, d.mime)) {
                    Toast.makeText(context, "没有可打开该文件的应用", Toast.LENGTH_SHORT).show()
                }
            })
            DropdownMenuItem(text = { Text("删除") }, onClick = {
                menu = false
                Downloader.deleteFile(context, d.uri, d.path)
                Store.removeDownload(d)
                Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
            })
        }
    }
}

/** 任务卡片：进度条 + 速度 + 线路 + 暂停/继续/重试/安装 */
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
                        else -> buildString {
                            append("${humanSize(task.done)} / ${humanSize(task.total)}  ·  ${humanSize(task.speed)}/s")
                            if (task.lineCount > 1 && task.lineIndex > 0) append("  ·  线路${task.lineIndex}/${task.lineCount}")
                        }
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
                        if (task.isApk) {
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