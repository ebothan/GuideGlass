# Requirement status after P4

Requirements are defined in `docs/00-problem-constraints-goals.md` section 9. Rungs are as in `CLAUDE.md`. Status values:
- **met**: met at the stated rung.
- **partly**: met with a stated exception.
- **not met**: fails as written.
- **needs hardware**: cannot be checked here.

Test names refer to `core/src/test/kotlin/org/guideglass/core/`: 74 tests, all passing on 2026-11-12.

| REQ | Short form | Status | Evidence | Rung |
|---|---|---|---|---|
| 01 | Safety path needs no network | met | HealthTest "network loss": hazard cue times identical with and without network | L1 |
| 02 | Fewer misses than R5, false alarms bounded | partly | Shared scene set, like-for-like: 1.30% FA / 11.33% miss vs R5 2.00 / 47.39 (`g6_baseline_comparison.md`). The deployed safety setting alerts earlier by design: 15.9% FA by the harness's 2.5 m definition. Worst error sweep: 2.86% FA, above R5. | L1 |
| 03 | STOP at 2.5 s, CAUTION at 4.0 s to contact | met | AssessorTest: STOP within one frame step of 3.5 m at 1.4 m/s | L1 |
| 04 | Predict to the ear using measured stage times | met | AssessorTest: a frame 0.5 s late fires 0.7 m earlier | L1 |
| 05 | Track life in seconds; survives 0.6 s gaps | met | TrackerTest at 7/15/30 fps, 0.5 s gap; ScenarioTest at 7/15/30 fps and 40% dropout. Alerts from a coasted track stop after 0.3 s (ADR 0006). | L1 |
| 06 | Still-object and moving-object channels, smaller wins | met | ScenarioTest S-09, S-10, S-12 | L1 |
| 07 | Pitch shift changes at most 2% of decisions | met | Pitch flip 0.79% (like-for-like) and 1.69% (deployed) on the shared scene set; ScenarioTest at 2/10/18 degrees | L1 |
| 08 | No new alert from head rotation alone | partly | S-13: 1/30 runs has a false STOP | L1 |
| 09 | No steering instructions on the safety path | met | ScenarioTest banned-phrase check; the veer hint is removed | L1 |
| 10 | Camera lost: cue start within 0.6 s, at the ear within 1.0 s, repeat every 30 s | partly | Start within 0.6 s when no equal-priority cue is playing (HealthTest). If one is playing, it waits up to 1.6 s (measured bound). Ear p95 877 ms (`latency_report.md`). Audio latency is drawn, not measured. | L1; ear timing needs hardware |
| 11 | Glasses or audio route lost: phone announces within 1.0 s | met (logic) | HealthTest F-05, F-06 | L1; OS route callbacks need hardware |
| 12 | Reduced frame rate or latency announced | met | HealthTest F-03, F-04 | L1 |
| 13 | Network loss: one low-priority notice | met | HealthTest | L1 |
| 14 | Start and stop chimes | met | HealthTest | L1 |
| 15 | Pre-emption by priority | met | ArbiterTest; S-19 shows no overlapping cues | L1 |
| 16 | One alert sequence per approach | met | S-07: exactly 2 STOPs in 30/30; S-08: exactly 1 in 30/30 | L1 |
| 17 | Non-urgent speech at most once per 4 s | met | ArbiterTest | L1 |
| 18 | No "clear / safe / walk now / cross now" | met | ScenarioTest, CloudGuardTest, NavigatorTest | L1 |
| 19 | Every cue logged with stage times | met | `CueLog` and `SimCue`; `demo_cue_log.md` | L3-substitute |
| 20 | Turn cues by predicted time | partly | Fire before the turn in 20/20 runs, but 1.1-7.7 s earlier than the ideal lead because of 4 m GNSS noise (ADR 0006 item 9) | L1 |
| 21 | Recorded route when no key | met | NavigatorTest. The route is synthetic, in Directions format. The live client exists but is unverified. | L1 |
| 22 | Off-route only after 5 s of evidence | met | NavigatorTest NV-04, NV-05 | L1 |
| 23 | Crossings announced as "ahead" only | met | NavigatorTest NV-06 | L1 |
| 24 | Cloud off the critical path | met | CloudGuardTest "slow or absent cloud" | L1 |
| 25 | Guard against hallucinated or injected answers | met | CloudGuardTest T1-T20 | L1 |
| 26 | Cloud works with no key | met | All cloud tests use recorded fixtures | L1 |
| 27 | Per-stage p50/p95/p99 report | met | `latency_report.md`. CAUTION and MOVING_OBJECT exceed the 780 ms budget at p95 (974 / 1050 ms) because of queueing behind other cues. | L1; core compute measured on desktop |
| 28 | Fault injection | met | `Faults` in `sim/World.kt` | L1 |
| 29 | Core has no Android dependency | met | `core/` builds with a plain JDK | build |
| 30 | No secrets; runs without keys | met | `init.sh` on a clean clone, no keys | build |

## Known gaps not covered by any requirement test

- Drop-offs, curbs, stairs and glass are not detected (S-16, S-17).
- No real detector or depth model has been run. The perception stand-in assumes boxes exist.
- The Android adapters (DAT camera, LiteRT models, audio, location) are not written. The existing `app/` is unchanged.
