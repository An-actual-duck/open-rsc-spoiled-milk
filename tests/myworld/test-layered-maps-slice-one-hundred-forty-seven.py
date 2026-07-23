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
REGION_MANAGER = REGION_SRC / "RegionManager.java"
TRANSACTION = REGION_SRC / "RegionObjectCollisionTransactionExecutor.java"
CURRENT = SERVER / (
    "src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationCurrentStateRecoverySnapshot.java"
)
STORE = SERVER / (
    "src/com/openrsc/server/event/rsc/handler/GameTickEventStore.java"
)
COORDINATOR = SERVER / (
    "src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationRecoveryCoordinatorContract.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
BASE = runpy.run_path(str(ROOT / (
    "tests/myworld/"
    "test-layered-maps-slice-one-hundred-forty-six.py"
)))


EXTRA_METHODS = r'''
    private static void regionManagerReconstructsAndAppliesWithoutLoading() {
        RegionManager manager = new RegionManager(null);
        manager.getRegion(130, 130);
        GameObject current = authoredObject(130, 130, 321, 320);
        Result captured = managerProjection(current, false);
        GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
            currentSnapshot(
                CallbackKind.SCENERY_SPAWN,
                ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                320, 320, current, captured);
        RegionManager.CurrentStateRecoveryApplicationResult applied =
            manager.applyGameTickEventCurrentStateRecoverySnapshot(
                snapshot, RegionObjectCollisionTransactionFixture
                    ::managerProjection);
        Region target = manager.getRegion(130, 130);
        check(applied.isApplied()
                && applied.isMembershipRegistered()
                && !applied.isForceFullBlockProjectionSelected()
                && applied.getBoundaryCount() == 1
                && target.getGameObjects().size() == 1
                && target.getMutableTileValue(34, 34)
                    .getBlockingSceneryCount() == 1,
            "non-loading adapter reconstructs exact membership and collision");

        RegionManager.CurrentStateRecoveryApplicationResult noOp =
            manager.applyGameTickEventCurrentStateRecoverySnapshot(
                snapshot, RegionObjectCollisionTransactionFixture
                    ::managerProjection);
        check(noOp.isNoOp()
                && !noOp.isMembershipRegistered()
                && target.getGameObjects().size() == 1
                && target.getMutableTileValue(34, 34)
                    .getBlockingSceneryCount() == 1,
            "adapter retry is idempotent");
        check(!applied.isSnapshotRetained()
                && !applied.isEventHandleRetained()
                && !applied.isSchedulerStateTouched()
                && !applied.isCallbackInvoked()
                && !applied.isEventCancellation()
                && !applied.isEventReschedule()
                && !applied.isLoadingPerformed()
                && !applied.isArrivalGate()
                && !applied.isVisibilityReleased()
                && !applied.isLifecycleAuthority(),
            "adapter result grants no scheduler, loading, or arrival role");
    }

    private static void forceFullBlockProjectionIsSelectedOnlyWhenExact() {
        RegionManager manager = new RegionManager(null);
        manager.getRegion(132, 130);
        GameObject current = authoredObject(132, 130, 321, 320);
        Result captured = managerProjection(current, true);
        GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
            currentSnapshot(
                CallbackKind.SCENERY_SPAWN,
                ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                320, 320, current, captured);
        RegionManager.CurrentStateRecoveryApplicationResult result =
            manager.applyGameTickEventCurrentStateRecoverySnapshot(
                snapshot, RegionObjectCollisionTransactionFixture
                    ::managerProjection);
        check(result.isApplied()
                && result.isForceFullBlockProjectionSelected()
                && manager.getRegion(132, 130)
                    .getMutableTileValue(36, 34)
                    .getBlockingSceneryCount() == 2,
            "force-full-block fallback is selected only by exact contribution");
    }

    private static void missingRegionsAndProjectionRefuseWithoutCreation() {
        RegionManager missingTarget = new RegionManager(null);
        GameObject targetObject = authoredObject(140, 130, 321, 320);
        Result targetFootprint = managerProjection(targetObject, false);
        GameTickEventRestorationCurrentStateRecoverySnapshot targetSnapshot =
            currentSnapshot(
                CallbackKind.SCENERY_SPAWN,
                ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                320, 320, targetObject, targetFootprint);
        RegionManager.CurrentStateRecoveryApplicationResult targetRefusal =
            missingTarget.applyGameTickEventCurrentStateRecoverySnapshot(
                targetSnapshot, RegionObjectCollisionTransactionFixture
                    ::managerProjection);
        check(targetRefusal.isRefused()
                && targetRefusal.getReason()
                    == RegionManager.CurrentStateRecoveryApplicationReason
                        .TARGET_REGION_UNAVAILABLE,
            "missing target Region refuses without loading");

        RegionManager missingNeighbor = new RegionManager(null);
        missingNeighbor.getRegion(47, 150);
        GameObject crossing = authoredDirectedObject(
            47, 150, 500, 500, 4);
        Result crossingFootprint = managerProjection(crossing, false);
        GameTickEventRestorationCurrentStateRecoverySnapshot crossingSnapshot =
            currentSnapshot(
                CallbackKind.SCENERY_REMOVE,
                ObservedCurrentState.EXACT_RESTORATION_SCENERY_PRESENT,
                500, 500, crossing, crossingFootprint);
        RegionManager.CurrentStateRecoveryApplicationResult regionRefusal =
            missingNeighbor.applyGameTickEventCurrentStateRecoverySnapshot(
                crossingSnapshot, RegionObjectCollisionTransactionFixture
                    ::managerProjection);
        check(regionRefusal.isRefused()
                && regionRefusal.getReason()
                    == RegionManager.CurrentStateRecoveryApplicationReason
                        .REQUIRED_REGION_UNAVAILABLE
                && missingNeighbor.getRegion(47, 150).getGameObjects().isEmpty()
                && missingNeighbor.getRegion(47, 150)
                    .getMutableTileValue(47, 6)
                    .getDynamicCollisionCounts()[0] == 0,
            "missing collision Region refuses without partial state");

        RegionManager missingDefinition = new RegionManager(null);
        missingDefinition.getRegion(142, 130);
        GameObject definitionObject = authoredObject(142, 130, 321, 320);
        Result definitionFootprint = managerProjection(
            definitionObject, false);
        GameTickEventRestorationCurrentStateRecoverySnapshot
            definitionSnapshot = currentSnapshot(
                CallbackKind.SCENERY_SPAWN,
                ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                320, 320, definitionObject, definitionFootprint);
        RegionManager.CurrentStateRecoveryApplicationResult
            definitionRefusal = missingDefinition
                .applyGameTickEventCurrentStateRecoverySnapshot(
                    definitionSnapshot, (object, force) -> null);
        check(definitionRefusal.isRefused()
                && definitionRefusal.getReason()
                    == RegionManager.CurrentStateRecoveryApplicationReason
                        .COLLISION_FOOTPRINT_UNAVAILABLE
                && missingDefinition.getRegion(142, 130)
                    .getGameObjects().isEmpty(),
            "missing definition projection refuses without mutation");
    }

    private static void nonmatchingProjectionRefusesBeforeRegionMutation() {
        RegionManager manager = new RegionManager(null);
        manager.getRegion(144, 130);
        GameObject current = authoredObject(144, 130, 321, 320);
        Result captured = managerProjection(current, false);
        GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
            currentSnapshot(
                CallbackKind.SCENERY_SPAWN,
                ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                320, 320, current, captured);
        RegionManager.CurrentStateRecoveryApplicationResult refused =
            manager.applyGameTickEventCurrentStateRecoverySnapshot(
                snapshot, (object, force) ->
                    GameTickEventRestorationCollisionFootprintPlanner.plan(
                        Operation.REGISTER,
                        ConstructorState.of(
                            object.getID(), object.getLoc().getX(),
                            object.getLoc().getY(), object.getDirection(),
                            object.getType()),
                        Definition.scenery(
                            0, 1, 1, "decoration", ALLOWLIST),
                        force, BOUNDS));
        check(refused.isRefused()
                && refused.getReason()
                    == RegionManager.CurrentStateRecoveryApplicationReason
                        .CURRENT_COLLISION_SNAPSHOT_MISMATCH
                && manager.getRegion(144, 130).getGameObjects().isEmpty()
                && manager.getRegion(144, 130)
                    .getMutableTileValue(0, 34)
                    .getBlockingSceneryCount() == 0,
            "nonmatching normal and forced projections refuse before mutation");
    }

    private static GameObject authoredDirectedObject(
            int x, int y, int objectId, int permanentObjectId, int direction) {
        GameObjectLoc loc = new GameObjectLoc(
            objectId, permanentObjectId, x, y, direction, 0);
        loc.assignAuthoredPlacementIdentity(
            new LayeredAuthoredPlacementIdentity(
                7L, 1, 1, 22, ConstructionKind.SCENERY));
        return new GameObject(null, loc);
    }

    private static Result managerProjection(
            GameObject object, boolean forceFullBlock) {
        Definition definition = object.getID() == 500
            ? Definition.scenery(2, 1, 1, "directional", ALLOWLIST)
            : BLOCKING;
        return GameTickEventRestorationCollisionFootprintPlanner.plan(
            Operation.REGISTER,
            ConstructorState.of(
                object.getID(), object.getLoc().getX(),
                object.getLoc().getY(), object.getDirection(),
                object.getType()),
            definition, forceFullBlock, BOUNDS);
    }
'''


def build_fixture():
    fixture = BASE["build_fixture"]()
    fixture = fixture.replace(
        "        collisionSnapshotMismatchRefusesUnchanged();",
        "        collisionSnapshotMismatchRefusesUnchanged();\n"
        "        regionManagerReconstructsAndAppliesWithoutLoading();\n"
        "        forceFullBlockProjectionIsSelectedOnlyWhenExact();\n"
        "        missingRegionsAndProjectionRefuseWithoutCreation();\n"
        "        nonmatchingProjectionRefusesBeforeRegionMutation();",
    )
    fixture = fixture.replace(
        "    private static GameTickEventRestorationCurrentStateRecoverySnapshot\n"
        "            currentSnapshot(",
        EXTRA_METHODS +
        "\n    private static GameTickEventRestorationCurrentStateRecoverySnapshot\n"
        "            currentSnapshot(",
    )
    return fixture


class LayeredMapsSliceOneHundredFortySevenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-current-state-manager-"
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
                str(CURRENT), str(TRANSACTION), str(REGION_MANAGER),
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

    def test_non_loading_region_manager_fixture(self):
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

    def test_adapter_reconstructs_and_selects_exact_projection(self):
        source = REGION_MANAGER.read_text(encoding="utf-8")
        start = source.index(
            "applyGameTickEventCurrentStateRecoverySnapshot("
        )
        end = source.index(
            "public enum RestorationCommitOutcome", start
        )
        method = source[start:end]
        self.assertIn("new GameObjectLoc(", method)
        self.assertIn("assignSerializedAuthoredPlacementIdentity(", method)
        self.assertIn("project(current, false)", method)
        self.assertIn("project(current, true)", method)
        self.assertIn("matchesCurrentStateRecoveryFootprint(", method)
        self.assertIn("executeCurrentStateRecovery(", method)

    def test_adapter_never_loads_and_scheduler_paths_remain_disconnected(self):
        source = REGION_MANAGER.read_text(encoding="utf-8")
        start = source.index(
            "applyGameTickEventCurrentStateRecoverySnapshot("
        )
        end = source.index(
            "public enum RestorationCommitOutcome", start
        )
        method = source[start:end]
        self.assertIn("peekRegionFromSectorCoordinates(", method)
        self.assertNotIn("getRegionFromSectorCoordinates(", method)
        self.assertNotIn(".load(", method)
        name = "applyGameTickEventCurrentStateRecoverySnapshot("
        self.assertNotIn(name, STORE.read_text(encoding="utf-8"))
        self.assertNotIn(name, COORDINATOR.read_text(encoding="utf-8"))
        for required in (
            "isSchedulerStateTouched() { return false; }",
            "isCallbackInvoked() { return false; }",
            "isEventCancellation() { return false; }",
            "isEventReschedule() { return false; }",
            "isLoadingPerformed() { return false; }",
            "isArrivalGate() { return false; }",
            "isVisibilityReleased() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, method)

    def test_living_plan_records_slice_one_hundred_forty_seven(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 147: Non-loading current-state reconstruction adapter",
            plan,
        )
        normalized = " ".join(plan.split())
        self.assertIn("normal and force-full-block projections", normalized)
        self.assertIn("missing required Region refuses", normalized)


if __name__ == "__main__":
    unittest.main()
