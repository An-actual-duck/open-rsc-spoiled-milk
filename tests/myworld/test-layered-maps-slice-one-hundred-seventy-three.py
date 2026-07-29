#!/usr/bin/env python3
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REGION = ROOT / "server/src/com/openrsc/server/model/world/region"
BOUNDARY = REGION / "LayeredPackedRegionSourceLifecycleBoundary.java"
REGION_MANAGER = REGION / "RegionManager.java"
RESIDENCY = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredRegionResidencyMirror.java"
)
OWNER_BOUNDARY = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventNpcOwnerPreservationBoundary.java"
)
GAME_EVENT_HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
)
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
import java.util.concurrent.atomic.AtomicBoolean;

public final class PackedRegionSourceLifecycleBoundaryFixture {
    public static void main(String[] args) throws Exception {
        Object lifecycleLock = new Object();
        LayeredPackedRegionSourceLifecycleBoundary boundary;
        synchronized (lifecycleLock) {
            boundary = LayeredPackedRegionSourceLifecycleBoundary.open(
                new LayeredPackedRegionNpcOwnerPreservationRequirements(true),
                17L, Thread.holdsLock(lifecycleLock));
            check(boundary.getGeneration() == 9L
                    && boundary.getRequirementsObservedAtTick() == 12L
                    && boundary.getResidencyMirrorVersion() == 17L
                    && boundary.getSelectedSourceCount() == 2
                    && boundary.getSelectedSources().get(0)
                        .getPackedRegionX() == 4
                    && boundary.getSelectedSources().get(0)
                        .getPackedRegionY() == 7
                    && boundary.getSelectedSources().get(1)
                        .getPackedRegionX() == 5
                    && boundary.getSelectedSources().get(1)
                        .getPackedRegionY() == 7
                    && boundary.matchesRequirements(
                        new LayeredPackedRegionNpcOwnerPreservationRequirements(
                            true))
                    && boundary.isRegionLifecycleBoundaryHeld()
                    && boundary.isAllSourcesResidentAtEntry()
                    && !boundary.isSourceAbsencePerformed()
                    && !boundary.isSourceReconstructionPerformed()
                    && !boundary.isRuntimeHandleRetained()
                    && !boundary.isLifecycleAuthority(),
                "boundary lost exact detached source identity");
            expectUnsupported(() ->
                boundary.getSelectedSources().clear());

            AtomicBoolean otherThreadRefused = new AtomicBoolean();
            Thread other = new Thread(() -> {
                try {
                    boundary.getResidencyMirrorVersion();
                } catch (IllegalStateException expected) {
                    otherThreadRefused.set(true);
                }
            }, "packed-source-boundary-leak");
            other.start();
            other.join(2000L);
            check(!other.isAlive() && otherThreadRefused.get(),
                "boundary crossed its owning thread");

            boundary.invalidate();
            expectIllegalState(() -> boundary.getSelectedSourceCount());
            expectIllegalState(() -> boundary.invalidate());
        }

        expectIllegalArgument(() ->
            LayeredPackedRegionSourceLifecycleBoundary.open(
                new LayeredPackedRegionNpcOwnerPreservationRequirements(true),
                17L, false));
    }

    private static void expectIllegalArgument(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void expectIllegalState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected IllegalStateException");
        } catch (IllegalStateException expected) {
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


class LayeredMapsSliceOneHundredSeventyThreeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-packed-source-lifecycle-boundary-"
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
            "PackedRegionSourceLifecycleBoundaryFixture.java"
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
                str(requirements), str(BOUNDARY), str(fixture),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_boundary_is_ephemeral_thread_confined_and_handle_free(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.region."
                "PackedRegionSourceLifecycleBoundaryFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_region_manager_checks_real_storage_and_mirror_under_lock(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        residency = RESIDENCY.read_text(encoding="utf-8")
        method = manager[manager.index(
            "public boolean withinLayeredPackedRegionSourceLifecycleBoundary("
        ):manager.index(
            "/** Opens one dormant owner",
            manager.index(
                "public boolean "
                "withinLayeredPackedRegionSourceLifecycleBoundary("
            ),
        )]
        self.assertIn("synchronized (layeredRegionLifecycleLock)", method)
        self.assertIn("peekRegionFromSectorCoordinates(", method)
        self.assertIn("isPackedRegionRegistered(", method)
        self.assertIn("LayeredPackedRegionSourceLifecycleBoundary.open(", method)
        self.assertIn("Thread.holdsLock(layeredRegionLifecycleLock)", method)
        self.assertLess(
            method.index("checkedOperation.execute(boundary)"),
            method.index("boundary.invalidate()"),
        )
        self.assertIn(
            "public synchronized boolean isPackedRegionRegistered(",
            residency,
        )
        for forbidden in (
            "regions.remove(",
            ".unload()",
            "unregisterPackedRegion(",
            "registerPackedRegion(",
            "invalidateVisibleObjectWindowCache(",
        ):
            self.assertNotIn(forbidden, method)

    def test_owner_scope_uses_real_source_boundary_not_assumed_quiescence(self):
        handler = GAME_EVENT_HANDLER.read_text(encoding="utf-8")
        owner = OWNER_BOUNDARY.read_text(encoding="utf-8")
        self.assertEqual(
            2,
            handler.count(
                ".withinLayeredPackedRegionSourceLifecycleBoundary("
            ),
        )
        self.assertEqual(
            2, handler.count("requireExactPackedSourceBoundary(") - 1
        )
        self.assertIn("maximumOwners, true", handler)
        self.assertIn("maximumOwners, false", handler)
        self.assertIn("boundary.matchesRequirements(requirements)", handler)
        self.assertIn(
            "capture.regionAbsenceQuiescenceHeld =",
            owner,
        )
        self.assertIn("regionLifecycleBoundaryHeld", owner)
        self.assertNotIn(
            "capture.regionAbsenceQuiescenceHeld = true",
            owner,
        )
        self.assertIn(
            "&& capture.regionAbsenceQuiescenceHeld",
            owner,
        )

    def test_living_plan_records_slice_one_hundred_seventy_three(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 173: Real packed-source lifecycle boundary",
            plan,
        )
        self.assertIn("residency mirror", plan)
        self.assertIn("no source absence", plan)


if __name__ == "__main__":
    unittest.main()
