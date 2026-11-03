#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANT_HOME="${ANT_HOME:-$SCRIPT_ROOT/tools/vendor/apache-ant-1.10.5}"
ANT_BIN="${ANT_BIN:-$ANT_HOME/bin/ant}"
ANT_ARGS=(jar)

if [[ -n "${SPOILED_MILK_JAVAC_LINT:-}" ]]; then
  ANT_ARGS=("-Djavac.lint=$SPOILED_MILK_JAVAC_LINT" "-Djavac.maxwarns=${SPOILED_MILK_JAVAC_MAXWARNS:-10000}" "${ANT_ARGS[@]}")
fi

if [[ ! -f "$ANT_BIN" ]]; then
  printf 'FAIL: Missing bundled Ant launcher: %s\n' "$ANT_BIN" >&2
  exit 1
fi

(
  cd "$SCRIPT_ROOT/tools/world-builder"
  ANT_HOME="$ANT_HOME" sh "$ANT_BIN" "${ANT_ARGS[@]}"
)

printf 'Built %s\n' "$SCRIPT_ROOT/output/builds/world-builder/world-builder-tools.jar"
