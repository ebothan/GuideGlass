# ADR 0006: Design changes forced by the P4 simulation evidence

Status: accepted, 2026-11-12. Amends ADR 0004 and parts of docs/00 sections 7-9. All evidence is L1: synthetic scenes, geometric perception stand-in, and the repo's own noise model (10% box-size jitter, 3% box-position jitter).

The first scenario runs showed failures that the P3 design did not anticipate. Each change below is kept only because a scenario or test showed it was needed. Rates are over 30 seeds.

## Changes

1. **An object counts as "moving" only on significant, persistent evidence.**
   - Problem: with the repo's box jitter, a fitted closing speed is noisy by several m/s at 8 m. The first probe gave false STOPs for:
     - a parked car beside the path,
     - a wall 9 m away,
     - a user standing still,
     - fast head turns.
   - Rule now:
     - Fit velocity over 1.0 s with at least 8 samples, all after the last fast head turn.
     - Require the difference from a still object's expected closing speed to exceed 3 standard errors.
     - Require that on 3 consecutive decisions.
     - Hold the "moving" verdict for 1.5 s.
   - Forward and sideways motion are tested separately.
   - The expected closing speed is the walker's mean speed over the same window. This prevents a still object from looking "moving" after the walker changes speed.

2. **Image expansion (tau) is its own channel, with stricter evidence.**
   - Range noise hides a fast cyclist's speed at 15-20 m. Box growth does not depend on range, so it sees the cyclist earlier.
   - It needs at least 6 samples over 0.5 s, growth above 3 standard errors, and that on 2 consecutive decisions before it raises CAUTION.
   - It needs 4 consecutive decisions before it can raise STOP.
   - S-10 (cyclist head-on at 4.5 m/s): STOP at least 1.0 s before contact in 30/30 runs.

3. **Movable classes (person, bicycle, scooter, vehicle) get no STOP from the "still object" assumption until their motion has been measured.**
   - Exception: the close-range floor (1.2 m).
   - S-12 (pedestrian ahead at the user's pace) gave a STOP at first sight before this change. It now gives 2/30 false STOPs.
   - To keep approaching pedestrians timely (S-09), a movable object whose fitted closing speed, minus one standard error, exceeds the still-object value uses that lower bound.
   - Result: S-09 meets the 1.36 s typical margin in 30/30 runs.
   - Cost: one false STOP in 30 for a parked car beside the path (S-05). The S-05 test gate changed from 0 to at most 1 in 30.

4. **Re-arming needs either walking or backing away.**
   - Rule: an object's alert state re-arms after 1.2 s outside the alert zone, but only if the user is walking or the distance has grown by more than 1 m since the closest approach.
   - S-08 (standing in front of a bollard) re-alerted in 5/30 runs before. It now gets exactly one STOP in 30/30.
   - S-07 (stop, back off, approach again) gets exactly two STOPs in 30/30.

5. **No new STOP within 1.5 s of the last one.** In a crowd the user is already stopping, and repeated "Stop" adds nothing. S-19 went from up to 6 STOPs in 3 s to at most 2.

6. **Objects moving sideways are judged by their predicted position at contact.** A cyclist crossing well ahead passes through the path but is gone when the walker arrives. False STOPs went from 3/30 to 1/30.

7. **Track staleness is measured in capture time, not decision time.**
   - Bug: the first version judged staleness against decision time. With more than 0.6 s of transport delay, every track looked stale and hazard cues were silently dropped.
   - The bug was found by a unit test; it would have hit the F-04 latency-spike fault.
   - **Coast time reduced from 0.6 s to 0.3 s.**
     - With a true 0.6 s, tracks extrapolated through fast head turns gave false STOPs in 22/30 head-sweep runs, against 1/30 at 0.3 s.
     - Every earlier run had effectively used about 0.3 s, because of the bug.
     - Track identity still survives gaps up to 1.0 s (REQ-05 as worded still holds), but a track unseen for more than 0.3 s cannot cause an alert.

8. **Camera-loss timeout scales with the requested frame rate.**
   - At 2 fps, frames are 0.5 s apart, the same as the timeout, so the stream flapped between "camera lost" and normal.
   - Timeout is now max(0.5 s, 2.5 frame intervals) at the lower of the requested and measured rates. The app knows the requested rate: per research note 01#2, the glasses do not drop below 15 fps on their own.
   - After a Bluetooth loss, the state stays "glasses lost" until fresh frames return, so the user hears "Glasses back" rather than a spurious "Camera lost".

9. **Navigation smooths position and uses reported speed.**
   - With speed differenced from 4 m-noise GPS fixes, "turn now" fired up to 20 s early.
   - The along-route position is now filtered.
   - Speed comes from the fix, which phones report from Doppler. Its noise of 0.2 m/s is assumed [A].
   - The turn rule uses distance minus the fix's accuracy radius.
   - Turn cues now always come before the turn, but 1.1-7.7 s (median 3.7 s) earlier than the ideal 3.1 s lead. With about 4 m of GNSS error near a corner this is inherent.

10. **Range pessimism is a parameter (`pessimismK`, default 1).** Measured trade-off on the shared scene set, deployed setting:

    | k | False alarms | Misses |
    |---|---|---|
    | 1 | 15.9% | 0.94% |
    | 0.5 | 12.1% | 1.13% |
    | 0 | 10.0% | 1.54% |

    k = 1 is kept because a miss is the worse error. This is a value for field data to decide, not a settled one.

## Consequences

- The design now has many thresholds. Each is a configuration value with a stated reason, and the tests check behaviour over 30 seeds rather than one.
- Most were tuned on the same synthetic world they are tested in. **This is the main threat to validity.** The shared-scene result (G6) is the only check on data this design was not tuned on, because those scenes come from the repo's generator, not this one.
- Residual false STOPs, measured and kept as regression gates rather than hidden:

  | Scenario | False STOPs |
  |---|---|
  | S-05 parked car beside the path | 1/30 |
  | S-11 cyclist crossing well ahead | 1/30 |
  | S-12 pedestrian ahead at the same pace | 2/30 |
  | S-13 fast head turns | 1/30 |
