#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUTPUT_DIR="$ROOT_DIR/output/navigation"
TAGS_FILE="$OUTPUT_DIR/tags"

if ! command -v ctags >/dev/null 2>&1; then
  echo "Universal Ctags is not installed; no navigation index was generated." >&2
  echo "Install it through your normal local environment if desired, then rerun this helper." >&2
  exit 1
fi

CTAGS_VERSION="$(ctags --version 2>/dev/null | head -n 1 || true)"
if [[ "$CTAGS_VERSION" != *"Universal Ctags"* ]]; then
  echo "Universal Ctags is required; found: ${CTAGS_VERSION:-unknown ctags}" >&2
  exit 1
fi

mkdir -p "$OUTPUT_DIR"
rm -f "$TAGS_FILE"

ctags \
  --languages=Java \
  --fields=+n \
  --extras=+q \
  --recurse \
  --output-format=e-ctags \
  -f "$TAGS_FILE" \
  "$ROOT_DIR/Client_Base/src" \
  "$ROOT_DIR/PC_Client/src" \
  "$ROOT_DIR/server/src" \
  "$ROOT_DIR/server/plugins" \
  "$ROOT_DIR/server/test"

echo "Generated local Java navigation tags: $TAGS_FILE"
echo "This disposable index is ignored by Git and is not a release input."
