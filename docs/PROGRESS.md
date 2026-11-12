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

## 2026-11-12: P1 Research (in progress)
- Four subagents (workstreams 1+2, 3+4, 5+6, 7+8) started, stopped on a usage limit before writing files, and were resumed.

## Next
1. Collect the eight research notes; check every finding has a source and confidence.
2. Write `docs/research/hypotheses.md`.
3. Commit and push; then P2.
