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
import com.openrsc.server.event.rsc.GameTickEventRestorationCommitRequest;
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
import java.util.concurrent.atomic.AtomicReference;

public final class RestorationCommitRequestFixture {
    private static final class RestorableEvent extends GameTickEvent {
        private final GameTickEventRestorationState restoration;
        RestorableEvent() {
            super(new World(), null, 100L, "commit-request-fixture",
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
        stableRequestCopiesClosedStateAndExcludesStop();
        staleGenerationRefusesWithoutDeliveringRequest();
        stoppedEventRefusesWithoutDeliveringRequest();
    }

    private static void stableRequestCopiesClosedStateAndExcludesStop() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = new RestorableEvent();
        check(store.add(event), "event registered");
        String scope = scope(store);
        long sequence = sequenceOf(store, event);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<GameTickEventRestorationCommitRequest> request =
            new AtomicReference<>();
        AtomicReference<GameTickEventStore.RestorationCommitRequestExecution>
            execution = new AtomicReference<>();
        Thread requester = thread(() -> execution.set(
            store.withValidatedRestorationCommitRequest(
                scope, sequence, 7L, value -> {
                    request.set(value);
                    entered.countDown();
                    await(release);
                })));
        requester.start();
        await(entered);

        Thread stopper = thread(event::stop);
        stopper.start();
        check(!joinWithin(stopper, 150L),
            "stop waits for the narrow lifecycle commit boundary");
        release.countDown();
        join(requester);
        join(stopper);

        GameTickEventRestorationCommitRequest value = request.get();
        GameTickEventStore.RestorationCommitRequestExecution result =
            execution.get();
        check(value != null
                && value.getSchedulerInstanceIdentity().equals(scope)
                && value.getRegistrationSequence() == sequence
                && value.getProposalGeneration() == 7L
                && value.getAuthoredGeneration() == 7L
                && value.getLifecycleVersion() > 0L
                && value.isEventExecutionBoundaryHeld()
                && !value.isSchedulerStoreBoundaryHeld()
                && value.isRegistrationRevalidated()
                && value.isLifecycleBoundaryHeld()
                && value.getObjectId() == 310
                && value.getPermanentObjectId() == 310
                && value.getX() == 524 && value.getY() == 489
                && value.getDirection() == 0 && value.getType() == 0
                && value.isForceFullBlock()
                && value.getAuthoredPackedRegionX() == 10
                && value.getAuthoredPackedRegionY() == 10
                && value.getAuthoredSourceOrdinal() == 22
                && value.getAuthoredConstructionKind().equals("SCENERY")
                && value.isEphemeralBoundaryValue()
                && !value.isReusablePermit()
                && !value.isMutationAuthorized()
                && !value.isCommitToken()
                && result != null && result.isRequestDelivered()
                && result.isLifecycleBoundaryEntered()
                && !result.isRequestRetained()
                && !result.isMutationPerformed()
                && !result.isCallbackInvoked()
                && !result.isEventCancellation()
                && !result.isEventReschedule()
                && !result.isExecutableRestoration()
                && !result.isLifecycleAuthority(),
            "accepted request is closed, exact, and non-authoritative");
    }

    private static void staleGenerationRefusesWithoutDeliveringRequest() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = new RestorableEvent();
        check(store.add(event), "stale-generation event registered");
        GameTickEventStore.RestorationCommitRequestExecution execution =
            store.withValidatedRestorationCommitRequest(
                scope(store), sequenceOf(store, event), 8L,
                request -> fail("stale generation delivered a request"));
        check(!execution.isRequestDelivered()
                && !execution.isLifecycleBoundaryEntered()
                && execution.getReason()
                    == GameTickEventStore.RestorationRegistrationFenceReason
                        .PROPOSAL_GENERATION_MISMATCH,
            "stale authored generation refuses before request delivery");
    }

    private static void stoppedEventRefusesWithoutDeliveringRequest() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = new RestorableEvent();
        check(store.add(event), "stopped event registered");
        event.stop();
        GameTickEventStore.RestorationCommitRequestExecution execution =
            store.withValidatedRestorationCommitRequest(
                scope(store), sequenceOf(store, event), 7L,
                request -> fail("stopped event delivered a request"));
        check(!execution.isRequestDelivered()
                && execution.getReason()
                    == GameTickEventStore.RestorationRegistrationFenceReason
                        .EVENT_NOT_RUNNING,
            "stopped event refuses before lifecycle boundary");
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
        try {
            check(latch.await(5L, TimeUnit.SECONDS), "latch completed");
        } catch (InterruptedException interrupted) {
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
    private static void fail(String message) {
        throw new AssertionError(message);
    }
    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
'''


class LayeredMapsSliceOneHundredThirtySevenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-restoration-commit-request-"
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
            "RestorationCommitRequestFixture.java": FIXTURE,
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
                str(REQUEST), str(ROOT / (
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

    def test_commit_request_boundary_fixture_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", self.classpath,
                "com.openrsc.server.event.rsc.handler."
                "RestorationCommitRequestFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_request_and_result_retain_no_runtime_authority(self):
        request = REQUEST.read_text(encoding="utf-8")
        store = STORE.read_text(encoding="utf-8")
        for required in (
            "isReusablePermit() { return false; }",
            "isRuntimeHandleRetained() { return false; }",
            "isMutationAuthorized() { return false; }",
            "isMutationPerformed() { return false; }",
            "isCallbackInvoked() { return false; }",
            "isEventCancellation() { return false; }",
            "isEventReschedule() { return false; }",
            "isExecutableRestoration() { return false; }",
            "isCommitToken() { return false; }",
            "isArrivalGate() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, request)
        result_class = store[store.index(
            "class RestorationCommitRequestExecution"
        ):]
        for required in (
            "isRequestRetained() { return false; }",
            "isMutationAuthorized() { return false; }",
            "isMutationPerformed() { return false; }",
            "isCallbackInvoked() { return false; }",
            "isEventCancellation() { return false; }",
            "isEventReschedule() { return false; }",
            "isExecutableRestoration() { return false; }",
            "isCommitToken() { return false; }",
            "isArrivalGate() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, result_class)

    def test_boundary_order_and_disconnection_are_explicit(self):
        event = EVENT.read_text(encoding="utf-8")
        store = STORE.read_text(encoding="utf-8")
        method = event[event.index(
            "withinStableRestorationLifecycleBoundary("
        ):event.index(
            "public interface ExecutionBoundaryOperation", event.index(
                "withinStableRestorationLifecycleBoundary("
            )
        )]
        self.assertIn("isExecutionBoundaryHeldByCurrentThread()", method)
        self.assertIn("synchronized (timingLock)", method)
        self.assertLess(
            method.index("isExecutionBoundaryHeldByCurrentThread()"),
            method.index("synchronized (timingLock)"),
        )
        commit = store[store.index(
            "withValidatedRestorationCommitRequest("
        ):store.index(
            "private static RestorationRegistrationFenceExecution",
            store.index("withValidatedRestorationCommitRequest("),
        )]
        self.assertLess(
            commit.index("withValidatedRegistrationFence("),
            commit.index("withinStableRestorationLifecycleBoundary("),
        )
        self.assertLess(
            commit.index("withinStableRestorationLifecycleBoundary("),
            commit.index("GameTickEventRestorationCommitRequest.request("),
        )
        for forbidden in (
            "RegionManager", "GameObject", "registerGameObject",
            "unregisterGameObject", ".run()", ".stop()",
        ):
            self.assertNotIn(forbidden, commit)

    def test_living_plan_records_slice_one_hundred_thirty_seven(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 137: Scheduler-fenced restoration commit request",
            plan,
        )
        normalized = " ".join(plan.split())
        self.assertIn(
            "no Region mutation consumer is connected", normalized
        )
        self.assertIn("stop/reset/tick", normalized)


if __name__ == "__main__":
    unittest.main()
