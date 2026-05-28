#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

"$ROOT_DIR/tools/benchmarks/benchmark-foundation-matrix.sh" "$@"
