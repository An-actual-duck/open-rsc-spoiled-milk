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
import java.util.Arrays;
import java.util.Collections;

public final class LiveRecoveryPreparationFixture {
    private static final long GENERATION = 7L;

    private static final class RestorableEvent extends GameTickEvent {
        private final GameTickEventRestorationState restoration;
        RestorableEvent(boolean overdue) {
            super(new World(), null, 100L, "live-preparation-fixture",
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
            return GameTickEventSpatialAffinity.exactFixedLocation(524, 489);
        }
    }

    public static void main(String[] args) {
        futureStateIsCapturedIntoReadyPreparation();
        overdueStateNeedsNoCurrentSnapshot();
        incompleteRelatedEventRefusesBeforeSchedulerCapture();
        mixedProposalPreflightClassifiesEveryRelatedEvent();
        changedRegistrationSetRefusesClosed();
    }

    private static void futureStateIsCapturedIntoReadyPreparation() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = registered(store, false);
        RegionManager region = new RegionManager(true);
        GameTickEventRestorationLivePreparationCoordinator.PreparationCapture
            result = coordinator(store, region).capture(
                inventory(store, event, true), 1);
        check(result.isReady()
                && result.getRecoveryCandidateCount() == 1
                && result.getInventoryEventCount() == 1
                && result.getPreparation().getDirectives().size() == 1
                && result.getFutureSnapshots().size() == 1
                && result.getProposalGeneration() == GENERATION
                && result.isRegistrationSetStable()
                && result.isCandidateTimingStable()
                && region.getCaptureCalls() == 1
                && store.eventIsContained(event)
                && !result.isRuntimeHandleRetained()
                && !result.isReconstructionInvoked()
                && !result.isRetryPerformed()
                && !result.isArrivalGate()
                && !result.isVisibilityReleased(),
            "future callback produces exact ready pre-reconstruction state");
    }

    private static void overdueStateNeedsNoCurrentSnapshot() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = registered(store, true);
        RegionManager region = new RegionManager(true);
        GameTickEventRestorationLivePreparationCoordinator.PreparationCapture
            result = coordinator(store, region).capture(
                inventory(store, event, true), 1);
        check(result.isReady()
                && result.getRecoveryCandidateCount() == 1
                && result.getFutureSnapshots().isEmpty()
                && result.getPreparation().getDirectives().get(0)
                    .getTicksBeforeRun() == 0L
                && region.getCaptureCalls() == 0
                && store.eventIsContained(event),
            "overdue callback captures timing without current scenery");
    }

    private static void incompleteRelatedEventRefusesBeforeSchedulerCapture() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = registered(store, false);
        RegionManager region = new RegionManager(true);
        GameTickEventRestorationLivePreparationCoordinator.PreparationCapture
            result = coordinator(store, region).capture(
                inventory(store, event, false), 1);
        check(result.getReason()
                    == GameTickEventRestorationLivePreparationCoordinator
                        .Reason.RELATED_EVENT_RECOVERY_INCOMPLETE
                && !result.isReady()
                && region.getCaptureCalls() == 0
                && store.eventIsContained(event),
            "related event without recovery state refuses before live work");
    }

    private static void mixedProposalPreflightClassifiesEveryRelatedEvent() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent incomplete = registered(store, false);
        RestorableEvent complete = registered(store, false);
        LayeredPackedRegionEventOwnershipInventory inventory =
            mixedInventory(store, incomplete, complete);
        GameTickEventRestorationLivePreparationCoordinator.RecoveryPreflight
            preflight = GameTickEventRestorationLivePreparationCoordinator
                .assessRecovery(inventory);
        RegionManager region = new RegionManager(true);
        GameTickEventRestorationLivePreparationCoordinator.PreparationCapture
            result = coordinator(store, region).capture(inventory, 2);
        check(preflight.getProposalRelatedEventCount() == 2
                && preflight.getRecoveryCompleteEventCount() == 1
                && preflight.getRecoveryIncompleteEventCount() == 1
                && preflight.getIncompleteOwnerPositionHintEventCount() == 1
                && preflight.getIncompleteExactSpatialEventCount() == 0
                && preflight.getFirstIncompleteRegistrationSequence()
                    .longValue() == sequenceOf(store, incomplete)
                && preflight.getFirstIncompleteOwnerKind() == OwnerKind.NPC
                && preflight.getFirstIncompleteAttributionKind()
                    == AttributionKind.OWNER_POSITION_HINT
                && preflight.getFirstIncompleteRequirement()
                    == GameTickEventRestorationLivePreparationCoordinator
                        .RecoveryRequirement.RESTORATION_STATE_UNAVAILABLE
                && !preflight.isComplete()
                && !preflight.isRuntimeHandleRetained()
                && result.getReason()
                    == GameTickEventRestorationLivePreparationCoordinator
                        .Reason.RELATED_EVENT_RECOVERY_INCOMPLETE
                && result.getRecoveryCandidateCount() == 1
                && region.getCaptureCalls() == 0
                && store.eventIsContained(incomplete)
                && store.eventIsContained(complete),
            "mixed proposal reports complete work and its first blocker");
    }

    private static void changedRegistrationSetRefusesClosed() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = registered(store, false);
        LayeredPackedRegionEventOwnershipInventory inventory =
            inventory(store, event, true);
        RestorableEvent added = registered(store, false);
        RegionManager region = new RegionManager(true);
        GameTickEventRestorationLivePreparationCoordinator.PreparationCapture
            result = coordinator(store, region).capture(inventory, 1);
        check(result.getReason()
                    == GameTickEventRestorationLivePreparationCoordinator
                        .Reason.REGISTRATION_SET_DRIFT
                && !result.isReady()
                && region.getCaptureCalls() == 0
                && store.eventIsContained(event)
                && store.eventIsContained(added),
            "changed scheduler registration set refuses exact inventory");
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
    private static LayeredPackedRegionEventOwnershipInventory mixedInventory(
            GameTickEventStore store,
            RestorableEvent incomplete,
            RestorableEvent complete) {
        GameTickEvent.AtomicTimingSnapshot incompleteTiming =
            incomplete.captureAtomicTimingSnapshot();
        GameTickEvent.AtomicTimingSnapshot completeTiming =
            complete.captureAtomicTimingSnapshot();
        EventState incompleteState = EventState.of(
            0, sequenceOf(store, incomplete), OwnerKind.NPC,
            AttributionKind.OWNER_POSITION_HINT,
            incompleteTiming.isRunning(),
            incompleteTiming.getTicksBeforeRun(),
            incompleteTiming.getTimesRan(),
            Collections.singletonList(SpatialReference.of(
                SpatialRole.OWNER_CURRENT_POSITION, 524, 489)),
            EventRestorationState.unavailable(), false);
        EventRestorationState restoration =
            EventRestorationState.scenerySpawn(
                SceneryRestorationState.of(
                    310, 310, 524, 489, 0, 0, null, 0,
                    AuthoredPlacementRestorationState.of(
                        GENERATION, 10, 10, 22,
                        AuthoredConstructionKind.SCENERY)),
                true, ExecutionSemantics.ONE_SHOT,
                TimeProgressionPolicy.CONTINUE_SERVER_TICKS);
        EventState completeState = EventState.of(
            1, sequenceOf(store, complete), OwnerKind.NONE,
            AttributionKind.EXACT_SPATIAL, completeTiming.isRunning(),
            completeTiming.getTicksBeforeRun(), completeTiming.getTimesRan(),
            Collections.singletonList(SpatialReference.of(
                SpatialRole.FIXED_EFFECT_LOCATION, 524, 489)),
            restoration, true);
        return LayeredPackedRegionEventOwnershipInventory.inventory(
            GENERATION, 1L, scope(store),
            Collections.singletonList(PackedSource.of(10, 10)),
            Arrays.asList(incompleteState, completeState), 1, 2, 2);
    }
    private static RestorableEvent registered(
            GameTickEventStore store, boolean overdue) {
        RestorableEvent event = new RestorableEvent(overdue);
        check(store.add(event), "event registered");
        return event;
    }
    private static GameTickEventRestorationLivePreparationCoordinator
            coordinator(GameTickEventStore store, RegionManager region) {
        return new GameTickEventRestorationLivePreparationCoordinator(
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


class LayeredMapsSliceOneHundredFiftyFiveTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-live-recovery-preparation-"
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
                SHARED_150["REGION_MANAGER_STUB"],
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
            "LiveRecoveryPreparationFixture.java": FIXTURE,
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
                str(EVENT), str(STATE), str(AFFINITY), str(SNAPSHOT),
                str(REQUEST), str(ONE_SHOT), str(BATCH),
                str(COORDINATOR_CONTRACT), str(INVENTORY),
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

    def test_live_preparation_fixture_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", self.classpath,
                "com.openrsc.server.event.rsc.handler."
                "LiveRecoveryPreparationFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_source_captures_only_without_reconstruction_or_visibility(self):
        source = LIVE_PREPARATION.read_text(encoding="utf-8")
        self.assertIn("event.isCandidateRelated()", source)
        self.assertIn("registrationSetMatches(", source)
        self.assertIn("candidateTimingMatches(", source)
        self.assertIn("currentStateCapture.capture(", source)
        self.assertNotIn("WorldLoader", source)
        self.assertNotIn("reconstructThenRecover", source)
        for required in (
            "isReconstructionInvoked() { return false; }",
            "isRetryPerformed() { return false; }",
            "isArrivalGate() { return false; }",
            "isVisibilityReleased() { return false; }",
        ):
            self.assertIn(required, source)

    def test_plan_records_slice_one_hundred_fifty_five(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn("Slice 155", plan)
        self.assertIn("live recovery preparation", plan.lower())


if __name__ == "__main__":
    unittest.main()
