package org.guideglass.core.sim

import org.guideglass.core.AlertLevel
import org.guideglass.core.Box
import org.guideglass.core.CoreConfig
import org.guideglass.core.FrameInput
import org.guideglass.core.HazardAssessor
import org.guideglass.core.StageStamps
import org.guideglass.core.Tracker
import org.guideglass.core.perception.Camera
import org.guideglass.core.perception.DepthSample
import org.guideglass.core.perception.PitchEstimate
import org.guideglass.core.perception.Ranger
import java.io.File
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/*
 * Gate G6: score this design on the exact scene set of the repo's five-rule comparison
 * (paper/simulation/sim_baselines.py, exported by export_scenes.py), against R5's
 * 2.00% false alarms and 47.39% misses. Evidence rung L1 (synthetic scenes, idealised detector).
 *
 * The repo's scenes give a box per frame. This design also needs a range estimate, which the repo's
 * rules do not use. Range comes from the same two sources as in the app (ADR 0004):
 *  - ground-contact geometry from the box bottom edge, assuming camera height 1.30 m and a pitch
 *    estimate equal to the true pitch plus a per-scene error (sweep: 0, 2, 5 degrees);
 *  - a depth model whose output is true distance times the repo's own per-scene scale error
 *    (log-normal, sigma 0.30, the value the repo uses for its depth rule R4), plus 5% frame noise.
 * Both assumptions are swept, because the result depends on them.
 */

data class SceneRow(
    val k: Int, val tMs: Double, val hf: Double, val wf: Double, val cx: Double, val bottom: Double,
    val d: Double, val camH: Double, val pitch: Double, val scaleErr: Double, val x: Double,
)

class SceneSet(val clear: Map<Int, Map<String, List<SceneRow>>>, val hazard: Map<Int, List<SceneRow>>) {
    companion object {
        fun load(path: String): SceneSet {
            val clear = HashMap<Int, HashMap<String, MutableList<SceneRow>>>()
            val haz = HashMap<Int, MutableList<SceneRow>>()
            File(path).bufferedReader().useLines { lines ->
                lines.drop(1).forEach { ln ->
                    val c = ln.split(',')
                    val r = SceneRow(c[3].toInt(), c[4].toDouble(), c[5].toDouble(), c[6].toDouble(), c[7].toDouble(),
                        c[8].toDouble(), c[9].toDouble(), c[10].toDouble(), c[11].toDouble(), c[12].toDouble(), c[13].toDouble())
                    val idx = c[1].toInt()
                    if (c[0] == "clear") clear.getOrPut(idx) { HashMap() }.getOrPut(c[2]) { mutableListOf() } += r
                    else haz.getOrPut(idx) { mutableListOf() } += r
                }
            }
            return SceneSet(clear, haz)
        }
    }
}

enum class RangeSource { FUSED, GROUND_ONLY, DEPTH_ONLY }

data class EvalSetting(
    val name: String,
    val cfg: CoreConfig,
    val depthScaleSigma: Double = 0.30,
    val pitchSigmaDeg: Double = 2.0,
    val source: RangeSource = RangeSource.FUSED,
    /** Alert threshold: STOP only, or CAUTION and above. */
    val minLevel: AlertLevel = AlertLevel.STOP,
)

data class EvalResult(
    val setting: EvalSetting,
    val nClear: Int, val nHaz: Int,
    val fa: Int, val miss: Int, val flip: Int,
    val missByBand: Map<String, Pair<Int, Int>>,
    /** False alarms whose object is in the walking path but 2.5-3.6 m away: early, not spurious. */
    val faEarlyInPath: Int,
) {
    val faPct get() = 100.0 * fa / nClear
    val missPct get() = 100.0 * miss / nHaz
    val flipPct get() = 100.0 * flip / nClear
}

object BaselineEvaluator {
    private val cam = Camera(Math.toRadians(60.0), Math.toRadians(46.0), 1.30)
    private val ranger = Ranger(cam)

    /** True if the design alerts at the last frame of this 0.9 s window. */
    fun decide(rows: List<SceneRow>, s: EvalSetting, sceneSeed: Long): Boolean {
        val rng = java.util.Random(sceneSeed)
        val pitchErr = rng.nextGaussian() * Math.toRadians(s.pitchSigmaDeg)
        val pitchSigma = Math.toRadians(max(0.5, s.pitchSigmaDeg))
        val tracker = Tracker(s.cfg)
        val assessor = HazardAssessor(s.cfg)
        val delay = s.cfg.nominalPipelineS - s.cfg.earconStartS - s.cfg.audioOutLatencyS
        var level = AlertLevel.NONE
        for (r in rows) {
            val t = r.tMs / 1000.0
            val box = Box(r.cx, r.bottom, r.wf, r.hf)
            // Rescale the repo's scale error to the swept sigma: same draw, different spread.
            val scale = exp(ln(r.scaleErr) * s.depthScaleSigma / 0.30)
            val depthR = r.d * scale * (1 + 0.05 * rng.nextGaussian())
            val depth = DepthSample(depthR, max(0.1, depthR * 0.30))
            val pitch = PitchEstimate(r.pitch + pitchErr, pitchSigma)
            val det = when (s.source) {
                RangeSource.FUSED -> ranger.detect("object", box, pitch, depth)
                RangeSource.GROUND_ONLY -> ranger.detect("object", box, pitch, null)
                RangeSource.DEPTH_ONLY -> ranger.detect("object", box, pitch, depth, headHeight = true)
            }
            val f = FrameInput(r.k.toLong(), StageStamps(t, t, t), listOfNotNull(det), r.k.toLong())
            tracker.update(f, 1.4)
            val now = t + delay
            assessor.decide(tracker.alertable(now), t, now, 1.4, 0.0, motionChannel = true)
            level = assessor.lastAssessments.maxOfOrNull { it.level } ?: AlertLevel.NONE
        }
        return level >= s.minLevel
    }

    fun evaluate(set: SceneSet, s: EvalSetting): EvalResult {
        var fa = 0; var miss = 0; var flip = 0; var early = 0
        val bands = linkedMapOf("0.8-1.5 m" to intArrayOf(0, 0), "1.5-2.0 m" to intArrayOf(0, 0), "2.0-2.5 m" to intArrayOf(0, 0))
        for ((i, v) in set.clear) {
            val seed = 1_000_003L * i + 17
            val base = v.getValue("base")
            if (decide(base, s, seed)) {
                fa++
                val last = base.last()
                if (kotlin.math.abs(last.x) <= 0.45 && last.d <= 3.6) early++
            }
            if (decide(v.getValue("lo"), s, seed) != decide(v.getValue("hi"), s, seed)) flip++
        }
        for ((i, rows) in set.hazard) {
            val alerted = decide(rows, s, 2_000_003L * i + 29)
            val d = rows.last().d
            val key = when { d < 1.5 -> "0.8-1.5 m"; d < 2.0 -> "1.5-2.0 m"; else -> "2.0-2.5 m" }
            bands.getValue(key)[1]++
            if (!alerted) { miss++; bands.getValue(key)[0]++ }
        }
        return EvalResult(s, set.clear.size, set.hazard.size, fa, miss, flip, bands.mapValues { it.value[0] to it.value[1] }, early)
    }

    /** Wilson score interval for k of n, in percent. */
    fun wilson(k: Int, n: Int, z: Double = 1.96): Pair<Double, Double> {
        if (n == 0) return Double.NaN to Double.NaN
        val p = k.toDouble() / n
        val den = 1 + z * z / n
        val c = (p + z * z / (2 * n)) / den
        val h = z * sqrt(p * (1 - p) / n + z * z / (4.0 * n * n)) / den
        return 100 * (c - h) to 100 * (c + h)
    }

    /** Settings: the ground-truth-matched comparison, the deployed safety setting, and the sweeps. */
    fun settings(): List<EvalSetting> {
        val matched = CoreConfig(stopLeadS = 2.5 / 1.4, pessimismK = 0.0, corridorIncludesObjectWidth = false)
        val deployed = CoreConfig()
        val out = mutableListOf(
            EvalSetting("matched", matched),
            EvalSetting("deployed STOP", deployed),
            EvalSetting("deployed STOP, k=0.5", deployed.copy(pessimismK = 0.5)),
            EvalSetting("deployed STOP, k=0", deployed.copy(pessimismK = 0.0)),
            EvalSetting("deployed CAUTION+", deployed, minLevel = AlertLevel.CAUTION),
            EvalSetting("matched, ground only", matched, source = RangeSource.GROUND_ONLY),
            EvalSetting("matched, depth only", matched, source = RangeSource.DEPTH_ONLY),
        )
        for (ds in listOf(0.10, 0.30, 0.50)) for (ps in listOf(0.0, 2.0, 5.0)) {
            if (ds == 0.30 && ps == 2.0) continue
            out += EvalSetting("matched, depth sd %.2f, pitch sd %.0f deg".format(ds, ps), matched, ds, ps)
        }
        return out
    }
}

/** Entry point: ./gradlew -p core runBaselineEval -Pargs="core/build/eval/scenes.csv docs/results" */
fun main(args: Array<String>) {
    val csv = args.getOrElse(0) { "core/build/eval/scenes.csv" }
    val outDir = File(args.getOrElse(1) { "docs/results" }).apply { mkdirs() }
    val set = SceneSet.load(csv)
    val results = BaselineEvaluator.settings().map { BaselineEvaluator.evaluate(set, it) }
    val md = StringBuilder()
    md.appendLine("# G6: hazard decisions on the repo's shared scene set")
    md.appendLine()
    md.appendLine("Generated by `core/src/main/kotlin/org/guideglass/core/sim/BaselineEval.kt` from the scenes exported by")
    md.appendLine("`paper/simulation/export_scenes.py` (seed 53, ${set.clear.size} hazard-free and ${set.hazard.size} hazard scenes; the export")
    md.appendLine("re-scores R5 at 2.00% / 47.39% as a check). Evidence rung **L1**: synthetic scenes, idealised detector, assumed range errors.")
    md.appendLine()
    md.appendLine("| Setting | False alarm % [95% CI] | Miss % [95% CI] | Pitch flip % | Miss by distance (missed/total) |")
    md.appendLine("|---|---|---|---|---|")
    md.appendLine("| R5 (repo baseline) | 2.00 | 47.39 | 0.0 | (not broken down in the repo) |")
    for (r in results) {
        val (fl, fh) = BaselineEvaluator.wilson(r.fa, r.nClear)
        val (ml, mh) = BaselineEvaluator.wilson(r.miss, r.nHaz)
        val bands = r.missByBand.entries.joinToString("; ") { "${it.key}: ${it.value.first}/${it.value.second}" }
        md.appendLine("| ${r.setting.name} | %.2f [%.2f, %.2f] | %.2f [%.2f, %.2f] | %.2f | %s |".format(
            r.faPct, fl, fh, r.missPct, ml, mh, r.flipPct, bands))
    }
    val dep = results.first { it.setting.name == "deployed STOP" }
    md.appendLine()
    md.appendLine("In the deployed setting, ${dep.faEarlyInPath} of ${dep.fa} false alarms are objects in the walking path between 2.5 and 3.6 m:")
    md.appendLine("early warnings by the harness's 2.5 m definition, not spurious alerts.")
    File(outDir, "g6_baseline_comparison.md").writeText(md.toString())
    val json = results.joinToString(",\n", "[\n", "\n]") { r ->
        """  {"setting": "${r.setting.name}", "fa_pct": %.4f, "miss_pct": %.4f, "flip_pct": %.4f, "fa": ${r.fa}, "miss": ${r.miss}, "n_clear": ${r.nClear}, "n_hazard": ${r.nHaz}, "fa_early_in_path": ${r.faEarlyInPath}}""".format(r.faPct, r.missPct, r.flipPct)
    }
    File(outDir, "g6_baseline_comparison.json").writeText(json + "\n")
    print(md)
}
