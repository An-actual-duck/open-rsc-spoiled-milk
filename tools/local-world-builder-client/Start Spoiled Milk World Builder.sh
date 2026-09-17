#!/usr/bin/env bash
set -euo pipefail
APP_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PATCH_ROOT="$APP_ROOT/local-spoiled-milk-client"
fail() { printf 'Local World Builder presentation refused: %s\n' "$*" >&2; exit 1; }

[[ -z "${JAVA_TOOL_OPTIONS:-}${JDK_JAVA_OPTIONS:-}${_JAVA_OPTIONS:-}" ]] \
  || fail 'Existing Java-wide options could change the isolated launch. Use a clean shell.'
[[ ! -e "$APP_ROOT/.world-builder-v2-update.lock" ]] || fail 'An application update is in progress.'
[[ -f "$PATCH_ROOT/agent.jar" && ! -L "$PATCH_ROOT/agent.jar" ]] || fail 'Local adapter is missing or linked.'
[[ "$PATCH_ROOT" != *'"'* && "$PATCH_ROOT" != *$'\n'* ]] || fail 'Unsupported installation path.'
(
  cd "$APP_ROOT"
  sha256sum --status -c "$PATCH_ROOT/application-pin.sha256"
) || fail 'Application version changed; this one-off adapter needs review before reuse.'
(
  cd "$PATCH_ROOT"
  sha256sum --status -c bundle.sha256
) || fail 'Local adapter files changed.'

# This only affects the launcher and its children. The agent is inert except in
# the exact hashed adaptive loopback client. Existing manifests remain authentic.
export JAVA_TOOL_OPTIONS="-javaagent:\"$PATCH_ROOT/agent.jar\""
export WORLD_BUILDER_SKIP_UPDATE=1
exec bash "$APP_ROOT/Start World Builder.sh" "$@"
