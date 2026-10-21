#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR/server"
export ANT_OPTS="${ANT_OPTS:--Xmx512m -XX:+UseSerialGC}"
bash ../tools/vendor/apache-ant-1.10.5/bin/ant test_configurable_agility
