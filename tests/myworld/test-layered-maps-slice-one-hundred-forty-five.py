#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EVENT_DIR = ROOT / "server/src/com/openrsc/server/event/rsc"
STATE = EVENT_DIR / "GameTickEventRestorationState.java"
CURRENT = EVENT_DIR / (
    "GameTickEventRestorationCurrentStateRecoverySnapshot.java"
)
BATCH = EVENT_DIR / "GameTickEventRestorationRecoveryBatchContract.java"
COORDINATOR = EVENT_DIR / (
    "GameTickEventRestorationRecoveryCoordinatorContract.java"
)
DIRECTIVE_EXECUTOR = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventRestorationRecoveryDirectiveExecutor.java"
)
BATCH_EXECUTOR = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventRestorationRecoveryBatchExecutor.java"
)
LIFECYCLE_COORDINATOR = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventRestorationReconstructionLifecycleCoordinator.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.event.rsc;

import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot
        .AuthoredConstructionKind;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot.CallbackExpectation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot.CallbackKind;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot.Creation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot.CurrentScenery;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot
        .ObservedCurrentState;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryBatchContract.Candidate;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryBatchContract.Plan;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryCoordinatorContract.Completion;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryCoordinatorContract.CompletionReason;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryCoordinatorContract.Directive;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryCoordinatorContract.OperationKind;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryCoordinatorContract.OperationOutcome;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryCoordinatorContract.OperationResult;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryCoordinatorContract.Preparation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryCoordinatorContract.Reason;
import java.util.Arrays;
import java.util.Collections;

public final class RestorationRecoveryCoordinatorFixture {
    public static void main(String[] args) {
        mixedBatchMapsToExactlyOneOperationPerStep();
        futureSnapshotCorrelationFailsClosed();
        typedProgressPreservesPrefixRetryAndReadiness();
        emptyBatchIsACompleteNonAuthoritativeContract();
    }

    private static void mixedBatchMapsToExactlyOneOperationPerStep() {
        Preparation preparation = preparation();
        check(preparation.isReady()
                && preparation.getDirectives().size() == 2,
            "mixed bounded batch prepares");
        Directive overdue = preparation.getDirectives().get(0);
        Directive future = preparation.getDirectives().get(1);
        check(overdue.getRegistrationSequence() == 3L
                && overdue.getOperationKind()
                    == OperationKind.DESIRED_STATE_COMMIT_AND_EVENT_CONSUME
                && !overdue.isFutureSnapshotCorrelated()
                && future.getRegistrationSequence() == 9L
                && future.getOperationKind()
                    == OperationKind.CURRENT_STATE_RESTORE_AND_EVENT_RETAIN
                && future.isFutureSnapshotCorrelated(),
            "each sorted step maps to exactly one action");
        check(!preparation.isRuntimeHandleRetained()
                && !preparation.isOperationInvoked()
                && !preparation.isRegionLoadingPerformed()
                && !preparation.isArrivalGate()
                && !preparation.isVisibilityReleased()
                && !preparation.isLifecycleAuthority(),
            "preparation remains disconnected");
    }

    private static void futureSnapshotCorrelationFailsClosed() {
        Plan plan = mixedPlan();
        Preparation missing =
            GameTickEventRestorationRecoveryCoordinatorContract.prepare(
                plan,
                Collections
                    .<GameTickEventRestorationCurrentStateRecoverySnapshot>
                        emptyList(),
                10);
        check(missing.getReason() == Reason.FUTURE_SNAPSHOT_MISSING,
            "future step requires its snapshot");

        GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
            snapshot(9L, 4L, 5L);
        Preparation duplicate =
            GameTickEventRestorationRecoveryCoordinatorContract.prepare(
                plan, Arrays.asList(snapshot, snapshot), 10);
        check(duplicate.getReason()
                == Reason.DUPLICATE_FUTURE_SNAPSHOT_REGISTRATION,
            "duplicate snapshot registration refuses");

        Preparation stale =
            GameTickEventRestorationRecoveryCoordinatorContract.prepare(
                plan, Collections.singletonList(snapshot(9L, 4L, 6L)), 10);
        check(stale.getReason()
                == Reason.FUTURE_SNAPSHOT_CORRELATION_MISMATCH,
            "lifecycle mismatch refuses");

        Preparation overdueSnapshot =
            GameTickEventRestorationRecoveryCoordinatorContract.prepare(
                plan, Arrays.asList(snapshot(3L, 4L, 5L), snapshot), 10);
        check(overdueSnapshot.getReason()
                == Reason.OVERDUE_STEP_HAS_FUTURE_SNAPSHOT,
            "overdue work cannot consume a future-state snapshot");
    }

    private static void typedProgressPreservesPrefixRetryAndReadiness() {
        Preparation preparation = preparation();
        OperationResult overdue = result(
            3L, OperationKind.DESIRED_STATE_COMMIT_AND_EVENT_CONSUME,
            OperationOutcome.DESIRED_STATE_APPLIED_AND_EVENT_CONSUMED);
        Completion pending =
            GameTickEventRestorationRecoveryCoordinatorContract.assess(
                preparation, Collections.singletonList(overdue));
        check(pending.getReason() == CompletionReason.BATCH_PENDING
                && pending.getCompletedPrefixCount() == 1
                && !pending.isReadyForFirstVisibilityContract(),
            "successful prefix remains pending");

        Completion refused =
            GameTickEventRestorationRecoveryCoordinatorContract.assess(
                preparation, Arrays.asList(overdue, result(
                    9L,
                    OperationKind.CURRENT_STATE_RESTORE_AND_EVENT_RETAIN,
                    OperationOutcome.REFUSED)));
        check(refused.requiresFreshInventoryRetry()
                && refused.getCompletedPrefixCount() == 1
                && refused.getRefusedRegistrationSequence() == 9L,
            "refusal keeps prefix and requires fresh inventory");

        Completion ready =
            GameTickEventRestorationRecoveryCoordinatorContract.assess(
                preparation, Arrays.asList(overdue, result(
                    9L,
                    OperationKind.CURRENT_STATE_RESTORE_AND_EVENT_RETAIN,
                    OperationOutcome
                        .CURRENT_STATE_RESTORED_AND_EVENT_RETAINED)));
        check(ready.isReadyForFirstVisibilityContract()
                && ready.getCompletedPrefixCount() == 2
                && !ready.isVisibilityReleased()
                && !ready.isOperationInvoked()
                && !ready.isLifecycleAuthority(),
            "complete typed prefix is contractual readiness only");

        Completion wrongType =
            GameTickEventRestorationRecoveryCoordinatorContract.assess(
                preparation, Arrays.asList(overdue, result(
                    9L,
                    OperationKind.CURRENT_STATE_RESTORE_AND_EVENT_RETAIN,
                    OperationOutcome.DESIRED_STATE_APPLIED_AND_EVENT_CONSUMED)));
        check(wrongType.getReason()
                == CompletionReason.RESULT_OUTCOME_MISMATCH,
            "desired-state result cannot satisfy future-state work");
    }

    private static void emptyBatchIsACompleteNonAuthoritativeContract() {
        Plan empty = GameTickEventRestorationRecoveryBatchContract.plan(
            "scope", 100L, Collections.<Candidate>emptyList(),
            10, true, true);
        Preparation preparation =
            GameTickEventRestorationRecoveryCoordinatorContract.prepare(
                empty,
                Collections
                    .<GameTickEventRestorationCurrentStateRecoverySnapshot>
                        emptyList(),
                10);
        Completion completion =
            GameTickEventRestorationRecoveryCoordinatorContract.assess(
                preparation, Collections.<OperationResult>emptyList());
        check(completion.isReadyForFirstVisibilityContract()
                && !completion.isVisibilityReleased()
                && !completion.isArrivalGate(),
            "empty complete batch remains only a readiness contract");
    }

    private static Preparation preparation() {
        return GameTickEventRestorationRecoveryCoordinatorContract.prepare(
            mixedPlan(), Collections.singletonList(snapshot(9L, 4L, 5L)), 10);
    }

    private static Plan mixedPlan() {
        return GameTickEventRestorationRecoveryBatchContract.plan(
            "scope", 100L,
            Arrays.asList(candidate(9L, 4L), candidate(3L, 0L)),
            10, true, true);
    }

    private static Candidate candidate(long registration, long ticks) {
        return Candidate.declare(
            "scope", registration, 7L, 5L, ticks,
            0, true, true, true);
    }

    private static GameTickEventRestorationCurrentStateRecoverySnapshot
            snapshot(long registration, long ticks, long lifecycle) {
        CallbackExpectation callback = CallbackExpectation.declare(
            CallbackKind.SCENERY_SPAWN, "scope", registration,
            7L, lifecycle, ticks, 0, true, true, true,
            320, 320, 90, 90, 0, 0, null, 0,
            7L, 1, 1, 5,
            AuthoredConstructionKind.HARVESTING_SCENERY);
        CurrentScenery current = CurrentScenery.declare(
            ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
            321, 320, 90, 90, 0, 0, null, 0,
            7L, 1, 1, 5,
            AuthoredConstructionKind.HARVESTING_SCENERY,
            1, true, true, true, true, true,
            Collections
                .<GameTickEventRestorationCurrentStateRecoverySnapshot
                    .CollisionContribution>emptyList());
        Creation creation =
            GameTickEventRestorationCurrentStateRecoverySnapshot.assess(
                callback, current);
        check(creation.isSnapshotAvailable(), "fixture snapshot available");
        return creation.getSnapshot();
    }

    private static OperationResult result(
            long registration, OperationKind operation,
            OperationOutcome outcome) {
        return OperationResult.report(registration, operation, outcome);
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
'''


class LayeredMapsSliceOneHundredFortyFiveTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-restoration-coordinator-"
        )
        cls.classes = Path(cls.temp_dir.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.temp_dir.name) / (
            "src/com/openrsc/server/event/rsc/"
            "RestorationRecoveryCoordinatorFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(STATE), str(CURRENT), str(BATCH), str(COORDINATOR),
                str(fixture),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        if result.returncode != 0:
            raise AssertionError(result.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.temp_dir.cleanup()

    def test_recovery_coordinator_fixture(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.event.rsc."
                "RestorationRecoveryCoordinatorFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_coordinator_has_no_runtime_or_operation_capability(self):
        source = COORDINATOR.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.model",
            "import com.openrsc.server.event.rsc.handler",
            "GameTickEvent event", "RegionManager", "GameObject object",
            "World world", "Region region", "synchronized (",
            ".doRun()", ".run()", ".stop()", "unregisterAccepted",
            "applyGameTickEventRestorationCommitRequest",
        ):
            self.assertNotIn(forbidden, source)
        for required in (
            "isRuntimeHandleRetained() { return false; }",
            "isOperationInvoked() { return false; }",
            "isRegionLoadingPerformed() { return false; }",
            "isArrivalGate() { return false; }",
            "isVisibilityReleased() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_runtime_consumers_remain_disconnected(self):
        name = "GameTickEventRestorationRecoveryCoordinatorContract"
        for path in (ROOT / "server/src").rglob("*.java"):
            if path in (
                COORDINATOR, DIRECTIVE_EXECUTOR, BATCH_EXECUTOR,
                LIFECYCLE_COORDINATOR,
            ):
                continue
            self.assertNotIn(name, path.read_text(encoding="utf-8"))

    def test_living_plan_records_slice_one_hundred_forty_five(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 145: Detached recovery coordinator contract", plan
        )
        normalized = " ".join(plan.split())
        self.assertIn("exactly one typed operation", normalized)
        self.assertIn("fixture-supplied typed outcomes", normalized)


if __name__ == "__main__":
    unittest.main()
