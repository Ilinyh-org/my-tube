package org.mytube.crypto

/**
 * Shared crypto-module skeleton. Per repo AGENTS.md §12, no production
 * cryptographic format is implemented before ADR-005 (key hierarchy and
 * parameters) is documented and reviewed. This file defines only the
 * versioned-format marker used by every future serialized crypto structure.
 */
public const val CRYPTO_FORMAT_VERSION: Int = 1

/** Marker interface for all versioned crypto container formats (MEK wrap, recovery, access files). */
public interface VersionedCryptoContainer {
    public val formatVersion: Int
}
