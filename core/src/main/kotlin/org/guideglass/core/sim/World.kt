package org.guideglass.core.sim

import org.guideglass.core.Box
import org.guideglass.core.Detection
import org.guideglass.core.perception.Camera
import org.guideglass.core.perception.DepthSample
import org.guideglass.core.perception.PitchEstimate
import org.guideglass.core.perception.Ranger
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/*
 * Synthetic world used as the frame source and perception test double (ADR 0002).
 * Geometry follows the repo's simulator (paper/simulation/sim_reflex.py): pinhole camera, 60 x 46
 * degree field of view, camera height 1.3 m, box noise 10% on size and 3% on position.
 * The perception double assumes the detector finds the object with probability [Perception.pDetect];
 * everything it reports is evidence about the decision logic only (rung L1), not about a real detector.
 */

/** World frame: x forward along the initial travel direction, y to the right, metres. */
data class Obstacle(
    val id: Int,
    val cls: String,
    val x: Double,
    val y: Double,
    val heightM: Double,
    val widthM: Double,
    /** Height of the object's lowest point; > 1.0 m means a head-height hazard with nothing below. */
    val bottomM: Double = 0.0,
    val vx: Double = 0.0,
    val vy: Double = 0.0,
    val appearsAt: Double = 0.0,
) {
    fun posAt(t: Double) = (x + vx * (t - appearsAt)) to (y + vy * (t - appearsAt))
}

/** Scripted walker speed: piecewise-constant segments (duration s, speed m/s; negative = backing up). */
data class Segment(val durationS: Double, val speedMS: Double)

data class Perception(
    val pDetect: Double = 1.0,
    val dimNoise: Double = 0.10,
    val posNoise: Double = 0.03,
    /** Per-scenario multiplicative depth scale error, log-normal sigma (repo's R4 uses 0.30). */
    val depthScaleSigma: Double = 0.30,
    /** The depth model's own stated relative error, used as its sigma. */
    val depthRelSigma: Double = 0.30,
    val depthFrameNoise: Double = 0.05,
    /** One-sigma error of the camera pitch estimate (horizon or IMU) [A]. */
    val pitchSigmaRad: Double = Math.toRadians(2.0),
    val maxRangeM: Double = 12.0,
)

/** Faults injected on the camera link and the stream (REQ-28). Times are in seconds of scenario time. */
data class Faults(
    val fps: Double = 15.0,
    /** Intervals with no frames delivered at all (camera/stream loss). */
    val gaps: List<ClosedFloatingPointRange<Double>> = emptyList(),
    /** Intervals where the same image is delivered again and again (frozen stream). */
    val frozen: List<ClosedFloatingPointRange<Double>> = emptyList(),
    /** Intervals where the Bluetooth link is down (no frames, no glasses audio). */
    val linkDown: List<ClosedFloatingPointRange<Double>> = emptyList(),
    /** Extra transport latency added in these intervals. */
    val extraLatency: List<Pair<ClosedFloatingPointRange<Double>, Double>> = emptyList(),
    /** Probability that any one frame is dropped in transport. */
    val dropProb: Double = 0.0,
    /** Frame rate inside an interval (e.g. degraded to 7 or 2 fps). */
    val fpsOverride: List<Pair<ClosedFloatingPointRange<Double>, Double>> = emptyList(),
    val darkFrom: Double = Double.POSITIVE_INFINITY,
    val networkDown: List<ClosedFloatingPointRange<Double>> = emptyList(),
    val audioToPhone: List<ClosedFloatingPointRange<Double>> = emptyList(),
)

/**
 * Per-stage latency model for the parts that cannot be measured without hardware (docs/00 section 7).
 * Values are drawn so that p50/p95 match the budget table; they are assumptions [A], so a latency report
 * built on them tests only the arithmetic and the queueing inside the core, not the hardware.
 */
class LatencyModel(private val rng: java.util.Random) {
    fun glasses() = uniform(0.045, 0.085)                       // stage 1, p95 ~ 0.083
    fun bluetooth() = logNormal(0.080, 0.50)                    // stage 2, median 80 ms, p95 ~ 182 ms
    fun decode() = uniform(0.015, 0.040)                        // stage 3
    fun detector() = uniform(0.008, 0.030)                      // stage 5a
    fun depth() = uniform(0.025, 0.050)                         // stage 5b
    fun audioOut() = uniform(0.150, 0.300)                      // stage 8, A2DP, unknown until measured

    private fun uniform(a: Double, b: Double) = a + (b - a) * rng.nextDouble()
    private fun logNormal(median: Double, sigma: Double) = median * exp(sigma * rng.nextGaussian())
}

data class Scenario(
    val name: String,
    val obstacles: List<Obstacle> = emptyList(),
    val walk: List<Segment>,
    /** Head yaw relative to travel direction as a function of time (radians, positive right). */
    val headYaw: (Double) -> Double = { 0.0 },
    val pitchRad: Double = Math.toRadians(10.0),
    val cameraHeightM: Double = 1.30,
    val perception: Perception = Perception(),
    val faults: Faults = Faults(),
    /** Closed loop: the simulated walker stops after hearing STOP (reaction time, then one step). */
    val walkerReacts: Boolean = false,
    val reactionS: Double = 0.5,
    val stopDistanceM: Double = 0.7,
    val seed: Long = 1,
) {
    val durationS get() = walk.sumOf { it.durationS }
}

class World(val sc: Scenario) {
    val cam = Camera(Math.toRadians(60.0), Math.toRadians(46.0), sc.cameraHeightM)
    private val rng = java.util.Random(sc.seed)
    private val ranger = Ranger(Camera(cam.vfovRad, cam.hfovRad, 1.30))   // perception assumes nominal height
    private val pitchError = rng.nextGaussian() * sc.perception.pitchSigmaRad
    private val depthScale = exp(rng.nextGaussian() * sc.perception.depthScaleSigma)
    private val kV = 2.0 * tan(cam.vfovRad / 2.0)
    private val kH = 2.0 * tan(cam.hfovRad / 2.0)

    fun nextGaussian() = rng.nextGaussian()
    fun nextDouble() = rng.nextDouble()

    /** Ground-truth relative position (forward, lateral) of an obstacle in the travel frame. */
    fun relative(o: Obstacle, t: Double, walkerX: Double): Pair<Double, Double> {
        val (ox, oy) = o.posAt(t)
        return (ox - walkerX) to oy
    }

    /** Render detections for one frame from ground truth, with detector noise and the range estimate. */
    fun perceive(t: Double, walkerX: Double, yaw: Double): List<Detection> {
        val p = sc.perception
        val out = mutableListOf<Detection>()
        for (o in sc.obstacles) {
            if (t < o.appearsAt) continue
            val (fx, fy) = relative(o, t, walkerX)
            // Into the head frame (head turned right by yaw).
            val hx = fx * cos(yaw) + fy * sin(yaw)
            val hy = -fx * sin(yaw) + fy * cos(yaw)
            if (hx < 0.3) continue
            val d = hypot(hx, hy)
            if (d > p.maxRangeM) continue
            val bearing = atan2(hy, hx)
            if (kotlin.math.abs(bearing) > cam.hfovRad / 2 + 0.05) continue
            if (rng.nextDouble() > p.pDetect) continue

            fun imgY(z: Double) = 0.5 + (atan((sc.cameraHeightM - z) / d) - sc.pitchRad) / cam.vfovRad
            val bottomTrue = imgY(o.bottomM)
            val topTrue = imgY(o.bottomM + o.heightM)
            if (bottomTrue < 0.0 || topTrue > 1.0) continue                         // entirely out of frame vertically
            val hf = max(1e-4, (min(1.0, o.heightM / (kV * d))) * (1 + p.dimNoise * rng.nextGaussian()))
            val wf = max(1e-4, min(1.0, o.widthM / (kH * d) * (1 + p.dimNoise * rng.nextGaussian())))
            val cx = 0.5 + bearing / cam.hfovRad + p.posNoise * rng.nextGaussian()
            val bottom = (bottomTrue + p.posNoise * rng.nextGaussian()).coerceIn(0.0, 1.0)
            val box = Box(cx, bottom, wf, hf)
            val depthR = d * depthScale * (1 + p.depthFrameNoise * rng.nextGaussian())
            val det = ranger.detect(
                o.cls, box, PitchEstimate(sc.pitchRad + pitchError, p.pitchSigmaRad),
                DepthSample(depthR, max(0.1, depthR * p.depthRelSigma)), headHeight = o.bottomM > 1.0,
            ) ?: continue
            out += det
        }
        return out
    }
}
