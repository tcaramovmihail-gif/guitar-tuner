package ru.eurovafel.guitartuner.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Локальная база мест игры и истории песен.
 *
 * Версия 2 добавила `updated_at` и отдельную таблицу визитов: одно место
 * хранит все песни, которые в нём играли, а не только последнюю.
 * Миграция сохраняет данные пользователя.
 */
class PlayPlaceDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $PLACES (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                song_title TEXT,
                accuracy REAL NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_${PLACES}_updated ON $PLACES(updated_at DESC)")
        createVisitsTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL(
                "ALTER TABLE $PLACES ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0",
            )
            db.execSQL("UPDATE $PLACES SET updated_at = created_at WHERE updated_at = 0")
            createVisitsTable(db)
            // Старая единственная песня становится первой записью истории.
            db.execSQL(
                """
                INSERT INTO $VISITS (place_id, song_title, played_at)
                SELECT id, song_title, created_at FROM $PLACES
                """.trimIndent(),
            )
        }
    }

    fun readAll(): List<PlayPlace> {
        val places = mutableListOf<PlayPlace>()
        val sql = """
            SELECT p.id, p.latitude, p.longitude, p.created_at, p.updated_at,
                (SELECT COUNT(*) FROM $VISITS v WHERE v.place_id = p.id) AS visit_count,
                (SELECT v.song_title FROM $VISITS v WHERE v.place_id = p.id
                    ORDER BY v.played_at DESC, v.id DESC LIMIT 1) AS last_song
            FROM $PLACES p
            ORDER BY p.updated_at DESC, p.id DESC
        """.trimIndent()

        readableDatabase.rawQuery(sql, null).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val latitudeIndex = cursor.getColumnIndexOrThrow("latitude")
            val longitudeIndex = cursor.getColumnIndexOrThrow("longitude")
            val createdIndex = cursor.getColumnIndexOrThrow("created_at")
            val updatedIndex = cursor.getColumnIndexOrThrow("updated_at")
            val countIndex = cursor.getColumnIndexOrThrow("visit_count")
            val lastSongIndex = cursor.getColumnIndexOrThrow("last_song")

            while (cursor.moveToNext()) {
                places += PlayPlace(
                    id = cursor.getLong(idIndex),
                    latitude = cursor.getDouble(latitudeIndex),
                    longitude = cursor.getDouble(longitudeIndex),
                    createdAt = cursor.getLong(createdIndex),
                    updatedAt = cursor.getLong(updatedIndex),
                    visitCount = cursor.getInt(countIndex),
                    lastSong = if (cursor.isNull(lastSongIndex)) {
                        null
                    } else {
                        cursor.getString(lastSongIndex)
                    },
                )
            }
        }
        return places
    }

    fun readHistory(placeId: Long): List<PlayVisit> {
        val visits = mutableListOf<PlayVisit>()
        readableDatabase.query(
            VISITS,
            VISIT_COLUMNS,
            "place_id = ?",
            arrayOf(placeId.toString()),
            null,
            null,
            "played_at DESC, id DESC",
        ).use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow("id")
            val placeIndex = cursor.getColumnIndexOrThrow("place_id")
            val titleIndex = cursor.getColumnIndexOrThrow("song_title")
            val playedIndex = cursor.getColumnIndexOrThrow("played_at")

            while (cursor.moveToNext()) {
                visits += PlayVisit(
                    id = cursor.getLong(idIndex),
                    placeId = cursor.getLong(placeIndex),
                    songTitle = if (cursor.isNull(titleIndex)) {
                        null
                    } else {
                        cursor.getString(titleIndex)
                    },
                    playedAt = cursor.getLong(playedIndex),
                )
            }
        }
        return visits
    }

    fun insertPlace(place: PlayPlace): Long = writableDatabase.insert(
        PLACES,
        null,
        ContentValues().apply {
            put("latitude", place.latitude)
            put("longitude", place.longitude)
            put("song_title", place.lastSong)
            put("accuracy", 0f)
            put("created_at", if (place.createdAt > 0) place.createdAt else System.currentTimeMillis())
            put("updated_at", if (place.updatedAt > 0) place.updatedAt else System.currentTimeMillis())
        },
    )

    fun insertVisit(visit: PlayVisit): Long = writableDatabase.insert(
        VISITS,
        null,
        ContentValues().apply {
            put("place_id", visit.placeId)
            put("song_title", visit.songTitle)
            put(
                "played_at",
                if (visit.playedAt > 0) visit.playedAt else System.currentTimeMillis(),
            )
        },
    )

    fun touchPlace(placeId: Long, updatedAt: Long) {
        writableDatabase.update(
            PLACES,
            ContentValues().apply { put("updated_at", updatedAt) },
            "id = ?",
            arrayOf(placeId.toString()),
        )
    }

    /** Удаляет место вместе со всей историей. */
    fun deletePlace(placeId: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(VISITS, "place_id = ?", arrayOf(placeId.toString()))
            db.delete(PLACES, "id = ?", arrayOf(placeId.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun createVisitsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $VISITS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                place_id INTEGER NOT NULL,
                song_title TEXT,
                played_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_${VISITS}_place ON $VISITS(place_id, played_at DESC)",
        )
    }

    private companion object {
        const val DATABASE_NAME = "play_places.db"
        const val DATABASE_VERSION = 2
        const val PLACES = "play_places"
        const val VISITS = "play_visits"
        val VISIT_COLUMNS = arrayOf("id", "place_id", "song_title", "played_at")
    }
}
