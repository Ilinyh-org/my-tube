package org.mytube.crypto

/**
 * Wrapped-MEK blob format v1 (ADR-005):
 *
 * magic "MTW1" | u8 formatVersion (=1) | u8 kdfId (=argon2id=1)
 * u32 kdf m | u32 kdf t | u8 kdf p | 16B salt
 * 24B nonce | u32 wrappedLen | MEK wrapped (XChaCha20-Poly1305 AEAD)
 *
 * All multi-byte integers are big-endian. Blob purpose (password-wrap vs
 * recovery-wrap) is recorded by the storage layout, not inside the blob.
 */
public class MekWrapBlob(
    public val kdfId: Int,
    public val kdfMemoryKiB: Int,
    public val kdfIterations: Int,
    public val kdfParallelism: Int,
    public val salt: ByteArray,
    public val nonce: ByteArray,
    public val wrappedMek: ByteArray,
) {
    public fun encode(): ByteArray {
        require(salt.size == SALT_LEN) { "salt must be $SALT_LEN bytes" }
        require(nonce.size == NONCE_LEN) { "nonce must be $NONCE_LEN bytes" }
        val buf = ByteArray(FIXED_HEADER_LEN + wrappedMek.size)
        var o = 0
        MAGIC_BYTES.copyInto(buf, o); o += MAGIC_BYTES.size
        buf[o++] = CRYPTO_FORMAT_VERSION.toByte()
        buf[o++] = kdfId.toByte()
        putU32(buf, o, kdfMemoryKiB); o += 4
        putU32(buf, o, kdfIterations); o += 4
        buf[o++] = kdfParallelism.toByte()
        salt.copyInto(buf, o); o += SALT_LEN
        nonce.copyInto(buf, o); o += NONCE_LEN
        putU32(buf, o, wrappedMek.size)
        wrappedMek.copyInto(buf, o + 4)
        return buf
    }

    public companion object {
        public const val MAGIC: String = "MTW1"
        public val MAGIC_BYTES: ByteArray = MAGIC.encodeToByteArray()
        public const val SALT_LEN: Int = 16
        public const val NONCE_LEN: Int = 24
        /** 4 magic + 1 version + 1 kdfId + 4 m + 4 t + 1 p + 16 salt + 24 nonce + 4 len */
        public const val FIXED_HEADER_LEN: Int = 59
        public const val KDF_ARGON2ID: Int = 1

        public fun decode(bytes: ByteArray): MekWrapBlob {
            require(bytes.size >= FIXED_HEADER_LEN) { "blob too short: ${bytes.size}" }
            var o = 0
            require(bytes.size >= MAGIC_BYTES.size && String(bytes, o, MAGIC_BYTES.size, Charsets.US_ASCII) == MAGIC) { "bad magic" }
            o += MAGIC_BYTES.size
            val version = bytes[o++].toInt() and 0xFF
            require(version == CRYPTO_FORMAT_VERSION) { "unsupported formatVersion: $version" }
            val kdfId = bytes[o++].toInt() and 0xFF
            val m = getU32(bytes, o); o += 4
            val t = getU32(bytes, o); o += 4
            val p = bytes[o++].toInt() and 0xFF
            val salt = bytes.copyOfRange(o, o + SALT_LEN); o += SALT_LEN
            val nonce = bytes.copyOfRange(o, o + NONCE_LEN); o += NONCE_LEN
            val len = getU32(bytes, o); o += 4
            require(len > 0 && bytes.size - o == len) { "wrappedLen mismatch" }
            val wrapped = bytes.copyOfRange(o, o + len)
            return MekWrapBlob(kdfId, m, t, p, salt, nonce, wrapped)
        }

        private fun putU32(buf: ByteArray, off: Int, v: Int) {
            buf[off] = (v ushr 24).toByte(); buf[off + 1] = (v ushr 16).toByte()
            buf[off + 2] = (v ushr 8).toByte(); buf[off + 3] = v.toByte()
        }

        private fun getU32(buf: ByteArray, off: Int): Int =
            ((buf[off].toInt() and 0xFF) shl 24) or ((buf[off + 1].toInt() and 0xFF) shl 16) or
                ((buf[off + 2].toInt() and 0xFF) shl 8) or (buf[off + 3].toInt() and 0xFF)
    }
}

/** Zeroes a key array in place (AGENTS.md §4.2 / ADR-005 in-memory key handling). */
public fun zeroKey(key: ByteArray) {
    key.fill(0)
}
