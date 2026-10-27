package ru.eurovafel.guitartuner.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.eurovafel.guitartuner.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Отправка отзыва без почтового приложения и без показа адреса.
 *
 * Основной канал — почта. Если при сборке задан Telegram-бот, копия уходит
 * и в Telegram. Пользователь видит только форму оценки и мнения.
 */
class FeedbackClient(
    private val telegram: TelegramClient = TelegramClient(
        token = BuildConfig.TELEGRAM_BOT_TOKEN,
        chatId = BuildConfig.TELEGRAM_CHAT_ID,
    ),
) {

    suspend fun send(
        rating: Int,
        opinion: String,
        versionName: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        var delivered = false
        var lastError: Throwable? = null

        runCatching { post(rating, opinion, versionName) }
            .onSuccess { delivered = true }
            .onFailure { lastError = it }

        if (telegram.isConfigured) {
            val text = buildString {
                append("Гитарный тюнер · отзыв\n")
                append("Оценка: ").append(rating).append(" из 5\n")
                append("Мнение: ").append(opinion.ifBlank { "без комментария" }).append('\n')
                if (versionName.isNotBlank()) append("Версия: ").append(versionName)
            }
            telegram.send(text)
                .onSuccess { delivered = true }
                .onFailure { lastError = it }
        }

        if (delivered) {
            Result.success(Unit)
        } else {
            Result.failure(lastError ?: IOException("Нет доступного канала доставки"))
        }
    }

    private fun post(rating: Int, opinion: String, versionName: String) {
        val recipient = String(
            Base64.decode(RECIPIENT_BASE64, Base64.NO_WRAP),
            Charsets.UTF_8,
        )
        val payload = JSONObject().apply {
            put("_subject", "Гитарный тюнер: отзыв")
            put("_template", "table")
            put("_captcha", "false")
            put("Оценка", "$rating из 5")
            put("Мнение", opinion.ifBlank { "Без комментария" })
            put("Версия", versionName)
        }.toString()

        val connection = (URL(ENDPOINT + recipient).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            // FormSubmit принимает AJAX-запросы только с «сайта».
            setRequestProperty("Origin", ORIGIN)
            setRequestProperty("Referer", "$ORIGIN/")
            setRequestProperty("User-Agent", USER_AGENT)
        }

        try {
            connection.outputStream.use { stream ->
                stream.write(payload.toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("HTTP $code")
            }
            val answer = runCatching { JSONObject(body) }.getOrElse {
                throw IOException("Некорректный ответ сервера")
            }
            if (!answer.optBoolean("success")) {
                throw IOException(answer.optString("message").ifBlank { "Отзыв не принят" })
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val ENDPOINT = "https://formsubmit.co/ajax/"
        // Псевдоним формы: адрес почты в APK не хранится.
        const val RECIPIENT_BASE64 = "NzhiNzQ2YTFlMjNmYjRjY2Q0ZWZmMDJiYWEyNzljZmQ="
        const val ORIGIN = "https://guitar-tuner.app"
        const val USER_AGENT = "GuitarTuner/0.4 (Android)"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
    }
}
