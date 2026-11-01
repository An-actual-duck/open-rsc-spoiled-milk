#!/usr/bin/env sh
set -eu

REPO="An-actual-duck/open-rsc-spoiled-milk"
CURRENT_VERSION="@VERSION@"
PACKAGE_KIND="java"
PAYLOAD_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
INSTALL_DIR="$(CDPATH= cd -- "$PAYLOAD_DIR/.." && pwd)"
API_URL="https://api.github.com/repos/$REPO/releases"

need_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'Missing required command: %s\n' "$1" >&2
    exit 1
  fi
}

sort_versions() {
  # Compare numeric components before prerelease status. Length-prefixed digits
  # avoid numeric overflow and work with the POSIX sort shipped on macOS too.
  awk '
    function number_key(value) {
      sub(/^0+/, "", value)
      if (value == "") value = "0"
      return sprintf("%06d:%s", length(value), value)
    }
    {
      original = $0
      sub(/^v/, "")
      count = split($0, parts, /[.-]/)
      stable = count == 3 ? 1 : 0
      print number_key(parts[1]) "." number_key(parts[2]) "." number_key(parts[3]) "." stable "." number_key(parts[5]) " " original
    }
  ' | sort -k1,1 | cut -d ' ' -f2
}

find_latest_version() {
  printf '%s\n' "$1" \
    | sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\(v[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*\(-alpha\.[0-9][0-9]*\)\?\)".*/\1/p' \
    | sort_versions \
    | tail -n 1
}

need_command curl
need_command unzip
need_command sort
need_command awk

printf 'Checking for Spoiled Milk updates...\n'
release_json="$(curl -fsSL "$API_URL")"
latest_version="$(find_latest_version "$release_json")"

if [ -z "$latest_version" ]; then
  printf 'Unable to determine the latest version from GitHub.\n' >&2
  exit 1
fi

if [ "$latest_version" = "$CURRENT_VERSION" ]; then
  printf 'Spoiled Milk is up to date (%s).\n' "$CURRENT_VERSION"
  exit 0
fi

newest_version="$(printf '%s\n' "$CURRENT_VERSION" "$latest_version" | sort_versions | tail -n 1)"
if [ "$newest_version" = "$CURRENT_VERSION" ]; then
  printf 'Installed version %s is newer than %s; keeping it.\n' "$CURRENT_VERSION" "$latest_version"
  exit 0
fi

asset_name="spoiled-milk-$latest_version-$PACKAGE_KIND.zip"
download_url="https://github.com/$REPO/releases/download/$latest_version/$asset_name"

printf '%s\n' "$release_json" | grep "\"name\"[[:space:]]*:[[:space:]]*\"$asset_name\"" >/dev/null \
  || { printf 'Latest version %s does not include %s.\n' "$latest_version" "$asset_name" >&2; exit 1; }

update_dir="$PAYLOAD_DIR/updates"
archive="$update_dir/$asset_name"
extract_dir="$update_dir/extracted"
mkdir -p "$update_dir"
rm -rf "$extract_dir"

printf 'Downloading %s...\n' "$asset_name"
curl -fL "$download_url" -o "$archive"

printf 'Installing %s...\n' "$latest_version"
mkdir -p "$extract_dir"
unzip -q "$archive" -d "$extract_dir"
package_root="$extract_dir/spoiled-milk-$latest_version-$PACKAGE_KIND"
if [ ! -d "$package_root" ]; then
  printf 'Downloaded package did not contain expected folder: %s\n' "$package_root" >&2
  exit 1
fi

cp -R "$package_root/." "$INSTALL_DIR/"
rm -f \
  "$INSTALL_DIR/Spoiled_Milk_Client.jar" \
  "$INSTALL_DIR/update-spoiled-milk.sh" \
  "$INSTALL_DIR/update-spoiled-milk.ps1" \
  "$INSTALL_DIR/Update Spoiled Milk.cmd" \
  "$INSTALL_DIR/ASSET-SOURCES.txt" \
  "$INSTALL_DIR/VERSION.txt" \
  "$INSTALL_DIR/LICENSE"
rm -rf "$INSTALL_DIR/Cache" "$INSTALL_DIR/runtime" "$INSTALL_DIR/updates"
rm -rf "$extract_dir"

printf 'Updated Spoiled Milk from %s to %s.\n' "$CURRENT_VERSION" "$latest_version"
