#!/usr/bin/env bash

# Exact reviewed Spoiled Milk layered-world product. Keep these values aligned
# with NativeLayeredWorldRuntimeProfile and the generator documentation. Every
# release, private production-profile run, and live deployment validates this
# identity before the server can consume the package.
SPOILED_MILK_LAYERED_PACKAGE_ID="rsc-remastered.spoiled-milk-layered-world"
SPOILED_MILK_LAYERED_PACKAGE_VERSION="0.5.0"
SPOILED_MILK_LAYERED_MANIFEST_SHA256="f5a79233700fa753a010e21bb5f697977c44d5385715b4d7cb69b2d0770280ae"
SPOILED_MILK_LAYERED_PACKAGE_FINGERPRINT="16c2e77304a1d7ab49d41faaa9d6495cfba9af8d64fcb5bde034a69729f3c6fd"
SPOILED_MILK_LAYERED_TERRAIN_SECTORS="1782"
SPOILED_MILK_LAYERED_PLACEMENTS="33526"

layered_world_fail() {
  printf 'FAIL: %s\n' "$*" >&2
  return 1
}

layered_world_manifest_sha256() {
  local package_root="$1"

  [[ -f "$package_root/manifest.json" ]] \
    || layered_world_fail "Layered-world package has no manifest: $package_root"
  sha256sum "$package_root/manifest.json" | awk '{print $1}'
}

layered_world_validate_package() {
  local repository_root="$1"
  local package_root="$2"
  local validation_workspace="$3"
  local manifest_sha validation_json

  for command_name in java javac python3 sha256sum; do
    command -v "$command_name" >/dev/null 2>&1 \
      || layered_world_fail "Missing layered-world dependency: $command_name"
  done

  manifest_sha="$(layered_world_manifest_sha256 "$package_root")"
  [[ "$manifest_sha" == "$SPOILED_MILK_LAYERED_MANIFEST_SHA256" ]] \
    || layered_world_fail "Layered-world manifest is $manifest_sha; expected $SPOILED_MILK_LAYERED_MANIFEST_SHA256"

  LAYERED_MAPS_WORKSPACE="$validation_workspace" \
    "$repository_root/tools/layered-maps/layered-maps.sh" \
      package-check --package "$package_root" >&2
  validation_json="$validation_workspace/package-validation.json"
  [[ -f "$validation_json" ]] \
    || layered_world_fail "Layered-world validator did not create $validation_json"

  python3 - \
    "$validation_json" \
    "$SPOILED_MILK_LAYERED_PACKAGE_ID" \
    "$SPOILED_MILK_LAYERED_PACKAGE_VERSION" \
    "$SPOILED_MILK_LAYERED_PACKAGE_FINGERPRINT" \
    "$SPOILED_MILK_LAYERED_TERRAIN_SECTORS" \
    "$SPOILED_MILK_LAYERED_PLACEMENTS" <<'PY'
import json
import sys

path, expected_id, expected_version, expected_fingerprint, expected_sectors, expected_placements = sys.argv[1:]
with open(path, "r", encoding="utf-8") as handle:
    report = json.load(handle)

actual_placements = sum(
    int(report.get(key, -1))
    for key in (
        "boundaryPlacementCount",
        "groundItemPlacementCount",
        "npcPlacementCount",
        "sceneryPlacementCount",
    )
)
expected = {
    "packageId": expected_id,
    "packageVersion": expected_version,
    "packageFingerprintSha256": expected_fingerprint,
    "terrainSectorCount": int(expected_sectors),
}
problems = [
    f"{key}={report.get(key)!r}, expected {value!r}"
    for key, value in expected.items()
    if report.get(key) != value
]
if actual_placements != int(expected_placements):
    problems.append(
        f"effective placements={actual_placements}, expected {expected_placements}"
    )
if problems:
    raise SystemExit("Layered-world validation mismatch: " + "; ".join(problems))
PY
}

layered_world_generate_package() {
  local repository_root="$1"
  local workspace="$2"
  local package_root="$workspace/package"

  if [[ -e "$package_root" ]]; then
    layered_world_fail "Layered-world generation requires a fresh workspace; refusing existing package: $package_root"
    return 1
  fi

  # A failed generator must never validate or return an incomplete package
  # emitted before that failed run stopped.
  if ! LAYERED_MAPS_WORKSPACE="$workspace" \
    "$repository_root/tools/layered-maps/layered-maps.sh" \
      spoiled-milk-package >&2; then
    layered_world_fail "Layered-world generation failed; refusing all package output in $workspace"
    return 1
  fi
  layered_world_validate_package \
    "$repository_root" \
    "$package_root" \
    "$workspace/runtime-validation"
  printf '%s\n' "$package_root"
}

layered_world_require_promotion_approved() {
  local generation_report="$1"

  [[ -f "$generation_report" ]] \
    || layered_world_fail "Layered-world generation report is missing: $generation_report"
  python3 - "$generation_report" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    report = json.load(handle)
if report.get("contentTarget") != "spoiled-milk":
    raise SystemExit("Layered-world promotion requires the Spoiled Milk target")
if report.get("reviewState") != "production-approved":
    raise SystemExit(
        "Layered-world promotion remains "
        + str(report.get("reviewState", "unreviewed"))
    )
if report.get("runtimePromotionApproved") is not True:
    raise SystemExit("Layered-world runtime promotion is not owner-approved")
PY
}

layered_world_live_package_path() {
  local live_state_root="$1"

  printf '%s/layered-worlds/%s/package\n' \
    "$live_state_root" \
    "$SPOILED_MILK_LAYERED_MANIFEST_SHA256"
}

layered_world_install_live_package() {
  local repository_root="$1"
  local live_state_root="$2"
  local generation_workspace package_root destination destination_parent
  local staging_root

  generation_workspace="$repository_root/tools/layered-maps/workspace/live-deploy"
  package_root="$(layered_world_generate_package \
    "$repository_root" "$generation_workspace")"
  layered_world_require_promotion_approved \
    "$generation_workspace/generation-report.json"
  destination="$(layered_world_live_package_path "$live_state_root")"
  destination_parent="$(dirname "$destination")"

  if [[ -d "$destination" ]]; then
    layered_world_validate_package \
      "$repository_root" \
      "$destination" \
      "$generation_workspace/installed-validation"
    printf '%s\n' "$destination"
    return 0
  fi
  [[ ! -e "$destination" ]] \
    || layered_world_fail "Layered-world live destination is not a directory: $destination"
  [[ ! -e "$destination_parent" ]] \
    || layered_world_fail "Incomplete layered-world live installation requires review: $destination_parent"

  staging_root="$(dirname "$destination_parent")/.layered-world-staging-${SPOILED_MILK_LAYERED_MANIFEST_SHA256}-$$"
  [[ ! -e "$staging_root" ]] \
    || layered_world_fail "Layered-world staging path already exists: $staging_root"
  mkdir -p "$staging_root"
  cp -a "$package_root" "$staging_root/package"
  cp "$generation_workspace/generation-report.json" "$staging_root/generation-report.json"
  cp "$generation_workspace/package-validation.json" "$staging_root/package-validation.json"
  printf '%s\n' "$(git -C "$repository_root" rev-parse HEAD)" \
    > "$staging_root/SOURCE-COMMIT.txt"

  layered_world_validate_package \
    "$repository_root" \
    "$staging_root/package" \
    "$staging_root/copy-validation"

  mkdir -p "$(dirname "$destination_parent")"
  mv "$staging_root" "$destination_parent"
  printf '%s\n' "$destination"
}

layered_world_require_live_package() {
  local repository_root="$1"
  local live_state_root="$2"
  local package_root

  package_root="$(layered_world_live_package_path "$live_state_root")"
  [[ -d "$package_root" ]] \
    || layered_world_fail "The reviewed live layered-world package is not installed: $package_root"
  layered_world_validate_package \
    "$repository_root" \
    "$package_root" \
    "$repository_root/tools/layered-maps/workspace/live-launch-validation"
  printf '%s\n' "$package_root"
}

layered_world_enable_private_production_profile() {
  local package_root="$1"

  export OPENRSC_LAYERED_PLAYER_LOCATION_AUTHORITY=true
  export OPENRSC_LAYERED_SPATIAL_RUNTIME_AUTHORITY=true
  export OPENRSC_LAYERED_PROTOCOL_CLIENT_AUTHORITY=true
  export OPENRSC_LAYERED_SYNTHETIC_DEEP_FIXTURE=false
  export OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE=true
  export OPENRSC_LAYERED_NATIVE_TERRAIN_RESIDENCY=true
  export OPENRSC_LAYERED_NATIVE_TERRAIN_READINESS=true
  export OPENRSC_LAYERED_NATIVE_TERRAIN_PREDICTION=true
  export OPENRSC_LAYERED_NATIVE_TERRAIN_SYMMETRIC_RESIDENCY=true
  export OPENRSC_LAYERED_NATIVE_TERRAIN_ATOMIC_ACTIVATION=true
  export OPENRSC_SYNC_SCENE_BASELINE=true
  export OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE_PATH="$package_root"
  export OPENRSC_LAYERED_NATIVE_WORLD_RUNTIME_PROFILE=spoiled-milk-replacement
  export SPOILED_MILK_LAYERED_RUNTIME_MODE=production
}

layered_world_enable_legacy_rollback_profile() {
  export OPENRSC_LAYERED_PLAYER_LOCATION_AUTHORITY=false
  export OPENRSC_LAYERED_SPATIAL_RUNTIME_AUTHORITY=false
  export OPENRSC_LAYERED_PROTOCOL_CLIENT_AUTHORITY=false
  export OPENRSC_LAYERED_SYNTHETIC_DEEP_FIXTURE=false
  export OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE=false
  export OPENRSC_LAYERED_NATIVE_TERRAIN_RESIDENCY=false
  export OPENRSC_LAYERED_NATIVE_TERRAIN_READINESS=false
  export OPENRSC_LAYERED_NATIVE_TERRAIN_PREDICTION=false
  export OPENRSC_LAYERED_NATIVE_TERRAIN_SYMMETRIC_RESIDENCY=false
  export OPENRSC_LAYERED_NATIVE_TERRAIN_ATOMIC_ACTIVATION=false
  export OPENRSC_SYNC_SCENE_BASELINE=false
  unset OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE_PATH
  export OPENRSC_LAYERED_NATIVE_WORLD_RUNTIME_PROFILE=fixture-additive
  export SPOILED_MILK_LAYERED_RUNTIME_MODE=legacy-rollback
}
