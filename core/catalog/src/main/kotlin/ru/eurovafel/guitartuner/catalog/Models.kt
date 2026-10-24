package ru.eurovafel.guitartuner.catalog

/** Песня, найденная на AmDm, но ещё не загруженная в приложение. */
data class RemoteSong(
    val title: String,
    val artist: String,
    val url: String,
    val artistUrl: String? = null,
    val views: Long = 0L,
    val verified: Boolean = false,
)

/** Исполнитель из поиска или со страницы исполнителя. */
data class RemoteArtist(
    val name: String,
    val url: String,
    val songCount: Int? = null,
)

/** Загруженная страница песни с уже очищенным текстом подбора. */
data class SongPage(
    val title: String,
    val artist: String,
    val text: String,
    val sourceUrl: String,
    val key: String? = null,
)

data class ArtistSongs(
    val artist: String,
    val artistUrl: String,
    val songs: List<RemoteSong>,
)

data class SearchResult(
    val songs: List<RemoteSong> = emptyList(),
    val artists: List<RemoteArtist> = emptyList(),
)
