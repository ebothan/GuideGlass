# Progress log

## 2026-11-12: P0 Orient (done)

Environment (checked 2026-11-11/12):
- macOS on Apple M4, 16 GB RAM, about 8 GB disk free (tight).
- Java: system Java 8 only; installed JDK 17 via Homebrew at `/opt/homebrew/opt/openjdk@17`.
- Android SDK and emulator: not installed. The user declined installing them (2026-11-11). Consequence: the Android app is not compiled here; the safety core is a pure Kotlin/JVM module; G2 is `blocked`.
- Python 3.11 with a project `.venv/` (numpy, matplotlib). Xcode 26.6 present (iOS not in scope yet). Node 24.
- Network: GitHub, Google Maven, Maven Central, PyPI, arXiv and Meta developer pages reachable.
- GitHub: logged in as `ebothan`; fork `ebothan/GuideGlass` created; branch `av-prototype` from upstream `main` at d018e3d, pushed.

Baseline (L1, reproduced with the repo's own `paper/simulation` scripts, seed fixed):
| Rule | False alarm % | Miss % |
|---|---|---|
| R5 shipped | 2.00 | 47.39 |
| R1 optical tau only | 31.03 | 24.47 |
| R2 size only | 26.23 | 22.36 |
| R3 bottom edge | 5.72 | 57.86 |
| R4 relative depth | 11.17 | 47.66 |
Numbers match `paper/simulation/README.md`. Regenerated PNGs differ byte-wise under this matplotlib version, so `init.sh` runs the simulation in a temp copy and checks the JSON numbers only.

Decisions:
- D1 (provisional, ADR due in P3): safety core as a standalone Kotlin/JVM Gradle build under `core/`, so it builds and tests without the Android SDK and the Android app wraps it later.

## 2026-11-12: P1 Research (done)
- Four subagents (workstreams 1+2, 3+4, 5+6, 7+8) started, stopped on a usage limit before writing files, were resumed with a reduced target (about 15 findings per note), and finished.
- Notes `docs/research/01..08` contain 142 findings in total. Every finding has a confidence level.
  - 125 cite a URL or SDK file path.
  - The other 17 either point to another finding's sources (synthesis, numbers tagged [est]) or name a source the agent could not open, marked "not opened" with Low confidence.
  - Checked with an awk scan of each Findings section.
- No on-device model latency was measured; all model numbers are read (L0).
- `docs/research/hypotheses.md` written. Main design shifts:
  - Two-channel time-to-contact.
  - Two-level CAUTION/STOP triggered on predicted time at the ear (about 2.5 s or 3 m) instead of the fixed 2.0 s.
  - YOLOX-class detector instead of ML Kit.
  - Cloud tier informational only, behind a guard.
  - Low frame rates are a fault case, not the norm.
  - The glasses IMU exists but is beta-only.
- Not verified by any agent:
  - ISO/SAE standard full texts.
  - Bliss 1995 and the Pundlik trial (snippets only).
  - The 2026 Sensors paper (HTTP 403).
  - The meaning of the DAT frame timestamp.
  - Audio route latency.
  - Phone latency for most models.

## 2026-11-12: P2 Problem, constraints, goals (done)
- `docs/00-problem-constraints-goals.md`: needs, 14 constraints, ODD, 43 scenarios (S-, F-, NV-, C-), 30 requirements, latency budget, lead-time windows.
- Latency budget (all L0; every figure tagged assumed or read, none measured):
  - 440 ms p50 and 780 ms p95 to earcon onset.
  - 570 ms p50 and 1040 ms p95 to speech onset.
- Design values: `L_urgent` 0.70 s, `L_speech` 1.00 s, both configuration.
- Lead times:
  - STOP needs 2.06 s typical and 3.06 s conservative, so the default is 2.5 s (the repo's 2.0 s is too late).
  - CAUTION fires at 4.0 s.
  - "Turn now" fires at 2.9 s predicted time.

## 2026-11-12: P3 Architecture (done)
- ADR 0001: three architectures compared; chose the layered on-device stack with a separate health monitor. Keep/replace table for repo pieces.
- ADR 0002: `core/` is a plain Kotlin/JVM library, deterministic and event-driven, with ports for platform adapters.
- ADR 0003: G2 stays blocked; G2a (desktop end-to-end run) added and labelled L3-substitute.
- ADR 0004: metric walker-centred tracks, two range sources, two time-to-contact channels, pessimistic bound, prediction to the ear, time-based track life.
- ADR 0005: cloud answers are data behind a guard.
- `docs/01-architecture.md`: components, timing record, nine system states with what the user hears, cue priorities.
- Plan unchanged; the P4 order in `docs/PLAN.md` still holds.

## 2026-11-12: P4 Build (done; G2 blocked by user decision)

### Built
- `core/`: standalone Kotlin/JVM library (ADR 0002). Contents:
  - Range estimation: ground-contact geometry plus a depth stand-in, fused.
  - Tracker with time-based life.
  - Hazard assessor with still, moving and expansion channels, predicted to the ear.
  - Cue arbiter.
  - Health monitor with 7 states.
  - Navigator.
  - Cloud guard.
  - Simulation: synthetic world, fault injection, latency model, event-log replay.
- 74 tests, all passing.

### Gates and headline results (all L1 unless stated)
- **G1:** `init.sh` passes on a clean clone in 38 s. It covers the R5 baseline, the core tests and the G6 regression.
- **G6:** `paper/simulation/export_scenes.py` re-creates the repo's seed-53 scene set, and re-scores R5 at 2.00/47.39 as a check.
  - Like-for-like: 1.30% false alarms / 11.33% misses (906/8000), against R5's 2.00 / 47.39.
  - Worst error sweep (depth scale sd 0.5, pitch sd 5°): 2.86% false alarms / 18.5% misses.
  - Deployed setting (STOP at 2.5 s, pessimistic range): 15.9% / 0.94%.
  - Close misses (0.8-1.5 m): 234 of 251 have the object's foot below the frame (depth only).
- **G3 (`docs/results/latency_report.md`):**
  - STOP capture-to-ear p95 is 613 ms against a 780 ms budget.
  - CAUTION (974 ms) and MOVING_OBJECT (1050 ms) **fail** the budget, because of queueing behind equal-priority cues.
  - Hardware stages are drawn from the assumed budget, not measured.
  - Core compute p95 is 0.02 ms on the desktop JVM.
- **G4, G5, G7, G8:** pass. See `acceptance.json` and `docs/results/requirements_status.md`.
- **G2a:** `docs/results/demo_cue_log.md`. Seven scenarios plus navigation, run end to end. The saved event logs replay to identical cues.

### Decisions
- ADR 0006 records ten design changes forced by simulation.
- Test thresholds changed openly:
  - S-10: a CAUTION before the STOP is no longer required.
  - S-05: the false-STOP gate went from 0 to at most 1 in 30.
  - Tracker coast unit test: now reads the configured value.
  - Range-fusion widening: the threshold was off by an equality.
  - 30 fps confirmation: a floating-point margin.

### Known gaps
- No drop-off, stairs or glass detection.
- No real models run.
- Android adapters not written.
- Residual false STOPs: 1-2 in 30 in S-05, S-11, S-12 and S-13.
- Most thresholds were tuned in the same synthetic world they are tested in. This is the main threat to validity.

## Next
1. **P5:**
   - Evaluation hardening.
   - Adversarial review by a fresh subagent with no access to this reasoning. It attacks the hazard analysis and every claim; fix or list each finding.
   - Write `docs/safety-case.md`, consolidated from `docs/research/02`.
2. **P6:**
   - Final report.
   - Hardware-validation checklist (H-1 to H-n), including Android adapters against DAT 1.0.1 and real latency measurements.
   - Set G9 and G10.
