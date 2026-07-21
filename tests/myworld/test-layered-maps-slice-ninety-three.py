#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
INVENTORY = COORDINATES / "LayeredPackedRegionEventOwnershipInventory.java"
HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


POINT_STUB = r'''
package com.openrsc.server.model;

public class Point {
    private final int x;
    private final int y;
    public Point(int x, int y) { this.x = x; this.y = y; }
    public static Point location(int x, int y) {
        if (x < 0 || y < 0 || x > Short.MAX_VALUE || y > Short.MAX_VALUE) {
            throw new IllegalArgumentException("packed point out of range");
        }
        return new Point(x, y);
    }
    public int getX() { return x; }
    public int getY() { return y; }
}
'''


FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.AttributionKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.AuthoredConstructionKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory
        .AuthoredPlacementRestorationState;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.EventRestorationState;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.EventState;
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
import java.util.Arrays;
import java.util.Collections;

public final class EventRestorationInventoryFixture {
    public static void main(String[] args) {
        restorationStateCorrelatesWithExactEvents();
        ambiguousOrMismatchedStateRefuses();
    }

    private static void restorationStateCorrelatesWithExactEvents() {
        SpatialReference tree = SpatialReference.of(
            SpatialRole.FIXED_EFFECT_LOCATION, 524, 489);
        SpatialReference stump = SpatialReference.of(
            SpatialRole.FIXED_EFFECT_LOCATION, 525, 489);
        SceneryRestorationState spawnScenery =
            SceneryRestorationState.of(
                310, 310, 524, 489, 0, 0, "private-owner", 0,
                AuthoredPlacementRestorationState.of(
                    7L, 10, 10, 42, AuthoredConstructionKind.SCENERY));
        SceneryRestorationState removalScenery =
            SceneryRestorationState.of(
                4, 4, 525, 489, 0, 0, null, 2, null);

        LayeredPackedRegionEventOwnershipInventory inventory =
            LayeredPackedRegionEventOwnershipInventory.inventory(
                9L, 120L,
                Collections.singletonList(PackedSource.of(10, 10)),
                Arrays.asList(
                    EventState.of(0, 21L, OwnerKind.NONE,
                        AttributionKind.EXACT_SPATIAL, true, 41L, 0,
                        Collections.singletonList(tree),
                        EventRestorationState.scenerySpawn(
                            spawnScenery, true)),
                    EventState.of(1, 22L, OwnerKind.NONE,
                        AttributionKind.EXACT_SPATIAL, true, 10L, 0,
                        Collections.singletonList(stump),
                        EventRestorationState.sceneryRemove(removalScenery)),
                    EventState.of(2, 23L, OwnerKind.NONE,
                        AttributionKind.UNATTRIBUTED, true, 1L, 4,
                        Collections.emptyList())),
                1, 3, 2);

        check(inventory.getRestorationStateAvailableEventCount() == 2
            && inventory
                .getDetachedCallbackPayloadCompleteEventCount() == 1
            && inventory.getRestorationStateCompleteEventCount() == 0,
            "available callback payload is not restoration completeness");
        check(inventory.getSources().get(0)
                .getRestorationStateEventOrdinals().equals(Arrays.asList(0, 1))
            && inventory.getSources().get(0)
                .getRestorationStateEventCount() == 2,
            "proposal source correlates both exact scenery states");
        check(inventory.getEvents().get(0).getRestorationState().getKind()
                == LayeredPackedRegionEventOwnershipInventory.RestorationKind
                    .SCENERY_SPAWN
            && inventory.getEvents().get(0).getRestorationState()
                .isDetachedCallbackPayloadComplete()
            && inventory.getEvents().get(0).getRestorationState()
                .getScenery().hasOwner()
            && inventory.getEvents().get(0).getRestorationState()
                .getScenery().getAuthoredPlacement().getSourceOrdinal() == 42,
            "spawn constructor and provenance state survive detachment");
        check(inventory.getEvents().get(1).getRestorationState()
                .getTargetBindingEvidence()
                == LayeredPackedRegionEventOwnershipInventory
                    .TargetBindingEvidence.LIVE_ENTITY_REFERENCE_ONLY
            && !inventory.getEvents().get(1).getRestorationState()
                .isDetachedCallbackPayloadComplete()
            && !inventory.getEvents().get(1).getRestorationState()
                .isStandaloneRestorationComplete(),
            "identity-less removal remains incomplete and inert");
    }

    private static void ambiguousOrMismatchedStateRefuses() {
        SpatialReference fixed = SpatialReference.of(
            SpatialRole.FIXED_EFFECT_LOCATION, 524, 489);
        SceneryRestorationState scenery = SceneryRestorationState.of(
            310, 310, 525, 489, 0, 0, null, 0, null);
        EventRestorationState state =
            EventRestorationState.scenerySpawn(scenery, false);
        expectIllegal(() -> EventState.of(
            0, 1L, OwnerKind.NONE, AttributionKind.EXACT_SPATIAL, true, 1L, 0,
            Collections.singletonList(fixed), state));
        expectIllegal(() -> EventState.of(
            0, 1L, OwnerKind.PLAYER, AttributionKind.OWNER_POSITION_HINT,
            true, 1L, 0,
            Collections.singletonList(SpatialReference.of(
                SpatialRole.OWNER_CURRENT_POSITION, 525, 489)), state));
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected refusal.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
    }
}
'''


class LayeredMapsSliceNinetyThreeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-ninety-three-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "EventRestorationInventoryFixture.java"
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

    def test_event_restoration_inventory_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "EventRestorationInventoryFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_runtime_snapshot_maps_explicit_state_without_reflection(self):
        source = HANDLER.read_text(encoding="utf-8")
        start = source.index("captureLayeredPackedRegionEventOwnershipInventory(")
        end = source.index("public boolean hasEvent", start)
        boundary = source[start:end]
        self.assertIn("event.getRestorationState()", boundary)
        self.assertIn("detachEventRestorationState(", boundary)
        self.assertIn("EventRestorationState.scenerySpawn(", boundary)
        self.assertIn("EventRestorationState.sceneryRemove(", boundary)
        for forbidden in (
            "getDescriptor()", "getUUID()", "getClass()", "getDeclared",
            ".stop()", "eventStore.remove", "eventStore.add", "doRun()",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_capture_is_privately_published_and_remains_inert(self):
        inventory = INVENTORY.read_text(encoding="utf-8")
        self.assertIn("getRestorationStateAvailableEventCount()", inventory)
        self.assertIn("LIVE_ENTITY_REFERENCE_ONLY", inventory)
        self.assertIn(
            "getRestorationStateAvailableEventCount()",
            OBSERVER.read_text(encoding="utf-8"),
        )
        self.assertNotIn(
            "scenery.getOwner()", OBSERVER.read_text(encoding="utf-8")
        )
        for forbidden in (
            "event.stop()", "eventStore.remove", "eventStore.add",
            "registerGameObject", "unregisterGameObject",
        ):
            self.assertNotIn(forbidden, inventory)

    def test_living_plan_records_slice_ninety_three_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 93: Bounded scenery-event restoration capture", plan
        )
        self.assertIn("fixed-effect coordinate", plan)
        self.assertIn("No event is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
