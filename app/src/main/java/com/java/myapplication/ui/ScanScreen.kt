package com.java.myapplication.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.zxing.ResultPoint
import com.java.myapplication.Scan
import com.java.myapplication.ScanHit
import com.java.myapplication.ScanKind
import com.java.myapplication.Store
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import kotlinx.coroutines.launch

/**
 * 扫一扫：相机取景识别二维码 / 条形码，也可以从相册选图识别。
 * 命中后交给 [onHit]（由上层决定打开还是弹结果），本页只负责「扫出来」。
 */
@Composable
fun ScanScreen(
    onHit: (ScanHit) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var torch by remember { mutableStateOf(false) }
    var decoding by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf("对准二维码 / 条形码，自动识别") }
    // 相机回调可能在解码线程上，用一个「已命中」标记防止同一码重复触发
    var consumed by remember { mutableStateOf(false) }
    val barcodeView = remember { mutableStateOf<BarcodeView?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
        if (!ok) hint = "没有相机权限，可以从相册选图识别"
    }

    LaunchedEffect(Unit) {
        if (!granted) permLauncher.launch(Manifest.permission.CAMERA)
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            decoding = true
            val hit = Scan.decodeFromUri(context, uri)
            decoding = false
            if (hit == null) {
                hint = "这张图里没找到二维码或条形码，换一张试试"
            } else {
                Scan.vibrate(context)
                onHit(hit)
            }
        }
    }

    val view = barcodeView.value
    DisposableEffect(granted, view) {
        if (granted && view != null) runCatching { view.resume() }
        onDispose { runCatching { view?.pause() } }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView(
                factory = { ctx ->
                    BarcodeView(ctx).apply {
                        setDecoderFactory(DefaultDecoderFactory(Scan.formats.toList()))
                        decodeContinuous(object : BarcodeCallback {
                            override fun barcodeResult(result: BarcodeResult?) {
                                val text = result?.text ?: return
                                // 相册识别与相机回调都会走到这里，标记保证只派发一次
                                if (consumed) return
                                consumed = true
                                val hit = Scan.hit(text, result.barcodeFormat?.name ?: "UNKNOWN")
                                Scan.vibrate(ctx)
                                android.os.Handler(android.os.Looper.getMainLooper()).post { onHit(hit) }
                            }

                            override fun possibleResultPoints(resultPoints: List<ResultPoint>?) {
                                // 不需要取景点提示
                            }
                        })
                        barcodeView.value = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { runCatching { it.pause() } }
            )
        } else {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("需要相机权限才能扫码", color = Color.White,
                    style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Text(hint, color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(18.dp))
                Row {
                    Button(
                        onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.height(48.dp)
                    ) { Text("授予相机权限") }
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = { pickImage.launch("image/*") }) { Text("从相册选图") }
                }
            }
        }

        // ---------- 取景框 ----------
        if (granted) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(260.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(18.dp))
                    .a11yLabel("扫描取景框")
            )
        }

        // ---------- 顶部栏 ----------
        Row(
            Modifier
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(48.dp).a11yLabel("关闭扫码")
            ) { Icon(Icons.Filled.Close, "关闭", tint = Color.White) }
            Text("扫一扫", color = Color.White, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    val on = !torch
                    torch = on
                    runCatching { barcodeView.value?.setTorch(on) }
                },
                modifier = Modifier.height(48.dp)
            ) { Text(if (torch) "关闭手电" else "手电筒", color = Color.White) }
        }

        // ---------- 底部：提示 + 相册 + 最近扫过 ----------
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .systemBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (decoding) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                        color = Color.White)
                    Spacer(Modifier.width(10.dp))
                    Text("正在识别相册图片…", color = Color.White,
                        style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
            }
            Text(hint, color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = { pickImage.launch("image/*") },
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 18.dp)
                    ) { Text("🖼 从相册选图", color = Color.White) }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "支持二维码、EAN/UPC 等条形码",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            val recent = Store.scans.take(5)
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("最近扫过", color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    recent.forEach { s ->
                        Surface(
                            onClick = {
                                onHit(ScanHit(s.text, s.format, runCatching {
                                    ScanKind.valueOf(s.kind)
                                }.getOrDefault(ScanKind.TEXT)))
                            },
                            color = Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(s.text.take(18), color = Color.White,
                                    style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                Text(s.format, color = Color.White.copy(alpha = 0.55f),
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 扫码结果弹窗：按类型给出「打开 / 搜索 / 复制」 */
@Composable
fun ScanResultDialog(
    hit: ScanHit,
    onOpen: () -> Unit,
    onSearch: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("扫到${Scan.label(hit.kind)}") },
        text = {
            Column {
                Text(hit.text, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 8, modifier = Modifier.heightInSafe())
                Spacer(Modifier.height(6.dp))
                Text("格式：${hit.format}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hit.kind == ScanKind.WIFI) {
                    Spacer(Modifier.height(6.dp))
                    Text("Wi-Fi 配置信息，可复制后用系统扫码或手动连接。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (hit.kind == ScanKind.PRODUCT) {
                    Spacer(Modifier.height(6.dp))
                    Text("商品条码，可用「搜索」查同款信息。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("scan", hit.text))
                    android.widget.Toast.makeText(context, "已复制", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("复制") }
                TextButton(onClick = onSearch) { Text("搜索") }
                if (Scan.openable(hit.kind)) {
                    Button(onClick = onOpen) { Text("打开") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

/** 结果文本最多占半屏，超长内容不撑爆弹窗 */
private fun Modifier.heightInSafe(): Modifier = this.then(
    androidx.compose.foundation.layout.heightIn(max = 240.dp)
)

/** 扫码结果的外部跳转（电话 / 邮件 / 短信 / 地图） */
fun openExternal(context: android.content.Context, hit: ScanHit): Boolean = runCatching {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(hit.text.trim())))
    true
}.getOrDefault(false)