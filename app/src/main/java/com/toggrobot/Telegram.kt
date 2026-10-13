package com.toggrobot

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Telegram bot uzerinden mesaj gonderir (iPhone vb. cihazlara bildirim icin).
 * Kurulum: @BotFather -> /newbot -> verilen token uygulamaya yapistirilir,
 * sonra bota Telegram'dan herhangi bir mesaj yazilip "Sohbetleri bul"a basilir.
 * Ag islemi yapar: ana is parcaciginda cagirma.
 */
object Telegram {
    private fun call(token: String, method: String, params: Map<String, String>): JSONObject {
        val body = params.entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
        val c = URL("https://api.telegram.org/bot$token/$method").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        try {
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}"
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (!json.optBoolean("ok")) throw Exception(json.optString("description", "HTTP $code"))
            return json
        } finally {
            c.disconnect()
        }
    }

    fun parseChats(s: String): List<String> = s.split(',', ' ', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    /** Mesaji gonderir; hata olursa kayda yazar, istisna firlatmaz. Basarili gonderim sayisini dondurur. */
    fun send(ctx: Context, text: String, chats: List<String>? = null): Int {
        val s = Settings(ctx)
        val token = s.tgToken.trim()
        val targets = chats?.takeIf { it.isNotEmpty() } ?: parseChats(s.tgChats)
        if (token.isEmpty() || targets.isEmpty()) return 0
        var ok = 0
        for (chat in targets) {
            try {
                call(token, "sendMessage", mapOf("chat_id" to chat, "text" to text, "disable_web_page_preview" to "true"))
                ok++
            } catch (e: Exception) {
                Log.add(ctx, "Telegram gönderilemedi ($chat): ${e.message}")
            }
        }
        return ok
    }

    /** Bota son yazan kisilerin/gruplarin sohbet numaralari: "id" -> "ad" */
    fun findChats(token: String): Map<String, String> {
        val res = call(token.trim(), "getUpdates", mapOf("limit" to "100"))
        val out = LinkedHashMap<String, String>()
        val arr = res.optJSONArray("result") ?: return out
        for (i in 0 until arr.length()) {
            val u = arr.getJSONObject(i)
            val msg = u.optJSONObject("message") ?: u.optJSONObject("channel_post") ?: u.optJSONObject("my_chat_member") ?: continue
            val chat = msg.optJSONObject("chat") ?: continue
            val name = chat.optString("title").ifEmpty {
                (chat.optString("first_name") + " " + chat.optString("last_name")).trim()
            }.ifEmpty { chat.optString("username") }
            out[chat.optLong("id").toString()] = name
        }
        return out
    }

    /** Bot adini dondurerek token'i dogrular. */
    fun botName(token: String): String = call(token.trim(), "getMe", emptyMap()).optJSONObject("result")?.optString("username") ?: "?"
}
