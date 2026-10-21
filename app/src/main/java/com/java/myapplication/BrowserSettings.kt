package com.java.myapplication

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableIntStateOf
import java.net.URLEncoder

/**
 * 浏览器设置中心：SharedPreferences 持久化 + Compose 状态触发。
 */
object Settings {
    private lateinit var p: SharedPreferences
    /** 每次修改自增，驱动 Compose 重组 */
    var version = mutableIntStateOf(0)
        private set

    fun init(context: Context) {
        p = context.getSharedPreferences("browser_settings", Context.MODE_PRIVATE)
    }

    private fun bump() { version.intValue++ }

    // ---------- 通用 ----------
    var searchEngine: String
        get() = p.getString("search_engine", "bing") ?: "bing"
        set(v) { p.edit().putString("search_engine", v).apply(); bump() }

    var homepage: String
        get() = p.getString("homepage", "zhiwei://home") ?: "zhiwei://home"
        set(v) { p.edit().putString("homepage", v).apply(); bump() }

    var restoreTabs: Boolean
        get() = p.getBoolean("restore_tabs", false)
        set(v) { p.edit().putBoolean("restore_tabs", v).apply(); bump() }

    // ---------- 外观 ----------
    /** 0=跟随系统 1=浅色 2=深色 */
    var darkTheme: Int
        get() = p.getInt("dark_theme", 0)
        set(v) { p.edit().putInt("dark_theme", v).apply(); bump() }

    var forceDarkWeb: Boolean
        get() = p.getBoolean("force_dark_web", false)
        set(v) { p.edit().putBoolean("force_dark_web", v).apply(); bump() }

    var textZoom: Int
        get() = p.getInt("text_zoom", 100)
        set(v) { p.edit().putInt("text_zoom", v).apply(); bump() }

    // ---------- 隐私与安全 ----------
    var incognito: Boolean
        get() = p.getBoolean("incognito", false)
        set(v) { p.edit().putBoolean("incognito", v).apply(); bump() }

    var doNotTrack: Boolean
        get() = p.getBoolean("do_not_track", false)
        set(v) { p.edit().putBoolean("do_not_track", v).apply(); bump() }

    var cookiesEnabled: Boolean
        get() = p.getBoolean("cookies", true)
        set(v) { p.edit().putBoolean("cookies", v).apply(); bump() }

    var thirdPartyCookies: Boolean
        get() = p.getBoolean("third_party_cookies", true)
        set(v) { p.edit().putBoolean("third_party_cookies", v).apply(); bump() }

    var geoAllowed: Boolean
        get() = p.getBoolean("geo_allowed", false)
        set(v) { p.edit().putBoolean("geo_allowed", v).apply(); bump() }

    // ---------- 浏览 ----------
    var jsEnabled: Boolean
        get() = p.getBoolean("js_enabled", true)
        set(v) { p.edit().putBoolean("js_enabled", v).apply(); bump() }

    var domStorage: Boolean
        get() = p.getBoolean("dom_storage", true)
        set(v) { p.edit().putBoolean("dom_storage", v).apply(); bump() }

    var loadImages: Boolean
        get() = p.getBoolean("load_images", true)
        set(v) { p.edit().putBoolean("load_images", v).apply(); bump() }

    var desktopMode: Boolean
        get() = p.getBoolean("desktop_mode", false)
        set(v) { p.edit().putBoolean("desktop_mode", v).apply(); bump() }

    var adBlock: Boolean
        get() = p.getBoolean("ad_block", true)
        set(v) { p.edit().putBoolean("ad_block", v).apply(); bump() }

    var blockPopups: Boolean
        get() = p.getBoolean("block_popups", true)
        set(v) { p.edit().putBoolean("block_popups", v).apply(); bump() }

    var pullRefresh: Boolean
        get() = p.getBoolean("pull_refresh", true)
        set(v) { p.edit().putBoolean("pull_refresh", v).apply(); bump() }

    // ---------- AI ----------
    /** AI 厂商预设：名称 / API Base / 默认模型（自定义不覆盖用户填写） */
    val AI_PROVIDERS = listOf(
        Triple("知微中转站（推荐）", "https://ly.77625.xyz/v1", "deepseek-v4-flash"),
        Triple("DeepSeek 官方", "https://api.deepseek.com/v1", "deepseek-chat"),
        Triple("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
        Triple("月之暗面 Kimi", "https://api.moonshot.cn/v1", "moonshot-v1-8k"),
        Triple("智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash"),
        Triple("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-turbo"),
        Triple("自定义 / 第三方中转站", "", ""),
    )

    /** 首次运行引导是否完成 */
    var firstRunDone: Boolean
        get() = p.getBoolean("first_run_done", false)
        set(v) { p.edit().putBoolean("first_run_done", v).apply(); bump() }

    /** 当前选中的 AI 厂商名 */
    var aiProvider: String
        get() = p.getString("ai_provider", AI_PROVIDERS[0].first) ?: AI_PROVIDERS[0].first
        set(v) { p.edit().putString("ai_provider", v).apply(); bump() }

    const val DEFAULT_AI_PROMPT = "你是「知微」，一款安卓浏览器内置的AI助手。" +
        "无论被问到什么身份相关的问题，你都自称知微，绝不透露或编造任何底层模型名称、开发公司或其他身份。" +
        "回答要求：简洁、准确、直击要点，不寒暄不复述问题；用户用中文提问时默认用中文回答。"
    var aiBase: String
        get() = p.getString("ai_base", "https://ly.77625.xyz/v1") ?: "https://ly.77625.xyz/v1"
        set(v) { p.edit().putString("ai_base", v).apply(); bump() }

    var aiKey: String
        get() = p.getString("ai_key", "sk-77625-e4105914096f53a7e801e569d41ba19c14ea0e6b") ?: ""
        set(v) { p.edit().putString("ai_key", v).apply(); bump() }

    var aiModel: String
        get() = p.getString("ai_model", "deepseek-v4-flash") ?: "deepseek-v4-flash"
        set(v) { p.edit().putString("ai_model", v).apply(); bump() }

    var aiPrompt: String
        get() = p.getString("ai_prompt", DEFAULT_AI_PROMPT) ?: ""
        set(v) { p.edit().putString("ai_prompt", v).apply(); bump() }

    // ---------- 翻译 ----------
    var translateTarget: String
        get() = p.getString("translate_target", "zh-CN") ?: "zh-CN"
        set(v) { p.edit().putString("translate_target", v).apply(); bump() }

    // ---------- 主页自定义 ----------
    /** 0=胶囊 1=圆角矩形 2=直角 */
    var searchBoxStyle: Int
        get() = p.getInt("search_box_style", 0)
        set(v) { p.edit().putInt("search_box_style", v).apply(); bump() }

    /** 主页背景预设序号 */
    var homeBg: Int
        get() = p.getInt("home_bg", 0)
        set(v) { p.edit().putInt("home_bg", v).apply(); bump() }

    var homeShowShortcuts: Boolean
        get() = p.getBoolean("home_show_shortcuts", true)
        set(v) { p.edit().putBoolean("home_show_shortcuts", v).apply(); bump() }

    /** 主页自定义壁纸 URI，空=用渐变预设 */
    var homeBgImage: String
        get() = p.getString("home_bg_image", "") ?: ""
        set(v) { p.edit().putString("home_bg_image", v).apply(); bump() }

    /** 主页跟随系统桌面壁纸（全屏，含底栏） */
    var homeUseSystemWallpaper: Boolean
        get() = p.getBoolean("home_sys_wallpaper", false)
        set(v) { p.edit().putBoolean("home_sys_wallpaper", v).apply(); bump() }

    /** 自动整页翻译（英文→目标语言，后续页面持续翻译） */
    var autoTranslate: Boolean
        get() = p.getBoolean("auto_translate", false)
        set(v) { p.edit().putBoolean("auto_translate", v).apply(); bump() }

    // ---------- 工具 ----------
    fun toUrl(input: String): String {
        val t = input.trim()
        if (t.startsWith("zhiwei://")) return t
        if (t.startsWith("http://") || t.startsWith("https://") || t.startsWith("file://")) return t
        return if (t.contains(".") && !t.contains(" ")) "https://$t"
        else searchUrl(t)
    }

    fun searchUrl(query: String): String {
        val q = URLEncoder.encode(query, "UTF-8")
        return when (searchEngine) {
            "google" -> "https://www.google.com/search?q=$q"
            "baidu" -> "https://www.baidu.com/s?wd=$q"
            "duckduckgo" -> "https://duckduckgo.com/?q=$q"
            "sogou" -> "https://www.sogou.com/web?query=$q"
            else -> "https://www.bing.com/search?q=$q"
        }
    }

    fun pageTranslateUrl(url: String): String {
        val u = URLEncoder.encode(url, "UTF-8")
        return "https://translate.google.com/translate?sl=auto&tl=${translateTarget}&u=$u"
    }

    const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
}
