#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
cd "$ROOT_DIR"

./scripts/check.sh
python3 ./tests/myworld/audit_client_item_coverage.py
python3 ./tests/myworld/audit-item-id-integrity.py
python3 ./tests/myworld/audit-client-sprite-references.py
python3 ./tests/myworld/test-content-item-resolution.py
python3 ./tests/myworld/test-magic-no-random-fail.py
python3 ./tests/myworld/test-magic-enchanting-costs.py
python3 ./tests/myworld/audit-debug-output.py
./tests/myworld/test-smoke.sh
make combat-check
