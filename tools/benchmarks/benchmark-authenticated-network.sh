#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/scripts/lib/myworld-common.sh"

GENERATOR_MODE="$(myworld_resolve_generator_mode "$@")"
LOG_DIR="$ROOT_DIR/output/logs"
ARTIFACT_DIR="$ROOT_DIR/output/benchmarks/optimization"
STAMP="$(date +%Y%m%d-%H%M%S)"
CLIENTS="${MYWORLD_NETWORK_BENCHMARK_CLIENTS:-8}"
TICKS="${MYWORLD_NETWORK_BENCHMARK_TICKS:-35}"
WARMUP_TICKS="${MYWORLD_NETWORK_BENCHMARK_WARMUP_TICKS:-20}"
REPETITIONS="${MYWORLD_NETWORK_BENCHMARK_REPETITIONS:-2}"
CLIENT_SECONDS="${MYWORLD_NETWORK_BENCHMARK_CLIENT_SECONDS:-42}"
EXTRA_JVM_ARGS="${MYWORLD_NETWORK_BENCHMARK_EXTRA_JVM_ARGS:-}"

(( CLIENTS >= 2 )) || myworld_fail "network benchmark requires at least two clients"
(( REPETITIONS >= 2 )) || myworld_fail "network benchmark repetitions must be at least two"
mkdir -p "$LOG_DIR" "$ARTIFACT_DIR"

myworld_prepare_generated_artifacts "$GENERATOR_MODE"
myworld_ant_build compile_core
myworld_ant_build compile_plugins

choose_port() {
  python3 - <<'PY'
import socket
with socket.socket() as sock:
    sock.bind(("127.0.0.1", 0))
    print(sock.getsockname()[1])
PY
}

for run in $(seq 1 "$REPETITIONS"); do
  port="$(choose_port)"
  token="network_benchmark_${$}_${run}"
  config="$ROOT_DIR/server/.${token}.conf"
  database="$ROOT_DIR/server/inc/sqlite/${token}.db"
  log_file="$LOG_DIR/authenticated-network-$STAMP-run$run.log"
  client_file="$ARTIFACT_DIR/authenticated-network-$STAMP-run$run-client.txt"
  summary_file="$ARTIFACT_DIR/authenticated-network-$STAMP-run$run.txt"
  server_pid=""

  cleanup() {
    if [[ -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null; then
      kill "$server_pid" 2>/dev/null || true
      wait "$server_pid" 2>/dev/null || true
    fi
    rm -f "$config" "$database" "$database-wal" "$database-shm"
  }
  trap cleanup EXIT

  cp "$ROOT_DIR/server/inc/sqlite/myworld_seed.db" "$database"
  cp "$ROOT_DIR/server/myworld.conf" "$config"
  sed -i \
    -e "s/^\([[:space:]]*db_name:[[:space:]]*\).*/\1${token}/" \
    -e "s/^\([[:space:]]*server_name:[[:space:]]*\).*/\1Authenticated Network Benchmark ${run}/" \
    -e "s/^\([[:space:]]*server_port:[[:space:]]*\).*/\1${port}/" \
    -e "s/^\([[:space:]]*want_feature_websockets:[[:space:]]*\).*/\1false/" \
    -e "s/^\([[:space:]]*max_connections_per_ip:[[:space:]]*\).*/\164/" \
    -e "s/^\([[:space:]]*max_connections_per_second:[[:space:]]*\).*/\164/" \
    -e "s/^\([[:space:]]*max_logins_per_second:[[:space:]]*\).*/\164/" \
    -e "s/^\([[:space:]]*max_logins_per_server_per_tick:[[:space:]]*\).*/\164/" \
    -e "s/^\([[:space:]]*max_players_per_ip:[[:space:]]*\).*/\164/" \
    "$config"

  benchmark_args="-Dopenrsc.benchmarkTicks=$TICKS -Dopenrsc.benchmarkWarmupTicks=$WARMUP_TICKS -Dopenrsc.benchmarkSyntheticPlayers=0 -Dopenrsc.benchmarkNpcProfiling=false -Dopenrsc.benchmarkDeepNpcProfiling=false -Dopenrsc.benchmarkAuthenticatedNetworkClients=$CLIENTS $EXTRA_JVM_ARGS"
  (
    cd "$ROOT_DIR/server"
    ANT_HOME="$ANT_HOME" sh "$ANT_BIN" runserver \
      "-DconfFile=.${token}" \
      "-DbenchmarkJvmArgs=$benchmark_args"
  ) >"$log_file" 2>&1 &
  server_pid=$!

  ready=0
  for _ in $(seq 1 240); do
    if rg -q "Game world is now online" "$log_file" 2>/dev/null; then
      ready=1
      break
    fi
    if ! kill -0 "$server_pid" 2>/dev/null; then
      tail -n 100 "$log_file"
      myworld_fail "authenticated network server exited before binding"
    fi
    sleep 0.25
  done
  (( ready == 1 )) || myworld_fail "timed out waiting for authenticated network listener"

  python3 "$ROOT_DIR/tools/benchmarks/authenticated-network-client.py" \
    --port "$port" --clients "$CLIENTS" --seconds "$CLIENT_SECONDS" \
    >"$client_file" &
  client_pid=$!

  set +e
  wait "$server_pid"
  server_status=$?
  server_pid=""
  wait "$client_pid"
  client_status=$?
  set -e

  summary="$(rg "FOUNDATION_BENCHMARK" "$log_file" | tail -n 1 || true)"
  [[ -n "$summary" ]] || myworld_fail "run $run emitted no server benchmark summary"
  printf '%s\n' "$summary" >"$summary_file"
  (( server_status == 0 )) || myworld_fail "run $run server exited with $server_status"
  (( client_status == 0 )) || { cat "$client_file"; myworld_fail "run $run client failed"; }
  [[ "$summary" == *"authenticatedNetworkInvariant=pass"* ]] || {
    cat "$summary_file"
    myworld_fail "run $run failed its server network invariant"
  }
  rg -q '"invariant": "pass"' "$client_file" || myworld_fail "run $run failed client framing invariant"

  cat "$summary_file"
  cat "$client_file"
  printf 'Log: %s\nSummary: %s\nClient: %s\n' "$log_file" "$summary_file" "$client_file"
  cleanup
  trap - EXIT
done
