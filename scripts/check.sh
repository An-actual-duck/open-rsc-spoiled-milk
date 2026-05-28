#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_DIR="${ROOT_DIR:-$SCRIPT_ROOT}"
source "$SCRIPT_ROOT/scripts/lib/myworld-common.sh"

GENERATOR_MODE="$(myworld_resolve_generator_mode "$@")"

echo "Checking MyWorld dev prerequisites..."

myworld_require_command java
myworld_require_ant
myworld_require_seed_db

echo "Java: $(command -v java)"
echo "Bundled Ant: $ANT_BIN"
echo "MyWorld seed DB: $ROOT_DIR/server/inc/sqlite/myworld_seed.db"
if [[ "$GENERATOR_MODE" == "sync" ]]; then
  echo "Generated artifact mode: sync"
else
  echo "Generated artifact mode: check-only"
fi
myworld_prepare_generated_artifacts "$GENERATOR_MODE"
echo "Prerequisite check passed."
