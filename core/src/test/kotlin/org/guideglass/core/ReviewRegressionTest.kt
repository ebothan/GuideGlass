package org.guideglass.core

import org.guideglass.core.cloud.CloudGuard
import org.guideglass.core.cloud.CloudRequest
import org.guideglass.core.cloud.DeviceObject
import org.guideglass.core.cloud.RequestKind
import org.guideglass.core.cloud.SpeakContext
import org.guideglass.core.nav.Directions
import org.guideglass.core.sim.Faults
import org.guideglass.core.sim.NavSim
import org.guideglass.core.sim.NavWalk
import org.guideglass.core.sim.Obstacle
import org.guideglass.core.sim.RouteSource
import org.guideglass.core.sim.Runner
import org.guideglass.core.sim.Scenario
import org.guideglass.core.sim.Scenarios
import org.guideglass.core.sim.Segment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cases from the independent adversarial review (docs/review/adversarial-review.md), run on seeds 31-130,
 * which were not used while the logic was tuned. Finding numbers refer to that report. Where a test bounds
 * a rate instead of requiring zero, the residual is reported in docs/review/response.md.
 */
class ReviewRegressionTest {
    private val runner = Runner()
    private val seeds = 31L..130L
    private fun contacts(b: (Long) -> Scenario) = seeds.count { s -> runner.run(b(s)).contactT.values.any { it != null } }

    @Test fun `finding 1 - stop, stand, walk on - a second STOP is given and a walker who obeys it does not make contact`() {
        for (stand in listOf(2.0, 5.0, 15.0)) {
            var silent = 0
            for (s in seeds) {
                val sc = Scenario("stop-stand-resume", listOf(Obstacle(1, "bollard", 6.0, 0.0, 0.9, 0.2)),
                    listOf(Segment(2.6, 1.4), Segment(stand, 0.0), Segment(4.0, 1.4)), seed = s)
                val r = runner.run(sc)
                val resume = 2.6 + stand
                if (r.of(CueKind.STOP).none { it.play.startT >= resume }) silent++
            }
            assertEquals(0, silent, "stand $stand s: runs with no STOP after resuming")
        }
    }

    private fun person(seed: Long, f: Faults = Faults(), x: Double = 10.0, appears: Double = 0.0, cls: String = "person") =
        Scenario("standing $cls", listOf(Obstacle(1, cls, x, 0.1, 1.7, 0.5, appearsAt = appears)), listOf(Segment(9.0, 1.4)),
            faults = f, walkerReacts = true, seed = seed)

    @Test fun `finding 2 - a standing person gets a timely STOP at low frame rate, with frame loss, latency and in low light`() {
        assertEquals(0, contacts { person(it, Faults(fps = 5.0)) }, "5 fps")
        assertEquals(0, contacts { person(it, Faults(fps = 7.0)) }, "7 fps")
        // Measured 1 in 100 when written; kept as a bound and reported as a residual, not tuned away.
        assertTrue(contacts { person(it, Faults(fps = 10.0, dropProb = 0.2)) } <= 2, "10 fps, 20% loss")
        assertEquals(0, contacts { person(it, Faults(extraLatency = listOf(0.0..20.0 to 0.4))) }, "+0.4 s latency")
        assertEquals(0, contacts { person(it, Faults(darkFrom = 0.0)) }, "low light")
        assertEquals(0, contacts { person(it, cls = "car") }, "parked car in path")
        assertEquals(0, contacts { person(it, cls = "bicycle") }, "parked bike in path")
    }

    @Test fun `finding 2 - a person first seen 3 m ahead - STOP is immediate, contact rate bounded`() {
        // Seen at 3.0 m while walking at 1.4 m/s leaves 2.1 s in total; pipeline, reaction and one step use about 2.0 s.
        // No logic can make this comfortable: about one run in five still ends in contact (19.7% over 300 seeds,
        // docs/results/scenario_rates.md). Gate at the upper 95% limit as a tripwire; before the fix it was 98%.
        val n = contacts { s -> person(s, x = 7.2, appears = 3.0) }   // walker is at 4.2 m when the person appears at 7.2 m
        assertTrue(n <= 25, "contacts $n/100")
    }

    @Test fun `finding 8 - two bollards 1_2 m apart - the second one gets its own STOP`() {
        var noSecond = 0
        for (s in seeds) {
            val sc = Scenario("two bollards", listOf(Obstacle(1, "bollard", 6.0, -0.2, 0.9, 0.2), Obstacle(2, "bollard", 7.2, 0.25, 0.9, 0.2)),
                listOf(Segment(3.0, 1.4), Segment(3.0, 0.0), Segment(0.9, 1.4), Segment(2.0, 0.0)), seed = s)
            val stops = runner.run(sc).of(CueKind.STOP)
            if (stops.size < 2) noSecond++
        }
        assertEquals(0, noSecond)
    }

    @Test fun `finding 9 - a frame stamped 60 s in the future does not hide camera loss`() {
        val p = SafetyPipeline()
        p.start(0.0)
        var t = 0.0; var seq = 0L
        while (t < 2.0) { p.onFrame(FrameInput(seq, StageStamps(t - 0.2, t - 0.1, t), emptyList(), seq++), t); p.onTick(t); t += 1.0 / 15 }
        p.onFrame(FrameInput(seq, StageStamps(t + 60.0, t + 60.0, t + 60.0), emptyList(), seq), t)
        var lostAt = Double.NaN
        while (t < 6.0 && lostAt.isNaN()) { if (p.onTick(t).any { it.cue.kind == CueKind.CAMERA_LOST }) lostAt = t; t += 0.05 }
        assertTrue(lostAt <= 2.0 + 0.7, "camera lost announced at $lostAt")
    }

    @Test fun `finding 16 - every frame 3 s late is treated as camera lost, not as usable`() {
        val sc = Scenarios.s03Bin(40).copy(faults = Faults(extraLatency = listOf(0.0..20.0 to 3.0)))
        val r = runner.run(sc)
        assertTrue(r.of(CueKind.CAMERA_LOST).isNotEmpty())
        assertTrue(r.of(CueKind.STOP).isEmpty(), "no STOP from 3 s old frames")
    }

    @Test fun `finding 16 - camera lost is not delayed by cautions in a crowd`() {
        var worst = 0.0
        for (s in seeds) {
            val sc = Scenarios.s19Crowd(s).let { it.copy(faults = Faults(gaps = listOf(1.5..9.0))) }
            val r = runner.run(sc)
            val lost = r.of(CueKind.CAMERA_LOST).first()
            var last = 0.0; for ((at, _) in r.deliveries) { if (at > lost.play.startT) break; last = at }
            worst = maxOf(worst, lost.play.startT - last)
        }
        // 0.6 s detection plus at most one "Stop." (0.63 s) that may not be cut off.
        assertTrue(worst <= 0.6 + 0.65, "worst wait $worst s")
    }

    @Test fun `finding 11 - slow walker at 0_6 m per s who stops on STOP does not make contact`() {
        assertEquals(0, contacts { s -> Scenarios.s03Bin(s, react = true).copy(walk = listOf(Segment(20.0, 0.6))) })
    }

    @Test fun `finding 14 - a guide dog or companion keeping 1 m ahead does not cause repeated STOPs`() {
        var total = 0
        for (s in 31L..60L) {
            val sc = Scenario("guide dog", listOf(Obstacle(1, "dog", 1.0, 0.2, 0.6, 0.3, vx = 1.4)), listOf(Segment(60.0, 1.4)), seed = s)
            total += runner.run(sc).of(CueKind.STOP).count { it.play.startT > 5.0 }
        }
        // After the first 5 s (one STOP at first sight, before the dog's motion can be measured, is expected and
        // reported): repeated STOPs per walking minute, averaged over 30 runs.
        assertTrue(total / 30.0 <= 0.2, "repeated STOPs per minute ${total / 30.0}")
    }

    // ------------------------------------------------------------------ cloud guard (findings 3 and 5)
    private fun speak(text: String, kind: String = "description", action: String = "none", scene: String? = null,
                      rk: RequestKind = RequestKind.DESCRIBE): String {
        val g = CloudGuard(); g.register(CloudRequest("f", rk, 10.0, 0.0, 0.0, 0.0, 2))
        val sc = scene?.let { ",\"scene_text_verbatim\":\"$it\"" } ?: ""
        val raw = """{"schema_version":"gg-cloud-1","frame_id":"f","answerable":true,"items":[{"kind":"$kind","text":"$text","action":"$action","confidence":"high"$sc}]}"""
        val ctx = SpeakContext(11.0, 0.0, 0.5, 0.0, 2, false, listOf(DeviceObject("traffic_light", 0.0, 12.0, 0.1)), obstacleInPath = true, freeSpaceAheadM = 6.0)
        return g.onAnswer(raw, ctx).cues.joinToString(" ") { it.text }
    }

    @Test fun `finding 3 - the reviewer's 24 paraphrases are not spoken, as description or advisory`() {
        val paraphrases = listOf("Nothing is blocking you", "It's fine to go", "The way ahead is open", "You may proceed", "No cars are coming",
            "The road is empty", "There is no traffic", "The light is green for you", "It is OK to cross", "The cars are waiting for you to cross",
            "The sidewalk is free of obstacles", "Obstacle free ahead", "All good ahead, keep walking", "The door is open, walk straight through it",
            "A bench is on the left, so just step into the road to pass it", "The crossing is empty; cross quickly",
            "You should turn left into the road here", "Please walk forward", "Traffic has stopped for you", "The path is clear",
            "It is safe to continue", "cl​ear path in front", "The pаth is open", "Go ahead, nobody is there")
        for (p in paraphrases) for (kind in listOf("description", "advisory")) {
            assertEquals("", speak(p, kind), "spoken: '$p' as $kind")
        }
    }

    @Test fun `finding 3 - forward is never spoken even with free space reported, and crossing is always refused`() {
        assertEquals("", speak("Continue", kind = "action_suggestion", action = "forward"))
        assertEquals("I can't help with crossing.", speak("Signal", kind = "scene_text", scene = "DON'T WALK", rk = RequestKind.CAN_I_CROSS))
    }

    @Test fun `finding 5 - no sign text can make the guard throw or be read out as an instruction`() {
        for (sign in listOf("KEEP CLEAR", "FIRE EXIT KEEP CLEAR", "SAFE DEPOSIT", "Clear Lake Cafe", "CROSS NOW", "WALK")) {
            assertEquals("A sign has text on it.", speak("A sign", kind = "scene_text", scene = sign, rk = RequestKind.READ_TEXT), sign)
        }
        assertEquals("A sign reads: \"Platform 3\".", speak("A sign", kind = "scene_text", scene = "Platform 3", rk = RequestKind.READ_TEXT))
        val g = CloudGuard()
        for (junk in listOf("", "{", "null", "[]", "{\"schema_version\":\"gg-cloud-1\"}", "\u0000\u0001", "{\"items\":[{}]}")) {
            g.register(CloudRequest("j", RequestKind.DESCRIBE, 0.0, 0.0, 0.0, 0.0, 0))
            g.onAnswer(junk, SpeakContext(1.0, 0.0, 0.0, 0.0, 0, false))   // must not throw
        }
    }

    // ------------------------------------------------------------------ navigation (finding 10)
    @Test fun `finding 10 - no navigation cue says now`() {
        val route = Directions.parse(RouteSource.recorded())
        for (s in 21L..60L) {
            val r = NavSim.run(route, NavWalk(listOf(Segment(330.0 / 1.3 + 5, 1.3)), seed = s))
            assertTrue(r.cues.none { Regex("\\bnow\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.cue.text) })
            assertTrue(r.cues.any { it.cue.text == "Turn right at the next corner." || it.cue.text == "Turn right soon." })
        }
    }
}
