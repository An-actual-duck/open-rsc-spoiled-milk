#!/usr/bin/env python3
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
INVENTORY = COORDINATES / (
    "LayeredPackedRegionEventOwnershipInventory.java"
)
HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_88 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-eighty-eight.py"
)))


FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.AttributionKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.EventRestorationState;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.EventState;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.NpcOwnerIdentity;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.OwnerKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.PackedSource;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.SpatialReference;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.SpatialRole;
import java.util.Arrays;
import java.util.Collections;

public final class EventNpcOwnerIdentityFixture {
    public static void main(String[] args) {
        exactNpcOwnerIdentityIsDetachedAndCounted();
        absentAndInvalidIdentityRemainClosed();
    }

    private static void exactNpcOwnerIdentityIsDetachedAndCounted() {
        LayeredAuthoredPlacementIdentity authored =
            new LayeredAuthoredPlacementIdentity(
                7L, 2, 10, 44, ConstructionKind.NPC_SPAWN);
        NpcOwnerIdentity owner = NpcOwnerIdentity.of(
            authored.getGeneration(), authored.getPackedRegionX(),
            authored.getPackedRegionY(), authored.getSourceOrdinal(),
            authored.getConstructionKind().name(), 321);
        EventState identified = EventState.of(
            0, 11L, OwnerKind.NPC, owner,
            AttributionKind.OWNER_POSITION_HINT, true, 8L, 0,
            Collections.singletonList(SpatialReference.of(
                SpatialRole.OWNER_CURRENT_POSITION, 100, 500)),
            EventRestorationState.unavailable(), false);
        EventState dynamic = EventState.of(
            1, 12L, OwnerKind.NPC,
            AttributionKind.OWNER_POSITION_HINT, true, 8L, 0,
            Collections.singletonList(SpatialReference.of(
                SpatialRole.OWNER_CURRENT_POSITION, 101, 500)));
        LayeredPackedRegionEventOwnershipInventory inventory =
            LayeredPackedRegionEventOwnershipInventory.inventory(
                7L, 99L, "00000000-0000-0000-0000-000000000160",
                Collections.singletonList(PackedSource.of(2, 10)),
                Arrays.asList(identified, dynamic), 1, 2, 2);

        NpcOwnerIdentity detached =
            inventory.getEvents().get(0).getNpcOwnerIdentity();
        check(inventory.getNpcOwnerIdentityCapturedEventCount() == 1
                && detached != null
                && detached.getGeneration() == 7L
                && detached.getPackedRegionX() == 2
                && detached.getPackedRegionY() == 10
                && detached.getSourceOrdinal() == 44
                && detached.getRuntimeNpcId() == 321
                && detached.isDetachedValue()
                && !detached.isRuntimeIndex()
                && !detached.isEntityHandle()
                && !detached.isLifecycleAuthority()
                && inventory.getEvents().get(1).getNpcOwnerIdentity() == null,
            "authored NPC owner identity is exact, detached, and optional");
    }

    private static void absentAndInvalidIdentityRemainClosed() {
        LayeredAuthoredPlacementIdentity npc =
            new LayeredAuthoredPlacementIdentity(
                7L, 2, 10, 44, ConstructionKind.NPC_SPAWN);
        NpcOwnerIdentity owner = NpcOwnerIdentity.of(
            npc.getGeneration(), npc.getPackedRegionX(),
            npc.getPackedRegionY(), npc.getSourceOrdinal(),
            npc.getConstructionKind().name(), 321);
        expectIllegal(() -> EventState.of(
            0, 1L, OwnerKind.PLAYER, owner,
            AttributionKind.OWNER_POSITION_HINT, true, 1L, 0,
            Collections.singletonList(SpatialReference.of(
                SpatialRole.OWNER_CURRENT_POSITION, 100, 500)),
            EventRestorationState.unavailable(), false));
        expectIllegal(() -> NpcOwnerIdentity.of(
            7L, 2, 10, 44, ConstructionKind.SCENERY.name(), 321));
        expectIllegal(() -> NpcOwnerIdentity.of(
            7L, 2, 10, 44, ConstructionKind.NPC_SPAWN.name(), -1));
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


class LayeredMapsSliceOneHundredSixtyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-event-npc-owner-identity-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(SLICE_88["POINT_STUB"], encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "EventNpcOwnerIdentityFixture.java"
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

    def test_exact_npc_owner_identity_contract_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "EventNpcOwnerIdentityFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_runtime_capture_detaches_authored_identity_without_index(self):
        inventory = INVENTORY.read_text(encoding="utf-8")
        handler = HANDLER.read_text(encoding="utf-8")
        self.assertIn("public static final class NpcOwnerIdentity", inventory)
        self.assertIn("getNpcOwnerIdentityCapturedEventCount()", inventory)
        self.assertIn("npcOwner.getAuthoredPlacementIdentity()", handler)
        self.assertIn("NpcOwnerIdentity.of(", handler)
        self.assertNotIn("npcOwner.getIndex()", handler)

    def test_living_plan_records_slice_one_hundred_sixty(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 160: Detached NPC event-owner identity",
            plan,
        )
        self.assertIn("world index", plan)
        self.assertIn("does not prove owner preservation", plan)


if __name__ == "__main__":
    unittest.main()
