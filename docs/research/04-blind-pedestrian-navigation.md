# 04 — What a Blind Pedestrian Needs from A-to-B Navigation, and Where Maps, GPS and Heading Fail

Research date: 2026-11-11. Status: desk research, partly verified. Every number is tagged **[read]** (from a source I opened) or **[est]** (my estimate or derivation). Sources I could not open are marked "not opened" and are not relied on for numbers. Evidence rung for all numbers here: L0 (read or assumed).

## Question

What does a blind pedestrian actually need from A-to-B street navigation, and where do maps, GPS and heading fail?

## Findings

1. **Head-level collisions are common, and neither cane nor guide dog prevents them.** Manduchi & Kurniawan (2011) surveyed 307 legally blind or blind respondents. 13% had head-level accidents at least once a month, and 7% had falls at least once a month [read]. Only 2% of blind respondents had *never* had a head-level accident [read]. 86% of head-level accidents happened outdoors, caused mostly by tree branches, then poles and signs, construction equipment and trucks [read]. 23% had medical consequences, such as stitches, staples or broken teeth [read]. For falls, 36% had medical consequences, 51% changed walking habits and 30% lost confidence [read]. Cane and dog-guide users did *not* differ in accident frequency (p = .425) [read]. The sample skewed young and expert [read].
   - Sources: https://users.soe.ucsc.edu/~manduchi/papers/MobilityAccidents.pdf ; https://tr.soe.ucsc.edu/research/technical-reports/UCSC-SOE-10-24
   - Confidence: high. Primary paper read in full (self-report survey).
   - Design impact: Head and chest height above the cane arc is GuideGlass's clearest value-add, since the glasses camera sits at eye level. Make overhead obstacles (branches, signs, truck mirrors) a first-class detection class with its own cue ("Head height, 12 o'clock").

2. **Without guidance, blind pedestrians veer far outside a crosswalk within one crossing.** Guth et al. (2017) summarize a synthesis of eight pre-1990 studies: at 72 ft (22 m) the average blind or blindfolded walker was about 16.5 ft (5.0 m) left or right of the intended path [read]. In their own study (11 cane users, 72 ft path), mean absolute error at 72 ft was 140.7 ft without a beacon versus 28.2 ft with one (likely a lateral-plus-path error measure; units as reported) [read]. At 36 ft it was 41.3 ft versus 21.0 ft [read]. Snow did not change veer but increased steps (42.4 per trial) [read]. There was no consistent left or right bias, meaning veer is mostly variable rather than constant error [read].
   - Sources: https://pmc.ncbi.nlm.nih.gov/articles/PMC5753772/
   - Confidence: high. Primary paper read.
   - Design impact: Expect a lateral deviation of about 2 m per 10 m walked without cues [est, from 5.0 m at 22 m]. Veer is random, not biased, so correct it from *measured* deviation, not a fixed offset. Within a 3 m crosswalk, drift becomes significant after about 7 m [est].

3. **Even at accessible-signal (APS) intersections, blind pedestrians spend much of the crossing outside the crosswalk. An audible beacon at the far end halves this.** In Barlow et al. (as summarized in Guth 2017), crosswalks were 56–115 ft (17.1–35.1 m) long and typically 10 ft (3.0 m) wide [read]. With standard APS, participants were outside the crosswalk on 59.7% of measurements, and 6 ft or more outside on 48.5% of those [read]. With a beaconing APS the figures were 26.1% and 16.5% [read]. US federal guidance (MUTCD 4E.13, via Guth) says beaconing should be considered for crosswalks longer than 70 ft, skewed crosswalks and irregular geometry [read].
   - Sources: https://pmc.ncbi.nlm.nih.gov/articles/PMC5753772/ (Barlow et al. original not opened)
   - Confidence: high (secondary summary inside a primary paper).
   - Design impact: Mid-crossing heading guidance is a genuine need. The safe form is a far-side *aiming* cue (like a beacon) from vision or map. Keep it subordinate to traffic sounds and never let it imply the crossing is safe.

4. **Telling a blind user "walk now" from a camera is high-risk, and the literature has no field-validated false-positive rate.** Ivanchenko, Coughlan & Shen's walk-light detector was tested with a single blind volunteer. It added a rule requiring the Walk icon to sit near and below a green light specifically to reduce false positives, and the experimenters saw "occasional, isolated false positives" [read]. A 2024 Technion paper notes that a false-positive green wrongly indicates it is safe to cross, so precision matters more than recall (search snippet). Hong & Coughlan (W4A 2024) note that computer-vision apps make false positive and negative recognitions (snippet; PDF fetch returned an HTML stub).
   - Sources: https://pmc.ncbi.nlm.nih.gov/articles/PMC4778721/ ; https://sipl.ece.technion.ac.il/wp-content/uploads/2024/06/Pedestrian-Traffic-Light-Detection.pdf (fetched, not read in detail) ; https://ski.org/sites/default/files/publications/2024_W4A_VRTraining_preprint.pdf (not opened)
   - Confidence: high on the risk; no published per-crossing error rate was found.
   - Design impact: GuideGlass must never say "walk", "safe to cross" or "clear". At most it may report observed signal state with hedging ("Signal looks like walk"), as an optional off-by-default feature. The crossing decision stays with the user's traffic listening, the APS and their O&M training.

5. **Quiet vehicles shorten the auditory warning that blind pedestrians rely on for crossing decisions.** Wall Emerson et al. (2011) studied blind pedestrians at an uncontrolled crossing. Approaching under 20 mph, a Toyota Prius hybrid was detected only about 2 s away (58.6 ft) [read]. At higher speeds, hybrids and internal-combustion (ICE) cars were detected 4–5 s away, which is still less than the 6 s needed to cross a 24 ft two-lane road at 4 ft/s [read]. Blind participants made poor gap decisions in 23.8% of cases versus 14.2% for sighted participants, and potentially dangerous decisions in 1% versus 0.2% [read]. Only 50–75% of parallel traffic surges containing ICE vehicles were detected within 2 s [read]. (NHTSA has since required pedestrian alert sounds on new EVs; that page was not opened.)
   - Sources: https://pmc.ncbi.nlm.nih.gov/articles/PMC3046409/
   - Confidence: high. Primary paper read; small sample, older hybrids.
   - Design impact: An open-ear cue that masks even 1–2 s of vehicle sound takes away a meaningful share of the user's margin. Keep silence near the curb (see 03, finding 14). Vision-based detection of moving vehicles and bikes is valuable as a *caution*, not as a crossing permission.

6. **Route APIs do not reliably model sidewalks, crossings or side of street, and Google requires apps to warn users of this.** The Google Routes API says WALK routes are in beta and "might sometimes be missing clear sidewalks", and apps must display this warning to the user [read; page updated 2026-09-04]. The Directions API (Legacy) says walking uses "pedestrian paths & sidewalks (where available)", and the result returns warnings that must be displayed [read]. Neither API gives which side of the street the route uses, crossing type, curb ramps, or entrance location as structured fields (none were seen in the opened docs) [read, absence noted].
   - Sources: https://developers.google.com/maps/documentation/routes/reference/rest/v2/RouteTravelMode ; https://developers.google.com/maps/documentation/directions/get-directions
   - Confidence: high.
   - Design impact: Treat the Google route as a street-level *corridor*, not a sidewalk path. Speak the mandated caveat once at route start in plain words. Do not issue crossing instructions from route geometry alone; announce "intersection ahead" and leave the crossing to the user.

7. **Sidewalk-level open data exists, but coverage is patchy.** OpenStreetMap supports separately mapped sidewalks (`footway=sidewalk`), crossings (`footway=crossing`, `crossing=*`), `tactile_paving=yes/no/partial`, and `traffic_signals:sound` for audible signals [read]. The wiki notes there is no consensus on attaching street names to separately mapped sidewalks and that regional customs differ [read]. The OpenSidewalks schema models a routable pedestrian graph: crossings do not connect to sidewalk centerlines, and curb interfaces and curb ramps are mapped at edge endpoints [read].
   - Sources: https://wiki.openstreetmap.org/wiki/Sidewalks ; https://wiki.openstreetmap.org/wiki/Key:crossing ; https://wiki.openstreetmap.org/wiki/Key:tactile_paving ; https://wiki.openstreetmap.org/wiki/Key:traffic_signals:sound ; https://github.com/OpenSidewalks/OpenSidewalks-Schema
   - Confidence: high on schema; coverage not quantified (Project Sidewalk not opened).
   - Design impact: Where OSM or OpenSidewalks data exists, use it to pre-announce crossings, APS presence and tactile paving ("Crossing ahead, audible signal"). Fall back to Google corridor plus vision otherwise. Log coverage per test route.

8. **Phone GNSS in cities commonly puts the user on the wrong side of the street. Google's 3D-mapping corrections reduce but do not remove this.** Google's Android blog calls wrong-side-of-street and wrong-block errors the biggest location issue in dense cities. 3D-mapping-aided corrections cut wrong-side occurrences by about 75% (v2, Pixel) and about 50% (v1, Android 8+), are rolled into the Fused Location Provider, and at launch supported pedestrian use cases only [read; 2020 post]. Research on smartphones in Hong Kong urban canyons reports cross-street errors of "tens of meters" and multipath errors of more than 50 m in the worst case. Dual-frequency L1/L5 with 3D-map aiding reached about 10 m average, and a 2025 sidewalk-map method reached under 5 m and resolved street side (search snippets; papers not opened).
   - Sources: https://android-developers.googleblog.com/2020/12/improving-urban-gps-accuracy-for-your.html ; https://navi.ion.org/content/68/4/727 (not opened) ; https://ira.lib.polyu.edu.hk/bitstream/10397/105744/1/Weng_GNSS_Fault_Detection.pdf (not opened)
   - Confidence: high for Google's percentages (vendor claim); medium for the urban numbers.
   - Design impact: Never state the side of the street or "you are at the corner" from GNSS alone. Treat urban fixes as 5–20 m [est]. Use vision (curb, crosswalk, building line) and map matching to constrain position. Turn cues need hysteresis and confirmation at the corner.

9. **Android reports accuracy as a 68% radius, so the true error exceeds the number shown about one time in three.** `Location.getAccuracy()` is the horizontal radius at the 68th percentile, and `getBearingAccuracyDegrees()` is a 68% bearing uncertainty [read]. Raw GNSS measurements are mandatory on devices running Android 10 (API 29) or higher [read].
   - Sources: https://developer.android.com/reference/android/location/Location ; https://developer.android.com/develop/sensors-and-location/sensors/gnss
   - Confidence: high. Official documentation.
   - Design impact: For go/no-go logic (e.g. "turn now"), inflate the reported accuracy about 2× to approximate a 95% bound [est, assuming roughly Gaussian error]. Gate turn cues on accuracy, and say "approaching the turn" rather than "turn now" when uncertainty exceeds the distance to the turn.

10. **Visual positioning (VPS / ARCore Geospatial) can fix position and heading far better than GNSS, but documented accuracy varies by platform and it needs network and coverage.** The ARCore iOS docs say "under typical conditions" VPS gives about 5 m position and 5° rotation accuracy [read]. Google's June 2026 Android XR post says the Geospatial API (preview in ARCore for Jetpack XR) gives "sub-meter accuracy and precise orientation in supported areas" [read]. Sources noted it requires internet, VPS coverage, fine location, and a qualifying magnetometer (snippet). I found no documented path to feed a third-party camera stream, such as Meta glasses frames, into ARCore Geospatial; it expects the session's own camera [est, not confirmed either way].
    - Sources: https://developers.google.com/ar/develop/ios/geospatial/enable ; https://android-developers.googleblog.com/2026/06/android-xr-geospatial-api-gemini.html ; https://developer.android.com/develop/xr/jetpack-xr-sdk/arcore/geospatial
    - Confidence: medium. Accuracy claims differ between platforms; glasses-feed support is unverified.
    - Design impact: VPS is a candidate for the "which way am I facing at the start" problem and for side-of-street resolution, but it is off the safety path because it is network-dependent. Prototype it with the *phone* camera first (user holds the phone up once). Treat feeding glasses frames as an open question.

11. **Heading is the hardest part. Three sensors each measure something different, and none is reliable alone.** This combines the API facts above with design reasoning [est]. The phone compass in a pocket reports pocket orientation, not body orientation, and is disturbed by magnetic interference. GNSS course-over-ground exists only while moving; Android gives bearing with a 68% accuracy [read], which is meaningless at standstill. The glasses camera gives *head* direction, and people scan with their heads.
    - Sources: Android Location docs (above); my synthesis
    - Confidence: medium.
    - Design impact: Fuse the three. For the travel direction, use course-over-ground after about 5–10 m of walking [est]. For the start direction, ask the user to face along the street edge or building line (an O&M technique) and confirm with vision or VPS. Do not give left/right correction cues from head yaw alone. Require consistent heading for about 2 s before saying "bear left/right" [est].

12. **Blind travelers combine tools. Apps should complement the cane, dog and O&M skills rather than replace them.** Kameswaran et al. (ASSETS 2020; 23 interviews, 10 diary participants) describe "complementarity": travelers use different apps for different information and granularity, alongside canes and guide dogs (snippet; not opened). Microsoft Soundscape's design used spatial audio callouts and a beacon rather than turn-by-turn instructions (press page; not opened). Manduchi & Kurniawan found 9% of respondents used GPS, and all of them also used a cane or dog [read].
    - Sources: https://www.microsoft.com/en-us/research/?p=677091 (not opened) ; https://news.microsoft.com/features/the-telltale-tap-soundscape-app-brings-3d-audio-map-to-pedestrians-gaining-fans-well-beyond-those-with-vision-loss/ (not opened) ; Manduchi PDF (opened)
    - Confidence: medium.
    - Design impact: Do not duplicate what the cane already does well (surface texture, low obstacles in its arc, curb edges at the tip). Concentrate on what it misses: head height, approaching moving objects, distant drop-offs, and route state. Offer a "quiet mode" with hazards only and no route chatter.

13. **Drop-offs, stairs and platform edges carry severe consequences, and published counts are mostly self-report.** A 2016 Japanese guide-dog-user survey (540 responses) reported that 21% had lost their footing on or stepped off a platform and 5.6% had fallen onto tracks. A 2026 RNIB survey said up to 80% had difficulty with the platform-train gap (both from news snippets; primary reports not opened). Manduchi & Kurniawan list misjudged distances and unexpected obstacles among the causes of falls [read].
    - Sources: https://www.inkl.com/news/four-in-five-blind-people-struggle-with-gap-at-uk-train-stations-survey-finds (not opened) ; Manduchi PDF (opened)
    - Confidence: low for platform numbers (secondary); high that falls are common and injurious.
    - Design impact: Detecting drop-offs and stairs down is a top-tier (P1) cue. Train edges are out of scope for v1 except a generic "drop-off ahead". Do not claim platform safety.

14. **US accessibility standards for the right of way are now in force for some construction, so APS, curb ramps and detectable warnings are increasingly expected but not universal.** The US Access Board timeline shows the PROWAG final rule published August 8, 2023, adopted by GSA in July 2024, and adopted by DOT on December 18, 2024 for new construction and alterations of transit stops in the public right of way [read].
    - Sources: https://www.access-board.gov/prowag/
    - Confidence: high for dates; low for actual coverage of streets on the ground.
    - Design impact: Do not assume APS or truncated domes are present. Prefer map tags (finding 7) and vision to report them, and say "no audible signal mapped" when that is known.

15. **Blind travelers using a cane often walk well below the 1.4 m/s working assumption.** The FHWA table gives a mean of 2.62 ft/s (0.80 m/s) for "cane or crutch user" and 4.0 ft/s (1.2 m/s) for an average adult; this is a mixed mobility-impaired group, not specifically blind users [read]. Skovfoged et al. (2022) cite about 0.9 m/s nominal for white-cane users, about 0.6 m/s on difficult routes, about 1.45 m/s for guide-dog users and about 1.8 m/s with sighted guides (snippet; PDF not opened). Guth et al. found more steps on snow [read].
    - Sources: https://www.fhwa.dot.gov/publications/research/safety/pedbike/05085/pptchapt8.cfm ; https://vbn.aau.dk/files/497212137/The_Cost_of_Knowing_How_Obstacle_Alerts_Reduce_Walking_Speeds_of_Augmented_White_Cane_Users.pdf (not opened)
    - Confidence: medium.
    - Design impact: Make walking speed a measured variable (from step cadence or GNSS) with a default of about 1.0 m/s for cane users and 1.4 m/s for guide-dog users [est]. Keep 1.4 m/s as the worst-case design point. The paper also suggests that frequent obstacle alerts *slow* cane users, which is a cost to track in trials.

16. **What an electronic travel aid should NOT do.** This is a synthesis of findings 1–5, 12 and the brief [est]:
    - Do not decide street crossings ("walk now", "clear").
    - Do not replace cane or dog detection of the immediate path.
    - Do not give continuous audio that masks traffic.
    - Do not state certainty the sensors cannot support, such as side of street from GNSS or heading from head yaw.
    - Do not stay silent when blind: silence must not be the failure mode.
    - Sources: as cited
    - Confidence: medium (synthesis, consistent with O&M emphasis on the user's own traffic judgment).
    - Design impact: Write these down as explicit non-goals in the requirements, each with a test, e.g. a unit test that no cue string contains "safe", "clear" or "walk now".

## Disagreements between sources

- **VPS accuracy.** The ARCore iOS docs say about 5 m / 5° typical, while the Android XR materials (2026) say sub-meter with precise orientation. These are different platforms and conditions, so plan for 5 m / 5°.
- **Guide dog versus cane protection.** Manduchi & Kurniawan found no difference in accident frequency, while Skovfoged's cited speeds (snippet) show dog users walking much faster. Faster travel may mean dog users need *more* lead distance even if their accident rate is similar.
- **Urban GNSS error.** Google reports relative reductions in wrong-side events, while the academic papers report absolute errors from 5 m to over 50 m depending on method and city. There is no single number; it depends on the device, the city and whether dual-frequency is used.
- **Veer metrics.** The pre-1990 synthesis gives about 5 m at 22 m, while Guth 2017's no-beacon absolute error at 72 ft is much larger (140.7 ft). That figure is probably a different error definition or path geometry; I did not resolve it from the text read.

## Open questions

- Can Meta glasses frames (via the Wearables Device Access Toolkit) be fed into any VPS? Is Google's VPS available outside ARCore sessions? This was unverified.
- How accurately can the glasses camera estimate a pedestrian signal's state, and should we ship that at all? The current recommendation is no.
- What are actual BLV preferred walking speeds on our test routes? Measure them.
- Is OSM/OpenSidewalks coverage of crossings, APS and tactile paving adequate in our test city? Measure per route.
- Which initial-orientation procedure do O&M specialists prefer: building-line alignment, a VPS scan, or walking a few meters to get course? Consult a certified O&M specialist (COMS).
- The full texts of Kallie et al. 2007 (step variability) and Barlow et al. (APS veer) were not read; read them for per-step veer values.

## Sources

Opened:
- Manduchi & Kurniawan (2011), Mobility-related accidents. https://users.soe.ucsc.edu/~manduchi/papers/MobilityAccidents.pdf
- Guth et al. (2017), Beaconing reduces veer on snow. https://pmc.ncbi.nlm.nih.gov/articles/PMC5753772/
- Wall Emerson et al. (2011), quiet vehicles and blind pedestrians' crossing decisions. https://pmc.ncbi.nlm.nih.gov/articles/PMC3046409/
- Ivanchenko, Coughlan & Shen, walk-light detection. https://pmc.ncbi.nlm.nih.gov/articles/PMC4778721/
- Google Routes API RouteTravelMode. https://developers.google.com/maps/documentation/routes/reference/rest/v2/RouteTravelMode
- Google Directions API (Legacy). https://developers.google.com/maps/documentation/directions/get-directions
- OSM wiki: Sidewalks. https://wiki.openstreetmap.org/wiki/Sidewalks
- OSM wiki: Key:crossing. https://wiki.openstreetmap.org/wiki/Key:crossing
- OSM wiki: Key:tactile_paving. https://wiki.openstreetmap.org/wiki/Key:tactile_paving
- OSM wiki: Key:traffic_signals:sound. https://wiki.openstreetmap.org/wiki/Key:traffic_signals:sound
- OpenSidewalks Schema. https://github.com/OpenSidewalks/OpenSidewalks-Schema
- Android Developers Blog (2020), urban GPS accuracy. https://android-developers.googleblog.com/2020/12/improving-urban-gps-accuracy-for-your.html
- Android Location reference. https://developer.android.com/reference/android/location/Location
- Android raw GNSS. https://developer.android.com/develop/sensors-and-location/sensors/gnss
- ARCore Geospatial (iOS) enable page. https://developers.google.com/ar/develop/ios/geospatial/enable
- Android Developers Blog (June 2026), Android XR Geospatial. https://android-developers.googleblog.com/2026/06/android-xr-geospatial-api-gemini.html
- US Access Board PROWAG page. https://www.access-board.gov/prowag/
- FHWA pedestrian walking speeds table. https://www.fhwa.dot.gov/publications/research/safety/pedbike/05085/pptchapt8.cfm

Not opened (pointers only):
- Barlow et al. APS veer study
- Kallie et al. 2007
- Kameswaran et al. 2020
- Soundscape research and press
- BlindSquare, Lazarillo, NaviLens, GoodMaps, Aira, Glide, biped, .lumen, WeWALK product documentation (not researched for lack of budget)
- Project Sidewalk
- Skovfoged et al. 2022
- Hsu / Weng / NAVIGATION urban GNSS papers
- Hong & Coughlan 2024
- Japanese platform survey and RNIB 2026 survey (news only)
- NHTSA quiet-car rule page (HTTP stub)
