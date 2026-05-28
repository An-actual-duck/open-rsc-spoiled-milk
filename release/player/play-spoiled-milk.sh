#!/usr/bin/env sh
set -eu

GAME_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
exec java -jar "$GAME_DIR/Spoiled_Milk_Client.jar"
