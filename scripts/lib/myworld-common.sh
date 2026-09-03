#!/usr/bin/env bash

MYWORLD_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MYWORLD_INFERRED_ROOT="$(cd "$MYWORLD_DIR/../.." && pwd)"
ROOT_DIR="${ROOT_DIR:-$MYWORLD_INFERRED_ROOT}"
if [[ ! -f "$ROOT_DIR/server/build.xml" || ! -d "$ROOT_DIR/scripts" ]]; then
  printf 'WARN: Ignoring invalid ROOT_DIR=%s; using %s\n' "$ROOT_DIR" "$MYWORLD_INFERRED_ROOT" >&2
  ROOT_DIR="$MYWORLD_INFERRED_ROOT"
fi
ANT_HOME="${ANT_HOME:-$ROOT_DIR/tools/vendor/apache-ant-1.10.5}"
ANT_BIN="${ANT_BIN:-$ANT_HOME/bin/ant}"
MYWORLD_GENERATOR_RUNNER="$ROOT_DIR/tools/generators/run-generators.py"
# The public server path is an identity boundary, not a convenience setting.
# Keeping it fixed prevents an inherited environment variable from blessing a
# development checkout as the live server. The declaration guard keeps this
# shared library safe to source more than once in an interactive shell.
if [[ "$(declare -p MYWORLD_LIVE_ROOT 2>/dev/null || true)" != declare\ -r* ]]; then
  MYWORLD_LIVE_ROOT="/tmp/spoiled-milk-live-main"
  readonly MYWORLD_LIVE_ROOT
fi
MYWORLD_LIVE_DB_ROOT="${MYWORLD_LIVE_DB_ROOT:-${HOME}/.local/share/spoiled-milk/live}"
if [[ "$(declare -p MYWORLD_LIVE_DB_NAME 2>/dev/null || true)" != declare\ -r* ]]; then
  MYWORLD_LIVE_DB_NAME="spoiled_milk_alpha.db"
  readonly MYWORLD_LIVE_DB_NAME
fi
MYWORLD_PUBLIC_PORT="${MYWORLD_PUBLIC_PORT:-43605}"
MYWORLD_DEV_PORT="${MYWORLD_DEV_PORT:-43615}"
# shellcheck source=scripts/lib/layered-world-package.sh
source "$MYWORLD_DIR/layered-world-package.sh"

myworld_fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

myworld_require_command() {
  local command_name="$1"
  command -v "$command_name" >/dev/null 2>&1 || myworld_fail "Missing required dependency: $command_name"
}

myworld_java_major_version() {
  local version_line raw_version major_version

  myworld_require_command java
  version_line="$(java -version 2>&1 | head -n 1)"
  raw_version="$(sed -n 's/.*version "\([^"]*\)".*/\1/p' <<<"$version_line")"
  [[ -n "$raw_version" ]] || myworld_fail "Unable to determine Java version from: $version_line"

  if [[ "$raw_version" == 1.* ]]; then
    major_version="${raw_version#1.}"
    major_version="${major_version%%.*}"
  else
    major_version="${raw_version%%.*}"
  fi

  [[ "$major_version" =~ ^[0-9]+$ ]] || myworld_fail "Unable to parse Java major version from: $raw_version"
  printf '%s\n' "$major_version"
}

myworld_require_ant() {
  [[ -f "$ANT_BIN" ]] || myworld_fail "Missing bundled Ant launcher: $ANT_BIN"
}

myworld_require_seed_db() {
  local source_db_path="$ROOT_DIR/server/inc/sqlite/myworld_seed.db"
  [[ -f "$source_db_path" ]] || myworld_fail "Missing MyWorld seed database: $source_db_path"
}

myworld_require_java_17_for_zgc() {
  local java_major_version
  java_major_version="$(myworld_java_major_version)"
  (( java_major_version >= 17 )) || myworld_fail "run-server-zgc.sh requires Java 17+; found Java $java_major_version"
}

myworld_load_local_env() {
  local env_file="${MYWORLD_LOCAL_ENV_FILE:-$ROOT_DIR/server/local.env}"

  if [[ -f "$env_file" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$env_file"
    set +a
  fi
}

myworld_resolve_generator_mode() {
  local mode="check"

  while (($#)); do
    case "$1" in
      --sync-generated)
        mode="sync"
        ;;
      *)
        myworld_fail "Unknown option: $1"
        ;;
    esac
    shift
  done

  printf '%s\n' "$mode"
}

myworld_git_commit() {
  git -C "$ROOT_DIR" rev-parse HEAD 2>/dev/null || true
}

myworld_git_branch() {
  git -C "$ROOT_DIR" rev-parse --abbrev-ref HEAD 2>/dev/null || true
}

myworld_git_main_commit() {
  myworld_git_published_main_commit "$ROOT_DIR"
}

myworld_git_published_main_commit() {
  local root="${1:-$ROOT_DIR}"

  git -C "$root" rev-parse --verify --quiet 'spoiled-milk/main^{commit}'
}

myworld_git_commit_publication_state() {
  local root="$1"
  local commit="$2"
  local main_commit

  main_commit="$(myworld_git_published_main_commit "$root" 2>/dev/null || true)"
  if [[ -z "$main_commit" ]] || ! git -C "$root" rev-parse --verify --quiet "$commit^{commit}" >/dev/null; then
    printf 'unknown\n'
  elif [[ "$commit" == "$main_commit" ]]; then
    printf 'current\n'
  elif git -C "$root" merge-base --is-ancestor "$commit" "$main_commit" 2>/dev/null; then
    printf 'previous\n'
  else
    printf 'unpublished\n'
  fi
}

myworld_git_dirty_status() {
  local root="${1:-$ROOT_DIR}"

  git -C "$root" status --porcelain --untracked-files=all
}

myworld_live_database_path() {
  local root="${1:-$ROOT_DIR}"

  printf '%s/server/inc/sqlite/%s\n' "$root" "$MYWORLD_LIVE_DB_NAME"
}

myworld_external_live_database_path() {
  printf '%s/%s\n' "$MYWORLD_LIVE_DB_ROOT" "$MYWORLD_LIVE_DB_NAME"
}

myworld_require_live_database_link() {
  local root="${1:-$ROOT_DIR}"
  local checkout_db external_db checkout_real external_real

  checkout_db="$(myworld_live_database_path "$root")"
  external_db="$(myworld_external_live_database_path)"

  [[ -f "$external_db" ]] \
    || myworld_fail "External live database is missing: $external_db"
  [[ -L "$checkout_db" ]] \
    || myworld_fail "Live database path must be a symlink to the external database: $checkout_db"

  checkout_real="$(readlink -f "$checkout_db" 2>/dev/null || true)"
  external_real="$(readlink -f "$external_db" 2>/dev/null || true)"
  [[ -n "$checkout_real" && "$checkout_real" == "$external_real" ]] \
    || myworld_fail "Live database link points to '$checkout_real'; expected '$external_real'."
}

myworld_realpath() {
  local path="$1"
  (cd "$path" 2>/dev/null && pwd -P)
}

myworld_conf_path() {
  local conf_name="$1"
  [[ "$conf_name" == *.conf ]] || conf_name="${conf_name}.conf"
  printf '%s/server/%s\n' "$ROOT_DIR" "$conf_name"
}

myworld_conf_value() {
  local conf_name="$1"
  local key="$2"
  local conf_path

  conf_path="$(myworld_conf_path "$conf_name")"
  [[ -f "$conf_path" ]] || myworld_fail "Missing server config: $conf_path"

  awk -v key="$key" '
    /^[[:space:]]*#/ { next }
    {
      line = $0
      sub(/[[:space:]]+#.*/, "", line)
      if (line ~ "^[[:space:]]*" key "[[:space:]]*:") {
        sub(/^[^:]*:[[:space:]]*/, "", line)
        sub(/[[:space:]]*$/, "", line)
        print line
        exit
      }
    }
  ' "$conf_path"
}

myworld_require_conf_value() {
  local conf_name="$1"
  local key="$2"
  local expected="$3"
  local actual

  actual="$(myworld_conf_value "$conf_name" "$key")"
  [[ "$actual" == "$expected" ]] \
    || myworld_fail "$(myworld_conf_path "$conf_name") expected $key=$expected, found $actual"
}

myworld_listener_pids_for_port() {
  local port="$1"
  local line pid

  command -v ss >/dev/null 2>&1 || return 0

  while IFS= read -r line; do
    [[ "$line" == LISTEN* ]] || continue
    [[ "$line" == *":$port "* || "$line" == *":$port" ]] || continue
    [[ "$line" == *"pid="* ]] || continue
    pid="${line#*pid=}"
    pid="${pid%%,*}"
    [[ "$pid" =~ ^[0-9]+$ ]] && printf '%s\n' "$pid"
  done < <(ss -ltnp 2>/dev/null)
}

myworld_listener_summary_for_port() {
  local port="$1"
  local line

  command -v ss >/dev/null 2>&1 || return 0

  while IFS= read -r line; do
    [[ "$line" == LISTEN* ]] || continue
    [[ "$line" == *":$port "* || "$line" == *":$port" ]] || continue
    printf '%s\n' "$line"
  done < <(ss -ltnp 2>/dev/null)
}

myworld_require_port_free() {
  local port="$1"
  local summary

  summary="$(myworld_listener_summary_for_port "$port")"
  [[ -z "$summary" ]] || myworld_fail "Port $port is already in use:
$summary"
}

myworld_process_args() {
  local pid="$1"
  ps -p "$pid" -o args= 2>/dev/null || true
}

myworld_server_pids_for_root() {
  local root="$1"
  local root_real pid args

  root_real="$(myworld_realpath "$root" 2>/dev/null || printf '%s\n' "$root")"
  while read -r pid args; do
    [[ "$pid" =~ ^[0-9]+$ ]] || continue
    [[ "$args" == *"$root_real/server/core.jar"* ]] || continue
    printf '%s\n' "$pid"
  done < <(ps -eo pid=,args= 2>/dev/null)
}

myworld_process_database_targets() {
  local pid="$1"
  local fd target

  for fd in "/proc/$pid/fd/"*; do
    [[ -e "$fd" || -L "$fd" ]] || continue
    target="$(readlink "$fd" 2>/dev/null || true)"
    [[ "$target" == *"/$MYWORLD_LIVE_DB_NAME" || "$target" == *"/$MYWORLD_LIVE_DB_NAME (deleted)" ]] \
      || continue
    printf '%s\n' "$target"
  done
}

myworld_process_uses_only_external_live_database() {
  local pid="$1"
  local expected targets target count=0

  expected="$(readlink -f "$(myworld_external_live_database_path)" 2>/dev/null || true)"
  [[ -n "$expected" ]] || return 1
  targets="$(myworld_process_database_targets "$pid")"
  [[ -n "$targets" ]] || return 1

  while IFS= read -r target; do
    [[ -n "$target" ]] || continue
    count=$((count + 1))
    [[ "$target" == "$expected" ]] || return 1
  done <<<"$targets"
  ((count > 0))
}

myworld_server_root_for_pid() {
  local pid="$1"
  local args root

  args="$(myworld_process_args "$pid")"
  if [[ "$args" =~ ([^[:space:]:]+/server/core\.jar) ]]; then
    root="${BASH_REMATCH[1]%/server/core.jar}"
    myworld_realpath "$root" 2>/dev/null || printf '%s\n' "$root"
  fi
}

myworld_server_conf_for_pid() {
  local pid="$1"
  local args word last_conf=""

  args="$(myworld_process_args "$pid")"
  for word in $args; do
    if [[ "$word" == *.conf ]]; then
      last_conf="$word"
    fi
  done

  printf '%s\n' "$last_conf"
}

myworld_print_server_launch_banner() {
  local label="$1"
  local conf_name="$2"
  local branch commit db_name server_name bind_address server_port ws_port root_real

  root_real="$(myworld_realpath "$ROOT_DIR" 2>/dev/null || printf '%s\n' "$ROOT_DIR")"
  branch="$(myworld_git_branch)"
  commit="$(myworld_git_commit)"
  db_name="$(myworld_conf_value "$conf_name" db_name)"
  server_name="$(myworld_conf_value "$conf_name" server_name)"
  bind_address="$(myworld_conf_value "$conf_name" server_bind_address)"
  server_port="$(myworld_conf_value "$conf_name" server_port)"
  ws_port="$(myworld_conf_value "$conf_name" ws_server_port)"

  printf '\n============================================================\n' >&2
  printf '%s\n' "$label" >&2
  printf 'Worktree: %s\n' "$root_real" >&2
  printf 'Branch:   %s\n' "${branch:-unknown}" >&2
  printf 'Commit:   %s\n' "${commit:-unknown}" >&2
  printf 'Config:   %s\n' "$(myworld_conf_path "$conf_name")" >&2
  printf 'DB:       %s\n' "$db_name" >&2
  printf 'Server:   %s\n' "$server_name" >&2
  printf 'Bind:     %s:%s\n' "$bind_address" "$server_port" >&2
  printf 'WS Port:  %s\n' "$ws_port" >&2
  printf '============================================================\n\n' >&2
}

myworld_launch_marker_path() {
  local conf_name="$1"
  local server_port

  server_port="$(myworld_conf_value "$conf_name" server_port)"
  printf '%s/server/run/server-%s.env\n' "$ROOT_DIR" "$server_port"
}

myworld_write_launch_marker() {
  local label="$1"
  local conf_name="$2"
  local safety_attestation="${3:-unverified}"
  local marker_path marker_dir root_real branch commit db_name server_name bind_address server_port ws_port
  local database_path database_real layered_package_path layered_manifest_sha256 layered_runtime_mode

  marker_path="$(myworld_launch_marker_path "$conf_name")"
  marker_dir="$(dirname "$marker_path")"
  mkdir -p "$marker_dir"

  root_real="$(myworld_realpath "$ROOT_DIR" 2>/dev/null || printf '%s\n' "$ROOT_DIR")"
  branch="$(myworld_git_branch)"
  commit="$(myworld_git_commit)"
  db_name="$(myworld_conf_value "$conf_name" db_name)"
  server_name="$(myworld_conf_value "$conf_name" server_name)"
  bind_address="$(myworld_conf_value "$conf_name" server_bind_address)"
  server_port="$(myworld_conf_value "$conf_name" server_port)"
  ws_port="$(myworld_conf_value "$conf_name" ws_server_port)"
  database_path="$ROOT_DIR/server/inc/sqlite/${db_name}.db"
  database_real="$(readlink -f "$database_path" 2>/dev/null || true)"
  layered_package_path="${OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE_PATH:-}"
  layered_manifest_sha256=""
  layered_runtime_mode="${SPOILED_MILK_LAYERED_RUNTIME_MODE:-legacy-default}"
  if [[ -n "$layered_package_path" && -f "$layered_package_path/manifest.json" ]]; then
    layered_package_path="$(myworld_realpath "$layered_package_path" 2>/dev/null || true)"
    layered_manifest_sha256="$(layered_world_manifest_sha256 "$layered_package_path")"
  fi

  {
    printf 'marker_label=%q\n' "$label"
    printf 'marker_root=%q\n' "$root_real"
    printf 'marker_branch=%q\n' "$branch"
    printf 'marker_commit=%q\n' "$commit"
    printf 'marker_config=%q\n' "$(basename "$(myworld_conf_path "$conf_name")")"
    printf 'marker_db=%q\n' "$db_name"
    printf 'marker_db_path=%q\n' "$database_real"
    printf 'marker_layered_package_path=%q\n' "$layered_package_path"
    printf 'marker_layered_manifest_sha256=%q\n' "$layered_manifest_sha256"
    printf 'marker_layered_runtime_mode=%q\n' "$layered_runtime_mode"
    printf 'marker_safety=%q\n' "$safety_attestation"
    printf 'marker_server=%q\n' "$server_name"
    printf 'marker_bind=%q\n' "$bind_address"
    printf 'marker_port=%q\n' "$server_port"
    printf 'marker_ws_port=%q\n' "$ws_port"
    printf 'marker_launcher_pid=%q\n' "$$"
    printf 'marker_started_at=%q\n' "$(date -Is 2>/dev/null || date)"
  } > "$marker_path"
}

myworld_require_hosted_conf() {
  local conf_name="$1"

  myworld_require_conf_value "$conf_name" db_name spoiled_milk_alpha
  myworld_require_conf_value "$conf_name" server_name "Spoiled Milk"
  myworld_require_conf_value "$conf_name" server_bind_address "0.0.0.0"
  myworld_require_conf_value "$conf_name" server_port "$MYWORLD_PUBLIC_PORT"
  myworld_require_conf_value "$conf_name" want_layered_player_location_authority true
  myworld_require_conf_value "$conf_name" want_layered_spatial_runtime_authority true
  myworld_require_conf_value "$conf_name" want_layered_protocol_client_authority true
  myworld_require_conf_value "$conf_name" want_layered_synthetic_deep_fixture false
  myworld_require_conf_value "$conf_name" want_layered_native_terrain_package true
  myworld_require_conf_value "$conf_name" want_layered_native_terrain_residency true
  myworld_require_conf_value "$conf_name" want_layered_native_terrain_readiness true
  myworld_require_conf_value "$conf_name" want_layered_native_terrain_prediction true
  myworld_require_conf_value "$conf_name" want_layered_native_terrain_symmetric_residency true
  myworld_require_conf_value "$conf_name" want_layered_native_terrain_atomic_activation true
  myworld_require_conf_value "$conf_name" want_sync_scene_baseline true
  myworld_require_conf_value "$conf_name" layered_native_world_runtime_profile world-builder-installed
}

myworld_require_private_dev_conf() {
  local conf_name="$1"
  local db_name bind_address server_port

  db_name="$(myworld_conf_value "$conf_name" db_name)"
  bind_address="$(myworld_conf_value "$conf_name" server_bind_address)"
  server_port="$(myworld_conf_value "$conf_name" server_port)"

  [[ "$db_name" != "spoiled_milk_alpha" ]] \
    || myworld_fail "Private dev server config must not use the hosted alpha database."
  [[ "$bind_address" == "127.0.0.1" || "$bind_address" == "localhost" || "$bind_address" == "::1" ]] \
    || myworld_fail "Private dev server must bind only to localhost; found $bind_address."
  [[ "$server_port" != "$MYWORLD_PUBLIC_PORT" ]] \
    || myworld_fail "Private dev server must not bind public player port $MYWORLD_PUBLIC_PORT."
}

myworld_require_safe_hosted_launch() {
  local branch dirty_status head_commit main_commit root_real live_root_real

  git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
    || myworld_fail "Hosted server must be started from the fixed live git worktree."

  root_real="$(myworld_realpath "$ROOT_DIR" 2>/dev/null || true)"
  live_root_real="$(myworld_realpath "$MYWORLD_LIVE_ROOT" 2>/dev/null || true)"
  [[ -n "$live_root_real" ]] || myworld_fail "Configured live worktree does not exist: $MYWORLD_LIVE_ROOT"
  [[ "$root_real" == "$live_root_real" ]] \
    || myworld_fail "Refusing hosted server launch from '$root_real'. Public hosted server must run from '$live_root_real'."

  branch="$(myworld_git_branch)"
  [[ "$branch" == "HEAD" ]] || myworld_fail "Refusing hosted server launch from attached branch '$branch'. The public live checkout must use detached HEAD. Run scripts/deploy-live-main.sh first."

  dirty_status="$(myworld_git_dirty_status "$ROOT_DIR")"
  [[ -z "$dirty_status" ]] || myworld_fail "Refusing hosted server launch from a dirty worktree, including untracked files. Clean the fixed live checkout with scripts/deploy-live-main.sh first."

  head_commit="$(myworld_git_commit)"
  main_commit="$(myworld_git_main_commit || true)"
  [[ -n "$main_commit" ]] || myworld_fail "Unable to resolve spoiled-milk/main. Publish or fetch it before starting the hosted server."
  [[ "$head_commit" == "$main_commit" ]] || myworld_fail "Refusing hosted server launch from commit $head_commit; expected published main $main_commit."

  printf 'Hosted launch safety: clean detached published main at %s\n' "$head_commit" >&2
}

myworld_prepare_generated_artifacts() {
  local mode="${1:-check}"
  local -a command=(python3 "$MYWORLD_GENERATOR_RUNNER")

  myworld_require_command python3

  case "$mode" in
    check)
      command+=(--check)
      ;;
    sync)
      ;;
    *)
      myworld_fail "Unknown generator mode: $mode"
      ;;
  esac

  (
    cd "$ROOT_DIR"
    "${command[@]}"
  )
}

myworld_ant_build() {
  local target="$1"
  shift || true

  myworld_require_ant
  ANT_HOME="$ANT_HOME" sh "$ANT_BIN" -f "$ROOT_DIR/server/build.xml" "$target" "$@"
}

myworld_ant_server() {
  local target="$1"
  shift || true

  myworld_require_ant
  (
    cd "$ROOT_DIR/server"
    ANT_HOME="$ANT_HOME" sh "$ANT_BIN" "$target" "$@"
  )
}
