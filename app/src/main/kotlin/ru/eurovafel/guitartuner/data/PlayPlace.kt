package ru.eurovafel.guitartuner.data

/**
 * Место, где пользователь играл.
 *
 * Точки в радиусе 300 метров считаются одним местом: на карте это одна
 * гитара, а все песни складываются в [history] (см. [PlayVisit]).
 * [visitCount] и [lastSong] — краткая сводка для маркера.
 */
data class PlayPlace(
    val id: Long = 0L,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val visitCount: Int = 0,
    val lastSong: String? = null,
)

/** Одна запись истории: какую песню играли в месте и когда. */
data class PlayVisit(
    val id: Long = 0L,
    val placeId: Long,
    val songTitle: String? = null,
    val playedAt: Long = 0L,
)
