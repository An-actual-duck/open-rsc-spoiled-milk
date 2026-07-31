#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_ROOT="$(cd "$ROOT_DIR/.." && pwd)"
RUNTIME_ROOT="$ROOT_DIR/builder-runtime"
WORKSPACE="$ROOT_DIR/workspace"
TOOLS_JAR="$RUNTIME_ROOT/launcher/world-builder-tools.jar"
LAYERED_PACKAGE="$RUNTIME_ROOT/layered-world/package"
RELEASE_IDENTITY="$ROOT_DIR/RELEASE-IDENTITY.json"

fail() {
	printf 'World Builder could not start: %s\n' "$*" >&2
	exit 1
}

if [[ -n "${WORLD_BUILDER_JAVA:-}" ]]; then
	JAVA_EXE="$WORLD_BUILDER_JAVA"
elif [[ -x "$ROOT_DIR/runtime/bin/java" ]]; then
	JAVA_EXE="$ROOT_DIR/runtime/bin/java"
else
	JAVA_EXE="$(command -v java || true)"
fi

[[ -n "$JAVA_EXE" ]] || fail "Java 17 or newer was not found."
[[ -f "$TOOLS_JAR" ]] || fail "The packaged launcher is missing: $TOOLS_JAR"
[[ -f "$LAYERED_PACKAGE/manifest.json" ]] \
	|| fail "The packaged signed-layered map is missing."
[[ -f "$RELEASE_IDENTITY" ]] \
	|| fail "World Builder 2 release identity is missing."
grep -F '"productId": "rsc-world-editor-v2"' "$RELEASE_IDENTITY" >/dev/null \
	|| fail "This launcher is not inside a World Builder 2 release."
"$JAVA_EXE" -version >/dev/null 2>&1 || fail "Java could not be executed: $JAVA_EXE"

if [[ -f "$WORKSPACE/project-source.json" ]]; then
	[[ -f "$WORKSPACE/layered-review.json" ]] \
		|| fail "The existing workspace is legacy or unidentified; World Builder 2 will not open or migrate it."
	exec "$JAVA_EXE" -jar "$TOOLS_JAR" run --workspace "$WORKSPACE"
fi
if [[ -e "$WORKSPACE" ]]; then
	fail "The workspace folder exists but is incomplete. Preserve it and review its contents before retrying."
fi

PORT="${WORLD_BUILDER_PORT:-43615}"
[[ "$PORT" =~ ^[0-9]+$ ]] && ((PORT >= 1 && PORT < 65535)) \
	|| fail "WORLD_BUILDER_PORT must be between 1 and 65534."

exec "$JAVA_EXE" -jar "$TOOLS_JAR" launch \
	--server-root "$TARGET_ROOT" \
	--runtime-root "$RUNTIME_ROOT" \
	--workspace "$WORKSPACE" \
	--port "$PORT" \
	--config server/myworld.conf \
	--runtime-config server/myworld.conf \
	--layered-package "$LAYERED_PACKAGE" \
	--layered-profile spoiled-milk-replacement
