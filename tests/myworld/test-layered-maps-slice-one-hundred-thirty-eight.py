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
TRANSACTION = REGION_SRC / "RegionObjectCollisionTransactionExecutor.java"
BOUNDARY = REGION_SRC / "RegionObjectCollisionMutationBoundary.java"
COLLISION_EXECUTOR = REGION_SRC / "RegionCollisionFootprintMutationExecutor.java"
REGION = REGION_SRC / "Region.java"
ENTITY = ENTITY_SRC / "Entity.java"
GAME_OBJECT = ENTITY_SRC / "GameObject.java"
WORLD = SERVER / "src/com/openrsc/server/model/world/World.java"
REGION_MANAGER = REGION_SRC / "RegionManager.java"
AUTHORED_IDENTITY = SERVER / (
    "src/com/openrsc/server/model/world/coordinate/"
    "LayeredAuthoredPlacementIdentity.java"
)
GAME_OBJECT_LOC = SERVER / "src/com/openrsc/server/external/GameObjectLoc.java"
STORE = SERVER / (
    "src/com/openrsc/server/event/rsc/handler/GameTickEventStore.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
BASE = runpy.run_path(str(ROOT / (
    "tests/myworld/"
    "test-layered-maps-slice-one-hundred-thirty-six.py"
)))


EXTRA_IMPORTS = r'''
import com.openrsc.server.event.rsc.GameTickEventRestorationCommitRequest;
import com.openrsc.server.event.rsc.GameTickEventRestorationTargetDecision
    .TargetOperation;
import com.openrsc.server.external.GameObjectLoc;
import com.openrsc.server.model.world.coordinate.LayeredAuthoredPlacementIdentity;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionAuthoredConstructionInventory.ConstructionKind;
'''


EXTRA_METHODS = r'''
    private static void restorationSpawnNoOpAndRemovalAreAtomic() {
        FixtureWorld world = new FixtureWorld();
        GameTickEventRestorationCommitRequest spawn = request(
            TargetOperation.SCENERY_SPAWN, 320, 320, 60, 60);
        GameObject desired = authoredObject(60, 60, 320, 320);
        RegionObjectCollisionTransactionExecutor.RestorationResult applied =
            restoration(world, spawn, null, desired);
        check(applied.isApplied()
                && applied.isMembershipRegistered()
                && !applied.isMembershipRemoved()
                && applied.getBoundaryCount() == 1
                && world.regionAt(60, 60).getGameObjects().contains(desired)
                && world.tiles.get(60, 60).getBlockingSceneryCount() == 1,
            "spawn into empty commits membership and collision");

        GameObject duplicateDesired = authoredObject(60, 60, 320, 320);
        RegionObjectCollisionTransactionExecutor.RestorationResult noOp =
            restoration(world, spawn, desired, duplicateDesired);
        check(noOp.isNoOp()
                && !noOp.isMembershipRemoved()
                && !noOp.isMembershipRegistered()
                && world.regionAt(60, 60).getGameObjects().size() == 1
                && world.regionAt(60, 60).getGameObjects().contains(desired)
                && duplicateDesired.getLocation() == null,
            "already-present exact restoration is an idempotent no-op");

        GameTickEventRestorationCommitRequest remove = request(
            TargetOperation.SCENERY_REMOVE, 320, 320, 60, 60);
        RegionObjectCollisionTransactionExecutor.RestorationResult removed =
            restoration(world, remove, desired, null);
        check(removed.isApplied()
                && removed.isMembershipRemoved()
                && !removed.isMembershipRegistered()
                && desired.isRemoved()
                && world.regionAt(60, 60).getGameObjects().isEmpty()
                && world.tiles.get(60, 60).getBlockingSceneryCount() == 0,
            "exact removal commits membership and collision");

        RegionObjectCollisionTransactionExecutor.RestorationResult absent =
            restoration(world, remove, null, null);
        check(absent.isNoOp()
                && world.regionAt(60, 60).getGameObjects().isEmpty(),
            "already-absent removal is an idempotent no-op");
    }

    private static void authoredTransientReplacementCommitsAtomically() {
        FixtureWorld world = new FixtureWorld();
        GameObject transientObject = authoredObject(70, 70, 321, 320);
        Prepared.register(world, transientObject, BLOCKING).execute();
        GameObject desired = authoredObject(70, 70, 320, 320);
        GameTickEventRestorationCommitRequest spawn = request(
            TargetOperation.SCENERY_SPAWN, 320, 320, 70, 70);
        RegionObjectCollisionTransactionExecutor.RestorationResult result =
            restoration(world, spawn, transientObject, desired);
        check(result.isApplied()
                && result.isMembershipRemoved()
                && result.isMembershipRegistered()
                && transientObject.isRemoved()
                && world.regionAt(70, 70).getGameObjects().contains(desired)
                && world.tiles.get(70, 70).getBlockingSceneryCount() == 1
                && desired.getLocation() != null,
            "exact transient replacement commits membership and collision");
    }

    private static void staleCandidateRefusesWithoutMutation() {
        FixtureWorld world = new FixtureWorld();
        GameObject observed = authoredObject(80, 80, 320, 320);
        Prepared.register(world, observed, BLOCKING).execute();
        Result observedUnregister = plan(
            Operation.UNREGISTER, observed, BLOCKING);
        Result observedRollback = plan(
            Operation.REGISTER, observed, BLOCKING);
        Prepared.unregister(world, observed, BLOCKING).execute();
        GameObject replacement = authoredObject(80, 80, 322, 322);
        Prepared.register(world, replacement, BLOCKING).execute();
        GameTickEventRestorationCommitRequest remove = request(
            TargetOperation.SCENERY_REMOVE, 320, 320, 80, 80);
        List<Result> footprints = new ArrayList<>();
        footprints.add(observedUnregister);
        footprints.add(observedRollback);
        RegionObjectCollisionTransactionExecutor.RestorationResult result =
            RegionObjectCollisionTransactionExecutor.executeRestoration(
                world.boundaries(footprints), world.regionAt(80, 80),
                remove, observed, observedUnregister, observedRollback,
                null, null, world.tiles,
                region -> world.invalidations.incrementAndGet());
        check(result.isRefused()
                && result.getReason()
                    == RegionObjectCollisionTransactionExecutor
                        .RestorationReason.TARGET_CHANGED_BEFORE_COMMIT
                && world.regionAt(80, 80).getGameObjects()
                    .contains(replacement)
                && world.tiles.get(80, 80).getBlockingSceneryCount() == 1,
            "candidate identity change refuses before mutation");
    }

    private static RegionObjectCollisionTransactionExecutor.RestorationResult
            restoration(
                FixtureWorld world,
                GameTickEventRestorationCommitRequest request,
                GameObject candidate,
                GameObject desired) {
        Result candidateUnregister = candidate == null ? null
            : plan(Operation.UNREGISTER, candidate, BLOCKING);
        Result candidateRollback = candidate == null ? null
            : plan(Operation.REGISTER, candidate, BLOCKING);
        Result desiredRegister = desired == null ? null
            : plan(Operation.REGISTER, desired, BLOCKING);
        List<Result> footprints = new ArrayList<>();
        if (candidateUnregister != null) {
            footprints.add(candidateUnregister);
            footprints.add(candidateRollback);
        }
        if (desiredRegister != null) { footprints.add(desiredRegister); }
        List<RegionObjectCollisionMutationBoundary> boundaries;
        if (footprints.isEmpty()) {
            boundaries = Collections.singletonList(
                world.regionAt(request.getX(), request.getY())
                    .getObjectCollisionMutationBoundary());
        } else {
            boundaries = world.boundaries(footprints);
        }
        return RegionObjectCollisionTransactionExecutor.executeRestoration(
            boundaries, world.regionAt(request.getX(), request.getY()),
            request, candidate, candidateUnregister, candidateRollback,
            desired, desiredRegister, world.tiles,
            region -> world.invalidations.incrementAndGet());
    }

    private static GameTickEventRestorationCommitRequest request(
            TargetOperation operation,
            int objectId,
            int permanentObjectId,
            int x,
            int y) {
        return GameTickEventRestorationCommitRequest.request(
            "fixture-scheduler", 11L, 7L, 7L, 5L,
            true, false, true, true, operation,
            objectId, permanentObjectId, x, y, 0, 0, false,
            1, 1, 22, "SCENERY");
    }

    private static GameObject authoredObject(
            int x, int y, int objectId, int permanentObjectId) {
        GameObjectLoc loc = new GameObjectLoc(
            objectId, permanentObjectId, x, y, 0, 0);
        loc.assignAuthoredPlacementIdentity(
            new LayeredAuthoredPlacementIdentity(
                7L, 1, 1, 22, ConstructionKind.SCENERY));
        return new GameObject(null, loc);
    }
'''


def build_fixture():
    fixture = BASE["FIXTURE"]
    fixture = fixture.replace(
        "import com.openrsc.server.model.Point;",
        "import com.openrsc.server.model.Point;\n" + EXTRA_IMPORTS,
    )
    fixture = fixture.replace(
        "        sameRegionTransactionsExcludeEachOther();",
        "        sameRegionTransactionsExcludeEachOther();\n"
        "        restorationSpawnNoOpAndRemovalAreAtomic();\n"
        "        authoredTransientReplacementCommitsAtomically();\n"
        "        staleCandidateRefusesWithoutMutation();",
    )
    fixture = fixture.replace(
        "    private static GameObject object(int x, int y, int id) {",
        EXTRA_METHODS +
        "\n    private static GameObject object(int x, int y, int id) {",
    )
    return fixture


class LayeredMapsSliceOneHundredThirtyEightTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-thirty-eight-"
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
                str(BOUNDARY), str(COLLISION_EXECUTOR), str(TRANSACTION),
                str(ENTITY), str(GAME_OBJECT), str(REGION), str(fixture),
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

    def test_disconnected_restoration_executor_fixture(self):
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

    def test_region_manager_consumes_only_the_closed_request(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        world = WORLD.read_text(encoding="utf-8")
        self.assertIn(
            "public RestorationCommitResult "
            "applyGameTickEventRestorationCommitRequest(", manager
        )
        method = manager[manager.index(
            "applyGameTickEventRestorationCommitRequest("
        ):manager.index(
            "private RegionCollisionFootprintMutationExecutor.Result",
            manager.index("applyGameTickEventRestorationCommitRequest("),
        )]
        self.assertIn("peekRegionFromSectorCoordinates(", method)
        self.assertIn(
            "RegionObjectCollisionTransactionExecutor.executeRestoration(",
            method,
        )
        self.assertIn("projectGameObjectCollisionFootprint(", method)
        self.assertIn(
            "public GameTickEventRestorationCollisionFootprintPlanner.Result\n"
            "\t\tprojectGameObjectCollisionFootprint(", world
        )
        self.assertNotIn(
            "LayeredPackedRegionAuthoredConstructionInventory", manager
        )
        identity = AUTHORED_IDENTITY.read_text(encoding="utf-8")
        self.assertIn("fromSerializedConstructionKind(", identity)
        self.assertIn("ConstructionKind.valueOf(constructionKind)", identity)
        game_object_loc = GAME_OBJECT_LOC.read_text(encoding="utf-8")
        self.assertIn(
            "assignSerializedAuthoredPlacementIdentity(", game_object_loc
        )
        self.assertIn(
            "loc.assignSerializedAuthoredPlacementIdentity(", manager
        )

    def test_runtime_scheduler_remains_disconnected_and_transient_refuses(self):
        store = STORE.read_text(encoding="utf-8")
        transaction = TRANSACTION.read_text(encoding="utf-8")
        self.assertNotIn(
            "applyGameTickEventRestorationCommitRequest(", store
        )
        self.assertIn(
            "GameTickEventRestorationTransientRollbackSnapshot", transaction
        )
        result = REGION_MANAGER.read_text(encoding="utf-8")
        for required in (
            "isRequestRetained() { return false; }",
            "isEventHandleRetained() { return false; }",
            "isEntityHandleRetained() { return false; }",
            "isRegionHandleRetained() { return false; }",
            "isCallbackInvoked() { return false; }",
            "isEventCancellation() { return false; }",
            "isEventReschedule() { return false; }",
            "isExecutableRestoration() { return false; }",
            "isCommitToken() { return false; }",
            "isArrivalGate() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, result)

    def test_living_plan_records_slice_one_hundred_thirty_eight(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 138: Disconnected restoration object-state commit",
            plan,
        )
        normalized = " ".join(plan.split())
        self.assertIn(
            "authored-transient replacement remains refused", normalized
        )
        self.assertIn("Store remains disconnected", normalized)


if __name__ == "__main__":
    unittest.main()
