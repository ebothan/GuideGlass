package org.guideglass.core

import org.guideglass.core.perception.Camera
import org.guideglass.core.perception.DepthSample
import org.guideglass.core.perception.PitchEstimate
import org.guideglass.core.perception.Ranger
import org.guideglass.core.sim.EventLog
import org.guideglass.core.sim.Runner
import org.guideglass.core.sim.Scenarios
import java.io.File
import kotlin.math.abs
import kotlin.math.atan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RangerTest {
    private val cam = Camera(Math.toRadians(60.0), Math.toRadians(46.0), 1.30)
    private val ranger = Ranger(cam)

    private fun bottomFor(d: Double, pitch: Double) = 0.5 + (atan(1.30 / d) - pitch) / cam.vfovRad

    @Test fun `ground geometry inverts the pinhole model for any distance and pitch`() {
        for (pitchDeg in listOf(-5.0, 0.0, 10.0, 25.0)) for (d in listOf(1.5, 3.0, 6.0, 9.0)) {
            val p = Math.toRadians(pitchDeg)
            val b = bottomFor(d, p)
            if (b !in 0.01..0.99) continue
            val g = assertNotNull(ranger.groundRange(Box(0.5, b, 0.1, 0.2), PitchEstimate(p, 0.0)))
            assertEquals(d, g.first, 1e-6)
        }
    }

    @Test fun `ground range error grows with distance`() {
        val near = ranger.groundRange(Box(0.5, bottomFor(2.0, 0.17), 0.1, 0.2), PitchEstimate(0.17, Math.toRadians(2.0)))!!.second
        val far = ranger.groundRange(Box(0.5, bottomFor(8.0, 0.17), 0.1, 0.2), PitchEstimate(0.17, Math.toRadians(2.0)))!!.second
        assertTrue(far > 4 * near, "near $near far $far")
    }

    @Test fun `no ground range when the foot is cut off or above the horizon`() {
        assertNull(ranger.groundRange(Box(0.5, 1.0, 0.1, 0.5), PitchEstimate(0.1, 0.03)))
        assertNull(ranger.groundRange(Box(0.5, 0.3, 0.1, 0.1), PitchEstimate(0.0, 0.03)))
    }

    @Test fun `fusion is inverse-variance and widens when sources disagree`() {
        val (r, s) = Ranger.fuse(4.0, 1.0, 6.0, 1.0)
        assertEquals(5.0, r, 1e-9); assertEquals(Math.sqrt(0.5), s, 1e-9)
        val (_, sWide) = Ranger.fuse(2.0, 0.2, 6.0, 0.2)
        // Widened from 0.14 m to half the 4 m disagreement.
        assertTrue(sWide >= 2.0 / 2 - 1e-9, "disagreeing sources must not yield false confidence: $sWide")
    }

    @Test fun `head-height objects use depth only`() {
        val det = ranger.detect("branch", Box(0.5, 0.2, 0.3, 0.1), PitchEstimate(0.1, 0.03), DepthSample(3.0, 0.9), headHeight = true)!!
        assertEquals(3.0, det.rangeM, 1e-9)
        assertTrue(det.headHeight)
    }
}

class TrackerTest {
    private fun det(r: Double, lat: Double = 0.0) = Detection("object", Box(0.5, 0.8, 0.1, 0.3), r, 0.2, kotlin.math.atan2(lat, r))

    private fun feed(tr: Tracker, fps: Double, from: Double, to: Double, present: (Double) -> Boolean): Double {
        var t = from
        while (t < to - 1e-9) {
            val d = if (present(t)) listOf(det(6.0 - 1.4 * t)) else emptyList()
            tr.update(FrameInput((t * 1000).toLong(), StageStamps(t, t, t), d, (t * 1000).toLong()), 1.4)
            t += 1.0 / fps
        }
        return t
    }

    @Test fun `confirmation takes the same time at 7, 15 and 30 fps (REQ-05)`() {
        for (fps in listOf(7.0, 15.0, 30.0)) {
            val tr = Tracker(CoreConfig())
            var t = 0.0; var confirmedAt = Double.NaN
            while (t < 1.0) {
                tr.update(FrameInput(0, StageStamps(t, t, t), listOf(det(6.0 - 1.4 * t)), 0), 1.4)
                if (confirmedAt.isNaN() && tr.all().any { it.confirmed }) confirmedAt = t
                t += 1.0 / fps
            }
            assertTrue(confirmedAt in (0.2 - 1e-9)..(0.2 + 1.0 / fps + 1e-9), "$fps fps confirmed at $confirmedAt")
        }
    }

    @Test fun `a track survives a 0_5 s detection gap and keeps its identity`() {
        val tr = Tracker(CoreConfig())
        feed(tr, 15.0, 0.0, 1.0) { true }
        val id = tr.all().single().id
        feed(tr, 15.0, 1.0, 2.0) { t -> t !in 1.2..1.7 }
        assertEquals(listOf(id), tr.all().map { it.id })
    }

    // Coast time is a configuration value (0.3 s since ADR 0006); identity survives up to deleteAfterS (1.0 s).
    @Test fun `a track is deleted after 1 s unseen and stops being alertable after the coast time`() {
        val cfg = CoreConfig()
        val tr = Tracker(cfg)
        val end = feed(tr, 15.0, 0.0, 1.0) { true }
        val lastSeen = tr.all().single().lastSeen
        assertEquals(1, tr.alertable(lastSeen + cfg.coastS - 0.01).size)
        assertEquals(0, tr.alertable(lastSeen + cfg.coastS + 0.01).size)
        tr.update(FrameInput(0, StageStamps(end + 1.1, end + 1.1, end + 1.1), emptyList(), 0), 1.4)
        assertTrue(tr.all().isEmpty())
    }

    @Test fun `no track is confirmed during a fast head turn (REQ-08)`() {
        val tr = Tracker(CoreConfig())
        var t = 0.0
        while (t < 1.0) {
            tr.update(FrameInput(0, StageStamps(t, t, t), listOf(det(5.0)), 0, yawRateRadS = Math.toRadians(120.0)), 1.4)
            t += 1.0 / 15
        }
        assertTrue(tr.all().none { it.confirmed })
    }
}

class AssessorTest {
    /** Distance at which STOP first fires for a still object approached at 1.4 m/s, given decision delay. */
    private fun stopDistance(decisionDelay: Double): Double {
        val cfg = CoreConfig()
        val tr = Tracker(cfg); val a = HazardAssessor(cfg)
        var t = 0.0
        while (t < 6.0) {
            val r = 9.0 - 1.4 * t
            tr.update(FrameInput(0, StageStamps(t, t, t), listOf(Detection("object", Box(0.5, 0.8, 0.1, 0.3), r, 0.01, 0.0)), 0), 1.4)
            val cues = a.decide(tr.alertable(t), t, t + decisionDelay, 1.4, 0.0, true)
            if (cues.any { it.kind == CueKind.STOP }) return r
            t += 1.0 / 30
        }
        return Double.NaN
    }

    @Test fun `STOP fires at 2_5 s to contact with nominal latency, and earlier by exactly the extra latency (REQ-03, REQ-04)`() {
        val cfg = CoreConfig()
        val nominal = cfg.nominalPipelineS - cfg.earconStartS - cfg.audioOutLatencyS
        val dNom = stopDistance(nominal)
        assertEquals(2.5 * 1.4, dNom, 1.4 / 30 + 0.02)          // within one frame step (plus sigma 0.01)
        val dLate = stopDistance(nominal + 0.5)
        assertEquals(0.5 * 1.4, dLate - dNom, 1.4 / 30 + 1e-6)  // frame arriving 0.5 s late -> fires 0.7 m earlier
    }

    @Test fun `a standing user gets no STOP for a still object beyond the close-range floor`() {
        val cfg = CoreConfig()
        val tr = Tracker(cfg); val a = HazardAssessor(cfg)
        var stops = 0; var t = 0.0
        while (t < 5.0) {
            tr.update(FrameInput(0, StageStamps(t, t, t), listOf(Detection("object", Box(0.5, 0.8, 0.1, 0.3), 1.5, 0.05, 0.0)), 0), 0.0)
            stops += a.decide(tr.alertable(t), t, t, 0.0, 0.0, true).count { it.kind == CueKind.STOP }
            t += 1.0 / 15
        }
        assertEquals(0, stops)
    }

    @Test fun `clock positions follow orientation and mobility convention`() {
        assertEquals(12, HazardAssessor.clock(5.0, 0.0))
        assertEquals(1, HazardAssessor.clock(5.0, 2.9))
        assertEquals(11, HazardAssessor.clock(5.0, -2.9))
        assertEquals(3, HazardAssessor.clock(0.01, 5.0))
    }
}

class ArbiterTest {
    private fun cue(kind: CueKind, p: Priority, key: String, t: Double, text: String = "Word word.", stale: Double = 2.0) =
        Cue(kind, p, text, Earcon.NONE, key, t, stale)

    @Test fun `higher priority cuts off lower at once, lower waits (REQ-15)`() {
        val a = CueArbiter(CoreConfig())
        a.submit(cue(CueKind.TURN_PRE, Priority.P3, "nav", 0.0, "In 20 meters, turn left."), 0.0)
        val stop = a.submit(cue(CueKind.STOP, Priority.P0, "stop", 0.3), 0.3).single()
        assertEquals(CueKind.TURN_PRE, stop.interrupted)
        assertTrue(a.submit(cue(CueKind.CAUTION, Priority.P1, "c", 0.35), 0.35).isEmpty(), "P1 waits behind P0")
        assertEquals(CueKind.CAUTION, a.advance(0.3 + stop.cue.durationS).single().cue.kind)
    }

    @Test fun `duplicates are dropped and stale cues expire`() {
        val a = CueArbiter(CoreConfig())
        a.submit(cue(CueKind.CAUTION, Priority.P1, "k", 0.0), 0.0)
        assertTrue(a.submit(cue(CueKind.CAUTION, Priority.P1, "k", 0.1), 0.1).isEmpty())
        assertEquals(Fate.DROPPED_DUPLICATE, a.records.last().fate)
        a.submit(cue(CueKind.TURN_PRE, Priority.P3, "late", 0.2, stale = 0.1), 0.2)
        a.advance(5.0)
        assertTrue(a.records.any { it.fate == Fate.DROPPED_STALE && it.cue.key == "late" })
    }

    @Test fun `non-urgent speech at most once per 4 s, urgent exempt (REQ-17)`() {
        val a = CueArbiter(CoreConfig())
        val starts = mutableListOf<PlayEvent>()
        for (i in 0 until 6) starts += a.submit(cue(CueKind.CLOUD, Priority.P3, "c$i", i * 0.5, "One.", stale = 10.0), i * 0.5)
        var t = 0.0
        while (t < 30) { starts += a.advance(t); t += 0.05 }
        val ts = starts.filter { it.cue.priority == Priority.P3 }.map { it.startT }
        assertTrue(ts.zipWithNext().all { (x, y) -> y - x >= 4.0 - 1e-9 }, ts.toString())
        val b = CueArbiter(CoreConfig())
        val s1 = b.submit(cue(CueKind.CAUTION, Priority.P1, "a", 0.0, "A."), 0.0)
        val s2 = b.submit(cue(CueKind.MOVING_OBJECT, Priority.P1, "b", 0.0, "B."), 0.0) + b.advance(1.0)
        assertEquals(2, (s1 + s2).size)
    }

    @Test fun `a second STOP within 1_5 s is dropped`() {
        val a = CueArbiter(CoreConfig())
        assertEquals(1, a.submit(cue(CueKind.STOP, Priority.P0, "s1", 0.0), 0.0).size)
        assertTrue(a.submit(cue(CueKind.STOP, Priority.P0, "s2", 1.0), 1.0).isEmpty())
        assertEquals(1, a.submit(cue(CueKind.STOP, Priority.P0, "s3", 1.6), 1.6).size)
    }

    @Test fun `cancel removes cloud speech that is playing`() {
        val a = CueArbiter(CoreConfig())
        a.submit(cue(CueKind.CLOUD, Priority.P3, "cloud", 0.0, "A long description of the scene ahead of you."), 0.0)
        a.cancel(0.2) { it.kind == CueKind.CLOUD }
        assertTrue(!a.isBusy)
        assertEquals(Fate.CUT_OFF, a.records.last().fate)
    }
}

class ReplayTest {
    @Test fun `replaying the recorded event log gives the same cues (G2a)`() {
        for (sc in listOf(Scenarios.s07DoubleApproach(3), Scenarios.f05LinkLoss(2), Scenarios.s19Crowd(4))) {
            val run = Runner().run(sc)
            val original = run.pipeline.log.map { Triple(it.play.startT, it.play.cue.kind, it.play.cue.text) }
            val replayed = EventLog.replay(run.events).map { Triple(it.startT, it.cue.kind, it.cue.text) }
            assertEquals(original, replayed, sc.name)
        }
    }

    @Test fun `the JSON Lines log round-trips and replays identically`() {
        val run = Runner().run(Scenarios.s09PersonToward(5))
        val f = File.createTempFile("gg-events", ".jsonl")
        try {
            EventLog.write(run.events, f)
            val back = EventLog.read(f)
            assertEquals(run.events.size, back.size)
            val a = EventLog.replay(run.events).map { it.startT to it.cue.text }
            val b = EventLog.replay(back).map { it.startT to it.cue.text }
            assertEquals(a, b)
            assertTrue(a.isNotEmpty() && abs(a.first().first) < 1e-9)
        } finally { f.delete() }
    }
}
