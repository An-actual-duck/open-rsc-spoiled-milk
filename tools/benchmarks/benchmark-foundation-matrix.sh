#!/usr/bin/env bash
set -euo pipefail

source "$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/scripts/lib/myworld-common.sh"

SCENARIOS="${MYWORLD_BENCHMARK_SCENARIOS:-short soak}"

run_scenario() {
  local name="$1"
  local ticks="$2"
  local warmup="$3"
  local synthetic_players="${4:-0}"
  local output

  output="$(cd "$ROOT_DIR" && MYWORLD_BENCHMARK_TICKS="$ticks" MYWORLD_BENCHMARK_WARMUP_TICKS="$warmup" MYWORLD_BENCHMARK_SYNTHETIC_PLAYERS="$synthetic_players" ./tools/benchmarks/benchmark-foundation.sh)"

  local summary
  summary="$(printf '%s\n' "$output" | grep 'FOUNDATION_BENCHMARK' | tail -n 1)"
  local log_file
  log_file="$(printf '%s\n' "$output" | sed -n 's/^Log: //p' | tail -n 1)"
  local summary_file
  summary_file="$(printf '%s\n' "$output" | sed -n 's/^Summary: //p' | tail -n 1)"

  printf '%s\t%s\t%s\t%s\n' "$name" "$summary" "$log_file" "$summary_file"
}

printf 'scenario\tbenchmark_summary\tlog\tsummary_file\n'

for scenario in $SCENARIOS; do
  case "$scenario" in
    short)
      run_scenario short 20 5
      ;;
    soak)
      run_scenario soak 300 30
      ;;
    players)
      run_scenario players 120 10 64
      ;;
    *)
      echo "FAIL: unsupported benchmark scenario '$scenario' (supported: short soak players)" >&2
      exit 1
      ;;
  esac
done
