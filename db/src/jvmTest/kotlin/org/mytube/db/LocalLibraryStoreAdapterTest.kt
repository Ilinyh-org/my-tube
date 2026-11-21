package org.mytube.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.mytube.core.Library
import org.mytube.core.LibraryId
import org.mytube.core.LocalLibraryStore
import org.mytube.core.Playlist
import org.mytube.core.PlaylistId
import org.mytube.core.Track
import org.mytube.core.TrackId
import org.mytube.core.TrackMetadata
import org.mytube.core.TrackRecord

/**
 * Exercises the `:core` [LocalLibraryStore] port against its concrete SQLDelight
 * adapter (`MyTubeDatabase.asLocalLibraryStore()`), confirming the port wiring:
 * library/track/playlist round trips, library isolation, and cascade delete —
 * all through the port interface only (PLAN-PHASE1 step 3+4 wiring).
 */
class LocalLibraryStoreAdapterTest {

    private val driver: SqlDriver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    private val db: MyTubeDatabase = MyTubeDatabase(driver)
    private val store: LocalLibraryStore = db.asLocalLibraryStore()

    init {
        MyTubeDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private fun track(id: String, libraryId: LibraryId) = TrackRecord(
        track = Track(
            id = TrackId(id),
            libraryId = libraryId,
            metadata = TrackMetadata(
                title = "Song $id",
                artist = "Artist",
                album = "Album",
                genre = "Rock",
                year = 2024,
                durationMs = 123_000L,
                trackNumber = 1,
                customTags = mapOf("source" to id),
            ),
        ),
        objectName = "obj/$id",
        contentSha256 = null,
    )

    @Test
    fun `library and track round trip through the port`() {
        val libraryId = LibraryId("a".repeat(32))
        store.saveLibrary(Library(libraryId, "Personal"), 1_000L)
        store.saveTrack(track("1".repeat(32), libraryId))

        assertEquals(listOf(Library(libraryId, "Personal")), store.listLibraries())
        val records = store.listTracks(libraryId)
        assertEquals(1, records.size)
        assertEquals("obj/" + "1".repeat(32), records[0].objectName)
        assertEquals("Song " + "1".repeat(32), records[0].track.metadata.title)
        assertEquals(mapOf("source" to "1".repeat(32)), records[0].track.metadata.customTags)
    }

    @Test
    fun `tracks from different libraries stay separate through the port`() {
        val a = LibraryId("a".repeat(32))
        val b = LibraryId("b".repeat(32))
        store.saveLibrary(Library(a, "A"), 1_000L)
        store.saveLibrary(Library(b, "B"), 2_000L)
        store.saveTrack(track("1".repeat(32), a))
        store.saveTrack(track("2".repeat(32), b))

        assertEquals(listOf("Song " + "1".repeat(32)), store.listTracks(a).map { it.track.metadata.title })
        assertEquals(listOf("Song " + "2".repeat(32)), store.listTracks(b).map { it.track.metadata.title })
    }

    @Test
    fun `playlist manual order round trip through the port`() {
        val libraryId = LibraryId("a".repeat(32))
        store.saveLibrary(Library(libraryId, "Personal"), 1_000L)
        store.saveTrack(track("1".repeat(32), libraryId))
        store.saveTrack(track("2".repeat(32), libraryId))
        val playlist = Playlist(
            id = PlaylistId("c".repeat(32)),
            libraryId = libraryId,
            name = "Mix",
            description = "desc",
            trackIds = listOf(TrackId("2".repeat(32)), TrackId("1".repeat(32))),
        )
        store.savePlaylist(playlist)

        val loaded = store.listPlaylists(libraryId).single()
        assertEquals(listOf("2".repeat(32), "1".repeat(32)), loaded.trackIds.map { it.value })
        assertEquals("desc", loaded.description)
    }

    @Test
    fun `deleting a library cascades through the port`() {
        val libraryId = LibraryId("a".repeat(32))
        store.saveLibrary(Library(libraryId, "Personal"), 1_000L)
        store.saveTrack(track("1".repeat(32), libraryId))
        store.savePlaylist(
            Playlist(
                id = PlaylistId("c".repeat(32)),
                libraryId = libraryId,
                name = "Mix",
                description = "",
                trackIds = listOf(TrackId("1".repeat(32))),
            ),
        )

        store.deleteLibrary(libraryId)
        assertTrue(store.listLibraries().isEmpty())
        assertTrue(store.listTracks(libraryId).isEmpty())
        assertTrue(store.listPlaylists(libraryId).isEmpty())
    }
}
