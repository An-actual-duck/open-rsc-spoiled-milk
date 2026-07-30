#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_DIR="${ROOT_DIR:-$SCRIPT_ROOT}"
source "$SCRIPT_ROOT/scripts/lib/myworld-common.sh"

myworld_load_local_env

GENERATOR_MODE="check"
LAYERED_PRODUCTION_PROFILE=false
while (($#)); do
  case "$1" in
    --sync-generated)
      GENERATOR_MODE="sync"
      ;;
    --layered-production)
      LAYERED_PRODUCTION_PROFILE=true
      ;;
    *)
      myworld_fail "Unknown option: $1"
      ;;
  esac
  shift
done
SERVER_CONF="myworld"

myworld_require_private_dev_conf "$SERVER_CONF"
myworld_require_port_free "$(myworld_conf_value "$SERVER_CONF" server_port)"
if [[ "$LAYERED_PRODUCTION_PROFILE" == true ]]; then
  PRIVATE_LAYERED_WORKSPACE="$ROOT_DIR/tools/layered-maps/workspace/private-production"
  PRIVATE_LAYERED_PACKAGE="$(layered_world_generate_package \
    "$ROOT_DIR" "$PRIVATE_LAYERED_WORKSPACE")"
  layered_world_enable_private_production_profile "$PRIVATE_LAYERED_PACKAGE"
fi
myworld_print_server_launch_banner "PRIVATE SPOILED MILK DEV SERVER - NOT PUBLIC HOSTED ALPHA" "$SERVER_CONF"
if [[ "$LAYERED_PRODUCTION_PROFILE" == true ]]; then
  printf 'Layered profile: spoiled-milk-replacement\n' >&2
  printf 'Layered world: %s\n' "$PRIVATE_LAYERED_PACKAGE" >&2
  printf 'Layered manifest: %s\n' "$SPOILED_MILK_LAYERED_MANIFEST_SHA256" >&2
fi
myworld_prepare_generated_artifacts "$GENERATOR_MODE"
myworld_write_launch_marker "PRIVATE SPOILED MILK DEV SERVER - NOT PUBLIC HOSTED ALPHA" "$SERVER_CONF"
myworld_ant_server compile-and-run -DconfFile=myworld
