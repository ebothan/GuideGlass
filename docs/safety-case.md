# Lightweight safety case

Version 2, 2026-11-12, revised after the adversarial review (`docs/review/adversarial-review.md`, response in `docs/review/response.md`). Draft origin: `docs/research/02-av-safety-engineering.md`. This version replaces the draft's targets with what was actually built and measured in P4.

This is a research prototype. It has never run on a phone or glasses and has never been used by a person. Nothing below is a claim about real streets. Evidence rungs (L0-L5) are defined in `CLAUDE.md`. "L1" means simulated with synthetic scenes and an idealised detector.

## 1. Top claim

> Within its operating domain, used by a trained person who always also uses a long cane or guide dog, the system gives supplementary audio cues about obstacles in the walking path and approaching objects. It announces loss of the camera, the glasses link and the audio route, and low frame rate or low light. It is designed not to tell the user that anything is safe, clear, or time to cross.

**Status of the top claim: not established.** Only the decision logic has evidence, and only at L1. An independent adversarial review (`docs/review/`) found four critical defects in version 1 of this case. They are fixed or restated below, with residuals. The claim needs L4 and L5 evidence before anyone relies on it.

The system does **not** announce every case in which it cannot see. Known silent cases: a detector that returns no boxes, a covered or fogged lens, a killed app, and a camera clock that runs ahead by less than 1 s.

## 2. Losses and hazards

| ID | Loss or hazard |
|---|---|
| L-1 | Injury to the user (collision, fall, traffic) |
| L-2 | Injury to others (no evidence gathered) |
| L-3 | Loss of trust: the user abandons a useful aid, or over-relies on it |
| L-4 | Privacy harm to bystanders (no evidence gathered; on-phone blurring before upload is not built) |
| H-1 | The user approaches a hazard without a timely cue |
| H-2 | The user takes an unsafe action because of a cue |
| H-3 | The user believes the path is clear while the system cannot see |
| H-4 | Audio output masks the environment or distracts |
| H-5 | The user learns to ignore cues because many are false |
| H-6 | The user reduces reliance on the cane or dog |
| H-7 | The user's own guide dog or companion triggers alerts (added after review) |

## 3. Sub-claims, evidence and gaps

Rates are from `docs/results/scenario_rates.md`: 300 seeds not used for tuning, 95% intervals there.

### C1. Hazard cues come early enough (H-1)

| Evidence | Rung |
|---|---|
| Still obstacles (person, pole, bin, low bollard, head-height branch, barrier): a simulated typical walker who stops on STOP makes contact in 0/300 runs each | L1 |
| Same for a standing person at 5 fps, at 7 fps, with +0.4 s transport delay and in low light; and for a bin with 40% of detections dropped | L1 |
| Typical walker at 0.6 m/s: 0/300 (STOP lead scales with walking speed) | L1 |
| After stopping for a STOP and walking on, a second STOP is given: 300/300 | L1 |
| Approaching pedestrian: STOP later than 1.36 s before contact in 3/300. Cyclist at 4.5 m/s: in 26/300; minimum 1.01 s | L1 |

Gaps:
- **No real detector, depth model or pitch estimator has been run.** Every result assumes a box exists for the obstacle and that range errors are as assumed. A detector that returns nothing gives silence.
- **Conservative walker (1.0 s reaction, two steps), default setting: contact in 9-12% of still-obstacle runs and 33% for the head-height branch.** With the conservative setting configured: 0-1/300, and 8/300 for the branch. The conservative setting has a high false-STOP cost (C4).
- A person who first becomes visible 3 m ahead: contact in 19.7% of runs for the typical walker and 100% for the conservative one.
- 10 fps with 20% frame loss: contact in 1.0%.
- The comparison with the repo's rule R5 is not like-for-like (`docs/results/g6_baseline_comparison.md`). The method is given a depth value derived from the true distance. With the camera pitch also given it shows 11.3% misses against 47.4%; without the pitch, 19.6% misses and more false alarms than R5.
- The perception stand-in is also given the true head-to-travel angle, camera height (1.30 m, not glasses height), walking speed and the head-height flag. Each is an estimator that does not exist yet. The reviewer measured, for example, 29% late STOPs with the head turned 20° and its direction unknown.
- Drop-offs, curbs, stairs and glass are not detected. Nothing outside the 46° horizontal field of view is seen: a car leaving a driveway, a cyclist from the side.
- Bluetooth video and audio delays are assumed. The latency report (`docs/results/latency_report.md`) is an arithmetic check, not a measurement.
- Two objects closer than about 1.5 m are treated as one; without a stop between them the second gets no cue of its own.

### C2. Cues do not cause unsafe actions (H-2)

| Evidence | Rung |
|---|---|
| The safety path gives no steering instruction; the repo's "veer left/right" hint is removed | L1 |
| Navigation never says "now": "Turn right at the next corner."; crossings only "Crossing ahead." | L1 |
| Cloud: crossing questions are always refused; "forward" is never spoken; free text on movement, traffic or path state is not spoken; the reviewer's 24 paraphrases are silent; no input can make the guard throw | L1 |

Gaps:
- The cloud filter is a word list and can be evaded by a paraphrase that avoids the listed words.
- Turn cues come several seconds before the corner (GPS noise). Crossings are recognised only from the word "cross" in route text, tested on a synthetic route. A real route may steer a turn across a road without any crossing announcement.
- A STOP on stairs or mid-crossing may itself be dangerous, and false STOPs are common (C4). Unmitigated.
- Clock positions assume the head direction relative to travel is known. If it is not, "2 o'clock" is relative to the camera.

### C3. Loss of sensing is announced (H-3)

| Evidence | Rung |
|---|---|
| Camera stopped, frozen, stalled, or delivering frames more than 1 s old: "Camera lost. Use your cane." starts within 0.6 s of the last usable frame, or within 1.25 s if a "Stop." is playing | L1 |
| Health uses only the phone's own clock; a frame stamped in the future does not hide the loss | L1 |
| No hazard cue is given from stale data while the camera is lost | L1 |
| Bluetooth loss: "Glasses disconnected" on the phone speaker within 1.0 s of the link signal; low frame rate, low light and audio reroute are each announced | L1 |

Gaps:
- The silent cases listed under the top claim.
- A stream that flaps on and off every few seconds alternates "Camera lost" and "Camera back".
- "Reduced detection." does not tell the user what is reduced.
- Link and audio-route callbacks from the operating system have not been exercised. The phone speaker may be inaudible in a pocket.
- Thermal throttling or low battery would push the phone into degraded states; neither is monitored.

### C4. False alarms stay low enough to keep trust (H-5, H-7)

**Not supported.** Measured rates on scenes where no STOP should be given:

| Scene | Runs with a false STOP | Runs with any CAUTION |
|---|---|---|
| Parked car beside the path | 9.7% | 13.7% |
| Wall never closer than 7.4 m | 0.7% | 77.7% |
| Cyclist crossing well ahead | 2.0% | 67.3% |
| Pedestrian ahead at the same pace | 13.0% | 100% |
| Fast head turns past roadside objects | 12.0% | 7.7% |

- With the conservative setting, following a pedestrian gives a false STOP in 87% of runs.
- On the repo's scene set the deployed setting alerts on 15.9% of hazard-free scenes (27.0% without a pitch estimate).
- A guide dog 1 m ahead: one STOP at first sight, then 0.03 per minute.
- Repetition is controlled: one alert sequence per approach in 299/300 runs for both repetition cases.
- No false alarms per walking hour figure exists.

Users respond to alarms about as often as the alarms are true (research note 03). At these rates the STOP cue would probably lose its meaning on a busy sidewalk. Reducing them without reopening the misses fixed under C1 is the main open design problem.

### C5. Audio does not mask the environment (H-4)

Evidence: non-urgent speech is limited to once per 4 s; one cue at a time. L1.

Gaps: nothing is known about loudness, masking of traffic by open-ear speakers, or real cue load. CAUTION cues are frequent (C4). Needs L4 and L5.

### C6. The user keeps using the cane or dog (H-6)

Evidence: none. This is a training and study-protocol matter. Needs L5 with an orientation and mobility specialist.

## 4. Operating domain

As stated in `docs/00-problem-constraints-goals.md` section 5. The system announces leaving the domain only for low frame rate, high frame age, low brightness, and lost camera or link. Rain, crowds, crossings and stairs are not detected and are residual risks.

## 5. What would falsify this case

- A real detector that misses a material share of in-path obstacles at 3-5 m.
- Measured capture-to-ear latency above about 1.2 s at p95.
- Real range error much worse than 30%.
- Users who do not stop on the STOP earcon within about 1 s.
- Any field evidence that users treat silence as clearance despite training.

## 6. Evidence still required

| Claim | L2 replayed real footage | L4 real device and glasses | L5 supervised users |
|---|---|---|---|
| C1 | Miss rate per hazard encounter with a real detector and depth model on annotated footage | Capture-to-ear latency; range error against a tape measure | Stopping distance and reaction on the STOP cue |
| C2 | Cue wording review on real routes | Turn cue timing with real GNSS | Whether any cue prompted an unsafe move |
| C3 | Replay with real dropouts | Pull the link, cover the lens, kill the app | Whether users understand each degraded cue |
| C4 | False alarms per walking hour | Same, on walks by sighted testers | Whether users keep responding after a week |
| C5, C6 | n/a | Loudness against traffic | Specialist observation |

No trial with human participants may be run without ethics approval and an orientation and mobility specialist.
