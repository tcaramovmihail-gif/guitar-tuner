package ru.eurovafel.guitartuner.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Необязательное дублирование отзыва в Telegram.
 *
 * Токен бота и chat_id не хранятся в git: они приходят из local.properties
 * или переменных среды при сборке. Без них приложение отправляет только почту.
 * Бот должен быть заранее запущен получателем, иначе Telegram не даст ему
 * написать первым.
 */
class TelegramClient(
    private val token: String,
    private val chatId: String,
) {
    val isConfigured: Boolean get() = token.isNotBlank() && chatId.isNotBlank()

    suspend fun send(text: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildString {
                append("chat_id=")
                append(URLEncoder.encode(chatId, "UTF-8"))
                append("&text=")
                append(URLEncoder.encode(text, "UTF-8"))
                append("&disable_web_page_preview=true")
            }.toByteArray(Charsets.UTF_8)

            val connection = (
                URL("https://api.telegram.org/bot$token/sendMessage").openConnection()
                    as HttpURLConnection
                ).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }

            try {
                connection.outputStream.use { it.write(body) }
                val code = connection.responseCode
                val stream = if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw IOException("HTTP $code")
                val answer = runCatching { JSONObject(raw) }.getOrNull()
                if (answer?.optBoolean("ok") != true) {
                    throw IOException("Telegram не принял сообщение")
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
    }
}
