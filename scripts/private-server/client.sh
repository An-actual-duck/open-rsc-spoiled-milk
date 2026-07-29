#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(CDPATH= cd "$SCRIPT_DIR/../.." && pwd)

echo "Spoiled Milk local client"
echo "This points the repo client at the private development server."
echo

exec "$ROOT_DIR/scripts/run-client.sh" --dev
