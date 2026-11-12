package org.guideglass.core.perception

import org.guideglass.core.Box
import org.guideglass.core.Detection
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tan

/** Pinhole camera with a level horizon; pitch is positive when looking down (repo convention). */
data class Camera(val vfovRad: Double, val hfovRad: Double, val heightM: Double)

/** A camera pitch estimate (from the horizon or the IMU) and its one-sigma error. */
data class PitchEstimate(val pitchRad: Double, val sigmaRad: Double)

/** A metric range from a depth model sampled inside a box, with its one-sigma error. */
data class DepthSample(val rangeM: Double, val sigmaM: Double)

/**
 * Turns a 2-D detection into a metric range by fusing two independent sources (ADR 0004):
 *  (a) ground-contact geometry from the box's bottom edge, camera height and pitch;
 *  (b) a monocular depth model.
 * Fusion is inverse-variance. Source (a) is skipped when the bottom edge is cut off by the frame
 * or lies at or above the horizon, and for objects flagged as head height (no ground contact).
 */
class Ranger(private val cam: Camera, private val heightSigmaM: Double = 0.08) {

    /** Range from the bottom edge, or null when the geometry gives no usable answer. */
    fun groundRange(box: Box, pitch: PitchEstimate): Pair<Double, Double>? {
        if (box.bottom >= 0.995 || box.bottom <= 0.005) return null       // foot of object outside the frame
        val below = (box.bottom - 0.5) * cam.vfovRad + pitch.pitchRad    // angle below the horizon
        if (below <= Math.toRadians(1.0)) return null                     // at or above the horizon
        val d = cam.heightM / tan(below)
        // First-order error: d(range)/d(angle) = h / sin^2, plus the camera-height uncertainty.
        val s = sin2(below)
        val angleSigma = sqrt(pitch.sigmaRad * pitch.sigmaRad + PIXEL_SIGMA_RAD * PIXEL_SIGMA_RAD)
        val fromAngle = cam.heightM / s * angleSigma
        val fromHeight = d * heightSigmaM / cam.heightM
        return d to sqrt(fromAngle * fromAngle + fromHeight * fromHeight)
    }

    fun bearing(box: Box): Double = (box.cx - 0.5) * cam.hfovRad

    fun widthM(box: Box, rangeM: Double): Double = box.wf * 2.0 * tan(cam.hfovRad / 2.0) * rangeM

    fun detect(cls: String, box: Box, pitch: PitchEstimate, depth: DepthSample?, headHeight: Boolean = false): Detection? {
        val g = if (headHeight) null else groundRange(box, pitch)
        val (r, s) = when {
            g != null && depth != null -> fuse(g.first, g.second, depth.rangeM, depth.sigmaM)
            g != null -> g
            depth != null -> depth.rangeM to depth.sigmaM
            else -> return null
        }
        val rr = max(0.1, r)
        return Detection(cls, box, rr, s, bearing(box), widthM(box, rr), headHeight)
    }

    companion object {
        /** Bottom-edge localisation error, about 3% of the frame as in the repo's detector noise model [A]. */
        val PIXEL_SIGMA_RAD = Math.toRadians(60.0) * 0.03

        fun fuse(r1: Double, s1: Double, r2: Double, s2: Double): Pair<Double, Double> {
            val w1 = 1.0 / (s1 * s1); val w2 = 1.0 / (s2 * s2)
            val r = (r1 * w1 + r2 * w2) / (w1 + w2)
            // If the two sources disagree by more than their errors explain, widen the result rather than trust it.
            val base = sqrt(1.0 / (w1 + w2))
            val gap = abs(r1 - r2) / sqrt(s1 * s1 + s2 * s2)
            return r to if (gap > 2.0) base * gap / 2.0 else base
        }

        private fun sin2(a: Double): Double { val s = kotlin.math.sin(a); return s * s }
    }
}
