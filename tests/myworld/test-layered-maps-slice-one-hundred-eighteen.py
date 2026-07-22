#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
STATE = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationState.java"
)
REQUIREMENT = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationRequirement.java"
)
DECISION = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationTargetDecision.java"
)
CONTRACT = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationAtomicRevalidationContract.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.event.rsc;

import com.openrsc.server.event.rsc
    .GameTickEventRestorationAtomicRevalidationContract.BoundaryDeclaration;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationAtomicRevalidationContract.Outcome;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationAtomicRevalidationContract.Reason;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTargetDecision.ObservedTargetState;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTargetDecision.TargetOperation;

public final class AtomicRevalidationContractFixture {
    private static final String SCOPE =
        "00000000-0000-0000-0000-000000000118";

    public static void main(String[] args) {
        satisfiedContractsRemainNonAuthoritative();
        boundaryFencesFailClosedInOrder();
        targetRefusalRetainsItsExactReason();
        invalidDeclarationsRefuseConstruction();
    }

    private static GameTickEventRestorationTargetDecision target(
            ObservedTargetState state) {
        return GameTickEventRestorationTargetDecision.decideDetached(
            TargetOperation.SCENERY_SPAWN, true, 7L, 7L, state);
    }

    private static BoundaryDeclaration boundary(
            String observedScope, long observedRegistration,
            long observedGeneration, boolean executionBoundary,
            boolean storeBoundary, boolean registrationValidated,
            boolean regionBoundary, boolean targetInsideRegion) {
        return BoundaryDeclaration.declare(
            SCOPE, observedScope, 21L, observedRegistration, 7L,
            observedGeneration, executionBoundary, storeBoundary,
            registrationValidated, regionBoundary, targetInsideRegion);
    }

    private static BoundaryDeclaration completeBoundary() {
        return boundary(SCOPE, 21L, 7L, true, false, true, true, true);
    }

    private static void satisfiedContractsRemainNonAuthoritative() {
        assertContract(
            completeBoundary(),
            target(ObservedTargetState.EXACT_AUTHORED_TRANSIENT_PRESENT),
            Outcome.MUTATION_PRECONDITION_CONTRACT_SATISFIED,
            Reason.MUTATION_PRECONDITION_REVALIDATED);
        assertContract(
            completeBoundary(),
            target(ObservedTargetState.EXACT_RESTORATION_SCENERY_PRESENT),
            Outcome.NO_OP_CONTRACT_SATISFIED,
            Reason.DESIRED_STATE_ALREADY_SATISFIED);
    }

    private static void boundaryFencesFailClosedInOrder() {
        GameTickEventRestorationTargetDecision usable =
            target(ObservedTargetState.EXACT_AUTHORED_TRANSIENT_PRESENT);
        assertContract(
            boundary(SCOPE, 21L, 7L, false, true, false, false, false),
            usable, Outcome.REFUSED,
            Reason.EVENT_EXECUTION_BOUNDARY_MISSING);
        assertContract(
            boundary(SCOPE, 21L, 7L, true, true, false, false, false),
            usable, Outcome.REFUSED,
            Reason.SCHEDULER_STORE_BOUNDARY_HELD);
        assertContract(
            boundary(SCOPE, 21L, 7L, true, false, false, false, false),
            usable, Outcome.REFUSED,
            Reason.REGISTRATION_NOT_VALIDATED_BEFORE_REGION_BOUNDARY);
        assertContract(
            boundary("different-scope", 21L, 7L,
                true, false, true, false, false),
            usable, Outcome.REFUSED, Reason.SCHEDULER_INSTANCE_MISMATCH);
        assertContract(
            boundary(SCOPE, 22L, 7L, true, false, true, false, false),
            usable, Outcome.REFUSED, Reason.REGISTRATION_SEQUENCE_MISMATCH);
        assertContract(
            boundary(SCOPE, 21L, 8L, true, false, true, false, false),
            usable, Outcome.REFUSED, Reason.PROPOSAL_GENERATION_MISMATCH);
        assertContract(
            boundary(SCOPE, 21L, 7L, true, false, true, false, true),
            usable, Outcome.REFUSED, Reason.REGION_OBJECT_BOUNDARY_MISSING);
        assertContract(
            boundary(SCOPE, 21L, 7L, true, false, true, true, false),
            usable, Outcome.REFUSED,
            Reason.TARGET_NOT_OBSERVED_INSIDE_REGION_BOUNDARY);
    }

    private static void targetRefusalRetainsItsExactReason() {
        GameTickEventRestorationTargetDecision refused = target(
            ObservedTargetState.MISMATCHED_OR_IDENTITYLESS_OCCUPANT);
        GameTickEventRestorationAtomicRevalidationContract contract =
            GameTickEventRestorationAtomicRevalidationContract.evaluate(
                completeBoundary(), refused);
        check(contract.isRefused()
            && contract.getReason() == Reason.TARGET_DECISION_REFUSED
            && contract.getTargetOutcome()
                == GameTickEventRestorationTargetDecision.Outcome.REFUSED
            && contract.getTargetReason()
                == GameTickEventRestorationTargetDecision.Reason
                    .MISMATCHED_OR_IDENTITYLESS_OCCUPANT,
            "target refusal must remain exact and fail closed");
    }

    private static void invalidDeclarationsRefuseConstruction() {
        expectIllegal(() -> boundary(
            "", 21L, 7L, true, false, true, true, true));
        expectIllegal(() -> boundary(
            SCOPE, 0L, 7L, true, false, true, true, true));
        expectIllegal(() -> boundary(
            SCOPE, 21L, 0L, true, false, true, true, true));
        expectNull(() -> GameTickEventRestorationAtomicRevalidationContract
            .evaluate(null,
                target(ObservedTargetState.EXACT_AUTHORED_TRANSIENT_PRESENT)));
        expectNull(() -> GameTickEventRestorationAtomicRevalidationContract
            .evaluate(completeBoundary(), null));
    }

    private static void assertContract(
            BoundaryDeclaration boundary,
            GameTickEventRestorationTargetDecision target,
            Outcome outcome, Reason reason) {
        GameTickEventRestorationAtomicRevalidationContract contract =
            GameTickEventRestorationAtomicRevalidationContract.evaluate(
                boundary, target);
        check(contract.getOutcome() == outcome
            && contract.getReason() == reason
            && contract.isRefused() == (outcome == Outcome.REFUSED)
            && contract.isNoOpContractSatisfied()
                == (outcome == Outcome.NO_OP_CONTRACT_SATISFIED)
            && contract.isMutationPreconditionContractSatisfied()
                == (outcome
                    == Outcome.MUTATION_PRECONDITION_CONTRACT_SATISFIED)
            && contract.isDormantContract()
            && contract.isEventExecutionBoundaryRequired()
            && contract.isRegionObjectBoundaryRequired()
            && contract.isSchedulerStoreBoundaryForbidden()
            && contract.isTargetRevalidationRequired()
            && !contract.isRuntimeRevalidationPerformed()
            && !contract.isAtomicityClaimed()
            && !contract.isEntityHandleRetained()
            && !contract.isMutationAuthorized()
            && !contract.isMutationPerformed()
            && !contract.isExecutableRestoration()
            && !contract.isCommitToken()
            && !contract.isArrivalGate()
            && !contract.isLifecycleAuthority(),
            "contract outcome or inert boundary disagrees");
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected refusal.
        }
    }

    private static void expectNull(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected refusal.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
    }
}
'''


class LayeredMapsSliceOneHundredEighteenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-eighteen-"
        )
        cls.classes = Path(cls.compile_temp.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.compile_temp.name) / (
            "src/com/openrsc/server/event/rsc/"
            "AtomicRevalidationContractFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(STATE), str(REQUIREMENT), str(DECISION),
                str(CONTRACT), str(fixture),
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
                "AtomicRevalidationContractFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_contract_has_no_runtime_lock_lookup_or_mutation_capability(self):
        source = CONTRACT.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.model",
            "import com.openrsc.server.net",
            "synchronized (", "GameTickEvent event", "World world",
            "Region region", "GameObject object", "getGameObject",
            "registerGameObject",
            "unregisterGameObject", "sendUpdatePackets", ".doRun()",
            ".stop()", "Lock ",
        ):
            self.assertNotIn(forbidden, source)
        for required in (
            "isRuntimeRevalidationPerformed() { return false; }",
            "isAtomicityClaimed() { return false; }",
            "isMutationAuthorized() { return false; }",
            "isMutationPerformed() { return false; }",
            "isCommitToken() { return false; }",
        ):
            self.assertIn(required, source)

    def test_contract_requires_safe_outer_to_inner_boundary_order(self):
        source = CONTRACT.read_text(encoding="utf-8")
        execution = source.index("isEventExecutionBoundaryHeld()")
        store = source.index("isSchedulerStoreBoundaryHeld()", execution)
        registration = source.index(
            "isRegistrationValidatedBeforeRegionBoundary()", store
        )
        region = source.index("isRegionObjectBoundaryHeld()", registration)
        target = source.index("isTargetObservedInsideRegionBoundary()", region)
        self.assertLess(execution, store)
        self.assertLess(store, registration)
        self.assertLess(registration, region)
        self.assertLess(region, target)

    def test_living_plan_records_slice_one_hundred_eighteen_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 118: Dormant atomic revalidation contract", plan
        )
        self.assertIn("scheduler-store lock", plan)
        self.assertIn("no atomicity claim", plan)


if __name__ == "__main__":
    unittest.main()
