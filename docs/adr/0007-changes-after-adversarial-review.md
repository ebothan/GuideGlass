# ADR 0007: Changes after the adversarial review

Status: accepted, 2026-11-12. Amends ADR 0004, 0005 and 0006. Details and measured effects per finding are in `docs/review/response.md`.

## Decisions

1. **Re-arm on resume.** Standing for 1 s and then walking re-arms every object's alert. A user who stops for a STOP and walks on is warned again.
2. **Movable classes default to the still-object assumption.** ADR 0006 item 3 held back STOP for people, bikes and cars until their motion was measured. That left STOP effectively off whenever motion could not be measured. Now the hold-back lasts at most 0.7 s after first sight, and never when the time to contact is urgent.
   - Accepted cost: more false STOPs (pedestrian ahead at the same pace: 13% of runs; parked car beside the path: 9.7%).
   - Reason: a missed standing person is the worse error.
3. **Only classes that can move, or unknown ones, are tested for motion of their own.** A pole or wall that appears to move is noise.
4. **STOP lead scales with walking speed** using the formula in the problem document, never below the configured 2.5 s. The human-model values (reaction 0.5 s, stopping distance 0.7 m) are per-user configuration.
5. **Dropped hazard cues are requested again.** The arbiter's outcome feeds back to the assessor. One cue covers two live tracks on the same object.
6. **Health uses only the phone's clock.** Frames older than 1.0 s, or stamped in the future, are unusable and do not count as a working camera.
7. **"Camera lost" and "Glasses disconnected" cut off every cue except a playing STOP.**
8. **Navigation never says "now".** "Turn right at the next corner."
9. **Cloud guard:** crossing questions are always refused; "forward" is never spoken; free text must avoid a list of movement, traffic and path-state words; the guard cannot throw.
10. **Reporting:** every rate is quoted over 300 seeds not used for tuning, with intervals. The baseline comparison is no longer called like-for-like and includes rows without a pitch estimate.

## Consequences

- False-STOP rates are now the main weakness (safety case C4). Lowering them without reopening the misses is the next design problem. It probably needs a real pitch estimate and better range, which need real footage.
- Gate G3 moves to `needs_hardware`. Requirements 02, 07 and 08 are "not met".
