#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/scripts/lib/myworld-common.sh"

GENERATOR_MODE="$(myworld_resolve_generator_mode "$@")"
LOG_DIR="$ROOT_DIR/output/logs"
ARTIFACT_DIR="$ROOT_DIR/output/benchmarks/optimization"
STAMP="$(date +%Y%m%d-%H%M%S)"
BENCHMARK_TICKS="${MYWORLD_BENCHMARK_TICKS:-60}"
BENCHMARK_WARMUP_TICKS="${MYWORLD_BENCHMARK_WARMUP_TICKS:-15}"
BENCHMARK_ACTIVE_COMBAT_PAIRS="${MYWORLD_BENCHMARK_ACTIVE_COMBAT_PAIRS:-64}"
BENCHMARK_COMBAT_SEED="${MYWORLD_BENCHMARK_COMBAT_SEED:-1511506100}"
BENCHMARK_REPETITIONS="${MYWORLD_BENCHMARK_REPETITIONS:-2}"
BENCHMARK_EXTRA_JVM_ARGS="${MYWORLD_BENCHMARK_EXTRA_JVM_ARGS:-}"

if (( BENCHMARK_ACTIVE_COMBAT_PAIRS <= 0 )); then
  myworld_fail "MYWORLD_BENCHMARK_ACTIVE_COMBAT_PAIRS must be positive"
fi
if (( BENCHMARK_REPETITIONS < 2 )); then
  myworld_fail "MYWORLD_BENCHMARK_REPETITIONS must be at least 2"
fi

mkdir -p "$LOG_DIR" "$ARTIFACT_DIR"
cd "$ROOT_DIR"

myworld_prepare_generated_artifacts "$GENERATOR_MODE"
myworld_ant_build compile_core
myworld_ant_build compile_plugins

expected_signature=""
for run in $(seq 1 "$BENCHMARK_REPETITIONS"); do
  log_file="$LOG_DIR/active-combat-benchmark-$STAMP-run$run.log"
  summary_file="$ARTIFACT_DIR/active-combat-benchmark-$STAMP-run$run.txt"
  benchmark_args="-Dopenrsc.benchmarkTicks=$BENCHMARK_TICKS -Dopenrsc.benchmarkWarmupTicks=$BENCHMARK_WARMUP_TICKS -Dopenrsc.benchmarkSyntheticPlayers=0 -Dopenrsc.benchmarkSyntheticClientVersion=10052 -Dopenrsc.syncVisibilitySnapshotInput=true -Dopenrsc.layeredPlayerLocationAuthority=true -Dopenrsc.layeredSpatialRuntimeAuthority=true -Dopenrsc.benchmarkNpcProfiling=false -Dopenrsc.benchmarkDeepNpcProfiling=false -Dopenrsc.benchmarkActiveCombatPairs=$BENCHMARK_ACTIVE_COMBAT_PAIRS -Dopenrsc.benchmarkCombatSeed=$BENCHMARK_COMBAT_SEED $BENCHMARK_EXTRA_JVM_ARGS"

  set +e
  myworld_ant_server runserver \
    -DconfFile=myworld \
    "-DbenchmarkJvmArgs=$benchmark_args" \
    >"$log_file" 2>&1
  status=$?
  set -e

  summary="$(grep "FOUNDATION_BENCHMARK" "$log_file" | tail -n 1 || true)"
  if [[ -z "$summary" ]]; then
    tail -n 80 "$log_file"
    myworld_fail "active-combat benchmark run $run did not emit a summary"
  fi
  printf '%s\n' "$summary" >"$summary_file"
  if (( status != 0 )); then
    cat "$summary_file"
    myworld_fail "active-combat benchmark run $run exited with status $status"
  fi
  if [[ "$summary" != *"activeCombatInvariant=pass"* ]]; then
    cat "$summary_file"
    myworld_fail "active-combat benchmark run $run failed its gameplay invariant"
  fi

  signature="$(sed -n 's/.* activeCombatDeterminism=\([^ ]*\).*/\1/p' <<<"$summary")"
  if [[ -z "$signature" ]]; then
    myworld_fail "active-combat benchmark run $run omitted its deterministic signature"
  fi
  if [[ -z "$expected_signature" ]]; then
    expected_signature="$signature"
  elif [[ "$signature" != "$expected_signature" ]]; then
    printf 'Expected signature: %s\nActual signature:   %s\n' \
      "$expected_signature" "$signature" >&2
    myworld_fail "active-combat benchmark outcomes diverged between runs"
  fi

  cat "$summary_file"
  printf 'Log: %s\nSummary: %s\n' "$log_file" "$summary_file"
done

printf 'Deterministic active-combat signature: %s\n' "$expected_signature"
