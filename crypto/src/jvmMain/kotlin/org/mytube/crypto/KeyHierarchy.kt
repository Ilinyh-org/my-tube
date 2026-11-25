package org.mytube.crypto

import java.security.SecureRandom
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

/**
 * ADR-005 key hierarchy for the JVM target (Argon2id + XChaCha20-Poly1305 via
 * BouncyCastle). Keys exist only in byte arrays; callers must release them
 * with [zeroKey] when a library is locked.
 */
public object KeyHierarchy {
    public const val MEK_LEN: Int = 32
    public const val DEFAULT_KDF_MEMORY_KIB: Int = 65536 // 64 MiB
    public const val DEFAULT_KDF_ITERATIONS: Int = 3
    public const val DEFAULT_KDF_PARALLELISM: Int = 1

    private val random = SecureRandom()

    /** Derive a 32-byte KEK from a password with Argon2id (ADR-005 parameters). */
    public fun derivePasswordKek(
        password: CharArray,
        salt: ByteArray,
        memoryKiB: Int = DEFAULT_KDF_MEMORY_KIB,
        iterations: Int = DEFAULT_KDF_ITERATIONS,
        parallelism: Int = DEFAULT_KDF_PARALLELISM,
        outputLen: Int = 32,
    ): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withParallelism(parallelism)
            .withMemoryAsKB(memoryKiB)
            .withIterations(iterations)
            .build()
        val generator = Argon2BytesGenerator()
        generator.init(params)
        val out = ByteArray(outputLen)
        generator.generateBytes(password, out)
        return out
    }

    /** XChaCha20-Poly1305 encrypt: returns nonce || ciphertext||tag. */
    public fun aeadEncrypt(key: ByteArray, plaintext: ByteArray, nonce: ByteArray = randomNonce()): ByteArray {
        val ct = XChaCha20Poly1305Aead.encrypt(key, nonce, plaintext)
        val out = nonce + ct
        return out
    }

    /** XChaCha20-Poly1305 decrypt from nonce || ciphertext||tag; throws on auth failure. */
    public fun aeadDecrypt(key: ByteArray, data: ByteArray): ByteArray {
        require(data.size >= 24 + 16) { "data too short" }
        val nonce = data.copyOfRange(0, 24)
        return try {
            XChaCha20Poly1305Aead.decrypt(key, nonce, data.copyOfRange(24, data.size))
        } catch (e: WrongCredentialException) {
            throw e
        } catch (e: Exception) {
            // AEAD auth failure (wrong key / tampered data) surfaces as
            // WrongCredentialException; other failures keep their type.
            if (e.javaClass.name.endsWith("InvalidCipherTextException")) {
                throw WrongCredentialException("authentication failed", e)
            }
            throw e
        }
    }

    /**
     * Wrap the MEK under [kek] (already derived, e.g. from password or the
     * random recovery key). [kdfSalt]/params are recorded verbatim in the v1
     * blob so [unwrapWithPassword] can re-derive the KEK. ADR-005 format.
     */
    public fun wrapMek(
        mek: ByteArray,
        kek: ByteArray,
        kdfSalt: ByteArray = ByteArray(MekWrapBlob.SALT_LEN),
        kdfMemoryKiB: Int = DEFAULT_KDF_MEMORY_KIB,
        kdfIterations: Int = DEFAULT_KDF_ITERATIONS,
        kdfParallelism: Int = DEFAULT_KDF_PARALLELISM,
    ): ByteArray {
        require(mek.size == MEK_LEN) { "MEK must be 32 bytes" }
        require(kdfSalt.size == MekWrapBlob.SALT_LEN) { "salt must be 16 bytes" }
        val sealed = aeadEncrypt(kek, mek)
        return MekWrapBlob(
            kdfId = MekWrapBlob.KDF_ARGON2ID,
            kdfMemoryKiB = kdfMemoryKiB,
            kdfIterations = kdfIterations,
            kdfParallelism = kdfParallelism,
            salt = kdfSalt,
            nonce = sealed.copyOfRange(0, 24),
            wrappedMek = sealed.copyOfRange(24, sealed.size),
        ).encode()
    }

    /** Wrap the MEK under a password: fresh salt, Argon2id KEK, v1 blob. */
    public fun wrapMekWithPassword(password: CharArray, mek: ByteArray): ByteArray {
        val salt = ByteArray(MekWrapBlob.SALT_LEN).also(random::nextBytes)
        val kek = derivePasswordKek(password, salt)
        return try {
            wrapMek(mek, kek, kdfSalt = salt)
        } finally {
            zeroKey(kek)
        }
    }

    /**
     * Unwrap the MEK from a blob using [kek] (already derived). Throws
     * [WrongCredentialException] on AEAD failure (wrong credential or tamper).
     */
    public fun unwrapMek(blobBytes: ByteArray, kek: ByteArray): ByteArray =
        try {
            val blob = MekWrapBlob.decode(blobBytes)
            val data = blob.nonce + blob.wrappedMek
            aeadDecrypt(kek, data)
        } catch (e: WrongCredentialException) {
            throw e
        } catch (e: Exception) {
            throw WrongCredentialException("MEK unwrap failed", e)
        }

    /** Convenience: derive the KEK from a password per blob parameters, then unwrap. */
    public fun unwrapWithPassword(blobBytes: ByteArray, password: CharArray): ByteArray {
        val blob = MekWrapBlob.decode(blobBytes)
        require(blob.kdfId == MekWrapBlob.KDF_ARGON2ID) { "unsupported kdfId ${blob.kdfId}" }
        val kek = derivePasswordKek(password, blob.salt, blob.kdfMemoryKiB, blob.kdfIterations, blob.kdfParallelism)
        return try {
            unwrapMek(blobBytes, kek)
        } finally {
            zeroKey(kek)
        }
    }

    public fun randomNonce(): ByteArray = ByteArray(24).also(random::nextBytes)

    public fun randomMek(): ByteArray = ByteArray(MEK_LEN).also(random::nextBytes)
}

/** Thrown when a password or recovery key fails AEAD verification. */
public class WrongCredentialException(message: String, cause: Throwable? = null) : Exception(message, cause)
