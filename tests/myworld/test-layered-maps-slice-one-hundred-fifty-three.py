#!/usr/bin/env python3
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EVENT_ROOT = ROOT / "server/src/com/openrsc/server/event/rsc"
HANDLER_ROOT = EVENT_ROOT / "handler"
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
STORE = HANDLER_ROOT / "GameTickEventStore.java"
FUTURE_APPLICATION = HANDLER_ROOT / (
    "GameTickEventRestorationFutureStateApplicationCoordinator.java"
)
DIRECTIVE_EXECUTOR = HANDLER_ROOT / (
    "GameTickEventRestorationRecoveryDirectiveExecutor.java"
)
BATCH_EXECUTOR = HANDLER_ROOT / (
    "GameTickEventRestorationRecoveryBatchExecutor.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SHARED = runpy.run_path(str(ROOT / (
    "tests/myworld/"
    "test-layered-maps-slice-one-hundred-twenty-two.py"
)))
SHARED_152 = runpy.run_path(str(ROOT / (
    "tests/myworld/"
    "test-layered-maps-slice-one-hundred-fifty-two.py"
)))


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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class RecoveryBatchExecutorFixture {
    private static final class RestorableEvent extends GameTickEvent {
        private final GameTickEventRestorationState restoration;
        RestorableEvent(boolean overdue, int x) {
            super(new World(), null, 100L, "batch-executor-fixture",
                DuplicationStrategy.ALLOW_MULTIPLE);
            restoration = GameTickEventRestorationState.scenerySpawn(
                SceneryState.of(
                    310, 310, x, 489, 0, 0, null, 0,
                    AuthoredPlacementState.of(
                        7L, 10, 10, x - 500,
                        AuthoredConstructionKind.SCENERY)),
                true);
            if (overdue) {
                setDelayTicks(0L);
                resetCountdown();
            }
        }
        public void run() { }
        @Override public GameTickEventRestorationState getRestorationState() {
            return restoration;
        }
        @Override public GameTickEventSpatialAffinity getSpatialAffinity() {
            SceneryState scenery = restoration.getScenery();
            return GameTickEventSpatialAffinity.exactFixedLocation(
                scenery.getX(), scenery.getY());
        }
    }

    public static void main(String[] args) {
        mixedBatchCompletesInPreparedOrder();
        firstRefusalStopsTheRemainingSuffix();
        futureSnapshotSetMustMatchPreparationExactly();
    }

    private static void mixedBatchCompletesInPreparedOrder() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent overdue = registered(store, true, 22);
        RestorableEvent future = registered(store, false, 23);
        GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
            snapshot(store, future, 23);
        RegionManager region = new RegionManager(
            RegionManager.RestorationCommitResult.applied(),
            RegionManager.CurrentStateRecoveryApplicationResult.applied());
        GameTickEventRestorationRecoveryBatchExecutor.BatchExecution result =
            executor(store, region).execute(
                preparation(store, overdue, future, snapshot),
                Collections.singletonList(snapshot), 2);
        check(result.isContractuallyReadyForFirstVisibility()
                && !result.requiresFreshInventoryRetry()
                && result.getCompletedPrefixCount() == 2
                && result.getDirectiveCount() == 2
                && result.getOperationResults().size() == 2
                && result.getRuntimeOperationCount() == 2
                && result.getOperationResults().get(0).getOutcome()
                    == GameTickEventRestorationRecoveryCoordinatorContract
                        .OperationOutcome
                            .DESIRED_STATE_APPLIED_AND_EVENT_CONSUMED
                && result.getOperationResults().get(1).getOutcome()
                    == GameTickEventRestorationRecoveryCoordinatorContract
                        .OperationOutcome
                            .CURRENT_STATE_RESTORED_AND_EVENT_RETAINED
                && !store.eventIsContained(overdue)
                && store.eventIsContained(future)
                && region.getCommitCalls() == 1
                && region.getApplicationCalls() == 1
                && !result.isRetryPerformed()
                && !result.isRegionLoadingPerformed()
                && !result.isArrivalGate()
                && !result.isVisibilityReleased()
                && !result.isRuntimeHandleRetained()
                && !result.isLifecycleAuthority(),
            "mixed batch completes exact overdue/future order: reason="
                + result.getReason()
                + ", prefix=" + result.getCompletedPrefixCount()
                + ", results=" + result.getOperationResults().size()
                + ", operations=" + result.getRuntimeOperationCount()
                + ", overdueContained=" + store.eventIsContained(overdue)
                + ", futureContained=" + store.eventIsContained(future)
                + ", commits=" + region.getCommitCalls()
                + ", applications=" + region.getApplicationCalls()
                + ", outcomes=" + result.getOperationResults());
    }

    private static void firstRefusalStopsTheRemainingSuffix() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent overdue = registered(store, true, 22);
        RestorableEvent future = registered(store, false, 23);
        GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
            snapshot(store, future, 23);
        RegionManager region = new RegionManager(
            RegionManager.RestorationCommitResult.refused(),
            RegionManager.CurrentStateRecoveryApplicationResult.applied());
        GameTickEventRestorationRecoveryBatchExecutor.BatchExecution result =
            executor(store, region).execute(
                preparation(store, overdue, future, snapshot),
                Collections.singletonList(snapshot), 2);
        check(!result.isContractuallyReadyForFirstVisibility()
                && result.requiresFreshInventoryRetry()
                && result.getCompletedPrefixCount() == 0
                && result.getOperationResults().size() == 1
                && result.getRuntimeOperationCount() == 1
                && result.getRefusedRegistrationSequence()
                    == sequenceOf(store, overdue)
                && result.getOperationResults().get(0).getOutcome()
                    == GameTickEventRestorationRecoveryCoordinatorContract
                        .OperationOutcome.REFUSED
                && store.eventIsContained(overdue)
                && store.eventIsContained(future)
                && region.getCommitCalls() == 1
                && region.getApplicationCalls() == 0,
            "first refusal returns prefix and never executes suffix");
    }

    private static void futureSnapshotSetMustMatchPreparationExactly() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent overdue = registered(store, true, 22);
        RestorableEvent future = registered(store, false, 23);
        GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
            snapshot(store, future, 23);
        RegionManager region = new RegionManager(
            RegionManager.RestorationCommitResult.applied(),
            RegionManager.CurrentStateRecoveryApplicationResult.applied());
        GameTickEventRestorationRecoveryCoordinatorContract.Preparation prep =
            preparation(store, overdue, future, snapshot);
        GameTickEventRestorationRecoveryBatchExecutor.BatchExecution missing =
            executor(store, region).execute(
                prep, Collections
                    .<GameTickEventRestorationCurrentStateRecoverySnapshot>
                        emptyList(), 2);
        GameTickEventRestorationRecoveryBatchExecutor.BatchExecution duplicate =
            executor(store, region).execute(
                prep, Arrays.asList(snapshot, snapshot), 2);
        check(missing.getReason()
                    == GameTickEventRestorationRecoveryBatchExecutor.Reason
                        .FUTURE_SNAPSHOT_SET_MISMATCH
                && duplicate.getReason()
                    == GameTickEventRestorationRecoveryBatchExecutor.Reason
                        .DUPLICATE_FUTURE_SNAPSHOT
                && missing.getOperationResults().isEmpty()
                && duplicate.getOperationResults().isEmpty()
                && region.getCommitCalls() == 0
                && region.getApplicationCalls() == 0,
            "snapshot set refuses closed before first directive");
    }

    private static GameTickEventRestorationRecoveryCoordinatorContract
            .Preparation preparation(
                GameTickEventStore store,
                RestorableEvent overdue,
                RestorableEvent future,
                GameTickEventRestorationCurrentStateRecoverySnapshot snapshot) {
        List<GameTickEventRestorationRecoveryBatchContract.Candidate> candidates =
            Arrays.asList(candidate(store, overdue), candidate(store, future));
        GameTickEventRestorationRecoveryBatchContract.Plan plan =
            GameTickEventRestorationRecoveryBatchContract.plan(
                scope(store), 1L, candidates, 2, true, true);
        return GameTickEventRestorationRecoveryCoordinatorContract.prepare(
            plan, Collections.singletonList(snapshot), 2);
    }
    private static GameTickEventRestorationRecoveryBatchContract.Candidate
            candidate(GameTickEventStore store, RestorableEvent event) {
        GameTickEvent.AtomicTimingSnapshot timing =
            event.captureAtomicTimingSnapshot();
        return GameTickEventRestorationRecoveryBatchContract.Candidate.declare(
            scope(store), sequenceOf(store, event), 7L,
            timing.getLifecycleVersion(), timing.getTicksBeforeRun(),
            timing.getTimesRan(), timing.isRunning(), true, true);
    }
    private static GameTickEventRestorationCurrentStateRecoverySnapshot
            snapshot(
                GameTickEventStore store, RestorableEvent event, int ordinal) {
        GameTickEvent.AtomicTimingSnapshot timing =
            event.captureAtomicTimingSnapshot();
        SceneryState scenery = event.getRestorationState().getScenery();
        CallbackExpectation callback = CallbackExpectation.declare(
            GameTickEventRestorationCurrentStateRecoverySnapshot.CallbackKind
                .SCENERY_SPAWN,
            scope(store), sequenceOf(store, event), 7L,
            timing.getLifecycleVersion(), timing.getTicksBeforeRun(),
            timing.getTimesRan(), timing.isRunning(), true, true,
            scenery.getObjectId(), scenery.getPermanentObjectId(),
            scenery.getX(), scenery.getY(), scenery.getDirection(),
            scenery.getType(), null, 0, 7L, 10, 10, ordinal,
            GameTickEventRestorationCurrentStateRecoverySnapshot
                .AuthoredConstructionKind.SCENERY);
        CurrentScenery current = CurrentScenery.declare(
            GameTickEventRestorationCurrentStateRecoverySnapshot
                .ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
            311, 311, scenery.getX(), scenery.getY(), 0, 0, null, 0,
            7L, 10, 10, ordinal,
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
            GameTickEventStore store, boolean overdue, int ordinal) {
        RestorableEvent event = new RestorableEvent(overdue, 500 + ordinal);
        check(store.add(event), "event registered");
        return event;
    }
    private static GameTickEventRestorationRecoveryBatchExecutor executor(
            GameTickEventStore store, RegionManager region) {
        return new GameTickEventRestorationRecoveryBatchExecutor(store, region);
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


class LayeredMapsSliceOneHundredFiftyThreeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-recovery-batch-executor-"
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
                SHARED_152["REGION_MANAGER_STUB"],
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
            "RecoveryBatchExecutorFixture.java": FIXTURE,
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
                str(STORE), str(FUTURE_APPLICATION),
                str(DIRECTIVE_EXECUTOR), str(BATCH_EXECUTOR),
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

    def test_batch_executor_fixture_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", self.classpath,
                "com.openrsc.server.event.rsc.handler."
                "RecoveryBatchExecutorFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_batch_executor_stops_on_refusal_without_visibility(self):
        source = BATCH_EXECUTOR.read_text(encoding="utf-8")
        self.assertIn("for (int index = 0;", source)
        self.assertIn("OperationOutcome.REFUSED", source)
        self.assertIn("break;", source)
        self.assertIn(".assess(", source)
        self.assertNotIn("WorldLoader", source)
        for required in (
            "isRetryPerformed() { return false; }",
            "isRegionLoadingPerformed() { return false; }",
            "isArrivalGate() { return false; }",
            "isVisibilityReleased() { return false; }",
            "isRuntimeHandleRetained() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_plan_records_slice_one_hundred_fifty_three(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn("Slice 153", plan)
        self.assertIn("bounded recovery batch runner", plan.lower())


if __name__ == "__main__":
    unittest.main()
