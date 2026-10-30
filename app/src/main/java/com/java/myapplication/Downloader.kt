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

/** 单个下载任务（进度为 Compose 状态，UI 直接订阅） */
class DlTask(val id: Long, val url: String, var name: String, val mime: String?) {
    var total by mutableLongStateOf(-1L)
    var done by mutableLongStateOf(0L)
    var status by mutableStateOf(DlStatus.PENDING)
    var message by mutableStateOf<String?>(null)
    var speed by mutableLongStateOf(0L)      // byte/s
    var resumable by mutableStateOf(false)
    var finalUri: String? = null             // MediaStore uri（API 29+）
    var finalPath: String? = null            // 公共目录文件路径
    val progress: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    val running: Boolean get() = status == DlStatus.RUNNING || status == DlStatus.PENDING
    val finished: Boolean get() = status == DlStatus.DONE
    val displayName: String get() = name.ifBlank { url.substringAfterLast('/').ifBlank { "download" } }
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

    /** 入队并开始下载；同名文件自动加 (1) (2) 后缀，不覆盖已有文件 */
    fun enqueue(
        context: Context,
        url: String,
        nameHint: String? = null,
        ua: String? = null,
        mime: String? = null,
        contentDisposition: String? = null,
        referer: String? = null
    ): DlTask {
        val app = context.applicationContext
        val base = sanitize(nameHint ?: guessName(url, contentDisposition))
        val task = DlTask(++seq, url, base, mime)
        tasks.add(0, task)
        Store.addDownload(task.name, url)
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

    private suspend fun runDownload(app: Context, task: DlTask, ua: String?, referer: String?) = withContext(Dispatchers.IO) {
        val tmp = tempFile(app.cacheDir, task.displayName)
        val existing = if (tmp.exists()) tmp.length() else 0L
        val conn = (URL(task.url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            (ua ?: android.webkit.WebSettings.getDefaultUserAgent(app)).takeIf { it.isNotBlank() }?.let { setRequestProperty("User-Agent", it) }
            referer?.let { setRequestProperty("Referer", it) }
            CookieManager.getInstance().getCookie(task.url)?.let { setRequestProperty("Cookie", it) }
            if (task.finalUri == null && existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        conn.connect()
        val code = conn.responseCode
        if (code >= 400) throw IllegalStateException("HTTP $code")
        val append = code == 206 && existing > 0
        if (!append && tmp.exists()) tmp.delete()
        val startAt = if (append) existing else 0L
        val len = conn.contentLengthLong
        task.total = if (len > 0) startAt + len else -1L
        task.done = startAt
        task.resumable = code == 206
        task.status = DlStatus.RUNNING

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

        // 落盘：一次性拷入最终位置
        publish(app, task, tmp)
        tmp.delete()
        task.status = DlStatus.DONE
    }

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
    }

    private fun uniqueName(name: String, i: Int): String {
        val dot = name.lastIndexOf('.')
        return if (dot > 0) name.substring(0, dot) + "($i)" + name.substring(dot) else "$name($i)"
    }

    private fun guessMime(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
            ?: "application/octet-stream"

    /** 下载完成后安装 APK（含未知来源授权引导） */
    fun installApk(app: Context, task: DlTask) {
        val uri = task.finalUri?.let { Uri.parse(it) }
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
