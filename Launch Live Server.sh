#!/usr/bin/env bash
set -euo pipefail

LIVE_ROOT="/tmp/spoiled-milk-live-main"

export MYWORLD_LIVE_ROOT="$LIVE_ROOT"

read -r -d '' SERVER_COMMAND <<'EOF' || true
set -uo pipefail

LIVE_ROOT="/tmp/spoiled-milk-live-main"
COMMAND_FILE="${SM_LIVE_LAUNCH_COMMAND_FILE:-}"

pause_and_exit() {
  local status="$1"
  if [[ -n "$COMMAND_FILE" && -f "$COMMAND_FILE" ]]; then
    rm -f "$COMMAND_FILE"
  fi
  printf '\nPress Enter to close this terminal...'
  read -r _
  exit "$status"
}

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  pause_and_exit 1
}

cd "$LIVE_ROOT" || fail "live worktree not found: $LIVE_ROOT"

printf 'Spoiled Milk live server launcher\n'
printf 'Worktree: %s\n\n' "$PWD"

git rev-parse --is-inside-work-tree >/dev/null 2>&1 \
  || fail "live server must be launched from a git worktree"

printf '\nCurrent live server status:\n'
./scripts/live-status.sh || true

printf 'Starting the already-deployed detached hosted server...\n\n'
RELEASE_TAG="$(sed -n 's/^marker_release_tag=\(v[0-9]*\.[0-9]*\.[0-9]*\)$/\1/p' server/run/server-43605.env 2>/dev/null || true)"
if [[ -n "$RELEASE_TAG" ]]; then
  bash /home/justin/Core-Framework/scripts/run-hosted-release.sh "$RELEASE_TAG"
else
  ./scripts/run-hosted-server.sh
fi
status=$?

printf '\nHosted server process exited with status %s.\n' "$status"
pause_and_exit "$status"
EOF

COMMAND_FILE="$(mktemp "${TMPDIR:-/tmp}/spoiled-milk-live-launch.XXXXXX.sh")"
printf '%s\n' "$SERVER_COMMAND" > "$COMMAND_FILE"
chmod 700 "$COMMAND_FILE"
export SM_LIVE_LAUNCH_COMMAND_FILE="$COMMAND_FILE"

if command -v gnome-terminal >/dev/null 2>&1; then
  exec gnome-terminal --title="Spoiled Milk Live Server" -- bash "$COMMAND_FILE"
elif command -v x-terminal-emulator >/dev/null 2>&1; then
  exec x-terminal-emulator -e bash "$COMMAND_FILE"
elif command -v xfce4-terminal >/dev/null 2>&1; then
  exec xfce4-terminal --title="Spoiled Milk Live Server" --command="bash $COMMAND_FILE"
elif command -v konsole >/dev/null 2>&1; then
  exec konsole --new-tab -p tabtitle="Spoiled Milk Live Server" -e bash "$COMMAND_FILE"
elif command -v xterm >/dev/null 2>&1; then
  exec xterm -T "Spoiled Milk Live Server" -e bash "$COMMAND_FILE"
fi

printf 'No graphical terminal launcher was found. Running in this shell.\n\n'
exec bash "$COMMAND_FILE"
