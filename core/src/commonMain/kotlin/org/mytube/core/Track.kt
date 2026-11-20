package org.mytube.core

/**
 * Immutable identifier for a track. Random, opaque; never derived from
 * file names or metadata (spec §6.1: no private data in identifiers).
 */
@JvmInline
value class TrackId(val value: String) {
    init {
        require(value.matches(ID_REGEX)) { "TrackId must match ${ID_REGEX.pattern}" }
    }

    companion object {
        private val ID_REGEX = Regex("^[0-9a-f]{32}$")

        /** Generates a new random identifier using a cryptographically secure source. */
        fun generate(): TrackId = TrackId(SecureRandomBytes.nextHex(16))
    }
}

/**
 * A track in a specific library. Every track retains its original library
 * association (spec §4.2); identical tracks in different libraries are never
 * merged.
 */
data class Track(
    val id: TrackId,
    val libraryId: LibraryId,
    val metadata: TrackMetadata,
)
