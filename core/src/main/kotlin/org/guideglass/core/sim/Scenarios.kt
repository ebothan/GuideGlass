package org.guideglass.core.sim

import kotlin.math.PI
import kotlin.math.sin

/**
 * Scenario library: IDs match docs/00-problem-constraints-goals.md section 6.
 * Each builder takes a seed so tests can run many noise realisations of the same situation.
 */
object Scenarios {
    private fun walk(s: Double, v: Double = 1.4) = listOf(Segment(s, v))

    // ---------------------------------------------------------------- obstacles (6.1)
    fun s01Person(seed: Long = 1, react: Boolean = false) = Scenario("S-01 person standing in path",
        listOf(Obstacle(1, "person", 10.0, 0.1, 1.7, 0.5)), walk(9.0), walkerReacts = react, seed = seed)

    fun s02Pole(seed: Long = 1, react: Boolean = false) = Scenario("S-02 thin pole in path",
        listOf(Obstacle(1, "pole", 9.0, -0.15, 2.5, 0.12)), walk(8.0), walkerReacts = react, seed = seed)

    fun s03Bin(seed: Long = 1, react: Boolean = false, pDetect: Double = 1.0) = Scenario("S-03 low bin in path",
        listOf(Obstacle(1, "bin", 9.0, 0.2, 0.9, 0.6)), walk(8.0), walkerReacts = react, seed = seed,
        perception = Perception(pDetect = pDetect))

    fun s03LowBollard(seed: Long = 1, react: Boolean = false) = Scenario("S-03 low bollard in path",
        listOf(Obstacle(1, "bollard", 8.0, 0.0, 0.5, 0.2)), walk(7.0), walkerReacts = react, seed = seed)

    fun s04HeadHeight(seed: Long = 1, react: Boolean = false) = Scenario("S-04 branch at head height",
        listOf(Obstacle(1, "branch", 8.0, 0.1, 0.4, 1.0, bottomM = 1.55)), walk(7.0), walkerReacts = react, seed = seed)

    fun s05Beside(seed: Long = 1) = Scenario("S-05 parked car beside path",
        listOf(Obstacle(1, "car", 9.0, 1.9, 1.5, 1.8)), walk(9.0), seed = seed)

    fun s06FarWall(seed: Long = 1) = Scenario("S-06 distant wall",
        listOf(Obstacle(1, "wall", 13.0, 0.0, 2.5, 20.0)), walk(4.0), seed = seed)

    /** The repo's double approach: walk in, stop, back off, wait, approach again. */
    fun s07DoubleApproach(seed: Long = 1) = Scenario("S-07 stop, back off, approach again",
        listOf(Obstacle(1, "bollard", 6.5, 0.0, 0.9, 0.2)),
        listOf(Segment(3.4, 1.4), Segment(2.0, 0.0), Segment(4.0, -0.8), Segment(2.0, 0.0), Segment(2.4, 1.4), Segment(2.0, 0.0)),
        seed = seed)

    fun s08StopAndStand(seed: Long = 1) = Scenario("S-08 stop in front of obstacle and stand",
        listOf(Obstacle(1, "bollard", 6.0, 0.0, 0.9, 0.2)), listOf(Segment(3.2, 1.4), Segment(10.0, 0.0)), seed = seed)

    fun s09PersonToward(seed: Long = 1, react: Boolean = false) = Scenario("S-09 pedestrian walking toward user",
        listOf(Obstacle(1, "person", 16.0, 0.15, 1.7, 0.5, vx = -1.3)), walk(7.0), walkerReacts = react, seed = seed)

    fun s10BikeToward(seed: Long = 1, react: Boolean = false) = Scenario("S-10 cyclist approaching head-on",
        listOf(Obstacle(1, "bicycle", 30.0, 0.2, 1.7, 0.6, vx = -4.5)), walk(6.0), walkerReacts = react, seed = seed,
        perception = Perception(maxRangeM = 20.0))

    /** Crossing cyclist that passes 5+ m in front: not a collision course. */
    fun s11BikeCrossingClear(seed: Long = 1) = Scenario("S-11 cyclist crossing well ahead",
        listOf(Obstacle(1, "bicycle", 9.0, -7.0, 1.7, 0.6, vy = 4.5)), walk(4.0), seed = seed)

    /** Crossing cyclist timed to enter the walking path as the user arrives. */
    fun s11BikeCrossingCollision(seed: Long = 1) = Scenario("S-11 cyclist crossing into the user's path",
        listOf(Obstacle(1, "bicycle", 5.6, -4.5, 1.7, 0.6, vy = 1.5)), walk(4.5), seed = seed)

    fun s12SameDirection(seed: Long = 1) = Scenario("S-12 pedestrian ahead walking the same way",
        listOf(Obstacle(1, "person", 5.0, 0.1, 1.7, 0.5, vx = 1.4)), walk(10.0), seed = seed)

    /** Head sweeps +/-60 degrees at up to about 190 deg/s while walking past roadside objects. */
    fun s13HeadSweep(seed: Long = 1) = Scenario("S-13 fast head turns past roadside objects",
        listOf(Obstacle(1, "pole", 6.0, 1.8, 2.5, 0.15), Obstacle(2, "bin", 8.0, -2.0, 0.9, 0.6), Obstacle(3, "person", 10.0, 2.2, 1.7, 0.5)),
        walk(6.0), headYaw = { t -> Math.toRadians(60.0) * sin(2 * PI * 0.5 * t) }, seed = seed)

    fun s14Pitch(base: Scenario, pitchDeg: Double) = base.copy(name = "${base.name} @pitch $pitchDeg", pitchRad = Math.toRadians(pitchDeg))

    fun s18Barrier(seed: Long = 1, react: Boolean = false) = Scenario("S-18 construction barrier across sidewalk",
        listOf(Obstacle(1, "barrier", 9.0, 0.0, 1.0, 3.0)), walk(8.0), walkerReacts = react, seed = seed)

    fun s19Crowd(seed: Long = 1) = Scenario("S-19 crowd ahead",
        listOf(
            Obstacle(1, "person", 7.0, 0.3, 1.7, 0.5, vx = -1.0), Obstacle(2, "person", 9.0, -0.4, 1.7, 0.5, vx = -1.2),
            Obstacle(3, "person", 11.0, 0.8, 1.7, 0.5, vx = -0.9), Obstacle(4, "person", 8.0, -1.2, 1.7, 0.5),
            Obstacle(5, "person", 12.0, 0.0, 1.7, 0.5, vx = -1.1), Obstacle(6, "person", 6.0, 1.5, 1.7, 0.5),
        ), walk(5.0), seed = seed)

    // ---------------------------------------------------------------- faults (6.2), open sidewalk with one far obstacle
    private fun quiet(name: String, f: Faults, s: Double = 14.0, seed: Long = 1) = Scenario(name,
        listOf(Obstacle(1, "pole", 40.0, 2.0, 2.5, 0.15)), walk(s), faults = f, seed = seed)

    fun f01CameraGap(seed: Long = 1) = quiet("F-01 camera stream stops 4-8 s", Faults(gaps = listOf(4.0..8.0)), seed = seed)
    fun f02Frozen(seed: Long = 1) = quiet("F-02 frozen frames 4-8 s", Faults(frozen = listOf(4.0..8.0)), seed = seed)
    fun f03LowFps(seed: Long = 1) = quiet("F-03 frame rate 7 fps then 2 fps",
        Faults(fpsOverride = listOf(3.0..7.0 to 7.0, 7.0..12.0 to 2.0)), s = 22.0, seed = seed)
    fun f04Latency(seed: Long = 1) = quiet("F-04 1 s latency spike 4-8 s", Faults(extraLatency = listOf(4.0..8.0 to 1.0)), seed = seed)
    fun f05LinkLoss(seed: Long = 1) = quiet("F-05 Bluetooth link lost 4-8 s", Faults(linkDown = listOf(4.0..8.0)), seed = seed)
    fun f06AudioRoute(seed: Long = 1) = quiet("F-06 glasses audio route lost 4-8 s", Faults(audioToPhone = listOf(4.0..8.0)), seed = seed)
    fun f07Network(seed: Long = 1) = quiet("F-07 network lost 2-10 s", Faults(networkDown = listOf(2.0..10.0)), seed = seed)
    fun f09Stall(seed: Long = 1) = quiet("F-09 perception stall (no output) 4-6 s", Faults(gaps = listOf(4.0..6.0)), seed = seed)
    fun s20Dark(seed: Long = 1) = quiet("S-20 low light from 4 s", Faults(darkFrom = 4.0), seed = seed)

    /** Hazard while the camera is lost: the system must not pretend to see it. */
    fun f01HazardDuringGap(seed: Long = 1) = Scenario("F-01 obstacle approached while the camera is lost",
        listOf(Obstacle(1, "pole", 9.0, 0.0, 2.5, 0.15)), walk(7.0), faults = Faults(gaps = listOf(1.0..10.0)), seed = seed)

    /** Obstacle approach with the link degraded to 7 fps: tracking must still work. */
    fun s03At(fps: Double, seed: Long = 1, pDetect: Double = 1.0) = s03Bin(seed, pDetect = pDetect).let {
        it.copy(name = "${it.name} @${fps}fps", faults = Faults(fps = fps))
    }
}
