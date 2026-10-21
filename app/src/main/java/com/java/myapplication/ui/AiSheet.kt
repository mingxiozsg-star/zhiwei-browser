package com.java.myapplication.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.java.myapplication.BrowserViewModel
import com.java.myapplication.Settings
import com.java.myapplication.ai.AiClient
import com.java.myapplication.ai.Translator
import kotlinx.coroutines.launch

private data class ChatMsg(val role: String, val content: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSheet(vm: BrowserViewModel, onDismiss: () -> Unit, onOpenSettings: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("总结", "翻译", "对话")

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 16.dp)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabTitles.forEachIndexed { i, t ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(t) })
                }
            }
            Spacer(Modifier.height(12.dp))
            when (selectedTab) {
                0 -> SummaryPane(vm, onOpenSettings)
                1 -> TranslatePane(vm)
                2 -> ChatPane(vm, onOpenSettings)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SummaryPane(vm: BrowserViewModel, onOpenSettings: () -> Unit) {
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column {
        Text("总结当前网页", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        if (Settings.aiKey.isBlank()) {
            Text("未配置 AI Key", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onOpenSettings) { Text("前往设置") }
        } else {
            Button(
                onClick = {
                    val tab = vm.current ?: return@Button
                    loading = true; result = ""
                    vm.extractPageText(tab) { text ->
                        scope.launch {
                            result = try {
                                if (text.length < 50) "页面正文太短，无法总结"
                                else AiClient.summarize(text, tab.title, tab.url)
                            } catch (e: Exception) { "出错：${e.message}" }
                            loading = false
                        }
                    }
                },
                enabled = !loading
            ) { Text(if (loading) "总结中…" else "开始总结") }
        }
        if (loading) {
            Spacer(Modifier.height(8.dp))
            CircularProgressIndicator()
        }
        if (result.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text(result, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TranslatePane(vm: BrowserViewModel) {
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column {
        Text("文本翻译 → ${Settings.translateTarget}", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))

        // ---------- 整页翻译 ----------
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    val tab = vm.current ?: return@Button
                    if (tab.url.startsWith("http")) tab.webView?.loadUrl(Settings.pageTranslateUrl(tab.url))
                },
                enabled = vm.current?.url?.startsWith("http") == true
            ) { Text("整页翻译当前网页") }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("自动翻译", style = MaterialTheme.typography.bodyMedium)
                Text("开启后每个英文网页自动翻译，翻页持续生效", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(
                checked = Settings.autoTranslate,
                onCheckedChange = {
                    Settings.autoTranslate = it
                    if (it) {
                        val tab = vm.current
                        if (tab != null && tab.url.startsWith("http") && !tab.url.contains("translate.google")) {
                            tab.webView?.loadUrl(Settings.pageTranslateUrl(tab.url))
                        }
                    }
                }
            )
        }
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.fillMaxWidth().height(110.dp),
            placeholder = { Text("输入或粘贴要翻译的文本") }
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Button(
                onClick = {
                    loading = true; result = ""
                    scope.launch {
                        result = try { Translator.translate(input) } catch (e: Exception) { "出错：${e.message}" }
                        loading = false
                    }
                },
                enabled = !loading && input.isNotBlank()
            ) { Text(if (loading) "翻译中…" else "翻译") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                val tab = vm.current ?: return@TextButton
                loading = true; result = ""
                vm.extractPageText(tab) { text ->
                    scope.launch {
                        result = try { Translator.translate(text.take(6000)) } catch (e: Exception) { "出错：${e.message}" }
                        loading = false
                    }
                }
            }, enabled = !loading) { Text("提取本页正文翻译") }
        }
        if (result.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                Text(result, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private data class Attachment(val name: String, val mime: String, val text: String?, val imageBytes: ByteArray?)

@Composable
private fun ChatPane(vm: BrowserViewModel, onOpenSettings: () -> Unit) {
    var messages by remember { mutableStateOf(listOf<ChatMsg>()) }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var usePage by remember { mutableStateOf(true) }
    var attachment by remember { mutableStateOf<Attachment?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val pickFile = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val cr = context.contentResolver
                val mime = cr.getType(uri) ?: "application/octet-stream"
                val name = run {
                    var n = "附件"
                    cr.query(uri, null, null, null, null)?.use { c ->
                        val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (c.moveToFirst() && i >= 0) n = c.getString(i) ?: n
                    }
                    n
                }
                attachment = when {
                    mime.startsWith("image/") -> {
                        // 压缩到最长边 1024，JPEG 80，控制请求体大小
                        val raw = cr.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching
                        val bmp = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size)
                        if (bmp == null) null else {
                            val scale = minOf(1f, 1024f / maxOf(bmp.width, bmp.height))
                            val scaled = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(
                                bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
                            val baos = java.io.ByteArrayOutputStream()
                            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos)
                            Attachment(name, "image/jpeg", null, baos.toByteArray())
                        }
                    }
                    else -> {
                        val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching
                        // 判断是否为文本：UTF-8 解码后无可疑控制字符则视为文本
                        val asText = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
                        val isText = asText != null && bytes.size <= 2_000_000 &&
                            asText.count { it.code < 9 || (it.code in 14..31) } < asText.length / 100 + 4
                        if (isText) {
                            Attachment(name, mime, asText!!.take(6000), null)
                        } else {
                            // 二进制/其他类型：保存到浏览器专属附件目录
                            val dir = java.io.File(context.getExternalFilesDir(null), "attachments").apply { mkdirs() }
                            val out = java.io.File(dir, "${System.currentTimeMillis()}_$name")
                            out.writeBytes(bytes)
                            Attachment(name, mime, "（二进制文件已保存到本地：${out.absolutePath}）", null)
                        }
                    }
                }
            }
        }
    }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AI 对话", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = { usePage = !usePage }) {
                Text(if (usePage) "✓ 携带当前页面上下文" else "不携带页面上下文",
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        if (Settings.aiKey.isBlank()) {
            Text("未配置 AI Key", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onOpenSettings) { Text("前往设置") }
            return@Column
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().height(300.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { m ->
                val isUser = m.role == "user"
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = if (isUser) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.widthIn(max = 300.dp)
                    ) {
                        Text(m.content, Modifier.padding(10.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (loading) {
                item { Text("思考中…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            }
        }
        Spacer(Modifier.height(8.dp))

        // ---------- 待发送附件提示 ----------
        attachment?.let { att ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text(
                        (if (att.imageBytes != null) "🖼 " else "📄 ") + att.name +
                            (att.text?.let { "（${it.length}字）" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    TextButton(onClick = { attachment = null }) { Text("移除", style = MaterialTheme.typography.labelSmall) }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        // ---------- 输入行：附件 + 输入框 + 发送（固定高度，防拉伸） ----------
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Surface(
                onClick = { pickFile.launch("*/*") },
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(26.dp),
                modifier = Modifier.height(52.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp)
                ) { Text("📎", style = MaterialTheme.typography.titleMedium) }
            }
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f).heightIn(min = 52.dp, max = 120.dp),
                placeholder = { Text("问点什么…") },
                maxLines = 4,
                enabled = !loading
            )
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = {
                    val q = input.trim()
                    val att = attachment
                    if (q.isEmpty() && att == null) return@Button
                    input = ""
                    attachment = null
                    val shown = q + (att?.let { "\n[附件：${it.name}]" } ?: "")
                    messages = messages + ChatMsg("user", shown)
                    loading = true
                    scope.launch {
                        val answer = try {
                            when {
                                att?.imageBytes != null -> AiClient.chatWithImage(q, att.imageBytes, att.mime)
                                else -> {
                                    val qFull = if (att?.text != null)
                                        "【附件 ${att.name} 内容】\n${att.text}\n\n【问题】\n$q" else q
                                    val tab = vm.current
                                    if (usePage && tab != null && att == null) {
                                        var pageText = ""
                                        var done = false
                                        vm.extractPageText(tab) { t -> pageText = t; done = true }
                                        var wait = 0
                                        while (!done && wait < 50) { kotlinx.coroutines.delay(100); wait++ }
                                        if (pageText.length > 100) AiClient.askAboutPage(qFull, pageText, tab.title)
                                        else AiClient.chat(listOf(AiClient.Msg("system", Settings.aiPrompt), AiClient.Msg("user", qFull)))
                                    } else {
                                        AiClient.chat(listOf(AiClient.Msg("system", Settings.aiPrompt), AiClient.Msg("user", qFull)))
                                    }
                                }
                            }
                        } catch (e: Exception) { "出错：${e.message}" }
                        messages = messages + ChatMsg("assistant", answer)
                        loading = false
                        listState.animateScrollToItem(messages.size - 1)
                    }
                },
                enabled = !loading,
                modifier = Modifier.height(52.dp)
            ) { Text("发送") }
        }
    }
}