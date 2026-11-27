package org.mytube.db

import org.mytube.core.Library
import org.mytube.core.LibraryId
import org.mytube.core.Playlist
import org.mytube.core.PlaylistId
import org.mytube.core.Track
import org.mytube.core.TrackId
import org.mytube.core.TrackMetadata
import org.mytube.core.TrackRecord
import org.mytube.core.ValueCipher

/**
 * Persistence bridge between the `:core` domain model and the SQLDelight
 * schema (PLAN-PHASE1 step 4 → model wiring), with the ADR-004 value-at-rest
 * encryption layer applied: when a [ValueCipher] is supplied, all
 * privacy-relevant text values (library name; track title/artist/album/
 * genre; tag names and values; playlist name/description) are sealed before
 * the SQL layer sees them and decrypted after reading. Opaque ids,
 * object_name, content hashes, sizes, durations, and timestamps stay
 * plaintext (ADR-004 decision item 2).
 *
 * Encrypted columns cannot be compared or ordered by SQL, so ordering of
 * affected results is applied in memory after decryption (ADR-004 decision
 * item 3; stage-1 duplicate candidates live in `:core`
 * CombinedLibraryView over decrypted rows, spec §8.4).
 *
 * Timestamps are passed explicitly by callers so this layer stays
 * deterministic and platform-agnostic (no clock dependency in common code).
 */

private fun ValueCipher?.enc(value: String): String =
    if (this == null) value else encrypt(value)

private fun ValueCipher?.encOrNull(value: String?): String? =
    if (this == null) value else value?.let { encrypt(it) }

private fun ValueCipher?.dec(value: String): String =
    if (this == null) value else decrypt(value)

private fun ValueCipher?.decOrNull(value: String?): String? =
    if (this == null) value else value?.let { decrypt(it) }

fun MyTubeDatabase.saveLibrary(
    library: Library,
    createdAtEpochMs: Long,
    cipher: ValueCipher? = null,
) {
    librariesQueries.insertLibrary(library.id.value, cipher.enc(library.name), createdAtEpochMs)
}

fun MyTubeDatabase.listLibraries(cipher: ValueCipher? = null): List<Library> =
    librariesQueries.selectLibraries().executeAsList().map { row ->
        Library(LibraryId(row.id), cipher.dec(row.name))
    }

fun MyTubeDatabase.deleteLibrary(id: LibraryId) {
    librariesQueries.deleteLibrary(id.value)
}

fun MyTubeDatabase.saveTrack(record: TrackRecord, cipher: ValueCipher? = null) {
    val t = record.track
    val m = t.metadata
    tracksQueries.insertTrack(
        id = t.id.value,
        library_id = t.libraryId.value,
        title = cipher.enc(m.title),
        artist = cipher.encOrNull(m.artist),
        album = cipher.encOrNull(m.album),
        genre = cipher.encOrNull(m.genre),
        year = m.year?.toLong(),
        duration_ms = m.durationMs,
        track_number = m.trackNumber?.toLong(),
        object_name = record.objectName,
        content_sha256 = record.contentSha256,
    )
    for ((tag, value) in m.customTags) {
        tracksQueries.addTag(t.id.value, cipher.enc(tag), cipher.enc(value))
    }
}

fun MyTubeDatabase.listTracks(libraryId: LibraryId, cipher: ValueCipher? = null): List<TrackRecord> =
    tracksQueries.selectTracksForLibrary(libraryId.value).executeAsList().map { row ->
        val tags = tracksQueries.tagsForTrack(row.id).executeAsList()
            .associate { cipher.dec(it.tag) to cipher.dec(it.value_) }
        TrackRecord(
            track = Track(
                id = TrackId(row.id),
                libraryId = LibraryId(row.library_id),
                metadata = TrackMetadata(
                    title = cipher.dec(row.title),
                    artist = cipher.decOrNull(row.artist),
                    album = cipher.decOrNull(row.album),
                    genre = cipher.decOrNull(row.genre),
                    year = row.year?.toInt(),
                    durationMs = row.duration_ms,
                    trackNumber = row.track_number?.toInt(),
                    customTags = tags,
                ),
            ),
            objectName = row.object_name,
            contentSha256 = row.content_sha256,
        )
    }.sortedWith(compareBy({ it.track.metadata.title }, { it.track.id.value }))

fun MyTubeDatabase.savePlaylist(playlist: Playlist, cipher: ValueCipher? = null) {
    playlistsQueries.insertPlaylist(
        playlist.id.value,
        playlist.libraryId.value,
        cipher.enc(playlist.name),
        cipher.enc(playlist.description),
    )
    replacePlaylistTracks(playlist)
}

fun MyTubeDatabase.replacePlaylistTracks(playlist: Playlist) {
    playlistsQueries.clearPlaylistTracks(playlist.id.value)
    playlist.trackIds.forEachIndexed { position, trackId ->
        playlistsQueries.addTrackToPlaylist(playlist.id.value, trackId.value, position.toLong())
    }
}

fun MyTubeDatabase.listPlaylists(libraryId: LibraryId, cipher: ValueCipher? = null): List<Playlist> =
    playlistsQueries.selectPlaylistsForLibrary(libraryId.value).executeAsList().map { row ->
        val trackIds = playlistsQueries.tracksForPlaylist(row.id).executeAsList()
            .map { TrackId(it.id) }
        Playlist(
            id = PlaylistId(row.id),
            libraryId = LibraryId(row.library_id),
            name = cipher.dec(row.name),
            description = cipher.dec(row.description),
            trackIds = trackIds,
        )
    }.sortedWith(compareBy({ it.name }, { it.id.value }))

fun MyTubeDatabase.deletePlaylist(id: PlaylistId) {
    playlistsQueries.deletePlaylist(id.value)
}
