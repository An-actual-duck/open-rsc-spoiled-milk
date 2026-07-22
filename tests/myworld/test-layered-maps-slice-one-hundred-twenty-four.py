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
DECISION = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationTargetDecision.java"
)
REQUIREMENT = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationRequirement.java"
)
CONTRACT = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationAtomicRevalidationContract.java"
)
REQUEST = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationTargetRevalidationRequest.java"
)
REVALIDATION = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationTargetRevalidation.java"
)
STORE = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventStore.java"
)
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
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


REGION_MANAGER_STUB = r'''
package com.openrsc.server.model.world.region;

import com.openrsc.server.event.rsc
    .GameTickEventRestorationAtomicRevalidationContract;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTargetDecision;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTargetRevalidation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTargetRevalidationRequest;
import java.util.concurrent.CountDownLatch;

public class RegionManager {
    private final Object objects = new Object();
    private final CountDownLatch classificationCaptured =
        new CountDownLatch(1);
    private final CountDownLatch releaseClassification =
        new CountDownLatch(1);
    private boolean occupied;
    private volatile GameTickEventRestorationTargetRevalidationRequest request;

    public GameTickEventRestorationTargetRevalidation
            captureGameTickEventRestorationTargetRevalidation(
                GameTickEventRestorationTargetRevalidationRequest checked) {
        synchronized (objects) {
            request = checked;
            GameTickEventRestorationTargetDecision.ObservedTargetState state =
                occupied
                    ? GameTickEventRestorationTargetDecision
                        .ObservedTargetState
                        .EXACT_RESTORATION_SCENERY_PRESENT
                    : GameTickEventRestorationTargetDecision
                        .ObservedTargetState.EMPTY;
            int count = occupied ? 1 : 0;
            GameTickEventRestorationTargetDecision decision =
                GameTickEventRestorationTargetDecision.decideDetached(
                    checked.getTargetOperation(),
                    checked.isTargetBindingComplete(),
                    checked.getAuthoredGeneration(),
                    checked.getProposalGeneration(), state);
            GameTickEventRestorationAtomicRevalidationContract contract =
                GameTickEventRestorationAtomicRevalidationContract.evaluate(
                    GameTickEventRestorationAtomicRevalidationContract
                        .BoundaryDeclaration.declare(
                            checked.getSchedulerInstanceIdentity(),
                            checked.getSchedulerInstanceIdentity(),
                            checked.getRegistrationSequence(),
                            checked.getRegistrationSequence(),
                            checked.getProposalGeneration(),
                            checked.getAuthoredGeneration(),
                            checked.isEventExecutionBoundaryHeld(),
                            checked.isSchedulerStoreBoundaryHeld(),
                            checked
                                .isRegistrationValidatedBeforeRegionBoundary(),
                            Thread.holdsLock(objects), true),
                    decision);
            GameTickEventRestorationTargetRevalidation result =
                GameTickEventRestorationTargetRevalidation.observe(
                    true, count, count, count, state,
                    Thread.holdsLock(objects), decision, contract);
            classificationCaptured.countDown();
            await(releaseClassification);
            return result;
        }
    }

    public void changeTarget() {
        synchronized (objects) { occupied = true; }
    }
    public void awaitClassification() { await(classificationCaptured); }
    public void releaseClassification() { releaseClassification.countDown(); }
    public GameTickEventRestorationTargetRevalidationRequest getRequest() {
        return request;
    }
    private static void await(CountDownLatch latch) {
        try { latch.await(); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
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
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTargetRevalidationRequest;
import com.openrsc.server.model.world.World;
import com.openrsc.server.model.world.region.RegionManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class RestorationTargetRevalidationFixture {
    private static final class RestorationEvent extends GameTickEvent {
        private final GameTickEventRestorationState restoration;
        RestorationEvent() {
            super(new World(), null, 100L, "target-revalidation-fixture",
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

    public static void main(String[] args) {
        stableCompositionExcludesRegistrationAndTargetChange();
        lifecycleChangeDiscardsClassifiedTarget();
    }

    private static void
            stableCompositionExcludesRegistrationAndTargetChange() {
        GameTickEventStore store = new GameTickEventStore();
        RestorationEvent event = new RestorationEvent();
        check(store.add(event), "event registered");
        RegionManager regions = new RegionManager();
        AtomicReference<GameTickEventStore
            .RestorationTargetRevalidationExecution> execution =
                new AtomicReference<>();
        Thread capture = thread(() -> execution.set(
            store.withValidatedRestorationTargetRevalidation(
                regions, scope(store), sequenceOf(store, event), 7L)));
        capture.start();
        regions.awaitClassification();

        Thread targetChange = thread(regions::changeTarget);
        Thread removal = thread(() -> store.remove(event));
        Thread callback = thread(event::doRun);
        targetChange.start();
        removal.start();
        callback.start();
        check(!joinWithin(targetChange, 100L),
            "target change waits for Region object boundary");
        check(!joinWithin(removal, 100L),
            "registration removal waits for event execution boundary");
        check(!joinWithin(callback, 100L),
            "callback execution waits for event execution boundary");

        regions.releaseClassification();
        join(capture);
        join(targetChange);
        join(removal);
        join(callback);
        GameTickEventStore.RestorationTargetRevalidationExecution result =
            execution.get();
        check(result != null && result.isOuterFenceAccepted()
                && result.isOperationInvoked()
                && result.isTimingStableAcrossOperation()
                && result.isRuntimeTargetLookupPerformed()
                && result.isRuntimeRevalidationPerformed()
                && result.getTarget() != null
                && result.getTarget().getObservedTargetState().name()
                    .equals("EMPTY")
                && result.getTarget().getContract()
                    .isMutationPreconditionContractSatisfied()
                && !result.isEventHandleRetained()
                && !result.isRegionHandleRetained()
                && !result.isEntityHandleRetained()
                && !result.isMutationPerformed()
                && !result.isExecutableRestoration()
                && !result.isCommitToken()
                && !result.isArrivalGate()
                && !result.isLifecycleAuthority(),
            "stable composition returns only detached read-only evidence");
        GameTickEventRestorationTargetRevalidationRequest request =
            regions.getRequest();
        check(request != null
                && request.getSchedulerInstanceIdentity() != null
                && request.getRegistrationSequence() > 0L
                && request.getProposalGeneration() == 7L
                && request.getAuthoredGeneration() == 7L
                && request.isEventExecutionBoundaryHeld()
                && !request.isSchedulerStoreBoundaryHeld()
                && request.isRegistrationValidatedBeforeRegionBoundary()
                && request.getX() == 524 && request.getY() == 489
                && !request.isRuntimeHandleRetained()
                && !request.isMutationAuthorized(),
            "inner Region receives the closed validated request");
    }

    private static void lifecycleChangeDiscardsClassifiedTarget() {
        GameTickEventStore store = new GameTickEventStore();
        RestorationEvent event = new RestorationEvent();
        check(store.add(event), "lifecycle-race event registered");
        RegionManager regions = new RegionManager();
        AtomicReference<GameTickEventStore
            .RestorationTargetRevalidationExecution> execution =
                new AtomicReference<>();
        Thread capture = thread(() -> execution.set(
            store.withValidatedRestorationTargetRevalidation(
                regions, scope(store), sequenceOf(store, event), 7L)));
        capture.start();
        regions.awaitClassification();
        long before = event.captureAtomicTimingSnapshot()
            .getLifecycleVersion();
        event.stop();
        long after = event.captureAtomicTimingSnapshot()
            .getLifecycleVersion();
        check(after == before + 1L,
            "stop completes while Region classification is blocked");
        regions.releaseClassification();
        join(capture);
        GameTickEventStore.RestorationTargetRevalidationExecution result =
            execution.get();
        check(result != null && !result.isOuterFenceAccepted()
                && result.getOuterFenceReason()
                    == GameTickEventStore
                        .RestorationRegistrationFenceReason
                        .EVENT_LIFECYCLE_CHANGED_DURING_OPERATION
                && result.isOperationInvoked()
                && !result.isTimingStableAcrossOperation()
                && result.getLifecycleVersionBeforeOperation() == before
                && result.getLifecycleVersionAfterOperation() == after
                && result.getTarget() == null
                && result.isRuntimeTargetLookupPerformed()
                && !result.isRuntimeRevalidationPerformed(),
            "lifecycle mismatch discards provisional target evidence");
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
    private static boolean joinWithin(Thread thread, long millis) {
        try { thread.join(millis); }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
        return !thread.isAlive();
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


class LayeredMapsSliceOneHundredTwentyFourTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-restoration-target-revalidation-"
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
            "com/openrsc/server/event/rsc/handler/"
            "RestorationTargetRevalidationFixture.java": FIXTURE,
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
                str(DECISION), str(REQUIREMENT), str(CONTRACT), str(REQUEST),
                str(REVALIDATION),
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

    def test_composed_runtime_fixture_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", self.classpath,
                "com.openrsc.server.event.rsc.handler."
                "RestorationTargetRevalidationFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_store_composes_outer_fence_region_work_and_postcheck(self):
        source = STORE.read_text(encoding="utf-8")
        start = source.index(
            "withValidatedRestorationTargetRevalidation("
        )
        end = source.index(
            "private static RestorationRegistrationFenceExecution", start
        )
        method = source[start:end]
        outer = method.index("withValidatedRestorationRegistrationFence(")
        request = method.index(
            "GameTickEventRestorationTargetRevalidationRequest.request(",
            outer,
        )
        region = method.index(
            ".captureGameTickEventRestorationTargetRevalidation(", request
        )
        postcheck = method.index("if (!outer.isAccepted())", region)
        observed = method.index(
            "RestorationTargetRevalidationExecution.observed(", postcheck
        )
        self.assertLess(outer, request)
        self.assertLess(request, region)
        self.assertLess(region, postcheck)
        self.assertLess(postcheck, observed)

    def test_real_region_manager_classifies_before_contract_without_mutation(self):
        source = REGION_MANAGER.read_text(encoding="utf-8")
        start = source.index(
            "captureGameTickEventRestorationTargetRevalidation("
        )
        end = source.index("private static TargetOperation", start)
        method = source[start:end]
        lookup = method.index("peekRegionFromSectorCoordinates(")
        requirement = method.index(
            "RestorationTargetMatchRequirement.of(", lookup
        )
        boundary = method.index(
            "captureRestorationTargetBoundarySnapshot(", requirement
        )
        decision = method.index(
            "GameTickEventRestorationTargetDecision.decideDetached(",
            boundary,
        )
        contract = method.index(
            "GameTickEventRestorationAtomicRevalidationContract.evaluate(",
            decision,
        )
        self.assertLess(lookup, requirement)
        self.assertLess(requirement, boundary)
        self.assertLess(boundary, decision)
        self.assertLess(decision, contract)
        for forbidden in (
            "registerGameObject", "unregisterGameObject",
            "replaceGameObject", ".doRun()", ".stop()",
            "sendUpdatePackets",
        ):
            self.assertNotIn(forbidden, method)

    def test_request_and_result_are_handle_free_and_non_authoritative(self):
        request = REQUEST.read_text(encoding="utf-8")
        result = REVALIDATION.read_text(encoding="utf-8")
        for forbidden in (
            "GameTickEvent event", "Region region", "GameObject",
            "World world", "Collection<", "Map<", "private final UUID",
            "import java.util.UUID",
            "Object monitor", "String owner",
        ):
            self.assertNotIn(forbidden, request)
            self.assertNotIn(forbidden, result)
        for required in (
            "isStaleAfterBoundaryRelease() { return true; }",
            "isEntityHandleRetained() { return false; }",
            "isMutationAuthorized() { return false; }",
            "isMutationPerformed() { return false; }",
            "isExecutableRestoration() { return false; }",
            "isCommitToken() { return false; }",
            "isArrivalGate() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, result)

    def test_living_plan_records_slice_one_hundred_twenty_four(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 124: Composed read-only target revalidation", plan
        )
        self.assertIn(
            "lifecycle mismatch discards the provisional target", plan
        )
        self.assertIn("stale immediately after release", plan)


if __name__ == "__main__":
    unittest.main()
