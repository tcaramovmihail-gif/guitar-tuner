package ru.eurovafel.guitartuner.ui.songs

import ru.eurovafel.guitartuner.data.Song

/** Порядок песен в библиотеке. Выбор сохраняется между запусками. */
enum class SongSort(val title: String) {
    ALPHABET("По алфавиту"),
    ARTIST("По исполнителю"),
    RECENT("Недавние"),
    FREQUENT("Часто играю"),
}

/** Что показывать в библиотеке. */
enum class SongFilter(val title: String) {
    ALL("Все"),
    CUSTOM("Свои"),
    CATALOG("Из каталога"),
}

fun List<Song>.orderedBy(sort: SongSort): List<Song> = when (sort) {
    SongSort.ALPHABET -> sortedWith(
        compareBy({ it.title.lowercase() }, { it.artist.lowercase() }),
    )
    SongSort.ARTIST -> sortedWith(
        compareBy({ it.artist.lowercase() }, { it.title.lowercase() }),
    )
    SongSort.RECENT -> sortedWith(
        compareByDescending<Song> { it.lastPlayedAt }
            .thenByDescending { it.savedAt }
            .thenBy { it.title.lowercase() },
    )
    SongSort.FREQUENT -> sortedWith(
        compareByDescending<Song> { it.playCount }
            .thenByDescending { it.lastPlayedAt }
            .thenBy { it.title.lowercase() },
    )
}

fun Song.matchesFilter(filter: SongFilter): Boolean = when (filter) {
    SongFilter.ALL -> true
    SongFilter.CUSTOM -> source == CUSTOM_SOURCE
    SongFilter.CATALOG -> source != CUSTOM_SOURCE
}

fun List<Song>.filteredBy(filter: SongFilter): List<Song> =
    if (filter == SongFilter.ALL) this else filter { it.matchesFilter(filter) }

/** Метка источника для песен, созданных вручную. */
const val CUSTOM_SOURCE = "Своя"
