package com.java.myapplication.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.java.myapplication.Downloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 内置查看目标：uri 为 content:// 或 file://，name 用于判定类型与展示 */
data class FileTarget(val uri: String, val name: String, val mime: String? = null)

private enum class FKind { IMAGE, VIDEO, AUDIO, TEXT, HTML, APK, OTHER }

private fun kindOf(name: String, mime: String?): FKind {
    val m = mime?.lowercase().orEmpty()
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        m.startsWith("image/") || ext in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif") -> FKind.IMAGE
        m.startsWith("video/") || ext in setOf("mp4", "mkv", "webm", "mov", "avi", "3gp", "flv", "m4v", "ts") -> FKind.VIDEO
        m.startsWith("audio/") || ext in setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "opus", "amr") -> FKind.AUDIO
        ext == "apk" -> FKind.APK
        ext in setOf("html", "htm", "xhtml") -> FKind.HTML
        m.startsWith("text/") || ext in setOf(
            "txt", "md", "json", "log", "xml", "csv", "yml", "yaml", "kt", "java",
            "js", "ts", "css", "ini", "conf", "srt", "vtt", "properties", "py", "c", "cpp", "h", "sh", "gradle", "toml"
        ) -> FKind.TEXT
        else -> FKind.OTHER
    }
}

/**
 * 内置文件查看器：图片（可缩放拖动）、视频/音频（内置播放）、纯文本（等宽渲染）、HTML（WebView）、
 * APK（跳系统安装器），其余类型给出「用系统应用打开」的兜底。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(target: FileTarget, onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val kind = remember(target.uri) { kindOf(target.name, target.mime) }
    val dark = kind == FKind.IMAGE || kind == FKind.VIDEO || kind == FKind.AUDIO

    Scaffold(
        containerColor = if (dark) Color(0xFF0B0B0F) else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(target.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    TextButton(onClick = {
                        if (kind == FKind.APK) {
                            Downloader.installApkByUri(context, target.uri, target.name)
                        } else {
                            Downloader.openExternal(context, target.uri, null, target.name, target.mime)
                        }
                    }) { Text("其他应用") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (dark) Color(0xFF0B0B0F) else MaterialTheme.colorScheme.background,
                    titleContentColor = if (dark) Color.White else MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = if (dark) Color.White else MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { p ->
        Box(Modifier.fillMaxSize().padding(p)) {
            when (kind) {
                FKind.IMAGE -> ImagePane(context, target)
                FKind.VIDEO -> MediaPane(context, target, false)
                FKind.AUDIO -> MediaPane(context, target, true)
                FKind.HTML -> HtmlPane(target)
                FKind.TEXT -> TextPane(context, target)
                FKind.APK -> InfoPane(context, target, "这是一个安装包", "跳转系统安装器", install = true)
                FKind.OTHER -> InfoPane(context, target, "暂不支持内置预览该格式", "用其他应用打开", install = false)
            }
        }
    }
}

/** 图片：超采样解码 + 双指缩放 / 拖动 */
@Composable
private fun ImagePane(context: Context, target: FileTarget) {
    var bmp by remember(target.uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(target.uri) { mutableStateOf(false) }
    LaunchedEffect(target.uri) {
        val b = withContext(Dispatchers.IO) { decodeSampled(context, Uri.parse(target.uri)) }
        bmp = b
        failed = b == null
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val b = bmp
    if (b == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (failed) Text("无法解码该图片", color = Color.White)
            else CircularProgressIndicator(color = Color.White)
        }
    } else {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = target.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 8f)
                        offset = if (scale > 1f) offset + pan else Offset.Zero
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )
    }
}

/** 视频 / 音频：内置播放器（带系统控制条） */
@Composable
private fun MediaPane(context: Context, target: FileTarget, audioOnly: Boolean) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setVideoURI(Uri.parse(target.uri))
                    setMediaController(MediaController(ctx).apply { setAnchorView(this@apply) })
                    setOnPreparedListener { start() }
                }
            },
            modifier = if (audioOnly) Modifier.fillMaxSize() else Modifier.fillMaxSize()
        )
    }
}

/** HTML：直接丢给 WebView 渲染 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlPane(target: FileTarget) {
    AndroidView(
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = android.webkit.WebViewClient()
                loadUrl(target.uri)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/** 纯文本：等宽渲染，最多读 2MB，避免大日志卡死 */
@Composable
private fun TextPane(context: Context, target: FileTarget) {
    var text by remember(target.uri) { mutableStateOf<String?>(null) }
    LaunchedEffect(target.uri) {
        text = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(target.uri))?.use { ins ->
                    val buf = ByteArray(2 * 1024 * 1024)
                    val n = ins.read(buf)
                    if (n > 0) String(buf, 0, n, Charsets.UTF_8) else ""
                }
            }.getOrNull() ?: "无法读取文件内容"
        }
    }
    val t = text
    if (t == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp)
        ) {
            Text(t, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 兜底提示页：给出唯一可行动作 */
@Composable
private fun InfoPane(context: Context, target: FileTarget, tip: String, action: String, install: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(target.name, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(tip, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Row {
                TextButton(onClick = {
                    if (install) Downloader.installApkByUri(context, target.uri, target.name)
                    else Downloader.openExternal(context, target.uri, null, target.name, target.mime)
                }) {
                    Icon(Icons.AutoMirrored.Filled.ExitToApp, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(action)
                }
            }
        }
    }
}

/** 大图保护性解码：先探尺寸再按 2 的幂采样 */
private fun decodeSampled(context: Context, uri: Uri, maxSize: Int = 3000): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) sample *= 2
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()
}