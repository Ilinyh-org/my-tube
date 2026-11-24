package org.mytube.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyHierarchyTest {

    private val manager = LibraryKeyManager()

    @Test
    fun `blob roundtrip preserves parameters`() {
        val blob = MekWrapBlob(
            kdfId = MekWrapBlob.KDF_ARGON2ID, kdfMemoryKiB = 65536, kdfIterations = 3,
            kdfParallelism = 1, salt = ByteArray(16) { it.toByte() },
            nonce = ByteArray(24) { (it * 3).toByte() }, wrappedMek = ByteArray(32) { 7 },
        )
        val decoded = MekWrapBlob.decode(blob.encode())
        assertEquals(blob.kdfId, decoded.kdfId)
        assertEquals(blob.kdfMemoryKiB, decoded.kdfMemoryKiB)
        assertEquals(blob.kdfIterations, decoded.kdfIterations)
        assertEquals(blob.kdfParallelism, decoded.kdfParallelism)
        assertContentEquals(blob.salt, decoded.salt)
        assertContentEquals(blob.nonce, decoded.nonce)
        assertContentEquals(blob.wrappedMek, decoded.wrappedMek)
    }

    @Test
    fun `blob rejects bad magic and truncation`() {
        val bytes = ByteArray(64) { 1 }
        assertFailsWith<IllegalArgumentException> { MekWrapBlob.decode(bytes) }
        val blob = MekWrapBlob(1, 65536, 3, 1, ByteArray(16), ByteArray(24), ByteArray(32))
        val enc = blob.encode()
        assertFailsWith<IllegalArgumentException> { MekWrapBlob.decode(enc.copyOfRange(0, 20)) }
        enc[10] = enc[10] // tamper wrappedLen path
        assertFailsWith<IllegalArgumentException> { MekWrapBlob.decode(enc.copyOfRange(0, enc.size - 1)) }
    }

    @Test
    fun `create library then unlock with password`() {
        val creds = manager.createLibrary("correct horse".toCharArray())
        assertEquals(32, creds.mek.size)
        assertTrue(creds.recoveryKeyDisplay.length >= 52)
        val mek = manager.unlockWithPassword(creds.passwordBlob, "correct horse".toCharArray())
        assertContentEquals(creds.mek, mek)
    }

    @Test
    fun `wrong password is rejected`() {
        val creds = manager.createLibrary("correct".toCharArray())
        assertFailsWith<WrongCredentialException> {
            manager.unlockWithPassword(creds.passwordBlob, "wrong".toCharArray())
        }
    }

    @Test
    fun `tampered blob is rejected`() {
        val creds = manager.createLibrary("pw".toCharArray())
        val tampered = creds.passwordBlob.copyOf()
        tampered[tampered.size - 1] = (tampered[tampered.size - 1] + 1).toByte()
        assertFailsWith<WrongCredentialException> {
            manager.unlockWithPassword(tampered, "pw".toCharArray())
        }
    }

    @Test
    fun `unlock via recovery key`() {
        val creds = manager.createLibrary("pw".toCharArray())
        val mek = manager.unlockWithRecoveryKey(creds.recoveryBlob, creds.recoveryKeyDisplay)
        assertContentEquals(creds.mek, mek)
        // corrupted display string must fail (checksum)
        assertFailsWith<IllegalArgumentException> {
            manager.unlockWithRecoveryKey(creds.recoveryBlob, creds.recoveryKeyDisplay.dropLast(1) + "A")
        }
    }

    @Test
    fun `password change re-wraps without touching recovery blob or mek`() {
        val creds = manager.createLibrary("old-pw".toCharArray())
        val mekOut = ByteArray(32)
        val newBlob = manager.changePassword(creds.passwordBlob, "old-pw".toCharArray(), "new-pw".toCharArray(), mekOut)
        assertContentEquals(creds.mek, mekOut)
        // old password no longer works
        assertFailsWith<WrongCredentialException> {
            manager.unlockWithPassword(newBlob, "old-pw".toCharArray())
        }
        // new password yields the same MEK
        val mek = manager.unlockWithPassword(newBlob, "new-pw".toCharArray())
        assertContentEquals(creds.mek, mek)
        // recovery blob still unlocks the same MEK
        assertContentEquals(creds.mek, manager.unlockWithRecoveryKey(creds.recoveryBlob, creds.recoveryKeyDisplay))
    }

    @Test
    fun `recovery key rotation re-wraps MEK`() {
        val creds = manager.createLibrary("pw".toCharArray())
        val (newBlob, newDisplay) = manager.rotateRecoveryKey(creds.recoveryBlob, creds.recoveryKeyDisplay)
        val mek = manager.unlockWithRecoveryKey(newBlob, newDisplay)
        assertContentEquals(creds.mek, mek)
        assertFailsWith<WrongCredentialException> {
            manager.unlockWithRecoveryKey(newBlob, creds.recoveryKeyDisplay)
        }
    }

    @Test
    fun `recovery codec roundtrip and checksum`() {
        val key = KeyHierarchy.randomMek()
        val display = RecoveryKeyCodec.encode(key)
        assertContentEquals(key, RecoveryKeyCodec.decode(display))
        assertFailsWith<IllegalArgumentException> {
            RecoveryKeyCodec.decode(display.drop(1) + (if (display.last() == 'A') "B" else "A"))
        }
    }

    @Test
    fun `hchacha20 matches draft-irtf-cfrg-xchacha section 2_2_1 vector`() {
        // Key = 00..1f, nonce = 00000009 0000004a 00000000 31415927,
        // expected subkey = 82413b42 27b27bfe d30e4250 8a877d73
        //                    a0f9e4d5 8a74a853 c12ec413 26d3ecdc.
        val key = ByteArray(32) { it.toByte() }
        val nonce = byteArrayOf(
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x09.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x4a.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x31.toByte(), 0x41.toByte(), 0x59.toByte(), 0x27.toByte(),
        )
        val expected = byteArrayOf(
            0x82.toByte(), 0x41.toByte(), 0x3b.toByte(), 0x42.toByte(),
            0x27.toByte(), 0xb2.toByte(), 0x7b.toByte(), 0xfe.toByte(),
            0xd3.toByte(), 0x0e.toByte(), 0x42.toByte(), 0x50.toByte(),
            0x8a.toByte(), 0x87.toByte(), 0x7d.toByte(), 0x73.toByte(),
            0xa0.toByte(), 0xf9.toByte(), 0xe4.toByte(), 0xd5.toByte(),
            0x8a.toByte(), 0x74.toByte(), 0xa8.toByte(), 0x53.toByte(),
            0xc1.toByte(), 0x2e.toByte(), 0xc4.toByte(), 0x13.toByte(),
            0x26.toByte(), 0xd3.toByte(), 0xec.toByte(), 0xdc.toByte(),
        )
        assertContentEquals(expected, XChaCha20Poly1305Aead.hchacha20(key, nonce))
    }

    @Test
    fun `aead roundtrip and nonce uniqueness`() {
        val key = KeyHierarchy.randomMek()
        val pt = "my-tube aead roundtrip".encodeToByteArray()
        val sealed1 = KeyHierarchy.aeadEncrypt(key, pt)
        val sealed2 = KeyHierarchy.aeadEncrypt(key, pt)
        // Random 24-byte nonces: same plaintext must produce different blobs.
        assertFalse(sealed1.contentEquals(sealed2))
        assertContentEquals(pt, KeyHierarchy.aeadDecrypt(key, sealed1))
        assertContentEquals(pt, KeyHierarchy.aeadDecrypt(key, sealed2))
        // Bit-flip in the tag must fail authentication.
        sealed1[sealed1.size - 1] = (sealed1[sealed1.size - 1] + 1).toByte()
        assertFailsWith<WrongCredentialException> { KeyHierarchy.aeadDecrypt(key, sealed1) }
    }

    @Test
    fun `wrong key is rejected by aead`() {
        val key = KeyHierarchy.randomMek()
        val sealed = KeyHierarchy.aeadEncrypt(key, "x".encodeToByteArray())
        assertFailsWith<WrongCredentialException> {
            KeyHierarchy.aeadDecrypt(KeyHierarchy.randomMek(), sealed)
        }
    }
}
