package org.mytube.db

import org.mytube.core.Library
import org.mytube.core.LibraryId
import org.mytube.core.Playlist
import org.mytube.core.PlaylistId
import org.mytube.core.Track
import org.mytube.core.TrackId
import org.mytube.core.TrackMetadata
import org.mytube.core.TrackRecord

/**
 * Persistence bridge between the `:core` domain model and the SQLDelight
 * schema (PLAN-PHASE1 step 4 → model wiring). Pure mapping + storage; no
 * encryption concerns here (ADR-004 layer is applied above this level).
 *
 * Timestamps are passed explicitly by callers so this layer stays
 * deterministic and platform-agnostic (no clock dependency in common code).
 */

fun MyTubeDatabase.saveLibrary(library: Library, createdAtEpochMs: Long) {
    librariesQueries.insertLibrary(library.id.value, library.name, createdAtEpochMs)
}

fun MyTubeDatabase.listLibraries(): List<Library> =
    librariesQueries.selectLibraries().executeAsList().map { row ->
        Library(LibraryId(row.id), row.name)
    }

fun MyTubeDatabase.deleteLibrary(id: LibraryId) {
    librariesQueries.deleteLibrary(id.value)
}

fun MyTubeDatabase.saveTrack(record: TrackRecord) {
    val t = record.track
    val m = t.metadata
    tracksQueries.insertTrack(
        id = t.id.value,
        library_id = t.libraryId.value,
        title = m.title,
        artist = m.artist,
        album = m.album,
        genre = m.genre,
        year = m.year?.toLong(),
        duration_ms = m.durationMs,
        track_number = m.trackNumber?.toLong(),
        object_name = record.objectName,
        content_sha256 = record.contentSha256,
    )
    for ((tag, value) in m.customTags) {
        tracksQueries.addTag(t.id.value, tag, value)
    }
}

fun MyTubeDatabase.listTracks(libraryId: LibraryId): List<TrackRecord> =
    tracksQueries.selectTracksForLibrary(libraryId.value).executeAsList().map { row ->
        val tags = tracksQueries.tagsForTrack(row.id).executeAsList()
            .associate { it.tag to it.value_ }
        TrackRecord(
            track = Track(
                id = TrackId(row.id),
                libraryId = LibraryId(row.library_id),
                metadata = TrackMetadata(
                    title = row.title,
                    artist = row.artist,
                    album = row.album,
                    genre = row.genre,
                    year = row.year?.toInt(),
                    durationMs = row.duration_ms,
                    trackNumber = row.track_number?.toInt(),
                    customTags = tags,
                ),
            ),
            objectName = row.object_name,
            contentSha256 = row.content_sha256,
        )
}

fun MyTubeDatabase.savePlaylist(playlist: Playlist) {
    playlistsQueries.insertPlaylist(
        playlist.id.value,
        playlist.libraryId.value,
        playlist.name,
        playlist.description,
    )
    replacePlaylistTracks(playlist)
}

fun MyTubeDatabase.replacePlaylistTracks(playlist: Playlist) {
    playlistsQueries.clearPlaylistTracks(playlist.id.value)
    playlist.trackIds.forEachIndexed { position, trackId ->
        playlistsQueries.addTrackToPlaylist(playlist.id.value, trackId.value, position.toLong())
    }
}

fun MyTubeDatabase.listPlaylists(libraryId: LibraryId): List<Playlist> =
    playlistsQueries.selectPlaylistsForLibrary(libraryId.value).executeAsList().map { row ->
        val trackIds = playlistsQueries.tracksForPlaylist(row.id).executeAsList()
            .map { TrackId(it.id) }
        Playlist(
            id = PlaylistId(row.id),
            libraryId = LibraryId(row.library_id),
            name = row.name,
            description = row.description,
            trackIds = trackIds,
        )
}

fun MyTubeDatabase.deletePlaylist(id: PlaylistId) {
    playlistsQueries.deletePlaylist(id.value)
}
