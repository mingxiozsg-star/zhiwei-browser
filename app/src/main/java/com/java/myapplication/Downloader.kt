package com.java.myapplication

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

enum class DlStatus { PENDING, RUNNING, PAUSED, DONE, FAILED, CANCELED }

/**
 * 单个下载任务（进度为 Compose 状态，UI 直接订阅）
 *
 * [url] 为原始地址，[mirrors] 为备用线路（加速代理）。下载时按「原始 → 各备用」顺序尝试，
 * 任何一条线路拿到 200/206 就立即采用，全部失败才判 FAILED。
 */
class DlTask(
    val id: Long,
    val url: String,
    var name: String,
    val mime: String?,
    val mirrors: List<String> = emptyList()
) {
    var total by mutableLongStateOf(-1L)
    var done by mutableLongStateOf(0L)
    var status by mutableStateOf(DlStatus.PENDING)
    var message by mutableStateOf<String?>(null)
    var speed by mutableLongStateOf(0L)      // byte/s
    var resumable by mutableStateOf(false)
    var finalUri: String? = null             // MediaStore uri（API 29+）
    var finalPath: String? = null            // 公共目录文件路径
    /** 实际命中的地址（含代理），供「复制下载链接」使用 */
    var resolvedUrl: String? = null
    /** 当前使用第几条线路（1 起，用于状态展示） */
    var lineIndex by mutableIntStateOf(0)
    val lineCount: Int get() = candidateUrls.size
    val candidateUrls: List<String> get() = (listOf(url) + mirrors).distinct()
    val progress: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    val running: Boolean get() = status == DlStatus.RUNNING || status == DlStatus.PENDING
    val finished: Boolean get() = status == DlStatus.DONE
    val displayName: String get() = name.ifBlank { url.substringAfterLast('/').ifBlank { "download" } }
    val isApk: Boolean get() = displayName.endsWith(".apk", true)
}

/**
 * 内置流式下载引擎。
 *
 * 设计要点（与开源下载器一致的工程约束，不引入额外依赖）：
 * - 流式写盘：固定 64KB 缓冲边收边写，绝不把整个文件聚合进内存；
 * - 断点续传：带 Range: bytes=N- 请求，206 追加、200 从头覆盖；服务端不支持则标记不可续传；
 * - 原子落盘：先写应用缓存 .part 临时文件，完成后一次性拷入最终位置（MediaStore 或公共下载目录），避免半成品文件；
 * - 状态机：PENDING → RUNNING →（DONE / FAILED / PAUSED / CANCELED），可暂停后继续；
 * - 进度节流：写入进度按 ~200ms 节流回传，避免高频重组拖累 UI。
 *
 * 兼容其他下载器：设置里可切换系统 DownloadManager 或任意第三方下载器（ADM / 1DM / IDM 等），
 * 通过标准 ACTION_VIEW + MIME 意图投递，不绑定具体实现。
 */
object Downloader {
    val tasks = mutableStateListOf<DlTask>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = HashMap<Long, Job>()
    private var seq = 0L

    private fun guessName(url: String, contentDisposition: String?): String {
        contentDisposition?.let { cd ->
            Regex("filename\\*=UTF-8''([^;]+)").find(cd)?.let {
                return runCatching { URLDecoder.decode(it.groupValues[1], "UTF-8") }.getOrDefault("download")
            }
            Regex("filename=\"?([^\";]+)\"?").find(cd)?.let {
                return runCatching { URLDecoder.decode(it.groupValues[1], "UTF-8") }.getOrDefault("download")
            }
        }
        val raw = url.substringBefore('?').substringAfterLast('/').takeIf { it.isNotBlank() } ?: "download"
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_").take(120).ifBlank { "download" }

    /**
     * 入队并开始下载；同名文件自动加 (1) (2) 后缀，不覆盖已有文件。
     * [mirrors] 为备用线路（加速代理），主链失败时自动切换。
     */
    fun enqueue(
        context: Context,
        url: String,
        nameHint: String? = null,
        ua: String? = null,
        mime: String? = null,
        contentDisposition: String? = null,
        referer: String? = null,
        mirrors: List<String> = emptyList()
    ): DlTask {
        val app = context.applicationContext
        val base = sanitize(nameHint ?: guessName(url, contentDisposition))
        val task = DlTask(++seq, url, base, mime, mirrors.filter { it.isNotBlank() && it != url })
        tasks.add(0, task)
        Store.addDownload(task.name, url, task.mime)
        start(app, task, ua, referer)
        return task
    }

    private fun start(app: Context, task: DlTask, ua: String?, referer: String?) {
        task.status = DlStatus.PENDING
        task.message = null
        jobs[task.id]?.cancel()
        jobs[task.id] = scope.launch {
            runCatching { runDownload(app, task, ua, referer) }
                .onFailure { e ->
                    if (task.status != DlStatus.CANCELED && task.status != DlStatus.PAUSED) {
                        task.status = DlStatus.FAILED
                        task.message = e.message ?: "下载失败"
                    }
                }
        }
    }

    /** 暂停：保留 .part 临时文件，可再次继续 */
    fun pause(task: DlTask) {
        jobs.remove(task.id)?.cancel()
        task.status = DlStatus.PAUSED
        task.speed = 0
    }

    fun resume(context: Context, task: DlTask, ua: String? = null) {
        if (task.status == DlStatus.RUNNING) return
        start(context.applicationContext, task, ua, null)
    }

    fun cancel(task: DlTask) {
        jobs.remove(task.id)?.cancel()
        task.status = DlStatus.CANCELED
        task.speed = 0
    }

    fun retry(context: Context, task: DlTask) {
        task.done = 0
        start(context.applicationContext, task, null, null)
    }

    fun remove(task: DlTask) {
        jobs.remove(task.id)?.cancel()
        tasks.remove(task)
    }

    fun clearFinished() {
        tasks.removeAll { it.finished || it.status == DlStatus.CANCELED || it.status == DlStatus.FAILED }
    }

    private fun tempFile(parent: File, name: String): File {
        val dir = File(parent, "dl").apply { if (!exists()) mkdirs() }
        return File(dir, "$name.part")
    }

    /**
     * 多线路下载：按「原始地址 → 各备用（加速代理）」顺序尝试，任一线路成功即结束。
     * 换线路时强制从 0 开始（不同源的字节内容未必一致，续传会毁文件）。
     */
    private suspend fun runDownload(app: Context, task: DlTask, ua: String?, referer: String?) = withContext(Dispatchers.IO) {
        val candidates = task.candidateUrls
        var lastError: Exception? = null
        for ((index, candidate) in candidates.withIndex()) {
            if (task.status == DlStatus.CANCELED) throw kotlinx.coroutines.CancellationException("canceled")
            task.lineIndex = index + 1
            val resumeFrom = if (index == 0) {
                tempFile(app.cacheDir, task.displayName).takeIf { it.exists() }?.length() ?: 0L
            } else 0L
            try {
                downloadFrom(app, task, candidate, ua, referer, resumeFrom)
                return@withContext
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                task.speed = 0
                if (index + 1 < candidates.size) {
                    task.message = "线路 ${index + 1}/${candidates.size} 失败（${e.message ?: "网络错误"}），切换备用…"
                }
            }
        }
        throw lastError ?: IllegalStateException("下载失败")
    }

    /**
     * 从单条线路取流并落盘。
     *
     * 自己处理 3xx 而不用 HttpURLConnection 的自动跳转：自动跳转在跨域时不会重新取 Cookie，
     * OAuth / 带签名参数的下载链（直链 → 302 CDN）会因此拿到 403；手动跟随可按当前域名重注 Cookie。
     */
    private suspend fun downloadFrom(
        app: Context, task: DlTask, rawUrl: String, ua: String?, referer: String?, resumeFrom: Long
    ) {
        val tmp = tempFile(app.cacheDir, task.displayName)
        val existing = resumeFrom
        val opened = openFollowingRedirects(app, rawUrl, ua, referer, existing)
        val conn = opened.first
        try {
            val code = conn.responseCode
            if (code >= 400) throw IllegalStateException("HTTP $code")
            val append = code == 206 && existing > 0
            if (!append && tmp.exists()) tmp.delete()
            val startAt = if (append) existing else 0L
            val len = conn.contentLengthLong
            task.total = if (len > 0) startAt + len else -1L
            task.done = startAt
            task.resumable = code == 206
            task.resolvedUrl = opened.second
            task.status = DlStatus.RUNNING
            task.message = null

            conn.inputStream.use { ins ->
                java.io.FileOutputStream(tmp, append).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var lastTick = System.currentTimeMillis()
                    var lastBytes = startAt
                    while (true) {
                        if (!coroutineContext.isActiveSafe()) break
                        val n = ins.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        task.done += n
                        val now = System.currentTimeMillis()
                        if (now - lastTick >= 200) {
                            task.speed = (task.done - lastBytes) * 1000 / (now - lastTick)
                            lastTick = now; lastBytes = task.done
                        }
                    }
                    out.flush()
                }
            }
            task.speed = 0
            if (task.status == DlStatus.CANCELED) throw kotlinx.coroutines.CancellationException("canceled")
        } finally {
            runCatching { conn.disconnect() }
        }

        // 落盘：一次性拷入最终位置
        publish(app, task, tmp)
        runCatching { tmp.delete() }
        task.status = DlStatus.DONE
    }

    /** 手动跟随重定向；每一跳按当前域重新注入 Cookie，兼容 OAuth / 签名跳转链 */
    private fun openFollowingRedirects(
        app: Context, rawUrl: String, ua: String?, referer: String?, rangeStart: Long
    ): Pair<HttpURLConnection, String> {
        var current = rawUrl
        var hops = 0
        while (true) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 20_000
                readTimeout = 30_000
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Encoding", "identity")
                val agent = ua?.takeIf { it.isNotBlank() }
                    ?: android.webkit.WebSettings.getDefaultUserAgent(app)
                setRequestProperty("User-Agent", agent)
                referer?.let { setRequestProperty("Referer", it) }
                cookieOf(current)?.let { setRequestProperty("Cookie", it) }
                if (rangeStart > 0) setRequestProperty("Range", "bytes=$rangeStart-")
            }
            conn.connect()
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                runCatching { conn.disconnect() }
                if (loc.isNullOrBlank()) throw IllegalStateException("HTTP $code 跳转缺少目标")
                if (++hops > 8) throw IllegalStateException("重定向次数过多")
                current = runCatching { URL(URL(current), loc).toString() }
                    .getOrElse { throw IllegalStateException("非法跳转地址") }
                continue
            }
            return conn to current
        }
    }

    private fun cookieOf(url: String): String? =
        runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull()

    /** 把临时文件送到最终位置：API 29+ 走 MediaStore（公共下载目录），低版本直接写公共目录 */
    private fun publish(app: Context, task: DlTask, tmp: File) {
        val sub = "知微浏览器"
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = app.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, task.displayName)
                put(MediaStore.Downloads.MIME_TYPE, task.mime ?: guessMime(task.displayName))
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + sub)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("无法创建下载条目")
            resolver.openOutputStream(uri)?.use { out -> tmp.inputStream().use { it.copyTo(out, 64 * 1024) } }
                ?: throw IllegalStateException("无法写入下载目录")
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            task.finalUri = uri.toString()
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), sub)
            if (!dir.exists()) dir.mkdirs()
            var target = File(dir, task.displayName)
            var i = 1
            while (target.exists()) { target = File(dir, uniqueName(task.displayName, i)); i++ }
            tmp.inputStream().use { ins -> target.outputStream().use { ins.copyTo(it, 64 * 1024) } }
            task.finalPath = target.absolutePath
        }
        // 回填落点，供下载记录「打开 / 查看 / 删除」使用
        Store.setDownloadLocation(task.url, task.finalUri, task.finalPath, task.mime ?: guessMime(task.displayName))
    }

    private fun uniqueName(name: String, i: Int): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) + "($i)" + name.substring(dot) else "$name($i)"
    }

    private fun guessMime(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"

    // ---------- 下载记录操作：打开 / 删除 ----------

    /** 解析可用的 content:// uri：MediaStore 优先，其次用 FileProvider 包装公共目录文件 */
    fun uriOf(app: Context, uriString: String?, path: String?): Uri? {
        uriString?.takeIf { it.isNotBlank() }?.let { return Uri.parse(it) }
        path?.takeIf { it.isNotBlank() }?.let { p ->
            return runCatching {
                androidx.core.content.FileProvider.getUriForFile(
                    app, "${app.packageName}.fileprovider", File(p)
                )
            }.getOrNull()
        }
        return null
    }

    fun uriOf(app: Context, task: DlTask): Uri? = uriOf(app, task.finalUri, task.finalPath)

    /** 删除已落盘的文件（MediaStore 条目或公共目录文件） */
    fun deleteFile(app: Context, uriString: String?, path: String?): Boolean {
        var ok = false
        uriString?.takeIf { it.isNotBlank() }?.let { u ->
            ok = runCatching { app.contentResolver.delete(Uri.parse(u), null, null) > 0 }.getOrDefault(false)
        }
        if (!ok) path?.takeIf { it.isNotBlank() }?.let { p ->
            ok = runCatching { File(p).delete() }.getOrDefault(false)
        }
        return ok
    }

    fun deleteFile(app: Context, task: DlTask): Boolean {
        if (task.finalUri == null && task.finalPath == null) return false
        val ok = deleteFile(app, task.finalUri, task.finalPath)
        if (ok) { task.finalUri = null; task.finalPath = null }
        return ok
    }

    /** 用系统应用打开已下载文件；APK 自动走系统安装器 */
    fun openExternal(app: Context, uriString: String?, path: String?, name: String, mime: String?): Boolean {
        val uri = uriOf(app, uriString, path) ?: return false
        val type = if (name.endsWith(".apk", true)) "application/vnd.android.package-archive"
        else (mime?.takeIf { it.isNotBlank() } ?: guessMime(name))
        return runCatching {
            app.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, type)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        }.getOrDefault(false)
    }

    /** 直接用 content:// uri 拉起系统安装器：先中转进私有缓存再经 FileProvider 授权，兼容国产 ROM */
    fun installApkByUri(app: Context, uriString: String, name: String): Boolean {
        val staged = runCatching {
            val dir = File(app.cacheDir, "install").apply { if (!exists()) mkdirs() }
            val out = File(dir, name.ifBlank { "update.apk" })
            app.contentResolver.openInputStream(Uri.parse(uriString))?.use { ins ->
                out.outputStream().use { ins.copyTo(it, 64 * 1024) }
            }
            androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", out)
        }.getOrNull()
        val uri = staged ?: Uri.parse(uriString)
        return runCatching {
            app.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        }.getOrDefault(false)
    }

    /** 下载完成后安装 APK（含未知来源授权引导）
     *  先复制到应用缓存再用 FileProvider 提供，规避部分设备对 MediaStore 条目安装的限制。 */
    fun installApk(app: Context, task: DlTask) {
        val uri = runCatching { stageForInstall(app, task) }.getOrNull()
            ?: task.finalUri?.let { Uri.parse(it) }
            ?: task.finalPath?.let { path ->
                runCatching {
                    androidx.core.content.FileProvider.getUriForFile(
                        app, "${app.packageName}.fileprovider", File(path)
                    )
                }.getOrNull()
            } ?: return
        runCatching {
            app.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    /** 把已下载的 APK 复制到 cacheDir/install 并由 FileProvider 暴露，保证任何落盘方式都能安装 */
    private fun stageForInstall(app: Context, task: DlTask): Uri? {
        val dir = File(app.cacheDir, "install").apply { if (!exists()) mkdirs() }
        val out = File(dir, task.displayName)
        val ins = when {
            task.finalUri != null -> app.contentResolver.openInputStream(Uri.parse(task.finalUri))
            task.finalPath != null -> File(task.finalPath).inputStream()
            else -> null
        } ?: return null
        ins.use { src -> out.outputStream().use { src.copyTo(it, 64 * 1024) } }
        return androidx.core.content.FileProvider.getUriForFile(
            app, "${app.packageName}.fileprovider", out
        )
    }

    /** 交给系统 DownloadManager（可选引擎） */
    fun enqueueSystem(context: Context, url: String, nameHint: String?, ua: String?, mime: String?) {
        val name = sanitize(nameHint ?: guessName(url, null))
        val req = DownloadManager.Request(Uri.parse(url)).apply {
            mime?.let { setMimeType(it) }
            addRequestHeader("User-Agent", ua ?: android.webkit.WebSettings.getDefaultUserAgent(context))
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            setTitle(name)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "知微浏览器/$name")
        }
        (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        Store.addDownload(name, url)
    }

    /** 交给第三方下载器（ADM / 1DM / IDM 等）；package 为空时弹出系统选择器 */
    fun enqueueExternal(context: Context, url: String, mime: String?, packageName: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), mime ?: "application/octet-stream")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (packageName.isNotBlank()) setPackage(packageName)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            Store.addDownload(guessName(url, null), url)
        } else {
            // 指定下载器不存在 → 退回系统选择器
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(url), mime ?: "application/octet-stream")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(Intent.createChooser(fallback, "选择下载器")) }
        }
    }

    /** 扫描已安装的可用下载器（供设置页选择） */
    fun installedDownloaders(context: Context): List<Pair<String, String>> {
        val probe = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("https://example.com/file.zip"), "application/octet-stream")
        }
        return context.packageManager.queryIntentActivities(probe, 0)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val label = runCatching {
                    context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
                }.getOrDefault(pkg)
                label to pkg
            }
            .distinctBy { it.second }
            .sortedBy { it.first }
    }
}

private fun kotlin.coroutines.CoroutineContext.isActiveSafe(): Boolean =
    this[Job]?.isActive ?: true
