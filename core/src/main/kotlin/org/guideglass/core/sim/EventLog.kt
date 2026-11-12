package org.guideglass.core.sim

import org.guideglass.core.AudioRoute
import org.guideglass.core.Box
import org.guideglass.core.CoreConfig
import org.guideglass.core.Detection
import org.guideglass.core.FrameInput
import org.guideglass.core.PlayEvent
import org.guideglass.core.SafetyPipeline
import org.guideglass.core.StageStamps
import org.guideglass.core.util.Json
import java.io.File

/**
 * Log replay, as used for automated-driving stacks: every input the core receives is recorded with its
 * time, and replaying the log into a fresh core must give exactly the same cues (the core is deterministic,
 * ADR 0002). The log is JSON Lines, one event per line, so a recording from the phone can be replayed here.
 */
sealed class CoreEvent {
    abstract val t: Double
    data class Start(override val t: Double) : CoreEvent()
    data class Frame(override val t: Double, val input: FrameInput, val walkSpeed: Double) : CoreEvent()
    data class Tick(override val t: Double) : CoreEvent()
    data class Advance(override val t: Double) : CoreEvent()
    data class Link(override val t: Double, val up: Boolean) : CoreEvent()
    data class Network(override val t: Double, val up: Boolean) : CoreEvent()
    data class Route(override val t: Double, val route: AudioRoute) : CoreEvent()
    data class RequestedFps(override val t: Double, val fps: Double) : CoreEvent()
}

object EventLog {
    fun apply(p: SafetyPipeline, e: CoreEvent): List<PlayEvent> = when (e) {
        is CoreEvent.Start -> p.start(e.t)
        is CoreEvent.Frame -> { p.onWalkSpeed(e.walkSpeed); p.onFrame(e.input, e.t) }
        is CoreEvent.Tick -> p.onTick(e.t)
        is CoreEvent.Advance -> p.advance(e.t)
        is CoreEvent.Link -> p.onLink(e.up, e.t)
        is CoreEvent.Network -> p.onNetwork(e.up, e.t)
        is CoreEvent.Route -> p.onAudioRoute(e.route, e.t)
        is CoreEvent.RequestedFps -> { p.health.onRequestedFps(e.fps); emptyList() }
    }

    fun replay(events: List<CoreEvent>, cfg: CoreConfig = CoreConfig()): List<PlayEvent> {
        val p = SafetyPipeline(cfg)
        return events.flatMap { apply(p, it) }
    }

    // ------------------------------------------------------------------ JSON Lines
    private fun d(x: Double) = if (x.isFinite()) x.toString() else "null"

    fun toJson(e: CoreEvent): String = when (e) {
        is CoreEvent.Start -> """{"type":"start","t":${e.t}}"""
        is CoreEvent.Tick -> """{"type":"tick","t":${e.t}}"""
        is CoreEvent.Advance -> """{"type":"advance","t":${e.t}}"""
        is CoreEvent.Link -> """{"type":"link","t":${e.t},"up":${e.up}}"""
        is CoreEvent.Network -> """{"type":"network","t":${e.t},"up":${e.up}}"""
        is CoreEvent.Route -> """{"type":"route","t":${e.t},"route":"${e.route}"}"""
        is CoreEvent.RequestedFps -> """{"type":"fps","t":${e.t},"fps":${e.fps}}"""
        is CoreEvent.Frame -> {
            val f = e.input
            val dets = f.detections.joinToString(",") { x ->
                """{"cls":"${x.cls}","cx":${x.box.cx},"bottom":${x.box.bottom},"wf":${x.box.wf},"hf":${x.box.hf},""" +
                    """"range":${x.rangeM},"sigma":${x.rangeSigmaM},"bearing":${x.bearingRad},"width":${x.widthM},"head":${x.headHeight}}"""
            }
            """{"type":"frame","t":${e.t},"walk":${e.walkSpeed},"seq":${f.seq},"capture":${f.stamps.capture},""" +
                """"receive":${f.stamps.receive},"done":${f.stamps.perceptionDone},"hash":${f.contentHash},""" +
                """"yawRate":${d(f.yawRateRadS ?: Double.NaN)},"headToTravel":${f.headToTravelRad},"brightness":${f.brightness},"dets":[$dets]}"""
        }
    }

    fun fromJson(line: String): CoreEvent {
        val o = Json.obj(Json.parse(line))
        val t = o["t"] as Double
        fun dd(k: String) = (o[k] as Number).toDouble()
        return when (o["type"]) {
            "start" -> CoreEvent.Start(t)
            "tick" -> CoreEvent.Tick(t)
            "advance" -> CoreEvent.Advance(t)
            "link" -> CoreEvent.Link(t, o["up"] as Boolean)
            "network" -> CoreEvent.Network(t, o["up"] as Boolean)
            "route" -> CoreEvent.Route(t, AudioRoute.valueOf(o["route"] as String))
            "fps" -> CoreEvent.RequestedFps(t, dd("fps"))
            "frame" -> {
                val dets = Json.arr(o["dets"]).map { dv ->
                    val m = Json.obj(dv)
                    fun g(k: String) = m[k] as Double
                    Detection(m["cls"] as String, Box(g("cx"), g("bottom"), g("wf"), g("hf")), g("range"), g("sigma"),
                        g("bearing"), g("width"), m["head"] as Boolean)
                }
                CoreEvent.Frame(t, FrameInput(dd("seq").toLong(), StageStamps(dd("capture"), dd("receive"), dd("done")), dets,
                    dd("hash").toLong(), o["yawRate"] as Double?, dd("headToTravel"), dd("brightness")), dd("walk"))
            }
            else -> error("unknown event ${o["type"]}")
        }
    }

    fun write(events: List<CoreEvent>, file: File) = file.bufferedWriter().use { w -> events.forEach { w.write(toJson(it)); w.newLine() } }
    fun read(file: File): List<CoreEvent> = file.readLines().filter { it.isNotBlank() }.map { fromJson(it) }
}
