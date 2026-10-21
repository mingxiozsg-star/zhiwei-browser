package com.java.myapplication.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.java.myapplication.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BrandGradient = Brush.verticalGradient(
    listOf(Color(0xFF3F51B5), Color(0xFF00BCD4))
)

// ==================== 开屏动画 ====================
@Composable
fun SplashScreen(onDone: () -> Unit) {
    var stage by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        stage = 1
        delay(1400)
        onDone()
    }
    val logoScale by animateFloatAsState(
        targetValue = if (stage == 1) 1f else 0.3f,
        animationSpec = spring(dampingRatio = 0.55f), label = "logoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (stage == 1) 1f else 0f,
        animationSpec = tween(500), label = "logoAlpha"
    )
    Box(
        Modifier.fillMaxSize().background(BrandGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(96.dp).scale(logoScale).alpha(logoAlpha)
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Text("知", style = MaterialTheme.typography.displayMedium, color = Color.White)
            }
            Spacer(Modifier.height(20.dp))
            AnimatedVisibility(
                visible = stage == 1,
                enter = fadeIn(tween(600, delayMillis = 300)) +
                        slideInVertically(tween(600, delayMillis = 300)) { it / 2 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("知微浏览器", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Spacer(Modifier.height(6.dp))
                    Text("见微知著 · 简洁高效", style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f))
                }
            }
        }
    }
}

// ==================== 厂商选择（设置页与引导页共用） ====================
@Composable
fun AiProviderPicker() {
    var expanded by remember { mutableStateOf(false) }
    Settings.version.intValue // 订阅设置变更
    Box {
        Surface(
            onClick = { expanded = true },
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("AI 厂商", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Settings.aiProvider, style = MaterialTheme.typography.bodyLarge)
                }
                Text("▾", style = MaterialTheme.typography.titleMedium)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Settings.AI_PROVIDERS.forEach { p ->
                DropdownMenuItem(
                    text = { Text(p.first) },
                    onClick = {
                        expanded = false
                        Settings.aiProvider = p.first
                        if (p.second.isNotEmpty()) {
                            Settings.aiBase = p.second
                            Settings.aiModel = p.third
                        }
                    }
                )
            }
        }
    }
}

// ==================== 首次使用引导页 ====================
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
        listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ))) {
        Column(Modifier.fillMaxSize()) {
            // 跳过
            Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
                if (pagerState.currentPage < 2) {
                    TextButton(onClick = onDone) { Text("跳过") }
                }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (page) {
                    0 -> OnboardPage(
                        emoji = "🌐", title = "欢迎使用知微浏览器",
                        lines = listOf("简洁高效的浏览体验", "无广告打扰，轻量流畅", "自定义主页与壁纸")
                    )
                    1 -> OnboardPage(
                        emoji = "🤖", title = "内置 AI 助手",
                        lines = listOf("网页一键总结", "整页翻译，翻页持续生效", "随时与 AI 对话")
                    )
                    else -> OnboardAiPage(onDone)
                }
            }
            // 指示点 + 按钮
            Row(
                Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                    repeat(3) { i ->
                        val active = pagerState.currentPage == i
                        val w by animateFloatAsState(if (active) 22f else 8f, tween(250), label = "dot")
                        Box(
                            Modifier.padding(4.dp).height(8.dp).width(w.dp)
                                .clip(CircleShape)
                                .background(
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                )
                        )
                    }
                }
                if (pagerState.currentPage < 2) {
                    Button(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) {
                        Text("下一步")
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardPage(emoji: String, title: String, lines: List<String>) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { show = true }

    // 呼吸浮动动画
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 1f, targetValue = 1.08f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1400, easing = FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse
        ), label = "pulseScale"
    )

    Column(
        Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(visible = show, enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 }) {
            Box(
                Modifier.size(120.dp).scale(pulse)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.25f)
                    ))),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier.size(96.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        ))),
                    contentAlignment = Alignment.Center
                ) { Text(emoji, style = MaterialTheme.typography.displaySmall) }
            }
        }
        Spacer(Modifier.height(28.dp))
        AnimatedVisibility(visible = show, enter = fadeIn(tween(500, 150)) + slideInVertically(tween(500, 150)) { it / 3 }) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(24.dp))
        lines.forEachIndexed { i, line ->
            AnimatedVisibility(
                visible = show,
                enter = fadeIn(tween(450, 250 + i * 120)) + slideInVertically(tween(450, 250 + i * 120)) { it / 3 }
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("✦", color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(line, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardAiPage(onDone: () -> Unit) {
    var show by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf(Settings.aiKey) }
    LaunchedEffect(Unit) { show = true }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(visible = show, enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 3 }) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔑", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(12.dp))
                Text("配置 AI 服务", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text("选择厂商自动填入 API 地址与模型，Key 需自行填写；也可使用第三方中转站",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(20.dp))
        AnimatedVisibility(visible = show, enter = fadeIn(tween(500, 200))) {
            Column {
                AiProviderPicker()
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it; Settings.aiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("开始使用")
                }
            }
        }
    }
}
