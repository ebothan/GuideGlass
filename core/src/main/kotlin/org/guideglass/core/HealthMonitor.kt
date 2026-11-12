package org.guideglass.core

/** System states from docs/01-architecture.md section 3, in priority order (first wins). */
enum class SystemState { STOPPED, STARTING, GLASSES_LOST, CAMERA_LOST, OUT_OF_ODD, REDUCED, NORMAL }

enum class StreamState { STREAMING, PAUSED, STOPPED }

/**
 * Watches the inputs the safety path depends on and owns the degraded cues (REQ-10 to 14).
 * It is fed frame arrivals, link and route events, and a periodic tick; it never trusts that
 * "no news" means "fine": the camera is declared lost when fresh frames stop, whatever the SDK says.
 */
class HealthMonitor(private val cfg: CoreConfig) {
    var state = SystemState.STOPPED; private set
    var audioRoute = AudioRoute.GLASSES; private set
    var headTurning = false; private set
    var networkUp = true; private set

    private var linkUp = true
    private var streamState = StreamState.STREAMING
    private var lastFreshFrame = Double.NEGATIVE_INFINITY
    private var lastHash: Long? = null
    private var freshSince = Double.NaN
    private val arrivals = ArrayDeque<Pair<Double, Double>>()   // (receive time, age at perception done)
    private var reducedSince = Double.NaN
    private var goodSince = Double.NaN
    private var darkSince = Double.NaN
    private var lightSince = Double.NaN
    private var lastBrightness = 1.0
    private var lastAnnounce = Double.NEGATIVE_INFINITY
    private var offlineAnnounced = false
    private var pendingRouteCue: Cue? = null

    /** Hazard cues are allowed only in these states. */
    val hazardCuesAllowed get() = state in setOf(SystemState.NORMAL, SystemState.REDUCED, SystemState.OUT_OF_ODD)

    /** The motion channel needs a good frame rate and a steady head. */
    val motionChannelAllowed get() = state == SystemState.NORMAL && !headTurning

    fun start(now: Double): List<Cue> {
        state = SystemState.STARTING
        lastFreshFrame = now      // grace: the camera must deliver within the timeout from start
        return listOf(Cue(CueKind.START, Priority.P2, "Hazard alerts on.", Earcon.START, "start", now, 5.0))
    }

    fun stop(now: Double): List<Cue> {
        state = SystemState.STOPPED
        return listOf(Cue(CueKind.END, Priority.P1, "Hazard alerts off.", Earcon.END, "end", now, 5.0))
    }

    /**
     * [now] is the phone's own monotonic clock at arrival: timestamps inside the frame are never trusted for
     * health (review finding 9). [usable] is false for frames too old or stamped in the future.
     */
    fun onFrame(f: FrameInput, now: Double, usable: Boolean = true) {
        val fresh = usable && f.contentHash != lastHash
        lastHash = f.contentHash
        if (fresh) {
            lastFreshFrame = now
            if (freshSince.isNaN()) freshSince = now
            arrivals.addLast(now to (now - f.stamps.capture).coerceAtLeast(0.0))
        }
        while (arrivals.isNotEmpty() && now - arrivals.first().first > 2.0) arrivals.removeFirst()
        headTurning = (f.yawRateRadS ?: 0.0).let { kotlin.math.abs(it) > cfg.fastYawRadS }
        lastBrightness = f.brightness
    }

    fun onStream(s: StreamState) { streamState = s }

    /** The frame rate the app asked the glasses for; lower rates come only from our own request (01#2). */
    fun onRequestedFps(fps: Double) { requestedFps = fps }
    private var requestedFps = 0.0

    fun onLink(up: Boolean, now: Double) {
        if (up && !linkUp) linkUpSince = now
        linkUp = up
        if (!up) freshSince = Double.NaN
    }
    private var linkUpSince = Double.NEGATIVE_INFINITY

    fun onAudioRoute(route: AudioRoute, now: Double) {
        if (route == audioRoute) return
        audioRoute = route
        pendingRouteCue = if (route == AudioRoute.PHONE)
            Cue(CueKind.AUDIO_REROUTED, Priority.P2, "Glasses audio lost. Alerts on phone.", Earcon.REDUCED, "audio-route", now, 3.0, vibrate = true)
        else Cue(CueKind.AUDIO_BACK, Priority.P3, "Glasses audio back.", Earcon.RECOVERED, "audio-route", now, 5.0)
    }

    fun onNetwork(up: Boolean, now: Double): List<Cue> {
        networkUp = up
        if (!up && !offlineAnnounced) {
            offlineAnnounced = true
            return listOf(Cue(CueKind.OFFLINE, Priority.P3, "Offline. Hazard alerts still on.", Earcon.NONE, "offline", now, 10.0))
        }
        if (up) offlineAnnounced = false
        return emptyList()
    }

    /** Re-evaluate the state. Call at least every 100 ms so loss is detected within 0.6 s (REQ-10). */
    fun tick(now: Double): List<Cue> {
        val out = mutableListOf<Cue>()
        pendingRouteCue?.let { out += it; pendingRouteCue = null }
        if (state == SystemState.STOPPED) return out

        while (arrivals.isNotEmpty() && now - arrivals.first().first > 2.0) arrivals.removeFirst()
        val fps = if (arrivals.size >= 2) (arrivals.size - 1) / (arrivals.last().first - arrivals.first().first).coerceAtLeast(1e-6) else 0.0
        // The loss timeout is 0.5 s at normal frame rates. At low rates (2 fps means a frame every 0.5 s)
        // it stretches to 2.5 frame intervals, so a slow stream is reported as reduced, not lost.
        val slowest = listOf(fps, requestedFps).filter { it > 0 }.minOrNull()
        val timeout = if (slowest != null) maxOf(cfg.frameTimeoutS, 2.5 / slowest) else cfg.frameTimeoutS
        val cameraLost = streamState != StreamState.STREAMING || now - lastFreshFrame > timeout
        if (cameraLost) freshSince = Double.NaN
        val meanAge = if (arrivals.isEmpty()) 0.0 else arrivals.map { it.second }.average()
        val reducedNow = !cameraLost && (fps < cfg.minFps * 0.9 || meanAge > cfg.maxFrameAgeS)
        if (reducedNow) { if (reducedSince.isNaN()) reducedSince = now; goodSince = Double.NaN }
        else { reducedSince = Double.NaN; if (goodSince.isNaN()) goodSince = now }
        val dark = lastBrightness < cfg.lowBrightness && !cameraLost
        if (dark) { if (darkSince.isNaN()) darkSince = now; lightSince = Double.NaN }
        else { darkSince = Double.NaN; if (lightSince.isNaN()) lightSince = now }

        val prev = state
        val next = when {
            !linkUp -> SystemState.GLASSES_LOST
            // Link back but no fresh frames yet: stay "glasses lost" for a grace period rather than
            // switching to "camera lost" at the moment the link returns.
            prev == SystemState.GLASSES_LOST && now - linkUpSince < cfg.frameTimeoutS + cfg.cameraBackAfterS + 2.0 &&
                (freshSince.isNaN() || now - freshSince < cfg.cameraBackAfterS) -> prev
            cameraLost -> SystemState.CAMERA_LOST
            prev in setOf(SystemState.CAMERA_LOST, SystemState.GLASSES_LOST) &&
                (freshSince.isNaN() || now - freshSince < cfg.cameraBackAfterS) -> prev
            prev == SystemState.STARTING && freshSince.isNaN() -> prev
            !darkSince.isNaN() && now - darkSince >= cfg.reducedEnterS -> SystemState.OUT_OF_ODD
            prev == SystemState.OUT_OF_ODD && (lightSince.isNaN() || now - lightSince < cfg.recoverAfterS) -> prev
            !reducedSince.isNaN() && now - reducedSince >= cfg.reducedEnterS -> SystemState.REDUCED
            prev == SystemState.REDUCED && (goodSince.isNaN() || now - goodSince < cfg.recoverAfterS) -> prev
            else -> SystemState.NORMAL
        }
        state = next
        if (next != prev) {
            lastAnnounce = now
            entryCue(next, prev, now)?.let { out += it }
        } else {
            val repeat = when (next) {
                SystemState.CAMERA_LOST, SystemState.GLASSES_LOST -> cfg.lostRepeatS
                SystemState.OUT_OF_ODD -> cfg.oddRepeatS
                else -> Double.POSITIVE_INFINITY
            }
            if (now - lastAnnounce >= repeat) { lastAnnounce = now; entryCue(next, prev, now)?.let { out += it } }
        }
        return out
    }

    private fun entryCue(s: SystemState, prev: SystemState, now: Double): Cue? = when (s) {
        SystemState.CAMERA_LOST -> Cue(CueKind.CAMERA_LOST, Priority.P1, "Camera lost. Use your cane.", Earcon.LOST, "health-$s", now, 2.0, vibrate = true)
        SystemState.GLASSES_LOST -> Cue(CueKind.GLASSES_LOST, Priority.P1, "Glasses disconnected.", Earcon.LOST, "health-$s", now, 2.0, vibrate = true)
        SystemState.OUT_OF_ODD -> Cue(CueKind.OUT_OF_ODD, Priority.P2, "Low light. Use your cane only.", Earcon.REDUCED, "health-$s", now, 3.0)
        SystemState.REDUCED -> Cue(CueKind.REDUCED, Priority.P2, "Reduced detection.", Earcon.REDUCED, "health-$s", now, 3.0)
        SystemState.NORMAL -> when (prev) {
            SystemState.CAMERA_LOST -> Cue(CueKind.CAMERA_BACK, Priority.P3, "Camera back.", Earcon.RECOVERED, "health-$s", now, 5.0)
            SystemState.GLASSES_LOST -> Cue(CueKind.GLASSES_BACK, Priority.P3, "Glasses back.", Earcon.RECOVERED, "health-$s", now, 5.0)
            SystemState.REDUCED, SystemState.OUT_OF_ODD -> Cue(CueKind.DETECTION_NORMAL, Priority.P3, "Detection normal.", Earcon.RECOVERED, "health-$s", now, 5.0)
            else -> null
        }
        else -> null
    }
}
