package org.mytube.core

/**
 * Port for application-level, value-at-rest encryption of database column
 * values (ADR-004). Privacy-relevant strings (track titles, artist/album
 * names, genres, tag names/values, playlist names/descriptions, library
 * names) are encrypted *before* being handed to the SQL layer and decrypted
 * after being read back; keys come from the library's unlocked key material
 * (ADR-005 hierarchy) and never touch the database.
 *
 * Non-sensitive columns (opaque random ids, sizes, durations, timestamps)
 * stay plaintext for indexing and range queries.
 *
 * Callers depend on this interface only; `:crypto` provides the concrete
 * XChaCha20-Poly1305 implementation. Per ADR-004, encryption is randomized
 * (fresh nonce per value), so equality/ordering on encrypted columns is
 * meaningless in SQL: filtering, search, and ordering of encrypted fields
 * happen in memory after decryption.
 */
interface ValueCipher {
    /** Encrypts [plaintext]; the output is self-describing (versioned format). */
    fun encrypt(plaintext: String): String

    /**
     * Decrypts a value produced by [encrypt]; fails closed (throws, never
     * returns guessed plaintext) on wrong key or tampered data.
     */
    fun decrypt(ciphertext: String): String
}
