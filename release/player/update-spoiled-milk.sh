#!/usr/bin/env sh
set -eu

REPO="An-actual-duck/open-rsc-spoiled-milk"
CURRENT_VERSION="@VERSION@"
PACKAGE_KIND="java"
GAME_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
API_URL="https://api.github.com/repos/$REPO/releases"

need_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    printf 'Missing required command: %s\n' "$1" >&2
    exit 1
  fi
}

find_latest_alpha_version() {
  latest_version=""
  latest_alpha=-1
  for version in $(printf '%s\n' "$1" | sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\(v[0-9][^"]*-alpha\.[0-9][0-9]*\)".*/\1/p'); do
    alpha="${version##*-alpha.}"
    case "$alpha" in
      *[!0-9]*|"")
        continue
        ;;
    esac
    if [ "$alpha" -gt "$latest_alpha" ]; then
      latest_alpha="$alpha"
      latest_version="$version"
    fi
  done
  printf '%s\n' "$latest_version"
}

need_command curl
need_command unzip

printf 'Checking for Spoiled Milk updates...\n'
release_json="$(curl -fsSL "$API_URL")"
latest_version="$(find_latest_alpha_version "$release_json")"

if [ -z "$latest_version" ]; then
  printf 'Unable to determine the latest release from GitHub.\n' >&2
  exit 1
fi

current_alpha="${CURRENT_VERSION##*-alpha.}"
latest_alpha="${latest_version##*-alpha.}"
if [ "$latest_version" = "$CURRENT_VERSION" ] \
  || { [ "$current_alpha" != "$CURRENT_VERSION" ] && [ "$latest_alpha" != "$latest_version" ] \
    && [ "$latest_alpha" -le "$current_alpha" ]; }; then
  printf 'Spoiled Milk is up to date (%s).\n' "$CURRENT_VERSION"
  exit 0
fi

asset_name="spoiled-milk-$latest_version-$PACKAGE_KIND.zip"
download_url="https://github.com/$REPO/releases/download/$latest_version/$asset_name"

printf '%s\n' "$release_json" | grep "\"name\"[[:space:]]*:[[:space:]]*\"$asset_name\"" >/dev/null \
  || { printf 'Latest release %s does not include %s.\n' "$latest_version" "$asset_name" >&2; exit 1; }

update_dir="$GAME_DIR/updates"
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

cp -R "$package_root/." "$GAME_DIR/"
rm -rf "$extract_dir"

printf 'Updated Spoiled Milk from %s to %s.\n' "$CURRENT_VERSION" "$latest_version"
