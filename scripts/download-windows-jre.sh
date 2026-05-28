#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_DIR="${ROOT_DIR:-$SCRIPT_ROOT}"
OUTPUT_DIR="${1:-$ROOT_DIR/output/runtimes}"

JAVA_VERSION="17"
RUNTIME_NAME="temurin-${JAVA_VERSION}-windows-x64-jre"
API_URL="https://api.adoptium.net/v3/binary/latest/${JAVA_VERSION}/ga/windows/x64/jre/hotspot/normal/eclipse"

fail() {
  printf 'FAIL: %s\n' "$*" >&2
  exit 1
}

for command_name in curl unzip sha256sum; do
  command -v "$command_name" >/dev/null 2>&1 || fail "Missing dependency: $command_name"
done

mkdir -p "$OUTPUT_DIR"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

ARCHIVE="$TEMP_DIR/$RUNTIME_NAME.zip"
UPSTREAM_CHECKSUM_FILE="$TEMP_DIR/$RUNTIME_NAME.zip.sha256.txt"
EXTRACT_DIR="$TEMP_DIR/extracted"
DEST_DIR="$OUTPUT_DIR/$RUNTIME_NAME"
CHECKSUM_FILE="$OUTPUT_DIR/$RUNTIME_NAME.zip.sha256"

printf 'Downloading Eclipse Temurin %s Windows x64 JRE from Adoptium...\n' "$JAVA_VERSION"
FETCH_URL="$(curl --fail --show-error --silent --write-out '%{redirect_url}' --output /dev/null "$API_URL")"
[[ -n "$FETCH_URL" ]] || fail "Unable to resolve Adoptium download URL"

curl --fail --location --show-error --output "$ARCHIVE" "$FETCH_URL"
CHECKSUM_URL="$FETCH_URL.sha256.txt"
curl --fail --location --show-error --silent --output "$UPSTREAM_CHECKSUM_FILE" "$CHECKSUM_URL"
expected_checksum="$(sed -n 's/^\([0-9a-fA-F]\{64\}\).*/\1/p' "$UPSTREAM_CHECKSUM_FILE" | head -n 1)"
actual_checksum="$(sha256sum "$ARCHIVE")"
actual_checksum="${actual_checksum%% *}"
[[ -n "$expected_checksum" ]] || fail "Unable to read upstream SHA-256 checksum"
[[ "$actual_checksum" == "$expected_checksum" ]] || fail "Downloaded archive checksum did not match Adoptium checksum"
printf '%s  %s\n' "$actual_checksum" "$RUNTIME_NAME.zip" > "$CHECKSUM_FILE"

mkdir -p "$EXTRACT_DIR"
unzip -q "$ARCHIVE" -d "$EXTRACT_DIR"

RUNTIME_DIR=""
while IFS= read -r -d '' candidate; do
  if [[ -f "$candidate/bin/java.exe" && -f "$candidate/release" ]] \
    && [[ -f "$candidate/LICENSE" || -f "$candidate/NOTICE" || -f "$candidate/legal/java.base/LICENSE" ]]; then
    RUNTIME_DIR="$candidate"
    break
  fi
done < <(find "$EXTRACT_DIR" -mindepth 1 -type d -print0)

[[ -n "$RUNTIME_DIR" ]] || fail "Downloaded archive did not contain a Windows JRE layout"

runtime_version="$(sed -n 's/^JAVA_VERSION="\([^"]*\)".*/\1/p' "$RUNTIME_DIR/release" | head -n 1)"
[[ -n "$runtime_version" ]] || fail "Unable to read JAVA_VERSION from downloaded runtime"
if [[ "$runtime_version" == 1.* ]]; then
  runtime_major="${runtime_version#1.}"
  runtime_major="${runtime_major%%.*}"
else
  runtime_major="${runtime_version%%.*}"
fi
[[ "$runtime_major" =~ ^[0-9]+$ ]] && ((runtime_major >= 17)) \
  || fail "Downloaded runtime must be Java 17+; found $runtime_version"

rm -rf "$DEST_DIR"
mv "$RUNTIME_DIR" "$DEST_DIR"

printf 'Prepared Windows JRE:\n'
printf '  %s\n' "$DEST_DIR"
printf 'Archive checksum recorded at:\n'
printf '  %s\n' "$CHECKSUM_FILE"
