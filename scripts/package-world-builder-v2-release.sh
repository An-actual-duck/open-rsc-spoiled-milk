#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_DIR="${ROOT_DIR:-$SCRIPT_ROOT}"

VERSION=""
LINUX_JRE=""
WINDOWS_JRE=""
LAYERED_PACKAGE=""
ASSETS_CLEARED=false
SKIP_BUILD=false
SOURCE_COMMIT=""

PRODUCT_ID="rsc-world-editor-v2"
PRODUCT_GENERATION=2
UPDATE_CHANNEL="rsc-world-editor-v2"
LEGACY_PRODUCT_ID="rsc-world-editor-v1"
LEGACY_FINAL_TAG="v1.1.0"
PACKAGE_NAME="Spoiled Milk World Builder 2"
ARTIFACT_PREFIX="rsc-world-editor-v2"

fail() {
	printf 'FAIL: %s\n' "$*" >&2
	exit 1
}

usage() {
	cat <<'EOF'
Usage:
  ./scripts/package-world-builder-v2-release.sh \
    --version v0.1.0-alpha.1 \
    --linux-jre /path/to/temurin-17-linux-x64-jre \
    --windows-jre /path/to/temurin-17-windows-x64-jre \
    --assets-cleared

Options:
  --layered-package  Override the generated signed-layered package. Intended
                     for deterministic packaging tests and reviewed inputs.
  --assets-cleared  Attest that all packaged visual assets, including editor
                    icons, have confirmed redistribution terms.
  --skip-build      Use existing jars. Restricted to packaging tests.
EOF
}

while (($#)); do
	case "$1" in
		--version)
			[[ $# -ge 2 ]] || fail "--version requires a value"
			VERSION="$2"
			shift 2
			;;
		--windows-jre)
			[[ $# -ge 2 ]] || fail "--windows-jre requires a value"
			WINDOWS_JRE="$2"
			shift 2
			;;
		--linux-jre)
			[[ $# -ge 2 ]] || fail "--linux-jre requires a value"
			LINUX_JRE="$2"
			shift 2
			;;
		--layered-package)
			[[ $# -ge 2 ]] || fail "--layered-package requires a value"
			LAYERED_PACKAGE="$2"
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
	|| fail "Version must use semantic form, for example v0.1.0 or v0.1.0-alpha.1"
[[ "$ASSETS_CLEARED" == true ]] \
	|| fail "Confirm redistribution terms with --assets-cleared before packaging"
if [[ "$SKIP_BUILD" == true && "${SPOILED_MILK_WORLD_BUILDER_V2_RELEASE_TEST_MODE:-}" != 1 ]]; then
	fail "--skip-build is restricted to World Builder 2 packaging tests"
fi

for command_name in git jar zip sha256sum; do
	command -v "$command_name" >/dev/null 2>&1 || fail "Missing dependency: $command_name"
done

require_release_git_state() {
	local expected_commit="${1:-}"
	local git_dir current_branch current_commit worktree_status published_commit
	local operation_entry operation_marker operation_name

	git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1 \
		|| fail "World Builder 2 release packaging must run from the manager Git worktree"
	git_dir="$(git -C "$ROOT_DIR" rev-parse --absolute-git-dir)"
	for operation_entry in \
		"MERGE_HEAD:merge" \
		"CHERRY_PICK_HEAD:cherry-pick" \
		"REVERT_HEAD:revert" \
		"REBASE_HEAD:rebase" \
		"rebase-apply:rebase or am" \
		"rebase-merge:rebase" \
		"sequencer:sequenced operation" \
		"BISECT_LOG:bisect"; do
		operation_marker="${operation_entry%%:*}"
		operation_name="${operation_entry#*:}"
		[[ ! -e "$git_dir/$operation_marker" ]] \
			|| fail "Packaging is blocked by an in-progress Git $operation_name operation"
	done

	current_branch="$(git -C "$ROOT_DIR" symbolic-ref --quiet --short HEAD 2>/dev/null || true)"
	[[ "$current_branch" == "main" ]] \
		|| fail "World Builder 2 packaging must run from manager branch main; found ${current_branch:-detached HEAD}"
	worktree_status="$(git -C "$ROOT_DIR" status --porcelain --untracked-files=all)"
	[[ -z "$worktree_status" ]] \
		|| fail "World Builder 2 packaging requires a clean manager main worktree"

	current_commit="$(git -C "$ROOT_DIR" rev-parse --verify 'HEAD^{commit}')"
	if [[ -n "$expected_commit" && "$current_commit" != "$expected_commit" ]]; then
		fail "Release source changed during packaging (expected $expected_commit, found $current_commit)"
	fi
	if [[ -z "$SOURCE_COMMIT" ]]; then
		SOURCE_COMMIT="$current_commit"
	fi
	published_commit="$(git -C "$ROOT_DIR" rev-parse --verify 'refs/remotes/spoiled-milk/main^{commit}' 2>/dev/null || true)"
	[[ -n "$published_commit" ]] || fail "Missing spoiled-milk/main"
	[[ "$SOURCE_COMMIT" == "$published_commit" ]] \
		|| fail "Packaging requires HEAD to match spoiled-milk/main"
}

require_release_git_state

validate_runtime() {
	local platform="$1" runtime="$2" java_path="$3" expected_os="$4"
	local runtime_version runtime_major runtime_os runtime_arch

	[[ -d "$runtime" ]] || fail "$platform JRE directory does not exist: $runtime"
	[[ -f "$runtime/$java_path" ]] || fail "$platform JRE must contain $java_path"
	[[ -f "$runtime/release" ]] || fail "$platform JRE must contain release metadata"
	[[ -f "$runtime/LICENSE" || -f "$runtime/NOTICE" || -f "$runtime/legal/java.base/LICENSE" ]] \
		|| fail "$platform JRE must contain redistribution legal files"

	runtime_version="$(sed -n 's/^JAVA_VERSION="\([^"]*\)".*/\1/p' "$runtime/release" | head -n 1)"
	[[ -n "$runtime_version" ]] || fail "Unable to read JAVA_VERSION from the $platform JRE"
	if [[ "$runtime_version" == 1.* ]]; then
		runtime_major="${runtime_version#1.}"
		runtime_major="${runtime_major%%.*}"
	else
		runtime_major="${runtime_version%%.*}"
	fi
	if [[ ! "$runtime_major" =~ ^[0-9]+$ ]] || ((runtime_major < 17)); then
		fail "$platform World Builder requires Java 17+; found $runtime_version"
	fi
	runtime_os="$(sed -n 's/^OS_NAME="\([^"]*\)".*/\1/p' "$runtime/release" | head -n 1)"
	runtime_arch="$(sed -n 's/^OS_ARCH="\([^"]*\)".*/\1/p' "$runtime/release" | head -n 1)"
	[[ "$runtime_os" == "$expected_os" ]] \
		|| fail "$platform JRE must report OS_NAME=\"$expected_os\"; found ${runtime_os:-missing}"
	[[ "$runtime_arch" == "x86_64" || "$runtime_arch" == "amd64" ]] \
		|| fail "$platform JRE must be x64; found ${runtime_arch:-missing}"
}

validate_runtime "Linux" "$LINUX_JRE" "bin/java" "Linux"
[[ -x "$LINUX_JRE/bin/java" ]] || fail "Linux JRE bin/java must be executable"
validate_runtime "Windows" "$WINDOWS_JRE" "bin/java.exe" "Windows"

PACKAGE_ASSETS="$ROOT_DIR/release/world-builder-v2"
ICON_CREDITS="$ROOT_DIR/dev/myworld/assets/ui/world-editor/CREDITS.md"
if [[ "$SKIP_BUILD" != true && ! -f "$PACKAGE_ASSETS/RELEASE-READY" ]]; then
	fail "World Builder 2 public packaging remains locked until layered export/import and final release validation are accepted"
fi
[[ -f "$ICON_CREDITS" ]] || fail "World editor icon credits are missing"
if grep -Eiq 'pending confirmation|pending;|not release-ready' "$ICON_CREDITS"; then
	fail "World editor icon provenance is unresolved; update $ICON_CREDITS before packaging"
fi

if [[ "$SKIP_BUILD" != true ]]; then
	"$ROOT_DIR/scripts/build-server.sh"
	"$ROOT_DIR/scripts/build-client.sh"
	"$ROOT_DIR/scripts/build-world-builder-tools.sh"
	"$ROOT_DIR/tools/layered-maps/layered-maps.sh" spoiled-milk-package
fi
require_release_git_state "$SOURCE_COMMIT"

if [[ -z "$LAYERED_PACKAGE" ]]; then
	LAYERED_PACKAGE="$ROOT_DIR/tools/layered-maps/workspace/spoiled-milk-package/package"
fi
LAYERED_PACKAGE="$(cd "$LAYERED_PACKAGE" 2>/dev/null && pwd -P)" \
	|| fail "Signed-layered package does not exist: $LAYERED_PACKAGE"
[[ -f "$LAYERED_PACKAGE/manifest.json" ]] \
	|| fail "Signed-layered package manifest is missing: $LAYERED_PACKAGE/manifest.json"
if find "$LAYERED_PACKAGE" -type l -print -quit | grep -q .; then
	fail "Signed-layered package must not contain symbolic links"
fi

CLIENT_JAR="$ROOT_DIR/Client_Base/Open_RSC_Client.jar"
TOOLS_JAR="$ROOT_DIR/output/world-builder-tools/world-builder-tools.jar"
SERVER_JAR="$ROOT_DIR/server/core.jar"
PLUGINS_JAR="$ROOT_DIR/server/plugins.jar"
SEED_DATABASE="$ROOT_DIR/server/inc/sqlite/myworld_seed.db"

for required_path in \
	"$CLIENT_JAR" \
	"$ROOT_DIR/Client_Base/Cache/audio" \
	"$ROOT_DIR/Client_Base/Cache/video" \
	"$ROOT_DIR/Client_Base/Cache/config.txt" \
	"$SERVER_JAR" \
	"$PLUGINS_JAR" \
	"$ROOT_DIR/server/lib" \
	"$ROOT_DIR/server/conf" \
	"$ROOT_DIR/server/database" \
	"$SEED_DATABASE" \
	"$TOOLS_JAR" \
	"$ROOT_DIR/tools/world-builder/schema" \
	"$ROOT_DIR/LICENSE" \
	"$ROOT_DIR/release/player/ASSET-SOURCES.txt" \
	"$PACKAGE_ASSETS/README.txt" \
	"$PACKAGE_ASSETS/ASSET-SOURCES.txt" \
	"$PACKAGE_ASSETS/world-builder-runtime.conf" \
	"$PACKAGE_ASSETS/Start World Builder.sh" \
	"$PACKAGE_ASSETS/Start World Builder.cmd" \
	"$PACKAGE_ASSETS/Import Map Changes.sh" \
	"$PACKAGE_ASSETS/Import Map Changes.cmd" \
	"$PACKAGE_ASSETS/Undo Last Map Import.sh" \
	"$PACKAGE_ASSETS/Undo Last Map Import.cmd"; do
	[[ -e "$required_path" ]] || fail "Missing release input: $required_path"
done

require_jar_entry() {
	local archive="$1" entry="$2" label="$3"
	jar tf "$archive" | grep -Fx "$entry" >/dev/null \
		|| fail "$label is missing required entry: $entry"
}

require_jar_entry "$CLIENT_JAR" "orsc/WorldBuilderClientProfile.class" "client jar"
require_jar_entry "$SERVER_JAR" "com/openrsc/server/content/worldedit/WorldEditStorageContext.class" "server jar"
require_jar_entry "$SERVER_JAR" "com/openrsc/server/content/worldedit/WorldBuilderRuntimeControl.class" "server jar"
require_jar_entry "$TOOLS_JAR" "com/openrsc/worldbuilder/WorldBuilderCli.class" "tools jar"

jar tf "$CLIENT_JAR" | grep '^myworld-assets/ui/world-editor/' >/dev/null \
	|| fail "Client jar is missing embedded World Builder UI assets"
for native_entry in \
	"linux/x64/org/lwjgl/liblwjgl.so" \
	"linux/x64/org/lwjgl/glfw/libglfw.so" \
	"linux/x64/org/lwjgl/opengl/liblwjgl_opengl.so" \
	"windows/x64/org/lwjgl/lwjgl.dll" \
	"windows/x64/org/lwjgl/glfw/glfw.dll" \
	"windows/x64/org/lwjgl/opengl/lwjgl_opengl.dll"; do
	require_jar_entry "$CLIENT_JAR" "$native_entry" "client jar"
done

server_protocol="$(sed -n 's/^[[:space:]]*client_version:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$ROOT_DIR/server/myworld.conf" | head -n 1)"
client_protocol="$(sed -n 's/.*CLIENT_VERSION[[:space:]]*=[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$ROOT_DIR/Client_Base/src/orsc/Config.java" | head -n 1)"
runtime_protocol="$(sed -n 's/^[[:space:]]*client_version:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$PACKAGE_ASSETS/world-builder-runtime.conf" | head -n 1)"
[[ -n "$server_protocol" && "$server_protocol" == "$client_protocol" && "$server_protocol" == "$runtime_protocol" ]] \
	|| fail "Client, server, and Builder runtime protocol versions disagree"

OUTPUT_DIR="$ROOT_DIR/output/releases/world-builder-v2/$VERSION"
STAGING_DIR="$OUTPUT_DIR/staging"
LINUX_STAGE="$STAGING_DIR/linux/$PACKAGE_NAME"
WINDOWS_STAGE="$STAGING_DIR/windows/$PACKAGE_NAME"
VERSION_NUMBER="${VERSION#v}"
LINUX_ARCHIVE="$OUTPUT_DIR/$ARTIFACT_PREFIX-$VERSION_NUMBER-linux-x64.zip"
WINDOWS_ARCHIVE="$OUTPUT_DIR/$ARTIFACT_PREFIX-$VERSION_NUMBER-windows-x64.zip"

rm -rf "$OUTPUT_DIR"
mkdir -p "$LINUX_STAGE" "$WINDOWS_STAGE" "$OUTPUT_DIR"

stage_builder() {
	local destination="$1"
	local runtime="$destination/builder-runtime"

	mkdir -p "$runtime/Client_Base/Cache" "$runtime/server/inc/sqlite" \
		"$runtime/launcher/schema" "$runtime/layered-world/package"
	cp "$CLIENT_JAR" "$runtime/Client_Base/Open_RSC_Client.jar"
	cp -R "$ROOT_DIR/Client_Base/Cache/audio" "$runtime/Client_Base/Cache/audio"
	cp -R "$ROOT_DIR/Client_Base/Cache/video" "$runtime/Client_Base/Cache/video"
	cp "$ROOT_DIR/Client_Base/Cache/config.txt" "$runtime/Client_Base/Cache/config.txt"
	cp "$SERVER_JAR" "$runtime/server/core.jar"
	cp "$PLUGINS_JAR" "$runtime/server/plugins.jar"
	cp -R "$ROOT_DIR/server/lib" "$runtime/server/lib"
	cp -R "$ROOT_DIR/server/conf" "$runtime/server/conf"
	cp -R "$ROOT_DIR/server/database" "$runtime/server/database"
	cp "$SEED_DATABASE" "$runtime/server/inc/sqlite/myworld_seed.db"
	for name in alertwords.txt badwords.txt goodwords.txt globalrules.txt ipbans.txt; do
		cp "$ROOT_DIR/server/$name" "$runtime/server/$name"
	done
	cp "$PACKAGE_ASSETS/world-builder-runtime.conf" "$runtime/server/myworld.conf"
	cp "$TOOLS_JAR" "$runtime/launcher/world-builder-tools.jar"
	cp -R "$ROOT_DIR/tools/world-builder/schema"/. "$runtime/launcher/schema/"
	cp -R "$LAYERED_PACKAGE"/. "$runtime/layered-world/package/"

	cp "$PACKAGE_ASSETS/Start World Builder.sh" "$destination/Start World Builder.sh"
	cp "$PACKAGE_ASSETS/Start World Builder.cmd" "$destination/Start World Builder.cmd"
	cp "$PACKAGE_ASSETS/Import Map Changes.sh" "$destination/Import Map Changes.sh"
	cp "$PACKAGE_ASSETS/Import Map Changes.cmd" "$destination/Import Map Changes.cmd"
	cp "$PACKAGE_ASSETS/Undo Last Map Import.sh" "$destination/Undo Last Map Import.sh"
	cp "$PACKAGE_ASSETS/Undo Last Map Import.cmd" "$destination/Undo Last Map Import.cmd"
	chmod +x "$destination/Start World Builder.sh" "$destination/Import Map Changes.sh" \
		"$destination/Undo Last Map Import.sh"
	sed "s/@VERSION@/$VERSION/g; s/@SOURCE_COMMIT@/$SOURCE_COMMIT/g" \
		"$PACKAGE_ASSETS/README.txt" > "$destination/README.txt"
	cp "$ROOT_DIR/LICENSE" "$destination/LICENSE"
	cp "$PACKAGE_ASSETS/ASSET-SOURCES.txt" "$destination/ASSET-SOURCES.txt"
	cp "$ROOT_DIR/release/player/ASSET-SOURCES.txt" "$destination/PLAYER-ASSET-SOURCES.txt"
	cp "$ICON_CREDITS" "$destination/EDITOR-ICON-CREDITS.txt"
	printf '%s\n' "$VERSION" > "$destination/VERSION.txt"
	printf '%s\n' "$SOURCE_COMMIT" > "$destination/SOURCE-COMMIT.txt"
	cat > "$destination/RELEASE-IDENTITY.json" <<EOF
{
  "schemaVersion": 1,
  "productId": "$PRODUCT_ID",
  "productGeneration": $PRODUCT_GENERATION,
  "displayName": "$PACKAGE_NAME",
  "updateChannel": "$UPDATE_CHANNEL",
  "releaseTag": "$ARTIFACT_PREFIX-$VERSION_NUMBER",
  "artifactPrefix": "$ARTIFACT_PREFIX",
  "worldCoordinateModel": "signed-layered-v1",
  "automaticUpgradeFromProductIds": [
    "$PRODUCT_ID"
  ],
  "legacyProductId": "$LEGACY_PRODUCT_ID",
  "legacyFinalTag": "$LEGACY_FINAL_TAG",
  "legacyWorkspaceMigration": false,
  "version": "$VERSION",
  "sourceCommit": "$SOURCE_COMMIT"
}
EOF
}

stage_builder "$LINUX_STAGE"
stage_builder "$WINDOWS_STAGE"
mkdir -p "$LINUX_STAGE/runtime"
cp -R "$LINUX_JRE"/. "$LINUX_STAGE/runtime/"
mkdir -p "$WINDOWS_STAGE/runtime"
cp -R "$WINDOWS_JRE"/. "$WINDOWS_STAGE/runtime/"

for stage in "$LINUX_STAGE" "$WINDOWS_STAGE"; do
	if find "$stage" -type f | grep -E '/(workspace|exports|backups|receipts|logs)/|world_builder\.db$|world-builder\.credential$|credentials\.txt$|uid\.dat$|clientSettings\.conf$|/ip\.txt$|/port\.txt$' >/dev/null; then
		fail "Staged World Builder package contains generated project, credential, identity, or endpoint state"
	fi
done

require_release_git_state "$SOURCE_COMMIT"

(
	cd "$STAGING_DIR/linux"
	zip -qr "$LINUX_ARCHIVE" "$PACKAGE_NAME"
)
(
	cd "$STAGING_DIR/windows"
	zip -qr "$WINDOWS_ARCHIVE" "$PACKAGE_NAME"
)
(
	cd "$OUTPUT_DIR"
	sha256sum "$(basename "$LINUX_ARCHIVE")" "$(basename "$WINDOWS_ARCHIVE")" > SHA256SUMS.txt
)

rm -rf "$STAGING_DIR"

printf 'Created World Builder 2 release artifacts:\n'
printf '  %s\n' "$LINUX_ARCHIVE"
printf '  %s\n' "$WINDOWS_ARCHIVE"
printf '  %s\n' "$OUTPUT_DIR/SHA256SUMS.txt"
