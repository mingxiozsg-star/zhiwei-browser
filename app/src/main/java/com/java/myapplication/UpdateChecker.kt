package com.java.myapplication

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 一次可用的版本信息 */
data class ReleaseInfo(
    val tag: String,
    val apkUrl: String?,
    val size: Long = 0L,
    val note: String? = null,
    /** 命中的通道名，便于排查 */
    val source: String = "",
    /** 下载候选（直链 + 各加速代理） */
    val apkMirrors: List<String> = emptyList()
)

/**
 * 更新通道 & 镜像加速。
 *
 * 原先只有一个 api.github.com 直连：国内常年不可达 → 「检查更新」超时，「下载」秒失败。
 * 这里把元数据和安装包都做成多通道：
 *   - 元数据：对 api.github.com 逐个套代理前缀请求，谁先返回有效 JSON 用谁；
 *   - 安装包：把 release 直链展开成「直链 + 各代理」候选，下载器逐个尝试。
 * 全部通道失败才报错，并把最后一次真实原因带出去。
 */
object UpdateChecker {

    const val REPO = "mingxiozsg-star/zhiwei-browser"
    private const val API_PATH = "https://api.github.com/repos/$REPO/releases/latest"

    /** 加速代理前缀，顺序即优先级。空串=直连（放最后兜底，海外/直连可用时仍能命中） */
    val PROXIES = listOf(
        "https://ghfast.top/",
        "https://gh-proxy.com/",
        "https://ghproxy.net/",
        "https://github.moeyy.xyz/",
        "https://gh.llkk.cc/",
        ""
    )

    /**
     * 把任意 github 链接展开成「直连 + 各代理」候选。
     * 非 github 链接原样返回，避免被无谓代理。
     */
    fun mirrorUrls(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        if (!raw.contains("github")) return listOf(raw)
        val out = LinkedHashSet<String>()
        out.add(raw)
        PROXIES.filter { it.isNotBlank() }.forEach { out.add(it + raw) }
        return out.toList()
    }

    /** 检查更新：所有通道并发竞速，谁先返回有效结果用谁，其余立刻取消 */
    suspend fun latest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val channels = PROXIES.map { prefix ->
            val label = labelOf(prefix)
            label to async { runCatching { fetchOne(prefix + API_PATH, label) } }
        }
        val deadline = System.currentTimeMillis() + 12_000
        var winner: ReleaseInfo? = null
        val errors = ArrayList<String>()
        while (winner == null) {
            for ((label, job) in channels) {
                if (!job.isCompleted) continue
                job.getCompleted()
                    .onSuccess { if (winner == null) winner = it }
                    .onFailure { e ->
                        val line = "$label：${e.message ?: "失败"}"
                        if (errors.none { it == line }) errors.add(line)
                    }
            }
            if (winner != null) break
            if (channels.all { it.second.isCompleted }) break
            if (System.currentTimeMillis() > deadline) break
            delay(80)
        }
        channels.forEach { it.second.cancel() }
        winner ?: throw IllegalStateException(
            "更新通道全部不可用" + errors.take(2).joinToString("", "（", "）") { it }
        )
    }

    /** 通道显示名 */
    private fun labelOf(prefix: String): String =
        if (prefix.isBlank()) "直连" else prefix.trimEnd('/').substringAfterLast('/')

    /**
     * 版本号比较：远端是否比本地新。
     * 按数字段逐段比（1.10.0 > 1.8.1），解析不出来时退回字符串比较。
     */
    fun isNewer(remote: String, local: String): Boolean {
        fun nums(v: String) = v.trim().removePrefix("v")
            .split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.mapNotNull { it.toIntOrNull() }
        val r = nums(remote)
        val l = nums(local)
        if (r.isEmpty() || l.isEmpty()) return remote.trim() != local.trim()
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun fetchOne(url: String, label: String): ReleaseInfo {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ZhiweiBrowser")
        }
        conn.connect()
        val code = conn.responseCode
        if (code >= 400) throw IllegalStateException("HTTP $code")
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        val tag = json.optString("tag_name").removePrefix("v").trim()
        if (tag.isBlank()) throw IllegalStateException("响应缺少版本号")
        var apk: String? = null
        var size = 0L
        json.optJSONArray("assets")?.let { arr ->
            for (i in 0 until arr.length()) {
                val a = arr.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", true)) {
                    apk = a.optString("browser_download_url")
                    size = a.optLong("size")
                    break
                }
            }
        }
        return ReleaseInfo(
            tag = tag,
            apkUrl = apk,
            size = size,
            note = json.optString("body").takeIf { it.isNotBlank() },
            source = label,
            apkMirrors = apk?.let { mirrorUrls(it) } ?: emptyList()
        )
    }
}
