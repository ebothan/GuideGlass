# Competing hypotheses

Confidence: **H** high, **M** medium, **L** low. Each line cites the research note (NN#finding) that moved it.
Status: `open`, `favoured`, `rejected`. Update this file when evidence arrives; log changes in `docs/PROGRESS.md`.
Last revised: 2026-11-12, after P1.

## H1. Where should collision warnings be decided?
- **H1a: on the phone, network-free, from a tracker with time-to-contact.** Favoured, **H**.
  - Cloud time to first token is 1.6-7.4 s (07#1). Published assistive VLM latency is worse (07#2).
  - Gemini Live caps video at about 1 fps and limits session length (07).
  - A cue must arrive at the ear before about 2.5-3 s to contact (03#1).
- H1b: cloud vision model with an on-device fallback. Rejected, **H**.
  - Too slow for the deadlines above.
  - About 5-10% false object presence on clean photos (07, RePOPE).
  - Over-alerting (07, ForeSightGuide).
- H1c: hybrid where the cloud "confirms" an on-device alert before it is spoken. Rejected, **H**.
  - It adds seconds to the only path that has a deadline.

## H2. How should "is it close and coming at me" be estimated?
- **H2a: two time-to-contact channels with the pessimistic bound used.** Favoured, **M**.
  - Static objects: range divided by own walking speed.
  - Moving objects: image expansion (tau) fitted over about 0.5 s.
  - Basis: 01#5-7. Two-frame tau is roughly 50% wrong [est]. Range/speed error is roughly 10-25% [est].
- H2b: tau alone (the repo's looming rule and R1). Partly rejected, **M**.
  - Without a spatial gate it gives 31% false alarms (L1 baseline).
  - It is noisy at low frame rates (01).
- H2c: apparent box size as a proxy for distance (R5). Rejected as the primary method, **H**.
  - 47.4% misses at L1, and a low curb never alerts.
- Open: is monocular metric depth good enough at 1-4 m?
  - delta1 is 40-90% depending on dataset (01#9, 05). Plan for ±25% error, worse on glass and at night. **M**.
  - Ground-plane geometry from known camera height may beat learned depth for objects that touch the ground, but needs a pitch estimate. **L**, test in P4.

## H3. What is the main detector?
- **H3a: YOLOX-class detector (Apache-2.0) with person, bicycle and vehicle classes.** Favoured, **M**.
  - Reported 1.6 ms w8a8 on Snapdragon 8 Gen 3 (05). This latency is read only, not measured by us.
  - ML Kit's base model has no person or vehicle class (05).
- H3b: keep ML Kit. Rejected, **M**. It lacks those classes and has no tracking IDs we control.
- H3c: Ultralytics YOLO. Rejected on licence (AGPL), **H**.
- Open: walkable-surface, curb and stairs segmentation.
  - No sidewalk-specific model has phone latency we could verify (05). **L**.
  - Drop-offs and stairs stay the largest perception gap.

## H4. Camera motion and head-versus-body heading
- **H4a: compensate head rotation with the glasses IMU (`mwdat-motion`) where available.** Open, **M**.
  - The module exists but is beta-only and cannot ship to production (06#2).
  - Data is rotated 90° on glasses without a display (06#2).
  - The glasses stabilise and crop frames, which weakens camera-IMU fusion (01#1, 06#5).
- H4b: treat fast head turns (gyro, or image-flow magnitude when there is no IMU) as a short low-confidence mode. Favoured as the fallback, **M**.
  - 100 deg/s gives about 150 px of image motion per frame at 10 fps [est] (01#4).
- Travel heading: GPS course over ground while moving, with the phone in a known pose only when stationary (04). **M**.
  - The glasses show where the head points, not the direction of travel.

## H5. Frame rate and link
- H5a: plan for 2-7 fps. **Revised by 01#2 and 06.**
  - Meta's automatic quality ladder does not go below 15 fps on its own.
  - Lower rates come only from our own request or from app stalls.
  - The 7 and 2 fps scenarios stay as fault-injection cases, not the expected operating point. **M**.
- Measure all tracker timeouts in seconds, not frames (01#3). **H**.
- Frame size can change silently, so read it from every frame (06#4). **H**.
- Frame timestamp (`presentationTimeUs`) may be capture time or arrival time; undocumented (06#6). **L**.
  - Design so that a hardware measurement can pick the true offset.

## H6. Cue timing and design
- **H6a: trigger on predicted time at the ear, with two levels.** Favoured, **M**.
  - CAUTION first, then STOP at about 2.5 s or 3 m, whichever comes first (03#1).
  - Predicted time at the ear = TTC minus pipeline latency, Bluetooth audio latency and utterance duration.
  - Older adults may need 2-3 or more steps to stop (03#1).
- H6b: keep the fixed 2.0 s TTC trigger from the repo. Rejected, **M**.
  - 2.0 s leaves about 2.8 m at 1.4 m/s, which is roughly what reaction plus stopping already consume (03#1).
- Pre-emption: higher priority cuts off lower priority, never queues behind it (03#4, TCAS). **H**.
- Users respond to alarms in proportion to how often they are real (03#3, 02).
  - Track STOP precision and false alarms per walking hour as first-class metrics. **H**.
- Silence must never mean clear. **H**.
  - A frozen-frame or camera-loss watchdog announces within 1 s (02, 03).
  - An optional sparse "alive" tick, off by default (03#16).
- Never say "walk", "safe" or "clear" (03#7, 04). **H**.
- Bluetooth audio: HFP drops cue audio to mono 8/16 kHz, and A2DP and HFP are mutually exclusive (06). Route latency is unknown and must be measured on hardware (L4).

## H7. Cloud tier
- **H7a: cloud only for informational jobs.** Favoured, **H**.
  - Jobs: descriptions on request, reading signs, rerouting.
  - Every answer passes a guard that classifies risk, drops stale answers, never speaks safety-negating claims, and requires on-device corroboration for action instructions (07 design D3-D6).
- Scene text is read out only as a quote, never acted on: signs can hijack these models (07#4). **H**.
- Blur faces and licence plates on the phone before upload (07#6). **M**. Retention by default is 30-55 days.

## H8. Navigation
- Google walking routes lack sidewalk, crossing and street-side detail. GNSS in urban canyons often puts the user on the wrong side of the street (04#9). **H**.
  - So turn cues use predicted time (distance over current speed plus latency plus utterance).
  - The route is advisory; crossings are announced as "crossing ahead", never "cross now".
- Visual positioning could fix side-of-street errors. Accuracy is about 5 m and 5° (04#10). Feeding it glasses frames is unverified. **L**, out of scope for this prototype.

## H9. Evaluation
- The repo's synthetic scene set stays as the shared yardstick for G6 (unchanged harness). **H**.
- Real footage for replay: SANPO, CC BY 4.0, downloadable file by file (08). **M**, about 4 MB per frame with depth.
- Statistics (08, 02):
  - Claiming a miss rate under 5% needs at least 59 hazard events with zero misses.
  - Showing a drop from 47.4% to 30% needs about 122 events per arm.
  - Synthetic scenes give plenty of events, but they are L1 only.

## Self-critique after P1
- Most latency figures are read or estimated, none measured on a phone. The latency budget in P2 will be mostly L0.
- Every number from the glasses SDK is read from docs; the meaning of the timestamp and the audio route latency are unknown until L4.
- The plan risks over-investing in obstacle tracking, which the synthetic harness rewards. Drop-offs and stairs are the more serious gap but have no good on-device model yet.
  - P2's hazard analysis must rank this honestly.
  - A drop-off result may only be achievable as "implemented, unverified".
- Several key human-factors sources were seen only through snippets: Bliss 1995, the Pundlik trial, ISO 15623 (03, 02).
  - Thresholds derived from them are starting values, not requirements.
