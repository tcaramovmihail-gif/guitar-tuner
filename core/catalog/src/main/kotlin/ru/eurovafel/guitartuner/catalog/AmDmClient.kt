package ru.eurovafel.guitartuner.catalog

import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * HTTP-клиент AmDm без сторонних сетевых библиотек.
 *
 * [baseUrl] позволяет подключить зеркало (`501.amdm.ru`): вёрстка та же,
 * поэтому парсер общий, а ссылки резолвятся от нужного домена.
 */
class AmDmClient(
    private val baseUrl: String = AmDmParser.BASE_URL,
    private val sourceName: String = "AmDm",
) {
    fun search(query: String): SearchResult {
        val encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8.name())
        val songs = AmDmParser.parseSearchSongs(
            HtmlHttp.get("${baseUrl.trimEnd('/')}/search/?q=$encoded"),
            baseUrl = baseUrl,
        ).map { it.copy(sourceName = sourceName) }
        val artists = runCatching {
            AmDmParser.parseArtistSearch(
                HtmlHttp.get("${baseUrl.trimEnd('/')}/search/artist/?q=$encoded"),
                baseUrl = baseUrl,
            )
        }.getOrDefault(emptyList())

        return SearchResult(songs = songs, artists = artists)
    }

    fun artistSongs(artistUrl: String): ArtistSongs {
        val resolved = resolve(artistUrl)
        val html = HtmlHttp.get(resolved)
        val parsed = AmDmParser.parseArtistSongs(
            html,
            fallbackArtistUrl = resolved,
            baseUrl = baseUrl,
        )
        return if (parsed.artist.isBlank()) {
            parsed.copy(
                artist = artistNameFromUrl(resolved),
                songs = parsed.songs.map { it.copy(sourceName = sourceName) },
            )
        } else {
            parsed.copy(songs = parsed.songs.map { it.copy(sourceName = sourceName) })
        }
    }

    fun song(songUrl: String): SongPage {
        val resolved = resolve(songUrl)
        val html = HtmlHttp.get(resolved)
        return AmDmParser.parseSong(html, resolved, baseUrl = baseUrl)
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
}
