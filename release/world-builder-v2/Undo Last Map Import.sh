#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_ROOT="$(cd "$ROOT_DIR/.." && pwd)"
WORKSPACE="$ROOT_DIR/workspace"
TOOLS_JAR="$ROOT_DIR/builder-runtime/launcher/world-builder-tools.jar"
RELEASE_IDENTITY="$ROOT_DIR/RELEASE-IDENTITY.json"
TERMINAL_SESSION="${WORLD_BUILDER_TERMINAL_SESSION:-0}"

fail() {
	printf 'Map undo could not start: %s\n' "$*" >&2
	exit 1
}

pause_terminal_session() {
	local status=$?
	trap - EXIT
	printf '\n'
	if [[ $status -eq 0 ]]; then
		printf 'Map undo finished.\n'
	else
		printf 'Map undo stopped with an error (exit %d).\n' "$status" >&2
	fi
	if [[ -t 0 ]]; then
		read -r -p "Press Enter to close this window..." _ || true
	fi
	exit "$status"
}

open_terminal_for_desktop_launch() {
	if [[ "$TERMINAL_SESSION" == "1" || "${WORLD_BUILDER_NO_TERMINAL:-0}" == "1" \
		|| -t 0 || -t 1 ]]; then
		return
	fi
	if [[ -z "${DISPLAY:-}" && -z "${WAYLAND_DISPLAY:-}" ]]; then
		return
	fi

	local script="$ROOT_DIR/Undo Last Map Import.sh"
	local -a command=(env WORLD_BUILDER_TERMINAL_SESSION=1 "$script")
	if command -v x-terminal-emulator >/dev/null 2>&1; then
		exec x-terminal-emulator -e "${command[@]}"
	elif command -v gnome-terminal >/dev/null 2>&1; then
		exec gnome-terminal --wait -- "${command[@]}"
	elif command -v konsole >/dev/null 2>&1; then
		exec konsole -e "${command[@]}"
	elif command -v xfce4-terminal >/dev/null 2>&1; then
		exec xfce4-terminal --disable-server -x "${command[@]}"
	elif command -v mate-terminal >/dev/null 2>&1; then
		exec mate-terminal --wait -- "${command[@]}"
	elif command -v xterm >/dev/null 2>&1; then
		exec xterm -e "${command[@]}"
	fi
	fail "No supported terminal application was found. Install a terminal or run this script from one."
}

if [[ "$TERMINAL_SESSION" == "1" ]]; then
	trap pause_terminal_session EXIT
fi
open_terminal_for_desktop_launch

if [[ -n "${WORLD_BUILDER_JAVA:-}" ]]; then
	JAVA_EXE="$WORLD_BUILDER_JAVA"
elif [[ -x "$ROOT_DIR/runtime/bin/java" ]]; then
	JAVA_EXE="$ROOT_DIR/runtime/bin/java"
else
	JAVA_EXE="$(command -v java || true)"
fi

[[ -n "$JAVA_EXE" ]] || fail "Java 17 or newer was not found."
[[ -f "$TOOLS_JAR" ]] || fail "The packaged launcher is missing."
[[ -f "$RELEASE_IDENTITY" ]] \
	|| fail "World Builder 2 release identity is missing."
grep -F '"productId": "rsc-world-editor-v2"' "$RELEASE_IDENTITY" >/dev/null \
	|| fail "This is not a World Builder 2 release."
[[ -f "$WORKSPACE/project-source.json" ]] || fail "No World Builder project was found."

"$JAVA_EXE" -jar "$TOOLS_JAR" undo-latest-import \
	--workspace "$WORKSPACE" \
	--target-root "$TARGET_ROOT"
