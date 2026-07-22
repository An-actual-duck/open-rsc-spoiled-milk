#!/usr/bin/env python3
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/GameTickEvent.java"
STATE = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationState.java"
)
AFFINITY = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventSpatialAffinity.java"
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
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventStore.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SHARED = runpy.run_path(str(ROOT / (
    "tests/myworld/"
    "test-layered-maps-slice-one-hundred-twenty-two.py"
)))


FIXTURE = r'''
package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import com.openrsc.server.event.rsc.GameTickEventRestorationOneShotConsumptionContract
    .RegionCommitOutcome;
import com.openrsc.server.event.rsc.GameTickEventRestorationOneShotConsumptionContract
    .RequiredAction;
import com.openrsc.server.event.rsc.GameTickEventRestorationState;
import com.openrsc.server.event.rsc.GameTickEventRestorationState
    .AuthoredConstructionKind;
import com.openrsc.server.event.rsc.GameTickEventRestorationState
    .AuthoredPlacementState;
import com.openrsc.server.event.rsc.GameTickEventRestorationState.SceneryState;
import com.openrsc.server.event.rsc.GameTickEventSpatialAffinity;
import com.openrsc.server.model.world.World;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class RestorationOneShotSchedulerFixture {
    private static final class RestorableEvent extends GameTickEvent {
        private final AtomicInteger callbackRuns = new AtomicInteger();
        private final GameTickEventRestorationState restoration;

        RestorableEvent() {
            super(new World(), null, 100L, "fixture",
                DuplicationStrategy.ALLOW_MULTIPLE);
            restoration = GameTickEventRestorationState.scenerySpawn(
                SceneryState.of(
                    310, 310, 524, 489, 0, 0, null, 0,
                    AuthoredPlacementState.of(
                        7L, 10, 10, 22,
                        AuthoredConstructionKind.SCENERY)),
                true);
        }

        public void run() { callbackRuns.incrementAndGet(); }

        @Override
        public GameTickEventRestorationState getRestorationState() {
            return restoration;
        }

        @Override
        public GameTickEventSpatialAffinity getSpatialAffinity() {
            return GameTickEventSpatialAffinity.exactFixedLocation(524, 489);
        }

        int getCallbackRuns() { return callbackRuns.get(); }
    }

    public static void main(String[] args) {
        appliedAndNoOpConsumeExactRegistration();
        refusedOutcomeRetainsExactRegistration();
        concurrentCallbackStopAndRemovalCannotCrossConsumption();
        invalidGenerationAndStoppedLifecycleRefuseBeforeOutcome();
    }

    private static void appliedAndNoOpConsumeExactRegistration() {
        for (RegionCommitOutcome outcome : new RegionCommitOutcome[] {
                RegionCommitOutcome.APPLIED, RegionCommitOutcome.NO_OP}) {
            GameTickEventStore store = new GameTickEventStore();
            RestorableEvent event = new RestorableEvent();
            check(store.add(event), "event registered");
            long registrationVersion = registrationVersion(store);
            long lifecycle = event.captureAtomicTimingSnapshot()
                .getLifecycleVersion();
            GameTickEventStore.RestorationOneShotConsumptionExecution result =
                store.withValidatedRestorationOneShotConsumption(
                    scope(store), sequenceOf(store, event), 7L,
                    request -> outcome);
            check(result.isRequestDelivered()
                    && result.getRegionCommitOutcome() == outcome
                    && result.getRequiredAction()
                        == RequiredAction.TERMINALLY_CONSUME
                    && result.getReason()
                        == GameTickEventStore
                            .RestorationOneShotConsumptionReason
                            .EVENT_TERMINALLY_CONSUMED
                    && result.isRegistrationRemoved()
                    && result.isEventTerminallyConsumed()
                    && !result.isExactRegistrationRetained()
                    && result.getLifecycleVersionBefore() == lifecycle
                    && result.getLifecycleVersionAfter() == lifecycle + 1L
                    && result.isFixtureReportedRegionMutation()
                        == (outcome == RegionCommitOutcome.APPLIED)
                    && !result.isRuntimeRegionManagerInvoked()
                    && !result.isRequestRetained()
                    && !result.isRuntimeHandleRetained()
                    && !result.isMutationAuthorized()
                    && !result.isCallbackInvoked()
                    && !result.isEventReschedule()
                    && !result.isExecutableRestoration()
                    && !result.isCommitToken()
                    && !result.isArrivalGate()
                    && !result.isLifecycleAuthority(),
                "applied/no-op outcome is terminal and closed");
            check(!store.eventIsContained(event)
                    && !event.isRunning()
                    && event.getTimesRan() == 0
                    && event.getCallbackRuns() == 0
                    && registrationVersion(store) == registrationVersion + 1L,
                "exact registration removed once without callback");
        }
    }

    private static void refusedOutcomeRetainsExactRegistration() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = new RestorableEvent();
        check(store.add(event), "refused event registered");
        long registrationVersion = registrationVersion(store);
        long lifecycle = event.captureAtomicTimingSnapshot()
            .getLifecycleVersion();
        long sequence = sequenceOf(store, event);
        GameTickEventStore.RestorationOneShotConsumptionExecution result =
            store.withValidatedRestorationOneShotConsumption(
                scope(store), sequence, 7L,
                request -> RegionCommitOutcome.REFUSED);
        check(result.isRequestDelivered()
                && result.getRegionCommitOutcome()
                    == RegionCommitOutcome.REFUSED
                && result.getRequiredAction()
                    == RequiredAction.RETAIN_SCHEDULED
                && result.getReason()
                    == GameTickEventStore.RestorationOneShotConsumptionReason
                        .REGION_COMMIT_REFUSED_RETAINED
                && result.isExactRegistrationRetained()
                && !result.isRegistrationRemoved()
                && !result.isEventTerminallyConsumed()
                && result.getLifecycleVersionBefore() == lifecycle
                && result.getLifecycleVersionAfter() == lifecycle,
            "refused outcome retains the exact scheduled event");
        check(store.eventIsContained(event)
                && sequenceOf(store, event) == sequence
                && registrationVersion(store) == registrationVersion
                && event.isRunning()
                && event.captureAtomicTimingSnapshot().getLifecycleVersion()
                    == lifecycle
                && event.getCallbackRuns() == 0,
            "refusal changes no registration or lifecycle state");
        store.remove(event);
        event.stop();
    }

    private static void concurrentCallbackStopAndRemovalCannotCrossConsumption() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = new RestorableEvent();
        check(store.add(event), "race event registered");
        long registrationVersion = registrationVersion(store);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<GameTickEventStore.RestorationOneShotConsumptionExecution>
            execution = new AtomicReference<>();
        Thread consumer = thread(() -> execution.set(
            store.withValidatedRestorationOneShotConsumption(
                scope(store), sequenceOf(store, event), 7L, request -> {
                    entered.countDown();
                    await(release);
                    return RegionCommitOutcome.APPLIED;
                })));
        consumer.start();
        await(entered);

        Thread callback = thread(event::doRun);
        Thread stopper = thread(event::stop);
        Thread remover = thread(() -> store.remove(event));
        callback.start();
        stopper.start();
        remover.start();
        check(!joinWithin(callback, 150L)
                && !joinWithin(stopper, 150L)
                && !joinWithin(remover, 150L),
            "callback, stop, and removal wait outside the closed boundary");

        release.countDown();
        join(consumer);
        join(callback);
        join(stopper);
        join(remover);
        GameTickEventStore.RestorationOneShotConsumptionExecution result =
            execution.get();
        check(result != null && result.isEventTerminallyConsumed()
                && result.isRegistrationRemoved()
                && event.getCallbackRuns() == 0
                && event.getTimesRan() == 0
                && !event.isRunning()
                && !store.eventIsContained(event)
                && registrationVersion(store) == registrationVersion + 1L,
            "terminal consumption wins every concurrent boundary race");
    }

    private static void invalidGenerationAndStoppedLifecycleRefuseBeforeOutcome() {
        GameTickEventStore staleStore = new GameTickEventStore();
        RestorableEvent stale = new RestorableEvent();
        check(staleStore.add(stale), "stale event registered");
        GameTickEventStore.RestorationOneShotConsumptionExecution staleResult =
            staleStore.withValidatedRestorationOneShotConsumption(
                scope(staleStore), sequenceOf(staleStore, stale), 8L,
                request -> failOutcome("stale generation reached outcome"));
        check(!staleResult.isRequestDelivered()
                && staleResult.getReason()
                    == GameTickEventStore.RestorationOneShotConsumptionReason
                        .PROPOSAL_GENERATION_MISMATCH
                && staleStore.eventIsContained(stale),
            "stale generation refuses before outcome delivery");
        staleStore.remove(stale);
        stale.stop();

        GameTickEventStore stoppedStore = new GameTickEventStore();
        RestorableEvent stopped = new RestorableEvent();
        check(stoppedStore.add(stopped), "stopped event registered");
        stopped.stop();
        GameTickEventStore.RestorationOneShotConsumptionExecution stoppedResult =
            stoppedStore.withValidatedRestorationOneShotConsumption(
                scope(stoppedStore), sequenceOf(stoppedStore, stopped), 7L,
                request -> failOutcome("stopped event reached outcome"));
        check(!stoppedResult.isRequestDelivered()
                && stoppedResult.getReason()
                    == GameTickEventStore.RestorationOneShotConsumptionReason
                        .EVENT_NOT_RUNNING
                && stoppedStore.eventIsContained(stopped),
            "stopped lifecycle refuses before outcome delivery");
        stoppedStore.remove(stopped);
    }

    private static RegionCommitOutcome failOutcome(String message) {
        throw new AssertionError(message);
    }

    private static String scope(GameTickEventStore store) {
        return store.getTrackedEventRegistrationSnapshot()
            .getSchedulerInstanceIdentity();
    }

    private static long registrationVersion(GameTickEventStore store) {
        return store.getTrackedEventRegistrationSnapshot()
            .getRegistrationVersion();
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
        try {
            check(latch.await(5L, TimeUnit.SECONDS), "latch completed");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static boolean joinWithin(Thread thread, long millis) {
        try {
            thread.join(millis);
        } catch (InterruptedException interrupted) {
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


class LayeredMapsSliceOneHundredFortyOneTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-one-shot-scheduler-"
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
                SHARED["REGION_MANAGER_STUB"],
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
            "RestorationOneShotSchedulerFixture.java": FIXTURE,
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
                str(STORE), str(EVENT), str(STATE), str(AFFINITY),
                str(REQUEST), str(CONTRACT), str(ROOT / (
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

    def test_scheduler_consumption_fixture_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", self.classpath,
                "com.openrsc.server.event.rsc.handler."
                "RestorationOneShotSchedulerFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_lock_order_and_runtime_disconnection_are_explicit(self):
        store = STORE.read_text(encoding="utf-8")
        method = store[store.index(
            "withValidatedRestorationOneShotConsumption("
        ):store.index(
            "private static RestorationRegistrationFenceExecution",
            store.index("withValidatedRestorationOneShotConsumption("),
        )]
        boundary = method[method.index(
            "withinStableRestorationConsumptionBoundary("
        ):]
        self.assertLess(
            boundary.index("checkedOperation.execute(request)"),
            boundary.index("synchronized (LOCK)"),
        )
        self.assertLess(
            boundary.index("synchronized (LOCK)"),
            boundary.index("unregisterAccepted("),
        )
        for forbidden in (
            "RegionManager", "registerGameObject", "unregisterGameObject",
            ".run()", ".doRun()",
        ):
            self.assertNotIn(forbidden, method)

    def test_result_and_lifecycle_boundary_retain_no_authority(self):
        event = EVENT.read_text(encoding="utf-8")
        store = STORE.read_text(encoding="utf-8")
        boundary = event[event.index(
            "withinStableRestorationConsumptionBoundary("
        ):event.index(
            "public interface ExecutionBoundaryOperation",
            event.index("withinStableRestorationConsumptionBoundary("),
        )]
        self.assertIn("synchronized (timingLock)", boundary)
        self.assertIn("running = false", boundary)
        self.assertIn("advanceLifecycleVersion()", boundary)
        result = store[store.index(
            "class RestorationOneShotConsumptionExecution"
        ):store.index(
            "class RestorationTargetRevalidationExecution",
            store.index("class RestorationOneShotConsumptionExecution"),
        )]
        for required in (
            "isRuntimeRegionManagerInvoked() { return false; }",
            "isRequestRetained() { return false; }",
            "isRuntimeHandleRetained() { return false; }",
            "isMutationAuthorized() { return false; }",
            "isCallbackInvoked() { return false; }",
            "isEventReschedule() { return false; }",
            "isExecutableRestoration() { return false; }",
            "isCommitToken() { return false; }",
            "isArrivalGate() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, result)

    def test_living_plan_records_slice_one_hundred_forty_one(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 141: Scheduler-local exact one-shot consumption",
            plan,
        )
        normalized = " ".join(plan.split())
        self.assertIn(
            "fixture-supplied detached Region outcome", normalized
        )
        self.assertIn("RegionManager remains disconnected", normalized)


if __name__ == "__main__":
    unittest.main()
