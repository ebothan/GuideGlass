package org.guideglass.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** One observation of a track in the head frame: forward and lateral metres (lateral positive right). */
data class Sample(
    val t: Double, val fwd: Double, val lat: Double, val sigma: Double, val hf: Double, val widthM: Double,
    /** Walker speed when the sample was taken, so a static object's expected closing speed is known. */
    val walkV: Double = 0.0,
)

/** Track state estimated at a given time. */
data class TrackEstimate(
    val fwd: Double, val lat: Double,
    val vFwd: Double, val vLat: Double,
    val sigma: Double, val widthM: Double,
    /** Optical time to contact from box growth (Lee's tau), or null if not significantly expanding. */
    val tauS: Double?,
    /** True if a velocity was fitted from history rather than assumed. */
    val fitted: Boolean,
    /** Standard errors of the fitted velocities (m/s); infinite when not fitted. */
    val vFwdSE: Double = Double.POSITIVE_INFINITY,
    val vLatSE: Double = Double.POSITIVE_INFINITY,
)

class Track(val id: Int, val cls: String, val headHeight: Boolean, first: Sample) {
    val samples = ArrayDeque<Sample>().apply { addLast(first) }
    val firstSeen = first.t
    var lastSeen = first.t; private set
    var hits = 1; private set
    var confirmed = false; internal set
    /** Last capture time at which the track showed significant motion of its own (hysteresis). */
    var lastMovingT = Double.NEGATIVE_INFINITY
    /** Consecutive decisions on which image expansion, forward motion and sideways motion were significant. */
    var tauStreak = 0
    var fwdStreak = 0
    var latStreak = 0

    /** Mean walker speed over the samples within [windowS] of the last one. */
    fun meanWalkV(windowS: Double) = samples.filter { lastSeen - it.t <= windowS + 1e-9 }.map { it.walkV }.average()
    fun samplesIn(windowS: Double) = samples.count { lastSeen - it.t <= windowS + 1e-9 }

    internal fun add(s: Sample, keepS: Double) {
        samples.addLast(s); lastSeen = s.t; hits++
        while (samples.size > 2 && s.t - samples.first().t > keepS) samples.removeFirst()
    }

    /** Rotate stored history about the head when the head turns, so old samples stay comparable. */
    internal fun rotate(angleRad: Double) {
        val c = cos(angleRad); val s = sin(angleRad)
        for (i in samples.indices) {
            val p = samples[i]
            samples[i] = p.copy(fwd = p.fwd * c + p.lat * s, lat = -p.fwd * s + p.lat * c)
        }
    }

    /**
     * Least-squares constant-velocity fit over the last [windowS] seconds, evaluated at time [t].
     * The range sigma is the mean of the sample sigmas, not reduced by averaging: depth-model scale
     * error is correlated from frame to frame, so averaging frames does not remove it.
     */
    fun estimate(t: Double, windowS: Double, priorVFwd: Double): TrackEstimate {
        val w = samples.filter { lastSeen - it.t <= windowS + 1e-9 }
        val sigma = w.map { it.sigma }.average()
        val width = w.map { it.widthM }.average()
        val span = w.last().t - w.first().t
        if (w.size < 3 || span < 0.15) {
            val p = w.last()
            val dt = t - p.t
            return TrackEstimate(p.fwd + priorVFwd * dt, p.lat, priorVFwd, 0.0, sigma, width, null, false)
        }
        val ts = w.map { it.t }
        val fF = fit(ts, w.map { it.fwd }, t)
        val fL = fit(ts, w.map { it.lat }, t)
        // Tau only from at least 6 boxes over 0.5 s, and only when the box grows by more than three
        // standard errors of the fit: with 10% size jitter, shorter or weaker evidence is mostly noise.
        val tau = w.filter { it.hf > 1e-4 }.takeIf { it.size >= 6 && it.last().t - it.first().t >= 0.5 - 1e-9 }?.let { hs ->
            val g = fit(hs.map { it.t }, hs.map { ln(it.hf) }, hs.last().t)
            if (g.slope > 1e-3 && g.slope > 3.0 * g.slopeSE) 1.0 / g.slope else null
        }
        return TrackEstimate(fF.value, fL.value, fF.slope, fL.slope, sigma, width, tau, true, fF.slopeSE, fL.slopeSE)
    }

    data class Fit(val value: Double, val slope: Double, val slopeSE: Double)

    companion object {
        /** Ordinary least squares y = a + b (x - t): value at t, slope, and the slope's standard error. */
        fun fit(xs: List<Double>, ys: List<Double>, t: Double): Fit {
            val n = xs.size
            val mx = xs.average(); val my = ys.average()
            var sxx = 0.0; var sxy = 0.0
            for (i in 0 until n) { val dx = xs[i] - mx; sxx += dx * dx; sxy += dx * (ys[i] - my) }
            val b = if (sxx > 1e-12) sxy / sxx else 0.0
            var rss = 0.0
            for (i in 0 until n) { val r = ys[i] - (my + b * (xs[i] - mx)); rss += r * r }
            val se = if (n > 2 && sxx > 1e-12) sqrt(rss / (n - 2) / sxx) else Double.POSITIVE_INFINITY
            return Fit(my + b * (t - mx), b, se)
        }
    }
}

/**
 * Multi-object tracker in the head frame (ADR 0004 items 1, 6, 7).
 * Association is greedy nearest-neighbour inside a gate scaled by the range uncertainty.
 * All life-cycle rules are in seconds, so behaviour does not change when the frame rate does.
 */
class Tracker(private val cfg: CoreConfig) {
    private val tracks = mutableListOf<Track>()
    private var nextId = 1
    private var lastFrameT = Double.NaN
    /** Last capture time with head yaw above the fast-turn threshold; motion evidence restarts after it. */
    var lastFastYawT = Double.NEGATIVE_INFINITY; private set

    fun all(): List<Track> = tracks

    /**
     * Tracks usable for alerts: confirmed and seen within the coast time of [captureT], the capture time of
     * the newest frame. Measured in capture time, not decision time, so a late frame is not judged stale
     * because of its own transport delay (found by AssessorTest; affected the F-04 latency spike).
     */
    fun alertable(captureT: Double): List<Track> = tracks.filter { it.confirmed && captureT - it.lastSeen <= cfg.coastS }

    fun update(frame: FrameInput, walkSpeed: Double): List<Track> {
        val t = frame.stamps.capture
        val dt = if (lastFrameT.isNaN()) 0.0 else max(0.0, t - lastFrameT)
        lastFrameT = t
        val yawRate = frame.yawRateRadS ?: 0.0
        val fastYaw = abs(yawRate) > cfg.fastYawRadS
        if (fastYaw) {
            lastFastYawT = t
            tracks.forEach { it.lastMovingT = Double.NEGATIVE_INFINITY; it.tauStreak = 0; it.fwdStreak = 0; it.latStreak = 0 }
        }
        if (dt > 0 && yawRate != 0.0) tracks.forEach { it.rotate(yawRate * dt) }

        tracks.removeAll { t - it.lastSeen > cfg.deleteAfterS }

        val obs = frame.detections.map {
            Sample(t, it.rangeM * cos(it.bearingRad), it.rangeM * sin(it.bearingRad), it.rangeSigmaM, it.box.hf, it.widthM, walkSpeed) to it
        }
        val prior = -walkSpeed * cos(frame.headToTravelRad)
        val pairs = mutableListOf<Triple<Double, Int, Int>>()
        tracks.forEachIndexed { ti, tr ->
            val e = tr.estimate(t, cfg.historyS, prior)
            obs.forEachIndexed { oi, (s, d) ->
                if (d.cls != tr.cls && d.cls != "unknown" && tr.cls != "unknown") return@forEachIndexed
                // Lateral error grows with range (bearing noise times distance), so the gate does too.
                val gF = max(0.6, 2.5 * max(e.sigma, s.sigma)); val gL = max(0.6, 0.1 * s.fwd)
                val nf = (s.fwd - e.fwd) / gF; val nl = (s.lat - e.lat) / gL
                val cost = nf * nf + nl * nl
                if (abs(nf) <= 1.0 && abs(nl) <= 1.0) pairs += Triple(cost, ti, oi)
            }
        }
        val usedT = HashSet<Int>(); val usedO = HashSet<Int>()
        for ((_, ti, oi) in pairs.sortedBy { it.first }) {
            if (ti in usedT || oi in usedO) continue
            usedT += ti; usedO += oi
            tracks[ti].add(obs[oi].first, cfg.historyS)
        }
        obs.forEachIndexed { oi, (s, d) -> if (oi !in usedO) tracks += Track(nextId++, d.cls, d.headHeight, s) }

        for (tr in tracks) {
            if (!tr.confirmed && !fastYaw && tr.hits >= 2 && tr.lastSeen - tr.firstSeen >= cfg.confirmAfterS - 1e-9) {
                tr.confirmed = true
            }
        }
        return tracks
    }

    companion object {
        fun distance(fwd: Double, lat: Double) = sqrt(fwd * fwd + lat * lat)
    }
}
