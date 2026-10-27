package ru.eurovafel.guitartuner.catalog

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/** Общий HTTP-слой каталогов: без сторонних библиотек и с таймаутами. */
internal object HtmlHttp {

    fun get(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5")
        }

        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code: $url")
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            val charset = charsetOf(connection.contentType) ?: charsetFromMeta(bytes)
            return String(bytes, charset)
        } finally {
            connection.disconnect()
        }
    }

    private fun charsetOf(contentType: String?): Charset? {
        val name = contentType
            ?.substringAfter("charset=", "")
            ?.substringBefore(';')
            ?.trim()
            ?.trim('"')
            .orEmpty()
        return name.takeIf { it.isNotEmpty() }?.let {
            runCatching { Charset.forName(it) }.getOrNull()
        }
    }

    /** Сайты иногда отдают UTF-8 без charset в заголовке. */
    private fun charsetFromMeta(bytes: ByteArray): Charset {
        val head = String(bytes, 0, minOf(bytes.size, 2048), StandardCharsets.ISO_8859_1)
        val match = Regex("""charset=["']?([A-Za-z0-9_\-]+)""", RegexOption.IGNORE_CASE)
            .find(head)
        val name = match?.groupValues?.getOrNull(1)
        return name?.let { runCatching { Charset.forName(it) }.getOrNull() }
            ?: StandardCharsets.UTF_8
    }

    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 20_000
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 GuitarTuner/0.2"
}
