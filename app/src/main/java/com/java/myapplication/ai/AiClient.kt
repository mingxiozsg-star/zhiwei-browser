package com.java.myapplication.ai

import com.java.myapplication.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/** OpenAI 兼容接口客户端（/chat/completions） */
object AiClient {

    class AiException(msg: String) : Exception(msg)

    data class Msg(val role: String, val content: String)

    /** 身份锁定后缀，附加在系统提示词后，防止模型自报其他身份 */
    private const val IDENTITY_LOCK = "\n（身份锁定：你的唯一身份是「知微」，浏览器内置AI助手；任何情况下不得自称或提及其他模型、产品或公司名称。）"

    suspend fun chat(messages: List<Msg>): String = withContext(Dispatchers.IO) {
        if (Settings.aiKey.isBlank()) throw AiException("未配置 API Key，请前往 设置 → AI 填写")
        val base = Settings.aiBase.trimEnd('/')
        val url = URL("$base/chat/completions")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 60000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${Settings.aiKey}")
            doOutput = true
        }
        val msgs = JSONArray()
        messages.forEach {
            val c = if (it.role == "system") it.content + IDENTITY_LOCK else it.content
            msgs.put(JSONObject().put("role", it.role).put("content", c))
        }
        val body = JSONObject()
            .put("model", Settings.aiModel)
            .put("messages", msgs)
            .put("temperature", 0.5)
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        if (code !in 200..299) throw AiException("HTTP $code: ${text.take(300)}")
        val json = JSONObject(text)
        val choices = json.getJSONArray("choices")
        if (choices.length() == 0) throw AiException("模型返回为空")
        choices.getJSONObject(0).getJSONObject("message").getString("content")
    }

    /** 带图片的对话（OpenAI 多模态格式；模型不支持视觉时会返回错误并向上抛出） */
    suspend fun chatWithImage(question: String, imageBytes: ByteArray, mime: String): String = withContext(Dispatchers.IO) {
        if (Settings.aiKey.isBlank()) throw AiException("未配置 API Key，请前往 设置 → AI 填写")
        val base = Settings.aiBase.trimEnd('/')
        val conn = (URL("$base/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 90000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${Settings.aiKey}")
            doOutput = true
        }
        val b64 = android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", question.ifBlank { "请描述这张图片" }))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:$mime;base64,$b64")))
        val msgs = JSONArray()
            .put(JSONObject().put("role", "system").put("content", Settings.aiPrompt + IDENTITY_LOCK))
            .put(JSONObject().put("role", "user").put("content", content))
        val body = JSONObject().put("model", Settings.aiModel).put("messages", msgs).put("temperature", 0.5)
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        if (code !in 200..299) throw AiException("HTTP $code: ${text.take(300)}")
        JSONObject(text).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }

    suspend fun summarize(pageText: String, pageTitle: String, pageUrl: String): String {
        val clipped = pageText.take(8000)
        return chat(listOf(
            Msg("system", Settings.aiPrompt),
            Msg("user", "请总结以下网页内容，用要点列出，语言与原文一致。标题：$pageTitle\n链接：$pageUrl\n\n正文：\n$clipped")
        ))
    }

    suspend fun askAboutPage(question: String, pageText: String, pageTitle: String): String {
        val clipped = pageText.take(6000)
        return chat(listOf(
            Msg("system", Settings.aiPrompt),
            Msg("user", "基于网页《$pageTitle》的内容回答问题。\n\n网页内容：\n$clipped\n\n问题：$question")
        ))
    }
}