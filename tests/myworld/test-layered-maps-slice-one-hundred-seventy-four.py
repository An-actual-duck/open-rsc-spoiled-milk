#!/usr/bin/env python3
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REGION = ROOT / "server/src/com/openrsc/server/model/world/region"
BOUNDARY = REGION / "LayeredPackedRegionSourceLifecycleBoundary.java"
PREFLIGHT = REGION / "LayeredPackedRegionSourceAbsencePreflight.java"
REGION_MANAGER = REGION / "RegionManager.java"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_169 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-sixty-nine.py"
)))


FIXTURE = r'''
package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionNpcOwnerPreservationRequirements;
import java.util.Arrays;

public final class PackedRegionSourceAbsencePreflightFixture {
    public static void main(String[] args) {
        Object lifecycleLock = new Object();
        LayeredPackedRegionSourceAbsencePreflight preflight;
        synchronized (lifecycleLock) {
            LayeredPackedRegionSourceLifecycleBoundary boundary =
                LayeredPackedRegionSourceLifecycleBoundary.open(
                    new LayeredPackedRegionNpcOwnerPreservationRequirements(
                        true),
                    17L, Thread.holdsLock(lifecycleLock));
            preflight = LayeredPackedRegionSourceAbsencePreflight.assess(
                boundary,
                Arrays.asList(
                    inventory(4, 7, true, 1, 2, 4, 1, 3, 5),
                    inventory(5, 7, false, 0, 0, 0, 0, 0, 0)),
                14L, false, Thread.holdsLock(lifecycleLock));
            boundary.invalidate();
        }

        check(preflight.getGeneration() == 9L
                && preflight.getRequirementsObservedAtTick() == 12L
                && preflight.getObservedAtTick() == 14L
                && preflight.getResidencyMirrorVersion() == 17L,
            "preflight lost lifecycle identity");
        check(preflight.getSourceCount() == 2
                && preflight.getBlockedSourceCount() == 2
                && preflight.getReadySourceCount() == 0
                && !preflight.isAbsenceReadyAtObservation(),
            "preflight promoted a blocked source");
        check(preflight.getPlayerCount() == 1L
                && preflight.getNpcCount() == 2L
                && preflight.getAuthoredObjectCount() == 3L
                && preflight.getDynamicObjectCount() == 1L
                && preflight.getGroundItemCount() == 3L
                && preflight.getCollisionProductTileCount() == 5L,
            "preflight totals do not reconcile");
        check(preflight.getSources().get(0).getBlockers().size() == 7
                && preflight.getSources().get(1).getBlockers().size() == 2
                && preflight.getBlockerSummary(
                    LayeredPackedRegionSourceAbsencePreflight.Blocker
                        .REGION_RELOAD_PATH_UNAVAILABLE)
                    .getBlockedSourceCount() == 2
                && preflight.getBlockerSummary(
                    LayeredPackedRegionSourceAbsencePreflight.Blocker
                        .ACTIVE_PLAYER_PRESENT)
                    .getBlockedSourceCount() == 1,
            "preflight blocker summaries are inconsistent");
        check(preflight.isPointInTimeOnly()
                && !preflight.isSourceAbsencePerformed()
                && !preflight.isSourceReconstructionPerformed()
                && !preflight.isRuntimeHandleRetained()
                && !preflight.isRegionRegistryMutated()
                && !preflight.isResidencyMirrorMutated()
                && !preflight.isVisibilityCacheMutated()
                && !preflight.isArrivalGate()
                && !preflight.isLifecycleAuthority(),
            "preflight crossed its read-only boundary");
        expectUnsupported(() -> preflight.getSources().clear());
        expectUnsupported(() ->
            preflight.getSources().get(0).getBlockers().clear());

        expectIllegalArgument(() ->
            LayeredPackedRegionSourceAbsencePreflight.SourceInventory.of(
                4, 7, true, 0, 0, 0, 1, 0, 0));
    }

    private static LayeredPackedRegionSourceAbsencePreflight.SourceInventory
        inventory(
            int x, int y, boolean tiles, int players, int npcs, int objects,
            int dynamicObjects, int items, int collisionTiles) {
        return LayeredPackedRegionSourceAbsencePreflight.SourceInventory.of(
            x, y, tiles, players, npcs, objects, dynamicObjects, items,
            collisionTiles);
    }

    private static void expectIllegalArgument(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
'''


class LayeredMapsSliceOneHundredSeventyFourTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-source-absence-preflight-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        requirements = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LayeredPackedRegionNpcOwnerPreservationRequirements.java"
        )
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/region/"
            "PackedRegionSourceAbsencePreflightFixture.java"
        )
        requirements.parent.mkdir(parents=True, exist_ok=True)
        fixture.parent.mkdir(parents=True, exist_ok=True)
        requirements.write_text(
            SLICE_169["REQUIREMENTS_STUB"], encoding="utf-8"
        )
        fixture.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(requirements), str(BOUNDARY), str(PREFLIGHT), str(fixture),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_preflight_is_exact_detached_and_fail_closed(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.region."
                "PackedRegionSourceAbsencePreflightFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_region_manager_captures_only_under_real_boundary(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        method = manager[manager.index(
            "public LayeredPackedRegionSourceAbsencePreflight"
        ):manager.index(
            "/** Opens one dormant owner",
            manager.index(
                "public LayeredPackedRegionSourceAbsencePreflight"
            ),
        )]
        self.assertIn("Thread.holdsLock(layeredRegionLifecycleLock)", method)
        self.assertIn("checked.getResidencyMirrorVersion()", method)
        self.assertIn("peekRegionFromSectorCoordinates(", method)
        self.assertIn("isPackedRegionRegistered(", method)
        self.assertIn("captureRetirementContentsSnapshot()", method)
        self.assertIn("LAYERED_PACKED_REGION_RELOAD_SUPPORTED", method)
        for forbidden in (
            "getRegion(",
            "regions.remove(",
            ".unload()",
            "unregisterPackedRegion(",
            "registerPackedRegion(",
            "invalidateVisibleObjectWindowCache(",
        ):
            self.assertNotIn(forbidden, method)

    def test_preflight_names_all_unproven_runtime_families(self):
        source = PREFLIGHT.read_text(encoding="utf-8")
        for blocker in (
            "ACTIVE_PLAYER_PRESENT",
            "NPC_MEMBERSHIP_PRESERVATION_UNAVAILABLE",
            "AUTHORED_OBJECT_RELOAD_UNAVAILABLE",
            "DYNAMIC_OBJECT_PRESERVATION_UNAVAILABLE",
            "GROUND_ITEM_PRESERVATION_UNAVAILABLE",
            "COLLISION_REBUILD_UNAVAILABLE",
            "REGION_RELOAD_PATH_UNAVAILABLE",
        ):
            self.assertIn(blocker, source)
        self.assertIn("isSourceAbsencePerformed() { return false; }", source)
        self.assertIn(
            "isSourceReconstructionPerformed() { return false; }", source
        )
        self.assertIn("isLifecycleAuthority() { return false; }", source)

    def test_living_plan_records_slice_one_hundred_seventy_four(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 174: Exact source-absence preflight",
            plan,
        )
        self.assertIn("no Region becomes absent", plan)


if __name__ == "__main__":
    unittest.main()
