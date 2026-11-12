# 06 — Meta glasses platform: what the Wearables Device Access Toolkit (DAT) gives us

Research date: 2026-11-11. Nothing in this file was measured. Every number is **read** from the source cited next to it, unless a line says otherwise.

## Question

What exactly can and cannot the Meta AI glasses do for GuideGlass through the Wearables Device Access Toolkit (DAT), as of the newest SDK version? This covers versions, supported glasses, registration and permissions, camera stream API, audio, MockDeviceKit, distribution, sensors, background execution, iOS/Android differences, timestamps, thermal and battery limits, voice and button input, and build requirements.

## How this was researched

- I made shallow clones of `github.com/facebook/meta-wearables-dat-android` (HEAD `f4371bc`, committed 2026-10-09) and `github.com/facebook/meta-wearables-dat-ios` (HEAD `bbae597`, committed 2026-10-09). I read the README, CHANGELOG, AGENTS.md, `plugins/mwdat-*/skills/*/SKILL.md`, `.cursor/rules` and the sample apps.
- I downloaded the Android 1.0.1 AARs (`mwdat-core`, `-camera`, `-motion`, `-inputs`, `-mockdevice`, `-speech`) and their POMs from Maven Central (`repo1.maven.org/maven2/com/meta/wearable/...`). I read the public class signatures straight from `classes.jar` with a small class-file parser, because no working `javap` was installed. Signatures written below as `getFoo()` are JVM-level getters, which Kotlin exposes as the property `foo`.
- I fetched the official docs through the documented LLM endpoint `https://wearables.developer.meta.com/llms.txt?...&document=products/dat/sections/guides/<page>`. Below, a page like `.../guides/streaming` means `https://wearables.developer.meta.com/llms.txt?product=all&version=1.0&document=products/dat/sections/guides/streaming`. The human-readable versions live under `https://wearables.developer.meta.com/docs/develop/dat/<page>/`.
- I read the Android foreground-service-type rules at developer.android.com.
- I read GuideGlass's own `origin/meta-glasses` branch: `app/build.gradle.kts` and `GlassesCameraSource.kt`.

## Findings

1. **The newest SDK is 1.0.1 (2026-10-09) on both platforms. 1.0.0 shipped on 2026-09-24. Our `meta-glasses` branch pins 1.0.0.**
   - **Claim.**
     - Android CHANGELOG: `## [1.0.1] - 2026-10-09`. It fixes "Buffer corruption while streaming compressed HEVC buffers, causing sporadic frame freezes" and "performance issues on `Stream` causing lag and stutter".
     - iOS 1.0.1 says "No developer-facing changes".
     - Maven `maven-metadata.xml` for `mwdat-core` lists versions 0.7.0, 0.8.0, 0.9.0, 1.0.0 and 1.0.1, with `<release>1.0.1</release>`.
     - The first Android release was 0.1.0 on 2025-10-30.
     - Our branch has `val mwdat_version = "1.0.0"` (`git show origin/meta-glasses:app/build.gradle.kts`, line 161).
   - **Source.** `dat-android/CHANGELOG.md`; `dat-ios/CHANGELOG.md`; https://repo1.maven.org/maven2/com/meta/wearable/mwdat-core/maven-metadata.xml
   - **Confidence.** High: primary sources that agree with each other.
   - **Design impact.** Bump to 1.0.1. Its fixes target exactly the stream lag and freezes that would hurt hazard latency. The API changed sharply between 0.x releases (0.7 renamed Session to DeviceSession; 0.9 replaced `addStream` with `addCamera`), so pin the version exactly and wrap DAT behind our own `GlassesCameraSource` interface.

2. **How to get the dependencies.**
   - **Claim.**
     - **Android.** Maven Central, group `com.meta.wearable`. Artifacts: `mwdat-core`, `mwdat-camera`, `mwdat-display`, `mwdat-inputs`, `mwdat-motion`, `mwdat-mockdevice`, `mwdat-speech`, all at version `1.0.1`. The README says "no access token or extra repository definition is required. The `mavenCentral()` repository ... is all you need." Earlier 0.x versions were distributed differently: the Maven metadata starts at 0.7.0. I did not verify the older GitHub Packages route, so do not rely on it.
     - **iOS.** Swift Package `https://github.com/facebook/meta-wearables-dat-ios`, version tag `1.0.1`. Products include `MWDATCore`, `MWDATCamera`, `MWDATMockDevice`, `MWDATMotion`, `MWDATInputs` and `MWDATSpeech`. No auth is needed because it is a public repo.
     - The `mwdat-core` POM pulls in fbjni 0.7.0, soloader 0.12.1, okhttp 4.10.0, protobuf-javalite 3.25.5, tink-android 1.20.0, security-crypto 1.1.0 and kotlinx-coroutines-android 1.11.0.
     - `mwdat-core.aar` is about 9.0 MB, with native libs for arm64-v8a, armeabi-v7a, x86 and x86_64. File sizes were measured with `ls` on the downloaded files.
   - **Source.** `dat-android/README.md` §"Including the SDK"; `dat-ios/README.md`; `mwdat-core-1.0.1.pom`; `dat-ios/plugins/mwdat-ios/skills/mockdevice-testing/SKILL.md` ("Add `MWDATMockDevice` ... included in the `meta-wearables-dat-ios` package").
   - **Confidence.** High.
   - **Design impact.** CI can resolve DAT with no secrets. Watch for transitive version conflicts: okhttp, protobuf-lite and coroutines 1.11.

3. **Supported glasses.**
   - **Claim.** Setup doc: "Ray-Ban Meta glasses (Gen 1 and Gen 2), Ray-Ban Meta Optics glasses, Meta glasses, Meta Ray-Ban Display glasses, Oakley Meta HSTN glasses, and Oakley Meta Vanguard glasses." The `DeviceType` enum in `mwdat-core` 1.0.1 has `UNKNOWN, RAYBAN_META, OAKLEY_META_HSTN, OAKLEY_META_VANGUARD, META_RAYBAN_DISPLAY, RAYBAN_META_OPTICS, META_GLASSES`.
   - **Firmware and app minimums for 1.0.1.** Meta AI app V292 (Android and iOS). Glasses firmware V128 for all models.
   - **Source.** `.../guides/getting-started-toolkit`; `.../guides/version-dependencies`; `com/meta/wearable/dat/core/types/DeviceType.class`.
   - **Confidence.** High.
   - **Design impact.** At startup, check `Device.compatibility`, which can be `DEVICE_UPDATE_REQUIRED` or `SDK_UPDATE_REQUIRED`. Speak a system-health cue if the glasses need a firmware update. Known issue: after the v128 OTA "camera capture does not work" until the glasses are rebooted (`.../guides/knownissues`).

4. **Registration and permissions go through the Meta AI app. Developer Mode skips attestation.**
   - **Claim.**
     - Two steps: registration, then per-capability permission. Calls: `Wearables.startRegistration(activity)`; `Wearables.checkPermissionStatus(Permission)`, a suspend function returning `DatResult<PermissionStatus, PermissionError>`; and `Wearables.RequestPermissionContract()`. The only `Permission` enum values are `CAMERA` and `MICROPHONE`.
     - "Both flows depend on the Meta AI app being installed". The prerequisites list "Internet connection for registration".
     - In Developer Mode, `APPLICATION_ID` and `CLIENT_TOKEN` can be `0`. For production you use credentials from Wearables Developer Center plus a release channel.
     - Since 1.0.0, `Wearables.handleIntent(intent, onRegistrationRequest)` handles registration that starts in the Meta AI app.
     - Some capabilities are "gated server-side per user ... the device may decline it at runtime" (`CAPABILITY_DENIED`).
   - **Source.** `dat-android/AGENTS.md` §Permissions; `Wearables.class` signatures; `Permission.class`; `.../guides/version-dependencies`.
   - **Confidence.** High.
   - **Design impact.** First-run setup needs network access and a sighted helper, or a fully accessible flow through the Meta AI app. After registration, the runtime safety path must not need the network. Whether a session can start fully offline after registration is **not documented**; verify it on hardware. Treat `CAPABILITY_DENIED` and permission loss as spoken system-health states.

5. **Camera stream API (Android 1.0.1).**
   - **Claim.** Flow: `Wearables.createSession(AutoDeviceSelector())` returns a `DatResult<DeviceSession,…>`. Then `session.start()`, which returns `Unit` and is fire-and-forget. Wait for `DeviceSessionState.STARTED`, then call `session.addCamera(StreamConfiguration)` to get a `Camera`, then `camera.stream.start()`, which returns a `DatResult`.
   - **Signatures read from `classes.jar`:**
     - `StreamConfiguration(audioCodec: AudioCodec?, videoQuality: VideoQuality, frameRate: Int, compressVideo: Boolean)`
     - `VideoQuality { HIGH, MEDIUM, LOW }`: 720×1280, 504×896, 360×640
     - Allowed frame rates are 2, 7, 15, 24 and 30.
     - `Stream`: `state: StateFlow<StreamState>`, `videoStream: Flow<VideoFrame>`, `audioStream: Flow<AudioFrame>`, `errorStream: Flow<StreamError>`, `start(): DatResult`, `suspend capturePhoto()`, `stop()`
     - `VideoFrame(buffer: ByteBuffer, width: Int, height: Int, presentationTimeUs: Long, isCompressed: Boolean, isCodecConfig: Boolean)`
     - `StreamState { STARTING, STARTED, STREAMING, STOPPING, STOPPED, PAUSED, CLOSED }`
     - `StreamError { STREAM_ERROR, CRITICAL_STREAM_ERROR, HINGE_CLOSED, PERMISSIONS_DENIED, THERMAL_HOT, BATTERY_LOW, PEAK_POWER_LIMIT, TIMEOUT }`
   - **Frame format.** By default "the SDK decodes on the phone and `VideoFrame.buffer` holds YUV pixel data". The exact YUV layout is not stated in the docs; our `Yuv420.kt` assumes I420. Setting `compressVideo = true` delivers HEVC instead.
   - **Pause behaviour.** `PAUSED` happens "for example on a single cap-touch tap; the stream can resume on its own".
   - **Source.** `dat-android/plugins/mwdat-android/skills/camera-streaming/SKILL.md`; `x-camera/classes.jar` (`com/meta/wearable/dat/camera/{Stream,Camera}.class`, `types/*.class`).
   - **Confidence.** High.
   - **Design impact.**
     - LOW (360×640) at 15–30 fps matches the bottom of our perception input range. MEDIUM (504×896) is the most we should plan detector cost around.
     - A wearer tapping the temple pauses our hazard feed. `PAUSED` must trigger a spoken "camera paused" health cue.
     - `CRITICAL_STREAM_ERROR` means tear down and recreate the stream. `STREAM_ERROR` is informational.

6. **Adaptive quality is silent: there is no callback, so read the size from each frame.**
   - **Claim.**
     - "an automatic ladder reduces quality as needed. It first lowers the resolution by one step ... then reduces the frame rate (for example, 30 to 24), but never below 15 fps."
     - Image quality can drop because of "per-frame compression that adapts to available Bluetooth Classic bandwidth".
     - "Because the glasses can lower the resolution of a running stream, a frame can be smaller than the resolution you requested. Read the size from each frame". With `compressVideo = true`, `width` and `height` report the *requested* size, so you must parse the SPS.
     - There is no public event that reports a step down the ladder. I found none among the public classes.
   - **Source.** `.../guides/streaming`; `.../guides/camera-stream-processing`.
   - **Confidence.** High.
   - **Design impact.**
     - The perception input stage must handle a resolution change in the middle of a stream.
     - Estimate the effective frame rate from `presentationTimeUs` deltas, not from the configured value.
     - Any pixel-based distance heuristic must be normalised by the frame width.
     - Log width, height and fps per second as a health metric.

7. **Frames are cropped, undistorted and stabilized on the glasses. There are no intrinsics, and stabilization breaks camera–IMU rigidity.**
   - **Claim.**
     - "the glasses crop the sensor image to a 9:16 frame, correct lens distortion, stabilize the image, and compress the video with ... HEVC."
     - "The SDK does not report intrinsic camera parameters". The docs recommend calibrating a pinhole model per glasses model, and rescaling it by frame width.
     - "Stabilization moves each frame to counter small head movements, so the image does not exactly track the head rotation that the Motion capability reports. Visual-inertial tracking ... lose[s] accuracy during head movement."
     - A stream frame has a narrower field of view than a standalone photo.
   - **Source.** `.../guides/camera-stream-processing`.
   - **Confidence.** High.
   - **Design impact.**
     - Metric depth, ground-plane fitting and any monocular-geometry heuristics need our own intrinsics calibration for each model, for example a checkerboard on stream frames.
     - Do not build tight VIO on DAT frames plus DAT motion.
     - A portrait 9:16 crop shows the ground near the feet poorly. Drop-off and curb detection may need the wearer's head tilted down, so test this.

8. **Timestamps exist, but their semantics are not documented.**
   - **Claim.**
     - Android `VideoFrame.presentationTimeUs: Long`, added in 0.4.0.
     - `AudioFrame(buffer, presentationTimeUs)`. "Use `presentationTimeUs` to preserve order or align audio with video."
     - On iOS, `VideoFrame.sampleBuffer` is a `CMSampleBuffer` that carries a presentation timestamp. iOS audio has `frame.presentationTimeStamp`, which "aligns with video".
     - Motion uses `timestampNs` on the "Device monotonic clock". Inputs use `timestampMs`, a "device timestamp".
     - **Not documented:** whether video `presentationTimeUs` is the capture time on the glasses or the decoder or arrival time on the phone, and how it relates to the phone's `SystemClock`/`elapsedRealtimeNanos` or to the motion clock.
   - **Source.** `VideoFrame.class`; `AudioFrame.class`; `MotionSample.class`; `InputEvent*.class`; `.../guides/microphones-and-speakers`; `.../guides/motion`; `dat-ios/.../camera-streaming/SKILL.md`.
   - **Confidence.**
     - That the fields exist: high.
     - Their meaning: low, because it is undocumented.
   - **Design impact.**
     - Treat `presentationTimeUs` as a monotonic ordering key of unknown epoch.
     - Measure glass-to-phone latency ourselves, for example by filming an LED or phone-screen clock through the glasses.
     - Base the hazard-cue age budget on phone arrival time (`SystemClock.elapsedRealtimeNanos()` at collect) plus that measured offset.

9. **Head motion IS exposed: the experimental Motion capability gives accelerometer, gyroscope, magnetometer and orientation at up to 60 Hz.**
   - **Claim.**
     - `mwdat-motion`: `session.addMotion(MotionConfiguration(samplingRate))` returns a `DatResult<Motion>`.
     - `Motion.start()`, `stop()`, `samples: Flow<MotionSample>`, `state: StateFlow<MotionState>`, `errors: StateFlow<MotionError?>`.
     - `MotionSample(timestampNs: Long, accelerometer: Vector3?, gyroscope: Vector3?, magnetometer: Vector3?, orientation: Quaternion?, source: MotionSource)`
     - `MotionSamplingRate { HZ_5, HZ_10, HZ_15, HZ_24, HZ_30, HZ_60 }`, default 10 Hz.
     - `MotionSource { GLASSES, NEURAL_BAND, UNKNOWN }`
     - Units are m/s², rad/s and µT. There is no runtime permission, but it must be enabled in Developer Center.
     - The buffer holds 256 samples and drops the oldest when the consumer falls behind.
     - Known issue: "Motion data is incorrectly rotated 90 degrees for displayless glasses."
     - **Beta:** "available in the development and beta release channels only".
   - **Source.** `x-motion/classes.jar`; `.../guides/motion`; `.../guides/knownissues`; `dat-android/CHANGELOG.md` 1.0.0.
   - **Confidence.** High.
   - **Design impact.**
     - This settles unknown (1): yes, there is head IMU and pose.
     - Uses: head-pitch gating for drop-off cues, "looking at the ground" checks, and turning speech direction words into head-relative terms.
     - Because Motion is beta-only, we cannot depend on it in a production-channel release. Design it as an optional enhancement and fall back to the phone IMU (`SensorRepository.kt`).
     - Apply the 90° rotation fix behind a flag.

10. **Other device state, outside any session: battery, charging, worn or not, hinge, and an 8-level thermal enum. There is no GPS, no ambient light, and no proximity sensor.**
    - **Claim.**
      - `Device` gained `batteryLevel`, `chargingState`, `donState` (`DONNED`/`DOFFED`), `hingeState` (`OPEN`/`CLOSED`) and `thermalLevel` in 1.0.0. They are observed through `Wearables.devicesMetadata[id]` and `DeviceSession.deviceInfo: StateFlow<Device>`.
      - `ThermalLevel { UNKNOWN, NONE, LIGHT, MODERATE, SEVERE, CRITICAL, EMERGENCY, SHUTDOWN }`.
      - Device state "is available as soon as the device connects, before you start a session".
      - The full public type list in `mwdat-core`, `-camera`, `-motion`, `-inputs` and `-speech` contains no location, light, proximity, eye-tracking or depth types. Camera, microphone, IMU and inputs are the only sensor surfaces.
    - **Source.** `dat-android/CHANGELOG.md` 1.0.0; `.../guides/device-state`; class listing of all six AARs.
    - **Confidence.**
      - That these state fields exist: high.
      - That no other sensors exist: medium, because it rests on the absence of an API, not an explicit statement.
    - **Design impact.**
      - Feed `donState`, `hingeState`, `thermalLevel` and `batteryLevel` straight into the system-health cue channel. Examples: "glasses removed, obstacle detection off"; "glasses hot, reducing camera rate".
      - Step our own requested fps and resolution down at `MODERATE`/`SEVERE`, before the glasses force `THERMAL_HOT`.

11. **Thermal, battery and power limits are reported as typed errors. No session-duration cap is documented.**
    - **Claim.**
      - `DeviceSessionError` includes `THERMAL_CRITICAL`, `THERMAL_EMERGENCY`, `PEAK_POWER_SHUTDOWN` and `BATTERY_CRITICAL`.
      - `StreamError` includes `THERMAL_HOT`, `BATTERY_LOW`, `PEAK_POWER_LIMIT` and `TIMEOUT`.
      - I found no documented maximum streaming duration, battery-drain rate or time-to-thermal-throttle.
      - The known-issues page says DAT glasses-app installation fails below 10 % battery.
    - **Source.** `DeviceSessionError.class`; `StreamError.class`; `.../guides/knownissues`; `.../guides/device-state`.
    - **Confidence.**
      - Error types: high.
      - "No documented cap": medium.
    - **Design impact.** Unknown (5) is answered as "limits exist but are not quantified". Sustained streaming time at 15 vs 30 fps, and LOW vs MEDIUM, must be measured on hardware. Plan a "degraded: phone camera fallback" mode.

12. **Audio: the SDK does not route TTS. Output uses OS Bluetooth (A2DP or HFP). In-band microphone PCM over the camera stream is beta. No latency figure is published.**
    - **Claim.**
      - "Device audio uses two Bluetooth profiles". A2DP is output only, at 44.1/48 kHz stereo. HFP is bidirectional mono at 16 or 8 kHz. They are "mutually exclusive: activating HFP switches the glasses away from A2DP, and audio output drops to mono voice quality".
      - HFP mics are beamformed toward the wearer.
      - Playback uses standard OS APIs (`AudioManager` / `AVAudioSession`). The Android sample `AudioInputHandler.kt` routes the HFP mic with `audioManager.mode = MODE_IN_COMMUNICATION` and `setCommunicationDevice(scoDevice)`.
      - Beta in-band audio: `StreamConfiguration(audioCodec = AudioCodec.PCM(sampleRate = AudioSampleRate.RATE_16000, numberOfChannels = 1), …)`, collected from `stream.audioStream`. It is not beamformed. Allowed rates are 16 000, 44 100 and 48 000 Hz. It needs `Permission.CAMERA` and `Permission.MICROPHONE`. It is "not supported in production release channels".
      - iOS ordering rule: "the HFP route must be settled before the stream starts ... can cause the audio route to fail silently."
      - LE Audio is not mentioned anywhere. No audio latency number is given.
      - Known issue: iPhone 17 on iOS 26 has HFP with no audio until iOS 27.
    - **Source.** `.../guides/microphones-and-speakers`; `.../guides/knownissues`; `dat-android/samples/CameraAccess/.../stream/AudioInputHandler.kt`; `dat-android/plugins/mwdat-android/skills/audio-streaming/SKILL.md`.
    - **Confidence.** High.
    - **Design impact.**
      - Speak cues over A2DP (`USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`).
      - Avoid HFP during navigation unless the user is talking, because it degrades cue audio and adds an SCO setup delay.
      - For voice commands, prefer the in-band PCM (beta) or DAT Speech, or the phone mic.
      - A2DP output latency (often hundreds of ms on Bluetooth headsets in general; not measured here) is a direct cost on hazard-cue timeliness. Measure it.

13. **Third-party apps can receive captouch and button input and "Hey Meta, start <app>". All of this is beta, and there is no free-form wake word.**
    - **Claim.**
      - **Inputs.** `mwdat-inputs` `session.addInputs(InputsConfiguration(sources, consumeBack))` returns `Inputs.events: Flow<InputEvent>`. The subtypes are `Nav(direction)`, `Select`, `Back`, `Button(ACTION)`, `Capture(pressType: SHORT_PRESS|HOLD|DOUBLE_PRESS)` and `Drag(action,x,y,dx,dy)`, each carrying `source` and `timestampMs`. `InputSource` values are `CAPTOUCH, NEURAL_BAND, CAPTURE_BUTTON, ACTION_BUTTON, NEURAL_BAND_DRAG, UNKNOWN`.
      - Beta limits:
        - "`Back` events are not currently delivered by real glasses".
        - A double-press arrives as `SHORT_PRESS` followed by `DOUBLE_PRESS`.
        - After the glasses are taken off and put back on with Wear Detection enabled, "inputs stop working" until the capability is re-added.
      - **Voice invocations.** `Wearables.startVoiceInvocationsStream(AutoDeviceSelector())` delivers `LaunchApp` when the wearer says "Hey Meta, start {app name}". The invocation must be acknowledged with `responseHandle.sendSuccess/sendFailure`. It needs approval in Developer Center.
      - **Speech.** `mwdat-speech` gives on-device transcription (`TranscriptionResult`, partial and final, plus a confidence). Known issue: it "silently stops reporting text" after about one minute.
      - Everything above is "development and beta release channels only".
    - **Source.** `x-inputs/classes.jar`; `.../guides/inputs`; `dat-android/plugins/mwdat-android/skills/{inputs,voice-invocations,speech}/SKILL.md`; `.../guides/knownissues`.
    - **Confidence.** High.
    - **Design impact.**
      - Unknown (6) is answered: yes, beta-only.
      - "Hey Meta, start GuideGlass" lets the wearer launch the app hands-free.
      - Capture-button `SHORT_PRESS` could mean "describe scene" and `HOLD` could mean "repeat last cue". Needs a debounce of about 300 ms for the double-press bug.
      - Speech needs a watchdog restart every 50–60 s.
      - The phone-side fallback (`VoiceCommandManager.kt`) is still needed for production builds.

14. **Android background execution: Meta's sample keeps streaming with the screen off using a `connectedDevice` foreground service plus a partial wake lock. DAT documents no other limits.**
    - **Claim.**
      - The CameraAccess sample declares `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_CONNECTED_DEVICE` (plus `WAKE_LOCK`), and `<service ... android:foregroundServiceType="connectedDevice" />`.
      - `StreamingService.kt`'s KDoc says the service "keeps the camera streaming alive when the screen is locked or the app is in the background". It calls `startForeground(..., ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)`. A code comment says this type "doesn't require CAMERA permission since the phone's camera isn't used".
      - It acquires `PARTIAL_WAKE_LOCK` with a 60-minute safety timeout.
      - Android rules (developer.android.com):
        - From Android 14, every foreground service must declare a type.
        - `connectedDevice` needs `FOREGROUND_SERVICE_CONNECTED_DEVICE` plus a granted runtime permission such as `BLUETOOTH_CONNECT`.
        - The `camera` and `microphone` types are "subject to while-in-use restrictions ... you cannot create a [camera/microphone] foreground service while your app is in the background".
        - `mediaProcessing` and `dataSync` are time-limited ("6 hours out of every 24" for mediaProcessing). `connectedDevice` has no documented timeout.
      - Our app uses `targetSdk = 34` on the branch, while the DAT sample uses targetSdk 36.
    - **Source.** `dat-android/samples/CameraAccess/app/src/main/AndroidManifest.xml`; `.../stream/StreamingService.kt`; https://developer.android.com/develop/background-work/services/fgs/service-types
    - **Confidence.**
      - That the sample pattern works: high.
      - Behaviour on Android 16 with aggressive OEM battery managers: low, untested.
    - **Design impact.**
      - Run the whole perception and cue pipeline inside a `connectedDevice` foreground service started while the app is visible.
      - Add the `microphone` type only if we use the phone mic, and start it from the foreground.
      - If phone-camera fallback is used, add the `camera` type and start it while visible.
      - Ask the user to exempt the app from battery optimization.

15. **iOS background: frames continue only with HEVC (`.hvc1`). `.raw` frames stop in the background. The sample now ends preview when the app is backgrounded.**
    - **Claim.**
      - "HEVC frames keep arriving while your app is in the background; `.raw` frames stop."
      - Required `UIBackgroundModes`: `bluetooth-central`, plus (per the plugin guide) `processing`, `bluetooth-peripheral` and `external-accessory`. Also needed: `UISupportedExternalAccessoryProtocols` = `com.meta.ar.wearable`.
      - Streaming on iOS may run over Wi-Fi hosted by the glasses. That needs the `NSLocalNetworkUsageDescription`, `_bonjour._tcp` and Hotspot Configuration entitlements. "If a user denies Local Network access or declines to join the glasses' Wi-Fi network ... continue over Bluetooth LE, but without streaming."
      - The 0.9.0 iOS changelog: "Camera Access now ends the active preview session when the app backgrounds instead of attempting to preserve the session across app switches". The same release also says the sample records "continuing while the app is backgrounded".
      - **Not documented:** behaviour when the phone is locked for a long time, and whether the OS suspends HEVC decoding or our Core ML inference in the background (`VTDecompressionSession` and GPU or ANE access in the background are restricted by iOS generally).
    - **Source.** `.../guides/camera-stream-processing`; `.../guides/build-integration-ios`; `dat-ios/plugins/mwdat-ios/skills/getting-started/SKILL.md`; `dat-ios/CHANGELOG.md` 0.9.0.
    - **Confidence.**
      - That HEVC frames continue: high.
      - That useful background *processing* is possible on iOS: low.
    - **Design impact.**
      - The iOS port must request `.hvc1` and decode it ourselves.
      - Assume the iOS safety path works only while the app is in the foreground with the screen on, until proven otherwise on hardware. Document this as a platform limitation.

16. **iOS vs Android differences.**
    - **Claim.**
      - **Async style.** Android uses `DatResult` with `Flow`/`StateFlow`. iOS uses `throws`/`async`, `stateStream()` AsyncSequences and `*.listen {}` publishers with `ListenerTokenBag`.
      - **Frame API.** Android `VideoFrame.buffer` is YUV in a `ByteBuffer`, or HEVC when `compressVideo`. iOS `VideoFrame` has `.sampleBuffer` (`CMSampleBuffer`) and `.makeUIImage()`, and uses `VideoCodec { raw, hvc1 }`. The iOS default `StreamConfiguration()` is `.raw`, `.medium`, 30 fps.
      - **Stream states.** iOS: `stopping, stopped, waitingForDevice, starting, streaming, paused`. Android: `STARTING, STARTED, STREAMING, STOPPING, STOPPED, PAUSED, CLOSED`.
      - **Photo.** Android `suspend capturePhoto(): DatResult<PhotoData, CaptureError>`. iOS `capturePhoto(format:) -> Bool` plus `photoDataPublisher`. iOS removed `CaptureError` in 0.9.0.
      - **Motion.** Android has `PAUSED` and auto-resumes. iOS has a 10 s no-sample timeout and must be re-attached after a disconnect.
      - **Inputs.** iOS-only `Capability unavailable` error. Android auto-restores after reconnect.
      - **Transport.** Only the iOS docs describe Wi-Fi streaming and the Hotspot entitlement. Android docs talk about Bluetooth Classic only.
      - **Mock testing.** iOS has an XCUITest test-server client (`MockDeviceTestClient`), plus async `disable()`/`unpairDevice()`.
      - **Minimum OS.** iOS 17.2+. Android 10+ (API 29).
    - **Source.** `dat-ios/plugins/mwdat-ios/skills/camera-streaming/SKILL.md`; `dat-ios/CHANGELOG.md`; `.../guides/motion`; `.../guides/inputs`; `.../guides/build-integration-ios`; `.../guides/testing-mdk-ios`.
    - **Confidence.** High for the items quoted.
    - **Design impact.** Keep a platform-neutral `FrameSource` contract: monotonic timestamp, width and height taken per frame, pixel format, and a health state. Write one adapter per platform. Do not share frame-handling code across platforms directly.

17. **MockDeviceKit simulates registration, permissions, device state, camera feed (video file or phone camera), photo, IMU replay, inputs, speech and voice invocations. It runs on an emulator or simulator in CI. It does not emit deterministic audio.**
    - **Claim.**
      - `MockDeviceKit.getInstance(context)`.
      - `enable(MockDeviceKitConfig(initiallyRegistered, initialPermissionsGranted))`.
      - `pairGlasses(GlassesModel)` returns a `DatResult<MockGlasses>`.
      - `MockDevice.powerOn/powerOff/don/doff/setBatteryLevel/setChargingState/setThermalLevel`; `MockGlasses.fold/unfold`.
      - `services.camera: MockCameraKit.setCameraFeed(Uri)`, `setCameraFeed(CameraFacing)` and `setCapturedImage(Uri)`.
      - `services.motion.setMotionFeed(List<MotionSample>, loop)` or `setMotionFeed(csvUri)`.
      - `services.input.navUp/…/capture(CapturePressType)/drag(...)`.
      - `services.captouch.tap()/tapAndHold()`.
      - `services.speech.simulateTranscription(text, isFinal, confidence)`.
      - `services.voiceInvocation.simulateLaunchAppAction()`.
      - `startTestServer(port)`.
      - Mock video must be H.264 or H.265. The docs say "Android doesn't transcode video automatically. Mock video files must be in h.265 format", and the mock-kit table lists "h.264 (AVC), h.265 (HEVC)". The two statements disagree.
      - It "runs inside your app on a device or emulator, so add it with `implementation`, not `testImplementation`".
      - The iOS docs say you can "run XCUITests on CI simulators".
      - It "does not provide a public deterministic audio-frame injection API".
      - Coordinates are the same as the SDK: `com.meta.wearable:mwdat-mockdevice:1.0.1` from Maven Central with no token, and `MWDATMockDevice` from the SPM package.
    - **Source.** `x-mockdevice/classes.jar` (`api/*.class`); `dat-android/AGENTS.md` §Testing; `.../guides/camera-capture`; `.../guides/testing-mdk-ios`; `dat-android/.../audio-streaming/SKILL.md`.
    - **Confidence.** High.
    - **Design impact.**
      - We can run instrumented tests on an emulator, feeding recorded sidewalk HEVC clips (curbs, stairs) through the real DAT `Stream` path, plus IMU CSV replay and thermal/doff injection, to test the health cues.
      - Our branch already uses `debugImplementation`/`androidTestImplementation` for mockdevice and has `androidTest/assets/color_bars.mp4`. Make sure that clip is HEVC.

18. **Distribution: developer preview. Camera streaming is publishable. Motion, Inputs, Speech, Voice Invocations, in-stream audio and standalone photo are not yet. There is one private release channel per integration.**
    - **Claim.**
      - The README says DAT "is in developer preview". The CHANGELOG says "features labeled experimental may change between minor releases. They can be built and tested against, though apps using them cannot be published yet."
      - Release channels: "Each integration supports one private release channel" (an auto-created "Beta"). Testers are invited by Meta-account email; "Managed Meta Account is not supported". The tester limit is shown in the channel's Users tab; no number is published.
      - Each new version triggers a build on Meta's side that takes "within 10 minutes, but may take up to 30".
      - Camera and microphone permissions need a written justification that Meta reviews.
      - The acceptable-use page explicitly encourages "Assistive and accessibility — ... navigation aid". Meta may revoke access "without prior notice".
    - **Source.** `dat-android/README.md`; `dat-android/CHANGELOG.md` header; `.../guides/set-up-release-channels`; `.../guides/manage-projects`; `.../guides/acceptable-use`.
    - **Confidence.** High.
    - **Design impact.**
      - A research study can run on the Beta channel with invited participants. Each participant needs a personal Meta account and must accept an email invite. Budget onboarding time for that.
      - Keep the safety path working on camera streaming alone, since that is the only publishable capability.

19. **Build requirements.**
    - **Claim.**
      - **Android.** `mwdat-*` AAR manifests declare `minSdkVersion="29"` and `targetSdkVersion="33"`. Docs require "Android 10+", "Kotlin 2.2 or later because the toolkit is compiled with Kotlin 2.2", and "Android Studio Flamingo or later".
      - The CameraAccess sample uses AGP 8.11.1, Kotlin 2.2.21, compileSdk 36, targetSdk 36, minSdk 31, JDK 17 and "Android Studio Narwhal (2025.1.1) or newer". Our branch already uses minSdk 29 and compileSdk 36.
      - **iOS.** "Xcode 26.4+, Swift 6.3+, iOS 17.2+ deployment target". The minimum was raised from iOS 15.2 to 17.2 in 0.9.0.
      - The SDK libraries need `BLUETOOTH` and `BLUETOOTH_CONNECT`, and the sample adds `INTERNET`.
    - **Source.** `x-core/AndroidManifest.xml`; `.../guides/getting-started-toolkit`; `dat-android/samples/CameraAccess/gradle/libs.versions.toml` and `app/build.gradle.kts`; `dat-ios/plugins/mwdat-ios/skills/getting-started/SKILL.md`; `dat-ios/CHANGELOG.md` 0.9.0.
    - **Confidence.** High.
    - **Design impact.** Unknown (7) is settled. Our Kotlin plugin version must be at least 2.2. The iOS port needs Xcode 26.4 and an iOS 17.2 minimum.

20. **Data collection is on by default. Crash reporting is on by default.**
    - **Claim.** Opt out with `<meta-data android:name="com.meta.wearable.mwdat.ANALYTICS_OPT_OUT" android:value="true"/>` and `...CRASH_REPORTING_OPT_OUT`. On iOS use `MWDAT > Analytics > OptOut` in Info.plist. "Meta may collect information about how users' Meta devices communicate with your app."
    - **Source.** `dat-android/README.md`; `dat-ios/README.md`.
    - **Confidence.** High.
    - **Design impact.** For participant privacy and IRB, set both opt-outs in the manifest and say so in the consent form.

## Disagreements between sources

- **Mock video codec.** AGENTS.md says Android mock video "must be in h.265 format", while the supported-media table in the same file lists "h.264 (AVC), h.265 (HEVC)". Use HEVC to be safe.
- **Android modules.** AGENTS.md "Architecture" lists "four public modules" (core, camera, display, mockdevice). README and Maven list seven, adding inputs, motion and speech. AGENTS.md is stale.
- **GitHub org.** The repo README uses `facebook/meta-wearables-dat-*`. The official setup page's plugin commands use `meta/meta-wearables-dat-android` and `github.com/meta/...`. Both may resolve through a redirect; I cloned from `facebook/`.
- **iOS backgrounding.** The 0.9.0 CHANGELOG both adds recording "continuing while the app is backgrounded" and says preview "ends ... when the app backgrounds". The likely reading is that recording (HEVC) continues while the raw preview stops. This matches the `.hvc1` vs `.raw` note.
- **Error enums.** The 0.9.0 Android CHANGELOG says `StreamError.THERMAL_EMERGENCY` was removed. iOS 1.0.0 maps several thermal cases to `.thermalHot`. The two platforms converged only in 1.0.0, so older docs and blog posts may cite cases that no longer exist.
- **Version-dependencies page.** It shows a `MWARegistrationRequest` type in its Kotlin sample, but the AAR exposes `com.meta.wearable.dat.core.registration.RegistrationRequest`. Trust the binary.

## Open questions

- What `presentationTimeUs` means: capture time on the glasses vs. phone decode time; its epoch; clock drift relative to Motion `timestampNs`.
- Whether a session can start without internet after registration (offline-start).
- Glass-to-phone video latency at LOW, MEDIUM and HIGH and at 15, 24 and 30 fps, and how it degrades over a 30–60 min walk. The 0.4.0 changelog mentions "Improved stream latency, which was degrading over time".
- Sustained-streaming battery drain and time to `THERMAL_HOT` on Ray-Ban Meta Gen 2 outdoors in summer.
- Whether the Android stream survives Doze and OEM killers on Pixel and Samsung with Android 15/16 and the screen off for more than 30 min.
- Exact YUV layout of decoded Android frames: I420 vs NV12 vs NV21, and row stride.
- Whether captouch `PAUSED` can be disabled, or consumed by `Inputs` so it does not pause the camera.
- Whether Motion and Camera can run concurrently without lowering the camera's frame rate.

## Things that can only be measured with real hardware

- Glass-to-phone video latency end to end (photon to `videoStream` collect), for example with a phone stopwatch filmed through the glasses.
- Phone-to-ear audio cue latency over A2DP vs HFP. Includes the time to set up the SCO route.
- Real delivered resolution and fps over time while walking, especially with a congested 2.4 GHz band near Wi-Fi APs.
- Glasses battery drain per hour and the thermal ramp for each configuration. Phone battery and thermal state with DAT plus on-device ML running.
- Session behaviour when the screen is off or locked, in Doze, and while switching to other apps (Android and iOS).
- Captouch, capture-button and "Hey Meta, start" input latency and reliability on Beta firmware.
- IMU-to-frame alignment, given on-glasses stabilization. Validation of the 90° rotation bug fix.
- Our own camera intrinsics per glasses model, from calibration on stream frames.

## What the glasses can do for us

- Stream the head-mounted camera to the phone at 360×640, 504×896 or 720×1280 and 2–30 fps, as YUV or HEVC, with a `presentationTimeUs` on each frame. Distortion is already corrected and the image is stabilized.
- Report the stream lifecycle (`STREAMING`/`PAUSED`/`STOPPED`/`CLOSED`) and typed errors: thermal, battery, hinge closed, permission, timeout.
- Report battery, charging, worn or not, hinge and an 8-level thermal state, even before a session starts.
- Play our TTS and earcons through the open-ear speakers over standard OS Bluetooth (A2DP).
- Capture the wearer's voice over HFP (beamformed). In beta, deliver ambient PCM in-band with video.
- *(Beta)* Stream IMU data (accel, gyro, mag, fused orientation) at 5–60 Hz.
- *(Beta)* Deliver captouch, capture-button and action-button events. Launch the app with "Hey Meta, start GuideGlass". Provide on-device speech transcription.
- Be tested with no hardware through MockDeviceKit on an emulator or simulator, including video feeds, IMU replay, thermal and doff injection, and input injection.
- Keep streaming on Android with the screen off, via a `connectedDevice` foreground service (Meta's sample pattern).

## What they cannot do, or what is unknown

- No depth sensor, no camera intrinsics, no GPS or location, no ambient light or proximity sensor through DAT.
- No callback when adaptive quality steps down. The resolution and fps changes are silent.
- No documented capture-time semantics for frame timestamps, and no published latency numbers.
- No documented session-duration, battery or thermal budget.
- No free-form wake word or custom voice commands. Only "Hey Meta, start <app>" (beta).
- Motion, Inputs, Speech, Voice Invocations, in-band audio and standalone photo **cannot ship on the production channel yet**.
- A single captouch tap pauses the stream, which risks the wearer switching off hazard detection by accident.
- iOS: raw frames stop in the background. Useful background processing on iOS is unproven.
- Registration requires the Meta AI app and internet. Whether the app can start offline afterwards is unknown.
- Stabilization makes a camera–IMU rigid-body model inaccurate.

## Sources

- https://github.com/facebook/meta-wearables-dat-android (README.md, CHANGELOG.md, AGENTS.md, plugins/mwdat-android/skills/*, samples/CameraAccess/*), commit f4371bc, 2026-10-09
- https://github.com/facebook/meta-wearables-dat-ios (README.md, CHANGELOG.md, plugins/mwdat-ios/skills/*, samples/CameraAccess/CameraAccess/Info.plist), commit bbae597, 2026-10-09
- https://repo1.maven.org/maven2/com/meta/wearable/ (mwdat-core, -camera, -motion, -inputs, -mockdevice, -speech 1.0.1 AAR and POM; mwdat-core maven-metadata.xml)
- https://wearables.developer.meta.com/llms.txt?product=all&version=1.0&document=products/dat/sections/guides/{getting-started-toolkit, streaming, camera-stream-processing, microphones-and-speakers, lifecycle-events, motion, inputs, speech, voice-invocations, device-state, mock-device-kit, testing-mdk-android, testing-mdk-ios, camera-capture, build-integration-android, build-integration-ios, knownissues, version-dependencies, set-up-release-channels, manage-projects, permissions-requests, acceptable-use}
- https://wearables.developer.meta.com/docs/develop/ (human-readable versions of the above)
- https://developer.android.com/develop/background-work/services/fgs/service-types
- GuideGlass `origin/meta-glasses`: `app/build.gradle.kts`, `app/src/main/java/com/impairedvision/guideglass/glasses/GlassesCameraSource.kt`
