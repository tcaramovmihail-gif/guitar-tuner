package ru.eurovafel.guitartuner.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AkkordsProParserTest {

    @Test
    fun `search results carry title artist and source`() {
        val html = """
            <ol class="apx-top-songs apx-top-songs--archive">
              <li class="apx-top-songs__item">
                <a class="apx-top-songs__link" href="https://akkords.pro/songs/kino/zvezda/">
                  <span class="apx-top-songs__body">
                    <span class="apx-top-songs__title">Звезда по имени Солнце</span>
                    <span class="apx-top-songs__artist">Кино (Виктор Цой)</span>
                  </span>
                </a>
              </li>
              <li class="apx-top-songs__item">
                <a class="apx-top-songs__link" href="https://akkords.pro/songs/kino/pachka/">
                  <span class="apx-top-songs__body">
                    <span class="apx-top-songs__title">Пачка сигарет</span>
                    <span class="apx-top-songs__artist">Кино (Виктор Цой)</span>
                  </span>
                </a>
              </li>
            </ol>
        """.trimIndent()

        val songs = AkkordsProParser.parseSearchSongs(html)

        assertEquals(2, songs.size)
        assertEquals("Звезда по имени Солнце", songs[0].title)
        assertEquals("Кино (Виктор Цой)", songs[0].artist)
        assertEquals("Akkords.pro", songs[0].sourceName)
        assertEquals("https://akkords.pro/songs/kino/zvezda/", songs[0].url)
    }

    @Test
    fun `song page turns chord spans into chord lines`() {
        val html = """
            <html>
              <head><title>Звезда по имени Солнце • Кино (В.Цой)</title></head>
              <body>
                <h1 class="entry-title">Звезда по имени Солнце: аккорды для гитары</h1>
                <p class="chords">
                  <b>Куплет 1:</b>
                  <span>Am</span>
                  Белый день, белый снег,
                  <span>C</span>
                  Белый саван из бумаги.
                </p>
              </body>
            </html>
        """.trimIndent()

        val page = AkkordsProParser.parseSong(html, "https://akkords.pro/songs/kino/zvezda/")

        assertNotNull(page)
        page!!
        assertEquals("Звезда по имени Солнце", page.title)
        assertEquals("Кино (В.Цой)", page.artist)
        assertTrue(page.text.contains("Куплет 1:"))
        assertTrue(page.text.contains("\nAm\n"))
        assertTrue(page.text.contains("Белый день, белый снег,"))
        assertTrue(page.text.contains("\nC\n"))
        assertTrue(page.text.contains("Белый саван из бумаги."))
    }

    @Test
    fun `search url encodes spaces and cyrillic`() {
        val url = AkkordsProParser.searchUrl("кино дом")

        assertTrue(url.startsWith("https://akkords.pro/search/"))
        assertTrue(url.contains("%20"))
        assertTrue(url.endsWith("/"))
    }
}
