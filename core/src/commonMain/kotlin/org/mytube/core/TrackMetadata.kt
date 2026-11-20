package org.mytube.core

/**
 * Editable descriptive metadata for a track (spec §8.3). Changes are stored
 * separately in the encrypted library metadata; original audio files and their
 * embedded metadata are never modified.
 */
data class TrackMetadata(
    val title: String,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val durationMs: Long? = null,
    val trackNumber: Int? = null,
    val customTags: Map<String, String> = emptyMap(),
) {
    init {
        require(title.isNotBlank()) { "Track title must not be blank" }
        require(year == null || year in 1..9999) { "Year out of range: $year" }
        require(durationMs == null || durationMs >= 0) { "Duration must be non-negative" }
        require(trackNumber == null || trackNumber >= 0) { "Track number must be non-negative" }
    }
}
