# 05 — On-device perception models for GuideGlass

Research date: 2026-11-11. **Nothing in this file was measured by me.** The optional desktop-CPU proxy measurement was skipped because the time budget was tight. Every number below is **read** from the source cited next to it (evidence rung L0 in the repo's terms). Vendor latency numbers time the model alone, on the vendor's own test harness. They do not include YUV-to-tensor conversion, pre- and post-processing, decoding the DAT stream, or thermal throttling.

## Question

Which on-device models should a phone run at 7–30 fps on frames of 360×640 to 720×1280 for each of these tasks?

- (a) Detecting pedestrian-scale hazards.
- (b) Segmenting walkable surface, sidewalk, curb and stairs.
- (c) Monocular depth, both relative and metric.
- (d) Small vision-language models (VLMs) for non-urgent scene description.

Also: which runtimes should run them, and what is known about thermal and battery behaviour under sustained load?

Context from 06-meta-glasses-platform.md: DAT delivers 360×640, 504×896 or 720×1280 portrait frames at 2–30 fps. The frames are already undistorted and stabilized on the glasses. The SDK reports no camera intrinsics, and it may lower the resolution or frame rate without warning.

## Findings

1. **ML Kit Object Detection, our current detector, is the wrong tool for hazards.**
   - **Claim.** The base model's coarse classifier knows only five categories: "home goods, fashion goods, food, plants, and places". It has no person, vehicle, pole, bicycle or barrier class. The docs say custom image classifiers can be plugged in, but only as classifiers applied to the detected boxes. No latency figure is published; the docs say only "intended for use in real-time applications, even on lower-end devices".
   - **Source.** https://developers.google.com/ml-kit/vision/object-detection
   - **Confidence.** High: primary docs.
   - **Design impact.** Replace ML Kit as the safety detector. It could at most remain a fallback for unclassified "something is there" boxes.

2. **MediaPipe Object Detector (EfficientDet-Lite / SSD MobileNetV2) is the easiest permissive drop-in, but it is slow on CPU.**
   - **Claim.** Official Pixel 6 benchmark table (all values read):

     | Model | Input | Pixel 6 CPU | Pixel 6 GPU |
     |---|---|---|---|
     | EfficientDet-Lite0 fp32 | 320×320 | 61.30 ms | 27.83 ms |
     | EfficientDet-Lite0 int8 | 320×320 | 29.31 ms | — |
     | EfficientDet-Lite2 fp32 | 448×448 | 197.98 ms | 41.15 ms |
     | EfficientDet-Lite2 int8 | 448×448 | 70.91 ms | — |
     | SSD MobileNetV2 fp32 | 256×256 | 36.30 ms | 24.01 ms |

     The models use COCO classes, which include person, bicycle, car, bench, fire hydrant, stop sign and others. The page states no license or file size.
   - **Source.** https://developers.google.com/edge/mediapipe/solutions/vision/object_detector
   - **Confidence.** High for the numbers.
   - **Design impact.** EfficientDet-Lite0 on GPU (about 28 ms, so roughly 30 fps of headroom) is a low-risk first upgrade. It reaches about 20–30 fps on a Tensor-class phone. COCO classes still miss poles, bollards, curbs and open manholes.

3. **The YOLO family is fast on Snapdragon NPUs, but Ultralytics YOLOv8/11 is AGPL-3.0.**
   - **Claim.** Qualcomm AI Hub YOLOv11-Detection:
     - Input 640×640; 2.64 M parameters; 10.1 MB float, 2.83 MB w8a8.
     - TFLite NPU: Snapdragon 8 Gen 3 at 2.622 ms float and 1.097 ms w8a8; Snapdragon 8 Elite at 1.977 ms and 0.805 ms.
     - License "agpl-3.0".
     - The Ultralytics repo LICENSE begins "GNU AFFERO GENERAL PUBLIC LICENSE Version 3".
   - **Source.** https://huggingface.co/qualcomm/YOLOv11-Detection ; https://github.com/ultralytics/ultralytics/blob/main/LICENSE
   - **Confidence.** High.
   - **Design impact.** AGPL applies to the trained weights and to an app that distributes them. Fine for an internal research prototype if the code is published. Avoid it if GuideGlass may ever be closed-source. Prefer the Apache-2.0 alternatives below.

4. **YOLOX (Apache-2.0) gets near-YOLO NPU latency with no copyleft.**
   - **Claim.** Qualcomm AI Hub Yolo-X:
     - 640×640; 8.98 M parameters; 34.3 MB float, 8.96 MB w8a8.
     - TFLite NPU: 8 Gen 3 at 4.87 ms float and 1.584 ms w8a8; 8 Elite at 3.797 ms and 1.166 ms.
     - ONNX Runtime w8a8 on 8 Gen 3: 6.146 ms.
     - License apache-2.0. The upstream Megvii YOLOX LICENSE is Apache 2.0.
   - **Source.** https://huggingface.co/qualcomm/Yolo-X ; https://github.com/Megvii-BaseDetection/YOLOX
   - **Confidence.** High.
   - **Design impact.** This is the primary candidate for the hazard detector on Snapdragon. Even at 1/10 of the NPU speed (CPU fallback), it would still sit around 50 ms. Fine-tune on pedestrian-hazard classes.

5. **Apache-2.0 DETR-family detectors: RF-DETR N/S/M and D-FINE are permissive. The smallest and largest RF-DETR sizes are not.**
   - **Claim.**
     - RF-DETR README (NVIDIA T4, TensorRT FP16, batch 1):
       - RF-DETR-N: 384×384, 48.4 COCO AP, 2.3 ms, 30.5 M parameters, Apache 2.0.
       - RF-DETR-S: 512×512, 53.0 AP, 3.5 ms, Apache 2.0.
       - RF-DETR-Seg-N: 312×312, 40.3 AP, 3.4 ms, Apache 2.0.
     - "RF-DETR Atto, Femto, Pico, XL and 2XL" are under the Roboflow "PML 1.0" license, where commercial self-hosting needs an Enterprise add-on.
     - The D-FINE and RT-DETR repo LICENSE files are Apache 2.0.
     - NanoDet is Apache 2.0.
     - The YOLO-NAS weights are under Deci's own "non-exclusive, revocable ... limited" license, which is **not** open source.
     - I found no published phone latency for RF-DETR, D-FINE or RT-DETR.
   - **Source.**
     - https://github.com/roboflow/rf-detr
     - https://github.com/Peterande/D-FINE (LICENSE)
     - https://github.com/lyuwenyu/RT-DETR (LICENSE)
     - https://github.com/RangiLyu/nanodet (LICENSE)
     - https://github.com/Deci-AI/super-gradients/blob/master/LICENSE.YOLONAS.md
   - **Confidence.**
     - Licenses: high.
     - Phone viability: low, because only server-GPU numbers exist.
   - **Design impact.** RF-DETR-N is a second candidate, attractive because it also comes in a segmentation variant. Transformer attention often runs poorly on mobile NPU delegates, so we must benchmark it ourselves before committing. Avoid YOLO-NAS.

6. **Cityscapes-trained real-time segmentation already runs fast on NPUs and includes road and sidewalk classes.**
   - **Claim.**
     - Qualcomm FFNet-40S: Cityscapes; it "segments street scene images with per-pixel classes like road, sidewalk, and pedestrian". Input 2048×1024; 13.9 M parameters; 13.5 MB w8a8; BSD-3-Clause. TFLite w8a8 runs in 5.323 ms on 8 Gen 3 and 3.986 ms on 8 Elite.
     - Qualcomm DDRNet23-Slim: 2048×1024; 6.13 M parameters; 19 classes; MIT. TFLite float runs in 22.584 ms on 8 Gen 3.
     - Neither card states mIoU.
   - **Source.** https://huggingface.co/qualcomm/FFNet-40S ; https://huggingface.co/qualcomm/DDRNet23-Slim
   - **Confidence.**
     - Latency: high.
     - Accuracy on our egocentric view: low. Cityscapes is shot from a car's viewpoint.
   - **Design impact.**
     - These run at 2048×1024. At 504×896 the cost should drop roughly with pixel count, by about 4.6×. That is an inference, not measured.
     - Use FFNet-40S or DDRNet as a walkable-surface baseline, but fine-tune it on a pedestrian-view sidewalk dataset (see finding 7).
     - Cityscapes has no curb, stairs or drop-off classes.

7. **Pedestrian-view sidewalk data exists, but curb and stairs remain unsolved.**
   - **Claim.**
     - SENSATION-DS (arXiv 2607.21137, 2026-07-23): 2,752 chest-height image-mask pairs and a 9-class taxonomy, defined with a blind co-author. Sources include Mapillary Vistas.
     - Its best offline result is UPerNet-MobileNetV3 at mIoU 0.715 ± 0.006.
     - DeepLabV3Plus-MobileNetV3 has the lowest "Road-as-Sidewalk Error Rate" (0.079) and ran at 7.383 FPS at 512×384 on Android via ONNX. The phone model is not given. Code and weight availability is not stated.
     - Curb segmentation study (arXiv 2406.07202): foundation models had "high false-positive rates (up to 95%)" and the best took "3.70-second inference time".
     - Other datasets: SideGuide and VIDVIP are sidewalk datasets cited in arXiv 2409.11164. GuideTWSI (arXiv 2603.07060) covers tactile paving. I have not opened those three papers myself.
   - **Source.** https://arxiv.org/abs/2607.21137 ; https://arxiv.org/abs/2406.07202 ; search results pointing to arXiv 2409.11164 and 2603.07060.
   - **Confidence.**
     - SENSATION and the curb study: medium, from abstracts only.
     - The other datasets: low, not opened.
   - **Design impact.**
     - A MobileNetV3 DeepLabV3+ or UPerNet trained on SENSATION-DS plus Mapillary Vistas is a realistic walkable-surface model.
     - Its **safety metric**, the road-as-sidewalk error, should be ours too, rather than mIoU.
     - Curb, step and drop-off detection should **not** rely on segmentation alone. Combine depth discontinuity (finding 9) with head pitch.
     - Budget our own labelling of curbs and stairs from DAT footage.

8. **Promptable segmenters (EdgeSAM, MobileSAM, FastSAM) are not suitable as the per-frame safety path.**
   - **Claim.**
     - EdgeSAM README: "the first SAM variant that can run at over 30 FPS on an iPhone 14", at 38.7 FPS vs 4.9 FPS for MobileSAM on iPhone 14. 9.6 M parameters. License "NTU S-Lab License 1.0". Core ML and ONNX exports are available.
     - These models need prompts (points or boxes) and output class-agnostic masks.
     - FastSAM is built on Ultralytics, so it is AGPL by inheritance. I did not verify its LICENSE file.
   - **Source.** https://github.com/chongzhou96/EdgeSAM
   - **Confidence.**
     - EdgeSAM numbers: high.
     - FastSAM license: low.
   - **Design impact.** At most, use one to refine a detector box into a mask for description. Do not use these for walkable-surface classification. Check whether the S-Lab license permits our use; I believe it is non-commercial-oriented, but did not verify.

9. **Depth Anything V2 Small (Apache-2.0) is the best-documented relative-depth model on both phone platforms. Base and Large are CC-BY-NC.**
   - **Claim.**
     - Upstream README: Small has 24.8 M parameters and "is under the Apache-2.0 license". "Base/Large/Giant models are under the CC-BY-NC-4.0 license."
     - Metric models were released "based on Depth-Anything-V2-Small and Base". Their licenses are not stated on the README.
     - Qualcomm AI Hub Depth-Anything-V2 (518×518, 24.7 M parameters, 94.3 MB float):
       - TFLite float: 30.652 ms on 8 Gen 3, 21.505 ms on 8 Elite.
       - QNN w8a16: 22.348 ms on 8 Gen 3, 15.138 ms on 8 Elite.
     - Apple Core ML (float16, 49.8 MB, Neural Engine): 31.10 ms on iPhone 12 Pro Max and 33.90 ms on iPhone 15 Pro Max.
   - **Source.** https://github.com/DepthAnything/Depth-Anything-V2 ; https://huggingface.co/depth-anything/Depth-Anything-V2-Small ; https://huggingface.co/qualcomm/Depth-Anything-V2 ; https://huggingface.co/apple/coreml-depth-anything-v2-small
   - **Confidence.** High.
   - **Design impact.**
     - About 22–34 ms means depth can run at around 10–15 fps alongside detection. That is fine for an obstacle-proximity and drop-off channel at a lower rate than detection.
     - Its output is *relative* inverse depth. Converting to metres needs a scale: ground-plane fit with known camera height, or a metric head.
     - Never ship the Base or Large weights.

10. **MiDaS v2 small is about 15× cheaper than DA-V2 and MIT-licensed. Use it as the low-power or thermal fallback.**
    - **Claim.** Qualcomm Midas-V2: 256×256 input; 16.6 M parameters; 63.2 MB float, 16.9 MB w8a8; MIT. TFLite float runs in 1.955 ms on 8 Gen 3 and 1.474 ms on 8 Elite. TFLite w8a8 runs in 0.751 ms on 8 Gen 3.
    - **Source.** https://huggingface.co/qualcomm/Midas-V2
    - **Confidence.** High for latency. Its accuracy is lower than DA-V2, but I did not open a comparison table.
    - **Design impact.** A near-free depth prior that can run every frame. Use it when the phone or glasses report thermal pressure.

11. **Metric depth: the strongest models are too heavy or have restrictive licenses. Use relative depth plus geometry.**
    - **Claim.**
      - Metric3D: LICENSE is "BSD 2-Clause".
      - UniDepth: LICENSE is "Attribution-NonCommercial 4.0 International", which is **non-commercial**.
      - Apple Depth Pro: LICENSE is Apple's custom sample-code-style license ("This Apple software is supplied to you by Apple Inc. ... in consideration of your agreement to the following terms"). Read the full text before use.
      - I found no published phone latency for Metric3D v2, UniDepth or Depth Pro.
      - The DA-V2 metric fine-tunes exist for Small and Base, but their licenses are unverified.
    - **Source.** LICENSE files at github.com/YvanYin/Metric3D, github.com/lpiccinelli-eth/UniDepth and github.com/apple/ml-depth-pro.
    - **Confidence.**
      - Licenses: high.
      - Their phone feasibility: low, no data.
    - **Design impact.** Recover metric scale from relative depth with a ground-plane and camera-height prior. That needs our own per-model intrinsics, because DAT gives none. If a learned metric head is needed, evaluate DA-V2-Small-metric (check the license first) or Metric3D-small. Do not use UniDepth.

12. **On-device VLMs: Gemini Nano through ML Kit GenAI is easiest on Android, but it is foreground-only, quota-limited, beta, English-only and device-limited.**
    - **Claim.**
      - Image Description API: `com.google.mlkit:genai-image-description:1.0.0-beta1`, with `ImageDescription.getClient(options)`, `checkFeatureStatus()`, `downloadFeature(...)` and `runInference(request, callback)`.
      - It runs Gemini Nano through AICore. It is "English only" and gives one short description per image, with no custom prompt.
      - The Prompt API (`com.google.mlkit:genai-prompt`) accepts image plus text.
      - The GenAI overview says inference is "permitted only when the app is the top foreground application". Calls from the background, "including from a foreground service", return `ErrorCode.BACKGROUND_USE_BLOCKED`.
      - AICore enforces per-app quotas (`BUSY`) and `PER_APP_BATTERY_USE_QUOTA_EXCEEDED`.
      - Devices are limited to lists such as the Galaxy S25 series and Pixel 10/11.
      - No latency figure is published.
    - **Source.** https://developers.google.com/ml-kit/genai/image-description/android ; https://developers.google.com/ml-kit/genai/prompt/android ; https://developers.google.com/ml-kit/genai
    - **Confidence.** High.
    - **Design impact.**
      - Usable for "describe what's ahead" only while the app is visible on screen.
      - It conflicts with running in a foreground service with the screen off, so it cannot be the main description path for a pocketed phone. Treat it as opportunistic.
      - Never put it on the safety path.

13. **Open small VLMs (SmolVLM, Moondream2, FastVLM) have permissive or research licenses but no trustworthy phone-latency numbers.**
    - **Claim.**
      - SmolVLM-256M-Instruct: "0.3B params", Apache 2.0, "64 visual tokens per 512×512 patch", "under 1GB of GPU RAM" per image, with ONNX weights. No phone latency is given.
      - The Moondream repo LICENSE is Apache 2.0. Newer Moondream releases may have different weight terms; not checked.
      - Apple FastVLM (0.5B/1.5B/7B): "85x faster Time-to-First-Token" than LLaVA-OneVision-0.5B, and ships an iOS demo app. Its LICENSE_MODEL says the weights are released "for the sole purpose of scientific research", so they are **research-only**.
      - I did not find official on-device numbers for PaliGemma or Gemma 3n image input.
    - **Source.** https://huggingface.co/HuggingFaceTB/SmolVLM-256M-Instruct ; https://github.com/vikhyat/moondream ; https://github.com/apple/ml-fastvlm (README and LICENSE_MODEL)
    - **Confidence.** Medium.
    - **Design impact.**
      - SmolVLM-256M through ONNX Runtime is the only VLM candidate that is fully permissive and not tied to one vendor. It runs in the background on both OSes.
      - We must measure its seconds-per-description ourselves.
      - FastVLM is acceptable for a research study on iOS only.

14. **Runtimes: LiteRT with `CompiledModel` and NPU/GPU accelerators is the Android default. NPU needs API 31+.**
    - **Claim.**
      - LiteRT NPU guide: `CompiledModel.create(...)` with `CompiledModel.Options(Accelerator.NPU, Accelerator.GPU)`, so the GPU is the fallback.
      - "API level 31+ is required for NPU support."
      - Vendors: Google Tensor (AOT only), Qualcomm AI Engine Direct, MediaTek NeuroPilot, among others.
      - Maven: `com.google.ai.edge.litert:litert`, `litert-gpu`, and NPU runtime packages from release 2.3.0.
      - Qualcomm AI Hub publishes the same models for TFLite, QNN_DLC and ONNX. The tables above show TFLite and QNN performing similarly.
      - ONNX Runtime numbers are in the same range for YOLOX float (9.082 ms on 8 Gen 3), but slower for w8a8 DDRNet.
      - ExecuTorch and Core ML were not benchmarked here beyond the Apple DA-V2 card.
    - **Source.** https://developers.google.com/edge/litert/next/npu?hl=en ; Qualcomm AI Hub HF cards listed above.
    - **Confidence.** High for the API facts; medium for "TFLite ≈ QNN".
    - **Design impact.**
      - The app's minSdk is 29 but the NPU path needs 31. Gate NPU at runtime with a GPU then CPU fallback.
      - Use one `.tflite` per model and pin the LiteRT version.
      - On iOS use Core ML. ONNX Runtime Mobile is the cross-platform fallback, and the required one for SmolVLM.

15. **No primary source I found measures sustained (more than 10 min) thermal or battery behaviour of these models on phones.**
    - **Claim.** The Qualcomm AI Hub cards report single-inference latency and peak memory only. The ML Kit GenAI docs mention battery quotas but no rates. I found no vendor table for sustained throughput under throttling.
    - **Source.** The pages above.
    - **Confidence.** Medium, since this is an absence of evidence.
    - **Design impact.**
      - Plan a 30-minute soak test on our target phone with detection, segmentation and depth running.
      - Make the pipeline duty-cycle-aware: run detection every frame, segmentation and depth at about 5–10 fps, and the VLM only on demand.
      - Step down using the phone's `PowerManager` thermal status and the glasses' `ThermalLevel`.

16. **Per-model latencies on Snapdragon NPUs are small next to the system latency we haven't measured.**
    - **Claim.** The detection + segmentation + depth stack on 8 Gen 3 (YOLOX w8a8 1.6 ms + FFNet-40S w8a8 5.3 ms + DA-V2 QNN w8a16 22.3 ms) adds up to about 29 ms of model time. That is arithmetic on the read numbers. No Pixel or Tensor equivalents were found except the MediaPipe Pixel 6 table. Bluetooth video transport and A2DP output latency are unknown (see doc 06).
    - **Source.** Findings 2, 4, 6 and 9.
    - **Confidence.** Medium. These are model-only numbers, not end to end.
    - **Design impact.** The bottleneck is likely transport, pre-processing and audio, not inference. Instrument end-to-end age-of-cue first. Also test on a mid-range or Tensor phone, because Snapdragon flagship NPU numbers flatter the result.

## Disagreements between sources

- **DA-V2 license.** The Qualcomm AI Hub card tags Depth-Anything-V2 as "MIT", while the upstream README says Small is Apache-2.0 and Base/Large are CC-BY-NC-4.0. Qualcomm's tag likely covers its wrapper code. The weights' license comes from upstream.
- **Qualcomm card licenses.** The FFNet card says BSD-3-Clause and DDRNet says MIT. Both point back to the upstream licenses, which I did not open.
- **RF-DETR licensing differs by size.** N/S/M are Apache 2.0, while Atto/Femto/Pico/XL/2XL are PML 1.0. Blogs often say "RF-DETR is Apache" without this caveat.
- **EdgeSAM speed.** The README's "14 times as fast as MobileSAM" does not match its own iPhone 14 table (38.7 vs 4.9 FPS, about 7.9×).
- **Input size.** Apple's DA-V2 Core ML card evaluates at 518×396. Qualcomm's uses 518×518. Latencies are not directly comparable.

## Open questions

- Phone latency of YOLOX, RF-DETR-N, DA-V2-Small and SmolVLM on a **Pixel (Tensor)** and a mid-range Snapdragon 7-series. None was found.
- Licenses of the DA-V2 metric checkpoints and of the original FFNet and DDRNet weights.
- Whether SENSATION-DS images and weights are downloadable, and on what terms.
- Real accuracy of Cityscapes or COCO models on DAT's stabilized 9:16 head-mounted view, especially of near-field ground (curbs, steps).
- Gemini Nano image-description latency and its daily quota size on supported devices.
- Sustained thermal behaviour of the phone with DAT decoding plus three models.

## Recommended shortlist (for the next prototype iteration)

1. **Hazard detection: YOLOX-S or Nano (Apache-2.0) through LiteRT with NPU → GPU → CPU fallback.** It is about 1.6 ms w8a8 on 8 Gen 3 at 640² (read). Fine-tune on pedestrian hazards (poles, bollards, bins, scooters, open doors, people, cyclists). Interim: EfficientDet-Lite0 through MediaPipe, about 28 ms on Pixel 6 GPU (read).
2. **Walkable surface: DeepLabV3+ or UPerNet with a MobileNetV3 backbone,** trained on Mapillary Vistas plus SENSATION-DS. Evaluate with the road-as-sidewalk error rate. FFNet-40S (BSD-3) is a ready-made Cityscapes baseline.
3. **Depth: Depth Anything V2 Small (Apache-2.0),** about 22–34 ms (read), at 5–10 fps. Combine it with a ground-plane fit plus head-pitch from DAT Motion or the phone IMU, for drop-off and curb cues and metric scale.
4. **Description (non-urgent): ML Kit GenAI Image Description** when the app is in the foreground on supported devices. Otherwise SmolVLM-256M through ONNX Runtime. Never on the safety path.
5. **Runtimes:** LiteRT `CompiledModel` on Android and Core ML on iOS. Use ONNX Runtime only where LiteRT has no path.

## Fallback list

- **MiDaS v2 small** (MIT; about 2 ms on NPU, read): depth under thermal pressure or on low-end phones.
- **SSD MobileNetV2 / EfficientDet-Lite0 int8** (Pixel 6 CPU 29–36 ms, read): detector when no GPU or NPU delegate is available.
- **DDRNet23-Slim** (MIT): alternative Cityscapes segmenter.
- **RF-DETR-N or RF-DETR-Seg-N** (Apache-2.0): only after we benchmark it on a phone NPU ourselves.
- **YOLOv8/11** (AGPL-3.0): only if the project accepts copyleft; not recommended.
- **Excluded:** YOLO-NAS (Deci license), UniDepth (CC-BY-NC), DA-V2 Base/Large (CC-BY-NC), FastVLM weights (research-only) except in research builds, EdgeSAM (S-Lab license, unverified).

## Sources

- https://developers.google.com/ml-kit/vision/object-detection
- https://developers.google.com/edge/mediapipe/solutions/vision/object_detector
- https://huggingface.co/qualcomm/YOLOv11-Detection
- https://huggingface.co/qualcomm/Yolo-X
- https://huggingface.co/qualcomm/FFNet-40S
- https://huggingface.co/qualcomm/DDRNet23-Slim
- https://huggingface.co/qualcomm/Depth-Anything-V2
- https://huggingface.co/qualcomm/Midas-V2
- https://huggingface.co/apple/coreml-depth-anything-v2-small
- https://huggingface.co/depth-anything/Depth-Anything-V2-Small
- https://github.com/DepthAnything/Depth-Anything-V2
- https://github.com/roboflow/rf-detr
- https://github.com/chongzhou96/EdgeSAM
- https://github.com/apple/ml-fastvlm (README, LICENSE_MODEL)
- https://huggingface.co/HuggingFaceTB/SmolVLM-256M-Instruct
- LICENSE files: github.com/ultralytics/ultralytics, Megvii-BaseDetection/YOLOX, Peterande/D-FINE, lyuwenyu/RT-DETR, RangiLyu/nanodet, Deci-AI/super-gradients (LICENSE.YOLONAS.md), YvanYin/Metric3D, lpiccinelli-eth/UniDepth, apple/ml-depth-pro, vikhyat/moondream
- https://developers.google.com/ml-kit/genai ; https://developers.google.com/ml-kit/genai/image-description/android ; https://developers.google.com/ml-kit/genai/prompt/android
- https://developers.google.com/edge/litert/next/npu?hl=en
- https://arxiv.org/abs/2607.21137 (SENSATION-DS) ; https://arxiv.org/abs/2406.07202 (curb segmentation)
- Search results (not opened): arXiv 2409.11164 (SideGuide/VIDVIP synthetic data), arXiv 2603.07060 (GuideTWSI)
