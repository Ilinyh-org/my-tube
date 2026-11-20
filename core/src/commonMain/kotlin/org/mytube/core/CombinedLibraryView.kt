package org.mytube.core

/**
 * Aggregated view across connected libraries (spec §4.2). Tracks keep their
 * original library association and identical tracks are never merged; results
 * are plain sets/lists of the source tracks.
 */
class CombinedLibraryView(private val tracks: List<Track>) {

    /** Filters the view to a single library (spec §4.2: filter results by library). */
    fun filterByLibrary(libraryId: LibraryId): List<Track> =
        tracks.filter { it.libraryId == libraryId }

    /** All distinct artists aggregated across connected libraries. */
    fun artists(): Set<String> = tracks.mapNotNull { it.metadata.artist }.toSet()

    /** All distinct albums aggregated across connected libraries. */
    fun albums(): Set<String> = tracks.mapNotNull { it.metadata.album }.toSet()

    /** All distinct genres aggregated across connected libraries. */
    fun genres(): Set<String> = tracks.mapNotNull { it.metadata.genre }.toSet()

    /**
     * Stage-1 duplicate candidates (spec §8.4): groups of tracks in the view
     * with equal title, artist, album, and duration. Groups are returned in
     * discovery order; identical tracks are never merged or removed here —
     * the user decides how to handle duplicates.
     */
    fun duplicateCandidates(): List<List<Track>> =
        tracks
            .groupBy { DuplicateKey(it.metadata) }
            .values
            .filter { it.size > 1 }

    private data class DuplicateKey(
        val title: String,
        val artist: String?,
        val album: String?,
        val durationMs: Long?,
    ) {
        constructor(m: TrackMetadata) : this(m.title, m.artist, m.album, m.durationMs)
    }
}
