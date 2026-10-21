package com.java.myapplication

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.java.myapplication.ui.AiSheet
import com.java.myapplication.ui.BookmarksScreen
import com.java.myapplication.ui.BrowserScreen
import com.java.myapplication.ui.HistoryScreen
import com.java.myapplication.ui.OnboardingScreen
import com.java.myapplication.ui.SettingsScreen
import com.java.myapplication.ui.SplashScreen
import com.java.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private lateinit var vm: BrowserViewModel
    private var screen by mutableStateOf("splash") // splash/onboarding/browser/settings/bookmarks/history
    private var navDir by androidx.compose.runtime.mutableIntStateOf(1) // 1=前进 -1=返回
    private var aiOpen by mutableStateOf(false)

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cb = vm.fileChooserCallback
        vm.fileChooserCallback = null
        if (cb == null) return@registerForActivityResult
        val uris: Array<Uri>? = if (result.resultCode == RESULT_OK) {
            val data = result.data
            when {
                data?.clipData != null -> Array(data.clipData!!.itemCount) { data.clipData!!.getItemAt(it).uri }
                data?.data != null -> arrayOf(data.data!!)
                else -> null
            }
        } else null
        cb.onReceiveValue(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Settings.init(this)
        Store.init(this)
        vm = ViewModelProvider(this)[BrowserViewModel::class.java]

        // 恢复会话或新建
        if (vm.tabs.isEmpty()) {
            val external = intent?.dataString
            val restored = if (Settings.restoreTabs) Store.restoreSession() else emptyList()
            when {
                external != null -> vm.addTab(this, external)
                restored.isNotEmpty() -> restored.forEach { vm.addTab(this, it) }
                else -> vm.addTab(this)
            }
            vm.currentIndex = 0
        }

        // 全屏视频
        vm.onFullscreenChange = fullscreen@{ enter ->
            if (enter) {
                val v = vm.fullscreenView ?: return@fullscreen
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.decorView.systemUiVisibility = (
                        android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        )
                (window.decorView as ViewGroup).addView(
                    v,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
        }

        setContent {
            val dark = when (Settings.darkTheme) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            MyApplicationTheme(darkTheme = dark) {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        val dir = navDir
                        (fadeIn(tween(240)) + slideInHorizontally(tween(240)) { dir * it / 5 })
                            .togetherWith(fadeOut(tween(180)) + slideOutHorizontally(tween(180)) { -dir * it / 5 })
                    },
                    label = "screenSwitch"
                ) { scr ->
                // 子页面（设置/书签/历史）系统返回键 → 带动画回主页
                if (scr == "settings" || scr == "bookmarks" || scr == "history") {
                    BackHandler { navDir = -1; screen = "browser" }
                }
                when (scr) {
                    "splash" -> SplashScreen {
                        screen = if (Settings.firstRunDone) "browser" else "onboarding"
                    }
                    "onboarding" -> OnboardingScreen {
                        Settings.firstRunDone = true
                        navDir = 1
                        screen = "browser"
                    }
                    "settings" -> SettingsScreen(vm) { navDir = -1; screen = "browser" }
                    "bookmarks" -> BookmarksScreen(
                        onBack = { navDir = -1; screen = "browser" },
                        onOpen = { url -> vm.current?.webView?.loadUrl(url) ?: vm.addTab(this@MainActivity, url); navDir = -1; screen = "browser" }
                    )
                    "history" -> HistoryScreen(
                        onBack = { navDir = -1; screen = "browser" },
                        onOpen = { url -> vm.current?.webView?.loadUrl(url) ?: vm.addTab(this@MainActivity, url); navDir = -1; screen = "browser" }
                    )
                    else -> {
                        Scaffold(Modifier.fillMaxSize()) { padding ->
                            BrowserScreen(
                                vm = vm,
                                onOpenSettings = { navDir = 1; screen = "settings" },
                                onOpenBookmarks = { navDir = 1; screen = "bookmarks" },
                                onOpenHistory = { navDir = 1; screen = "history" },
                                onOpenAi = { aiOpen = true },
                            )
                            // padding 交给边到边布局
                            @Suppress("UNUSED_EXPRESSION") padding
                        }
                        if (aiOpen) {
                            AiSheet(
                                vm = vm,
                                onDismiss = { aiOpen = false },
                                onOpenSettings = { aiOpen = false; navDir = 1; screen = "settings" }
                            )
                        }
                        var lastBackAt by remember { mutableStateOf(0L) }
                        BackHandler {
                            val tab = vm.current
                            when {
                                vm.fullscreenView != null -> vm.current?.webView?.webChromeClient?.onHideCustomView()
                                tab?.webView?.canGoBack() == true -> tab.webView?.goBack()
                                tab != null && !tab.isHome -> vm.goHome(tab)
                                else -> {
                                    val now = System.currentTimeMillis()
                                    if (now - lastBackAt < 2000) finish()
                                    else {
                                        lastBackAt = now
                                        android.widget.Toast.makeText(
                                            this@MainActivity, "再按一次退出", android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { vm.addTab(this, it) }
    }

    override fun onPause() {
        super.onPause()
        Store.saveSession(vm.tabs.map { it.url })
    }

    override fun onDestroy() {
        if (isFinishing) {
            vm.tabs.forEach { tab ->
                tab.webView?.let { w ->
                    (w.parent as? ViewGroup)?.removeView(w)
                    w.destroy()
                }
            }
        }
        super.onDestroy()
    }
}