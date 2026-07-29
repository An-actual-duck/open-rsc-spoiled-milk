#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RSC = ROOT / "server/src/com/openrsc/server/event/rsc"
REGION = ROOT / "server/src/com/openrsc/server/model/world/region"
COLLISION_FLAG = ROOT / "server/src/com/openrsc/server/util/rsc/CollisionFlag.java"
POLICY = ROOT / (
    "server/src/com/openrsc/server/util/rsc/"
    "LegacyObjectProjectileCollisionPolicy.java"
)
STATE = RSC / "GameTickEventRestorationState.java"
REQUIREMENT = RSC / "GameTickEventRestorationRequirement.java"
DECISION = RSC / "GameTickEventRestorationTargetDecision.java"
COMMIT_REQUEST = RSC / "GameTickEventRestorationCommitRequest.java"
ATOMIC = RSC / "GameTickEventRestorationAtomicRevalidationContract.java"
REQUEST = RSC / "GameTickEventRestorationTargetRevalidationRequest.java"
REVALIDATION = RSC / "GameTickEventRestorationTargetRevalidation.java"
INTENT = RSC / "GameTickEventRestorationMutationIntent.java"
ROLLBACK = RSC / "GameTickEventRestorationTransientRollbackSnapshot.java"
TRANSACTION = RSC / "GameTickEventRestorationCollisionTransactionContract.java"
PLANNER = RSC / "GameTickEventRestorationCollisionFootprintPlanner.java"
APPLICATION = RSC / "GameTickEventRestorationCollisionApplicationContract.java"
TILE = REGION / "TileValue.java"
BOUNDARY = REGION / "RegionObjectCollisionMutationBoundary.java"
EXECUTOR = REGION / "RegionCollisionFootprintMutationExecutor.java"
WORLD = ROOT / "server/src/com/openrsc/server/model/world/World.java"
REGION_MANAGER = REGION / "RegionManager.java"
STORE = RSC / "handler/GameTickEventStore.java"
HANDLER = RSC / "handler/GameEventHandler.java"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.model.world.region;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionFootprintPlanner;
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
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionTransactionContract.PackedRegionCoordinate;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTransientRollbackSnapshot.CollisionContribution;

public final class WorldCollisionRuntimeFixture {
    private static final String[] ALLOWLIST = {"gate", "chair"};
    private static final WorldBounds BOUNDS = WorldBounds.of(1008, 4032);

    public static void main(String[] args) {
        coversSceneryTypesAndRotations();
        coversBoundaryDirectionsAndProjectileOverlap();
        coversMultiRegionReplacementAndRepeatedCycles();
        preservesSpecialObjectSaturatingUnregister();
    }

    private static void coversSceneryTypesAndRotations() {
        for (int collisionType = 0; collisionType <= 2; collisionType++) {
            for (int direction : new int[]{0, 2, 4, 6}) {
                Definition definition = Definition.scenery(
                    collisionType, 2, 3,
                    collisionType == 1 ? "tree" : "gate", ALLOWLIST);
                roundTrip(100 + collisionType, 60, 60, direction, 0,
                    definition, new TileStore());
            }
        }
        Result zeroSized = plan(
            Operation.REGISTER, 180, 60, 60, 0, 0,
            Definition.scenery(1, 0, 0, "Watch tower", ALLOWLIST));
        check(zeroSized.isFootprintAvailable()
                && zeroSized.getContributionTileCount() == 0,
            "legacy zero-sized definitions remain collisionless");
        roundTrip(769, 60, 60, 8, 0,
            Definition.scenery(1, 2, 3, "Travel Cart", ALLOWLIST),
            new TileStore());
    }

    private static void coversBoundaryDirectionsAndProjectileOverlap() {
        Definition boundary = Definition.boundary(1, "gate", ALLOWLIST);
        for (int direction = 0; direction <= 3; direction++) {
            roundTrip(200 + direction, 60, 60, direction, 1,
                boundary, new TileStore());
        }

        Definition directional = Definition.scenery(
            2, 2, 1, "gate", ALLOWLIST);
        Result register = plan(
            Operation.REGISTER, 210, 60, 60, 0, 0, directional);
        TileStore store = new TileStore();
        applied(register, store);
        int maximumProjectileCount = 0;
        for (CollisionContribution contribution : register.getContributions()) {
            maximumProjectileCount = Math.max(
                maximumProjectileCount,
                store.get(contribution.getX(), contribution.getY())
                    .getDynamicProjectileCount());
        }
        check(maximumProjectileCount == 2,
            "overlapping projectile contributions retain exact counts");
        applied(plan(Operation.UNREGISTER, 210, 60, 60, 0, 0,
            directional), store);
        check(store.isZero(), "projectile overlap unregister returns to zero");
    }

    private static void coversMultiRegionReplacementAndRepeatedCycles() {
        Definition blocking = Definition.scenery(
            1, 2, 2, "tree", ALLOWLIST);
        Result crossRegion = plan(
            Operation.REGISTER, 300, 47, 47, 0, 0, blocking);
        check(crossRegion.getRequiredRegionCount() == 4,
            "two-by-two corner footprint spans four packed Regions");
        TileStore store = new TileStore();
        for (int cycle = 0; cycle < 5; cycle++) {
            applied(crossRegion, store);
            applied(plan(Operation.UNREGISTER, 300, 47, 47, 0, 0,
                blocking), store);
            check(store.isZero(), "repeated cycle returns every counter to zero");
        }

        Definition directional = Definition.scenery(
            2, 1, 2, "gate", ALLOWLIST);
        applied(crossRegion, store);
        applied(plan(Operation.UNREGISTER, 300, 47, 47, 0, 0,
            blocking), store);
        Result replacement = plan(
            Operation.REGISTER, 301, 47, 47, 2, 0, directional);
        applied(replacement, store);
        applied(plan(Operation.UNREGISTER, 301, 47, 47, 2, 0,
            directional), store);
        check(store.isZero(),
            "unregister-then-register replacement sequence remains reversible");
    }

    private static void preservesSpecialObjectSaturatingUnregister() {
        TileStore store = new TileStore();
        Result register = GameTickEventRestorationCollisionFootprintPlanner.plan(
            Operation.REGISTER, ConstructorState.of(1147, 70, 70, 0, 0),
            null, false, BOUNDS);
        applied(register, store);
        Result unregister = plan(Operation.UNREGISTER, 1147, 70, 70, 0, 0,
            Definition.scenery(1, 1, 1, "Spellcharge", ALLOWLIST));
        check(unregister.isLegacySaturatingUnregister(),
            "special unregister declares the legacy saturating rule");
        applied(unregister, store);
        check(store.isZero(),
            "special collisionless register/unregister does not fail underflow");
    }

    private static void roundTrip(
            int id, int x, int y, int direction, int type,
            Definition definition, TileStore store) {
        applied(plan(Operation.REGISTER, id, x, y, direction, type,
            definition), store);
        applied(plan(Operation.UNREGISTER, id, x, y, direction, type,
            definition), store);
        check(store.isZero(), "collision round-trip returns to zero");
    }

    private static Result plan(
            Operation operation, int id, int x, int y, int direction,
            int type, Definition definition) {
        return GameTickEventRestorationCollisionFootprintPlanner.plan(
            operation, ConstructorState.of(id, x, y, direction, type),
            definition, false, BOUNDS);
    }

    private static void applied(Result footprint, TileStore store) {
        RegionCollisionFootprintMutationExecutor.Result result =
            RegionCollisionFootprintMutationExecutor.execute(
                boundaries(footprint), footprint, store);
        check(result.isApplied(), "planned collision mutation applies");
    }

    private static List<RegionObjectCollisionMutationBoundary> boundaries(
            Result footprint) {
        List<RegionObjectCollisionMutationBoundary> result = new ArrayList<>();
        for (PackedRegionCoordinate coordinate : footprint.getRequiredRegions()) {
            result.add(new RegionObjectCollisionMutationBoundary(
                coordinate.getRegionX(), coordinate.getRegionY()));
        }
        return result;
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
    }

    private static final class TileStore implements
            RegionCollisionFootprintMutationExecutor.MutableTileAccess {
        private final Map<Long, TileValue> tiles = new HashMap<>();

        @Override
        public TileValue getMutableTile(int x, int y) {
            long key = ((long) x << 32) ^ (y & 0xffffffffL);
            TileValue value = tiles.get(key);
            if (value == null) {
                value = new TileValue();
                tiles.put(key, value);
            }
            return value;
        }

        private TileValue get(int x, int y) {
            return getMutableTile(x, y);
        }

        private boolean isZero() {
            for (TileValue tile : tiles.values()) {
                if (tile.getBlockingSceneryCount() != 0
                        || tile.getDynamicProjectileCount() != 0) {
                    return false;
                }
                for (int count : tile.getDynamicCollisionCounts()) {
                    if (count != 0) { return false; }
                }
            }
            return true;
        }
    }
}
'''


class LayeredMapsSliceOneHundredThirtyFiveTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-thirty-five-"
        )
        cls.classes = Path(cls.compile_temp.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.compile_temp.name) / (
            "src/com/openrsc/server/model/world/region/"
            "WorldCollisionRuntimeFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(COLLISION_FLAG), str(POLICY), str(STATE),
                str(REQUIREMENT), str(DECISION), str(ATOMIC), str(REQUEST),
                str(REVALIDATION), str(COMMIT_REQUEST), str(INTENT), str(ROLLBACK),
                str(TRANSACTION), str(PLANNER), str(APPLICATION), str(TILE),
                str(BOUNDARY), str(EXECUTOR), str(fixture),
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

    def test_runtime_collision_table_fixture(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.region."
                "WorldCollisionRuntimeFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
            timeout=15,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_world_collision_adoption_is_superseded_by_composed_transaction(self):
        world = WORLD.read_text(encoding="utf-8")
        start = world.index("public void registerGameObject(final GameObject o)")
        end = world.index("public void registerItem(final GroundItem i)", start)
        registration = world[start:end]
        self.assertIn("Operation.REGISTER", registration)
        self.assertIn("Operation.UNREGISTER", world)
        self.assertIn(
            "applyObjectMembershipAndCollisionTransaction", registration
        )
        self.assertNotIn("o.setLocation(", registration)
        self.assertNotIn("o.remove()", world)
        for direct_mutation in (
            "addBlockingScenery", "removeBlockingScenery",
            "addDynamicCollision", "removeDynamicCollision",
            "addDynamicProjectileBlock", "removeDynamicProjectileBlock",
        ):
            self.assertNotIn(direct_mutation, registration)
        delayed = world[world.index("public void delayedSpawnObject("):start]
        self.assertIn(
            "registerGameObject(new GameObject(getWorld(), loc), "
            "forceFullBlock)", delayed
        )
        self.assertNotIn("addBlockingScenery", delayed)

    def test_runtime_seam_is_narrow_and_scheduler_remains_disconnected(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        self.assertIn(
            "public void applyCollisionFootprintUnderOrderedBoundaries(",
            manager,
        )
        self.assertIn(
            "public void applyObjectMembershipAndCollisionTransaction(",
            manager,
        )
        self.assertIn("applyCollisionFootprintUnderOrderedBoundaries(footprint, true)", manager)
        self.assertIn("getRegionFromSectorCoordinates(", manager)
        name = "RegionCollisionFootprintMutationExecutor"
        self.assertNotIn(name, WORLD.read_text(encoding="utf-8"))
        self.assertNotIn(name, STORE.read_text(encoding="utf-8"))
        self.assertNotIn(name, HANDLER.read_text(encoding="utf-8"))

    def test_living_plan_records_slice_one_hundred_thirty_five(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 135: Ordered runtime collision-counter adoption", plan
        )
        self.assertIn("object ID 1147", plan)
        self.assertIn("owner route", plan)


if __name__ == "__main__":
    unittest.main()
