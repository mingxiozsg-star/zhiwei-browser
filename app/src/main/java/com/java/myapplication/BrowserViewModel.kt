package com.java.myapplication

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.JsResult
import android.webkit.JsPromptResult
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import android.app.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.java.myapplication.ai.AiTools
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

class Tab(val id: Long) {
    var webView: WebView? = null
    var url by mutableStateOf("")
    var title by mutableStateOf("新标签页")
    var progress by mutableIntStateOf(0)
    var isLoading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var favicon by mutableStateOf<Bitmap?>(null)
    var loadError by mutableStateOf(false)
    val isHome: Boolean get() = url.isBlank() || url.startsWith("zhiwei://home")
    /** 从主页搜索进入时，地址栏显示搜索词而非引擎URL */
    var searchQuery by mutableStateOf("")
    var pendingSearch: String? = null
    /** 正在把 WebView 清成空白页（回主页），此期间的加载回调不是真实浏览 */
    var blanking = false
    /** 页面加载完成后清空历史（从主页打开的第一个页面用它去掉多余的历史项） */
    var clearOnFinish = false
    /** 已经为哪个网址弹过网页提示，避免同一页重复打扰 */
    var cueUrl: String? = null
}

class BrowserViewModel : ViewModel() {

    val tabs = androidx.compose.runtime.mutableStateListOf<Tab>()
    var currentIndex by mutableIntStateOf(0)
    var fullscreenView: View? = null
    var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var idSeq = 0L

    val current: Tab? get() = tabs.getOrNull(currentIndex)

    fun addTab(context: Context, url: String = Settings.homepage, background: Boolean = false): Tab {
        val tab = Tab(++idSeq)
        tabs.add(tab)
        if (!background) currentIndex = tabs.size - 1
        ensureWebView(context, tab)
        if (url.isNotBlank() && !url.startsWith("zhiwei://")) {
            // 乐观更新：立即进入加载态，避免慢站点连接期间闪回主页
            tab.url = url
            tab.title = url
            tab.isLoading = true
            tab.progress = 5
            tab.webView?.loadUrl(url)
        }
        if (url.startsWith("zhiwei://")) tab.url = url
        return tab
    }

    /**
     * 回主页。
     *
     * 内置主页不是网页，必须同时清掉 WebView 的画面与历史：
     *  - 不清画面：下次从主页点开链接，WebView 重新上屏会先闪出上一页的残留内容；
     *  - 不清历史：新页面会叠在旧页面的历史上，按返回要退两次才能回到主页。
     */
    fun goHome(tab: Tab) {
        tab.loadError = false
        tab.searchQuery = ""
        tab.pendingSearch = null
        tab.isLoading = false
        tab.progress = 0
        tab.title = "新标签页"
        tab.canGoBack = false
        tab.canGoForward = false
        tab.url = Settings.homepage
        if (Settings.homepage.startsWith("zhiwei://")) {
            // 用 about:blank 顶掉旧页面（清屏）；历史的最终清理在 onPageFinished 里做
            tab.blanking = true
            tab.clearOnFinish = false
            tab.webView?.let { w ->
                w.stopLoading()
                w.loadUrl("about:blank")
            }
        } else {
            tab.blanking = false
            tab.webView?.loadUrl(Settings.homepage)
        }
    }

    /** 从主页发起搜索/导航，地址栏显示原始输入 */
    fun homeNavigate(tab: Tab, input: String) {
        val target = Settings.toUrl(input)
        val fromHome = tab.isHome          // 必须在改写 url 之前判断
        val isSearch = !target.startsWith("http") || target == Settings.searchUrl(input)
        tab.pendingSearch = if (isSearch || !input.contains(".")) input else null
        // 乐观更新：立即脱离主页态并进入加载画面，不等慢站点 onPageStarted
        tab.url = target
        tab.title = target
        tab.loadError = false
        tab.isLoading = true
        tab.progress = 5
        tab.blanking = false
        // 从主页打开的页面：加载完成后清空历史，返回键直接回主页
        if (fromHome) tab.clearOnFinish = true
        tab.webView?.loadUrl(target)
    }

    /** 书签 / 历史等入口打开网址：复用当前标签，走同一套导航逻辑 */
    fun openUrl(context: Context, url: String) {
        val tab = current
        if (tab == null) addTab(context, url) else homeNavigate(tab, url)
    }

    fun closeTab(index: Int) {
        val tab = tabs.getOrNull(index) ?: return
        tab.webView?.let { w ->
            (w.parent as? ViewGroup)?.removeView(w)
            w.destroy()
        }
        tabs.removeAt(index)
        if (currentIndex >= tabs.size) currentIndex = tabs.size - 1
        if (currentIndex < 0) currentIndex = 0
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun ensureWebView(context: Context, tab: Tab): WebView {
        tab.webView?.let { return it }
        val w = ZwWebView(context)
        applySettings(w)
        w.webViewClient = BrowserClient(tab)
        w.webChromeClient = BrowserChrome(tab)
        w.setDownloadListener(DownloadListener { url, ua, cd, mime, _ ->
            promptDownload(context, url, ua, cd, mime)
        })
        // 长按链接/图片：给出与主流浏览器一致的快捷菜单
        w.setOnLongClickListener { view -> longPressMenu(context, view as WebView) }
        // 下拉刷新（设置内可关）
        w.pullThresholdPx = 90f * context.resources.displayMetrics.density
        w.onPullRefresh = {
            if (Settings.pullRefresh) {
                android.widget.Toast.makeText(context, "已刷新", android.widget.Toast.LENGTH_SHORT).show()
                (w as WebView).reload()
            }
        }
        tab.webView = w
        return w
    }

    /** 长按命中链接/图片时的快捷操作菜单 */
    private fun longPressMenu(context: Context, w: WebView): Boolean {
        val hit = w.hitTestResult ?: return false
        val extra = hit.extra ?: return false
        val isImage = hit.type == WebView.HitTestResult.IMAGE_TYPE
        val actions: List<Pair<String, () -> Unit>> = when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> listOf(
                "在新标签中打开" to { addTab(context, extra) },
                "在后台标签打开" to { addTab(context, extra, background = true) },
                "复制链接" to { copyText(context, extra, "链接已复制") },
                "下载链接" to { promptDownload(context, extra, w.settings.userAgentString, null, null) },
                "分享链接" to { shareText(context, extra) }
            )
            WebView.HitTestResult.IMAGE_TYPE,
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> listOf(
                "在新标签中打开图片" to { addTab(context, extra) },
                "保存图片" to { promptDownload(context, extra, w.settings.userAgentString, null, null) },
                "复制图片地址" to { copyText(context, extra, "图片地址已复制") },
                "分享图片地址" to { shareText(context, extra) }
            )
            else -> return false
        }
        runCatching {
            android.app.AlertDialog.Builder(context)
                .setTitle(if (isImage) "图片" else "链接")
                .setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
                .show()
        }
        return true
    }

    private fun copyText(context: Context, text: String, toast: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("text", text))
        android.widget.Toast.makeText(context, toast, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun shareText(context: Context, text: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(Intent.createChooser(i, "分享")) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun applySettings(w: WebView) {
        w.settings.apply {
            javaScriptEnabled = Settings.jsEnabled
            domStorageEnabled = Settings.domStorage
            loadsImagesAutomatically = Settings.loadImages
            blockNetworkImage = !Settings.loadImages
            textZoom = Settings.textZoom
            userAgentString = if (Settings.desktopMode) Settings.DESKTOP_UA
            else WebSettings.getDefaultUserAgent(w.context)
            setSupportZoom(true)
            setSupportMultipleWindows(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            javaScriptCanOpenWindowsAutomatically = !Settings.blockPopups
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            if (Settings.doNotTrack) {
                // WebView 无标准 DNT，这里通过额外头不可行；保留设置项供未来扩展
            }
        }
        CookieManager.getInstance().setAcceptCookie(Settings.cookiesEnabled)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, Settings.thirdPartyCookies)
        // 强制网页深色
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            WebSettingsCompat.setForceDark(
                w.settings,
                if (Settings.forceDarkWeb) WebSettingsCompat.FORCE_DARK_ON
                else WebSettingsCompat.FORCE_DARK_AUTO
            )
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
            @Suppress("DEPRECATION")
            WebSettingsCompat.setForceDarkStrategy(
                w.settings,
                WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY
            )
        }
    }

    fun refreshAllSettings() {
        tabs.forEach { it.webView?.let { w -> applySettings(w) } }
    }

    // ---------------- 网页提示：英文页翻译 / 长文总结 / 选中问答 ----------------

    enum class CueKind { TRANSLATE, SUMMARY }

    /** 待用户确认的一条网页提示 */
    data class PageCue(
        val kind: CueKind,
        val title: String,
        val body: String,
        val confirm: String,
        val host: String,
        val url: String
    )

    /** 当前待确认的提示；null 表示没有 */
    var pageCue by mutableStateOf<PageCue?>(null)

    /**
     * 页面加载完成后探测一次：英文页问要不要翻译，长文页问要不要总结。
     * 只用轻量 JS 取 lang / 正文长度 / 中英文字符数，不把整页正文拉回客户端。
     */
    private fun probePage(tab: Tab, view: WebView) {
        if (!Settings.assistPrompt) return
        if (!tab.url.startsWith("http")) return
        if (tab.url == tab.cueUrl) return          // 同一页只提示一次
        tab.cueUrl = tab.url
        val js = """
            (function(){
              try{
                var t = document.body ? (document.body.innerText||'') : '';
                var lang = (document.documentElement.getAttribute('lang')||'').toLowerCase();
                var cjk = (t.match(/[\u4e00-\u9fff]/g)||[]).length;
                var latin = (t.match(/[A-Za-z]/g)||[]).length;
                return JSON.stringify({lang:lang,len:t.length,cjk:cjk,latin:latin});
              }catch(e){ return ''; }
            })();
        """.trimIndent()
        view.evaluateJavascript(js) { raw ->
            val s = unescapeJs(raw ?: "")
            if (s.isBlank()) return@evaluateJavascript
            val o = runCatching { org.json.JSONObject(s) }.getOrNull() ?: return@evaluateJavascript
            val host = runCatching { java.net.URI(tab.url).host ?: "" }.getOrDefault("")
            if (host.isBlank() || Settings.isAssistMuted(host)) return@evaluateJavascript
            val lang = o.optString("lang")
            val len = o.optInt("len")
            val cjk = o.optInt("cjk")
            val latin = o.optInt("latin")
            val english = lang.startsWith("en") || (latin > 240 && cjk < latin / 20)
            if (english) {
                pageCue = PageCue(
                    CueKind.TRANSLATE, "检测到英文页面",
                    "要不要翻译成 ${Settings.translateTarget}？翻译后站内链接会持续翻译。",
                    "翻译", host, tab.url
                )
            } else if (len >= 900) {
                pageCue = PageCue(
                    CueKind.SUMMARY, "这篇页面比较长",
                    "正文约 $len 个字符，要不要让知微总结一下要点？",
                    "总结", host, tab.url
                )
            }
        }
    }

    /** 提示被确认：翻译当前页，或交给界面打开 AI 总结 */
    fun acceptCue(cue: PageCue, onSummary: () -> Unit) {
        pageCue = null
        val tab = current
        when (cue.kind) {
            CueKind.TRANSLATE -> if (tab != null && tab.url.startsWith("http")) {
                tab.webView?.loadUrl(Settings.pageTranslateUrl(tab.url))
            }
            CueKind.SUMMARY -> onSummary()
        }
    }

    fun dismissCue() { pageCue = null }

    /** 提示不再打扰这个站点 */
    fun muteCue(cue: PageCue) {
        Settings.muteAssist(cue.host)
        pageCue = null
    }

    /** 取当前选中的文字；没选中时回调空串 */
    fun selectedText(tab: Tab, onText: (String) -> Unit) {
        val js = "(function(){try{return (window.getSelection()||'').toString();}catch(e){return '';}})();"
        tab.webView?.evaluateJavascript(js) { raw -> onText(unescapeJs(raw ?: "").trim()) }
    }

    fun extractPageText(tab: Tab, callback: (String) -> Unit) {
        val js = """
            (function(){
              var clone = document.body ? document.body.cloneNode(true) : null;
              if(!clone) return '';
              clone.querySelectorAll('script,style,noscript,iframe,nav,header,footer').forEach(function(e){e.remove();});
              return (clone.innerText||'').replace(/\n{3,}/g,'\n\n').trim();
            })();
        """.trimIndent()
        tab.webView?.evaluateJavascript(js) { raw ->
            callback(unescapeJs(raw ?: ""))
        } ?: callback("")
    }

    private fun unescapeJs(s: String): String {
        if (s == "null" || s.isEmpty()) return ""
        return try {
            org.json.JSONArray("[${s}]").getString(0)
        } catch (e: Exception) { s }
    }

    // ---------------- 下载 ----------------
    /**
     * 点链接时直接走下载的文件类型。
     * 只列「WebView 渲染不了」的：安装包、压缩包、可执行文件、文档、音视频、PDF。
     * 图片 / txt / json / xml 等交给 WebView 正常显示（要保存走长按菜单），
     * 否则点一下就弹下载框，看起来就是「网页没显示出来」。
     */
    private val downloadExts = listOf(
        ".apk", ".zip", ".rar", ".7z", ".tar", ".gz", ".exe", ".msi", ".dmg",
        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx", ".epub", ".mobi",
        ".mp3", ".wav", ".flac", ".mp4", ".mkv", ".avi", ".mov"
    )

    fun isDownloadLink(url: String): Boolean {
        val path = Uri.parse(url).path?.lowercase() ?: return false
        return downloadExts.any { path.endsWith(it) }
    }

    /** 翻译代理域名：命中说明当前已经在翻译页里，不能再跳一次翻译（否则会无限转圈） */
    private val translateHosts = listOf(
        "translate.google", "translate.googleapis", "translate.goog",
        "translate.bing", "translate.yandex", "fanyi.baidu", "translate.qq"
    )

    /** 弹出下载确认框；确认后按用户设置的下载引擎分派 */
    fun promptDownload(context: Context, url: String, ua: String?, contentDisposition: String?, mime: String?) {
        val name = URLUtil.guessFileName(url, contentDisposition, mime)
        val engine = Settings.dlEngine
        val engineHint = when (engine) {
            1 -> "保存到：下载/知微浏览器/（系统下载器）"
            2 -> "交给：${Settings.dlExternalLabel.ifBlank { "第三方下载器" }}"
            else -> "保存到：下载/知微浏览器/（内置下载器，带进度）"
        }
        AlertDialog.Builder(context)
            .setTitle("下载文件")
            .setMessage("$name\n\n$engineHint")
            .setPositiveButton("下载") { _, _ ->
                try {
                    when (engine) {
                        1 -> Downloader.enqueueSystem(context, url, name, ua, mime)
                        2 -> Downloader.enqueueExternal(context, url, mime, Settings.dlExternalPackage)
                        else -> Downloader.enqueue(
                            context, url, nameHint = name, ua = ua, mime = mime,
                            contentDisposition = contentDisposition,
                            // GitHub 资源自动展开成「直链 + 各加速」，交给下载器并发测速选最快
                            mirrors = UpdateChecker.mirrorUrls(url)
                        )
                    }
                    Toast.makeText(context, "开始下载：$name", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "下载失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /** 包内直链下载（更新页等场景）：不弹框，直接按引擎入队 */
    fun startDownloadDirect(
        context: Context,
        url: String,
        nameHint: String?,
        mime: String?,
        mirrors: List<String> = emptyList()
    ) {
        runCatching {
            when (Settings.dlEngine) {
                1 -> Downloader.enqueueSystem(context, url, nameHint, null, mime)
                2 -> Downloader.enqueueExternal(context, url, mime, Settings.dlExternalPackage)
                else -> Downloader.enqueue(context, url, nameHint = nameHint, mime = mime, mirrors = mirrors)
            }
        }
    }

    // ---------------- WebViewClient ----------------
    inner class BrowserClient(private val tab: Tab) : WebViewClient() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            return handleUrl(view, url)
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            return handleUrl(view, url)
        }

        private fun handleUrl(view: WebView, url: String): Boolean {
            if (url.startsWith("zhiwei://")) {
                tab.url = url
                return true
            }
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("about:")) {
                // 直链兜底：识别可下载文件扩展名，直接弹下载框
                if (isDownloadLink(url)) {
                    promptDownload(view.context, url, view.settings.userAgentString, null, null)
                    return true
                }
                tab.loadError = false
                return false
            }
            // intent://、tel:、mailto: 等外链
            return try {
                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                intent.component = null
                intent.selector = null
                view.context.startActivity(intent)
                true
            } catch (e: Exception) {
                if (url.startsWith("intent://")) {
                    val fallback = Uri.parse(url).getQueryParameter("browser_fallback_url")
                    if (fallback != null) { view.loadUrl(fallback); return true }
                }
                true
            }
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            if (AdBlocker.isBlocked(request.url.toString())) {
                return WebResourceResponse(
                    "text/plain", "utf-8",
                    ByteArrayInputStream(ByteArray(0))
                )
            }
            return super.shouldInterceptRequest(view, request)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            // 回主页时的清屏导航（about:blank）不是真实浏览，不要回写标签状态
            if (tab.blanking || url == "about:blank") return
            tab.isLoading = true
            tab.url = url
            tab.loadError = false
            // 主页搜索：地址栏显示搜索词；页内跳转则清空
            val pending = tab.pendingSearch
            if (pending != null) {
                tab.searchQuery = pending
                tab.pendingSearch = null
            } else {
                tab.searchQuery = ""
            }
            updateNav(tab, view)
        }

        override fun onPageFinished(view: WebView, url: String) {
            // 回主页的清屏：只留下这一条空白历史，界面继续停在内置主页
            if (tab.blanking || url == "about:blank") {
                if (tab.blanking) {
                    tab.blanking = false
                    view.clearHistory()
                    updateNav(tab, view)
                }
                return
            }
            tab.isLoading = false
            tab.url = view.url ?: url
            tab.title = view.title?.takeIf { it.isNotBlank() } ?: url
            // 从主页打开的第一个页面：清掉空白主页那一条历史，返回键即回主页
            if (tab.clearOnFinish) {
                tab.clearOnFinish = false
                view.clearHistory()
            }
            updateNav(tab, view)
            Store.addHistory(tab.title, tab.url)
            // 自动整页翻译：非翻译代理页面且是 http 页面时，跳转翻译（翻译代理内链接天然持续翻译）
            // 注意要排除所有翻译代理域名，否则「翻译页再被翻译」会反复跳转、页面一直转圈
            val translatable = tab.url.startsWith("http") && translateHosts.none { tab.url.contains(it) }
            if (translatable && Settings.autoTranslate && !Settings.autoTranslateAsk) {
                view.post { view.loadUrl(Settings.pageTranslateUrl(tab.url)) }
            } else if (translatable) {
                // 不直接翻译：交给网页提示问一句「要不要翻译 / 要不要总结」
                view.post { probePage(tab, view) }
            }
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
            if (request.isForMainFrame) {
                tab.loadError = true
                tab.isLoading = false
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            AlertDialog.Builder(view.context)
                .setTitle("证书错误")
                .setMessage("该站点的安全证书存在问题（${error.primaryError}）。继续访问可能有风险。")
                .setPositiveButton("仍要继续") { _, _ -> handler.proceed() }
                .setNegativeButton("返回") { _, _ -> handler.cancel() }
                .show()
        }

        private fun updateNav(tab: Tab, view: WebView) {
            tab.canGoBack = view.canGoBack()
            tab.canGoForward = view.canGoForward()
        }
    }

    // ---------------- WebChromeClient ----------------
    inner class BrowserChrome(private val tab: Tab) : WebChromeClient() {

        override fun onProgressChanged(view: WebView, newProgress: Int) {
            tab.progress = newProgress
        }

        override fun onReceivedTitle(view: WebView, title: String) {
            tab.title = title.ifBlank { tab.url }
        }

        override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
            tab.favicon = icon
        }

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            if (Settings.geoAllowed) callback.invoke(origin, true, false)
            else {
                AlertDialog.Builder(tab.webView?.context ?: return callback.invoke(origin, false, false))
                    .setTitle("位置请求")
                    .setMessage("$origin 请求获取你的位置信息")
                    .setPositiveButton("允许") { _, _ -> callback.invoke(origin, true, false) }
                    .setNegativeButton("拒绝") { _, _ -> callback.invoke(origin, false, false) }
                    .show()
            }
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            request.grant(request.resources)
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            fullscreenView = view
            fullscreenCallback = callback
            onFullscreenChange?.invoke(true)
        }

        override fun onHideCustomView() {
            fullscreenCallback?.onCustomViewHidden()
            fullscreenView = null
            fullscreenCallback = null
            onFullscreenChange?.invoke(false)
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = filePathCallback
            return try {
                val intent = fileChooserParams.createIntent()
                // 必须交给 Activity 的 launcher 启动，否则拿不到选择结果，网页上传会一直没反应
                val launcher = onLaunchFileChooser
                if (launcher != null) launcher(intent) else webView.context.startActivity(intent)
                true
            } catch (e: Exception) {
                fileChooserCallback = null
                false
            }
        }

        override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
            AlertDialog.Builder(view.context).setMessage(message)
                .setPositiveButton("确定") { _, _ -> result.confirm() }
                .setOnCancelListener { result.cancel() }.show()
            return true
        }

        override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
            AlertDialog.Builder(view.context).setMessage(message)
                .setPositiveButton("确定") { _, _ -> result.confirm() }
                .setNegativeButton("取消") { _, _ -> result.cancel() }
                .setOnCancelListener { result.cancel() }.show()
            return true
        }

        override fun onJsPrompt(view: WebView, url: String, message: String, defaultValue: String, result: JsPromptResult): Boolean {
            val input = EditText(view.context).apply { setText(defaultValue) }
            AlertDialog.Builder(view.context).setMessage(message).setView(input)
                .setPositiveButton("确定") { _, _ -> result.confirm(input.text.toString()) }
                .setNegativeButton("取消") { _, _ -> result.cancel() }
                .setOnCancelListener { result.cancel() }.show()
            return true
        }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
            if (Settings.blockPopups && !isUserGesture) return false
            // 弹窗转新标签
            val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
            val newTab = addTab(view.context, "")
            transport.webView = newTab.webView
            resultMsg.sendToTarget()
            return true
        }
    }

    var onFullscreenChange: ((Boolean) -> Unit)? = null

    /** 由 Activity 提供的文件选择启动器（网页 <input type="file"> 用） */
    var onLaunchFileChooser: ((Intent) -> Unit)? = null

    fun clearAllData(context: Context) {
        android.webkit.WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        context.cacheDir.deleteRecursively()
        tabs.forEach { it.webView?.clearCache(true) }
        Store.clearHistory()
    }

    // ==================== AI 网页操作工具 ====================

    /** 在 tab 上跑一段 JS 并取回字符串结果 */
    private suspend fun evalJs(tab: Tab, js: String): String = suspendCancellableCoroutine { cont ->
        val wv = tab.webView
        if (wv == null) {
            cont.resume("")
            return@suspendCancellableCoroutine
        }
        wv.evaluateJavascript(js) { raw -> if (cont.isActive) cont.resume(unescapeJs(raw ?: "")) }
    }

    /** 等这一页加载完（最多 waitMs），再留一点渲染时间，否则点不到刚出现的元素 */
    private suspend fun awaitPage(tab: Tab, waitMs: Long = 8000) {
        var waited = 0L
        while (tab.isLoading && waited < waitMs) {
            kotlinx.coroutines.delay(200)
            waited += 200
        }
        kotlinx.coroutines.delay(300)
    }

    /**
     * AI 工具执行入口。每个分支都返回一句人能看懂的结论，模型据此决定下一步。
     * 工具的名字与参数定义见 ai/AiTools.kt。
     */
    suspend fun aiTool(name: String, args: JSONObject): String {
        val tab = current ?: return "没有可操作的标签页"
        return when (name) {
            AiTools.WEB_READ -> {
                awaitPage(tab, 3000)
                val js = """
                    (function(){
                      try{
                        var c = document.body ? document.body.cloneNode(true) : null;
                        if(!c) return JSON.stringify({title:document.title,url:location.href,text:''});
                        c.querySelectorAll('script,style,noscript,iframe,nav,header,footer').forEach(function(e){e.remove();});
                        var t = (c.innerText||'').replace(/\n{3,}/g,'\n\n').trim();
                        return JSON.stringify({title:document.title,url:location.href,text:t.slice(0,6000)});
                      }catch(e){ return JSON.stringify({title:'',url:'',text:'读取失败:'+e.message}); }
                    })();
                """.trimIndent()
                val o = runCatching { JSONObject(evalJs(tab, js)) }.getOrNull()
                    ?: return "读取网页失败（页面可能还没加载完）"
                val text = o.optString("text")
                if (text.isBlank()) return "页面标题：${o.optString("title")}\n网址：${o.optString("url")}\n正文为空。"
                "标题：${o.optString("title")}\n网址：${o.optString("url")}\n正文（${text.length} 字）：\n${text.take(4000)}"
            }

            AiTools.WEB_LINKS -> {
                awaitPage(tab, 3000)
                val limit = args.optInt("limit", 40).coerceIn(5, 80)
                val js = """
                    (function(){
                      var out=[];
                      var els=document.querySelectorAll('a[href],button,input[type=submit],[role=button]');
                      for(var i=0;i<els.length && out.length<$limit;i++){
                        var e=els[i];
                        var r=e.getBoundingClientRect?e.getBoundingClientRect():{width:9,height:9};
                        if(r.width<2||r.height<2) continue;
                        var t=(e.innerText||e.value||e.getAttribute('aria-label')||'').replace(/\s+/g,' ').trim().slice(0,60);
                        if(!t) continue;
                        out.push((e.getAttribute('href')||'')+' || '+t);
                      }
                      return JSON.stringify(out);
                    })();
                """.trimIndent()
                val arr = runCatching { org.json.JSONArray(evalJs(tab, js)) }.getOrNull()
                if (arr == null || arr.length() == 0) "这一页没找到可点击的链接或按钮。"
                else buildString {
                    append("网址：").append(tab.url).append("\n可点击项：\n")
                    for (i in 0 until arr.length()) append(i + 1).append(". ").append(arr.optString(i)).append("\n")
                }
            }

            AiTools.WEB_OPEN -> {
                var u = args.optString("url").trim()
                if (!u.startsWith("http")) {
                    if (u.contains(".") && !u.contains(" ")) u = "https://$u" else return "网址不合法：$u"
                }
                val wv = tab.webView ?: return "浏览器还没准备好"
                if (args.optBoolean("new_tab", false)) {
                    val t = addTab(wv.context, u)
                    awaitPage(t, 10000)
                    "已在新标签页打开：${t.url}（${t.title}）"
                } else {
                    tab.url = u
                    wv.loadUrl(u)
                    awaitPage(tab, 10000)
                    "已打开：${tab.url}（${tab.title}）"
                }
            }

            AiTools.WEB_CLICK -> {
                val target = args.optString("target").trim()
                if (target.isBlank()) return "缺少 target：请给出要点击的文字或 CSS 选择器"
                awaitPage(tab, 3000)
                val js = """
                    (function(){
                      var tg=__TARGET__;
                      function vis(e){var r=e.getBoundingClientRect();return r.width>1&&r.height>1;}
                      var el=null;
                      try{ var q=document.querySelector(tg); if(q&&vis(q)) el=q; }catch(e){}
                      if(!el){
                        var c=document.querySelectorAll('a,button,[role=button],input[type=submit],input[type=button],summary,label');
                        var best=null,bl=0;
                        for(var i=0;i<c.length;i++){
                          var e=c[i]; if(!vis(e)) continue;
                          var t=((e.innerText||e.value||'')+'').replace(/\s+/g,' ').trim();
                          if(!t) continue;
                          if(t===tg){ el=e; break; }
                          if(t.indexOf(tg)>=0 && (best===null || t.length<bl)){ best=e; bl=t.length; }
                        }
                        el=el||best;
                      }
                      if(!el) return 'NOTFOUND';
                      try{ el.scrollIntoView({block:'center'}); }catch(e){}
                      try{ el.click(); }catch(e){ return 'FAIL:'+e.message; }
                      var t=((el.innerText||el.value||'')+'').replace(/\s+/g,' ').trim().slice(0,40);
                      return 'OK:'+t+'|'+(el.getAttribute&&el.getAttribute('href')||'');
                    })();
                """.trimIndent().replace("__TARGET__", JSONObject.quote(target))
                val res = evalJs(tab, js)
                when {
                    res == "NOTFOUND" -> "没找到「$target」。可以先调 web_links 看看这一页有什么可点的。"
                    res.startsWith("FAIL:") -> "点击失败：${res.removePrefix("FAIL:")}"
                    else -> {
                        val label = res.removePrefix("OK:").substringBefore('|')
                        val href = res.substringAfter('|', "")
                        awaitPage(tab, 6000)
                        val jump = if (href.startsWith("http") || href.startsWith("/")) "，指向 $href" else ""
                        "已点击「$label」$jump。当前页：${tab.url}"
                    }
                }
            }

            AiTools.WEB_TYPE -> {
                val value = args.optString("text")
                if (value.isEmpty()) return "要填写的内容为空"
                val target = args.optString("target").trim()
                val submit = args.optBoolean("submit", false)
                awaitPage(tab, 3000)
                val js = """
                    (function(){
                      var tg=__TARGET__, val=__VALUE__, doSubmit=__SUBMIT__;
                      function vis(e){var r=e.getBoundingClientRect();return r.width>1&&r.height>1;}
                      function pick(sel){
                        var list=document.querySelectorAll('input:not([type=hidden]),textarea,[contenteditable=true]');
                        for(var i=0;i<list.length;i++){
                          var e=list[i]; if(!vis(e)) continue;
                          var hay=((e.getAttribute('placeholder')||'')+' '+(e.getAttribute('name')||'')+' '+(e.id||'')+' '+(e.getAttribute('aria-label')||''));
                          if(!sel || hay.indexOf(sel)>=0) return e;
                        }
                        return null;
                      }
                      var el=null;
                      if(tg){ try{ var q=document.querySelector(tg); if(q&&vis(q)) el=q; }catch(e){} }
                      if(!el) el=pick(tg);
                      if(!el) return 'NOTFOUND';
                      try{ el.focus(); }catch(e){}
                      if(el.isContentEditable){ el.textContent=val; } else { el.value=val; }
                      el.dispatchEvent(new Event('input',{bubbles:true}));
                      el.dispatchEvent(new Event('change',{bubbles:true}));
                      if(doSubmit){
                        try{
                          var f=el.form;
                          if(f && f.requestSubmit) f.requestSubmit();
                          else if(f) f.submit();
                          else el.dispatchEvent(new KeyboardEvent('keydown',{key:'Enter',keyCode:13,which:13,bubbles:true}));
                        }catch(e){}
                      }
                      return 'OK';
                    })();
                """.trimIndent()
                    .replace("__TARGET__", JSONObject.quote(target))
                    .replace("__VALUE__", JSONObject.quote(value))
                    .replace("__SUBMIT__", if (submit) "true" else "false")
                if (evalJs(tab, js) == "NOTFOUND") "没找到输入框。可以先调 web_links 或 web_read 看看这一页的结构。"
                else {
                    awaitPage(tab, 6000)
                    "已填入「$value」${if (submit) "并提交" else ""}。当前页：${tab.url}"
                }
            }

            AiTools.WEB_SCROLL -> {
                val up = args.optString("direction", "down").lowercase() == "up"
                val amount = args.optInt("amount", 0)
                val js = """
                    (function(){
                      var a=__AMT__; if(!a) a=Math.round(window.innerHeight*0.8);
                      window.scrollBy(0, __SIGN__(a));
                      return JSON.stringify({y:Math.round(window.scrollY),h:document.body?document.body.scrollHeight:0});
                    })();
                """.trimIndent()
                    .replace("__AMT__", amount.toString())
                    .replace("__SIGN__", if (up) "-" else "")
                val o = runCatching { JSONObject(evalJs(tab, js)) }.getOrNull()
                if (o == null) "滚动失败" else "已滚动，当前位置 ${o.optInt("y")} / 页面高 ${o.optInt("h")}"
            }

            AiTools.WEB_BACK -> {
                val wv = tab.webView ?: return "浏览器还没准备好"
                if (!wv.canGoBack()) "没有上一页可以返回。"
                else {
                    wv.goBack()
                    awaitPage(tab, 6000)
                    "已返回：${tab.url}"
                }
            }

            AiTools.DOWNLOAD -> {
                var u = args.optString("url").trim()
                if (u.startsWith("//")) u = "https:$u"
                if (!u.startsWith("http")) {
                    return "下载失败：需要文件的 http/https 直链（收到的是「$u」）。如果这是页面上的按钮，先调 web_click 点它。"
                }
                val ctx = tab.webView?.context ?: return "下载失败：浏览器上下文不可用"
                val nm = args.optString("name").trim().ifBlank { null }
                val t = Downloader.enqueue(
                    context = ctx,
                    url = u,
                    nameHint = nm,
                    ua = tab.webView?.settings?.userAgentString,
                    mime = null,
                    contentDisposition = null,
                    referer = tab.url,
                    mirrors = UpdateChecker.mirrorUrls(u).filter { it != u }
                )
                "已加入下载队列：${t.name}（可选线路 ${t.lineCount} 条）。用户可以在「下载」页看进度。"
            }

            AiTools.TABS -> buildString {
                append("共 ${tabs.size} 个标签页：\n")
                tabs.forEachIndexed { i, t ->
                    append(if (i == currentIndex) "* " else "  ").append(i + 1).append(". ")
                        .append(t.title.ifBlank { t.url }).append(" — ").append(t.url).append("\n")
                }
            }

            else -> "未知工具：$name"
        }
    }
}