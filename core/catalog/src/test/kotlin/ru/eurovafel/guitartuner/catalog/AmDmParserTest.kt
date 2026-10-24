package ru.eurovafel.guitartuner.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmDmParserTest {

    @Test
    fun `search results are parsed and deduplicated`() {
        val html = """
            <table class="items">
              <tr class="top_label"><th>Исполнитель</th></tr>
              <tr>
                <td class="artist_name">
                  <a href="https://496.amdm.ru/akkordi/korol_i_shut/" class="artist">Король и Шут</a> —
                  <a href="https://496.amdm.ru/akkordi/korol_i_shut/2455/lesnik/" class="artist">Лесник</a>
                  <span class="fa fa-check-circle"></span>
                </td>
              </tr>
              <tr>
                <td class="artist_name">
                  <a href="https://496.amdm.ru/akkordi/korol_i_shut/" class="artist">Король и Шут</a> —
                  <a href="https://496.amdm.ru/akkordi/korol_i_shut/2860/lesnik/" class="artist">Лесник</a>
                </td>
              </tr>
              <tr>
                <td class="artist_name">
                  <a href="https://496.amdm.ru/akkordi/bi_2/" class="artist">Би-2</a> —
                  <a href="https://496.amdm.ru/akkordi/bi_2/123/polu_shans/" class="artist">Полушания</a>
                </td>
              </tr>
            </table>
        """.trimIndent()

        val songs = AmDmParser.parseSearchSongs(html)

        assertEquals(2, songs.size)
        val lesnik = songs.first { it.title == "Лесник" }
        assertTrue(lesnik.verified)
        assertEquals("https://496.amdm.ru/akkordi/korol_i_shut/2455/lesnik/", lesnik.url)
        assertEquals("Король и Шут", lesnik.artist)
    }

    @Test
    fun `artist search returns counts`() {
        val html = """
            <table class="items">
              <tr><td class="artist_name">
                <a href="https://496.amdm.ru/akkordi/korol_i_shut/" class="artist">Король и Шут</a>
                <span class="artist_songs_count">(635)</span>
              </td></tr>
              <tr><td class="artist_name">
                <a href="https://496.amdm.ru/akkordi/bi_2/" class="artist">Би-2</a>
                <span class="artist_songs_count">(324)</span>
              </td></tr>
            </table>
        """.trimIndent()

        val artists = AmDmParser.parseArtistSearch(html)

        assertEquals(2, artists.size)
        assertEquals(635, artists[0].songCount)
        assertEquals("Би-2", artists[1].name)
    }

    @Test
    fun `artist page keeps verified version of duplicate songs`() {
        val html = """
            <div class="artist-profile__info"><h1>Король и Шут</h1></div>
            <table id="tablesort" class="items tablesorter">
              <tbody>
                <tr>
                  <td><a href="https://496.amdm.ru/akkordi/korol_i_shut/92516/amtv/" class="g-link">AMTV</a></td>
                  <td class="number icon"></td>
                  <td class="number hidden-phone">180,756</td>
                </tr>
                <tr>
                  <td><a href="https://496.amdm.ru/akkordi/korol_i_shut/115206/amtv/" class="g-link">AMTV</a>
                      <span class="fa fa-check-circle"></span></td>
                  <td class="number icon"></td>
                  <td class="number hidden-phone">101,290</td>
                </tr>
                <tr>
                  <td><a href="https://496.amdm.ru/akkordi/korol_i_shut/2860/lesnik/" class="g-link">Лесник</a></td>
                  <td class="number icon"></td>
                  <td class="number hidden-phone">500,000</td>
                </tr>
              </tbody>
            </table>
        """.trimIndent()

        val result = AmDmParser.parseArtistSongs(html)

        assertEquals("Король и Шут", result.artist)
        assertEquals(2, result.songs.size)
        val amtv = result.songs.first { it.title == "AMTV" }
        assertTrue(amtv.verified)
        assertEquals(101_290L, amtv.views)
    }

    @Test
    fun `song page renders chords without html`() {
        val html = """
            <div itemscope itemtype="https://schema.org/MusicRecording">
              <h1>
                <span itemprop="byArtist">Король и Шут</span> -
                <span itemprop="name">Лесник</span>, аккорды
              </h1>
            </div>
            <pre itemprop="chordsBlock" class="field__podbor_new podbor__text"><div class="podbor__keyword">[Вступление]:</div>Riff 1:
              <div class="podbor__chord" data-chord="Am"><span>Am</span></div>                              <div class="podbor__chord" data-chord="F"><span>F</span></div>
            [Куплет 1]:
            <div class="podbor__chord" data-chord="Am"><span>Am</span></div>
            Замученный дорогой,
            <div class="podbor__chord" data-chord="F"><span>F</span></div>
            Я выбился из сил,
            </pre>
        """.trimIndent()

        val page = AmDmParser.parseSong(html, "https://496.amdm.ru/akkordi/korol_i_shut/2860/lesnik/")

        assertNotNull(page)
        page!!
        assertEquals("Король и Шут", page.artist)
        assertEquals("Лесник", page.title)
        assertTrue(page.text.contains("[Вступление]:"))
        assertTrue(page.text.contains("Am"))
        assertTrue(page.text.contains("F"))
        assertTrue(page.text.contains("Замученный дорогой,"))
        assertFalse(page.text.contains("<div"))
        assertEquals("Am", page.key)
    }
}
