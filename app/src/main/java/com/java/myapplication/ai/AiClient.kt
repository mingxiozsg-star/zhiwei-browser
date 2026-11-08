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

    /** 一次工具调用的记录，用于在界面上展示 AI 做了什么 */
    data class ToolStep(val name: String, val summary: String)

    /** agent 跑完的结果：最终文本 + 中途用过的工具 */
    data class AgentResult(val text: String, val steps: List<ToolStep>)

    /** 身份约束：可以说自己是什么模型（如实照抄运行信息），但不许编造别的身份 */
    private const val IDENTITY_LOCK =
        "\n（身份：你的唯一身份是「知微」，浏览器内置 AI 助手；不编造自身来历、训练来源或其他公司产品。" +
            "被问到使用什么模型时，按下面的【当前运行信息】如实回答。）"

    /** 运行信息：注入系统提示词，让模型能如实回答「你用的什么模型」，并知道此刻的工具能力 */
    private fun runtimeInfo(): String = buildString {
        append("\n【当前运行信息】模型标识：")
        append(Settings.aiModel.ifBlank { "未配置" })
        if (Settings.aiProvider.isNotBlank()) append("；接入渠道：").append(Settings.aiProvider)
        append("；工具能力：")
        append(if (Settings.aiWebTools) "可操作网页（读取/打开/点击/填写/滚动/返回/下载）" else "已关闭")
        append("。")
    }

    private fun systemText(raw: String): String = raw + IDENTITY_LOCK + runtimeInfo()

    /**
     * 发一轮请求，返回 choices[0].message（含 content 与 tool_calls）。
     * [tools] 为 null 表示这次不带工具。
     */
    private fun post(convo: JSONArray, tools: JSONArray?): JSONObject {
        val base = Settings.aiBase.trimEnd('/')
        val conn = (URL("$base/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 90000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${Settings.aiKey}")
            doOutput = true
        }
        val body = JSONObject()
            .put("model", Settings.aiModel)
            .put("messages", convo)
            .put("temperature", 0.5)
        if (tools != null) body.put("tools", tools).put("tool_choice", "auto")
        OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        if (code !in 200..299) throw AiException("HTTP $code: ${text.take(300)}")
        val choices = JSONObject(text).getJSONArray("choices")
        if (choices.length() == 0) throw AiException("模型返回为空")
        return choices.getJSONObject(0).getJSONObject("message")
    }

    suspend fun chat(messages: List<Msg>): String = withContext(Dispatchers.IO) {
        if (Settings.aiKey.isBlank()) throw AiException("未配置 API Key，请前往 设置 → AI 填写")
        val msgs = JSONArray()
        messages.forEach {
            val c = if (it.role == "system") systemText(it.content) else it.content
            msgs.put(JSONObject().put("role", it.role).put("content", c))
        }
        post(msgs, null).optString("content")
    }

    /**
     * 带工具的对话：模型可以先操作网页、看结果、再决定下一步，最多 [maxRounds] 轮工具调用。
     * [executor] 真正执行工具并把文本结果回灌给模型；[onStep] 每次执行前回调，供界面展示。
     * 服务端不支持 tools（4xx）时自动去掉工具重试一次，保证还能正常聊天。
     */
    suspend fun chatAgent(
        messages: List<Msg>,
        enableTools: Boolean,
        maxRounds: Int = 6,
        onStep: suspend (ToolStep) -> Unit = {},
        executor: (suspend (name: String, args: JSONObject) -> String)? = null
    ): AgentResult = withContext(Dispatchers.IO) {
        if (Settings.aiKey.isBlank()) throw AiException("未配置 API Key，请前往 设置 → AI 填写")
        val convo = JSONArray()
        messages.forEach {
            val c = if (it.role == "system") systemText(it.content) else it.content
            convo.put(JSONObject().put("role", it.role).put("content", c))
        }
        var toolsOn = enableTools && executor != null
        val steps = mutableListOf<ToolStep>()
        var round = 0
        while (true) {
            val msg = try {
                post(convo, if (toolsOn) AiTools.specs() else null)
            } catch (e: AiException) {
                if (toolsOn) {
                    toolsOn = false
                    post(convo, null)
                } else throw e
            }
            val calls = msg.optJSONArray("tool_calls")
            val content = msg.optString("content", "")
            if (calls == null || calls.length() == 0 || round >= maxRounds) {
                return@withContext AgentResult(content.ifBlank { "（模型没有返回内容）" }, steps)
            }
            round++
            convo.put(
                JSONObject().put("role", "assistant")
                    .put("content", content)
                    .put("tool_calls", calls)
            )
            for (i in 0 until calls.length()) {
                val c = calls.getJSONObject(i)
                val id = c.optString("id")
                val f = c.optJSONObject("function") ?: continue
                val name = f.optString("name")
                val args = runCatching { JSONObject(f.optString("arguments", "{}")) }.getOrDefault(JSONObject())
                withContext(Dispatchers.Main) { onStep(ToolStep(name, AiTools.summaryOf(name, args))) }
                steps.add(ToolStep(name, AiTools.summaryOf(name, args)))
                val result = try {
                    executor?.invoke(name, args) ?: "工具不可用"
                } catch (e: Exception) {
                    "工具执行失败：${e.message}"
                }
                convo.put(
                    JSONObject().put("role", "tool")
                        .put("tool_call_id", id)
                        .put("content", result.take(5000))
                )
            }
        }
        AgentResult("", steps)
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
            .put(JSONObject().put("role", "system").put("content", systemText(Settings.aiPrompt)))
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
        return chat(
            listOf(
                Msg("system", Settings.aiPrompt),
                Msg("user", "请总结以下网页内容，用要点列出，语言与原文一致。标题：$pageTitle\n链接：$pageUrl\n\n正文：\n$clipped")
            )
        )
    }

    suspend fun askAboutPage(question: String, pageText: String, pageTitle: String): String {
        val clipped = pageText.take(6000)
        return chat(
            listOf(
                Msg("system", Settings.aiPrompt),
                Msg("user", "基于网页《$pageTitle》的内容回答问题。\n\n网页内容：\n$clipped\n\n问题：$question")
            )
        )
    }
}