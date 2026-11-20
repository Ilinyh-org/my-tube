package org.mytube.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.sql.SQLException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * JVM (in-memory SQLite) tests for the SQLDelight schema: foreign keys,
 * cascades, library isolation, playlist ordering, and duplicate-detection
 * indexes behavior. See spec §4.1/§4.2/§8.4 and AGENTS.md §10.
 */
class MyTubeDatabaseTest {

    private val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    private val db: MyTubeDatabase = MyTubeDatabase(driver)

    init {
        MyTubeDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private fun insertLibrary(id: String, name: String) {
        db.librariesQueries.insertLibrary(id, name, 1_000L)
    }

    @Test
    fun `library round trip`() {
        insertLibrary("a".repeat(32), "Personal")
        insertLibrary("b".repeat(32), "Shared")

        assertEquals(2L, db.librariesQueries.countLibraries().executeAsOne())
        val names = db.librariesQueries.selectLibraries().executeAsList().map { it.name }
        assertEquals(listOf("Personal", "Shared"), names)
    }

    @Test
    fun `track requires an existing library`() {
        val err = assertFailsWith<SQLException> {
            db.tracksQueries.insertTrack(
                id = "t1".padEnd(32, '0'),
                library_id = "c".repeat(32), // no such library
                title = "Song",
                artist = null, album = null, genre = null,
                year = null, duration_ms = 1L, track_number = null,
                object_name = "obj-0001", content_sha256 = null,
            )
        }
        assertTrue(err.message.orEmpty().contains("FOREIGN KEY"), err.message)
    }

    @Test
    fun `deleting a library cascades to tracks and playlists`() {
        insertLibrary("a".repeat(32), "Personal")
        db.tracksQueries.insertTrack(
            id = "t1".padEnd(32, '0'), library_id = "a".repeat(32),
            title = "Song", artist = null, album = null, genre = null,
            year = null, duration_ms = 1L, track_number = null,
            object_name = "obj-0001", content_sha256 = null,
        )
        db.playlistsQueries.insertPlaylist("p1".padEnd(32, '0'), "a".repeat(32), "Favorites")

        db.librariesQueries.deleteLibrary("a".repeat(32))

        assertEquals(0, db.tracksQueries.selectTracksForLibrary("a".repeat(32)).executeAsList().size)
        assertEquals(0, db.playlistsQueries.selectPlaylistsForLibrary("a".repeat(32)).executeAsList().size)
    }

    @Test
    fun `tracks from different libraries stay separate`() {
        insertLibrary("a".repeat(32), "A")
        insertLibrary("b".repeat(32), "B")
        val sameTrack = { id: String, lib: String ->
            db.tracksQueries.insertTrack(
                id = id, library_id = lib,
                title = "Song", artist = "Artist", album = "Album", genre = null,
                year = 2020L, duration_ms = 200_000L, track_number = 1L,
                object_name = "obj-$lib", content_sha256 = "cafe",
            )
        }
        sameTrack("t1".padEnd(32, '0'), "a".repeat(32))
        sameTrack("t2".padEnd(32, '0'), "b".repeat(32))

        assertEquals(1, db.tracksQueries.selectTracksForLibrary("a".repeat(32)).executeAsList().size)
        assertEquals(1, db.tracksQueries.selectTracksForLibrary("b".repeat(32)).executeAsList().size)
    }

    @Test
    fun `playlist preserves manual order and library scoping`() {
        insertLibrary("a".repeat(32), "A")
        val lib = "a".repeat(32)
        listOf("t1", "t2", "t3").forEachIndexed { i, t ->
            db.tracksQueries.insertTrack(
                id = t.padEnd(32, '0'), library_id = lib,
                title = "Track $i", artist = null, album = null, genre = null,
                year = null, duration_ms = null, track_number = null,
                object_name = "obj-$t", content_sha256 = null,
            )
        }
        val playlist = "p1".padEnd(32, '0')
        db.playlistsQueries.insertPlaylist(playlist, lib, "Mix")
        // Insert in scrambled order; query must still return manual order
        // (t1@0, t2@1, t3@2).
        listOf(2L, 0L, 1L).forEachIndexed { _, pos ->
            db.playlistsQueries.addTrackToPlaylist(playlist, "t${pos + 1}".padEnd(32, '0'), pos)
        }

        val titles = db.playlistsQueries.tracksForPlaylist(playlist).executeAsList().map { it.title }
        assertEquals(listOf("Track 0", "Track 1", "Track 2"), titles)
    }

    @Test
    fun `duplicate detection index helper queries by metadata`() {
        insertLibrary("a".repeat(32), "A")
        val lib = "a".repeat(32)
        fun track(id: String, duration: Long?) = db.tracksQueries.insertTrack(
            id = id, library_id = lib,
            title = "Song", artist = "Artist", album = "Album", genre = null,
            year = null, duration_ms = duration, track_number = null,
            object_name = "obj-$id", content_sha256 = null,
        )
        track("t1".padEnd(32, '0'), 200_000L)
        track("t2".padEnd(32, '0'), 200_000L) // possible duplicate (stage 1)
        track("t3".padEnd(32, '0'), 190_000L)

        val dupes = db.tracksQueries.selectPossibleDuplicates(
            library_id = lib, title = "Song", artist = "Artist", album = "Album", duration_ms = 200_000L,
        ).executeAsList()
        assertEquals(2, dupes.size)
    }
}
