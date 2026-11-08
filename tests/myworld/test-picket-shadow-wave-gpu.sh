#!/usr/bin/env bash
# Hidden actual-mask repeated-fence controls; no server, shown window or user input.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
[[ -n "${DISPLAY:-}" && $# -eq 0 ]] || { echo 'Usage: DISPLAY=:0 tests/myworld/test-picket-shadow-wave-gpu.sh' >&2; exit 2; }
RUN="$(python3 scripts/workspace-layout.py path tests picket-shadow-wave --mkdir)/gpu-$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$RUN/classes"
JAR="$ROOT/Client_Base/Open_RSC_Client.jar"
javac -source 8 -target 8 -cp "$JAR" -d "$RUN/classes" tests/myworld/PicketShadowWaveGpuFixture.java
COMMON="$(git rev-parse --git-common-dir)"; COMMON="$(cd "$COMMON" && pwd)"
exec 9>"$COMMON/ai-workspaces/renderer-gpu-benchmark.lock"
flock 9
for material in timber railing; do
  for variant in near seam low elevated; do
    props=(-Dprobe.cascade=near)
    if [[ "$variant" == seam ]]; then props=(-Dprobe.cascade=combined -Dprobe.mixedZ=3400 -Dprobe.bothAngles=true); fi
    if [[ "$variant" == low ]]; then props+=(-Dprobe.low=true); fi
    if [[ "$variant" == elevated ]]; then props+=(-Dprobe.height=-1000 -Dprobe.elevation=10 -Dprobe.bothAngles=true); fi
    java "${props[@]}" -cp "$RUN/classes:$JAR" orsc.PicketShadowWaveGpuFixture "$RUN/$material-$variant" "tests/myworld/fixtures/native-$material-alpha.properties" | tee "$RUN/$material-$variant.log"
  done
done
printf 'Evidence: %s\n' "$RUN"
