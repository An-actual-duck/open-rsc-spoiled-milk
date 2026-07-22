#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
STATE = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationState.java"
)
REQUIREMENT = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationRequirement.java"
)
DECISION = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationTargetDecision.java"
)
OBSERVATION = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredPackedRegionEventTargetObservation.java"
)
REGION = ROOT / "server/src/com/openrsc/server/model/world/region/Region.java"
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.event.rsc.GameTickEventRestorationTargetDecision;
import com.openrsc.server.event.rsc.GameTickEventRestorationTargetDecision
    .TargetOperation;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventTargetObservation.ObservedTargetState;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventTargetObservation.Outcome;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventTargetObservation.Reason;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventTargetObservation.TargetRecord;
import java.util.Arrays;
import java.util.Collections;

public final class EventTargetObservationFixture {
    public static void main(String[] args) {
        aggregateRetainsEveryTargetCategory();
        incompleteBindingRefusesBeforeExactOccupancy();
        invalidMetadataAndOrderingRefuse();
    }

    private static TargetRecord record(
            int ordinal, long registration, boolean regionAvailable,
            int slotCount, int exactSceneryCount, int exactIdentityCount,
            TargetOperation operation, boolean binding, long authored) {
        ObservedTargetState observed =
            TargetRecord.classifyObservedTargetState(
                regionAvailable, slotCount, exactSceneryCount,
                exactIdentityCount, binding);
        GameTickEventRestorationTargetDecision decision =
            GameTickEventRestorationTargetDecision.decideDetached(
                operation, binding, authored, 7L,
                GameTickEventRestorationTargetDecision.ObservedTargetState
                    .valueOf(observed.name()));
        return TargetRecord.observe(
            ordinal, registration, 524 + ordinal, 489, regionAvailable,
            slotCount, exactSceneryCount, exactIdentityCount,
            binding, Outcome.valueOf(decision.getOutcome().name()),
            Reason.valueOf(decision.getReason().name()));
    }

    private static void aggregateRetainsEveryTargetCategory() {
        TargetRecord transientSpawn = record(
            0, 21L, true, 1, 0, 1,
            TargetOperation.SCENERY_SPAWN, true, 7L);
        TargetRecord exactRemoval = record(
            1, 22L, true, 1, 1, 1,
            TargetOperation.SCENERY_REMOVE, true, 7L);
        TargetRecord absentRemoval = record(
            2, 23L, true, 0, 0, 0,
            TargetOperation.SCENERY_REMOVE, true, 7L);
        TargetRecord ambiguousSpawn = record(
            3, 24L, true, 2, 0, 0,
            TargetOperation.SCENERY_SPAWN, true, 7L);
        TargetRecord unavailableSpawn = record(
            4, 25L, false, 0, 0, 0,
            TargetOperation.SCENERY_SPAWN, true, 7L);

        LayeredPackedRegionEventTargetObservation observation =
            LayeredPackedRegionEventTargetObservation.observation(
                7L, 120L, 121L,
                "00000000-0000-0000-0000-000000000116",
                Arrays.asList(
                    transientSpawn, exactRemoval, absentRemoval,
                    ambiguousSpawn, unavailableSpawn), 5);
        check(observation.getTargetCount() == 5
            && observation.getAvailableTargetCount() == 4
            && observation.getUnavailableTargetCount() == 1
            && observation.getNoOpSuccessCount() == 1
            && observation.getMutationPreconditionSatisfiedCount() == 2
            && observation.getRefusedTargetCount() == 2
            && observation.isOutcomeCountComplete()
            && observation.isPointInTimeOnly()
            && !observation.isAtomicWithEventInventory()
            && observation.isReadOnlyTargetLookupPerformed()
            && !observation.isEntityHandleRetained()
            && !observation.isMutationPerformed()
            && !observation.isExecutableRestoration()
            && !observation.isArrivalGate()
            && !observation.isLifecycleAuthority(),
            "aggregate target evidence must reconcile and remain inert");
        check(transientSpawn.getObservedTargetState()
                == ObservedTargetState.EXACT_AUTHORED_TRANSIENT_PRESENT
            && transientSpawn.getDecisionOutcome()
                == Outcome.MUTATION_PRECONDITION_SATISFIED
            && exactRemoval.getObservedTargetState()
                == ObservedTargetState.EXACT_RESTORATION_SCENERY_PRESENT
            && exactRemoval.getDecisionReason()
                == Reason.EXACT_REMOVAL_TARGET_PRESENT
            && absentRemoval.getDecisionOutcome() == Outcome.NO_OP_SUCCESS
            && ambiguousSpawn.getDecisionReason()
                == Reason.AMBIGUOUS_OCCUPANCY
            && unavailableSpawn.getDecisionReason()
                == Reason.TARGET_OBSERVATION_UNAVAILABLE,
            "per-target categories and decisions must remain exact");
    }

    private static void incompleteBindingRefusesBeforeExactOccupancy() {
        TargetRecord record = record(
            0, 21L, true, 1, 1, 0,
            TargetOperation.SCENERY_SPAWN, false, 0L);
        check(record.getObservedTargetState()
                == ObservedTargetState.MISMATCHED_OR_IDENTITYLESS_OCCUPANT
            && record.getDecisionOutcome() == Outcome.REFUSED
            && record.getDecisionReason() == Reason.TARGET_BINDING_INCOMPLETE,
            "exact occupancy cannot repair missing authored binding");
        TargetRecord wrongIdentity = record(
            1, 22L, true, 1, 1, 0,
            TargetOperation.SCENERY_SPAWN, true, 7L);
        check(wrongIdentity.getObservedTargetState()
                == ObservedTargetState.MISMATCHED_OR_IDENTITYLESS_OCCUPANT
            && wrongIdentity.getDecisionOutcome() == Outcome.REFUSED
            && wrongIdentity.getDecisionReason()
                == Reason.MISMATCHED_OR_IDENTITYLESS_OCCUPANT,
            "matching constructor state cannot substitute for authored identity");
    }

    private static void invalidMetadataAndOrderingRefuse() {
        TargetRecord first = record(
            1, 22L, true, 0, 0, 0,
            TargetOperation.SCENERY_SPAWN, true, 7L);
        TargetRecord second = record(
            0, 21L, true, 0, 0, 0,
            TargetOperation.SCENERY_SPAWN, true, 7L);
        expectIllegal(() -> LayeredPackedRegionEventTargetObservation
            .observation(7L, 121L, 120L, "scope",
                Collections.emptyList(), 0));
        expectIllegal(() -> LayeredPackedRegionEventTargetObservation
            .observation(7L, 120L, 121L, "scope",
                Arrays.asList(first, second), 2));
        expectIllegal(() -> LayeredPackedRegionEventTargetObservation
            .observation(7L, 120L, 121L, "scope",
                Collections.singletonList(first), 0));
        expectIllegal(() -> TargetRecord.observe(
            0, 21L, 524, 489, false, 1, 0, 0,
            true, Outcome.REFUSED, Reason.TARGET_OBSERVATION_UNAVAILABLE));
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected refusal.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
    }
}
'''


class LayeredMapsSliceOneHundredSixteenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-sixteen-"
        )
        cls.classes = Path(cls.compile_temp.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.compile_temp.name) / (
            "src/com/openrsc/server/model/world/coordinate/"
            "EventTargetObservationFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(STATE), str(REQUIREMENT), str(DECISION),
                str(OBSERVATION), str(fixture),
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

    def test_observation_fixture_is_executable_and_bounded(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "EventTargetObservationFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_region_copies_every_relevant_exact_slot_object(self):
        source = REGION.read_text(encoding="utf-8")
        start = source.index("captureRestorationTargetSlotSnapshot(")
        end = source.index("Gets the list of players", start)
        boundary = source[start:end]
        self.assertIn("synchronized (objects)", boundary)
        self.assertIn("objects.get(location)", boundary)
        self.assertIn("object.getType() == type", boundary)
        self.assertIn("type == 0 || object.getDirection() == direction", boundary)
        self.assertIn("new RestorationTargetObjectSnapshot(object)", boundary)
        self.assertNotIn("findFirst", boundary)

    def test_manager_capture_is_read_only_and_correlated(self):
        source = REGION_MANAGER.read_text(encoding="utf-8")
        start = source.index(
            "captureLayeredPackedRegionEventTargetObservation("
        )
        end = source.index(
            "Captures one strictly newer, same-tick", start
        )
        boundary = source[start:end]
        for required in (
            "synchronized (layeredRegionLifecycleLock)",
            "event.getSnapshotOrdinal()", "event.getRegistrationSequence()",
            "checked.getSchedulerInstanceIdentity()",
            "captureRestorationTargetSlotSnapshot(",
            "matchesRestorationScenery(", "matchesAuthoredIdentity(",
            "TargetRecord", "targetOperation(restoration.getKind())",
        ):
            self.assertIn(required, boundary)
        for forbidden in (
            "getGameObject(", "registerGameObject", "unregisterGameObject",
            "event.stop()", ".doRun()", "sendUpdatePackets", "removeEntity",
        ):
            self.assertNotIn(forbidden, boundary)

    def test_living_plan_records_slice_one_hundred_sixteen_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 116: Read-only restoration target observation", plan
        )
        self.assertIn("not atomic with the event inventory", plan)


if __name__ == "__main__":
    unittest.main()
