package org.mytube.core

/**
 * Immutable identifier for a library. Random, opaque; never derived from
 * user-visible library names (spec §6.1: no private data in identifiers).
 */
@JvmInline
value class LibraryId(val value: String) {
    init {
        require(value.matches(ID_REGEX)) { "LibraryId must match ${ID_REGEX.pattern}" }
    }

    companion object {
        private val ID_REGEX = Regex("^[0-9a-f]{32}$")

        /** Generates a new random identifier using a cryptographically secure source. */
        fun generate(): LibraryId = LibraryId(SecureRandomBytes.nextHex(16))
    }
}

/** A music library: independent encryption, storage, metadata, playlists (spec §4.1). */
data class Library(
    val id: LibraryId,
    val name: String,
) {
    init {
        require(name.isNotBlank()) { "Library name must not be blank" }
    }
}

/**
 * Library domain model aggregate shared across modules. Deliberately free of
 * storage-provider and persistence details (architectural principle §3.1.4).
 */
