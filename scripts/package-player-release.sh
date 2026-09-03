#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_DIR="${ROOT_DIR:-$SCRIPT_ROOT}"

VERSION=""
HOST=""
PORT=""
WINDOWS_JRE=""
ASSETS_CLEARED=false
SKIP_BUILD=false
SOURCE_COMMIT=""
RELEASE_MARKER_ENTRY="spoiled-milk-release-build.marker"

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  ./scripts/package-player-release.sh \
    --version v0.1.1 \
    --host game.example.org \
    --port 43605 \
    --windows-jre /path/to/temurin-17-windows-x64-jre \
    --assets-cleared

Options:
  --assets-cleared  Acknowledge that redistribution terms for packaged visual
                    assets were checked before creating publishable archives.
  --skip-build      Use an already-built client jar. Intended for packaging
                    regression tests only.
EOF
}

while (($#)); do
  case "$1" in
    --version)
      [[ $# -ge 2 ]] || fail "--version requires a value"
      VERSION="$2"
      shift 2
      ;;
    --host)
      [[ $# -ge 2 ]] || fail "--host requires a value"
      HOST="$2"
      shift 2
      ;;
    --port)
      [[ $# -ge 2 ]] || fail "--port requires a value"
      PORT="$2"
      shift 2
      ;;
    --windows-jre)
      [[ $# -ge 2 ]] || fail "--windows-jre requires a value"
      WINDOWS_JRE="$2"
      shift 2
      ;;
    --assets-cleared)
      ASSETS_CLEARED=true
      shift
      ;;
    --skip-build)
      SKIP_BUILD=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "Unknown option: $1"
      ;;
  esac
done

[[ "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-alpha\.[0-9]+)?$ ]] \
  || fail "Version must use semantic form, for example v0.1.1 or v0.1.0-alpha.84"
[[ "$HOST" =~ ^[A-Za-z0-9.-]+$ ]] || fail "Host must be a DNS name or IPv4 address"
case "${HOST,,}" in
  localhost|127.*|0.0.0.0)
    fail "Player release host must be a public host/IP, not $HOST"
    ;;
esac
[[ "$PORT" =~ ^[0-9]+$ ]] && ((PORT >= 1 && PORT <= 65535)) \
  || fail "Port must be between 1 and 65535"
[[ "$ASSETS_CLEARED" == true ]] \
  || fail "Confirm redistribution terms for packaged third-party visual assets before packaging with --assets-cleared"
if [[ "$SKIP_BUILD" == true && "${SPOILED_MILK_RELEASE_TEST_MODE:-}" != 1 ]]; then
  fail "--skip-build is restricted to packaging regression tests; manager releases must build the client"
fi

for command_name in git jar zip sha256sum python3; do
  command -v "$command_name" >/dev/null 2>&1 || fail "Missing dependency: $command_name"
done

require_release_git_state() {
  local expected_commit="${1:-}"
  local git_dir
  local current_branch
  local current_commit
  local worktree_status
  local spoiled_milk_main_commit
  local operation_entry
  local operation_marker
  local operation_name

  git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
    || fail "Release packaging must run from the manager Git worktree"
  git_dir="$(git -C "$ROOT_DIR" rev-parse --absolute-git-dir)"

  for operation_entry in \
    "MERGE_HEAD:merge" \
    "CHERRY_PICK_HEAD:cherry-pick" \
    "REVERT_HEAD:revert" \
    "REBASE_HEAD:rebase" \
    "rebase-apply:rebase or am" \
    "rebase-merge:rebase" \
    "sequencer:sequenced cherry-pick or revert" \
    "BISECT_LOG:bisect"; do
    operation_marker="${operation_entry%%:*}"
    operation_name="${operation_entry#*:}"
    [[ ! -e "$git_dir/$operation_marker" ]] \
      || fail "Release packaging is blocked by an in-progress Git $operation_name operation"
  done

  current_branch="$(git -C "$ROOT_DIR" symbolic-ref --quiet --short HEAD 2>/dev/null || true)"
  [[ "$current_branch" == "main" ]] \
    || fail "Release packaging must run from manager branch main; found ${current_branch:-detached HEAD}"

  worktree_status="$(git -C "$ROOT_DIR" status --porcelain --untracked-files=all)"
  [[ -z "$worktree_status" ]] \
    || fail "Release packaging requires a clean manager main worktree; commit or remove pending files first"

  current_commit="$(git -C "$ROOT_DIR" rev-parse --verify 'HEAD^{commit}')"
  if [[ -n "$expected_commit" && "$current_commit" != "$expected_commit" ]]; then
    fail "Release source changed during packaging (expected $expected_commit, found $current_commit)"
  fi
  if [[ -z "$SOURCE_COMMIT" ]]; then
    SOURCE_COMMIT="$current_commit"
  fi
  spoiled_milk_main_commit="$(git -C "$ROOT_DIR" rev-parse --verify 'refs/remotes/spoiled-milk/main^{commit}' 2>/dev/null || true)"
  [[ -n "$spoiled_milk_main_commit" ]] \
    || fail "Missing spoiled-milk/main; fetch the spoiled-milk remote before packaging"
  [[ "$SOURCE_COMMIT" == "$spoiled_milk_main_commit" ]] \
    || fail "Release packaging requires HEAD to match spoiled-milk/main (HEAD $SOURCE_COMMIT, spoiled-milk/main $spoiled_milk_main_commit)"
}

require_release_git_state

[[ -d "$WINDOWS_JRE" ]] || fail "Windows JRE directory does not exist: $WINDOWS_JRE"
[[ -f "$WINDOWS_JRE/bin/java.exe" ]] || fail "Windows JRE must contain bin/java.exe"
[[ -f "$WINDOWS_JRE/release" ]] || fail "Windows JRE must contain its release metadata file"
[[ -f "$WINDOWS_JRE/LICENSE" || -f "$WINDOWS_JRE/NOTICE" || -f "$WINDOWS_JRE/legal/java.base/LICENSE" ]] \
  || fail "Windows JRE must contain redistribution legal files"

runtime_version="$(sed -n 's/^JAVA_VERSION="\([^"]*\)".*/\1/p' "$WINDOWS_JRE/release" | head -n 1)"
[[ -n "$runtime_version" ]] || fail "Unable to read JAVA_VERSION from Windows JRE release metadata"
if [[ "$runtime_version" == 1.* ]]; then
  runtime_major="${runtime_version#1.}"
  runtime_major="${runtime_major%%.*}"
else
  runtime_major="${runtime_version%%.*}"
fi
[[ "$runtime_major" =~ ^[0-9]+$ ]] && ((runtime_major >= 17)) \
  || fail "Windows player bundle requires a Java 17+ runtime; found $runtime_version"

if [[ "$SKIP_BUILD" != true ]]; then
  SPOILED_MILK_RELEASE_BUILD=1 "$ROOT_DIR/scripts/build-client.sh"
fi

# A build or concurrent session must not change the source snapshot after its
# provenance commit was captured.
require_release_git_state "$SOURCE_COMMIT"

CLIENT_JAR="$ROOT_DIR/Client_Base/Open_RSC_Client.jar"
CLIENT_CACHE="$ROOT_DIR/Client_Base/Cache"
CLIENT_WORLD_BUILDER_PROFILE="$ROOT_DIR/Client_Base/world-builder-configs/installed-client.json"
PACKAGE_ASSETS="$ROOT_DIR/release/player"

mapfile -t CLIENT_WORLD_BUILDER_PROFILE_VALUES < <(
  python3 - "$CLIENT_WORLD_BUILDER_PROFILE" <<'PY'
import json
import sys

with open(sys.argv[1], "r", encoding="utf-8") as handle:
    profile = json.load(handle)
if profile.get("manifestType") != "world-builder-installed-client-profile":
    raise SystemExit("Installed client profile has the wrong manifest type")
if profile.get("active") is not True:
    raise SystemExit("Installed client profile is not active")
for key in ("packageFingerprintSha256", "manifestSha256", "packageRelativePath"):
    value = profile.get(key)
    if not isinstance(value, str):
        raise SystemExit(f"Installed client profile field {key} is missing")
    print(value)
PY
)
[[ ${#CLIENT_WORLD_BUILDER_PROFILE_VALUES[@]} -eq 3 ]] \
  || fail "Unable to read the installed World Builder client profile"
CLIENT_WORLD_BUILDER_FINGERPRINT="${CLIENT_WORLD_BUILDER_PROFILE_VALUES[0]}"
CLIENT_WORLD_BUILDER_MANIFEST_SHA256="${CLIENT_WORLD_BUILDER_PROFILE_VALUES[1]}"
CLIENT_WORLD_BUILDER_RELATIVE_PATH="${CLIENT_WORLD_BUILDER_PROFILE_VALUES[2]}"
[[ "$CLIENT_WORLD_BUILDER_FINGERPRINT" =~ ^[0-9a-f]{64}$ ]] \
  || fail "Installed client package fingerprint is invalid"
[[ "$CLIENT_WORLD_BUILDER_MANIFEST_SHA256" =~ ^[0-9a-f]{64}$ ]] \
  || fail "Installed client manifest SHA-256 is invalid"
[[ "$CLIENT_WORLD_BUILDER_RELATIVE_PATH" == \
  "world-builder/packages/$CLIENT_WORLD_BUILDER_FINGERPRINT/package" ]] \
  || fail "Installed client package path is not fingerprint-addressed"
CLIENT_WORLD_BUILDER_PACKAGE="$ROOT_DIR/Client_Base/$CLIENT_WORLD_BUILDER_RELATIVE_PATH"
[[ -d "$CLIENT_WORLD_BUILDER_PACKAGE" ]] \
  || fail "Installed client package is missing: $CLIENT_WORLD_BUILDER_PACKAGE"
CLIENT_WORLD_BUILDER_ACTUAL_MANIFEST_SHA256="$(
  sha256sum "$CLIENT_WORLD_BUILDER_PACKAGE/manifest.json" | awk '{print $1}'
)"
[[ "$CLIENT_WORLD_BUILDER_ACTUAL_MANIFEST_SHA256" == \
  "$CLIENT_WORLD_BUILDER_MANIFEST_SHA256" ]] \
  || fail "Installed client package manifest does not match its profile"

jar tf "$CLIENT_JAR" | grep -Fx "$RELEASE_MARKER_ENTRY" >/dev/null \
  || fail "Release client jar is missing the embedded release-build marker"

for required_path in \
  "$CLIENT_JAR" \
  "$CLIENT_CACHE/audio" \
  "$CLIENT_CACHE/video" \
  "$CLIENT_CACHE/config.txt" \
  "$ROOT_DIR/LICENSE" \
  "$PACKAGE_ASSETS/PLAYER-README.txt" \
  "$PACKAGE_ASSETS/ASSET-SOURCES.txt" \
  "$PACKAGE_ASSETS/play-spoiled-milk.sh" \
  "$PACKAGE_ASSETS/update-spoiled-milk.sh" \
  "$PACKAGE_ASSETS/update-spoiled-milk.ps1" \
  "$PACKAGE_ASSETS/Play Spoiled Milk.cmd" \
  "$PACKAGE_ASSETS/Update Spoiled Milk.cmd" \
  "$PACKAGE_ASSETS/Play Spoiled Milk Windows.cmd"; do
  [[ -e "$required_path" ]] || fail "Missing release input: $required_path"
done

jar tf "$CLIENT_JAR" | grep '^myworld-assets/' >/dev/null \
  || fail "Client jar is missing embedded MyWorld visual assets; rebuild the client before packaging"

require_client_jar_entry() {
  local entry="$1"

  jar tf "$CLIENT_JAR" | grep -Fx "$entry" >/dev/null \
    || fail "Client jar is missing OpenGL runtime entry: $entry. Run scripts/download-lwjgl.sh with the release native classifiers and rebuild."
}

for opengl_runtime_entry in \
  "linux/x64/org/lwjgl/liblwjgl.so" \
  "linux/x64/org/lwjgl/glfw/libglfw.so" \
  "linux/x64/org/lwjgl/opengl/liblwjgl_opengl.so" \
  "macos/x64/org/lwjgl/liblwjgl.dylib" \
  "macos/x64/org/lwjgl/glfw/libglfw.dylib" \
  "macos/x64/org/lwjgl/opengl/liblwjgl_opengl.dylib" \
  "macos/arm64/org/lwjgl/liblwjgl.dylib" \
  "macos/arm64/org/lwjgl/glfw/libglfw.dylib" \
  "macos/arm64/org/lwjgl/opengl/liblwjgl_opengl.dylib" \
  "windows/x64/org/lwjgl/lwjgl.dll" \
  "windows/x64/org/lwjgl/glfw/glfw.dll" \
  "windows/x64/org/lwjgl/opengl/lwjgl_opengl.dll"; do
  require_client_jar_entry "$opengl_runtime_entry"
done

require_launcher_flag() {
  local launcher="$1"
  local flag="$2"

  grep -F -- "$flag" "$launcher" >/dev/null \
    || fail "Player launcher is missing renderer-v2 flag $flag: $launcher"
}

for launcher in \
  "$PACKAGE_ASSETS/play-spoiled-milk.sh" \
  "$PACKAGE_ASSETS/Play Spoiled Milk.cmd" \
  "$PACKAGE_ASSETS/Play Spoiled Milk Windows.cmd"; do
  require_launcher_flag "$launcher" "-Xms512m"
  require_launcher_flag "$launcher" "-Xmx2g"
  require_launcher_flag "$launcher" "-Dspoiledmilk.releaseBuild=true"
  require_launcher_flag "$launcher" "-Dsun.java2d.opengl=true"
  for renderer_flag in \
    "-Dspoiledmilk.directFramebuffer=true" \
    "-Dspoiledmilk.openglPresenter=true" \
    "-Dspoiledmilk.openglInput=true" \
    "-Dspoiledmilk.openglPrimaryWindow=true" \
    "-Dspoiledmilk.openglVsync=false" \
    "-Dspoiledmilk.renderer3DGeometryCapture=true" \
    "-Dspoiledmilk.openglWorldMesh=true" \
    "-Dspoiledmilk.openglWorldMeshTexturedVisible=true" \
    "-Dspoiledmilk.openglWorldMeshTexturedStaticVisible=true" \
    "-Dspoiledmilk.openglWorldStaticTextures=true" \
    "-Dspoiledmilk.openglWorldTexturedAlpha=1.0" \
    "-Dspoiledmilk.openglWorldChunksTexturedVisible=true" \
    "-Dspoiledmilk.openglWorldChunksReplacementComposite=true" \
    "-Dspoiledmilk.openglWorldChunksTrustedReplacement=true" \
    "-Dspoiledmilk.openglWorldChunksResidentObjects=true" \
    "-Dspoiledmilk.openglWorldChunksSpatialCull=true" \
    "-Dspoiledmilk.openglWorldChunkUploadBudgetMs=3.0" \
    "-Dspoiledmilk.openglWorldChunksRemasterLightingShader=true" \
    "-Dspoiledmilk.openglWorldSpritesVisible=true" \
    "-Dspoiledmilk.skipLegacyWorldRaster=true" \
    "-Dspoiledmilk.modernClientLoop=true"; do
    require_launcher_flag "$launcher" "$renderer_flag"
  done
done

OUTPUT_DIR="$ROOT_DIR/output/releases/$VERSION"
STAGING_DIR="$OUTPUT_DIR/staging"
JAVA_NAME="spoiled-milk-$VERSION-java"
WINDOWS_NAME="spoiled-milk-$VERSION-windows-x64"
JAVA_DIR="$STAGING_DIR/$JAVA_NAME"
WINDOWS_DIR="$STAGING_DIR/$WINDOWS_NAME"
PAYLOAD_SUBDIR="game-files"
JAVA_PAYLOAD_DIR="$JAVA_DIR/$PAYLOAD_SUBDIR"
WINDOWS_PAYLOAD_DIR="$WINDOWS_DIR/$PAYLOAD_SUBDIR"

rm -rf "$OUTPUT_DIR"
mkdir -p "$JAVA_PAYLOAD_DIR/Cache" "$WINDOWS_PAYLOAD_DIR/Cache" "$OUTPUT_DIR"

stage_payload_files() {
	local destination="$1"

	cp "$CLIENT_JAR" "$destination/Spoiled_Milk_Client.jar"
	cp -R "$CLIENT_CACHE/audio" "$destination/Cache/audio"
	cp -R "$CLIENT_CACHE/video" "$destination/Cache/video"
	cp "$CLIENT_CACHE/config.txt" "$destination/Cache/config.txt"
  mkdir -p "$destination/world-builder-configs"
  cp "$CLIENT_WORLD_BUILDER_PROFILE" \
    "$destination/world-builder-configs/installed-client.json"
  mkdir -p \
    "$destination/world-builder/packages/$CLIENT_WORLD_BUILDER_FINGERPRINT"
  cp -R "$CLIENT_WORLD_BUILDER_PACKAGE" \
    "$destination/world-builder/packages/$CLIENT_WORLD_BUILDER_FINGERPRINT/package"
  printf '%s\n' "$HOST" > "$destination/Cache/ip.txt"
	printf '%s\n' "$PORT" > "$destination/Cache/port.txt"
	cp "$ROOT_DIR/LICENSE" "$destination/LICENSE"
	cp "$PACKAGE_ASSETS/ASSET-SOURCES.txt" "$destination/ASSET-SOURCES.txt"
	printf '%s\n' "$VERSION" > "$destination/VERSION.txt"
	printf '%s\n' "$SOURCE_COMMIT" > "$destination/SOURCE-COMMIT.txt"
}

stage_root_files() {
	local destination="$1"

	sed "s/@VERSION@/$VERSION/g" "$PACKAGE_ASSETS/PLAYER-README.txt" > "$destination/README.txt"
}

stage_payload_files "$JAVA_PAYLOAD_DIR"
stage_payload_files "$WINDOWS_PAYLOAD_DIR"
stage_root_files "$JAVA_DIR"
stage_root_files "$WINDOWS_DIR"

cp "$PACKAGE_ASSETS/play-spoiled-milk.sh" "$JAVA_DIR/play-spoiled-milk.sh"
cp "$PACKAGE_ASSETS/Play Spoiled Milk.cmd" "$JAVA_DIR/Play Spoiled Milk.cmd"
sed "s/@VERSION@/$VERSION/g" "$PACKAGE_ASSETS/update-spoiled-milk.sh" > "$JAVA_PAYLOAD_DIR/update-spoiled-milk.sh"
sed "s/@VERSION@/$VERSION/g; s/@PACKAGE_KIND@/java/g" "$PACKAGE_ASSETS/update-spoiled-milk.ps1" > "$JAVA_PAYLOAD_DIR/update-spoiled-milk.ps1"
cp "$PACKAGE_ASSETS/Update Spoiled Milk.cmd" "$JAVA_PAYLOAD_DIR/Update Spoiled Milk.cmd"
chmod +x "$JAVA_DIR/play-spoiled-milk.sh"
chmod +x "$JAVA_PAYLOAD_DIR/update-spoiled-milk.sh"

cp "$PACKAGE_ASSETS/Play Spoiled Milk Windows.cmd" "$WINDOWS_DIR/Play Spoiled Milk.cmd"
sed "s/@VERSION@/$VERSION/g; s/@PACKAGE_KIND@/windows-x64/g" "$PACKAGE_ASSETS/update-spoiled-milk.ps1" > "$WINDOWS_PAYLOAD_DIR/update-spoiled-milk.ps1"
cp "$PACKAGE_ASSETS/Update Spoiled Milk.cmd" "$WINDOWS_PAYLOAD_DIR/Update Spoiled Milk.cmd"
mkdir -p "$WINDOWS_PAYLOAD_DIR/runtime"
cp -R "$WINDOWS_JRE"/. "$WINDOWS_PAYLOAD_DIR/runtime/"

# Recheck immediately before archive creation so SOURCE-COMMIT.txt cannot
# describe a different or dirty source tree.
require_release_git_state "$SOURCE_COMMIT"

(
  cd "$STAGING_DIR"
  zip -qr "$OUTPUT_DIR/$JAVA_NAME.zip" "$JAVA_NAME"
  zip -qr "$OUTPUT_DIR/$WINDOWS_NAME.zip" "$WINDOWS_NAME"
)
(
  cd "$OUTPUT_DIR"
  sha256sum "$JAVA_NAME.zip" "$WINDOWS_NAME.zip" > SHA256SUMS.txt
)

rm -rf "$STAGING_DIR"

printf 'Created player release artifacts:\n'
printf '  %s\n' "$OUTPUT_DIR/$JAVA_NAME.zip"
printf '  %s\n' "$OUTPUT_DIR/$WINDOWS_NAME.zip"
printf '  %s\n' "$OUTPUT_DIR/SHA256SUMS.txt"
