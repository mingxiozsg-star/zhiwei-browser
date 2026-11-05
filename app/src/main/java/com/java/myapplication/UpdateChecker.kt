package com.java.myapplication

import kotlinx.coroutines.Dispatchers
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

    /** 检查更新：逐通道尝试，返回首个成功结果 */
    suspend fun latest(): ReleaseInfo = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (prefix in PROXIES) {
            val label = if (prefix.isBlank()) "直连" else prefix.trimEnd('/').substringAfterLast('/')
            runCatching { fetchOne(prefix + API_PATH, label) }
                .onSuccess { return@withContext it }
                .onFailure { lastError = it as? Exception ?: Exception(it) }
        }
        throw lastError ?: IllegalStateException("所有更新通道均不可用")
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
