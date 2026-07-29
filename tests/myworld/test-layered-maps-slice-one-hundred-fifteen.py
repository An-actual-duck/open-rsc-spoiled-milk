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
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.event.rsc;

import com.openrsc.server.event.rsc.GameTickEventRestorationState
    .AuthoredConstructionKind;
import com.openrsc.server.event.rsc.GameTickEventRestorationState
    .AuthoredPlacementState;
import com.openrsc.server.event.rsc.GameTickEventRestorationState.SceneryState;
import com.openrsc.server.event.rsc.GameTickEventRestorationTargetDecision
    .ObservedTargetState;
import com.openrsc.server.event.rsc.GameTickEventRestorationTargetDecision
    .Outcome;
import com.openrsc.server.event.rsc.GameTickEventRestorationTargetDecision
    .Reason;

public final class RestorationTargetDecisionFixture {
    public static void main(String[] args) {
        spawnMatrixIsIdempotentAndTransientAware();
        removalMatrixProtectsChangedSuccessors();
        bindingAndGenerationRefuseBeforeOccupancy();
        unavailableAndInvalidInputsFailClosed();
    }

    private static SceneryState scenery(boolean authored, long generation) {
        AuthoredPlacementState identity = authored
            ? AuthoredPlacementState.of(
                generation, 10, 10, 42, AuthoredConstructionKind.SCENERY)
            : null;
        return SceneryState.of(
            310, 310, 524, 489, 0, 0, null, 0, identity);
    }

    private static GameTickEventRestorationRequirement spawn(
            boolean authored, long generation) {
        return GameTickEventRestorationRequirement.from(
            GameTickEventRestorationState.scenerySpawn(
                scenery(authored, generation), false));
    }

    private static GameTickEventRestorationRequirement removal(
            boolean authored, long generation) {
        return GameTickEventRestorationRequirement.from(
            GameTickEventRestorationState.sceneryRemove(
                scenery(authored, generation)));
    }

    private static void spawnMatrixIsIdempotentAndTransientAware() {
        assertDecision(spawn(true, 7L), 7L, ObservedTargetState.EMPTY,
            Outcome.MUTATION_PRECONDITION_SATISFIED,
            Reason.SPAWN_DESTINATION_EMPTY);
        assertDecision(spawn(true, 7L), 7L,
            ObservedTargetState.EXACT_RESTORATION_SCENERY_PRESENT,
            Outcome.NO_OP_SUCCESS,
            Reason.DESIRED_PRESENT_STATE_ALREADY_SATISFIED);
        assertDecision(spawn(true, 7L), 7L,
            ObservedTargetState.EXACT_AUTHORED_TRANSIENT_PRESENT,
            Outcome.MUTATION_PRECONDITION_SATISFIED,
            Reason.EXACT_AUTHORED_TRANSIENT_PRESENT);
        assertDecision(spawn(true, 7L), 7L,
            ObservedTargetState.MISMATCHED_OR_IDENTITYLESS_OCCUPANT,
            Outcome.REFUSED, Reason.MISMATCHED_OR_IDENTITYLESS_OCCUPANT);
        assertDecision(spawn(true, 7L), 7L,
            ObservedTargetState.AMBIGUOUS_OCCUPANCY,
            Outcome.REFUSED, Reason.AMBIGUOUS_OCCUPANCY);
    }

    private static void removalMatrixProtectsChangedSuccessors() {
        assertDecision(removal(true, 7L), 7L, ObservedTargetState.EMPTY,
            Outcome.NO_OP_SUCCESS,
            Reason.DESIRED_ABSENT_STATE_ALREADY_SATISFIED);
        assertDecision(removal(true, 7L), 7L,
            ObservedTargetState.EXACT_RESTORATION_SCENERY_PRESENT,
            Outcome.MUTATION_PRECONDITION_SATISFIED,
            Reason.EXACT_REMOVAL_TARGET_PRESENT);
        assertDecision(removal(true, 7L), 7L,
            ObservedTargetState.EXACT_AUTHORED_TRANSIENT_PRESENT,
            Outcome.REFUSED,
            Reason.REMOVAL_TARGET_CHANGED_TO_AUTHORED_TRANSIENT);
        assertDecision(removal(true, 7L), 7L,
            ObservedTargetState.MISMATCHED_OR_IDENTITYLESS_OCCUPANT,
            Outcome.REFUSED, Reason.MISMATCHED_OR_IDENTITYLESS_OCCUPANT);
        assertDecision(removal(true, 7L), 7L,
            ObservedTargetState.AMBIGUOUS_OCCUPANCY,
            Outcome.REFUSED, Reason.AMBIGUOUS_OCCUPANCY);
    }

    private static void bindingAndGenerationRefuseBeforeOccupancy() {
        assertDecision(spawn(false, 7L), 7L,
            ObservedTargetState.EXACT_RESTORATION_SCENERY_PRESENT,
            Outcome.REFUSED, Reason.TARGET_BINDING_INCOMPLETE);
        assertDecision(spawn(true, 7L), 8L,
            ObservedTargetState.AMBIGUOUS_OCCUPANCY,
            Outcome.REFUSED, Reason.GENERATION_MISMATCH);
    }

    private static void unavailableAndInvalidInputsFailClosed() {
        GameTickEventRestorationRequirement unavailable =
            GameTickEventRestorationRequirement.from(
                GameTickEventRestorationState.unavailable());
        assertDecision(unavailable, 7L, ObservedTargetState.EMPTY,
            Outcome.REFUSED, Reason.REQUIREMENT_UNAVAILABLE);
        assertDecision(spawn(true, 7L), 7L,
            ObservedTargetState.UNAVAILABLE,
            Outcome.REFUSED, Reason.TARGET_OBSERVATION_UNAVAILABLE);
        expectIllegal(() -> GameTickEventRestorationTargetDecision.decide(
            spawn(true, 7L), 0L, ObservedTargetState.EMPTY));
        expectNull(() -> GameTickEventRestorationTargetDecision.decide(
            null, 7L, ObservedTargetState.EMPTY));
        expectNull(() -> GameTickEventRestorationTargetDecision.decide(
            spawn(true, 7L), 7L, null));
    }

    private static void assertDecision(
            GameTickEventRestorationRequirement requirement,
            long generation,
            ObservedTargetState observation,
            Outcome outcome,
            Reason reason) {
        GameTickEventRestorationTargetDecision decision =
            GameTickEventRestorationTargetDecision.decide(
                requirement, generation, observation);
        check(decision.getOutcome() == outcome
            && decision.getReason() == reason
            && decision.getObservedTargetState() == observation
            && decision.isNoOpSuccess()
                == (outcome == Outcome.NO_OP_SUCCESS)
            && decision.isMutationPreconditionSatisfied()
                == (outcome == Outcome.MUTATION_PRECONDITION_SATISFIED)
            && decision.isRefused() == (outcome == Outcome.REFUSED)
            && decision.isDetachedObservationClassification()
            && !decision.isRuntimeTargetLookupPerformed()
            && !decision.isRuntimeTargetStateInspected()
            && !decision.isMutationPerformed()
            && !decision.isExecutableRestoration()
            && !decision.isArrivalGate()
            && !decision.isLifecycleAuthority(),
            "target decision disagrees with its detached input");
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected refusal.
        }
    }

    private static void expectNull(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected refusal.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
    }
}
'''


class LayeredMapsSliceOneHundredFifteenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-fifteen-"
        )
        cls.classes = Path(cls.compile_temp.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.compile_temp.name) / (
            "src/com/openrsc/server/event/rsc/"
            "RestorationTargetDecisionFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(STATE), str(REQUIREMENT), str(DECISION), str(fixture),
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

    def test_target_decision_fixture_is_executable_and_fail_closed(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.event.rsc."
                "RestorationTargetDecisionFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_classifier_contains_no_runtime_lookup_or_mutation_handle(self):
        source = DECISION.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.model",
            "import com.openrsc.server.net",
            "GameTickEvent ", "World ", "Region ", "GameObject ",
            "getGameObject", "registerGameObject", "unregisterGameObject",
            "sendUpdatePackets", ".doRun()", ".stop()",
        ):
            self.assertNotIn(forbidden, source)
        self.assertIn(
            "isRuntimeTargetLookupPerformed() { return false; }", source
        )
        self.assertIn("isMutationPerformed() { return false; }", source)

    def test_living_plan_records_slice_one_hundred_fifteen_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 115: Dormant target-state decision classifier", plan
        )
        self.assertIn("generation failure takes precedence", plan)


if __name__ == "__main__":
    unittest.main()
