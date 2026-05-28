#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_DIR="${ROOT_DIR:-$SCRIPT_ROOT}"
source "$SCRIPT_ROOT/scripts/lib/myworld-common.sh"

GENERATOR_MODE="$(myworld_resolve_generator_mode "$@")"

myworld_prepare_generated_artifacts "$GENERATOR_MODE"
myworld_require_java_17_for_zgc
myworld_ant_server runserverzgc -DconfFile=myworld
