# ADR-006: Desktop Playback Engine

- **Status:** Draft (docs-only; no engine selected — selection requires the
  evaluation prototype described below per spec §2.2 and §14.1)
- **Date:** 2026-11-25
- **Deciders:** my-tube-bot (draft), owner (review/decision pending)

## Context

The spec (§2.2) leaves desktop audio playback as "platform-specific engine
(TBD)". Android uses Media3/ExoPlayer, which is not a supported desktop story.
The desktop engine must work on Linux and Windows (§2.1) and must:

- Play audio while the app is minimized (§12.5), ideally with system media
  controls where feasible.
- Consume an encrypted input stream and support seeking **without ever
  writing decrypted audio to disk** (§12.5). The encrypted format itself is a
  separate open question (§14.1 item 2: versioned chunk-based authenticated
  encryption with partial reads/seeking) — this ADR only concerns which
  playback engine can consume such a stream.
- Support common audio formats (MP3, FLAC, OGG/Vorbis/Opus, AAC/M4A at
  minimum) on both target OSes.
- Integrate with Compose Desktop / Kotlin Multiplatform via the existing
  `PlaybackEngine` port so Android (Media3) and desktop implementations stay
  interchangeable.
- Be redistributable: reasonable binary size and licensing (the app is
  private, but GPL/LGPL obligations should still be understood).

## Candidate engines

1. **libVLC via vlcj (Java binding).** Mature, very broad format support,
   Linux+Windows binaries, can play from custom `OutputStream`-style media
   (vlcj supports supplying media via callbacks with some effort). LGPL;
   bundles large native libs (~100+ MB unpacked across platforms). Seek in
   custom-piped media is the main risk.
2. **libmpv via a JNI binding (e.g. mpv-j / dev.inmo bindings).** Excellent
   format support (FFmpeg-based), strong seeking, can read from a custom
   stream via `stream_cb`. GPLv2+ (build options may relax); native libs to
   package per-platform. Less Kotlin/Java community precedent than vlcj.
3. **GStreamer (via gstreamer-java / JNI).** Pipeline model fits
   custom-source + decrypt-in-pipeline designs well (an app element could
   decrypt in-graph). Good Linux support; Windows packaging is heavier and
   historically painful. LGPL (plugins vary).
4. **JavaFX Media.** Zero extra native footprint if Compose Desktop adopts
   JavaFX... it does not; adding JavaFX to a Compose Desktop app is awkward.
   Format support (MP3, AAC; FLAC only via newer versions) is weaker;
   custom-stream playback (decrypting pipe) is limited. Cheapest but least
   capable — included for completeness.
5. **Media3/ExoPlayer on JVM.** Officially Android-first; desktop/JVM use is
   possible for some surfaces but audio rendering, MediaSession, and
   platform integration are not supported targets. Not viable as-is today.

## Evaluation plan (prototype gates, per spec §14.1)

Before selection, spike a minimal prototype against a synthetic encrypted
chunk source for each serious candidate (expected: vlcj, libmpv, GStreamer):

- **G1 Encrypted streaming:** play a chunked, authenticated-encrypted source
  delivered through an in-memory pipe/callback; verify no plaintext hits disk.
- **G2 Seeking:** random-access seek within the encrypted stream with correct
  chunk/index resolution; measure seek latency.
- **G3 Formats:** play MP3, FLAC, OGG, AAC on Linux and Windows.
- **G4 Background + controls:** audio continues minimized; basic system
  media-control integration feasibility on both OSes.
- **G5 Packaging & license:** measure added app size per platform and record
  license obligations of the chosen native libs.

## Decision (proposed shape, not yet made)

Select the engine that passes G1–G3 on both OSes with the best G4/G5 trade-
off; expected ranking before testing is libmpv ≈ vlcj > GStreamer > others,
but the decision is explicitly deferred to prototype results. Whatever is
chosen implements the existing `PlaybackEngine` port as a desktop-specific
adapter; Android keeps Media3. A follow-up ADR or an update of this one will
record the decision with prototype evidence.

## Consequences

- One more native dependency to package per platform (size, build matrix).
- The encrypted-format ADR (§14.1 item 2) must define a seekable index so any
  candidate can implement G2; engine choice does not change the format.
- If no candidate passes G1–G3 cleanly, fall back to decrypt-to-pipe with
  strict memory bounds and re-evaluate.

## Alternatives considered

- Deferring until Android UI is done: rejected — engine choice constrains the
  encrypted-format ADR, which gates Phase 2 sync/playback work.
- Writing a custom JVM decoder: rejected — format breadth and native
  reliability make this disproportionate effort.
