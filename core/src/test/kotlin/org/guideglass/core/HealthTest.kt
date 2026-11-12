package org.guideglass.core

import org.guideglass.core.sim.Faults
import org.guideglass.core.sim.Runner
import org.guideglass.core.sim.RunResult
import org.guideglass.core.sim.Scenarios
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Gate G4: the safety path survives loss of camera, Bluetooth, audio route and network, and the user
 * hears a distinct degraded cue within the stated time (REQ-10 to REQ-14; docs/01 section 3). L1.
 */
class HealthTest {
    private val runner = Runner()
    private val seeds = 1L..10L

    /** Arrival time of the last fresh frame before [t]. */
    private fun lastFreshBefore(r: RunResult, t: Double): Double {
        var last = Double.NEGATIVE_INFINITY; var prevHash: Long? = null
        for ((at, h) in r.deliveries) { if (at > t) break; if (h != prevHash) last = at; prevHash = h }
        return last
    }

    private fun first(r: RunResult, k: CueKind) = r.of(k).firstOrNull()

    @Test fun `camera stream stops - CAMERA_LOST starts within 0_6 s of the last fresh frame, then CAMERA_BACK (REQ-10)`() {
        for (s in seeds) {
            val r = runner.run(Scenarios.f01CameraGap(s))
            val lost = first(r, CueKind.CAMERA_LOST) ?: error("no CAMERA_LOST")
            val delay = lost.play.startT - lastFreshBefore(r, lost.play.startT)
            assertTrue(delay <= 0.6 + 1e-9, "seed $s delay $delay")
            assertTrue(lost.play.cue.vibrate && lost.play.cue.earcon == Earcon.LOST, "distinct earcon and vibration")
            val back = first(r, CueKind.CAMERA_BACK) ?: error("no CAMERA_BACK")
            assertTrue(back.play.startT >= 8.0 + 3.0 - 0.1, "back only after 3 s of fresh frames, was ${back.play.startT}")
        }
    }

    @Test fun `frozen stream is detected as camera loss (REQ-10, F-02)`() {
        for (s in seeds) {
            val r = runner.run(Scenarios.f02Frozen(s))
            val lost = first(r, CueKind.CAMERA_LOST) ?: error("no CAMERA_LOST")
            assertTrue(lost.play.startT - lastFreshBefore(r, lost.play.startT) <= 0.6 + 1e-9)
            assertTrue(lost.play.startT in 4.0..5.0)
        }
    }

    @Test fun `perception stall is reported like camera loss (F-09)`() {
        for (s in seeds) assertTrue(first(runner.run(Scenarios.f09Stall(s)), CueKind.CAMERA_LOST) != null)
    }

    @Test fun `while the camera is lost no hazard cue is given and the user has been told before contact`() {
        for (s in seeds) {
            val r = runner.run(Scenarios.f01HazardDuringGap(s))
            val lost = first(r, CueKind.CAMERA_LOST) ?: error("no CAMERA_LOST")
            val contact = r.contactT[1] ?: error("expected open-loop contact in this scenario")
            assertTrue(lost.earT < contact - 2.0, "told well before contact")
            assertTrue(r.cues.none { it.kind == CueKind.STOP && it.play.startT > lost.play.startT }, "no hazard cue from stale data")
        }
    }

    @Test fun `bluetooth loss - GLASSES_LOST on the phone speaker within 1 s, then GLASSES_BACK (REQ-11)`() {
        for (s in seeds) {
            val r = runner.run(Scenarios.f05LinkLoss(s))
            val lost = first(r, CueKind.GLASSES_LOST) ?: error("no GLASSES_LOST")
            assertEquals(AudioRoute.PHONE, lost.play.route)
            assertTrue(lost.play.startT - 4.0 <= 1.0)
            assertTrue(first(r, CueKind.GLASSES_BACK) != null)
            assertTrue(r.of(CueKind.CAMERA_LOST).isEmpty(), "link loss is reported once, as glasses lost")
        }
    }

    @Test fun `audio route lost - announced on the phone, hazard cues follow it (REQ-11, F-06)`() {
        val r = runner.run(Scenarios.f06AudioRoute(1))
        val rr = first(r, CueKind.AUDIO_REROUTED) ?: error("no AUDIO_REROUTED")
        assertEquals(AudioRoute.PHONE, rr.play.route)
        assertTrue(rr.play.startT - 4.0 <= 1.0)
        assertTrue(first(r, CueKind.AUDIO_BACK) != null)
        // A hazard during the reroute is played on the phone.
        val sc = Scenarios.s03Bin(1).let { it.copy(faults = Faults(audioToPhone = listOf(0.0..20.0))) }
        val stop = runner.run(sc).of(CueKind.STOP).first()
        assertEquals(AudioRoute.PHONE, stop.play.route)
    }

    @Test fun `low frame rate - REDUCED once, not CAMERA_LOST, and recovery announced (REQ-12, F-03)`() {
        for (s in seeds) {
            val r = runner.run(Scenarios.f03LowFps(s))
            assertTrue(r.of(CueKind.CAMERA_LOST).isEmpty(), "seed $s: 2 fps is reduced, not lost")
            assertEquals(1, r.of(CueKind.REDUCED).size)
            assertTrue(first(r, CueKind.DETECTION_NORMAL) != null)
        }
    }

    @Test fun `latency spike is announced as a degraded state (REQ-12, F-04)`() {
        for (s in seeds) {
            val r = runner.run(Scenarios.f04Latency(s))
            assertTrue(r.cues.any { it.kind == CueKind.REDUCED || it.kind == CueKind.CAMERA_LOST })
        }
    }

    @Test fun `network loss - one notice, and hazard cues are identical with and without it (REQ-01, REQ-13)`() {
        val r = runner.run(Scenarios.f07Network(1))
        assertEquals(1, r.of(CueKind.OFFLINE).size)
        for (s in seeds) {
            val base = Scenarios.s03Bin(s)
            val a = runner.run(base).cues.filter { it.kind == CueKind.STOP || it.kind == CueKind.CAUTION }.map { it.play.startT }
            val b = runner.run(base.copy(faults = Faults(networkDown = listOf(0.5..20.0)))).cues
                .filter { it.kind == CueKind.STOP || it.kind == CueKind.CAUTION }.map { it.play.startT }
            assertEquals(a, b, "seed $s")
        }
    }

    @Test fun `low light - OUT_OF_ODD announced (S-20)`() {
        val r = runner.run(Scenarios.s20Dark(1))
        val c = first(r, CueKind.OUT_OF_ODD) ?: error("no OUT_OF_ODD")
        assertTrue(c.play.startT in 6.0..7.0)
    }

    @Test fun `start and stop of protection are marked by distinct chimes (REQ-14)`() {
        val p = SafetyPipeline()
        val start = p.start(0.0).single()
        assertEquals(Earcon.START, start.cue.earcon)
        val end = p.stop(10.0).single()
        assertEquals(Earcon.END, end.cue.earcon)
    }

    @Test fun `camera loss cue waits at most one equal-priority cue (documented bound on REQ-10)`() {
        for (s in seeds) {
            val r = runner.run(Scenarios.f01HazardDuringGap(s))
            val lost = first(r, CueKind.CAMERA_LOST)!!
            val wait = lost.play.startT - lastFreshBefore(r, lost.play.startT)
            assertTrue(wait <= 0.6 + 1.6, "seed $s wait $wait")
        }
    }
}
