#!/usr/bin/env python3
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
HANDLER = ROOT / "server/src/com/openrsc/server/event/rsc/handler"
SCOPE = HANDLER / "GameTickEventNpcOwnerPreservationScope.java"
LIFECYCLE = HANDLER / "GameTickEventNpcOwnerPreservationLifecycle.java"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_169 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-sixty-nine.py"
)))


FIXTURE = r'''
package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.event.rsc.handler
    .GameTickEventNpcOwnerPreservationLifecycle.LifecycleRefusalReason;
import com.openrsc.server.event.rsc.handler
    .GameTickEventNpcOwnerPreservationLifecycle.PreservationEvidence;
import com.openrsc.server.event.rsc.handler
    .GameTickEventNpcOwnerPreservationLifecycle.Reason;
import com.openrsc.server.event.rsc.handler
    .GameTickEventNpcOwnerPreservationLifecycle.Result;
import com.openrsc.server.event.rsc.handler
    .GameTickEventNpcOwnerPreservationLifecycle.SourceLifecycleCompletion;
import com.openrsc.server.event.rsc.handler
    .GameTickEventNpcOwnerPreservationLifecycle.SourceLifecycleRequest;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionNpcOwnerPreservationRequirements;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NpcOwnerPreservationLifecycleFixture {
    public static void main(String[] args) throws Exception {
        completeLifecycleAdmitsOneEphemeralConsumer();
        everyIncompleteLifecycleRefusesTheConsumer();
        completionCannotCrossScopes();
        exceptionalPathsInvalidateCapabilities();
    }

    private static void completeLifecycleAdmitsOneEphemeralConsumer()
        throws Exception {
        GameTickEventNpcOwnerPreservationScope scope = scope();
        SourceLifecycleRequest[] leakedRequest =
            new SourceLifecycleRequest[1];
        PreservationEvidence[] leakedEvidence =
            new PreservationEvidence[1];
        AtomicBoolean otherThreadRefused = new AtomicBoolean();

        Result result = GameTickEventNpcOwnerPreservationLifecycle.execute(
            scope,
            request -> {
                leakedRequest[0] = request;
                check(request.getGeneration() == 9L
                        && request.getRequirementsObservedAtTick() == 12L
                        && request.getSchedulerInstanceIdentity()
                            .endsWith("0169")
                        && request.getRequiredEventLinkCount() == 3
                        && request.getRequiredOwnerCount() == 2
                        && request.getSelectedSources().size() == 2
                        && request.getSelectedSources().get(0)
                            .getPackedRegionX() == 4
                        && request.getSelectedSources().get(0)
                            .getPackedRegionY() == 7
                        && request.getSelectedSources().get(1)
                            .getPackedRegionX() == 5
                        && request.getSelectedSources().get(1)
                            .getPackedRegionY() == 7,
                    "source request lost exact scope identity");
                expectUnsupported(() ->
                    request.getSelectedSources().clear());
                return request.completed(2, 2, true, true);
            },
            evidence -> {
                leakedEvidence[0] = evidence;
                check(evidence.getGeneration() == 9L
                        && evidence.getSelectedSourceCount() == 2
                        && evidence.getRequiredEventLinkCount() == 3
                        && evidence.getRequiredOwnerCount() == 2
                        && evidence.isPreservationEstablishedForActiveScope()
                        && !evidence.isRuntimeHandleRetained()
                        && !evidence.isReusablePreservationFact(),
                    "consumer did not receive exact ephemeral evidence");
                Thread other = new Thread(() -> {
                    try {
                        evidence.getGeneration();
                    } catch (IllegalStateException expected) {
                        otherThreadRefused.set(true);
                    }
                }, "preservation-evidence-leak");
                other.start();
                try {
                    other.join(2000L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
                check(!other.isAlive() && otherThreadRefused.get(),
                    "preservation evidence crossed its boundary thread");
            });
        check(result.getReason() == Reason.PRESERVED_CONSUMER_COMPLETED
                && result.getGeneration() == 9L
                && result.getSelectedSourceCount() == 2
                && result.getRequiredEventLinkCount() == 3
                && result.getRequiredOwnerCount() == 2
                && result.getAbsentSourceCount() == 2
                && result.getReconstructedSourceCount() == 2
                && result.areAllSourcesRestoredBeforeReturn()
                && result.isFirstVisibilityWithheld()
                && result.getLifecycleRefusalReason() == null
                && result.isPreservedConsumerInvoked()
                && result.isPreservationEstablishedForConsumedWork()
                && !result.isReusablePreservationFact()
                && !result.isRuntimeHandleRetained()
                && !result.isArrivalGate()
                && !result.isVisibilityReleased(),
            "complete source lifecycle did not admit exactly one consumer");
        expectIllegalState(() -> leakedRequest[0].getGeneration());
        expectIllegalState(() -> leakedEvidence[0].getGeneration());
        check(scope.isCompleteBoundaryHeld(),
            "inner lifecycle must not invalidate the outer scope");
        scope.invalidate();
    }

    private static void everyIncompleteLifecycleRefusesTheConsumer() {
        check(refused(
                request -> request.refused(
                    LifecycleRefusalReason.ABSENCE_OPERATION_REFUSED))
                    .getReason() == Reason.SOURCE_LIFECYCLE_REFUSED,
            "typed lifecycle refusal was not preserved");
        check(refused(request -> request.completed(1, 2, true, true))
                    .getReason() == Reason.SOURCE_ABSENCE_INCOMPLETE,
            "partial source absence was accepted");
        check(refused(request -> request.completed(2, 1, true, true))
                    .getReason() == Reason.SOURCE_RECONSTRUCTION_INCOMPLETE,
            "partial source reconstruction was accepted");
        check(refused(request -> request.completed(2, 2, false, true))
                    .getReason() == Reason.SOURCES_NOT_RESTORED_BEFORE_RETURN,
            "unrestored sources were accepted");
        check(refused(request -> request.completed(2, 2, true, false))
                    .getReason() == Reason.FIRST_VISIBILITY_NOT_WITHHELD,
            "visible incomplete lifecycle was accepted");
    }

    private static Result refused(
        GameTickEventNpcOwnerPreservationLifecycle.SourceLifecycleOperation
            operation) {
        GameTickEventNpcOwnerPreservationScope scope = scope();
        AtomicBoolean consumerInvoked = new AtomicBoolean();
        Result result = GameTickEventNpcOwnerPreservationLifecycle.execute(
            scope, operation, evidence -> consumerInvoked.set(true));
        check(!consumerInvoked.get()
                && !result.isPreservedConsumerInvoked()
                && !result.isPreservationEstablishedForConsumedWork()
                && !result.isReusablePreservationFact(),
            "refused lifecycle invoked preserved work");
        scope.invalidate();
        return result;
    }

    private static void completionCannotCrossScopes() {
        SourceLifecycleCompletion[] foreign =
            new SourceLifecycleCompletion[1];
        GameTickEventNpcOwnerPreservationScope first = scope();
        Result firstResult =
            GameTickEventNpcOwnerPreservationLifecycle.execute(
                first,
                request -> {
                    foreign[0] = request.completed(2, 2, true, true);
                    return request.refused(
                        LifecycleRefusalReason.SOURCE_SET_UNAVAILABLE);
                },
                evidence -> {
                    throw new AssertionError("refused first consumer ran");
                });
        check(firstResult.getReason() == Reason.SOURCE_LIFECYCLE_REFUSED,
            "first scoped completion setup failed");
        first.invalidate();

        GameTickEventNpcOwnerPreservationScope second = scope();
        AtomicBoolean consumerInvoked = new AtomicBoolean();
        Result secondResult =
            GameTickEventNpcOwnerPreservationLifecycle.execute(
                second, request -> foreign[0],
                evidence -> consumerInvoked.set(true));
        check(secondResult.getReason() == Reason.COMPLETION_SCOPE_MISMATCH
                && !consumerInvoked.get(),
            "completion from another scope was accepted");
        second.invalidate();
    }

    private static void exceptionalPathsInvalidateCapabilities() {
        GameTickEventNpcOwnerPreservationScope lifecycleScope = scope();
        SourceLifecycleRequest[] leakedRequest =
            new SourceLifecycleRequest[1];
        try {
            GameTickEventNpcOwnerPreservationLifecycle.execute(
                lifecycleScope,
                request -> {
                    leakedRequest[0] = request;
                    throw new IllegalStateException("fixture lifecycle failure");
                },
                evidence -> {
                    throw new AssertionError("failed lifecycle consumer ran");
                });
            throw new AssertionError("expected lifecycle failure");
        } catch (IllegalStateException expected) {
            check("fixture lifecycle failure".equals(expected.getMessage()),
                "wrong lifecycle failure propagated");
        }
        expectIllegalState(() -> leakedRequest[0].getGeneration());
        lifecycleScope.invalidate();

        GameTickEventNpcOwnerPreservationScope consumerScope = scope();
        PreservationEvidence[] leakedEvidence =
            new PreservationEvidence[1];
        try {
            GameTickEventNpcOwnerPreservationLifecycle.execute(
                consumerScope,
                request -> request.completed(2, 2, true, true),
                evidence -> {
                    leakedEvidence[0] = evidence;
                    throw new IllegalStateException("fixture consumer failure");
                });
            throw new AssertionError("expected consumer failure");
        } catch (IllegalStateException expected) {
            check("fixture consumer failure".equals(expected.getMessage()),
                "wrong consumer failure propagated");
        }
        expectIllegalState(() -> leakedEvidence[0].getGeneration());
        consumerScope.invalidate();
    }

    private static GameTickEventNpcOwnerPreservationScope scope() {
        return GameTickEventNpcOwnerPreservationScope.open(
            new LayeredPackedRegionNpcOwnerPreservationRequirements(true),
            3, 2, true, true);
    }

    private static void expectIllegalState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected.
        }
    }

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
'''


class LayeredMapsSliceOneHundredSeventyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-npc-owner-preservation-lifecycle-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        requirements_stub = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LayeredPackedRegionNpcOwnerPreservationRequirements.java"
        )
        fixture = cls.temp / (
            "src/com/openrsc/server/event/rsc/handler/"
            "NpcOwnerPreservationLifecycleFixture.java"
        )
        requirements_stub.parent.mkdir(parents=True, exist_ok=True)
        fixture.parent.mkdir(parents=True, exist_ok=True)
        requirements_stub.write_text(
            SLICE_169["REQUIREMENTS_STUB"], encoding="utf-8"
        )
        fixture.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(requirements_stub), str(SCOPE), str(LIFECYCLE),
                str(fixture),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_complete_lifecycle_admits_only_ephemeral_preserved_work(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.event.rsc.handler."
                "NpcOwnerPreservationLifecycleFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_contract_is_disconnected_and_handle_free(self):
        source = LIFECYCLE.read_text(encoding="utf-8")
        scope = SCOPE.read_text(encoding="utf-8")

        self.assertIn("COMPLETION_SCOPE_MISMATCH", source)
        self.assertIn("SOURCE_ABSENCE_INCOMPLETE", source)
        self.assertIn("SOURCE_RECONSTRUCTION_INCOMPLETE", source)
        self.assertIn("SOURCES_NOT_RESTORED_BEFORE_RETURN", source)
        self.assertIn("FIRST_VISIBILITY_NOT_WITHHELD", source)
        self.assertIn("PRESERVED_CONSUMER_COMPLETED", source)
        self.assertIn("completion.request != request", source)
        self.assertIn("evidence.invalidate()", source)
        self.assertIn("request.invalidateIfActive()", source)
        self.assertIn("isReusablePreservationFact() { return false; }", source)
        for forbidden in (
            "import com.openrsc.server.model.entity",
            "import com.openrsc.server.model.world.region",
            "GameEventHandler",
            "RegionManager",
            "registerNpc(",
            "unregisterNpc(",
            "event.stop()",
        ):
            self.assertNotIn(forbidden, source)
        self.assertNotIn(
            "GameTickEventNpcOwnerPreservationLifecycle", scope
        )

    def test_outer_runtime_scope_has_no_production_consumer_yet(self):
        consumers = []
        for path in (ROOT / "server/src").rglob("*.java"):
            if path == LIFECYCLE:
                continue
            if "GameTickEventNpcOwnerPreservationLifecycle" in path.read_text(
                encoding="utf-8"
            ):
                consumers.append(path.relative_to(ROOT).as_posix())
        self.assertEqual([], consumers)

    def test_living_plan_records_slice_one_hundred_seventy(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 170: Ephemeral preserved source lifecycle",
            plan,
        )
        self.assertIn("PRESERVED_CONSUMER_COMPLETED", plan)
        self.assertIn("completion cannot cross scopes", plan)


if __name__ == "__main__":
    unittest.main()
