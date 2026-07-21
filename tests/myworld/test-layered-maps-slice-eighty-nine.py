#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
AFFINITY = ROOT / (
    "server/src/com/openrsc/server/event/rsc/GameTickEventSpatialAffinity.java"
)
EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/GameTickEvent.java"
HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
)
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
COMMAND = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.event.rsc;

import java.util.Arrays;
import java.util.Collections;

public final class EventSpatialAffinityFixture {
    public static void main(String[] args) {
        explicitScopesRemainImmutableAndDistinct();
        invalidDeclarationsRefuse();
    }

    private static void explicitScopesRemainImmutableAndDistinct() {
        GameTickEventSpatialAffinity unspecified =
            GameTickEventSpatialAffinity.unspecified();
        GameTickEventSpatialAffinity global =
            GameTickEventSpatialAffinity.nonSpatialGlobal();
        GameTickEventSpatialAffinity exact =
            GameTickEventSpatialAffinity.exact(Arrays.asList(
                GameTickEventSpatialAffinity.Reference.of(
                    GameTickEventSpatialAffinity.Role.SUBJECT_CURRENT_POSITION,
                    100, 500),
                GameTickEventSpatialAffinity.Reference.of(
                    GameTickEventSpatialAffinity.Role.TARGET_CURRENT_POSITION,
                    150, 500)));
        check(unspecified.getScope()
                == GameTickEventSpatialAffinity.Scope.UNSPECIFIED
            && unspecified.getReferences().isEmpty(),
            "legacy default stays unspecified");
        check(global.getScope()
                == GameTickEventSpatialAffinity.Scope.NON_SPATIAL_GLOBAL
            && global.getReferences().isEmpty(),
            "global scope requires an explicit declaration");
        check(exact.getScope()
                == GameTickEventSpatialAffinity.Scope.EXACT_SPATIAL
            && exact.getReferences().size() == 2
            && exact.getReferences().get(1).getX() == 150,
            "exact multi-location effect is retained");
        expectUnsupported(() -> exact.getReferences().clear());
        GameTickEventSpatialAffinity fixed =
            GameTickEventSpatialAffinity.exactFixedLocation(145, 660);
        check(fixed.getReferences().get(0).getRole()
                == GameTickEventSpatialAffinity.Role.FIXED_EFFECT_LOCATION
            && fixed.getReferences().get(0).getY() == 660,
            "fixed-location convenience retains exact coordinates");
    }

    private static void invalidDeclarationsRefuse() {
        expectIllegal(() -> GameTickEventSpatialAffinity.exact(
            Collections.emptyList()));
        expectIllegal(() -> GameTickEventSpatialAffinity.Reference.of(
            GameTickEventSpatialAffinity.Role.FIXED_EFFECT_LOCATION, -1, 0));
        expectNull(() -> GameTickEventSpatialAffinity.exact(
            Collections.singletonList(null)));
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected refusal.
        }
    }

    private static void expectNull(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected refusal.
        }
    }

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected refusal.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
    }
}
'''


class LayeredMapsSliceEightyNineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-eighty-nine-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        fixture = cls.temp / (
            "src/com/openrsc/server/event/rsc/EventSpatialAffinityFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(AFFINITY), str(fixture),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_event_affinity_contract_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.event.rsc.EventSpatialAffinityFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_runtime_snapshot_defaults_legacy_events_conservatively(self):
        event = EVENT.read_text(encoding="utf-8")
        handler = HANDLER.read_text(encoding="utf-8")
        self.assertIn("getSpatialAffinity()", event)
        self.assertIn("GameTickEventSpatialAffinity.unspecified()", event)
        start = handler.index(
            "captureLayeredPackedRegionEventOwnershipInventory("
        )
        end = handler.index("public boolean hasEvent", start)
        boundary = handler[start:end]
        self.assertIn("getEvents()", boundary)
        self.assertIn("OWNER_POSITION_HINT", boundary)
        self.assertIn("UNATTRIBUTED", boundary)
        self.assertIn("EXACT_SPATIAL", boundary)
        self.assertIn("NON_SPATIAL_GLOBAL", boundary)
        self.assertIn("owner.getX()", boundary)
        self.assertIn("owner.getY()", boundary)
        for forbidden in (
            "getDescriptor()", "getUUID()", "getClass()", "getDeclared",
            ".stop()", "eventStore.remove", "eventStore.add", "doRun()",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_runtime_snapshot_is_connected_through_private_observer(self):
        method = "captureLayeredPackedRegionEventOwnershipInventory("
        self.assertNotIn(method, OBSERVER.read_text(encoding="utf-8"))
        self.assertIn(method, PLAYER.read_text(encoding="utf-8"))
        self.assertIn(method, COMMAND.read_text(encoding="utf-8"))

    def test_living_plan_records_slice_eighty_nine_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 89: Bounded runtime event-affinity snapshot", plan
        )
        self.assertIn("legacy null-owned events remain unattributed", plan)
        self.assertIn("No event is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
