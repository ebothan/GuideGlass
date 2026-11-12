# ADR 0004: How hazards are ranged and when cues fire

Status: accepted, 2026-11-12. Revisit after the range-error sweep in P4.

## Context

The repo decides from apparent box size in one frame and fires at a fixed 2.0 s. Research (01, 03) and section 8 of the problem document show both are inadequate.

## Decisions

1. **State is metric and walker-centred.** Each track holds range and lateral offset in metres on the ground plane, relative to the direction of travel, plus their rates. The corridor is ±0.45 m in metres, not a band of image columns.

2. **Range comes from two independent sources, fused by variance:**
   - (a) Ground-contact geometry: the box bottom edge, camera height and camera pitch. This needs a pitch estimate, taken from the horizon implied by other ground contacts or from the IMU when available.
   - (b) A monocular depth model sampled inside the box.
   - Head-height objects have no ground contact and use (b) only.
   - Alternative rejected: depth only. It leaves one failure mode (glass, night) with no cross-check.
   - Alternative rejected: box size with class height priors. The shared scene set has no classes, and real obstacles vary too much.

3. **Two time-to-contact channels; the smaller wins (REQ-06):**
   - Static channel: range divided by walking speed.
   - Motion channel: image expansion fitted over at least 0.5 s, for tracks whose range rate differs from walking speed.

4. **Trigger on the pessimistic bound.** Time to contact is computed from range minus one standard deviation of its estimate.

5. **Predict to the ear (REQ-04).**
   - Decision time to contact = estimate at capture time, minus the measured age of the frame at decision, minus configured audio latency.
   - Thresholds: STOP ≤ 2.5 s and CAUTION ≤ 4.0 s, minus the pipeline term already spent.
   - Close-range floor: STOP at range ≤ 1.2 m while approaching.

6. **Track life is in seconds (REQ-05):**
   - Confirm after 0.2 s of consistent detections [A].
   - Coast through gaps up to 0.6 s [A].
   - Delete after 1.0 s unseen [A].

7. **Head rotation.**
   - If a yaw rate is available (IMU, or global image shift between frames), tracks are rotated by it before matching.
   - Above 60 deg/s [A] no new track may be confirmed and the motion channel is disabled, because expansion estimates are unreliable while the image smears.
   - Confirmed tracks keep their static-channel alerts.

8. **Walking speed** comes from step detection or GPS speed on the phone. In tests it is supplied by the scenario. If speed is unknown, 1.4 m/s is assumed, and the close-range floor still applies.

## Consequences

- Performance on the shared scene set depends on the assumed error of sources (a) and (b). The evaluation reports results across a sweep of that error, including the pessimistic 30% scale error the repo's own harness uses for its depth rule (R4).
- The baseline scene set has no class labels and one object per scene. It does not exercise the motion channel or association. Those are tested by this project's own scenarios (S-09 to S-13), which carry less weight as evidence because the author wrote both sides.
