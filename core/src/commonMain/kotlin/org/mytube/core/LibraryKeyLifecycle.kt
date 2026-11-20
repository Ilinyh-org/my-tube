package org.mytube.core

/**
 * Orchestration of library key lifecycle: creation, password change, recovery.
 * Pure flow logic over [KeyOperations]; all concrete cryptography is deferred
 * to the `:crypto` module (ADR-004/ADR-005 still Proposed — repo AGENTS.md §12).
 *
 * Spec references: §6.2 (MEK), §6.3 (password, re-wrap only), §6.4 (recovery key).
 */
class LibraryKeyLifecycle(private val keyOperations: KeyOperations) {

    /** Key material for a library awaiting recovery-key confirmation. */
    data class CreatedKeys(
        val mek: ByteArray,
        /** Recovery key, shown only during creation or explicit export (§6.4). */
        val recoveryKey: KeyProtector.RecoveryKey,
        /** MEK wrapped under the user's password. */
        val passwordWrapped: WrappedKey,
        /** MEK wrapped under the recovery key. */
        val recoveryWrapped: WrappedKey,
    )

    /**
     * Begins library key creation: MEK + recovery key generation, both wrappings.
     * Completion is gated by [completeCreation] (§6.4: the user must confirm the
     * recovery key has been saved before library creation completes).
     */
    fun beginCreation(password: KeyProtector.Password): CreatedKeys {
        val mek = keyOperations.generateMek()
        val recoveryKey = KeyProtector.RecoveryKey(SecureRandomBytes.nextHex(RECOVERY_KEY_BYTES))
        return CreatedKeys(
            mek = mek,
            recoveryKey = recoveryKey,
            passwordWrapped = keyOperations.wrap(mek, password),
            recoveryWrapped = keyOperations.wrap(mek, recoveryKey),
        )
    }

    /**
     * Confirms the recovery key was saved and returns the stored wrappings.
     * The recovery key material must already have been shown exactly once; it is
     * deliberately NOT returned here (§6.4).
     */
    fun completeCreation(created: CreatedKeys): StoredKeyWrappings =
        StoredKeyWrappings(
            passwordWrapped = created.passwordWrapped,
            recoveryWrapped = created.recoveryWrapped,
        )

    /**
     * Changes the password by re-wrapping the MEK only. Any wrapped material
     * other than the new password wrapping is untouched (§6.3: password change
     * must not re-encrypt library data).
     */
    fun changePassword(
        stored: StoredKeyWrappings,
        current: KeyProtector,
        newPassword: KeyProtector.Password,
    ): StoredKeyWrappings {
        val mek = keyOperations.unwrap(stored.passwordWrapped, current)
        return stored.copy(passwordWrapped = keyOperations.wrap(mek, newPassword))
    }

    /**
     * Recovers access with the recovery key by unwrapping the MEK through the
     * recovery wrapping and re-wrapping under a new password (§6.4: recovery
     * allows setting a new password).
     */
    fun recoverWithRecoveryKey(
        stored: StoredKeyWrappings,
        recoveryKey: KeyProtector.RecoveryKey,
        newPassword: KeyProtector.Password,
    ): StoredKeyWrappings {
        val mek = keyOperations.unwrap(stored.recoveryWrapped, recoveryKey)
        return stored.copy(passwordWrapped = keyOperations.wrap(mek, newPassword))
    }

    companion object {
        /** Recovery key length in bytes (256-bit; ADR-005 pending, size fixed here as the flow contract). */
        const val RECOVERY_KEY_BYTES: Int = 32
    }
}

/** Wrappings persisted to the library's cloud storage (§6.2: encrypted MEK only). */
data class StoredKeyWrappings(
    val passwordWrapped: WrappedKey,
    val recoveryWrapped: WrappedKey,
)
