# Hardware-validation checklist

For a person who has Meta glasses, an Android phone and a machine with Android Studio. Everything the prototype shows so far is simulation (rung L1). The steps below are the ones that can move any claim to L3 (emulator or mock device) or L4 (real device and glasses).

**This checklist does not authorise any trial with people.** Rung L5 (supervised use by blind or low-vision participants) needs ethics approval and an orientation and mobility specialist. Nothing here asks for that. Steps that put the system on a real street are for sighted testers who also use their own judgment, and the system must not be relied on in them.

Status of the whole prototype: `docs/safety-case.md`. Sources for platform facts: `docs/research/06-meta-glasses-platform.md`.

## 0. Before you start

| # | Item | Why |
|---|---|---|
| 0.1 | Clone the fork, check out `av-prototype`, run `./init.sh`. Expect: R5 reproduced (2.00% / 47.39%), 85 tests pass | Proves the machine builds the core |
| 0.2 | Install Android Studio, SDK platform 34 or later, and a recent emulator image | The SDK was not installed where this was built; gate G2 stays blocked until this exists |
| 0.3 | Note the exact glasses model, firmware and Meta AI app version | The research found 1.0.1 needs Meta AI app V292 and glasses firmware V128 (read from docs, not verified) |
| 0.4 | Enable Developer Mode in the Meta AI app | Per the docs, the app ID and client token can be 0 in Developer Mode; production needs Developer Center credentials and an invite-only release channel |

## 1. Build the Android adapters (nothing here is written yet)

The existing `app/` is unchanged and still contains the old two-tier design. The decision core (`core/`) has no Android types. Adapters to write, one per port in ADR 0002:

| # | Adapter | Notes from the research | Done when |
|---|---|---|---|
| 1.1 | Frame source from the DAT camera stream | SDK 1.0.1 (not the 1.0.0 the `meta-glasses` branch pins; 1.0.1 fixes frame freezes). Maven Central group `com.meta.wearable`, artifacts such as `mwdat-core`, `mwdat-camera`, `mwdat-mockdevice`. Minimum Android API 29, Kotlin 2.2 or later | Frames reach `SafetyPipeline.onFrame` with a monotonic arrival time |
| 1.2 | Build tooling | The repo's root build uses Kotlin 1.9.22 and AGP 8.2.2, older than the SDK needs. The upgrade is untested | `app` builds against the SDK |
| 1.3 | Perception: detector, depth, pitch | Candidates in `docs/research/05-on-device-perception.md` (YOLOX-class, Depth Anything V2 Small). Pitch and head-to-travel angle must come from a real source; the simulation was given both as truth | Detections with range, class and an honest uncertainty |
| 1.4 | Audio sink: earcons, speech, route callbacks | A2DP and HFP are mutually exclusive; HFP makes cue audio mono 8 or 16 kHz (read). Phone speaker and vibration fallback | The core's route events come from the operating system |
| 1.5 | Location and heading | Fused location, with the accuracy radius passed to the navigator | `NavConfig` uses real accuracy |
| 1.6 | Foreground service | Android keeps streaming with the screen off through a `connectedDevice` foreground service plus a wake lock in Meta's sample (read, unverified) | Protection continues with the screen off |
| 1.7 | Cloud client and on-phone blurring of faces and plates before upload | Not built. Without blurring, do not enable the cloud tier | Privacy review passed |
| 1.8 | Drop frames that arrive out of order | Review finding 20 | Capture times are non-decreasing at the core |

## 2. Emulator or mock device (rung L3)

| # | Test | Pass condition |
|---|---|---|
| 2.1 | Run the app in the emulator with `mwdat-mockdevice` feeding recorded video | Cues are produced, spoken and logged (closes gate G2) |
| 2.2 | Replay the 7 desktop scenarios' event logs (`core/build/demo/*.jsonl`) through the Android build | Same cues as the desktop run |
| 2.3 | Pause, stop and reconnect the mock stream | "Camera lost" and "Camera back" as in `HealthTest` |
| 2.4 | Rotate the screen, background the app, lock the phone | Protection state is correct and announced |

## 3. Real glasses and phone, bench tests (rung L4)

Nobody walks anywhere for these.

| # | Measurement | How | What it replaces |
|---|---|---|---|
| H-1 | **Capture-to-phone video delay** | Show a running millisecond counter to the glasses; record the phone screen and the counter together; compute the difference per frame | Assumed 60 ms (glasses) + 80 ms (Bluetooth) + 20 ms (decode) |
| H-2 | **What the frame timestamp means** | Compare `presentationTimeUs` with the counter in H-1 | Unknown: capture time or arrival time (review finding 9) |
| H-3 | **Audio delay to the ear** | Play a click from the app; record the glasses speaker and the phone clock with a microphone; repeat 100 times; report p50, p95, p99 for A2DP, and for the phone speaker | Assumed 200 ms p50, 300 ms p95 |
| H-4 | **Frame rate and size actually delivered** | Log per-frame size and rate for 10 minutes at each requested setting; watch for silent step-downs and a pause on a captouch tap | Assumed 15 fps or more; the docs say quality can step down silently |
| H-5 | **Clock offset** between glasses frames and the phone | From H-1 and H-2 | Zero |
| H-6 | **Camera-loss detection on hardware** | Cover the lens, pull the Bluetooth link, switch glasses off, kill the app, put the glasses on a charger, turn the screen off | Time from event to the "Camera lost" cue at the ear is within the 1.0 s target; list every case that stays silent |
| H-7 | **Operating-system callbacks** for link and audio-route changes | Toggle Bluetooth, connect another audio device, take a phone call | "Glasses disconnected" and "Glasses audio lost" within 1.0 s |
| H-8 | **Camera height, field of view and distortion** | Photograph a measured grid through the glasses | Assumed 1.30 m (a chest-held phone), 60 by 46 degrees; the glasses crop and stabilise frames and report no calibration |
| H-9 | **Gyro and pitch** | Check whether `mwdat-motion` is available to your build (it was beta-only) and what it reports | Pitch and head-turn rate were given as truth |
| H-10 | **Battery and heat** | Run 60 minutes streaming and detecting | Not monitored in the core; review finding 18 |
| H-11 | **Loudness against traffic noise** | Measure cue sound level at the ear in a quiet room and next to a road, with a sound meter | No data |

Put the measured values into `CoreConfig` (`audioOutLatencyS`, `nominalPipelineS`, `speechStartS`) and into the latency report, then rerun `./gradlew -p core runDemo` so the report uses real stage times. Gate G3 is `needs_hardware` until that report is built from measured stages.

## 4. Real footage replay (rung L2)

| # | Test | Notes |
|---|---|---|
| 4.1 | Download one SANPO-Real session (CC BY 4.0) and replay frames through the real detector, depth model and pitch estimator | Research note 08 gives the URL. Frames are about 3.5 MB each with depth; ten frames come to about 42 MB |
| 4.2 | Annotate in-path obstacles by hand and score hits, misses and false alarms per encounter | This is where the "detector returns a box" assumption is tested |
| 4.3 | Record your own glasses footage while walking a route you can see | Needed because SANPO cameras are not the Meta stream (stabilised, cropped, compressed) |
| 4.4 | False alarms per walking hour, and STOP precision | The main open figure: simulated false-STOP rates are 10-13% on three quiet scenes |
| 4.5 | Statistical support | Showing a miss rate below 5% needs at least 59 hazardous encounters with zero misses; showing a drop from 47.4% to 30% needs about 122 per arm (research note 08) |

## 5. Street tests by sighted testers (rung L4)

Only after sections 2 to 4 pass. Shadow mode: the system listens and logs, speaks nothing or speaks only to the tester, who also uses their own eyes.

| # | Test | Pass condition |
|---|---|---|
| 5.1 | Walk 10 known routes; log every cue with its timestamps | Every STOP and CAUTION can be matched to a real object or marked false |
| 5.2 | Count missed hazards by class | No hazard class is silent (the simulation cannot detect drop-offs, stairs, glass or objects outside the 46-degree view) |
| 5.3 | Navigation: walk the route with real Directions responses and a real key | Turn cues arrive before the corner and are not misleading; check how crossings appear in real route text (the synthetic route used the word "cross") |
| 5.4 | Guide dog or companion walking ahead | No repeated STOPs |
| 5.5 | Crowds, rain, low sun, night | System announces or the case is listed as outside the domain |

## 6. Needs approval and a specialist (rung L5)

Do not start these without ethics approval and an orientation and mobility specialist present.

- Reaction and stopping distance to the STOP cue in blind and low-vision users, including older users.
- Comprehension of each degraded-state cue.
- Whether users keep responding to STOP after a week of use.
- Whether cues mask traffic sound.
- Whether the aid changes cane or dog use.

## 7. Open design problems the hardware may not fix

1. False STOPs: 10-13% on quiet scenes, and a "caution" in 78% of runs for a distant wall.
2. A conservative-walker setting avoids contact but gives a false STOP in 87% of runs behind a pedestrian.
3. Drop-offs, curbs, stairs and glass are not detected; a ground-surface model is needed.
4. Objects outside the 46-degree view are invisible.
5. The cloud filter is a word list.
6. No detector output means silence in state NORMAL.
