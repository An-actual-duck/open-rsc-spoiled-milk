#!/usr/bin/env python3
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
COMMIT_REQUEST = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationCommitRequest.java"
)
ONE_SHOT_CONTRACT = ROOT / (
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


MOB_STUB = r'''
package com.openrsc.server.model.entity;
import java.util.UUID;
public class Mob {
    private final UUID uuid = UUID.randomUUID();
    public UUID getUUID() { return uuid; }
    public boolean isPlayer() { return false; }
    public boolean isNpc() { return false; }
}
'''


PLAYER_STUB = r'''
package com.openrsc.server.model.entity.player;
import com.openrsc.server.model.entity.Mob;
public class Player extends Mob {
    private final long usernameHash;
    public Player(long usernameHash) { this.usernameHash = usernameHash; }
    public long getUsernameHash() { return usernameHash; }
    public int getIndex() { return 0; }
    @Override public boolean isPlayer() { return true; }
}
'''


NPC_STUB = r'''
package com.openrsc.server.model.entity.npc;
import com.openrsc.server.model.entity.Mob;
public class Npc extends Mob {
    @Override public boolean isNpc() { return true; }
}
'''


SERVER_STUB = r'''
package com.openrsc.server;
public class Server {
    public static final class Config { public int GAME_TICK = 640; }
    private final Config config = new Config();
    public long bench(Runnable operation) { operation.run(); return 0L; }
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


PLUGIN_STUB = r'''
package com.openrsc.server.event.rsc;
public class PluginTickEvent extends GameTickEvent {
    private final String pluginName;
    public PluginTickEvent(String pluginName) {
        super(null, null, 1L, "plugin", DuplicationStrategy.ONE_PER_SERVER);
        this.pluginName = pluginName;
    }
    public String getPluginName() { return pluginName; }
    public void run() { }
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
            int direction, int type, boolean forceFullBlock,
            int regionX, int regionY,
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
import com.openrsc.server.event.rsc.GameTickEventRestorationCommitRequest;
import com.openrsc.server.event.rsc.GameTickEventRestorationTargetRevalidation;
import com.openrsc.server.event.rsc.GameTickEventRestorationTargetRevalidationRequest;
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
	public RestorationCommitResult applyGameTickEventRestorationCommitRequest(
			GameTickEventRestorationCommitRequest request) {
		return new RestorationCommitResult();
	}
    public GameTickEventRestorationTargetRevalidation
            captureGameTickEventRestorationTargetRevalidation(
                GameTickEventRestorationTargetRevalidationRequest request) {
        return new GameTickEventRestorationTargetRevalidation();
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RestorationRegistrationFenceFixture {
    private static final class RestorationEvent extends GameTickEvent {
        private final GameTickEventRestorationState restoration;
        private final GameTickEventSpatialAffinity affinity;
        RestorationEvent(
                GameTickEventRestorationState restoration,
                GameTickEventSpatialAffinity affinity) {
            super(new World(), null, 100L, "restoration-fence-fixture",
                DuplicationStrategy.ALLOW_MULTIPLE);
            this.restoration = restoration;
            this.affinity = affinity;
        }
        public void run() { }
        @Override public GameTickEventRestorationState getRestorationState() {
            return restoration;
        }
        @Override public GameTickEventSpatialAffinity getSpatialAffinity() {
            return affinity;
        }
    }

    public static void main(String[] args) {
        exactRegistrationIsLocatedWithoutCallerHandle();
        everyIncompleteOrStaleStateRefusesClosed();
        handleFreeOperationStillExcludesRemoval();
    }

    private static void exactRegistrationIsLocatedWithoutCallerHandle() {
        GameTickEventStore store = new GameTickEventStore();
        RestorationEvent event = event(spawnState(7L, null, 0), exact());
        check(store.add(event), "authored spawn accepted");
        String scope = scope(store);
        long sequence = sequenceOf(store, event);
        AtomicBoolean operationRan = new AtomicBoolean();
        GameTickEventStore.RestorationRegistrationFenceExecution execution =
            store.withValidatedRestorationRegistrationFence(
                scope, sequence, 7L, fence -> {
                    operationRan.set(true);
                    check(fence.getSchedulerInstanceIdentity().equals(scope)
                            && fence.getRegistrationSequence() == sequence
                            && fence.isEventExecutionBoundaryHeld()
                            && !fence.isSchedulerStoreBoundaryHeld()
                            && fence
                                .isRegistrationValidatedBeforeInnerBoundary()
                            && fence.getRestorationKind()
                                == GameTickEventStore.RestorationKind
                                    .SCENERY_SPAWN
                            && fence.getExpectedProposalGeneration() == 7L
                            && fence.getObservedAuthoredGeneration() == 7L
                            && fence.getTicksBeforeRun() == 100L
                            && fence.getTimesRan() == 0
                            && fence.isAtomicTimingCaptured()
                            && !fence.isTimingStableAcrossOperation()
                            && !fence.isEventCancellationExcluded()
                            && fence.getObjectId() == 310
                            && fence.getPermanentObjectId() == 310
                            && fence.getX() == 524 && fence.getY() == 489
                            && fence.getDirection() == 0
                            && fence.getType() == 0
                            && !fence.isForceFullBlock()
                            && fence.getAuthoredPackedRegionX() == 10
                            && fence.getAuthoredPackedRegionY() == 10
                            && fence.getAuthoredSourceOrdinal() == 22
                            && fence.getAuthoredConstructionKind()
                                == GameTickEventStore
                                    .AuthoredConstructionKind.SCENERY
                            && fence.isSpatialAffinityValidated()
                            && fence.isAuthoredGenerationValidated()
                            && !fence.isOwnerStateRetained()
                            && !fence.isRuntimeAttributeStateRetained()
                            && !fence.isEventHandleRetained()
                            && !fence.isStoreHandleRetained(),
                        "operation receives only validated detached facts");
                });
        check(operationRan.get() && execution.isAccepted()
                && execution.getReason()
                    == GameTickEventStore
                        .RestorationRegistrationFenceReason
                            .OPERATION_COMPLETED
                && execution.getFence() != null
                && execution.isOperationInvoked()
                && execution.isTimingStableAcrossOperation()
                && !execution.isEventLifecycleChangeDetected()
                && execution.getLifecycleVersionBeforeOperation()
                    == execution.getLifecycleVersionAfterOperation()
                && execution.getLifecycleVersionBeforeOperation()
                    == execution.getFence().getLifecycleVersion()
                && !execution.isRuntimeTargetLookupPerformed()
                && !execution.isRuntimeRevalidationPerformed()
                && !execution.isCommitToken()
                && !execution.isMutationPerformed()
                && !execution.isExecutableRestoration()
                && !execution.isArrivalGate()
                && !execution.isLifecycleAuthority(),
            "accepted generation fence remains non-authoritative");
        store.remove(event);
        check(store.withValidatedRestorationRegistrationFence(
                scope, sequence, 7L, fence -> { }).getReason()
                == GameTickEventStore.RestorationRegistrationFenceReason
                    .EVENT_NOT_REGISTERED,
            "removed registration refuses without invoking operation");

        RestorationEvent removal = event(removeState(9L), exact());
        check(store.add(removal), "authored removal accepted");
        check(store.withValidatedRestorationRegistrationFence(
                scope, sequenceOf(store, removal), 9L,
                fence -> check(fence.getRestorationKind()
                    == GameTickEventStore.RestorationKind.SCENERY_REMOVE,
                    "remove kind remains exact")).isAccepted(),
            "authored remove generation fence is representable");
    }

    private static void everyIncompleteOrStaleStateRefusesClosed() {
        assertRefusal(
            GameTickEventRestorationState.unavailable(), exact(), 7L,
            GameTickEventStore.RestorationRegistrationFenceReason
                .RESTORATION_STATE_UNAVAILABLE);
        assertRefusal(
            spawnStateWithoutAuthored(), exact(), 7L,
            GameTickEventStore.RestorationRegistrationFenceReason
                .AUTHORED_IDENTITY_MISSING);
        assertRefusal(
            removeStateWithoutAuthored(), exact(), 7L,
            GameTickEventStore.RestorationRegistrationFenceReason
                .RESTORATION_PAYLOAD_INCOMPLETE);
        assertRefusal(
            spawnState(7L, "private-owner", 0), exact(), 7L,
            GameTickEventStore.RestorationRegistrationFenceReason
                .OWNER_BOUND_STATE_REFUSED);
        assertRefusal(
            spawnState(7L, null, 1), exact(), 7L,
            GameTickEventStore.RestorationRegistrationFenceReason
                .RUNTIME_ATTRIBUTE_STATE_INCOMPLETE);
        assertRefusal(
            spawnStateWithConstructionKind(
                AuthoredConstructionKind.NPC_SPAWN), exact(), 7L,
            GameTickEventStore.RestorationRegistrationFenceReason
                .AUTHORED_CONSTRUCTION_KIND_MISMATCH);
        assertRefusal(
            spawnState(7L, null, 0),
            GameTickEventSpatialAffinity.unspecified(), 7L,
            GameTickEventStore.RestorationRegistrationFenceReason
                .SPATIAL_AFFINITY_MISMATCH);
        assertRefusal(
            spawnState(7L, null, 0), exact(), 8L,
            GameTickEventStore.RestorationRegistrationFenceReason
                .PROPOSAL_GENERATION_MISMATCH);

        GameTickEventStore stoppedStore = new GameTickEventStore();
        RestorationEvent stopped = event(spawnState(7L, null, 0), exact());
        check(stoppedStore.add(stopped), "stopped event accepted");
        stopped.stop();
        check(stoppedStore.withValidatedRestorationRegistrationFence(
                scope(stoppedStore), sequenceOf(stoppedStore, stopped), 7L,
                fence -> { }).getReason()
                == GameTickEventStore.RestorationRegistrationFenceReason
                    .EVENT_NOT_RUNNING,
            "stopped callback refuses");

        GameTickEventStore executedStore = new GameTickEventStore();
        RestorationEvent executed = event(spawnState(7L, null, 0), exact());
        check(executedStore.add(executed), "executed event accepted");
        for (int tick = 0; tick < 100; tick++) { executed.tick(); }
        executed.doRun();
        check(executedStore.withValidatedRestorationRegistrationFence(
                scope(executedStore), sequenceOf(executedStore, executed),
                7L, fence -> { }).getReason()
                == GameTickEventStore.RestorationRegistrationFenceReason
                    .EVENT_ALREADY_EXECUTED,
            "already-executed callback refuses");

        GameTickEventStore store = new GameTickEventStore();
        RestorationEvent valid = event(spawnState(7L, null, 0), exact());
        check(store.add(valid), "scope refusal event accepted");
        String scope = scope(store);
        long sequence = sequenceOf(store, valid);
        check(store.withValidatedRestorationRegistrationFence(
                "different-scope", sequence, 7L, fence -> { }).getReason()
                == GameTickEventStore.RestorationRegistrationFenceReason
                    .SCHEDULER_INSTANCE_MISMATCH,
            "wrong scheduler scope refuses");
        check(store.withValidatedRestorationRegistrationFence(
                scope, sequence + 1L, 7L, fence -> { }).getReason()
                == GameTickEventStore.RestorationRegistrationFenceReason
                    .EVENT_NOT_REGISTERED,
            "unknown registration refuses");
    }

    private static void handleFreeOperationStillExcludesRemoval() {
        GameTickEventStore store = new GameTickEventStore();
        RestorationEvent event = event(spawnState(7L, null, 0), exact());
        check(store.add(event), "concurrency event accepted");
        String scope = scope(store);
        long sequence = sequenceOf(store, event);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch removeStarted = new CountDownLatch(1);
        CountDownLatch removeFinished = new CountDownLatch(1);
        Thread fenced = thread(() ->
            store.withValidatedRestorationRegistrationFence(
                scope, sequence, 7L, fence -> {
                    entered.countDown();
                    await(release);
                }));
        fenced.start();
        await(entered);
        Thread remover = thread(() -> {
            removeStarted.countDown();
            store.remove(event);
            removeFinished.countDown();
        });
        remover.start();
        await(removeStarted);
        check(!awaitWithin(removeFinished, 200L),
            "handle-free generation operation retains outer fence");
        release.countDown();
        join(fenced);
        join(remover);
        check(store.getTrackedEventRegistrations().isEmpty(),
            "removal completes after generation operation");
    }

    private static void assertRefusal(
            GameTickEventRestorationState state,
            GameTickEventSpatialAffinity affinity,
            long expectedGeneration,
            GameTickEventStore.RestorationRegistrationFenceReason reason) {
        GameTickEventStore store = new GameTickEventStore();
        RestorationEvent event = event(state, affinity);
        check(store.add(event), "refusal fixture event accepted");
        AtomicBoolean invoked = new AtomicBoolean();
        GameTickEventStore.RestorationRegistrationFenceExecution execution =
            store.withValidatedRestorationRegistrationFence(
                scope(store), sequenceOf(store, event), expectedGeneration,
                fence -> invoked.set(true));
        check(execution.getReason() == reason && !execution.isAccepted()
                && execution.getFence() == null && !invoked.get(),
            "incomplete or stale state refuses before operation: " + reason);
    }

    private static RestorationEvent event(
            GameTickEventRestorationState state,
            GameTickEventSpatialAffinity affinity) {
        return new RestorationEvent(state, affinity);
    }
    private static GameTickEventSpatialAffinity exact() {
        return GameTickEventSpatialAffinity.exactFixedLocation(524, 489);
    }
    private static GameTickEventRestorationState spawnState(
            long generation, String owner, int runtimeAttributes) {
        return GameTickEventRestorationState.scenerySpawn(
            scenery(generation, owner, runtimeAttributes), false);
    }
    private static GameTickEventRestorationState removeState(long generation) {
        return GameTickEventRestorationState.sceneryRemove(
            scenery(generation, null, 0));
    }
    private static GameTickEventRestorationState spawnStateWithoutAuthored() {
        return GameTickEventRestorationState.scenerySpawn(
            SceneryState.of(310, 310, 524, 489, 0, 0, null, 0, null),
            false);
    }
    private static GameTickEventRestorationState removeStateWithoutAuthored() {
        return GameTickEventRestorationState.sceneryRemove(
            SceneryState.of(310, 310, 524, 489, 0, 0, null, 0, null));
    }
    private static GameTickEventRestorationState
            spawnStateWithConstructionKind(AuthoredConstructionKind kind) {
        return GameTickEventRestorationState.scenerySpawn(
            SceneryState.of(
                310, 310, 524, 489, 0, 0, null, 0,
                AuthoredPlacementState.of(7L, 10, 10, 22, kind)),
            false);
    }
    private static SceneryState scenery(
            long generation, String owner, int runtimeAttributes) {
        return SceneryState.of(
            310, 310, 524, 489, 0, 0, owner, runtimeAttributes,
            AuthoredPlacementState.of(
                generation, 10, 10, 22, AuthoredConstructionKind.SCENERY));
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
    private static boolean awaitWithin(CountDownLatch latch, long millis) {
        try { return latch.await(millis, TimeUnit.MILLISECONDS); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
    private static void join(Thread thread) {
        try { thread.join(5000L); }
        catch (InterruptedException interrupted) {
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


class LayeredMapsSliceOneHundredTwentyTwoTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-restoration-registration-fence-"
        )
        cls.temp = Path(cls.temp_dir.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        sources = {
            "com/openrsc/server/model/entity/Mob.java": MOB_STUB,
            "com/openrsc/server/model/entity/player/Player.java": PLAYER_STUB,
            "com/openrsc/server/model/entity/npc/Npc.java": NPC_STUB,
            "com/openrsc/server/Server.java": SERVER_STUB,
            "com/openrsc/server/model/world/World.java": WORLD_STUB,
            "com/openrsc/server/model/world/region/RegionManager.java":
                REGION_MANAGER_STUB,
            "com/openrsc/server/event/rsc/PluginTickEvent.java": PLUGIN_STUB,
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
            "RestorationRegistrationFenceFixture.java": FIXTURE,
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
                str(COMMIT_REQUEST), str(ONE_SHOT_CONTRACT),
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

    def test_handle_free_generation_fixture_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", self.classpath,
                "com.openrsc.server.event.rsc.handler."
                "RestorationRegistrationFenceFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_public_internal_signature_requires_no_event_handle(self):
        source = STORE.read_text(encoding="utf-8")
        start = source.index("withValidatedRestorationRegistrationFence(")
        signature = source[start:source.index(") {", start)]
        self.assertNotIn("GameTickEvent", signature)
        self.assertIn("expectedSchedulerInstanceIdentity", signature)
        self.assertIn("expectedRegistrationSequence", signature)
        self.assertIn("expectedProposalGeneration", signature)
        self.assertIn("final GameTickEvent candidate", source[start:])

    def test_operation_receives_closed_detached_facts_only(self):
        source = STORE.read_text(encoding="utf-8")
        operation = source[
            source.index("interface RestorationRegistrationFenceOperation"):
            source.index("enum RestorationRegistrationFenceReason")
        ]
        self.assertIn(
            "void execute(RestorationRegistrationFence fence)", operation
        )
        detached = source[
            source.index("class RestorationRegistrationFence {"):
            source.index("class RestorationRegistrationFenceExecution")
        ]
        for forbidden in (
            "GameTickEvent ", "GameTickEventStore ", "GameObject ",
            "Region ", "World ", "String owner", "getOwner()",
            "UUID ", "GameTickKey ", "Object monitor",
        ):
            self.assertNotIn(forbidden, detached)
        for required in (
            "isOwnerStateRetained() { return false; }",
            "isRuntimeAttributeStateRetained() { return false; }",
            "isEventHandleRetained() { return false; }",
            "isStoreHandleRetained() { return false; }",
            "isTimingStableAcrossOperation() { return false; }",
            "isEventCancellationExcluded() { return false; }",
        ):
            self.assertIn(required, detached)

    def test_generation_fence_has_no_region_mutation_or_authority(self):
        source = STORE.read_text(encoding="utf-8")
        start = source.index("withValidatedRestorationRegistrationFence(")
        end = source.index("private void registerAccepted", start)
        seam = source[start:end]
        for forbidden in (
            "import com.openrsc.server.model.world.region",
            "Region region", "GameObject", "registerGameObject",
            "unregisterGameObject", "sendUpdatePackets", ".run()",
            ".doRun()", ".stop()",
        ):
            self.assertNotIn(forbidden, seam)
        execution = source[
            source.index("class RestorationRegistrationFenceExecution"):]
        for required in (
            "isRuntimeTargetLookupPerformed() { return false; }",
            "isRuntimeRevalidationPerformed() { return false; }",
            "isCommitToken() { return false; }",
            "isMutationPerformed() { return false; }",
            "isExecutableRestoration() { return false; }",
            "isArrivalGate() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, execution)

    def test_living_plan_records_slice_one_hundred_twenty_two(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 122: Handle-free authored-generation fence", plan
        )
        self.assertIn("caller supplies no event handle", plan)
        self.assertIn("No Region object boundary is entered", plan)


if __name__ == "__main__":
    unittest.main()
