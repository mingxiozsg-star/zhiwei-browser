package com.java.myapplication.ai

import com.java.myapplication.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 免费 Google gtx 翻译接口 */
object Translator {

    suspend fun translate(text: String, target: String = Settings.translateTarget): String =
        withContext(Dispatchers.IO) {
            if (text.isBlank()) return@withContext ""
            // gtx 单次不宜过长，分块翻译
            val chunks = splitText(text, 3500)
            val sb = StringBuilder()
            for (chunk in chunks) {
                sb.append(translateChunk(chunk, target))
            }
            sb.toString()
        }

    private fun splitText(text: String, max: Int): List<String> {
        val list = mutableListOf<String>()
        var rest = text
        while (rest.length > max) {
            var cut = rest.lastIndexOf('\n', max)
            if (cut < max / 2) cut = rest.lastIndexOf('。', max)
            if (cut < max / 2) cut = rest.lastIndexOf(". ", max)
            if (cut < max / 2) cut = max
            list.add(rest.substring(0, cut + 1))
            rest = rest.substring(cut + 1)
        }
        if (rest.isNotBlank()) list.add(rest)
        return list
    }

    private fun translateChunk(text: String, target: String): String {
        val q = URLEncoder.encode(text, "UTF-8")
        val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$target&dt=t&q=$q")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        val raw = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        val arr = JSONArray(raw).getJSONArray(0)
        val sb = StringBuilder()
        for (i in 0 until arr.length()) {
            sb.append(arr.getJSONArray(i).optString(0))
        }
        return sb.toString()
    }
}