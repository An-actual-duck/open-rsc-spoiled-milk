#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_DIR="${ROOT_DIR:-$SCRIPT_ROOT}"
source "$SCRIPT_ROOT/scripts/lib/myworld-common.sh"

GENERATOR_MODE="$(myworld_resolve_generator_mode "$@")"

cd "$ROOT_DIR"

if [[ "$GENERATOR_MODE" == "sync" ]]; then
  ./scripts/check.sh --sync-generated
else
  ./scripts/check.sh
fi
./scripts/init-sqlite.sh
./scripts/build-server.sh
./scripts/run-server.sh
