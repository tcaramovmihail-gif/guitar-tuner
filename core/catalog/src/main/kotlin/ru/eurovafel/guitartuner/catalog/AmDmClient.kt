package ru.eurovafel.guitartuner.catalog

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** HTTP-клиент AmDm без сторонних сетевых библиотек. */
class AmDmClient(
    private val baseUrl: String = AmDmParser.BASE_URL,
) {
    fun search(query: String): SearchResult {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val songs = AmDmParser.parseSearchSongs(
            get("${baseUrl.trimEnd('/')}/search/?q=$encoded"),
        )
        val artists = runCatching {
            AmDmParser.parseArtistSearch(
                get("${baseUrl.trimEnd('/')}/search/artist/?q=$encoded"),
            )
        }.getOrDefault(emptyList())

        return SearchResult(songs = songs, artists = artists)
    }

    fun artistSongs(artistUrl: String): ArtistSongs {
        val resolved = resolve(artistUrl)
        val html = get(resolved)
        val parsed = AmDmParser.parseArtistSongs(html, resolved)
        return if (parsed.artist.isBlank()) {
            parsed.copy(artist = artistNameFromUrl(resolved))
        } else {
            parsed
        }
    }

    fun song(songUrl: String): SongPage {
        val resolved = resolve(songUrl)
        val html = get(resolved)
        return AmDmParser.parseSong(html, resolved)
            ?: throw IOException("Не удалось разобрать страницу песни: $resolved")
    }

    private fun resolve(url: String): String = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("/") -> baseUrl.trimEnd('/') + url
        else -> baseUrl.trimEnd('/') + "/" + url
    }

    private fun artistNameFromUrl(url: String): String =
        url.trimEnd('/')
            .substringAfterLast('/')
            .replace('_', ' ')
            .replaceFirstChar { it.titlecase() }

    private fun get(url: String): String {
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
            return connection.inputStream
                .bufferedReader(StandardCharsets.UTF_8)
                .use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 25_000
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) GuitarTuner/0.1"
    }
}
