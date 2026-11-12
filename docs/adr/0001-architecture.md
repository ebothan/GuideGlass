# ADR 0001: Overall architecture

Status: accepted, 2026-11-12. Requirements cited are in `docs/00-problem-constraints-goals.md`.

## Context

The repo has two tiers: a per-frame box heuristic on the phone and a cloud vision model. Its own simulation shows 47.4% misses [L1]. It has no tracking, no range estimate, no latency compensation and no announced failure state.

## Candidates

**A. Deepen the existing two tiers.** Keep the per-frame box rule, tune thresholds, add a looming check, keep the cloud model for guidance.

**B. A layered on-device stack in the style of an automated-driving system, with a separate health monitor.** Stages: frame source, perception, tracking with prediction in a metric walker-centred frame, hazard assessment by time to contact, cue arbitration, audio. A separate monitor watches the stream, the timing and the audio route and owns the degraded cues. Navigation and the cloud tier feed the same arbiter at lower priority.

**C. One vision-language model does everything.** On-device small model or cloud model turns frames into spoken cues directly.

## Comparison

| Requirement | A | B | C |
|---|---|---|---|
| REQ-01 no network on the safety path | Yes | Yes | Only if on-device; phone VLMs take far longer than the deadline, and Gemini Nano is blocked in the background (05) |
| REQ-02 fewer misses than R5 at a bounded false-alarm rate | Unlikely: the repo's own comparison shows size and looming rules trade misses for false alarms | Plausible: a metric range and a corridor in metres attack the cause (apparent size is a poor stand-in for distance) | Unknown; 5-10% false object presence (07) |
| REQ-03/04 cue timed at the ear | No state to predict forward | Yes: tracks carry range and closing speed | No |
| REQ-05 time-based track life | No tracks | Yes | No |
| REQ-10 to 14 announced failures | Would have to be added beside it | Built in as its own component | Would have to be added |
| REQ-16 one alert sequence per approach | Needs per-object memory; the repo uses cooldown timers instead | Natural: alert state lives on the track | Poor: 1.7 redundant alerts per output reported (07) |
| REQ-25 cloud cannot instruct | Cloud speaks directly today | Guard sits before the arbiter | The model is the instructor |
| REQ-29 portable core | Logic is inside an Android Activity | Pure logic, no platform types | Model runtime is platform-specific |
| Cost and risk | Lowest effort | Most code; depends on a range estimate whose error is ±25% | Latency and hallucination make it unfit for the safety path |

## Decision

**B.**

- A cannot meet REQ-03, 04, 05 or 16 without becoming B.
- C fails REQ-01 and the timing requirements on today's evidence (K8, K9).

## What is kept from the repo, and why

| Piece | Decision | Reason |
|---|---|---|
| `paper/simulation` harness and scene set | Keep unchanged | It is the shared yardstick for gate G6; changing it would make the comparison meaningless |
| Arbitration idea (urgent pre-empts, duplicates dropped) | Keep the idea, rewrite | Agrees with aviation practice (03#4); the repo's version is cooldown timers inside an Activity |
| Edge-triggered alerts with re-arm (1.2 s) | Keep the behaviour | Passes the double-approach case; moved onto tracks |
| Maps Directions client, polyline decoder, step model | Keep, port into the core as plain data plus a recorded-response source | Works; small; needed for G5 |
| Glasses camera source and YUV conversion (`meta-glasses` branch) | Rewrite against DAT 1.0.1 as a thin adapter, in the Android phase | The branch pins 1.0.0; 1.0.1 fixes stream freezes (06#1). Cannot be compiled here (K13), so it is deferred, not dropped |
| ML Kit detector | Replace | No person or vehicle class (05) |
| Size and looming thresholds (R5) | Replace | 47.4% misses; kept only as the baseline |
| Local "veer left/right" hint | Remove | A wrong steering hint is worse than none (REQ-09) |
| Cloud model speaking directly to the user | Replace with guard + arbiter | REQ-25 |
| Fixed 2.0 s trigger, fixed-distance turn cues | Replace | Section 8 of the problem document |
| `VisionActivity` (1,534 lines) | Not built on | Mixes camera, map, sensors and both tiers; untestable |

## Consequences

- The safety result depends on range quality. The evaluation must sweep range error and report where the design stops beating R5.
- More components to test; mitigated by a deterministic, single-threaded core (ADR 0002).
- Drop-offs and stairs remain undetected in this architecture until a ground-surface model is added. This is a stated gap.

## Self-critique

Design B is scored here by its own author against requirements the same author wrote. The only outside check is the repo's unchanged scene set (G6) and the adversarial review in P5.
