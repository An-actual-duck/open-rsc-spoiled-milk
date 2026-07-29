#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd "$(dirname "$0")" && pwd)
ROOT_DIR=$(CDPATH= cd "$SCRIPT_DIR/../.." && pwd)
ANT_HOME="$ROOT_DIR/tools/vendor/apache-ant-1.10.5"
ANT_BIN="$ANT_HOME/bin/ant"
DB_PATH="$ROOT_DIR/server/inc/sqlite/myworld_dev.db"
SEED_DB_PATH="$ROOT_DIR/server/inc/sqlite/myworld_seed.db"
CONF_PATH="$ROOT_DIR/server/myworld.conf"

echo "Spoiled Milk private server"
echo "Keep this window open while people are playing."
echo

if ! command -v java >/dev/null 2>&1; then
  echo "Java was not found. Install Java, then run this file again."
  exit 1
fi

if [ ! -f "$ANT_BIN" ]; then
  echo "Missing bundled Ant launcher: $ANT_BIN"
  exit 1
fi

SERVER_PORT=$(
  sed -n \
    's/^[[:space:]]*server_port:[[:space:]]*\([0-9][0-9]*\).*/\1/p' \
    "$CONF_PATH" \
    | head -n 1
)
if [ -z "$SERVER_PORT" ]; then
  echo "Could not read server_port from $CONF_PATH"
  exit 1
fi
if [ "$SERVER_PORT" = "43605" ]; then
  echo "Refusing to launch a private server/client pair on public port 43605."
  exit 1
fi

if [ ! -f "$DB_PATH" ]; then
  if [ ! -f "$SEED_DB_PATH" ]; then
    echo "Missing seed database: $SEED_DB_PATH"
    exit 1
  fi
  echo "Creating a fresh local save database..."
  cp "$SEED_DB_PATH" "$DB_PATH"
else
  echo "Using existing local save database."
fi

printf '%s\n' "localhost" > "$ROOT_DIR/Client_Base/Cache/ip.txt"
printf '%s\n' "$SERVER_PORT" > "$ROOT_DIR/Client_Base/Cache/port.txt"

echo "Building and starting the server..."
echo
cd "$ROOT_DIR/server"
ANT_HOME="$ANT_HOME" sh "$ANT_BIN" compile-and-run -DconfFile=myworld
