#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_DIR="${ROOT_DIR:-$SCRIPT_ROOT}"
if [[ ! -f "$ROOT_DIR/Client_Base/build.xml" || ! -d "$ROOT_DIR/scripts" ]]; then
  printf 'WARN: Ignoring invalid ROOT_DIR=%s; using %s\n' "$ROOT_DIR" "$SCRIPT_ROOT" >&2
  ROOT_DIR="$SCRIPT_ROOT"
fi
source "$SCRIPT_ROOT/scripts/lib/myworld-common.sh"

CLIENT_TARGET_MODE="live"
CLIENT_TARGET_HOST="localhost"
CLIENT_TARGET_PORT="$MYWORLD_PUBLIC_PORT"
RENDERER_DIAGNOSTICS=false
FRAME_CAPTURE_ENABLED=true
JFR_PROFILE_ENABLED=false

while (($#)); do
  case "$1" in
    --live)
      CLIENT_TARGET_MODE="live"
      CLIENT_TARGET_HOST="localhost"
      CLIENT_TARGET_PORT="$MYWORLD_PUBLIC_PORT"
      ;;
    --dev)
      CLIENT_TARGET_MODE="dev"
      CLIENT_TARGET_HOST="localhost"
      CLIENT_TARGET_PORT="$MYWORLD_DEV_PORT"
      ;;
    --cache)
      CLIENT_TARGET_MODE="cache"
      CLIENT_TARGET_HOST=""
      CLIENT_TARGET_PORT=""
      ;;
    --renderer-diagnostics)
      RENDERER_DIAGNOSTICS=true
      ;;
    --no-frame-capture)
      FRAME_CAPTURE_ENABLED=false
      ;;
    --jfr)
      JFR_PROFILE_ENABLED=true
      RENDERER_DIAGNOSTICS=true
      FRAME_CAPTURE_ENABLED=false
      ;;
    --target)
      [[ $# -ge 2 ]] || myworld_fail "--target requires host:port"
      CLIENT_TARGET_MODE="custom"
      CLIENT_TARGET_HOST="${2%:*}"
      CLIENT_TARGET_PORT="${2##*:}"
      [[ -n "$CLIENT_TARGET_HOST" && "$CLIENT_TARGET_HOST" != "$2" ]] || myworld_fail "--target requires host:port"
      [[ "$CLIENT_TARGET_PORT" =~ ^[0-9]+$ ]] || myworld_fail "--target port must be numeric"
      shift
      ;;
    *)
      myworld_fail "Unknown option: $1"
      ;;
  esac
  shift
done

if [[ ! -f "$ANT_BIN" ]]; then
  printf 'FAIL: Missing bundled Ant launcher: %s\n' "$ANT_BIN" >&2
  exit 1
fi

if [[ "$CLIENT_TARGET_MODE" != "cache" ]]; then
  mkdir -p "$ROOT_DIR/Client_Base/Cache"
  printf '%s\n' "$CLIENT_TARGET_HOST" > "$ROOT_DIR/Client_Base/Cache/ip.txt"
  printf '%s\n' "$CLIENT_TARGET_PORT" > "$ROOT_DIR/Client_Base/Cache/port.txt"
else
  CLIENT_TARGET_HOST="$(tr -d '\r\n' < "$ROOT_DIR/Client_Base/Cache/ip.txt" 2>/dev/null || true)"
  CLIENT_TARGET_PORT="$(tr -d '\r\n' < "$ROOT_DIR/Client_Base/Cache/port.txt" 2>/dev/null || true)"
fi

if [[ "$RENDERER_DIAGNOSTICS" == true ]]; then
  myworld_require_command python3
  RENDERER_DIAGNOSTIC_MAX_LOG_MB="${SPOILED_MILK_RENDERER_DIAGNOSTIC_MAX_LOG_MB:-256}"
  [[ "$RENDERER_DIAGNOSTIC_MAX_LOG_MB" =~ ^[0-9]+$ ]] \
    || myworld_fail "SPOILED_MILK_RENDERER_DIAGNOSTIC_MAX_LOG_MB must be numeric"
  ((RENDERER_DIAGNOSTIC_MAX_LOG_MB >= 1)) \
    || myworld_fail "SPOILED_MILK_RENDERER_DIAGNOSTIC_MAX_LOG_MB must be at least 1"
  RENDERER_DIAGNOSTIC_MAX_LOG_BYTES=$((RENDERER_DIAGNOSTIC_MAX_LOG_MB * 1024 * 1024))
  RENDERER_DIAGNOSTIC_SESSION_DIR="$ROOT_DIR/output/renderer-diagnostics/session-$(date +%Y%m%d-%H%M%S)-$$"
  mkdir -p "$RENDERER_DIAGNOSTIC_SESSION_DIR/captures"
  export SPOILED_MILK_RENDERER_DIAGNOSTICS=true
  export SPOILED_MILK_RENDERER_TELEMETRY=true
  export SPOILED_MILK_OPENGL_FRAME_CAPTURE="$FRAME_CAPTURE_ENABLED"
  export SPOILED_MILK_RENDERER_DIAGNOSTIC_SESSION_DIR="$RENDERER_DIAGNOSTIC_SESSION_DIR"
  export SPOILED_MILK_RENDERER_DIAGNOSTIC_MAX_LOG_BYTES="$RENDERER_DIAGNOSTIC_MAX_LOG_BYTES"
  export SPOILED_MILK_OPENGL_FRAME_CAPTURE_DIR="$RENDERER_DIAGNOSTIC_SESSION_DIR/captures"
  export SPOILED_MILK_CLIENT_LOG="$RENDERER_DIAGNOSTIC_SESSION_DIR/client-runtime.log"
  export SPOILED_MILK_CLIENT_BRANCH="$(myworld_git_branch)"
  export SPOILED_MILK_CLIENT_COMMIT="$(myworld_git_commit)"
  export SPOILED_MILK_CLIENT_TARGET_MODE="$CLIENT_TARGET_MODE"
  if [[ "$JFR_PROFILE_ENABLED" == true ]]; then
    CLIENT_JFR_PATH="$RENDERER_DIAGNOSTIC_SESSION_DIR/client-profile.jfr"
    export SPOILED_MILK_CLIENT_JFR_PATH="$CLIENT_JFR_PATH"
    CLIENT_JVM_ARGS="-XX:+UnlockCommercialFeatures -XX:+FlightRecorder -XX:StartFlightRecording=settings=profile,filename=$CLIENT_JFR_PATH,dumponexit=true,maxsize=128m,maxage=30m"
  else
    CLIENT_JVM_ARGS=""
  fi
fi

printf '\n============================================================\n' >&2
case "$CLIENT_TARGET_MODE" in
  live)
    printf 'CLIENT TARGET: LIVE SPOILED MILK HOSTED ALPHA\n' >&2
    ;;
  dev)
    printf 'CLIENT TARGET: PRIVATE SPOILED MILK DEV SERVER\n' >&2
    ;;
  cache)
    printf 'CLIENT TARGET: EXISTING CACHE VALUES\n' >&2
    ;;
  custom)
    printf 'CLIENT TARGET: CUSTOM SERVER\n' >&2
    ;;
esac
printf 'Worktree: %s\n' "$(myworld_realpath "$ROOT_DIR" 2>/dev/null || printf '%s\n' "$ROOT_DIR")" >&2
printf 'Branch:   %s\n' "$(myworld_git_branch)" >&2
printf 'Commit:   %s\n' "$(myworld_git_commit)" >&2
printf 'Endpoint: %s:%s\n' "${CLIENT_TARGET_HOST:-unknown}" "${CLIENT_TARGET_PORT:-unknown}" >&2
if [[ "$RENDERER_DIAGNOSTICS" == true ]]; then
  printf 'Diagnostics: %s\n' "$RENDERER_DIAGNOSTIC_SESSION_DIR" >&2
  printf 'Frame capture: %s\n' "$FRAME_CAPTURE_ENABLED" >&2
  printf 'JFR profile: %s\n' "$JFR_PROFILE_ENABLED" >&2
fi
printf '============================================================\n\n' >&2

run_client() {
  (
    cd "$ROOT_DIR/Client_Base"
    ANT_HOME="$ANT_HOME" sh "$ANT_BIN" compile-and-run \
      -Dclient.jvmargs="${CLIENT_JVM_ARGS:-}"
  )
}

if [[ "$RENDERER_DIAGNOSTICS" == true ]]; then
  run_client 2>&1 \
    | python3 -u "$SCRIPT_ROOT/scripts/bounded-log-tee.py" \
        "$RENDERER_DIAGNOSTIC_SESSION_DIR/console.log" \
        "$RENDERER_DIAGNOSTIC_MAX_LOG_BYTES"
else
  run_client
fi
