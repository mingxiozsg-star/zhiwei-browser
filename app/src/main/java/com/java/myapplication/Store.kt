package com.java.myapplication

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject

data class Bookmark(val title: String, val url: String, val time: Long = System.currentTimeMillis())
data class HistoryItem(val title: String, val url: String, val time: Long = System.currentTimeMillis())
data class Shortcut(val name: String, val url: String)
data class DownloadItem(val name: String, val url: String, val time: Long = System.currentTimeMillis())

/** 书签 & 历史 & 主页快捷方式持久化存储（JSON in SharedPreferences） */
object Store {
    private lateinit var app: Context
    val bookmarks = mutableStateListOf<Bookmark>()
    val history = mutableStateListOf<HistoryItem>()
    val shortcuts = mutableStateListOf<Shortcut>()
    val downloads = mutableStateListOf<DownloadItem>()

    private val defaultShortcuts = listOf(
        Shortcut("必应", "https://www.bing.com"),
        Shortcut("百度", "https://www.baidu.com"),
        Shortcut("哔哩哔哩", "https://www.bilibili.com"),
        Shortcut("知乎", "https://www.zhihu.com"),
        Shortcut("微博", "https://m.weibo.cn"),
        Shortcut("GitHub", "https://github.com"),
        Shortcut("淘宝", "https://m.taobao.com"),
        Shortcut("DeepSeek", "https://chat.deepseek.com"),
    )

    fun init(context: Context) {
        app = context.applicationContext
        load()
    }

    private fun prefs() = app.getSharedPreferences("browser_store", Context.MODE_PRIVATE)

    private fun load() {
        val b = prefs().getString("bookmarks", "[]") ?: "[]"
        val ba = JSONArray(b)
        for (i in 0 until ba.length()) {
            val o = ba.getJSONObject(i)
            bookmarks.add(Bookmark(o.optString("title"), o.optString("url"), o.optLong("time")))
        }
        val h = prefs().getString("history", "[]") ?: "[]"
        val ha = JSONArray(h)
        for (i in 0 until ha.length()) {
            val o = ha.getJSONObject(i)
            history.add(HistoryItem(o.optString("title"), o.optString("url"), o.optLong("time")))
        }
        val s = prefs().getString("shortcuts", null)
        if (s == null) {
            shortcuts.addAll(defaultShortcuts)
            saveShortcuts()
        } else {
            val sa = JSONArray(s)
            for (i in 0 until sa.length()) {
                val o = sa.getJSONObject(i)
                shortcuts.add(Shortcut(o.optString("name"), o.optString("url")))
            }
        }
        // 下载记录
        val da = JSONArray(prefs().getString("downloads", "[]") ?: "[]")
        for (i in 0 until da.length()) {
            val o = da.getJSONObject(i)
            downloads.add(DownloadItem(o.optString("name"), o.optString("url"), o.optLong("time")))
        }
    }

    private fun saveShortcuts() {
        val arr = JSONArray()
        shortcuts.forEach { arr.put(JSONObject().put("name", it.name).put("url", it.url)) }
        prefs().edit().putString("shortcuts", arr.toString()).apply()
    }

    fun addShortcut(name: String, url: String) {
        shortcuts.removeAll { it.url == url }
        shortcuts.add(Shortcut(name, url))
        saveShortcuts()
    }

    fun removeShortcut(s: Shortcut) { shortcuts.remove(s); saveShortcuts() }

    private fun saveBookmarks() {
        val arr = JSONArray()
        bookmarks.forEach {
            arr.put(JSONObject().put("title", it.title).put("url", it.url).put("time", it.time))
        }
        prefs().edit().putString("bookmarks", arr.toString()).apply()
    }

    private fun saveHistory() {
        val arr = JSONArray()
        history.take(500).forEach {
            arr.put(JSONObject().put("title", it.title).put("url", it.url).put("time", it.time))
        }
        prefs().edit().putString("history", arr.toString()).apply()
    }

    fun isBookmarked(url: String) = bookmarks.any { it.url == url }

    fun toggleBookmark(title: String, url: String): Boolean {
        val idx = bookmarks.indexOfFirst { it.url == url }
        return if (idx >= 0) { bookmarks.removeAt(idx); saveBookmarks(); false }
        else { bookmarks.add(0, Bookmark(title.ifBlank { url }, url)); saveBookmarks(); true }
    }

    fun removeBookmark(b: Bookmark) { bookmarks.remove(b); saveBookmarks() }

    fun addHistory(title: String, url: String) {
        if (Settings.incognito) return
        if (url.isBlank() || url.startsWith("about:")) return
        history.removeAll { it.url == url }
        history.add(0, HistoryItem(title.ifBlank { url }, url))
        if (history.size > 500) repeat(history.size - 500) { history.removeAt(history.size - 1) }
        saveHistory()
    }

    fun clearHistory() { history.clear(); prefs().edit().putString("history", "[]").apply() }

    // ---------- 下载记录 ----------
    fun addDownload(name: String, url: String) {
        downloads.add(0, DownloadItem(name, url))
        if (downloads.size > 200) repeat(downloads.size - 200) { downloads.removeAt(downloads.size - 1) }
        saveDownloads()
    }

    fun removeDownload(d: DownloadItem) { downloads.remove(d); saveDownloads() }
    fun clearDownloads() { downloads.clear(); prefs().edit().putString("downloads", "[]").apply() }

    private fun saveDownloads() {
        val arr = JSONArray()
        downloads.take(200).forEach {
            arr.put(JSONObject().put("name", it.name).put("url", it.url).put("time", it.time))
        }
        prefs().edit().putString("downloads", arr.toString()).apply()
    }

    // ---------- 会话标签恢复 ----------
    fun saveSession(urls: List<String>) {
        if (!Settings.restoreTabs || Settings.incognito) return
        prefs().edit().putString("session_tabs", JSONArray(urls).toString()).apply()
    }

    fun restoreSession(): List<String> {
        val s = prefs().getString("session_tabs", "[]") ?: "[]"
        val arr = JSONArray(s)
        return (0 until arr.length()).map { arr.getString(it) }
    }
}
