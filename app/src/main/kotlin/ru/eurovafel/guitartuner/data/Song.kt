package ru.eurovafel.guitartuner.data

/**
 * Песня в локальной библиотеке. [id] стабилен: для импортированных песен это
 * исполнитель и название, поэтому повторный импорт заменяет версию, а не плодит дубли.
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val bpm: Int,
    val chords: String,
    val key: String? = null,
    val sourceUrl: String? = null,
    val savedAt: Long = 0L,
    val favorite: Boolean = false,
)

/** Ключ для сравнения песен без учёта регистра и лишних пробелов. */
fun songKey(artist: String, title: String): String =
    artist.trim().lowercase() + "\u0000" + title.trim().lowercase()

fun Song.songKey(): String = songKey(artist, title)
