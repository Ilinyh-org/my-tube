package org.mytube.crypto

/**
 * Recovery key encoding (ADR-005): 32 random bytes, shown to the user as a
 * base32 (RFC 4648, no padding) string of [key || CRC32(key)] so transcription
 * errors are detected. Never logged or uploaded unwrapped.
 */
public object RecoveryKeyCodec {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val CHECK_LEN = 4

    /** Encode a 32-byte recovery key to its display form (base32 of key + CRC32). */
    public fun encode(key: ByteArray): String {
        require(key.size == 32) { "recovery key must be 32 bytes" }
        val payload = ByteArray(key.size + CHECK_LEN)
        key.copyInto(payload)
        val crc = crc32(key)
        payload[key.size] = (crc ushr 24).toByte()
        payload[key.size + 1] = (crc ushr 16).toByte()
        payload[key.size + 2] = (crc ushr 8).toByte()
        payload[key.size + 3] = crc.toByte()
        return base32Encode(payload)
    }

    /**
     * Decode and validate a display-form recovery key. Returns the 32 raw
     * bytes, or throws [IllegalArgumentException] on format/checksum error.
     */
    public fun decode(display: String): ByteArray {
        val payload = base32Decode(display.trim().uppercase())
        require(payload.size == 36) { "invalid recovery key length" }
        val key = payload.copyOfRange(0, 32)
        val crc = ((payload[32].toInt() and 0xFF) shl 24) or ((payload[33].toInt() and 0xFF) shl 16) or
            ((payload[34].toInt() and 0xFF) shl 8) or (payload[35].toInt() and 0xFF)
        require(crc == crc32(key)) { "recovery key checksum mismatch" }
        return key
    }

    private fun crc32(data: ByteArray): Int {
        var crc = 0xFFFFFFFFu.toInt()
        for (b in data) {
            crc = crc xor (b.toInt() and 0xFF)
            repeat(8) {
                crc = if (crc and 1 != 0) (crc ushr 1) xor 0xEDB88320.toInt() else crc ushr 1
            }
        }
        return crc.inv()
    }

    private fun base32Encode(data: ByteArray): String {
        val sb = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                sb.append(ALPHABET[(buffer ushr bits) and 31])
            }
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }

    private fun base32Decode(s: String): ByteArray {
        val clean = s.filter { it in ALPHABET }
        require(clean.length * 5 >= 36 * 8) { "too short" }
        val out = ByteArray(clean.length * 5 / 8)
        var buffer = 0
        var bits = 0
        var o = 0
        for (c in clean) {
            buffer = (buffer shl 5) or ALPHABET.indexOf(c)
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out[o++] = ((buffer ushr bits) and 0xFF).toByte()
            }
        }
        return out
    }
}
