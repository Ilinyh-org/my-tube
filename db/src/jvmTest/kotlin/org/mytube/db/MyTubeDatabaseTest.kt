package org.mytube.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.sql.SQLException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.mytube.core.LibraryId
import org.mytube.core.Track
import org.mytube.core.TrackId
import org.mytube.core.TrackMetadata
import org.mytube.core.TrackRecord
import org.mytube.crypto.KeyHierarchy
import org.mytube.crypto.XChaCha20ValueCipher

/**
 * JVM (in-memory SQLite) tests for the SQLDelight schema: foreign keys,
 * cascades, library isolation, playlist ordering, and the ADR-004 value
 * encryption behavior through the Repositories layer. See spec
 * §4.1/§4.2/§8.4 and AGENTS.md §10.
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
        db.playlistsQueries.insertPlaylist("p1".padEnd(32, '0'), "a".repeat(32), "Favorites", "")

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
        db.playlistsQueries.insertPlaylist(playlist, lib, "Mix", "")
        // Insert in scrambled order; query must still return manual order
        // (t1@0, t2@1, t3@2).
        listOf(2L, 0L, 1L).forEachIndexed { _, pos ->
            db.playlistsQueries.addTrackToPlaylist(playlist, "t${pos + 1}".padEnd(32, '0'), pos)
        }

        val titles = db.playlistsQueries.tracksForPlaylist(playlist).executeAsList().map { it.title }
        assertEquals(listOf("Track 0", "Track 1", "Track 2"), titles)
    }

    @Test
    fun `stage-1 duplicate candidates are computed on decrypted rows, not by SQL`() {
        // ADR-004: metadata columns are sealed with a fresh nonce per value,
        // so identical plaintexts never compare equal in SQL; stage 1 (spec
        // §8.4) must run in memory over decrypted rows (CombinedLibraryView).
        insertLibrary("a".repeat(32), "A")
        val lib = "a".repeat(32)
        val cipher = XChaCha20ValueCipher(KeyHierarchy.randomMek())
        fun track(id: String, duration: Long?) = db.saveTrack(
            TrackRecord(
                track = Track(
                    id = TrackId(id), libraryId = LibraryId(lib),
                    metadata = TrackMetadata(
                        title = "Song", artist = "Artist", album = "Album",
                        genre = null, year = null, durationMs = duration,
                        trackNumber = null, customTags = emptyMap(),
                    ),
                ),
                objectName = "obj-$id", contentSha256 = null,
            ),
            cipher,
        )
        track("1".repeat(32), 200_000L)
        track("2".repeat(32), 200_000L)
        val storedTitles = db.tracksQueries.selectTracksForLibrary(lib).executeAsList().map { it.title }
        assertTrue(storedTitles.all { it != "Song" }, "titles must not be stored plaintext in this mode")
    }
}
