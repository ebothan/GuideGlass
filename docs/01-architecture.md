# Architecture

Version 1, 2026-11-12. Decisions and alternatives are in `docs/adr/0001` to `0005`. Requirements (REQ-xx) and scenarios (S-, F-, NV-, C-) are in `docs/00-problem-constraints-goals.md`.

Nothing in this document has been built yet. It is the specification for P4.

## 1. Components

```
 glasses camera ──Bluetooth──▶ Frame source ──▶ Perception ──▶ Tracker ──▶ Hazard assessor ─┐
   (or mock / replay)             │ stamps         │ stamps      │ stamps        │ stamps    │
                                  ▼                                                          ▼
                             Health monitor ───────────────────────────────────────────▶ Cue arbiter ──▶ Audio sink ──▶ ear
                                  ▲                                                          ▲              (glasses, or
 link + audio route state ────────┘                                                          │               phone fallback)
 GPS + route ───────────────────▶ Navigator ─────────────────────────────────────────────────┤
 cloud model ──(network)────────▶ Cloud guard ───────────────────────────────────────────────┘
```

**Safety path** (no network, REQ-01): frame source, perception, tracker, hazard assessor, health monitor, cue arbiter, audio sink.

**Off the safety path:** navigator (needs a route, which can be cached), cloud guard.

| Component | Job | Borrowed from automated driving | Where the analogy breaks |
|---|---|---|---|
| Frame source | Delivers frames with a capture timestamp and a size, from glasses, mock device or replay | Sensor abstraction with per-sample timestamps | Timestamp meaning is undocumented (K4); frames are already stabilised and cropped (K3) |
| Perception | Per frame: boxes with class, a range estimate and its uncertainty | Detection plus depth | One moving camera, no lidar or radar; range error about ±25% |
| Tracker | Associates detections over time; holds range, lateral offset and their rates in a walker-centred metric frame; predicts forward | Multi-object tracking with Kalman prediction | Head turns move every object in the image at once; track life is set in seconds because frame rate varies |
| Hazard assessor | Time to contact per track on two channels; corridor test; raises CAUTION or STOP by lead time at the ear | Time-to-collision and forward-collision-warning timing | The "vehicle" is a person: about 0.5-1.0 s reaction and one or two steps to stop, and the only actuator is a sound |
| Health monitor | Watches frame age, frozen frames, frame rate, inference stalls, link and audio route; owns the system state and the degraded cues | Independent safety monitor and takeover request | The fallback is not a physical stop. It is telling the user that protection is off |
| Cue arbiter | One audio stream: priority, pre-emption, per-object alert state, rate limits, staleness | Alert prioritisation (aviation practice more than automotive) | Speech takes 0.5-2 s to say, so wording is part of the timing budget |
| Navigator | Follows the route; turn cues by predicted time; off-route detection | Route following | GNSS error can exceed the cue's whole lead distance; no lane-level map for sidewalks |
| Cloud guard | Classifies cloud answers by risk; drops, hedges or passes them | Plausibility gate between a non-assured component and the actuator | The "actuator" is language, so the gate must reason about meaning |
| Audio sink | Plays earcons and speech; reports route state and start time | Human-machine interface | Bluetooth audio delay of unknown size sits after the last point the software can measure |

## 2. Timing

Every frame carries a record of stage timestamps (REQ-27):

`t_capture`, `t_receive`, `t_perception_done`, `t_track_done`, `t_decision`, `t_audio_start`, and `t_ear_predicted` = `t_audio_start` + configured audio latency.

- The assessor uses `now - t_capture` as the frame's age.
- If `t_capture` proves to be arrival time (K4), a configured offset is added. A hardware measurement can set that offset without code changes.
- The latency report compares each stage's measured p50, p95 and p99 with the budget in section 7 of the problem document.

## 3. System states and what the user hears

The health monitor holds exactly one state. A change of state produces the cue shown.

- Wording follows the cue table in `docs/research/03`. No phrase contains "clear" or "safe" (REQ-18).
- All times are [A] until measured.
- "Phone" means the phone's own speaker plus vibration, used when the glasses cannot be reached.

| State | Entered when | Detected within | User hears | Repeats | Hazard cues | Leaves when |
|---|---|---|---|---|---|---|
| STARTING | App starts protection | n/a | Start chime, then "Hazard alerts on" | No | Not yet | First fresh frame |
| NORMAL | Fresh frames inside the ODD, audio on the glasses | n/a | Nothing (optional soft tick every 20-30 s, off by default) | n/a | Full | |
| REDUCED | Frame rate under 7 fps, or frame age 300-1000 ms, for over 2 s (F-03, F-04) | 2 s | Low tone, "Reduced detection" | Once | Confirmed tracks only; no motion channel | 5 s back in NORMAL conditions, then rising tone, "Detection normal" |
| HEAD TURN | Yaw rate over 60 deg/s (S-13) | 1 frame | Nothing; soft tick if it lasts over 2 s | No | Confirmed tracks only | Yaw rate drops |
| CAMERA LOST | No fresh frame for 0.5 s, frozen frames, stream paused or stopped, or inference stalled over 1 s (F-01, F-02, F-09) | 0.6 s from the last fresh frame | Falling three-note earcon, "Camera lost. Use your cane." Phone vibrates twice | Every 30 s | None | Fresh frames for 3 s, then "Camera back" |
| GLASSES LOST | Bluetooth link down (F-05) | 1.0 s from the OS signal | On the phone: "Glasses disconnected" | Every 30 s on the phone | None (no camera) | Link and stream back, then "Glasses back" |
| AUDIO REROUTED | Glasses audio route lost or switched to call audio while video continues (F-06) | 1.0 s from the OS signal | On the route that works: "Glasses audio lost. Alerts on phone." | Once | Full, on the phone speaker and vibration | Route restored, then "Glasses audio back" |
| OUT OF ODD | Low light or other ODD check fails (S-20) | 2 s | "Low light. Use your cane only." | Every 60 s | Best effort, not relied on | Checks pass for 5 s, then "Detection normal" |
| STOPPED | User or app ends protection | n/a | Stop chime, then "Hazard alerts off" | No | None | |

Independent of state:
- **Network lost** (F-07): one low-priority notice, "Offline. Hazard alerts still on." Navigation continues on the cached route. Cloud requests answer "Not available offline."
- **Cloud slow** (F-08): nothing is heard; the late answer is dropped by the guard.
- **App killed** (F-10): cannot be announced. The start and stop chimes are the only bracket. This is a residual risk.

Priority when several conditions hold: GLASSES LOST, then CAMERA LOST, then AUDIO REROUTED, then OUT OF ODD, then REDUCED, then HEAD TURN.

## 4. Cue priorities

| Priority | Cues | May cut off |
|---|---|---|
| P0 | STOP (earcon, "Stop", phone vibration) | Everything |
| P1 | CAUTION with type and clock position; moving object; camera lost; glasses lost | P2 and below |
| P2 | "Turn now"; reduced detection; audio rerouted; out of ODD | P3 and below |
| P3 | Turn pre-announce; arrival; recovered notices; offline notice; cloud speech | Nothing; queued, dropped when stale |
| P4 | Alive tick | Nothing |

Rules (REQ-15 to 17):
- A higher priority cuts off a lower one at once. Equal priority queues.
- Each track has its own alert state: none, cautioned, stopped. It re-arms only after 1.2 s outside the alert zone.
- Non-urgent speech is limited to one utterance per 4 s.
- Queued cues carry a staleness limit and are dropped past it.

## 5. Build shape

- `core/`: plain Kotlin library, own Gradle build, no Android types (ADR 0002). Holds every component above except the real frame source, real perception models and real audio.
- `core` test doubles: synthetic scene source, geometric perception with injected noise, logging audio sink, replayed GPS, recorded route and cloud answers, simulated clock.
- `paper/simulation/`: unchanged. A new script exports its scene set so the Kotlin hazard assessor is scored on the identical scenes (G6).
- `app/`: existing Android app, untouched for now. Later it becomes adapters around `core` (DAT 1.0.1 camera, on-device models, audio, location).

## 6. Known gaps carried into P4

- Drop-offs, stairs and glass are not detected (S-16, S-17).
- Real perception models are not run; all detection evidence stays at L1.
- Audio latency and the frame timestamp's meaning are configuration values until measured on hardware.
- The Android app is not compiled on this machine (ADR 0003).
