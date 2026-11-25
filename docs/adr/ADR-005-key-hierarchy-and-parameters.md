# ADR-005: Cryptographic Key Hierarchy and Parameters

- **Status:** Accepted (owner approval 2026-11-24; implementation this ADR governs
  has started; production use additionally requires security review)
- **Date:** 2026-11-19
- **Deciders:** my-tube-bot (draft), owner (review)

## Context

Each library has one random Master Encryption Key (MEK) protecting all
encrypted library content (spec §6). The user protects access with a password
(spec §6.3); a recovery key must independently unlock the MEK (spec §6.4).
Password changes must only re-wrap the MEK. The exact hierarchy, Argon2id
parameters, nonce strategy, wrapping format, and versioning must be documented
before implementation.

## Decision (proposed)

### Key hierarchy

```
Password ──Argon2id──▶ Password Key (KEK-p)  ──┐
                                               ├──▶ each wraps a copy of the MEK
Recovery Key (KEK-r, random) ──────────────────┘
MEK (random 32 bytes) ──▶ encrypts all library content (via per-file keys, later ADR)
```

- **MEK:** 32 random bytes from the platform secure RNG, generated at library
  creation. Never leaves the device except wrapped.
- **Password KEK (KEK-p):** Argon2id(password, salt) output, 32 bytes.
- **Recovery KEK (KEK-r):** 32 random bytes, generated at library creation,
  shown to the user as a base32-formatted string (with checksum) exactly once;
  never uploaded unwrapped or logged.
- Both KEKs wrap their own copy of the MEK (two wrapped blobs). Password
  change = decrypt MEK with old KEK-p, re-wrap with new KEK-p. Recovery-key
  change = re-wrap with new KEK-r. Password loss → unlock via KEK-r.

### Argon2id parameters (proposed)

- `m = 65536` KiB (64 MiB), `t = 3`, `p = 1`, output 32 bytes, salt 16 random
  bytes per derivation. (Above OWASP minimum guidance; bounded to keep mobile
  unlock under ~1 s. Parameter values are stored in every wrapped blob, so
  future strengthening only affects new wraps/unlocks.)

### Nonce strategy

XChaCha20-Poly1305 with a **random 24-byte nonce per encryption operation**
(XChaCha's 192-bit nonce makes collision probability negligible; no counter
state to persist). Nonces are stored adjacent to ciphertext, never reused with
the same key.

### Wrapped-MEK blob format (v1)

```
magic "MTW1" | u8 formatVersion (=1) | u8 kdfId (=argon2id=1)
u32 kdf m | u32 kdf t | u8 kdf p | 16B salt
24B nonce | u32 wrappedLen | MEK wrapped (XChaCha20-Poly1305 AEAD)
```

- Blob type (password-wrap vs recovery-wrap) is recorded by the storage layout,
  not inside the blob; a v2 may add an explicit purpose tag if needed.
- Every versioned crypto container implements `VersionedCryptoContainer`
  (already defined in `:crypto`); version bumps require a documented migration.

### In-memory key handling

Derived KEKs and the MEK exist in memory only while a library is unlocked;
locking/closing clears references and requests GC. Keys are never logged,
never written to the database or cloud, never placed in collections that
outlive the unlock (repo AGENTS.md §4.2).

## Alternatives considered

1. **Single KDF output used both for wrapping and content encryption:**
   rejected — key separation (wrap keys vs content keys) limits blast radius.
2. **AES-KW / AES-GCM for wrapping:** rejected — stack mandates XChaCha20-
   Poly1305 (or approved equivalent); key wrapping with a standard AEAD is
   simpler and avoids key-hiding subtleties of AES-KW.
3. **Argon2id with lower memory (19 MiB minimum):** rejected in favor of
   64 MiB — offline attack resistance is the primary control; parameters are
   stored per blob so they can rise later without format changes.
4. **Deterministic nonce via counter file:** rejected — persistent counter
   state is failure-prone across interrupted syncs; XChaCha's nonce size makes
   random nonces safe.

## Consequences

- (+) Password change and recovery-key rotation are O(1) re-wraps; content is
  never re-encrypted on credential changes.
- (+) All parameters self-describing in blobs; upgrades are per-object and lazy.
- (−) Unlock cost ~0.5–1 s (Argon2id at proposed params) per library.
- (−) Two wrapped blobs per library must be kept consistent (both re-written
  atomically on credential change; interrupted-change handling follows the
  sync ADR).

## Security and compatibility implications

- Wrong password/recovery key is detected by AEAD failure (no oracle beyond
  that); tests must verify rejection and tamper detection (§4.4).
- Recovery key display/confirmation flow is governed by spec §6.4; the key is
  never uploaded automatically and the user must confirm saving it.
- This ADR does not define the encrypted audio file format, DB value
  encryption (ADR-004), or the library-access file format; those are separate
  decisions that consume this hierarchy.
