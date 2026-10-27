package ru.eurovafel.guitartuner.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Единственная точка доступа к локальной библиотеке. */
class SongRepository(context: Context) {

    private val appContext = context.applicationContext
    private val database = SongDatabase(appContext)
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    suspend fun seedDemoSongsIfNeeded() = withContext(Dispatchers.IO) {
        if (preferences.getBoolean(KEY_DEMO_SEEDED, false)) return@withContext

        if (database.count() == 0) {
            val now = System.currentTimeMillis()
            database.upsertAll(
                DemoSongs.all.mapIndexed { index, song ->
                    song.copy(savedAt = now - index)
                },
            )
        }
        preferences.edit().putBoolean(KEY_DEMO_SEEDED, true).apply()
    }

    suspend fun load() = withContext(Dispatchers.IO) {
        _songs.value = database.readAll()
    }

    suspend fun save(song: Song) = saveAll(listOf(song))

    suspend fun saveAll(songs: List<Song>) = withContext(Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext
        database.upsertAll(songs)
        _songs.value = database.readAll()
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        database.delete(id)
        _songs.value = database.readAll()
    }

    suspend fun deleteAll(ids: Collection<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        database.deleteAll(ids)
        _songs.value = database.readAll()
    }

    suspend fun setFavorite(id: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        database.setFavorite(id, favorite)
        _songs.value = database.readAll()
    }

    fun snapshot(): List<Song> = _songs.value

    private companion object {
        const val PREFERENCES = "songs_preferences"
        const val KEY_DEMO_SEEDED = "demo_seeded"
    }
}
