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

## Next
1. P2: `docs/00-problem-constraints-goals.md` (needs, ODD, hazard and scenario library, REQ-xx, latency budget tagged assumed/measured/read, lead-time windows per cue type).
2. P3: ADRs comparing at least two architectures; degraded modes.
