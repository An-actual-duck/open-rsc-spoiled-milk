#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_DIR="${ROOT_DIR:-$SCRIPT_ROOT}"
# shellcheck source=scripts/lib/layered-world-package.sh
source "$SCRIPT_ROOT/scripts/lib/layered-world-package.sh"

VERSION=""

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage:
  ./scripts/package-layered-world-release.sh --version v0.2.59

Creates the exact validated Spoiled Milk layered-world server artifact beside
the player archives and adds all three ZIP files to SHA256SUMS.txt.
EOF
}

while (($#)); do
  case "$1" in
    --version)
      [[ $# -ge 2 ]] || fail "--version requires a value"
      VERSION="$2"
      shift 2
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
  || fail "Version must use semantic form, for example v0.2.59"

for command_name in git java javac python3 sha256sum zip; do
  command -v "$command_name" >/dev/null 2>&1 \
    || fail "Missing release dependency: $command_name"
done

current_branch="$(git -C "$ROOT_DIR" symbolic-ref --quiet --short HEAD 2>/dev/null || true)"
[[ "$current_branch" == main ]] \
  || fail "Layered-world packaging must run from manager branch main"
worktree_status="$(git -C "$ROOT_DIR" status --porcelain --untracked-files=all)"
[[ -z "$worktree_status" ]] \
  || fail "Layered-world packaging requires a clean manager main worktree"
source_commit="$(git -C "$ROOT_DIR" rev-parse HEAD)"
published_commit="$(git -C "$ROOT_DIR" rev-parse --verify 'spoiled-milk/main^{commit}' 2>/dev/null || true)"
[[ -n "$published_commit" && "$source_commit" == "$published_commit" ]] \
  || fail "Layered-world packaging requires HEAD to match spoiled-milk/main"

output_dir="$ROOT_DIR/output/releases/$VERSION"
java_archive="$output_dir/spoiled-milk-$VERSION-java.zip"
windows_archive="$output_dir/spoiled-milk-$VERSION-windows-x64.zip"
for player_archive in "$java_archive" "$windows_archive"; do
  [[ -f "$player_archive" ]] \
    || fail "Create the player release first; missing $player_archive"
done

workspace="$ROOT_DIR/tools/layered-maps/workspace/release-$VERSION"
package_root="$(layered_world_generate_package "$ROOT_DIR" "$workspace")"
layered_world_require_promotion_approved "$workspace/generation-report.json"
staging_dir="$output_dir/staging-layered"
package_name="spoiled-milk-$VERSION-layered-world"
stage="$staging_dir/$package_name"
archive="$output_dir/$package_name.zip"

rm -rf "$staging_dir"
mkdir -p "$stage"
cp -a "$package_root" "$stage/package"
cp "$workspace/generation-report.json" "$stage/GENERATION-REPORT.json"
cp "$workspace/package-validation.json" "$stage/PACKAGE-VALIDATION.json"
cp "$ROOT_DIR/LICENSE" "$stage/LICENSE"
printf '%s\n' "$VERSION" > "$stage/VERSION.txt"
printf '%s\n' "$source_commit" > "$stage/SOURCE-COMMIT.txt"
printf '%s\n' "$SPOILED_MILK_LAYERED_MANIFEST_SHA256" \
  > "$stage/MANIFEST-SHA256.txt"
printf '%s\n' "$SPOILED_MILK_LAYERED_PACKAGE_FINGERPRINT" \
  > "$stage/PACKAGE-FINGERPRINT.txt"
cat > "$stage/README.txt" <<EOF
Spoiled Milk layered world for $VERSION

This is the server-side rsc-remastered layered-world package. Player clients
do not install it directly. The guarded Spoiled Milk deployment installs and
validates this package before enabling the layered runtime.

Package: $SPOILED_MILK_LAYERED_PACKAGE_ID@$SPOILED_MILK_LAYERED_PACKAGE_VERSION
Manifest SHA-256: $SPOILED_MILK_LAYERED_MANIFEST_SHA256
Package fingerprint: $SPOILED_MILK_LAYERED_PACKAGE_FINGERPRINT
Source commit: $source_commit
EOF

(
  cd "$staging_dir"
  zip -qr "$archive" "$package_name"
)
rm -rf "$staging_dir"

(
  cd "$output_dir"
  sha256sum \
    "$(basename "$java_archive")" \
    "$(basename "$windows_archive")" \
    "$(basename "$archive")" \
    > SHA256SUMS.txt
)

printf 'Created layered-world release artifact:\n'
printf '  %s\n' "$archive"
printf '  %s\n' "$output_dir/SHA256SUMS.txt"
