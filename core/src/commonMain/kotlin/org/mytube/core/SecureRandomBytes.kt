package org.mytube.core

/**
 * Cryptographically secure random byte source shared by domain identifiers.
 * Isolated here so the RNG policy has exactly one implementation point.
 * Platform actuals must use the platform's secure RNG (e.g. java.security.SecureRandom).
 */
internal expect object SecureRandomBytes {
    fun nextHex(byteCount: Int): String
}
