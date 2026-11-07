#!/usr/bin/env bash
# Optional hardware temporal regression; no shown/focused window, server or input.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
[[ -n "${DISPLAY:-}" ]] || { echo 'A hardware-capable DISPLAY is required.' >&2; exit 2; }
[[ $# -eq 0 ]] || { echo "Usage: DISPLAY=:0 $0" >&2; exit 2; }
RUN="$(python3 scripts/workspace-layout.py path tests thin-shadow-stability --mkdir)/temporal-$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$RUN/classes"
JAR="$ROOT/Client_Base/Open_RSC_Client.jar"
javac -source 8 -target 8 -cp "$JAR" -d "$RUN/classes" tests/myworld/ThinShadowTemporalGpuFixture.java
COMMON="$(git rev-parse --git-common-dir)"
COMMON="$(cd "$COMMON" && pwd)"
flock "$COMMON/ai-workspaces/renderer-gpu-benchmark.lock" java -cp "$RUN/classes:$JAR" orsc.ThinShadowTemporalGpuFixture "$RUN" | tee "$RUN/proof.log"
printf 'Evidence: %s\n' "$RUN"
