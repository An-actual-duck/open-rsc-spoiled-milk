#!/usr/bin/env python3
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EVENT_ROOT = ROOT / "server/src/com/openrsc/server/event/rsc"
HANDLER_ROOT = EVENT_ROOT / "handler"
COORDINATE_ROOT = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate"
)
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
INVENTORY = COORDINATE_ROOT / (
    "LayeredPackedRegionEventOwnershipInventory.java"
)
STORE = HANDLER_ROOT / "GameTickEventStore.java"
CURRENT_CAPTURE = HANDLER_ROOT / (
    "GameTickEventRestorationCurrentStateCaptureCoordinator.java"
)
LIVE_PREPARATION = HANDLER_ROOT / (
    "GameTickEventRestorationLivePreparationCoordinator.java"
)
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
LIVE_RECONSTRUCTION = HANDLER_ROOT / (
    "GameTickEventRestorationLiveReconstructionCoordinator.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SHARED = runpy.run_path(str(ROOT / (
    "tests/myworld/"
    "test-layered-maps-slice-one-hundred-twenty-two.py"
)))
SHARED_150 = runpy.run_path(str(ROOT / (
    "tests/myworld/"
    "test-layered-maps-slice-one-hundred-fifty.py"
)))


APPLICATION_STUB = r'''
    public enum CurrentStateRecoveryApplicationOutcome {
        REFUSED, NO_OP, APPLIED
    }
    public enum CurrentStateRecoveryApplicationReason {
        CURRENT_STATE_RESTORED
    }
    public static final class CurrentStateRecoveryApplicationResult {
        public CurrentStateRecoveryApplicationOutcome getOutcome() {
            return CurrentStateRecoveryApplicationOutcome.APPLIED;
        }
        public CurrentStateRecoveryApplicationReason getReason() {
            return CurrentStateRecoveryApplicationReason
                .CURRENT_STATE_RESTORED;
        }
        public boolean isApplied() { return true; }
        public boolean isNoOp() { return false; }
        public boolean isMembershipRegistered() { return true; }
        public boolean isForceFullBlockProjectionSelected() { return false; }
        public int getBoundaryCount() { return 1; }
    }
    private int applicationCalls;
    public CurrentStateRecoveryApplicationResult
            applyGameTickEventCurrentStateRecoverySnapshot(
                GameTickEventRestorationCurrentStateRecoverySnapshot snapshot) {
        applicationCalls++;
        return new CurrentStateRecoveryApplicationResult();
    }
    public int getApplicationCalls() { return applicationCalls; }
'''
REGION_MANAGER_STUB = (
    SHARED_150["REGION_MANAGER_STUB"].rsplit("}", 1)[0]
    + APPLICATION_STUB + "}\n"
)


FIXTURE = r'''
package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.GameTickEventRestorationState;
import com.openrsc.server.event.rsc.GameTickEventRestorationState
    .AuthoredPlacementState;
import com.openrsc.server.event.rsc.GameTickEventRestorationState.SceneryState;
import com.openrsc.server.event.rsc.GameTickEventSpatialAffinity;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.AttributionKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.EventRestorationState;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.EventState;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.ExecutionSemantics;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.OwnerKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.PackedSource;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.SceneryRestorationState;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.SpatialReference;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.SpatialRole;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.TimeProgressionPolicy;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory
        .AuthoredConstructionKind;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory
        .AuthoredPlacementRestorationState;
import com.openrsc.server.model.world.region.RegionManager;
import java.util.Collections;

public final class LiveReconstructionCoordinatorFixture {
    private static final long GENERATION = 7L;

    private static final class RestorableEvent extends GameTickEvent {
        private final GameTickEventRestorationState restoration;
        RestorableEvent() {
            super(new World(), null, 100L, "live-reconstruction-fixture",
                DuplicationStrategy.ALLOW_MULTIPLE);
            restoration = GameTickEventRestorationState.scenerySpawn(
                SceneryState.of(
                    310, 310, 524, 489, 0, 0, null, 0,
                    AuthoredPlacementState.of(
                        GENERATION, 10, 10, 22,
                        com.openrsc.server.event.rsc
                            .GameTickEventRestorationState
                            .AuthoredConstructionKind.SCENERY)),
                true);
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
        exactLiveCaptureFlowsThroughReconstructionAndRecovery();
        incompleteLiveCaptureNeverInvokesReconstruction();
        reconstructionRefusalNeverInvokesRecovery();
    }

    private static void exactLiveCaptureFlowsThroughReconstructionAndRecovery() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = registered(store);
        RegionManager region = new RegionManager(true);
        GameTickEventRestorationLiveReconstructionCoordinator coordinator =
            new GameTickEventRestorationLiveReconstructionCoordinator(
                store, region);
        GameTickEventRestorationLiveReconstructionCoordinator
            .LiveCapturedRecovery captured =
                coordinator.captureBeforeReconstruction(
                    inventory(store, event, true), 1);
        final int[] reconstructionCalls = {0};
        GameTickEventRestorationLiveReconstructionCoordinator
            .LiveLifecycleExecution result = coordinator.reconstructThenRecover(
                captured, boundary -> {
                    reconstructionCalls[0]++;
                    return GameTickEventRestorationReconstructionLifecycleCoordinator
                        .ReconstructionExecution.completed(boundary);
                });
        check(captured.isCaptured()
                && captured.getInventoryEventCount() == 1
                && captured.getRecoveryCandidateCount() == 1
                && captured.getFutureSnapshotCount() == 1
                && captured.getProposalGeneration() == GENERATION
                && !captured.isInputSubstitutionAvailable()
                && reconstructionCalls[0] == 1
                && result.isReconstructionInvoked()
                && result.isRecoveryInvoked()
                && result.isContractuallyReadyForFirstVisibility()
                && !result.requiresFreshInventoryRetry()
                && region.getCaptureCalls() == 1
                && region.getApplicationCalls() == 1
                && store.eventIsContained(event)
                && !result.isRetryPerformed()
                && !result.isArrivalGate()
                && !result.isVisibilityReleased()
                && !result.isRuntimeHandleRetained(),
            "exact live capture reaches recovery without input substitution");
    }

    private static void incompleteLiveCaptureNeverInvokesReconstruction() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = registered(store);
        RegionManager region = new RegionManager(true);
        GameTickEventRestorationLiveReconstructionCoordinator coordinator =
            new GameTickEventRestorationLiveReconstructionCoordinator(
                store, region);
        GameTickEventRestorationLiveReconstructionCoordinator
            .LiveCapturedRecovery captured =
                coordinator.captureBeforeReconstruction(
                    inventory(store, event, false), 1);
        final int[] reconstructionCalls = {0};
        GameTickEventRestorationLiveReconstructionCoordinator
            .LiveLifecycleExecution result = coordinator.reconstructThenRecover(
                captured, boundary -> {
                    reconstructionCalls[0]++;
                    return GameTickEventRestorationReconstructionLifecycleCoordinator
                        .ReconstructionExecution.completed(boundary);
                });
        check(!captured.isCaptured()
                && captured.getReason()
                    == GameTickEventRestorationLiveReconstructionCoordinator
                        .CaptureReason.LIVE_PREPARATION_REFUSED
                && result.getReason()
                    == GameTickEventRestorationLiveReconstructionCoordinator
                        .ExecutionReason.LIVE_CAPTURE_REFUSED
                && reconstructionCalls[0] == 0
                && region.getCaptureCalls() == 0
                && region.getApplicationCalls() == 0
                && store.eventIsContained(event),
            "refused live capture reaches neither later phase");
    }

    private static void reconstructionRefusalNeverInvokesRecovery() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = registered(store);
        RegionManager region = new RegionManager(true);
        GameTickEventRestorationLiveReconstructionCoordinator coordinator =
            new GameTickEventRestorationLiveReconstructionCoordinator(
                store, region);
        GameTickEventRestorationLiveReconstructionCoordinator
            .LiveCapturedRecovery captured =
                coordinator.captureBeforeReconstruction(
                    inventory(store, event, true), 1);
        GameTickEventRestorationLiveReconstructionCoordinator
            .LiveLifecycleExecution result = coordinator.reconstructThenRecover(
                captured, boundary ->
                    GameTickEventRestorationReconstructionLifecycleCoordinator
                        .ReconstructionExecution.refused(boundary));
        check(result.isReconstructionInvoked()
                && !result.isRecoveryInvoked()
                && !result.isContractuallyReadyForFirstVisibility()
                && region.getCaptureCalls() == 1
                && region.getApplicationCalls() == 0
                && store.eventIsContained(event),
            "reconstruction refusal preserves captured state without recovery");
    }

    private static LayeredPackedRegionEventOwnershipInventory inventory(
            GameTickEventStore store,
            RestorableEvent event,
            boolean completeRestoration) {
        GameTickEvent.AtomicTimingSnapshot timing =
            event.captureAtomicTimingSnapshot();
        EventRestorationState restoration = completeRestoration
            ? EventRestorationState.scenerySpawn(
                SceneryRestorationState.of(
                    310, 310, 524, 489, 0, 0, null, 0,
                    AuthoredPlacementRestorationState.of(
                        GENERATION, 10, 10, 22,
                        AuthoredConstructionKind.SCENERY)),
                true, ExecutionSemantics.ONE_SHOT,
                TimeProgressionPolicy.CONTINUE_SERVER_TICKS)
            : EventRestorationState.unavailable();
        EventState state = EventState.of(
            0, sequenceOf(store, event), OwnerKind.NONE,
            AttributionKind.EXACT_SPATIAL, timing.isRunning(),
            timing.getTicksBeforeRun(), timing.getTimesRan(),
            Collections.singletonList(SpatialReference.of(
                SpatialRole.FIXED_EFFECT_LOCATION, 524, 489)),
            restoration, completeRestoration);
        return LayeredPackedRegionEventOwnershipInventory.inventory(
            GENERATION, 1L, scope(store),
            Collections.singletonList(PackedSource.of(10, 10)),
            Collections.singletonList(state), 1, 1, 1);
    }
    private static RestorableEvent registered(GameTickEventStore store) {
        RestorableEvent event = new RestorableEvent();
        check(store.add(event), "event registered");
        return event;
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


class LayeredMapsSliceOneHundredFiftySixTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-live-reconstruction-coordinator-"
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
            "com/openrsc/server/model/world/coordinate/"
            "LayeredPackedRegionRetirementRefinementProposal.java": r'''
package com.openrsc.server.model.world.coordinate;
public final class LayeredPackedRegionRetirementRefinementProposal {
    public static final int MAXIMUM_CANDIDATE_SOURCES = 8192;
}
''',
            "com/openrsc/server/model/world/coordinate/WorldRegionKey.java": r'''
package com.openrsc.server.model.world.coordinate;
public final class WorldRegionKey {
    public static final int REGION_SIZE = 48;
}
''',
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
            "LiveReconstructionCoordinatorFixture.java": FIXTURE,
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
                str(STORE), str(CURRENT_CAPTURE), str(LIVE_PREPARATION),
                str(FUTURE_APPLICATION), str(DIRECTIVE_EXECUTOR),
                str(BATCH_EXECUTOR), str(LIFECYCLE),
                str(LIVE_RECONSTRUCTION), str(EVENT), str(STATE),
                str(AFFINITY), str(SNAPSHOT), str(REQUEST), str(ONE_SHOT),
                str(BATCH), str(COORDINATOR_CONTRACT), str(INVENTORY),
                str(EVENT_ROOT / "DuplicationStrategy.java"),
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

    def test_live_reconstruction_fixture_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", self.classpath,
                "com.openrsc.server.event.rsc.handler."
                "LiveReconstructionCoordinatorFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_composition_prevents_input_substitution_and_visibility(self):
        source = LIVE_RECONSTRUCTION.read_text(encoding="utf-8")
        preparation = source.index("preparation.capture(")
        lifecycle_capture = source.index(
            "lifecycle.captureBeforeReconstruction(", preparation
        )
        lifecycle_execute = source.index(
            "lifecycle.reconstructThenRecover(", lifecycle_capture
        )
        self.assertLess(preparation, lifecycle_capture)
        self.assertLess(lifecycle_capture, lifecycle_execute)
        self.assertIn("isInputSubstitutionAvailable() { return false; }", source)
        self.assertNotIn("WorldLoader", source)
        self.assertNotIn("Player", source)
        for required in (
            "isRetryPerformed() { return false; }",
            "isArrivalGate() { return false; }",
            "isVisibilityReleased() { return false; }",
            "isRuntimeHandleRetained() { return false; }",
        ):
            self.assertIn(required, source)

    def test_plan_records_slice_one_hundred_fifty_six(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn("Slice 156", plan)
        self.assertIn("live reconstruction composition", plan.lower())


if __name__ == "__main__":
    unittest.main()
