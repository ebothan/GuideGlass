# 03 — Cue Design and Human Factors for GuideGlass

Research date: 2026-11-11. Status: desk research, partly verified. Every number is tagged **[read]** (taken from a source I opened) or **[est]** (my own estimate or derivation). Sources I could not open are marked "not opened" and are not relied on for numbers.

## Question

How should cues be designed for a blind traveler so they arrive in time, are understood at once, and do not mask the environment?

## Findings

1. **How fast a warning sound gets a response depends on its temporal structure, but only by tens of milliseconds. Workload matters much more.** Suied, Susini & McAdams (2008) used 20 ms, 1 kHz pulses. Simple reaction time (RT) was 297 ms with a 100 ms inter-onset interval (IOI) and 307 ms with a 300 ms IOI when participants were only monitoring [read]. Under a concurrent tracking task the figures were 396 ms and 406 ms [read]. A faster IOI gained about 10 ms [read], while divided attention cost about 100 ms [read]. Listeners rated the 100 ms IOI as more urgent (0.7 vs 0.2) [read]. The paper notes that an earlier study using 350 ms pulses found a mean RT of about 450 ms and no IOI effect, because the rhythm had not been heard by the time the listener reacted [read].
   - Sources: https://www.mcgill.ca/mpcl/files/mpcl/suied_2008_jepappl.pdf
   - Confidence: high. Primary paper read; lab setting with sighted participants.
   - Design impact: Build the urgent earcon from short pulses (20–75 ms) at a fast IOI (about 100 ms) so its urgency is audible within the first 200–300 ms. Budget simple RT at about 300 ms and about 400 ms for a walker who is busy [read, applied]. Do not count on the sound design to make up for a late trigger.

2. **Urgency can be engineered predictably through pulse speed, fundamental frequency, repetition and inharmonicity, and speed is the strongest lever.** Edworthy, Loxley & Dennis (1991) showed that fundamental frequency, harmonic series, envelope, speed, rhythm, pitch range and melodic structure all have consistent effects on perceived urgency [read, abstract]. Hellier, Edworthy & Dennis (1993) scaled speed, F0, repetition units and inharmonicity with Stevens' power law and found that some parameters contribute more than others [read, abstract]. The secondary search summary says rate is the most efficient parameter (not verified in the full text).
   - Sources: https://pubmed.ncbi.nlm.nih.gov/1860703/ ; https://pubmed.ncbi.nlm.nih.gov/8163282/
   - Confidence: high for direction, medium for exponents (full text not opened).
   - Design impact: Encode the three priority tiers mainly by pulse rate and repetition, keeping pitch fixed. Pitch is better saved for spatial or semantic encoding, and high F0 carries badly through small open-ear speakers.

3. **Auditory icons and speech can produce faster responses than abstract tones, but icons cause more wrong responses.** Graham (1999) compared a car horn and skidding tyres against a simple tone and the spoken word "ahead". The icons gave significantly faster braking, but they also caused more inappropriate brake presses in non-collision scenes [read, abstract]. FHWA traveler-information guidance says speech should not be used for time-critical tasks (search snippet only; page not opened).
   - Sources: https://pubmed.ncbi.nlm.nih.gov/10503056/ ; FHWA https://www.fhwa.dot.gov/publications/research/safety/03065/06.cfm (not opened)
   - Confidence: medium. Driving context; the sources disagree on speech (see Disagreements).
   - Design impact: For "imminent collision", use a unique non-speech earcon (or icon) as the first ~150 ms, followed by a one-syllable word such as "Stop". The earcon wins on speed and the word removes ambiguity. Never reuse the stop earcon for anything else.

4. **The aviation precedent is strict pre-emption: the higher-priority aural alert inhibits the lower one rather than queuing it.** The FAA TCAS II v7.1 booklet says the aural priority scheme gives windshear and GPWS higher priority than TCAS. TCAS aurals are *inhibited* while a windshear or GPWS alert is active, and they are also inhibited below 500±100 ft AGL [read]. TCAS v7.0 was revised specifically to reduce unnecessary RAs because nuisance alerts were harming the system [read].
   - Sources: https://www.faa.gov/documentlibrary/media/advisory_circular/tcas%20ii%20v7.1%20intro%20booklet.pdf
   - Confidence: high. Official FAA document opened.
   - Design impact: Use a fixed, static priority table. A higher-priority cue cuts off any lower cue mid-utterance, and the lower cue is dropped rather than replayed unless it is still valid afterwards. Add context inhibits, for example route chatter is suppressed while a hazard is active or while the user is mid-crossing.

5. **Medical alarm practice (IEC 60601-1-8) encodes priority through burst structure and pulse rhythm, and it allows voice as an alternative.** In a commentary on the standard, a HIGH priority burst is 10 pulses (two groups of 5 with a pause), with MEDIUM and LOW using fewer, slower pulses. The HIGH signal is designed to be "very different from most other sounds", and voice synthesis is allowed as alternative technology if it is priority-encoded and validated [read; commentary page, not the standard text]. A vendor summary of AMD2:2020 gives pulse durations of high 25–75 ms, medium 90–200 ms and low 400–600 ms (search snippet; not opened). Search results gave conflicting descriptions of the high-priority burst between editions.
   - Sources: https://standards.har-el.com/Projects/181701/60601-1-8/html/A2SubCl-6-3-3-1-a-priority-encoded.htm ; https://puiaudio.com/news/key-updates-to-iec-60601-1-8-what-engineers-need-to-know (not opened)
   - Confidence: medium. Standard text is paywalled and was not opened.
   - Design impact: Use three tiers with distinct rhythms. The HIGH tier should be a pattern that appears nowhere else in the app and nowhere common in the street, such as a car-horn-like or phone-ring-like pattern.

6. **False alarms make users stop responding. People match their response rate to the alarm's reliability.** Bliss, Gilson & Deaton (1995, N=138) presented alarms at 25/50/75% true-alarm rates. About 90% of subjects matched their response frequency to the expected probability of a true alarm, and about 10% responded all-or-none [read, abstract]. In a forward collision warning (FCW) study, undistracted drivers stopped responding to false warnings after only a few exposures (TRID abstract via search; not opened). Naujoks et al. (2016, N=80) found that *false* alarms reduced compliance, while *unnecessary* alarms (real but not needed) did not (search snippet; not opened).
   - Sources: https://pubmed.ncbi.nlm.nih.gov/7498189/ ; https://trid.trb.org/view/709838 (not opened) ; https://www.psychologie.uni-freiburg.de/Members/kiesel/Publications/Naujoks-Kiesel-Neukum-2016.pdf/at_download/file (not opened)
   - Confidence: high for probability matching; medium for the driving results.
   - Design impact: If only half of "Stop" cues are real, expect users to act on about half. Track precision for the STOP class as a release gate, with a target of at least 90% true [est, design target]. Demote uncertain detections to the CAUTION tier rather than issuing STOP. An "unnecessary but explainable" alert (a real object you would have avoided anyway) does far less harm than a phantom.

7. **Alarm fatigue in hospitals shows what happens when nuisance alarms dominate.** Drew et al. (2014) recorded 2,558,760 alarms in 31 days across 461 ICU beds, an audible burden of 187 per bed per day, and 88.8% of 12,671 annotated arrhythmia alarms were false positives [read].
   - Sources: https://pmc.ncbi.nlm.nih.gov/articles/PMC4206416/
   - Confidence: high. Primary data.
   - Design impact: Log alerts per walking-minute in every field trial and set a budget, for example no more than 1 STOP per 10 minutes and no more than 1 CAUTION per minute in ordinary sidewalk travel [est]. If the budget is exceeded, raise thresholds rather than adding more sounds.

8. **Automotive FCW practice uses staged warnings and timing derived from driver reaction plus braking, not a fixed time-to-contact (TTC).** The CAMP/NHTSA FCW work modeled alert range from brake reaction time plus "hard" braking behavior and noted that this differs from headway/TTC criteria (search snippet). An NHTSA test protocol used a nominal TTC of 2.1 s (snippet). ISO 15623 (FCW performance) and NHTSA DOT HS 812 360 (Human Factors Design Guidance) were not opened: ISO is paywalled and NHTSA returned HTTP 403.
   - Sources: https://rosap.ntl.bts.gov/view/dot/34015/dot_34015_DS1.pdf (not opened, redirect) ; https://www.nhtsa.gov/sites/nhtsa.gov/files/documents/812360_humanfactorsdesignguidance.pdf (not opened, 403) ; ISO 15623:2013 (not opened)
   - Confidence: low for specific numbers; medium for the "stage + RT-based" principle.
   - Design impact: Compute the trigger as *distance needed = speed × (system latency + utterance-to-recognition + RT) + stopping distance*. Do not use a fixed 2.0 s TTC; see finding 12. Use two stages, CAUTION and then STOP.

9. **A walking person needs reaction time plus one to three steps to stop, and older adults need more.** In Menant et al. (2009; 10 young, 26 older adults), participants stopped "as quickly as possible" to an *audible cue*. Older subjects took longer time and distance to last foot contact and more often used a "three or more steps" strategy [read, abstract]. Tirosh & Sparrow (2005) found older men took two steps to stop in 46% of trials versus 20% for young men, and their stance-leg EMG response came at 215 ms versus 176 ms (visual cue) [read, abstract]. Wet surfaces lengthened stopping time and distance [read].
   - Sources: https://pubmed.ncbi.nlm.nih.gov/19359178/ ; https://pubmed.ncbi.nlm.nih.gov/15760743/ ; https://pubmed.ncbi.nlm.nih.gov/30852468/
   - Confidence: medium. Small samples, sighted participants, lab walkway.
   - Design impact: Model stopping as RT plus *n* steps, with n = 1–2 for young adults and 2–3+ for older adults or on wet surfaces. With a step length of about 0.6–0.7 m [est], stopping adds 0.7–2.0 m on top of the RT travel. Add a per-user "older/cautious" profile that lengthens lead distance.

10. **Speech rate: blind screen-reader users listen far faster than default TTS, but that is for reading, not for alarms in traffic.** Bragg et al. give a typical human speaking rate of 120–180 wpm and note that experienced screen-reader users set voices up to about 500 wpm [read]. Their crowdsourced study (N≈1,409) found a mean maximum intelligible rate of about 298 wpm, increasing with years of screen-reader use (search snippet of the same paper). Guerreiro & Gonçalves (2015) found two concurrent voices at 1.75× (~278 wpm) workable for scanning, with performance falling with age above 2.5× (search snippet; not opened).
   - Sources: https://homes.cs.washington.edu/~dkbragg/papers/Expanding_a_Large_Inclusive_Study.pdf ; Guerreiro & Gonçalves 2015 (not opened)
   - Confidence: medium. Rates were measured seated and in quiet, not while walking in traffic.
   - Design impact: Make the speech rate a user setting, defaulting to about 200–250 wpm for route cues and capped for safety cues at about 220 wpm [est]. Comprehension of an unexpected word in street noise matters more than speed. Never play our speech concurrently with the user's screen reader; duck or pause it.

11. **Utterance duration is a large, often ignored part of the timing budget.** Derived from 180–220 wpm (≈270–333 ms per word) plus about 100–200 ms of TTS start latency [all est]:
    - "Stop": ≈350–450 ms of audio. Recognition probably happens before the word ends (≈250–300 ms) [est].
    - "Curb down, two steps": ≈1.3–1.6 s [est].
    - "Turn left in ten meters": ≈1.4–1.8 s [est].
    - "Bike approaching from the left": ≈1.6–2.0 s [est].
    - Sources: rate from Bragg et al. above; durations are my estimates and should be measured on our actual TTS voice.
    - Confidence: medium (estimate).
    - Design impact: Treat the time to the *decisive word* as latency. Put the action first ("Stop", "Step down") and the qualifier after it. Pre-render safety words as audio clips (no TTS synthesis on the safety path) and measure each clip's duration in CI.

12. **The current working assumption (2.0 s TTC at 1.4 m/s) is tight for a stop command.** Worked budget [est]: camera-to-phone plus inference latency of 0.2–0.3 s, then earcon/"Stop" recognition of 0.3 s, then RT of 0.3–0.4 s, gives about 0.9 s, or 1.25 m at 1.4 m/s. Adding 1–2 steps of stopping (0.7–1.4 m) gives a total of about 2.0–2.7 m, against the 2.8 m that 2.0 s TTC provides at 1.4 m/s. Older users or wet ground would take about 3+ m. Many cane users also walk slower than 1.4 m/s (see 04, finding 15), which helps.
    - Sources: derived from findings 1, 9, 11
    - Confidence: medium (derivation from [read] parts).
    - Design impact: Express the trigger as a *distance* computed from measured walking speed, not a fixed TTC. Raise the STOP trigger to about 2.5 s or at least 3 m (whichever is larger) for moving objects, and issue a CAUTION at about 4–5 s [est]. Measure end-to-end latency on device.

13. **Take-over-request research supports multimodal (auditory or vibrotactile) degraded-state alerts and shows that urgency and prior experience speed responses.** Zhang et al. (2019) meta-analysed 129 studies. Shorter take-over time was associated with higher urgency, prior take-over experience, and auditory or vibrotactile requests compared with visual-only ones; no consistent age effect was found [read, abstract].
    - Sources: https://research.tudelft.nl/en/publications/determinants-of-take-over-time-from-automated-driving-a-meta-anal
    - Confidence: medium. Driving context; mean values in seconds were not extracted.
    - Design impact: When the camera, Bluetooth link or perception is lost, use a distinct "I can't see" earcon plus speech, and also a phone haptic pattern because the glasses speaker may be the thing that failed. Rehearse the degraded alert during onboarding so the first real one is not novel.

14. **Open-ear and bone-conduction audio keeps the ear canal open but has not been shown to leave traffic perception unaffected for blind pedestrians.** I found no controlled study of open-ear devices with blind pedestrians detecting traffic. A Virginia Tech ICAD 2022 VR study of sighted pedestrians compared air and bone conduction for detecting and localizing a siren (PDF fetched, but its full results were not extracted). Microsoft's Cities Unlocked bone-conduction trial reported a participant keeping the guide dog because the system could not tell them when traffic was coming (press, search snippet). The quiet-vehicle study (04, finding 7) shows blind pedestrians are already marginal at detecting some vehicles by ear.
    - Sources: https://icad2022.icad.org/wp-content/uploads/2022/06/ICAD2022_35.pdf (fetched, results not verified) ; https://newatlas.com/microsoft-bone-conduction-headset-for-blindess/34622/ (not opened)
    - Confidence: low. Evidence gap.
    - Design impact: Default to silence during travel, with no continuous beacons or ambient soundscape. Mute all non-safety audio while at a curb or crossing, where traffic listening is the primary task. Keep volume adaptive to ambient level but never loud enough to mask traffic. Run our own masking test as a study item.

15. **Orientation and mobility (O&M) and audio-wayfinding conventions favor short, egocentric, action-first instructions with landmarks, and clock-face directions for angles.** ITU-T F.921 (the Wayfindr Open Standard) covers how to give audio instructions to blind travelers (search snippet; standard PDF not opened). Kameswaran et al. (ASSETS 2020; 23 interviewees, 10 diary participants) found blind travelers *combine* apps, cane or dog, and other aids, each serving different information needs (snippet; not opened). The clock-face convention is visible in recent guidance-generation work, where cues such as "pedestrians... at 1 o'clock" were paired with an action such as "slow down and move slightly to your right" [read, from a fetched arXiv PDF on BLV guidance; title not recorded].
    - Sources: https://www.itu.int/hub/2020/06/how-the-wayfindr-open-standard-uses-new-tech-to-help-the-visually-impaired/ (not opened) ; https://www.microsoft.com/en-us/research/?p=677091 (not opened)
    - Confidence: medium. The convention is widely reported, but the primary standard text was not opened.
    - Design impact: Use egocentric phrasing. Use "left/right" for turns and "bear left/right" (not "veer", which O&M uses for unintended drift) for small corrections. Use clock directions (10–2 o'clock) only for hazards. Give distances in meters (user-switchable to steps or feet), rounded (5, 10, 20 m). Avoid "safe" and "clear".

16. **The cry-wolf effect applies equally to *silence*: if silence means "clear", every perception gap is a silent false negative.** This is a design inference from the brief (silence is read as "clear") and finding 6 [est].
    - Sources: brief; Bliss 1995 above
    - Confidence: medium.
    - Design impact: Use a sparse "alive" tick only during long cue-free stretches, for example a soft single tick every 20–30 s, configurable and off by default for experts [est]. A "confidence lost" cue must fire within 1 s of perception dropout [est]. Testing should settle whether users prefer a heartbeat or an explicit "lost" alert.

## Proposed cue vocabulary

Priority P0 is highest. "Interrupts" means the cue cuts any lower-priority audio immediately. Durations are **[est]** at about 200 wpm plus earcon, to be measured on the real voice.

| Cue class | Modality | Example wording / sound | Est. duration (ms) | Priority | May interrupt? |
|---|---|---|---|---|---|
| Imminent collision stop | Unique 10-pulse fast earcon (25–75 ms pulses, ~100 ms IOI) + word + strong phone haptic | [earcon] "Stop." | 150 earcon + 400 = ~550 [est] | P0 | Yes, everything, including system-degraded |
| Drop-off / stairs / curb down | Earcon (descending 2-tone) + speech | "Step down ahead." / "Stairs down, two meters." | 1,000–1,600 [est] | P1 (P0 if < 1.5 m and walking) | Yes, all except P0 |
| Hazard ahead caution (static, head-height) | Medium earcon (3 pulses) + speech with clock | "Head height, 12 o'clock." / "Pole, one o'clock." | 1,100–1,500 [est] | P1 | Yes, P2 and below |
| Moving object approaching | Spatialised earcon panned toward the source + speech | "Bike, left." / "Scooter behind." | 700–1,000 [est] | P1 | Yes, P2 and below |
| System degraded: camera lost | Distinct "lost" earcon (falling 3-note) + speech + haptic double pulse | "Camera off. Use your cane." | 1,500 [est] | P1 | Yes, P2 and below; repeat every 30 s while degraded |
| System degraded: Bluetooth lost | Phone speaker + haptic (glasses are unreachable) | "Glasses disconnected." | 1,200 [est] | P1 | Yes |
| System degraded: low confidence | Soft earcon + speech | "I can't see well here." | 1,200 [est] | P2 | Yes, P3 only |
| System degraded: network lost | Speech only, once | "Offline. Hazard alerts still on." | 1,700 [est] | P3 | No; queued |
| System recovered | Rising 2-note earcon, optional speech | "Camera back." | 300–800 [est] | P3 | No |
| Veer correction | Short soft tick panned to the correction side, or speech | "Bear right." | 150 tick / 600 speech [est] | P2 | No; dropped if stale > 1 s |
| Turn pre-announce | Speech | "In 20 meters, turn left." | 1,600 [est] | P3 | No; queued, discarded if superseded |
| Turn now | Short chime + speech | "Turn left now." | 900 [est] | P2 | Interrupts P3 only |
| Arrival | Pleasant 3-note chime + speech | "Arriving. Entrance on your right." | 2,000 [est] | P3 | No |
| Alive heartbeat (optional) | Single soft tick | — | 50 [est] | P4 | Never; suppressed while any other cue plays |

Rules:
- A higher priority pre-empts a lower one, and the lower cue is discarded rather than resumed if its trigger condition has expired.
- Do not repeat the same hazard within about 5 s unless the distance has halved [est].
- Route cues (P2–P4) are suppressed while a P0–P1 condition is active and while the user is crossing a street.

## Disagreements between sources

- **Speech versus non-speech for urgent cues.** Graham (1999) found auditory icons faster than speech or tone, but with more wrong responses. An intersection-warning study (search snippet only) found speech with direction faster than a beep. FHWA guidance (not opened) says to avoid speech for time-critical tasks. This supports a hybrid: earcon first, then word.
- **Does cry-wolf always appear?** The driving studies show compliance loss after a few false alerts. Wickens et al. (2009, snippet) found no cry-wolf effect for high false-alarm-rate air traffic conflict alerts. Naujoks (2016) found it only with visual-auditory warnings and not for "unnecessary" alarms.
- **IEC 60601-1-8 burst description.** Search results gave conflicting HIGH-burst descriptions between the 2006 and 2020 editions. The commentary I read says 10 pulses in 2×5.
- **TTC thresholds.** NHTSA testing used about 2.1 s (snippet), while an older-driver study suggested 4 s (snippet), and CAMP rejects pure TTC criteria altogether.

## Open questions

- How much does an open-ear speaker on Meta glasses, at a cue-audible level, reduce detection and localization of approaching vehicles for blind users? This needs our own test.
- What is the RT and stopping distance for blind cane users (as opposed to sighted lab walkers) given an auditory stop command while walking? No study was found.
- Do users prefer a heartbeat or an explicit "lost" alert? What heartbeat interval is tolerable over a 30-minute walk?
- What actual on-device latency do we get from frame capture through inference to audio onset, and from TTS onset? It needs measuring.
- The ISO 15623 and NHTSA 812 360 text was not opened; their specific timing and loudness values should be checked.

## Sources

Opened:
- Suied, Susini & McAdams (2008), J Exp Psychol Appl. https://www.mcgill.ca/mpcl/files/mpcl/suied_2008_jepappl.pdf
- Edworthy, Loxley & Dennis (1991), Hum Factors, abstract. https://pubmed.ncbi.nlm.nih.gov/1860703/
- Hellier, Edworthy & Dennis (1993), Hum Factors, abstract. https://pubmed.ncbi.nlm.nih.gov/8163282/
- Graham (1999), Ergonomics, abstract. https://pubmed.ncbi.nlm.nih.gov/10503056/
- Bliss, Gilson & Deaton (1995), Ergonomics, abstract. https://pubmed.ncbi.nlm.nih.gov/7498189/
- FAA, Introduction to TCAS II Version 7.1. https://www.faa.gov/documentlibrary/media/advisory_circular/tcas%20ii%20v7.1%20intro%20booklet.pdf
- IEC 60601-1-8 commentary (Har-El). https://standards.har-el.com/Projects/181701/60601-1-8/html/A2SubCl-6-3-3-1-a-priority-encoded.htm
- Drew et al. (2014), PLoS ONE, ICU alarms. https://pmc.ncbi.nlm.nih.gov/articles/PMC4206416/
- Menant et al. (2009), Gait Posture, abstract. https://pubmed.ncbi.nlm.nih.gov/19359178/
- Tirosh & Sparrow (2005), Gait Posture, abstract. https://pubmed.ncbi.nlm.nih.gov/15760743/
- Kasahara & Saito (2019), Hum Mov Sci, abstract. https://pubmed.ncbi.nlm.nih.gov/30852468/
- Bragg et al., Expanding a Large Inclusive Study of Human Listening Rates. https://homes.cs.washington.edu/~dkbragg/papers/Expanding_a_Large_Inclusive_Study.pdf
- Zhang et al. (2019), take-over time meta-analysis, abstract. https://research.tudelft.nl/en/publications/determinants-of-take-over-time-from-automated-driving-a-meta-anal

Not opened (cited only as pointers):
- NHTSA DOT HS 812 360 (403)
- NHTSA CWS HF insights report (403)
- ISO 15623:2013
- IEC 60601-1-8:2006+AMD2:2020 full text
- FHWA 03065 chapter 6
- Naujoks et al. 2016
- TRID 709838
- Guerreiro & Gonçalves 2015
- Wayfindr / ITU-T F.921 PDF
- Kameswaran et al. 2020
- ICAD 2022 paper 35 (fetched; results not verified)
