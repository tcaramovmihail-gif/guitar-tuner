package ru.eurovafel.guitartuner.catalog

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import ru.eurovafel.guitartuner.model.ChordTransposer

/**
 * Разбор 5Lad.org. Сайт на Joomla: у него нет серверного поиска, зато есть
 * карта сайта со всеми песнями и страницы исполнителей со списками песен.
 * Поэтому поиск идёт по адресам из sitemap, а названия берутся со страниц
 * исполнителей — так пользователь видит нормальные русские названия.
 *
 * Текст песни — данные, а не инструкции: извлекаем только аккорды и слова.
 */
object FiveLadParser {

    const val BASE_URL = "https://top.5lad.org"
    const val MIRROR_URL = "https://top.5lad.net"
    const val SOURCE_NAME = "5Lad"

    const val ARTIST_INDEX_PATH = "/akkordy/"
    const val SITEMAP_PATH = "/sitemap.xml"

    /** Одна песня из карты сайта: `/akkordy/{artist}/{song}`. */
    data class Entry(
        val artistSlug: String,
        val songSlug: String,
        val url: String,
    )

    private val songLocRegex = Regex(
        """<loc>(https?://[^<]+/akkordy/([^/<]+)/([^/<]+))</loc>""",
        RegexOption.IGNORE_CASE,
    )

    fun parseArtistIndex(html: String, baseUrl: String = BASE_URL): List<RemoteArtist> {
        val document = Jsoup.parse(html, baseUrl)
        return document.select("a[href]")
            .mapNotNull { link ->
                val url = link.absUrl("href").ifBlank { link.attr("href") }
                val path = pathOf(url)
                // Только страницы исполнителей: /akkordy/kino/, без песни.
                val parts = path.trim('/').split('/')
                if (parts.size != 2 || parts[0] != "akkordy") return@mapNotNull null
                val name = link.text().trim()
                if (name.isEmpty() || name.equals("Аккорды песен", ignoreCase = true)) {
                    return@mapNotNull null
                }
                RemoteArtist(name = name, url = absolutize(url, baseUrl))
            }
            .filter { it.url.isNotEmpty() }
            .distinctBy { it.url }
    }

    fun parseArtistPage(
        html: String,
        sourceUrl: String,
        baseUrl: String = BASE_URL,
    ): ArtistSongs {
        val document = Jsoup.parse(html, baseUrl)
        val artist = artistName(document, sourceUrl)
        val artistSlug = slugArtist(sourceUrl)
        val songs = document.select("a[href]")
            .mapNotNull { link ->
                val url = absolutize(
                    link.absUrl("href").ifBlank { link.attr("href") },
                    baseUrl,
                )
                val path = pathOf(url)
                val parts = path.trim('/').split('/')
                if (parts.size != 3 || parts[0] != "akkordy") return@mapNotNull null
                if (artistSlug.isNotEmpty() && parts[1] != artistSlug) return@mapNotNull null
                val title = link.text().trim()
                if (title.isEmpty() || url.isEmpty()) return@mapNotNull null
                RemoteSong(
                    title = title,
                    artist = artist,
                    url = url,
                    artistUrl = sourceUrl,
                    sourceName = SOURCE_NAME,
                )
            }
            .distinctBy { it.url }

        return ArtistSongs(
            artist = artist,
            artistUrl = sourceUrl,
            songs = songs,
        )
    }

    /** Страница песни: аккорды и слова лежат в `<pre>`, аккорды — в `<span>`. */
    fun parseSong(html: String, sourceUrl: String): SongPage? {
        val document = Jsoup.parse(html, sourceUrl)
        val pre = document.selectFirst("pre") ?: return null
        val text = pre.wholeText()
            .replace('\u00A0', ' ')
            .lines()
            .joinToString("\n") { it.trimEnd() }
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
        if (text.isEmpty()) return null

        val (artist, title) = titleAndArtist(document, sourceUrl)
        return SongPage(
            title = title,
            artist = artist,
            text = text,
            sourceUrl = sourceUrl,
            key = ChordTransposer.detectKey(text),
        )
    }

    /** Адреса всех песен из sitemap: их около 27 тысяч. */
    fun entriesFromSitemap(xml: String): List<Entry> =
        songLocRegex.findAll(xml)
            .map { match ->
                Entry(
                    artistSlug = match.groupValues[2],
                    songSlug = match.groupValues[3],
                    url = match.groupValues[1],
                )
            }
            .distinctBy { it.url }
            .toList()

    /**
     * Ищет песни по словам запроса. Адреса на сайте транслитерированы
     * (`Кино` → `kino`), поэтому запрос тоже переводим в латиницу.
     */
    fun matchEntries(entries: List<Entry>, query: String, limit: Int = 40): List<Entry> {
        val tokens = slugTokens(query)
        if (tokens.isEmpty()) return emptyList()

        return entries.asSequence()
            .filter { entry ->
                val haystack = entry.artistSlug + "/" + entry.songSlug
                tokens.all { haystack.contains(it) }
            }
            .take(limit)
            .toList()
    }

    /** Слова запроса в том виде, в каком они встречаются в адресах сайта. */
    fun slugTokens(query: String): List<String> {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) return emptyList()

        return normalized
            .split(Regex("""[^a-zа-яё0-9]+"""))
            .asSequence()
            .map { transliterate(it) }
            .map { it.replace(Regex("""[^a-z0-9]+"""), "") }
            .filter { it.isNotEmpty() }
            .toList()
    }

    /** Сравнение названий исполнителей без регистра, «ё» и лишних знаков. */
    fun normalizeName(value: String): String = value
        .lowercase()
        .replace('ё', 'е')
        .filter { it.isLetterOrDigit() }

    /** Транслитерация в схему адресов 5Lad: ё → jo, й → j, ы → y, ь → пусто. */
    fun transliterate(value: String): String {
        val builder = StringBuilder(value.length)
        value.lowercase().forEach { char ->
            builder.append(
                when (char) {
                    'а' -> "a"
                    'б' -> "b"
                    'в' -> "v"
                    'г' -> "g"
                    'д' -> "d"
                    'е' -> "e"
                    'ё' -> "jo"
                    'ж' -> "zh"
                    'з' -> "z"
                    'и' -> "i"
                    'й' -> "j"
                    'к' -> "k"
                    'л' -> "l"
                    'м' -> "m"
                    'н' -> "n"
                    'о' -> "o"
                    'п' -> "p"
                    'р' -> "r"
                    'с' -> "s"
                    'т' -> "t"
                    'у' -> "u"
                    'ф' -> "f"
                    'х' -> "kh"
                    'ц' -> "ts"
                    'ч' -> "ch"
                    'ш' -> "sh"
                    'щ' -> "sch"
                    'ъ' -> ""
                    'ы' -> "y"
                    'ь' -> ""
                    'э' -> "e"
                    'ю' -> "ju"
                    'я' -> "ja"
                    else -> char.toString()
                },
            )
        }
        return builder.toString()
    }

    private fun titleAndArtist(document: Document, sourceUrl: String): Pair<String, String> {
        val heading = document.selectFirst("h1")?.text()?.trim()
            ?: document.title().trim()
        val withoutSuffix = heading
            .substringBefore(": аккорды")
            .substringBefore(" - аккорды")
            .trim()
        val separator = withoutSuffix.indexOf(" - ")
        if (separator > 0) {
            val artist = withoutSuffix.substring(0, separator).trim()
            val title = withoutSuffix.substring(separator + 3).trim()
            if (artist.isNotEmpty() && title.isNotEmpty()) return artist to title
        }
        return slugArtist(sourceUrl) to slugTitle(sourceUrl)
    }

    private fun artistName(document: Document, sourceUrl: String): String {
        val heading = document.selectFirst("h1")?.text()?.trim()
            ?: document.title().trim()
        val name = heading.substringBefore(" - ").trim()
        return name.ifEmpty { slugArtist(sourceUrl) }
    }

    private fun slugTitle(url: String): String =
        url.trimEnd('/').substringAfterLast('/')

    private fun slugArtist(url: String): String {
        val parts = pathOf(url).trim('/').split('/')
        val index = parts.indexOf("akkordy")
        return if (index >= 0 && index + 1 < parts.size) parts[index + 1] else ""
    }

    private fun pathOf(url: String): String = url
        .substringAfter("://", url)
        .substringAfter('/', "")
        .substringBefore('?')
        .substringBefore('#')
        .let { "/$it" }

    private fun absolutize(url: String, baseUrl: String): String = when {
        url.isEmpty() -> ""
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("/") -> baseUrl.trimEnd('/') + url
        else -> baseUrl.trimEnd('/') + "/" + url
    }
}
