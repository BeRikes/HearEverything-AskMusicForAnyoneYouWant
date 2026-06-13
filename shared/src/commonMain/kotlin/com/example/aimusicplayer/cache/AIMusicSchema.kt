package com.example.aimusicplayer.cache

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema

/**
 * SQLite schema for the AIMusicPlayer cache + download tracking database.
 *
 * ## Version history
 * - **v1**: SongCache, FailedLookup
 * - **v2**: Added DownloadRecord table for persistent download tracking
 * - **v3**: Added displayTitle, displayArtist columns to DownloadRecord
 * - **v4**: Renamed displayTitle→realName, displayArtist→realArtist
 */
object AIMusicSchema : SqlSchema<QueryResult.Value<Unit>> {

    override val version: Long = 4

    override fun create(driver: SqlDriver): QueryResult.Value<Unit> {
        driver.execute(identifier = null, sql = CREATE_SONG_CACHE, parameters = 0)
        driver.execute(identifier = null, sql = CREATE_FAILED_LOOKUP, parameters = 0)
        driver.execute(identifier = null, sql = CREATE_DOWNLOAD_RECORD, parameters = 0)
        return QueryResult.Value(Unit)
    }

    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion
    ): QueryResult.Value<Unit> {
        // v1 → v2: add DownloadRecord table if missing
        if (oldVersion < 2) {
            driver.execute(identifier = null, sql = CREATE_DOWNLOAD_RECORD, parameters = 0)
        }
        // v2 → v3: add displayTitle and displayArtist columns
        if (oldVersion < 3) {
            driver.execute(identifier = null, sql = "ALTER TABLE DownloadRecord ADD COLUMN displayTitle TEXT", parameters = 0)
            driver.execute(identifier = null, sql = "ALTER TABLE DownloadRecord ADD COLUMN displayArtist TEXT", parameters = 0)
        }
        // v3 → v4: rename displayTitle/displayArtist → realName/realArtist
        if (oldVersion < 4) {
            driver.execute(identifier = null, sql = "ALTER TABLE DownloadRecord RENAME COLUMN displayTitle TO realName", parameters = 0)
            driver.execute(identifier = null, sql = "ALTER TABLE DownloadRecord RENAME COLUMN displayArtist TO realArtist", parameters = 0)
        }
        return QueryResult.Value(Unit)
    }

    private val CREATE_SONG_CACHE = """
        CREATE TABLE IF NOT EXISTS SongCache (
            id TEXT NOT NULL PRIMARY KEY,
            songName TEXT NOT NULL,
            artist TEXT,
            platform TEXT NOT NULL,
            audioUrl TEXT NOT NULL,
            coverUrl TEXT,
            lyrics TEXT,
            quality TEXT NOT NULL,
            expireTime INTEGER NOT NULL,
            createTime INTEGER NOT NULL
        )
    """.trimIndent()

    private val CREATE_FAILED_LOOKUP = """
        CREATE TABLE IF NOT EXISTS FailedLookup (
            id TEXT NOT NULL PRIMARY KEY,
            expireTime INTEGER NOT NULL
        )
    """.trimIndent()

    private val CREATE_DOWNLOAD_RECORD = """
        CREATE TABLE IF NOT EXISTS DownloadRecord (
            id TEXT NOT NULL PRIMARY KEY,
            fileName TEXT NOT NULL,
            songName TEXT NOT NULL,
            artist TEXT,
            platform TEXT NOT NULL,
            audioUrl TEXT NOT NULL,
            localPath TEXT NOT NULL,
            fileSize INTEGER NOT NULL DEFAULT 0,
            quality TEXT NOT NULL DEFAULT 'standard',
            coverUrl TEXT,
            lyrics TEXT,
            downloadTime INTEGER NOT NULL,
            realName TEXT,
            realArtist TEXT
        )
    """.trimIndent()
}
