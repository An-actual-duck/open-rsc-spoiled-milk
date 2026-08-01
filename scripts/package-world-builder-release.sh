#!/usr/bin/env bash
set -euo pipefail

printf '%s\n' \
	'FAIL: The legacy RSC World Editor v1 release line is frozen at v1.1.0.' \
	'Current World Builder sources use signed layered maps and must never be packaged on the v1 product/update channel.' \
	'Use scripts/package-world-builder-v2-release.sh for the separate World Builder 2 product line.' >&2
exit 1
