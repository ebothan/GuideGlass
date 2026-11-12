# ADR 0002: A portable, deterministic core; platform code as thin adapters

Status: accepted, 2026-11-12.

## Context

- The build machine has JDK 17 but no Android SDK or emulator (user decision, K13).
- iOS parity must be a defined port, not a rewrite (REQ-29).
- Tests must inject latency, frame drops and link loss and get the same result every run (REQ-28).

## Options

1. Put the logic in the Android app module and test with Android instrumentation. Not possible here, and ties the logic to Android.
2. Kotlin Multiplatform module shared by Android and iOS. Right long-term shape, but the multiplatform toolchain adds build risk now and iOS is out of scope until Android gates pass.
3. **A plain Kotlin/JVM library under `core/` with its own Gradle build, written without Android or `java.*` platform types in its public interfaces.**

## Decision

Option 3.

- `core/` is a standalone Gradle build (`core/settings.gradle.kts`) so `./gradlew -p core test` works without the Android SDK. The Android app will include it as a composite build later.
- **Deterministic by construction.** The core has no threads, no wall clock and no I/O. It is driven by events, each carrying a timestamp from an injected clock:
  - `onFrame(detections, stage timestamps)`
  - `onTick(now)`
  - `onLocation(fix)`
  - `onLinkState(...)`
  - `onCloudAnswer(...)`

  It returns cue commands. The same event log always gives the same cues. This is the log-replay practice from automated driving.
- **Ports (interfaces the platform implements):**

| Port | Android adapter (later) | iOS adapter (later) | Test double (now) |
|---|---|---|---|
| Frame source | DAT `mwdat-camera` stream or MockDeviceKit | `MWDATCamera` | Synthetic scene renderer; replayed logs |
| Perception (frame to detections with range) | LiteRT detector + depth model | Core ML | Scene-geometry model with injected noise |
| Audio sink (earcons, speech, route state) | AudioTrack + TextToSpeech; Bluetooth route callbacks | AVAudioEngine + AVSpeechSynthesizer | Logging sink with configurable latency |
| Location | Fused Location Provider | Core Location | Replayed GPS trace |
| Route provider | Directions REST client | same REST | Recorded responses |
| Cloud advisor | HTTPS client | same | Recorded answers with injected delay |
| Clock | `SystemClock.elapsedRealtimeNanos` | `mach_absolute_time` | Simulated clock |

- Core code sticks to the Kotlin standard library, so moving it into a multiplatform module later is a build change, not a rewrite.

## Consequences

- G1 is met by the core build plus the Python harness. The existing Android `app/` module is not compiled by `init.sh`.
- Real perception models are not run in this project phase. The perception port is exercised by a geometric test double. This limits every detection result to L1.
- Thread handling (camera thread, inference thread, audio thread) lives in the adapters and is untested until the Android phase. The core's stage timestamps make that latency visible when it is.
