"""
Export the exact scene set behind the repo's five-rule comparison (sim_baselines.evaluate, seed 53)
so another implementation can be scored on identical scenes (gate G6 on branch av-prototype).

Nothing in sim_baselines.py is changed. This script repeats evaluate()'s random draws in the same
order, writes every observation the rules see, and re-scores R5 from the exported rows as a check
that the export is faithful (must give 2.00% false alarms and 47.39% misses).

Usage (from this directory):  python3 export_scenes.py OUT.csv
Columns: set (clear|hazard), scene, variant (base|lo|hi; lo/hi = pitch -/+ 8 deg, clear set only),
k (0..9, last = decision frame), t_ms, hf, wf, cx, bottom, d (true), cam_h (true), pitch (true, rad,
positive = looking down), scale_err (relative-depth scale error drawn for R4), x (true lateral, m),
hazard (ground truth: in path within 2.5 m).
"""
import csv
import math
import sys

import numpy as np

import sim_baselines as sb


def main(out_path, n_clear=16000, n_haz=8000, seed=53):
    rng = np.random.default_rng(seed)
    r5_fa = r5_miss = 0
    with open(out_path, "w", newline="") as fh:
        w = csv.writer(fh)
        w.writerow(["set", "scene", "variant", "k", "t_ms", "hf", "wf", "cx", "bottom", "d",
                    "cam_h", "pitch", "scale_err", "x", "hazard"])

        def emit(set_name, idx, variant, frames, cam_h, pitch, se, x, hazard):
            for k, f in enumerate(frames):
                w.writerow([set_name, idx, variant, k, "%.1f" % f["t_ms"], "%.6g" % f["hf"], "%.6g" % f["wf"],
                            "%.6g" % f["cx"], "%.6g" % f["bottom"], "%.6g" % f["d"], "%.6g" % cam_h,
                            "%.6g" % pitch, "%.6g" % se, "%.6g" % x, int(hazard)])

        for i in range(n_clear):
            d, H, Wm, x, cam_h, pitch, se, hz = sb.sample_scene(rng, force_hazard=False)
            nz = sb.draw_noise(rng)
            frames = sb.make_frames(nz, d, H, Wm, x, cam_h, pitch, se)
            r5_fa += sb.rule_shipped(frames)
            emit("clear", i, "base", frames, cam_h, pitch, se, x, hz)
            lo = sb.make_frames(nz, d, H, Wm, x, cam_h, pitch - math.radians(8), se)
            hi = sb.make_frames(nz, d, H, Wm, x, cam_h, pitch + math.radians(8), se)
            emit("clear", i, "lo", lo, cam_h, pitch - math.radians(8), se, x, hz)
            emit("clear", i, "hi", hi, cam_h, pitch + math.radians(8), se, x, hz)

        for i in range(n_haz):
            d, H, Wm, x, cam_h, pitch, se, hz = sb.sample_scene(rng, force_hazard=True)
            nz = sb.draw_noise(rng)
            frames = sb.make_frames(nz, d, H, Wm, x, cam_h, pitch, se)
            r5_miss += not sb.rule_shipped(frames)
            emit("hazard", i, "base", frames, cam_h, pitch, se, x, True)

    fa = 100.0 * r5_fa / n_clear
    miss = 100.0 * r5_miss / n_haz
    print("export check: R5 false alarms %.2f%%, misses %.2f%% (expected 2.00, 47.39)" % (fa, miss))
    assert abs(fa - 2.00) < 0.005 and abs(miss - 47.3875) < 0.005, "export does not reproduce the baseline"


if __name__ == "__main__":
    main(sys.argv[1])
