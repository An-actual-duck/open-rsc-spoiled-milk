#!/usr/bin/env bash
set -euo pipefail

SCRIPT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_DIR="${ROOT_DIR:-$SCRIPT_ROOT}"

"$ROOT_DIR/tools/benchmarks/benchmark-foundation-matrix.sh" "$@"
