package org.guideglass.core

import org.guideglass.core.nav.Directions
import org.guideglass.core.nav.LatLng
import org.guideglass.core.nav.NavConfig
import org.guideglass.core.nav.Navigator
import org.guideglass.core.sim.NavSim
import org.guideglass.core.sim.NavWalk
import org.guideglass.core.sim.RouteSource
import org.guideglass.core.sim.Segment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Gate G5: navigation on a replayed GPS trace from A to B with predicted-time turn cues. L1. */
class NavigatorTest {
    private val route = Directions.parse(RouteSource.recorded())
    private val nav0 = Navigator(route)
    /** Along-route positions of the manoeuvres: right turn, crossing, left turn, arrival. */
    private val turnRight = 120.0; private val crossing = 200.0; private val turnLeft = 230.0

    @Test fun `recorded response parses without a key (REQ-21)`() {
        assertEquals(4, route.steps.size)
        assertEquals("turn-right", route.steps[1].maneuver)
        assertEquals(330.0, nav0.lengthM, 1.0)
        assertEquals(listOf(LatLng(38.5, -120.2), LatLng(40.7, -120.95), LatLng(43.252, -126.453)),
            Directions.decodePolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@"))  // Google's documented example polyline
    }

    private fun steady(v: Double, seed: Long) = NavWalk(listOf(Segment(330.0 / v + 5, v)), seed = seed)

    /** True time to the manoeuvre when the cue fired. */
    private fun timeTo(c: org.guideglass.core.sim.NavCue, at: Double) = (at - c.trueAlongM) / c.trueSpeed

    @Test fun `turn-now cues fire by predicted time, before the turn, for every seed (NV-01, REQ-20)`() {
        val lead = NavConfig().speechLatencyS + 0.15 + 3 * 0.33 + NavConfig().reactS   // "Turn right now." = 2.9 s
        val errs = mutableListOf<Double>()
        for (seed in 1L..20L) {
            val r = NavSim.run(route, steady(1.3, seed))
            val now = r.cues.filter { it.cue.kind == CueKind.TURN_NOW }
            assertEquals(2, now.size, "seed $seed: both turns announced once")
            for ((c, at) in now.zip(listOf(turnRight, turnLeft))) {
                val tt = timeTo(c, at)
                assertTrue(tt > 0.0, "seed $seed: cue after the turn point ($tt s)")
                errs += tt - lead
            }
            assertEquals(1, r.cues.count { it.cue.kind == CueKind.ARRIVAL })
        }
        val sorted = errs.sorted()
        println("G5 turn-now timing error vs %.1f s lead (true minus intended): median %.2f s, min %.2f, max %.2f (4 m GPS noise)"
            .format(lead, sorted[sorted.size / 2], sorted.first(), sorted.last()))
    }

    @Test fun `a faster walker hears the turn cue farther away, a slower one nearer (NV-03)`() {
        val fast = (1L..10L).map { s -> NavSim.run(route, steady(1.8, s)).cues.first { it.cue.kind == CueKind.TURN_NOW } }
        val slow = (1L..10L).map { s -> NavSim.run(route, steady(0.7, s)).cues.first { it.cue.kind == CueKind.TURN_NOW } }
        val dFast = fast.map { turnRight - it.trueAlongM }.average()
        val dSlow = slow.map { turnRight - it.trueAlongM }.average()
        assertTrue(dFast > dSlow + 2.0, "fast $dFast m, slow $dSlow m")
    }

    @Test fun `no turn cue while the walker stands before the turn (NV-02)`() {
        // Walk to 15 m before the turn, stand 20 s, then continue.
        val w = NavWalk(listOf(Segment(105.0 / 1.3, 1.3), Segment(20.0, 0.0), Segment(60.0, 1.3)), gpsSigmaM = 1.0, seed = 3)
        val r = NavSim.run(route, w)
        val now = r.cues.first { it.cue.kind == CueKind.TURN_NOW }
        val standStart = 105.0 / 1.3
        assertTrue(now.cue.requestedAt !in (standStart + 3.0)..(standStart + 20.0), "turn cue while standing at ${now.cue.requestedAt}")
        assertTrue(now.trueAlongM > 105.0)
    }

    @Test fun `a single sideways GPS jump does not declare off-route, leaving the route does (NV-04, REQ-22)`() {
        for (seed in 1L..10L) {
            val r = NavSim.run(route, steady(1.3, seed).copy(jumpsAt = listOf(30.0, 60.0, 150.0)))
            assertTrue(r.cues.none { it.cue.kind == CueKind.OFF_ROUTE }, "seed $seed")
        }
        val off = NavSim.run(route, NavWalk(listOf(Segment(20.0, 1.3), Segment(40.0, 0.0)), offRoute = 20.0..60.0, seed = 2))
        assertTrue(off.cues.any { it.cue.kind == CueKind.OFF_ROUTE })
    }

    @Test fun `walking back along the route gives a wrong-way notice after confirmation (NV-05)`() {
        val r = NavSim.run(route, NavWalk(listOf(Segment(40.0, 1.3), Segment(15.0, -1.3)), gpsSigmaM = 2.0, seed = 4))
        val c = r.cues.firstOrNull { it.cue.kind == CueKind.WRONG_WAY }
        assertTrue(c != null && c.cue.requestedAt >= 40.0 + 5.0, "wrong-way at ${c?.cue?.requestedAt}")
    }

    @Test fun `a crossing is only ever announced as ahead (NV-06, REQ-23)`() {
        for (seed in 1L..10L) {
            val r = NavSim.run(route, steady(1.3, seed))
            assertEquals(1, r.cues.count { it.cue.kind == CueKind.CROSSING_AHEAD })
            assertTrue(r.cues.none { Regex("cross now|walk now|clear|safe", RegexOption.IGNORE_CASE).containsMatchIn(it.cue.text) })
        }
    }

    @Test fun `pre-announcement comes before turn-now for each turn`() {
        val r = NavSim.run(route, steady(1.3, 5))
        for (k in listOf(1, 3)) {
            val pre = r.cues.first { it.cue.key == "nav-pre-${k - 1}" }
            val now = r.cues.first { it.cue.key == "nav-now-${k - 1}" }
            assertTrue(pre.cue.requestedAt < now.cue.requestedAt - 4.0, pre.cue.text)
        }
    }
}
