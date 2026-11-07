#!/usr/bin/env bash
# Optional exact native-export replay; never opens a visible window or logs in.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
if [[ $# -lt 1 || $# -gt 2 || -z "${DISPLAY:-}" ]]; then
  echo "Usage: DISPLAY=:0 $0 SEERS_EXPORT_DIRECTORY [BASELINE_CLIENT_JAR]" >&2
  exit 2
fi
INPUT="$(realpath "$1")"
[[ -f "$INPUT/terrain-triangles.tsv" && -f "$INPUT/terrain-frame.json" ]] || { echo 'Missing native export' >&2; exit 2; }
DEST="$(python3 scripts/workspace-layout.py path tests terrain-shadow-artifacts --mkdir)/replay-$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$DEST/classes"
JAR="$ROOT/Client_Base/Open_RSC_Client.jar"
javac -source 8 -target 8 -cp "$JAR" -d "$DEST/classes" tests/myworld/TerrainLightingGpuFixture.java
COMMON="$(git rev-parse --git-common-dir)"; COMMON="$(cd "$COMMON" && pwd)"
ARGS=("$INPUT" "$DEST")
if [[ $# -eq 2 ]]; then ARGS+=("$(realpath "$2")"); fi
flock "$COMMON/ai-workspaces/renderer-gpu-benchmark.lock" java -cp "$DEST/classes:$JAR" orsc.TerrainLightingGpuFixture "${ARGS[@]}" | tee "$DEST/replay.log"
printf 'Evidence: %s\n' "$DEST"
