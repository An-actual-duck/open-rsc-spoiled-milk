#!/usr/bin/env python3
import os
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
REGION_SRC = SERVER / "src/com/openrsc/server/model/world/region"
ENTITY_SRC = SERVER / "src/com/openrsc/server/model/entity"
REGION_MANAGER = REGION_SRC / "RegionManager.java"
RELOAD_RECIPE = REGION_SRC / "LayeredPackedRegionReloadRecipe.java"
TRANSACTION = REGION_SRC / "RegionObjectCollisionTransactionExecutor.java"
CURRENT = SERVER / (
    "src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationCurrentStateRecoverySnapshot.java"
)
GAME_OBJECT = ENTITY_SRC / "GameObject.java"
COLLISION_STATE = ENTITY_SRC / "GameObjectCollisionRegistrationState.java"
STORE = SERVER / (
    "src/com/openrsc/server/event/rsc/handler/GameTickEventStore.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
BASE = runpy.run_path(str(ROOT / (
    "tests/myworld/"
    "test-layered-maps-slice-one-hundred-forty-eight.py"
)))


EXTRA_METHODS = r'''
    private static void liveRegionCaptureUsesExactRegisteredProvenance() {
        RegionManager manager = new RegionManager(null);
        manager.getRegion(170, 170);
        GameObject current = authoredObject(170, 170, 321, 320);
        Result footprint = managerProjection(current, false);
        GameTickEventRestorationCurrentStateRecoverySnapshot seed =
            currentSnapshot(
                CallbackKind.SCENERY_SPAWN,
                ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                320, 320, current, footprint);
        check(manager.applyGameTickEventCurrentStateRecoverySnapshot(
                seed, RegionObjectCollisionTransactionFixture
                    ::managerProjection).isApplied(),
            "capture fixture current object registered");

        RegionManager.CurrentStateRecoveryCaptureResult captured =
            manager.captureGameTickEventCurrentStateRecoverySnapshot(
                callbackFor(
                    CallbackKind.SCENERY_SPAWN, 320, 320, current),
                true, true);
        GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
            captured.getSnapshot();
        check(captured.isSnapshotAvailable()
                && snapshot != null
                && snapshot.getCurrentObjectId() == 321
                && snapshot.getCurrentPermanentObjectId() == 320
                && snapshot.getCollisionContributionTileCount() == 1
                && snapshot.getCollisionContributions().get(0)
                    .getBlockingSceneryCount() == 1
                && snapshot.getObservedCurrentState()
                    == ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT
                && captured.isRuntimeObservationPerformed()
                && !captured.isRuntimeHandleRetained()
                && !captured.isMutationPerformed()
                && !captured.isRegionLoadingPerformed()
                && !captured.isCallbackInvoked()
                && !captured.isEventCancellation()
                && !captured.isEventReschedule()
                && !captured.isArrivalGate()
                && !captured.isVisibilityReleased()
                && !captured.isLifecycleAuthority(),
            "live capture copies exact current constructor and provenance");
    }

    private static void liveRemovalCaptureUsesTheSameBoundaryPath() {
        RegionManager manager = new RegionManager(null);
        manager.getRegion(172, 170);
        GameObject current = authoredObject(172, 170, 390, 390);
        Result footprint = managerProjection(current, false);
        GameTickEventRestorationCurrentStateRecoverySnapshot seed =
            currentSnapshot(
                CallbackKind.SCENERY_REMOVE,
                ObservedCurrentState.EXACT_RESTORATION_SCENERY_PRESENT,
                390, 390, current, footprint);
        manager.applyGameTickEventCurrentStateRecoverySnapshot(
            seed, RegionObjectCollisionTransactionFixture::managerProjection);
        RegionManager.CurrentStateRecoveryCaptureResult captured =
            manager.captureGameTickEventCurrentStateRecoverySnapshot(
                callbackFor(
                    CallbackKind.SCENERY_REMOVE, 390, 390, current),
                true, true);
        check(captured.isSnapshotAvailable()
                && captured.getSnapshot().getObservedCurrentState()
                    == ObservedCurrentState
                        .EXACT_RESTORATION_SCENERY_PRESENT,
            "future removal uses the same exact live capture boundary");
    }

    private static void missingTargetOrProvenanceRefusesWithoutLoading() {
        RegionManager missing = new RegionManager(null);
        GameObject absent = authoredObject(174, 170, 321, 320);
        RegionManager.CurrentStateRecoveryCaptureResult missingTarget =
            missing.captureGameTickEventCurrentStateRecoverySnapshot(
                callbackFor(
                    CallbackKind.SCENERY_SPAWN, 320, 320, absent),
                true, true);
        check(missingTarget.isSnapshotAvailable() == false
                && missingTarget.getReason()
                    == RegionManager.CurrentStateRecoveryCaptureReason
                        .TARGET_REGION_UNAVAILABLE,
            "missing target Region refuses without creation");

        RegionManager manager = new RegionManager(null);
        manager.getRegion(176, 170);
        GameObject current = authoredObject(176, 170, 321, 320);
        Result footprint = managerProjection(current, false);
        GameTickEventRestorationCurrentStateRecoverySnapshot seed =
            currentSnapshot(
                CallbackKind.SCENERY_SPAWN,
                ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                320, 320, current, footprint);
        manager.applyGameTickEventCurrentStateRecoverySnapshot(
            seed, RegionObjectCollisionTransactionFixture::managerProjection);
        manager.getRegion(176, 170).getGameObjects().iterator().next()
            .clearOrderedCollisionRegistrationState();
        RegionManager.CurrentStateRecoveryCaptureResult noProvenance =
            manager.captureGameTickEventCurrentStateRecoverySnapshot(
                callbackFor(
                    CallbackKind.SCENERY_SPAWN, 320, 320, current),
                true, true);
        check(noProvenance.getReason()
                == RegionManager.CurrentStateRecoveryCaptureReason
                    .COLLISION_PROVENANCE_UNAVAILABLE,
            "missing exact collision provenance refuses instead of projecting");
    }

    private static void missingOuterBoundaryRefusesSnapshotAssessment() {
        RegionManager manager = new RegionManager(null);
        manager.getRegion(178, 170);
        GameObject current = authoredObject(178, 170, 321, 320);
        Result footprint = managerProjection(current, false);
        GameTickEventRestorationCurrentStateRecoverySnapshot seed =
            currentSnapshot(
                CallbackKind.SCENERY_SPAWN,
                ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                320, 320, current, footprint);
        manager.applyGameTickEventCurrentStateRecoverySnapshot(
            seed, RegionObjectCollisionTransactionFixture::managerProjection);
        RegionManager.CurrentStateRecoveryCaptureResult refused =
            manager.captureGameTickEventCurrentStateRecoverySnapshot(
                callbackFor(
                    CallbackKind.SCENERY_SPAWN, 320, 320, current),
                false, true);
        check(refused.getReason()
                == RegionManager.CurrentStateRecoveryCaptureReason
                    .SNAPSHOT_REFUSED
                && refused.getSnapshotReason()
                    == GameTickEventRestorationCurrentStateRecoverySnapshot
                        .Reason.EVENT_BOUNDARY_MISSING
                && manager.getRegion(178, 170).getGameObjects().size() == 1,
            "missing scheduler boundary refuses without changing live state");
    }

    private static CallbackExpectation callbackFor(
            CallbackKind kind,
            int callbackObjectId,
            int callbackPermanentObjectId,
            GameObject current) {
        return CallbackExpectation.declare(
            kind, "fixture-scheduler", 37L, 7L, 5L, 12L,
            0, true, true, true,
            callbackObjectId, callbackPermanentObjectId,
            current.getLoc().getX(), current.getLoc().getY(),
            current.getDirection(), current.getType(), null, 0,
            7L, 1, 1, 22, AuthoredConstructionKind.SCENERY);
    }
'''


def build_fixture():
    fixture = BASE["build_fixture"]()
    fixture = fixture.replace(
        "        forceFullBlockContributionIsCopiedExactly();",
        "        forceFullBlockContributionIsCopiedExactly();\n"
        "        liveRegionCaptureUsesExactRegisteredProvenance();\n"
        "        liveRemovalCaptureUsesTheSameBoundaryPath();\n"
        "        missingTargetOrProvenanceRefusesWithoutLoading();\n"
        "        missingOuterBoundaryRefusesSnapshotAssessment();",
    )
    fixture = fixture.replace(
        "    private static GameTickEventRestorationCurrentStateRecoverySnapshot\n"
        "            currentSnapshot(",
        EXTRA_METHODS +
        "\n    private static GameTickEventRestorationCurrentStateRecoverySnapshot\n"
        "            currentSnapshot(",
    )
    return fixture


class LayeredMapsSliceOneHundredFortyNineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-live-current-state-capture-"
        )
        cls.classes = Path(cls.compile_temp.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.compile_temp.name) / (
            "src/com/openrsc/server/model/world/region/"
            "RegionObjectCollisionTransactionFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(build_fixture(), encoding="utf-8")
        classpath = os.pathsep.join(
            [str(SERVER / "core.jar"), str(SERVER / "lib/*")]
        )
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-cp", classpath,
                "-d", str(cls.classes),
                str(CURRENT), str(COLLISION_STATE), str(GAME_OBJECT),
                str(TRANSACTION), str(RELOAD_RECIPE), str(REGION_MANAGER),
                str(fixture),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        if result.returncode != 0:
            raise AssertionError(result.stderr)
        cls.classpath = os.pathsep.join(
            [str(cls.classes), str(SERVER / "core.jar"), str(SERVER / "lib/*")]
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_live_region_capture_fixture(self):
        result = subprocess.run(
            [
                "java", "-cp", self.classpath,
                "com.openrsc.server.model.world.region."
                "RegionObjectCollisionTransactionFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
            timeout=20,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_capture_uses_exact_boundaries_and_provenance(self):
        source = REGION_MANAGER.read_text(encoding="utf-8")
        start = source.index(
            "captureGameTickEventCurrentStateRecoverySnapshot("
        )
        end = source.index(
            "applyGameTickEventCurrentStateRecoverySnapshot(", start
        )
        method = source[start:end]
        self.assertIn("getCollisionRegistrationState()", method)
        self.assertIn(
            "executeUnderExistingOrderedObjectCollisionBoundaries(", method
        )
        self.assertIn("getGameObjectTransactionMonitor()", method)
        self.assertIn("containsGameObjectIdentityUnderTransaction(", method)
        self.assertIn("CurrentScenery.declare(", method)
        self.assertIn(".assess(checked, current)", method)

    def test_capture_is_non_loading_and_store_remains_disconnected(self):
        source = REGION_MANAGER.read_text(encoding="utf-8")
        start = source.index(
            "captureGameTickEventCurrentStateRecoverySnapshot("
        )
        end = source.index(
            "applyGameTickEventCurrentStateRecoverySnapshot(", start
        )
        method = source[start:end]
        self.assertIn("peekRegionFromSectorCoordinates(", method)
        self.assertNotIn("getRegionFromSectorCoordinates(", method)
        self.assertNotIn("projectGameObjectCollisionFootprint(", method)
        self.assertNotIn(
            "captureGameTickEventCurrentStateRecoverySnapshot(",
            STORE.read_text(encoding="utf-8"),
        )
        for required in (
            "isRuntimeObservationPerformed() { return true; }",
            "isRuntimeHandleRetained() { return false; }",
            "isMutationPerformed() { return false; }",
            "isRegionLoadingPerformed() { return false; }",
            "isEventCancellation() { return false; }",
            "isEventReschedule() { return false; }",
            "isArrivalGate() { return false; }",
            "isVisibilityReleased() { return false; }",
        ):
            self.assertIn(required, method)

    def test_living_plan_records_slice_one_hundred_forty_nine(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 149: Live Region current-state capture", plan
        )
        normalized = " ".join(plan.split())
        self.assertIn("exact registered collision provenance", normalized)
        self.assertIn("scheduler Store remains disconnected", normalized)


if __name__ == "__main__":
    unittest.main()
