#!/usr/bin/env bash

# Exact reviewed Spoiled Milk layered-world product. Keep these values aligned
# with NativeLayeredWorldRuntimeProfile and the generator documentation. Every
# release, private production-profile run, and live deployment validates this
# identity before the server can consume the package.
SPOILED_MILK_LAYERED_PACKAGE_ID="rsc-remastered.spoiled-milk-layered-world"
SPOILED_MILK_LAYERED_PACKAGE_VERSION="0.5.0"
SPOILED_MILK_LAYERED_MANIFEST_SHA256="f3489503d2c323e669ab3004345271c6137a58698cc8c0bf0b0fb2587eb52b29"
SPOILED_MILK_LAYERED_PACKAGE_FINGERPRINT="f699e5a37709a0f127d676d91f07f64e256825bef6d65a7a4ccd13c8310f3b75"
SPOILED_MILK_LAYERED_TERRAIN_SECTORS="1782"
SPOILED_MILK_LAYERED_PLACEMENTS="33545"
SPOILED_MILK_LAYERED_SOURCE_RELATIVE_PATH="server/world-builder/packages/$SPOILED_MILK_LAYERED_PACKAGE_FINGERPRINT/package"

layered_world_fail() {
  printf 'FAIL: %s\n' "$*" >&2
  return 1
}

# Selection/inventory is not permission to run placements. The maintained generic
# package parser and the server's material/collision/capacity preflight still run.
layered_world_selected_pair() {
  local repository_root="$1" workspace="$2" before after package_root
  before="$(python3 "$repository_root/scripts/installed-map-selection.py" "$repository_root")" || return 1
  package_root="$(python3 -c 'import json,sys;print(json.loads(sys.argv[1])["serverPackage"])' "$before")" || return 1
  LAYERED_MAPS_WORKSPACE="$workspace" \
    "$repository_root/tools/layered-maps/layered-maps.sh" package-check --package "$package_root" >&2 || return 1
  after="$(python3 "$repository_root/scripts/installed-map-selection.py" "$repository_root")" || return 1
  [[ "$before" == "$after" ]] || { layered_world_fail 'Installed pair changed during validation'; return 1; }
  printf '%s\n' "$after"
}

layered_world_generate_selected_package() {
  local repository_root="$1" workspace="$2" selection package_root after
  selection="$(layered_world_selected_pair "$repository_root" "$workspace/runtime-validation")" || return 1
  package_root="$(python3 -c 'import json,sys;print(json.loads(sys.argv[1])["serverPackage"])' "$selection")" || return 1
  [[ ! -e "$workspace/package" ]] || { layered_world_fail "Existing promotion output: $workspace/package"; return 1; }
  mkdir -p "$workspace"
  cp -a "$package_root" "$workspace/package" || return 1
  after="$(python3 "$repository_root/scripts/installed-map-selection.py" "$repository_root")" || return 1
  [[ "$selection" == "$after" ]] || { layered_world_fail 'Installed pair changed during copy'; return 1; }
  python3 - "$repository_root" "$workspace" "$selection" <<'PY' || return 1
import importlib.util, json, pathlib, subprocess, sys
sys.dont_write_bytecode = True
root, workspace, encoded = sys.argv[1:]
spec = importlib.util.spec_from_file_location('selection', root+'/scripts/installed-map-selection.py')
module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
report = json.loads(encoded)
_, copied = module.inventory(pathlib.Path(workspace)/'package')
if copied != report['packageFingerprintSha256']:
    raise SystemExit('Copied installed package differs')
report.update(reportType='source-selected-package-promotion', reviewState='reviewed-source-required',
              sourceCommit=subprocess.check_output(['git','-C',root,'rev-parse','HEAD'],text=True).strip())
pathlib.Path(workspace,'generation-report.json').write_text(json.dumps(report,sort_keys=True,indent=2)+'\n')
PY
  cp "$workspace/runtime-validation/package-validation.json" "$workspace/package-validation.json" || return 1
  printf '%s\n' "$workspace/package"
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
	"$package_root" \
	"$SPOILED_MILK_LAYERED_PACKAGE_ID" \
    "$SPOILED_MILK_LAYERED_PACKAGE_VERSION" \
    "$SPOILED_MILK_LAYERED_PACKAGE_FINGERPRINT" \
    "$SPOILED_MILK_LAYERED_TERRAIN_SECTORS" \
    "$SPOILED_MILK_LAYERED_PLACEMENTS" <<'PY'
import hashlib
import json
import sys
from pathlib import Path

path, package_root, expected_id, expected_version, expected_fingerprint, expected_sectors, expected_placements = sys.argv[1:]
with open(path, "r", encoding="utf-8") as handle:
    report = json.load(handle)

root = Path(package_root)
digest = hashlib.sha256()
for item in sorted(path for path in root.rglob("*") if path.is_file()):
    relative = item.relative_to(root).as_posix()
    payload = item.read_bytes()
    for value in (relative, str(len(payload)), hashlib.sha256(payload).hexdigest()):
        digest.update(value.encode("utf-8"))
        digest.update(b"\0")
actual_fingerprint = digest.hexdigest()

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
if actual_fingerprint != expected_fingerprint:
    problems.append(
        f"content fingerprint={actual_fingerprint}, expected {expected_fingerprint}"
    )
if problems:
    raise SystemExit("Layered-world validation mismatch: " + "; ".join(problems))
PY
}

layered_world_generate_package() {
  # A present selector always wins, including refusal for malformed metadata.
  if [[ -e "$1/server/world-builder-configs/primary.json" || -L "$1/server/world-builder-configs/primary.json" ]]; then
    layered_world_generate_selected_package "$1" "$2"
  else
    layered_world_generate_pinned_package "$1" "$2"
  fi
}

layered_world_generate_pinned_package() {
	local repository_root="$1"
	local workspace="$2"
	local package_root="$workspace/package"
	local source_package="$repository_root/$SPOILED_MILK_LAYERED_SOURCE_RELATIVE_PATH"

  if [[ -e "$package_root" ]]; then
    layered_world_fail "Layered-world generation requires a fresh workspace; refusing existing package: $package_root"
    return 1
  fi

	if [[ ! -d "$source_package" ]]; then
		layered_world_fail "Reviewed World Builder package is missing; refusing all package output: $source_package"
		return 1
	fi
	mkdir -p "$workspace"
	if ! cp -a "$source_package" "$package_root"; then
		layered_world_fail "World Builder package copy failed; refusing all package output in $workspace"
		return 1
	fi
	layered_world_validate_package \
		"$repository_root" \
		"$package_root" \
		"$workspace/runtime-validation"
	cp "$workspace/runtime-validation/package-validation.json" \
		"$workspace/package-validation.json"
	python3 - "$workspace/generation-report.json" \
		"$SPOILED_MILK_LAYERED_PACKAGE_FINGERPRINT" <<'PY'
import json
import sys

path, fingerprint = sys.argv[1:]
with open(path, "w", encoding="utf-8") as handle:
    json.dump({
        "schemaVersion": 1,
        "reportType": "world-builder-reviewed-package-promotion",
        "contentTarget": "spoiled-milk",
        "reviewState": "production-approved",
        "runtimePromotionApproved": True,
        "packageFingerprintSha256": fingerprint,
    }, handle, indent=2, sort_keys=True)
    handle.write("\n")
PY
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
if report.get('reportType') == 'source-selected-package-promotion':
    import subprocess
    root = report['root']
    if subprocess.check_output(['git','-C',root,'status','--porcelain','--untracked-files=all'],text=True):
        raise SystemExit('Selected map promotion requires clean reviewed source; use an authoring copy, commit and publish first')
    if subprocess.check_output(['git','-C',root,'rev-parse','HEAD'],text=True).strip() != report['sourceCommit']:
        raise SystemExit('Selected map promotion source changed')
    raise SystemExit(0)
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
  # The exact release carries both roles. Absent/malformed selectors refuse;
  # deploying must never silently regenerate the historical distribution.
  layered_world_require_live_package "$1" "$2"
}

layered_world_install_pinned_live_package() {
  local repository_root="$1"
  local live_state_root="$2"
  local generation_workspace package_root destination destination_parent
  local staging_root

  generation_workspace="$repository_root/tools/layered-maps/workspace/live-deploy"
  package_root="$(layered_world_generate_pinned_package \
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
  local selection
  selection="$(layered_world_selected_pair "$1" "$1/tools/layered-maps/workspace/installed-launch-validation")" || return 1
  python3 -c 'import json,sys;print(json.loads(sys.argv[1])["serverPackage"])' "$selection"
}

layered_world_enable_hosted_installed_profile() {
  local pin options property
  for pin in OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE_PATH OPENRSC_LAYERED_NATIVE_TERRAIN_MANIFEST_SHA256 OPENRSC_LAYERED_NATIVE_WORLD_RUNTIME_PROFILE; do
    [[ -z "${!pin:-}" ]] || { layered_world_fail "Hosted installed mode refuses explicit $pin; remove it or choose deliberate rollback"; return 1; }
  done
  for options in JAVA_TOOL_OPTIONS _JAVA_OPTIONS JDK_JAVA_OPTIONS ANT_OPTS JAVA_OPTS; do
    for property in openrsc.layeredNativeTerrainPackagePath openrsc.layeredNativeTerrainManifestSha256 openrsc.layeredNativeWorldRuntimeProfile openrsc.worldBuilderTargetRoot; do
      [[ "${!options:-}" != *"-D$property"* ]] || { layered_world_fail "Hosted installed mode refuses source property $property in $options"; return 1; }
    done
  done
  layered_world_require_live_package "$1" "$2"
}

# Lightweight status attestation: byte/kind/selector checks only, no JVM/build.
layered_world_installed_marker_matches() {
  local observed
  observed="$(python3 "$1/scripts/installed-map-selection.py" "$1")" || return 1
  python3 - "$observed" "$2" "$3" "$4" <<'PY'
import json,sys
selection=json.loads(sys.argv[1])
if [selection['serverPackage'],selection['manifestSha256'],selection['selectionSha256']] != sys.argv[2:]:
    raise SystemExit('Installed launch marker no longer matches selected map inputs')
PY
}

# Deliberate rollback only; normal hosted startup must consume PRIMARY instead.
layered_world_require_pinned_live_package() {
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
  export OPENRSC_LAYERED_NATIVE_TERRAIN_MANIFEST_SHA256="$SPOILED_MILK_LAYERED_MANIFEST_SHA256"
  export OPENRSC_LAYERED_NATIVE_WORLD_RUNTIME_PROFILE=world-builder-installed
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
  unset OPENRSC_LAYERED_NATIVE_TERRAIN_MANIFEST_SHA256
  export OPENRSC_LAYERED_NATIVE_WORLD_RUNTIME_PROFILE=fixture-additive
  export SPOILED_MILK_LAYERED_RUNTIME_MODE=legacy-rollback
}
