package ru.eurovafel.guitartuner.ui.songs

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import ru.eurovafel.guitartuner.catalog.CatalogSource
import ru.eurovafel.guitartuner.catalog.RemoteArtist
import ru.eurovafel.guitartuner.catalog.RemoteSong
import ru.eurovafel.guitartuner.catalog.SongCatalog
import ru.eurovafel.guitartuner.data.Song
import ru.eurovafel.guitartuner.data.SongRepository
import ru.eurovafel.guitartuner.data.songKey
import ru.eurovafel.guitartuner.data.toSong

data class ArtistFolder(
    val artist: String,
    val songs: List<Song>,
)

data class ImportProgress(
    val artistNumber: Int,
    val artistsTotal: Int,
    val artistName: String,
    val total: Int = 0,
    val done: Int = 0,
    val saved: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val currentTitle: String = "",
    val finished: Boolean = false,
) {
    val percent: Float
        get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)

    val indeterminate: Boolean get() = total <= 0
}

data class SongsUiState(
    val loading: Boolean = true,
    val songs: List<Song> = emptyList(),
    val artists: List<ArtistFolder> = emptyList(),
    val savedKeys: Set<String> = emptySet(),
    val savedUrls: Set<String> = emptySet(),
    val openedArtist: String? = null,
    val favoritesOpen: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val localQuery: String = "",
    val query: String = "",
    val source: CatalogSource = CatalogSource.AMDM,
    val searching: Boolean = false,
    val searched: Boolean = false,
    val remoteSongs: List<RemoteSong> = emptyList(),
    val remoteArtists: List<RemoteArtist> = emptyList(),
    val loadingUrl: String? = null,
    val importProgress: ImportProgress? = null,
    val message: String? = null,
    val error: String? = null,
) {
    val openedFolder: ArtistFolder?
        get() = artists.firstOrNull { it.artist == openedArtist }

    /** Песни с сердечком — отдельная папка «Избранное». */
    val favorites: List<Song>
        get() = songs
            .filter { it.favorite }
            .sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))

    val selectedCount: Int get() = selectedIds.size

    /** Совпадения по уже сохранённым песням: поиск идёт без сети. */
    val localMatches: List<Song>
        get() {
            val needle = localQuery.trim()
            if (needle.isEmpty()) return emptyList()
            return songs
                .filter {
                    it.title.contains(needle, ignoreCase = true) ||
                        it.artist.contains(needle, ignoreCase = true)
                }
                .sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
        }

    val localSearching: Boolean get() = localQuery.isNotBlank()

    fun isSaved(remote: RemoteSong): Boolean =
        remote.url in savedUrls || songKey(remote.artist, remote.title) in savedKeys
}

class SongsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SongRepository(application)
    private val client = SongCatalog()

    private val _state = MutableStateFlow(SongsUiState())
    val state: StateFlow<SongsUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var importJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching {
                repository.seedDemoSongsIfNeeded()
                repository.load()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        loading = false,
                        error = "Не удалось открыть библиотеку: ${error.userMessage()}",
                    )
                }
            }
        }

        viewModelScope.launch {
            repository.songs.collect { songs ->
                _state.update { current ->
                    val existingIds = songs.mapTo(HashSet()) { it.id }
                    current.copy(
                        loading = false,
                        songs = songs,
                        artists = songs.groupBy { it.artist }
                            .map { (artist, artistSongs) ->
                                ArtistFolder(
                                    artist = artist,
                                    songs = artistSongs.sortedBy { it.title.lowercase() },
                                )
                            }
                            .sortedBy { it.artist.lowercase() },
                        savedKeys = songs.map { it.songKey() }.toSet(),
                        savedUrls = songs.mapNotNull { it.sourceUrl }.toSet(),
                        selectedIds = current.selectedIds.filterTo(HashSet()) { it in existingIds },
                    )
                }
            }
        }
    }

    /** Поиск по локальной библиотеке, без запросов в сеть. */
    fun onLocalQueryChange(query: String) {
        _state.update { it.copy(localQuery = query) }
    }

    fun clearLocalQuery() {
        _state.update { it.copy(localQuery = "") }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    /** Смена источника очищает прошлые результаты: они от другого сайта. */
    fun selectSource(source: CatalogSource) {
        if (_state.value.source == source) return
        searchJob?.cancel()
        _state.update {
            it.copy(
                source = source,
                searching = false,
                searched = false,
                remoteSongs = emptyList(),
                remoteArtists = emptyList(),
            )
        }
    }

    fun search() {
        val state = _state.value
        val query = state.query.trim()
        if (query.isEmpty()) return
        val source = state.source

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(searching = true, searched = true, error = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching { client.search(query, source) }
            }
            result.onSuccess { searchResult ->
                _state.update {
                    it.copy(
                        searching = false,
                        remoteSongs = searchResult.songs,
                        remoteArtists = searchResult.artists,
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        searching = false,
                        remoteSongs = emptyList(),
                        remoteArtists = emptyList(),
                        error = "Поиск не удался: ${error.userMessage()}",
                    )
                }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.update {
            it.copy(
                query = "",
                searching = false,
                searched = false,
                remoteSongs = emptyList(),
                remoteArtists = emptyList(),
            )
        }
    }

    fun openArtist(artist: String) {
        _state.update {
            it.copy(
                openedArtist = artist,
                favoritesOpen = false,
                localQuery = "",
                selectionMode = false,
                selectedIds = emptySet(),
            )
        }
    }

    fun closeArtist() {
        _state.update {
            it.copy(openedArtist = null, selectionMode = false, selectedIds = emptySet())
        }
    }

    fun openFavorites() {
        _state.update {
            it.copy(
                openedArtist = null,
                favoritesOpen = true,
                localQuery = "",
                selectionMode = false,
                selectedIds = emptySet(),
            )
        }
    }

    fun closeFavorites() {
        _state.update {
            it.copy(favoritesOpen = false, selectionMode = false, selectedIds = emptySet())
        }
    }

    /** Сердечко в карточке: песня сразу появляется в папке «Избранное». */
    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            runCatching { repository.setFavorite(song.id, !song.favorite) }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = "Не удалось изменить избранное: ${error.userMessage()}")
                    }
                }
        }
    }

    fun setSelectionMode(enabled: Boolean) {
        _state.update {
            it.copy(selectionMode = enabled, selectedIds = if (enabled) it.selectedIds else emptySet())
        }
    }

    fun toggleSelected(song: Song) {
        _state.update { current ->
            val selected = current.selectedIds.toMutableSet()
            if (!selected.add(song.id)) selected.remove(song.id)
            current.copy(
                selectionMode = true,
                selectedIds = selected,
            )
        }
    }

    fun selectAll(songs: List<Song>) {
        _state.update { it.copy(selectionMode = true, selectedIds = songs.mapTo(HashSet()) { song -> song.id }) }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedIds = emptySet()) }
    }

    /** Групповое удаление выбранных песен — только после подтверждения в UI. */
    fun deleteSelected() {
        val ids = _state.value.selectedIds
        if (ids.isEmpty()) return
        viewModelScope.launch {
            runCatching { repository.deleteAll(ids) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            selectionMode = false,
                            selectedIds = emptySet(),
                            message = "Удалено: ${ids.size}",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = "Не удалось удалить: ${error.userMessage()}")
                    }
                }
        }
    }

    /**
     * Загружает страницу песни и сохраняет её в библиотеку.
     * [openAfter] = true — открыть текст; false — только сохранить.
     */
    fun loadRemoteSong(
        remote: RemoteSong,
        openAfter: Boolean,
        onOpened: (Song) -> Unit,
    ) {
        val existing = repository.snapshot().firstOrNull {
            it.sourceUrl == remote.url || it.songKey() == songKey(remote.artist, remote.title)
        }
        if (existing != null) {
            if (openAfter) {
                onOpened(existing)
            } else {
                _state.update { it.copy(message = "Уже в библиотеке: ${existing.title}") }
            }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(loadingUrl = remote.url, error = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching { client.song(remote.url) }
            }
            result.onSuccess { page ->
                val song = page.toSong()
                repository.save(song)
                _state.update {
                    it.copy(
                        loadingUrl = null,
                        message = "Сохранено: ${song.title}",
                    )
                }
                if (openAfter) onOpened(song)
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        loadingUrl = null,
                        error = "Не удалось загрузить песню: ${error.userMessage()}",
                    )
                }
            }
        }
    }

    fun importExamples() {
        startImport(
            listOf(
                RemoteArtist(
                    name = "Король и Шут",
                    url = "https://496.amdm.ru/akkordi/korol_i_shut/",
                    songCount = 635,
                ),
                RemoteArtist(
                    name = "Би-2",
                    url = "https://496.amdm.ru/akkordi/bi_2/",
                    songCount = 324,
                ),
            ),
        )
    }

    fun importArtist(artist: RemoteArtist) {
        startImport(listOf(artist))
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _state.update { it.copy(importProgress = null) }
    }

    fun dismissImportProgress() {
        _state.update { it.copy(importProgress = null) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    /** Удаление из локальной библиотеки: папка и поиск обновятся сами. */
    fun deleteSong(song: Song) {
        viewModelScope.launch {
            runCatching { repository.delete(song.id) }
                .onSuccess {
                    _state.update {
                        it.copy(message = "Удалено: ${song.title}")
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = "Не удалось удалить: ${error.userMessage()}")
                    }
                }
        }
    }

    private fun startImport(artists: List<RemoteArtist>) {
        if (importJob?.isActive == true || artists.isEmpty()) return

        importJob = viewModelScope.launch {
            _state.update { it.copy(error = null, message = null, importProgress = null) }
            var totalSaved = 0
            var totalSkipped = 0
            var totalFailed = 0

            try {
                repository.load()
                artists.forEachIndexed { index, artist ->
                    val counts = importArtist(index, artists.size, artist)
                    totalSaved += counts.saved
                    totalSkipped += counts.skipped
                    totalFailed += counts.failed
                }

                _state.update { current ->
                    current.copy(
                        importProgress = current.importProgress?.copy(
                            finished = true,
                            currentTitle = "",
                        ),
                        message = "Импорт завершён: сохранено $totalSaved, " +
                            "уже было $totalSkipped, ошибок $totalFailed",
                    )
                }
            } catch (cancelled: CancellationException) {
                _state.update {
                    it.copy(importProgress = null, message = "Импорт отменён")
                }
                throw cancelled
            } catch (error: Throwable) {
                _state.update {
                    it.copy(
                        importProgress = null,
                        error = "Импорт не удался: ${error.userMessage()}",
                    )
                }
            } finally {
                importJob = null
            }
        }
    }

    private suspend fun importArtist(
        index: Int,
        artistsTotal: Int,
        artist: RemoteArtist,
    ): ImportCounts {
        val listing = withContext(Dispatchers.IO) { client.artistSongs(artist.url) }
        if (listing.songs.isEmpty()) {
            throw IllegalStateException("Не удалось получить список песен для «${artist.name}»")
        }

        val artistName = listing.artist.ifBlank { artist.name }
        _state.update {
            it.copy(
                importProgress = ImportProgress(
                    artistNumber = index + 1,
                    artistsTotal = artistsTotal,
                    artistName = artistName,
                    total = listing.songs.size,
                ),
            )
        }

        val lock = Mutex()
        val existing = repository.snapshot().map { it.songKey() }.toMutableSet()
        val buffer = mutableListOf<Song>()
        val semaphore = Semaphore(IMPORT_CONCURRENCY)

        var done = 0
        var saved = 0
        var skipped = 0
        var failed = 0

        coroutineScope {
            listing.songs.map { remote ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        if (!isActive) return@withPermit

                        val key = songKey(remote.artist.ifBlank { artistName }, remote.title)
                        val duplicate = lock.withLock { !existing.add(key) }
                        if (duplicate) {
                            lock.withLock {
                                done++
                                skipped++
                                publishProgress(done, saved, skipped, failed, remote.title)
                            }
                            return@withPermit
                        }

                        val page = runCatching { client.song(remote.url) }.getOrNull()
                        val song = page?.let { runCatching { it.toSong() }.getOrNull() }

                        lock.withLock {
                            if (song == null) {
                                failed++
                            } else {
                                buffer += song
                                saved++
                                if (buffer.size >= SAVE_BATCH_SIZE) {
                                    repository.saveAll(buffer.toList())
                                    buffer.clear()
                                }
                            }
                            done++
                            publishProgress(done, saved, skipped, failed, remote.title)
                        }

                        delay(IMPORT_REQUEST_DELAY_MS)
                    }
                }
            }.awaitAll()
        }

        lock.withLock {
            if (buffer.isNotEmpty()) {
                repository.saveAll(buffer.toList())
                buffer.clear()
            }
        }

        return ImportCounts(saved = saved, skipped = skipped, failed = failed)
    }

    private suspend fun publishProgress(
        done: Int,
        saved: Int,
        skipped: Int,
        failed: Int,
        currentTitle: String,
    ) {
        _state.update { current ->
            current.copy(
                importProgress = current.importProgress?.copy(
                    done = done,
                    saved = saved,
                    skipped = skipped,
                    failed = failed,
                    currentTitle = currentTitle,
                ),
            )
        }
    }

    private fun Throwable.userMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName

    private data class ImportCounts(
        val saved: Int,
        val skipped: Int,
        val failed: Int,
    )

    private companion object {
        const val IMPORT_CONCURRENCY = 4
        const val SAVE_BATCH_SIZE = 20
        const val IMPORT_REQUEST_DELAY_MS = 100L
    }
}
