#!/usr/bin/env bash
# Hidden hardware presentation/state contracts; never starts a server or shows a window.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
[[ -n "${DISPLAY:-}" && $# -eq 0 ]] || { echo 'Usage: DISPLAY=:0 tests/myworld/test-underground-lighting-gpu.sh' >&2; exit 2; }
RUN="$(python3 scripts/workspace-layout.py path tests underground-light-normalization --mkdir)/gpu-$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$RUN/classes"
JAR="$ROOT/Client_Base/Open_RSC_Client.jar"
javac -source 8 -target 8 -cp "$JAR" -d "$RUN/classes" tests/myworld/UndergroundLightingGpuFixture.java tests/myworld/GeometryShadowRoofMaskFixture.java
COMMON="$(git rev-parse --git-common-dir)"; COMMON="$(cd "$COMMON" && pwd)"
flock "$COMMON/ai-workspaces/renderer-gpu-benchmark.lock" java -cp "$RUN/classes:$JAR" orsc.UndergroundLightingGpuFixture "$RUN" | tee "$RUN/proof.log"
printf 'Evidence: %s\n' "$RUN"
