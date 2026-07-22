#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/GameTickEvent.java"
STORE = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameTickEventStore.java"
)
HANDLER = ROOT / "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
INVENTORY = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredPackedRegionEventOwnershipInventory.java"
)
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
STORE_FIXTURE = ROOT / "tests/myworld/test-layered-maps-slice-ninety-five.py"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


LOGGER_STUB = r'''
package org.apache.logging.log4j;
public interface Logger {
    void error(String message, Object... arguments);
}
'''


LOG_MANAGER_STUB = r'''
package org.apache.logging.log4j;
public final class LogManager {
    private static final Logger LOGGER = new Logger() {
        public void error(String message, Object... arguments) { }
    };
    private LogManager() { }
    public static Logger getLogger() { return LOGGER; }
}
'''


MOB_STUB = r'''
package com.openrsc.server.model.entity;
public class Mob {
    public boolean isPlayer() { return false; }
    public boolean isNpc() { return false; }
}
'''


PLAYER_STUB = r'''
package com.openrsc.server.model.entity.player;
import com.openrsc.server.model.entity.Mob;
public class Player extends Mob {
    public int getIndex() { return 0; }
}
'''


NPC_STUB = r'''
package com.openrsc.server.model.entity.npc;
import com.openrsc.server.model.entity.Mob;
public class Npc extends Mob { }
'''


SERVER_STUB = r'''
package com.openrsc.server;
public class Server {
    public static final class Config { public int GAME_TICK = 640; }
    private final Config config = new Config();
    public long bench(Runnable operation) {
        operation.run();
        return 0L;
    }
    public Config getConfig() { return config; }
}
'''


WORLD_STUB = r'''
package com.openrsc.server.model.world;
import com.openrsc.server.Server;
public class World {
    private final Server server = new Server();
    public Server getServer() { return server; }
}
'''


FIXTURE = r'''
package com.openrsc.server.event.rsc;

import com.openrsc.server.model.world.World;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class AtomicEventTimingFixture {
    private static final class BlockingEvent extends GameTickEvent {
        private final Object foreignCallbackLock = new Object();
        private final CountDownLatch foreignLockOwned = new CountDownLatch(1);
        private final CountDownLatch entered = new CountDownLatch(1);

        BlockingEvent(World world) {
            super(world, null, 1L, "atomic timing fixture",
                DuplicationStrategy.ALLOW_MULTIPLE);
        }

        public void run() {
            entered.countDown();
            synchronized (foreignCallbackLock) { }
            stop();
        }
    }

    public static void main(String[] args) {
        BlockingEvent event = new BlockingEvent(new World());
        GameTickEvent.AtomicTimingSnapshot initial =
            event.captureAtomicTimingSnapshot();
        check(initial.isRunning()
                && initial.getTicksBeforeRun() == 1L
                && initial.getTimesRan() == 0,
            "constructor state is captured as one timing tuple");

        AtomicReference<GameTickEvent.AtomicTimingSnapshot> captured =
            new AtomicReference<>();
        Thread capture = new Thread(() -> {
            synchronized (event.foreignCallbackLock) {
                event.foreignLockOwned.countDown();
                await(event.entered);
                captured.set(event.captureAtomicTimingSnapshot());
            }
        });
        capture.setDaemon(true);
        capture.start();
        await(event.foreignLockOwned);

        Thread runner = new Thread(() -> event.doRun());
        runner.setDaemon(true);
        runner.start();
        boolean captureCompleted = joinWithin(capture, 1000L);
        GameTickEvent.AtomicTimingSnapshot active = captured.get();

        check(captureCompleted
                && active != null
                && active.isRunning()
                && active.getTicksBeforeRun() == 0L
                && active.getTimesRan() == 0,
            "timing capture breaks the callback lock inversion");
        join(runner);
        GameTickEvent.AtomicTimingSnapshot complete =
            event.captureAtomicTimingSnapshot();
        check(!complete.isRunning()
                && complete.getTicksBeforeRun() == 1L
                && complete.getTimesRan() == 1,
            "capture observes the complete post-callback timing tuple");
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
        check(joinWithin(thread, 5000L), "fixture thread completed");
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

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
'''


class LayeredMapsSliceOneHundredFourTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-four-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        sources = {
            "org/apache/logging/log4j/Logger.java": LOGGER_STUB,
            "org/apache/logging/log4j/LogManager.java": LOG_MANAGER_STUB,
            "com/openrsc/server/model/entity/Mob.java": MOB_STUB,
            "com/openrsc/server/model/entity/player/Player.java": PLAYER_STUB,
            "com/openrsc/server/model/entity/npc/Npc.java": NPC_STUB,
            "com/openrsc/server/Server.java": SERVER_STUB,
            "com/openrsc/server/model/world/World.java": WORLD_STUB,
            "com/openrsc/server/event/rsc/AtomicEventTimingFixture.java": FIXTURE,
        }
        paths = []
        for relative, source in sources.items():
            path = cls.temp / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding="utf-8")
            paths.append(path)
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-d", str(cls.classes),
                str(EVENT),
                str(ROOT / "server/src/com/openrsc/server/event/rsc/DuplicationStrategy.java"),
                str(ROOT / "server/src/com/openrsc/server/event/rsc/GameTickEventSpatialAffinity.java"),
                str(ROOT / "server/src/com/openrsc/server/event/rsc/GameTickEventRestorationState.java"),
                *[str(path) for path in paths],
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        if result.returncode != 0:
            raise AssertionError(result.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_event_timing_fixture_captures_without_callback_lock_inversion(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.event.rsc.AtomicEventTimingFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_event_uses_one_private_lifecycle_lock_for_timing(self):
        source = EVENT.read_text(encoding="utf-8")
        self.assertIn("private final Object executionLock", source)
        self.assertIn("private final Object timingLock", source)
        self.assertIn("captureAtomicTimingSnapshot()", source)
        self.assertIn("class AtomicTimingSnapshot", source)
        do_run = source[source.index("public final long doRun()"):
                        source.index("@Override", source.index("public final long doRun()"))]
        self.assertIn("synchronized (executionLock)", do_run)
        self.assertIn("synchronized (timingLock)", do_run)
        self.assertIn("timesRan++", do_run)
        self.assertIn("ticksBeforeRun = delayTicks", do_run)
        self.assertIn("Never hold the timing monitor", do_run)

    def test_store_binds_tick_registration_and_refuses_mixed_version(self):
        source = STORE.read_text(encoding="utf-8")
        fixture = STORE_FIXTURE.read_text(encoding="utf-8")
        self.assertIn("getTrackedEventAtomicTimingSnapshot(", source)
        self.assertIn("registrationVersion", source)
        self.assertIn("captureAtomicTimingSnapshot()", source)
        self.assertIn("Scheduler registrations changed", source)
        self.assertIn("registrationChangeRefusesAtomicTimingSnapshot", fixture)
        self.assertIn("mixed-registration atomic timing snapshot is refused", fixture)

    def test_atomic_timing_foundation_is_consumed_by_current_head(self):
        handler = HANDLER.read_text(encoding="utf-8")
        inventory = INVENTORY.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        self.assertIn("getTrackedEventAtomicTimingSnapshot(", handler)
        self.assertIn("getAtomicTimingCapturedEventCount()", inventory)
        self.assertIn("isAtomicTimingComplete()", inventory)
        self.assertIn('EVENT_SCHEMA = "layered-map-parity-event-v42"', observer)
        self.assertIn("getAtomicTimingCapturedEventCount()", observer)
        self.assertIn("isAtomicTimingCaptured()", observer)

    def test_living_plan_records_slice_one_hundred_four_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 104: Atomic scheduler event-timing foundation",
            plan,
        )
        self.assertIn("mixed-registration", plan)
        self.assertIn("No callback is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
