#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/scripts/lib/myworld-common.sh"

GENERATOR_MODE="$(myworld_resolve_generator_mode "$@")"
LOG_DIR="$ROOT_DIR/output/logs"
ARTIFACT_DIR="$ROOT_DIR/output/benchmarks/optimization"
STAMP="$(date +%Y%m%d-%H%M%S)"
BENCHMARK_TICKS="${MYWORLD_PROJECTILE_BENCHMARK_TICKS:-60}"
BENCHMARK_WARMUP_TICKS="${MYWORLD_PROJECTILE_BENCHMARK_WARMUP_TICKS:-15}"
BENCHMARK_GROUPS="${MYWORLD_PROJECTILE_BENCHMARK_GROUPS:-16}"
BENCHMARK_COMBAT_SEED="${MYWORLD_PROJECTILE_BENCHMARK_SEED:-1511506148}"
BENCHMARK_REPETITIONS="${MYWORLD_PROJECTILE_BENCHMARK_REPETITIONS:-2}"
BENCHMARK_EXTRA_JVM_ARGS="${MYWORLD_PROJECTILE_BENCHMARK_EXTRA_JVM_ARGS:-}"
BENCHMARK_FAMILIES="${MYWORLD_PROJECTILE_BENCHMARK_FAMILIES:-ranged magic multi}"

(( BENCHMARK_GROUPS >= 3 )) || myworld_fail "projectile benchmark requires at least three groups"
(( BENCHMARK_REPETITIONS >= 2 )) || myworld_fail "projectile benchmark repetitions must be at least two"
mkdir -p "$LOG_DIR" "$ARTIFACT_DIR"

myworld_prepare_generated_artifacts "$GENERATOR_MODE"
myworld_ant_build compile_core
myworld_ant_build compile_plugins

for family in $BENCHMARK_FAMILIES; do
 case "$family" in
  ranged|magic|multi) ;;
  *) myworld_fail "unknown projectile benchmark family: $family" ;;
 esac
 expected_signature=""
 for run in $(seq 1 "$BENCHMARK_REPETITIONS"); do
  token="projectile_benchmark_${family}_${$}_${run}"
  config="$ROOT_DIR/server/.${token}.conf"
  database="$ROOT_DIR/server/inc/sqlite/${token}.db"
  log_file="$LOG_DIR/projectile-combat-benchmark-$STAMP-$family-run$run.log"
  summary_file="$ARTIFACT_DIR/projectile-combat-benchmark-$STAMP-$family-run$run.txt"

  cleanup() {
    rm -f "$config" "$database" "$database-wal" "$database-shm"
  }
  trap cleanup EXIT

  cp "$ROOT_DIR/server/inc/sqlite/myworld_seed.db" "$database"
  cp "$ROOT_DIR/server/myworld.conf" "$config"
  sed -i \
    -e "s/^\([[:space:]]*db_name:[[:space:]]*\).*/\1${token}/" \
    -e "s/^\([[:space:]]*server_name:[[:space:]]*\).*/\1Projectile Combat Benchmark ${family} ${run}/" \
    "$config"

  benchmark_args="-Dopenrsc.benchmarkTicks=$BENCHMARK_TICKS -Dopenrsc.benchmarkWarmupTicks=$BENCHMARK_WARMUP_TICKS -Dopenrsc.benchmarkSyntheticPlayers=0 -Dopenrsc.benchmarkSyntheticClientVersion=10052 -Dopenrsc.syncVisibilitySnapshotInput=true -Dopenrsc.layeredPlayerLocationAuthority=true -Dopenrsc.layeredSpatialRuntimeAuthority=true -Dopenrsc.benchmarkNpcProfiling=false -Dopenrsc.benchmarkDeepNpcProfiling=false -Dopenrsc.benchmarkProjectileCombatGroups=$BENCHMARK_GROUPS -Dopenrsc.benchmarkProjectileCombatFamily=$family -Dopenrsc.benchmarkCombatSeed=$BENCHMARK_COMBAT_SEED $BENCHMARK_EXTRA_JVM_ARGS"

  set +e
  (
    cd "$ROOT_DIR/server"
    ANT_HOME="$ANT_HOME" sh "$ANT_BIN" runserver \
      "-DconfFile=.${token}" \
      "-DbenchmarkJvmArgs=$benchmark_args"
  ) >"$log_file" 2>&1
  status=$?
  set -e

  summary="$(rg "FOUNDATION_BENCHMARK" "$log_file" | tail -n 1 || true)"
  [[ -n "$summary" ]] || {
    tail -n 100 "$log_file"
    myworld_fail "projectile benchmark run $run emitted no summary"
  }
  printf '%s\n' "$summary" >"$summary_file"
  (( status == 0 )) || {
    cat "$summary_file"
    myworld_fail "projectile benchmark run $run exited with $status"
  }
  [[ "$summary" == *"projectileCombatFamily=$family"* ]] ||
    myworld_fail "projectile benchmark run $run reported the wrong family"
  [[ "$summary" == *"projectileCombatInvariant=pass"* ]] || {
    cat "$summary_file"
    myworld_fail "projectile benchmark run $run failed its gameplay invariant"
  }

  signature="$(sed -n 's/.* projectileCombatDeterminism=\([^ ]*\).*/\1/p' <<<"$summary")"
  [[ -n "$signature" ]] || myworld_fail "projectile benchmark omitted deterministic signature"
  if [[ -z "$expected_signature" ]]; then
    expected_signature="$signature"
  elif [[ "$signature" != "$expected_signature" ]]; then
    printf 'Expected signature: %s\nActual signature:   %s\n' \
      "$expected_signature" "$signature" >&2
    myworld_fail "projectile benchmark outcomes diverged between runs"
  fi

  cat "$summary_file"
  printf 'Log: %s\nSummary: %s\n' "$log_file" "$summary_file"
  cleanup
  trap - EXIT
 done

 printf 'Deterministic %s projectile-combat signature: %s\n' \
  "$family" "$expected_signature"
done
