package org.guideglass.core

import org.guideglass.core.cloud.CloudGuard
import org.guideglass.core.cloud.GuardResult
import org.guideglass.core.cloud.SpeakContext
import org.guideglass.core.nav.GpsFix
import org.guideglass.core.nav.Navigator

/** A cue that started playing, with the timing of the frame that caused it (REQ-19). */
data class CueLog(
    val play: PlayEvent,
    val stamps: StageStamps?,
    /** Predicted time the sound reaches the ear: start + audio start latency + audio-out latency. */
    val earPredicted: Double,
)

/**
 * The decision core (docs/01-architecture.md). Event-driven and single-threaded: the platform
 * delivers events with timestamps from one monotonic clock and plays the returned cues.
 * Nothing here touches the network; the navigator and the cloud guard only add cues.
 */
class SafetyPipeline(
    val cfg: CoreConfig = CoreConfig(),
    val navigator: Navigator? = null,
    val guard: CloudGuard? = null,
) {
    val tracker = Tracker(cfg)
    val assessor = HazardAssessor(cfg)
    val health = HealthMonitor(cfg)
    val arbiter = CueArbiter(cfg)
    val log = mutableListOf<CueLog>()

    var walkSpeedMS = cfg.defaultWalkSpeedMS
    private var linkUp = true
    private val frameStamps = HashMap<Double, StageStamps>()

    fun start(now: Double) = submit(health.start(now), now)
    fun stop(now: Double) = submit(health.stop(now), now)

    /** Walking speed from step detection or GPS; the static time-to-contact channel depends on it. */
    fun onWalkSpeed(v: Double) { walkSpeedMS = v }

    fun onFrame(f: FrameInput, now: Double): List<PlayEvent> {
        // Frames older than the usable age, or stamped in the future, are not evidence that the camera works
        // and are not used for hazard decisions (review findings 9 and 16).
        val age = now - f.stamps.capture
        val usable = age <= cfg.maxUsableFrameAgeS && age >= -0.05
        health.onFrame(f, now, usable)
        if (!usable) return onTick(now)
        val out = mutableListOf<PlayEvent>()
        out += submit(health.tick(now), now)
        tracker.update(f, walkSpeedMS)
        f.stamps.trackDone = now
        val cues = if (health.hazardCuesAllowed) {
            assessor.decide(tracker.alertable(f.stamps.capture), f.stamps.capture, now, walkSpeedMS, f.headToTravelRad, health.motionChannelAllowed, tracker.lastFastYawT)
        } else emptyList()
        f.stamps.decision = now
        if (cues.isNotEmpty()) {
            frameStamps[f.stamps.capture] = f.stamps
            arbiter.cancel(now) { it.kind == CueKind.CLOUD }   // hazards cut off cloud speech (T13)
        }
        out += submit(cues, now)
        return out
    }

    fun onTick(now: Double): List<PlayEvent> {
        routeAudio()
        val out = submit(health.tick(now), now).toMutableList()
        routeAudio()
        out += record(arbiter.advance(now))
        return out
    }

    /** Start the next queued cue if the current one has finished; call often (e.g. every audio buffer). */
    fun advance(now: Double): List<PlayEvent> { routeAudio(); return record(arbiter.advance(now)) }

    fun onLink(up: Boolean, now: Double): List<PlayEvent> {
        linkUp = up
        health.onLink(up, now)
        return onTick(now)
    }

    fun onStream(s: StreamState, now: Double): List<PlayEvent> { health.onStream(s); return onTick(now) }

    fun onAudioRoute(r: AudioRoute, now: Double): List<PlayEvent> { health.onAudioRoute(r, now); return onTick(now) }

    fun onNetwork(up: Boolean, now: Double) = submit(health.onNetwork(up, now), now)

    fun onLocation(fix: GpsFix, now: Double): List<PlayEvent> = submit(navigator?.update(fix) ?: emptyList(), now)

    fun onCloudAnswer(raw: String, ctx: SpeakContext): Pair<List<PlayEvent>, GuardResult> {
        val g = guard ?: return emptyList<PlayEvent>() to GuardResult(emptyList(), listOf("no_guard"))
        val r = g.onAnswer(raw, ctx)
        return submit(r.cues, ctx.now) to r
    }

    private fun routeAudio() {
        arbiter.route = if (!linkUp || health.audioRoute == AudioRoute.PHONE) AudioRoute.PHONE else AudioRoute.GLASSES
    }

    private fun submit(cues: List<Cue>, now: Double): List<PlayEvent> {
        routeAudio()
        val out = mutableListOf<PlayEvent>()
        for (c in cues) out += arbiter.submit(c, now)
        out += arbiter.advance(now)
        return record(out)
    }

    private var recordsSeen = 0

    /** Tell the assessor about hazard cues the arbiter dropped, so they are requested again if still due. */
    private fun feedBack() {
        val recs = arbiter.records
        while (recordsSeen < recs.size) {
            val r = recs[recordsSeen++]
            val hazard = r.cue.kind == CueKind.STOP || r.cue.kind == CueKind.CAUTION || r.cue.kind == CueKind.MOVING_OBJECT
            if (hazard && (r.fate == Fate.DROPPED_RATE || r.fate == Fate.DROPPED_STALE || r.fate == Fate.CUT_OFF)) assessor.notPlayed(r.cue)
        }
    }

    private fun record(evts: List<PlayEvent>): List<PlayEvent> {
        feedBack()
        for (e in evts) {
            val start = if (e.cue.earcon == Earcon.NONE) cfg.speechStartS else cfg.earconStartS
            log += CueLog(e, frameStamps[e.cue.captureTime], e.startT + start + cfg.audioOutLatencyS)
        }
        return evts
    }
}
