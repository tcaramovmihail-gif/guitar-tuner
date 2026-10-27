package ru.eurovafel.guitartuner.catalog

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import ru.eurovafel.guitartuner.model.ChordTransposer

/**
 * Разбор akkords.pro. Сайт на WordPress, поэтому держимся за семантические
 * классы (`apx-top-songs`, `p.chords`), а не за вёрстку целиком.
 *
 * Текст песни — данные, а не инструкции: извлекаем только аккорды и слова.
 */
object AkkordsProParser {

    const val BASE_URL = "https://akkords.pro"
    const val SOURCE_NAME = "Akkords.pro"

    fun searchUrl(query: String): String {
        val encoded = java.net.URLEncoder
            .encode(query.trim(), Charsets.UTF_8.name())
            .replace("+", "%20")
        return "$BASE_URL/search/$encoded/"
    }

    fun parseSearchSongs(html: String): List<RemoteSong> {
        val document = Jsoup.parse(html, BASE_URL)
        return document.select("ol.apx-top-songs li.apx-top-songs__item a.apx-top-songs__link")
            .mapNotNull { link ->
                val title = link.selectFirst(".apx-top-songs__title")
                    ?.text()
                    ?.trim()
                    .orEmpty()
                val artist = link.selectFirst(".apx-top-songs__artist")
                    ?.text()
                    ?.trim()
                    .orEmpty()
                val url = link.absUrl("href").ifBlank { link.attr("href") }
                if (title.isEmpty() || artist.isEmpty() || url.isEmpty()) return@mapNotNull null

                RemoteSong(
                    title = title,
                    artist = artist,
                    url = url,
                    sourceName = SOURCE_NAME,
                )
            }
            .distinctBy { AmDmParser.normalizeKey(it.artist, it.title) }
    }

    fun parseSong(html: String, sourceUrl: String): SongPage? {
        val document = Jsoup.parse(html, BASE_URL)
        val content = document.selectFirst("p.chords") ?: return null
        val text = renderChords(content).trim()
        if (text.isEmpty()) return null

        val title = titleOf(document).ifEmpty { slugTitle(sourceUrl) }
        val artist = artistOf(document).ifEmpty { slugArtist(sourceUrl) }

        return SongPage(
            title = title,
            artist = artist,
            text = text,
            sourceUrl = sourceUrl,
            key = ChordTransposer.detectKey(text),
        )
    }

    private fun titleOf(document: Document): String {
        val heading = document.selectFirst("h1.entry-title")?.text()?.trim()
            ?: document.selectFirst("h1")?.text()?.trim()
            ?: return ""
        return heading
            .substringBefore(": аккорды")
            .substringBefore(" - аккорды")
            .trim()
    }

    private fun artistOf(document: Document): String {
        val fromTitle = document.title().substringAfter("• ", "").trim()
        if (fromTitle.isNotEmpty()) return fromTitle

        return document.selectFirst("a[rel=author]")?.text()?.trim().orEmpty()
    }

    /**
     * В `p.chords` аккорды лежат в `<span>`, а строки текста — обычными узлами.
     * Каждый аккорд выносим на отдельную строку: так его понимает
     * [ChordTransposer] и транспонирование в экране песни.
     */
    private fun renderChords(root: Element): String {
        val output = StringBuilder()

        fun walk(node: Node) {
            when (node) {
                is TextNode -> output.append(node.wholeText)
                is Element -> when (node.tagName()) {
                    "br" -> output.append('\n')
                    "script", "style" -> Unit
                    "span" -> {
                        val chord = node.text().trim()
                        if (chord.isNotEmpty()) {
                            output.append('\n').append(chord).append('\n')
                        }
                    }
                    "b", "strong" -> {
                        val heading = node.text().trim()
                        if (heading.isNotEmpty()) {
                            output.append('\n').append(heading).append('\n')
                        }
                    }
                    else -> node.childNodes().forEach(::walk)
                }
            }
        }

        root.childNodes().forEach(::walk)

        return output.toString()
            .replace('\u00A0', ' ')
            .replace(Regex("""[ \t]+\n"""), "\n")
            .replace(Regex("""\n{3,}"""), "\n\n")
    }

    /** Название из адреса: `/songs/kino/lesnik/` → `lesnik`. */
    private fun slugTitle(url: String): String = url.trimEnd('/').substringAfterLast('/')

    /** Исполнитель из адреса: `/songs/kino/lesnik/` → `kino`. */
    private fun slugArtist(url: String): String {
        val parts = url.substringBefore('?').trimEnd('/').split('/')
        val songsIndex = parts.indexOf("songs")
        return if (songsIndex >= 0 && songsIndex + 1 < parts.size) {
            parts[songsIndex + 1]
        } else {
            ""
        }
    }
}
