#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

cd "$ROOT_DIR"

renderer_tests=(
  test-client-cpu-section-window-cache.py
  test-client-predictive-terrain-preload.py
  test-client-world-model-product-split.py
  test-client-world-streaming-backend.py
  test-desktop-graphics-options-tab.py
  test-opengl-geometry-modes.py
	test-opengl-below-terrain.py
  test-opengl-input-modifiers.py
	test-opengl-world-sky.py
  test-opengl-spirit-summon-alpha.py
  test-opengl-window-viewport-extraction.py
  test-renderer-2d-overflow-telemetry.py
  test-renderer-diagnostic-session.py
  test-renderer-diagnostic-session-analyzer.py
  test-renderer-experimental-camera-options.py
  test-renderer-material-family.py
  test-renderer-shading-diagnostics.py
  test-renderer-settings-panel-extraction.py
  test-renderer-profile-applier.py
  test-legacy-software-scaling-settings.py
  test-client-external-asset-loader.py
  test-client-scene-instance-store.py
  test-roof-visibility.py
  test-renderer-relog-resident-world.py
  test-renderer-v2-capture-analyzer.py
  test-renderer-v2-font-policy.py
  test-renderer-v2-frame-capture.py
  test-renderer-v2-lwjgl-hot-handles.py
  test-renderer-v2-object-chunk-primitive-builder.py
  test-renderer-v2-options-cleanup.py
  test-renderer-v2-phased-overlay.py
  test-renderer-v2-shadow-inventory-cache.py
  test-renderer-v2-sprite-ownership.py
  test-renderer-v2-world-geometry.py
)

for test_file in "${renderer_tests[@]}"; do
  python3 "./tests/myworld/$test_file"
done
