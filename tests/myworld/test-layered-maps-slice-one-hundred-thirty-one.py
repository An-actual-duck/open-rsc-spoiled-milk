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
PROJECTILE_POLICY = ROOT / (
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

import java.util.List;

import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionFootprintPlanner.ConstructorState;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionFootprintPlanner.Definition;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionFootprintPlanner.Operation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionFootprintPlanner.Reason;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionFootprintPlanner.Result;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionFootprintPlanner.WorldBounds;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTransientRollbackSnapshot.CollisionContribution;

public final class RestorationCollisionFootprintFixture {
    private static final WorldBounds BOUNDS = WorldBounds.of(1008, 4032);
    private static final String[] ALLOWLIST = {"gate", "chair"};

    public static void main(String[] args) {
        fullBlockingSceneryRotatesAndAggregatesForce();
        directionalSceneryAggregatesOverlap();
        boundaryPreservesLegacyCollisionAndProjectileAxes();
        specialObjectPreservesRegisterUnregisterAsymmetry();
        refusesUnavailableMismatchedAndOutOfWorldEffects();
        keepsEmptyAndPopulatedResultsImmutableAndInert();
    }

    private static void fullBlockingSceneryRotatesAndAggregatesForce() {
        Result result = plan(
            Operation.REGISTER, 5, 47, 10, 0, 0,
            Definition.scenery(1, 2, 1, "tree", ALLOWLIST), true);
        check(result.isFootprintAvailable()
                && result.getContributionTileCount() == 2
                && result.getRequiredRegionCount() == 2,
            "two-tile scenery crosses a packed Region boundary");
        CollisionContribution first = result.getContributions().get(0);
        CollisionContribution second = result.getContributions().get(1);
        check(first.getX() == 47 && first.getY() == 10
                && first.getBlockingSceneryCount() == 2
                && first.getDynamicCollisionMask() == 0
                && first.getDynamicProjectileCount() == 1,
            "force-full-block adds a second anchor blocking contribution");
        check(second.getX() == 48 && second.getY() == 10
                && second.getBlockingSceneryCount() == 1
                && second.getDynamicProjectileCount() == 1,
            "rotated footprint preserves the other full-blocking tile");
        check(result.getRequiredRegions().get(0).getRegionX() == 0
                && result.getRequiredRegions().get(1).getRegionX() == 1,
            "required Regions are unique and canonical");
    }

    private static void directionalSceneryAggregatesOverlap() {
        Result result = plan(
            Operation.UNREGISTER, 6, 47, 10, 2, 0,
            Definition.scenery(2, 2, 1, "gate", ALLOWLIST), false);
        check(result.getContributionTileCount() == 3,
            "rotated directional scenery has three unique affected tiles");
        CollisionContribution first = result.getContributions().get(0);
        CollisionContribution middle = result.getContributions().get(1);
        CollisionContribution last = result.getContributions().get(2);
        check(first.getX() == 47 && first.getY() == 10
                && first.getDynamicCollisionMask() == 4
                && first.getDynamicProjectileCount() == 1,
            "first rotated tile contributes south collision");
        check(middle.getX() == 47 && middle.getY() == 11
                && middle.getDynamicCollisionMask() == 5
                && middle.getDynamicProjectileCount() == 2,
            "overlapping neighbor retains both flags and projectile counts");
        check(last.getX() == 47 && last.getY() == 12
                && last.getDynamicCollisionMask() == 1
                && last.getDynamicProjectileCount() == 1,
            "last neighbor contributes north collision");
    }

    private static void boundaryPreservesLegacyCollisionAndProjectileAxes() {
        Result result = plan(
            Operation.REGISTER, 7, 48, 10, 0, 1,
            Definition.boundary(1, "gate", ALLOWLIST), false);
        check(result.getContributionTileCount() == 3,
            "boundary direction zero affects three unique tiles");
        List<CollisionContribution> contributions = result.getContributions();
        check(contributions.get(0).getX() == 48
                && contributions.get(0).getY() == 9
                && contributions.get(0).getDynamicCollisionMask() == 4,
            "boundary collision neighbor uses the legacy south tile");
        check(contributions.get(1).getX() == 47
                && contributions.get(1).getY() == 10
                && contributions.get(1).getDynamicProjectileCount() == 1,
            "boundary projectile neighbor preserves legacy west axis");
        check(contributions.get(2).getX() == 48
                && contributions.get(2).getY() == 10
                && contributions.get(2).getDynamicCollisionMask() == 1
                && contributions.get(2).getDynamicProjectileCount() == 1,
            "boundary anchor aggregates collision and projectile state");
    }

    private static void specialObjectPreservesRegisterUnregisterAsymmetry() {
        Result registered = plan(
            Operation.REGISTER, 1147, 100, 100, 0, 0, null, false);
        check(registered.isFootprintAvailable()
                && registered.getContributionTileCount() == 0
                && registered.getRequiredRegionCount() == 1,
            "special register returns before definition or collision access");
        Result forced = plan(
            Operation.REGISTER, 1147, 100, 100, 0, 0, null, true);
        check(forced.getContributionTileCount() == 1
                && forced.getContributions().get(0)
                    .getBlockingSceneryCount() == 1,
            "delayed force-full-block remains after the special early return");
        Result unregistered = plan(
            Operation.UNREGISTER, 1147, 100, 100, 0, 0,
            Definition.scenery(1, 1, 1, "chest", ALLOWLIST), false);
        check(unregistered.getContributionTileCount() == 1
                && unregistered.getContributions().get(0)
                    .getBlockingSceneryCount() == 1
                && unregistered.isLegacySaturatingUnregister(),
            "legacy unregister does not contain the 1147 early return");
    }

    private static void refusesUnavailableMismatchedAndOutOfWorldEffects() {
        check(plan(Operation.REGISTER, 8, 10, 10, 0, 0, null, false)
                .getReason() == Reason.DEFINITION_UNAVAILABLE,
            "ordinary missing definition refuses");
        check(plan(
                Operation.REGISTER, 8, 10, 10, 0, 0,
                Definition.boundary(1, "door", ALLOWLIST), false).getReason()
                    == Reason.DEFINITION_KIND_MISMATCH,
            "constructor and definition kind mismatch refuses");
        check(plan(
                Operation.REGISTER, 8, 0, 10, 0, 0,
                Definition.scenery(2, 1, 1, "chest", ALLOWLIST), false).getReason()
                    == Reason.OUT_OF_WORLD_EFFECT,
            "directional neighbor outside the world refuses");
        check(plan(
                Operation.REGISTER, 8, 10, 10, 0, 0,
                Definition.scenery(1, 65, 64, "statue", ALLOWLIST), false).getReason()
                    == Reason.CONTRIBUTION_TILE_LIMIT_EXCEEDED,
            "oversized contribution refuses before expansion");
        check(plan(
                Operation.UNREGISTER, 8, 10, 10, 0, 0,
                Definition.scenery(1, 1, 1, "chest", ALLOWLIST), true).getReason()
                    == Reason.FORCE_FULL_BLOCK_REQUIRES_REGISTER_OPERATION,
            "force-full-block cannot be invented for unregister");
    }

    private static void keepsEmptyAndPopulatedResultsImmutableAndInert() {
        Result empty = plan(
            Operation.REGISTER, 9, 20, 20, 0, 0,
            Definition.scenery(0, 1, 1, "tree", ALLOWLIST), false);
        check(empty.isFootprintAvailable()
                && empty.getContributionTileCount() == 0
                && empty.getRequiredRegionCount() == 1,
            "non-colliding definition remains explicit and anchor-qualified");
        Result result = plan(
            Operation.REGISTER, 10, 20, 20, 0, 0,
            Definition.scenery(1, 1, 1, "chest", ALLOWLIST), false);
        expectUnsupported(() -> result.getContributions().clear());
        expectUnsupported(() -> result.getRequiredRegions().clear());
        check(!result.isRuntimeObservationPerformed()
                && !result.isRuntimeBoundaryAcquired()
                && !result.isMutationAuthorized()
                && !result.isMutationPerformed()
                && !result.isRollbackAuthorized()
                && !result.isRollbackPerformed()
                && !result.isExecutableRestoration()
                && !result.isCommitToken()
                && !result.isArrivalGate()
                && !result.isLifecycleAuthority(),
            "available footprint grants no runtime authority");
    }

    private static Result plan(
            Operation operation, int id, int x, int y, int direction, int type,
            Definition definition, boolean forceFullBlock) {
        return GameTickEventRestorationCollisionFootprintPlanner.plan(
            operation, ConstructorState.of(id, x, y, direction, type),
            definition, forceFullBlock, BOUNDS);
    }

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected immutable list.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
    }
}
'''


class LayeredMapsSliceOneHundredThirtyOneTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-thirty-one-"
        )
        cls.classes = Path(cls.compile_temp.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.compile_temp.name) / (
            "src/com/openrsc/server/event/rsc/"
            "RestorationCollisionFootprintFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(COLLISION_FLAG), str(PROJECTILE_POLICY),
                str(STATE), str(REQUIREMENT),
                str(DECISION), str(ATOMIC_CONTRACT), str(REQUEST),
                str(REVALIDATION), str(INTENT), str(ROLLBACK),
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

    def test_planner_fixture_proves_legacy_collision_tables(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.event.rsc."
                "RestorationCollisionFootprintFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_planner_has_no_runtime_observation_or_mutation_capability(self):
        source = PLANNER.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.model", "World world", "Region region",
            "GameObject object", "TileValue tile", "synchronized (",
            "registerGameObject", "unregisterGameObject", "getMutableTile",
            "addDynamicCollision", "removeDynamicCollision",
            "addDynamicProjectileBlock", "removeDynamicProjectileBlock",
        ):
            self.assertNotIn(forbidden, source)
        for required in (
            "SPECIAL_COLLISIONLESS_REGISTER_OBJECT_ID = 1147",
            "Math.addExact", "MAXIMUM_COLLISION_CONTRIBUTION_TILES",
            "PackedRegionCoordinate.fromTile",
            "isRuntimeBoundaryAcquired() { return false; }",
            "isMutationAuthorized() { return false; }",
        ):
            self.assertIn(required, source)

    def test_planner_connects_only_through_the_slice_135_runtime_seam(self):
        name = "GameTickEventRestorationCollisionFootprintPlanner"
        for path in (STORE, HANDLER):
            self.assertNotIn(name, path.read_text(encoding="utf-8"))
        world = WORLD.read_text(encoding="utf-8")
        self.assertIn(name, world)
        self.assertIn("planGameObjectCollision", world)
        self.assertIn("applyGameObjectTransaction", world)
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        self.assertIn(name, manager)
        self.assertIn(
            "applyCollisionFootprintUnderExistingOrderedBoundaries", manager
        )

    def test_living_plan_records_slice_one_hundred_thirty_one(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 131: Exact collision-footprint planner", plan
        )
        self.assertIn("object ID 1147", plan)
        self.assertIn("force-full-block", plan)


if __name__ == "__main__":
    unittest.main()
