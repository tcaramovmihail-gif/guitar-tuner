package ru.eurovafel.guitartuner.catalog

import java.io.IOException

/** Источник аккордов. Поиск идёт только по выбранному, а не по всем сразу. */
enum class CatalogSource(
    val title: String,
    val homeUrl: String,
) {
    AKKORDS("Akkords.pro", "https://akkords.pro"),
    AMDM("AmDm", "https://amdm.ru"),
    AMDM501("AmDm 501", "https://501.amdm.ru"),
    FIVELAD("5Lad", "https://top.5lad.org"),
}

/**
 * Единая точка поиска по каталогам.
 *
 * Источник выбирает пользователь: запрос уходит только на него. Так поиск
 * быстрее, а результат не смешивает разные редакции одного подбора.
 * Все страницы открываются внутри приложения — в браузер ничего не уводим.
 */
class SongCatalog(
    private val amdm: AmDmClient = AmDmClient(),
    private val amdm501: AmDmClient = AmDmClient(
        baseUrl = "https://501.amdm.ru",
        sourceName = "AmDm 501",
    ),
    private val akkords: AkkordsProClient = AkkordsProClient(),
    private val fiveLad: FiveLadClient = FiveLadClient(),
) {

    fun search(query: String, source: CatalogSource): SearchResult = try {
        when (source) {
            CatalogSource.AKKORDS -> akkords.search(query)
            CatalogSource.AMDM -> amdm.search(query)
            CatalogSource.AMDM501 -> amdm501.search(query)
            CatalogSource.FIVELAD -> fiveLad.search(query)
        }
    } catch (error: IOException) {
        throw IOException("${source.title}: ${error.message ?: "нет ответа"}", error)
    }

    fun artistSongs(artistUrl: String): ArtistSongs =
        if (isAkkords(artistUrl)) {
            throw IOException("Akkords.pro не поддерживает импорт всех песен исполнителя")
        } else if (isAmdm501(artistUrl)) {
            amdm501.artistSongs(artistUrl)
        } else if (isFiveLad(artistUrl)) {
            fiveLad.artistSongs(artistUrl)
        } else {
            amdm.artistSongs(artistUrl)
        }

    fun song(songUrl: String): SongPage = when {
        isAkkords(songUrl) -> akkords.song(songUrl)
        isAmdm501(songUrl) -> amdm501.song(songUrl)
        isFiveLad(songUrl) -> fiveLad.song(songUrl)
        else -> amdm.song(songUrl)
    }

    private fun isAkkords(url: String): Boolean = url.contains("akkords.pro")

    private fun isAmdm501(url: String): Boolean = url.contains("501.amdm.ru")

    private fun isFiveLad(url: String): Boolean =
        url.contains("5lad.org") || url.contains("5lad.net")
}
