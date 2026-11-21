package org.mytube.db

import org.mytube.core.LocalLibraryStore
import org.mytube.core.Library
import org.mytube.core.LibraryId
import org.mytube.core.Playlist
import org.mytube.core.PlaylistId
import org.mytube.core.TrackRecord

/**
 * Adapter exposing the SQLDelight database as the `:core` [LocalLibraryStore]
 * port (PLAN-PHASE1 step 3+4 wiring). Delegates to the extension functions in
 * [Repositories]; pure storage, no encryption concerns (ADR-004 layer above).
 */
fun MyTubeDatabase.asLocalLibraryStore(): LocalLibraryStore =
    SqlDelightLocalLibraryStore(this)

internal class SqlDelightLocalLibraryStore(
    private val database: MyTubeDatabase,
) : LocalLibraryStore {
    override fun saveLibrary(library: Library, createdAtEpochMs: Long) {
        database.saveLibrary(library, createdAtEpochMs)
    }

    override fun listLibraries(): List<Library> = database.listLibraries()

    override fun deleteLibrary(id: LibraryId) {
        database.deleteLibrary(id)
    }

    override fun saveTrack(record: TrackRecord) {
        database.saveTrack(record)
    }

    override fun listTracks(libraryId: LibraryId): List<TrackRecord> =
        database.listTracks(libraryId)

    override fun savePlaylist(playlist: Playlist) {
        database.savePlaylist(playlist)
    }

    override fun listPlaylists(libraryId: LibraryId): List<Playlist> =
        database.listPlaylists(libraryId)

    override fun deletePlaylist(id: PlaylistId) {
        database.deletePlaylist(id)
    }
}
