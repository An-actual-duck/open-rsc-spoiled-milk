#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_DIR="${ROOT_DIR:-$SCRIPT_ROOT}"
source "$SCRIPT_ROOT/scripts/lib/myworld-common.sh"

DB_NAME="myworld_dev"
DB_PATH="$ROOT_DIR/server/inc/sqlite/${DB_NAME}.db"
SOURCE_DB_PATH="$ROOT_DIR/server/inc/sqlite/myworld_seed.db"

myworld_require_seed_db

cd "$ROOT_DIR"

rm -f "$DB_PATH"
cp "$SOURCE_DB_PATH" "$DB_PATH"

echo "Initialized SQLite database from MyWorld seed: $DB_PATH"
