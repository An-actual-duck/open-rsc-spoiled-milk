#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/scripts/lib/myworld-common.sh"

LOG_DIR="$ROOT_DIR/output/logs"
LOG_FILE="$LOG_DIR/test-smoke.log"

mkdir -p "$LOG_DIR"

myworld_require_command timeout

cd "$ROOT_DIR"

./scripts/build-server.sh >"$LOG_FILE" 2>&1

set +e
timeout 20s ./scripts/run-server.sh >>"$LOG_FILE" 2>&1
status=$?
set -e

if [[ $status -eq 0 ]]; then
  echo "FAIL: server exited successfully instead of reaching a stable run or expected startup boundary"
  tail -n 80 "$LOG_FILE"
  exit 1
fi

if [[ $status -ne 1 && $status -ne 124 ]]; then
  echo "FAIL: server exited with unexpected status $status"
  tail -n 80 "$LOG_FILE"
  exit 1
fi

grep -q "Definitions Completed" "$LOG_FILE" || { echo "FAIL: definitions did not finish loading"; tail -n 80 "$LOG_FILE"; exit 1; }
grep -q "World Completed" "$LOG_FILE" || { echo "FAIL: world did not finish loading"; tail -n 80 "$LOG_FILE"; exit 1; }
grep -q "Plugins Completed" "$LOG_FILE" || { echo "FAIL: plugins did not finish loading"; tail -n 80 "$LOG_FILE"; exit 1; }
grep -q "Applying item overrides from ItemDefsMyWorld.json" "$LOG_FILE" || { echo "FAIL: item overrides were not applied"; tail -n 80 "$LOG_FILE"; exit 1; }
grep -q "Applying npc overrides from NpcDefsMyWorld.json" "$LOG_FILE" || { echo "FAIL: npc overrides were not applied"; tail -n 80 "$LOG_FILE"; exit 1; }

if grep -q 'JSONObject\["name"\] not found' "$LOG_FILE"; then
  echo "FAIL: override json parsing regressed"
  tail -n 80 "$LOG_FILE"
  exit 1
fi

if grep -q "Address already in use" "$LOG_FILE"; then
  echo "FAIL: smoke test hit a port collision instead of a clean startup boundary"
  tail -n 80 "$LOG_FILE"
  exit 1
fi

if [[ $status -eq 124 ]]; then
  if grep -q "BUILD FAILED" "$LOG_FILE"; then
    echo "FAIL: Ant reported a build failure before the stable running timeout"
    tail -n 80 "$LOG_FILE"
    exit 1
  fi
  echo "PASS: smoke test reached stable running state before timeout"
else
  grep -Eq "Operation not permitted|Socket creation failed" "$LOG_FILE" || { echo "FAIL: server stopped before expected sandbox socket failure"; tail -n 80 "$LOG_FILE"; exit 1; }
  grep -q "BUILD FAILED" "$LOG_FILE" || { echo "FAIL: Ant did not propagate the expected server startup failure"; tail -n 80 "$LOG_FILE"; exit 1; }
  echo "PASS: smoke test reached expected sandbox-limited startup boundary"
fi

echo "Log: $LOG_FILE"
