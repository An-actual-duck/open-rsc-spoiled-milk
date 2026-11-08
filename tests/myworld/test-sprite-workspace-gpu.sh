#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
[[ -n "${DISPLAY:-}" ]] || { echo 'A hardware-capable DISPLAY is required.' >&2; exit 2; }
RUN="$(python3 scripts/workspace-layout.py path tests client-portability-pass --mkdir)/gpu-$(date -u +%Y%m%dT%H%M%SZ)-$$"
mkdir -p "$RUN/classes"
JAR="$ROOT/Client_Base/Open_RSC_Client.jar"
javac -source 8 -target 8 -cp "$JAR" -d "$RUN/classes" tests/myworld/SpriteWorkspaceGpuFixture.java
COMMON="$(git rev-parse --path-format=absolute --git-common-dir)"
flock "$COMMON/ai-workspaces/renderer-gpu-benchmark.lock" java -cp "$RUN/classes:$JAR" orsc.SpriteWorkspaceGpuFixture | tee "$RUN/proof.log"
printf 'Evidence: %s\n' "$RUN"
