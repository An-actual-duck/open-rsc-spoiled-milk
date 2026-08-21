#!/usr/bin/env python3
"""Focused contracts for the deterministic active-combat performance fixture."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[2]
SERVER = (ROOT / "server/src/com/openrsc/server/Server.java").read_text()
FIXTURE = (
    ROOT / "server/src/com/openrsc/server/diagnostics/ActiveCombatBenchmark.java"
).read_text()
EVENT_HANDLER = (
    ROOT
    / "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
).read_text()
SCRIPT = (ROOT / "tools/benchmarks/benchmark-active-combat.sh").read_text()


class ActiveCombatBenchmarkContractTest(unittest.TestCase):
    def test_fixture_is_explicit_and_listener_free(self):
        self.assertIn('"openrsc.benchmarkActiveCombatPairs", 0', SERVER)
        self.assertIn("Active combat workload requires foundation benchmark mode", SERVER)
        self.assertIn("Foundation benchmark mode: network listeners are disabled", SERVER)

    def test_combat_uses_authoritative_transaction_and_production_events(self):
        self.assertIn("player.getAttackTransaction().issue", FIXTURE)
        self.assertIn("player.getAttackTransaction()", FIXTURE)
        self.assertIn("player.startCombat(npc)", FIXTURE)
        self.assertIn("DataConversions.getRandom().setSeed", FIXTURE)
        self.assertNotIn("handlePlugin(AttackNpcTrigger.class", FIXTURE)
        self.assertIn("activeCombatEngagedPairs", FIXTURE)

    def test_plugin_workload_uses_real_dispatch_without_gameplay_mutation(self):
        self.assertIn("handlePlugin(TimedEventTrigger.class", FIXTURE)
        self.assertIn("INTERACTION_PLAYERS_PER_TICK_DIVISOR", FIXTURE)
        self.assertIn("activeCombatPluginDispatches", FIXTURE)

    def test_event_costs_are_measured_after_execution_only_in_benchmark_mode(self):
        call = EVENT_HANDLER.index("event.call();")
        record = EVENT_HANDLER.index("recordFoundationBenchmarkEvent(event)", call)
        self.assertGreater(record, call)
        self.assertIn("event instanceof PvmMeleeEvent", SERVER)
        self.assertIn("event instanceof PluginTickEvent", SERVER)

    def test_runner_requires_repeatable_outcomes(self):
        self.assertIn("BENCHMARK_REPETITIONS:-2", SCRIPT)
        self.assertIn("-Dopenrsc.layeredPlayerLocationAuthority=true", SCRIPT)
        self.assertIn("-Dopenrsc.layeredSpatialRuntimeAuthority=true", SCRIPT)
        self.assertIn("activeCombatInvariant=pass", SCRIPT)
        self.assertIn("activeCombatDeterminism", SCRIPT)
        self.assertIn("benchmark outcomes diverged between runs", SCRIPT)


if __name__ == "__main__":
    unittest.main()
