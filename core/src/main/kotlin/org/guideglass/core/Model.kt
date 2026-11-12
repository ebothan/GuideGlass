package org.guideglass.core

/*
 * Shared types for the decision core. All times are seconds on one monotonic clock supplied by the
 * caller (ADR 0002): the core never reads a wall clock, so a recorded event log replays exactly.
 */

/** Box in normalised image coordinates: centre x, bottom edge y (0 = top, 1 = bottom), width, height. */
data class Box(val cx: Double, val bottom: Double, val wf: Double, val hf: Double)

/**
 * One detection from the perception port, already converted to a metric estimate.
 * [rangeM] is distance along the ground from the camera; [rangeSigmaM] its one-sigma error.
 * [bearingRad] is positive to the right of the camera's optical axis.
 */
data class Detection(
    val cls: String,
    val box: Box,
    val rangeM: Double,
    val rangeSigmaM: Double,
    val bearingRad: Double,
    val widthM: Double = 0.0,
    val headHeight: Boolean = false,
)

/** Timestamps for one frame, filled in as it moves through the pipeline (REQ-19, REQ-27). */
data class StageStamps(
    val capture: Double,
    val receive: Double,
    val perceptionDone: Double,
    var trackDone: Double = Double.NaN,
    var decision: Double = Double.NaN,
)

/**
 * One frame's perception output delivered to the core.
 * [contentHash] lets the health monitor detect frozen streams (same image delivered again).
 * [yawRateRadS] is head yaw rate if known (IMU or image-shift estimate), else null.
 * [headToTravelRad] is the angle from the travel direction to the head direction, if known.
 */
data class FrameInput(
    val seq: Long,
    val stamps: StageStamps,
    val detections: List<Detection>,
    val contentHash: Long,
    val yawRateRadS: Double? = null,
    val headToTravelRad: Double = 0.0,
    val brightness: Double = 1.0,
)

/** Priority: lower number wins (03 cue table, docs/01-architecture.md section 4). */
enum class Priority(val level: Int) { P0(0), P1(1), P2(2), P3(3), P4(4) }

enum class Earcon { STOP, CAUTION, MOVING, LOST, RECOVERED, REDUCED, START, END, TURN, ARRIVE, TICK, NONE }

enum class AudioRoute { GLASSES, PHONE }

enum class CueKind {
    STOP, CAUTION, MOVING_OBJECT,
    CAMERA_LOST, CAMERA_BACK, GLASSES_LOST, GLASSES_BACK, AUDIO_REROUTED, AUDIO_BACK,
    REDUCED, DETECTION_NORMAL, OUT_OF_ODD, OFFLINE, START, END,
    TURN_PRE, TURN_NOW, CROSSING_AHEAD, ARRIVAL, OFF_ROUTE, WRONG_WAY,
    CLOUD, TICK,
}

/**
 * A cue requested by a component. [key] identifies duplicates (same key queued or playing is dropped).
 * [staleAfterS] is how long it may wait in the queue before it is dropped.
 * [captureTime] is the capture time of the frame that caused it, when there is one.
 */
data class Cue(
    val kind: CueKind,
    val priority: Priority,
    val text: String,
    val earcon: Earcon,
    val key: String,
    val requestedAt: Double,
    val staleAfterS: Double,
    val captureTime: Double = Double.NaN,
    val vibrate: Boolean = false,
) {
    /** Estimated playback duration: earcon plus speech at about 180 wpm (03#11, [est]). */
    val durationS: Double get() {
        val words = text.split(' ').count { it.isNotBlank() }
        val earconS = when (earcon) { Earcon.NONE -> 0.0; Earcon.TICK -> 0.05; else -> 0.15 }
        val speechS = if (words == 0) 0.0 else 0.15 + words * 0.33
        return earconS + speechS
    }
}

/** Everything that can be configured; defaults are the [A]/[R] values in docs/00 sections 7-9. */
data class CoreConfig(
    // Timing (docs/00 section 7). Audio latency after the software's last timestamp, measured only on hardware.
    val audioOutLatencyS: Double = 0.20,
    val earconStartS: Double = 0.02,
    val speechStartS: Double = 0.15,
    val nominalPipelineS: Double = 0.70,
    // Lead times at capture with nominal pipeline (docs/00 section 8.3).
    val stopLeadS: Double = 2.5,
    val cautionLeadS: Double = 4.0,
    val closeFloorM: Double = 1.2,
    // Human model used to scale the STOP lead with walking speed (docs/00 section 8.1, typical column).
    val recogniseS: Double = 0.15,
    val reactS: Double = 0.5,
    val stopDistanceM: Double = 0.7,
    val stopMarginM: Double = 0.3,
    val minLeadSpeedMS: Double = 0.5,
    /** False fixes the STOP lead at [stopLeadS] (used only to match the baseline harness's 2.5 m definition). */
    val scaleLeadWithSpeed: Boolean = true,
    /** Movable classes: hold a still-object STOP this long after first sight, unless urgent [A]. */
    val movableHoldS: Double = 0.7,
    val movableUrgentS: Double = 0.4,
    /** Standing this long and then walking re-arms all alerts [A]. */
    val resumeAfterStandS: Double = 1.0,
    /** A frame older than this at decision time is not used (treated as no frame) [A]. */
    val maxUsableFrameAgeS: Double = 1.0,
    // Geometry (repo's walking path definition, kept for comparability).
    val corridorHalfWidthM: Double = 0.45,
    val corridorIncludesObjectWidth: Boolean = true,
    /** Time to contact uses range minus this many standard deviations (1 = pessimistic bound, 0 = mean). */
    val pessimismK: Double = 1.0,
    // Track life in seconds (REQ-05, ADR 0004).
    val confirmAfterS: Double = 0.2,
    /** Alerts may use a coasted (unseen) track for this long; 0.3 s, not 0.6 s: see ADR 0006. */
    val coastS: Double = 0.3,
    val deleteAfterS: Double = 1.0,
    val historyS: Double = 1.0,
    val motionWindowS: Double = 0.5,
    val independentMotionMS: Double = 0.5,
    val fastYawRadS: Double = Math.toRadians(60.0),
    // Alerts (REQ-16).
    val rearmS: Double = 1.2,
    val defaultWalkSpeedMS: Double = 1.4,
    val standingSpeedMS: Double = 0.2,
    // Health (REQ-10 to 12; docs/01 section 3).
    val frameTimeoutS: Double = 0.5,
    val cameraBackAfterS: Double = 3.0,
    val lostRepeatS: Double = 30.0,
    val minFps: Double = 7.0,
    val maxFrameAgeS: Double = 0.3,
    val reducedEnterS: Double = 2.0,
    val recoverAfterS: Double = 5.0,
    val lowBrightness: Double = 0.15,
    val oddRepeatS: Double = 60.0,
    // Arbitration (REQ-17).
    val nonUrgentGapS: Double = 4.0,
    /** No new STOP within this time of the last one [A]. */
    val stopRefractoryS: Double = 1.5,
)
