package ru.eurovafel.guitartuner.data

import ru.eurovafel.guitartuner.catalog.SongPage
import ru.eurovafel.guitartuner.model.ChordTransposer

/** Превращает страницу AmDm в песню библиотеки. */
fun SongPage.toSong(defaultBpm: Int = 90): Song {
    val cleanArtist = artist.trim().ifEmpty { "Без исполнителя" }
    val cleanTitle = title.trim().ifEmpty { "Без названия" }
    return Song(
        id = buildString {
            append("amdm:")
            append(cleanArtist.lowercase())
            append(':')
            append(cleanTitle.lowercase())
        },
        title = cleanTitle,
        artist = cleanArtist,
        bpm = defaultBpm,
        chords = text,
        key = key ?: ChordTransposer.detectKey(text),
        sourceUrl = sourceUrl,
        savedAt = System.currentTimeMillis(),
    )
}
