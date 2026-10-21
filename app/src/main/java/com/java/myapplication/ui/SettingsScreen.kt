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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.java.myapplication.BrowserViewModel
import com.java.myapplication.Settings
import kotlinx.coroutines.launch
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
        "startup" -> "启动与标签页"
        "appearance" -> "外观"
        "search" -> "搜索引擎"
        "privacy" -> "隐私与安全"
        "ai" -> "AI 功能控制"
        "downloads" -> "下载"
        "language" -> "语言与输入"
        "accessibility" -> "无障碍"
        "system" -> "系统与性能"
        "opensource" -> "开源特有项"
        "developer" -> "开发者功能"
        "shortcuts" -> "主页快捷方式"
        "about" -> "重置与关于"
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
                "startup" -> StartupPage()
                "appearance" -> AppearancePage { page = "shortcuts" }
                "search" -> SearchPage()
                "privacy" -> PrivacyPage(vm)
                "ai" -> AiControlPage()
                "downloads" -> DownloadsPage()
                "language" -> LanguagePage()
                "accessibility" -> AccessibilityPage()
                "system" -> SystemPage()
                "opensource" -> OpenSourcePage()
                "developer" -> DeveloperPage()
                "shortcuts" -> ShortcutsPage()
                "about" -> AboutPage(vm)
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

// ================= 分组首页 =================

@Composable
private fun SettingsRoot(onOpen: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Spacer(Modifier.height(28.dp))
        Text("zhiwei browser", style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground)
        Text("设置", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))

        RoamGroup("基本") {
            RoamItem("🚀", "启动与标签页", "启动行为 / 恢复会话 / 默认浏览器") { onOpen("startup") }
            RoamItem("🎨", "外观", "主题 / 搜索框 / 主页背景") { onOpen("appearance") }
            RoamItem("🔍", "搜索引擎", "默认引擎 / 搜索建议") { onOpen("search") }
            RoamItem("⬇️", "下载", "保存位置 / 续传 / 完成动作") { onOpen("downloads") }
        }
        RoamGroup("安全") {
            RoamItem("🛡️", "隐私与安全", "追踪保护 / HTTPS-Only / DoH") { onOpen("privacy") }
        }
        RoamGroup("智能") {
            RoamItem("🤖", "AI 功能控制", "总开关 / 提供商 / 子功能") { onOpen("ai") }
            RoamItem("🌐", "语言与输入", "翻译 / 目标语言") { onOpen("language") }
            RoamItem("♿", "无障碍", "文字缩放 / 强制深色") { onOpen("accessibility") }
        }
        RoamGroup("高级") {
            RoamItem("⚙️", "系统与性能", "JavaScript / 桌面模式 / 缓存") { onOpen("system") }
            RoamItem("🧩", "开源特有项", "广告拦截 / 同步 / 许可证") { onOpen("opensource") }
            RoamItem("🧪", "开发者功能", "实验性功能（含风险标注）") { onOpen("developer") }
            RoamItem("ℹ️", "重置与关于", "版本 / 更新 / 重置") { onOpen("about") }
        }
    }
}

@Composable
private fun RoamGroup(label: String, content: @Composable () -> Unit) {
    Text(label, style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 22.dp, bottom = 8.dp))
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = RoundedCornerShape(20.dp)
    ) { Column { content() } }
}

@Composable
private fun RoamItem(icon: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(icon, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.KeyboardArrowRight, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun StartupPage() {
    Settings.version.intValue
    SwitchSetting("启动时恢复上次标签", Settings.restoreTabs) { Settings.restoreTabs = it }
    TextSetting("自定义主页（留空=内置主页）", Settings.homepage) { Settings.homepage = it }
    SwitchSetting("关闭多个标签页时确认", Settings.confirmCloseTabs) { Settings.confirmCloseTabs = it }
    SwitchSetting("关闭最后一个标签页时退出", Settings.exitOnLastTab) { Settings.exitOnLastTab = it }
    SwitchSetting("启动时检查默认浏览器", Settings.checkDefaultBrowser) { Settings.checkDefaultBrowser = it }

    // 设为默认浏览器：优先走系统角色请求，失败回退到默认应用设置页
    val context = LocalContext.current
    val defaultRoleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    GroupItem("设为默认浏览器", "让其他应用的链接默认用知微浏览器打开") {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val rm = context.getSystemService(android.app.role.RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)) {
                    defaultRoleLauncher.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER))
                    return@GroupItem
                }
            }
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (e: Exception) {
            android.widget.Toast.makeText(context, "无法打开系统设置：${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun SearchPage() {
    Settings.version.intValue
    ChoiceSetting(
        "默认搜索引擎", Settings.searchEngine,
        listOf("bing" to "必应", "baidu" to "百度", "google" to "Google", "duckduckgo" to "DuckDuckGo", "sogou" to "搜狗")
    ) { Settings.searchEngine = it }
    SwitchSetting("地址栏搜索建议", Settings.searchSuggest) { Settings.searchSuggest = it }
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

    SectionTitle("追踪与网络保护")
    ChoiceSetting(
        "追踪保护等级", Settings.trackingProtection.toString(),
        listOf("0" to "关闭", "1" to "标准", "2" to "严格（默认）")
    ) { Settings.trackingProtection = it.toInt() }
    SwitchSetting("HTTPS-Only 模式（http 先警告）", Settings.httpsOnly) { Settings.httpsOnly = it }
    SwitchSetting("DoH 安全 DNS", Settings.dohEnabled) { Settings.dohEnabled = it }
    SwitchSetting("发送“请勿追踪”请求", Settings.doNotTrack) { Settings.doNotTrack = it }
    SwitchSetting("不安全下载拦截", Settings.blockInsecureDownloads) { Settings.blockInsecureDownloads = it }

    SectionTitle("Cookie 与站点")
    SwitchSetting("无痕模式（不记录历史）", Settings.incognito) { Settings.incognito = it }
    SwitchSetting("接受 Cookie", Settings.cookiesEnabled) { Settings.cookiesEnabled = it }
    SwitchSetting("允许第三方 Cookie", Settings.thirdPartyCookies) { Settings.thirdPartyCookies = it }
    SwitchSetting("允许网站获取位置", Settings.geoAllowed) { Settings.geoAllowed = it }

    SectionTitle("数据收集（默认关闭）")
    SwitchSetting("遥测和数据收集", Settings.telemetry) { Settings.telemetry = it }
    SwitchSetting("崩溃报告自动发送", Settings.crashReports) { Settings.crashReports = it }

    SectionTitle("数据清理")

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
private fun SystemPage() {
    Settings.version.intValue
    SectionTitle("网页引擎")
    SwitchSetting("启用 JavaScript", Settings.jsEnabled) { Settings.jsEnabled = it }
    SwitchSetting("DOM 存储", Settings.domStorage) { Settings.domStorage = it }
    SwitchSetting("加载图片", Settings.loadImages) { Settings.loadImages = it }
    SwitchSetting("桌面模式（电脑版 UA）", Settings.desktopMode) { Settings.desktopMode = it }
    SwitchSetting("下拉刷新", Settings.pullRefresh) { Settings.pullRefresh = it }
}

@Composable
private fun OpenSourcePage() {
    Settings.version.intValue
    SectionTitle("内置拦截")
    SwitchSetting("广告拦截", Settings.adBlock) { Settings.adBlock = it }
    SwitchSetting("拦截弹窗", Settings.blockPopups) { Settings.blockPopups = it }
    Text("提示：若你通过其他方式（如私人 DNS）已做拦截，可关闭内置拦截避免重复过滤。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

    SectionTitle("同步")
    Text("当前版本不含云同步；书签/历史仅保存在本机。后续版本将支持本地加密备份与自建同步，不绑定单一厂商。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

    SectionTitle("开源")
    val context = LocalContext.current
    GroupItem("开源许可证", "MIT License / 第三方组件") {
        runCatching {
            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://github.com/mingxiozsg-star/zhiwei-browser")))
        }
    }
}

@Composable
private fun DeveloperPage() {
    Settings.version.intValue
    Text("以下均为实验性功能：不稳定，可能随时移除。", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    SwitchSetting("网页强制深色（实验性·可能破版）", Settings.forceDarkWeb) { Settings.forceDarkWeb = it }
}

@Composable
private fun DownloadsPage() {
    Settings.version.intValue
    Text("下载文件统一保存到：内部存储/下载/知微浏览器/",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    SwitchSetting("每次下载询问保存位置", Settings.dlAskLocation) { Settings.dlAskLocation = it }
    ChoiceSetting(
        "并行下载数量上限", Settings.dlMaxParallel.toString(),
        listOf("1" to "1", "2" to "2", "4" to "4（默认）", "8" to "8")
    ) { Settings.dlMaxParallel = it.toInt() }
    SwitchSetting("断点续传", Settings.dlResume) { Settings.dlResume = it }
    ChoiceSetting(
        "下载完成时动作", Settings.dlDoneAction.toString(),
        listOf("0" to "无操作（默认）", "1" to "打开文件", "2" to "打开所在文件夹")
    ) { Settings.dlDoneAction = it.toInt() }
    SwitchSetting("危险文件类型警告", Settings.dlDangerWarn) { Settings.dlDangerWarn = it }
    SwitchSetting("下载完成通知", Settings.dlNotify) { Settings.dlNotify = it }
}

@Composable
private fun AccessibilityPage() {
    Settings.version.intValue
    SliderSetting("网页文字缩放", Settings.textZoom, 50, 200, "%d%%") { Settings.textZoom = it }
    SwitchSetting("网页强制深色", Settings.forceDarkWeb) { Settings.forceDarkWeb = it }
}

@Composable
private fun LanguagePage() {
    Settings.version.intValue
    ChoiceSetting(
        "翻译目标语言", Settings.translateTarget,
        listOf("zh-CN" to "简体中文", "zh-TW" to "繁體中文", "en" to "English", "ja" to "日本語", "ko" to "한국어", "fr" to "Français", "de" to "Deutsch", "ru" to "Русский", "es" to "Español")
    ) { Settings.translateTarget = it }
    SwitchSetting("自动翻译英文页面", Settings.autoTranslate) { Settings.autoTranslate = it }
}

@Composable
private fun AiControlPage() {
    Settings.version.intValue
    SectionTitle("总开关")
    SwitchSetting("阻止 AI 增强（关闭所有 AI 入口与提示）", Settings.aiBlocked) { Settings.aiBlocked = it }
    if (!Settings.aiBlocked) {
        SectionTitle("子功能")
        SwitchSetting("侧边栏 AI 助手", Settings.aiSidebar) { Settings.aiSidebar = it }
        SwitchSetting("网页翻译（AI 驱动）", Settings.aiTranslate) { Settings.aiTranslate = it }
        SwitchSetting("链接预览摘要", Settings.aiLinkPreview) { Settings.aiLinkPreview = it }
        SwitchSetting("标签页分组建议", Settings.aiTabGroup) { Settings.aiTabGroup = it }
        SwitchSetting("PDF 图像替代文本", Settings.aiPdfAlt) { Settings.aiPdfAlt = it }
        Text("AI 不绑定单一提供商，下方可自由切换或自建中转。处理页面内容时，数据将发送至所选提供商。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        SectionTitle("提供商配置")
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            AiProviderPicker()
        }
        TextSetting("API Base", Settings.aiBase) { Settings.aiBase = it; Settings.aiProvider = "自定义 / 第三方中转站" }
        TextSetting("API Key", Settings.aiKey, secret = true) { Settings.aiKey = it }
        TextSetting("模型", Settings.aiModel) { Settings.aiModel = it; Settings.aiProvider = "自定义 / 第三方中转站" }
        TextSetting("系统提示词", Settings.aiPrompt, singleLine = false) { Settings.aiPrompt = it }
    } else {
        Text("已阻止全部 AI 增强：AI 菜单入口、总结/翻译面板与相关提示均不再出现。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp))
    }
}

@Composable
private fun AboutPage(vm: BrowserViewModel) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var updateUrl by remember { mutableStateOf<String?>(null) }

    val webviewVer = remember {
        runCatching {
            val pi = context.packageManager.getPackageInfo("com.google.android.webview", 0)
            "${pi.packageName}\n${pi.versionName}"
        }.getOrDefault("未知")
    }
    val resolution = remember {
        val m = context.resources.displayMetrics
        "${m.widthPixels} * ${m.heightPixels}"
    }
    val locale = remember { java.util.Locale.getDefault().toString().replace("_", ",") }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // ---------- 头部：英文名 + 版本（大面积留白） ----------
        Spacer(Modifier.height(48.dp))
        Text(
            "zhiwei browser",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            com.java.myapplication.BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(36.dp))

        // ---------- 白色胶囊更新按钮 ----------
        Surface(
            onClick = {
                if (checking) return@Surface
                checking = true
                updateMsg = null; updateUrl = null
                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    try {
                        val conn = java.net.URL("https://api.github.com/repos/mingxiozsg-star/zhiwei-browser/releases/latest")
                            .openConnection() as java.net.HttpURLConnection
                        conn.connectTimeout = 10000; conn.readTimeout = 10000
                        conn.setRequestProperty("Accept", "application/vnd.github+json")
                        val body = conn.inputStream.bufferedReader().readText()
                        val json = org.json.JSONObject(body)
                        val tag = json.optString("tag_name").removePrefix("v")
                        // 直接取 Release 里的 APK 附件地址，应用内下载，不跳外部浏览器
                        var apkUrl = json.optString("html_url")
                        val assets = json.optJSONArray("assets")
                        if (assets != null) {
                            for (i in 0 until assets.length()) {
                                val a = assets.getJSONObject(i)
                                if (a.optString("name").endsWith(".apk")) {
                                    apkUrl = a.optString("browser_download_url"); break
                                }
                            }
                        }
                        val cur = com.java.myapplication.BuildConfig.VERSION_NAME
                        launch(kotlinx.coroutines.Dispatchers.Main) {
                            checking = false
                            if (tag.isNotEmpty() && tag != cur) {
                                updateMsg = "发现新版本 v$tag（当前 v$cur）"
                                updateUrl = apkUrl
                            } else updateMsg = "已是最新版本（v$cur）"
                        }
                    } catch (e: Exception) {
                        launch(kotlinx.coroutines.Dispatchers.Main) {
                            checking = false
                            updateMsg = "检查失败：${e.message}"
                        }
                    }
                }
            },
            color = Color.White,
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 2.dp,
            modifier = Modifier.width(200.dp).height(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    if (checking) "检查中…" else "软件更新",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black
                )
            }
        }
        updateMsg?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            updateUrl?.let { url ->
                TextButton(onClick = {
                    // 应用内直接下载 APK，不跳转其他浏览器
                    vm.promptDownload(context, url, null, null, "application/vnd.android.package-archive")
                }) { Text("去下载", style = MaterialTheme.typography.labelSmall) }
            }
        }

        Spacer(Modifier.height(44.dp))

        // ---------- 软件与版本信息 ----------
        AboutSection("软件与版本信息") {
            AboutRow("软件名称", "Zhiwei Browser")
            AboutRow("软件版本", "${com.java.myapplication.BuildConfig.VERSION_NAME}（${com.java.myapplication.BuildConfig.VERSION_CODE}）")
            AboutRow("软件版本标识", com.java.myapplication.BuildConfig.BUILD_TYPE)
            AboutRow("编译日期", com.java.myapplication.BuildConfig.BUILD_TIME)
            AboutRow("增强特性",
                "Zhiwei AI 智能助手\nWebView 多标签内核\n广告拦截规则引擎\n整页自动翻译\n磨砂质感主页\n方向感知转场动画")
        }
        Spacer(Modifier.height(24.dp))

        // ---------- 设备与系统信息 ----------
        AboutSection("设备与系统信息") {
            AboutRow("系统版本", "Android ${android.os.Build.VERSION.RELEASE}")
            AboutRow("语言与地区", locale)
            AboutRow("设备", "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}")
            AboutRow("屏幕分辨率", resolution)
            AboutRow("WebKit", webviewVer)
        }
        Spacer(Modifier.height(24.dp))

        // ---------- 软件产品法律信息 ----------
        AboutSection("软件产品法律信息") {
            Row(
                Modifier.fillMaxWidth().clickable {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/mingxiozsg-star/zhiwei-browser/blob/main/LICENSE")))
                }.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("许可与服务协议（MIT）", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(48.dp))
        Text(
            "Zhiwei Team 出品",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) { content() }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(24.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
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