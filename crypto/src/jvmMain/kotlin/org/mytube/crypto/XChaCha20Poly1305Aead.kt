package org.mytube.crypto

import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter

/**
 * AEAD_XChaCha20_Poly1305 per draft-irtf-cfrg-xchacha (§2):
 * HChaCha20 subkey from key + first 16 nonce bytes, then RFC 8439
 * AEAD_CHACHA20_POLY1305 with the remaining 8 nonce bytes prefixed by
 * 4 NUL bytes. BouncyCastle 1.79 ships no XChaCha engine, so the standard
 * composition is assembled here; the HChaCha20 block function is pinned by
 * the draft's §2.2.1 test vector (XChaChaVectorTest) and the AEAD core is
 * BouncyCastle's reviewed RFC 8439 implementation.
 */
internal object XChaCha20Poly1305Aead {
    private val SIGMA = intArrayOf(0x61707865, 0x3320646e, 0x79622d32, 0x6b206574)

    /** AEAD encrypt; returns ciphertext||tag (16-byte tag). */
    fun encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
        require(key.size == 32) { "key must be 32 bytes" }
        require(nonce.size == 24) { "nonce must be 24 bytes" }
        val sub = hchacha20(key, nonce.copyOfRange(0, 16))
        val iv = ByteArray(12).also { nonce.copyInto(it, 4, 16, 24) }
        val engine = ChaCha20Poly1305()
        engine.init(true, AEADParameters(KeyParameter(sub), 128, iv))
        val out = ByteArray(plaintext.size + 16)
        val n = engine.processBytes(plaintext, 0, plaintext.size, out, 0)
        engine.doFinal(out, n)
        return out
    }

    /** AEAD decrypt; throws BouncyCastle InvalidCipherTextException on auth failure. */
    fun decrypt(key: ByteArray, nonce: ByteArray, data: ByteArray): ByteArray {
        require(key.size == 32) { "key must be 32 bytes" }
        require(nonce.size == 24) { "nonce must be 24 bytes" }
        require(data.size >= 16) { "data too short" }
        val sub = hchacha20(key, nonce.copyOfRange(0, 16))
        val iv = ByteArray(12).also { nonce.copyInto(it, 4, 16, 24) }
        val engine = ChaCha20Poly1305()
        engine.init(false, AEADParameters(KeyParameter(sub), 128, iv))
        val out = ByteArray(data.size - 16)
        val n = engine.processBytes(data, 0, data.size, out, 0)
        engine.doFinal(out, n)
        return out
    }


    /** HChaCha20 block function (draft-irtf-cfrg-xchacha §2.2). */
    fun hchacha20(key: ByteArray, nonce: ByteArray): ByteArray {
        require(key.size == 32) { "key must be 32 bytes" }
        require(nonce.size == 16) { "hchacha nonce must be 16 bytes" }
        val st = IntArray(16)
        SIGMA.copyInto(st)
        for (i in 0 until 8) st[4 + i] = loadLe(key, i * 4)
        for (i in 0 until 4) st[12 + i] = loadLe(nonce, i * 4)
        repeat(10) {
            quarterRound(st, 0, 4, 8, 12); quarterRound(st, 1, 5, 9, 13)
            quarterRound(st, 2, 6, 10, 14); quarterRound(st, 3, 7, 11, 15)
            quarterRound(st, 0, 5, 10, 15); quarterRound(st, 1, 6, 11, 12)
            quarterRound(st, 2, 7, 8, 13); quarterRound(st, 3, 4, 9, 14)
        }
        val out = ByteArray(32)
        for (i in 0 until 4) storeLe(out, i * 4, st[i])
        for (i in 0 until 4) storeLe(out, 16 + i * 4, st[12 + i])
        return out
    }

    private fun quarterRound(s: IntArray, a: Int, b: Int, c: Int, d: Int) {
        s[a] += s[b]; s[d] = (s[d] xor s[a]).rotateLeft(16)
        s[c] += s[d]; s[b] = (s[b] xor s[c]).rotateLeft(12)
        s[a] += s[b]; s[d] = (s[d] xor s[a]).rotateLeft(8)
        s[c] += s[d]; s[b] = (s[b] xor s[c]).rotateLeft(7)
    }

    private fun loadLe(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun storeLe(b: ByteArray, o: Int, v: Int) {
        b[o] = v.toByte(); b[o + 1] = (v ushr 8).toByte()
        b[o + 2] = (v ushr 16).toByte(); b[o + 3] = (v ushr 24).toByte()
    }
}
