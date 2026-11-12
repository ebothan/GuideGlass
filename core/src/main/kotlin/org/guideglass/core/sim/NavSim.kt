package org.guideglass.core.sim

import org.guideglass.core.Cue
import org.guideglass.core.nav.Directions
import org.guideglass.core.nav.GpsFix
import org.guideglass.core.nav.NavConfig
import org.guideglass.core.nav.Navigator
import org.guideglass.core.nav.Route
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Properties

/**
 * Route source for the JVM tools (gate G5). With a key in the environment variable GOOGLE_DIRECTIONS_KEY
 * or in local.properties, it calls the Directions API (walking mode); otherwise it loads the bundled
 * synthetic response. The live path is implemented but unverified: no key was available when it was written.
 */
object RouteSource {
    fun key(repoRoot: File = File(".")): String? {
        System.getenv("GOOGLE_DIRECTIONS_KEY")?.takeIf { it.isNotBlank() }?.let { return it }
        val lp = File(repoRoot, "local.properties")
        if (!lp.exists()) return null
        val p = Properties().apply { lp.inputStream().use { load(it) } }
        return p.getProperty("GOOGLE_DIRECTIONS_KEY")?.trim()?.trim('"')?.takeIf { it.isNotBlank() && !it.startsWith("your_") }
    }

    fun recorded(): String = RouteSource::class.java.getResource("/routes/directions_synthetic.json")!!.readText()

    fun live(origin: String, destination: String, key: String): String {
        fun e(s: String) = URLEncoder.encode(s, Charsets.UTF_8)
        val uri = URI("https://maps.googleapis.com/maps/api/directions/json?mode=walking&origin=${e(origin)}&destination=${e(destination)}&key=${e(key)}")
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
        val resp = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString())
        check(resp.statusCode() == 200) { "Directions HTTP ${resp.statusCode()}" }
        return resp.body()
    }

    /** Live route if a key is configured and the request works, else the recorded one; says which. */
    fun load(origin: String? = null, destination: String? = null): Pair<Route, String> {
        val k = key()
        if (k != null && origin != null && destination != null) {
            runCatching { return Directions.parse(live(origin, destination, k)) to "live Directions API" }
        }
        return Directions.parse(recorded()) to "recorded synthetic response (no key)"
    }
}

/** A walker following the route: piecewise speed segments along the route (negative = walking back). */
data class NavWalk(val segments: List<Segment>, val gpsSigmaM: Double = 4.0, val accuracyM: Double = 5.0,
                   /** Noise on the reported (Doppler) ground speed, m/s [A]; null = fix carries no speed. */
                   val speedSigma: Double? = 0.2,
                   /** Times at which one fix jumps sideways by [jumpM] (urban-canyon multipath). */
                   val jumpsAt: List<Double> = emptyList(), val jumpM: Double = 25.0,
                   /** Interval in which the walker leaves the route sideways at 1 m/s. */
                   val offRoute: ClosedFloatingPointRange<Double>? = null,
                   val seed: Long = 1)

data class NavCue(val cue: Cue, val trueAlongM: Double, val trueSpeed: Double)

data class NavRun(val cues: List<NavCue>, val nav: Navigator, val trueAlong: List<Pair<Double, Double>>)

object NavSim {
    fun run(route: Route, w: NavWalk, cfg: NavConfig = NavConfig()): NavRun {
        val nav = Navigator(route, cfg)
        val rng = java.util.Random(w.seed)
        val out = mutableListOf<NavCue>()
        val truth = mutableListOf<Pair<Double, Double>>()
        var s = 0.0; var side = 0.0; var t = 0.0
        val dt = 0.05
        val end = w.segments.sumOf { it.durationS }
        var nextFix = 0.0
        while (t <= end) {
            var acc = 0.0; var v = 0.0
            for (seg in w.segments) { if (t < acc + seg.durationS) { v = seg.speedMS; break }; acc += seg.durationS }
            s = (s + v * dt).coerceIn(0.0, nav.lengthM)
            if (w.offRoute != null && t in w.offRoute) side += 1.0 * dt
            truth += t to s
            if (t + 1e-9 >= nextFix) {
                nextFix += 1.0
                val p = nav.pointAt(s)
                // Offset sideways from the route direction, plus GNSS noise and occasional jumps.
                val q = nav.pointAt((s + 1.0).coerceAtMost(nav.lengthM)); val r0 = nav.pointAt((s - 1.0).coerceAtLeast(0.0))
                val hx = q[0] - r0[0]; val hy = q[1] - r0[1]; val hn = Math.hypot(hx, hy).coerceAtLeast(1e-9)
                val nx = hy / hn; val ny = -hx / hn
                val jump = if (w.jumpsAt.any { kotlin.math.abs(it - t) < 0.5 }) w.jumpM else 0.0
                val x = p[0] + nx * (side + jump) + rng.nextGaussian() * w.gpsSigmaM
                val y = p[1] + ny * (side + jump) + rng.nextGaussian() * w.gpsSigmaM
                val accReported = if (jump > 0) w.accuracyM * 2 else w.accuracyM
                val rep = w.speedSigma?.let { kotlin.math.max(0.0, kotlin.math.abs(v) + rng.nextGaussian() * it) }
                for (c in nav.update(GpsFix(t, nav.fromEnu(x, y), accReported, rep))) out += NavCue(c, s, v)
            }
            t += dt
        }
        return NavRun(out, nav, truth)
    }
}
