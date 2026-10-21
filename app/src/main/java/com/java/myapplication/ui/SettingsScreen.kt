package com.java.myapplication.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.java.myapplication.BrowserViewModel
import com.java.myapplication.Settings
import com.java.myapplication.Store

/** 设置：分组首页 + 二级页面 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: BrowserViewModel, onBack: () -> Unit) {
    var page by remember { mutableStateOf("root") }
    Settings.version.intValue // 订阅变更

    // 返回键：二级页 → 设置首页 → 浏览器，绝不直接退出
    BackHandler {
        if (page != "root") page = "root" else onBack()
    }

    val title = when (page) {
        "general" -> "通用"
        "appearance" -> "外观与主页"
        "privacy" -> "隐私与安全"
        "browsing" -> "浏览"
        "ai" -> "AI 助手"
        "translate" -> "翻译"
        "shortcuts" -> "主页快捷方式"
        "about" -> "关于"
        else -> "设置"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = { if (page == "root") onBack() else page = "root" }) {
                        Icon(Icons.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            when (page) {
                "root" -> SettingsRoot { page = it }
                "general" -> GeneralPage()
                "appearance" -> AppearancePage { page = "shortcuts" }
                "privacy" -> PrivacyPage(vm)
                "browsing" -> BrowsingPage()
                "ai" -> AiPage()
                "translate" -> TranslatePage()
                "shortcuts" -> ShortcutsPage()
                "about" -> AboutPage()
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

// ================= 分组首页 =================

@Composable
private fun SettingsRoot(onOpen: (String) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        GroupItem("通用", "搜索引擎 / 主页 / 启动") { onOpen("general") }
        GroupItem("外观与主页", "主题 / 搜索框样式 / 主页背景") { onOpen("appearance") }
        GroupItem("隐私与安全", "无痕 / Cookie / 清除数据") { onOpen("privacy") }
        GroupItem("浏览", "JavaScript / 广告拦截 / 桌面模式") { onOpen("browsing") }
        GroupItem("AI 助手", "API / 模型 / 提示词") { onOpen("ai") }
        GroupItem("翻译", "目标语言") { onOpen("translate") }
        GroupItem("关于", "版本信息") { onOpen("about") }
    }
}

@Composable
private fun GroupItem(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.KeyboardArrowRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ================= 二级页面 =================

@Composable
private fun GeneralPage() {
    Settings.version.intValue
    ChoiceSetting(
        "搜索引擎", Settings.searchEngine,
        listOf("bing" to "必应", "baidu" to "百度", "google" to "Google", "duckduckgo" to "DuckDuckGo", "sogou" to "搜狗")
    ) { Settings.searchEngine = it }
    TextSetting("自定义主页（留空=内置主页）", Settings.homepage) { Settings.homepage = it }
    SwitchSetting("启动时恢复上次标签", Settings.restoreTabs) { Settings.restoreTabs = it }
}

@Composable
private fun AppearancePage(onManageShortcuts: () -> Unit) {
    Settings.version.intValue
    ChoiceSetting(
        "深色主题", Settings.darkTheme.toString(),
        listOf("0" to "跟随系统", "1" to "浅色", "2" to "深色")
    ) { Settings.darkTheme = it.toInt() }
    SwitchSetting("网页强制深色", Settings.forceDarkWeb) { Settings.forceDarkWeb = it }
    SliderSetting("网页文字缩放", Settings.textZoom, 50, 200, "%d%%") { Settings.textZoom = it }

    SectionTitle("搜索框样式")
    ChoiceSetting(
        "样式", Settings.searchBoxStyle.toString(),
        listOf("0" to "胶囊", "1" to "圆角矩形", "2" to "直角")
    ) { Settings.searchBoxStyle = it.toInt() }

    SectionTitle("主页背景")
    LazyRow(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        itemsIndexed(homeBgPresets) { i, colors ->
            val selected = Settings.homeBg == i
            Box(
                Modifier
                    .padding(end = 10.dp)
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(colors))
                    .border(
                        width = if (selected) 3.dp else 0.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { Settings.homeBg = i }
            )
        }
    }
    SwitchSetting("主页显示快捷方式", Settings.homeShowShortcuts) { Settings.homeShowShortcuts = it }

    // 跟随系统壁纸（开启时申请读取权限）
    val context = LocalContext.current
    val wallpaperPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        Settings.version.intValue // 触发刷新
    }
    Row(
        Modifier.fillMaxWidth()
            .clickable {
                val target = !Settings.homeUseSystemWallpaper
                Settings.homeUseSystemWallpaper = target
                if (target) {
                    val perm = if (android.os.Build.VERSION.SDK_INT >= 33)
                        android.Manifest.permission.READ_MEDIA_IMAGES
                    else android.Manifest.permission.READ_EXTERNAL_STORAGE
                    wallpaperPerm.launch(perm)
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("跟随系统桌面壁纸", style = MaterialTheme.typography.bodyLarge)
            Text("全屏铺满，含底部菜单栏", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = Settings.homeUseSystemWallpaper, onCheckedChange = {
            Settings.homeUseSystemWallpaper = it
            if (it) {
                val perm = if (android.os.Build.VERSION.SDK_INT >= 33)
                    android.Manifest.permission.READ_MEDIA_IMAGES
                else android.Manifest.permission.READ_EXTERNAL_STORAGE
                wallpaperPerm.launch(perm)
            }
        })
    }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Settings.homeBgImage = uri.toString()
        }
    }
    Row(
        Modifier.fillMaxWidth().clickable { wallpaperPicker.launch("image/*") }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("自定义壁纸", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (Settings.homeBgImage.isBlank()) "未设置（使用上方渐变色卡）" else "已设置，点按更换",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (Settings.homeBgImage.isNotBlank()) {
            TextButton(onClick = { Settings.homeBgImage = "" }) { Text("清除") }
        }
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Row(
        Modifier.fillMaxWidth().clickable(onClick = onManageShortcuts)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("管理主页快捷方式", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ShortcutsPage() {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical = 8.dp)) {
        Store.shortcuts.forEach { sc ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(sc.name, style = MaterialTheme.typography.bodyLarge)
                    Text(sc.url, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                IconButton(onClick = { Store.removeShortcut(sc) }) {
                    Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(Modifier.padding(16.dp)) {
            OutlinedButton(onClick = { showAdd = true }) { Text("添加快捷方式") }
        }
    }
    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
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
                            Store.addShortcut(name.trim(), u); showAdd = false
                        }
                    },
                    enabled = name.isNotBlank() && url.isNotBlank()
                ) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun PrivacyPage(vm: BrowserViewModel) {
    Settings.version.intValue
    val context = LocalContext.current
    SwitchSetting("无痕模式（不记录历史）", Settings.incognito) { Settings.incognito = it }
    SwitchSetting("发送“请勿追踪”请求", Settings.doNotTrack) { Settings.doNotTrack = it }
    SwitchSetting("接受 Cookie", Settings.cookiesEnabled) { Settings.cookiesEnabled = it }
    SwitchSetting("允许第三方 Cookie", Settings.thirdPartyCookies) { Settings.thirdPartyCookies = it }
    SwitchSetting("允许网站获取位置", Settings.geoAllowed) { Settings.geoAllowed = it }

    var showClear by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { showClear = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("清除浏览数据", style = MaterialTheme.typography.bodyLarge)
            Text("缓存 / Cookie / 历史记录", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text("清除浏览数据") },
            text = { Text("将清空所有缓存、Cookie 和历史记录，确定？") },
            confirmButton = {
                TextButton(onClick = { vm.clearAllData(context); showClear = false }) { Text("清除") }
            },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun BrowsingPage() {
    Settings.version.intValue
    SwitchSetting("启用 JavaScript", Settings.jsEnabled) { Settings.jsEnabled = it }
    SwitchSetting("DOM 存储", Settings.domStorage) { Settings.domStorage = it }
    SwitchSetting("加载图片", Settings.loadImages) { Settings.loadImages = it }
    SwitchSetting("桌面模式（电脑版 UA）", Settings.desktopMode) { Settings.desktopMode = it }
    SwitchSetting("广告拦截", Settings.adBlock) { Settings.adBlock = it }
    SwitchSetting("拦截弹窗", Settings.blockPopups) { Settings.blockPopups = it }
    SwitchSetting("下拉刷新", Settings.pullRefresh) { Settings.pullRefresh = it }
}

@Composable
private fun AiPage() {
    Settings.version.intValue
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        AiProviderPicker()
    }
    TextSetting("API Base", Settings.aiBase) { Settings.aiBase = it; Settings.aiProvider = "自定义 / 第三方中转站" }
    TextSetting("API Key", Settings.aiKey, secret = true) { Settings.aiKey = it }
    TextSetting("模型", Settings.aiModel) { Settings.aiModel = it; Settings.aiProvider = "自定义 / 第三方中转站" }
    TextSetting("系统提示词", Settings.aiPrompt, singleLine = false) { Settings.aiPrompt = it }
}

@Composable
private fun TranslatePage() {
    Settings.version.intValue
    ChoiceSetting(
        "目标语言", Settings.translateTarget,
        listOf("zh-CN" to "简体中文", "zh-TW" to "繁體中文", "en" to "English", "ja" to "日本語", "ko" to "한국어", "fr" to "Français", "de" to "Deutsch", "ru" to "Русский", "es" to "Español")
    ) { Settings.translateTarget = it }
}

@Composable
private fun AboutPage() {
    Column(Modifier.padding(20.dp)) {
        Text("知微浏览器 1.0", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "基于系统 WebView（Chromium 内核），网站兼容性跟随系统 WebView 版本。\n\n" +
                    "内置 AI 总结 / 翻译 / 对话，支持自定义主页、快捷方式与背景。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ================= 通用组件 =================

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchSetting(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
private fun ChoiceSetting(title: String, current: String, options: List<Pair<String, String>>, onChange: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val label = options.firstOrNull { it.first == current }?.second ?: current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Box {
            OutlinedButton(onClick = { open = true }) { Text(label) }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { (v, l) ->
                    DropdownMenuItem(text = { Text(l) }, onClick = { onChange(v); open = false })
                }
            }
        }
    }
}

@Composable
private fun TextSetting(
    title: String,
    value: String,
    secret: Boolean = false,
    singleLine: Boolean = true,
    onChange: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; onChange(it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = singleLine,
            visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None
        )
    }
}

@Composable
private fun SliderSetting(title: String, value: Int, min: Int, max: Int, format: String, onChange: (Int) -> Unit) {
    var v by remember(value) { mutableStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(format.format(v.toInt()), style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = v,
            onValueChange = { v = it },
            onValueChangeFinished = { onChange(v.toInt()) },
            valueRange = min.toFloat()..max.toFloat()
        )
    }
}