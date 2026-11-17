package org.mytube.core

import java.security.SecureRandom

internal actual object SecureRandomBytes {
    private val random = SecureRandom()

    actual fun nextHex(byteCount: Int): String {
        require(byteCount > 0)
        val bytes = ByteArray(byteCount)
        random.nextBytes(bytes)
        return bytes.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
    }
}
