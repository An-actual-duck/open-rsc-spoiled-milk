#!/usr/bin/env bash
# Optional real-hardware pixel/state proof; creates only an unfocused hidden window.
set -euo pipefail
SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$SCRIPT_ROOT"
MODE="supported"
if [[ "${1:-}" == "--unsupported" ]]; then MODE="unsupported"; shift; fi
if [[ $# -ne 0 ]]; then echo "Usage: $0 [--unsupported]" >&2; exit 2; fi
if [[ -z "${DISPLAY:-}" ]]; then echo "A hardware-capable DISPLAY is required; this test never shows or focuses its window." >&2; exit 2; fi
TEST_ROOT="$(python3 scripts/workspace-layout.py path tests geometry-shadow-renderer --mkdir)"
RUN_DIR="$TEST_ROOT/gpu-$MODE-$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$RUN_DIR/classes"
JAR="$SCRIPT_ROOT/Client_Base/Open_RSC_Client.jar"
javac -source 8 -target 8 -cp "$JAR" -d "$RUN_DIR/classes" tests/myworld/GeometryShadowGpuFixture.java tests/myworld/TerrainShadowGpuFixture.java tests/myworld/GeometryShadowRoofMaskFixture.java
COMMON_GIT="$(git rev-parse --git-common-dir)"
COMMON_GIT="$(cd "$COMMON_GIT" && pwd)"
LOCK="$COMMON_GIT/ai-workspaces/renderer-gpu-benchmark.lock"
if [[ "$MODE" == unsupported ]]; then
  flock "$LOCK" env MESA_GL_VERSION_OVERRIDE=2.1 java -cp "$RUN_DIR/classes:$JAR" orsc.GeometryShadowGpuFixture "$RUN_DIR" unsupported | tee "$RUN_DIR/proof.log"
else
  flock "$LOCK" java -cp "$RUN_DIR/classes:$JAR" orsc.GeometryShadowGpuFixture "$RUN_DIR" | tee "$RUN_DIR/proof.log"
  flock "$LOCK" java -cp "$RUN_DIR/classes:$JAR" orsc.TerrainShadowGpuFixture "$RUN_DIR/actual-terrain" | tee "$RUN_DIR/terrain-proof.log"
fi
printf 'Evidence: %s\n' "$RUN_DIR"
