# Technical Specification: Cross-Platform Encrypted Music Player

**Status:** Draft 1.0  
**Date:** 2026-11-16

## 1. Project Overview

The project is a cross-platform music player for Android, Linux, and Windows.

Users manage their own music libraries. Libraries can be stored locally or in cloud storage and synchronized between devices. The application provides end-to-end encryption: cloud storage providers must not be able to access audio files, metadata, playlists, or other private library information.

The application does not use a central backend, user accounts, or a central database. Devices communicate directly with configured cloud storage providers.

### 1.1 Goals

- Play music from personal and shared libraries.
- Synchronize libraries between multiple devices.
- Support WebDAV and S3-compatible storage.
- Protect all library content with client-side encryption.
- Support offline playback.
- Provide automatic and manual synchronization.
- Offer a consistent interface across Android and desktop.
- Keep the architecture extensible for possible future video support.

### 1.2 Non-goals

The initial version will not include:

- A public or shared music catalogue.
- Music streaming subscriptions or third-party music services.
- A central application server.
- Audio transcoding.
- Video playback.
- Automatic deletion of cached music to free storage.

## 2. Supported Platforms and Technology Stack

### 2.1 Platforms

- Android
- Linux desktop
- Windows desktop

The Android and desktop applications should provide essentially the same functionality, with adaptive layouts where necessary.

### 2.2 Technology

| Component | Technology |
|---|---|
| Programming language | Kotlin |
| Shared application code | Kotlin Multiplatform |
| User interface | Compose Multiplatform |
| Android audio playback | Android Media3 |
| Desktop audio playback | Platform-specific engine (TBD) |
| Local database | SQLDelight |
| HTTP networking | Ktor |
| Cloud storage | WebDAV and S3-compatible APIs |
| Password key derivation | Argon2id |
| Authenticated encryption | XChaCha20-Poly1305 or an equivalent reviewed construction |
| Testing | Kotlin Test / JUnit |

The application should use shared business logic wherever possible. Platform-specific code should be limited to operating-system integration, filesystem access, secure credential storage, notifications, and media playback.

Desktop playback technology must be selected after testing encrypted streaming, seeking, and format compatibility.

## 3. Application Architecture

The application consists of the following logical components:

- **UI:** screens, navigation, controls, and user interaction.
- **Core:** library management, playlists, metadata, playback coordination, and synchronization logic.
- **Cryptography:** key management, encryption, decryption, and access-file handling.
- **Storage providers:** WebDAV and S3 implementations.
- **Local storage:** encrypted cache and local database.
- **Platform integration:** audio playback, background services, filesystem access, and secure credential storage.

### 3.1 Architectural principles

1. Business logic must not depend directly on a particular cloud provider.
2. All cloud providers must implement a common storage interface.
3. Playback must use a common interface independent of the underlying audio engine.
4. Cryptographic operations must be implemented in a shared, testable module.
5. Cloud credentials must be stored locally and must never be included in synchronized library data.
6. No plaintext library content may be written to cloud storage.
7. New providers and platforms should be addable without redesigning the library format.

## 4. Library Model

### 4.1 Libraries

The application must support multiple independent libraries.

Examples:

- Personal library.
- Shared library.
- Additional libraries from other users.

Each library has:

- A unique identifier.
- Its own encryption key.
- Its own cloud storage configuration.
- Its own metadata and playlists.
- Its own synchronization state.
- Its own access and recovery configuration.

A library must remain logically independent of other libraries.

### 4.2 Combined library view

The main Library screen displays tracks from all connected libraries.

Artists, albums, and genres are aggregated across connected libraries.

Users must be able to filter results by library.

Each track retains its original library association. Identical tracks in different libraries must not be automatically merged.

Playlists belong to a particular library and cannot contain tracks from other libraries.

### 4.3 Library management

Users must be able to:

- Create a library.
- Connect an existing library.
- Disconnect a library.
- Permanently delete a library.

Disconnecting a library removes its local connection and configuration but must not delete cloud data.

Permanent deletion must require explicit confirmation, write permissions, and a warning that other users may lose access. The application must clearly distinguish permanent deletion from disconnection.

## 5. Cloud Storage and Synchronization

### 5.1 Storage providers

The initial implementation must support:

- WebDAV.
- S3-compatible object storage.

The architecture must allow additional providers in the future.

A library may use multiple storage providers. The initial design should support a single active provider per library while keeping the provider abstraction extensible for future replication and redundancy.

### 5.2 Direct communication

Each device communicates directly with the configured storage provider.

There is no application backend.

Users configure cloud credentials independently on each device. Credentials must not be synchronized through the library.

### 5.3 Permissions

Cloud storage permissions determine what operations are possible.

Read-only access allows users to browse, download, and play available content.

Read/write access additionally allows users to upload tracks, edit metadata, and manage playlists.

The application must detect permission failures and display understandable error messages.

### 5.4 Synchronization

Synchronization must occur:

- When a library is opened.
- Periodically while the application is running.
- After local changes.
- When the user requests manual synchronization.

The interface must show synchronization status and errors.

The application must detect conflicting changes and must never silently overwrite data.

Synchronization must work across devices that may be offline for extended periods.

Android background synchronization is subject to operating-system restrictions. Desktop synchronization should operate while the application is running.

### 5.5 Conflict handling

The synchronization implementation must support concurrent changes to metadata, playlists, and other library data.

It must preserve enough revision information to identify conflicting modifications.

The exact conflict-resolution and storage format is an implementation decision that must be documented before implementation. Automatic last-write-wins behavior must not be used when it could silently discard user changes.

## 6. Security and Encryption

### 6.1 Encryption model

All private library content must be encrypted on the client before being uploaded.

The cloud provider must not be able to read:

- Audio files.
- Track titles.
- Artist and album names.
- Genres and tags.
- Cover images.
- Playlists.
- Listening history.
- Playback progress.
- Other private library metadata.

Object names and paths must also avoid revealing private information. Random identifiers should be used instead of original filenames and directory names.

### 6.2 Master Encryption Key

Each library has one independent random Master Encryption Key (MEK).

The MEK is generated when the library is created.

The MEK is never uploaded in plaintext.

The application stores an encrypted representation of the MEK in the library's cloud storage.

### 6.3 Password protection

The user protects access to the MEK with a password.

A key derived from the password using Argon2id is used to encrypt or wrap the MEK.

Changing the password must only require re-wrapping the MEK. It must not require re-encrypting all library files.

The password itself must not be stored in cloud storage.

Key derivation parameters and encryption format versions must be stored with the encrypted key data.

### 6.4 Recovery key

When a library is created, the application generates a recovery key.

The recovery key must:

- Independently allow access to the MEK.
- Allow the user to set a new password.
- Be displayed to the user only during creation or explicit recovery-key export.
- Never be uploaded automatically.

The user must confirm that the recovery key has been saved before completing library creation.

The application must warn:

**Do not store the recovery key in the same cloud storage as your library.**

If both the password and recovery key are lost, the library data must be considered unrecoverable.

### 6.5 Encryption at rest

Downloaded audio files must remain encrypted on the device.

Decryption must happen in memory for playback.

The application must not create plaintext temporary audio files.

Local metadata and database contents must also be protected at rest. SQLDelight provides database access and mapping, not encryption by itself; an appropriate database-encryption or application-level encryption solution must be selected.

### 6.6 Platform security

- Android: use Android Keystore for appropriate local key protection.
- Linux: integrate with available secure credential storage, such as Secret Service or KWallet.
- Windows: use Windows Credential Manager or an appropriate Windows-protected credential mechanism.

Unlocked key material should remain in memory only while necessary and should be cleared when practical after locking or closing the library.

## 7. Library Sharing

A user can share a library by granting another user access to its cloud storage location.

The owner must provide the recipient with a separate library-access file.

The access file contains the information necessary to identify the library and access its encrypted MEK. It must not contain plaintext passwords, plaintext MEKs, or cloud credentials.

The recipient must:

1. Obtain read or read/write access to the cloud storage location.
2. Import the library-access file.
3. Enter the library password.
4. Unlock and access the library.

The access file format must be versioned and designed to avoid exposing sensitive key material.

Sharing and access permissions are controlled by the cloud provider, not by an application-managed user account system.

## 8. Audio Files and Import

### 8.1 Supported formats

The initial version must support:

- MP3
- FLAC
- WAV
- OGG

Additional formats may be added later.

### 8.2 Import

Users must be able to import:

- Individual files.
- Multiple selected files.
- Entire folders recursively.

The application must read embedded metadata and cover art during import.

Original audio files must be preserved without transcoding.

### 8.3 Metadata

The application must support:

- Title.
- Artist.
- Album.
- Genre.
- Year.
- Duration.
- Track number.
- Custom tags.

Users must be able to edit metadata manually.

Metadata changes must be stored separately in the encrypted library metadata. The original audio file and its embedded metadata must not be modified.

Cover images must be stored separately.

### 8.4 Duplicate detection

The application must detect possible duplicates.

The first stage uses metadata comparison:

- Title.
- Artist.
- Album.
- Duration.

The second stage uses SHA-256 to identify identical file content.

The application must ask the user how to handle duplicates. It must not automatically delete or overwrite tracks.

## 9. Local Downloads and Offline Playback

Users must be able to download:

- Individual tracks.
- Albums.
- Entire playlists.

A playlist may be marked for automatic offline synchronization.

The application must allow users to configure the maximum cache size.

The application must never silently remove cached tracks to free storage.

If the cache limit is reached, the application must warn the user and ask them to manage downloaded content.

Downloaded files remain encrypted at rest.

The application must support playback of downloaded content without an internet connection.

## 10. Library Organization and Playlists

### 10.1 Main sections

The main navigation must include:

- Library.
- Artists.
- Albums.
- Genres.
- Playlists.
- Downloads.
- Settings.

### 10.2 Manual playlists

Users must be able to:

- Create playlists.
- Rename playlists.
- Add and remove tracks.
- Reorder tracks.
- Add descriptions.
- Set playlist cover art.

Manual playlists are stored as encrypted data in their corresponding library.

### 10.3 Smart playlists

Smart playlists store rules rather than copies of track lists. Their contents are calculated locally.

Supported conditions must include:

- Artist.
- Album.
- Genre.
- Year.
- Rating (reserved for possible future use; star ratings are not part of the initial UI).
- Date added.
- Favorite status.
- Deprioritized status.
- Recently played.
- Play count.

Rules must support AND and OR combinations.

Smart playlist results must respect the library associations of their tracks.

## 11. Track Preferences and Listening History

### 11.1 Track preferences

The application must support two user preferences:

- **Favorite:** increases the track's playback priority.
- **Deprioritize:** temporarily lowers the track's playback priority.

A deprioritized track must remain in the library.

The default deprioritization period is 30 days. The application should allow the user to clear the mark manually.

The application should store the timestamp of the action rather than a permanently modified priority score.

### 11.2 Listening history

The application must track:

- Last played date.
- Play count.
- Total listening duration.
- Recently played status.
- Playback completion information.

Listening history must be encrypted and synchronized as library data.

It must be available to smart playlists and continuous playback selection.

## 12. Playback

### 12.1 Playback controls

The player must support:

- Play.
- Pause.
- Stop.
- Next.
- Previous.
- Seek.
- Shuffle.
- Repeat track.
- Repeat playlist.
- Queue management.

### 12.2 Playback modes

**Playlist mode**

- Plays tracks from the selected playlist.
- Supports sequential and shuffled playback.
- Supports repeat modes.
- Stops when the playlist finishes unless repeat is enabled.

**Continuous library mode**

- Automatically selects another track after the current one finishes.
- Considers favorite and deprioritized preferences.
- Avoids repeatedly selecting recently played tracks.
- Supports shuffle and repeat behavior.

Users must be able to switch between modes.

The selection algorithm should be deterministic enough to test and should not permanently exclude deprioritized tracks.

### 12.3 Playback state

The application must remember:

- Current track.
- Playback position.
- Queue.
- Playback mode.
- Shuffle and repeat settings.

Playback progress must be synchronized between devices.

The application must handle network interruptions during streaming and support seeking without requiring a complete download whenever the underlying storage provider and format allow it.

### 12.4 Android playback

Android playback must continue when:

- The user switches applications.
- The screen is locked.
- The application is in the background.

The application must integrate with Android MediaSession, foreground media playback services, media notifications, audio focus, headphones, and system playback controls.

### 12.5 Desktop playback

Desktop playback must continue while the application is minimized.

System media controls should be supported where feasible.

The desktop playback engine must support encrypted streaming and seeking without writing decrypted audio to disk.

## 13. User Interface

### 13.1 General design

The interface must be:

- Minimalist.
- Dark-themed.
- Responsive.
- Consistent across platforms.
- Designed for efficient music browsing and playback.

Desktop layouts may use additional screen space, but functionality and navigation should remain consistent.

### 13.2 Player interface

The application must provide a compact player at the bottom of the main interface.

Selecting it opens a larger Now Playing view containing:

- Cover art.
- Track title.
- Artist.
- Album.
- Playback progress.
- Seek control.
- Play/pause.
- Previous/next.
- Shuffle/repeat.
- Queue.

### 13.3 Search

Search must support:

- Current library.
- All connected libraries.
- Filtering by library.
- Partial matches.

Searchable fields:

- Track title.
- Artist.
- Album.
- Genre.
- Custom tags.

Search must work offline using locally available metadata.

The local search index must be protected at rest.

## 14. Localization

The first application language is English.

The application must be designed for future localization.

All user-facing strings must be stored in localization resources rather than hardcoded in application logic.

Date, time, and other locale-sensitive values must use platform-appropriate formatting.

## 15. Future Extensibility

Video playback is not part of the initial implementation.

However, the media model and playback architecture should not prevent future support for video.

The application must avoid unnecessary abstractions or premature implementation of video-specific functionality.

## 16. Testing and Quality Requirements

The project must include automated tests for:

- Encryption and decryption.
- Password-based key derivation and key wrapping.
- Recovery-key operations.
- Library creation and connection.
- Metadata import and editing.
- Duplicate detection.
- Playlist operations.
- Smart playlist evaluation.
- Synchronization and conflict detection.
- Playback selection logic.
- Offline playback.
- Cloud provider operations.

Integration tests must verify WebDAV and S3-compatible storage behavior.

Security tests must verify that plaintext audio, metadata, filenames, passwords, and MEKs are not uploaded or written to persistent storage.

The application must handle network errors, expired credentials, insufficient permissions, corrupted files, and interrupted downloads without losing existing library data.

## 17. Implementation Priorities

### Phase 1 — Core foundation

- Project structure and build configuration.
- Shared application architecture.
- Local database.
- Library model.
- Cryptographic key management.
- Library creation and recovery.

### Phase 2 — Storage and synchronization

- WebDAV provider.
- S3-compatible provider.
- Encrypted file storage.
- Metadata synchronization.
- Conflict detection and handling.

### Phase 3 — Music library

- Audio import.
- Metadata extraction.
- Artists, albums, genres.
- Search.
- Manual playlists.
- Smart playlists.

### Phase 4 — Playback

- Android playback.
- Desktop playback prototype and engine selection.
- Encrypted streaming and seeking.
- Queue and playback modes.
- Background playback.

### Phase 5 — Offline and sharing

- Encrypted local cache.
- Offline playback.
- Automatic playlist downloads.
- Library-access files.
- Shared libraries.

### Phase 6 — Polish

- Listening history.
- Track preferences.
- Synchronization improvements.
- Localization infrastructure.
- Platform-specific UX improvements.

## 18. Decisions Requiring Technical Validation

The following details must be resolved before the corresponding implementation begins:

1. **Desktop audio engine:** choose an engine after testing encrypted streaming, seeking, and supported formats on Linux and Windows.
2. **Encrypted audio format:** define a versioned chunk-based authenticated encryption format that supports partial reads and seeking.
3. **Synchronization format:** define object naming, revisions, atomic updates, conflict handling, and recovery from interrupted synchronization.
4. **Local database encryption:** select a solution compatible with SQLDelight and all supported platforms.
5. **Key management:** define exact key hierarchy, Argon2id parameters, nonce generation, key wrapping, and cryptographic format versioning.
6. **Library-access file:** define its exact versioned format and secure import/export process.
7. **Multiple storage providers:** define how replication and recovery work before enabling multi-provider redundancy.

These decisions must be documented and tested. Cryptographic formats and key-management mechanisms must receive security review before production use.

---

**End of specification — Draft 1.0**
