#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
python3 "$SCRIPT_ROOT/scripts/generate-npc-capacity-contract.py" --check
ROOT_DIR="${ROOT_DIR:-$SCRIPT_ROOT}"
if [[ ! -f "$ROOT_DIR/Client_Base/build.xml" || ! -d "$ROOT_DIR/scripts" ]]; then
  printf 'WARN: Ignoring invalid ROOT_DIR=%s; using %s\n' "$ROOT_DIR" "$SCRIPT_ROOT" >&2
  ROOT_DIR="$SCRIPT_ROOT"
fi
ANT_HOME="${ANT_HOME:-$ROOT_DIR/tools/vendor/apache-ant-1.10.5}"
ANT_BIN="${ANT_BIN:-$ANT_HOME/bin/ant}"
ANT_ARGS=(compile)

if [[ -n "${SPOILED_MILK_JAVAC_LINT:-}" ]]; then
  ANT_ARGS=("-Djavac.lint=$SPOILED_MILK_JAVAC_LINT" "-Djavac.maxwarns=${SPOILED_MILK_JAVAC_MAXWARNS:-10000}" "${ANT_ARGS[@]}")
fi

if [[ "${SPOILED_MILK_RELEASE_BUILD:-0}" == 1 ]]; then
  RELEASE_MARKER_DIR="$ROOT_DIR/output/release-build"
  RELEASE_MARKER_FILE="$RELEASE_MARKER_DIR/spoiled-milk-release-build.marker"
  mkdir -p "$RELEASE_MARKER_DIR"
  printf 'release-build=true\n' > "$RELEASE_MARKER_FILE"
  ANT_ARGS=("-Drelease.marker.file=$RELEASE_MARKER_FILE" "${ANT_ARGS[@]}")
fi

if [[ ! -f "$ANT_BIN" ]]; then
  printf 'FAIL: Missing bundled Ant launcher: %s\n' "$ANT_BIN" >&2
  exit 1
fi

(
  cd "$ROOT_DIR/Client_Base"
  ANT_HOME="$ANT_HOME" sh "$ANT_BIN" "${ANT_ARGS[@]}"
)
