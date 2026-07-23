#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CONTRACT = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationRecoveryBatchContract.java"
)
COORDINATOR = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationRecoveryCoordinatorContract.java"
)
BATCH_EXECUTOR = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventRestorationRecoveryBatchExecutor.java"
)
STORE = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventStore.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.event.rsc;

import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryBatchContract.Candidate;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryBatchContract.Outcome;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryBatchContract.Plan;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryBatchContract.Progress;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryBatchContract.ProgressReason;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryBatchContract.Reason;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryBatchContract.StepAction;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryBatchContract.StepOutcome;
import java.util.Arrays;
import java.util.Collections;

public final class RestorationRecoveryBatchFixture {
    public static void main(String[] args) {
        overdueAndFutureCandidatesHaveDistinctActions();
        futureStateRequiresASeparateRecoveryCapability();
        identityBoundsAndSemanticsRefuse();
        orderedProgressIsMonotonicAndFailClosed();
    }

    private static void overdueAndFutureCandidatesHaveDistinctActions() {
        Plan plan = plan(true, candidate(9L, 4L), candidate(3L, 0L));
        check(plan.isAccepted() && plan.getSteps().size() == 2
                && plan.getSteps().get(0).getCandidate()
                    .getRegistrationSequence() == 3L
                && plan.getSteps().get(0).getAction()
                    == StepAction.COMMIT_DESIRED_STATE_AND_CONSUME
                && plan.getSteps().get(1).getCandidate()
                    .getRegistrationSequence() == 9L
                && plan.getSteps().get(1).getAction()
                    == StepAction.RESTORE_CURRENT_STATE_AND_RETAIN_SCHEDULED,
            "batch order and countdown actions are deterministic");
        check(!plan.isRegionInvocation()
                && !plan.isCallbackInvoked()
                && !plan.isEventCancellation()
                && !plan.isEventReschedule()
                && !plan.isLoadingAuthority()
                && !plan.isArrivalGate()
                && !plan.isLifecycleAuthority(),
            "accepted policy remains inert");
    }

    private static void futureStateRequiresASeparateRecoveryCapability() {
        Plan refused = GameTickEventRestorationRecoveryBatchContract.plan(
            "scope", 100L, Collections.singletonList(candidate(1L, 5L)),
            10, true, false);
        check(!refused.isAccepted()
                && refused.getReason()
                    == Reason.FUTURE_CURRENT_STATE_RECOVERY_UNAVAILABLE,
            "future callback cannot be committed early as desired state");
        Plan overdue = GameTickEventRestorationRecoveryBatchContract.plan(
            "scope", 100L, Collections.singletonList(candidate(1L, -1L)),
            10, true, false);
        check(overdue.isAccepted()
                && overdue.getSteps().get(0).getAction()
                    == StepAction.COMMIT_DESIRED_STATE_AND_CONSUME,
            "overdue desired-state recovery needs no transient capability");
    }

    private static void identityBoundsAndSemanticsRefuse() {
        Plan visible = GameTickEventRestorationRecoveryBatchContract.plan(
            "scope", 100L, Collections.singletonList(candidate(1L, 0L)),
            10, false, false);
        check(visible.getReason() == Reason.FIRST_VISIBILITY_NOT_WITHHELD,
            "recovery cannot begin after visibility");
        Plan duplicate = GameTickEventRestorationRecoveryBatchContract.plan(
            "scope", 100L,
            Arrays.asList(candidate(1L, 0L), candidate(1L, -1L)),
            10, true, false);
        check(duplicate.getReason() == Reason.DUPLICATE_REGISTRATION,
            "duplicate exact registration refuses");
        Candidate wrongScope = Candidate.declare(
            "other", 2L, 7L, 5L, 0L, 0, true, true, true);
        Plan mismatched = GameTickEventRestorationRecoveryBatchContract.plan(
            "scope", 100L, Collections.singletonList(wrongScope),
            10, true, false);
        check(mismatched.getReason() == Reason.SCHEDULER_INSTANCE_MISMATCH,
            "cross-scheduler registration refuses");
        Candidate recurring = Candidate.declare(
            "scope", 2L, 7L, 5L, 0L, 0, true, false, true);
        Plan semantics = GameTickEventRestorationRecoveryBatchContract.plan(
            "scope", 100L, Collections.singletonList(recurring),
            10, true, false);
        check(semantics.getReason() == Reason.EVENT_SEMANTICS_REFUSED,
            "non-one-shot semantics refuse");
    }

    private static void orderedProgressIsMonotonicAndFailClosed() {
        Plan plan = plan(true, candidate(2L, 0L), candidate(5L, 3L));
        Progress pending = GameTickEventRestorationRecoveryBatchContract
            .assessProgress(plan, Collections.singletonList(
                StepOutcome.report(2L, Outcome.APPLIED)));
        check(pending.getReason() == ProgressReason.BATCH_PENDING
                && pending.getCompletedPrefixCount() == 1
                && !pending.isReadyForFirstVisibility(),
            "successful prefix remains withheld while work remains");
        Progress refused = GameTickEventRestorationRecoveryBatchContract
            .assessProgress(plan, Arrays.asList(
                StepOutcome.report(2L, Outcome.NO_OP),
                StepOutcome.report(5L, Outcome.REFUSED)));
        check(refused.requiresFreshInventoryRetry()
                && refused.getCompletedPrefixCount() == 1
                && refused.getRefusedRegistrationSequence() == 5L
                && !refused.isReadyForFirstVisibility(),
            "refusal preserves completed prefix and requires fresh retry");
        Progress ready = GameTickEventRestorationRecoveryBatchContract
            .assessProgress(plan, Arrays.asList(
                StepOutcome.report(2L, Outcome.NO_OP),
                StepOutcome.report(5L, Outcome.CURRENT_STATE_RESTORED)));
        check(ready.isReadyForFirstVisibility()
                && ready.getCompletedPrefixCount() == 2,
            "only a complete compatible prefix permits visibility");
        Progress wrong = GameTickEventRestorationRecoveryBatchContract
            .assessProgress(plan, Arrays.asList(
                StepOutcome.report(2L, Outcome.APPLIED),
                StepOutcome.report(5L, Outcome.APPLIED)));
        check(wrong.getReason() == ProgressReason.OUTCOME_ACTION_MISMATCH,
            "desired-state apply cannot substitute for future transient state");
    }

    private static Plan plan(
            boolean currentStateRecoveryAvailable,
            Candidate... candidates) {
        return GameTickEventRestorationRecoveryBatchContract.plan(
            "scope", 100L, Arrays.asList(candidates), 10, true,
            currentStateRecoveryAvailable);
    }

    private static Candidate candidate(long sequence, long ticksBeforeRun) {
        return Candidate.declare(
            "scope", sequence, 7L, 5L, ticksBeforeRun,
            0, true, true, true);
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
'''


class LayeredMapsSliceOneHundredFortyThreeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-restoration-recovery-batch-"
        )
        cls.classes = Path(cls.temp_dir.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.temp_dir.name) / (
            "src/com/openrsc/server/event/rsc/"
            "RestorationRecoveryBatchFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
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
        cls.temp_dir.cleanup()

    def test_recovery_batch_contract_fixture(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.event.rsc."
                "RestorationRecoveryBatchFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_contract_has_no_runtime_or_invocation_capability(self):
        source = CONTRACT.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.model",
            "import com.openrsc.server.event.rsc.handler",
            "GameTickEvent event", "RegionManager", "GameObject",
            "synchronized (", ".run()", ".stop()", "unregisterAccepted",
            "applyGameTickEventRestorationCommitRequest",
        ):
            self.assertNotIn(forbidden, source)
        for required in (
            "isRuntimeHandleRetained() { return false; }",
            "isRegionInvocation() { return false; }",
            "isCallbackInvoked() { return false; }",
            "isEventCancellation() { return false; }",
            "isEventReschedule() { return false; }",
            "isLoadingAuthority() { return false; }",
            "isArrivalGate() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_runtime_consumers_remain_disconnected(self):
        name = "GameTickEventRestorationRecoveryBatchContract"
        self.assertNotIn(name, STORE.read_text(encoding="utf-8"))
        for path in (ROOT / "server/src").rglob("*.java"):
            if path in (CONTRACT, COORDINATOR, BATCH_EXECUTOR):
                continue
            self.assertNotIn(name, path.read_text(encoding="utf-8"))

    def test_living_plan_records_slice_one_hundred_forty_three(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 143: Recovery batch and timing policy", plan
        )
        normalized = " ".join(plan.split())
        self.assertIn("future transient state", normalized)
        self.assertIn("fresh inventory retry", normalized)


if __name__ == "__main__":
    unittest.main()
