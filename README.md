# my-tube

Cross-platform encrypted music player (Android, Linux, Windows). WebDAV/S3 cloud
sync, end-to-end encryption, no central backend.

See `AGENTS.md` (agent instructions, architecture, security requirements) and
`docs/TECHICAL_SPECIFICATION.md` (authoritative product specification).

## Build

Requires JDK 17+ and Gradle (or the wrapper once committed).

```
./gradlew build      # build + tests for all locally supported targets
```

## Modules

- `core` — shared business logic and domain models.
- `crypto` — key derivation, encryption, key management (no production format before ADR review).

## Note

Android-target builds require the Android SDK (owner-gated; not installed in bot
environments). Desktop UI and playback modules follow ADR decisions (repo AGENTS.md §12).
