#!/usr/bin/env bash
# Manager-owned explicit published-tag launch; never launch from development.
set -euo pipefail
MANAGER_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ "$MANAGER_ROOT" == /home/justin/Core-Framework ]] || exit 1
[[ $# == 1 ]] || { echo 'Usage: run-hosted-release.sh VERSION' >&2; exit 1; }
export MYWORLD_HOSTED_RELEASE_TAG="$1"
ROOT_DIR=/tmp/spoiled-milk-live-main
source "$MANAGER_ROOT/scripts/lib/myworld-common.sh"
myworld_load_local_env
myworld_require_safe_hosted_launch
myworld_require_hosted_conf myworld-host
myworld_require_port_free "$(myworld_conf_value myworld-host server_port)"
HOSTED_LAYERED_PACKAGE="$(layered_world_require_live_package "$ROOT_DIR" "$MYWORLD_LIVE_DB_ROOT")"
export OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE_PATH="$HOSTED_LAYERED_PACKAGE"
export OPENRSC_LAYERED_NATIVE_TERRAIN_MANIFEST_SHA256="$SPOILED_MILK_LAYERED_MANIFEST_SHA256"
export SPOILED_MILK_LAYERED_RUNTIME_MODE=production
myworld_prepare_generated_artifacts check
myworld_require_safe_hosted_launch
myworld_require_live_database_link "$ROOT_DIR"
myworld_print_server_launch_banner 'LIVE SPOILED MILK HOSTED RELEASE' myworld-host
myworld_write_launch_marker 'LIVE SPOILED MILK HOSTED RELEASE' myworld-host verified-live
printf 'marker_release_tag=%q\n' "$MYWORLD_HOSTED_RELEASE_TAG" >> "$(myworld_launch_marker_path myworld-host)"
cd "$ROOT_DIR"
myworld_ant_server compile-and-run -DconfFile=myworld-host
