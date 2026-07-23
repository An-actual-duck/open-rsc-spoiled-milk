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
LIFECYCLE = HANDLER_ROOT / (
    "GameTickEventRestorationReconstructionLifecycleCoordinator.java"
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

public final class ReconstructionLifecycleCoordinatorFixture {
    private static final long GENERATION = 7L;

    private static final class RestorableEvent extends GameTickEvent {
        private final GameTickEventRestorationState restoration;
        RestorableEvent(int x) {
            super(new World(), null, 100L, "lifecycle-fixture",
                DuplicationStrategy.ALLOW_MULTIPLE);
            restoration = GameTickEventRestorationState.scenerySpawn(
                SceneryState.of(
                    310, 310, x, 489, 0, 0, null, 0,
                    AuthoredPlacementState.of(
                        GENERATION, 10, 10, 22,
                        AuthoredConstructionKind.SCENERY)),
                true);
            setDelayTicks(0L);
            resetCountdown();
        }
        public void run() { }
        @Override public GameTickEventRestorationState getRestorationState() {
            return restoration;
        }
        @Override public GameTickEventSpatialAffinity getSpatialAffinity() {
            return GameTickEventSpatialAffinity.exactFixedLocation(524, 489);
        }
    }

    public static void main(String[] args) {
        reconstructionCompletesBeforeOneSingleUseRecovery();
        reconstructionRefusalNeverStartsRecovery();
        invalidCaptureNeverStartsReconstruction();
    }

    private static void reconstructionCompletesBeforeOneSingleUseRecovery() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = registered(store);
        RegionManager region = region(
            RegionManager.RestorationCommitResult.applied());
        GameTickEventRestorationReconstructionLifecycleCoordinator coordinator =
            coordinator(store, region);
        GameTickEventRestorationReconstructionLifecycleCoordinator
            .CapturedRecovery captured = coordinator.captureBeforeReconstruction(
                preparation(store, event), Collections
                    .<GameTickEventRestorationCurrentStateRecoverySnapshot>
                        emptyList(), GENERATION, 1);
        final int[] reconstructionCalls = {0};
        GameTickEventRestorationReconstructionLifecycleCoordinator
            .LifecycleExecution result = coordinator.reconstructThenRecover(
                captured, boundary -> {
                    check(boundary.isRecoveryCapturedBeforeOperation()
                            && boundary.isFirstVisibilityWithheld()
                            && !boundary.isRegionLoadingPerformed()
                            && !boundary.isLoadPermit()
                            && !boundary.isVisibilityReleased(),
                        "reconstruction receives only the closed boundary");
                    reconstructionCalls[0]++;
                    return GameTickEventRestorationReconstructionLifecycleCoordinator
                        .ReconstructionExecution.completed(boundary);
                });
        GameTickEventRestorationReconstructionLifecycleCoordinator
            .LifecycleExecution repeated = coordinator.reconstructThenRecover(
                captured, boundary -> {
                    reconstructionCalls[0]++;
                    return GameTickEventRestorationReconstructionLifecycleCoordinator
                        .ReconstructionExecution.completed(boundary);
                });
        check(captured.isCaptured()
                && captured.isExecutionClaimed()
                && !captured.isReusable()
                && result.isReconstructionInvoked()
                && result.isRecoveryInvoked()
                && result.isContractuallyReadyForFirstVisibility()
                && result.getCompletedRecoveryPrefixCount() == 1
                && reconstructionCalls[0] == 1
                && region.getCommitCalls() == 1
                && !store.eventIsContained(event)
                && repeated.getReason()
                    == GameTickEventRestorationReconstructionLifecycleCoordinator
                        .LifecycleReason.CAPTURE_ALREADY_CONSUMED
                && !repeated.isReconstructionInvoked()
                && !repeated.isRecoveryInvoked()
                && !result.isRetryPerformed()
                && !result.isArrivalGate()
                && !result.isVisibilityReleased()
                && !result.isRuntimeHandleRetained(),
            "reconstruction precedes one single-use recovery batch");
    }

    private static void reconstructionRefusalNeverStartsRecovery() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = registered(store);
        RegionManager region = region(
            RegionManager.RestorationCommitResult.applied());
        GameTickEventRestorationReconstructionLifecycleCoordinator coordinator =
            coordinator(store, region);
        GameTickEventRestorationReconstructionLifecycleCoordinator
            .CapturedRecovery captured = coordinator.captureBeforeReconstruction(
                preparation(store, event), Collections
                    .<GameTickEventRestorationCurrentStateRecoverySnapshot>
                        emptyList(), GENERATION, 1);
        GameTickEventRestorationReconstructionLifecycleCoordinator
            .LifecycleExecution result = coordinator.reconstructThenRecover(
                captured, boundary ->
                    GameTickEventRestorationReconstructionLifecycleCoordinator
                        .ReconstructionExecution.refused(boundary));
        check(result.getReason()
                    == GameTickEventRestorationReconstructionLifecycleCoordinator
                        .LifecycleReason.RECONSTRUCTION_REFUSED
                && result.isReconstructionInvoked()
                && !result.isRecoveryInvoked()
                && region.getCommitCalls() == 0
                && store.eventIsContained(event),
            "reconstruction refusal leaves recovery and callback untouched");
    }

    private static void invalidCaptureNeverStartsReconstruction() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = registered(store);
        RegionManager region = region(
            RegionManager.RestorationCommitResult.applied());
        GameTickEventRestorationReconstructionLifecycleCoordinator coordinator =
            coordinator(store, region);
        GameTickEventRestorationReconstructionLifecycleCoordinator
            .CapturedRecovery captured = coordinator.captureBeforeReconstruction(
                preparation(store, event), Collections
                    .<GameTickEventRestorationCurrentStateRecoverySnapshot>
                        emptyList(), GENERATION + 1L, 1);
        final int[] reconstructionCalls = {0};
        GameTickEventRestorationReconstructionLifecycleCoordinator
            .LifecycleExecution result = coordinator.reconstructThenRecover(
                captured, boundary -> {
                    reconstructionCalls[0]++;
                    return GameTickEventRestorationReconstructionLifecycleCoordinator
                        .ReconstructionExecution.completed(boundary);
                });
        check(!captured.isCaptured()
                && captured.getCaptureReason()
                    == GameTickEventRestorationReconstructionLifecycleCoordinator
                        .CaptureReason.PROPOSAL_GENERATION_MISMATCH
                && result.getReason()
                    == GameTickEventRestorationReconstructionLifecycleCoordinator
                        .LifecycleReason.CAPTURE_REFUSED
                && reconstructionCalls[0] == 0
                && region.getCommitCalls() == 0
                && store.eventIsContained(event),
            "invalid pre-reconstruction capture invokes no operation");
    }

    private static GameTickEventRestorationRecoveryCoordinatorContract
            .Preparation preparation(
                GameTickEventStore store, RestorableEvent event) {
        GameTickEvent.AtomicTimingSnapshot timing =
            event.captureAtomicTimingSnapshot();
        GameTickEventRestorationRecoveryBatchContract.Candidate candidate =
            GameTickEventRestorationRecoveryBatchContract.Candidate.declare(
                scope(store), sequenceOf(store, event), GENERATION,
                timing.getLifecycleVersion(), timing.getTicksBeforeRun(),
                timing.getTimesRan(), timing.isRunning(), true, true);
        GameTickEventRestorationRecoveryBatchContract.Plan plan =
            GameTickEventRestorationRecoveryBatchContract.plan(
                scope(store), 1L, Collections.singletonList(candidate),
                1, true, true);
        return GameTickEventRestorationRecoveryCoordinatorContract.prepare(
            plan, Collections
                .<GameTickEventRestorationCurrentStateRecoverySnapshot>
                    emptyList(), 1);
    }
    private static RestorableEvent registered(GameTickEventStore store) {
        RestorableEvent event = new RestorableEvent(524);
        check(store.add(event), "event registered");
        return event;
    }
    private static RegionManager region(
            RegionManager.RestorationCommitResult commit) {
        return new RegionManager(
            commit,
            RegionManager.CurrentStateRecoveryApplicationResult.refused());
    }
    private static GameTickEventRestorationReconstructionLifecycleCoordinator
            coordinator(GameTickEventStore store, RegionManager region) {
        return new GameTickEventRestorationReconstructionLifecycleCoordinator(
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


class LayeredMapsSliceOneHundredFiftyFourTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-reconstruction-lifecycle-"
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
            "ReconstructionLifecycleCoordinatorFixture.java": FIXTURE,
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
                str(DIRECTIVE_EXECUTOR), str(BATCH_EXECUTOR), str(LIFECYCLE),
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

    def test_reconstruction_lifecycle_fixture_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", self.classpath,
                "com.openrsc.server.event.rsc.handler."
                "ReconstructionLifecycleCoordinatorFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_source_orders_reconstruction_before_recovery_without_visibility(self):
        source = LIFECYCLE.read_text(encoding="utf-8")
        reconstruction = source.index("operation.reconstruct(")
        recovery = source.index("batchExecutor.execute(", reconstruction)
        self.assertLess(reconstruction, recovery)
        self.assertIn("claimExecution()", source)
        self.assertIn("CAPTURE_ALREADY_CONSUMED", source)
        self.assertNotIn("WorldLoader", source)
        self.assertNotIn("Player", source)
        for required in (
            "isRetryPerformed() { return false; }",
            "isArrivalGate() { return false; }",
            "isVisibilityReleased() { return false; }",
            "isRuntimeHandleRetained() { return false; }",
        ):
            self.assertIn(required, source)

    def test_plan_records_slice_one_hundred_fifty_four(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn("Slice 154", plan)
        self.assertIn("reconstruction lifecycle coordinator", plan.lower())


if __name__ == "__main__":
    unittest.main()
