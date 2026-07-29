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
    "GameTickEventRestorationCurrentStateCaptureCoordinator.java"
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
    .GameTickEventRestorationCurrentStateRecoverySnapshot.CallbackExpectation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot.CollisionContribution;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot.CurrentScenery;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTargetRevalidation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTargetRevalidationRequest;
import java.util.Collections;
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

    public enum CurrentStateRecoveryCaptureReason {
        SNAPSHOT_AVAILABLE, TARGET_NOT_EXACTLY_ONE, SNAPSHOT_REFUSED
    }
    public static final class CurrentStateRecoveryCaptureResult {
        private final CurrentStateRecoveryCaptureReason reason;
        private final GameTickEventRestorationCurrentStateRecoverySnapshot
            snapshot;
        private final GameTickEventRestorationCurrentStateRecoverySnapshot
            .Reason snapshotReason;
        private CurrentStateRecoveryCaptureResult(
                CurrentStateRecoveryCaptureReason reason,
                GameTickEventRestorationCurrentStateRecoverySnapshot snapshot,
                GameTickEventRestorationCurrentStateRecoverySnapshot.Reason
                    snapshotReason) {
            this.reason = reason;
            this.snapshot = snapshot;
            this.snapshotReason = snapshotReason;
        }
        static CurrentStateRecoveryCaptureResult missing() {
            return new CurrentStateRecoveryCaptureResult(
                CurrentStateRecoveryCaptureReason.TARGET_NOT_EXACTLY_ONE,
                null, null);
        }
        static CurrentStateRecoveryCaptureResult assess(
                CallbackExpectation callback) {
            GameTickEventRestorationCurrentStateRecoverySnapshot
                .ObservedCurrentState observed = callback.getKind()
                    == GameTickEventRestorationCurrentStateRecoverySnapshot
                        .CallbackKind.SCENERY_SPAWN
                    ? GameTickEventRestorationCurrentStateRecoverySnapshot
                        .ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT
                    : GameTickEventRestorationCurrentStateRecoverySnapshot
                        .ObservedCurrentState
                            .EXACT_RESTORATION_SCENERY_PRESENT;
            CurrentScenery current = CurrentScenery.declare(
                observed, callback.getObjectId(),
                callback.getPermanentObjectId(), callback.getX(),
                callback.getY(), callback.getDirection(), callback.getType(),
                null, 0, callback.getAuthoredGeneration(),
                callback.getAuthoredPackedRegionX(),
                callback.getAuthoredPackedRegionY(),
                callback.getAuthoredSourceOrdinal(),
                callback.getAuthoredConstructionKind(), 1,
                true, true, true, true, true,
                Collections.<CollisionContribution>emptyList());
            GameTickEventRestorationCurrentStateRecoverySnapshot.Creation
                creation = GameTickEventRestorationCurrentStateRecoverySnapshot
                    .assess(callback, current);
            return new CurrentStateRecoveryCaptureResult(
                creation.isSnapshotAvailable()
                    ? CurrentStateRecoveryCaptureReason.SNAPSHOT_AVAILABLE
                    : CurrentStateRecoveryCaptureReason.SNAPSHOT_REFUSED,
                creation.getSnapshot(), creation.getReason());
        }
        public CurrentStateRecoveryCaptureReason getReason() { return reason; }
        public GameTickEventRestorationCurrentStateRecoverySnapshot
            getSnapshot() { return snapshot; }
        public GameTickEventRestorationCurrentStateRecoverySnapshot.Reason
            getSnapshotReason() { return snapshotReason; }
    }

    private final boolean targetPresent;
    private final CountDownLatch entered;
    private final CountDownLatch release;
    private int captureCalls;
    private CallbackExpectation callback;
    private boolean eventExecutionBoundaryHeld;
    private boolean stableLifecycleBoundaryHeld;

    public RegionManager(boolean targetPresent) {
        this(targetPresent, null, null);
    }
    public RegionManager(
            boolean targetPresent,
            CountDownLatch entered,
            CountDownLatch release) {
        this.targetPresent = targetPresent;
        this.entered = entered;
        this.release = release;
    }
    public CurrentStateRecoveryCaptureResult
            captureGameTickEventCurrentStateRecoverySnapshot(
                CallbackExpectation checked,
                boolean executionHeld,
                boolean lifecycleHeld) {
        captureCalls++;
        callback = checked;
        eventExecutionBoundaryHeld = executionHeld;
        stableLifecycleBoundaryHeld = lifecycleHeld;
        if (entered != null) {
            entered.countDown();
            await(release);
        }
        return targetPresent
            ? CurrentStateRecoveryCaptureResult.assess(checked)
            : CurrentStateRecoveryCaptureResult.missing();
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
    public int getCaptureCalls() { return captureCalls; }
    public CallbackExpectation getCallback() { return callback; }
    public boolean isEventExecutionBoundaryHeld() {
        return eventExecutionBoundaryHeld;
    }
    public boolean isStableLifecycleBoundaryHeld() {
        return stableLifecycleBoundaryHeld;
    }
}
'''


FIXTURE = r'''
package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.GameTickEventRestorationState;
import com.openrsc.server.event.rsc.GameTickEventRestorationState
    .AuthoredConstructionKind;
import com.openrsc.server.event.rsc.GameTickEventRestorationState
    .AuthoredPlacementState;
import com.openrsc.server.event.rsc.GameTickEventRestorationState.SceneryState;
import com.openrsc.server.event.rsc.GameTickEventSpatialAffinity;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.region.RegionManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class CurrentStateCaptureCoordinatorFixture {
    private static final class RestorableEvent extends GameTickEvent {
        private final GameTickEventRestorationState restoration;
        RestorableEvent() {
            super(new World(), null, 100L, "current-state-capture-fixture",
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
        exactFutureCaptureRetainsRegistrationAndCountdown();
        missingTargetRefusesClosedWithoutChangingEvent();
        staleGenerationNeverReachesRegion();
        stableLifecycleCaptureExcludesConcurrentStop();
    }

    private static void exactFutureCaptureRetainsRegistrationAndCountdown() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = new RestorableEvent();
        check(store.add(event), "future event registered");
        RegionManager region = new RegionManager(true);
        long beforeTicks = event.captureAtomicTimingSnapshot()
            .getTicksBeforeRun();
        GameTickEventRestorationCurrentStateCaptureCoordinator
            .CaptureExecution result = coordinator(store, region).capture(
                scope(store), sequenceOf(store, event), 7L);
        long afterTicks = event.captureAtomicTimingSnapshot()
            .getTicksBeforeRun();
        check(result.isSchedulerFenceAccepted()
                && result.isLifecycleBoundaryEntered()
                && result.isRegionCaptureInvoked()
                && result.isSnapshotAvailable()
                && result.getSnapshot().getTicksBeforeRun() == beforeTicks
                && result.getSnapshot().getRegistrationSequence()
                    == sequenceOf(store, event)
                && result.isExactRegistrationRetained()
                && result.isCountdownRetained()
                && store.eventIsContained(event)
                && beforeTicks == afterTicks
                && region.getCaptureCalls() == 1
                && region.isEventExecutionBoundaryHeld()
                && region.isStableLifecycleBoundaryHeld()
                && region.getCallback().getLifecycleVersion()
                    == result.getLifecycleVersionBeforeOperation()
                && !result.isRegionManagerHandleRetained()
                && !result.isEventHandleRetained()
                && !result.isRegistrationHandleRetained()
                && !result.isMutationPerformed()
                && !result.isCallbackInvoked()
                && !result.isEventCancellation()
                && !result.isEventReschedule()
                && !result.isRegionLoadingPerformed()
                && !result.isExecutableRestoration()
                && !result.isCommitToken()
                && !result.isArrivalGate()
                && !result.isVisibilityReleased()
                && !result.isLifecycleAuthority(),
            "exact future state is captured under all boundaries");
    }

    private static void missingTargetRefusesClosedWithoutChangingEvent() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = new RestorableEvent();
        check(store.add(event), "missing-target event registered");
        RegionManager region = new RegionManager(false);
        long before = event.captureAtomicTimingSnapshot().getTicksBeforeRun();
        GameTickEventRestorationCurrentStateCaptureCoordinator
            .CaptureExecution result = coordinator(store, region).capture(
                scope(store), sequenceOf(store, event), 7L);
        check(result.isSchedulerFenceAccepted()
                && result.isRegionCaptureInvoked()
                && !result.isSnapshotAvailable()
                && result.getRegionReason()
                    == RegionManager.CurrentStateRecoveryCaptureReason
                        .TARGET_NOT_EXACTLY_ONE
                && result.getSnapshotReason() == null
                && store.eventIsContained(event)
                && event.captureAtomicTimingSnapshot().getTicksBeforeRun()
                    == before,
            "closed Region refusal preserves future registration");
    }

    private static void staleGenerationNeverReachesRegion() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = new RestorableEvent();
        check(store.add(event), "stale event registered");
        RegionManager region = new RegionManager(true);
        GameTickEventRestorationCurrentStateCaptureCoordinator
            .CaptureExecution result = coordinator(store, region).capture(
                scope(store), sequenceOf(store, event), 8L);
        check(!result.isSchedulerFenceAccepted()
                && !result.isLifecycleBoundaryEntered()
                && !result.isRegionCaptureInvoked()
                && !result.isSnapshotAvailable()
                && result.getSchedulerReason()
                    == GameTickEventStore.RestorationRegistrationFenceReason
                        .PROPOSAL_GENERATION_MISMATCH
                && region.getCaptureCalls() == 0
                && store.eventIsContained(event),
            "stale proposal refuses before Region observation");
    }

    private static void stableLifecycleCaptureExcludesConcurrentStop() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = new RestorableEvent();
        check(store.add(event), "concurrent event registered");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        RegionManager region = new RegionManager(true, entered, release);
        GameTickEventRestorationCurrentStateCaptureCoordinator
            .CaptureExecution[] result =
                new GameTickEventRestorationCurrentStateCaptureCoordinator
                    .CaptureExecution[1];
        Thread capture = thread(() -> result[0] = coordinator(store, region)
            .capture(scope(store), sequenceOf(store, event), 7L));
        capture.start();
        await(entered);
        Thread stopper = thread(event::stop);
        stopper.start();
        check(!joinWithin(stopper, 150L),
            "stop waits outside stable capture lifecycle");
        release.countDown();
        join(capture);
        join(stopper);
        check(result[0] != null && result[0].isSnapshotAvailable(),
            "capture made under stable boundary survives later stop");
    }

    private static GameTickEventRestorationCurrentStateCaptureCoordinator
            coordinator(GameTickEventStore store, RegionManager region) {
        return new GameTickEventRestorationCurrentStateCaptureCoordinator(
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


class LayeredMapsSliceOneHundredFiftyTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-current-state-capture-coordinator-"
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
            "CurrentStateCaptureCoordinatorFixture.java": FIXTURE,
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

    def test_scheduler_region_capture_fixture_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", self.classpath,
                "com.openrsc.server.event.rsc.handler."
                "CurrentStateCaptureCoordinatorFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_coordinator_is_disconnected_and_non_authoritative(self):
        source = COORDINATOR.read_text(encoding="utf-8")
        store = STORE.read_text(encoding="utf-8")
        self.assertIn("withValidatedRestorationStableLifecycle", store)
        self.assertIn(
            "captureGameTickEventCurrentStateRecoverySnapshot", source
        )
        self.assertNotIn(
            "applyGameTickEventCurrentStateRecoverySnapshot", source
        )
        self.assertNotIn("WorldLoader", source)
        self.assertNotIn("getRegionFromSector", source)
        for required in (
            "isMutationPerformed() { return false; }",
            "isCallbackInvoked() { return false; }",
            "isEventCancellation() { return false; }",
            "isEventReschedule() { return false; }",
            "isRegionLoadingPerformed() { return false; }",
            "isExecutableRestoration() { return false; }",
            "isCommitToken() { return false; }",
            "isArrivalGate() { return false; }",
            "isVisibilityReleased() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_plan_records_slice_one_hundred_fifty(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn("Slice 150", plan)
        self.assertIn("scheduler-fenced current-state capture", plan.lower())


if __name__ == "__main__":
    unittest.main()
