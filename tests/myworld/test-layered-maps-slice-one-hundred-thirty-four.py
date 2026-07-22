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
APPLICATION = RSC / "GameTickEventRestorationCollisionApplicationContract.java"
REGION_DIR = ROOT / "server/src/com/openrsc/server/model/world/region"
TILE = REGION_DIR / "TileValue.java"
BOUNDARY = REGION_DIR / "RegionObjectCollisionMutationBoundary.java"
EXECUTOR = REGION_DIR / "RegionCollisionFootprintMutationExecutor.java"
REGION_MANAGER = REGION_DIR / "RegionManager.java"
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
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.model.world.region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionApplicationContract.Reason;
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

public final class RegionCollisionMutationExecutorFixture {
    private static final String[] ALLOWLIST = {"gate", "chair"};
    private static final WorldBounds BOUNDS = WorldBounds.of(1008, 4032);

    public static void main(String[] args) throws Exception {
        appliesExactMultiRegionRoundTrip();
        sameFootprintMutationsExcludeEachOther();
        underflowAndUnavailableTilesRefuseWithoutPartialMutation();
        reversedBoundaryCoverageRefusesBeforeMutation();
    }

    private static void appliesExactMultiRegionRoundTrip() {
        TileStore store = new TileStore();
        Result register = blockingPlan(Operation.REGISTER);
        RegionCollisionFootprintMutationExecutor.Result added =
            RegionCollisionFootprintMutationExecutor.execute(
                boundaries(register), register, store);
        check(added.isApplied() && added.getBoundaryCount() == 2
                && added.isMutationAuthorized()
                && added.isMutationPerformed()
                && !added.isRollbackPerformed(),
            "cross-Region register applies under both boundaries");
        for (CollisionContribution contribution : register.getContributions()) {
            TileValue tile = store.get(contribution.getX(), contribution.getY());
            check(tile.getBlockingSceneryCount()
                        == contribution.getBlockingSceneryCount()
                    && tile.getDynamicProjectileCount()
                        == contribution.getDynamicProjectileCount(),
                "register writes exact blocking and projectile contribution");
        }

        Result unregister = blockingPlan(Operation.UNREGISTER);
        RegionCollisionFootprintMutationExecutor.Result removed =
            RegionCollisionFootprintMutationExecutor.execute(
                boundaries(unregister), unregister, store);
        check(removed.isApplied() && removed.getBoundaryCount() == 2,
            "matching unregister applies under the same Region coverage");
        for (CollisionContribution contribution : unregister.getContributions()) {
            TileValue tile = store.get(contribution.getX(), contribution.getY());
            check(tile.getBlockingSceneryCount() == 0
                    && tile.getDynamicProjectileCount() == 0,
                "register/unregister round-trip restores exact zero counters");
        }
    }

    private static void sameFootprintMutationsExcludeEachOther()
            throws Exception {
        Result footprint = blockingPlan(Operation.REGISTER);
        List<RegionObjectCollisionMutationBoundary> boundaries =
            boundaries(footprint);
        TileStore store = new TileStore();
        CountDownLatch firstAccess = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondAccess = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread first = new Thread(() -> {
            try {
                RegionCollisionFootprintMutationExecutor.execute(
                    boundaries, footprint, (x, y) -> {
                        firstAccess.countDown();
                        await(releaseFirst, "release first mutation");
                        return store.getMutableTile(x, y);
                    });
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        }, "collision-mutation-first");
        Thread second = new Thread(() -> {
            try {
                await(firstAccess, "first mutation entered");
                RegionCollisionFootprintMutationExecutor.execute(
                    boundaries, footprint, (x, y) -> {
                        secondAccess.countDown();
                        return store.getMutableTile(x, y);
                    });
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        }, "collision-mutation-second");

        first.start();
        second.start();
        await(firstAccess, "first access observed");
        check(!secondAccess.await(150L, TimeUnit.MILLISECONDS),
            "same-footprint mutation cannot cross held Region boundaries");
        releaseFirst.countDown();
        join(first);
        join(second);
        check(failure.get() == null && secondAccess.getCount() == 0L,
            "second mutation enters only after the first releases");
    }

    private static void underflowAndUnavailableTilesRefuseWithoutPartialMutation() {
        Result unregister = blockingPlan(Operation.UNREGISTER);
        TileStore store = new TileStore();
        RegionCollisionFootprintMutationExecutor.Result underflow =
            RegionCollisionFootprintMutationExecutor.execute(
                boundaries(unregister), unregister, store);
        check(underflow.isRefused()
                && underflow.getReason()
                    == RegionCollisionFootprintMutationExecutor.Reason
                        .APPLICATION_PRECONDITION_REFUSED
                && underflow.getEvaluation().getReason()
                    == Reason.COUNTER_UNDERFLOW
                && !underflow.isMutationAuthorized()
                && !underflow.isMutationPerformed(),
            "underflow refuses before any contribution is removed");
        for (CollisionContribution contribution : unregister.getContributions()) {
            check(store.get(contribution.getX(), contribution.getY())
                    .getBlockingSceneryCount() == 0,
                "underflow refusal leaves every tile unchanged");
        }

        Result register = blockingPlan(Operation.REGISTER);
        CollisionContribution missing = register.getContributions().get(1);
        RegionCollisionFootprintMutationExecutor.Result unavailable =
            RegionCollisionFootprintMutationExecutor.execute(
                boundaries(register), register,
                (x, y) -> x == missing.getX() && y == missing.getY()
                    ? null : store.getMutableTile(x, y));
        check(unavailable.isRefused()
                && unavailable.getReason()
                    == RegionCollisionFootprintMutationExecutor.Reason
                        .REQUIRED_TILE_UNAVAILABLE,
            "missing tile refuses before arithmetic or mutation");
    }

    private static void reversedBoundaryCoverageRefusesBeforeMutation() {
        Result footprint = blockingPlan(Operation.REGISTER);
        List<RegionObjectCollisionMutationBoundary> reversed =
            boundaries(footprint);
        Collections.reverse(reversed);
        TileStore store = new TileStore();
        expectIllegal(() -> RegionCollisionFootprintMutationExecutor.execute(
            reversed, footprint, store));
        check(store.size() == 0,
            "reversed boundary coverage refuses before tile access");
    }

    private static Result blockingPlan(Operation operation) {
        return GameTickEventRestorationCollisionFootprintPlanner.plan(
            operation, ConstructorState.of(40, 47, 10, 0, 0),
            Definition.scenery(1, 2, 1, "tree", ALLOWLIST),
            false, BOUNDS);
    }

    private static List<RegionObjectCollisionMutationBoundary> boundaries(
            Result footprint) {
        List<RegionObjectCollisionMutationBoundary> boundaries =
            new ArrayList<>();
        for (PackedRegionCoordinate region : footprint.getRequiredRegions()) {
            boundaries.add(new RegionObjectCollisionMutationBoundary(
                region.getRegionX(), region.getRegionY()));
        }
        return boundaries;
    }

    private static void await(CountDownLatch latch, String label) {
        try {
            check(latch.await(2L, TimeUnit.SECONDS), label);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(label, interrupted);
        }
    }

    private static void join(Thread thread) throws Exception {
        thread.join(2000L);
        check(!thread.isAlive(), thread.getName() + " completed");
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected canonical coverage refusal.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
    }

    private static final class TileStore implements
            RegionCollisionFootprintMutationExecutor.MutableTileAccess {
        private final Map<Long, TileValue> tiles = new HashMap<>();

        @Override
        public synchronized TileValue getMutableTile(int x, int y) {
            long key = ((long) x << 32) ^ (y & 0xffffffffL);
            TileValue tile = tiles.get(key);
            if (tile == null) {
                tile = new TileValue();
                tiles.put(key, tile);
            }
            return tile;
        }

        private TileValue get(int x, int y) {
            return getMutableTile(x, y);
        }

        private synchronized int size() { return tiles.size(); }
    }
}
'''


class LayeredMapsSliceOneHundredThirtyFourTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-thirty-four-"
        )
        cls.classes = Path(cls.compile_temp.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.compile_temp.name) / (
            "src/com/openrsc/server/model/world/region/"
            "RegionCollisionMutationExecutorFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(COLLISION_FLAG), str(POLICY), str(STATE),
                str(REQUIREMENT), str(DECISION), str(ATOMIC_CONTRACT),
                str(REQUEST), str(REVALIDATION), str(INTENT), str(ROLLBACK),
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

    def test_executor_fixture_proves_atomic_collision_mutation_boundary(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.region."
                "RegionCollisionMutationExecutorFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
            timeout=15,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_region_manager_exposes_only_a_package_local_disconnected_seam(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        self.assertIn(
            "applyCollisionFootprintUnderExistingOrderedBoundaries", manager
        )
        self.assertIn("peekRegionFromSectorCoordinates(", manager)
        self.assertIn("synchronized (layeredRegionLifecycleLock)", manager)
        self.assertIn("RegionCollisionFootprintMutationExecutor.execute(", manager)
        name = "RegionCollisionFootprintMutationExecutor"
        self.assertNotIn(name, WORLD.read_text(encoding="utf-8"))
        self.assertNotIn(name, STORE.read_text(encoding="utf-8"))
        self.assertNotIn(name, HANDLER.read_text(encoding="utf-8"))

    def test_executor_is_narrow_and_does_not_mutate_object_or_callback_state(self):
        source = EXECUTOR.read_text(encoding="utf-8")
        for forbidden in (
            "GameObject", "registerGameObject", "unregisterGameObject",
            "replaceGameObject", "GameEventHandler", "sendUpdatePackets",
            "getWorld()", "getRegions()", "addPlayer", "removePlayer",
        ):
            self.assertNotIn(forbidden, source)
        for required in (
            "RegionObjectCollisionMutationBoundary.executeMutation(",
            "GameTickEventRestorationCollisionApplicationContract.evaluate(",
            "matchesProjectedState", "applyContribution(opposite(",
            "isMutationPerformed() { return !isRefused(); }",
        ):
            self.assertIn(required, source)

    def test_living_plan_records_slice_one_hundred_thirty_four(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 134: Disconnected ordered collision executor", plan
        )
        self.assertIn("add/remove round-trip", plan)
        self.assertIn("underflow refusal", plan)


if __name__ == "__main__":
    unittest.main()
