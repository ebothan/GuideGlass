package org.guideglass.core.cloud

import org.guideglass.core.Cue
import org.guideglass.core.CueKind
import org.guideglass.core.Earcon
import org.guideglass.core.Priority
import org.guideglass.core.util.Json
import org.guideglass.core.util.JsonException
import kotlin.math.abs
import kotlin.math.hypot

enum class RequestKind { DESCRIBE, READ_TEXT, FIND, CAN_I_CROSS }

/** What the phone knew when it sent a frame to the cloud. Positions are local metres. */
data class CloudRequest(
    val frameId: String, val kind: RequestKind, val captureT: Double,
    val x: Double, val y: Double, val yawRad: Double, val stepIndex: Int,
    val lowResolution: Boolean = false,
)

/** An object the on-device perception currently sees; bearing relative to the head now, positive right. */
data class DeviceObject(val cls: String, val bearingRad: Double, val rangeM: Double, val ageS: Double)

/** What the phone knows at the moment the answer would be spoken. */
data class SpeakContext(
    val now: Double, val x: Double, val y: Double, val yawRad: Double, val stepIndex: Int,
    /** True if speed stayed below 0.2 m/s for the whole time since capture. */
    val stationary: Boolean,
    val deviceObjects: List<DeviceObject> = emptyList(),
    val obstacleInPath: Boolean = false,
    val freeSpaceAheadM: Double = 0.0,
)

data class GuardResult(val cues: List<Cue>, val codes: List<String>)

/** Risk classes from docs/research/07 D3 and ADR 0005. */
enum class Risk { R0_INFO, R1_ADVISORY, R2_ACTION, R3_SAFETY_NEGATING, R4_MALFORMED }

/**
 * Decides what, if anything, of a cloud answer may be spoken (REQ-25). Cloud answers are data:
 * nothing reaches the user without passing schema validation, staleness, the safety-negating
 * filter, injection checks and on-device corroboration for anything that tells the user to act.
 */
class CloudGuard(
    private val maxAgeS: Double = 4.0,
    private val maxAgeStationaryS: Double = 8.0,
    private val maxMoveM: Double = 3.0,
    private val maxYawActionRad: Double = Math.toRadians(30.0),
    private val maxYawInfoRad: Double = Math.toRadians(90.0),
    private val corroborationAgeS: Double = 0.3,
    private val corroborationBearingRad: Double = Math.toRadians(25.0),
    private val minFreeSpaceM: Double = 2.5,
) {
    private val outstanding = HashMap<String, CloudRequest>()
    private val recentActions = ArrayDeque<Triple<Double, RequestKind, String>>()
    private var failures = 0
    var disabled = false; private set

    fun register(req: CloudRequest) { outstanding[req.frameId] = req }

    /** No answer by the deadline (including a stream cut off mid-answer). */
    fun onTimeout(frameId: String, ctx: SpeakContext): GuardResult {
        val req = outstanding.remove(frameId) ?: return GuardResult(emptyList(), listOf("unknown_frame"))
        return fail(ctx, "timeout", req)
    }

    fun onAnswer(raw: String, ctx: SpeakContext): GuardResult {
        if (disabled) return GuardResult(emptyList(), listOf("disabled"))
        val parsed = try { parse(raw) } catch (e: JsonException) { null } catch (e: IllegalArgumentException) { null }
        if (parsed == null) {
            // A malformed answer cannot be matched safely to its request; fail the oldest outstanding one.
            val req = outstanding.values.minByOrNull { it.captureT }?.also { outstanding.remove(it.frameId) }
            return fail(ctx, "r4_malformed", req)
        }
        val req = outstanding.remove(parsed.frameId) ?: return GuardResult(emptyList(), listOf("unknown_frame"))
        failures = 0
        val codes = mutableListOf<String>()

        // Staleness (rule S).
        val age = ctx.now - req.captureT
        val moved = hypot(ctx.x - req.x, ctx.y - req.y)
        val yaw = abs(wrap(ctx.yawRad - req.yawRad))
        val stepChanged = ctx.stepIndex != req.stepIndex
        val infoStale = moved > maxMoveM || stepChanged || yaw > maxYawInfoRad ||
            age > (if (ctx.stationary) maxAgeStationaryS else maxAgeS)
        val actionStale = infoStale || yaw > maxYawActionRad || age > maxAgeS
        if (infoStale) return GuardResult(listOf(say("Sorry, that took too long. Ask again.", req, ctx)), listOf("stale"))

        if (!parsed.answerable) return GuardResult(listOf(say("I can't tell from here.", req, ctx)), listOf("abstained"))

        // Language and markup (T19).
        for (it in parsed.items) if (!latin(it.text)) return GuardResult(emptyList(), listOf("r4_language"))

        // Safety-negating content anywhere drops the whole answer (T1, T2).
        if (parsed.items.any { R3.containsMatchIn(clean(it.text)) }) return GuardResult(emptyList(), listOf("r3_blocked"))

        val sceneTokens = parsed.items.flatMap { tokens(it.sceneText ?: "") + if (it.kind == "scene_text") tokens(it.text) else emptySet() }.toSet()
        val actions = parsed.items.map { it.action }.filter { it != "none" }.toSet()
        recentActions.removeAll { ctx.now - it.first > 10.0 }
        val conflict = actions.size > 1 ||
            actions.any { a -> recentActions.any { it.second == req.kind && it.third != a } }
        actions.forEach { recentActions.addLast(Triple(ctx.now, req.kind, it)) }
        if (conflict) codes += "conflict"

        val lowConf = req.lowResolution
        val parts = mutableListOf<String>()
        for (it in parsed.items) {
            val text = stripDistances(clean(it.text))
            val risk = classify(it)
            when {
                req.kind == RequestKind.CAN_I_CROSS -> {}  // handled below; never an action
                it.kind == "scene_text" -> parts += if (req.kind == RequestKind.READ_TEXT && it.sceneText != null)
                    "A sign reads: \"${clean(it.sceneText).take(120)}\"." else "A sign has text on it."
                risk == Risk.R0_INFO -> parts += sentence(text)
                risk == Risk.R1_ADVISORY -> {
                    if (actionStale) { codes += "r1_stale"; continue }
                    val unsure = lowConf || it.confidence == "low"
                    parts += (if (unsure) "Not sure, but the camera suggests " else "The camera suggests ") + lowerFirst(sentence(text))
                }
                risk == Risk.R2_ACTION -> {
                    if (actionStale) { codes += "r2_stale"; continue }
                    if (conflict) continue
                    if (tokens(it.text).intersect(sceneTokens).size >= 2) { codes += "r4_injection"; continue }
                    corroborated(it, req, ctx)?.let { s -> parts += s; codes += "r2_corroborated" } ?: run {
                        codes += "r2_uncorroborated"
                        downgrade(it, req, ctx)?.let { s -> parts += s }
                    }
                }
                else -> codes += "dropped_${risk.name.lowercase()}"
            }
        }
        if (req.kind == RequestKind.CAN_I_CROSS) {
            val walkShown = parsed.items.any { (it.sceneText ?: "").contains("walk", ignoreCase = true) }
            val light = ctx.deviceObjects.any { it.cls == "traffic_light" && it.ageS <= corroborationAgeS }
            parts.clear()
            parts += if (walkShown && light) "The signal appears to show WALK. Check traffic before crossing." else "I can't tell reliably."
            codes += "crossing_never_instructed"
        }
        val text = parts.take(2).joinToString(" ")
        if (text.isBlank()) return GuardResult(emptyList(), codes + "nothing_to_say")
        check(!BANNED.containsMatchIn(text)) { "guard produced banned phrase: $text" }
        return GuardResult(listOf(say(text, req, ctx)), codes)
    }

    private fun fail(ctx: SpeakContext, code: String, req: CloudRequest?): GuardResult {
        failures++
        if (failures >= 3) {
            disabled = true
            return GuardResult(listOf(Cue(CueKind.CLOUD, Priority.P3, "Cloud answers unavailable. Hazard alerts still on.",
                Earcon.NONE, "cloud-disabled", ctx.now, 5.0)), listOf(code, "disabled"))
        }
        val cues = if (req != null) listOf(say("Couldn't get a reliable answer.", req, ctx)) else emptyList()
        return GuardResult(cues, listOf(code))
    }

    private fun corroborated(it: Item, req: CloudRequest, ctx: SpeakContext): String? = when (it.action) {
        "forward" -> if (ctx.freeSpaceAheadM >= minFreeSpaceM && !ctx.obstacleInPath) "The camera suggests continuing ahead." else null
        "turn_left", "turn_right", "enter" -> it.objectClass?.let { cls ->
            val b = regionBearing(it.region) - wrap(ctx.yawRad - req.yawRad)
            ctx.deviceObjects.firstOrNull { o -> o.cls == cls && o.ageS <= corroborationAgeS && abs(wrap(o.bearingRad - b)) <= corroborationBearingRad }
                ?.let { o -> "${cap(cls)} ${direction(o.bearingRad)}, about ${Math.round(o.rangeM)} meters." }
        }
        else -> null   // step_up, step_down and stop are never corroborated: no on-device detector for them
    }

    private fun downgrade(it: Item, req: CloudRequest, ctx: SpeakContext): String? = when (it.action) {
        "step_down" -> "The camera thinks there may be a step down; check with your cane."
        "step_up" -> "The camera thinks there may be a step up; check with your cane."
        "turn_left", "turn_right", "enter" -> it.objectClass?.let { cls ->
            val b = regionBearing(it.region) - wrap(ctx.yawRad - req.yawRad)
            "The camera may see a ${cls.replace('_', ' ')} ${direction(b)}; not confirmed."
        }
        else -> null   // "forward" without corroboration, or "stop": say nothing
    }

    private fun say(text: String, req: CloudRequest, ctx: SpeakContext) =
        Cue(CueKind.CLOUD, Priority.P3, text, Earcon.NONE, "cloud-${req.frameId}", ctx.now, 3.0)

    // ------------------------------------------------------------------ schema
    data class Item(
        val kind: String, val text: String, val confidence: String, val sceneText: String?,
        val objectClass: String?, val region: String?, val action: String,
    )
    data class Answer(val frameId: String, val answerable: Boolean, val items: List<Item>)

    fun classify(it: Item): Risk {
        val t = clean(it.text)
        return when {
            R3.containsMatchIn(t) -> Risk.R3_SAFETY_NEGATING
            it.action != "none" || it.kind == "action_suggestion" || IMPERATIVE.containsMatchIn(t) -> Risk.R2_ACTION
            it.kind == "advisory" -> Risk.R1_ADVISORY
            else -> Risk.R0_INFO
        }
    }

    companion object {
        private val TOP = setOf("schema_version", "frame_id", "answerable", "abstain_reason", "items")
        private val ITEM = setOf("kind", "text", "scene_text_verbatim", "object_class", "image_region", "action", "confidence")
        private val KINDS = setOf("description", "scene_text", "landmark", "advisory", "action_suggestion")
        private val CLASSES = setOf("door", "entrance", "stairs", "curb", "crosswalk", "traffic_light", "bus_stop", "sign",
            "person", "vehicle", "bicycle", "pole", "bench", "other")
        private val REGIONS = setOf("left", "center_left", "center", "center_right", "right", "unknown")
        private val ACTIONS = setOf("none", "forward", "turn_left", "turn_right", "stop", "enter", "step_up", "step_down")
        private val CONF = setOf("low", "medium", "high")
        private val ABSTAIN = setOf("too_dark", "blurred", "occluded", "not_visible", "out_of_scope", "other")

        /** Safety-negating claims (R3): never spoken. */
        val R3 = Regex("""\b(clear|safe(ly)?|unobstructed|no obstacles?|nothing (in front|ahead|in the way)|go now|you can (go|cross|walk)|cross(ing)? now|walk now|(car|vehicle|traffic) (has )?stopped)\b""", RegexOption.IGNORE_CASE)
        /** Phrases no cue may ever contain (REQ-18); applied to every guard output. */
        val BANNED = Regex("""\b(clear|safe|walk now|cross now|go now)\b""", RegexOption.IGNORE_CASE)
        private val IMPERATIVE = Regex("""^\s*(walk|go|turn|cross|step|move|head|continue|keep|stop|enter|proceed|bear|veer)\b""", RegexOption.IGNORE_CASE)
        private val DISTANCE = Regex("""\b(about |around |approximately |roughly )?(\d+(\.\d+)?|one|two|three|four|five|six|seven|eight|nine|ten|a few) ?(m|meters?|metres?|feet|foot|ft|yards?|steps?)( away)?\b""", RegexOption.IGNORE_CASE)
        private val STOPWORDS = setOf("the", "and", "for", "you", "your", "user", "into", "with", "this", "that", "are", "now", "to")

        fun parse(raw: String): Answer {
            val o = Json.obj(Json.parse(raw))
            require(o.keys.all { it in TOP }) { "extra top-level field" }
            require(o["schema_version"] == "gg-cloud-1") { "schema_version" }
            val fid = o["frame_id"] as? String ?: throw IllegalArgumentException("frame_id")
            val ans = o["answerable"] as? Boolean ?: throw IllegalArgumentException("answerable")
            (o["abstain_reason"])?.let { require(it is String && it in ABSTAIN) { "abstain_reason" } }
            val items = Json.arr(o["items"] ?: throw IllegalArgumentException("items"))
            require(items.size <= 4) { "too many items" }
            return Answer(fid, ans, items.map { iv ->
                val m = Json.obj(iv)
                require(m.keys.all { it in ITEM }) { "extra item field" }
                fun str(k: String, allowed: Set<String>? = null, max: Int = 200): String? {
                    val v = m[k] ?: return null
                    require(v is String && v.length <= max) { k }
                    if (allowed != null) require(v in allowed) { k }
                    return v
                }
                Item(
                    kind = str("kind", KINDS) ?: throw IllegalArgumentException("kind"),
                    text = str("text", max = 140) ?: throw IllegalArgumentException("text"),
                    confidence = str("confidence", CONF) ?: throw IllegalArgumentException("confidence"),
                    sceneText = str("scene_text_verbatim"),
                    objectClass = str("object_class", CLASSES),
                    region = str("image_region", REGIONS),
                    action = str("action", ACTIONS) ?: "none",
                )
            })
        }

        fun clean(s: String) = s.replace(Regex("<[^>]*>"), " ").replace(Regex("[^\\p{L}\\p{N}\\p{P}\\p{Zs}]"), " ")
            .replace(Regex("\\s+"), " ").trim()

        fun stripDistances(s: String) = s.replace(DISTANCE, "").replace(Regex("\\s+([,.])"), "$1").replace(Regex("\\s+"), " ").trim()

        private fun latin(s: String): Boolean {
            val letters = s.filter { it.isLetter() }
            return letters.isEmpty() || letters.count { it.code < 0x250 }.toDouble() / letters.length >= 0.9
        }

        private fun tokens(s: String) = s.lowercase().split(Regex("[^a-z]+")).filter { it.length >= 3 && it !in STOPWORDS }.toSet()

        private fun sentence(s: String) = s.trim().let { if (it.isEmpty() || it.last() in ".!?") it else "$it." }
        private fun lowerFirst(s: String) = s.replaceFirstChar { it.lowercase() }
        private fun cap(s: String) = s.replace('_', ' ').replaceFirstChar { it.uppercase() }

        fun regionBearing(r: String?): Double = Math.toRadians(when (r) {
            "left" -> -18.0; "center_left" -> -9.0; "center_right" -> 9.0; "right" -> 18.0; else -> 0.0
        })

        fun direction(b: Double): String {
            val d = Math.toDegrees(b)
            return when {
                abs(d) < 10 -> "ahead"
                abs(d) < 30 -> if (d > 0) "ahead slightly right" else "ahead slightly left"
                else -> if (d > 0) "to your right" else "to your left"
            }
        }

        fun wrap(a: Double): Double { var x = a; while (x > Math.PI) x -= 2 * Math.PI; while (x < -Math.PI) x += 2 * Math.PI; return x }
    }
}
