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
                saved_at INTEGER NOT NULL,
                favorite INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_${TABLE}_artist ON $TABLE(artist COLLATE NOCASE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Данные пользователя (импортированные песни) не должны теряться.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
        }
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
            val favoriteIndex = cursor.getColumnIndexOrThrow("favorite")

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
                    favorite = cursor.getInt(favoriteIndex) != 0,
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
        val favorite = favoriteOf(song.id) ?: song.favorite
        writableDatabase.insertWithOnConflict(
            TABLE,
            null,
            toValues(song.copy(favorite = favorite)),
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun upsertAll(songs: List<Song>) {
        if (songs.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            // Пересохранённая из сети песня не должна терять сердечко.
            val favorites = favoritesById(db)
            songs.forEach { song ->
                db.insertWithOnConflict(
                    TABLE,
                    null,
                    toValues(song.copy(favorite = favorites[song.id] ?: song.favorite)),
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

    fun deleteAll(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val placeholders = ids.joinToString(",") { "?" }
        writableDatabase.delete(TABLE, "id IN ($placeholders)", ids.toTypedArray())
    }

    fun setFavorite(id: String, favorite: Boolean) {
        val values = ContentValues().apply { put("favorite", if (favorite) 1 else 0) }
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(id))
    }

    private fun favoriteOf(id: String): Boolean? =
        readableDatabase.query(
            TABLE,
            arrayOf("favorite"),
            "id = ?",
            arrayOf(id),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) != 0 else null
        }

    private fun favoritesById(db: SQLiteDatabase): Map<String, Boolean> {
        val result = mutableMapOf<String, Boolean>()
        db.query(TABLE, arrayOf("id", "favorite"), null, null, null, null, null).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val favoriteIndex = cursor.getColumnIndexOrThrow("favorite")
            while (cursor.moveToNext()) {
                result[cursor.getString(idIndex)] = cursor.getInt(favoriteIndex) != 0
            }
        }
        return result
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
        put("favorite", if (song.favorite) 1 else 0)
    }

    private companion object {
        const val DATABASE_NAME = "songs.db"
        const val DATABASE_VERSION = 2
        const val TABLE = "songs"
        val COLUMNS = arrayOf(
            "id", "title", "artist", "bpm", "chords", "song_key", "source_url", "saved_at",
            "favorite",
        )
    }
}
