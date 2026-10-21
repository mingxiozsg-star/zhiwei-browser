package com.java.myapplication.ui

import android.graphics.BitmapFactory
import android.app.WallpaperManager
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.java.myapplication.Settings
import com.java.myapplication.Shortcut
import com.java.myapplication.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 主页背景预设（渐变，现代化） */
val homeBgPresets = listOf(
    listOf(Color(0xFFF5F7FA), Color(0xFFC3CFE2)),           // 晨雾
    listOf(Color(0xFFFFF1EB), Color(0xFFACE0F9)),           // 蜜桃汽水
    listOf(Color(0xFFE0EAFC), Color(0xFFCFDEF3)),           // 浅蓝
    listOf(Color(0xFFFCE4EC), Color(0xFFF8BBD0)),           // 樱花
    listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9)),           // 抹茶
    listOf(Color(0xFF232526), Color(0xFF414345)),           // 暗夜
    listOf(Color(0xFF0F2027), Color(0xFF2C5364)),           // 深海
    listOf(Color(0xFF41295A), Color(0xFF2F0743)),           // 星云紫
)

/** 快捷方式图标底色轮换 */
private val shortcutColors = listOf(
    Color(0xFF6750A4), Color(0xFF006A6A), Color(0xFF9A4522),
    Color(0xFF0061A4), Color(0xFF984061), Color(0xFF48680D),
    Color(0xFF7D5260), Color(0xFF343E6B),
)

/**
 * 主页壁纸：系统桌面壁纸优先（开关开启时），其次自定义图，都无则 null（用渐变）。
 * 在 BrowserScreen 根部调用，铺满全屏（含底栏）。
 */
@Composable
fun rememberHomeWallpaper(): ImageBitmap? {
    val context = LocalContext.current
    Settings.version.intValue
    val useSys = Settings.homeUseSystemWallpaper
    val uri = Settings.homeBgImage
    val wp by produceState<ImageBitmap?>(null, useSys, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (useSys) {
                    val d = WallpaperManager.getInstance(context).drawable ?: return@runCatching null
                    val w = d.intrinsicWidth.coerceAtLeast(1)
                    val h = d.intrinsicHeight.coerceAtLeast(1)
                    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                    d.setBounds(0, 0, w, h)
                    d.draw(android.graphics.Canvas(bmp))
                    bmp.asImageBitmap()
                } else if (uri.isNotBlank()) {
                    context.contentResolver.openInputStream(Uri.parse(uri))?.use { ins ->
                        BitmapFactory.decodeStream(ins)?.asImageBitmap()
                    }
                } else null
            }.getOrNull()
        }
    }
    return wp
}

/**
 * 内置新标签页。
 * 支持自定义壁纸 / 系统壁纸 / 渐变预设；搜索框靠上、输入常驻本地 state；
 * 只有按「搜索」键或点建议项才发起跳转。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePage(
    wallpaper: ImageBitmap?,
    onSearch: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Settings.version.intValue // 订阅设置变更
    var query by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Shortcut?>(null) }

    val bg = homeBgPresets.getOrElse(Settings.homeBg) { homeBgPresets[0] }
    val hasWallpaper = wallpaper != null
    val dark = if (hasWallpaper) true else bg.first().luminance() < 0.4f
    val onBg = if (dark) Color(0xFFF2F2F7) else Color(0xFF1C1B21)

    val boxShape = when (Settings.searchBoxStyle) {
        1 -> RoundedCornerShape(16.dp)
        2 -> RoundedCornerShape(4.dp)
        else -> CircleShape
    }

    // 建议来源：书签 + 历史，不打断输入
    val suggestions = remember(query, Store.bookmarks.size, Store.history.size) {
        if (query.isBlank()) emptyList()
        else {
            val q = query.lowercase()
            (Store.bookmarks.map { it.title to it.url } + Store.history.map { it.title to it.url })
                .filter { it.first.lowercase().contains(q) || it.second.lowercase().contains(q) }
                .distinctBy { it.second }
                .take(6)
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 背景层：壁纸由 BrowserScreen 根部全屏绘制，这里只在无壁纸时画渐变
        if (!hasWallpaper) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(bg)))
        }

        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ---------- 顶部日期 ----------
            item {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    val now = remember { Date() }
                    val day = remember { SimpleDateFormat("M月d日", Locale.CHINA).format(now) }
                    val week = remember {
                        arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")[
                            java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1]
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(day, style = MaterialTheme.typography.titleMedium, color = onBg)
                        Text(week, style = MaterialTheme.typography.bodySmall, color = onBg.copy(alpha = 0.7f))
                    }
                }
                Spacer(Modifier.height(120.dp))
            }

            // ---------- 搜索框（磨砂透明胶囊：图标 | 输入 | 分隔线 | 搜索） ----------
            item {
                Surface(
                    shape = boxShape,
                    color = if (dark) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.55f),
                    border = BorderStroke(2.dp, onBg.copy(alpha = 0.8f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(10.dp))
                        Icon(Icons.Filled.Search, null, tint = onBg.copy(alpha = 0.8f))
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("搜索或输入网址", color = onBg.copy(alpha = 0.45f)) },
                            singleLine = true,
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(onClick = { query = "" }) {
                                        Icon(Icons.Filled.Close, "清空", tint = onBg.copy(alpha = 0.7f))
                                    }
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (query.isNotBlank()) onSearch(query)
                            }),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = onBg,
                                unfocusedTextColor = onBg,
                                cursorColor = onBg,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                        // 分隔线 + 搜索按钮
                        Box(
                            Modifier.width(1.dp).height(26.dp)
                                .background(onBg.copy(alpha = 0.3f))
                        )
                        Text(
                            "搜索",
                            color = onBg,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier
                                .clickable { if (query.isNotBlank()) onSearch(query) }
                                .padding(horizontal = 18.dp, vertical = 16.dp)
                        )
                    }
                }
            }

            // ---------- 搜索建议 ----------
            if (suggestions.isNotEmpty()) {
                item { Spacer(Modifier.height(8.dp)) }
                items(suggestions) { (title, url) ->
                    Surface(
                        color = if (hasWallpaper) Color.Black.copy(alpha = 0.30f)
                                else onBg.copy(alpha = if (dark) 0.08f else 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { onOpenUrl(url) }
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium, color = onBg)
                            Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelSmall,
                                color = onBg.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            // ---------- 快捷方式（整块毛玻璃面板包住） ----------
            if (Settings.homeShowShortcuts) {
                item { Spacer(Modifier.height(36.dp)) }
                val items = Store.shortcuts.toList()
                val rows = (items + null).chunked(4) // 末尾 null = 添加按钮
                item {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = if (dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = if (dark) 0.25f else 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(vertical = 18.dp)) {
                            rows.forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                    row.forEach { sc ->
                                        if (sc == null) {
                                            ShortcutTile("添加", "+", Color.Gray, dark, onBg, onClick = { showAddDialog = true })
                                        } else {
                                            val idx = items.indexOf(sc)
                                            ShortcutTile(
                                                sc.name, sc.name.take(1),
                                                shortcutColors[idx % shortcutColors.size], dark, onBg,
                                                onClick = { onOpenUrl(sc.url) },
                                                onLongClick = { deleteTarget = sc }
                                            )
                                        }
                                    }
                                    repeat(4 - row.size) { Spacer(Modifier.width(64.dp)) }
                                }
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(120.dp)) }
        }
    }

    // ---------- 添加快捷方式 ----------
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加快捷方式") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it },
                        label = { Text("名称") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = url, onValueChange = { url = it },
                        label = { Text("网址") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        var u = url.trim()
                        if (u.isNotEmpty() && !u.startsWith("http")) u = "https://$u"
                        if (name.isNotBlank() && u.isNotBlank()) {
                            Store.addShortcut(name.trim(), u)
                            showAddDialog = false
                        }
                    },
                    enabled = name.isNotBlank() && url.isNotBlank()
                ) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } }
        )
    }

    // ---------- 删除确认 ----------
    deleteTarget?.let { sc ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除快捷方式") },
            text = { Text("删除「${sc.name}」？") },
            confirmButton = {
                TextButton(onClick = { Store.removeShortcut(sc); deleteTarget = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShortcutTile(
    name: String,
    badge: String,
    color: Color,
    dark: Boolean,
    onBg: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape)
                .background(if (dark) Color.White.copy(alpha = 0.18f) else color),
            contentAlignment = Alignment.Center
        ) {
            Text(badge, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall, color = onBg.copy(alpha = 0.85f)
        )
    }
}

/** 简易亮度 */
private fun Color.luminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}
