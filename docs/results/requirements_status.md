# Requirement status after P5 (post-review)

Requirements are defined in `docs/00-problem-constraints-goals.md` section 9. Rungs are as in `CLAUDE.md`. Status values:
- **met**: met at the stated rung.
- **partly**: met with a stated exception.
- **not met**: fails as written.
- **needs hardware**: cannot be checked here.

Test names refer to `core/src/test/kotlin/org/guideglass/core/`: 85 tests, all passing on 2026-11-12. Rates are from `scenario_rates.md` (300 untuned seeds). This table was revised after the adversarial review; several rows were downgraded.

| REQ | Short form | Status | Evidence | Rung |
|---|---|---|---|---|
| 01 | Safety path needs no network | met | HealthTest "network loss": hazard cue times identical with and without network | L1 |
| 02 | Fewer misses than R5, false alarms bounded | not met as written | Deployed setting: 15.9% false alarms (27.0% with pitch unknown) against the 5% bound; misses 0.9-2.4% against R5's 47.4%. The comparison is not like-for-like: the method is given depth derived from true distance (`g6_baseline_comparison.md`, `docs/review/response.md` finding 4). | L1 |
| 03 | STOP at 2.5 s, CAUTION at 4.0 s to contact | met | AssessorTest: STOP within one frame step of 3.5 m at 1.4 m/s | L1 |
| 04 | Predict to the ear using measured stage times | met | AssessorTest: a frame 0.5 s late fires 0.7 m earlier | L1 |
| 05 | Track life in seconds; survives 0.6 s gaps | partly | Identity survives gaps (TrackerTest, 7/15/30 fps). Alerts from an unseen track stop after 0.3 s, not 0.6 s (ADR 0006). Contact 0/300 with 40% detection dropout. | L1 |
| 06 | Still-object and moving-object channels, smaller wins | met | ScenarioTest S-09, S-10, S-12 | L1 |
| 07 | Pitch shift changes at most 2% of decisions | not met | 2.95% (matched) and 14.36% (deployed) when the shift is not told to the method. The earlier 0.79% assumed a pitch estimator that does not exist. | L1 |
| 08 | No new alert from head rotation alone | not met | S-13: false STOP in 12.0% of 300 runs (`scenario_rates.md`) | L1 |
| 09 | No steering instructions on the safety path | met | ScenarioTest banned-phrase check; the veer hint is removed | L1 |
| 10 | Camera lost: cue start within 0.6 s, at the ear within 1.0 s, repeat every 30 s | partly | Start within 0.6 s of the last usable frame, or 1.25 s if a "Stop." is playing (HealthTest, ReviewRegressionTest). Ear timing uses assumed audio delay. The 30 s repeat works but has no test. | L1; ear timing needs hardware |
| 11 | Glasses or audio route lost: phone announces within 1.0 s | met (logic) | HealthTest F-05, F-06 | L1; OS route callbacks need hardware |
| 12 | Reduced frame rate or latency announced | met | HealthTest F-03, F-04 | L1 |
| 13 | Network loss: one low-priority notice | met | HealthTest | L1 |
| 14 | Start and stop chimes | met | HealthTest | L1 |
| 15 | Pre-emption by priority | met | ArbiterTest; S-19 shows no overlapping cues | L1 |
| 16 | One alert sequence per approach | met | S-07: exactly 2 STOPs in 299/300; S-08: exactly 1 in 299/300. After stop-and-resume a second STOP is given by design. | L1 |
| 17 | Non-urgent speech at most once per 4 s | met | ArbiterTest | L1 |
| 18 | No "clear / safe / walk now / cross now" | partly | Safety path and navigation: fixed wording, tested. Cloud: word-list filter; the reviewer's 24 paraphrases are silent, but a word list can be evaded. | L1 |
| 19 | Every cue logged with stage times | met | `CueLog` and `SimCue`; `demo_cue_log.md` | L3-substitute |
| 20 | Turn cues by predicted time | partly | Rule implemented; cues come several seconds before the corner because of GPS noise, so the wording is "at the next corner", never "now". | L1 |
| 21 | Recorded route when no key | partly | The route is synthetic, in Directions format, not a recorded Maps response. The live client is unverified (no key). | L1 |
| 22 | Off-route only after 5 s of evidence | met | NavigatorTest NV-04, NV-05 | L1 |
| 23 | Crossings announced as "ahead" only | partly | Holds on the synthetic route. Crossings are recognised only from the word "cross" in route text. | L1 |
| 24 | Cloud off the critical path | met | CloudGuardTest "slow or absent cloud" | L1 |
| 25 | Guard against hallucinated or injected answers | partly | CloudGuardTest (21) and ReviewRegressionTest; crossing always refused; guard cannot throw. Residual: word-list evasion. | L1 |
| 26 | Cloud works with no key | met | All cloud tests use recorded fixtures | L1 |
| 27 | Per-stage p50/p95/p99 report | needs hardware | The report exists but hardware stages are drawn from the assumed budget; only core compute is measured (desktop). | L1 |
| 28 | Fault injection | met | `Faults` in `sim/World.kt` | L1 |
| 29 | Core has no Android dependency | met | `core/` builds with a plain JDK | build |
| 30 | No secrets; runs without keys | met | `init.sh` on a clean clone, no keys | build |

## Known gaps not covered by any requirement test

- Drop-offs, curbs, stairs and glass are not detected (S-16, S-17).
- No real detector or depth model has been run. The perception stand-in assumes boxes exist.
- The Android adapters (DAT camera, LiteRT models, audio, location) are not written. The existing `app/` is unchanged.
