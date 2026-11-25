# ADR-004: Local Database Encryption


Note: accepted via PR #5 merge; implementation pending.
- **Status:** Accepted (owner approval 2026-11-24; implementation this ADR governs)
- **Date:** 2026-11-19
- **Deciders:** my-tube-bot (draft), owner (review)

## Context

The local database (SQLDelight) stores library metadata, playlists, listening
history, and playback state. Per the specification (§6.5), local metadata and
database contents must be protected at rest. SQLDelight provides database
access and mapping, not encryption. The protection mechanism must work on all
three platforms: Android, Linux, Windows.

Threat model: an attacker with access to the device filesystem (lost device,
stolen backup, malware reading user files) must not read library metadata in
plaintext. This is defense-in-depth on top of E2E encryption for cloud content.

## Decision (proposed)

**Application-level, value encryption driven by the `crypto` module**, not a
native whole-database encryption library:

1. All privacy-relevant column values (track titles, artist/album names,
   genres, tags, playlist names/descriptions, history entries) are encrypted
   **before** being passed to SQLDelight, using XChaCha20-Poly1305 with keys
   derived from the library's unlocked key material (see ADR-005).
2. Non-sensitive columns (opaque random IDs, sizes, durations, timestamps used
   for sorting) remain plaintext to allow indexing and range queries.
3. Text search operates on decrypted in-memory metadata, not on SQL `LIKE`.
4. The database file itself may additionally be protected per-platform in a
   later ADR if a whole-file solution proves necessary; this is explicitly
   out of scope here.

## Alternatives considered

1. **SQLCipher (or similar SQLite encryption extension):** mature whole-file
   encryption on Android; however, desktop JVM integration requires
   platform-specific native binaries for Linux and Windows, complicating the
   KMP build, and the encryption key would still need to come from our key
   hierarchy. Rejected for now: build complexity and platform variance.
2. **OS-level protection only (e.g. Android file-based encryption, BitLocker,
   LUKS):** protects against device theft but not against other users/processes
   on the same machine reading user files. Insufficient as sole measure;
   remains valuable as an additional layer.
3. **Encrypting the whole DB file at the file level and keeping it closed
   while the app runs:** incompatible with SQLDelight's expectation of an open
   SQLite file; would require a virtual filesystem layer. Rejected.

## Consequences

- (+) Single encryption implementation shared across all platforms (pure Kotlin
  in `crypto`, see ADR-005); no native dependencies added.
- (+) Column-level design keeps useful plaintext indexes for queries.
- (−) Encrypted columns cannot be queried, joined, or sorted by the database
  engine; filtering happens in memory after decryption.
- (−) Slightly larger DB rows (nonce + auth tag overhead per value).
- Security: no plaintext private metadata at rest; keys never stored in the
  database; locking the library drops derived keys from memory, making the
  remaining data unreadable ciphertext.

## Security and compatibility implications

- Must never log decrypted values or keys (repo AGENTS.md §4.2).
- Encryption format for stored values is versioned per ADR-005 rules so future
  migrations are possible without data loss.
- Compatibility: existing databases created before this ADR do not exist yet
  (greenfield); the first released schema will include this encryption, so no
  migration burden.
