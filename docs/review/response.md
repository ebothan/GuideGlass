# Response to the adversarial review

Date: 2026-11-12. Review: `docs/review/adversarial-review.md`, written by an independent agent with no access to the author's reasoning, against commit 30db65c.

The review was right on every Critical and High finding. This file says, per finding, what was changed, what was only reworded, and what remains open. All rates are L1 (simulation). Rates quoted here are over seeds 1001-1300 from `docs/results/scenario_rates.md`, or over seeds 31-130 in `ReviewRegressionTest`. Neither seed range was used while tuning.

Status values:
- **Fixed**: code changed and a test added.
- **Partly**: improved, with a stated residual.
- **Reworded**: the claim was wrong and the documents now say so.
- **Open**: not addressed.

## Critical

| # | Finding | Status | What was done | Residual |
|---|---|---|---|---|
| 1 | No second warning after stop, stand, walk on | Fixed | Standing for 1 s and then walking re-arms every object (`HazardAssessor.decide`). Runs with no second STOP: 0/300; before, 97/100. | None seen in simulation. |
| 2 | STOP for people, bikes and cars effectively off in degraded states and for late first sight | Partly | Movable classes now use the still-object assumption whenever motion cannot be measured. The only hold-back is a 0.7 s window after first sight, and only while the time to contact is not urgent. Contact for a walker who stops on STOP: 0/300 at 5 fps, at 7 fps, with +0.4 s latency and in low light. | See below. |
| 3 | Cloud guard said "WALK" for "DON'T WALK" and spoke safety-negating paraphrases | Partly | Crossing questions are always refused ("I can't help with crossing."). "Forward" is never spoken. Free text is spoken only if it avoids a list of movement, traffic and path-state words; otherwise a fixed template or nothing. Sign text on those topics is not read out. Non-ASCII letters and zero-width characters drop the item. All 24 of the reviewer's paraphrases are now silent. | See below. |
| 4 | Comparison with R5 not like-for-like | Reworded, and measured | New "pitch unknown" rows in `docs/results/g6_baseline_comparison.md`. "Like-for-like" is removed everywhere. | See below. |

Residuals for the Critical findings:

- **Finding 2.**
  - 10 fps with 20% frame loss: contact in 3/300.
  - A person first visible 3.0 m ahead: contact in 59/300 (19.7%); before, 98%. At 1.4 m/s that leaves 2.1 s in total, about what the pipeline plus a typical reaction and one step need. No decision logic fixes this.
  - **Cost of the fix:** more false STOPs for movable classes. See finding 6.
- **Finding 3.** The filter is still a word list. A paraphrase using none of the listed words would be spoken. The list is broad enough that many harmless descriptions are also dropped. The durable fix is to speak only templates built from structured fields; that would remove free descriptions, which are the reason for having a cloud tier. Not done.
- **Finding 4.** Measured result with the pitch not given to the method:

  | Setting | False alarms | Misses | Pitch flip |
  |---|---|---|---|
  | R5 (repo baseline) | 2.00% | 47.39% | 0.0% |
  | Matched threshold, pitch given (±2°) | 1.30% | 11.33% | 0.79% |
  | Matched threshold, pitch unknown | 2.29% | 19.60% | 2.95% |
  | Deployed setting, pitch given | 15.89% | 0.94% | 1.69% |
  | Deployed setting, pitch unknown | 27.04% | 2.41% | 14.36% |

  - Without a pitch estimate, the method has more false alarms than R5 at the matched threshold.
  - The deployed setting has 8-13 times R5's false alarms.
  - Every row still receives a depth value derived from the true distance.
  - REQ-02's 5% bound is not met by the deployed setting.

## High

| # | Finding | Status | What was done | Residual |
|---|---|---|---|---|
| 5 | "KEEP CLEAR" sign threw an exception through the pipeline | Fixed | `CloudGuard.onAnswer` catches everything and cannot throw; the assertion on output text is removed; such sign text is not read out. Tested with six signs and seven junk inputs. | None known. |
| 6 | False-STOP rates quoted from the best 30-seed window | Fixed (reporting); rates are worse | All rates are now over 300 untuned seeds with intervals. Test gates use 100 untuned seeds with bounds at the upper 95% limit. Motion logic is limited to classes that can move, which removed the STOPs for a wall at 8-11 m. | See below. |
| 7 | REQ-07 (pitch) met by construction | Reworded, and measured | REQ-07 is **not met**: pitch flip is 2.95% (matched) and 14.36% (deployed) when the shift is not told to the method, against a 2% requirement. No pitch estimator exists. | Gait pitch oscillation is not simulated. |
| 8 | STOP dropped by the 1.5 s rule never reissued | Fixed | The arbiter's outcome is fed back; a hazard cue that was dropped, cut off or went stale is requested again on the next frame if still due. Two bollards 1.2 m apart with a stop between them: second STOP in 100/100. | A user who does not stop between two objects closer than about 1.5 m gets one STOP for both: they are treated as one object. |
| 9 | Health monitor trusted the frame's timestamp | Fixed | Health uses only the phone's arrival clock. Frames older than 1.0 s or stamped in the future are not used and do not count as a working camera. A frame stamped 60 s ahead no longer hides camera loss. | Clock offset between glasses and phone is still unknown; a capture stamp that runs ahead by less than 1 s makes cues late by that offset. Needs hardware. |
| 10 | "Turn right now" 4-10 s early, unhedged | Partly | No navigation cue says "now". The cue is "Turn right at the next corner." | Timing is unchanged: still several seconds early. Crossings are recognised only from the word "cross" in the instruction text, on a synthetic route. A real route may steer a turn across a road with no "Crossing ahead". |
| 11 | 2.5 s lead too short for slow and conservative walkers | Partly | The STOP lead now follows the document's own formula with measured walking speed, never below 2.5 s. A typical walker at 0.6 m/s: contact 0/300. A user configured as conservative: contact 0-1/300 for ground obstacles, 8/300 for the head-height branch. | See below. |

Residuals for the High findings:

- **Finding 6.** False-STOP rates on scenes where none should be given:

  | Scene | Runs with a false STOP | Runs with any CAUTION |
  |---|---|---|
  | Parked car beside the path | 9.7% | 13.7% |
  | Wall never closer than 7.4 m | 0.7% | 77.7% |
  | Cyclist crossing well ahead | 2.0% | 67.3% |
  | Pedestrian ahead at the same pace | 13.0% | 100% |
  | Fast head turns past roadside objects | 12.0% | 7.7% |

  These are high. Following another pedestrian gives a false STOP in about one run in eight. This is the price of fixing finding 2.

- **Finding 11.**
  - With the default setting, a conservative walker (1.0 s reaction, two steps) still makes contact in 9-12% of still-obstacle runs, and in 33% for the head-height branch.
  - The conservative setting costs false STOPs: 87% of runs when following a pedestrian.
  - The setting has to be chosen per user, and nobody has validated either human model.

## Medium

| # | Finding | Status | What was done or why not |
|---|---|---|---|
| 12 | Perception stand-in supplies ground truth | Open | The stand-in is given: true head-to-travel angle; camera height 1.30 m; walking speed; the head-height flag; stable class labels; a perfectly calibrated depth sigma; no phantom detections. The safety case now lists them as unbuilt estimators. A detector returning no boxes still gives silence with state NORMAL. |
| 13 | Latency report measures its own inputs | Reworded | The report says at its top that it cannot fail on hardware stages. It now also lists dropped and cut-off hazard cues. G3 is set to `needs_hardware`. |
| 14 | Guide dog or companion causes repeated STOPs | Partly | The close-range STOP does not fire for an object measured to keep its distance. Repeated STOPs: 0.03 per minute after the first 5 s. One STOP at first sight remains (1.0 per run). Added to the hazard list. |
| 15 | CAUTION load unmeasured | Partly | Now measured (see finding 6 table). It is high: a CAUTION in 78% of runs for a wall never closer than 7.4 m. No bound set; no fix. |
| 16 | Camera-loss timing and flapping | Partly | "Camera lost" now cuts off everything except a playing STOP: worst wait in a crowd is within 1.25 s of the last frame (tested). Frames more than 1 s old count as camera lost. Flapping on a 2.8 s on / 0.6 s off stream is not fixed. |
| 17 | Tests that do not test their requirement | Partly | Fixed: T4 has one expected output; S-11 collision variant and REQ-18 are covered by the new guard tests on arbitrary text. Not fixed: the F-04 test accepts either degraded cue; the 30 s repeat is untested; REQ-05 wording (alerts stop after 0.3 s unseen). G2a wording corrected: replay shows determinism only. |
| 18 | Missing hazards | Reworded | Added to the safety case as unmitigated: objects from outside the 46° field of view; clock positions relative to the camera when head direction is unknown; false STOP on a crossing or stairs; thermal throttling; phone speaker inaudible in a pocket; no evidence for injury to others or bystander privacy. |

## Low

| # | Finding | Status |
|---|---|---|
| 19 | Wording stronger than the evidence | Reworded in the safety case, requirement status, `acceptance.json` and PROGRESS. The top claim no longer says the system tells the user "whenever" it cannot see. |
| 20 | Out-of-order frames; alert inheritance for a second person; stale frame-rate window | Partly. The frame-rate window is now pruned on every tick. Out-of-order frames and inheritance are open and listed for the Android adapter. |

## Tests changed in this round, and why

- `ScenarioTest`: the 30-seed false-STOP gates were replaced by 100-seed rate bounds (finding 6). The bounds are much looser than before because the true rates are much higher than the first window suggested.
- `CloudGuardTest` T4 and T7: expected outputs changed because the guard's rules changed (finding 3).
- The simulated walker now includes the 0.15 s recognition time (finding 11).
- New: `ReviewRegressionTest`, 13 tests reproducing the reviewer's cases.

Total: 85 tests, all passing.

## What the review could not break (unchanged)

All documented numbers regenerate. Still obstacles at nominal inputs give a timely STOP for the typical walker. The repetition cases S-07 and S-08 hold (299/300 each after the fixes). Camera loss is detected from the first frame. Link flapping does not spam.
