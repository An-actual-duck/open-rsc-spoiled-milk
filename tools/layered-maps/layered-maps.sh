#!/usr/bin/env bash
set -euo pipefail

TOOL_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "${TOOL_ROOT}/../.." && pwd)"
CLASSES="${TOOL_ROOT}/build/classes"
COMMAND="${1:-preflight}"
WORKSPACE="${TOOL_ROOT}/workspace/${COMMAND}"

if [[ $# -gt 0 ]]; then
  shift
fi

mkdir -p "${CLASSES}"
find "${TOOL_ROOT}/src" -name '*.java' -print0 \
  | sort -z \
  | xargs -0 javac -source 8 -target 8 -encoding UTF-8 -d "${CLASSES}"

exec java -cp "${CLASSES}" com.openrsc.layeredmaps.LayeredMapsCli \
  "${COMMAND}" --root "${REPOSITORY_ROOT}" --workspace "${WORKSPACE}" "$@"
