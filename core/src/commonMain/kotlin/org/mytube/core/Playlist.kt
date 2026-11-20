package org.mytube.core

/**
 * Immutable identifier for a playlist. Random, opaque (spec §6.1).
 */
@JvmInline
value class PlaylistId(val value: String) {
    init {
        require(value.matches(ID_REGEX)) { "PlaylistId must match ${ID_REGEX.pattern}" }
    }

    companion object {
        private val ID_REGEX = Regex("^[0-9a-f]{32}$")

        /** Generates a new random identifier using a cryptographically secure source. */
        fun generate(): PlaylistId = PlaylistId(SecureRandomBytes.nextHex(16))
    }
}

/**
 * A manual playlist (spec §10.2). Belongs to exactly one library and cannot
 * contain tracks from other libraries (spec §4.2). Manual ordering is the
 * stored [trackIds] order.
 */
data class Playlist(
    val id: PlaylistId,
    val libraryId: LibraryId,
    val name: String,
    val description: String = "",
    val trackIds: List<TrackId> = emptyList(),
) {
    init {
        require(name.isNotBlank()) { "Playlist name must not be blank" }
    }

    /** Adds a track at the end; rejects tracks belonging to another library. */
    fun addTrack(track: Track): Playlist {
        require(track.libraryId == libraryId) {
            "Playlist cannot contain tracks from another library (spec §4.2)"
        }
        return copy(trackIds = trackIds + track.id)
    }

    /** Removes a track; order of remaining tracks is preserved. */
    fun removeTrack(trackId: TrackId): Playlist =
        copy(trackIds = trackIds - trackId)

    /** Moves a track to a new zero-based position within the manual ordering. */
    fun moveTrack(trackId: TrackId, toIndex: Int): Playlist {
        require(trackId in trackIds) { "Track is not in this playlist" }
        require(toIndex in trackIds.indices) { "Index out of range: $toIndex" }
        val remaining = trackIds - trackId
        return copy(trackIds = remaining.subList(0, toIndex) + trackId + remaining.subList(toIndex, remaining.size))
    }
}
