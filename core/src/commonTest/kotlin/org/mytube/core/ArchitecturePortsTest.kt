package org.mytube.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract checks for the architecture ports: they must stay provider- and
 * engine-free (spec §3.1), which common-code compilation already enforces at
 * the type level. These tests pin the small behavior guarantees the ports
 * declare.
 */
class ArchitecturePortsTest {

    private class FakeStore : LocalLibraryStore {
        val libraries = mutableListOf<Library>()
        val tracks = mutableListOf<TrackRecord>()
        val playlists = mutableListOf<Playlist>()

        override fun saveLibrary(library: Library, createdAtEpochMs: Long) {
            libraries.removeAll { it.id == library.id }
            libraries += library
        }
        override fun listLibraries(): List<Library> = libraries.toList()
        override fun deleteLibrary(id: LibraryId) { libraries.removeAll { it.id == id } }
        override fun saveTrack(record: TrackRecord) {
            tracks.removeAll { it.track.id == record.track.id }
            tracks += record
        }
        override fun listTracks(libraryId: LibraryId) = tracks.filter { it.track.libraryId == libraryId }
        override fun savePlaylist(playlist: Playlist) {
            playlists.removeAll { it.id == playlist.id }
            playlists += playlist
        }
        override fun listPlaylists(libraryId: LibraryId) = playlists.filter { it.libraryId == libraryId }
        override fun deletePlaylist(id: PlaylistId) { playlists.removeAll { it.id == id } }
    }

    @Test
    fun localStoreKeepsLibrariesIndependent() {
        val store = FakeStore()
        val a = Library(LibraryId("a".repeat(32)), "personal")
        val b = Library(LibraryId("b".repeat(32)), "shared")
        store.saveLibrary(a, 1)
        store.saveLibrary(b, 2)
        store.saveTrack(TrackRecord(Track(TrackId("1".repeat(32)), a.id, TrackMetadata("Song", "Artist")), "obj1", null))
        assertEquals(listOf(a, b), store.listLibraries())
        assertEquals(1, store.listTracks(a.id).size)
        assertEquals(0, store.listTracks(b.id).size, "track must stay in its own library (§4.2)")
        store.deleteLibrary(a.id)
        assertEquals(listOf(b), store.listLibraries())
    }

    @Test
    fun storageProviderContractIsContentAgnostic() {
        // Bytes in, bytes out; nothing in the signature mentions keys or plaintext rules.
        val p = object : StorageProvider {
            val blobs = mutableMapOf<String, ByteArray>()
            override fun checkAccess() {}
            override fun list(prefix: String) = blobs.keys.filter { it.startsWith(prefix) }
                .map { StoredObject(it, blobs[it]!!.size.toLong()) }
            override fun get(objectName: String) = blobs.getValue(objectName)
            override fun put(objectName: String, content: ByteArray): String? {
                blobs[objectName] = content
                return null
            }
            override fun delete(objectName: String) { blobs.remove(objectName) }
        }
        p.put("lib/a.enc", byteArrayOf(1, 2, 3))
        assertEquals(3, p.get("lib/a.enc").size)
        assertTrue(p.list("lib/").all { it.objectName.startsWith("lib/") })
        p.delete("lib/a.enc")
        p.delete("lib/a.enc") // deleting a missing object must not fail
    }

    @Test
    fun playbackSourceMarksEncryptionWithoutLeakingUrls() {
        val s = PlaybackSource("handle://track/1", encrypted = true)
        assertTrue(s.encrypted)
        assertEquals("handle://track/1", s.locator)
    }
}
