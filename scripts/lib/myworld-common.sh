#!/usr/bin/env bash

MYWORLD_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="${ROOT_DIR:-$(cd "$MYWORLD_DIR/../.." && pwd)}"
ANT_HOME="${ANT_HOME:-$ROOT_DIR/Portable_Windows/apache-ant-1.10.5}"
ANT_BIN="${ANT_BIN:-$ANT_HOME/bin/ant}"
MYWORLD_GENERATOR_RUNNER="$ROOT_DIR/tools/generators/run-generators.py"

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
