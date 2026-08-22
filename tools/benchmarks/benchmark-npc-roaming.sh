#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/scripts/lib/myworld-common.sh"

GENERATOR_MODE="$(myworld_resolve_generator_mode "$@")"
LOG_DIR="$ROOT_DIR/output/logs"
ARTIFACT_DIR="$ROOT_DIR/output/benchmarks/optimization"
STAMP="$(date +%Y%m%d-%H%M%S)"
TICKS="${MYWORLD_NPC_ROAM_BENCHMARK_TICKS:-80}"
WARMUP="${MYWORLD_NPC_ROAM_BENCHMARK_WARMUP_TICKS:-20}"
NPCS="${MYWORLD_NPC_ROAM_BENCHMARK_NPCS:-64}"
SEED="${MYWORLD_NPC_ROAM_BENCHMARK_SEED:-1511506148}"
REPETITIONS="${MYWORLD_NPC_ROAM_BENCHMARK_REPETITIONS:-2}"
EXTRA_JVM_ARGS="${MYWORLD_NPC_ROAM_BENCHMARK_EXTRA_JVM_ARGS:-}"

(( NPCS >= 12 && NPCS % 4 == 0 )) || myworld_fail "NPC count must be a multiple of four >= 12"
(( REPETITIONS >= 2 )) || myworld_fail "NPC roaming benchmark requires at least two repetitions"
mkdir -p "$LOG_DIR" "$ARTIFACT_DIR"

myworld_prepare_generated_artifacts "$GENERATOR_MODE"
myworld_ant_build compile_core
myworld_ant_build compile_plugins

expected_signature=""
for run in $(seq 1 "$REPETITIONS"); do
 token="npc_roam_benchmark_${$}_${run}"
 config="$ROOT_DIR/server/.${token}.conf"
 database="$ROOT_DIR/server/inc/sqlite/${token}.db"
 log_file="$LOG_DIR/npc-roaming-benchmark-$STAMP-run$run.log"
 summary_file="$ARTIFACT_DIR/npc-roaming-benchmark-$STAMP-run$run.txt"

 cleanup() { rm -f "$config" "$database" "$database-wal" "$database-shm"; }
 trap cleanup EXIT
 cp "$ROOT_DIR/server/inc/sqlite/myworld_seed.db" "$database"
 cp "$ROOT_DIR/server/myworld.conf" "$config"
 sed -i \
   -e "s/^\([[:space:]]*db_name:[[:space:]]*\).*/\1${token}/" \
   -e "s/^\([[:space:]]*server_name:[[:space:]]*\).*/\1NPC Roaming Benchmark ${run}/" \
   -e "s/^\([[:space:]]*want_npc_idle_tick_throttle:[[:space:]]*\).*/\1false/" \
   -e "s/^\([[:space:]]*want_custom_walking_speed:[[:space:]]*\).*/\1false/" \
   "$config"

 args="-Dopenrsc.benchmarkTicks=$TICKS -Dopenrsc.benchmarkWarmupTicks=$WARMUP -Dopenrsc.benchmarkSyntheticPlayers=0 -Dopenrsc.benchmarkSyntheticClientVersion=10052 -Dopenrsc.syncVisibilitySnapshotInput=true -Dopenrsc.layeredPlayerLocationAuthority=true -Dopenrsc.layeredSpatialRuntimeAuthority=true -Dopenrsc.benchmarkNpcProfiling=true -Dopenrsc.benchmarkDeepNpcProfiling=true -Dopenrsc.benchmarkNpcRoamingCount=$NPCS -Dopenrsc.benchmarkCombatSeed=$SEED $EXTRA_JVM_ARGS"

 set +e
 (cd "$ROOT_DIR/server" && ANT_HOME="$ANT_HOME" sh "$ANT_BIN" runserver \
   "-DconfFile=.${token}" "-DbenchmarkJvmArgs=$args") >"$log_file" 2>&1
 status=$?
 set -e
 summary="$(rg "FOUNDATION_BENCHMARK" "$log_file" | tail -n 1 || true)"
 [[ -n "$summary" ]] || { tail -n 120 "$log_file"; myworld_fail "run $run emitted no summary"; }
 printf '%s\n' "$summary" >"$summary_file"
 (( status == 0 )) || { cat "$summary_file"; myworld_fail "run $run exited with $status"; }
 [[ "$summary" == *"npcRoamingInvariant=pass"* ]] || { cat "$summary_file"; myworld_fail "roaming invariant failed"; }
 signature="$(sed -n 's/.* npcRoamingDeterminism=\([^ ]*\).*/\1/p' <<<"$summary")"
 [[ -n "$signature" ]] || myworld_fail "roaming signature missing"
 if [[ -z "$expected_signature" ]]; then expected_signature="$signature";
 elif [[ "$signature" != "$expected_signature" ]]; then
   myworld_fail "NPC roaming outcomes diverged: $expected_signature != $signature"
 fi
 cat "$summary_file"
 printf 'Log: %s\nSummary: %s\n' "$log_file" "$summary_file"
 cleanup
 trap - EXIT
done
printf 'Deterministic NPC-roaming signature: %s\n' "$expected_signature"
