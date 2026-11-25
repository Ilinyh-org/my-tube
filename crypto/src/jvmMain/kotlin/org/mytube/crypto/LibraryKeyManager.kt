package org.mytube.crypto

/**
 * Library key lifecycle (ADR-005): creation, unlock via password or recovery
 * key, password change, recovery-key rotation. All credential changes are
 * O(1) MEK re-wraps; content is never re-encrypted.
 */
public class LibraryKeyManager {

    /** Credentials produced at library creation. */
    public class LibraryCredentials(
        public val mek: ByteArray,
        public val recoveryKey: ByteArray,
        public val recoveryKeyDisplay: String,
        public val passwordBlob: ByteArray,
        public val recoveryBlob: ByteArray,
    )

    /** Create a new MEK, recovery key, and both wrapped blobs. */
    public fun createLibrary(password: CharArray): LibraryCredentials {
        val mek = KeyHierarchy.randomMek()
        val recoveryKey = KeyHierarchy.randomMek()
        val passwordBlob = KeyHierarchy.wrapMekWithPassword(password, mek)
        // ADR-005: the recovery key wraps a copy of the MEK (KEK-r is random,
        // used directly as the AEAD key).
        val recoveryBlob = KeyHierarchy.wrapMek(mek, recoveryKey)
        return LibraryCredentials(
            mek = mek,
            recoveryKey = recoveryKey,
            recoveryKeyDisplay = RecoveryKeyCodec.encode(recoveryKey),
            passwordBlob = passwordBlob,
            recoveryBlob = recoveryBlob,
        )
    }

    /** Unlock the MEK with the user's password; throws [WrongCredentialException] on failure. */
    public fun unlockWithPassword(passwordBlob: ByteArray, password: CharArray): ByteArray =
        KeyHierarchy.unwrapWithPassword(passwordBlob, password)

    /** Unlock the MEK with the recovery key (display form accepted); throws on failure. */
    public fun unlockWithRecoveryKey(recoveryBlob: ByteArray, recoveryKeyDisplay: String): ByteArray {
        val recoveryKey = RecoveryKeyCodec.decode(recoveryKeyDisplay)
        return KeyHierarchy.unwrapMek(recoveryBlob, recoveryKey)
    }

    /**
     * Change the password: verify old password, re-wrap MEK under the new one.
     * Returns the new password blob; the MEK and recovery blob are unchanged.
     */
    public fun changePassword(
        passwordBlob: ByteArray,
        oldPassword: CharArray,
        newPassword: CharArray,
        mekOut: ByteArray? = null,
    ): ByteArray {
        val mek = unlockWithPassword(passwordBlob, oldPassword)
        try {
            mekOut?.let { mek.copyInto(it) }
            return KeyHierarchy.wrapMekWithPassword(newPassword, mek)
        } finally {
            zeroKey(mek)
        }
    }

    /** Rotate the recovery key: re-wrap the MEK under a fresh recovery key. */
    public fun rotateRecoveryKey(
        recoveryBlob: ByteArray,
        currentRecoveryKeyDisplay: String,
    ): Pair<ByteArray, String> {
        val mek = unlockWithRecoveryKey(recoveryBlob, currentRecoveryKeyDisplay)
        val newRecovery = KeyHierarchy.randomMek()
        return try {
            // Wrap a copy of the MEK under the fresh recovery key (ADR-005).
            val newBlob = KeyHierarchy.wrapMek(mek, newRecovery)
            Pair(newBlob, RecoveryKeyCodec.encode(newRecovery))
        } finally {
            zeroKey(mek)
            zeroKey(newRecovery)
        }
    }
}
