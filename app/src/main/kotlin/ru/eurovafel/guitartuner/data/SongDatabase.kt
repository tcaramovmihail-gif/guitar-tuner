package ru.eurovafel.guitartuner.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Локальная база песен. SQLite достаточно: выборки простые, внешних ключей нет. */
class SongDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                bpm INTEGER NOT NULL,
                chords TEXT NOT NULL,
                song_key TEXT,
                source_url TEXT,
                saved_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_${TABLE}_artist ON $TABLE(artist COLLATE NOCASE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Первая версия схемы. Миграции появятся вместе с изменением структуры.
    }

    fun readAll(): List<Song> {
        val songs = mutableListOf<Song>()
        readableDatabase.query(
            TABLE,
            COLUMNS,
            null,
            null,
            null,
            null,
            "artist COLLATE NOCASE, title COLLATE NOCASE",
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val titleIndex = cursor.getColumnIndexOrThrow("title")
            val artistIndex = cursor.getColumnIndexOrThrow("artist")
            val bpmIndex = cursor.getColumnIndexOrThrow("bpm")
            val chordsIndex = cursor.getColumnIndexOrThrow("chords")
            val keyIndex = cursor.getColumnIndexOrThrow("song_key")
            val sourceIndex = cursor.getColumnIndexOrThrow("source_url")
            val savedIndex = cursor.getColumnIndexOrThrow("saved_at")

            while (cursor.moveToNext()) {
                songs += Song(
                    id = cursor.getString(idIndex),
                    title = cursor.getString(titleIndex),
                    artist = cursor.getString(artistIndex),
                    bpm = cursor.getInt(bpmIndex),
                    chords = cursor.getString(chordsIndex),
                    key = if (cursor.isNull(keyIndex)) null else cursor.getString(keyIndex),
                    sourceUrl = if (cursor.isNull(sourceIndex)) null else cursor.getString(sourceIndex),
                    savedAt = cursor.getLong(savedIndex),
                )
            }
        }
        return songs
    }

    fun count(): Int =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $TABLE", null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }

    fun upsert(song: Song) {
        writableDatabase.insertWithOnConflict(
            TABLE,
            null,
            toValues(song),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun upsertAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            songs.forEach { song ->
                db.insertWithOnConflict(
                    TABLE,
                    null,
                    toValues(song),
                    SQLiteDatabase.CONFLICT_REPLACE,
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun delete(id: String) {
        writableDatabase.delete(TABLE, "id = ?", arrayOf(id))
    }

    private fun toValues(song: Song): ContentValues = ContentValues().apply {
        put("id", song.id)
        put("title", song.title)
        put("artist", song.artist)
        put("bpm", song.bpm)
        put("chords", song.chords)
        put("song_key", song.key)
        put("source_url", song.sourceUrl)
        put("saved_at", if (song.savedAt > 0) song.savedAt else System.currentTimeMillis())
    }

    private companion object {
        const val DATABASE_NAME = "songs.db"
        const val DATABASE_VERSION = 1
        const val TABLE = "songs"
        val COLUMNS = arrayOf(
            "id", "title", "artist", "bpm", "chords", "song_key", "source_url", "saved_at",
        )
    }
}
