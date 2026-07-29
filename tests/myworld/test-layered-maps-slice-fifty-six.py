#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
INVENTORY = COORDINATES / (
    "LayeredPackedRegionAuthoredConstructionInventory.java"
)
IDENTITY = COORDINATES / "LayeredAuthoredPlacementIdentity.java"
SLOT = COORDINATES / "LayeredAuthoredPlacementIdentitySlot.java"
MANIFEST = COORDINATES / "LayeredPackedRegionAuthoredPlacementManifest.java"
POPULATOR = ROOT / "server/src/com/openrsc/server/database/WorldPopulator.java"
ENTITY = ROOT / "server/src/com/openrsc/server/model/entity/Entity.java"
GAME_OBJECT = ROOT / "server/src/com/openrsc/server/model/entity/GameObject.java"
NPC = ROOT / "server/src/com/openrsc/server/model/entity/npc/Npc.java"
GROUND_ITEM = ROOT / "server/src/com/openrsc/server/model/entity/GroundItem.java"
WORLD = ROOT / "server/src/com/openrsc/server/model/world/World.java"
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
)
LOCS = [
    ROOT / "server/src/com/openrsc/server/external/GameObjectLoc.java",
    ROOT / "server/src/com/openrsc/server/external/NPCLoc.java",
    ROOT / "server/src/com/openrsc/server/external/ItemLoc.java",
]
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

public final class AuthoredPlacementIdentitySlotFixture {
    public static void main(String[] args) {
        slotIsAssignOnceAndConflictRefusing();
        manifestExposesOnlyTheImmediatelyPriorIdentity();
    }

    private static void slotIsAssignOnceAndConflictRefusing() {
        LayeredAuthoredPlacementIdentitySlot slot =
            new LayeredAuthoredPlacementIdentitySlot();
        LayeredAuthoredPlacementIdentity first = identity(3L, 2, 9, 1, "SCENERY");
        LayeredAuthoredPlacementIdentity same = identity(3L, 2, 9, 1, "SCENERY");
        check(slot.get() == null, "new slot is unassigned");
        slot.assign(first);
        slot.assign(same);
        check(slot.get().equals(first), "equal reassignment is idempotent");
        expectNull(() -> slot.assign(null));
        expectState(() -> slot.assign(identity(3L, 2, 9, 2, "SCENERY")));
        check(slot.get().equals(first), "conflict does not replace identity");
    }

    private static void manifestExposesOnlyTheImmediatelyPriorIdentity() {
        LayeredPackedRegionAuthoredPlacementManifest.Builder builder =
            LayeredPackedRegionAuthoredPlacementManifest.builder(6L);
        expectState(builder::getLastRecordedIdentity);
        builder.recordScenery(2, 9, 100, 100, 110, 440, 4, 0, null);
        check(builder.getLastRecordedIdentity().equals(
            identity(6L, 2, 9, 1, "SCENERY")),
            "first record exposes its canonical identity");
        builder.recordBoundary(2, 9, 5, 5, 111, 440, 1, 1, null);
        check(builder.getLastRecordedIdentity().equals(
            identity(6L, 2, 9, 2, "BOUNDARY")),
            "later record replaces only the builder cursor");
        builder.build();
        expectState(builder::getLastRecordedIdentity);
    }

    private static LayeredAuthoredPlacementIdentity identity(
            long generation, int x, int y, int ordinal, String kind) {
        return new LayeredAuthoredPlacementIdentity(
            generation, x, y, ordinal,
            LayeredPackedRegionAuthoredConstructionInventory
                .ConstructionKind.valueOf(kind));
    }

    private static void expectNull(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected refusal.
        }
    }

    private static void expectState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected refusal.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
'''


class LayeredMapsSliceFiftySixTest(unittest.TestCase):
    def test_conflict_refusing_identity_slot_contract(self):
        with tempfile.TemporaryDirectory(prefix="layered-slice56-") as temp:
            temp_path = Path(temp)
            fixture_path = temp_path / (
                "com/openrsc/server/model/world/coordinate/"
                "AuthoredPlacementIdentitySlotFixture.java"
            )
            fixture_path.parent.mkdir(parents=True)
            fixture_path.write_text(FIXTURE, encoding="utf-8")
            classes = temp_path / "classes"
            classes.mkdir()
            subprocess.run(
                [
                    "javac", "-d", str(classes), str(INVENTORY),
                    str(IDENTITY), str(SLOT), str(MANIFEST),
                    str(fixture_path),
                ],
                check=True,
                cwd=ROOT,
            )
            subprocess.run(
                [
                    "java", "-cp", str(classes),
                    "com.openrsc.server.model.world.coordinate."
                    "AuthoredPlacementIdentitySlotFixture",
                ],
                check=True,
                cwd=ROOT,
            )

    def test_attachment_is_observational_and_explicitly_propagated(self):
        slot = SLOT.read_text(encoding="utf-8")
        populator = POPULATOR.read_text(encoding="utf-8")
        entity = ENTITY.read_text(encoding="utf-8")
        game_object = GAME_OBJECT.read_text(encoding="utf-8")
        npc = NPC.read_text(encoding="utf-8")
        ground_item = GROUND_ITEM.read_text(encoding="utf-8")
        world = WORLD.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn("assign-once observational carrier", slot)
        self.assertNotIn("clear(", slot)
        self.assertIn("assignAuthoredPlacementIdentity", entity)
        for loc in LOCS:
            source = loc.read_text(encoding="utf-8")
            self.assertIn("LayeredAuthoredPlacementIdentitySlot", source)
            self.assertIn("assignAuthoredPlacementIdentity", source)
        for source in (game_object, npc, ground_item):
            self.assertIn("loc.getAuthoredPlacementIdentity()", source)
            self.assertIn("assignAuthoredPlacementIdentity(", source)

        self.assertIn("getLastRecordedIdentity()", populator)
        self.assertIn("object.assignAuthoredPlacementIdentity", populator)
        self.assertIn("npc.assignAuthoredPlacementIdentity", populator)
        self.assertIn("authoredItem.assignAuthoredPlacementIdentity", populator)
        replacement = world.split(
            "public void replaceGameObject(final GameObject old,", 1
        )[1].split("public void sendKilledUpdate", 1)[0]
        self.assertIn("old.getAuthoredPlacementIdentity()", replacement)
        self.assertIn("_new.getLoc().assignAuthoredPlacementIdentity", replacement)
        self.assertLess(
            replacement.index("assignAuthoredPlacementIdentity"),
            replacement.index("applyGameObjectTransaction(old, _new, false)"),
        )
        registration = world.split(
            "public void registerGameObject(final GameObject o)", 1
        )[1].split("public void registerItem(final GroundItem i)", 1)[0]
        self.assertNotIn("AuthoredPlacementIdentity", registration)

        identity_name = "LayeredAuthoredPlacementIdentity"
        self.assertNotIn(identity_name, manager)
        self.assertNotIn(identity_name, observer)
        self.assertIn(
            "### Slice 56: Observational runtime provenance attachment",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
