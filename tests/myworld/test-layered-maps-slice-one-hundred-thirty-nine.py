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
STORE = SERVER / (
    "src/com/openrsc/server/event/rsc/handler/GameTickEventStore.java"
)
ROLLBACK = SERVER / (
    "src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationTransientRollbackSnapshot.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
BASE = runpy.run_path(str(ROOT / (
    "tests/myworld/"
    "test-layered-maps-slice-one-hundred-thirty-eight.py"
)))


EXTRA_METHODS = r'''
    private static void opaqueTransientStateRefusesUnchanged() {
        FixtureWorld world = new FixtureWorld();
        GameObject transientObject = authoredObject(90, 90, 321, 320);
        Prepared.register(world, transientObject, BLOCKING).execute();
        transientObject.setAttribute("opaque-fixture-state", true);
        GameObject desired = authoredObject(90, 90, 320, 320);
        GameTickEventRestorationCommitRequest spawn = request(
            TargetOperation.SCENERY_SPAWN, 320, 320, 90, 90);
        RegionObjectCollisionTransactionExecutor.RestorationResult result =
            restoration(world, spawn, transientObject, desired);
        check(result.isRefused()
                && result.getReason()
                    == RegionObjectCollisionTransactionExecutor
                        .RestorationReason
                            .TRANSIENT_ROLLBACK_SNAPSHOT_REFUSED
                && world.regionAt(90, 90).getGameObjects()
                    .contains(transientObject)
                && world.tiles.get(90, 90).getBlockingSceneryCount() == 1
                && desired.getLocation() == null,
            "opaque transient state refuses without partial mutation");
    }

    private static void asymmetricCollisionTransientRefusesUnchanged() {
        FixtureWorld world = new FixtureWorld();
        GameObject transientObject = authoredObject(100, 100, 1147, 320);
        Prepared.register(world, transientObject, BLOCKING).execute();
        GameObject desired = authoredObject(100, 100, 320, 320);
        GameTickEventRestorationCommitRequest spawn = request(
            TargetOperation.SCENERY_SPAWN, 320, 320, 100, 100);
        RegionObjectCollisionTransactionExecutor.RestorationResult result =
            restoration(world, spawn, transientObject, desired);
        check(result.isRefused()
                && result.getReason()
                    == RegionObjectCollisionTransactionExecutor
                        .RestorationReason
                            .TRANSIENT_COLLISION_ROLLBACK_MISMATCH
                && world.regionAt(100, 100).getGameObjects()
                    .contains(transientObject)
                && world.tiles.get(100, 100).getBlockingSceneryCount() == 0
                && desired.getLocation() == null,
            "asymmetric transient collision refuses without mutation");
    }
'''


def build_fixture():
    fixture = BASE["build_fixture"]()
    fixture = fixture.replace(
        "        staleCandidateRefusesWithoutMutation();",
        "        staleCandidateRefusesWithoutMutation();\n"
        "        opaqueTransientStateRefusesUnchanged();\n"
        "        asymmetricCollisionTransientRefusesUnchanged();",
    )
    fixture = fixture.replace(
        "    private static GameObject authoredObject(",
        EXTRA_METHODS + "\n    private static GameObject authoredObject(",
    )
    return fixture


class LayeredMapsSliceOneHundredThirtyNineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-thirty-nine-"
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

    def test_transient_replacement_fixture(self):
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

    def test_snapshot_is_captured_only_inside_the_closed_transaction(self):
        transaction = TRANSACTION.read_text(encoding="utf-8")
        method = transaction[transaction.index(
            "private static RestorationResult applyTransientReplacement("
        ):transaction.index(
            "private static boolean collisionRollbackIsExact(",
        )]
        self.assertIn(
            "GameTickEventRestorationTransientRollbackSnapshot.Candidate",
            method,
        )
        self.assertIn(
            "GameTickEventRestorationTransientRollbackSnapshot.assess(",
            method,
        )
        self.assertIn("getRuntimeAttributeCount()", method)
        self.assertIn("target.getSlotObjectCount()", method)
        self.assertIn("oldChange.forward.getContributions()", method)
        self.assertIn("collisionRollbackIsExact(", transaction)

    def test_scheduler_store_remains_disconnected(self):
        store = STORE.read_text(encoding="utf-8")
        self.assertNotIn(
            "applyGameTickEventRestorationCommitRequest(", store
        )
        rollback = ROLLBACK.read_text(encoding="utf-8")
        self.assertIn("isDormantSnapshot() { return false; }", rollback)
        self.assertIn(
            "isRuntimeConsumerConnected() { return true; }", rollback
        )
        for forbidden in (
            "import com.openrsc.server.model",
            "GameObject object", "TileValue tile", "World world",
            "Region region", "synchronized (", ".doRun()", ".stop()",
        ):
            self.assertNotIn(forbidden, rollback)

    def test_living_plan_records_slice_one_hundred_thirty_nine(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 139: Exact authored-transient replacement", plan
        )
        normalized = " ".join(plan.split())
        self.assertIn("opaque runtime attributes refuse", normalized)
        self.assertIn("scheduler Store remains disconnected", normalized)


if __name__ == "__main__":
    unittest.main()
