#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
python3 "$SCRIPT_ROOT/scripts/generate-npc-capacity-contract.py" --check
python3 "$SCRIPT_ROOT/scripts/generate-cleric-blessing-contract.py" --check
ROOT_DIR="${ROOT_DIR:-$SCRIPT_ROOT}"
source "$SCRIPT_ROOT/scripts/lib/myworld-common.sh"

GENERATOR_MODE="$(myworld_resolve_generator_mode "$@")"
ANT_ANALYSIS_ARGS=()
if [[ -n "${SPOILED_MILK_JAVAC_LINT:-}" ]]; then
  ANT_ANALYSIS_ARGS=("-Djavac.lint=$SPOILED_MILK_JAVAC_LINT" "-Djavac.maxwarns=${SPOILED_MILK_JAVAC_MAXWARNS:-10000}")
fi

myworld_prepare_generated_artifacts "$GENERATOR_MODE"
myworld_ant_build compile_core "${ANT_ANALYSIS_ARGS[@]}"
myworld_ant_build compile_plugins "${ANT_ANALYSIS_ARGS[@]}"
python3 "$SCRIPT_ROOT/scripts/audit-server-build.py" --check --require-artifacts
