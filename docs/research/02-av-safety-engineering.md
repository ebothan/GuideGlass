# Applying AV safety engineering, lightly, to GuideGlass

Research note for GuideGlass. Written 2026-11-11. Numbers are tagged **[read]** (from a source I opened) or **[est]** (my own estimate or arithmetic). Several standards' product pages blocked automated access. Where I summarise a standard I could not open, it is marked **not opened**, and the summary rests on widely published descriptions, so treat those findings as lower confidence. No standard text is reproduced here.

GuideGlass is a research prototype that supplements a long cane or guide dog. It is not a mobility aid, and nothing here claims it is safe.

## Question

How should AV safety engineering be applied, lightly, to this product?

## Findings

1. **STPA fits this product better than FMEA or FTA, because our worst hazards come from correct components interacting badly (latency, trust, silence), not from part failures.** The STPA Handbook defines a loss as harm to something stakeholders value, and a hazard as a system state that, together with worst-case environmental conditions, leads to a loss **[read]**. It defines four types of unsafe control action (UCA) **[read]**: not provided; provided and unsafe; provided too early, too late or out of order; and applied too long or stopped too soon. A loss scenario explains why a UCA occurs *and* why a correct action might be executed wrongly, with sensors and actuators included in the control structure **[read]**. Abdulkhaleq et al. apply STPA to an automotive function and derive safety constraints from UCAs **[read]**.
   - Sources: https://psas.scripts.mit.edu/home/get_file.php?name=STPA_handbook.pdf ; https://arxiv.org/abs/1703.03657 (opened)
   - Confidence: **high**.
   - Design impact: Model the control loop explicitly: glasses camera -> BT Classic or Wi-Fi -> phone perception -> decision -> TTS/earcon -> BT A2DP -> ears -> human -> gait. Then derive UCAs for the *cue* (the control action) and for the *human's* control action (stop, veer, continue). The "human" controller and their mental model ("silence means clear") belong inside the analysis. See the UCA table in the safety case below.

2. **SOTIF (ISO 21448) is the most relevant standard. Our main risks are functional insufficiencies (the perception cannot see glass, drop-offs, or things at night), not faults.** SOTIF sorts scenarios into known-safe, known-unsafe, unknown-unsafe and unknown-safe, and aims to shrink the two unsafe areas. It uses "triggering conditions" (conditions that activate an insufficiency) and "functional insufficiencies" (specification or performance gaps). **Not opened** (the ISO page blocked automated access).
   - Source: https://www.iso.org/standard/77490.html (not opened)
   - Confidence: **medium**. These concepts are widely described, but I did not read the standard.
   - Design impact: Keep a **triggering-condition register**: glass doors, low sun, night, rain on the lens, head-level overhangs, descending stairs and curbs, fast cyclists, crowds, sudden head turns, frame-rate drops. Each entry needs one of three outcomes: handled (with evidence), announced as out of ODD, or a documented residual risk. Use log replay (Finding 9) to move conditions from "unknown" to "known".

3. **From ISO 26262, borrow the vocabulary (safety goal, safe state, fault-tolerant time interval, freedom from interference) and skip the rest.** Safety goals are top-level requirements derived from hazard analysis. The fault-tolerant time interval (FTTI) is the time from a fault to possible harm, and detection plus reaction must fit inside it. ASIL determination, hardware metrics and the full V-model documentation are overkill for a research prototype on consumer hardware, and their premises (controlled hardware, quantifiable random failure rates) do not hold for a phone plus Meta glasses. **Not opened** (ISO page blocked).
   - Source: https://www.iso.org/standard/68383.html (not opened)
   - Confidence: **medium**.
   - Design impact: Define an FTTI for "loss of perception". At 1.4 m/s with a 2 s trigger, a hazard can go from out-of-range to inside the TTC trigger in about 1-2 s. The watchdog must therefore detect stalled frames and announce degradation within about **0.5 s** of the last good frame **[est]**. The "safe state" is not "stop the user". It is "the user knows the aid is not watching", so they rely only on the cane or dog. Keep the alert path free of interference: the optional cloud model and the TTS queue must not delay the earcon path.

4. **The ODD is the core scoping tool. State it as a taxonomy (scenery, environment, dynamic elements) and say what happens outside it.** SAE J3016 defines the ODD as the operating conditions a driving automation feature is specifically designed for **[not opened; J3016 page returned no text]**. BSI PAS 1883 gives a free ODD taxonomy with top-level groups for scenery, environmental conditions and dynamic elements **[not opened; BSI PDF failed to load]**. ISO 34503 builds on it with a hierarchical ODD taxonomy for ADS **[not opened]**.
   - Sources: https://www.sae.org/standards/content/j3016_202104/ ; https://www.iso.org/standard/78952.html ; BSI PAS 1883 (all not opened)
   - Confidence: **medium**.
   - Design impact: Write an ODD with an explicit in and out list (draft below). Monitor the ODD at runtime with cheap checks (ambient light from image statistics, effective fps, yaw rate, walking speed, phone battery and thermal state) and **announce exits**. A pedestrian aid cannot "pull over", so leaving the ODD always means "announce and degrade", not "continue silently".

5. **Fallback and takeover research says humans need seconds, not milliseconds, and modality matters.** Zhang et al. pooled take-over times from 129 studies. Shorter take-over times went with higher urgency, auditory or vibrotactile requests rather than visual-only ones, no handheld device, and prior exposure to the scenario **[read: abstract via search; full text not opened]**. The pooled mean take-over time, which I have seen cited as about 2.7 s, is **not verified**.
   - Sources: https://research.tudelft.nl/en/publications/determinants-of-take-over-time-from-automated-driving-a-meta-anal (abstract via search; full text not opened)
   - Confidence: **medium**.
   - Design impact: (i) Driver takeover is a different task from "stop walking", but the transferable lesson holds: an auditory cue the user has rehearsed beats a novel one. Train users on each earcon at onboarding (L5). (ii) Use a distinct, short earcon for "stop now" and keep speech for non-urgent information. (iii) Announcements of degraded mode should come *early and calmly*, not as emergencies.

6. **The cry-wolf effect is quantified: people match their response rate to alarm reliability.** In Bliss et al. (1995), 138 participants faced alarms that were 25%, 50% or 75% true. About 90% matched their response rate to the true-alarm probability, and about 10% used all-or-none strategies **[read: abstract via search snippets; full paper not opened]**. Manzey et al. (2014) found extreme non-responding with low-PPV alarms, and found that giving validity information reduced it **[read: abstract via search snippet]**.
   - Sources: https://stars.library.ucf.edu/scopus1990/1980 (search result) ; https://informahealthcare.com/doi/abs/10.1080/00140139.2014.957732 (search result; not opened)
   - Confidence: **medium**.
   - Design impact: **The positive predictive value (PPV) of a "stop" cue is a safety metric, not only a UX metric.** If only half of stop cues are real, expect users to respond to about half of them. Set a PPV target for the urgent channel (for example >= 80% in the ODD **[est]**). Graded cues (a "caution" tone vs a "stop" tone) carry validity information, which Manzey found reduces extreme non-responding. Report the false-alarm rate per hour of walking, not only per frame. Today's simulation figure of 2.0% false alarms per frame is meaningless to a user without the per-hour rate.

7. **Fail-silent is the dangerous default for this product. It needs "fail-loud-then-degrade".** In AV practice, fail-operational means continuing in reduced capacity, and fail-silent means a component stops emitting outputs so it cannot emit wrong ones **[est: general systems-engineering usage; no single source opened]**. The brief notes that users read silence as "clear". The STPA Handbook's "control action not provided" UCA type **[read]** covers exactly this.
   - Source: STPA Handbook (opened)
   - Confidence: **high** on the reasoning.
   - Design impact: Every degraded state has an audible signature (degraded modes table below). Add a low-rate heartbeat (an optional subtle tick every N seconds while protecting, or on request via a tap) so the absence of the heartbeat means "not watching". Its annoyance cost must be tested at L5.

8. **Shadow mode is the cheapest strong evidence, and a BLV RCT already used it.** Shadow mode runs a new function silently next to the live one and logs disagreements. Pundlik et al.'s collision-warning RCT alternated "active" and "silent" device modes within participants, and reported a contact rate ratio of 0.63 (contacts per 100 hazards per hour) **[read: via search snippets of the press release; JAMA full text not opened]**.
   - Source: doi:10.1001/jamaophthalmol.2021.2624 (not opened); https://masseyeandear.org/news/press-releases/2021/07/wearable-devices-can-reduce-collision-risk-in-blind-and-visually-impaired-people (search result)
   - Confidence: **medium**.
   - Design impact: Ship the new reflex in **shadow mode beside the old one** (both log, only the old one speaks) during L4 sighted-tester walks, and record the disagreements. At L5, use silent and active epochs, with masked video review, for outcome measures.

9. **Scenario-based testing and log replay transfer directly.** ASAM OpenSCENARIO XML describes dynamic scenarios (manoeuvres, storyboards, triggers) for simulation **[read: ASAM page]**. ISO 34502 gives a scenario-based safety evaluation framework **[not opened]**. PEGASUS defined a scenario-based method with functional, logical and concrete scenario layers **[not opened in this pass]**. AMLAS gives a six-stage process for building a safety case for an ML component: scoping, requirements, data management, model learning, verification, deployment **[read]**.
   - Sources: https://www.asam.net/standards/detail/openscenario-xml/ (opened) ; https://arxiv.org/abs/2102.01564 (AMLAS, opened) ; https://www.iso.org/standard/78951.html (not opened)
   - Confidence: **high** on the method; **low** on adopting OpenSCENARIO itself, which is vehicle-centric and heavier than we need.
   - Design impact: Define a small **scenario catalogue** in our own lightweight format: logical scenarios (for example "static head-level obstacle, approach speed 0.8-1.6 m/s, light 10-10,000 lux, yaw 0-120 deg/s") with concrete instances drawn from recorded footage. Every perception change must re-run the catalogue on recorded logs (L2) with fixed pass criteria. Follow AMLAS for the detector and depth model: record data provenance and the ODD coverage of the training and test sets.

10. **Safety metrics: borrow TTC and post-encroachment time (PET) as surrogate measures, and normalise every rate by exposure.** FHWA's SSAM derives conflict surrogates, including TTC and PET, from trajectories, and uses a TTC threshold to classify conflicts **[read]**. Pundlik et al. normalised contacts per 100 hazards per hour **[read via snippet]**.
    - Sources: https://www.fhwa.dot.gov/publications/research/safety/08051/ (opened) ; RCT as above.
    - Confidence: **high**.
    - Design impact: Primary metrics: (a) **miss rate per hazardous encounter** (a hazard reached TTC < 1 s with no cue at least 1.5 s before **[est thresholds]**); (b) **false-alarm rate per walking hour**; (c) **PPV of urgent cues**; (d) **cue lead time distribution** (time from cue onset at the ear to the time of contact or closest approach); (e) minimum TTC or PET for near misses on replay; (f) contacts per 100 hazards per hour at L5. Report every rate with its denominator and evidence rung.

11. **Statistical evidence for a miss-rate claim needs hundreds to thousands of hazardous encounters, not hours of walking.** Rule of three **[est: standard binomial result]**: with zero misses in n independent encounters, the 95% upper bound on miss rate is about 3/n. Claiming a miss rate under 5% needs about 60 consecutive caught encounters. Under 1% needs about 300. Under 0.1% needs about 3,000. If hazardous encounters occur at about 10-30 per walking hour in urban routes **[est; must be measured]**, then < 1% needs about 10-30 h of *annotated* walking, and < 0.1% needs 100-300 h. With one or more misses, the requirement grows (for example about 475 encounters for < 1% with 1 miss **[est: binomial]**). Kalra & Paddock (RAND 2016) used the same failure-free logic to show that AVs would need hundreds of millions of miles to demonstrate fatality-rate parity. **Not opened** (RAND returned 403); cited only for the method.
    - Sources: https://www.rand.org/pubs/research_reports/RR1478.html (not opened)
    - Confidence: **high** on the arithmetic; **low** on the encounter rate.
    - Design impact: Measure the miss rate per *encounter class* (static head-level, drop-off, moving cyclist, and so on), since a pooled rate hides the rare dangerous classes. Encounters within one walk are correlated, so count across many routes and users. Supplement real data with replayed and synthetic scenario instances (L1/L2), and say plainly that they do not substitute for L4/L5. Do **not** claim any miss rate below what the encounter count supports.

12. **The real-world baseline risk is non-trivial, which both justifies the work and limits our claims.** In Manduchi & Kurniawan's survey of about 300 blind and legally blind people, 13% reported head-level accidents at least once a month, and 7% reported falls while walking at least once a month **[read]**. Legally blind respondents were about twice as likely as blind respondents to have frequent head-level accidents (18% vs 9%) **[read]**. 55% used a long cane only **[read]**.
    - Source: Manduchi & Kurniawan, "Mobility-related accidents experienced by people with visual impairment", https://users.soe.ucsc.edu/~manduchi/papers/MobilityAccidents.pdf (opened)
    - Confidence: **high** for the survey numbers (self-report).
    - Design impact: Prioritise **head-level obstacles** (which the cane misses) and **drop-offs** (falls). Those are the hazards where we add to the cane rather than duplicate it. Ground-level obstacles the cane already finds should get *lower* alert priority, to protect PPV.

13. **A credible safety case states its claims narrowly and attaches evidence to each one.** Waymo's safety-case paper treats the safety case as a structured argument with evidence and defines credibility criteria for it **[read: abstract]**. AMLAS structures ML assurance as explicit argument patterns linked to evidence **[read]**.
    - Sources: https://arxiv.org/abs/2306.01917 (abstract opened) ; https://arxiv.org/abs/2102.01564 (opened)
    - Confidence: **high**.
    - Design impact: Keep the safety case below as a living file. Every claim cites its current evidence rung (L0-L5), and anything at L4/L5 stays `needs_hardware` until collected.

14. **The vendor link imposes its own degraded states, and we must model them.** The DAT stream states are `waitingForDevice`, `starting`, `streaming`, `paused`, `stopping` and `stopped` **[read]**. A stopped session cannot be restarted, so a new camera is needed **[read]**. If the user declines Wi-Fi or local-network access, the iOS integration continues "over Bluetooth LE, but without streaming" **[read]**. Activating HFP (microphone) drops audio output to mono 16/8 kHz **[read]**.
    - Sources: https://wearables.developer.meta.com/docs/develop/dat/streaming/ ; https://wearables.developer.meta.com/docs/develop/dat/build-integration-ios/ ; https://wearables.developer.meta.com/docs/develop/dat/microphones-and-speakers/ (opened)
    - Confidence: **high**.
    - Design impact: Map each SDK state to a degraded mode (table below). Avoid HFP during navigation (use in-stream audio for mic input), so earcon quality is not degraded. On iOS, check whether streaming runs over Wi-Fi or BT. The docs point to Wi-Fi streaming on iOS, which conflicts with the brief's "BT Classic" assumption, so verify this.

15. **Human reaction plus stopping is the binding time budget.** File 01 (Findings 13-14) estimates 0.4-1.0 s of pipeline latency plus 0.5-1.5 s of human reaction, plus about one step to stop **[est]**. Gait-termination evidence suggests that one-step stops need the cue early in the gait cycle **[read via snippet]**.
    - Source: https://pmc.ncbi.nlm.nih.gov/articles/PMC9953996/ (opened)
    - Confidence: **medium**.
    - Design impact: The safety requirement should be stated at the **ear**: "a cue for a static head-level obstacle in the corridor reaches the ear at least 2.5 s before contact at 1.4 m/s" **[est target]**. It should not be stated as "TTC < 2 s at frame time".

---

## Draft lightweight safety case (v0, 2026-11-11)

### Top claim (C0)

> GuideGlass, operated within its stated ODD, by a trained user who **always** also uses a long cane or guide dog, provides supplementary audio cues about head-level obstacles, drop-offs and approaching objects. It does not increase the user's risk of collision or fall compared with cane or dog alone. Whenever it cannot provide those cues, it tells the user. It is a research prototype for supervised study. It is **not** a mobility aid, and it does **not** replace the cane, the dog or orientation-and-mobility training.

Sub-claims:
- **C1 (no harmful interference):** Cues do not mask important environmental sound or distract the user into hazards. This covers open-ear audio, cue rate limits and loudness.
- **C2 (benefit where claimed):** For in-ODD head-level static obstacles and drop-offs, cues arrive early enough to act on, at the stated miss rate and PPV.
- **C3 (no false reassurance):** Silence never means "clear". Every loss or degradation of perception is announced within the FTTI.
- **C4 (honest limits):** The user is trained on the ODD, the limits and the meaning of each cue. Out-of-ODD operation is announced.
- **C5 (no worse than baseline):** In supervised trials, the contact and fall rate with GuideGlass active is not higher than with GuideGlass silent.

### Losses and hazards

- **L1** Injury to the user (collision, fall, traffic). **L2** Injury to others. **L3** Loss of user trust or independence (abandoning a useful aid, or over-relying on it). **L4** Privacy harm to bystanders (camera).
- **H1** The user approaches a hazard without a timely cue (L1).
- **H2** The user takes an unsafe action because of a cue: a veer into the road, a sudden stop on stairs, a step back into a cyclist (L1, L2).
- **H3** The user believes the area is clear while perception is degraded or absent (L1).
- **H4** The user's attention or hearing of the environment is degraded by audio output (L1).
- **H5** The user learns to ignore cues (low PPV), so true cues go unheeded (L1, L3).
- **H6** The user reduces reliance on the cane or dog (L1, L3).

### Unsafe control actions, causal scenarios and requirements

Controllers: **S** = GuideGlass software (cue = control action); **U** = user (stop, veer, continue). UCA types follow the STPA Handbook: N = not provided, P = provided unsafe, T = wrong timing, D = wrong duration.

| # | UCA (type) | Hazard | Causal / loss scenario | Mitigation / requirement |
|---|---|---|---|---|
| 1 | S does not cue a static head-level obstacle in the corridor (N) | H1 | Detector trained on ground-level classes; overhang out of the vertical FOV at close range; depth fails on thin poles or branches | R1: dedicated head-level class and a corridor-height check; replay test set of overhangs (L2); log misses by class |
| 2 | S does not cue a drop-off, curb or descending stair (N) | H1 | Box detector cannot represent "missing ground"; ground-plane fit fails on low texture or at night | R2: ground-continuity monitor in the corridor; an "uncertain ground" cue when the ground is unobserved; ODD excludes unmarked drop-offs at night |
| 3 | S cues too late (T) | H1 | Pipeline latency (BT plus decode plus inference plus TTS plus A2DP) not compensated; trigger computed at frame time | R3: timestamp every stage; predict TTC to cue-at-ear time; urgent cues are earcons (not TTS); end-to-end latency p95 <= 700 ms **[est target]** measured at L4 |
| 4 | S stays silent while frames are stale or frozen (N) | H3 | SDK reports `streaming` but frames are frozen or HEVC-corrupt; decode thread stalled; phone thermal throttling | R4: frame-freshness watchdog (content hash plus timestamp age); announce "camera lost" within 0.5 s **[est]**; heartbeat absence is audible |
| 5 | S stays silent after the session pauses or stops (N) | H3 | `paused` or `stopped` state not handled; a stopped session cannot restart | R5: map each SDK state to a degraded mode with an announcement; a reconnect needs an explicit "protection resumed" cue |
| 6 | S cues a hazard that is not there (P) | H5 | Box jitter giving two-frame tau noise; depth hallucinating on reflections or glass; parked objects off the corridor | R6: confirm tracks over time (seconds, not frames); require TTC confidence bounds; corridor gating; PPV of urgent cues >= 80% in ODD **[est target]**, reported per walking hour |
| 7 | S gives a directional cue that leads the user into a worse hazard (P) | H2 | "Go left" when left is the road or a cyclist lane; free-space estimate ignores moving objects | R7: the reflex never issues steering commands, only hazard location and "stop"; route guidance stays outside the safety path |
| 8 | S cues "stop" while the user is on stairs or in a crossing (P/T) | H2 | Sudden-stop cue mid-stair or mid-crossing leads to a fall or traffic exposure | R8: context suppression of "stop" in detected stair and crossing contexts, replaced by a hazard-location cue; to be validated at L5 with O&M specialists |
| 9 | S keeps cueing after the hazard has passed or the user stopped (D) | H4, H5 | Track not deleted; static TTC computed with stale ego-speed when the user is stopped | R9: tracks expire by time; ego-speed from steps; cues stop when the user stops or turns away |
| 10 | S floods audio (several cues, long TTS) (D) | H4 | Many low-priority detections in a crowd; TTS queue backs up | R10: priority arbitration; at most 1 urgent cue in flight; rate limit on non-urgent speech; urgent cue pre-empts TTS |
| 11 | S misses during a fast head turn (N) | H1 | 100+ deg/s yaw causes motion blur and association failures | R11: during high yaw, suppress new alerts but keep confirmed tracks (rotated by the gyro); announce nothing unless the condition lasts more than 1 s **[est]**; log yaw with every miss |
| 12 | S misses at night, in glare or in rain (N) | H1 | Depth and detection degrade at night (md4all); lens droplets | R12: ODD monitor using image brightness and noise; announce "low light, reduced detection"; night outside ODD for v1 |
| 13 | S treats a moving hazard as static (P/T) | H1 | Range/speed TTC used for a cyclist approaching from ahead gives TTC far too long | R13: tau channel for tracks with independent motion; TTC = min over channels with confidence |
| 14 | U ignores a true cue (U-N) | H5 | Prior false alarms (cry wolf); cue indistinguishable from non-urgent speech | R14: distinct urgent earcon; graded cues; PPV monitoring; onboarding training |
| 15 | U relies on GuideGlass and leaves the cane at home (U-P) | H6 | Over-trust after a run of good experience | R15: study protocol requires a cane or dog; onboarding script; periodic reminder; the safety case claim is conditional on it |
| 16 | U cannot hear the cue (U-N) | H1 | Traffic noise; HFP mono downgrade; phone volume low; BT audio routed elsewhere | R16: avoid HFP during navigation; check the audio route at start and announce it; volume floor for urgent earcons |

### Draft ODD (v0)

**In:**
- Scenery: daytime outdoor urban and suburban sidewalks and pedestrian plazas; indoor corridors with ordinary lighting; known, previously walked routes for L5.
- Environment: ambient light within the camera's usable range (daylight, well-lit indoor); dry; no fog.
- Dynamic elements: sparse to moderate pedestrian density; parked vehicles; slow cyclists.
- User: trained participant, walking at 0.6-1.6 m/s, with a long cane or guide dog, glasses worn normally.
- System: stream at >= 7 fps effective, frame age < 300 ms, phone not thermally throttled, battery > 20%, A2DP audio route to the glasses.

**Out (announced if detected; otherwise a documented residual risk):**
- Night or low light, heavy rain, snow, fog, direct low sun into the lens.
- Road crossings (no traffic-signal or crossing guidance; the user crosses using their own skills).
- Stairs and escalators as a navigated feature (best-effort warnings only, no claim).
- Dense crowds, running, cycling, any vehicle use.
- Unfamiliar routes without supervision during the study.
- Effective fps < 7, or the camera stream not streaming.

### Degraded modes and what the user hears

| Mode | Entry condition | What the user hears | Exit |
|---|---|---|---|
| D0 Normal | All ODD checks pass | Hazard cues only; optional quiet heartbeat | n/a |
| D1 Reduced rate | Effective fps 2-7, or frame age 300-1000 ms | Once: "Reduced detection" (short spoken phrase) plus a distinct low tone; urgent cues only for confirmed tracks | 5 s back in D0 conditions, then "Detection normal" |
| D2 Head-turn blind | Yaw > ~60 deg/s for > 1 s **[est]** | Nothing at first; if it lasts > 2 s, a soft tick | Yaw drops |
| D3 Low light / out of ODD | Brightness or noise checks fail | "Low light. Use cane only." Repeated every 60 s while it lasts | Checks pass, then "Detection normal" |
| D4 Camera lost | Frame age > 1 s, stream `paused`/`stopped`, or frozen frames detected | Distinct descending earcon plus "Camera lost. Use cane only." Repeated every 30 s | A fresh stream for 3 s, then "Camera back" |
| D5 Phone degraded | Thermal throttling or battery < 20% | "Phone hot, reduced detection" or "Battery low" | Condition clears |
| D6 Audio route problem | A2DP route lost, or HFP active | Phone speaker or vibration fallback: "Glasses audio lost" | Route restored |
| D7 App killed | OS kills the app or the session ends | The heartbeat stops (if enabled); the phone OS notification stays | User restarts |

D7 cannot announce itself. That is the main argument for an audible heartbeat, or for teaching users that the start and stop chimes bracket protection.

### Evidence needed per claim and rung

| Claim | L0 assumed/read | L1 simulated | L2 replayed real footage | L3 emulator / mock device | L4 real device + glasses | L5 supervised BLV users |
|---|---|---|---|---|---|---|
| C2 head-level and drop-off detection | Literature on depth and detection limits (file 01) | Synthetic approach scenarios: miss and false-alarm rate vs fps, latency, yaw | Miss rate per encounter class on SANPO plus own annotated footage, with encounter counts (rule of three) | Mock Device Kit replay through the real app path; timing logs | Sighted-tester walks in shadow mode; measured end-to-end latency p50/p95 | Contacts per 100 hazards per hour, active vs silent epochs, masked review |
| C2 timeliness (cue at ear) | Reaction and gait literature | Latency-compensated TTC simulation | Replay with recorded timestamps | Measured app-internal latency | Measured capture-to-audio latency with a photodiode or clap test | Observed reaction and stopping distances |
| C3 no false reassurance | SDK state docs | Fault injection: dropped and frozen frames, pauses | Replay with injected dropouts | Mock device pause/stop/freeze tests | Pull the BT link, cover the lens, force thermal throttling | User comprehension of degraded cues (interview plus behaviour) |
| C1 no harmful interference | Open-ear audio literature (to do) | Cue-rate simulation in crowd scenarios | Cue rate per hour on real footage | n/a | Cue rate and loudness measured outdoors | User-reported distraction; O&M specialist review |
| C5 no worse than baseline | Pundlik RCT design | n/a | n/a | n/a | n/a | Within-subject active vs silent, with a cane or dog always present, under ethics approval |
| C4 honest limits | ODD draft | ODD monitor unit tests | ODD monitor accuracy on footage (light, fps) | Monitor behaviour on mock streams | Monitor triggers on real walks | Training-comprehension check |

Current status (as of this note): almost everything is at L0, with L1 for the old reflex only (2.0% false alarms, 47.4% misses in its own synthetic simulation). Those L1 numbers are per-sample rates in a simulation, not per-encounter or per-hour rates. They must not be quoted as real-world performance.

## Disagreements between sources

- **The streaming link.** Meta's iOS integration guide says camera streaming runs over the glasses' Wi-Fi hotspot, with BT LE for control. The streaming page says resolution and fps are "constrained by the Bluetooth Classic connection". The brief assumes BT Classic. The transport may differ by platform or version; verify at L4.
- **What a fallback means.** In AV standards the minimal-risk condition is a physical state (vehicle stopped). For us, the equivalent is an informational state (the user knows protection is off). AV literature gives no direct template for this.
- **Takeover-time transfer.** Driver takeover times (seconds, a complex task) are not the same as a walker's stop reaction (sub-second to about 1.5 s, a simple task). I treat the takeover literature as qualitative only.

## Open questions

1. The real hazardous-encounter rate per walking hour on study routes, by class. This sets how many hours each claim needs.
2. Whether a heartbeat cue is acceptable to BLV users or adds to audio clutter. This needs L5 co-design.
3. How to annotate "hazardous encounters" consistently (definition, inter-rater agreement), as Pundlik et al. did with masked reviewers.
4. Ethics and supervision protocol for L5, including bystander privacy (L4 loss).
5. The full texts of ISO 21448, 26262, 34502 and 34503, PAS 1883, J3016, Kalra & Paddock, Zhang et al. and Bliss et al. were not opened. Their summaries here are second-hand.

## Sources

Opened:
- Leveson & Thomas, STPA Handbook (2018), https://psas.scripts.mit.edu/home/get_file.php?name=STPA_handbook.pdf
- Abdulkhaleq et al., STPA for automotive, https://arxiv.org/abs/1703.03657
- Hawkins et al., AMLAS, https://arxiv.org/abs/2102.01564
- Favaro et al., Waymo safety case approach, https://arxiv.org/abs/2306.01917 (abstract)
- FHWA SSAM (surrogate safety measures), https://www.fhwa.dot.gov/publications/research/safety/08051/
- ASAM OpenSCENARIO XML, https://www.asam.net/standards/detail/openscenario-xml/
- Manduchi & Kurniawan, Mobility-related accidents of people with visual impairment, https://users.soe.ucsc.edu/~manduchi/papers/MobilityAccidents.pdf
- Koo & Kwon 2023, unplanned gait termination, https://pmc.ncbi.nlm.nih.gov/articles/PMC9953996/
- Meta Wearables DAT docs: streaming, iOS integration, microphones and speakers, https://wearables.developer.meta.com/docs/develop/dat/

Not opened (blocked, paywalled, or seen only via search snippets):
- ISO 21448 (SOTIF), https://www.iso.org/standard/77490.html
- ISO 26262, https://www.iso.org/standard/68383.html
- ISO 34502, https://www.iso.org/standard/78951.html
- ISO 34503, https://www.iso.org/standard/78952.html
- SAE J3016, https://www.sae.org/standards/content/j3016_202104/
- BSI PAS 1883 (ODD taxonomy)
- PEGASUS project method
- Kalra & Paddock, Driving to Safety (RAND RR1478), https://www.rand.org/pubs/research_reports/RR1478.html
- Zhang et al. 2019, take-over time meta-analysis, https://research.tudelft.nl/en/publications/determinants-of-take-over-time-from-automated-driving-a-meta-anal
- Bliss, Gilson & Deaton 1995, Ergonomics 38(11), https://stars.library.ucf.edu/scopus1990/1980
- Manzey et al. 2014, https://informahealthcare.com/doi/abs/10.1080/00140139.2014.957732
- Pundlik et al. 2021 JAMA Ophthalmology RCT, doi:10.1001/jamaophthalmol.2021.2624
