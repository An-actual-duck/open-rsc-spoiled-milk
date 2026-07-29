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
STORE = RSC / "handler/GameTickEventStore.java"
HANDLER = RSC / "handler/GameEventHandler.java"
REGION = ROOT / "server/src/com/openrsc/server/model/world/region/Region.java"
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
WORLD = ROOT / "server/src/com/openrsc/server/model/world/World.java"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.event.rsc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionTransactionContract.BoundaryDeclaration;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionTransactionContract.Outcome;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionTransactionContract.PackedRegionCoordinate;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionTransactionContract.Reason;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionTransactionContract.RegionBoundary;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationMutationIntent.AuthoredConstructionKind;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTargetDecision.ObservedTargetState;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTargetDecision.TargetOperation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTransientRollbackSnapshot.Candidate;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTransientRollbackSnapshot.CollisionContribution;

public final class RestorationCollisionTransactionFixture {
    public static void main(String[] args) {
        acceptsCanonicalCrossRegionCoverage();
        acceptsCollisionlessAnchorCoverage();
        refusesBoundaryAndCoverageFailures();
        keepsDeclarationsAndResultsImmutableAndInert();
    }

    private static void acceptsCanonicalCrossRegionCoverage() {
        GameTickEventRestorationCollisionTransactionContract result =
            GameTickEventRestorationCollisionTransactionContract.evaluate(
                rollbackSnapshot(true), validBoundary(true));
        check(result.getOutcome()
                == Outcome.TRANSACTION_PRECONDITION_SATISFIED
                && result.getReason()
                    == Reason.TRANSACTION_PRECONDITION_REVALIDATED
                && result.isTransactionPreconditionSatisfied()
                && !result.isRefused()
                && result.getRequiredRegionCount() == 2,
            "canonical cross-Region coverage satisfies the contract");
        check(result.getRequiredRegions().get(0).getRegionX() == 10
                && result.getRequiredRegions().get(0).getRegionY() == 10
                && result.getRequiredRegions().get(1).getRegionX() == 11
                && result.getRequiredRegions().get(1).getRegionY() == 10,
            "required Regions derive from anchor and collision footprint");
        assertInert(result);
    }

    private static void acceptsCollisionlessAnchorCoverage() {
        GameTickEventRestorationCollisionTransactionContract result =
            GameTickEventRestorationCollisionTransactionContract.evaluate(
                rollbackSnapshot(false), validBoundary(false));
        check(result.isTransactionPreconditionSatisfied()
                && result.getRequiredRegionCount() == 1
                && result.getRequiredRegions().get(0)
                    .equals(PackedRegionCoordinate.of(10, 10)),
            "collisionless objects still require their anchor Region");
    }

    private static void refusesBoundaryAndCoverageFailures() {
        GameTickEventRestorationTransientRollbackSnapshot snapshot =
            rollbackSnapshot(true);
        List<RegionBoundary> exact = regions(true);
        expectReason(snapshot, boundary(
            false, false, true, true, true, true, true, exact),
            Reason.EVENT_EXECUTION_BOUNDARY_MISSING);
        expectReason(snapshot, boundary(
            true, true, true, true, true, true, true, exact),
            Reason.SCHEDULER_STORE_BOUNDARY_HELD);
        expectReason(snapshot, boundary(
            true, false, false, true, true, true, true, exact),
            Reason.TARGET_REGION_OBJECT_BOUNDARY_MISSING);
        expectReason(snapshot, boundary(
            true, false, true, false, true, true, true, exact),
            Reason.EXACT_TARGET_NOT_REVALIDATED);
        expectReason(snapshot, boundary(
            true, false, true, true, true, true, true,
            Arrays.asList(exact.get(1), exact.get(0))),
            Reason.REGION_LOCK_ORDER_NOT_CANONICAL);
        expectReason(snapshot, boundary(
            true, false, true, true, true, true, true,
            Collections.singletonList(exact.get(0))),
            Reason.REGION_COVERAGE_MISMATCH);
        expectReason(snapshot, boundary(
            true, false, true, true, true, true, true,
            Arrays.asList(
                RegionBoundary.declare(10, 10, true, true),
                RegionBoundary.declare(11, 10, false, true))),
            Reason.REQUIRED_REGION_UNAVAILABLE);
        expectReason(snapshot, boundary(
            true, false, true, true, true, true, true,
            Arrays.asList(
                RegionBoundary.declare(10, 10, true, true),
                RegionBoundary.declare(11, 10, true, false))),
            Reason.COLLISION_MUTATION_BOUNDARY_MISSING);
        expectReason(snapshot, boundary(
            true, false, true, true, false, true, true, exact),
            Reason.SHARED_MUTATION_BOUNDARY_INCOMPLETE);
        expectReason(snapshot, boundary(
            true, false, true, true, true, false, true, exact),
            Reason.COLLISION_SNAPSHOT_NOT_FRESHLY_COMPARED);
        expectReason(snapshot, boundary(
            true, false, true, true, true, true, false, exact),
            Reason.ROLLBACK_UNCHANGED_STATE_CHECK_MISSING);
    }

    private static void keepsDeclarationsAndResultsImmutableAndInert() {
        List<RegionBoundary> mutable = new ArrayList<>(regions(true));
        BoundaryDeclaration declaration = boundary(
            true, false, true, true, true, true, true, mutable);
        mutable.clear();
        check(declaration.getRegions().size() == 2,
            "boundary declaration defensively copies Regions");
        expectUnsupported(() -> declaration.getRegions().clear());
        GameTickEventRestorationCollisionTransactionContract result =
            GameTickEventRestorationCollisionTransactionContract.evaluate(
                rollbackSnapshot(true), declaration);
        expectUnsupported(() -> result.getRequiredRegions().clear());
        expectIllegal(() -> boundary(
            true, false, true, true, true, true, true,
            Collections.<RegionBoundary>emptyList()));
        expectIllegal(() -> RegionBoundary.declare(-1, 10, true, true));
        assertInert(result);
    }

    private static BoundaryDeclaration validBoundary(boolean crossRegion) {
        return boundary(
            true, false, true, true, true, true, true,
            regions(crossRegion));
    }

    private static BoundaryDeclaration boundary(
            boolean eventHeld, boolean storeHeld, boolean objectHeld,
            boolean targetFresh, boolean sharedMutationBoundary,
            boolean collisionFresh, boolean rollbackCheck,
            List<RegionBoundary> regions) {
        return BoundaryDeclaration.declare(
            eventHeld, storeHeld, objectHeld, targetFresh,
            sharedMutationBoundary, collisionFresh, rollbackCheck, regions);
    }

    private static List<RegionBoundary> regions(boolean crossRegion) {
        List<RegionBoundary> regions = new ArrayList<>();
        regions.add(RegionBoundary.declare(10, 10, true, true));
        if (crossRegion) {
            regions.add(RegionBoundary.declare(11, 10, true, true));
        }
        return regions;
    }

    private static GameTickEventRestorationTransientRollbackSnapshot
            rollbackSnapshot(boolean crossRegion) {
        List<CollisionContribution> contributions = new ArrayList<>();
        if (crossRegion) {
            contributions.add(CollisionContribution.of(527, 489, 0, 8, 1));
            contributions.add(CollisionContribution.of(528, 489, 0, 2, 1));
        }
        Candidate candidate = Candidate.declare(
            4, 4, 527, 489, 0, 0, null, 0,
            7L, 10, 10, 22, AuthoredConstructionKind.SCENERY,
            1, true, true, true, contributions);
        GameTickEventRestorationTransientRollbackSnapshot.Creation creation =
            GameTickEventRestorationTransientRollbackSnapshot.assess(
                transientIntent(), candidate);
        check(creation.isSnapshotAvailable(), "rollback snapshot available");
        return creation.getSnapshot();
    }

    private static GameTickEventRestorationMutationIntent transientIntent() {
        TargetOperation operation = TargetOperation.SCENERY_SPAWN;
        ObservedTargetState state =
            ObservedTargetState.EXACT_AUTHORED_TRANSIENT_PRESENT;
        GameTickEventRestorationTargetDecision decision =
            GameTickEventRestorationTargetDecision.decideDetached(
                operation, true, 7L, 7L, state);
        GameTickEventRestorationAtomicRevalidationContract atomic =
            GameTickEventRestorationAtomicRevalidationContract.evaluate(
                GameTickEventRestorationAtomicRevalidationContract
                    .BoundaryDeclaration.declare(
                        "scheduler", "scheduler", 4L, 4L, 7L, 7L,
                        true, false, true, true, true), decision);
        GameTickEventRestorationTargetRevalidation observed =
            GameTickEventRestorationTargetRevalidation.observe(
                true, 1, 0, 1, state, true, decision, atomic);
        GameTickEventRestorationTargetRevalidationRequest request =
            GameTickEventRestorationTargetRevalidationRequest.request(
                "scheduler", 4L, 7L, 7L, true, false, true,
                operation, 310, 310, 527, 489, 0, 0, false,
                10, 10, 22, "SCENERY");
        GameTickEventRestorationMutationIntent.Creation creation =
            GameTickEventRestorationMutationIntent.assess(
                request, observed, 12L, 12L);
        check(creation.isIntentAvailable(), "fixture intent available");
        return creation.getIntent();
    }

    private static void expectReason(
            GameTickEventRestorationTransientRollbackSnapshot snapshot,
            BoundaryDeclaration boundary, Reason reason) {
        GameTickEventRestorationCollisionTransactionContract result =
            GameTickEventRestorationCollisionTransactionContract.evaluate(
                snapshot, boundary);
        check(result.isRefused()
                && !result.isTransactionPreconditionSatisfied()
                && result.getOutcome() == Outcome.REFUSED
                && result.getReason() == reason,
            "expected fail-closed reason " + reason);
        assertInert(result);
    }

    private static void assertInert(
            GameTickEventRestorationCollisionTransactionContract result) {
        check(result.isDormantContract()
                && !result.isRuntimeLockAcquired()
                && !result.isRuntimeComparisonPerformed()
                && !result.isSnapshotRetainedByRuntime()
                && !result.isReusablePermit()
                && !result.isAtomicityClaimed()
                && !result.isRollbackAuthorized()
                && !result.isRollbackPerformed()
                && !result.isMutationAuthorized()
                && !result.isMutationPerformed()
                && !result.isExecutableRestoration()
                && !result.isCommitToken()
                && !result.isArrivalGate()
                && !result.isLifecycleAuthority(),
            "collision transaction contract remains inert");
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


class LayeredMapsSliceOneHundredTwentyNineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-twenty-nine-"
        )
        cls.classes = Path(cls.compile_temp.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.compile_temp.name) / (
            "src/com/openrsc/server/event/rsc/"
            "RestorationCollisionTransactionFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(STATE), str(REQUIREMENT), str(DECISION),
                str(ATOMIC_CONTRACT), str(REQUEST), str(REVALIDATION),
                str(COMMIT_REQUEST), str(INTENT), str(ROLLBACK),
                str(TRANSACTION), str(fixture),
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

    def test_transaction_fixture_is_executable_and_fail_closed(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.event.rsc."
                "RestorationCollisionTransactionFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_transaction_contract_has_no_runtime_or_mutation_capability(self):
        source = TRANSACTION.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.model",
            "import com.openrsc.server.net",
            "synchronized (", "GameTickEvent event", "World world",
            "Region region", "GameObject object", "TileValue tile",
            "registerGameObject", "unregisterGameObject",
            "replaceGameObject", "getMutableTile", ".doRun()", ".stop()",
            "sendUpdatePackets", "Lock ",
        ):
            self.assertNotIn(forbidden, source)
        for required in (
            "isRuntimeLockAcquired() { return false; }",
            "isRuntimeComparisonPerformed() { return false; }",
            "isReusablePermit() { return false; }",
            "isAtomicityClaimed() { return false; }",
            "isRollbackAuthorized() { return false; }",
            "isRollbackPerformed() { return false; }",
            "isMutationAuthorized() { return false; }",
            "isMutationPerformed() { return false; }",
            "isExecutableRestoration() { return false; }",
            "isCommitToken() { return false; }",
            "isArrivalGate() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_transaction_contract_remains_disconnected_from_mutation_runtime(self):
        name = "GameTickEventRestorationCollisionTransactionContract"
        for path in (STORE, HANDLER, REGION, WORLD):
            self.assertNotIn(name, path.read_text(encoding="utf-8"))
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        self.assertIn(
            "executeUnderExistingOrderedObjectCollisionBoundaries", manager
        )

    def test_living_plan_records_slice_one_hundred_twenty_nine(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 129: Dormant collision transaction contract", plan
        )
        self.assertIn("canonical lock order", plan)
        self.assertIn("multi-Region", plan)


if __name__ == "__main__":
    unittest.main()
