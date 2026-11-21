package org.mytube.core

/**
 * Port for local persistence of the library model (spec §3: Local storage
 * component). Concrete SQLDelight implementation lives in `:db`; `:core`
 * stays database-agnostic. Encryption of the local database (ADR-004) is
 * layered beneath/around this port, not expressed here.
 */
interface LocalLibraryStore {
    fun saveLibrary(library: Library, createdAtEpochMs: Long)
    fun listLibraries(): List<Library>
    fun deleteLibrary(id: LibraryId)

    /** Saves a track together with its storage-level fields (object name, content hash). */
    fun saveTrack(record: TrackRecord)

    fun listTracks(libraryId: LibraryId): List<TrackRecord>

    fun savePlaylist(playlist: Playlist)
    fun listPlaylists(libraryId: LibraryId): List<Playlist>
    fun deletePlaylist(id: PlaylistId)
}

/** A track plus storage fields the domain model deliberately omits (spec §5, §8.4). */
data class TrackRecord(
    val track: Track,
    /** Opaque storage object name; must never reveal the original filename (spec §5). */
    val objectName: String,
    /** SHA-256 of original content for duplicate detection stage 2 (spec §8.4); null until checked. */
    val contentSha256: String?,
)
