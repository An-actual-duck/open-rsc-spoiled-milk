#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/GameTickEvent.java"
STORE = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventStore.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


MOB_STUB = r'''
package com.openrsc.server.model.entity;
import java.util.UUID;
public class Mob {
    private final UUID uuid = UUID.randomUUID();
    public UUID getUUID() { return uuid; }
}
'''


PLAYER_STUB = r'''
package com.openrsc.server.model.entity.player;
import com.openrsc.server.model.entity.Mob;
public class Player extends Mob {
    private final long usernameHash;
    public Player(long usernameHash) { this.usernameHash = usernameHash; }
    public long getUsernameHash() { return usernameHash; }
}
'''


DUPLICATION_STUB = r'''
package com.openrsc.server.event.rsc;
public enum DuplicationStrategy {
    ALLOW_MULTIPLE,
    ONE_PER_SERVER,
    ONE_PER_MOB
}
'''


TARGET_DECISION_STUB = r'''
package com.openrsc.server.event.rsc;
public final class GameTickEventRestorationTargetDecision {
    public enum TargetOperation {
        UNAVAILABLE, SCENERY_SPAWN, SCENERY_REMOVE
    }
}
'''


TARGET_REVALIDATION_REQUEST_STUB = r'''
package com.openrsc.server.event.rsc;
public final class GameTickEventRestorationTargetRevalidationRequest {
    public static GameTickEventRestorationTargetRevalidationRequest request(
            String scheduler, long sequence, long proposal, long authored,
            boolean executionHeld, boolean storeHeld, boolean validated,
            GameTickEventRestorationTargetDecision.TargetOperation operation,
            int objectId, int permanentObjectId, int x, int y,
            int direction, int type, int regionX, int regionY,
            int ordinal, String kind) {
        return new GameTickEventRestorationTargetRevalidationRequest();
    }
}
'''


TARGET_REVALIDATION_STUB = r'''
package com.openrsc.server.event.rsc;
public final class GameTickEventRestorationTargetRevalidation {
    public boolean isRuntimeRevalidationPerformed() { return true; }
}
'''


REGION_MANAGER_STUB = r'''
package com.openrsc.server.model.world.region;
import com.openrsc.server.event.rsc.GameTickEventRestorationTargetRevalidation;
import com.openrsc.server.event.rsc.GameTickEventRestorationTargetRevalidationRequest;
public class RegionManager {
    public GameTickEventRestorationTargetRevalidation
            captureGameTickEventRestorationTargetRevalidation(
                GameTickEventRestorationTargetRevalidationRequest request) {
        return new GameTickEventRestorationTargetRevalidation();
    }
}
'''


EVENT_STUB = r'''
package com.openrsc.server.event.rsc;

import com.openrsc.server.model.entity.Mob;
import java.util.UUID;

public class GameTickEvent {
    private final Object executionLock = new Object();
    private final Mob owner;
    private final DuplicationStrategy duplicationStrategy;
    private final UUID uuid = UUID.randomUUID();
    private volatile boolean running = true;

    public GameTickEvent(Mob owner, DuplicationStrategy strategy) {
        this.owner = owner;
        this.duplicationStrategy = strategy;
    }
    public DuplicationStrategy getDuplicationStrategy() {
        return duplicationStrategy;
    }
    public UUID getUUID() { return uuid; }
    public Mob getOwner() { return owner; }
    public boolean hasOwner() { return owner != null; }
    public boolean isRunning() { return running; }
    public void stop() { running = false; }
    public void run() { }
    public long doRun() {
        return withinExecutionBoundary(() -> {
            run();
            return Long.valueOf(0L);
        }).longValue();
    }
    public <T> T withinExecutionBoundary(
            ExecutionBoundaryOperation<T> operation) {
        synchronized (executionLock) { return operation.execute(); }
    }
    public boolean isExecutionBoundaryHeldByCurrentThread() {
        return Thread.holdsLock(executionLock);
    }
    public interface ExecutionBoundaryOperation<T> { T execute(); }
    public GameTickEventRestorationState getRestorationState() {
        return GameTickEventRestorationState.unavailable();
    }
    public GameTickEventSpatialAffinity getSpatialAffinity() {
        return GameTickEventSpatialAffinity.unspecified();
    }
    public AtomicTimingSnapshot captureAtomicTimingSnapshot() {
        return new AtomicTimingSnapshot(running, 7L, 2);
    }
    public static final class AtomicTimingSnapshot {
        private final boolean running;
        private final long ticksBeforeRun;
        private final int timesRan;
        public AtomicTimingSnapshot(
                boolean running, long ticksBeforeRun, int timesRan) {
            this.running = running;
            this.ticksBeforeRun = ticksBeforeRun;
            this.timesRan = timesRan;
        }
        public boolean isRunning() { return running; }
        public long getTicksBeforeRun() { return ticksBeforeRun; }
        public int getTimesRan() { return timesRan; }
        public long getLifecycleVersion() { return 1L; }
    }
}
'''


PLUGIN_EVENT_STUB = r'''
package com.openrsc.server.event.rsc;
public class PluginTickEvent extends GameTickEvent {
    private final String pluginName;
    public PluginTickEvent(String pluginName) {
        super(null, DuplicationStrategy.ONE_PER_SERVER);
        this.pluginName = pluginName;
    }
    public String getPluginName() { return pluginName; }
}
'''


FIXTURE = r'''
package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.event.rsc.DuplicationStrategy;
import com.openrsc.server.event.rsc.GameTickEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class RegistrationFenceFixture {
    private static final class EventA extends GameTickEvent {
        EventA() { super(null, DuplicationStrategy.ONE_PER_SERVER); }
    }
    private static final class EventB extends GameTickEvent {
        EventB() { super(null, DuplicationStrategy.ONE_PER_SERVER); }
    }
    private static final class EventC extends GameTickEvent {
        EventC() { super(null, DuplicationStrategy.ONE_PER_SERVER); }
    }
    private static final class BlockingCallback extends GameTickEvent {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        BlockingCallback() {
            super(null, DuplicationStrategy.ONE_PER_SERVER);
        }
        @Override
        public void run() {
            entered.countDown();
            await(release);
        }
    }

    public static void main(String[] args) {
        exactRegistrationRunsBehindClosedFence();
        removalWaitsForAcceptedFence();
        replacementWaitsAndReceivesNewIdentity();
        callbackExecutionExcludesRemoval();
    }

    private static void exactRegistrationRunsBehindClosedFence() {
        GameTickEventStore store = new GameTickEventStore();
        EventA event = new EventA();
        check(store.add(event), "event accepted");
        GameTickEventStore.RegistrationSnapshot snapshot =
            store.getTrackedEventRegistrationSnapshot();
        String scope = snapshot.getSchedulerInstanceIdentity();
        long sequence = sequenceOf(store, event);
        AtomicBoolean operationRan = new AtomicBoolean();
        GameTickEventStore.RegistrationFenceExecution accepted =
            store.withValidatedRegistrationFence(
                event, scope, sequence, fence -> {
                    operationRan.set(true);
                    check(fence.getSchedulerInstanceIdentity().equals(scope)
                            && fence.getRegistrationSequence() == sequence
                            && fence.isEventExecutionBoundaryHeld()
                            && !fence.isSchedulerStoreBoundaryHeld()
                            && fence
                                .isRegistrationValidatedBeforeInnerBoundary()
                            && event
                                .isExecutionBoundaryHeldByCurrentThread(),
                        "accepted operation runs behind the closed fence");
                });
        check(operationRan.get()
                && accepted.isAccepted()
                && accepted.getReason()
                    == GameTickEventStore.RegistrationFenceReason
                        .OPERATION_COMPLETED
                && accepted.getFence() != null
                && !accepted.isCommitToken()
                && !accepted.isMutationPerformed()
                && !accepted.isExecutableRestoration()
                && !accepted.isArrivalGate()
                && !accepted.isLifecycleAuthority(),
            "accepted fence remains non-authoritative");

        operationRan.set(false);
        check(store.withValidatedRegistrationFence(
                event, "different-scope", sequence,
                fence -> operationRan.set(true))
                .getReason()
                == GameTickEventStore.RegistrationFenceReason
                    .SCHEDULER_INSTANCE_MISMATCH
                && !operationRan.get(),
            "scheduler mismatch refuses before the operation");
        check(store.withValidatedRegistrationFence(
                event, scope, sequence + 1L, fence -> { })
                .getReason()
                == GameTickEventStore.RegistrationFenceReason
                    .REGISTRATION_SEQUENCE_MISMATCH,
            "registration mismatch refuses before the operation");
        store.remove(event);
        check(store.withValidatedRegistrationFence(
                event, scope, sequence, fence -> { })
                .getReason()
                == GameTickEventStore.RegistrationFenceReason
                    .EVENT_NOT_REGISTERED,
            "removed registration refuses before the operation");
    }

    private static void removalWaitsForAcceptedFence() {
        GameTickEventStore store = new GameTickEventStore();
        EventB event = new EventB();
        check(store.add(event), "removal event accepted");
        GameTickEventStore.RegistrationSnapshot snapshot =
            store.getTrackedEventRegistrationSnapshot();
        String scope = snapshot.getSchedulerInstanceIdentity();
        long sequence = sequenceOf(store, event);
        CountDownLatch fenceEntered = new CountDownLatch(1);
        CountDownLatch releaseFence = new CountDownLatch(1);
        CountDownLatch removalStarted = new CountDownLatch(1);
        CountDownLatch removalFinished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread fenced = thread(() -> {
            try {
                store.withValidatedRegistrationFence(
                    event, scope, sequence, fence -> {
                        fenceEntered.countDown();
                        await(releaseFence);
                    });
            } catch (Throwable caught) {
                failure.set(caught);
            }
        });
        fenced.start();
        await(fenceEntered);
        Thread remover = thread(() -> {
            removalStarted.countDown();
            store.remove(event);
            removalFinished.countDown();
        });
        remover.start();
        await(removalStarted);
        check(!awaitWithin(removalFinished, 200L),
            "removal cannot cross the accepted fence");
        releaseFence.countDown();
        join(fenced);
        join(remover);
        check(failure.get() == null
                && store.getTrackedEventRegistrations().isEmpty(),
            "removal completes only after the fence closes");
    }

    private static void replacementWaitsAndReceivesNewIdentity() {
        GameTickEventStore store = new GameTickEventStore();
        EventC original = new EventC();
        EventC replacement = new EventC();
        check(store.add(original), "replacement original accepted");
        original.stop();
        GameTickEventStore.RegistrationSnapshot snapshot =
            store.getTrackedEventRegistrationSnapshot();
        String scope = snapshot.getSchedulerInstanceIdentity();
        long originalSequence = sequenceOf(store, original);
        CountDownLatch fenceEntered = new CountDownLatch(1);
        CountDownLatch releaseFence = new CountDownLatch(1);
        CountDownLatch replacementStarted = new CountDownLatch(1);
        CountDownLatch replacementFinished = new CountDownLatch(1);
        AtomicBoolean replacementAccepted = new AtomicBoolean();
        Thread fenced = thread(() -> store.withValidatedRegistrationFence(
            original, scope, originalSequence, fence -> {
                fenceEntered.countDown();
                await(releaseFence);
            }));
        fenced.start();
        await(fenceEntered);
        Thread replacer = thread(() -> {
            replacementStarted.countDown();
            replacementAccepted.set(store.addOrUpdate(replacement));
            replacementFinished.countDown();
        });
        replacer.start();
        await(replacementStarted);
        check(!awaitWithin(replacementFinished, 200L),
            "replacement cannot cross the accepted fence");
        releaseFence.countDown();
        join(fenced);
        join(replacer);
        long replacementSequence = sequenceOf(store, replacement);
        check(replacementAccepted.get()
                && replacementSequence > originalSequence
                && store.withValidatedRegistrationFence(
                    original, scope, originalSequence, fence -> { })
                    .getReason()
                    == GameTickEventStore.RegistrationFenceReason
                        .EVENT_NOT_REGISTERED
                && store.withValidatedRegistrationFence(
                    replacement, scope, replacementSequence,
                    fence -> { }).isAccepted(),
            "replacement receives a distinct validated registration");
    }

    private static void callbackExecutionExcludesRemoval() {
        GameTickEventStore store = new GameTickEventStore();
        BlockingCallback event = new BlockingCallback();
        check(store.add(event), "callback event accepted");
        Thread callback = thread(event::doRun);
        callback.start();
        await(event.entered);
        CountDownLatch removalStarted = new CountDownLatch(1);
        CountDownLatch removalFinished = new CountDownLatch(1);
        Thread remover = thread(() -> {
            removalStarted.countDown();
            store.remove(event);
            removalFinished.countDown();
        });
        remover.start();
        await(removalStarted);
        check(!awaitWithin(removalFinished, 200L),
            "callback execution excludes concurrent removal");
        event.release.countDown();
        join(callback);
        join(remover);
        check(store.getTrackedEventRegistrations().isEmpty(),
            "removal completes after callback execution exits");
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
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
    private static boolean awaitWithin(
            CountDownLatch latch, long millis) {
        try {
            return latch.await(millis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
    private static void join(Thread thread) {
        try {
            thread.join(5000L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
        check(!thread.isAlive(), "fixture thread completed");
    }
    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
'''


class LayeredMapsSliceOneHundredTwentyOneTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-registration-fence-"
        )
        cls.root = Path(cls.temp_dir.name)
        cls.classes = cls.root / "classes"
        cls.classes.mkdir()
        sources = {
            "com/openrsc/server/model/entity/Mob.java": MOB_STUB,
            "com/openrsc/server/model/entity/player/Player.java": PLAYER_STUB,
            "com/openrsc/server/event/rsc/DuplicationStrategy.java":
                DUPLICATION_STUB,
            "com/openrsc/server/event/rsc/GameTickEvent.java": EVENT_STUB,
            "com/openrsc/server/event/rsc/PluginTickEvent.java":
                PLUGIN_EVENT_STUB,
            "com/openrsc/server/model/world/region/RegionManager.java":
                REGION_MANAGER_STUB,
            "com/openrsc/server/event/rsc/"
            "GameTickEventRestorationTargetDecision.java":
                TARGET_DECISION_STUB,
            "com/openrsc/server/event/rsc/"
            "GameTickEventRestorationTargetRevalidationRequest.java":
                TARGET_REVALIDATION_REQUEST_STUB,
            "com/openrsc/server/event/rsc/"
            "GameTickEventRestorationTargetRevalidation.java":
                TARGET_REVALIDATION_STUB,
            "com/openrsc/server/event/rsc/handler/"
            "RegistrationFenceFixture.java": FIXTURE,
        }
        paths = []
        for relative, source in sources.items():
            path = cls.root / relative
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
                "-cp", classpath, "-d", str(cls.classes), str(STORE),
                str(ROOT / (
                    "server/src/com/openrsc/server/event/rsc/"
                    "GameTickEventRestorationState.java"
                )),
                str(ROOT / (
                    "server/src/com/openrsc/server/event/rsc/"
                    "GameTickEventSpatialAffinity.java"
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

    def test_registration_fence_fixture_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", self.classpath,
                "com.openrsc.server.event.rsc.handler."
                "RegistrationFenceFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_real_event_exposes_only_scoped_execution_boundary(self):
        source = EVENT.read_text(encoding="utf-8")
        method = source[
            source.index("withinExecutionBoundary("):
            source.index("@Override", source.index("withinExecutionBoundary("))
        ]
        self.assertIn("synchronized (executionLock)", method)
        self.assertIn("Thread.holdsLock(executionLock)", method)
        self.assertNotIn("return executionLock", method)
        self.assertNotIn("getExecutionLock", source)

    def test_store_orders_event_boundary_before_store_boundary(self):
        source = STORE.read_text(encoding="utf-8")
        add = source[
            source.index("public boolean add(GameTickEvent event)"):
            source.index("public boolean addOrUpdate(GameTickEvent event)")
        ]
        update = source[
            source.index("public boolean addOrUpdate(GameTickEvent event)"):
            source.index("public boolean eventIsContained(GameTickEvent event)")
        ]
        remove = source[
            source.index("public void remove(GameTickEvent event)"):
            source.index("public Collection<GameTickEvent> getPlayerEvents")
        ]
        self.assertLess(
            add.index("withinExecutionBoundary"),
            add.index("synchronized (LOCK)"),
        )
        update_boundary = update.index(
            "replacement.withinExecutionBoundary"
        )
        self.assertLess(
            update_boundary,
            update.index("synchronized (LOCK)", update_boundary),
        )
        replacement_boundary = update.index(
            "existing.withinExecutionBoundary"
        )
        self.assertLess(
            replacement_boundary,
            update.index("unregisterAccepted", replacement_boundary),
        )
        remove_boundary = remove.index(
            "registered.withinExecutionBoundary"
        )
        self.assertLess(
            remove_boundary,
            remove.index("synchronized (LOCK)", remove_boundary),
        )
        self.assertIn(
            "Registration requires event boundary before store boundary",
            source,
        )
        self.assertIn(
            "Removal requires event boundary before store boundary", source
        )

    def test_validated_operation_releases_store_boundary_and_stays_inert(self):
        source = STORE.read_text(encoding="utf-8")
        start = source.index("withValidatedRegistrationFence(")
        end = source.index(
            "withValidatedRestorationRegistrationFence(", start
        )
        method = source[start:end]
        self.assertLess(
            method.index("synchronized (LOCK)"),
            method.index("RegistrationFence fence"),
        )
        self.assertLess(
            method.index("RegistrationFence fence"),
            method.index("checkedOperation.execute(fence)"),
        )
        self.assertIn("Thread.holdsLock(LOCK)", method)
        for forbidden in (
            "Region", "GameObject", "registerGameObject",
            "unregisterGameObject", ".run()", ".doRun()", ".stop()",
            "sendUpdatePackets",
        ):
            self.assertNotIn(forbidden, method)
        result = source[source.index("class RegistrationFenceExecution"):]
        for required in (
            "isCommitToken() { return false; }",
            "isMutationPerformed() { return false; }",
            "isExecutableRestoration() { return false; }",
            "isArrivalGate() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, result)

    def test_living_plan_records_slice_one_hundred_twenty_one(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 121: Scheduler registration execution fence", plan
        )
        self.assertIn("automated concurrency fixture", plan)
        self.assertIn("no Region object boundary", plan)


if __name__ == "__main__":
    unittest.main()
