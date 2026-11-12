package org.guideglass.core.sim

import org.guideglass.core.AudioRoute
import org.guideglass.core.CoreConfig
import org.guideglass.core.CueKind
import org.guideglass.core.Detection
import org.guideglass.core.Earcon
import org.guideglass.core.FrameInput
import org.guideglass.core.PlayEvent
import org.guideglass.core.SafetyPipeline
import org.guideglass.core.StageStamps
import org.guideglass.core.SystemState
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** A cue as heard in the simulation: [earT] uses the drawn (not configured) audio latency. */
data class SimCue(val play: PlayEvent, val earT: Double, val captureT: Double?) {
    val kind get() = play.cue.kind
    val text get() = play.cue.text
}

data class RunResult(
    val scenario: Scenario,
    val cues: List<SimCue>,
    val pipeline: SafetyPipeline,
    /** Per obstacle: time of contact (walker body reaches it), or null. */
    val contactT: Map<Int, Double?>,
    /** Per obstacle: smallest forward gap while laterally overlapping the walker, metres. */
    val minGapM: Map<Int, Double>,
    val stages: Map<String, List<Double>>,
    val coreComputeS: List<Double>,
    val states: List<Pair<Double, SystemState>>,
    val walkerX: List<Pair<Double, Double>>,
    /** Every input the core received, in order (replayable, see [EventLog]). */
    val events: List<CoreEvent> = emptyList(),
    /** Frame deliveries to the core: (time, content hash). */
    val deliveries: List<Pair<Double, Long>> = emptyList(),
) {
    fun of(kind: CueKind) = cues.filter { it.kind == kind }
}

/**
 * Runs a [Scenario] through the full [SafetyPipeline] on a simulated clock (step 10 ms).
 * Frames go through the latency model; ticks run every 100 ms; faults are injected at their times.
 * Core compute time is measured with System.nanoTime and reported separately [M, desktop JVM];
 * it does not advance the simulated clock, so runs are deterministic.
 */
class Runner(
    private val cfg: CoreConfig = CoreConfig(),
    private val pipelineFactory: (CoreConfig) -> SafetyPipeline = { SafetyPipeline(it) },
    /** Optional hook after each delivered frame (debugging and per-frame metrics). */
    private val onFrameDone: ((Double, FrameInput, SafetyPipeline) -> Unit)? = null,
) {

    fun run(sc: Scenario): RunResult {
        val world = World(sc)
        val lat = LatencyModel(java.util.Random(sc.seed * 7919 + 1))
        val audioRng = LatencyModel(java.util.Random(sc.seed * 104729 + 3))
        val p = pipelineFactory(cfg)
        val f = sc.faults
        val dt = 0.01
        val end = sc.durationS

        val cues = mutableListOf<SimCue>()
        val events = mutableListOf<CoreEvent>()
        val deliveries = mutableListOf<Pair<Double, Long>>()
        var lastFps = -1.0
        val stages = linkedMapOf<String, MutableList<Double>>()
        fun stage(n: String, v: Double) { stages.getOrPut(n) { mutableListOf() } += v }
        val compute = mutableListOf<Double>()
        val states = mutableListOf<Pair<Double, SystemState>>()
        val walk = mutableListOf<Pair<Double, Double>>()
        val contact = HashMap<Int, Double?>().apply { sc.obstacles.forEach { put(it.id, null) } }
        val minGap = HashMap<Int, Double>().apply { sc.obstacles.forEach { put(it.id, Double.POSITIVE_INFINITY) } }

        // Pending frames waiting for their perception-done time.
        data class Pending(val input: FrameInput, val readyT: Double)
        val pending = ArrayDeque<Pending>()
        var nextCapture = 0.0
        var seq = 0L
        var lastDets: List<Detection> = emptyList()
        var lastHash = 0L
        var x = 0.0
        var speed = 0.0
        var stoppingFrom = Double.NaN     // closed loop: time the walker starts to stop
        var stopV0 = 0.0
        var lastTick = -1.0
        var lastState: SystemState? = null
        var linkWasDown = false
        var netWasDown = false
        var phoneWas = false

        fun inAny(t: Double, l: List<ClosedFloatingPointRange<Double>>) = l.any { t in it }
        fun scripted(t: Double): Double {
            var acc = 0.0
            for (s in sc.walk) { if (t < acc + s.durationS) return s.speedMS; acc += s.durationS }
            return 0.0
        }
        fun take(evts: List<PlayEvent>, t: Double) {
            for (e in evts) {
                val startLat = if (e.cue.earcon == Earcon.NONE) cfg.speechStartS else cfg.earconStartS
                val ear = e.startT + startLat + audioRng.audioOut()
                val cap = e.cue.captureTime.takeIf { !it.isNaN() }
                cues += SimCue(e, ear, cap)
                if (cap != null) {
                    stage("cue: capture->decision", e.cue.requestedAt - cap)
                    stage("cue: decision->audio start", e.startT - e.cue.requestedAt)
                    stage("cue: capture->ear (" + (if (e.cue.earcon == Earcon.NONE) "speech" else "earcon") + ")", ear - cap)
                }
                if (sc.walkerReacts && e.cue.kind == org.guideglass.core.CueKind.STOP && stoppingFrom.isNaN()) {
                    stoppingFrom = ear + 0.15 + sc.reactionS   // 0.15 s to recognise the earcon (docs/00 section 8.1)
                }
            }
        }

        fun ev(e: CoreEvent) = EventLog.apply(p, e).also { events += e }
        take(ev(CoreEvent.Start(0.0)), 0.0)
        var t = 0.0
        while (t <= end + 1e-9) {
            // --- walker kinematics
            val target = scripted(t)
            speed = if (!stoppingFrom.isNaN() && t >= stoppingFrom) {
                if (stopV0 == 0.0) stopV0 = max(speed, 1e-6)
                val decel = stopV0 * stopV0 / (2 * sc.stopDistanceM)
                max(0.0, speed - decel * dt)
            } else target
            x += speed * dt
            walk += t to x
            for (o in sc.obstacles) {
                if (t < o.appearsAt) continue
                val (fx, fy) = world.relative(o, t, x)
                if (abs(fy) <= o.widthM / 2 + 0.25) {
                    minGap[o.id] = minOf(minGap[o.id]!!, fx - 0.0)
                    if (fx <= 0.2 && fx > -0.5 && contact[o.id] == null) contact[o.id] = t
                }
            }

            // --- link, network and audio-route faults
            val linkDown = inAny(t, f.linkDown)
            if (linkDown != linkWasDown) { linkWasDown = linkDown; take(ev(CoreEvent.Link(t, !linkDown)), t) }
            val netDown = inAny(t, f.networkDown)
            if (netDown != netWasDown) { netWasDown = netDown; take(ev(CoreEvent.Network(t, !netDown)), t) }
            val phone = inAny(t, f.audioToPhone)
            if (phone != phoneWas) { phoneWas = phone; take(ev(CoreEvent.Route(t, if (phone) AudioRoute.PHONE else AudioRoute.GLASSES)), t) }

            // --- capture a frame
            if (t + 1e-9 >= nextCapture) {
                val fps = f.fpsOverride.firstOrNull { t in it.first }?.second ?: f.fps
                if (fps != lastFps) { lastFps = fps; ev(CoreEvent.RequestedFps(t, fps)) }
                nextCapture += 1.0 / fps
                val delivered = !linkDown && !inAny(t, f.gaps) && world.nextDouble() >= f.dropProb
                if (delivered) {
                    val yaw = sc.headYaw(t)
                    val frozen = inAny(t, f.frozen)
                    val dets = if (frozen) lastDets else world.perceive(t, x, yaw)
                    val hash = if (frozen) lastHash else ++seq
                    lastDets = dets; lastHash = hash
                    val g = lat.glasses(); val bt = lat.bluetooth(); val dec = lat.decode()
                    val extra = f.extraLatency.firstOrNull { t in it.first }?.second ?: 0.0
                    val inferPeriod = 1.0 / fps
                    val queue = world.nextDouble() * minOf(inferPeriod, 0.04)
                    val det = lat.detector(); val dep = lat.depth()
                    val receive = t + g + bt + dec + extra
                    val done = receive + queue + det + dep
                    stage("1 glasses capture+encode", g); stage("2 bluetooth video", bt + extra); stage("3 decode", dec)
                    stage("4 queue wait", queue); stage("5a detector", det); stage("5b depth", dep)
                    val yawRate = (sc.headYaw(t + 0.01) - sc.headYaw(t - 0.01)) / 0.02
                    val brightness = if (t >= f.darkFrom) 0.05 else 0.8
                    pending += Pending(FrameInput(hash, StageStamps(t, receive, done), dets, hash, yawRate, yaw, brightness), done)
                }
            }

            // --- deliver frames whose perception has finished (latest wins: older ready frames are dropped)
            val ready = pending.filter { it.readyT <= t + 1e-9 }
            if (ready.isNotEmpty()) {
                pending.removeAll(ready)
                val fr = ready.maxBy { it.readyT }
                val walkV = max(0.0, speed + 0.05 * world.nextGaussian())
                deliveries += t to fr.input.contentHash
                val c0 = System.nanoTime()
                val evts = ev(CoreEvent.Frame(t, fr.input, walkV))
                compute += (System.nanoTime() - c0) / 1e9
                onFrameDone?.invoke(t, fr.input, p)
                take(evts, t)
            }

            // --- 10 Hz tick
            if (t - lastTick >= 0.1 - 1e-9) { lastTick = t; take(ev(CoreEvent.Tick(t)), t) }
            take(ev(CoreEvent.Advance(t)), t)

            if (p.health.state != lastState) { lastState = p.health.state; states += t to p.health.state }
            t += dt
        }
        compute.sort()
        return RunResult(sc, cues, p, contact, minGap, stages, compute, states, walk, events, deliveries)
    }

    companion object {
        fun percentile(xs: List<Double>, q: Double): Double {
            if (xs.isEmpty()) return Double.NaN
            val s = xs.sorted()
            val pos = q * (s.size - 1)
            val lo = pos.toInt(); val hi = minOf(lo + 1, s.size - 1)
            return s[lo] + (s[hi] - s[lo]) * (pos - lo)
        }
        fun mean(xs: List<Double>) = if (xs.isEmpty()) Double.NaN else xs.sum() / xs.size
        fun sd(xs: List<Double>): Double { val m = mean(xs); return sqrt(xs.sumOf { (it - m) * (it - m) } / max(1, xs.size - 1)) }
    }
}
