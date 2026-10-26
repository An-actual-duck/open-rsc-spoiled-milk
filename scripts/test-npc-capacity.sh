#!/usr/bin/env bash
# Offline-only gate: never starts Server, client, listeners or owner-map operations.
set -euo pipefail
NPC_TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$NPC_TASK_ROOT"
./scripts/build-server.sh
./scripts/build-client.sh
python3 tests/myworld/test-entity-list-safety.py
python3 tests/myworld/test-npc-capacity-production.py
python3 tests/myworld/test-npc-overlap-layout.py
python3 tests/myworld/test-packet-shape-guards.py
python3 tests/myworld/test-client-custom-movement-stability.py
python3 tests/myworld/test-layered-spatial-runtime-authority.py
python3 tests/myworld/test-layered-protocol-client-authority.py
python3 tests/myworld/test-scene-entity-lifecycle.py
python3 tests/myworld/test-monster-slayer-client-npc-definitions.py
./server/test_combat test_network_encoder test_network_decoder test_slow test_abyssal_demon_combat test_dark_beast_combat test_bloodveld_combat test_terror_dog_combat test_naga_combat
