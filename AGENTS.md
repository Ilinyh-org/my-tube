# AGENTS.md

# Instructions for Coding Agents

## 1. Project Overview

This repository contains a cross-platform encrypted music player for:

- Android
- Linux
- Windows

The application allows users to manage, play, and synchronize their own music libraries using cloud storage.

The application has **no central backend, no central user accounts, and no central database**. Each device communicates directly with configured cloud storage providers.

The technical specification is the source of truth for product requirements:

- Read the technical specification before implementing features.
- Do not silently change agreed requirements.
- Do not introduce features that are outside the specification without approval.
- If the specification is ambiguous or contradictory, identify the issue and ask for clarification before making architectural decisions.

## 2. Technology Stack

The agreed technology stack is:

| Area | Technology |
|---|---|
| Programming language | Kotlin |
| Shared code | Kotlin Multiplatform |
| UI | Compose Multiplatform |
| Android playback | Android Media3 |
| Desktop playback | Platform-specific engine (not yet selected) |
| Local database | SQLDelight |
| Networking | Ktor |
| Cloud storage | WebDAV and S3-compatible APIs |
| Key derivation | Argon2id |
| Authenticated encryption | XChaCha20-Poly1305 or an approved equivalent |
| Testing | Kotlin Test / JUnit |

Do not replace these technologies without documenting the reasons and obtaining approval.

Do not select an unapproved desktop audio engine or invent a cryptographic storage format. These decisions require technical validation.

## 3. Architectural Principles

Follow these principles in all implementations:

1. Prefer shared Kotlin Multiplatform code over duplicated platform implementations.
2. Keep platform-specific functionality behind well-defined interfaces.
3. Separate business logic from UI code.
4. Separate storage-provider implementations from library logic.
5. Keep audio playback independent of the underlying playback engine.
6. Keep cryptographic operations in a dedicated, testable module.
7. Avoid unnecessary dependencies and abstractions.
8. Do not introduce a backend or application-managed user accounts.
9. Preserve compatibility with existing library data formats.
10. Design new functionality to support future extensions without implementing speculative features.

### 3.1 Suggested module responsibilities

Use these logical boundaries. The exact Gradle module structure may be adjusted when justified.

- `core`: shared business logic and domain models.
- `crypto`: encryption, decryption, key derivation, and key management.
- `library`: library creation, access, and management.
- `storage`: common cloud storage interfaces.
- `providers`: WebDAV and S3 implementations.
- `synchronization`: synchronization, revisions, and conflict handling.
- `metadata`: track metadata and cover management.
- `playlists`: manual and smart playlists.
- `playback`: playback coordination, queues, and playback modes.
- `composeApp`: shared UI and platform entry points.

Platform-specific code should be limited to functionality that genuinely requires platform APIs.

## 4. Security Requirements

Security is a core product requirement, not an optional feature.

### 4.1 Encryption

- All private library content must be encrypted before upload.
- Cloud storage must never receive plaintext audio, metadata, playlists, or other private library information.
- Object names and paths must not reveal original filenames or private metadata.
- Each library must have an independent random Master Encryption Key (MEK).
- The MEK must never be uploaded in plaintext.
- Passwords must not be stored in cloud storage.
- Password changes must not require re-encrypting all library content.
- Downloaded audio must remain encrypted on disk.
- Audio must be decrypted in memory for playback.
- Never write decrypted audio to temporary files.
- Protect local metadata and database contents at rest.

### 4.2 Key management

- Use Argon2id for password-based key derivation.
- Use authenticated encryption.
- Use secure random number generation.
- Never invent cryptographic algorithms or modify established algorithms.
- Never reuse nonces where the selected algorithm requires nonce uniqueness.
- Never log passwords, MEKs, derived keys, recovery keys, or plaintext private metadata.
- Keep key material in memory only as long as necessary.
- Use platform secure storage where appropriate.

The exact key hierarchy, encryption format, key-wrapping format, Argon2id parameters, and nonce strategy must follow an approved architecture decision.

**Do not implement a production cryptographic format before these decisions are documented and reviewed.**

### 4.3 Recovery and sharing

- Recovery keys must be generated locally.
- Recovery keys must never be uploaded automatically.
- Library-access files must never contain plaintext MEKs or passwords.
- Cloud credentials must not be included in library data or access files.
- Sharing permissions are controlled by the cloud provider.

### 4.4 Security testing

For security-sensitive changes, include tests that verify:

- Correct encryption and decryption.
- Rejection of incorrect passwords.
- Detection of corrupted or modified encrypted data.
- Correct key wrapping and unwrapping.
- Recovery-key behavior.
- Absence of plaintext data in cloud objects.
- Absence of plaintext audio files in local storage.

Do not claim end-to-end encryption is implemented merely because an encryption library is present.

## 5. Library and Storage Rules

- Multiple independent libraries must be supported.
- Each library has its own MEK and cloud storage configuration.
- A combined library view must preserve the association of every track with its original library.
- Playlists belong to a single library.
- Identical tracks in different libraries must remain independent.
- Cloud credentials are configured separately on each device.
- Read-only storage permissions must be respected.
- Never silently overwrite conflicting changes.
- Never silently delete user data.
- Disconnecting a library must not delete cloud data.
- Permanent deletion requires explicit confirmation.

Storage providers must implement a common abstraction. Library logic must not depend directly on WebDAV- or S3-specific APIs.

## 6. Synchronization Rules

Synchronization must support:

- Initial synchronization when connecting a library.
- Synchronization when opening a library.
- Periodic synchronization while the application is running.
- Synchronization after local changes.
- Manual synchronization.

Implementation requirements:

- Handle offline devices and interrupted operations.
- Detect concurrent modifications.
- Preserve conflicting user changes.
- Make synchronization operations recoverable after interruption.
- Do not assume that the device clock is authoritative.
- Do not use last-write-wins when it can silently discard user changes.
- Report synchronization errors clearly.

Any change to the synchronization protocol must consider compatibility with other devices and existing library data.

## 7. Audio and Playback Rules

Initially supported formats:

- MP3
- FLAC
- WAV
- OGG

Requirements:

- Preserve original audio files.
- Do not transcode audio.
- Read embedded metadata and cover art during import.
- Store edited metadata separately from original audio files.
- Support individual files, multiple files, and recursive folder imports.
- Support streaming, seeking, and offline playback.
- Keep decrypted audio in memory.
- Avoid requiring a complete download for seeking when the provider and format support partial access.

Playback logic must be independent of the platform-specific audio engine.

Do not introduce platform-specific playback logic into shared business logic unless required by the architecture.

### Android

Use Android Media3 and follow Android requirements for:

- Background playback.
- Foreground media playback services.
- MediaSession.
- Media notifications.
- Audio focus.
- System playback controls.

### Desktop

Support playback while minimized.

Support system media controls where feasible.

The desktop engine must be compatible with encrypted streaming and must not require plaintext temporary audio files.

## 8. User Interface

The application must use Compose Multiplatform.

Design requirements:

- Minimalist interface.
- Dark theme.
- Consistent functionality across platforms.
- Adaptive layouts.
- No duplicated business logic in UI components.
- All user-facing text must use localization resources.

Main sections:

- Library
- Artists
- Albums
- Genres
- Playlists
- Downloads
- Settings

The player must include a compact bottom player and an expanded Now Playing view.

Keep UI state and business logic separate. Prefer reusable components and predictable state management.

## 9. Code Quality

All code must follow these rules:

- Use clear, descriptive names.
- Prefer small, focused classes and functions.
- Avoid unnecessary global state.
- Avoid duplicated logic.
- Avoid circular dependencies.
- Use dependency injection or explicit dependencies where appropriate.
- Handle errors explicitly.
- Do not swallow exceptions.
- Do not use placeholder implementations in production paths.
- Do not add unused dependencies.
- Do not commit generated build artifacts.
- Do not hardcode secrets, credentials, or environment-specific paths.

Comments should explain non-obvious decisions, not repeat what the code already expresses.

Public APIs and important architectural boundaries should have appropriate documentation.

## 10. Testing Requirements

Every feature must include appropriate tests.

At minimum:

- Unit tests for business logic.
- Unit tests for cryptographic operations.
- Tests for library and playlist operations.
- Tests for synchronization and conflict handling.
- Integration tests for storage providers.
- Platform tests for playback and OS integration where appropriate.

Tests must be deterministic and must not depend on real user credentials or personal cloud storage.

Use mocks or dedicated test storage environments for external services.

Do not weaken or delete existing tests just to make a build pass.

Do not mark incomplete tests as successful.

## 11. Development Workflow

Before starting a task:

1. Read this file.
2. Read the relevant parts of the technical specification.
3. Inspect the existing implementation.
4. Identify affected modules and dependencies.
5. Check existing tests and architectural decisions.
6. Prepare a short implementation plan.

During implementation:

1. Keep changes limited to the requested task.
2. Follow existing project conventions.
3. Preserve existing functionality.
4. Add or update tests.
5. Update documentation when architecture or behavior changes.
6. Do not introduce unrelated refactoring.

After implementation:

1. Run relevant tests.
2. Run static analysis and lint checks if configured.
3. Run the project build.
4. Review the complete diff.
5. Check for accidental secrets, generated files, and unrelated changes.
6. Report any checks that could not be executed.

Never claim that a test or build passed unless it was actually executed successfully.

## 12. Architectural Decision Records

Important architectural decisions must be documented before implementation.

Use `docs/adr/` for Architecture Decision Records.

Each ADR should include:

- Title and status.
- Context and problem.
- Decision.
- Alternatives considered.
- Consequences.
- Security and compatibility implications where relevant.

The following decisions require explicit documentation:

1. Desktop audio engine.
2. Encrypted audio file format and chunk structure.
3. Library synchronization and conflict resolution protocol.
4. Local database encryption.
5. Cryptographic key hierarchy and parameters.
6. Library-access file format.
7. Multi-provider replication and recovery.

Do not make irreversible architectural decisions without documenting them.

## 13. Implementation Priorities

Follow this implementation order unless explicitly instructed otherwise:

1. Shared project foundation and module structure.
2. Cryptographic key management and library creation.
3. Encrypted local storage.
4. Cloud storage abstraction.
5. WebDAV and S3 providers.
6. Synchronization and conflict handling.
7. Audio import and metadata.
8. Library browsing and search.
9. Playlists.
10. Android and desktop playback.
11. Offline downloads.
12. Library sharing.
13. Listening history and track preferences.
14. UI polish and localization.

Do not implement later phases by introducing shortcuts that compromise earlier security or data-integrity requirements.

## 14. Reporting

At the end of each task, provide a concise report containing:

- What was implemented.
- Which files or modules were changed.
- Tests executed and their results.
- Build and lint results.
- Known limitations.
- Any specification or architecture decisions that require user input.

Clearly distinguish completed work from partial implementations.

If a task cannot be completed safely or correctly because a requirement is unclear, explain the issue and ask for clarification instead of guessing.

## 15. Final Principle

**Data integrity, security, and compatibility take priority over implementation speed.**

Never silently lose, overwrite, expose, or corrupt user data.

When in doubt, preserve existing data and ask for clarification.
