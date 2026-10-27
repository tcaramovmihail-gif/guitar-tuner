package ru.eurovafel.guitartuner.data

import android.content.Context
import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Результат записи места: UI должен сказать, что произошло. */
sealed interface PlaceRecordResult {
    /** Новая гитара на карте. */
    data class Created(val place: PlayPlace) : PlaceRecordResult

    /** Песня добавлена в историю уже отмеченного места. */
    data class Merged(val place: PlayPlace, val songTitle: String?) : PlaceRecordResult

    data object Duplicate : PlaceRecordResult
    data object NoPermission : PlaceRecordResult
    data object NoLocation : PlaceRecordResult
}

/**
 * Места игры: хранение и запись по текущей геолокации.
 *
 * Радиус объединения — 300 метров: прогулка по парку не создаёт десятки
 * гитар, а история песен остаётся у одного места.
 */
class PlayPlaceRepository(
    context: Context,
    private val locationReader: LocationReader = LocationReader(context.applicationContext),
) {
    private val database = PlayPlaceDatabase(context.applicationContext)

    private val _places = MutableStateFlow<List<PlayPlace>>(emptyList())
    val places: StateFlow<List<PlayPlace>> = _places.asStateFlow()

    fun hasLocationPermission(): Boolean = locationReader.hasPermission()

    suspend fun currentLocation(): Location? = locationReader.currentLocation()

    suspend fun refresh() = withContext(Dispatchers.IO) {
        _places.value = database.readAll()
    }

    suspend fun history(placeId: Long): List<PlayVisit> = withContext(Dispatchers.IO) {
        database.readHistory(placeId)
    }

    /**
     * Записывает песню в ближайшее место в радиусе 300 м или создаёт новое.
     *
     * Повторное нажатие «Играть» на ту же песню в течение 15 минут не плодит
     * записи: пауза и продолжение — одна игра, а не новая.
     */
    suspend fun recordCurrent(songTitle: String?): PlaceRecordResult {
        if (!locationReader.hasPermission()) return PlaceRecordResult.NoPermission
        val location = locationReader.currentLocation() ?: return PlaceRecordResult.NoLocation

        return withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cleanTitle = songTitle?.trim()?.takeIf { it.isNotEmpty() }
            val nearest = database.readAll()
                .map { place ->
                    place to distanceMeters(
                        place.latitude,
                        place.longitude,
                        location.latitude,
                        location.longitude,
                    )
                }
                .filter { (_, distance) -> distance <= MERGE_RADIUS_METERS }
                .minByOrNull { (_, distance) -> distance }
                ?.first

            if (nearest != null) {
                val lastVisit = database.readHistory(nearest.id).firstOrNull()
                if (lastVisit != null &&
                    sameSong(lastVisit.songTitle, cleanTitle) &&
                    now - lastVisit.playedAt < DUPLICATE_WINDOW_MILLIS
                ) {
                    return@withContext PlaceRecordResult.Duplicate
                }
                database.insertVisit(
                    PlayVisit(
                        placeId = nearest.id,
                        songTitle = cleanTitle,
                        playedAt = now,
                    ),
                )
                database.touchPlace(nearest.id, now)
                _places.value = database.readAll()
                val updated = _places.value.firstOrNull { it.id == nearest.id } ?: nearest
                return@withContext PlaceRecordResult.Merged(updated, cleanTitle)
            }

            val place = PlayPlace(
                latitude = location.latitude,
                longitude = location.longitude,
                createdAt = now,
                updatedAt = now,
                lastSong = cleanTitle,
            )
            val id = database.insertPlace(place)
            database.insertVisit(
                PlayVisit(
                    placeId = id,
                    songTitle = cleanTitle,
                    playedAt = now,
                ),
            )
            _places.value = database.readAll()
            PlaceRecordResult.Created(_places.value.firstOrNull { it.id == id } ?: place.copy(id = id))
        }
    }

    suspend fun delete(place: PlayPlace) = withContext(Dispatchers.IO) {
        database.deletePlace(place.id)
        _places.value = database.readAll()
    }

    private fun sameSong(first: String?, second: String?): Boolean {
        if (first == null || second == null) return first == second
        return first.trim().equals(second.trim(), ignoreCase = true)
    }

    private fun distanceMeters(
        firstLatitude: Double,
        firstLongitude: Double,
        secondLatitude: Double,
        secondLongitude: Double,
    ): Float {
        val result = FloatArray(1)
        Location.distanceBetween(
            firstLatitude,
            firstLongitude,
            secondLatitude,
            secondLongitude,
            result,
        )
        return result[0]
    }

    private companion object {
        const val MERGE_RADIUS_METERS = 300f
        const val DUPLICATE_WINDOW_MILLIS = 15 * 60 * 1000L
    }
}
