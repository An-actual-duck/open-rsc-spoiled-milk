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
COMPARE_APPLY = RSC / "GameTickEventRestorationCompareAndApplyContract.java"
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
    .GameTickEventRestorationCompareAndApplyContract.ApplyOperation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCompareAndApplyContract.BoundaryDeclaration;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCompareAndApplyContract.FreshTargetObservation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCompareAndApplyContract.Outcome;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCompareAndApplyContract.Reason;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCompareAndApplyContract.RollbackStrategy;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTargetDecision.ObservedTargetState;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTargetDecision.TargetOperation;

public final class RestorationCompareAndApplyFixture {
    public static void main(String[] args) {
        acceptsThreeExactApplyShapes();
        recognizesIdempotentNoOps();
        refusesChangedOrIncompleteTargetEvidence();
        refusesMissingOrChangedOuterBoundaries();
    }

    private static void acceptsThreeExactApplyShapes() {
        assertApply(intent(TargetOperation.SCENERY_SPAWN,
                ObservedTargetState.EMPTY),
            target(ObservedTargetState.EMPTY, 0, 0, 0, true),
            boundary(true, false, true, true, true, true, true, true,
                "scheduler", "scheduler", 4L, 4L, 7L, 7L, 12L, 12L),
            ApplyOperation.SPAWN_INTO_EMPTY,
            RollbackStrategy.REMOVE_INSERTED_SCENERY);

        GameTickEventRestorationMutationIntent transientIntent = intent(
            TargetOperation.SCENERY_SPAWN,
            ObservedTargetState.EXACT_AUTHORED_TRANSIENT_PRESENT);
        assertReason(transientIntent,
            target(ObservedTargetState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                1, 0, 1, true),
            boundary(true, false, true, true, true, true, false, true,
                "scheduler", "scheduler", 4L, 4L, 7L, 7L, 12L, 12L),
            Reason.EXACT_TRANSIENT_ROLLBACK_STATE_MISSING);
        assertApply(transientIntent,
            target(ObservedTargetState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                1, 0, 1, true),
            validBoundary(), ApplyOperation.REPLACE_EXACT_AUTHORED_TRANSIENT,
            RollbackStrategy.RESTORE_EXACT_AUTHORED_TRANSIENT);

        assertApply(intent(TargetOperation.SCENERY_REMOVE,
                ObservedTargetState.EXACT_RESTORATION_SCENERY_PRESENT),
            target(ObservedTargetState.EXACT_RESTORATION_SCENERY_PRESENT,
                1, 1, 1, true),
            validBoundary(), ApplyOperation.REMOVE_EXACT_RESTORATION_SCENERY,
            RollbackStrategy.RESTORE_REMOVED_SCENERY);
    }

    private static void recognizesIdempotentNoOps() {
        assertNoOp(intent(TargetOperation.SCENERY_SPAWN,
                ObservedTargetState.EMPTY),
            target(ObservedTargetState.EXACT_RESTORATION_SCENERY_PRESENT,
                1, 1, 1, true));
        assertNoOp(intent(TargetOperation.SCENERY_REMOVE,
                ObservedTargetState.EXACT_RESTORATION_SCENERY_PRESENT),
            target(ObservedTargetState.EMPTY, 0, 0, 0, true));
    }

    private static void refusesChangedOrIncompleteTargetEvidence() {
        GameTickEventRestorationMutationIntent spawn = intent(
            TargetOperation.SCENERY_SPAWN, ObservedTargetState.EMPTY);
        assertReason(spawn,
            target(ObservedTargetState.MISMATCHED_OR_IDENTITYLESS_OCCUPANT,
                1, 0, 0, true), validBoundary(),
            Reason.TARGET_CHANGED_SINCE_INTENT);
        assertReason(spawn,
            target(ObservedTargetState.EMPTY, 1, 0, 0, true), validBoundary(),
            Reason.TARGET_EVIDENCE_INCONSISTENT);
        assertReason(spawn,
            target(ObservedTargetState.EMPTY, 0, 0, 0, false), validBoundary(),
            Reason.TARGET_NOT_OBSERVED_INSIDE_REGION_BOUNDARY);
        assertReason(spawn,
            target(ObservedTargetState.EMPTY, 0, 0, 0, true),
            boundary(true, false, true, true, true, true, true, false,
                "scheduler", "scheduler", 4L, 4L, 7L, 7L, 12L, 12L),
            Reason.COLLISION_ROLLBACK_UNAVAILABLE);
    }

    private static void refusesMissingOrChangedOuterBoundaries() {
        GameTickEventRestorationMutationIntent spawn = intent(
            TargetOperation.SCENERY_SPAWN, ObservedTargetState.EMPTY);
        FreshTargetObservation empty = target(
            ObservedTargetState.EMPTY, 0, 0, 0, true);
        assertReason(spawn, empty,
            boundary(false, false, true, true, true, true, true, true,
                "scheduler", "scheduler", 4L, 4L, 7L, 7L, 12L, 12L),
            Reason.EVENT_EXECUTION_BOUNDARY_MISSING);
        assertReason(spawn, empty,
            boundary(true, true, true, true, true, true, true, true,
                "scheduler", "scheduler", 4L, 4L, 7L, 7L, 12L, 12L),
            Reason.SCHEDULER_STORE_BOUNDARY_HELD);
        assertReason(spawn, empty,
            boundary(true, false, false, true, true, true, true, true,
                "scheduler", "scheduler", 4L, 4L, 7L, 7L, 12L, 12L),
            Reason.REGISTRATION_NOT_REVALIDATED);
        assertReason(spawn, empty,
            boundary(true, false, true, true, true, true, true, true,
                "scheduler", "other", 4L, 4L, 7L, 7L, 12L, 12L),
            Reason.SCHEDULER_INSTANCE_MISMATCH);
        assertReason(spawn, empty,
            boundary(true, false, true, true, true, true, true, true,
                "scheduler", "scheduler", 4L, 5L, 7L, 7L, 12L, 12L),
            Reason.REGISTRATION_SEQUENCE_MISMATCH);
        assertReason(spawn, empty,
            boundary(true, false, true, true, true, true, true, true,
                "scheduler", "scheduler", 4L, 4L, 7L, 8L, 12L, 12L),
            Reason.PROPOSAL_GENERATION_MISMATCH);
        assertReason(spawn, empty,
            boundary(true, false, true, false, true, true, true, true,
                "scheduler", "scheduler", 4L, 4L, 7L, 7L, 12L, 12L),
            Reason.LIFECYCLE_VERSION_NOT_REVALIDATED);
        assertReason(spawn, empty,
            boundary(true, false, true, true, true, true, true, true,
                "scheduler", "scheduler", 4L, 4L, 7L, 7L, 12L, 13L),
            Reason.EVENT_LIFECYCLE_VERSION_MISMATCH);
        assertReason(spawn, empty,
            boundary(true, false, true, true, false, true, true, true,
                "scheduler", "scheduler", 4L, 4L, 7L, 7L, 12L, 12L),
            Reason.REGION_OBJECT_BOUNDARY_MISSING);
        assertReason(spawn, empty,
            boundary(true, false, true, true, true, false, true, true,
                "scheduler", "scheduler", 4L, 4L, 7L, 7L, 12L, 12L),
            Reason.EXACT_INTENT_COMPARISON_MISSING);
    }

    private static GameTickEventRestorationMutationIntent intent(
            TargetOperation operation, ObservedTargetState state) {
        GameTickEventRestorationTargetDecision decision =
            GameTickEventRestorationTargetDecision.decideDetached(
                operation, true, 7L, 7L, state);
        int slots = state == ObservedTargetState.EMPTY ? 0 : 1;
        int restored = state
            == ObservedTargetState.EXACT_RESTORATION_SCENERY_PRESENT ? 1 : 0;
        int authored = slots;
        GameTickEventRestorationAtomicRevalidationContract atomic =
            GameTickEventRestorationAtomicRevalidationContract.evaluate(
                GameTickEventRestorationAtomicRevalidationContract
                    .BoundaryDeclaration.declare(
                        "scheduler", "scheduler", 4L, 4L, 7L, 7L,
                        true, false, true, true, true), decision);
        GameTickEventRestorationTargetRevalidation observed =
            GameTickEventRestorationTargetRevalidation.observe(
                true, slots, restored, authored, state, true,
                decision, atomic);
        GameTickEventRestorationTargetRevalidationRequest request =
            GameTickEventRestorationTargetRevalidationRequest.request(
                "scheduler", 4L, 7L, 7L, true, false, true,
                operation, 310, 310, 524, 489, 0, 0, false,
                10, 10, 22, "SCENERY");
        GameTickEventRestorationMutationIntent.Creation creation =
            GameTickEventRestorationMutationIntent.assess(
                request, observed, 12L, 12L);
        check(creation.isIntentAvailable(), "fixture intent available");
        return creation.getIntent();
    }

    private static BoundaryDeclaration validBoundary() {
        return boundary(true, false, true, true, true, true, true, true,
            "scheduler", "scheduler", 4L, 4L, 7L, 7L, 12L, 12L);
    }

    private static BoundaryDeclaration boundary(
            boolean eventHeld, boolean storeHeld, boolean registrationFresh,
            boolean lifecycleFresh, boolean regionHeld, boolean exactCompared,
            boolean transientRollback, boolean collisionRollback,
            String expectedScheduler, String observedScheduler,
            long expectedRegistration, long observedRegistration,
            long expectedGeneration, long observedGeneration,
            long expectedLifecycle, long observedLifecycle) {
        return BoundaryDeclaration.declare(
            expectedScheduler, observedScheduler,
            expectedRegistration, observedRegistration,
            expectedGeneration, observedGeneration,
            expectedLifecycle, observedLifecycle,
            eventHeld, storeHeld, registrationFresh, lifecycleFresh,
            regionHeld, exactCompared, transientRollback, collisionRollback);
    }

    private static FreshTargetObservation target(
            ObservedTargetState state, int slots, int restored, int authored,
            boolean insideRegionBoundary) {
        return FreshTargetObservation.observe(
            state, slots, restored, authored, insideRegionBoundary);
    }

    private static void assertApply(
            GameTickEventRestorationMutationIntent intent,
            FreshTargetObservation target, BoundaryDeclaration boundary,
            ApplyOperation operation, RollbackStrategy rollback) {
        GameTickEventRestorationCompareAndApplyContract result =
            GameTickEventRestorationCompareAndApplyContract.evaluate(
                intent, boundary, target);
        check(result.getOutcome() == Outcome.APPLY_PRECONDITION_SATISFIED
                && result.isApplyPreconditionSatisfied()
                && !result.isRefused() && !result.isNoOpSatisfied()
                && result.getReason()
                    == Reason.APPLY_PRECONDITION_REVALIDATED
                && result.getApplyOperation() == operation
                && result.getRollbackStrategy() == rollback,
            "exact apply precondition and rollback strategy");
        assertInert(result);
    }

    private static void assertNoOp(
            GameTickEventRestorationMutationIntent intent,
            FreshTargetObservation target) {
        GameTickEventRestorationCompareAndApplyContract result =
            GameTickEventRestorationCompareAndApplyContract.evaluate(
                intent, validBoundary(), target);
        check(result.getOutcome() == Outcome.NO_OP_SATISFIED
                && result.isNoOpSatisfied() && !result.isRefused()
                && !result.isApplyPreconditionSatisfied()
                && result.getReason()
                    == Reason.DESIRED_STATE_ALREADY_SATISFIED
                && result.getApplyOperation() == ApplyOperation.NONE
                && result.getRollbackStrategy() == RollbackStrategy.NONE,
            "already satisfied state is an inert no-op");
        assertInert(result);
    }

    private static void assertReason(
            GameTickEventRestorationMutationIntent intent,
            FreshTargetObservation target, BoundaryDeclaration boundary,
            Reason reason) {
        GameTickEventRestorationCompareAndApplyContract result =
            GameTickEventRestorationCompareAndApplyContract.evaluate(
                intent, boundary, target);
        check(result.getOutcome() == Outcome.REFUSED
                && result.isRefused() && result.getReason() == reason
                && result.getApplyOperation() == ApplyOperation.NONE
                && result.getRollbackStrategy() == RollbackStrategy.NONE,
            "expected fail-closed reason " + reason);
        assertInert(result);
    }

    private static void assertInert(
            GameTickEventRestorationCompareAndApplyContract result) {
        check(result.isDormantContract()
                && result.isFreshTargetComparisonRequired()
                && !result.isRuntimeComparisonPerformed()
                && !result.isIntentRetained()
                && !result.isReusablePermit()
                && !result.isAtomicityClaimed()
                && !result.isMutationAuthorized()
                && !result.isMutationPerformed()
                && !result.isRollbackPerformed()
                && !result.isExecutableRestoration()
                && !result.isCommitToken()
                && !result.isArrivalGate()
                && !result.isLifecycleAuthority(),
            "contract result remains entirely inert");
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
    }
}
'''


class LayeredMapsSliceOneHundredTwentySevenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-twenty-seven-"
        )
        cls.classes = Path(cls.compile_temp.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.compile_temp.name) / (
            "src/com/openrsc/server/event/rsc/"
            "RestorationCompareAndApplyFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(STATE), str(REQUIREMENT), str(DECISION),
                str(ATOMIC_CONTRACT), str(REQUEST), str(REVALIDATION),
                str(INTENT), str(COMPARE_APPLY), str(fixture),
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

    def test_contract_fixture_is_executable_and_fail_closed(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.event.rsc."
                "RestorationCompareAndApplyFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_contract_has_no_runtime_or_mutation_capability(self):
        source = COMPARE_APPLY.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.model",
            "import com.openrsc.server.net",
            "synchronized (", "GameTickEvent event", "World world",
            "Region region", "GameObject object", "registerGameObject",
            "unregisterGameObject", "replaceGameObject", ".doRun()",
            ".stop()", "sendUpdatePackets", "Lock ",
        ):
            self.assertNotIn(forbidden, source)
        for required in (
            "isRuntimeComparisonPerformed() { return false; }",
            "isReusablePermit() { return false; }",
            "isAtomicityClaimed() { return false; }",
            "isMutationAuthorized() { return false; }",
            "isMutationPerformed() { return false; }",
            "isRollbackPerformed() { return false; }",
            "isExecutableRestoration() { return false; }",
            "isCommitToken() { return false; }",
            "isArrivalGate() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_contract_remains_disconnected_from_runtime_consumers(self):
        name = "GameTickEventRestorationCompareAndApplyContract"
        self.assertNotIn(name, STORE.read_text(encoding="utf-8"))
        self.assertNotIn(name, HANDLER.read_text(encoding="utf-8"))
        self.assertNotIn(name, REGION_MANAGER.read_text(encoding="utf-8"))

    def test_living_plan_records_slice_one_hundred_twenty_seven(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 127: Dormant compare-and-apply contract", plan
        )
        self.assertIn("exact transient rollback state", plan)
        self.assertIn("compare-and-apply", plan)


if __name__ == "__main__":
    unittest.main()
