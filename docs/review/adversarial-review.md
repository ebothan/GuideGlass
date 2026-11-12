# Adversarial review of the av-prototype safety case

Reviewer: independent, no access to the author's reasoning. Date: 2026-11-12. Commit reviewed: 30db65c.
Method: read the documents, code and tests listed in the brief; ran `./init.sh` (passes, 74 tests); wrote throwaway
JUnit probes against `core/` (deleted afterwards; `git status --short` is clean apart from this file).
"CONFIRMED" means I ran something and saw it. "INFERRED" means I only read the code.
Paths below are relative to `core/src/main/kotlin/org/guideglass/core/` unless they start with `docs/`, `core/` or `paper/`.

## Verdict

The numbers in the documents reproduce, and the documents are more candid than most about what was not measured.
The safety case still does not hold at L1. The decision core has at least three defects that produce silence or a
wrong statement exactly where the top claim says it will not: no second STOP after a user stops and walks on, no
timely STOP for people, bikes or cars in the announced-but-still-running "Reduced detection" and "Low light" states,
and a cloud guard that says "The signal appears to show WALK" for a "DON'T WALK" sign and speaks "No cars are coming"
verbatim. The headline comparison with R5 (1.30% / 11.33% against 2.00% / 47.39%) is not like-for-like: the new method
is given a depth value computed from the true distance and the true camera pitch, which R5 never sees; without the
pitch oracle it loses to R5 on false alarms. Several "measured" false-STOP rates are the best 30-seed window, and
several requirements marked "met" are met by construction of the test. Gates G5, G6 and G7 should not read "pass".

## Findings

### Critical

**1. After a STOP, a user who stops and then walks on gets no second warning. CONFIRMED.**
- Observed: bollard at 6 m, walk 3.2 s (STOP is heard), stand 5 s, walk on. 100 seeds (31-130): contact in 100/100;
  in 97/100 no STOP or CAUTION at all after resuming. With a 2 s stand: 56/100 silent. With 15 s: 97/100.
- Cause: `HazardAssessor.kt` lines 164-167. Re-arming sits in the `else` branch of `if (a.level != NONE)`. While
  standing, the level is NONE but `walkSpeed > standingSpeedMS` is false, so no re-arm. On the first walking frame
  the level is STOP again, so the re-arm branch is never reached. ADR 0006 item 4 says walking re-arms; it cannot.
  S-07 passes only because the walker backs off more than 1 m. No test covers stop, wait, continue.
- Why it matters: stop, probe with the cane, find nothing in reach (the obstacle is 1.5-3 m away), continue. That is
  the normal response to a STOP. The system then stays silent into the obstacle, and silence is read as "clear".
- Fix: re-arm on the standing-to-walking transition when the object is still in the alert zone (new STOP if time to
  contact is below the threshold when walking resumes). Add the scenario as a 100-seed test.

**2. In "Reduced detection", "Low light", at low frame rate and for newly seen objects, STOP for people, bikes and cars comes only at 1.2 m. CONFIRMED.**
- Observed (100 seeds, person standing in path, typical simulated walker):

  | Condition | STOP margin below 1.36 s | Contact (walker who stops on STOP) |
  |---|---|---|
  | 15 fps, nominal | 0/100 | 0/100 |
  | +0.4 s transport latency (state REDUCED) | 100/100 (median margin -0.25 s) | 100/100 |
  | Same, but a bin instead of a person | 0/100 | 0/100 |
  | Low light (state OUT_OF_ODD) | 82/100 | 82/100 |
  | 5 fps | 100/100 | 100/100 |
  | 7 fps (inside the ODD) | 2/100 | 2/100 |
  | 10 fps with 20% frame loss | 6/100 | 2/100 |
  | Person first visible at 3.0 m (doorway) | 100/100 | 98/100 (a pole in the same case: 0/100) |

- Cause: `HazardAssessor.kt` lines 75-76 and 116-117. For classes in `MOVABLE`, STOP from the still-object channel
  needs `enough`: the motion channel allowed (`HealthMonitor.kt` line 38: state NORMAL and head steady), 8 samples in
  1 s, 0.5 s span, no fast head turn in the window. Otherwise only the 1.2 m floor fires, about 0.86 s before contact,
  less the 0.7 s pipeline.
- Tests shaped around it: every frame-rate, dropout and latency test uses a `bin` (`Scenarios.s03At`, `s03Bin`),
  which is not a movable class. REQ-05 is "met" on that basis.
- Why it matters: a standing person is the most common in-path obstacle. In the degraded states the user still hears
  CAUTION cues, so the system sounds alive, while its STOP for people is effectively off. "Reduced detection." does
  not say that.
- Fix: when motion cannot be measured, fall back to the still-object assumption for movable classes (accept the S-12
  false STOPs in degraded states), or stop issuing hazard cues and say "Hazard alerts off. Use your cane."
  Test S-01, a parked car and a parked bike at 5/7/10 fps, with dropout, with latency, and in each degraded state.

**3. The cloud guard states "WALK" for a "DON'T WALK" sign and speaks safety-negating paraphrases verbatim. CONFIRMED.**
- `cloud/CloudGuard.kt` line 133: `walkShown` is `sceneText.contains("walk")`. With a traffic light in the device
  objects, a CAN_I_CROSS answer whose scene text is "DON'T WALK", "DONT WALK", "NO WALKING", "SIDEWALK CLOSED" or
  "Walk-ins welcome" all produce: "The signal appears to show WALK. Check traffic before crossing."
- Line 208 (`R3`) and line 211 (`IMPERATIVE`, anchored at the start of the text). With `obstacleInPath = true`, all
  of these were spoken unchanged as `description` items, and with "The camera suggests ..." as `advisory` items:
  "Nothing is blocking you", "It's fine to go", "The way ahead is open", "You may proceed", "No cars are coming",
  "The road is empty", "There is no traffic", "The light is green for you", "It is OK to cross", "The cars are waiting
  for you to cross", "The sidewalk is free of obstacles", "Obstacle free ahead", "All good ahead, keep walking",
  "The door is open, walk straight through it", "A bench is on the left, so just step into the road to pass it",
  "The crossing is empty; cross quickly", "You should turn left into the road here", "Please walk forward".
  Of 24 paraphrases tried, 1 was blocked ("Traffic has stopped for you"). A zero-width space inside "clear" and a
  Cyrillic "а" also pass.
- Line 157: with 2.5 m of device-reported free space the guard itself says "The camera suggests continuing ahead."
  The device cannot see drop-offs, glass or stairs, so this is a clearance statement by another name.
- Why it matters: the top claim says the system "does not tell the user that anything is safe, clear, or time to
  cross". The implemented guard does. REQ-18 and REQ-25 are marked "met"; G7 is "pass".
- Fix: do not speak free text from the model. Map answers to a closed set of templates chosen by structured fields;
  free text only as a quoted sign reading. Remove the WALK branch and the "continuing ahead" branch; for CAN_I_CROSS
  always say "I can't help with crossing." Until then mark REQ-18, REQ-25 and G7 "not met".

**4. The comparison with R5 is not like-for-like; the gain comes mostly from oracle inputs. CONFIRMED, quantified.**
- What the new method gets that R5 does not (`sim/BaselineEval.kt` lines 100-104, `paper/simulation/export_scenes.py`):
  a depth sample equal to true distance times the scale error (with a correctly stated 30% sigma), and the true camera
  pitch plus a 2° error, in a scene set whose true pitch is uniform over -10° to 30°. R5 sees only the box.
  The "matched" setting (line 154) is also the label definition itself: threshold 2.5 m, corridor 0.45 m.
- Re-scored on the same seed-53 scenes:

  | Setting | False alarm % | Miss % |
  |---|---|---|
  | R5 | 2.00 | 47.39 |
  | matched, as published | 1.30 | 11.33 |
  | A three-line rule: last frame, true d x scale error <= 2.5 m and lateral <= 0.45 m | 2.48 | 19.85 |
  | matched, ground only, as published (true pitch + 2°) | 0.70 | 44.64 |
  | matched, ground only, pitch not known (fixed 10°) | 2.81 | 61.69 |
  | matched, fused, pitch not known (fixed 10°, stated sigma 2°) | 3.17 | 32.20 |
  | matched, fused, pitch not known (stated sigma 11.5°) | 3.14 | 19.80 |
  | deployed, fused, pitch not known | 20.43 | 7.35 |

  Without the pitch oracle the method no longer beats R5 on false alarms. Without the depth oracle it does not beat
  R5 on misses. A trivial threshold on the oracle depth already cuts misses from 47% to 20%.
- The setting that would ship ("deployed") has 15.89% false alarms, eight times R5 and three times REQ-02's own 5%
  bound. `init.sh` and gate G6 check only "matched".
- The result is stable across scene seeds (seed 99: 1.27% / 11.38%), so this is a fairness problem, not noise.
- Honest wording: "Given a depth estimate with 30% scale error and a pitch estimate with 2° error, neither of which
  R5 uses and neither of which has been shown achievable on the glasses, the range-based rule misses 11% against
  R5's 47% at a matched threshold. The setting we would deploy has 16% false alarms against R5's 2%." Drop
  "like-for-like". Set G6 to "partly" and REQ-02 to "not met as written (5% bound)".

### High

**5. A sign reading "KEEP CLEAR" throws an uncaught exception through the safety pipeline. CONFIRMED.**
- `cloud/CloudGuard.kt` line 141: `check(!BANNED.containsMatchIn(text))`. Scene text is not filtered before it is
  quoted (lines 112-113). READ_TEXT with "KEEP CLEAR", "FIRE EXIT KEEP CLEAR", "SAFE DEPOSIT" or "Clear Lake Cafe"
  gives `IllegalStateException`, which propagates out of `SafetyPipeline.onCloudAnswer` (line 86-90).
- Why it matters: the core is single-threaded. On a phone an uncaught exception there kills the hazard path, and the
  safety case admits a killed app announces nothing. A common street sign becomes a way to silence the aid. It also
  contradicts REQ-24 (cloud cannot affect the safety path).
- Fix: catch everything at the guard boundary and return "nothing to say"; filter or drop quoted sign text instead of
  asserting; add a test that no cloud input can throw.

**6. The "measured" false-STOP rates are the best 30-seed window; the gates fail on other seeds. CONFIRMED.**
- `core/src/test/kotlin/org/guideglass/core/ScenarioTest.kt` line 21 fixes seeds 1-30; lines 99-110 set gates "at the
  rate measured when the test was written". False-STOP runs per 30-seed window, seeds 1-300:

  | Scenario | Claimed | Per window | Rate over 300 | Windows failing the gate |
  |---|---|---|---|---|
  | S-06 distant wall | 0 | 0,2,1,2,1,0,2,1,2,1 | 4.0% | 8/10 |
  | S-12 pedestrian ahead, same pace | 2/30 | 2,3,2,5,4,3,3,2,2,4 | 10.0% | 6/10 |
  | S-13 fast head turns | 1/30 | 1,3,1,1,2,5,1,3,6,3 | 8.7% | 6/10 |
  | S-05 parked car beside path | 1/30 | 1,0,0,2,1,2,2,1,0,1 | 3.3% | 3/10 |
  | S-11 cyclist crossing ahead | 1/30 | 1,2,1,0,3,2,0,0,1,0 | 3.3% | 1/10 |

- S-06 false STOPs fire for a wall 7.8 to 11.0 m away (seeds 36, 56, 71, 105, 118).
- Why it matters: `docs/safety-case.md` C4 quotes the first-window numbers as "measured". A STOP for a wall 10 m
  away, one in ten following a pedestrian, is what teaches a user to ignore STOP.
- Fix: report rates over at least 300 seeds with intervals; gate on a seed range not used for tuning; correct C4,
  ADR 0006 and `requirements_status.md` (REQ-08).

**7. REQ-07 (pitch) is met only because the estimator is handed the shifted pitch. CONFIRMED.**
- `sim/BaselineEval.kt` line 104 uses `r.pitch`, and the "lo"/"hi" rows carry pitch -/+ 8°. `sim/World.kt` line 163
  passes `sc.pitchRad + pitchError`. The pitch test in `ScenarioTest` gives identical margins to two decimals at
  -5°, 0°, 25° and 30° (min 2.16 s, median 2.96 s): pitch has no effect by construction.
- If the ±8° shift is not told to the estimator: flip rate 3.69% (matched) and 24.45% (deployed), against the
  published 0.79% and 1.69% and the requirement of 2%.
- Why it matters: a blind user's head pitch moves with every step and every sweep. No pitch source (horizon, IMU) has
  been built or tested; the glasses IMU is "beta-only" per the author's own notes.
- Fix: mark REQ-07 "not met; depends on an unbuilt pitch estimator". Add gait pitch oscillation and a lagging,
  biased pitch estimate to the simulator.

**8. A STOP dropped by the 1.5 s refractory rule is never reissued; the second of two close obstacles is silent. CONFIRMED.**
- Two bollards 1.2 m apart on either side of the centre line: in 100/100 runs the second bollard never gets a STOP of
  its own (92 STOPs `DROPPED_RATE`). In S-19, 272 of 438 STOP requests are dropped.
- Cause: `HazardAssessor.kt` lines 168-171 sets the track's level to STOP when the cue is requested, not when it is
  played. `CueArbiter.kt` lines 45-47 drops it. The same applies to CAUTION dropped as stale (76 of 560 in the
  latency suite).
- Why it matters: the user steps round the first obstacle and walks into the second with no cue.
- Fix: the arbiter reports the fate back; a dropped hazard cue leaves the track un-alerted and is retried after the
  refractory time if still due.

**9. The health monitor trusts a timestamp inside the frame; a wrong stamp hides camera loss. CONFIRMED.**
- `HealthMonitor.kt` lines 52-58 use `f.stamps.perceptionDone`, not the arrival time. One frame stamped 60 s ahead,
  then no frames: "Camera lost" at 62.5 s instead of 2.5 s. Stamps on a clock 5 s ahead: loss at 3.0 s announced at
  8.5 s.
- INFERRED, related: `HazardAssessor.kt` line 61 computes `now - captureT` across the glasses and phone clocks.
  `docs/PROGRESS.md` says the meaning of the DAT frame timestamp is unverified. A capture stamp that runs ahead makes
  every STOP late by the offset; REQ-04 depends on clock alignment that is not in the hazard analysis.
- Fix: health uses the phone's own monotonic arrival time only; clamp frame age to [0, max] and treat an out-of-range
  age as a fault that is announced.

**10. "Turn right now." is spoken 4-10 s before the corner, unhedged; the gate is marked pass. CONFIRMED.**
- Seeds 21-120: true time to the turn when "turn now" fired: min 3.9 s, median 6.9 s, max 10.3 s (13 m before the
  corner); intended 2.9 s. Only 12/200 were hedged to "soon". With 8 m GPS error: up to 17.3 s early.
- `core/src/test/kotlin/org/guideglass/core/NavigatorTest.kt` lines 35-52 assert only that the cue is before the
  corner; the timing error is printed, not checked.
- INFERRED: crossings are recognised only when the instruction text contains "cross" (`nav/Navigator.kt` line 205).
  The synthetic route was written with "Cross Second St at the crosswalk". Real walking directions usually express a
  crossing as a turn or a street change, so "Crossing ahead" would rarely fire and "Turn left now" could point
  across a road.
- Why it matters: an early "turn now" turns a user into a wall, a driveway or the roadway. The safety case admits
  it; `acceptance.json` G5 says "pass" and REQ-23 says "met".
- Fix: never say "now" from GNSS alone; say "Turn right at the next corner" and let the cane find it. Set G5 and
  REQ-20 to "not met", REQ-23 to "met on a synthetic route only".

**11. The default 2.5 s lead is too short inside the stated operating domain. CONFIRMED.**
- Bin, walker at 0.6 m/s (ODD lower bound), typical reaction: contact in 4/100. By the document's own formula
  (`docs/00-problem-constraints-goals.md` section 8.2) a 0.6 m/s walker needs 3.02 s.
- Conservative walker (1.0 s reaction, 1.4 m to stop) at 1.4 m/s: contact in 3-5/100 for pole, bin, low bollard,
  branch and barrier. The safety case discloses this gap in words but gives no number.
- The closed-loop walker also omits the 0.15 s recognition time the document's own model includes.
- Fix: default to the conservative lead (3.1 s) and state the false-alarm cost, or scale lead time with speed.

### Medium

**12. The perception stand-in supplies ground truth the phone will not have. CONFIRMED for the items with numbers.**
- Head-to-travel angle is passed from truth (`sim/Runner.kt` line 170). If it is unknown and the head is turned 20°,
  29/100 STOPs for a bin are later than the typical margin (median margin 1.62 s against 2.76 s).
- Camera height is assumed 1.30 m everywhere (`sim/World.kt` line 120); no scenario varies it. That is a chest-held
  phone. At glasses height: parked-car false STOPs rise from 2/100 to 11/100 (1.65 m) and 13/100 (1.75 m).
- Walking speed is truth plus 0.05 m/s (`sim/Runner.kt` line 179). Reported 0.7 while walking 1.4: 20/100 late STOPs.
- The head-height flag comes from truth (`o.bottomM > 1.0`). Objects with a lowest edge at 0.6-0.9 m (tailgate,
  barrier bar, table) are ranged from a "foot" that is not on the ground: 7-9/100 below the typical margin.
- INFERRED: class labels never flicker (a flip starts a new track and resets motion evidence); the depth sigma is
  perfectly calibrated; there are no phantom detections, so every false-alarm figure excludes detector false
  positives; a detector that returns no boxes gives total silence with state NORMAL (CONFIRMED: bin approach with
  `pDetect = 0`, only "Hazard alerts on." is heard, contact at 6.3 s).
- Fix: list each oracle input in the safety case as an unbuilt estimator with an assumed error, and sweep each.

**13. The latency "report" measures its own inputs. CONFIRMED by reading and re-running.**
- `docs/results/latency_report.md` regenerates identically except the JVM timing row. STOP p95 613 ms is the sum of
  draws from distributions fitted to the budget, compared with a budget that is the plain sum of their p95s. It cannot
  fail. The only measured figure is 0.02 ms of desktop compute.
- Survivorship: the per-cue table covers played cues only. In the same suite 76 CAUTION and 2 MOVING_OBJECT cues were
  dropped as stale and 90 STOPs dropped by the refractory rule; none appear.
- Fix: G3 status "not measurable here"; safety case C1 should drop the 613 ms line or label it "arithmetic check".

**14. A user's own guide dog or companion produces repeated false STOPs. CONFIRMED. Missing from the hazard analysis.**
- Object 1.0 m ahead moving with the user for 60 s: 1.1 STOPs per minute (dog, perfect detection), 1.6 (dog, 80%
  detection, max 5), 1.2 (person, a sighted guide). The top claim requires a cane or dog, so this is the normal case.
- Fix: add the hazard; suppress tracks that hold constant range while the user walks.

**15. CAUTION load is unmeasured and looks high. CONFIRMED.**
- CAUTION or "approaching" cues fire in 90/100 runs for a wall never closer than 7.4 m (S-06), 74/100 for a cyclist
  crossing well ahead, 100/100 for a pedestrian walking ahead at the same pace. C4 counts only STOPs.
- Fix: report all cues per minute on a busy-sidewalk scenario; add a CAUTION nuisance bound.

**16. Camera-loss timing and flapping. CONFIRMED.**
- Stream stops during a crowd (600 runs): "Camera lost" started up to 2.49 s after the stream stopped. The safety case
  says "up to 1.6 s measured" on top of 0.6 s; `HealthTest` line 133 sets the bound from that measurement.
- Camera on 2.8 s / off 0.6 s: "Camera lost" and "Camera back" alternate every few seconds and hazard cues are
  allowed 9% of the time. "Camera back." was played 0.2 s after the stream had stopped again.
- Every frame 3 s late: "Reduced detection." and "Stop." arrive together 0.02 s after contact.
- Fix: cap frame age (above about 1 s is "camera lost"); add hysteresis on "Camera back"; give CAMERA_LOST its own
  priority above CAUTION.

**17. Tests that do not test the requirement they are cited for. CONFIRMED by reading.**
- REQ-18: cited as a vocabulary test "over all cue templates and guard outputs". It scans cues that happened to play
  in 7 scenarios x 5 seeds. Finding 3 shows what it misses.
- REQ-12 / F-04: passes if either REDUCED or CAMERA_LOST appears.
- REQ-10: the 30 s repeat is not tested (it does work: 4.8, 34.8, 64.9 s in my run).
- REQ-05: "survives 0.6 s gaps" is "met", but alerts stop after 0.3 s unseen (ADR 0006 item 7).
- S-11 "cue only if predicted to enter the path": only the no-collision variant is tested;
  `Scenarios.s11BikeCrossingCollision` is never used (it does give a STOP in 100/100, but never produces contact, so
  it tests nothing as written).
- T4 accepts either silence or "I can't tell reliably."
- Gates loosened after the fact, as the author records: S-05 from 0 to at most 1; S-10 no longer needs an early cue.
  `CLAUDE.md` says "Do not edit or delete a test to make it pass".
- G2a "replayed logs": the logs are the simulator's own event stream replayed through the same code. That shows
  determinism, nothing else.

**18. Hazards missing or waved away. INFERRED.**
- Anything approaching from outside the 46° horizontal field of view: a car leaving a driveway, a cyclist from the
  side, a door opening.
- Clock position is computed in the travel frame; if head direction is unknown it is relative to the camera, and the
  user hears "2 o'clock" for something at body 12 o'clock.
- A false STOP (finding 6) while the user is on a crossing or stairs. C2 lists STOP-on-crossing as unmitigated but
  does not connect it to the false-STOP rate.
- Thermal or battery throttling pushes the phone into the degraded states of finding 2.
- L-2 (injury to others) and L-4 (privacy) have no sub-claim and no evidence line.
- The phone-speaker fallback is inaudible in a pocket in traffic; no vibration-only path is analysed.

### Low

**19. Wording that reads stronger than the evidence.**
- "like-for-like" (finding 4). "pass" for G3, G5, G6, G7. "met (logic)". "L3-substitute" for a desktop simulation.
- Top claim: "It tells the user whenever it cannot do so." Not true for a dead detector, a covered lens, a wrong
  timestamp, or people in degraded states.
- "Residual false STOPs measured: 1/30 ..." should be the 300-seed rates.
- S-09 "margin at least 1.36 s in 30/30": on seeds 31-130 one run has 1.01 s.
- "worst error sweep 2.86%" in `acceptance.json`, `requirements_status.md` and `PROGRESS.md`; the table says 2.87
  (459/16000). `init.sh` prints 11.32% where the report says 11.33% (906/8000 rounded two ways).

**20. Smaller code issues. INFERRED.**
- `sim/Runner.kt` delivers frames by perception-done time, so capture times can go backwards; `Track.lastSeen` then
  moves backwards and the sample deque is unordered. A real adapter must drop out-of-order frames.
- `HazardAssessor.kt` lines 157-160: a new track within 1.5 m and 0.8 m of a recently lost alerted track inherits its
  alert level. A second person stepping into the place of one who left gets no cue for up to 1.2 s.
- The health monitor's frame-rate window is only pruned in `onFrame`, so the loss timeout after a slow stream is
  based on stale arrivals.

## Claims I tried to break and could not

- All documented numbers regenerate: R5 2.00 / 47.39; the full G6 table (byte-identical); 74 tests; the latency
  report (identical apart from JVM timing); the demo cue log (identical).
- G6 is not a lucky scene seed: seed 99 gives 1.27% / 11.38% for "matched".
- Still obstacles at 15 fps with nominal inputs: STOP margin at least 1.36 s in 100/100 on seeds 31-130 for person,
  pole, bin, low bollard, branch and barrier (minimum 1.74 s); no contact for the typical walker, also at 7 fps and
  with 40% detection dropout for a bin.
- S-07 gives exactly two STOPs and S-08 exactly one on seeds 31-130.
- S-10 cyclist: STOP at least 1.0 s before contact in 100/100 (minimum 1.21 s).
- Camera loss: detected when the stream is frozen from the very first frame (0.53 s) and when no frame ever arrives
  (0.6 s). It repeats every 30 s. No hazard cue is issued from stale tracks during loss.
- Link flapping every 0.4 s for 20 s: one "Glasses disconnected", one "Glasses back", no spam.
- Walking speed never reported, or reported as zero while walking: STOP still on time (the motion channel covers it
  in state NORMAL).
- Depth scale error of 0.5 with a stated 0.3, and a 5° pitch error, on the bin approach: no late STOP.
- Turn cues never fired at or after the corner in 300 runs, including with no Doppler speed.
- Cloud staleness, unknown frame id, malformed JSON, three-strikes disable and "clear"/"safe" literal blocking work
  as tested.
