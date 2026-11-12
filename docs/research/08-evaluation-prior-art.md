# 08 - Evaluation without people or hardware, and prior art

Research date: 2026-11-11. Tags:

- **[read]**: the number comes from the cited source.
- **[est]**: our own estimate or arithmetic.
- **"not opened"**: we only saw a search snippet, mirror, or press piece.

Every number here is evidence rung L0 (assumed/read) or, for the statistics, our arithmetic. Nothing here is replayed or tested.

## Question

How do we evaluate GuideGlass without people and hardware, and what does prior art tell us?

## Short answer

1. **Replay SANPO first.**
   - Google's real outdoor pedestrian footage: chest- and head-mounted ZED stereo cameras, dense depth, panoptic segmentation, and per-session hazard metadata.
   - Licence is CC BY 4.0. The files sit in a public bucket, so single frames can be fetched over plain HTTPS without registration (we checked this).
2. **Then build a closed-loop walker simulator** around replayed or synthetic scenes, with injected latency and dropouts.
3. **Size the test set before claiming results:**
   - About 59 hazard events with zero misses to claim a miss rate under 5% (one-sided 95%).
   - About 120 hazard events per arm to show a drop from 47.4% to 30%.
4. **Prior art:**
   - Validated on-device systems put end-to-end obstacle latency in the hundreds of milliseconds.
   - VLM systems are seconds slow and over-alert.
   - BLV users consistently complain about verbosity, distance errors, and not being able to verify answers.

## Findings

### Part A - Datasets

1. **SANPO (Google, WACV 2025) is the best fit for obstacle replay and 1-10 m depth.**
   - Content **[read]**: 701 real stereo videos of 30+ s each, plus 1,961 synthetic videos. Every frame has depth, and 112K real frames have temporally consistent panoptic segmentation.
   - Licence: "Creative Commons V4.0" (CC BY 4.0) **[read]**.
   - Download: bucket `gs://gresearch/sanpo_dataset/v0`, about 6 TB in total, with rsync exclude patterns for partial downloads **[read]**.
   - Our own checks of one session (`-5OCPnbrwJdu3jH70ieU7pUiFsOJQoeG`), all anonymous HTTPS with no login:
     - Layout: `camera_chest/` and `camera_head/`, each with `left/video_frames/*.png` (~3.5 MB per frame), `depth_maps/*.float16.gz` (~0.6 MB per frame), `zed_depth_maps/`, `segmentation_masks/`, and `camera_poses.csv` **[read, listed via the storage JSON API]**.
     - `description.json` gives a ZED 2i at 15 fps and 2208×1242. It also carries hazard-relevant tags such as human/vehicular traffic, number of obstacles, environment (urban, road junction), elevation changes, and motion blur **[read]**.
     - `https://storage.googleapis.com/gresearch/sanpo_dataset/v0/labelmap.json` returned HTTP 200 **[read]**.
   - (b) https://google-research-datasets.github.io/sanpo_dataset/ ; https://github.com/google-research-datasets/sanpo_dataset ; https://research.google/blog/sanpo-a-scene-understanding-accessibility-navigation-pathfinding-obstacle-avoidance-dataset/ ; https://arxiv.org/pdf/2309.12172 (not opened)
   - (c) High. We read the licence and fetched files ourselves.
   - **Design impact:**
     - SANPO becomes our primary L2 replay source for:
       - (i) obstacle/hazard replay, by deriving ground truth from panoptic masks plus depth inside a walking corridor;
       - (ii) depth evaluation at 1-10 m.
     - Its head-mounted camera is the closest public analogue to glasses.
     - Note that the dense depth comes from CREStereo, not lidar. It is a strong reference, but not ground truth **[read: "Dense depth from the CREStereo disparity method"]**.

2. **WAD - Walking Awareness Dataset (WalkVLM, ICCV 2025) is the BLV-specific VLM benchmark, but its licence is unknown.**
   - Content **[read]**: about 12k video-annotation pairs from Europe and Asia.
   - Annotations **[read]**: weather, danger level, traffic flow, scene summary, reminder or QA text, and per-frame bounding boxes.
   - Download is a single tar from a Tencent COS URL on the project page. **No licence text** is on the page **[read]**.
   - (b) https://walkvlm2024.github.io/ ; https://arxiv.org/html/2412.20903v4
   - (c) Medium.
   - **Design impact:**
     - Useful for evaluating the cloud tier's alert verbosity and danger rating.
     - Do not redistribute or ship anything trained on it until the licence is clarified. Email the authors.

3. **GuideDog (ACL 2026) offers BLV guidance labels for pedestrian images, under a non-commercial licence.**
   - Content **[read]**:
     - 22,084 image-description pairs: 2,106 human-verified "gold", the rest VLM-generated "silver".
     - Depth and object multiple-choice QA subsets: 383 and 435 rows.
     - Total 5.11 GB.
   - Licence: "**CC BY-NC 4.0** … released for non-commercial research purposes only" **[read]**.
   - Hugging Face access is gated, with auto-approval and login required **[read]**.
   - (b) https://huggingface.co/datasets/kjunh/GuideDog ; https://arxiv.org/abs/2503.12844 (not opened)
   - (c) High.
   - **Design impact:**
     - Use only the gold subset to score cloud-tier description and hazard-by-clock-direction outputs.
     - Flag it as non-commercial.

4. **SideGuide (IROS 2020) is the sidewalk obstacle and stereo-depth set built for impaired people. It is non-commercial and gated.**
   - Content **[read]**: 350K images with boxes, 100K polygon masks, and 180K ZED stereo pairs with ground-truth disparity.
   - Access requires a survey plus approval **[read]**.
   - The Google Form terms (read via search snippet) restrict use to non-commercial research and forbid redistribution. A Roboflow mirror labelled CC BY 4.0 appears to be unofficial.
   - (b) https://ytaek-oh.github.io/sideguide ; https://docs.google.com/forms/d/e/1FAIpQLScBmoVoj0d-omBOVCHGjhRislXP0TYzRqaUJOmJcqN6ylQcxQ/viewform (snippet)
   - (c) Medium. We did not open the licence terms directly.
   - **Design impact:**
     - Best labelled source for Korean sidewalk obstacle classes such as bollards, kick scooters, and planters.
     - Worth applying for, but not a quick sample.

5. **EgoBlind (NeurIPS 2025 D&B) is real egocentric video recorded by blind people.**
   - Content **[read via snippets]**: 1,392 videos and 5,311 questions posed or verified by blind users.
   - Results **[read via snippets]**: the best MLLMs score about 60%, against 87.4% for humans.
   - (b) https://arxiv.org/html/2503.08221v3 (not opened: rate limit) ; https://neurips.cc/virtual/2025/poster/121565
   - (c) Medium.
   - **Design impact:**
     - The best test set for the cloud tier on genuinely BLV-captured footage, including bad framing.
     - Licence not checked.

6. **Walking Tours (WTours, ICLR 2024) offers hours of city walking video under CC BY, but no labels.**
   - Content **[read via snippets]**: 10 egocentric city walks, 4K60, 59 min-2 h55 each.
   - Licence: the Hugging Face card says `cc-by-4.0`, but the videos must be fetched from YouTube by script. Faces and plates are blurred with Deface.
   - (b) https://huggingface.co/datasets/shawshankvkt/Walking_Tours (not opened)
   - (c) Low-medium.
   - **Design impact:**
     - Use for false-alarm-per-minute soak tests on long unlabelled walks, where every alert is reviewed by hand.
     - Not usable for miss rate.

7. **Restrictive "classic" sets work for research only.**
   - Licences **[read]**:
     - **KITTI**: "Creative Commons Attribution-NonCommercial-ShareAlike 3.0".
     - **Cityscapes**: non-commercial only, registration required, no redistribution.
     - **JRDB**: CC BY-NC-SA 3.0, login required. Annotations: 2.4M 2D and 1.8M 3D human boxes, collected from a robot at pedestrian height.
     - **Nymeria** (Aria): CC BY-NC 4.0 per Project Aria. Its HF metadata tag says Apache-2.0, which conflicts.
     - **Aria Digital Twin**: a custom licence; commercial use needs Meta.
     - **Aria Everyday Activities**: needs a licence agreement and email request.
   - (b) https://www.cvlibs.net/datasets/kitti/ ; https://www.cityscapes-dataset.com/license/ ; https://jrdb.erc.monash.edu/ ; https://www.projectaria.com/datasets/aea/ ; https://www.projectaria.com/datasets/adt/license/ (via snippet) ; https://projectaria.com/datasets/nymeria (via snippet)
   - (c) High for KITTI, Cityscapes, and JRDB. Medium for the Aria sets.
   - **Design impact:**
     - JRDB is the best pedestrian-dynamics test, with crowds near a pedestrian-height sensor.
     - Keep all NC data in a separate evaluation-only bucket and never train shipped models on it.

8. **Ego4D licence: sources disagree.**
   - Official mirrors describe an agreement with about 48 h approval and AWS credentials. Ego-Exo4D's guide mentions research and commercial use "subject to restrictions". Vendor blogs say "CC-BY-NC" **[read via snippets]**.
   - The licence PDF we downloaded could not be parsed.
   - (b) https://ego4d-data.org/pdfs/Ego4D-Licenses-Draft.pdf (fetched, unreadable)
   - (c) Low.
   - **Design impact:**
     - Ego4D/Ego-Exo4D are mostly indoor or activity footage.
     - Low priority for us. Do not rely on them until someone reads the signed agreement.

9. **Other candidates:**
   - **SideSeeing**: 3 h and 12 km of chest-mounted phone video plus IMU and GPS around 9 hospitals in Brazil and the USA; 325K frames **[read via snippet]**. The project site gives no licence and no download link **[read]**.
   - **TartanGround**: CC BY 4.0 on Hugging Face, synthetic ground-robot data with perfect depth **[read via snippet]**.
   - **EgoWalk**: 50+ h of stereo navigation video; licence unknown **[snippet]**.
   - Not checked: Mapillary Vistas, SCAND, TartanAir.
   - We found **no** public dedicated curb, stair, or drop-off dataset with a clear licence within budget.
   - (b) https://sites.usp.br/sideseeing/ ; https://huggingface.co/datasets/theairlabcmu/TartanGround (not opened) ; https://arxiv.org/abs/2505.21282 (not opened)
   - (c) Low-medium.
   - **Design impact:**
     - For stairs, curbs, and drop-offs, mine SANPO sessions tagged with elevation changes (the `elevation_changes` field exists in `description.json`).
     - Use TartanGround or SANPO-Synthetic for perfect-depth edge cases.

**Best picks:**

| Purpose | Best sets |
|---|---|
| (i) Obstacle/hazard replay with ground truth | SANPO-Real (head camera) > JRDB > SideGuide |
| (ii) Depth at 1-10 m | SANPO (CREStereo dense plus ZED sparse) > SideGuide stereo GT > SANPO-Synthetic/TartanGround (exact) |
| (iii) Stairs, curbs, drop-offs | SANPO sessions with elevation-change tags, plus SANPO-Synthetic; no dedicated licensed set found |
| Small sample with no registration | **SANPO only** (anonymous HTTPS per file) |

### Part B - Closed-loop test design

10. **AV practice separates open-loop log replay from closed-loop reactive simulation. Open-loop metrics can mislead.**
    - We did not open AV-simulation primary sources within budget (examples include nuPlan, Waymax, OpenSCENARIO). What follows is general knowledge **[est]**:
      - Open-loop replay scores a system against fixed logs. It cannot show what happens after the system changes behaviour.
      - Closed-loop sim lets the agent's response change the future.
      - Scenario languages such as ASAM OpenSCENARIO describe actors, triggers, and parameters.
    - Project Guideline ships a 3D simulator that runs the real ML models and audio stack natively, for testing without hardware **[read]**.
    - (b) https://research.google/blog/open-sourcing-project-guideline-a-platform-for-computer-vision-accessibility-technology/
    - (c) Medium. Project Guideline was read; the AV generalities are our own.
    - **Design impact:** Two layers.
      - **L2 replay:** frames, depth, and ground truth from SANPO drive the real perception and cue code. Open loop, used for detection timing and false alarms.
      - **L1 closed loop:** a simulated walker reacts to cues in synthetic or reconstructed scenes. Used for collisions and margins.

11. **Proposed closed-loop design [est throughout].**

    - **Walker model:**
      - Speed v₀ ~ U(0.8, 1.4) m/s.
      - Reaction time to an audio cue: t_r ~ lognormal, median 0.9 s, 95th percentile 1.8 s. Treat as a hypothesis to be calibrated with users at L5.
      - Deceleration to stop: a ~ U(1.5, 3) m/s².
      - Stopping distance = v·t_r + v²/2a. At 1.2 m/s that is about 1.1-2.6 m.
      - Cue compliance probability p_c (default 0.95). Cane contact radius 0.9 m, which detects ground obstacles but not head-height ones.
      - Turn response: heading change of 30-90° over 1-2 s.
    - **Scenario library**, one YAML per scene. Fields:
      - `id`, `source` (sanpo session plus frame range, or synthetic);
      - `actors[]`: class, trajectory, size, height band (ground, waist, head);
      - `drop_offs[]`: position, height;
      - `route`: Maps-like polyline;
      - `expected`: hazard events with `must_warn_by_s` (time-to-contact threshold) and `must_not_warn` windows;
      - `faults[]`.
    - **Fault injection** (per scenario or sampled):
      - camera latency (+0 to +500 ms);
      - frame drops (Bernoulli, and bursts of 0.5-3 s);
      - Bluetooth resolution or fps downgrade (720→504→360; 30→15→7 fps), matching the DAT behaviour in 07 finding 16;
      - full Bluetooth loss;
      - network loss and cloud latency drawn from a measured distribution;
      - clock skew;
      - phone thermal throttling (perception at 0.5× speed).
    - **Metrics:**
      - **Time-to-warning margin:** TTC at first warning minus required stopping time. Report the distribution plus p5.
      - **Miss rate:** hazard events with no warning before margin < 0, or with a collision.
      - **Collision rate.**
      - **False alarms:** per minute and per scene.
      - **Repetition rate:** duplicate cues for the same track within N s.
      - **Instruction correctness:** route cues matched to ground-truth turns.
      - **Time-to-degraded-cue:** delay from fault onset to the "camera lost / warnings off" cue. Target under 1 s.
      - **Cloud guard metrics:** R3 leak count (must be 0) and stale-answer speak count (must be 0).
    - **Run seeds:** fixed and logged. Every result is tagged L1 (sim) or L2 (replay).
    - (c) Medium. Our own design. Reaction-time numbers are not sourced here and must be cited or measured before use.
    - **Design impact:** This becomes the acceptance harness for `acceptance.json`.

12. **Statistics needed to claim improvements [est, computed].**
    - **Miss rate below 5%:**
      - With **0 misses**, Clopper-Pearson one-sided 95% needs **n ≥ 59** independent hazard events.
      - With 1 miss: n ≥ 95. With 2 misses: n ≥ 125. With a true rate of 3%: about 315.
    - **Beating the 47.4% miss baseline** (two-sided α = 0.05, power 0.8, independent arms). Events needed per arm:

      | Target miss rate | Events per arm |
      |---|---|
      | 40% | 705 |
      | 35% | 247 |
      | 30% | 122 |
      | 20% | 46 |
      | 10% | 22 |

      Using the same events for both rules with McNemar's test needs fewer.
    - **Beating the 2.0% false-alarm baseline:** going to 1.0% needs about 2,300 negative opportunities per arm; going to 1.5% needs about 10,800.
      - Define the unit precisely: per frame, per second, or per scene.
      - Prefer false alarms per minute with a Poisson CI.
    - **Wilson 95% CIs for reference:**
      - 47/100 → [37.5%, 56.7%].
      - 20/1000 → [1.3%, 3.1%].
    - **Independence:** hazard events within one scene are correlated. Count scenes, or cluster-bootstrap by session.
    - (c) High (standard formulas, computed).
    - **Design impact:** The replay set needs at least 60-150 distinct annotated hazard events across at least 30 sessions before any "<5% miss" claim. Report CIs, never point estimates.

### Part C - Prior art

13. **Google Project Guideline: on-device, open source.**
    - Hardware and compute **[read]**:
      - Runs on a Pixel phone with headphones.
      - ARCore provides pose.
      - DeepLabV3+-style line segmentation, plus a monocular depth model trained on SANPO, with depth projected to point clouds for obstacles.
      - On a Pixel 8, the depth model ran 28× faster on the TPU than on the CPU.
    - Audio and simulation **[read]**:
      - Spatialised audio via AAudio and Resonance Audio.
      - A "stop" cue plays on anomaly or when there is no signal.
      - A 3D simulator is included.
    - The blog gives no latency or accuracy numbers and no user-study count **[read]**. The repo disclaims detection of all obstacles (snippet).
    - (b) https://research.google/blog/open-sourcing-project-guideline-a-platform-for-computer-vision-accessibility-technology/ ; https://github.com/google-research/project-guideline (not opened)
    - (c) High for the description. Evidence of efficacy is anecdotal.
    - **Lessons:**
      - Copy the "stop on anomaly or no signal" pattern as our system-health cue.
      - Reuse its simulator idea.
      - Its SANPO-trained depth model is a candidate baseline.

14. **Gao et al., Nature Communications 16:2857 (2025): "A wearable obstacle avoidance device for visually impaired individuals with cross-modal learning".** This is the sub-320 ms paper.
    - Hardware **[read via snippets]**:
      - Custom glasses of about 400 g, including an 80 g battery, with ToF depth plus an IR camera and an FPGA.
      - Depth-aided video compression sends data to a **smartphone**, which runs cross-modal detection and gives audio and tactile alerts.
    - Reported results **[read via RePEc and press snippets]**:
      - 100% collision avoidance in indoor and outdoor tests;
      - delay **< 320 ms**;
      - about 11 h of battery.
    - The university news release gives no participant numbers **[read]**.
    - (b) https://www.nature.com/articles/s41467-025-58085-x (not opened: paywall/auth redirect) ; https://ideas.repec.org/a/nat/natcom/v16y2025i1d10.1038_s41467-025-58085-x.html (snippet) ; https://www.njupt.edu.cn/2025/0415/c17118a281475/page.htm (opened)
    - (c) Medium. Paper exists and headline numbers are consistent across sources; we did not read the methods.
    - **Lessons:**
      - Their architecture (glasses stream to phone, phone detects) is ours, minus depth sensing.
      - Make ~300 ms end-to-end our explicit target for the device obstacle path.
      - "100% avoidance" with an unknown protocol is not a benchmark we can compare against.

15. **Tang et al., Nature Machine Intelligence (2025): glasses with an RGB-D camera, artificial-skin haptics, and bone-conduction audio.**
    - Reported **[read via press]**:
      - 20 visually impaired participants;
      - about 25% improvement in walking distance and time versus the white cane;
      - audio cues every 250 ms;
      - 18 ms haptic response for lateral obstacles.
    - External experts noted few subjects and controlled environments **[read via press]**.
    - (b) https://physicsworld.com/a/visual-assistance-system-helps-blind-people-navigate/ (snippet) ; paper not opened
    - (c) Medium-low. Press only.
    - **Lessons:** Even top-venue systems are tested in controlled settings with about 20 users. Our L5 bar should be similar and supervised.

16. **ForeSightGuide (arXiv 2608.18993): verified to exist.** Our note was correct in substance.
    - Authors: Wang, Li, Guo, Meng, Liu, Zuo. Submitted 19 Aug 2026. Licence: arXiv non-exclusive **[read]**.
    - Method **[read]**:
      - Qwen2-VL-7B fine-tuned on WAD, plus YOLO11, Depth Pro, and a Kalman filter for motion prediction.
      - A 650 ms "fast response" path that includes transmission, network, and inference, and VLM updates every 3 s.
      - Hardware location not stated.
    - Results on 107 samples from a self-built 140-clip dataset:

      | System | Redundant alerts per output | Misses per output |
      |---|---|---|
      | ForeSightGuide | 0.299 | 0.112 |
      | GPT-5.5 | 1.74 | 0.056 |

    - Walking test **[read]**: fast-response accuracy 71.32% (537/753), VLM instruction accuracy 84.41% (157/186). Outputs were judged by ChatGPT. Participant count and visual status are **not stated**.
    - (b) https://arxiv.org/abs/2608.18993 ; https://arxiv.org/html/2608.18993
    - (c) Medium. Small dataset, LLM-judged, no BLV study.
    - **Lessons:**
      - Predict motion before alerting, which cuts false alarms about 6×.
      - The trade-off is twice the misses of GPT-5.5. Our miss-first policy should not copy that trade-off blindly.

17. **Sensors 2026 paper found:** Lin, Fan & Chang, "Multimodal Navigation System for Visually Impaired Users Using Environmental Perception and Vision-Language Models", *Sensors* 26(10):3045, 12 May 2026, DOI 10.3390/s26103045 **[read via snippets]**.
    - It evaluates hallucination on RePOPE and runs real-world **indoor** navigation experiments **[snippet]**.
    - The MDPI page returned HTTP 403, so we **could not verify** that it reports latency as an open problem.
    - (b) https://www.mdpi.com/1424-8220/26/10/3045 (not opened: 403)
    - (c) Low for the latency claim. Medium that it is the paper we meant.
    - **Lessons:** Pending. Fetch the PDF via the DOI later.

18. **WorldScribe (UIST 2024 Best Paper).**
    - Architecture **[read]**: iPhone camera; local YOLO World plus ByteTrack at 5 fps; remote Moondream, Depth Anything, and CLIPSeg; GPT-4v for detail.
    - Latency **[read]**: overall 1.44 s; GPT-4v 8.78 s.
    - Description accuracy **[read]**: 15.74% of 2,350 descriptions could not be justified by the referenced frame.
    - Participants: 5 BVI in the formative study, 6 BVI in the evaluation **[read]**.
    - Complaints: false detections cut trust, information overload, descriptions lagging movement, image-relative left/right. Requests: distances, spatial audio, control **[read]**.
    - (b) https://arxiv.org/html/2408.06627v1
    - (c) High.
    - **Lessons:** Layered fast/slow description, user-relative directions, verbosity control, and staleness dropping (doc 07 rule S).

19. **WalkVLM (ICCV 2025).**
    - Setup **[read]**: cloud-deployed, input sampled at 2 fps, temporal-aware prediction to suppress redundant reminders.
    - Temporal redundancy score (higher is better) **[read]**: 0.505 vs GPT-4o 0.430.
    - Closed-loop test with **3** VI participants **[read]**: manual reminders needed fell from 35/33/37 to 24/22/28.
    - No latency reported **[read]**.
    - (b) https://arxiv.org/html/2412.20903v4
    - (c) Medium.
    - **Lessons:** "Manual reminders needed" is a neat proxy metric for a supervised L5 pilot.

20. **NaviGPT (GROUP '25).**
    - Setup **[read]**: iPhone LiDAR proximity with vibration, GPT-4 via API for camera images, Apple Maps routing.
    - **No latency measured and no user study.** It cites Be My AI taking over 5 s. Stated limits: connectivity delays and outdoor-only routing **[read]**.
    - (b) https://arxiv.org/html/2410.04005v1
    - (c) High that it reports nothing quantitative.
    - **Lessons:** Their split (local LiDAR proximity, cloud LLM for semantics) matches ours. We should actually measure what they did not.

21. **StreetNav.**
    - Uses existing street cameras plus computer vision for real-time guidance; preprint, revised 2024 **[read]**.
    - Snippet: 8 BLV participants in the COSMOS testbed, more precise than GPS, sensitive to lighting and occlusion.
    - (b) https://arxiv.org/abs/2310.00491
    - (c) Medium.
    - **Lessons:** Infrastructure-side sensing is out of scope for us, but it shows GPS alone is too coarse for crossings.

22. **VIALM (2024).**
    - 200-image benchmark; for GPT-4, 25.7% of guidance was not grounded and 32.1% not fine-grained **[read]**.
    - (b) https://arxiv.org/html/2402.01735v2
    - (c) High.
    - **Lessons:** Cloud guidance needs grounding against device perception (doc 07, C-rules).

23. **Commercial and community tools.**
    - **Be My Eyes / Be My AI and Ray-Ban Meta** **[read]**:
      - Meta added "detailed responses" for BLV users (US/Canada first).
      - "Call a Volunteer" with Be My Eyes runs in 18 Meta AI countries.
    - **User studies of Be My AI-style tools** **[read]**:
      - CHI 2024 diary study (16 BLV users): satisfaction 2.76/5, trust 2.43/4.
      - ASSETS 2024 (26 blind users): users verify errors by cross-checking.
      - CHI 2026 (11 users, 418 interactions): no verbosity control, poor distance estimates.
    - **Microsoft Soundscape** (spatial-audio beacons): retired in 2023 and open-sourced, continued as "Soundscape Community" **[read via snippets]**.
    - **NavCog3** (BLE beacons) **[read via snippets]**: 1.6 m average localisation error with 10 VI participants; a free-navigation study with 43 participants.
    - **Glidance Glide:** 2024 pre-order price $1,499 with a subscription, delivery "late 2025" (snippet). Current status unknown.
    - **Not researched within budget:** Seeing AI, Envision, biped NOA, .lumen, OrCam, Aira, and ChatGPT/Gemini Live use by BLV people.
    - (b) https://about.fb.com/news/2025/05/advancing-accessibility-meta/ ; https://arxiv.org/abs/2403.15604 ; https://arxiv.org/abs/2408.06546 ; https://arxiv.org/abs/2602.16930 ; https://itsfoss.com/news/microsoft-soundscape-open-source/ (not opened) ; https://pure.ewha.ac.kr/en/publications/navcog3-in-the-wild-large-scale-blind-indoor-navigation-assistant/ (not opened)
    - (c) High for the Meta post and the three studies. Low for the others.
    - **Lessons:**
      - The recurring user asks are brevity, controllable verbosity, accurate distance, user-relative direction, low false alarms, and a way to check answers.
      - Soundscape's demise shows the risk of depending on one vendor. Keep our core open and offline.

## Disagreements between sources

- **SideGuide size:** 350K/100K/180K "totalling 492K" on the project page, vs "over 490K" in a 2024 paper. Consistent, but counted differently.
- **Nymeria licence:** CC BY-NC 4.0 (Project Aria page, HF README) vs Apache-2.0 (HF metadata tag).
- **Ego4D commercial use:** the official agreement summary ("research and commercial, subject to restrictions", for Ego-Exo4D) vs vendor blogs ("non-commercial").
- **ForeSightGuide vs GPT-5.5:** fewer redundant alerts but twice the misses. Which counts as "better" depends on the miss/false-alarm cost ratio.
- **Gao et al. "100% collision avoidance":** the paper's protocol is not visible to us. The university release gives no numbers at all.
- **SANPO depth:** described as "pixel accurate" for synthetic data, while real data uses stereo-estimated (CREStereo) depth. Real "ground truth" is estimated.

## Open questions

- Does SANPO's CREStereo depth hold up at 5-10 m well enough to grade monocular depth, or do we need ZED sparse depth only at range?
- How many SANPO sessions contain annotatable curbs or stairs (`elevation_changes` ≠ FLAT)? Count them via `description.json` across all sessions; the files are small.
- WAD and EgoBlind licences: email the authors.
- A literature-sourced reaction-time distribution for BLV pedestrians responding to audio cues. Needed before closed-loop numbers mean anything.
- Gao et al. methods: obtain the PDF (institutional access) to check the protocol behind < 320 ms and 100%.
- Lin, Fan & Chang (Sensors 2026): verify the latency claim.

## Recommended small replay sample (no registration)

- **Source:** SANPO-Real, one session, head or chest camera, left lens. Bucket `gresearch`, prefix `sanpo_dataset/v0/sanpo-real/<session>/`.
- **Example fetch:**
  - Frames: `https://storage.googleapis.com/gresearch/sanpo_dataset/v0/sanpo-real/-5OCPnbrwJdu3jH70ieU7pUiFsOJQoeG/camera_chest/left/video_frames/000000.png`
  - Depth: the matching `depth_maps/000000.float16.gz`.
  - Metadata: `description.json` (5.6 KB) and `camera_poses.csv` (85 KB).
- **Size [read sizes, est totals]:**
  - About 3.5 MB per PNG frame plus about 0.6 MB per depth map.
  - 10 consecutive frames with depth and masks ≈ 42 MB.
  - 2 s at 15 fps ≈ 125 MB.
  - Downscale to 720p JPEG locally and delete the PNGs to save disk.
- **Licence:** CC BY 4.0 (attribution required).

## Sources

- SANPO dataset page - https://google-research-datasets.github.io/sanpo_dataset/ (opened); repo https://github.com/google-research-datasets/sanpo_dataset (opened); bucket listing via https://storage.googleapis.com/storage/v1/b/gresearch/o?prefix=sanpo_dataset/v0/ (opened)
- SANPO blog - https://research.google/blog/sanpo-a-scene-understanding-accessibility-navigation-pathfinding-obstacle-avoidance-dataset/ (snippet)
- WalkVLM project - https://walkvlm2024.github.io/ (opened); paper https://arxiv.org/html/2412.20903v4 (opened)
- GuideDog - https://huggingface.co/datasets/kjunh/GuideDog (opened)
- SideGuide - https://ytaek-oh.github.io/sideguide (opened)
- SideSeeing - https://sites.usp.br/sideseeing/ (opened); https://arxiv.org/abs/2407.06464 (snippet)
- EgoBlind - https://arxiv.org/html/2503.08221v3 (snippet)
- Walking Tours - https://huggingface.co/datasets/shawshankvkt/Walking_Tours (snippet)
- KITTI - https://www.cvlibs.net/datasets/kitti/ (opened); Cityscapes licence https://www.cityscapes-dataset.com/license/ (opened); JRDB https://jrdb.erc.monash.edu/ (opened)
- Aria Everyday Activities - https://www.projectaria.com/datasets/aea/ (opened); ADT licence https://www.projectaria.com/datasets/adt/license/ (snippet); Nymeria https://projectaria.com/datasets/nymeria (snippet)
- Ego4D licence draft - https://ego4d-data.org/pdfs/Ego4D-Licenses-Draft.pdf (fetched, unreadable)
- TartanGround - https://huggingface.co/datasets/theairlabcmu/TartanGround (snippet); EgoWalk https://arxiv.org/abs/2505.21282 (snippet)
- Project Guideline - https://research.google/blog/open-sourcing-project-guideline-a-platform-for-computer-vision-accessibility-technology/ (opened)
- Gao et al. Nat Commun 2025 - https://www.nature.com/articles/s41467-025-58085-x (not opened); NJUPT news https://www.njupt.edu.cn/2025/0415/c17118a281475/page.htm (opened)
- Tang et al. Nat Mach Intell 2025 - https://physicsworld.com/a/visual-assistance-system-helps-blind-people-navigate/ (snippet)
- ForeSightGuide - https://arxiv.org/abs/2608.18993 (opened); https://arxiv.org/html/2608.18993 (opened)
- Lin, Fan & Chang, Sensors 2026 - https://www.mdpi.com/1424-8220/26/10/3045 (not opened: 403)
- WorldScribe - https://arxiv.org/html/2408.06627v1 (opened)
- NaviGPT - https://arxiv.org/html/2410.04005v1 (opened)
- StreetNav - https://arxiv.org/abs/2310.00491 (opened)
- VIALM - https://arxiv.org/html/2402.01735v2 (opened)
- Meta accessibility - https://about.fb.com/news/2025/05/advancing-accessibility-meta/ (opened)
- Misfitting with AI - https://arxiv.org/abs/2408.06546 (opened); Say It My Way https://arxiv.org/abs/2602.16930 (opened); CHI 2024 use cases https://arxiv.org/abs/2403.15604 (snippet)
- Soundscape - https://itsfoss.com/news/microsoft-soundscape-open-source/ (snippet); NavCog3 https://pure.ewha.ac.kr/en/publications/navcog3-in-the-wild-large-scale-blind-indoor-navigation-assistant/ (snippet)
