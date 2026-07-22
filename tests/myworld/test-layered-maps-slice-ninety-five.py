#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
STORE = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventStore.java"
)
HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
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


EVENT_STUB = r'''
package com.openrsc.server.event.rsc;

import com.openrsc.server.model.entity.Mob;
import java.util.UUID;

public class GameTickEvent {
    private final Object executionLock = new Object();
    private final Mob owner;
    private final DuplicationStrategy duplicationStrategy;
    private final UUID uuid = UUID.randomUUID();
    private boolean running = true;

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
    public <T> T withinExecutionBoundary(ExecutionBoundaryOperation<T> operation) {
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
import com.openrsc.server.model.entity.player.Player;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class GameTickEventRegistrationFixture {
    private static final class ServerEventA extends GameTickEvent {
        ServerEventA() { super(null, DuplicationStrategy.ONE_PER_SERVER); }
    }
    private static final class ServerEventB extends GameTickEvent {
        ServerEventB() { super(null, DuplicationStrategy.ONE_PER_SERVER); }
    }
    private static final class ServerEventC extends GameTickEvent {
        ServerEventC() { super(null, DuplicationStrategy.ONE_PER_SERVER); }
    }
    private static final class PlayerEvent extends GameTickEvent {
        PlayerEvent(Player owner) {
            super(owner, DuplicationStrategy.ONE_PER_MOB);
        }
    }
    private static final class BlockingTimingEvent extends GameTickEvent {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        BlockingTimingEvent() {
            super(null, DuplicationStrategy.ONE_PER_SERVER);
        }
        @Override
        public AtomicTimingSnapshot captureAtomicTimingSnapshot() {
            entered.countDown();
            await(release);
            return super.captureAtomicTimingSnapshot();
        }
    }

    public static void main(String[] args) {
        acceptedAndRejectedAddsKeepStableIdentity();
        removalAndReplacementCreateNewRegistrations();
        schedulerInstanceIdentityScopesAtomicSnapshots();
        atomicTimingBindsTickRegistrationAndState();
        registrationChangeRefusesAtomicTimingSnapshot();
    }

    private static void acceptedAndRejectedAddsKeepStableIdentity() {
        GameTickEventStore store = new GameTickEventStore();
        ServerEventA first = new ServerEventA();
        ServerEventA duplicate = new ServerEventA();
        ServerEventB second = new ServerEventB();
        check(store.add(first), "first add accepted");
        check(!store.add(duplicate), "duplicate rejected");
        check(store.add(second), "different event accepted");

        List<GameTickEventStore.RegisteredEvent> firstSnapshot =
            store.getTrackedEventRegistrations();
        List<GameTickEventStore.RegisteredEvent> secondSnapshot =
            store.getTrackedEventRegistrations();
        check(firstSnapshot.size() == 2
            && firstSnapshot.get(0).getEvent() == first
            && firstSnapshot.get(0).getRegistrationSequence() == 1L
            && firstSnapshot.get(1).getEvent() == second
            && firstSnapshot.get(1).getRegistrationSequence() == 2L,
            "accepted registrations use canonical monotonic order");
        check(secondSnapshot.get(0).getRegistrationSequence()
                == firstSnapshot.get(0).getRegistrationSequence()
            && secondSnapshot.get(1).getRegistrationSequence()
                == firstSnapshot.get(1).getRegistrationSequence(),
            "repeated snapshots preserve registration identity");
        expectUnsupported(() -> firstSnapshot.clear());
    }

    private static void removalAndReplacementCreateNewRegistrations() {
        GameTickEventStore store = new GameTickEventStore();
        ServerEventA first = new ServerEventA();
        ServerEventA equivalentHandle = new ServerEventA();
        ServerEventB second = new ServerEventB();
        check(store.add(first) && store.add(second), "baseline accepted");

        store.remove(equivalentHandle);
        List<GameTickEventStore.RegisteredEvent> afterRemoval =
            store.getTrackedEventRegistrations();
        check(afterRemoval.size() == 1
            && afterRemoval.get(0).getEvent() == second
            && afterRemoval.get(0).getRegistrationSequence() == 2L,
            "key-equivalent removal clears the registered instance");
        check(store.add(first), "removed instance can register again");
        check(sequenceOf(store, first) == 3L,
            "re-registration receives a new identity");

        ServerEventC original = new ServerEventC();
        ServerEventC replacement = new ServerEventC();
        check(store.add(original), "original accepted");
        check(!store.addOrUpdate(replacement),
            "running registration refuses replacement");
        check(sequenceOf(store, original) == 4L,
            "rejected replacement does not consume identity");
        original.stop();
        check(store.addOrUpdate(replacement), "stopped event replaced");
        check(sequenceOf(store, replacement) == 5L,
            "replacement receives a new identity");

        Player owner = new Player(1234L);
        PlayerEvent playerEvent = new PlayerEvent(owner);
        check(store.add(playerEvent), "player event accepted");
        check(sequenceOf(store, playerEvent) == 6L
            && store.getPlayerEvents(owner).contains(playerEvent),
            "identity bookkeeping preserves player index behavior");
    }

    private static void schedulerInstanceIdentityScopesAtomicSnapshots() {
        GameTickEventStore firstStore = new GameTickEventStore();
        check(firstStore.add(new ServerEventA()), "snapshot event accepted");
        GameTickEventStore.RegistrationSnapshot first =
            firstStore.getTrackedEventRegistrationSnapshot();
        GameTickEventStore.RegistrationSnapshot repeated =
            firstStore.getTrackedEventRegistrationSnapshot();
        GameTickEventStore.RegistrationSnapshot other =
            new GameTickEventStore().getTrackedEventRegistrationSnapshot();
        String identity = first.getSchedulerInstanceIdentity();
        check(identity.matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"),
            "scheduler-instance identity is canonical opaque text");
        check(identity.equals(repeated.getSchedulerInstanceIdentity()),
            "one store lifetime keeps one scheduler-instance identity");
        check(!identity.equals(other.getSchedulerInstanceIdentity()),
            "different stores have different scheduler-instance identities");
        check(first.getRegistrations().size() == 1
                && first.getRegistrations().get(0).getRegistrationSequence() == 1L,
            "instance identity and registrations share one atomic snapshot");
        expectUnsupported(() -> first.getRegistrations().clear());
    }

    private static void atomicTimingBindsTickRegistrationAndState() {
        GameTickEventStore store = new GameTickEventStore();
        ServerEventA event = new ServerEventA();
        check(store.add(event), "timed snapshot event accepted");
        GameTickEventStore.StoreAtomicTimingSnapshot snapshot =
            store.getTrackedEventAtomicTimingSnapshot(77L);
        check(snapshot.getObservedAtTick() == 77L
                && snapshot.getSchedulerInstanceIdentity().matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                && snapshot.getRegistrations().size() == 1,
            "store scope and observation tick bind one timing snapshot");
        GameTickEventStore.AtomicTimedRegisteredEvent timed =
            snapshot.getRegistrations().get(0);
        check(timed.getEvent() == event
                && timed.getRegistrationSequence() == 1L
                && timed.getTiming().isRunning()
                && timed.getTiming().getTicksBeforeRun() == 7L
                && timed.getTiming().getTimesRan() == 2,
            "registration and event-local timing remain correlated");
        expectUnsupported(() -> snapshot.getRegistrations().clear());
        expectIllegal(() -> store.getTrackedEventAtomicTimingSnapshot(-1L));
    }

    private static void registrationChangeRefusesAtomicTimingSnapshot() {
        GameTickEventStore store = new GameTickEventStore();
        BlockingTimingEvent blocking = new BlockingTimingEvent();
        check(store.add(blocking), "blocking timing event accepted");
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread capture = new Thread(() -> {
            try {
                store.getTrackedEventAtomicTimingSnapshot(88L);
            } catch (Throwable caught) {
                failure.set(caught);
            }
        });
        capture.start();
        await(blocking.entered);
        check(store.add(new ServerEventA()),
            "registration set changes during event-local capture");
        blocking.release.countDown();
        join(capture);
        check(failure.get() instanceof IllegalStateException,
            "mixed-registration atomic timing snapshot is refused");
    }

    private static long sequenceOf(
        GameTickEventStore store,
        GameTickEvent expected) {
        for (GameTickEventStore.RegisteredEvent registration
                : store.getTrackedEventRegistrations()) {
            if (registration.getEvent() == expected) {
                return registration.getRegistrationSequence();
            }
        }
        throw new AssertionError("registration not found");
    }

    private static void expectUnsupported(Runnable action) {
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            return;
        }
        throw new AssertionError("expected UnsupportedOperationException");
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("expected IllegalArgumentException");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
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
        check(!thread.isAlive(), "timing capture thread completed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
'''


class LayeredMapsSliceNinetyFiveTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-event-registration-"
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
            "com/openrsc/server/event/rsc/handler/"
            "GameTickEventRegistrationFixture.java": FIXTURE,
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
                "javac", "-cp", classpath, "-d", str(cls.classes),
                str(STORE),
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

    def test_registration_fixture(self):
        result = subprocess.run(
            [
                "java", "-cp", self.classpath,
                "com.openrsc.server.event.rsc.handler."
                "GameTickEventRegistrationFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_identity_is_store_local_and_not_existing_uuid_or_key(self):
        source = STORE.read_text(encoding="utf-8")
        self.assertIn("IdentityHashMap", source)
        self.assertIn("nextRegistrationSequence", source)
        self.assertIn("getTrackedEventRegistrations()", source)
        boundary = source[
            source.index("List<RegisteredEvent> getTrackedEventRegistrations()"):
            source.index("private void registerAccepted")
        ]
        for forbidden in (
            "getUUID()", "getDescriptor()", "GameTickKey", "ownerUUID",
            "event.stop()", "remove(event)", "doRun()",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_registration_identity_reaches_inventory_and_private_observer(self):
        handler = HANDLER.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        self.assertIn("getTrackedEventAtomicTimingSnapshot(", handler)
        self.assertIn("timingSnapshot.getRegistrations()", handler)
        self.assertIn("getRegistrationSequence()", handler)
        self.assertNotIn("getTrackedEventRegistrations()", observer)
        self.assertIn("getRegistrationSequence()", observer)

    def test_living_plan_records_slice_ninety_five_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 95: Scheduler-local event registration identity",
            plan,
        )
        self.assertIn("rejected duplicate", plan)
        self.assertIn("process-local", plan)
        self.assertIn("No event is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
