#!/usr/bin/env python3
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/GameTickEvent.java"
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
TIMING_FIXTURE = ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-four.py"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceOneHundredSevenTest(unittest.TestCase):
    def test_callback_execution_is_not_inside_the_timing_monitor(self):
        source = EVENT.read_text(encoding="utf-8")
        boundary = source[
            source.index("public final long doRun()"):
            source.index("@Override", source.index("public final long doRun()"))
        ]
        first_timing = boundary.index("synchronized (timingLock)")
        callback = boundary.index("run();")
        second_timing = boundary.index(
            "synchronized (timingLock)", first_timing + 1
        )
        self.assertLess(first_timing, callback)
        self.assertLess(callback, second_timing)
        self.assertIn("Never hold the timing monitor", boundary)

    def test_private_execution_lock_retains_do_run_serialization(self):
        source = EVENT.read_text(encoding="utf-8")
        boundary = source[
            source.index("public final long doRun()"):
            source.index("@Override", source.index("public final long doRun()"))
        ]
        self.assertIn("private final Object executionLock", source)
        self.assertIn("synchronized (executionLock)", boundary)
        self.assertEqual(1, boundary.count("run();"))
        self.assertEqual(1, boundary.count("timesRan++;"))
        self.assertEqual(1, boundary.count("ticksBeforeRun = delayTicks;"))

    def test_executable_fixture_recreates_the_foreign_lock_cycle(self):
        fixture = TIMING_FIXTURE.read_text(encoding="utf-8")
        self.assertIn("foreignCallbackLock", fixture)
        self.assertIn("foreignLockOwned", fixture)
        self.assertIn("await(event.entered)", fixture)
        self.assertIn("event.captureAtomicTimingSnapshot()", fixture)
        self.assertIn("timing capture breaks the callback lock inversion", fixture)
        self.assertIn("active.getTicksBeforeRun() == 0L", fixture)

    def test_observer_remains_detached_and_without_execution_authority(self):
        source = OBSERVER.read_text(encoding="utf-8")
        boundary = source[
            source.index("private static void appendPackedRegionEventOwnership("):
            source.index("private static void appendIntegerList(")
        ]
        self.assertIn("isAtomicTimingComplete()", boundary)
        for forbidden in (
            "executionLock",
            "timingLock",
            "captureAtomicTimingSnapshot",
            "GameTickEventStore",
            ".stop()",
            "doRun()",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_living_plan_records_slice_one_hundred_seven_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 107: Atomic timing callback-lock deadlock hardening",
            plan,
        )
        self.assertIn("PluginThread-0", plan)
        self.assertIn("No callback is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
