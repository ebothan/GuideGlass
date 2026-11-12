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

# --- Kotlin safety core (added in P4) -------------------------------------
if [ -f core/settings.gradle.kts ]; then
  ./gradlew -p core --no-daemon -q test
  echo "core tests passed"
else
  echo "core/ not built yet (P4)"
fi
echo "init.sh: OK"
