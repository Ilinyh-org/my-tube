package org.mytube.crypto

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * ADR-004 value-encryption format tests (security properties per repo
 * AGENTS.md §4.4 style: wrong-key rejection, tamper detection, nonce
 * freshness).
 */
class XChaCha20ValueCipherTest {
    private val key = KeyHierarchy.randomMek()

    @Test
    fun `roundtrip preserves plaintext`() {
        val cipher = XChaCha20ValueCipher(key)
        for (value in listOf("Track 01", "Édith Piaf — Non, je ne regrette rien", "", "tags/value 🎵")) {
            assertEqualsOriginal(value, cipher.decrypt(cipher.encrypt(value)))
        }
    }

    @Test
    fun `encryption is randomized - same plaintext yields different ciphertexts`() {
        val cipher = XChaCha20ValueCipher(key)
        assertNotEquals(cipher.encrypt("Song"), cipher.encrypt("Song"))
    }

    @Test
    fun `ciphertext hides plaintext`() {
        val stored = XChaCha20ValueCipher(key).encrypt("Private Song Title")
        assertTrue(!stored.contains("Private") && !stored.contains("Song"))
    }

    @Test
    fun `wrong key is rejected`() {
        val cipher = XChaCha20ValueCipher(key)
        val other = XChaCha20ValueCipher(KeyHierarchy.randomMek())
        val stored = cipher.encrypt("Song")
        assertFailsWith<WrongCredentialException> { other.decrypt(stored) }
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val cipher = XChaCha20ValueCipher(key)
        val stored = cipher.encrypt("Song")
        val buf = java.util.Base64.getDecoder().decode(stored)
        buf[buf.size - 1] = (buf[buf.size - 1].toInt() xor 0x01).toByte()
        val tampered = java.util.Base64.getEncoder().encodeToString(buf)
        assertFailsWith<WrongCredentialException> { cipher.decrypt(tampered) }
    }

    @Test
    fun `malformed input is rejected`() {
        val cipher = XChaCha20ValueCipher(key)
        assertFailsWith<WrongCredentialException> { cipher.decrypt("not base64!!") }
        assertFailsWith<IllegalArgumentException> {
            cipher.decrypt(java.util.Base64.getEncoder().encodeToString(ByteArray(8)))
        }
    }

    @Test
    fun `format version must match`() {
        val cipher = XChaCha20ValueCipher(key)
        val raw = java.util.Base64.getDecoder().decode(cipher.encrypt("Song"))
        raw[4] = 99
        assertFailsWith<IllegalArgumentException> {
            cipher.decrypt(java.util.Base64.getEncoder().encodeToString(raw))
        }
    }

    private fun assertEqualsOriginal(expected: String, actual: String) {
        kotlin.test.assertEquals(expected, actual)
    }
}
