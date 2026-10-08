package club.ithueti.routesms

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody

class TelegramClient(private val botToken: String) {
    private val http = OkHttpClient()

    suspend fun sendMessage(chatId: String, text: String) {
        val url = "https://api.telegram.org/bot${botToken}/sendMessage"
        val body = FormBody.Builder()
            .add("chat_id", chatId)
            .add("text", text)
            .build()
        executePost(url, body)
    }

    suspend fun sendChatAction(chatId: String, action: String = "typing"): Boolean {
        val url = "https://api.telegram.org/bot${botToken}/sendChatAction"
        val body = FormBody.Builder()
            .add("chat_id", chatId)
            .add("action", action)
            .build()
        return try {
            executePost(url, body)
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun executePost(url: String, body: RequestBody) {
        val req = Request.Builder().url(url).post(body).build()
        withContext(Dispatchers.IO) {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            }
        }
    }
}
