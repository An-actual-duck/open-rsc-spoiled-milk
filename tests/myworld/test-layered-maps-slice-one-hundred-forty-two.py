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
DIRECTIVE_EXECUTOR = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventRestorationRecoveryDirectiveExecutor.java"
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
import com.openrsc.server.event.rsc.GameTickEventRestorationTargetRevalidation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTargetRevalidationRequest;

public class RegionManager {
    public enum RestorationCommitOutcome { REFUSED, NO_OP, APPLIED }
    public enum RestorationCommitReason {
        FIXTURE_REFUSED, FIXTURE_NO_OP, FIXTURE_APPLIED
    }

    public static final class RestorationCommitResult {
        private final RestorationCommitOutcome outcome;
        private final RestorationCommitReason reason;
        private final boolean removed;
        private final boolean registered;
        private final int boundaryCount;

        private RestorationCommitResult(
                RestorationCommitOutcome outcome,
                RestorationCommitReason reason,
                boolean removed,
                boolean registered,
                int boundaryCount) {
            this.outcome = outcome;
            this.reason = reason;
            this.removed = removed;
            this.registered = registered;
            this.boundaryCount = boundaryCount;
        }

        public static RestorationCommitResult refused() {
            return new RestorationCommitResult(
                RestorationCommitOutcome.REFUSED,
                RestorationCommitReason.FIXTURE_REFUSED,
                false, false, 0);
        }
        public static RestorationCommitResult noOp() {
            return new RestorationCommitResult(
                RestorationCommitOutcome.NO_OP,
                RestorationCommitReason.FIXTURE_NO_OP,
                false, false, 1);
        }
        public static RestorationCommitResult applied() {
            return new RestorationCommitResult(
                RestorationCommitOutcome.APPLIED,
                RestorationCommitReason.FIXTURE_APPLIED,
                false, true, 1);
        }
        public RestorationCommitOutcome getOutcome() { return outcome; }
        public RestorationCommitReason getReason() { return reason; }
        public boolean isMembershipRemoved() { return removed; }
        public boolean isMembershipRegistered() { return registered; }
        public int getBoundaryCount() { return boundaryCount; }
    }

    private final RestorationCommitResult result;
    private int commitCalls;
    private GameTickEventRestorationCommitRequest request;

    public RegionManager(RestorationCommitResult result) {
        this.result = result;
    }

    public RestorationCommitResult applyGameTickEventRestorationCommitRequest(
            GameTickEventRestorationCommitRequest checked) {
        request = checked;
        commitCalls++;
        return result;
    }

    public GameTickEventRestorationTargetRevalidation
            captureGameTickEventRestorationTargetRevalidation(
                GameTickEventRestorationTargetRevalidationRequest checked) {
        return new GameTickEventRestorationTargetRevalidation();
    }

    public int getCommitCalls() { return commitCalls; }
    public GameTickEventRestorationCommitRequest getRequest() { return request; }
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
import com.openrsc.server.model.world.region.RegionManager
    .RestorationCommitOutcome;
import com.openrsc.server.model.world.region.RegionManager
    .RestorationCommitReason;
import com.openrsc.server.model.world.region.RegionManager
    .RestorationCommitResult;
import java.util.concurrent.atomic.AtomicInteger;

public final class RestorationRegionSchedulerCompositionFixture {
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
        @Override public GameTickEventRestorationState getRestorationState() {
            return restoration;
        }
        @Override public GameTickEventSpatialAffinity getSpatialAffinity() {
            return GameTickEventSpatialAffinity.exactFixedLocation(524, 489);
        }
        int getCallbackRuns() { return callbackRuns.get(); }
    }

    public static void main(String[] args) {
        appliedRegionCommitConsumesExactRegistration();
        noOpRegionCommitConsumesExactRegistration();
        refusedRegionCommitRetainsExactRegistration();
        schedulerRefusalNeverInvokesRegionCommit();
    }

    private static void appliedRegionCommitConsumesExactRegistration() {
        Scenario scenario = new Scenario(RestorationCommitResult.applied());
        GameTickEventStore.RestorationRegionCommitConsumptionExecution result =
            scenario.execute(7L);
        check(result.isRegionCommitInvoked()
                && result.getRegionOutcome()
                    == RestorationCommitOutcome.APPLIED
                && result.getRegionReason()
                    == RestorationCommitReason.FIXTURE_APPLIED
                && result.isMutationPerformed()
                && !result.isMembershipRemoved()
                && result.isMembershipRegistered()
                && result.getBoundaryCount() == 1
                && result.isEventTerminallyConsumed()
                && !result.isExactRegistrationRetained()
                && scenario.manager.getCommitCalls() == 1
                && scenario.manager.getRequest() != null
                && scenario.manager.getRequest().isLifecycleBoundaryHeld()
                && !scenario.store.eventIsContained(scenario.event)
                && !scenario.event.isRunning()
                && scenario.event.getCallbackRuns() == 0,
            "applied Region commit composes with terminal consumption");
        checkClosed(result);
    }

    private static void noOpRegionCommitConsumesExactRegistration() {
        Scenario scenario = new Scenario(RestorationCommitResult.noOp());
        GameTickEventStore.RestorationRegionCommitConsumptionExecution result =
            scenario.execute(7L);
        check(result.isRegionCommitInvoked()
                && result.getRegionOutcome() == RestorationCommitOutcome.NO_OP
                && result.getRegionReason()
                    == RestorationCommitReason.FIXTURE_NO_OP
                && !result.isMutationPerformed()
                && !result.isMembershipRemoved()
                && !result.isMembershipRegistered()
                && result.getBoundaryCount() == 1
                && result.isEventTerminallyConsumed()
                && !scenario.store.eventIsContained(scenario.event)
                && !scenario.event.isRunning()
                && scenario.event.getCallbackRuns() == 0,
            "Region no-op consumes the obsolete exact callback");
        checkClosed(result);
    }

    private static void refusedRegionCommitRetainsExactRegistration() {
        Scenario scenario = new Scenario(RestorationCommitResult.refused());
        long sequence = sequenceOf(scenario.store, scenario.event);
        long lifecycle = scenario.event.captureAtomicTimingSnapshot()
            .getLifecycleVersion();
        GameTickEventStore.RestorationRegionCommitConsumptionExecution result =
            scenario.execute(7L);
        check(result.isRegionCommitInvoked()
                && result.getRegionOutcome()
                    == RestorationCommitOutcome.REFUSED
                && result.getRegionReason()
                    == RestorationCommitReason.FIXTURE_REFUSED
                && !result.isMutationPerformed()
                && result.isExactRegistrationRetained()
                && !result.isEventTerminallyConsumed()
                && scenario.store.eventIsContained(scenario.event)
                && sequenceOf(scenario.store, scenario.event) == sequence
                && scenario.event.isRunning()
                && scenario.event.captureAtomicTimingSnapshot()
                    .getLifecycleVersion() == lifecycle
                && scenario.event.getCallbackRuns() == 0,
            "Region refusal retains exact scheduler and lifecycle state");
        checkClosed(result);
        scenario.store.remove(scenario.event);
        scenario.event.stop();
    }

    private static void schedulerRefusalNeverInvokesRegionCommit() {
        Scenario scenario = new Scenario(RestorationCommitResult.applied());
        GameTickEventStore.RestorationRegionCommitConsumptionExecution result =
            scenario.execute(8L);
        check(!result.isRegionCommitInvoked()
                && result.getRegionOutcome() == null
                && result.getRegionReason() == null
                && scenario.manager.getCommitCalls() == 0
                && scenario.manager.getRequest() == null
                && scenario.store.eventIsContained(scenario.event)
                && scenario.event.isRunning()
                && scenario.event.getCallbackRuns() == 0,
            "scheduler refusal cannot reach the Region commit seam");
        checkClosed(result);
        scenario.store.remove(scenario.event);
        scenario.event.stop();
    }

    private static void checkClosed(
            GameTickEventStore.RestorationRegionCommitConsumptionExecution result) {
        check(!result.isRegionResultRetained()
                && !result.isRequestRetained()
                && !result.isRuntimeHandleRetained()
                && !result.isCallbackInvoked()
                && !result.isEventReschedule()
                && !result.isExecutableRestoration()
                && !result.isCommitToken()
                && !result.isArrivalGate()
                && !result.isLifecycleAuthority(),
            "composition result retains no runtime authority");
    }

    private static final class Scenario {
        private final GameTickEventStore store = new GameTickEventStore();
        private final RestorableEvent event = new RestorableEvent();
        private final RegionManager manager;

        Scenario(RestorationCommitResult regionResult) {
            manager = new RegionManager(regionResult);
            check(store.add(event), "scenario event registered");
        }

        GameTickEventStore.RestorationRegionCommitConsumptionExecution execute(
                long generation) {
            return store.withValidatedRestorationRegionCommitConsumption(
                manager, scope(store), sequenceOf(store, event), generation);
        }
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

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
'''


class LayeredMapsSliceOneHundredFortyTwoTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-region-scheduler-composition-"
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
            "RestorationRegionSchedulerCompositionFixture.java": FIXTURE,
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

    def test_region_scheduler_composition_fixture_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", self.classpath,
                "com.openrsc.server.event.rsc.handler."
                "RestorationRegionSchedulerCompositionFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_store_calls_the_existing_real_region_seam(self):
        store = STORE.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        method = store[store.index(
            "withValidatedRestorationRegionCommitConsumption("
        ):store.index(
            "private static RestorationRegistrationFenceReason",
            store.index("withValidatedRestorationRegionCommitConsumption("),
        )]
        self.assertIn(
            "applyGameTickEventRestorationCommitRequest(request)", method
        )
        self.assertIn(
            "withValidatedRestorationOneShotConsumptionInternal(", method
        )
        self.assertIn(
            "RegionObjectCollisionTransactionExecutor.executeRestoration(",
            manager,
        )

    def test_composition_is_unreachable_from_production_callers(self):
        needle = "withValidatedRestorationRegionCommitConsumption("
        callers = []
        for path in (ROOT / "server/src").rglob("*.java"):
            if path == STORE:
                continue
            if needle in path.read_text(encoding="utf-8"):
                callers.append(path)
        self.assertEqual([DIRECTIVE_EXECUTOR], callers)
        result = STORE.read_text(encoding="utf-8")
        result = result[result.index(
            "class RestorationRegionCommitConsumptionExecution"
        ):result.index(
            "class RestorationTargetRevalidationExecution",
            result.index("class RestorationRegionCommitConsumptionExecution"),
        )]
        for required in (
            "isRegionResultRetained() { return false; }",
            "isRequestRetained() { return false; }",
            "isRuntimeHandleRetained() { return false; }",
            "isCallbackInvoked() { return false; }",
            "isEventReschedule() { return false; }",
            "isExecutableRestoration() { return false; }",
            "isCommitToken() { return false; }",
            "isArrivalGate() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, result)

    def test_living_plan_records_slice_one_hundred_forty_two(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 142: Disconnected Region/scheduler composition",
            plan,
        )
        normalized = " ".join(plan.split())
        self.assertIn("no production caller", normalized)
        self.assertIn("arrival and gameplay remain disconnected", normalized)


if __name__ == "__main__":
    unittest.main()
