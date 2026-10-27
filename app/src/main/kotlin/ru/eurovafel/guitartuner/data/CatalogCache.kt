package ru.eurovafel.guitartuner.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.eurovafel.guitartuner.catalog.CatalogSource
import ru.eurovafel.guitartuner.catalog.RemoteArtist
import ru.eurovafel.guitartuner.catalog.RemoteSong
import ru.eurovafel.guitartuner.catalog.SearchResult

/** Результат поиска, сохранённый на устройстве. */
data class CachedSearch(
    val result: SearchResult,
    val savedAt: Long,
)

/**
 * Офлайн-кэш поиска по каталогам.
 *
 * В метро и на репетиции сеть пропадает, а список уже найденных песен
 * остаётся полезным. Кэш — вспомогательный: свежий ответ всегда важнее.
 */
class CatalogCache(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    /** @return true, если результат удалось сохранить. */
    fun saveSearch(source: CatalogSource, query: String, result: SearchResult): Boolean =
        runCatching {
            val key = key(source, query)
            val savedAt = System.currentTimeMillis()
            val json = JSONObject().apply {
                put("savedAt", savedAt)
                put("songs", JSONArray().apply {
                    result.songs.forEach { song ->
                        put(
                            JSONObject().apply {
                                put("title", song.title)
                                put("artist", song.artist)
                                put("url", song.url)
                                song.artistUrl?.let { put("artistUrl", it) }
                                put("views", song.views)
                                put("verified", song.verified)
                                put("sourceName", song.sourceName)
                            },
                        )
                    }
                })
                put("artists", JSONArray().apply {
                    result.artists.forEach { artist ->
                        put(
                            JSONObject().apply {
                                put("name", artist.name)
                                put("url", artist.url)
                                artist.songCount?.let { put("songCount", it) }
                            },
                        )
                    }
                })
            }

            val index = readIndex().toMutableMap()
            index[key] = savedAt
            while (index.size > MAX_ENTRIES) {
                val oldest = index.minByOrNull { it.value }?.key ?: break
                index.remove(oldest)
                prefs.edit().remove(oldest).apply()
            }

            prefs.edit()
                .putString(key, json.toString())
                .putString(KEY_INDEX, JSONObject(index as Map<*, *>).toString())
                .apply()
            true
        }.getOrDefault(false)

    fun loadSearch(source: CatalogSource, query: String): CachedSearch? = runCatching {
        val key = key(source, query)
        val raw = prefs.getString(key, null) ?: return null
        val json = JSONObject(raw)
        val savedAt = json.optLong("savedAt", 0L)
        val songs = json.optJSONArray("songs").orEmpty().map { item ->
            val song = item as JSONObject
            RemoteSong(
                title = song.getString("title"),
                artist = song.getString("artist"),
                url = song.getString("url"),
                artistUrl = song.optString("artistUrl").takeIf { it.isNotBlank() },
                views = song.optLong("views", 0L),
                verified = song.optBoolean("verified", false),
                sourceName = song.optString("sourceName", source.title),
            )
        }
        val artists = json.optJSONArray("artists").orEmpty().map { item ->
            val artist = item as JSONObject
            RemoteArtist(
                name = artist.getString("name"),
                url = artist.getString("url"),
                songCount = artist.optInt("songCount", 0).takeIf { it > 0 },
            )
        }
        CachedSearch(SearchResult(songs = songs, artists = artists), savedAt)
    }.getOrNull()

    /** Сколько запросов лежит в кэше. */
    fun size(): Int = readIndex().size

    fun clear() {
        val editor = prefs.edit()
        readIndex().keys.forEach { editor.remove(it) }
        editor.remove(KEY_INDEX).apply()
    }

    private fun key(source: CatalogSource, query: String): String =
        "search_${source.name}_${query.trim().lowercase()}"

    private fun readIndex(): Map<String, Long> = runCatching {
        val raw = prefs.getString(KEY_INDEX, null) ?: return emptyMap()
        val json = JSONObject(raw)
        buildMap {
            json.keys().forEach { key -> put(key, json.optLong(key, 0L)) }
        }
    }.getOrDefault(emptyMap())

    private fun JSONArray?.orEmpty(): List<Any> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                get(index)?.let { add(it) }
            }
        }
    }

    private companion object {
        const val PREFERENCES = "catalog_cache"
        const val KEY_INDEX = "search_index"
        const val MAX_ENTRIES = 40
    }
}
