package ru.eurovafel.guitartuner.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FiveLadParserTest {

    @Test
    fun `artist index keeps only artist pages`() {
        val html = """
            <html><body>
              <a href="/akkordy/">Аккорды песен</a>
              <a href="/akkordy/kino/"><b>Кино</b></a>
              <a href="/akkordy/ddt/"><b>ДДТ</b></a>
              <a href="/akkordy/kino/zvezda-po-imeni-solntse">Звезда по имени Солнце</a>
            </body></html>
        """.trimIndent()

        val artists = FiveLadParser.parseArtistIndex(html)

        assertEquals(listOf("Кино", "ДДТ"), artists.map { it.name })
        assertEquals("https://top.5lad.org/akkordy/kino/", artists[0].url)
    }

    @Test
    fun `artist page returns songs with readable titles`() {
        val html = """
            <html><head>
              <title>Кино - Аккорды песен для гитары - 5Lad.org</title>
            </head><body>
              <h1>Кино - аккорды и тексты песен</h1>
              <a href="/akkordy/kino/zvezda-po-imeni-solntse">Звезда по имени Солнце</a>
              <a href="/akkordy/kino/aprel">Апрель</a>
              <a href="/akkordy/sektor-gaza/lirika">Лирика</a>
            </body></html>
        """.trimIndent()

        val page = FiveLadParser.parseArtistPage(
            html,
            sourceUrl = "https://top.5lad.org/akkordy/kino/",
        )

        assertEquals("Кино", page.artist)
        assertEquals(listOf("Звезда по имени Солнце", "Апрель"), page.songs.map { it.title })
        assertEquals("https://top.5lad.org/akkordy/kino/aprel", page.songs[1].url)
        assertEquals("5Lad", page.songs[0].sourceName)
    }

    @Test
    fun `song page keeps chords on their own lines`() {
        val html = """
            <html><head>
              <title>Кино - Звезда по имени Солнце: аккорды для гитары</title>
            </head><body>
              <h1>Кино - Звезда по имени Солнце: аккорды</h1>
              <pre><em>Вступление</em>: <span>D</span>m <span>A</span>m

        <span>A</span>m
Белый снег, серый лёд</pre>
            </body></html>
        """.trimIndent()

        val page = FiveLadParser.parseSong(
            html,
            sourceUrl = "https://top.5lad.org/akkordy/kino/zvezda-po-imeni-solntse",
        )

        assertNotNull(page)
        page!!
        assertEquals("Кино", page.artist)
        assertEquals("Звезда по имени Солнце", page.title)
        assertTrue(page.text.lines().any { it.trim() == "Am" })
        assertTrue(page.text.contains("Белый снег, серый лёд"))
    }

    @Test
    fun `sitemap entries expose artist and song slugs`() {
        val xml = """
            <urlset>
              <url><loc>https://top.5lad.org/akkordy/kino/zvezda-po-imeni-solntse</loc></url>
              <url><loc>https://top.5lad.org/akkordy/ddt/chto-takoe-osen</loc></url>
              <url><loc>https://top.5lad.org/akkordy/</loc></url>
              <url><loc>https://top.5lad.org/applikatury/a</loc></url>
            </urlset>
        """.trimIndent()

        val entries = FiveLadParser.entriesFromSitemap(xml)

        assertEquals(2, entries.size)
        assertEquals("kino", entries[0].artistSlug)
        assertEquals("zvezda-po-imeni-solntse", entries[0].songSlug)
    }

    @Test
    fun `search translates russian words into site slugs`() {
        val entries = listOf(
            FiveLadParser.Entry(
                "kino",
                "zvezda-po-imeni-solntse",
                "https://top.5lad.org/akkordy/kino/zvezda-po-imeni-solntse",
            ),
            FiveLadParser.Entry(
                "ddt",
                "chto-takoe-osen",
                "https://top.5lad.org/akkordy/ddt/chto-takoe-osen",
            ),
            FiveLadParser.Entry(
                "aljona-shvets",
                "gorod",
                "https://top.5lad.org/akkordy/aljona-shvets/gorod",
            ),
        )

        assertEquals("zvezda", FiveLadParser.slugTokens("Звезда").first())
        assertEquals(
            listOf("kino", "zvezda"),
            FiveLadParser.slugTokens("Кино — звезда"),
        )

        val stars = FiveLadParser.matchEntries(entries, "звезда по имени солнце")
        assertEquals(1, stars.size)
        assertEquals("zvezda-po-imeni-solntse", stars[0].songSlug)

        val kino = FiveLadParser.matchEntries(entries, "кино")
        assertEquals(1, kino.size)
        assertEquals("kino", kino[0].artistSlug)
    }

    @Test
    fun `name normalization ignores case and yo`() {
        assertEquals(
            FiveLadParser.normalizeName("Король и Шут"),
            FiveLadParser.normalizeName("король и шут"),
        )
        assertEquals(
            FiveLadParser.normalizeName("Белый орёл"),
            FiveLadParser.normalizeName("белый орел"),
        )
    }
}
