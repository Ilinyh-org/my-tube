package org.mytube.core

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Fake [KeyOperations] that tracks protectors and rejects mismatches.
 */
private class ProtectorAwareFake(private val protectors: MutableMap<String, KeyProtector>) : KeyOperations {
    private val meks = mutableListOf<ByteArray>()

    override fun generateMek(): ByteArray = ByteArray(32) { meks.size.toByte() }.also { meks += it }

    override fun wrap(mek: ByteArray, protector: KeyProtector): WrappedKey {
        val id = "${protector.javaClass.simpleName}:${identifier(protector)}"
        protectors[id] = protector
        return WrappedKey(ByteArray(mek.size) { (mek[it].toInt() xor id.hashCode()).toByte() }, 1)
    }

    override fun unwrap(wrapped: WrappedKey, protector: KeyProtector): ByteArray {
        val id = "${protector.javaClass.simpleName}:${identifier(protector)}"
        if (protectors[id] != protector) throw IllegalArgumentException("protector mismatch")
        // XOR is symmetric; recover any MEK wrapped with the same protector id.
        return ByteArray(wrapped.keyMaterial.size) { (wrapped.keyMaterial[it].toInt() xor id.hashCode()).toByte() }
    }

    private fun identifier(p: KeyProtector): String = when (p) {
        is KeyProtector.Password -> p.value
        is KeyProtector.RecoveryKey -> p.value
    }
}

class LibraryKeyLifecycleTest {

    private fun awareFake() = ProtectorAwareFake(mutableMapOf())

    @Test
    fun `begin creation generates independent MEK and both wrappings`() {
        val ops = awareFake()
        val lifecycle = LibraryKeyLifecycle(ops)
        val created = lifecycle.beginCreation(KeyProtector.Password("correct horse"))
        assertTrue(created.mek.size == 32)
        assertTrue(created.recoveryKey.value.matches(Regex("^[0-9a-f]{64}$")), created.recoveryKey.value)
        assertNotEquals(created.passwordWrapped, created.recoveryWrapped)
    }

    @Test
    fun `recovery keys are unique per library creation`() {
        val ops = awareFake()
        val lifecycle = LibraryKeyLifecycle(ops)
        val a = lifecycle.beginCreation(KeyProtector.Password("p1"))
        val b = lifecycle.beginCreation(KeyProtector.Password("p1"))
        assertNotEquals(a.recoveryKey, b.recoveryKey)
        assertNotEquals(a.mek.toList(), b.mek.toList())
    }

    @Test
    fun `complete creation returns stored wrappings without exposing MEK or recovery key`() {
        val ops = awareFake()
        val lifecycle = LibraryKeyLifecycle(ops)
        val created = lifecycle.beginCreation(KeyProtector.Password("pw"))
        val stored = lifecycle.completeCreation(created)
        assertEquals(created.passwordWrapped, stored.passwordWrapped)
        assertEquals(created.recoveryWrapped, stored.recoveryWrapped)
    }

    @Test
    fun `password change rewraps MEK only and keeps recovery wrapping untouched`() {
        val ops = awareFake()
        val lifecycle = LibraryKeyLifecycle(ops)
        val created = lifecycle.beginCreation(KeyProtector.Password("old"))
        val stored = lifecycle.completeCreation(created)
        val changed = lifecycle.changePassword(
            stored,
            current = KeyProtector.Password("old"),
            newPassword = KeyProtector.Password("new"),
        )
        // Recovery wrapping untouched (§6.3 re-wrap only).
        assertEquals(stored.recoveryWrapped, changed.recoveryWrapped)
        assertNotEquals(stored.passwordWrapped, changed.passwordWrapped)
        // Both old-recovery and new password still unwrap the same MEK.
        val viaRecovery = ops.unwrap(changed.recoveryWrapped, created.recoveryKey)
        val viaNewPassword = ops.unwrap(changed.passwordWrapped, KeyProtector.Password("new"))
        assertContentEquals(viaRecovery, viaNewPassword)
    }

    @Test
    fun `recovery with recovery key sets a new password`() {
        val ops = awareFake()
        val lifecycle = LibraryKeyLifecycle(ops)
        val created = lifecycle.beginCreation(KeyProtector.Password("lost"))
        val stored = lifecycle.completeCreation(created)
        val recovered = lifecycle.recoverWithRecoveryKey(
            stored,
            recoveryKey = created.recoveryKey,
            newPassword = KeyProtector.Password("fresh"),
        )
        assertEquals(stored.recoveryWrapped, recovered.recoveryWrapped)
        val viaNewPassword = ops.unwrap(recovered.passwordWrapped, KeyProtector.Password("fresh"))
        val viaRecovery = ops.unwrap(recovered.recoveryWrapped, created.recoveryKey)
        assertContentEquals(viaRecovery, viaNewPassword)
    }

    @Test
    fun `wrong recovery key is rejected`() {
        val ops = awareFake()
        val lifecycle = LibraryKeyLifecycle(ops)
        val created = lifecycle.beginCreation(KeyProtector.Password("lost"))
        val stored = lifecycle.completeCreation(created)
        val wrong = KeyProtector.RecoveryKey("0".repeat(64))
        try {
            lifecycle.recoverWithRecoveryKey(stored, wrong, KeyProtector.Password("x"))
            fail("wrong recovery key must fail")
        } catch (e: IllegalArgumentException) {
            // expected: protector mismatch
        }
    }
}
