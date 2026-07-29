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
COLLISION_EXECUTOR = REGION_SRC / (
    "RegionCollisionFootprintMutationExecutor.java"
)
REGION = REGION_SRC / "Region.java"
ENTITY = ENTITY_SRC / "Entity.java"
GAME_OBJECT = ENTITY_SRC / "GameObject.java"
CURRENT = SERVER / (
    "src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationCurrentStateRecoverySnapshot.java"
)
STATE = SERVER / (
    "src/com/openrsc/server/event/rsc/GameTickEventRestorationState.java"
)
STORE = SERVER / (
    "src/com/openrsc/server/event/rsc/handler/GameTickEventStore.java"
)
REGION_MANAGER = REGION_SRC / "RegionManager.java"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
BASE = runpy.run_path(str(ROOT / (
    "tests/myworld/"
    "test-layered-maps-slice-one-hundred-thirty-nine.py"
)))


EXTRA_IMPORTS = r'''
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot
        .AuthoredConstructionKind;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot.CallbackExpectation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot.CallbackKind;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot.Creation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot.CurrentScenery;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot
        .ObservedCurrentState;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTransientRollbackSnapshot;
'''


EXTRA_METHODS = r'''
    private static void futureCurrentStateAppliesAndIsIdempotent() {
        FixtureWorld world = new FixtureWorld();
        GameObject current = authoredObject(110, 110, 321, 320);
        Result register = plan(Operation.REGISTER, current, BLOCKING);
        GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
            currentSnapshot(
                CallbackKind.SCENERY_SPAWN,
                ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                320, 320, current, register);
        RegionObjectCollisionTransactionExecutor.CurrentStateRecoveryResult
            applied = currentStateRecovery(
                world, snapshot, null, current, register);
        check(applied.isApplied()
                && applied.isMembershipRegistered()
                && applied.getBoundaryCount() == 1
                && world.regionAt(110, 110).getGameObjects().contains(current)
                && world.tiles.get(110, 110).getBlockingSceneryCount() == 1,
            "future current state registers membership and collision exactly");

        GameObject duplicate = authoredObject(110, 110, 321, 320);
        Result duplicateRegister = plan(
            Operation.REGISTER, duplicate, BLOCKING);
        RegionObjectCollisionTransactionExecutor.CurrentStateRecoveryResult
            noOp = currentStateRecovery(
                world, snapshot, current, duplicate, duplicateRegister);
        check(noOp.isNoOp()
                && !noOp.isMembershipRegistered()
                && world.regionAt(110, 110).getGameObjects().size() == 1
                && world.regionAt(110, 110).getGameObjects().contains(current)
                && duplicate.getLocation() == null
                && world.tiles.get(110, 110).getBlockingSceneryCount() == 1,
            "already-matching current state is an idempotent no-op");
        check(!applied.isEventHandleRetained()
                && !applied.isSchedulerStateTouched()
                && !applied.isCallbackInvoked()
                && !applied.isEventCancellation()
                && !applied.isEventReschedule()
                && !applied.isLoadingPerformed()
                && !applied.isArrivalGate()
                && !applied.isVisibilityReleased()
                && !applied.isLifecycleAuthority(),
            "Region application has no scheduler, loading, or arrival role");
    }

    private static void futureRemovalSnapshotUsesTheSameApplication() {
        FixtureWorld world = new FixtureWorld();
        GameObject current = authoredObject(112, 110, 390, 390);
        Result register = plan(Operation.REGISTER, current, BLOCKING);
        GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
            currentSnapshot(
                CallbackKind.SCENERY_REMOVE,
                ObservedCurrentState.EXACT_RESTORATION_SCENERY_PRESENT,
                390, 390, current, register);
        RegionObjectCollisionTransactionExecutor.CurrentStateRecoveryResult
            applied = currentStateRecovery(
                world, snapshot, null, current, register);
        check(applied.isApplied()
                && world.regionAt(112, 110).getGameObjects().contains(current)
                && world.tiles.get(112, 110).getBlockingSceneryCount() == 1,
            "future removal restores its exact currently-present object");
    }

    private static void conflictAndStaleIdentityRefuseUnchanged() {
        FixtureWorld world = new FixtureWorld();
        GameObject desiredCurrent = authoredObject(114, 110, 321, 320);
        Result desiredRegister = plan(
            Operation.REGISTER, desiredCurrent, BLOCKING);
        GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
            currentSnapshot(
                CallbackKind.SCENERY_SPAWN,
                ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                320, 320, desiredCurrent, desiredRegister);
        GameObject conflict = authoredObject(114, 110, 322, 322);
        Prepared.register(world, conflict, BLOCKING).execute();
        RegionObjectCollisionTransactionExecutor.CurrentStateRecoveryResult
            refused = currentStateRecovery(
                world, snapshot, conflict, desiredCurrent, desiredRegister);
        check(refused.isRefused()
                && refused.getReason()
                    == RegionObjectCollisionTransactionExecutor
                        .CurrentStateRecoveryReason
                            .TARGET_CLASSIFICATION_REFUSED
                && world.regionAt(114, 110).getGameObjects().contains(conflict)
                && desiredCurrent.getLocation() == null
                && world.tiles.get(114, 110).getBlockingSceneryCount() == 1,
            "conflicting occupant refuses without mutation");

        FixtureWorld staleWorld = new FixtureWorld();
        GameObject observed = authoredObject(116, 110, 321, 320);
        Prepared.register(staleWorld, observed, BLOCKING).execute();
        Prepared.unregister(staleWorld, observed, BLOCKING).execute();
        GameObject replacement = authoredObject(116, 110, 322, 322);
        Prepared.register(staleWorld, replacement, BLOCKING).execute();
        GameObject desired = authoredObject(116, 110, 321, 320);
        Result desiredFootprint = plan(Operation.REGISTER, desired, BLOCKING);
        GameTickEventRestorationCurrentStateRecoverySnapshot staleSnapshot =
            currentSnapshot(
                CallbackKind.SCENERY_SPAWN,
                ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                320, 320, desired, desiredFootprint);
        RegionObjectCollisionTransactionExecutor.CurrentStateRecoveryResult
            stale = currentStateRecovery(
                staleWorld, staleSnapshot, observed,
                desired, desiredFootprint);
        check(stale.isRefused()
                && stale.getReason()
                    == RegionObjectCollisionTransactionExecutor
                        .CurrentStateRecoveryReason
                            .TARGET_CHANGED_BEFORE_RECOVERY
                && staleWorld.regionAt(116, 110).getGameObjects()
                    .contains(replacement)
                && staleWorld.tiles.get(116, 110)
                    .getBlockingSceneryCount() == 1,
            "stale observed identity refuses without mutation");
    }

    private static void collisionSnapshotMismatchRefusesUnchanged() {
        FixtureWorld world = new FixtureWorld();
        GameObject current = authoredObject(118, 110, 321, 320);
        Definition nonBlocking = Definition.scenery(
            0, 1, 1, "Decoration", ALLOWLIST);
        Result captured = plan(Operation.REGISTER, current, nonBlocking);
        Result actual = plan(Operation.REGISTER, current, BLOCKING);
        GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
            currentSnapshot(
                CallbackKind.SCENERY_SPAWN,
                ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                320, 320, current, captured);
        RegionObjectCollisionTransactionExecutor.CurrentStateRecoveryResult
            refused = currentStateRecovery(
                world, snapshot, null, current, actual);
        check(refused.isRefused()
                && refused.getReason()
                    == RegionObjectCollisionTransactionExecutor
                        .CurrentStateRecoveryReason
                            .CURRENT_COLLISION_SNAPSHOT_MISMATCH
                && world.regionAt(118, 110).getGameObjects().isEmpty()
                && current.getLocation() == null
                && world.tiles.nonZeroTileCount() == 0,
            "collision mismatch refuses before membership mutation");
    }

    private static RegionObjectCollisionTransactionExecutor
            .CurrentStateRecoveryResult currentStateRecovery(
                FixtureWorld world,
                GameTickEventRestorationCurrentStateRecoverySnapshot snapshot,
                GameObject observed,
                GameObject current,
                Result register) {
        List<Result> footprints = Collections.singletonList(register);
        return RegionObjectCollisionTransactionExecutor
            .executeCurrentStateRecovery(
                world.boundaries(footprints),
                world.regionAt(snapshot.getX(), snapshot.getY()),
                snapshot, observed, current, register, world.tiles,
                region -> world.invalidations.incrementAndGet());
    }

    private static GameTickEventRestorationCurrentStateRecoverySnapshot
            currentSnapshot(
                CallbackKind kind,
                ObservedCurrentState observedState,
                int callbackObjectId,
                int callbackPermanentObjectId,
                GameObject current,
                Result footprint) {
        CallbackExpectation callback = CallbackExpectation.declare(
            kind, "fixture-scheduler", 37L, 7L, 5L, 12L,
            0, true, true, true,
            callbackObjectId, callbackPermanentObjectId,
            current.getLoc().getX(), current.getLoc().getY(),
            current.getDirection(), current.getType(), null, 0,
            7L, 1, 1, 22, AuthoredConstructionKind.SCENERY);
        List<GameTickEventRestorationCurrentStateRecoverySnapshot
            .CollisionContribution> collision = new ArrayList<>();
        for (GameTickEventRestorationTransientRollbackSnapshot
                .CollisionContribution contribution
                : footprint.getContributions()) {
            collision.add(
                GameTickEventRestorationCurrentStateRecoverySnapshot
                    .CollisionContribution.ofCounts(
                        contribution.getX(), contribution.getY(),
                        contribution.getBlockingSceneryCount(),
                        contribution.getDynamicCollisionCounts(),
                        contribution.getDynamicProjectileCount()));
        }
        CurrentScenery currentState = CurrentScenery.declare(
            observedState, current.getID(), current.getLoc().getPermId(),
            current.getLoc().getX(), current.getLoc().getY(),
            current.getDirection(), current.getType(), null, 0,
            7L, 1, 1, 22, AuthoredConstructionKind.SCENERY,
            1, true, true, true, true, true, collision);
        Creation creation =
            GameTickEventRestorationCurrentStateRecoverySnapshot.assess(
                callback, currentState);
        check(creation.isSnapshotAvailable(),
            "fixture current-state snapshot available");
        return creation.getSnapshot();
    }
'''


def build_fixture():
    fixture = BASE["build_fixture"]()
    fixture = fixture.replace(
        "import com.openrsc.server.model.Point;",
        "import com.openrsc.server.model.Point;\n" + EXTRA_IMPORTS,
    )
    fixture = fixture.replace(
        "        asymmetricCollisionTransientRefusesUnchanged();",
        "        asymmetricCollisionTransientRefusesUnchanged();\n"
        "        futureCurrentStateAppliesAndIsIdempotent();\n"
        "        futureRemovalSnapshotUsesTheSameApplication();\n"
        "        conflictAndStaleIdentityRefuseUnchanged();\n"
        "        collisionSnapshotMismatchRefusesUnchanged();",
    )
    fixture = fixture.replace(
        "    private static GameObject authoredObject(",
        EXTRA_METHODS + "\n    private static GameObject authoredObject(",
    )
    return fixture


class LayeredMapsSliceOneHundredFortySixTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-current-state-region-"
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
                str(STATE), str(CURRENT), str(BOUNDARY),
                str(COLLISION_EXECUTOR), str(TRANSACTION),
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

    def test_disconnected_current_state_region_fixture(self):
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

    def test_application_uses_existing_ordered_transaction(self):
        source = TRANSACTION.read_text(encoding="utf-8")
        method = source[source.index(
            "static CurrentStateRecoveryResult executeCurrentStateRecovery("
        ):source.index(
            "private static RestorationResult executeRestorationInsideBoundaries("
        )]
        self.assertIn("RegionObjectCollisionMutationBoundary.executeMutation(", method)
        self.assertIn("getGameObjectTransactionMonitor()", method)
        self.assertIn("executeInsideBoundaries(", method)
        self.assertIn("matchesCurrentStateRecoveryFootprint(", method)
        self.assertIn("CURRENT_STATE_ALREADY_SATISFIED", source)
        self.assertIn("CURRENT_STATE_RESTORED", source)

    def test_store_loading_and_arrival_remain_disconnected(self):
        method = "executeCurrentStateRecovery("
        self.assertNotIn(method, STORE.read_text(encoding="utf-8"))
        self.assertIn(method, REGION_MANAGER.read_text(encoding="utf-8"))
        source = TRANSACTION.read_text(encoding="utf-8")
        for required in (
            "isEventHandleRetained() { return false; }",
            "isSchedulerStateTouched() { return false; }",
            "isCallbackInvoked() { return false; }",
            "isEventCancellation() { return false; }",
            "isEventReschedule() { return false; }",
            "isLoadingPerformed() { return false; }",
            "isArrivalGate() { return false; }",
            "isVisibilityReleased() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_living_plan_records_slice_one_hundred_forty_six(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 146: Disconnected future current-state application",
            plan,
        )
        normalized = " ".join(plan.split())
        self.assertIn("already-matching current object", normalized)
        self.assertIn("scheduler event remains untouched", normalized)


if __name__ == "__main__":
    unittest.main()
