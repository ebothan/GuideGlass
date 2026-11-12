# End-to-end desktop run: what the user would hear (gate G2a)

Evidence label: **L3-substitute** (full decision pipeline on the desktop JVM, synthetic frame source, logging
audio sink). Not an emulator run and not a real device. Times are seconds from start; `ear` includes drawn audio
latency. Each run's complete input stream is saved as JSON Lines under `core/build/demo/` and replays to the
same cues (ReplayTest).

## S-01 person standing in path

Obstacle contact: none (simulated walker stops on STOP). Replay of the saved event log (1123 events) gives identical cues: **true**.

| start s | ear s | route | cue | text |
|---|---|---|---|---|
| 0.00 | 0.21 | GLASSES | START | Hazard alerts on. |
| 1.01 | 1.31 | GLASSES | CAUTION | Person, 12 o'clock. (cut off START) |
| 4.13 | 4.44 | GLASSES | STOP | Stop. |

## S-07 stop, back off, approach again

Obstacle contact: none. Replay of the saved event log (1970 events) gives identical cues: **true**.

| start s | ear s | route | cue | text |
|---|---|---|---|---|
| 0.00 | 0.21 | GLASSES | START | Hazard alerts on. |
| 0.53 | 0.83 | GLASSES | CAUTION | Obstacle, 12 o'clock. (cut off START) |
| 1.35 | 1.66 | GLASSES | STOP | Stop. (cut off CAUTION) |
| 11.42 | 11.60 | GLASSES | CAUTION | Obstacle, 12 o'clock. |
| 12.46 | 12.74 | GLASSES | STOP | Stop. (cut off CAUTION) |

## S-10 cyclist approaching head-on

Obstacle contact: 5.05 s. Replay of the saved event log (748 events) gives identical cues: **true**.

| start s | ear s | route | cue | text |
|---|---|---|---|---|
| 0.00 | 0.21 | GLASSES | START | Hazard alerts on. |
| 2.80 | 3.10 | GLASSES | MOVING_OBJECT | Bike approaching, 12 o'clock. |
| 3.25 | 3.56 | GLASSES | STOP | Stop. (cut off MOVING_OBJECT) |

## S-19 crowd ahead

Obstacle contact: 2.83 s. Replay of the saved event log (621 events) gives identical cues: **true**.

| start s | ear s | route | cue | text |
|---|---|---|---|---|
| 0.00 | 0.21 | GLASSES | START | Hazard alerts on. |
| 0.46 | 0.76 | GLASSES | CAUTION | Person, 12 o'clock. (cut off START) |
| 0.74 | 1.05 | GLASSES | STOP | Stop. (cut off CAUTION) |
| 1.37 | 1.55 | GLASSES | CAUTION | Person, 12 o'clock. |

## F-01 obstacle approached while the camera is lost

Obstacle contact: 6.28 s. Replay of the saved event log (788 events) gives identical cues: **true**.

| start s | ear s | route | cue | text |
|---|---|---|---|---|
| 0.00 | 0.21 | GLASSES | START | Hazard alerts on. |
| 0.68 | 0.98 | GLASSES | CAUTION | Obstacle, 12 o'clock. (cut off START) |
| 1.97 | 2.28 | GLASSES | CAMERA_LOST | Camera lost. Use your cane. |

## F-05 Bluetooth link lost 4-8 s

Obstacle contact: none. Replay of the saved event log (1685 events) gives identical cues: **true**.

| start s | ear s | route | cue | text |
|---|---|---|---|---|
| 0.00 | 0.21 | GLASSES | START | Hazard alerts on. |
| 4.01 | 4.31 | PHONE | GLASSES_LOST | Glasses disconnected. |
| 11.36 | 11.67 | GLASSES | GLASSES_BACK | Glasses back. |

## F-03 frame rate 7 fps then 2 fps

Obstacle contact: none. Replay of the saved event log (2648 events) gives identical cues: **true**.

| start s | ear s | route | cue | text |
|---|---|---|---|---|
| 0.00 | 0.21 | GLASSES | START | Hazard alerts on. |
| 10.00 | 10.30 | GLASSES | REDUCED | Reduced detection. |
| 17.88 | 18.19 | GLASSES | DETECTION_NORMAL | Detection normal. |

## Navigation A to B on a replayed GPS trace (recorded synthetic response (no key))

| t s | true distance along route m | cue | text |
|---|---|---|---|
| 77 | 100.2 | TURN_PRE | In 20 meters, turn right. |
| 84 | 109.3 | TURN_NOW | Turn right now. |
| 140 | 182.1 | CROSSING_AHEAD | Crossing ahead. |
| 162 | 210.7 | TURN_PRE | In 20 meters, turn left. |
| 170 | 221.1 | TURN_NOW | Turn left now. |
| 235 | 305.6 | TURN_PRE | Destination ahead. |
| 249 | 323.8 | ARRIVAL | Arriving. |
