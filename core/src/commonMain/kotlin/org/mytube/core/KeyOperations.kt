package org.mytube.core

/**
 * A secret used to protect (wrap/unwrap) a library's Master Encryption Key.
 * Spec §6.3/§6.4: a password, or the independently-generated recovery key.
 * Both protectors wrap the same MEK; either alone must allow access.
 */
sealed interface KeyProtector {
    /** User password. Never stored in cloud storage (spec §6.3). */
    data class Password(val value: String) : KeyProtector

    /**
     * Recovery key shown only during creation or explicit export, never
     * uploaded automatically (spec §6.4).
     */
    data class RecoveryKey(val value: String) : KeyProtector
}

/**
 * Encrypted representation of a Master Encryption Key, stored in the library's
 * cloud storage (spec §6.2). Format version travels with the data so key
 * derivation parameters and formats stay verifiable (spec §6.3).
 */
data class WrappedKey(
    /** Opaque wrapped MEK bytes produced by [KeyOperations.wrap]. */
    val keyMaterial: ByteArray,
    /** Version of the wrap format used; the crypto layer owns interpretation. */
    val formatVersion: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is WrappedKey && formatVersion == other.formatVersion && keyMaterial.contentEquals(other.keyMaterial)

    override fun hashCode(): Int = 31 * formatVersion + keyMaterial.contentHashCode()
}

/**
 * Port for MEK generation and wrapping. Deliberately crypto-format-free:
 * ADR-005 is still Proposed, so no concrete wrapping algorithm is chosen here.
 * The `:crypto` module implements this once crypto is owner-approved
 * (repo AGENTS.md §12); call sites depend on this interface only.
 */
interface KeyOperations {
    /** Generates a fresh random MEK (spec §6.2: one independent random MEK per library). */
    fun generateMek(): ByteArray

    /** Wraps the MEK under the given protector, recording the format version. */
    fun wrap(mek: ByteArray, protector: KeyProtector): WrappedKey

    /** Unwraps [wrapped] with [protector]; fails (does not throw plaintext leaks) on mismatch. */
    fun unwrap(wrapped: WrappedKey, protector: KeyProtector): ByteArray
}
