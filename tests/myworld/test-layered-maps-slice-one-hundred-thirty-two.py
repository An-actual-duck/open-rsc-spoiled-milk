#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RSC = ROOT / "server/src/com/openrsc/server/event/rsc"
STATE = RSC / "GameTickEventRestorationState.java"
REQUIREMENT = RSC / "GameTickEventRestorationRequirement.java"
DECISION = RSC / "GameTickEventRestorationTargetDecision.java"
COMMIT_REQUEST = RSC / "GameTickEventRestorationCommitRequest.java"
ATOMIC_CONTRACT = RSC / "GameTickEventRestorationAtomicRevalidationContract.java"
REQUEST = RSC / "GameTickEventRestorationTargetRevalidationRequest.java"
REVALIDATION = RSC / "GameTickEventRestorationTargetRevalidation.java"
INTENT = RSC / "GameTickEventRestorationMutationIntent.java"
ROLLBACK = RSC / "GameTickEventRestorationTransientRollbackSnapshot.java"
TRANSACTION = RSC / "GameTickEventRestorationCollisionTransactionContract.java"
PLANNER = RSC / "GameTickEventRestorationCollisionFootprintPlanner.java"
COLLISION_FLAG = ROOT / (
    "server/src/com/openrsc/server/util/rsc/CollisionFlag.java"
)
POLICY = ROOT / (
    "server/src/com/openrsc/server/util/rsc/"
    "LegacyObjectProjectileCollisionPolicy.java"
)
WORLD = ROOT / "server/src/com/openrsc/server/model/world/World.java"
STORE = RSC / "handler/GameTickEventStore.java"
HANDLER = RSC / "handler/GameEventHandler.java"
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.event.rsc;

import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionFootprintPlanner.ConstructorState;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionFootprintPlanner.Definition;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionFootprintPlanner.Operation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionFootprintPlanner.Result;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionFootprintPlanner.WorldBounds;
import com.openrsc.server.util.rsc.LegacyObjectProjectileCollisionPolicy;

public final class LegacyProjectileClassificationFixture {
    private static final String[] ALLOWLIST = {
        "gravestone", "gate", "chair"
    };

    public static void main(String[] args) {
        reproducesSceneryDecisionOrder();
        reproducesBoundaryAllowlistRule();
        plannerDefinitionUsesTheSharedPolicy();
        refusesMissingClassificationInputs();
    }

    private static void reproducesSceneryDecisionOrder() {
        check(scenery("DeadTREE", 8, 9),
            "tree substring wins before dimensions or allowlist");
        check(scenery("rock", 1, 1),
            "one-by-one non-chest clips while allowlist is populated");
        check(!scenery("ChEsT", 1, 1),
            "one-by-one chest exclusion remains case-insensitive");
        check(scenery("GATE", 4, 3),
            "larger allowlisted scenery clips case-insensitively");
        check(!scenery("statue", 4, 3),
            "larger non-allowlisted scenery does not clip");
        check(!LegacyObjectProjectileCollisionPolicy
                .allowsSceneryProjectileClip(
                    "rock", 1, 1, new String[0]),
            "legacy one-by-one check remains inside the allowlist loop");
    }

    private static void reproducesBoundaryAllowlistRule() {
        check(LegacyObjectProjectileCollisionPolicy
                .allowsBoundaryProjectileClip("GaTe", ALLOWLIST),
            "allowlisted boundary clips case-insensitively");
        check(!LegacyObjectProjectileCollisionPolicy
                .allowsBoundaryProjectileClip("door", ALLOWLIST),
            "ordinary boundary does not gain the scenery dimension rule");
    }

    private static void plannerDefinitionUsesTheSharedPolicy() {
        Definition gate = Definition.scenery(
            2, 2, 1, "gate", ALLOWLIST);
        Definition chest = Definition.scenery(
            1, 1, 1, "chest", ALLOWLIST);
        check(gate.isProjectileClipAllowed()
                && !chest.isProjectileClipAllowed(),
            "detached definitions retain the shared classification result");
        Result gatePlan = GameTickEventRestorationCollisionFootprintPlanner.plan(
            Operation.REGISTER,
            ConstructorState.of(10, 50, 50, 0, 0),
            gate, false, WorldBounds.of(1008, 4032));
        Result chestPlan = GameTickEventRestorationCollisionFootprintPlanner.plan(
            Operation.REGISTER,
            ConstructorState.of(11, 50, 50, 0, 0),
            chest, false, WorldBounds.of(1008, 4032));
        check(gatePlan.getContributions().get(0)
                    .getDynamicProjectileCount() == 1
                && chestPlan.getContributions().get(0)
                    .getDynamicProjectileCount() == 0,
            "planner consumes policy-derived projectile contributions");
    }

    private static void refusesMissingClassificationInputs() {
        expectNull(() -> LegacyObjectProjectileCollisionPolicy
            .allowsSceneryProjectileClip(null, 1, 1, ALLOWLIST));
        expectNull(() -> LegacyObjectProjectileCollisionPolicy
            .allowsSceneryProjectileClip("rock", 1, 1, null));
        expectNull(() -> LegacyObjectProjectileCollisionPolicy
            .allowsBoundaryProjectileClip("gate", new String[]{null}));
        expectNull(() -> Definition.scenery(1, 1, 1, null, ALLOWLIST));
    }

    private static boolean scenery(String name, int width, int height) {
        return LegacyObjectProjectileCollisionPolicy
            .allowsSceneryProjectileClip(name, width, height, ALLOWLIST);
    }

    private static void expectNull(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected fail-closed classification input.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
    }
}
'''


class LayeredMapsSliceOneHundredThirtyTwoTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-thirty-two-"
        )
        cls.classes = Path(cls.compile_temp.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.compile_temp.name) / (
            "src/com/openrsc/server/event/rsc/"
            "LegacyProjectileClassificationFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(COLLISION_FLAG), str(POLICY), str(STATE),
                str(REQUIREMENT), str(DECISION), str(ATOMIC_CONTRACT),
                str(REQUEST), str(REVALIDATION), str(COMMIT_REQUEST),
                str(INTENT), str(ROLLBACK),
                str(TRANSACTION), str(PLANNER), str(fixture),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        if result.returncode != 0:
            raise AssertionError(result.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_shared_policy_fixture_reproduces_legacy_table(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.event.rsc."
                "LegacyProjectileClassificationFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_world_projects_definitions_through_the_shared_policy_factory(self):
        world = WORLD.read_text(encoding="utf-8")
        self.assertNotIn("LegacyObjectProjectileCollisionPolicy", world)
        self.assertIn("Definition.scenery(", world)
        self.assertIn("Definition.boundary(", world)
        self.assertIn("Constants.objectsProjectileClipAllowed", world)
        self.assertNotIn("for (final String s :", world)
        self.assertNotIn('.contains("tree")', world)
        self.assertIn("planGameObjectCollision(", world)
        self.assertIn("applyGameObjectTransaction(", world)
        self.assertIn("public void registerGameObject", world)
        self.assertIn("public void unregisterGameObject", world)

    def test_policy_is_pure_and_planner_has_no_raw_public_boolean_factory(self):
        policy = POLICY.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.model", "import com.openrsc.server.constants",
            "World", "Region", "GameObject", "TileValue", "synchronized (",
            "registerGameObject", "unregisterGameObject", "getMutableTile",
        ):
            self.assertNotIn(forbidden, policy)
        planner = PLANNER.read_text(encoding="utf-8")
        self.assertIn(
            "LegacyObjectProjectileCollisionPolicy\n"
            "\t\t\t\t\t.allowsSceneryProjectileClip(", planner
        )
        name = "LegacyObjectProjectileCollisionPolicy"
        for path in (STORE, HANDLER, REGION_MANAGER):
            self.assertNotIn(name, path.read_text(encoding="utf-8"))

    def test_living_plan_records_slice_one_hundred_thirty_two(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 132: Shared projectile-clipping classification", plan
        )
        self.assertIn("one-by-one non-chests", plan)
        self.assertIn("behavior-identical", plan)


if __name__ == "__main__":
    unittest.main()
