#!/usr/bin/env python3
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
INVENTORY = COORDINATES / "LayeredPackedRegionEventOwnershipInventory.java"
HANDLER = ROOT / "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
SCHEMA_V36 = ROOT / "tools/layered-maps/schema/layered-map-parity-event-v36.schema.json"
STORE_FIXTURE = ROOT / "tests/myworld/test-layered-maps-slice-ninety-five.py"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


POINT_STUB = r'''
package com.openrsc.server.model;
public class Point {
    private final int x;
    private final int y;
    public Point(int x, int y) { this.x = x; this.y = y; }
    public static Point location(int x, int y) { return new Point(x, y); }
    public int getX() { return x; }
    public int getY() { return y; }
}
'''


FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.AttributionKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.EventRestorationState;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.EventState;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.ExecutionSemantics;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.OwnerKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.PackedSource;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.SceneryRestorationState;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.SpatialReference;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.SpatialRole;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.TimeProgressionPolicy;
import java.util.Arrays;
import java.util.Collections;

public final class AtomicTimingInventoryFixture {
    public static void main(String[] args) {
        knownTimingDetachesAndUnknownTimingStaysUnavailable();
        invalidOrIncompleteAtomicTimingRefusesOrStaysVisible();
    }

    private static EventRestorationState knownState() {
        return EventRestorationState.scenerySpawn(
            SceneryRestorationState.of(
                310, 310, 524, 489, 0, 0, null, 0, null),
            false, ExecutionSemantics.ONE_SHOT,
            TimeProgressionPolicy.CONTINUE_SERVER_TICKS);
    }

    private static void knownTimingDetachesAndUnknownTimingStaysUnavailable() {
        LayeredPackedRegionEventOwnershipInventory inventory =
            LayeredPackedRegionEventOwnershipInventory.inventory(
                4L, 120L, "00000000-0000-0000-0000-000000000105",
                Collections.singletonList(PackedSource.of(10, 10)),
                Arrays.asList(
                    EventState.of(
                        0, 31L, OwnerKind.NONE,
                        AttributionKind.EXACT_SPATIAL, true, 17L, 2,
                        Collections.singletonList(SpatialReference.of(
                            SpatialRole.FIXED_EFFECT_LOCATION, 524, 489)),
                        knownState(), true),
                    EventState.of(
                        1, 32L, OwnerKind.NONE,
                        AttributionKind.UNATTRIBUTED, true, 5L, 9,
                        Collections.emptyList())),
                1, 2, 1);
        check(inventory.getObservedAtTick() == 120L
                && inventory.getAtomicTimingCapturedEventCount() == 1
                && inventory.isAtomicTimingCaptured()
                && inventory.isAtomicTimingComplete(),
            "known timing reconciles against available restoration state");
        check(inventory.getEvents().get(0).isAtomicTimingCaptured()
                && inventory.getEvents().get(0).isRunning()
                && inventory.getEvents().get(0).getTicksBeforeRun() == 17L
                && inventory.getEvents().get(0).getTimesRan() == 2
                && !inventory.getEvents().get(1).isAtomicTimingCaptured(),
            "event timing stays correlated with registration order");
    }

    private static void invalidOrIncompleteAtomicTimingRefusesOrStaysVisible() {
        expectIllegal(() -> EventState.of(
            0, 1L, OwnerKind.NONE, AttributionKind.UNATTRIBUTED,
            true, 1L, 0, Collections.emptyList(),
            EventRestorationState.unavailable(), true));
        LayeredPackedRegionEventOwnershipInventory incomplete =
            LayeredPackedRegionEventOwnershipInventory.inventory(
                5L, 121L, "00000000-0000-0000-0000-000000000105",
                Collections.singletonList(PackedSource.of(10, 10)),
                Collections.singletonList(EventState.of(
                    0, 33L, OwnerKind.NONE,
                    AttributionKind.EXACT_SPATIAL, true, 8L, 0,
                    Collections.singletonList(SpatialReference.of(
                        SpatialRole.FIXED_EFFECT_LOCATION, 524, 489)),
                    knownState())),
                1, 1, 1);
        check(incomplete.getAtomicTimingCapturedEventCount() == 0
                && !incomplete.isAtomicTimingCaptured()
                && !incomplete.isAtomicTimingComplete(),
            "known restoration without atomic timing remains incomplete");
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
'''


class LayeredMapsSliceOneHundredFiveTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-five-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "AtomicTimingInventoryFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes), str(point),
                str(fixture),
                *(str(path) for path in sorted(COORDINATES.glob("*.java"))),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_atomic_timing_inventory_fixture(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "AtomicTimingInventoryFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_handler_consumes_exactly_one_atomic_store_snapshot(self):
        source = HANDLER.read_text(encoding="utf-8")
        start = source.index("captureLayeredPackedRegionEventOwnershipInventory(")
        end = source.index("public boolean hasEvent", start)
        boundary = source[start:end]
        self.assertEqual(1, boundary.count("getTrackedEventAtomicTimingSnapshot("))
        self.assertIn("timingSnapshot.getObservedAtTick()", boundary)
        self.assertIn("registration.getTiming()", boundary)
        self.assertIn("timing.isRunning()", boundary)
        self.assertIn("timing.getTicksBeforeRun()", boundary)
        self.assertIn("timing.getTimesRan()", boundary)
        for forbidden in (
            "event.isRunning()", "event.getTicksBeforeRun()",
            "event.getTimesRan()", "eventStore.remove", "eventStore.add",
            ".stop()", "doRun()",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_inventory_requires_semantics_and_reconciles_atomic_timing(self):
        source = INVENTORY.read_text(encoding="utf-8")
        self.assertIn("atomicTimingCapturedEventCount", source)
        self.assertIn("isAtomicTimingComplete()", source)
        self.assertIn("Atomic timing requires explicit execution semantics", source)
        self.assertIn("isAtomicTimingCaptured()", source)

    def test_v36_contract_remains_explicitly_non_atomic(self):
        schema = json.loads(SCHEMA_V36.read_text(encoding="utf-8"))
        observer = OBSERVER.read_text(encoding="utf-8")
        aggregate = schema["$defs"]["eventOwnership"]["properties"]
        self.assertEqual(0, aggregate["atomicTimingCapturedEventCount"]["const"])
        self.assertFalse(aggregate["atomicTimingCaptured"]["const"])
        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v43"', observer)
        self.assertIn("getAtomicTimingCapturedEventCount()", observer)
        self.assertIn("isAtomicTimingComplete()", observer)
        self.assertIn("registrationChangeRefusesAtomicTimingSnapshot",
                      STORE_FIXTURE.read_text(encoding="utf-8"))

    def test_living_plan_records_slice_one_hundred_five_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 105: Detached atomic scenery-event timing",
            plan,
        )
        self.assertIn("Historical schema-v36 remains", plan)
        self.assertIn("No callback is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
