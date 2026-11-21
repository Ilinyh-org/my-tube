package org.mytube.core

/**
 * Credentials for a cloud storage provider (spec §5.1). Held locally only;
 * must never be included in synchronized library data (spec §3.1 #5).
 */
sealed interface CloudCredentials {
    data class Basic(val username: String, val password: String) : CloudCredentials
    data class ApiKey(val keyId: String, val secret: String) : CloudCredentials
    data class BearerToken(val token: String) : CloudCredentials
}

/** A single stored object, addressed by an opaque object name. */
data class StoredObject(
    /** Opaque storage-side name; must never reveal the original filename (spec §5). */
    val objectName: String,
    val sizeBytes: Long,
    /** ETag/revision token, when the backend provides one; used for change detection. */
    val etag: String? = null,
)

/**
 * Port for cloud storage backends (spec §3.1 #1/#2: business logic must not
 * depend on a particular provider; all providers implement this interface).
 * Implementations live in Phase 2 (WebDAV, S3-compatible). Operations are
 * content-agnostic: encryption is applied by callers, so nothing here knows
 * about keys or plaintext (spec §3.1 #6).
 */
interface StorageProvider {
    /** Verifies connectivity and write permission; throws on failure. */
    fun checkAccess()

    /** Lists stored objects under the given prefix (empty prefix = root). */
    fun list(prefix: String = ""): List<StoredObject>

    /** Reads the full content of an object. */
    fun get(objectName: String): ByteArray

    /** Creates or overwrites an object; returns the backend's etag if offered. */
    fun put(objectName: String, content: ByteArray): String?

    /** Deletes an object. Deleting a missing object must not fail. */
    fun delete(objectName: String)
}
