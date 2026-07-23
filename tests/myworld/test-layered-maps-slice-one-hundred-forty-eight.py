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
    "test-layered-maps-slice-one-hundred-forty-seven.py"
)))


EXTRA_IMPORT = r'''
import com.openrsc.server.model.entity
    .GameObjectCollisionRegistrationState;
'''


EXTRA_METHODS = r'''
    private static void exactCollisionProvenanceFollowsCommittedMembership() {
        FixtureWorld world = new FixtureWorld();
        GameObject object = authoredObject(150, 150, 321, 320);
        Result footprint = plan(Operation.REGISTER, object, BLOCKING);
        RegionObjectCollisionTransactionExecutor.Result registered =
            Prepared.register(world, object, BLOCKING).execute();
        GameObjectCollisionRegistrationState state =
            object.getCollisionRegistrationState();
        check(registered.isApplied()
                && state != null
                && state.matchesConstructor(object)
                && state.getObjectId() == 321
                && state.getPermanentObjectId() == 320
                && state.getContributionTileCount()
                    == footprint.getContributionTileCount()
                && state.getRequiredRegionCount()
                    == footprint.getRequiredRegionCount()
                && state.getContributions().get(0)
                    .getBlockingSceneryCount() == 1,
            "successful registration records exact detached collision state");

        RegionObjectCollisionTransactionExecutor.Result removed =
            Prepared.unregister(world, object, BLOCKING).execute();
        check(removed.isApplied()
                && object.getCollisionRegistrationState() == null,
            "successful removal clears collision registration provenance");
    }

    private static void replacementTransfersOnlyCommittedProvenance() {
        FixtureWorld world = new FixtureWorld();
        GameObject oldObject = authoredObject(152, 150, 321, 320);
        Prepared.register(world, oldObject, BLOCKING).execute();
        GameObjectCollisionRegistrationState oldState =
            oldObject.getCollisionRegistrationState();
        GameObject newObject = authoredObject(154, 150, 322, 322);
        RegionObjectCollisionTransactionExecutor.Result replaced =
            Prepared.replace(
                world, oldObject, BLOCKING, newObject, BLOCKING).execute();
        check(replaced.isApplied()
                && oldObject.getCollisionRegistrationState() == null
                && newObject.getCollisionRegistrationState() != null
                && newObject.getCollisionRegistrationState()
                    .matchesConstructor(newObject)
                && oldState != newObject.getCollisionRegistrationState(),
            "committed replacement clears old and records new provenance");
    }

    private static void refusedReplacementPreservesOldAndAttachesNoNewState() {
        FixtureWorld world = new FixtureWorld();
        GameObject oldObject = authoredObject(156, 150, 321, 320);
        Prepared.register(world, oldObject, BLOCKING).execute();
        GameObjectCollisionRegistrationState oldState =
            oldObject.getCollisionRegistrationState();
        GameObject newObject = authoredObject(158, 150, 322, 322);
        world.tiles.put(158, 150, new TileValue(
            (byte) 0, (short) 0, (byte) 0, (byte) 0, (byte) 0,
            (byte) 0, false, false, false, Integer.MAX_VALUE, 0,
            new int[6], false, 0, 0));
        RegionObjectCollisionTransactionExecutor.Result refused =
            Prepared.replace(
                world, oldObject, BLOCKING, newObject, BLOCKING).execute();
        check(refused.isRefused()
                && oldObject.getCollisionRegistrationState() == oldState
                && oldState.matchesConstructor(oldObject)
                && newObject.getCollisionRegistrationState() == null
                && world.regionAt(156, 150).getGameObjects()
                    .contains(oldObject),
            "refused replacement preserves exact old provenance only");
    }

    private static void forceFullBlockContributionIsCopiedExactly() {
        GameObject object = authoredObject(160, 150, 321, 320);
        Result forced = managerProjection(object, true);
        GameObjectCollisionRegistrationState state =
            GameObjectCollisionRegistrationState.capture(object, forced);
        check(state.getContributionTileCount() == 1
                && state.getContributions().get(0)
                    .getBlockingSceneryCount() == 2
                && state.getRequiredRegionCount() == 1
                && state.isDetachedPrimitiveCopy()
                && !state.isRuntimeHandleRetained()
                && !state.isRegionLoadingPerformed()
                && !state.isMutationAuthorized()
                && !state.isMutationPerformed()
                && !state.isArrivalGate()
                && !state.isLifecycleAuthority(),
            "registration state copies forced contribution without authority");
    }
'''


def build_fixture():
    fixture = BASE["build_fixture"]()
    fixture = fixture.replace(
        "import com.openrsc.server.model.Point;",
        "import com.openrsc.server.model.Point;\n" + EXTRA_IMPORT,
    )
    fixture = fixture.replace(
        "        nonmatchingProjectionRefusesBeforeRegionMutation();",
        "        nonmatchingProjectionRefusesBeforeRegionMutation();\n"
        "        exactCollisionProvenanceFollowsCommittedMembership();\n"
        "        replacementTransfersOnlyCommittedProvenance();\n"
        "        refusedReplacementPreservesOldAndAttachesNoNewState();\n"
        "        forceFullBlockContributionIsCopiedExactly();",
    )
    fixture = fixture.replace(
        "    private static GameTickEventRestorationCurrentStateRecoverySnapshot\n"
        "            currentSnapshot(",
        EXTRA_METHODS +
        "\n    private static GameTickEventRestorationCurrentStateRecoverySnapshot\n"
        "            currentSnapshot(",
    )
    return fixture


class LayeredMapsSliceOneHundredFortyEightTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-collision-registration-state-"
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
                str(TRANSACTION), str(REGION_MANAGER), str(fixture),
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

    def test_collision_registration_state_fixture(self):
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

    def test_state_is_detached_and_copies_exact_footprint(self):
        source = COLLISION_STATE.read_text(encoding="utf-8")
        self.assertIn("Collections.unmodifiableList", source)
        self.assertIn("source.getDynamicCollisionCounts()", source)
        self.assertIn("checkedFootprint.getRequiredRegions()", source)
        self.assertNotIn("private final GameObject ", source)
        self.assertNotIn("private final Region ", source)
        self.assertNotIn("private final World ", source)
        self.assertNotIn("private final TileValue ", source)
        for required in (
            "isRuntimeHandleRetained() { return false; }",
            "isRegionLoadingPerformed() { return false; }",
            "isMutationAuthorized() { return false; }",
            "isMutationPerformed() { return false; }",
            "isArrivalGate() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_only_atomic_transaction_attaches_or_clears_state(self):
        attach = "attachOrderedCollisionRegistrationState("
        clear = "clearOrderedCollisionRegistrationState("
        for path in (ROOT / "server/src").rglob("*.java"):
            if path in (GAME_OBJECT, TRANSACTION):
                continue
            source = path.read_text(encoding="utf-8")
            self.assertNotIn(attach, source)
            self.assertNotIn(clear, source)
        transaction = TRANSACTION.read_text(encoding="utf-8")
        self.assertIn(attach, transaction)
        self.assertIn(clear, transaction)
        self.assertNotIn(
            "GameObjectCollisionRegistrationState",
            STORE.read_text(encoding="utf-8"),
        )

    def test_living_plan_records_slice_one_hundred_forty_eight(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 148: Exact collision-registration provenance",
            plan,
        )
        normalized = " ".join(plan.split())
        self.assertIn("normal versus force-full-block", normalized)
        self.assertIn("refused replacement preserves", normalized)


if __name__ == "__main__":
    unittest.main()
