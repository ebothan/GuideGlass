# Lightweight safety case

Version 1, 2026-11-12. Draft origin: `docs/research/02-av-safety-engineering.md`. This version replaces the draft's targets with what was actually built and measured in P4.

This is a research prototype. It has never run on a phone or glasses and has never been used by a person. Nothing below is a claim about real streets. Evidence rungs (L0-L5) are defined in `CLAUDE.md`. "L1" means simulated with synthetic scenes and an idealised detector.

## 1. Top claim

> Within its operating domain, used by a trained person who always also uses a long cane or guide dog, the system gives supplementary audio cues about obstacles in the walking path and approaching objects. It tells the user whenever it cannot do so. It does not tell the user that anything is safe, clear, or time to cross.

**Status of the top claim: not established.** Only the decision logic has evidence, and only at L1. The claim needs L4 and L5 evidence before anyone relies on it.

## 2. Losses and hazards

| ID | Loss or hazard |
|---|---|
| L-1 | Injury to the user (collision, fall, traffic) |
| L-2 | Injury to others |
| L-3 | Loss of trust: the user abandons a useful aid, or over-relies on it |
| L-4 | Privacy harm to bystanders (camera, cloud upload) |
| H-1 | The user approaches a hazard without a timely cue |
| H-2 | The user takes an unsafe action because of a cue |
| H-3 | The user believes the path is clear while the system cannot see |
| H-4 | Audio output masks the environment or distracts |
| H-5 | The user learns to ignore cues because many are false |
| H-6 | The user reduces reliance on the cane or dog |

## 3. Sub-claims, evidence and gaps

### C1. Hazard cues come early enough (H-1)

| Evidence | Rung |
|---|---|
| STOP fires at 2.5 s to contact, shifted by measured frame age (AssessorTest) | L1 |
| Still obstacles (person, pole, bin, low bollard, head-height branch, barrier): STOP reaches the ear at least 1.36 s before contact in 30/30 runs each; a simulated walker who stops on STOP never makes contact (ScenarioTest) | L1 |
| Same at 7, 15, 30 fps and with 40% of detections dropped | L1 |
| Approaching pedestrian: margin at least 1.36 s in 30/30. Cyclist at 4.5 m/s: STOP at least 1.0 s before contact in 30/30 | L1 |
| Repo's shared scene set, like-for-like: 11.33% misses against R5's 47.39% (`docs/results/g6_baseline_comparison.md`) | L1 |
| STOP capture-to-ear p95 613 ms against a 780 ms budget, with hardware delays drawn from assumptions (`docs/results/latency_report.md`) | L1 |

Gaps:
- **No real detector has been run.** Every result assumes a box exists for the obstacle. Detector misses are not in any number here.
- Drop-offs, curbs, stairs and glass are not detected at all (scenarios S-16, S-17).
- The 1.36 s margin is for a typical walker (0.5 s reaction, one step). The conservative walker (1.0 s reaction, two steps) needs 2.36 s, which the default 2.5 s setting does not guarantee.
- The cyclist margin (1.0 s) is below even the typical requirement.
- Bluetooth video and audio delays are assumed. If they are longer than assumed, every margin shrinks by the difference.
- Range comes from an assumed depth error (30% scale error) and an assumed pitch error (2°). Real errors are unknown.

### C2. Cues do not cause unsafe actions (H-2)

| Evidence | Rung |
|---|---|
| The safety path gives no steering instruction; the repo's "veer left/right" hint is removed (ScenarioTest banned-phrase check) | L1 |
| No cue contains "clear", "safe", "walk now" or "cross now" (ScenarioTest, NavigatorTest, CloudGuardTest) | L1 |
| Crossings are announced only as "Crossing ahead" | L1 |
| Cloud answers pass a guard: 21 tests including injected sign text, stale answers, and "path is clear" | L1 |

Gaps:
- "Stop" on stairs or mid-crossing may itself be dangerous (research note 02, unsafe control action 8). There is no stairs or crossing context detection, so this is unmitigated.
- The guard's banned-phrase and imperative checks are word lists. A paraphrase can evade them. The schema limits this but does not remove it.
- Turn cues fire 1.1-7.7 s early because of GPS noise. An early "turn now" could turn a user into a wall or a road before the corner. The wording is hedged ("soon") only when the reported accuracy radius exceeds the distance.

### C3. Silence never means clear (H-3)

| Evidence | Rung |
|---|---|
| Camera stopped, frozen, or perception stalled: "Camera lost. Use your cane." starts within 0.6 s of the last fresh frame when nothing of equal priority is playing; p95 877 ms to the ear (HealthTest, latency report) | L1 |
| No hazard cue is given from stale data while the camera is lost | L1 |
| Bluetooth loss: "Glasses disconnected" on the phone speaker within 1.0 s of the link signal | L1 |
| Low frame rate, latency spike, low light, audio reroute: each announced once, recovery announced | L1 |

Gaps:
- If the app is killed, nothing is announced. Start and stop chimes are the only bracket.
- If another equal-priority cue is playing, "Camera lost" waits for it: up to 1.6 s measured.
- Detecting the Bluetooth link state and the audio route depends on operating-system callbacks that have not been exercised.
- A camera that delivers fresh but useless frames (lens covered, fogged, pointing at the sky) looks healthy. Only low brightness is checked.
- The optional "alive" tick is not implemented.

### C4. False alarms stay low enough to keep trust (H-5)

| Evidence | Rung |
|---|---|
| Shared scene set, like-for-like: 1.30% false alarms against R5's 2.00% | L1 |
| One alert sequence per approach; no repeat while standing; no second STOP within 1.5 s (ScenarioTest S-07, S-08, S-19) | L1 |
| Residual false STOPs measured: parked car beside the path 1/30, cyclist crossing ahead 1/30, pedestrian ahead at same pace 2/30, fast head turns 1/30 | L1 |

Gaps:
- The deployed setting gives 15.9% alerts on the harness's "hazard-free" scenes. About a quarter of those are objects in the path 2.5-3.6 m away, which are early warnings rather than errors; the rest are real false alarms caused by range error.
- No false alarms per walking hour figure exists. Per-scene rates cannot be converted to it without real footage.
- Users respond to alarms about as often as the alarms are true (research note 03). No target for the share of true STOPs has been validated.

### C5. Audio does not mask the environment (H-4)

Evidence: non-urgent speech is limited to once per 4 s; one cue at a time (ArbiterTest). L1.

Gaps: nothing is known about loudness, masking of traffic by open-ear speakers, or cue load in real crowds. Needs L4 and L5.

### C6. The user keeps using the cane or dog (H-6)

Evidence: none. This is a training and study-protocol matter. The "Camera lost" wording says "Use your cane". Needs L5 with an orientation and mobility specialist.

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
