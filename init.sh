#!/usr/bin/env bash
# One command to set up, build and test everything that can run on this machine.
# Works with no API keys. Exits non-zero on the first failure.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

# --- JDK 17 for the Kotlin core (Android SDK is not required) -------------
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@17 ]; then
  export JAVA_HOME=/opt/homebrew/opt/openjdk@17
fi

# --- Python environment for the simulation harness ------------------------
if [ ! -x .venv/bin/python ]; then
  python3 -m venv .venv
fi
.venv/bin/python -c "import numpy, matplotlib" 2>/dev/null || .venv/bin/pip -q install numpy matplotlib

# --- Baseline check: the repo's simulation must still reproduce R5 --------
# Runs in a temp copy so the committed figures are never rewritten.
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cp paper/simulation/*.py "$TMP"/
( cd "$TMP" && MPLBACKEND=Agg "$ROOT/.venv/bin/python" sim_baselines.py >/dev/null )
"$ROOT/.venv/bin/python" - "$TMP/sim_baseline_results.json" <<'PY'
import json, sys
r = json.load(open(sys.argv[1]))["rules"]["R5  Shipped VisionAI rule"]
assert abs(r["fa"] - 2.00) < 0.01 and abs(r["miss"] - 47.39) < 0.01, r
print(f"baseline R5 reproduced: false alarms {r['fa']:.2f}%, misses {r['miss']:.2f}%  [L1]")
PY

# --- Kotlin safety core: build and unit/scenario tests (gate G1) ----------
sh ./gradlew -p core --no-daemon -q test
echo "core tests passed"

# --- Regression gate G6: this design vs R5 on the repo's own scene set ----
# Exports the identical seed-53 scenes (the export re-checks R5), scores the core on them, and fails if
# the matched-threshold setting no longer beats R5. NOTE: that setting is given a depth value and the
# camera pitch, which R5 does not use; it is a regression tripwire, not a fair comparison (docs/review). Outputs stay in build/.
mkdir -p core/build/eval
( cd "$TMP" && MPLBACKEND=Agg "$ROOT/.venv/bin/python" "$ROOT/paper/simulation/export_scenes.py" "$ROOT/core/build/eval/scenes.csv" )
sh ./gradlew -p core --no-daemon -q runBaselineEval -Pargs="core/build/eval/scenes.csv core/build/eval" >/dev/null
"$ROOT/.venv/bin/python" - core/build/eval/g6_baseline_comparison.json <<'PY'
import json, sys
m = next(r for r in json.load(open(sys.argv[1])) if r["setting"] == "matched")
assert m["miss_pct"] < 47.39 and m["fa_pct"] <= 2.00, m
u = next(r for r in json.load(open(sys.argv[1])) if r["setting"] == "matched, pitch unknown")
print(f"G6 pitch unknown:   false alarms {u['fa_pct']:.2f}%, misses {u['miss_pct']:.2f}%  (worse than R5 on false alarms)")
print(f"G6 matched setting (given depth and pitch R5 does not use): false alarms {m['fa_pct']:.2f}% (R5 2.00), misses {m['miss_pct']:.2f}% (R5 47.39)  [L1]")
PY
echo "init.sh: OK"
