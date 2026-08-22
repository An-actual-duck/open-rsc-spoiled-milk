#!/usr/bin/env python3
"""Contracts for deterministic NPC roaming performance evidence."""

from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SERVER = (ROOT / "server/src/com/openrsc/server/Server.java").read_text()
FIXTURE = (ROOT / "server/src/com/openrsc/server/diagnostics/NpcRoamingBenchmark.java").read_text()
BEHAVIOR = (ROOT / "server/src/com/openrsc/server/model/entity/npc/NpcBehavior.java").read_text()
SCRIPT = (ROOT / "tools/benchmarks/benchmark-npc-roaming.sh").read_text()
INDEX = (ROOT / "server/src/com/openrsc/server/model/world/region/LayeredSpatialEntityIndex.java").read_text()
REGIONS = (ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java").read_text()


class NpcRoamingBenchmarkContractTest(unittest.TestCase):
    def test_explicit_listener_free_deterministic_activation(self):
        self.assertIn('"openrsc.benchmarkNpcRoamingCount", 0', SERVER)
        self.assertIn("NPC roaming workload requires foundation benchmark mode", SERVER)
        self.assertIn("DeterministicGameRandom", SERVER)
        self.assertIn("isolateBenchmarkNpcCohort();", FIXTURE)

    def test_fixture_controls_requested_roaming_inputs(self):
        self.assertIn("prepareBenchmarkRoamCadence", FIXTURE)
        self.assertIn("due ? Long.MIN_VALUE : Long.MAX_VALUE", BEHAVIOR)
        self.assertIn("cohort == 0 ? 8 : 1", FIXTURE)
        self.assertIn("cohort == 2 ? -1 : 0", FIXTURE)
        self.assertIn("addBlockingScenery", FIXTURE)

    def test_production_paths_and_layered_contract_are_exercised(self):
        self.assertIn("server.getWorld().registerNpc(npc)", FIXTURE)
        self.assertIn("server.getWorld().registerGameObject", FIXTURE)
        self.assertIn("player.setInitialLayeredLocation(location)", FIXTURE)
        self.assertIn("ClientLimitations.forVersion(clientVersion)", FIXTURE)

    def test_runner_repeats_and_rejects_state_drift(self):
        self.assertIn("REPETITIONS:-2", SCRIPT)
        self.assertIn("myworld_seed.db", SCRIPT)
        self.assertIn("npcRoamingInvariant=pass", SCRIPT)
        self.assertIn("npcRoamingDeterminism", SCRIPT)
        self.assertIn("outcomes diverged", SCRIPT)
        self.assertIn("-Dopenrsc.layeredSpatialRuntimeAuthority=true", SCRIPT)
        self.assertIn("want_npc_idle_tick_throttle", SCRIPT)
        self.assertIn("want_custom_walking_speed", SCRIPT)

    def test_exact_collision_lookups_do_not_snapshot_mixed_regions(self):
        self.assertIn("public Npc findNpcAt", INDEX)
        self.assertIn("public Player findPlayerAt", INDEX)
        npc_lookup = REGIONS.split("Npc findLayeredNpc", 1)[1].split(
            "Player findLayeredPlayer", 1
        )[0]
        player_lookup = REGIONS.split("Player findLayeredPlayer", 1)[1].split(
            "GroundItem findLayeredGroundItem", 1
        )[0]
        self.assertIn("layeredSpatialEntityIndex.findNpcAt", npc_lookup)
        self.assertIn("layeredSpatialEntityIndex.findPlayerAt", player_lookup)
        self.assertNotIn("snapshot(", npc_lookup)
        self.assertNotIn("snapshot(", player_lookup)


if __name__ == "__main__":
    unittest.main()
