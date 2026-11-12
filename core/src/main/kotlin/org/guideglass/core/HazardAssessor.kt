package org.guideglass.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

enum class AlertLevel { NONE, CAUTION, STOP }

/** The assessor's view of one track at one decision, kept for logs and tests. */
data class Assessment(
    val trackId: Int,
    val alongM: Double,
    val lateralM: Double,
    val sigmaM: Double,
    val inPath: Boolean,
    val ttcStaticS: Double,
    val ttcMotionS: Double,
    val ttcAtEarS: Double,
    val level: AlertLevel,
    val channel: String,
    val closingMS: Double = 0.0,
)

/**
 * Turns tracks into CAUTION and STOP cues (REQ-03, 04, 06, 08, 16; ADR 0004).
 *
 * Time to contact is taken at the frame's capture time and moved forward to the moment the cue is
 * expected to reach the ear: elapsed = (decision time - capture time) + audio start + audio-out latency.
 * Thresholds are the lead times in docs/00 section 8 minus the nominal pipeline already counted there,
 * so with nominal latency the rule equals "time to contact at capture <= stopLeadS".
 */
class HazardAssessor(private val cfg: CoreConfig) {

    private class State(var level: AlertLevel, var lastInZone: Double, var alongAtAlert: Double = Double.POSITIVE_INFINITY) // closest approach while alerted
    private data class Memory(val along: Double, val lat: Double, val level: AlertLevel, val t: Double, val sigma: Double, val closing: Double)

    private val state = HashMap<Int, State>()
    private val memories = mutableListOf<Memory>()
    var lastAssessments: List<Assessment> = emptyList(); private set

    private val stopAtEar get() = cfg.stopLeadS - cfg.nominalPipelineS
    private val cautionAtEar get() = cfg.cautionLeadS - cfg.nominalPipelineS

    /** Pure decision for one track; no alert state involved. */
    fun assess(
        tr: Track, captureT: Double, now: Double, walkSpeed: Double, headToTravel: Double, motionChannel: Boolean,
        fastYawT: Double = Double.NEGATIVE_INFINITY,
    ): Assessment {
        val prior = -walkSpeed * cos(headToTravel)
        val e = tr.estimate(captureT, cfg.historyS, prior)
        // Head frame -> travel frame.
        val c = cos(headToTravel); val s = sin(headToTravel)
        val along = e.fwd * c - e.lat * s
        val lat = e.fwd * s + e.lat * c
        val vAlong = e.vFwd * c - e.vLat * s
        val vLat = e.vFwd * s + e.vLat * c

        val elapsed = (now - captureT) + cfg.earconStartS + cfg.audioOutLatencyS
        val rangeForTtc = max(0.0, along - cfg.pessimismK * e.sigma)
        val walking = walkSpeed > cfg.standingSpeedMS

        val ttcStatic = if (walking && along > 0) rangeForTtc / walkSpeed - elapsed else Double.POSITIVE_INFINITY

        // An object counts as moving on its own only if its fitted velocity differs from what a static
        // object would show by more than three standard errors of the fit, from at least 8 samples over
        // the motion window, all taken after the last fast head turn. Box jitter alone otherwise
        // produces "moving" objects (found in the first scenario probe; docs/PROGRESS.md).
        // Forward and sideways motion are tested separately and used separately.
        val closing = -vAlong
        val expected = if (walking) tr.meanWalkV(cfg.historyS) * cos(headToTravel) else 0.0
        val span = tr.samples.last().t - tr.samples.first().t
        val enough = motionChannel && e.fitted && span >= cfg.motionWindowS - 1e-9 &&
            tr.samplesIn(cfg.historyS) >= 8 && tr.samples.first().t > fastYawT
        // Persistence: the motion test must pass on 3 decisions in a row (fit errors are heavy-tailed).
        tr.fwdStreak = if (enough && abs(closing - expected) > maxOf(cfg.independentMotionMS, 3.0 * e.vFwdSE)) tr.fwdStreak + 1 else 0
        tr.latStreak = if (enough && abs(vLat) > maxOf(cfg.independentMotionMS, 3.0 * e.vLatSE)) tr.latStreak + 1 else 0
        val latMoving = tr.latStreak >= 3
        if (tr.fwdStreak >= 3) tr.lastMovingT = captureT
        // Hysteresis: once shown to move, keep treating it as moving for 1.5 s, so the verdict does not
        // flicker back to the static assumption when one fit happens to be noisy.
        val independent = enough && captureT - tr.lastMovingT <= 1.5
        var ttcMotion = Double.POSITIVE_INFINITY
        if (independent && closing > 0.3 && along > 0) ttcMotion = rangeForTtc / closing - elapsed
        // Image expansion (tau) does not depend on the range estimate, so it sees fast closers at a
        // distance where range noise hides their speed. It must be significant on two decisions in a row.
        tr.tauStreak = if (motionChannel && e.tauS != null) tr.tauStreak + 1 else 0
        val ttcTau = if (tr.tauStreak >= 2) e.tauS!! - elapsed else Double.POSITIVE_INFINITY
        // For a moving object its measured closing speed replaces the static assumption (S-12: a
        // pedestrian ahead walking the same way is not closing, so it must not alert).
        // Expansion alone may raise CAUTION but never STOP: STOP needs a range-based channel.
        // Movable classes: if even a lower bound (estimate minus one standard error) of the fitted closing speed is faster than a
        // still object would close, use it. Approaching pedestrians are otherwise treated as still until
        // the motion test passes three times, which made STOP late in S-09.
        val movableClass = tr.cls in MOVABLE
        val closingLB = closing - e.vFwdSE
        val ttcFast = if (movableClass && enough && closingLB > expected && along > 0) rangeForTtc / closingLB - elapsed
            else Double.POSITIVE_INFINITY
        val ttc = minOf(if (independent) ttcMotion else ttcStatic, ttcFast)
        val tauCaution = ttcTau <= cautionAtEar
        val movingChannel = independent || latMoving || (tauCaution && ttcTau < 0.6 * ttcStatic)

        val halfW = cfg.corridorHalfWidthM + if (cfg.corridorIncludesObjectWidth) e.widthM / 2.0 else 0.0
        val latAtContact = if (ttc.isFinite() && latMoving) lat + vLat * max(0.0, ttc + elapsed) else lat
        // An object moving sideways is judged by where it will be at contact, not where it is now (a cyclist
        // crossing ahead passes through the path but is gone when the walker arrives), unless it is already close.
        val inPath = along > 0 && if (latMoving) abs(latAtContact) <= halfW || (abs(lat) <= halfW && along <= cfg.closeFloorM)
            else abs(lat) <= halfW

        val approaching = walking || closing > 0.2
        // Objects that can move on their own get no STOP from the still-object assumption until their
        // motion has been measured (S-12: a person ahead walking at the user's pace).
        val movable = tr.cls in MOVABLE
        val staticStopAllowed = !movable ||
            (enough && !independent && abs(closing - expected) <= maxOf(cfg.independentMotionMS, 2.0 * e.vFwdSE))
        // Sustained, significant image expansion may also raise STOP (fast closers, S-10).
        val tauStop = tr.tauStreak >= 4 && ttcTau <= stopAtEar
        val level = when {
            !inPath -> AlertLevel.NONE
            along <= cfg.closeFloorM && approaching -> AlertLevel.STOP
            ttc <= stopAtEar && (independent || staticStopAllowed || ttcFast <= stopAtEar) -> AlertLevel.STOP
            tauStop -> AlertLevel.STOP
            ttc <= cautionAtEar || tauCaution -> AlertLevel.CAUTION
            else -> AlertLevel.NONE
        }
        val channel = if (movingChannel) "motion" else "static"
        return Assessment(tr.id, along, lat, e.sigma, inPath, ttcStatic, minOf(ttcMotion, ttcTau), ttc, level, channel, closing)
    }

    /**
     * Assess every alertable track and return cues for escalations only (one sequence per approach).
     * A track's state re-arms after it has been out of the alert zone for [CoreConfig.rearmS].
     */
    fun decide(
        tracks: List<Track>, captureT: Double, now: Double, walkSpeed: Double,
        headToTravel: Double, motionChannel: Boolean, fastYawT: Double = Double.NEGATIVE_INFINITY,
    ): List<Cue> {
        val cues = mutableListOf<Cue>()
        val live = tracks.map { it.id }.toHashSet()
        // Remember alert state of tracks that disappeared, so a re-formed track on the same object
        // does not alert again (track fragmentation must not cause repetition).
        state.keys.filter { it !in live }.forEach { id ->
            val st = state.remove(id)!!
            val a = lastAssessments.firstOrNull { it.trackId == id }
            if (a != null && st.level != AlertLevel.NONE) memories += Memory(a.alongM, a.lateralM, st.level, now, a.sigmaM, a.closingMS)
        }
        memories.removeAll { now - it.t > cfg.rearmS }

        val out = mutableListOf<Assessment>()
        for (tr in tracks) {
            val a = assess(tr, captureT, now, walkSpeed, headToTravel, motionChannel, fastYawT)
            out += a
            val st = state.getOrPut(tr.id) {
                // Expected drift since the memory was stored is at most walking speed times the gap.
                val inherited = memories.firstOrNull {
                    abs(it.along - a.alongM) < maxOf(1.5, 3 * maxOf(it.sigma, a.sigmaM)) + maxOf(walkSpeed, it.closing) * (now - it.t) &&
                        abs(it.lat - a.lateralM) < 0.8
                }
                if (inherited != null) memories.remove(inherited)
                State(inherited?.level ?: AlertLevel.NONE, now)
            }
            if (a.level != AlertLevel.NONE) { st.lastInZone = now; st.alongAtAlert = minOf(st.alongAtAlert, a.alongM) }
            // Re-arm only while the user is walking or has backed away by more than 1 m: a user standing in
            // front of an obstacle must not be told again (S-08), however the noise moves the estimate.
            else if (now - st.lastInZone >= cfg.rearmS && (walkSpeed > cfg.standingSpeedMS || a.alongM > st.alongAtAlert + 1.0)) { st.level = AlertLevel.NONE; st.alongAtAlert = Double.POSITIVE_INFINITY }
            if (a.level.ordinal > st.level.ordinal) {
                cues += cueFor(tr, a, captureT, now)
                st.level = a.level
            }
        }
        lastAssessments = out
        return cues
    }

    private fun cueFor(tr: Track, a: Assessment, captureT: Double, now: Double): Cue {
        val clock = clock(a.alongM, a.lateralM)
        return when {
            a.level == AlertLevel.STOP -> Cue(CueKind.STOP, Priority.P0, "Stop.", Earcon.STOP,
                "stop-${tr.id}", now, 0.5, captureT, vibrate = true)
            a.channel == "motion" -> Cue(CueKind.MOVING_OBJECT, Priority.P1, "${label(tr)} approaching, $clock o'clock.",
                Earcon.MOVING, "caution-${tr.id}", now, 0.8, captureT)
            else -> Cue(CueKind.CAUTION, Priority.P1, "${label(tr)}, $clock o'clock.", Earcon.CAUTION,
                "caution-${tr.id}", now, 0.8, captureT)
        }
    }

    companion object {
        val MOVABLE = setOf("person", "bicycle", "scooter", "car", "vehicle")

        fun clock(along: Double, lat: Double): Int {
            val h = (Math.toDegrees(atan2(lat, along)) / 30.0).roundToInt()
            val c = ((h % 12) + 12) % 12
            return if (c == 0) 12 else c
        }

        fun label(tr: Track): String = when {
            tr.headHeight -> "Head height"
            tr.cls == "person" -> "Person"
            tr.cls == "bicycle" -> "Bike"
            tr.cls == "scooter" -> "Scooter"
            tr.cls == "car" || tr.cls == "vehicle" -> "Vehicle"
            else -> "Obstacle"
        }
    }
}
