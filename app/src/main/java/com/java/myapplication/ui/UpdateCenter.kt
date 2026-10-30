package com.java.myapplication.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.java.myapplication.BrowserViewModel
import com.java.myapplication.DlStatus
import com.java.myapplication.DlTask
import com.java.myapplication.Downloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 更新中心：品牌 LOGO 动态背景 + 检查更新 + 应用内下载进度。
 *
 * - 使用内置流式下载引擎（Downloader）直接落地，进度实时显示，不再跳出应用；
 * - 若用户在设置中把下载引擎切成系统/第三方下载器，则只做投递并给出提示；
 * - 全屏形态（onBack != null）供「关于页下滑」与「软件更新」入口复用。
 */

/** 品牌 LOGO：靛蓝→青渐变圆牌 + 白色 Z；animated 时叠加旋转光环与呼吸缩放 */
@Composable
fun ZhiweiLogo(size: Dp, animated: Boolean = false, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "zhiweiLogo")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing)),
        label = "rotation"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (animated) {
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer {
                        scaleX = pulse; scaleY = pulse; rotationZ = rotation; alpha = 0.8f
                    }
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                Color(0x006EE7F9), Color(0xFF6EE7F9), Color(0x006EE7F9),
                                Color(0xFFA5B4FC), Color(0x006EE7F9)
                            )
                        ),
                        CircleShape
                    )
            )
            Box(
                Modifier.fillMaxSize()
                    .graphicsLayer { scaleX = pulse; scaleY = pulse }
                    .background(
                        Brush.radialGradient(listOf(Color(0x4DFFFFFF), Color(0x00FFFFFF))),
                        CircleShape
                    )
            )
        }
        Box(
            Modifier.fillMaxSize().clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF0EA5E9)))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Z",
                color = Color.White,
                fontSize = (size.value * 0.52f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** 动态渐变背景：底色缓慢流动 + 两团呼吸光斑（绘制在自身尺寸内，不额外占用内存） */
@Composable
fun AnimatedGradientBackdrop(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "backdrop")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shift"
    )
    Box(
        modifier.drawBehind {
            drawRect(
                Brush.linearGradient(
                    listOf(Color(0xFF4338CA), Color(0xFF6D28D9), Color(0xFFBE185D)),
                    start = Offset(0f, 0f),
                    end = Offset(size.width * (0.85f + 0.3f * t), size.height)
                )
            )
            val r = size.minDimension * 0.85f
            val c1 = Offset(size.width * (0.18f + 0.6f * t), size.height * 0.16f)
            drawCircle(
                Brush.radialGradient(listOf(Color(0x59F0ABFC), Color(0x00F0ABFC)), center = c1, radius = r),
                radius = r, center = c1
            )
            val c2 = Offset(size.width * (0.88f - 0.5f * t), size.height * 0.84f)
            drawCircle(
                Brush.radialGradient(listOf(Color(0x4D67E8F9), Color(0x0067E8F9)), center = c2, radius = r * 0.9f),
                radius = r * 0.9f, center = c2
            )
        }
    )
}

/** 拉取 GitHub Releases 最新版本：返回 (版本号, APK 直链) */
private suspend fun fetchLatestRelease(): Pair<String, String?> = withContext(Dispatchers.IO) {
    val conn = java.net.URL("https://api.github.com/repos/mingxiozsg-star/zhiwei-browser/releases/latest")
        .openConnection() as java.net.HttpURLConnection
    conn.connectTimeout = 10_000
    conn.readTimeout = 10_000
    conn.setRequestProperty("Accept", "application/vnd.github+json")
    val body = conn.inputStream.bufferedReader().readText()
    val json = org.json.JSONObject(body)
    val tag = json.optString("tag_name").removePrefix("v")
    var apkUrl: String? = json.optString("html_url").ifBlank { null }
    json.optJSONArray("assets")?.let { assets ->
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) {
                apkUrl = a.optString("browser_download_url"); break
            }
        }
    }
    tag to apkUrl
}

/** 更新中心整页：LOGO 动态背景居中、版本号、底部跳转更新按钮 + 下载进度 */
@Composable
fun UpdateCenterPage(vm: BrowserViewModel, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cur = com.java.myapplication.BuildConfig.VERSION_NAME

    var checking by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var apkUrl by remember { mutableStateOf<String?>(null) }
    var tag by remember { mutableStateOf<String?>(null) }
    var taskId by remember { mutableStateOf<Long?>(null) }
    var dispatched by remember { mutableStateOf(false) }

    val task = taskId?.let { id -> Downloader.tasks.firstOrNull { it.id == id } }
    val external = com.java.myapplication.Settings.dlEngine != 0

    fun check() {
        if (checking) return
        checking = true; msg = null; apkUrl = null; tag = null; dispatched = false; taskId = null
        scope.launch {
            runCatching { fetchLatestRelease() }
                .onSuccess { (t, url) ->
                    checking = false
                    tag = t.ifBlank { null }
                    if (t.isNotBlank() && t != cur) {
                        msg = "发现新版本 v$t"
                        apkUrl = url
                    } else {
                        msg = "已是最新版本（v$cur）"
                    }
                }
                .onFailure {
                    checking = false
                    msg = "检查失败：${it.message}"
                }
        }
    }

    if (onBack != null) BackHandler { onBack() }

    Box(Modifier.fillMaxSize()) {
        AnimatedGradientBackdrop(Modifier.fillMaxSize())
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp)
            ) { Icon(Icons.Filled.Close, "关闭", tint = Color.White) }
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))
            ZhiweiLogo(118.dp, animated = true)
            Spacer(Modifier.height(22.dp))
            Text(
                "zhiwei browser",
                style = MaterialTheme.typography.displaySmall,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "V$cur · ${com.java.myapplication.BuildConfig.BUILD_TIME}",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f)
            )
            tag?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    "最新版本：v$it",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White
                )
            }
            Spacer(Modifier.height(26.dp))
            Surface(
                onClick = { check() },
                color = Color.White.copy(alpha = 0.24f),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.width(168.dp).height(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("检查更新", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    }
                }
            }
            msg?.let {
                Spacer(Modifier.height(14.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.weight(1f))

            // ---------- 底部：进度 + 跳转更新按钮 ----------
            task?.let {
                UpdateProgress(task)
                Spacer(Modifier.height(14.dp))
            }
            if (task == null && dispatched) {
                Text(
                    "已交给${if (external) "外部下载器" else "下载引擎"}下载，进度可在其应用中查看",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
            }
            val label = when {
                task == null -> if (apkUrl != null) "下载新版本${tag?.let { " v$it" } ?: ""}" else "检查更新"
                task.status == DlStatus.DONE -> "安装更新"
                task.status == DlStatus.PAUSED -> "继续下载"
                task.status == DlStatus.FAILED -> "重试下载"
                task.status == DlStatus.CANCELED -> "重新下载"
                else -> "暂停下载"
            }
            Surface(
                onClick = {
                    when {
                        task == null -> {
                            val url = apkUrl
                            if (url == null) {
                                check()
                            } else {
                                val name = "zhiwei-browser-v${tag ?: cur}.apk"
                                vm.startDownloadDirect(
                                    context, url, name, "application/vnd.android.package-archive"
                                )
                                val t = Downloader.tasks.firstOrNull { it.url == url }
                                if (t != null) taskId = t.id else dispatched = true
                            }
                        }
                        task.status == DlStatus.DONE -> Downloader.installApk(context, task)
                        task.status == DlStatus.PAUSED -> Downloader.resume(context, task)
                        task.status == DlStatus.FAILED || task.status == DlStatus.CANCELED ->
                            Downloader.retry(context, task)
                        else -> Downloader.pause(task)
                    }
                },
                color = Color.White,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF4338CA)
                    )
                }
            }
            Spacer(Modifier.height(34.dp))
        }
    }
}

/** 内嵌下载进度条（更新页专用，白字适配深色渐变背景） */
@Composable
private fun UpdateProgress(task: DlTask) {
    val animated by animateFloatAsState(
        targetValue = task.progress,
        animationSpec = tween(220),
        label = "updateProgress"
    )
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            task.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1
        )
        Spacer(Modifier.height(8.dp))
        if (task.total > 0) {
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f)
            )
        }
        Spacer(Modifier.height(8.dp))
        val text = when {
            task.status == DlStatus.DONE -> "下载完成 ${humanSize(task.done)}，可安装更新"
            task.status == DlStatus.PAUSED -> "已暂停 ${(task.progress * 100).toInt()}%"
            task.status == DlStatus.FAILED -> "下载失败：${task.message ?: "网络错误"}"
            task.status == DlStatus.CANCELED -> "已取消"
            else -> buildString {
                append("${(task.progress * 100).toInt()}% · ${humanSize(task.done)}")
                if (task.total > 0) append(" / ${humanSize(task.total)}")
                if (task.speed > 0) append(" · ${humanSize(task.speed)}/s")
            }
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.92f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 关于页顶部的「LOGO + 版本」板块：向下拖动即全屏展开更新中心。
 * 只消费向下的手势，向上滑动仍交给外层列表滚动，不抢滚动事件。
 */
@Composable
fun AboutUpdateHero(onOpen: () -> Unit) {
    var offset by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 110.dp.toPx() }

    Column(
        Modifier.fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var acc = 0f
                    var owned = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val dy = change.position.y - change.previousPosition.y
                        if (dy > 0f || (owned && acc + dy > 0f)) {
                            owned = true
                            acc = (acc + dy).coerceAtLeast(0f)
                            change.consume()
                            offset = acc
                        } else {
                            break
                        }
                    }
                    val triggered = owned && acc >= threshold
                    val from = acc
                    scope.launch {
                        if (triggered) {
                            offset = 0f
                            onOpen()
                        } else if (owned) {
                            androidx.compose.animation.core.animate(
                                0f, from, animationSpec = tween(240)
                            ) { value, _ -> offset = value }
                        }
                    }
                }
            }
            .graphicsLayer {
                translationY = offset * 0.35f
                alpha = (1f - offset / (threshold * 1.7f)).coerceIn(0.4f, 1f)
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ZhiweiLogo(96.dp, animated = true)
        Spacer(Modifier.height(14.dp))
        Text(
            "zhiwei browser",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "V${com.java.myapplication.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "下滑进入更新中心",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
