#!/usr/bin/env python3
"""Contracts for the deterministic projectile-family performance fixture."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
SERVER = (ROOT / "server/src/com/openrsc/server/Server.java").read_text()
FIXTURE = (
    ROOT / "server/src/com/openrsc/server/diagnostics/ProjectileCombatBenchmark.java"
).read_text()
SCRIPT = (ROOT / "tools/benchmarks/benchmark-projectile-combat.sh").read_text()


class ProjectileCombatBenchmarkContractTest(unittest.TestCase):
    def test_fixture_is_explicit_listener_free_and_melee_isolated(self):
        self.assertIn('"openrsc.benchmarkProjectileCombatGroups", 0', SERVER)
        self.assertIn(
            "Projectile combat workload requires foundation benchmark mode", SERVER
        )
        self.assertIn("Foundation benchmark mode: network listeners are disabled", SERVER)
        self.assertIn("isolateBenchmarkNpcCohort();", FIXTURE)
        self.assertIn("class PassiveProjectileTarget extends Npc", FIXTURE)
        self.assertIn("void startPvmCounterCombat", FIXTURE)

    def test_each_family_uses_production_combat_and_projectile_events(self):
        self.assertIn("new RangeEvent", FIXTURE)
        self.assertIn("MagicCombatEvent.start", FIXTURE)
        self.assertIn("new ThrowingEvent", FIXTURE)
        self.assertIn("ItemId.TIN_SHURIKEN", FIXTURE)
        self.assertIn("Spells.WIND_STRIKE", FIXTURE)
        self.assertIn("event instanceof RangeEvent", SERVER)
        self.assertIn("event instanceof MagicCombatEvent", SERVER)
        self.assertIn("event instanceof ThrowingEvent", SERVER)
        self.assertIn("event instanceof ProjectileEvent", SERVER)

    def test_real_plugin_work_and_deterministic_random_are_retained(self):
        self.assertIn("handlePlugin(TimedEventTrigger.class", FIXTURE)
        self.assertIn("DeterministicGameRandom", FIXTURE)
        self.assertIn("DataConversions.getRandom().setSeed", FIXTURE)
        self.assertIn("projectileCombatRandomDraws", FIXTURE)

    def test_runner_repeats_each_family_with_disposable_state(self):
        self.assertIn("for family in ranged magic multi", SCRIPT)
        self.assertIn("BENCHMARK_REPETITIONS:-2", SCRIPT)
        self.assertIn("myworld_seed.db", SCRIPT)
        self.assertIn("rm -f \"$config\" \"$database\"", SCRIPT)
        self.assertIn("projectileCombatInvariant=pass", SCRIPT)
        self.assertIn("projectileCombatDeterminism", SCRIPT)
        self.assertIn("benchmark outcomes diverged between runs", SCRIPT)

    def test_runner_uses_hosted_spatial_contract_without_a_listener(self):
        self.assertIn("-Dopenrsc.layeredPlayerLocationAuthority=true", SCRIPT)
        self.assertIn("-Dopenrsc.layeredSpatialRuntimeAuthority=true", SCRIPT)
        self.assertIn("-Dopenrsc.benchmarkSyntheticClientVersion=10052", SCRIPT)


if __name__ == "__main__":
    unittest.main()
