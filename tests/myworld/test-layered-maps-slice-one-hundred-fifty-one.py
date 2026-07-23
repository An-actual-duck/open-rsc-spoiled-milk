#!/usr/bin/env python3
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/GameTickEvent.java"
STATE = ROOT / (
    "server/src/com/openrsc/server/event/rsc/GameTickEventRestorationState.java"
)
AFFINITY = ROOT / (
    "server/src/com/openrsc/server/event/rsc/GameTickEventSpatialAffinity.java"
)
SNAPSHOT = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationCurrentStateRecoverySnapshot.java"
)
REQUEST = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationCommitRequest.java"
)
CONTRACT = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationOneShotConsumptionContract.java"
)
STORE = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameTickEventStore.java"
)
COORDINATOR = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventRestorationFutureStateApplicationCoordinator.java"
)
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
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
import java.util.concurrent.CountDownLatch;

public class RegionManager {
    public enum RestorationCommitOutcome { REFUSED, NO_OP, APPLIED }
    public enum RestorationCommitReason { FIXTURE }
    public static final class RestorationCommitResult {
        public RestorationCommitOutcome getOutcome() {
            return RestorationCommitOutcome.REFUSED;
        }
        public RestorationCommitReason getReason() {
            return RestorationCommitReason.FIXTURE;
        }
        public boolean isMembershipRemoved() { return false; }
        public boolean isMembershipRegistered() { return false; }
        public int getBoundaryCount() { return 0; }
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
        private final boolean registered;
        private final boolean forceFullBlock;
        private final int boundaryCount;
        private CurrentStateRecoveryApplicationResult(
                CurrentStateRecoveryApplicationOutcome outcome,
                CurrentStateRecoveryApplicationReason reason,
                boolean registered, boolean forceFullBlock,
                int boundaryCount) {
            this.outcome = outcome;
            this.reason = reason;
            this.registered = registered;
            this.forceFullBlock = forceFullBlock;
            this.boundaryCount = boundaryCount;
        }
        public static CurrentStateRecoveryApplicationResult applied() {
            return new CurrentStateRecoveryApplicationResult(
                CurrentStateRecoveryApplicationOutcome.APPLIED,
                CurrentStateRecoveryApplicationReason.CURRENT_STATE_RESTORED,
                true, false, 2);
        }
        public static CurrentStateRecoveryApplicationResult noOp() {
            return new CurrentStateRecoveryApplicationResult(
                CurrentStateRecoveryApplicationOutcome.NO_OP,
                CurrentStateRecoveryApplicationReason
                    .CURRENT_STATE_ALREADY_SATISFIED,
                false, false, 2);
        }
        public static CurrentStateRecoveryApplicationResult refused() {
            return new CurrentStateRecoveryApplicationResult(
                CurrentStateRecoveryApplicationOutcome.REFUSED,
                CurrentStateRecoveryApplicationReason.FIXTURE_REFUSED,
                false, false, 0);
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
        public boolean isMembershipRegistered() { return registered; }
        public boolean isForceFullBlockProjectionSelected() {
            return forceFullBlock;
        }
        public int getBoundaryCount() { return boundaryCount; }
    }

    private final CurrentStateRecoveryApplicationResult result;
    private final CountDownLatch entered;
    private final CountDownLatch release;
    private int applicationCalls;
    private GameTickEventRestorationCurrentStateRecoverySnapshot snapshot;

    public RegionManager(CurrentStateRecoveryApplicationResult result) {
        this(result, null, null);
    }
    public RegionManager(
            CurrentStateRecoveryApplicationResult result,
            CountDownLatch entered,
            CountDownLatch release) {
        this.result = result;
        this.entered = entered;
        this.release = release;
    }
    public CurrentStateRecoveryApplicationResult
            applyGameTickEventCurrentStateRecoverySnapshot(
                GameTickEventRestorationCurrentStateRecoverySnapshot checked) {
        applicationCalls++;
        snapshot = checked;
        if (entered != null) {
            entered.countDown();
            await(release);
        }
        return result;
    }
    public CurrentStateRecoveryApplicationResult
            verifyGameTickEventCurrentStateRecoverySnapshot(
                GameTickEventRestorationCurrentStateRecoverySnapshot checked) {
        return applyGameTickEventCurrentStateRecoverySnapshot(checked);
    }
    private static void await(CountDownLatch latch) {
        try { latch.await(); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
    public RestorationCommitResult applyGameTickEventRestorationCommitRequest(
            GameTickEventRestorationCommitRequest request) {
        return new RestorationCommitResult();
    }
    public GameTickEventRestorationTargetRevalidation
            captureGameTickEventRestorationTargetRevalidation(
                GameTickEventRestorationTargetRevalidationRequest request) {
        return new GameTickEventRestorationTargetRevalidation();
    }
    public int getApplicationCalls() { return applicationCalls; }
    public GameTickEventRestorationCurrentStateRecoverySnapshot getSnapshot() {
        return snapshot;
    }
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class FutureStateApplicationCoordinatorFixture {
    private static final class RestorableEvent extends GameTickEvent {
        private final GameTickEventRestorationState restoration;
        RestorableEvent() {
            super(new World(), null, 100L, "future-application-fixture",
                DuplicationStrategy.ALLOW_MULTIPLE);
            restoration = GameTickEventRestorationState.scenerySpawn(
                SceneryState.of(
                    310, 310, 524, 489, 0, 0, null, 0,
                    AuthoredPlacementState.of(
                        7L, 10, 10, 22,
                        AuthoredConstructionKind.SCENERY)),
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
        appliedStateRetainsExactFutureEvent();
        noOpAndRegionRefusalRetainFutureEvent();
        staleSnapshotCorrelationNeverReachesRegion();
        staleProposalRefusesBeforeStableOperation();
        stableApplicationExcludesConcurrentStop();
    }

    private static void appliedStateRetainsExactFutureEvent() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = registered(store);
        RegionManager region = new RegionManager(
            RegionManager.CurrentStateRecoveryApplicationResult.applied());
        GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
            snapshot(store, event, 7L, 0L, 0L);
        long ticks = event.captureAtomicTimingSnapshot().getTicksBeforeRun();
        GameTickEventRestorationFutureStateApplicationCoordinator
            .ApplicationExecution result = coordinator(store, region)
                .apply(snapshot);
        check(result.isCurrentStateRestored()
                && !result.isRefused()
                && result.isLifecycleBoundaryEntered()
                && result.isRegionApplicationInvoked()
                && result.isMembershipRegistered()
                && result.getBoundaryCount() == 2
                && result.isExactRegistrationRetained()
                && result.isCountdownRetained()
                && result.isMutationPerformed()
                && result.isExecutableRestoration()
                && store.eventIsContained(event)
                && event.captureAtomicTimingSnapshot().getTicksBeforeRun()
                    == ticks
                && region.getApplicationCalls() == 1
                && region.getSnapshot() == snapshot
                && !result.isRuntimeHandleRetained()
                && !result.isSnapshotRetained()
                && !result.isRegionResultRetained()
                && !result.isCallbackInvoked()
                && !result.isEventCancellation()
                && !result.isEventReschedule()
                && !result.isRegionLoadingPerformed()
                && !result.isCommitToken()
                && !result.isArrivalGate()
                && !result.isVisibilityReleased()
                && !result.isLifecycleAuthority(),
            "applied current state retains the exact future callback");
    }

    private static void noOpAndRegionRefusalRetainFutureEvent() {
        for (RegionManager.CurrentStateRecoveryApplicationResult declared
                : new RegionManager.CurrentStateRecoveryApplicationResult[] {
                    RegionManager.CurrentStateRecoveryApplicationResult.noOp(),
                    RegionManager.CurrentStateRecoveryApplicationResult
                        .refused(),
                }) {
            GameTickEventStore store = new GameTickEventStore();
            RestorableEvent event = registered(store);
            RegionManager region = new RegionManager(declared);
            long ticks = event.captureAtomicTimingSnapshot().getTicksBeforeRun();
            GameTickEventRestorationFutureStateApplicationCoordinator
                .ApplicationExecution result = coordinator(store, region)
                    .apply(snapshot(store, event, 7L, 0L, 0L));
            check(result.isRegionApplicationInvoked()
                    && result.isExactRegistrationRetained()
                    && store.eventIsContained(event)
                    && event.captureAtomicTimingSnapshot().getTicksBeforeRun()
                        == ticks
                    && (declared.isNoOp()
                        ? result.isCurrentStateAlreadySatisfied()
                            && !result.isMutationPerformed()
                        : result.isRefused()
                            && !result.isMutationPerformed()),
                "no-op/refusal preserves scheduled future state");
        }
    }

    private static void staleSnapshotCorrelationNeverReachesRegion() {
        for (long[] offsets : new long[][] {{1L, 0L}, {0L, 1L}}) {
            GameTickEventStore store = new GameTickEventStore();
            RestorableEvent event = registered(store);
            RegionManager region = new RegionManager(
                RegionManager.CurrentStateRecoveryApplicationResult.applied());
            GameTickEventRestorationFutureStateApplicationCoordinator
                .ApplicationExecution result = coordinator(store, region)
                    .apply(snapshot(
                        store, event, 7L, offsets[0], offsets[1]));
            check(result.isRefused()
                    && result.getReason()
                        == GameTickEventRestorationFutureStateApplicationCoordinator
                            .ApplicationReason
                                .SNAPSHOT_CORRELATION_MISMATCH
                    && !result.isRegionApplicationInvoked()
                    && region.getApplicationCalls() == 0
                    && store.eventIsContained(event),
                "lifecycle/countdown mismatch refuses before Region");
        }
    }

    private static void staleProposalRefusesBeforeStableOperation() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = registered(store);
        RegionManager region = new RegionManager(
            RegionManager.CurrentStateRecoveryApplicationResult.applied());
        GameTickEventRestorationFutureStateApplicationCoordinator
            .ApplicationExecution result = coordinator(store, region).apply(
                snapshot(store, event, 8L, 0L, 0L));
        check(result.isRefused()
                && result.getReason()
                    == GameTickEventRestorationFutureStateApplicationCoordinator
                        .ApplicationReason.SCHEDULER_FENCE_REFUSED
                && result.getSchedulerReason()
                    == GameTickEventStore.RestorationRegistrationFenceReason
                        .PROPOSAL_GENERATION_MISMATCH
                && !result.isLifecycleBoundaryEntered()
                && !result.isRegionApplicationInvoked()
                && region.getApplicationCalls() == 0,
            "stale proposal refuses before lifecycle and Region work");
    }

    private static void stableApplicationExcludesConcurrentStop() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = registered(store);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RegionManager region = new RegionManager(
            RegionManager.CurrentStateRecoveryApplicationResult.applied(),
            entered, release);
        GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
            snapshot(store, event, 7L, 0L, 0L);
        GameTickEventRestorationFutureStateApplicationCoordinator
            .ApplicationExecution[] result =
                new GameTickEventRestorationFutureStateApplicationCoordinator
                    .ApplicationExecution[1];
        Thread applier = thread(() -> result[0] = coordinator(store, region)
            .apply(snapshot));
        applier.start();
        await(entered);
        Thread stopper = thread(event::stop);
        stopper.start();
        check(!joinWithin(stopper, 150L),
            "stop waits outside stable current-state application");
        release.countDown();
        join(applier);
        join(stopper);
        check(result[0] != null && result[0].isCurrentStateRestored(),
            "completed application remains valid before later stop");
    }

    private static RestorableEvent registered(GameTickEventStore store) {
        RestorableEvent event = new RestorableEvent();
        check(store.add(event), "event registered");
        return event;
    }
    private static GameTickEventRestorationCurrentStateRecoverySnapshot
            snapshot(
                GameTickEventStore store,
                RestorableEvent event,
                long proposalGeneration,
                long lifecycleOffset,
                long countdownOffset) {
        GameTickEvent.AtomicTimingSnapshot timing =
            event.captureAtomicTimingSnapshot();
        CallbackExpectation callback = CallbackExpectation.declare(
            GameTickEventRestorationCurrentStateRecoverySnapshot.CallbackKind
                .SCENERY_SPAWN,
            scope(store), sequenceOf(store, event), proposalGeneration,
            timing.getLifecycleVersion() + lifecycleOffset,
            timing.getTicksBeforeRun() + countdownOffset,
            timing.getTimesRan(), timing.isRunning(), true, true,
            310, 310, 524, 489, 0, 0, null, 0,
            proposalGeneration, 10, 10, 22,
            GameTickEventRestorationCurrentStateRecoverySnapshot
                .AuthoredConstructionKind.SCENERY);
        CurrentScenery current = CurrentScenery.declare(
            GameTickEventRestorationCurrentStateRecoverySnapshot
                .ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
            311, 311, 524, 489, 0, 0, null, 0,
            proposalGeneration, 10, 10, 22,
            GameTickEventRestorationCurrentStateRecoverySnapshot
                .AuthoredConstructionKind.SCENERY,
            1, true, true, true, true, true,
            Collections.<CollisionContribution>emptyList());
        GameTickEventRestorationCurrentStateRecoverySnapshot.Creation creation =
            GameTickEventRestorationCurrentStateRecoverySnapshot.assess(
                callback, current);
        check(creation.isSnapshotAvailable(), "fixture snapshot available");
        return creation.getSnapshot();
    }
    private static GameTickEventRestorationFutureStateApplicationCoordinator
            coordinator(GameTickEventStore store, RegionManager region) {
        return new GameTickEventRestorationFutureStateApplicationCoordinator(
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
    private static Thread thread(Runnable operation) {
        Thread thread = new Thread(operation);
        thread.setDaemon(true);
        return thread;
    }
    private static void await(CountDownLatch latch) {
        try { check(latch.await(5L, TimeUnit.SECONDS), "latch completed"); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
    private static boolean joinWithin(Thread thread, long millis) {
        try { thread.join(millis); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
        return !thread.isAlive();
    }
    private static void join(Thread thread) {
        check(joinWithin(thread, 5000L), "fixture thread completed");
    }
    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
'''


class LayeredMapsSliceOneHundredFiftyOneTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-future-state-application-"
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
            "FutureStateApplicationCoordinatorFixture.java": FIXTURE,
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
                str(STORE), str(COORDINATOR), str(EVENT), str(STATE),
                str(AFFINITY), str(SNAPSHOT), str(REQUEST), str(CONTRACT),
                str(ROOT / (
                    "server/src/com/openrsc/server/event/rsc/"
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

    def test_future_application_fixture_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", self.classpath,
                "com.openrsc.server.event.rsc.handler."
                "FutureStateApplicationCoordinatorFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_application_is_bounded_and_not_arrival_connected(self):
        source = COORDINATOR.read_text(encoding="utf-8")
        region = REGION_MANAGER.read_text(encoding="utf-8")
        self.assertIn(
            "public CurrentStateRecoveryApplicationResult", region
        )
        self.assertIn("snapshotMatchesFence", source)
        self.assertIn(
            "applyGameTickEventCurrentStateRecoverySnapshot", source
        )
        self.assertNotIn("captureGameTickEventCurrentStateRecoverySnapshot", source)
        self.assertNotIn("WorldLoader", source)
        self.assertNotIn("getRegionFromSector", source)
        self.assertNotIn("RestorationRecoveryCoordinatorContract", source)
        for required in (
            "isRuntimeHandleRetained() { return false; }",
            "isSnapshotRetained() { return false; }",
            "isRegionResultRetained() { return false; }",
            "isCallbackInvoked() { return false; }",
            "isEventCancellation() { return false; }",
            "isEventReschedule() { return false; }",
            "isRegionLoadingPerformed() { return false; }",
            "isCommitToken() { return false; }",
            "isArrivalGate() { return false; }",
            "isVisibilityReleased() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_plan_records_slice_one_hundred_fifty_one(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn("Slice 151", plan)
        self.assertIn("future current-state application", plan.lower())


if __name__ == "__main__":
    unittest.main()
