# Plan (owned and revised by the maintainer; revisions logged in PROGRESS.md)

| Phase | Output | Gates it closes | Exit condition |
|---|---|---|---|
| P0 Orient | CLAUDE.md, PROGRESS.md, acceptance.json, init.sh, baseline numbers | none (G10 started) | Files exist; baseline R5 reproduced |
| P1 Research | docs/research/01..08, hypotheses.md | feeds G9 | 8 notes + hypotheses; every finding has a source and a confidence |
| P2 Problem | docs/00-problem-constraints-goals.md (needs, ODD, hazard/scenario library, REQ-xx, latency budget, lead-time windows) | feeds G3, G9 | Every budget figure tagged assumed/measured/read |
| P3 Architecture | docs/adr/ (at least two architectures compared), degraded modes and what the user hears | feeds G4, G9 | At least one ADR; keep/replace list for repo pieces |
| P4 Build | Kotlin core (JVM, no Android SDK): frame source, perception, tracking, decision, arbitration, speech sink, health monitor, nav, cloud guard; Python closed-loop harness | G1-G8 | Each gate pass, needs_hardware or blocked with evidence |
| P5 Evaluate | Scenario suite, regression gates, fault injection, adversarial review | G4, G6, G9 | Review findings fixed or listed |
| P6 Finish | Final report, hardware-validation checklist | G9, G10 | All gates set |

## Order of work in P4 (by risk, revisited after P2)
1. Walking skeleton: replayed/synthetic frames -> detections -> decision -> cue -> logged speech, timestamp per stage (G1, G3 seed).
2. Health monitor and degraded cues (G4): loss of camera/BT/network is the cheapest high-severity fix.
3. Tracking + time-to-contact with latency compensation, evaluated on the repo's scene set (G6).
4. Cue arbitration and repetition control, including the double approach (G8).
5. Navigation with predicted-time turn cues on a replayed GPS trace (G5).
6. Cloud guard (G7).

## Risks
- No Android SDK/emulator (user decision): G2 cannot pass here; the JVM end-to-end run is the substitute and is labelled as such.
- Synthetic scenes only; any real-footage replay depends on finding a small, licensed sample (P1 workstream 8).
- Disk space (about 8 GB free) limits model downloads; on-device model latency will mostly be read (L0), not measured.
- Research budget: subagents hit a usage limit once; notes may be thinner than planned.
- Self-evaluation bias: the same author builds and scores the system; mitigated by the adversarial review and by keeping the repo's baseline harness unchanged.
