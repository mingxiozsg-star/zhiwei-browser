package com.java.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.java.myapplication.BrowserViewModel
import com.java.myapplication.Settings
import com.java.myapplication.Store
import com.java.myapplication.Tab

@Composable
fun BrowserScreen(
    vm: BrowserViewModel,
    onOpenSettings: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAi: () -> Unit,
) {
    val context = LocalContext.current
    val tab = vm.current
    var menuOpen by remember { mutableStateOf(false) }
    var findBar by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }

    val settingsVersion = Settings.version.intValue
    LaunchedEffect(settingsVersion) { vm.refreshAllSettings() }

    val isHomeTab = tab?.isHome != false
    val wallpaper = if (isHomeTab) rememberHomeWallpaper() else null

    // 根部：壁纸全屏铺满（含底栏区域）
    Box(Modifier.fillMaxSize()) {
        if (wallpaper != null) {
            androidx.compose.foundation.Image(
                bitmap = wallpaper,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }

    Column(
        Modifier.fillMaxSize()
            .background(if (wallpaper != null) Color.Transparent else MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
    ) {

        // ---------- 加载进度 ----------
        if (tab?.isLoading == true && !tab.isHome) {
            LinearProgressIndicator(
                progress = { tab.progress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        } else {
            Spacer(Modifier.height(2.dp))
        }

        // ---------- 页内查找 ----------
        if (findBar) {
            FindBar(
                query = findQuery,
                onQueryChange = { q ->
                    findQuery = q
                    tab?.webView?.findAllAsync(q)
                },
                onPrev = { tab?.webView?.findNext(false) },
                onNext = { tab?.webView?.findNext(true) },
                onClose = { findBar = false; tab?.webView?.clearMatches() }
            )
        }

        // ---------- 内容区 ----------
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == null) {
                EmptyHome { vm.addTab(context) }
            } else if (tab.isHome) {
                HomePage(
                    wallpaper = wallpaper,
                    onSearch = { q -> vm.homeNavigate(tab, q) },
                    onOpenUrl = { url -> vm.homeNavigate(tab, url) }
                )
            } else {
                key(tab.id) {
                    WebContent(tab, vm)
                }
                if (tab.loadError) {
                    ErrorPage(onRetry = { tab.webView?.reload() })
                }
            }
        }

        // ---------- 标签条（仅多标签时显示） ----------
        if (vm.tabs.size > 1) {
            TabStrip(vm)
        }

        // ---------- 底部工具栏 ----------
        val barTint = if (wallpaper != null) Color.White else MaterialTheme.colorScheme.onSurface
        Surface(
            color = if (wallpaper != null) Color.Transparent else MaterialTheme.colorScheme.surface,
            tonalElevation = if (wallpaper != null) 0.dp else 3.dp
        ) {
            if (tab?.isHome != false) {
                // 主页：五键导航底栏 ← → ⌂ ☰ ↻（整体上抬，按钮加大）
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = { tab?.webView?.goBack() }, enabled = tab?.canGoBack == true, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "后退", tint = barTint, modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = { tab?.webView?.goForward() }, enabled = tab?.canGoForward == true, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "前进", tint = barTint, modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = { tab?.let { vm.goHome(it) } }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.Home, "主页", tint = barTint, modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.Menu, "菜单", tint = barTint, modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = { tab?.webView?.reload() }, enabled = tab?.webView != null, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.Refresh, "刷新", tint = barTint, modifier = Modifier.size(28.dp))
                    }
                }
            } else {
                // 浏览：导航 + 地址胶囊 + 菜单
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { tab.webView?.goBack() }, enabled = tab.canGoBack, modifier = Modifier.size(52.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "后退", modifier = Modifier.size(26.dp))
                    }
                    AddressPill(
                        tab = tab,
                        modifier = Modifier.weight(1f),
                        onNavigate = { input -> vm.homeNavigate(tab, input) }
                    )
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, "菜单")
                    }
                }
            }
        }
    }

    // ---------- 菜单面板（底部弹出，宫格样式） ----------
    if (menuOpen) {
        MenuSheet(
            tab = tab,
            onDismiss = { menuOpen = false },
            onAction = { action ->
                menuOpen = false
                handleMenuAction(action, vm, tab, context,
                    onOpenSettings, onOpenBookmarks, onOpenHistory, onOpenAi) { findBar = true }
            }
        )
    }
    }
}

// ================= 菜单 =================

private enum class MenuAction {
    HOME, NEW_TAB, BOOKMARK, BOOKMARKS, HISTORY, ADD_SHORTCUT,
    FIND, TRANSLATE, DESKTOP, SHARE, COPY_LINK, AI, SETTINGS
}

/** 底部弹出菜单面板：时间日期头 + 图标宫格 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuSheet(
    tab: Tab?,
    onDismiss: () -> Unit,
    onAction: (MenuAction) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        // 头部：时间 + 日期
        val now = remember { java.util.Date() }
        val time = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(now) }
        val day = remember { java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA).format(now) }
        val week = remember {
            arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")[
                java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) - 1]
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(time, style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(day, style = MaterialTheme.typography.titleSmall)
                Text(week, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(18.dp))

        // 宫格项
        val items = buildList {
            add(Triple(Icons.Filled.Home, "回主页", MenuAction.HOME))
            add(Triple(Icons.Filled.Add, "新建标签", MenuAction.NEW_TAB))
            add(Triple(Icons.Filled.Star, "书签", MenuAction.BOOKMARKS))
            add(Triple(Icons.Filled.DateRange, "历史记录", MenuAction.HISTORY))
            add(Triple(Icons.Filled.Settings, "设置", MenuAction.SETTINGS))
            if (tab != null && !tab.isHome) {
                add(Triple(
                    if (Store.isBookmarked(tab.url)) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    if (Store.isBookmarked(tab.url)) "取消收藏" else "收藏本页", MenuAction.BOOKMARK))
                add(Triple(Icons.Filled.Share, "分享", MenuAction.SHARE))
                add(Triple(Icons.Filled.List, "复制链接", MenuAction.COPY_LINK))
                add(Triple(Icons.Filled.Search, "页内查找", MenuAction.FIND))
                add(Triple(Icons.Filled.Refresh, "翻译本页", MenuAction.TRANSLATE))
                add(Triple(
                    if (Settings.desktopMode) Icons.Filled.Home else Icons.Filled.Home,
                    if (Settings.desktopMode) "手机模式" else "桌面模式", MenuAction.DESKTOP))
                add(Triple(Icons.Filled.Add, "加到主页", MenuAction.ADD_SHORTCUT))
            }
            add(Triple(Icons.Filled.Face, "AI 助手", MenuAction.AI))
        }

        items.chunked(5).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { (icon, label, action) ->
                    MenuCell(icon, label) { onAction(action) }
                }
                repeat(5 - row.size) { Spacer(Modifier.width(64.dp)) }
            }
            Spacer(Modifier.height(10.dp))
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun MenuCell(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, label, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(5.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

private fun handleMenuAction(
    action: MenuAction,
    vm: BrowserViewModel,
    tab: Tab?,
    context: android.content.Context,
    onOpenSettings: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAi: () -> Unit,
    onFind: () -> Unit,
) {
    when (action) {
        MenuAction.HOME -> tab?.let { vm.goHome(it) }
        MenuAction.NEW_TAB -> vm.addTab(context)
        MenuAction.BOOKMARK -> tab?.let { Store.toggleBookmark(it.title, it.url) }
        MenuAction.BOOKMARKS -> onOpenBookmarks()
        MenuAction.HISTORY -> onOpenHistory()
        MenuAction.ADD_SHORTCUT -> tab?.let { if (it.url.isNotBlank()) Store.addShortcut(it.title, it.url) }
        MenuAction.FIND -> onFind()
        MenuAction.TRANSLATE -> tab?.webView?.loadUrl(Settings.pageTranslateUrl(tab.url))
        MenuAction.DESKTOP -> {
            Settings.desktopMode = !Settings.desktopMode
            vm.refreshAllSettings()
            tab?.webView?.reload()
        }
        MenuAction.SHARE -> tab?.let {
            val i = android.content.Intent(android.content.Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(android.content.Intent.EXTRA_TEXT, "${it.title}\n${it.url}")
            context.startActivity(android.content.Intent.createChooser(i, "分享"))
        }
        MenuAction.COPY_LINK -> tab?.let {
            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("url", it.url))
            android.widget.Toast.makeText(context, "链接已复制", android.widget.Toast.LENGTH_SHORT).show()
        }
        MenuAction.AI -> onOpenAi()
        MenuAction.SETTINGS -> onOpenSettings()
    }
}

// ================= 底部地址胶囊 =================

@Composable
private fun AddressPill(tab: Tab, modifier: Modifier, onNavigate: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(tab.id, tab.url, tab.searchQuery) {
        if (!editing) text = if (tab.searchQuery.isNotEmpty()) tab.searchQuery else tab.url
    }

    TextField(
        value = text,
        onValueChange = { text = it },
        modifier = modifier
            .clip(CircleShape)
            .onFocusChanged { f ->
                editing = f.isFocused
                if (f.isFocused) text = if (tab.searchQuery.isNotEmpty()) tab.searchQuery else tab.url
            },
        placeholder = { Text("搜索或输入网址", style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        leadingIcon = {
            if (tab.url.startsWith("https://")) Text("🔒", style = MaterialTheme.typography.bodySmall)
            else Icon(Icons.Filled.Search, null, Modifier.size(16.dp))
        },
        trailingIcon = {
            when {
                editing && text.isNotEmpty() ->
                    IconButton(onClick = { text = "" }) { Icon(Icons.Filled.Close, "清空", Modifier.size(16.dp)) }
                tab.isLoading ->
                    IconButton(onClick = { tab.webView?.stopLoading() }) { Icon(Icons.Filled.Close, "停止", Modifier.size(16.dp)) }
                else ->
                    IconButton(onClick = { tab.webView?.reload() }) { Icon(Icons.Filled.Refresh, "刷新", Modifier.size(16.dp)) }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = {
            onNavigate(text)
            keyboard?.hide()
        }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        textStyle = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun AiButton(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
        modifier = Modifier.clip(CircleShape).clickable(onClick = onClick)
    ) {
        Text(
            "AI",
            Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

// ================= 其余组件 =================

@Composable
private fun FindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).height(52.dp),
                placeholder = { Text("在页面中查找", style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall
            )
            IconButton(onClick = onPrev) { Icon(Icons.Filled.KeyboardArrowUp, "上一个") }
            IconButton(onClick = onNext) { Icon(Icons.Filled.KeyboardArrowDown, "下一个") }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "关闭") }
        }
    }
}

@Composable
private fun WebContent(tab: Tab, vm: BrowserViewModel) {
    AndroidView(
        factory = { ctx -> vm.ensureWebView(ctx, tab) },
        update = { w -> (w.parent as? android.view.ViewGroup)?.removeView(w) },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun TabStrip(vm: BrowserViewModel) {
    val context = LocalContext.current
    LazyRow(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(vm.tabs, key = { _, t -> t.id }) { index, t ->
            val selected = index == vm.currentIndex
            Surface(
                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(end = 6.dp)
                    .clickable { vm.currentIndex = index }
            ) {
                Row(
                    Modifier.padding(start = 12.dp, end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (t.isHome) "主页" else t.title,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(96.dp)
                    )
                    IconButton(onClick = { vm.closeTab(index) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, "关闭标签", Modifier.size(14.dp))
                    }
                }
            }
        }
        item {
            IconButton(onClick = { vm.addTab(context) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Add, "新建标签")
            }
        }
    }
}

@Composable
private fun EmptyHome(onNewTab: () -> Unit) {
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("没有打开的标签", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            "＋ 新建标签",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.clickable(onClick = onNewTab)
        )
    }
}

@Composable
private fun ErrorPage(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Warning, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Text("页面加载失败", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "点按重试",
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onRetry)
        )
    }
}