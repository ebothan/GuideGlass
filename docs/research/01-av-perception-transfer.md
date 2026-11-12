# AV perception, tracking and prediction: what transfers to a head-worn monocular camera

Research note for GuideGlass. Written 2026-11-11. Every number is tagged **[read]** (taken from a source I opened) or **[est]** (my own estimate or arithmetic, with the working shown). Sources I could not open are marked "not opened".

Working assumptions, from the brief: walker speed 1.4 m/s; camera 10 fps (link allows 2/7/15/24/30); TTC trigger 2.0 s; FOV 60x46 deg; frames up to 720x1280 portrait. For the pixel arithmetic below I take a focal length of about 850 px across the 720 px axis **[est: 360 px / tan(23 deg)]**. That figure must be replaced by a calibration done on stream frames (see Finding 1).

## Question

Which AV perception, tracking and prediction techniques transfer to a pedestrian wearing a single moving monocular camera, and which do not?

## Findings

### A. The sensor and link we actually have

1. **The glasses deliver a processed stream, not raw sensor frames.** Before the app receives a frame, the glasses crop it to 9:16, correct lens distortion, apply electronic stabilisation and compress it with HEVC. Resolution can drop mid-stream. The SDK does not report the camera intrinsics, and Meta says stabilisation "moves each frame to counter small head movements", so the image does not exactly follow the IMU-reported head rotation. In Meta's words, VIO and other methods that assume a fixed camera-IMU relationship "lose accuracy during head movement". Resolution tiers are 720x1280, 504x896 and 360x640 **[read]**. Requested fps values are 2/7/15/24/30 **[read]**. The automatic ladder lowers resolution first, then frame rate, "but never below 15 fps" **[read]**. Meta also notes that a lower requested resolution or fps can produce better image quality because less compression is needed **[read]**. The IMU (Motion capability) runs at 5-60 Hz and defaults to 10 Hz **[read]**. Its timestamps come from a device monotonic clock, and "the rate you request is a target, not a guarantee" **[read]**.
   - Sources: https://wearables.developer.meta.com/docs/develop/dat/camera-stream-processing/ ; https://wearables.developer.meta.com/docs/develop/dat/streaming/ ; https://wearables.developer.meta.com/docs/develop/dat/motion/ (all updated Sep-Oct 2026, opened)
   - Confidence: **high**. These are the vendor's own current docs.
   - Design impact: (i) Calibrate intrinsics on stream frames. Rescale focal length and principal point by each frame's *actual* width, which on Android with compressed video means reading the HEVC SPS, not the requested size. (ii) Do not run a tightly coupled VIO that assumes rigid camera-IMU extrinsics. Use the IMU for rotation priors and gait phase, and treat the image-to-IMU alignment as loose and noisy. (iii) Our "2 fps" and "7 fps" modes are app-requested, not ladder-imposed. Re-check that the brief's "may fall to 7 or 2 fps" scenario comes from our own requests, decode or compute stalls, or `paused` states. (iv) Ask for the IMU at 30-60 Hz, not the 10 Hz default, because 10 Hz cannot resolve gait or head-turn dynamics **[est]**.

2. **Head rotation dominates image motion, and per-frame displacement at our frame rates breaks IoU association.** At 100 deg/s yaw and 10 fps the image shifts about 10 deg per frame, which is roughly 150 px at f of about 850 px **[est]**. At 2 fps the shift is 50 deg per frame, most of the FOV **[est]**. Grossman et al. (1988) is commonly summarised as reporting median peak head velocities under about 90 deg/s while walking, and 780 deg/s for vigorous voluntary yaw. I saw this only as a search-result abstract, so it is **not opened**. A BLV wearer scanning deliberately will exceed walking values.
   - Sources: https://link.springer.com/article/10.1007/BF00247595 (not opened; abstract-level only)
   - Confidence: **medium**. The arithmetic is simple; the head-velocity norms are not verified for BLV walkers.
   - Design impact: Rotate track predictions with the gyro before association. This is IMU-based camera-motion compensation (see Finding 4). Suppress *new* alerts while yaw rate exceeds a threshold (start at about 60 deg/s **[est]**) and say so in the degraded-mode design. Log the yaw rate with every alert.

### B. Multi-object tracking

3. **SORT-family tracking-by-detection transfers well as an architecture.** SORT uses a constant-velocity Kalman filter on [u, v, area, aspect], Hungarian assignment on IoU, a probationary period before a track is confirmed, and deletion after T_Lost frames. The paper set T_Lost = 1 because "the constant velocity model is a poor predictor of the true dynamics" **[read]**, and it ran at 260 Hz on one CPU core, excluding the detector **[read]**. ByteTrack adds a second association pass that uses low-score detections. Its defaults are a high threshold tau = 0.6, rejection of matches with IoU < 0.2, and lost tracks kept for 30 frames **[read]**. It scores MOTA 80.3, IDF1 77.3 and HOTA 63.1 on MOT17 at 30 fps on a V100 **[read]**. OC-SORT runs at 700+ fps on a CPU given detections **[read]**.
   - Sources: https://arxiv.org/abs/1602.00763 ; https://arxiv.org/abs/2110.06864 ; https://arxiv.org/abs/2203.14360 (opened, full text via ar5iv)
   - Confidence: **high** for the mechanics. The benchmarks use 25-30 fps video, mostly from static or vehicle cameras.
   - Design impact: Use a ByteTrack-style two-pass tracker on phone. The tracker is cheap, so the detector dominates compute. Express confirmation and deletion **in seconds, not frames**, so the logic survives fps changes. For example: confirm after 2 hits within 0.4 s; coast for at most 0.5 s **[est]**. ByteTrack's "30 frames" is 1 s at 30 fps but 15 s at 2 fps, which would be unsafe. Low-score second-pass matching is valuable for us because motion blur during head turns lowers detector confidence.

4. **Camera-motion compensation (CMC) is mandatory, but image-registration CMC is the wrong tool for us.** BoT-SORT says moving cameras make IoU association fail and fixes it with global affine registration (sparse flow plus RANSAC) applied to the Kalman state and covariance **[read]**. Its own limitations section says CMC "may fail due to lack of background keypoints" in dense dynamic scenes **[read]**. BoT-SORT scores MOTA 80.5, IDF1 80.2 and HOTA 65.0 on MOT17 **[read]**.
   - Source: https://arxiv.org/abs/2206.14651 (opened)
   - Confidence: **high** that compensation is needed; **medium** on the best method for us.
   - Design impact: Use the gyro to predict the homography for pure rotation (H = K R K^-1) and refine with a cheap image registration only when enough background features exist. Because the glasses stabilise the image (Finding 1), the gyro prior is approximate. Fit a correction gain on logged data. Crowded sidewalks are exactly where image-only CMC fails.

5. **Kalman velocity is noisy at high fps and goes stale at low fps; trackers degrade sharply as fps falls.** OC-SORT shows that velocity noise variance scales as 2*sigma^2/dt^2, so a small dt amplifies box jitter. On MOT17 the mean displacement per frame is only 1.93 px horizontally, so 1 px of jitter can match the motion itself **[read]**. Without observations, KF position error grows with time **[read]**. FraMOT reports that MOTA and HOTA of all tested state-of-the-art trackers "drop dramatically" as fps is lowered, and that ByteTrack's advantage at normal fps disappears at low fps **[read]**. StableTrack targets 1 Hz detections and reports better HOTA at low detection rates **[read; the abstract-level "+11.6% HOTA at 1 Hz" figure came via a search snippet and I did not check it in the table]**.
   - Sources: https://arxiv.org/abs/2203.14360 ; https://arxiv.org/abs/2209.11404 ; https://arxiv.org/abs/2511.20418 (opened)
   - Confidence: **high** on direction; **low** on transferring magnitudes, because those are MOT17 pedestrian-surveillance numbers.
   - Design impact: (i) At 2 fps, do not trust tracker-derived velocities or TTC. That mode should give only coarse "something ahead" information or an explicit "reduced mode" announcement (see file 02). (ii) Estimate velocity over a window (OC-SORT's observation-centric momentum), not from the last two frames. (iii) Benchmark our tracker on our own footage subsampled to 30/15/7/2 fps and publish the HOTA-versus-fps curve as evidence.

### C. Time to contact

6. **Image-expansion tau (Lee 1976) needs neither depth nor calibration, and it is roughly invariant to pure head rotation. But its noise is large at walking speeds and short baselines.** tau = s / (ds/dt), where s is image size **[read: stated in the scale-based TTC literature, e.g. Byrne & Taylor 2009, which also derives a first-order TTC uncertainty model from sub-pixel quantisation]**. Worked example **[est]**: a 0.5 m wide obstacle at 2.8 m (TTC 2.0 s at 1.4 m/s) spans about 152 px. Between 10 fps frames it grows by about 8 px; at 7 fps by about 12 px. With box-edge jitter of about 3 px per edge (width sigma about 4 px), a two-frame tau estimate has about 50% error. Getting under 15% needs regression of log(s) over about 0.5-0.8 s, which adds that much latency. Lee's original paper is **not opened** (paywalled; cited as the classic origin). Binary TTC (Badki et al., CVPR 2021) frames TTC as dense classification against thresholds instead of regression **[read: abstract via ar5iv]**.
   - Sources: https://www.cis.upenn.edu/~cjtaylor/PUBLICATIONS/pdfs/ByrneICRA09.pdf (opened) ; https://arxiv.org/abs/2101.04777 (opened) ; Lee 1976, Perception 5(4):437-459, https://pubmed.ncbi.nlm.nih.gov/1005020/ (not opened)
   - Confidence: **medium**. The mechanism is solid, but my noise numbers are a back-of-envelope calculation that needs measuring.
   - Design impact: Use tau mainly for **moving** objects (cyclists, people, vehicles), where closing speed is unknown. Fit it over a window, and report tau with a confidence value. A "tau < 2 s" test from two frames will produce false alarms. Gait bob changes the apparent box height every step, so prefer the width channel or the area, and gait-phase-align the samples (Finding 12).

7. **For static obstacles, which are most of a cane user's head-level and trip hazards, metric range divided by own walking speed beats tau.** If the obstacle is static, closing speed equals the walker's speed, which steps and IMU measure (Finding 12). TTC error then equals relative range error. Depth Pro and its peers report delta1 (inside +/-25%) of 40-90% depending on dataset (Finding 9), so expect roughly 10-25% TTC error, about 0.2-0.5 s at TTC 2 s, from depth alone **[est]**. That is an absolute range error of 0.3-0.7 m at 2.8 m **[est]**.
   - Sources: as Finding 9.
   - Confidence: **medium**.
   - Design impact: Use two TTC channels, fused as a minimum or with confidence weighting: range/own-speed for static objects, and tau for objects with independent motion. Set the trigger on the *pessimistic* bound (lower confidence limit of TTC), not on the mean.

### D. Monocular metric depth and free space

8. **Relative-depth models are strong and robust; their metric fine-tunes are in-domain.** Depth Anything V2 predicts *affine-invariant inverse depth* **[read]**. Its metric fine-tune (ViT-S to ViT-L encoders, 25M-1.3B params **[read]**) reaches, for example, AbsRel 0.078 and delta1 0.936 on KITTI and AbsRel 0.053 and delta1 0.973 on NYU-D. Those are in-domain numbers, with the model trained and tested on the same dataset **[read]**. The authors warn that models trained on NYU or KITTI "are not robust to transparent objects" **[read]**. On the NTIRE 2024 Transparent Surface Challenge, V2 scores 83.6% zero-shot, against 53.5% for V1 and 25.9% for MiDaS **[read]**.
   - Source: https://arxiv.org/abs/2406.09414 (opened)
   - Confidence: **high**.
   - Design impact: Relative depth plus a ground-plane or known-height scale cue (Finding 10) is a robust, cheaper route than a heavy metric model. Do not quote in-domain metric numbers as our expected accuracy.

9. **Zero-shot metric depth is far less accurate than the in-domain numbers suggest, and models disagree by dataset.** In Depth Pro's table, delta1 means the share of pixels within 25% of truth. Depth Pro scores 46.6 on Booster, 41.5 on ETH3D, 60.5 on Middlebury, 49.1 on nuScenes, 40.0 on Sintel and 89.0 on Sun-RGBD **[read]**. Metric3D v2 scores 87.7 on ETH3D and 82.6 on nuScenes but 39.4 on Booster **[read]**. UniDepth scores 95.8 on Sun-RGBD and 83.6 on nuScenes **[read]**. Depth Anything V2 (metric) scores 36.3 on ETH3D and 17.7 on nuScenes **[read]**. Depth Pro takes 0.3 s for 2.25 MP on a V100 **[read]**. Its stated limitation is "translucent surfaces and volumetric scattering" **[read]**. UniDepthV2 adds a per-pixel **uncertainty output** **[read]**. md4all shows that depth models are "highly unreliable" at night and in rain because of reflections, noise and dark regions **[read]**. Newer 2025-2026 work includes MoGe-2 (metric-scale point maps from one image **[read: abstract]**) and Depth Anything 3 (any-view geometry and pose; reports +35.7% pose and +23.6% geometry accuracy over VGGT **[read: abstract]**). I did not open their per-dataset metric tables.
   - Sources: https://arxiv.org/abs/2410.02073 ; https://arxiv.org/abs/2404.15506 ; https://arxiv.org/abs/2502.20110 ; https://arxiv.org/abs/2308.09711 ; https://arxiv.org/abs/2507.02546 ; https://arxiv.org/abs/2511.10647 (all opened, at least the HTML or abstract)
   - Confidence: **high** for the numbers as read; **medium** for transfer to 1-10 m egocentric sidewalk scenes, since none of these benchmarks is egocentric pedestrian footage.
   - Design impact: (i) Budget for **+/-25% range error** at 1-10 m, and worse on glass, at night or on low-texture walls. (ii) Prefer a model that outputs uncertainty (UniDepthV2-style), and gate alerts on it. (iii) Phone latency is a separate problem: 0.3 s on a V100 rules out Depth Pro-class models on a phone at 10 fps **[est]**. Use a small relative model (DAv2-S class) on a subset of frames. (iv) Validate on SANPO (Finding 11), not KITTI or NYU.

10. **Stixels and BEV occupancy transfer as representations, but their AV assumptions break: no stereo or lidar, no fixed camera height, and no fixed pitch.** Stixel and BEV pipelines assume a known, rigid camera pose relative to a roughly planar road. On a head the camera height changes with gait (a few cm per step **[est]**) and pitch changes with head nods. A ground plane can still be fitted per frame from relative depth plus the IMU gravity vector, and that plane supplies metric scale once the camera height (wearer eye height minus a small offset) is known **[est]**. This is the standard "known camera height" scale trick, and I did not verify it against a specific paper in this pass.
   - Sources: none opened specifically for stixels or BEV in this pass. Marked as an open question.
   - Confidence: **low to medium**.
   - Design impact: Build a "walkable corridor" estimate: the floor region from segmentation or depth in a 1 m wide by 3-5 m corridor along the heading. Raise an alert when the corridor is blocked or when the floor **disappears**. A drop-off is the absence of ground where it should be, a case that box detectors cannot represent. Anchor scale on the ground plane, and test it against stairs and curbs explicitly.

11. **AV datasets do not transfer for evaluation; SANPO is the closest egocentric dataset.** SANPO says AV datasets "are not suitable" for egocentric human navigation **[read]**. It offers human-egocentric video with panoptic masks, depth and camera pose. SANPO-Real has 617K stereo frame pairs **[read]**.
    - Source: https://arxiv.org/abs/2309.12172 (opened)
    - Confidence: **high**.
    - Design impact: Use SANPO for L2-style replay evaluation of depth and free space. Supplement it with our own glasses footage, because SANPO cameras are chest- or head-mounted rigs, not stabilised and HEVC-compressed Meta streams.

### E. Ego-motion

12. **Monocular VIO transfers in principle but is fragile here. Step-based dead reckoning transfers better for speed.** VINS-Mono calls monocular VIO challenging because of "the lack of direct distance measurement", and says camera-IMU extrinsic calibration is "critical" **[read]**. All VIO "suffer from long term drifting" **[read]**. Both points hurt us, since stabilisation breaks the rigid camera-IMU assumption (Finding 1). RoNIN shows that classic step counting assumes the IMU is rigidly attached and that the walker moves forward along the device axis **[read]**. A head IMU violates the second assumption whenever the wearer looks sideways. RoNIN-style learned inertial odometry estimates velocity from the IMU history instead **[read]**.
    - Sources: https://arxiv.org/abs/1708.03852 ; https://arxiv.org/abs/1905.12853 (opened)
    - Confidence: **medium**.
    - Design impact: Ego-speed for range/speed TTC should come from step cadence times a calibrated step length, from the glasses IMU or the phone IMU, or both. We need speed and gait phase, not position. Heading should come from phone or glasses orientation. Skip full VIO in v1. Detect "stopped" robustly, because a stopped walker should cause pending static-obstacle TTCs to expire.

### F. Latency and degraded input

13. **Timestamp every stage and predict to the moment the cue reaches the ear.** This is standard AV practice; my source for the pattern in this pass is PX4's EKF2, which runs a delayed-time-horizon filter and predicts forward to the current time **[read: the PX4 ECL EKF tuning guide describes per-sensor delay parameters and a fusion time horizon]**. Meta gives monotonic timestamps on IMU samples and on in-stream audio frames, with presentationTimeUs on audio **[read]**. Worked budget **[est]**: frame capture to phone about 100-300 ms (BT plus HEVC decode; to be measured), detection and depth about 50-150 ms, decision about 10 ms, TTS start about 100-300 ms (earcon about 20 ms), BT A2DP audio about 100-200 ms. Total about 0.4-1.0 s *before* the human reaction of 0.5-1.5 s. At 1.4 m/s that is 0.6-1.4 m travelled before the cue is even heard.
    - Sources: https://docs.px4.io/main/en/advanced_config/tuning_the_ecl_ekf.html (opened) ; Meta docs as Finding 1.
    - Confidence: **high** on the principle; **low** on my latency numbers, which must be measured.
    - Design impact: (i) Log t_capture (glasses), t_receive, t_infer_done, t_decision, t_audio_start and, if obtainable, the A2DP output latency. (ii) The decision rule should be "TTC at the time of hearing minus expected reaction is less than the stopping margin", not "TTC at frame time < 2.0 s". With about 0.7 s of pipeline latency, the 2.0 s trigger leaves about 1.3 s for reaction and stopping, which is marginal at the slow end of 0.5-1.5 s reactions **[est]**. (iii) Use earcons, not speech, for the urgent channel. (iv) Drop out-of-sequence or stale frames (older than about 300 ms **[est]**) from the alert path rather than fusing them late.

14. **Unplanned gait termination takes about one step and depends on gait phase.** An auditory stop-signal study of 20 young adults linked shorter stop-signal reaction times to more preparatory ankle and knee action **[read]**. A study cited in search results found that able-bodied walkers could stop within one step only if the cue came early in the gait cycle (at or before about 20%) **[read via search snippet; that paper not opened]**.
    - Sources: https://pmc.ncbi.nlm.nih.gov/articles/PMC9953996/ (opened)
    - Confidence: **medium**. These are healthy sighted young adults, not BLV walkers.
    - Design impact: The stopping model should be reaction time (0.5-1.5 s) plus one to two steps (0.7-1.4 m) **[est]**. At 1.4 m/s, warn at about 2.0 s plus pipeline latency. The 2.0 s trigger is the minimum, not a comfortable value. Consider an earlier soft "slow" cue at about 3-4 s for static obstacles **[est]**.

15. **AV stacks treat sensor dropout as a monitored state with explicit fallback, not as "no objects".** PX4 EKF2 checks innovation consistency and data timeouts and stops fusing a sensor that fails them **[read]**. Meta's stream has explicit `paused`, `waitingForDevice` and `stopped` states **[read]**. The SDK's 1.0 release had camera frame freezes acknowledged in its changelog **[read via secondary news source; changelog not opened]**.
    - Sources: PX4 guide; https://wearables.developer.meta.com/docs/develop/dat/streaming/ ; https://mixed-news.com/en/meta-wearables-device-access-toolkit-1-0-1-hevc-buffer-corruption/ (search result only, not opened)
    - Confidence: **high**.
    - Design impact: Add a **frame-freshness watchdog**. Detect frozen frames, which can be identical HEVC frames even while "streaming" reports normal. Track the effective fps and the age of the last good frame. Announce degraded or lost status, because silence must never mean "clear". File 02 defines the degraded modes.

16. **Prior BLV collision-warning evidence: a real but modest effect, delivered via vibrotactile cues.** A double-masked home-use RCT (31 participants, 4 weeks) of a wearable collision-warning device found a contact rate ratio of 0.63 between active and silent modes **[read via search snippets of the press release and abstract; JAMA full text not opened]**.
    - Source: https://masseyeandear.org/news/press-releases/2021/07/wearable-devices-can-reduce-collision-risk-in-blind-and-visually-impaired-people (search result) ; doi:10.1001/jamaophthalmol.2021.2624 (not opened)
    - Confidence: **medium**.
    - Design impact: The silent-mode versus active-mode design is exactly the "shadow mode" we should copy for L5 evaluation (file 02). Expect effect sizes of tens of percent, not elimination.

## Disagreements between sources

- **Which metric depth model is best depends on the dataset.** Depth Pro ranks first on average (rank 2.5), but Metric3D v2 beats it heavily on ETH3D (87.7 vs 41.5) and nuScenes (82.6 vs 49.1), and UniDepth beats it on Sun-RGBD. The UniDepthV2 authors argue that Depth Pro's boundary scores are "inflated by resolution". No single "realistic accuracy" exists, so we must measure on our own footage.
- **Frame rate, both ways.** OC-SORT argues that high fps *hurts* KF velocity estimates (noise amplification). FraMOT and StableTrack show that low fps *hurts* association. Both hold. The resolution is to estimate velocity over a fixed time window regardless of fps.
- **Meta's link behaviour versus the brief.** Meta's ladder never drops below 15 fps on its own. The brief expects 7 or 2 fps. Either those come from app requests and compute limits, or the docs do not describe real behaviour. To verify at L4.
- **Depth Anything V2 on glass.** DAv2 claims robustness to transparent surfaces (83.6% on NTIRE). Depth Pro lists translucent surfaces as a limitation. They measure different things (relative ordering vs metric), so neither fact settles glass doors for us.

## Open questions

1. Real end-to-end latency from glasses capture to audio onset, and its variance. This is the most important unknown.
2. How much glasses-side stabilisation decouples image motion from gyro rotation. Can a learned or fitted correction recover gyro-based CMC?
3. Box-size jitter, in pixels, of our detector on stream frames. This sets tau noise (Finding 6).
4. Drop-off and stair detection from one stabilised camera. No AV analogue was found; this needs a dedicated literature pass (stixels, ground-plane fitting, SANPO segmentation classes).
5. Depth accuracy of small, phone-capable models (DAv2-S-class, MoGe-2 small) on SANPO and on our footage at 1-10 m, by day and night.
6. Whether Meta's in-stream timestamps share a clock with the Motion IMU timestamps. Both are described as device monotonic, but this is unverified.

## Sources

Opened:
- Bewley et al., SORT, https://arxiv.org/abs/1602.00763
- Zhang et al., ByteTrack, https://arxiv.org/abs/2110.06864
- Cao et al., OC-SORT, https://arxiv.org/abs/2203.14360
- Aharon et al., BoT-SORT, https://arxiv.org/abs/2206.14651
- Frame Rate Agnostic MOT (FraMOT), https://arxiv.org/abs/2209.11404
- ColTrack, https://arxiv.org/abs/2308.05911
- StableTrack, https://arxiv.org/abs/2511.20418
- Byrne & Taylor, expansion-based TTC, ICRA 2009, https://www.cis.upenn.edu/~cjtaylor/PUBLICATIONS/pdfs/ByrneICRA09.pdf
- Badki et al., Binary TTC, https://arxiv.org/abs/2101.04777
- Yang et al., Depth Anything V2, https://arxiv.org/abs/2406.09414
- Bochkovskii et al., Depth Pro, https://arxiv.org/abs/2410.02073
- Hu et al., Metric3D v2, https://arxiv.org/abs/2404.15506
- Piccinelli et al., UniDepthV2, https://arxiv.org/abs/2502.20110 ; UniDepth, https://arxiv.org/abs/2403.18913
- Gasperini et al., md4all (night and rain depth), https://arxiv.org/abs/2308.09711
- MoGe-2, https://arxiv.org/abs/2507.02546
- Depth Anything 3, https://arxiv.org/abs/2511.10647
- SANPO dataset, https://arxiv.org/abs/2309.12172
- Qin et al., VINS-Mono, https://arxiv.org/abs/1708.03852
- Herath et al., RoNIN, https://arxiv.org/abs/1905.12853
- PX4 ECL EKF2 tuning guide, https://docs.px4.io/main/en/advanced_config/tuning_the_ecl_ekf.html
- Meta Wearables DAT docs: streaming, camera stream processing, motion, microphones and speakers, Android and iOS integration (https://wearables.developer.meta.com/docs/develop/dat/...)
- Koo & Kwon 2023, unplanned gait termination, https://pmc.ncbi.nlm.nih.gov/articles/PMC9953996/

Not opened (cited from search snippets or as classics only):
- Lee 1976, "A theory of visual control of braking based on information about time-to-collision", Perception 5:437-459
- Grossman et al. 1988, Exp Brain Res, https://link.springer.com/article/10.1007/BF00247595
- Pundlik et al. 2021, JAMA Ophthalmology RCT, doi:10.1001/jamaophthalmol.2021.2624
- Mixed-news report on the DAT 1.0 frame-freeze changelog
