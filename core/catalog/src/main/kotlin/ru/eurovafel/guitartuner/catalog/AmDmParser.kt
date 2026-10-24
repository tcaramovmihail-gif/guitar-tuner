package ru.eurovafel.guitartuner.catalog

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import ru.eurovafel.guitartuner.model.ChordTransposer

/**
 * Разбор HTML AmDm. Правила намеренно терпимые: сайт меняет вёрстку чаще,
 * чем API, поэтому при отсутствии одного селектора пробуем запасной.
 *
 * Текст подбора — это данные, а не инструкции: мы только извлекаем его.
 */
object AmDmParser {

    const val BASE_URL = "https://496.amdm.ru"

    fun parseSearchSongs(html: String): List<RemoteSong> {
        val document = Jsoup.parse(html, BASE_URL)
        val songs = document.select("table.items tr").mapNotNull { row ->
            val links = row.select("td.artist_name a.artist")
            if (links.size < 2) return@mapNotNull null

            val artistLink = links[0]
            val songLink = links[1]
            val artist = artistLink.text().trim()
            val title = songLink.text().trim()
            val url = absolutize(songLink, BASE_URL)
            if (artist.isEmpty() || title.isEmpty() || url.isEmpty()) return@mapNotNull null

            RemoteSong(
                title = title,
                artist = artist,
                url = url,
                artistUrl = absolutize(artistLink, BASE_URL),
                verified = row.selectFirst("span.fa-check-circle") != null,
            )
        }
        return bestVersions(songs)
    }

    fun parseArtistSearch(html: String): List<RemoteArtist> {
        val document = Jsoup.parse(html, BASE_URL)
        return document.select("table.items tr")
            .mapNotNull { row ->
                val link = row.selectFirst("td.artist_name a.artist") ?: return@mapNotNull null
                val name = link.text().trim()
                if (name.isEmpty()) return@mapNotNull null

                val count = row.selectFirst("span.artist_songs_count")
                    ?.text()
                    ?.filter(Char::isDigit)
                    ?.toIntOrNull()

                RemoteArtist(
                    name = name,
                    url = absolutize(link, BASE_URL),
                    songCount = count,
                )
            }
            .distinctBy { it.url }
    }

    fun parseArtistSongs(
        html: String,
        fallbackArtistUrl: String? = null,
    ): ArtistSongs {
        val document = Jsoup.parse(html, BASE_URL)
        val artist = document.selectFirst("div.artist-profile__info h1")
            ?.text()
            ?.trim()
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: ""

        val rows = document.select("table#tablesort tbody tr")
            .ifEmpty { document.select("table.items tbody tr") }

        val songs = rows.mapNotNull { row ->
            val link = row.selectFirst("a.g-link") ?: return@mapNotNull null
            val title = link.text().trim()
            val url = absolutize(link, BASE_URL)
            if (title.isEmpty() || url.isEmpty()) return@mapNotNull null

            RemoteSong(
                title = title,
                artist = artist,
                url = url,
                artistUrl = fallbackArtistUrl,
                views = row.selectFirst("td.number.hidden-phone")
                    ?.text()
                    ?.filter(Char::isDigit)
                    ?.toLongOrNull()
                    ?: 0L,
                verified = row.selectFirst("span.fa-check-circle") != null,
            )
        }

        return ArtistSongs(
            artist = artist,
            artistUrl = fallbackArtistUrl.orEmpty(),
            songs = bestVersions(songs),
        )
    }

    fun parseSong(html: String, sourceUrl: String): SongPage? {
        val document = Jsoup.parse(html, BASE_URL)
        val artist = document.selectFirst("span[itemprop=byArtist]")?.text()?.trim()
            .orEmpty()
            .ifEmpty { titleParts(document)?.first.orEmpty() }
        val title = document.selectFirst("span[itemprop=name]")?.text()?.trim()
            .orEmpty()
            .ifEmpty { titleParts(document)?.second.orEmpty() }

        val pre = document.selectFirst("pre[itemprop=chordsBlock]")
            ?: document.selectFirst("pre.podbor__text")
            ?: return null

        val text = renderChordBlock(pre).trim()
        if (text.isEmpty()) return null

        return SongPage(
            title = title.ifEmpty { "Без названия" },
            artist = artist.ifEmpty { "Без исполнителя" },
            text = text,
            sourceUrl = sourceUrl,
            key = ChordTransposer.detectKey(text),
        )
    }

    /**
     * Оставляет по одной версии каждой песни. Версия с зелёной галочкой важнее
     * любой другой, затем выбирается самая просматриваемая.
     */
    fun bestVersions(songs: List<RemoteSong>): List<RemoteSong> =
        songs.groupBy { normalizeKey(it.artist, it.title) }
            .mapNotNull { (_, versions) ->
                versions.maxWithOrNull(
                    compareBy<RemoteSong> { it.verified }.thenBy { it.views },
                )
            }
            .sortedWith(
                compareBy<RemoteSong> { it.artist.lowercase() }
                    .thenBy { it.title.lowercase() },
            )

    fun normalizeKey(artist: String, title: String): String =
        artist.trim().lowercase() + "\u0000" + normalizeTitle(title)

    fun normalizeTitle(title: String): String =
        title.trim().lowercase().replace(Regex("""\s+"""), " ")

    private fun renderChordBlock(pre: Element): String {
        val output = StringBuilder()

        fun walk(node: Node) {
            when (node) {
                is TextNode -> output.append(node.wholeText)
                is Element -> when {
                    node.tagName() == "br" -> output.append('\n')
                    node.tagName() == "script" || node.tagName() == "style" -> Unit
                    node.hasClass("podbor__chord") -> {
                        output.append(node.attr("data-chord").ifBlank { node.text() })
                    }
                    node.hasClass("podbor__keyword") -> {
                        val keyword = node.text().trim()
                        if (keyword.isNotEmpty()) {
                            output.append('\n').append(keyword).append('\n')
                        }
                    }
                    node.hasClass("podbor__pripev") -> {
                        output.append('\n')
                        node.childNodes().forEach(::walk)
                        output.append('\n')
                    }
                    node.hasClass("podbor__inline") -> node.childNodes().forEach(::walk)
                    else -> node.childNodes().forEach(::walk)
                }
            }
        }

        pre.childNodes().forEach(::walk)

        return output.toString()
            .replace('\u00A0', ' ')
            .replace(Regex("""\n{3,}"""), "\n\n")
    }

    private fun titleParts(document: Document): Pair<String, String>? {
        val raw = document.title().substringBefore(", аккорды").trim()
        val separator = raw.indexOf(" - ")
        if (separator <= 0) return null
        return raw.substring(0, separator).trim() to raw.substring(separator + 3).trim()
    }

    private fun absolutize(element: Element, baseUrl: String): String =
        element.absUrl("href").ifBlank {
            val href = element.attr("href")
            if (href.startsWith("http")) href else baseUrl.trimEnd('/') + "/" + href.trimStart('/')
        }
}
