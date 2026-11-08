package com.java.myapplication.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * AI 可以调用的浏览器工具（OpenAI tools 格式）。
 * 定义与执行分离：这里只描述「有什么工具」，执行放在 BrowserViewModel.aiTool。
 */
object AiTools {

    const val WEB_READ = "web_read"
    const val WEB_LINKS = "web_links"
    const val WEB_OPEN = "web_open"
    const val WEB_CLICK = "web_click"
    const val WEB_TYPE = "web_type"
    const val WEB_SCROLL = "web_scroll"
    const val WEB_BACK = "web_back"
    const val DOWNLOAD = "download_file"
    const val TABS = "list_tabs"

    private fun prop(type: String, desc: String) = JSONObject().put("type", type).put("description", desc)

    private fun fn(name: String, desc: String, props: JSONObject, required: List<String>) = JSONObject()
        .put("type", "function")
        .put(
            "function", JSONObject()
                .put("name", name)
                .put("description", desc)
                .put(
                    "parameters", JSONObject()
                        .put("type", "object")
                        .put("properties", props)
                        .put("required", JSONArray(required))
                )
        )

    /** 全部工具定义；模型每轮最多只能看到这些 */
    fun specs(): JSONArray = JSONArray()
        .put(
            fn(
                WEB_READ, "读取当前网页的标题、网址和正文纯文本。想了解页面上写了什么就先调它。",
                JSONObject(), emptyList()
            )
        )
        .put(
            fn(
                WEB_LINKS, "列出当前网页可点击的链接与按钮（带序号、可见文字、网址）。找下载入口或需要点进去的条目时用它。",
                JSONObject().put("limit", prop("integer", "最多返回条数，默认 40")), emptyList()
            )
        )
        .put(
            fn(
                WEB_OPEN, "在浏览器里打开一个网址。",
                JSONObject()
                    .put("url", prop("string", "完整网址，必须带 http:// 或 https://"))
                    .put("new_tab", prop("boolean", "是否新开标签页，默认 false")),
                listOf("url")
            )
        )
        .put(
            fn(
                WEB_CLICK, "点击网页上的元素。target 可以是元素的可见文字（如「下载」），也可以是 CSS 选择器（以 # . [ 开头）。",
                JSONObject().put("target", prop("string", "元素可见文字或 CSS 选择器")),
                listOf("target")
            )
        )
        .put(
            fn(
                WEB_TYPE, "往网页的输入框里填写内容（例如搜索框）。",
                JSONObject()
                    .put("text", prop("string", "要填入的内容"))
                    .put("target", prop("string", "输入框的占位文字 / name / id / CSS 选择器；留空则用页面上第一个可见输入框"))
                    .put("submit", prop("boolean", "填完是否回车提交，默认 false")),
                listOf("text")
            )
        )
        .put(
            fn(
                WEB_SCROLL, "滚动当前网页，用于把下面的内容或按钮带进视野。",
                JSONObject()
                    .put("direction", prop("string", "down 或 up，默认 down"))
                    .put("amount", prop("integer", "滚动像素，留空则滚一屏")),
                emptyList()
            )
        )
        .put(fn(WEB_BACK, "返回上一个页面。", JSONObject(), emptyList()))
        .put(
            fn(
                DOWNLOAD, "把文件加入浏览器下载队列（自动挂上加速线路）。下载 APK 安装包也用这个。url 必须是文件直链。",
                JSONObject()
                    .put("url", prop("string", "文件直链"))
                    .put("name", prop("string", "保存的文件名，可留空")),
                listOf("url")
            )
        )
        .put(fn(TABS, "列出浏览器当前打开的所有标签页。", JSONObject(), emptyList()))

    /** 给界面看的一句话摘要：「AI 正在做什么」 */
    fun summaryOf(name: String, args: JSONObject): String = when (name) {
        WEB_READ -> "读取网页内容"
        WEB_LINKS -> "查看页面链接"
        WEB_OPEN -> "打开 " + args.optString("url").take(60)
        WEB_CLICK -> "点击「" + args.optString("target").take(24) + "」"
        WEB_TYPE -> "填写「" + args.optString("text").take(20) + "」"
        WEB_SCROLL -> "滚动页面"
        WEB_BACK -> "返回上一页"
        DOWNLOAD -> "下载 " + args.optString("name").ifBlank { args.optString("url") }.take(40)
        TABS -> "查看标签页"
        else -> name
    }
}
