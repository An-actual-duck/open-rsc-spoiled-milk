#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BOUNDARY = ROOT / (
    "server/src/com/openrsc/server/model/world/region/"
    "RegionObjectCollisionMutationBoundary.java"
)
REGION = ROOT / "server/src/com/openrsc/server/model/world/region/Region.java"
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
WORLD = ROOT / "server/src/com/openrsc/server/model/world/World.java"
STORE = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameTickEventStore.java"
)
HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.model.world.region;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class RegionObjectCollisionMutationBoundaryFixture {
    public static void main(String[] args) throws Exception {
        sameRegionOperationsExcludeEachOther();
        crossRegionOperationHoldsCanonicalSet();
        signedRegionCoordinatesRemainValid();
        reversedAndDuplicateInputsRefuseBeforeOperation();
        unavailableAndCompletedResultsRemainInert();
    }

    private static void signedRegionCoordinatesRemainValid() {
        RegionObjectCollisionMutationBoundary negative =
            new RegionObjectCollisionMutationBoundary(-1, -2);
        RegionObjectCollisionMutationBoundary originColumn =
            new RegionObjectCollisionMutationBoundary(0, -2);
        AtomicBoolean ran = new AtomicBoolean();
        RegionObjectCollisionMutationBoundary.executeReadOnly(
            Arrays.asList(negative, originColumn), held -> {
                check(held.getCoordinates().get(0).getRegionX() == -1
                        && held.getCoordinates().get(0).getRegionY() == -2
                        && held.getCoordinates().get(1).getRegionX() == 0
                        && held.getCoordinates().get(1).getRegionY() == -2,
                    "signed Region coordinates retain canonical order");
                ran.set(true);
            });
        check(ran.get(),
            "visibility-edge signed Region boundaries remain usable");
    }

    private static void sameRegionOperationsExcludeEachOther()
            throws Exception {
        RegionObjectCollisionMutationBoundary boundary =
            new RegionObjectCollisionMutationBoundary(10, 10);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread first = new Thread(() -> {
            try {
                RegionObjectCollisionMutationBoundary.executeReadOnly(
                    Collections.singletonList(boundary), held -> {
                        check(held.areAllBoundariesHeld(),
                            "first operation boundary held");
                        firstEntered.countDown();
                        await(releaseFirst, "release first operation");
                    });
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        }, "boundary-first");
        Thread second = new Thread(() -> {
            try {
                await(firstEntered, "first operation entered");
                RegionObjectCollisionMutationBoundary.executeReadOnly(
                    Collections.singletonList(boundary), held ->
                        secondEntered.countDown());
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            }
        }, "boundary-second");

        first.start();
        second.start();
        await(firstEntered, "first operation entered from fixture");
        check(!secondEntered.await(150L, TimeUnit.MILLISECONDS),
            "second operation cannot cross the same Region boundary");
        releaseFirst.countDown();
        join(first);
        join(second);
        check(failure.get() == null, "same-Region fixture completed");
        check(secondEntered.getCount() == 0L,
            "second operation entered after release");
    }

    private static void crossRegionOperationHoldsCanonicalSet() {
        RegionObjectCollisionMutationBoundary first =
            new RegionObjectCollisionMutationBoundary(10, 10);
        RegionObjectCollisionMutationBoundary second =
            new RegionObjectCollisionMutationBoundary(11, 10);
        AtomicBoolean ran = new AtomicBoolean();
        RegionObjectCollisionMutationBoundary.Execution execution =
            RegionObjectCollisionMutationBoundary.executeReadOnly(
                Arrays.asList(first, second), held -> {
                    check(held.areAllBoundariesHeld(),
                        "all cross-Region monitors held");
                    check(held.getBoundaryCount() == 2
                            && held.getCoordinates().get(0).getRegionX() == 10
                            && held.getCoordinates().get(1).getRegionX() == 11,
                        "held boundary coordinates remain canonical");
                    check(!held.isMutationAuthorized()
                            && !held.isMutationPerformed()
                            && !held.isRollbackAuthorized()
                            && !held.isLifecycleAuthority(),
                        "held set grants no authority");
                    expectUnsupported(() -> held.getCoordinates().clear());
                    ran.set(true);
                });
        check(ran.get() && execution.isReadOnlyOperationCompleted()
                && !execution.isRefused()
                && execution.wereAllBoundariesHeldDuringOperation()
                && execution.getDeclaredBoundaryCount() == 2,
            "cross-Region read-only operation completed under both monitors");
        assertInert(execution);
    }

    private static void reversedAndDuplicateInputsRefuseBeforeOperation() {
        RegionObjectCollisionMutationBoundary first =
            new RegionObjectCollisionMutationBoundary(10, 10);
        RegionObjectCollisionMutationBoundary second =
            new RegionObjectCollisionMutationBoundary(11, 10);
        AtomicBoolean ran = new AtomicBoolean();
        expectIllegal(() -> RegionObjectCollisionMutationBoundary
            .executeReadOnly(Arrays.asList(second, first), held -> ran.set(true)));
        expectIllegal(() -> RegionObjectCollisionMutationBoundary
            .executeReadOnly(Arrays.asList(first, first), held -> ran.set(true)));
        expectIllegal(() -> RegionObjectCollisionMutationBoundary
            .executeReadOnly(Collections.emptyList(), held -> ran.set(true)));
        check(!ran.get(), "invalid order refuses before operation invocation");
    }

    private static void unavailableAndCompletedResultsRemainInert() {
        RegionObjectCollisionMutationBoundary.Execution unavailable =
            RegionObjectCollisionMutationBoundary.refuseUnavailable(2);
        check(unavailable.isRefused()
                && !unavailable.isReadOnlyOperationCompleted()
                && unavailable.getOutcome()
                    == RegionObjectCollisionMutationBoundary.Outcome.REFUSED
                && unavailable.getReason()
                    == RegionObjectCollisionMutationBoundary.Reason
                        .REQUIRED_REGION_UNAVAILABLE
                && unavailable.getDeclaredBoundaryCount() == 2
                && !unavailable.wereAllBoundariesHeldDuringOperation(),
            "unavailable Region refuses without running an operation");
        assertInert(unavailable);
        expectIllegal(() -> RegionObjectCollisionMutationBoundary
            .refuseUnavailable(0));
    }

    private static void assertInert(
            RegionObjectCollisionMutationBoundary.Execution execution) {
        check(!execution.isOperationRetained()
                && !execution.isResultValueRetained()
                && !execution.isMutationAuthorized()
                && !execution.isMutationPerformed()
                && !execution.isRollbackAuthorized()
                && !execution.isRollbackPerformed()
                && !execution.isExecutableRestoration()
                && !execution.isCommitToken()
                && !execution.isArrivalGate()
                && !execution.isLifecycleAuthority(),
            "boundary execution remains non-authoritative");
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
            // Expected refusal.
        }
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


class LayeredMapsSliceOneHundredThirtyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-thirty-"
        )
        cls.classes = Path(cls.compile_temp.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.compile_temp.name) / (
            "src/com/openrsc/server/model/world/region/"
            "RegionObjectCollisionMutationBoundaryFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(BOUNDARY), str(fixture),
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

    def test_boundary_fixture_proves_order_and_exclusion(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.region."
                "RegionObjectCollisionMutationBoundaryFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
            timeout=10,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_region_and_manager_wire_only_the_disconnected_boundary(self):
        region = REGION.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        self.assertIn(
            "new RegionObjectCollisionMutationBoundary(regionX, regionY)",
            region,
        )
        self.assertIn("getObjectCollisionMutationBoundary()", region)
        self.assertIn(
            "executeUnderExistingOrderedObjectCollisionBoundaries", manager
        )
        self.assertIn("peekRegionFromSectorCoordinates(", manager)
        self.assertIn("synchronized (layeredRegionLifecycleLock)", manager)
        self.assertIn(
            "RegionObjectCollisionMutationBoundary.executeReadOnly(", manager
        )
        read_only_start = manager.index(
            "executeUnderExistingOrderedObjectCollisionBoundaries("
        )
        read_only_end = manager.index(
            "private static int comparePackedRegionCoordinates(",
            read_only_start,
        )
        self.assertNotIn(
            "getRegionFromSectorCoordinates(\n"
            "\t\t\t\t\tcoordinate.getRegionX()",
            manager[read_only_start:read_only_end],
        )

    def test_boundary_is_not_used_by_existing_mutation_or_event_paths(self):
        name = "RegionObjectCollisionMutationBoundary"
        self.assertNotIn(name, WORLD.read_text(encoding="utf-8"))
        self.assertNotIn(name, STORE.read_text(encoding="utf-8"))
        self.assertNotIn(name, HANDLER.read_text(encoding="utf-8"))
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        self.assertEqual(
            1,
            manager.count(
                "executeUnderExistingOrderedObjectCollisionBoundaries("
            ),
        )
        boundary = BOUNDARY.read_text(encoding="utf-8")
        for forbidden in (
            "GameObject", "TileValue", "registerGameObject",
            "unregisterGameObject", "replaceGameObject", "getMutableTile",
            "GameTickEvent", "sendUpdatePackets",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_living_plan_records_slice_one_hundred_thirty(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 130: Disconnected ordered Region boundaries", plan
        )
        self.assertIn("same-Region exclusion", plan)
        self.assertIn("reverse order", plan)


if __name__ == "__main__":
    unittest.main()
