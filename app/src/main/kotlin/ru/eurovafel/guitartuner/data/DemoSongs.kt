package ru.eurovafel.guitartuner.data

object DemoSongs {
    val all: List<Song> = listOf(
        Song(
            id = "demo-1",
            title = "Разминка: Am–F–C–G",
            artist = "Учебный пример",
            bpm = 90,
            chords = """
                Куплет 1
                Am              F
                (текст песни — здесь)
                C               G
                (текст песни — здесь)

                Припев
                Am              F
                (текст песни — здесь)
                C               G
                (текст песни — здесь)
            """.trimIndent(),
        ),
        Song(
            id = "demo-2",
            title = "Медленная: Em–C–G–D",
            artist = "Учебный пример",
            bpm = 72,
            chords = """
                Вступление
                Em              C
                (текст песни — здесь)
                G               D
                (текст песни — здесь)

                Куплет 1
                Em              C
                (текст песни — здесь)
                G               D
                (текст песни — здесь)
            """.trimIndent(),
        ),
        Song(
            id = "demo-3",
            title = "Быстрая: D–A–Bm–G",
            artist = "Учебный пример",
            bpm = 128,
            chords = """
                Куплет 1
                D               A
                (текст песни — здесь)
                Bm              G
                (текст песни — здесь)

                Припев
                D               A
                (текст песни — здесь)
                Bm              G
                (текст песни — здесь)
            """.trimIndent(),
        ),
    )
}
