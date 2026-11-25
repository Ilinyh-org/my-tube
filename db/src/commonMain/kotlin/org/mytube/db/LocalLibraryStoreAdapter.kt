package org.mytube.db

import org.mytube.core.LocalLibraryStore
import org.mytube.core.Library
import org.mytube.core.LibraryId
import org.mytube.core.Playlist
import org.mytube.core.PlaylistId
import org.mytube.core.TrackRecord
import org.mytube.core.ValueCipher

/**
 * Adapter exposing the SQLDelight database as the `:core` [LocalLibraryStore]
 * port (PLAN-PHASE1 step 3+4 wiring). Delegates to the extension functions in
 * [Repositories]; pass the library's unlocked-key [ValueCipher] to enable the
 * ADR-004 value-at-rest encryption of privacy-relevant columns.
 */
fun MyTubeDatabase.asLocalLibraryStore(cipher: ValueCipher? = null): LocalLibraryStore =
    SqlDelightLocalLibraryStore(this, cipher)

internal class SqlDelightLocalLibraryStore(
    private val database: MyTubeDatabase,
    private val cipher: ValueCipher?,
) : LocalLibraryStore {
    override fun saveLibrary(library: Library, createdAtEpochMs: Long) {
        database.saveLibrary(library, createdAtEpochMs, cipher)
    }

    override fun listLibraries(): List<Library> = database.listLibraries(cipher)

    override fun deleteLibrary(id: LibraryId) {
        database.deleteLibrary(id)
    }

    override fun saveTrack(record: TrackRecord) {
        database.saveTrack(record, cipher)
    }

    override fun listTracks(libraryId: LibraryId): List<TrackRecord> =
        database.listTracks(libraryId, cipher)

    override fun savePlaylist(playlist: Playlist) {
        database.savePlaylist(playlist, cipher)
    }

    override fun listPlaylists(libraryId: LibraryId): List<Playlist> =
        database.listPlaylists(libraryId, cipher)

    override fun deletePlaylist(id: PlaylistId) {
        database.deletePlaylist(id)
    }
}
