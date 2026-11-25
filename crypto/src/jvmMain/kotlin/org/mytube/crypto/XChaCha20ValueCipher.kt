package org.mytube.crypto

import java.security.SecureRandom
import java.util.Base64
import org.mytube.core.ValueCipher

/**
 * ADR-004 value-at-rest encryption for database column values, JVM target.
 * Each value is sealed with XChaCha20-Poly1305 under the library's unlocked
 * MEK (ADR-005 key hierarchy) with a fresh random 24-byte nonce, so identical
 * plaintexts never produce equal ciphertexts (equality/ordering must happen
 * in memory after decryption, per ADR-004).
 *
 * Stored text format (versioned per CRYPTO_FORMAT_VERSION rules):
 *
 *   base64( magic "MTV1" | u8 formatVersion (=1) | nonce(24B) | ct||tag )
 *
 * Decryption throws [WrongCredentialException] on wrong key or tampered data
 * (AEAD auth failure) — it never returns guessed plaintext.
 */
public class XChaCha20ValueCipher(
    /** 32-byte symmetric key (the library MEK, or a key derived from it). */
    private val key: ByteArray,
) : ValueCipher {
    init {
        require(key.size == KeyHierarchy.MEK_LEN) { "key must be ${KeyHierarchy.MEK_LEN} bytes" }
    }

    private val random = SecureRandom()

    override fun encrypt(plaintext: String): String {
        val nonce = ByteArray(NONCE_LEN).also(random::nextBytes)
        val sealed = KeyHierarchy.aeadEncrypt(key, plaintext.encodeToByteArray(), nonce)
        val buf = ByteArray(MAGIC_BYTES.size + 1 + sealed.size)
        MAGIC_BYTES.copyInto(buf)
        buf[MAGIC_BYTES.size] = CRYPTO_FORMAT_VERSION.toByte()
        sealed.copyInto(buf, MAGIC_BYTES.size + 1)
        return Base64.getEncoder().encodeToString(buf)
    }

    override fun decrypt(ciphertext: String): String {
        val buf = try {
            Base64.getDecoder().decode(ciphertext)
        } catch (e: IllegalArgumentException) {
            throw WrongCredentialException("value blob is not valid base64", e)
        }
        require(buf.size >= HEADER_LEN) { "value blob too short: ${buf.size}" }
        require(buf.copyOfRange(0, MAGIC_BYTES.size).contentEquals(MAGIC_BYTES)) { "bad magic" }
        val version = buf[MAGIC_BYTES.size].toInt() and 0xFF
        require(version == CRYPTO_FORMAT_VERSION) { "unsupported formatVersion: $version" }
        val plaintext = KeyHierarchy.aeadDecrypt(key, buf.copyOfRange(HEADER_LEN, buf.size))
        return plaintext.decodeToString()
    }

    public companion object {
        public const val CRYPTO_FORMAT_VERSION: Int = 1
        public const val MAGIC: String = "MTV1"
        public val MAGIC_BYTES: ByteArray = MAGIC.encodeToByteArray()
        public const val NONCE_LEN: Int = 24
        /** 4 magic + 1 version; nonce + ct||tag follow and are consumed by aeadDecrypt. */
        public const val HEADER_LEN: Int = 5
    }
}
