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
STORE = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventStore.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_122 = ROOT / (
    "tests/myworld/"
    "test-layered-maps-slice-one-hundred-twenty-two.py"
)
SHARED = runpy.run_path(str(SLICE_122))


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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class EventLifecycleVersionFenceFixture {
    private static final class VersionedEvent extends GameTickEvent {
        private final GameTickEventRestorationState restoration;
        VersionedEvent() {
            super(new World(), null, 100L, "lifecycle-version-fixture",
                DuplicationStrategy.ALLOW_MULTIPLE);
            restoration = GameTickEventRestorationState.scenerySpawn(
                SceneryState.of(
                    310, 310, 524, 489, 0, 0, null, 0,
                    AuthoredPlacementState.of(
                        7L, 10, 10, 22,
                        AuthoredConstructionKind.SCENERY)),
                false);
        }
        public void run() { }
        @Override public GameTickEventRestorationState getRestorationState() {
            return restoration;
        }
        @Override public GameTickEventSpatialAffinity getSpatialAffinity() {
            return GameTickEventSpatialAffinity.exactFixedLocation(524, 489);
        }
    }

    @FunctionalInterface
    private interface LifecycleMutation {
        void mutate(VersionedEvent event);
    }

    public static void main(String[] args) {
        everyTimingMutationAdvancesVersion();
        stableOperationRetainsOneVersion();
        lifecycleChangeRefusesAfterReadOnlyOperation("stop", event ->
            event.stop());
        lifecycleChangeRefusesAfterReadOnlyOperation("reset", event ->
            event.resetCountdown());
        lifecycleChangeRefusesAfterReadOnlyOperation("tick", event ->
            event.tick());
    }

    private static void everyTimingMutationAdvancesVersion() {
        VersionedEvent event = new VersionedEvent();
        long initial = version(event);
        check(initial > 0L, "constructor initializes lifecycle version");
        event.tick();
        long ticked = version(event);
        check(ticked == initial + 1L, "tick advances lifecycle version");
        event.resetCountdown();
        long reset = version(event);
        check(reset == ticked + 1L, "reset advances lifecycle version");
        event.stop();
        long stopped = version(event);
        check(stopped == reset + 1L, "stop advances lifecycle version");
        event.stop();
        check(version(event) == stopped,
            "repeated stop without a state change is version-neutral");

        VersionedEvent executed = new VersionedEvent();
        for (int tick = 0; tick < 100; tick++) { executed.tick(); }
        long beforeRun = version(executed);
        executed.doRun();
        check(version(executed) == beforeRun + 2L
                && executed.captureAtomicTimingSnapshot().getTimesRan() == 1,
            "due execution versions countdown and completion transitions");
    }

    private static void stableOperationRetainsOneVersion() {
        GameTickEventStore store = new GameTickEventStore();
        VersionedEvent event = new VersionedEvent();
        check(store.add(event), "stable event accepted");
        long initial = version(event);
        GameTickEventStore.RestorationRegistrationFenceExecution execution =
            store.withValidatedRestorationRegistrationFence(
                scope(store), sequenceOf(store, event), 7L,
                fence -> check(fence.getLifecycleVersion() == initial
                        && !fence.isTimingStableAcrossOperation(),
                    "operation receives provisional point-in-time version"));
        check(execution.isAccepted()
                && execution.isOperationInvoked()
                && execution.isTimingStableAcrossOperation()
                && !execution.isEventLifecycleChangeDetected()
                && execution.getLifecycleVersionBeforeOperation() == initial
                && execution.getLifecycleVersionAfterOperation() == initial,
            "unchanged read-only operation closes one lifecycle window");
    }

    private static void lifecycleChangeRefusesAfterReadOnlyOperation(
            String label, LifecycleMutation mutation) {
        GameTickEventStore store = new GameTickEventStore();
        VersionedEvent event = new VersionedEvent();
        check(store.add(event), label + " event accepted");
        String scope = scope(store);
        long sequence = sequenceOf(store, event);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<
            GameTickEventStore.RestorationRegistrationFenceExecution> result =
                new AtomicReference<>();
        Thread fenced = thread(() -> result.set(
            store.withValidatedRestorationRegistrationFence(
                scope, sequence, 7L, fence -> {
                    entered.countDown();
                    await(release);
                })));
        fenced.start();
        await(entered);
        Thread changer = thread(() -> mutation.mutate(event));
        changer.start();
        check(joinWithin(changer, 1000L),
            label + " is not blocked by a retained timing monitor");
        release.countDown();
        join(fenced);
        GameTickEventStore.RestorationRegistrationFenceExecution execution =
            result.get();
        check(execution != null
                && !execution.isAccepted()
                && execution.isOperationInvoked()
                && execution.isEventLifecycleChangeDetected()
                && !execution.isTimingStableAcrossOperation()
                && execution.getReason()
                    == GameTickEventStore
                        .RestorationRegistrationFenceReason
                            .EVENT_LIFECYCLE_CHANGED_DURING_OPERATION
                && execution.getFence() == null
                && execution.getLifecycleVersionAfterOperation()
                    > execution.getLifecycleVersionBeforeOperation()
                && !execution.isCommitToken()
                && !execution.isMutationPerformed()
                && !execution.isExecutableRestoration()
                && !execution.isArrivalGate()
                && !execution.isLifecycleAuthority(),
            label + " race is detected after read-only operation");
    }

    private static long version(GameTickEvent event) {
        return event.captureAtomicTimingSnapshot().getLifecycleVersion();
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
        try { latch.await(); }
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


class LayeredMapsSliceOneHundredTwentyThreeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-event-lifecycle-version-"
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
            "EventLifecycleVersionFenceFixture.java": FIXTURE,
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

    def test_lifecycle_version_fixture_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", self.classpath,
                "com.openrsc.server.event.rsc.handler."
                "EventLifecycleVersionFenceFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_every_timing_mutator_versions_under_timing_monitor(self):
        source = EVENT.read_text(encoding="utf-8")
        self.assertIn("private long lifecycleVersion", source)
        for signature, following in (
            ("public void stop()", "public boolean isRunning()"),
            ("protected void setDelayTicks", "public void resetCountdown()"),
            ("public void resetCountdown()", "public void tick()"),
            ("public void tick()", "public long timeTillNextRun()"),
        ):
            method = source[
                source.index(signature):source.index(
                    following, source.index(signature)
                )
            ]
            self.assertIn("synchronized (timingLock)", method)
            self.assertIn("advanceLifecycleVersion()", method)
        do_run = source[
            source.index("public final long doRun()"):
            source.index("withinExecutionBoundary(")
        ]
        self.assertGreaterEqual(do_run.count("advanceLifecycleVersion()"), 2)
        snapshot = source[
            source.index("captureAtomicTimingSnapshot()"):
            source.index("private void requireLifecycleVersionAvailable()")
        ]
        self.assertIn("lifecycleVersion", snapshot)

    def test_store_compares_versions_around_operation_without_timing_lock(self):
        source = STORE.read_text(encoding="utf-8")
        method_start = source.index(
            "private static RestorationRegistrationFenceExecution\n"
            "\t\texecuteRestorationRegistrationFence("
        )
        method = source[
            method_start:
            source.index(
                "private static boolean matchesExactSceneryAffinity(",
                method_start,
            )
        ]
        first = method.index("event.captureAtomicTimingSnapshot()")
        operation = method.index("operation.execute(fence)", first)
        second = method.index("event.captureAtomicTimingSnapshot()", operation)
        mismatch = method.index(
            "EVENT_LIFECYCLE_CHANGED_DURING_OPERATION", second
        )
        self.assertLess(first, operation)
        self.assertLess(operation, second)
        self.assertLess(second, mismatch)
        self.assertNotIn("timingLock", method)
        self.assertNotIn("synchronized", method)

    def test_detection_remains_read_only_and_non_authoritative(self):
        source = STORE.read_text(encoding="utf-8")
        execution = source[
            source.index("class RestorationRegistrationFenceExecution"):]
        for required in (
            "isTimingStableAcrossOperation()",
            "isEventLifecycleChangeDetected()",
            "refusedAfterOperation(",
            "isCommitToken() { return false; }",
            "isMutationPerformed() { return false; }",
            "isExecutableRestoration() { return false; }",
            "isArrivalGate() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, execution)
        for forbidden in (
            "Region ", "GameObject ", "registerGameObject",
            "unregisterGameObject", "sendUpdatePackets",
        ):
            self.assertNotIn(forbidden, execution)

    def test_living_plan_records_slice_one_hundred_twenty_three(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 123: Event lifecycle-version detection", plan
        )
        normalized_plan = " ".join(plan.split())
        self.assertIn(
            "post-operation mismatch cannot roll back", normalized_plan
        )
        self.assertIn("Slice 107's lock-inversion fix", plan)


if __name__ == "__main__":
    unittest.main()
