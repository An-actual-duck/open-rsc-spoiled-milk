#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/scripts/lib/myworld-common.sh"

GENERATOR_MODE="$(myworld_resolve_generator_mode "$@")"
LOG_DIR="$ROOT_DIR/output/logs"
ARTIFACT_DIR="$ROOT_DIR/output/benchmarks/optimization"
STAMP="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/foundation-benchmark-$STAMP.log"
SUMMARY_FILE="$ARTIFACT_DIR/foundation-benchmark-$STAMP.txt"
BENCHMARK_TICKS="${MYWORLD_BENCHMARK_TICKS:-120}"
BENCHMARK_WARMUP_TICKS="${MYWORLD_BENCHMARK_WARMUP_TICKS:-10}"
BENCHMARK_SYNTHETIC_PLAYERS="${MYWORLD_BENCHMARK_SYNTHETIC_PLAYERS:-0}"

mkdir -p "$LOG_DIR" "$ARTIFACT_DIR"

cd "$ROOT_DIR"

myworld_prepare_generated_artifacts "$GENERATOR_MODE"
myworld_ant_build compile_core
myworld_ant_build compile_plugins

set +e
myworld_ant_server runserver \
  -DconfFile=myworld \
  "-DbenchmarkJvmArgs=-Dopenrsc.benchmarkTicks=$BENCHMARK_TICKS -Dopenrsc.benchmarkWarmupTicks=$BENCHMARK_WARMUP_TICKS -Dopenrsc.benchmarkSyntheticPlayers=$BENCHMARK_SYNTHETIC_PLAYERS" \
  >"$LOG_FILE" 2>&1
status=$?
set -e

if ! grep -q "FOUNDATION_BENCHMARK" "$LOG_FILE"; then
  echo "FAIL: benchmark summary was not emitted"
  echo "Log: $LOG_FILE"
  tail -n 80 "$LOG_FILE"
  exit 1
fi

grep "FOUNDATION_BENCHMARK" "$LOG_FILE" | tail -n 1 >"$SUMMARY_FILE"

if [[ $status -ne 0 ]]; then
  echo "FAIL: benchmark emitted a summary but server exited with status $status"
  echo "Log: $LOG_FILE"
  cat "$SUMMARY_FILE"
  exit 1
fi

cat "$SUMMARY_FILE"
echo "Log: $LOG_FILE"
echo "Summary: $SUMMARY_FILE"
