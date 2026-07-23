#!/usr/bin/env python3
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EVENT_ROOT = ROOT / "server/src/com/openrsc/server/event/rsc"
EVENT = EVENT_ROOT / "GameTickEvent.java"
STATE = EVENT_ROOT / "GameTickEventRestorationState.java"
AFFINITY = EVENT_ROOT / "GameTickEventSpatialAffinity.java"
SNAPSHOT = EVENT_ROOT / (
    "GameTickEventRestorationCurrentStateRecoverySnapshot.java"
)
REQUEST = EVENT_ROOT / "GameTickEventRestorationCommitRequest.java"
ONE_SHOT = EVENT_ROOT / (
    "GameTickEventRestorationOneShotConsumptionContract.java"
)
BATCH = EVENT_ROOT / "GameTickEventRestorationRecoveryBatchContract.java"
COORDINATOR_CONTRACT = EVENT_ROOT / (
    "GameTickEventRestorationRecoveryCoordinatorContract.java"
)
HANDLER_ROOT = EVENT_ROOT / "handler"
STORE = HANDLER_ROOT / "GameTickEventStore.java"
FUTURE_APPLICATION = HANDLER_ROOT / (
    "GameTickEventRestorationFutureStateApplicationCoordinator.java"
)
EXECUTOR = HANDLER_ROOT / (
    "GameTickEventRestorationRecoveryDirectiveExecutor.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SHARED = runpy.run_path(str(ROOT / (
    "tests/myworld/"
    "test-layered-maps-slice-one-hundred-twenty-two.py"
)))


REGION_MANAGER_STUB = r'''
package com.openrsc.server.model.world.region;

import com.openrsc.server.event.rsc.GameTickEventRestorationCommitRequest;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTargetRevalidation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTargetRevalidationRequest;

public class RegionManager {
    public enum RestorationCommitOutcome { REFUSED, NO_OP, APPLIED }
    public enum RestorationCommitReason {
        FIXTURE_REFUSED, FIXTURE_NO_OP, FIXTURE_APPLIED
    }
    public static final class RestorationCommitResult {
        private final RestorationCommitOutcome outcome;
        private final RestorationCommitReason reason;
        private RestorationCommitResult(
                RestorationCommitOutcome outcome,
                RestorationCommitReason reason) {
            this.outcome = outcome;
            this.reason = reason;
        }
        public static RestorationCommitResult applied() {
            return new RestorationCommitResult(
                RestorationCommitOutcome.APPLIED,
                RestorationCommitReason.FIXTURE_APPLIED);
        }
        public static RestorationCommitResult noOp() {
            return new RestorationCommitResult(
                RestorationCommitOutcome.NO_OP,
                RestorationCommitReason.FIXTURE_NO_OP);
        }
        public static RestorationCommitResult refused() {
            return new RestorationCommitResult(
                RestorationCommitOutcome.REFUSED,
                RestorationCommitReason.FIXTURE_REFUSED);
        }
        public RestorationCommitOutcome getOutcome() { return outcome; }
        public RestorationCommitReason getReason() { return reason; }
        public boolean isMembershipRemoved() { return outcome == RestorationCommitOutcome.APPLIED; }
        public boolean isMembershipRegistered() { return outcome == RestorationCommitOutcome.APPLIED; }
        public int getBoundaryCount() { return outcome == RestorationCommitOutcome.REFUSED ? 0 : 1; }
    }

    public enum CurrentStateRecoveryApplicationOutcome {
        REFUSED, NO_OP, APPLIED
    }
    public enum CurrentStateRecoveryApplicationReason {
        FIXTURE_REFUSED, CURRENT_STATE_ALREADY_SATISFIED,
        CURRENT_STATE_RESTORED
    }
    public static final class CurrentStateRecoveryApplicationResult {
        private final CurrentStateRecoveryApplicationOutcome outcome;
        private final CurrentStateRecoveryApplicationReason reason;
        private CurrentStateRecoveryApplicationResult(
                CurrentStateRecoveryApplicationOutcome outcome,
                CurrentStateRecoveryApplicationReason reason) {
            this.outcome = outcome;
            this.reason = reason;
        }
        public static CurrentStateRecoveryApplicationResult applied() {
            return new CurrentStateRecoveryApplicationResult(
                CurrentStateRecoveryApplicationOutcome.APPLIED,
                CurrentStateRecoveryApplicationReason.CURRENT_STATE_RESTORED);
        }
        public static CurrentStateRecoveryApplicationResult noOp() {
            return new CurrentStateRecoveryApplicationResult(
                CurrentStateRecoveryApplicationOutcome.NO_OP,
                CurrentStateRecoveryApplicationReason
                    .CURRENT_STATE_ALREADY_SATISFIED);
        }
        public static CurrentStateRecoveryApplicationResult refused() {
            return new CurrentStateRecoveryApplicationResult(
                CurrentStateRecoveryApplicationOutcome.REFUSED,
                CurrentStateRecoveryApplicationReason.FIXTURE_REFUSED);
        }
        public CurrentStateRecoveryApplicationOutcome getOutcome() {
            return outcome;
        }
        public CurrentStateRecoveryApplicationReason getReason() {
            return reason;
        }
        public boolean isApplied() {
            return outcome == CurrentStateRecoveryApplicationOutcome.APPLIED;
        }
        public boolean isNoOp() {
            return outcome == CurrentStateRecoveryApplicationOutcome.NO_OP;
        }
        public boolean isRefused() {
            return outcome == CurrentStateRecoveryApplicationOutcome.REFUSED;
        }
        public boolean isMembershipRegistered() { return isApplied(); }
        public boolean isForceFullBlockProjectionSelected() { return false; }
        public int getBoundaryCount() { return isRefused() ? 0 : 1; }
    }

    private final RestorationCommitResult commitResult;
    private final CurrentStateRecoveryApplicationResult applicationResult;
    private int commitCalls;
    private int applicationCalls;

    public RegionManager(
            RestorationCommitResult commitResult,
            CurrentStateRecoveryApplicationResult applicationResult) {
        this.commitResult = commitResult;
        this.applicationResult = applicationResult;
    }
    public RestorationCommitResult applyGameTickEventRestorationCommitRequest(
            GameTickEventRestorationCommitRequest request) {
        commitCalls++;
        return commitResult;
    }
    public CurrentStateRecoveryApplicationResult
            applyGameTickEventCurrentStateRecoverySnapshot(
                GameTickEventRestorationCurrentStateRecoverySnapshot snapshot) {
        applicationCalls++;
        return applicationResult;
    }
    public GameTickEventRestorationTargetRevalidation
            captureGameTickEventRestorationTargetRevalidation(
                GameTickEventRestorationTargetRevalidationRequest request) {
        return new GameTickEventRestorationTargetRevalidation();
    }
    public int getCommitCalls() { return commitCalls; }
    public int getApplicationCalls() { return applicationCalls; }
}
'''


FIXTURE = r'''
package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot.CallbackExpectation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot.CollisionContribution;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot.CurrentScenery;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryBatchContract;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationRecoveryCoordinatorContract;
import com.openrsc.server.event.rsc.GameTickEventRestorationState;
import com.openrsc.server.event.rsc.GameTickEventRestorationState
    .AuthoredConstructionKind;
import com.openrsc.server.event.rsc.GameTickEventRestorationState
    .AuthoredPlacementState;
import com.openrsc.server.event.rsc.GameTickEventRestorationState.SceneryState;
import com.openrsc.server.event.rsc.GameTickEventSpatialAffinity;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.region.RegionManager;
import java.util.Collections;

public final class RecoveryDirectiveExecutorFixture {
    private static final class RestorableEvent extends GameTickEvent {
        private final GameTickEventRestorationState restoration;
        RestorableEvent(boolean overdue) {
            super(new World(), null, 100L, "directive-executor-fixture",
                DuplicationStrategy.ALLOW_MULTIPLE);
            restoration = GameTickEventRestorationState.scenerySpawn(
                SceneryState.of(
                    310, 310, 524, 489, 0, 0, null, 0,
                    AuthoredPlacementState.of(
                        7L, 10, 10, 22,
                        AuthoredConstructionKind.SCENERY)),
                true);
            if (overdue) {
                setDelayTicks(0L);
                resetCountdown();
            }
        }
        public void run() { }
        void changeLifecycleWithoutChangingDueState() { resetCountdown(); }
        @Override public GameTickEventRestorationState getRestorationState() {
            return restoration;
        }
        @Override public GameTickEventSpatialAffinity getSpatialAffinity() {
            return GameTickEventSpatialAffinity.exactFixedLocation(524, 489);
        }
    }

    public static void main(String[] args) {
        overdueDirectiveCommitsAndConsumes();
        futureDirectiveAppliesAndRetains();
        staleOverdueTimingRefusesBeforeRegion();
        mismatchedDirectiveInputsInvokeNothing();
    }

    private static void overdueDirectiveCommitsAndConsumes() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = registered(store, true);
        RegionManager region = region(
            RegionManager.RestorationCommitResult.applied(),
            RegionManager.CurrentStateRecoveryApplicationResult.refused());
        GameTickEventRestorationRecoveryCoordinatorContract.Preparation prep =
            preparation(store, event, null);
        GameTickEventRestorationRecoveryDirectiveExecutor.DirectiveExecution
            result = executor(store, region).execute(prep, 0, null);
        check(result.getReason()
                    == GameTickEventRestorationRecoveryDirectiveExecutor.Reason
                        .OVERDUE_OPERATION_COMPLETED
                && result.getOperationResult().getOutcome()
                    == GameTickEventRestorationRecoveryCoordinatorContract
                        .OperationOutcome
                            .DESIRED_STATE_APPLIED_AND_EVENT_CONSUMED
                && result.isRuntimeOperationInvoked()
                && result.isEventTerminallyConsumed()
                && !result.isEventRetainedScheduled()
                && result.isRegionMutationPerformed()
                && region.getCommitCalls() == 1
                && region.getApplicationCalls() == 0
                && !store.eventIsContained(event),
            "overdue directive routes only to desired-state consumption");
    }

    private static void futureDirectiveAppliesAndRetains() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = registered(store, false);
        GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
            snapshot(store, event);
        RegionManager region = region(
            RegionManager.RestorationCommitResult.refused(),
            RegionManager.CurrentStateRecoveryApplicationResult.applied());
        GameTickEventRestorationRecoveryCoordinatorContract.Preparation prep =
            preparation(store, event, snapshot);
        long ticks = event.captureAtomicTimingSnapshot().getTicksBeforeRun();
        GameTickEventRestorationRecoveryDirectiveExecutor.DirectiveExecution
            result = executor(store, region).execute(prep, 0, snapshot);
        check(result.getReason()
                    == GameTickEventRestorationRecoveryDirectiveExecutor.Reason
                        .FUTURE_OPERATION_COMPLETED
                && result.getOperationResult().getOutcome()
                    == GameTickEventRestorationRecoveryCoordinatorContract
                        .OperationOutcome
                            .CURRENT_STATE_RESTORED_AND_EVENT_RETAINED
                && result.isRuntimeOperationInvoked()
                && !result.isEventTerminallyConsumed()
                && result.isEventRetainedScheduled()
                && result.isRegionMutationPerformed()
                && region.getCommitCalls() == 0
                && region.getApplicationCalls() == 1
                && store.eventIsContained(event)
                && event.captureAtomicTimingSnapshot().getTicksBeforeRun()
                    == ticks
                && !result.isBatchLoop()
                && !result.isRetryPerformed()
                && !result.isRegionLoadingPerformed()
                && !result.isArrivalGate()
                && !result.isVisibilityReleased()
                && !result.isRuntimeHandleRetained()
                && !result.isLifecycleAuthority(),
            "future directive routes only to current-state application");
    }

    private static void staleOverdueTimingRefusesBeforeRegion() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = registered(store, true);
        GameTickEventRestorationRecoveryCoordinatorContract.Preparation prep =
            preparation(store, event, null);
        event.changeLifecycleWithoutChangingDueState();
        RegionManager region = region(
            RegionManager.RestorationCommitResult.applied(),
            RegionManager.CurrentStateRecoveryApplicationResult.applied());
        GameTickEventRestorationRecoveryDirectiveExecutor.DirectiveExecution
            result = executor(store, region).execute(prep, 0, null);
        check(result.getOperationResult().getOutcome()
                    == GameTickEventRestorationRecoveryCoordinatorContract
                        .OperationOutcome.REFUSED
                && !result.isRuntimeOperationInvoked()
                && !result.isEventTerminallyConsumed()
                && !result.isRegionMutationPerformed()
                && region.getCommitCalls() == 0
                && store.eventIsContained(event),
            "stale planned lifecycle refuses before overdue mutation");
    }

    private static void mismatchedDirectiveInputsInvokeNothing() {
        GameTickEventStore overdueStore = new GameTickEventStore();
        RestorableEvent overdue = registered(overdueStore, true);
        GameTickEventStore futureStore = new GameTickEventStore();
        RestorableEvent future = registered(futureStore, false);
        GameTickEventRestorationCurrentStateRecoverySnapshot futureSnapshot =
            snapshot(futureStore, future);
        RegionManager overdueRegion = region(
            RegionManager.RestorationCommitResult.applied(),
            RegionManager.CurrentStateRecoveryApplicationResult.applied());
        RegionManager futureRegion = region(
            RegionManager.RestorationCommitResult.applied(),
            RegionManager.CurrentStateRecoveryApplicationResult.applied());
        GameTickEventRestorationRecoveryDirectiveExecutor.DirectiveExecution
            extra = executor(overdueStore, overdueRegion).execute(
                preparation(overdueStore, overdue, null), 0, futureSnapshot);
        GameTickEventRestorationRecoveryDirectiveExecutor.DirectiveExecution
            missing = executor(futureStore, futureRegion).execute(
                preparation(futureStore, future, futureSnapshot), 0, null);
        check(extra.getOperationResult().getOutcome()
                    == GameTickEventRestorationRecoveryCoordinatorContract
                        .OperationOutcome.REFUSED
                && missing.getOperationResult().getOutcome()
                    == GameTickEventRestorationRecoveryCoordinatorContract
                        .OperationOutcome.REFUSED
                && !extra.isRuntimeOperationInvoked()
                && !missing.isRuntimeOperationInvoked()
                && overdueRegion.getCommitCalls() == 0
                && futureRegion.getApplicationCalls() == 0,
            "operation-kind input mismatch refuses before either path");
    }

    private static GameTickEventRestorationRecoveryCoordinatorContract
            .Preparation preparation(
                GameTickEventStore store,
                RestorableEvent event,
                GameTickEventRestorationCurrentStateRecoverySnapshot snapshot) {
        GameTickEvent.AtomicTimingSnapshot timing =
            event.captureAtomicTimingSnapshot();
        GameTickEventRestorationRecoveryBatchContract.Candidate candidate =
            GameTickEventRestorationRecoveryBatchContract.Candidate.declare(
                scope(store), sequenceOf(store, event), 7L,
                timing.getLifecycleVersion(), timing.getTicksBeforeRun(),
                timing.getTimesRan(), timing.isRunning(), true, true);
        GameTickEventRestorationRecoveryBatchContract.Plan plan =
            GameTickEventRestorationRecoveryBatchContract.plan(
                scope(store), 1L, Collections.singletonList(candidate),
                1, true, true);
        return GameTickEventRestorationRecoveryCoordinatorContract.prepare(
            plan, snapshot == null
                ? Collections
                    .<GameTickEventRestorationCurrentStateRecoverySnapshot>
                        emptyList()
                : Collections.singletonList(snapshot),
            1);
    }

    private static GameTickEventRestorationCurrentStateRecoverySnapshot
            snapshot(GameTickEventStore store, RestorableEvent event) {
        GameTickEvent.AtomicTimingSnapshot timing =
            event.captureAtomicTimingSnapshot();
        CallbackExpectation callback = CallbackExpectation.declare(
            GameTickEventRestorationCurrentStateRecoverySnapshot.CallbackKind
                .SCENERY_SPAWN,
            scope(store), sequenceOf(store, event), 7L,
            timing.getLifecycleVersion(), timing.getTicksBeforeRun(),
            timing.getTimesRan(), timing.isRunning(), true, true,
            310, 310, 524, 489, 0, 0, null, 0, 7L, 10, 10, 22,
            GameTickEventRestorationCurrentStateRecoverySnapshot
                .AuthoredConstructionKind.SCENERY);
        CurrentScenery current = CurrentScenery.declare(
            GameTickEventRestorationCurrentStateRecoverySnapshot
                .ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
            311, 311, 524, 489, 0, 0, null, 0, 7L, 10, 10, 22,
            GameTickEventRestorationCurrentStateRecoverySnapshot
                .AuthoredConstructionKind.SCENERY,
            1, true, true, true, true, true,
            Collections.<CollisionContribution>emptyList());
        GameTickEventRestorationCurrentStateRecoverySnapshot.Creation creation =
            GameTickEventRestorationCurrentStateRecoverySnapshot.assess(
                callback, current);
        check(creation.isSnapshotAvailable(), "future snapshot available");
        return creation.getSnapshot();
    }
    private static RestorableEvent registered(
            GameTickEventStore store, boolean overdue) {
        RestorableEvent event = new RestorableEvent(overdue);
        check(store.add(event), "event registered");
        return event;
    }
    private static RegionManager region(
            RegionManager.RestorationCommitResult commit,
            RegionManager.CurrentStateRecoveryApplicationResult application) {
        return new RegionManager(commit, application);
    }
    private static GameTickEventRestorationRecoveryDirectiveExecutor executor(
            GameTickEventStore store, RegionManager region) {
        return new GameTickEventRestorationRecoveryDirectiveExecutor(
            store, region);
    }
    private static String scope(GameTickEventStore store) {
        return store.getTrackedEventRegistrationSnapshot()
            .getSchedulerInstanceIdentity();
    }
    private static long sequenceOf(
            GameTickEventStore store, GameTickEvent expected) {
        for (GameTickEventStore.RegisteredEvent registration
                : store.getTrackedEventRegistrations()) {
            if (registration.getEvent() == expected) {
                return registration.getRegistrationSequence();
            }
        }
        throw new AssertionError("registration not found");
    }
    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
'''


class LayeredMapsSliceOneHundredFiftyTwoTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-recovery-directive-executor-"
        )
        cls.temp = Path(cls.temp_dir.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        sources = {
            "com/openrsc/server/model/entity/Mob.java": SHARED["MOB_STUB"],
            "com/openrsc/server/model/entity/player/Player.java":
                SHARED["PLAYER_STUB"],
            "com/openrsc/server/model/entity/npc/Npc.java":
                SHARED["NPC_STUB"],
            "com/openrsc/server/Server.java": SHARED["SERVER_STUB"],
            "com/openrsc/server/model/world/World.java":
                SHARED["WORLD_STUB"],
            "com/openrsc/server/model/world/region/RegionManager.java":
                REGION_MANAGER_STUB,
            "com/openrsc/server/event/rsc/PluginTickEvent.java":
                SHARED["PLUGIN_STUB"],
            "com/openrsc/server/event/rsc/"
            "GameTickEventRestorationTargetDecision.java":
                SHARED["TARGET_DECISION_STUB"],
            "com/openrsc/server/event/rsc/"
            "GameTickEventRestorationTargetRevalidationRequest.java":
                SHARED["TARGET_REVALIDATION_REQUEST_STUB"],
            "com/openrsc/server/event/rsc/"
            "GameTickEventRestorationTargetRevalidation.java":
                SHARED["TARGET_REVALIDATION_STUB"],
            "com/openrsc/server/event/rsc/handler/"
            "RecoveryDirectiveExecutorFixture.java": FIXTURE,
        }
        paths = []
        for relative, source in sources.items():
            path = cls.temp / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding="utf-8")
            paths.append(path)
        classpath = ":".join(str(path) for path in (
            ROOT / "server/lib/guava-30.1.1-jre.jar",
            ROOT / "server/lib/guice-5.0.2-jar-with-dependencies.jar",
            ROOT / "server/lib/commons-lang3-3.12.0.jar",
            ROOT / "server/lib/log4j-api-2.17.0.jar",
        ))
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-cp", classpath, "-d", str(cls.classes),
                str(STORE), str(FUTURE_APPLICATION), str(EXECUTOR),
                str(EVENT), str(STATE), str(AFFINITY), str(SNAPSHOT),
                str(REQUEST), str(ONE_SHOT), str(BATCH),
                str(COORDINATOR_CONTRACT), str(EVENT_ROOT / (
                    "DuplicationStrategy.java"
                )),
                *(str(path) for path in paths),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        if result.returncode != 0:
            raise AssertionError(result.stderr)
        cls.classpath = classpath + ":" + str(cls.classes)

    @classmethod
    def tearDownClass(cls):
        cls.temp_dir.cleanup()

    def test_directive_executor_fixture_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", self.classpath,
                "com.openrsc.server.event.rsc.handler."
                "RecoveryDirectiveExecutorFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_executor_is_single_step_and_disconnected_from_arrival(self):
        source = EXECUTOR.read_text(encoding="utf-8")
        store = STORE.read_text(encoding="utf-8")
        self.assertIn("DIRECTIVE_TIMING_MISMATCH", store)
        self.assertIn(
            "withValidatedRestorationRegionCommitConsumption", source
        )
        self.assertIn("futureApplication.apply(snapshot)", source)
        self.assertNotIn("while (", source)
        self.assertNotIn("WorldLoader", source)
        self.assertNotIn("assess(", source)
        for required in (
            "isBatchLoop() { return false; }",
            "isRetryPerformed() { return false; }",
            "isRegionLoadingPerformed() { return false; }",
            "isArrivalGate() { return false; }",
            "isVisibilityReleased() { return false; }",
            "isRuntimeHandleRetained() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_plan_records_slice_one_hundred_fifty_two(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn("Slice 152", plan)
        self.assertIn("executable recovery directive", plan.lower())


if __name__ == "__main__":
    unittest.main()
