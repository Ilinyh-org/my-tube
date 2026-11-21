package org.mytube.core

/**
 * Playback command surface, independent of any audio engine (spec §3.1 #3).
 * The desktop engine selection is deferred to Phase 4; the Android engine is
 * Media3. Call sites depend only on this interface.
 */
interface PlaybackEngine {
    /** Prepares [source] and starts playback. [source] is engine-specific. */
    fun play(source: PlaybackSource)

    /** Pauses without discarding the prepared source. */
    fun pause()

    /** Resumes a paused playback. */
    fun resume()

    /** Stops playback and releases the prepared source. */
    fun stop()

    /** Seeks to the given position; unsupported engines throw [UnsupportedOperationException]. */
    fun seekTo(positionMs: Long)

    /** Current playback position in milliseconds. */
    fun currentPositionMs(): Long
}

/** Describes what to play without tying the port to any engine or codec API. */
data class PlaybackSource(
    /**
     * Engine-interpretable locator. For encrypted library content this is a
     * handle the platform integration resolves (decrypted stream, temp file,
     * or streaming decryptor) — never a plaintext cloud URL (spec §3.1 #6).
     */
    val locator: String,
    /** True when the underlying content requires decryption before/at playback. */
    val encrypted: Boolean = false,
)
