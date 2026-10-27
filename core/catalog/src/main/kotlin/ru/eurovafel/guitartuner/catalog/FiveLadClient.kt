package ru.eurovafel.guitartuner.catalog

import java.io.IOException

/**
 * Клиент 5Lad.org с запасным зеркалом 5Lad.net.
 *
 * Поиска на стороне сервера у сайта нет, поэтому:
 * 1. имена исполнителей ищем в общем индексе `/akkordy/`;
 * 2. названия песен — по адресам из `sitemap.xml`, где они транслитерированы;
 * 3. русские названия добираем со страницы исполнителя.
 *
 * Sitemap весит около 2 МБ и кешируется на время работы приложения.
 */
class FiveLadClient {

    @Volatile
    private var sitemapXml: String? = null

    @Volatile
    private var artistIndex: List<RemoteArtist>? = null

    fun search(query: String): SearchResult {
        val text = query.trim()
        if (text.length < 2) return SearchResult()

        val artists = runCatching { artists() }.getOrDefault(emptyList())
        val artistMatches = artists
            .filter { FiveLadParser.normalizeName(it.name).contains(FiveLadParser.normalizeName(text)) }
            .take(MAX_ARTISTS)

        val songs = LinkedHashMap<String, RemoteSong>()
        val resolvedArtists = artistMatches.toMutableList()

        artistMatches.forEach { artist ->
            runCatching { artistSongs(artist.url) }.getOrNull()?.let { page ->
                val exactArtist = FiveLadParser.normalizeName(page.artist) ==
                    FiveLadParser.normalizeName(text)
                page.songs
                    .filter {
                        exactArtist ||
                            FiveLadParser.normalizeName(it.title)
                                .contains(FiveLadParser.normalizeName(text))
                    }
                    .forEach { songs.putIfAbsent(it.url, it) }
            }
        }

        if (songs.size < MAX_SONGS) {
            val entries = runCatching {
                FiveLadParser.matchEntries(sitemapEntries(), text, limit = MAX_SONGS)
            }.getOrDefault(emptyList())

            val bySlug = artists.associateBy { slugOf(it.url) }
            val byArtist = entries.groupBy { it.artistSlug }.entries.take(MAX_ARTISTS)
            for ((artistSlug, group) in byArtist) {
                if (songs.size >= MAX_SONGS) break
                bySlug[artistSlug]?.let { known ->
                    if (resolvedArtists.none { it.url == known.url }) resolvedArtists += known
                }
                val artistUrl = "${FiveLadParser.BASE_URL}/akkordy/$artistSlug/"
                val page = runCatching { artistSongs(artistUrl) }.getOrNull() ?: continue
                val wanted = group.mapTo(HashSet()) { it.url }
                page.songs
                    .filter { it.url in wanted }
                    .forEach { songs.putIfAbsent(it.url, it) }
            }
        }

        return SearchResult(
            songs = songs.values.take(MAX_SONGS),
            artists = resolvedArtists.distinctBy { it.url }.take(MAX_ARTISTS),
        )
    }

    fun artistSongs(artistUrl: String): ArtistSongs {
        val url = resolve(artistUrl)
        val html = fetch(url)
        return FiveLadParser.parseArtistPage(html, sourceUrl = url, baseUrl = url)
    }

    fun song(songUrl: String): SongPage {
        val url = resolve(songUrl)
        val html = fetch(url)
        return FiveLadParser.parseSong(html, sourceUrl = url)
            ?: throw IOException("Не удалось разобрать страницу песни: $url")
    }

    private fun artists(): List<RemoteArtist> = artistIndex ?: synchronized(this) {
        artistIndex ?: run {
            val url = FiveLadParser.BASE_URL + FiveLadParser.ARTIST_INDEX_PATH
            val html = fetch(url)
            FiveLadParser.parseArtistIndex(html, baseUrl = url).also { artistIndex = it }
        }
    }

    private fun sitemapEntries(): List<FiveLadParser.Entry> {
        val xml = sitemapXml ?: synchronized(this) {
            sitemapXml ?: fetch(FiveLadParser.BASE_URL + FiveLadParser.SITEMAP_PATH)
                .also { sitemapXml = it }
        }
        return FiveLadParser.entriesFromSitemap(xml)
    }

    /** Если основной домен недоступен, пробуем зеркало. */
    private fun fetch(url: String): String {
        val primary = resolve(url)
        return try {
            HtmlHttp.get(primary)
        } catch (error: IOException) {
            val mirror = mirrorOf(primary)
            if (mirror == primary) throw error
            HtmlHttp.get(mirror)
        }
    }

    private fun mirrorOf(url: String): String = when {
        url.contains("top.5lad.org") -> url.replace("top.5lad.org", "top.5lad.net")
        url.contains("top.5lad.net") -> url.replace("top.5lad.net", "top.5lad.org")
        else -> url
    }

    private fun resolve(url: String): String = when {
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("/") -> FiveLadParser.BASE_URL + url
        else -> FiveLadParser.BASE_URL + "/" + url
    }

    private fun slugOf(url: String): String =
        url.trimEnd('/').substringAfterLast('/')

    private companion object {
        const val MAX_SONGS = 40
        const val MAX_ARTISTS = 3
    }
}
