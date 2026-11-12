package org.guideglass.core.nav

import org.guideglass.core.Cue
import org.guideglass.core.CueKind
import org.guideglass.core.Earcon
import org.guideglass.core.Priority
import org.guideglass.core.util.Json
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

data class LatLng(val lat: Double, val lng: Double)

/** One route step. [maneuver] is the action at the step's START (Google Directions semantics). */
data class RouteStep(val maneuver: String?, val instruction: String, val start: LatLng, val end: LatLng, val points: List<LatLng>)

data class Route(val steps: List<RouteStep>)

data class GpsFix(val t: Double, val pos: LatLng, val accuracyM: Double, val speedMS: Double? = null)

/** Route geometry parsing: Directions API JSON and Google's encoded polyline format. */
object Directions {
    fun parse(json: String): Route {
        val root = Json.obj(Json.parse(json))
        val status = root["status"] as? String
        require(status == "OK") { "Directions status $status" }
        val leg = Json.obj(Json.arr(Json.obj(Json.arr(root["routes"]).first())["legs"]).first())
        val steps = Json.arr(leg["steps"]).map { sv ->
            val s = Json.obj(sv)
            fun ll(k: String) = Json.obj(s[k]).let { LatLng(it["lat"] as Double, it["lng"] as Double) }
            RouteStep(
                maneuver = s["maneuver"] as? String,
                instruction = (s["html_instructions"] as? String ?: "").replace(Regex("<[^>]*>"), ""),
                start = ll("start_location"), end = ll("end_location"),
                points = decodePolyline(Json.obj(s["polyline"])["points"] as String),
            )
        }
        return Route(steps)
    }

    fun decodePolyline(enc: String): List<LatLng> {
        val out = mutableListOf<LatLng>()
        var i = 0; var lat = 0; var lng = 0
        while (i < enc.length) {
            for (k in 0..1) {
                var shift = 0; var result = 0
                while (true) {
                    val b = enc[i++].code - 63
                    result = result or ((b and 0x1f) shl shift); shift += 5
                    if (b < 0x20) break
                }
                val d = if (result and 1 != 0) (result shr 1).inv() else result shr 1
                if (k == 0) lat += d else lng += d
            }
            out += LatLng(lat / 1e5, lng / 1e5)
        }
        return out
    }

    fun encodePolyline(pts: List<LatLng>): String {
        val sb = StringBuilder(); var pLat = 0; var pLng = 0
        fun enc(v: Int) { var x = if (v < 0) (v shl 1).inv() else v shl 1
            while (x >= 0x20) { sb.append(((0x20 or (x and 0x1f)) + 63).toChar()); x = x shr 5 }
            sb.append((x + 63).toChar()) }
        for (p in pts) {
            val la = Math.round(p.lat * 1e5).toInt(); val ln = Math.round(p.lng * 1e5).toInt()
            enc(la - pLat); enc(ln - pLng); pLat = la; pLng = ln
        }
        return sb.toString()
    }
}

data class NavConfig(
    /** Latency from decision to speech at the ear, docs/00 section 7 (L_speech). */
    val speechLatencyS: Double = 1.0,
    /** Time a walker needs to act on a turn instruction [A]. */
    val reactS: Double = 1.0,
    /** Pre-announcement comes this much earlier than "turn now" [A]. */
    val preExtraS: Double = 8.0,
    val standingSpeedMS: Double = 0.2,
    val offRouteM: Double = 15.0,
    val confirmS: Double = 5.0,
    val wrongWaySpeedMS: Double = 0.3,
)

/**
 * Follows a route on GPS fixes and produces turn cues timed by predicted time to the manoeuvre
 * (docs/00 section 8.4, REQ-20 to 23): fire "now" when distance / speed <= speech latency +
 * utterance duration + reaction time. Never fires while the walker is standing.
 */
class Navigator(val route: Route, private val cfg: NavConfig = NavConfig()) {
    private val origin = route.steps.first().start
    private val cosLat = cos(Math.toRadians(origin.lat))
    private val poly: List<DoubleArray>
    private val cum: DoubleArray
    /** Along-route position of each manoeuvre: start of steps 1..n-1, and the end of the route. */
    private val events: List<Pair<Double, RouteStep?>>
    private var seg = -1
    private val history = ArrayDeque<Pair<Double, Double>>()  // (t, s)
    private val fired = HashSet<String>()
    private var offSince = Double.NaN
    private var wrongSince = Double.NaN
    private var offAnnounced = false
    private var wrongAnnounced = false
    private var sFilt = Double.NaN
    private var vFilt = 0.0
    private var lastFixT = Double.NaN

    var alongM = 0.0; private set
    var crossTrackM = 0.0; private set
    var speedMS = 0.0; private set
    var stepIndex = 0; private set
    val lengthM get() = cum.last()

    init {
        val pts = mutableListOf<LatLng>()
        route.steps.forEach { st -> st.points.forEach { if (pts.isEmpty() || pts.last() != it) pts += it } }
        poly = pts.map { enu(it) }
        cum = DoubleArray(poly.size)
        for (k in 1 until poly.size) cum[k] = cum[k - 1] + hypot(poly[k][0] - poly[k - 1][0], poly[k][1] - poly[k - 1][1])
        var from = 0.0
        val ev = mutableListOf<Pair<Double, RouteStep?>>()
        route.steps.drop(1).forEach { st -> val s = project(enu(st.start), 0, poly.size - 2).first; from = max(from, s); ev += from to st }
        ev += cum.last() to null
        events = ev
    }

    fun enu(p: LatLng) = doubleArrayOf(
        Math.toRadians(p.lng - origin.lng) * EARTH_R * cosLat,
        Math.toRadians(p.lat - origin.lat) * EARTH_R,
    )

    fun fromEnu(x: Double, y: Double) = LatLng(origin.lat + Math.toDegrees(y / EARTH_R), origin.lng + Math.toDegrees(x / (EARTH_R * cosLat)))

    /** Point on the route [s] metres from the start. */
    fun pointAt(s: Double): DoubleArray {
        val k = cum.indexOfFirst { it >= s }.let { if (it <= 0) 1 else it }
        val f = ((s - cum[k - 1]) / (cum[k] - cum[k - 1]).coerceAtLeast(1e-9)).coerceIn(0.0, 1.0)
        return doubleArrayOf(poly[k - 1][0] + f * (poly[k][0] - poly[k - 1][0]), poly[k - 1][1] + f * (poly[k][1] - poly[k - 1][1]))
    }

    /** Closest point among segments [a, b]: returns (along-route s, cross-track distance, segment). */
    private fun project(p: DoubleArray, a: Int, b: Int): Triple<Double, Double, Int> {
        var best = Triple(0.0, Double.POSITIVE_INFINITY, a)
        for (k in a.coerceAtLeast(0)..b.coerceAtMost(poly.size - 2)) {
            val ax = poly[k][0]; val ay = poly[k][1]; val bx = poly[k + 1][0]; val by = poly[k + 1][1]
            val dx = bx - ax; val dy = by - ay
            val l2 = dx * dx + dy * dy
            val f = if (l2 < 1e-9) 0.0 else (((p[0] - ax) * dx + (p[1] - ay) * dy) / l2).coerceIn(0.0, 1.0)
            val d = hypot(p[0] - (ax + f * dx), p[1] - (ay + f * dy))
            if (d < best.second) best = Triple(cum[k] + f * (cum[k + 1] - cum[k]), d, k)
        }
        return best
    }

    fun update(fix: GpsFix): List<Cue> {
        val p = enu(fix.pos)
        // Search near the last position first so a sideways GPS jump cannot skip ahead along the route.
        val (s, ct, k) = if (seg < 0) project(p, 0, poly.size - 2) else project(p, seg - 3, seg + 6)
        seg = k; crossTrackM = ct
        // Smooth the along-route position with a predict-and-correct filter. Speed comes from the fix
        // (phones report Doppler speed) when available: differencing 4 m-noise positions gives about
        // +/-2 m/s of speed noise, which fired turn cues up to 20 s early in the first test run.
        val dtFix = if (lastFixT.isNaN()) 0.0 else (fix.t - lastFixT).coerceAtLeast(0.0)
        lastFixT = fix.t
        if (sFilt.isNaN()) sFilt = s else {
            val pred = sFilt + vFilt * dtFix
            val r = s - pred
            sFilt = pred + ALPHA * r
            if (dtFix > 0) vFilt += BETA * r / dtFix
        }
        fix.speedMS?.let { vFilt = if (vFilt >= 0) it else -it }
        alongM = sFilt
        history.addLast(fix.t to sFilt)
        while (history.size > 2 && fix.t - history.first().first > 3.0) history.removeFirst()
        val ds = if (history.size >= 2) (history.last().second - history.first().second) / (history.last().first - history.first().first) else 0.0
        speedMS = fix.speedMS ?: max(0.0, vFilt)
        val sNow = sFilt
        stepIndex = events.indexOfFirst { it.first > sFilt + 0.5 }.let { if (it < 0) events.size else it }

        val out = mutableListOf<Cue>()
        val t = fix.t
        // Off route: cross-track error beyond what GPS accuracy explains, for confirmS seconds.
        if (ct - fix.accuracyM > cfg.offRouteM) { if (offSince.isNaN()) offSince = t } else { offSince = Double.NaN; offAnnounced = false }
        if (!offSince.isNaN() && t - offSince >= cfg.confirmS && !offAnnounced) {
            offAnnounced = true
            out += Cue(CueKind.OFF_ROUTE, Priority.P2, "You may be off route.", Earcon.NONE, "nav-off", t, 3.0)
        }
        if (history.size >= 2 && ds < -cfg.wrongWaySpeedMS) { if (wrongSince.isNaN()) wrongSince = t } else { wrongSince = Double.NaN; wrongAnnounced = false }
        if (!wrongSince.isNaN() && t - wrongSince >= cfg.confirmS && !wrongAnnounced) {
            wrongAnnounced = true
            out += Cue(CueKind.WRONG_WAY, Priority.P2, "You may be heading the wrong way.", Earcon.NONE, "nav-wrong", t, 3.0)
        }
        if (!offSince.isNaN()) return out
        if (speedMS < cfg.standingSpeedMS) return out

        val idx = stepIndex
        if (idx >= events.size) return out
        val (sEv, step) = events[idx]
        val dist = sEv - sNow
        // Pessimistic distance: subtract the fix's accuracy radius, as hazards use range minus one sigma.
        // GNSS error near a corner is about the whole 3-4 m lead distance (docs/00 section 8.4).
        val tTo = max(0.0, dist - fix.accuracyM) / speedMS
        val hedge = fix.accuracyM > dist
        val crossing = step?.instruction?.contains("cross", ignoreCase = true) == true
        val nowCue = when {
            step == null -> Cue(CueKind.ARRIVAL, Priority.P2, "Arriving.", Earcon.ARRIVE, "nav-now-$idx", t, 1.5)
            crossing -> null
            else -> maneuverWords(step.maneuver)?.let { w ->
                // Never "now": GNSS error near a corner is as large as the lead distance (review finding 10). The cue
                // names the next corner and leaves finding it to the user's cane or dog.
                Cue(CueKind.TURN_NOW, Priority.P2, if (hedge) "$w soon." else "$w at the next corner.", Earcon.TURN, "nav-now-$idx", t, 1.5)
            }
        }
        val leadNow = cfg.speechLatencyS + (nowCue?.durationS ?: 0.9) + cfg.reactS
        if (nowCue != null && tTo <= leadNow && fired.add("now-$idx")) { out += nowCue; fired += "pre-$idx" }
        else if (tTo <= leadNow + cfg.preExtraS && tTo > leadNow && fired.add("pre-$idx")) {
            val pre = when {
                step == null -> "Destination ahead."
                crossing -> "Crossing ahead."
                else -> maneuverWords(step.maneuver)?.let { "In ${roundDist(dist)} meters, ${it.lowercase()}." }
            }
            if (pre != null) {
                val kind = if (crossing) CueKind.CROSSING_AHEAD else CueKind.TURN_PRE
                out += Cue(kind, Priority.P3, pre, Earcon.NONE, "nav-pre-$idx", t, 3.0)
            }
        }
        return out
    }

    companion object {
        const val EARTH_R = 6_371_000.0
        private const val ALPHA = 0.3
        private const val BETA = 0.05

        fun maneuverWords(m: String?): String? = when (m) {
            "turn-left" -> "Turn left"; "turn-right" -> "Turn right"
            "turn-slight-left", "fork-left", "keep-left", "ramp-left" -> "Bear left"
            "turn-slight-right", "fork-right", "keep-right", "ramp-right" -> "Bear right"
            "turn-sharp-left", "uturn-left" -> "Sharp left"
            "turn-sharp-right", "uturn-right" -> "Sharp right"
            else -> null
        }

        fun roundDist(d: Double): Int = when {
            d < 20 -> (Math.round(d / 5) * 5).toInt().coerceAtLeast(5)
            else -> (Math.round(d / 10) * 10).toInt()
        }
    }
}
