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
import java.io.ByteArrayInputStream

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
}

class BrowserViewModel : ViewModel() {

    val tabs = androidx.compose.runtime.mutableStateListOf<Tab>()
    var currentIndex by mutableIntStateOf(0)
    var fullscreenView: View? = null
    var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var idSeq = 0L

    val current: Tab? get() = tabs.getOrNull(currentIndex)

    fun addTab(context: Context, url: String = Settings.homepage): Tab {
        val tab = Tab(++idSeq)
        tabs.add(tab)
        currentIndex = tabs.size - 1
        ensureWebView(context, tab)
        if (url.isNotBlank() && !url.startsWith("zhiwei://")) tab.webView?.loadUrl(url)
        if (url.startsWith("zhiwei://")) tab.url = url
        return tab
    }

    /** 回主页：内置主页不触发 WebView 加载 */
    fun goHome(tab: Tab) {
        tab.url = Settings.homepage
        tab.loadError = false
        tab.searchQuery = ""
        if (!Settings.homepage.startsWith("zhiwei://")) {
            tab.webView?.loadUrl(Settings.homepage)
        }
    }

    /** 从主页发起搜索/导航，地址栏显示原始输入 */
    fun homeNavigate(tab: Tab, input: String) {
        val target = Settings.toUrl(input)
        val isSearch = !target.startsWith("http") || target == Settings.searchUrl(input)
        tab.pendingSearch = if (isSearch || !input.contains(".")) input else null
        tab.webView?.loadUrl(target)
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
        val w = WebView(context)
        applySettings(w)
        w.webViewClient = BrowserClient(tab)
        w.webChromeClient = BrowserChrome(tab)
        w.setDownloadListener(DownloadListener { url, ua, cd, mime, _ ->
            promptDownload(context, url, ua, cd, mime)
        })
        tab.webView = w
        return w
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
    /** 常见可下载文件扩展名（直链兜底检测） */
    private val downloadExts = listOf(
        ".apk", ".zip", ".rar", ".7z", ".tar", ".gz", ".exe", ".msi", ".dmg",
        ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
        ".mp3", ".wav", ".flac", ".mp4", ".mkv", ".avi", ".mov",
        ".jpg", ".jpeg", ".png", ".gif", ".webp", ".psd",
        ".txt", ".csv", ".json", ".xml", ".epub", ".mobi"
    )

    fun isDownloadLink(url: String): Boolean {
        val path = Uri.parse(url).path?.lowercase() ?: return false
        return downloadExts.any { path.endsWith(it) }
    }

    /** 弹出下载确认框，确认后入队系统下载器并记录到下载历史 */
    fun promptDownload(context: Context, url: String, ua: String?, contentDisposition: String?, mime: String?) {
        val name = URLUtil.guessFileName(url, contentDisposition, mime)
        AlertDialog.Builder(context)
            .setTitle("下载文件")
            .setMessage("$name\n\n保存到：下载/知微浏览器/")
            .setPositiveButton("下载") { _, _ ->
                try {
                    val req = DownloadManager.Request(Uri.parse(url))
                    if (mime != null) req.setMimeType(mime)
                    req.addRequestHeader("User-Agent", ua ?: WebSettings.getDefaultUserAgent(context))
                    CookieManager.getInstance().getCookie(url)?.let { req.addRequestHeader("Cookie", it) }
                    req.setTitle(name)
                    req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "知微浏览器/$name")
                    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
                    Store.addDownload(name, url)
                    Toast.makeText(context, "开始下载：$name\n保存到：下载/知微浏览器/", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "下载失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
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
            tab.isLoading = false
            tab.url = view.url ?: url
            tab.title = view.title?.takeIf { it.isNotBlank() } ?: url
            updateNav(tab, view)
            Store.addHistory(tab.title, tab.url)
            // 自动整页翻译：非翻译代理页面且是 http 页面时，跳转翻译（翻译代理内链接天然持续翻译）
            if (Settings.autoTranslate
                && tab.url.startsWith("http")
                && !tab.url.contains("translate.google")
            ) {
                view.post { view.loadUrl(Settings.pageTranslateUrl(tab.url)) }
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
                webView.context.startActivity(fileChooserParams.createIntent())
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

    fun clearAllData(context: Context) {
        android.webkit.WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
        context.cacheDir.deleteRecursively()
        tabs.forEach { it.webView?.clearCache(true) }
        Store.clearHistory()
    }
}