package ru.eurovafel.guitartuner.catalog

import java.io.IOException

/**
 * Клиент akkords.pro: поиск по песням и загрузка страницы подбора.
 *
 * Список исполнителей сайт отдаёт иначе, поэтому импорт «все песни артиста»
 * для него не поддерживается — только поиск и сохранение отдельной песни.
 */
class AkkordsProClient {
    fun search(query: String): SearchResult {
        val html = HtmlHttp.get(AkkordsProParser.searchUrl(query))
        return SearchResult(songs = AkkordsProParser.parseSearchSongs(html))
    }

    fun song(songUrl: String): SongPage {
        val html = HtmlHttp.get(songUrl)
        return AkkordsProParser.parseSong(html, songUrl)
            ?: throw IOException("Не удалось разобрать страницу песни: $songUrl")
    }
}
