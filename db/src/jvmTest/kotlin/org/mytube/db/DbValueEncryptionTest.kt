package org.mytube.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.mytube.core.Library
import org.mytube.core.LibraryId
import org.mytube.core.Playlist
import org.mytube.core.PlaylistId
import org.mytube.core.Track
import org.mytube.core.TrackId
import org.mytube.core.TrackMetadata
import org.mytube.core.TrackRecord
import org.mytube.core.ValueCipher
import org.mytube.crypto.XChaCha20ValueCipher

/**
 * ADR-004 value-at-rest encryption tests through the Repositories layer:
 * privacy-relevant values are sealed before SQL, decrypted after read,
 * ordering is applied in memory, and raw rows never contain plaintext.
 */
class DbValueEncryptionTest {

    private lateinit var driver: SqlDriver
    private lateinit var db: MyTubeDatabase
    private lateinit var cipher: ValueCipher

    private val libId = LibraryId("a".repeat(32))

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        db = MyTubeDatabase(driver)
        MyTubeDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        cipher = XChaCha20ValueCipher(org.mytube.crypto.KeyHierarchy.randomMek())
        db.saveLibrary(Library(libId, "Personal Library"), 1_000L, cipher)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    private fun trackRecord(id: String, title: String, tags: Map<String, String> = emptyMap()) = TrackRecord(
        track = Track(
            id = TrackId(id),
            libraryId = libId,
            metadata = TrackMetadata(
                title = title,
                artist = "Édith Piaf",
                album = "Album $id",
                genre = "Chanson",
                year = 1961,
                durationMs = 200_000L,
                trackNumber = 3,
                customTags = tags,
            ),
        ),
        objectName = "obj-$id",
        contentSha256 = "cafe",
    )

    @Test
    fun `libraries round trip through encryption`() {
        val libraries = db.listLibraries(cipher)
        assertEquals(1, libraries.size)
        assertEquals("Personal Library", libraries[0].name)
    }

    @Test
    fun `tracks round trip and raw rows hide plaintext`() {
        db.saveTrack(trackRecord("1".repeat(32), "Non, je ne regrette rien"), cipher)
        db.saveTrack(trackRecord("2".repeat(32), "Milord"), cipher)

        val tracks = db.listTracks(libId, cipher)
        assertEquals(listOf("Milord", "Non, je ne regrette rien"), tracks.map { it.track.metadata.title })

        val raw = (driver as JdbcSqliteDriver).getConnection()
            .createStatement().executeQuery("SELECT title, artist, album, genre FROM tracks")
        // Every stored metadata value must be a sealed blob, not plaintext.
        val sealPrefix = XChaCha20ValueCipher.MAGIC_BYTES
        fun isSealed(v: String): Boolean = try {
            Base64.getDecoder().decode(v).copyOfRange(0, 4).contentEquals(sealPrefix)
        } catch (e: IllegalArgumentException) {
            false
        }
        while (raw.next()) {
            for (col in 0 until 4) {
                val v = raw.getString(col + 1) ?: ""
                assertTrue(isSealed(v), "column $col stored plaintext: $v")
            }
        }
        raw.statement.connection.close()
    }

    @Test
    fun `tags round trip encrypted`() {
        val id = "3".repeat(32)
        db.saveTrack(trackRecord(id, "Song", tags = mapOf("mood" to "melancholic", "zulu" to "value")), cipher)
        val tags = db.listTracks(libId, cipher).single().track.metadata.customTags
        assertEquals(mapOf("mood" to "melancholic", "zulu" to "value"), tags)
    }

    @Test
    fun `playlists round trip and ordering is by decrypted name`() {
        val pid1 = PlaylistId("4".repeat(32))
        val pid2 = PlaylistId("5".repeat(32))
        db.savePlaylist(Playlist(pid2, libId, "Zeta Mix", "desc z"), cipher)
        db.savePlaylist(Playlist(pid1, libId, "Alpha Mix", ""), cipher)

        val playlists = db.listPlaylists(libId, cipher)
        assertEquals(listOf("Alpha Mix", "Zeta Mix"), playlists.map { it.name })
        assertEquals("", playlists[0].description)
        assertEquals("desc z", playlists[1].description)

        val raw = (driver as JdbcSqliteDriver).getConnection()
            .createStatement().executeQuery("SELECT name, description FROM playlists")
        fun isSealed(v: String): Boolean = try {
            Base64.getDecoder().decode(v).copyOfRange(0, 4).contentEquals(XChaCha20ValueCipher.MAGIC_BYTES)
        } catch (e: IllegalArgumentException) {
            false
        }
        while (raw.next()) {
            for (col in 0 until 2) {
                val v = raw.getString(col + 1) ?: ""
                assertTrue(isSealed(v), "playlist column $col plaintext: $v")
            }
        }
        raw.statement.connection.close()
    }

    @Test
    fun `object name and content hash stay plaintext per ADR-004`() {
        val id = "6".repeat(32)
        db.saveTrack(trackRecord(id, "Song"), cipher)
        val row = (driver as JdbcSqliteDriver).getConnection()
            .createStatement().executeQuery("SELECT object_name, content_sha256 FROM tracks")
        row.next()
        assertEquals("obj-$id", row.getString(1))
        assertEquals("cafe", row.getString(2))
        row.statement.connection.close()
    }
}
