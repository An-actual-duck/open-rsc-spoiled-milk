#!/usr/bin/env python3
import os
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
STORE = SERVER / (
    "src/com/openrsc/server/event/rsc/handler/GameTickEventStore.java"
)
HANDLER = SERVER / (
    "src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.model.world.region;

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
import com.openrsc.server.model.Point;
import com.openrsc.server.model.entity.GameObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class RegionObjectCollisionTransactionFixture {
    private static final String[] ALLOWLIST = {"gate", "chair"};
    private static final WorldBounds BOUNDS = WorldBounds.of(1008, 4032);
    private static final Definition BLOCKING = Definition.scenery(
        1, 1, 1, "Rock", ALLOWLIST);

    public static void main(String[] args) throws Exception {
        registerAndUnregisterAreComposed();
        replacementAcrossRegionsIsComposed();
        refusedNewCollisionRestoresOldMembershipAndCounters();
        occupiedSlotRefusesWithoutMutation();
        sameRegionTransactionsExcludeEachOther();
    }

    private static void registerAndUnregisterAreComposed() {
        FixtureWorld world = new FixtureWorld();
        GameObject object = object(60, 60, 300);
        Prepared register = Prepared.register(world, object, BLOCKING);
        RegionObjectCollisionTransactionExecutor.Result registered =
            register.execute();
        check(registered.isApplied()
                && registered.isMembershipRegistered()
                && !registered.isMembershipRemoved()
                && registered.getBoundaryCount() == 1,
            "registration applies membership and collision together");
        check(world.regionAt(60, 60).getGameObjects().contains(object)
                && world.tiles.get(60, 60).getBlockingSceneryCount() == 1
                && object.getRegion() == world.regionAt(60, 60)
                && !object.isRemoved(),
            "registered object and collision post-state agree");

        Prepared unregister = Prepared.unregister(world, object, BLOCKING);
        RegionObjectCollisionTransactionExecutor.Result removed =
            unregister.execute();
        check(removed.isApplied()
                && removed.isMembershipRemoved()
                && !removed.isMembershipRegistered(),
            "unregistration applies membership and collision together");
        check(!world.regionAt(60, 60).getGameObjects().contains(object)
                && world.tiles.get(60, 60).getBlockingSceneryCount() == 0
                && object.isRemoved(),
            "unregistered object and collision post-state agree");
        check(world.invalidations.get() == 2,
            "each committed membership change invalidates its anchor cache");
    }

    private static void replacementAcrossRegionsIsComposed() {
        FixtureWorld world = new FixtureWorld();
        GameObject oldObject = object(47, 47, 301);
        Definition wide = Definition.scenery(
            1, 2, 2, "Rock", ALLOWLIST);
        Prepared.register(world, oldObject, wide).execute();
        check(world.tiles.nonZeroTileCount() == 4,
            "old cross-Region footprint is registered");

        GameObject newObject = object(96, 60, 302);
        Prepared replacement = Prepared.replace(
            world, oldObject, wide, newObject, BLOCKING);
        RegionObjectCollisionTransactionExecutor.Result result =
            replacement.execute();
        check(result.isApplied()
                && result.isMembershipRemoved()
                && result.isMembershipRegistered()
                && result.getBoundaryCount() == 5,
            "replacement holds the canonical union of old and new Regions");
        check(oldObject.isRemoved()
                && !world.regionAt(47, 47).getGameObjects().contains(oldObject)
                && !newObject.isRemoved()
                && world.regionAt(96, 60).getGameObjects().contains(newObject)
                && world.tiles.get(96, 60).getBlockingSceneryCount() == 1
                && world.tiles.nonZeroTileCount() == 1,
            "replacement leaves only the new membership and contribution");
    }

    private static void refusedNewCollisionRestoresOldMembershipAndCounters() {
        FixtureWorld world = new FixtureWorld();
        GameObject oldObject = object(60, 60, 303);
        Prepared.register(world, oldObject, BLOCKING).execute();
        GameObject newObject = object(100, 60, 304);
        world.tiles.put(100, 60, new TileValue(
            (byte) 0, (short) 0, (byte) 0, (byte) 0, (byte) 0,
            (byte) 0, false, false, false, Integer.MAX_VALUE, 0,
            new int[6], false, 0, 0));

        RegionObjectCollisionTransactionExecutor.Result result =
            Prepared.replace(
                world, oldObject, BLOCKING, newObject, BLOCKING).execute();
        check(result.isRefused()
                && result.getReason()
                    == RegionObjectCollisionTransactionExecutor.Reason
                        .NEW_COLLISION_REFUSED,
            "new collision overflow refuses the replacement");
        check(!oldObject.isRemoved()
                && world.regionAt(60, 60).getGameObjects().contains(oldObject)
                && world.tiles.get(60, 60).getBlockingSceneryCount() == 1,
            "refusal restores the old membership and collision exactly");
        check(newObject.getLocation() == null
                && newObject.getRegion() == null
                && !newObject.isRemoved()
                && !world.regionAt(100, 60).getGameObjects().contains(newObject)
                && world.tiles.get(100, 60).getBlockingSceneryCount()
                    == Integer.MAX_VALUE,
            "refusal restores the new object to its detached pre-state");
    }

    private static void occupiedSlotRefusesWithoutMutation() {
        FixtureWorld world = new FixtureWorld();
        GameObject first = object(70, 70, 305);
        Prepared.register(world, first, BLOCKING).execute();
        GameObject second = object(70, 70, 306);
        RegionObjectCollisionTransactionExecutor.Result result =
            Prepared.register(world, second, BLOCKING).execute();
        check(result.isRefused()
                && result.getReason()
                    == RegionObjectCollisionTransactionExecutor.Reason
                        .MEMBERSHIP_PRECONDITION_REFUSED,
            "unacknowledged occupied slot refuses");
        check(world.regionAt(70, 70).getGameObjects().size() == 1
                && world.regionAt(70, 70).getGameObjects().contains(first)
                && world.tiles.get(70, 70).getBlockingSceneryCount() == 1
                && second.getLocation() == null,
            "occupied-slot refusal changes neither membership nor collision");
    }

    private static void sameRegionTransactionsExcludeEachOther()
            throws Exception {
        FixtureWorld world = new FixtureWorld();
        GameObject first = object(80, 80, 307);
        GameObject second = object(82, 80, 308);
        Prepared firstPrepared = Prepared.register(world, first, BLOCKING);
        Prepared secondPrepared = Prepared.register(world, second, BLOCKING);
        CountDownLatch firstTileRead = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondComplete = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        firstPrepared.tileAccess = new BlockingTileAccess(
            world.tiles, firstTileRead, releaseFirst);

        Thread firstThread = new Thread(() -> {
            try { firstPrepared.execute(); }
            catch (Throwable error) { failure.compareAndSet(null, error); }
        }, "object-transaction-first");
        Thread secondThread = new Thread(() -> {
            try {
                await(firstTileRead, "first transaction reached tile state");
                secondPrepared.execute();
                secondComplete.countDown();
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        }, "object-transaction-second");
        firstThread.start();
        secondThread.start();
        await(firstTileRead, "first transaction entered");
        check(!secondComplete.await(150L, TimeUnit.MILLISECONDS),
            "second transaction cannot observe a split same-Region state");
        releaseFirst.countDown();
        firstThread.join(2000L);
        secondThread.join(2000L);
        check(!firstThread.isAlive() && !secondThread.isAlive()
                && failure.get() == null
                && secondComplete.getCount() == 0L
                && world.regionAt(80, 80).getGameObjects().contains(first)
                && world.regionAt(82, 80).getGameObjects().contains(second),
            "both transactions commit serially after boundary release");
    }

    private static GameObject object(int x, int y, int id) {
        return new GameObject(null, Point.location(x, y), id, 0, 0);
    }

    private static Result plan(
            Operation operation, GameObject object, Definition definition) {
        int x = object.getLocation() == null
            ? object.getLoc().getX() : object.getX();
        int y = object.getLocation() == null
            ? object.getLoc().getY() : object.getY();
        return GameTickEventRestorationCollisionFootprintPlanner.plan(
            operation,
            ConstructorState.of(
                object.getID(), x, y, object.getDirection(), object.getType()),
            definition, false, BOUNDS);
    }

    private static void await(CountDownLatch latch, String label) {
        try {
            check(latch.await(2L, TimeUnit.SECONDS), label);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(label, interrupted);
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
    }

    private static final class Prepared {
        private final FixtureWorld world;
        private final GameObject oldObject;
        private final Result oldUnregister;
        private final Result oldRollback;
        private final GameObject newObject;
        private final Result newRegister;
        private RegionCollisionFootprintMutationExecutor.MutableTileAccess
            tileAccess;

        private Prepared(
                FixtureWorld world,
                GameObject oldObject,
                Result oldUnregister,
                Result oldRollback,
                GameObject newObject,
                Result newRegister) {
            this.world = world;
            this.oldObject = oldObject;
            this.oldUnregister = oldUnregister;
            this.oldRollback = oldRollback;
            this.newObject = newObject;
            this.newRegister = newRegister;
            this.tileAccess = world.tiles;
        }

        private static Prepared register(
                FixtureWorld world, GameObject object, Definition definition) {
            return new Prepared(
                world, null, null, null, object,
                plan(Operation.REGISTER, object, definition));
        }

        private static Prepared unregister(
                FixtureWorld world, GameObject object, Definition definition) {
            return new Prepared(
                world, object,
                plan(Operation.UNREGISTER, object, definition),
                plan(Operation.REGISTER, object, definition),
                null, null);
        }

        private static Prepared replace(
                FixtureWorld world,
                GameObject oldObject,
                Definition oldDefinition,
                GameObject newObject,
                Definition newDefinition) {
            return new Prepared(
                world, oldObject,
                plan(Operation.UNREGISTER, oldObject, oldDefinition),
                plan(Operation.REGISTER, oldObject, oldDefinition),
                newObject,
                plan(Operation.REGISTER, newObject, newDefinition));
        }

        private RegionObjectCollisionTransactionExecutor.Result execute() {
            List<Result> footprints = new ArrayList<>();
            if (oldUnregister != null) { footprints.add(oldUnregister); }
            if (oldRollback != null) { footprints.add(oldRollback); }
            if (newRegister != null) { footprints.add(newRegister); }
            List<RegionObjectCollisionMutationBoundary> boundaries =
                world.boundaries(footprints);
            Region oldRegion = oldObject == null ? null
                : world.regionAt(oldObject.getX(), oldObject.getY());
            Region newRegion = newObject == null ? null
                : world.regionAt(
                    newObject.getLoc().getX(), newObject.getLoc().getY());
            return RegionObjectCollisionTransactionExecutor.execute(
                boundaries,
                oldRegion, oldObject, oldUnregister, oldRollback,
                newRegion, newObject, newRegister,
                tileAccess,
                region -> world.invalidations.incrementAndGet());
        }
    }

    private static final class FixtureWorld {
        private final Map<Long, Region> regions = new HashMap<>();
        private final TileStore tiles = new TileStore();
        private final AtomicInteger invalidations = new AtomicInteger();

        private Region regionAt(int x, int y) {
            return region(Math.floorDiv(x, 48), Math.floorDiv(y, 48));
        }

        private Region region(int regionX, int regionY) {
            long key = key(regionX, regionY);
            Region found = regions.get(key);
            if (found == null) {
                found = new Region(null, regionX, regionY);
                regions.put(key, found);
            }
            return found;
        }

        private List<RegionObjectCollisionMutationBoundary> boundaries(
                List<Result> footprints) {
            TreeMap<Long, PackedRegionCoordinate> required = new TreeMap<>();
            for (Result footprint : footprints) {
                for (PackedRegionCoordinate coordinate
                        : footprint.getRequiredRegions()) {
                    required.put(
                        key(coordinate.getRegionX(), coordinate.getRegionY()),
                        coordinate);
                }
            }
            List<RegionObjectCollisionMutationBoundary> result =
                new ArrayList<>();
            for (PackedRegionCoordinate coordinate : required.values()) {
                result.add(region(
                    coordinate.getRegionX(), coordinate.getRegionY())
                    .getObjectCollisionMutationBoundary());
            }
            return Collections.unmodifiableList(result);
        }

        private static long key(int x, int y) {
            return ((long) x << 32) ^ (y & 0xffffffffL);
        }
    }

    private static class TileStore implements
            RegionCollisionFootprintMutationExecutor.MutableTileAccess {
        private final Map<Long, TileValue> values = new HashMap<>();

        @Override
        public synchronized TileValue getMutableTile(int x, int y) {
            long key = FixtureWorld.key(x, y);
            TileValue tile = values.get(key);
            if (tile == null) {
                tile = new TileValue();
                values.put(key, tile);
            }
            return tile;
        }

        private TileValue get(int x, int y) {
            return getMutableTile(x, y);
        }

        private synchronized void put(int x, int y, TileValue tile) {
            values.put(FixtureWorld.key(x, y), tile);
        }

        private synchronized int nonZeroTileCount() {
            int count = 0;
            for (TileValue tile : values.values()) {
                if (tile.getBlockingSceneryCount() != 0
                        || tile.getDynamicProjectileCount() != 0) {
                    count++;
                    continue;
                }
                for (int value : tile.getDynamicCollisionCounts()) {
                    if (value != 0) { count++; break; }
                }
            }
            return count;
        }
    }

    private static final class BlockingTileAccess implements
            RegionCollisionFootprintMutationExecutor.MutableTileAccess {
        private final TileStore delegate;
        private final CountDownLatch entered;
        private final CountDownLatch release;
        private boolean blocked;

        private BlockingTileAccess(
                TileStore delegate,
                CountDownLatch entered,
                CountDownLatch release) {
            this.delegate = delegate;
            this.entered = entered;
            this.release = release;
        }

        @Override
        public TileValue getMutableTile(int x, int y) {
            if (!blocked) {
                blocked = true;
                entered.countDown();
                await(release, "release first transaction");
            }
            return delegate.getMutableTile(x, y);
        }
    }
}
'''


class LayeredMapsSliceOneHundredThirtySixTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-thirty-six-"
        )
        cls.classes = Path(cls.compile_temp.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.compile_temp.name) / (
            "src/com/openrsc/server/model/world/region/"
            "RegionObjectCollisionTransactionFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
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

    def test_composed_transaction_fixture(self):
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

    def test_world_and_region_use_only_the_composed_membership_seam(self):
        world = WORLD.read_text(encoding="utf-8")
        region = REGION.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        self.assertIn("applyGameObjectTransaction(", world)
        self.assertIn(
            "applyObjectMembershipAndCollisionTransaction(", world
        )
        self.assertNotIn("o.setLocation(", world)
        self.assertNotIn("o.remove();", world)
        self.assertIn(
            "GameObject membership registration requires its ordered "
            "collision transaction",
            region,
        )
        self.assertIn(
            "GameObject membership removal requires its ordered collision "
            "transaction",
            region,
        )
        self.assertIn(
            "public void applyObjectMembershipAndCollisionTransaction(",
            manager,
        )
        self.assertIn("RegionObjectCollisionTransactionExecutor.execute(", manager)

    def test_scheduler_and_handlers_do_not_gain_transaction_authority(self):
        name = "RegionObjectCollisionTransactionExecutor"
        self.assertNotIn(name, STORE.read_text(encoding="utf-8"))
        self.assertNotIn(name, HANDLER.read_text(encoding="utf-8"))
        transaction = TRANSACTION.read_text(encoding="utf-8")
        for forbidden in (
            "GameTickEventStore", "GameEventHandler", "DelayedEvent",
            "ActionSender", "sendUpdatePackets", "Player",
        ):
            self.assertNotIn(forbidden, transaction)

    def test_living_plan_records_slice_one_hundred_thirty_six(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 136: Atomic object membership and collision", plan
        )
        self.assertIn("exact slot", plan)
        self.assertIn("rollback", plan)


if __name__ == "__main__":
    unittest.main()
