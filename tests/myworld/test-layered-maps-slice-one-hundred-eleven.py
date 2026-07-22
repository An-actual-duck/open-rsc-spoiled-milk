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
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.event.rsc;

import com.openrsc.server.event.rsc.GameTickEventRestorationRequirement
    .DesiredState;
import com.openrsc.server.event.rsc.GameTickEventRestorationRequirement
    .GenerationBindingRequirement;
import com.openrsc.server.event.rsc.GameTickEventRestorationRequirement
    .IdempotencyPolicy;
import com.openrsc.server.event.rsc.GameTickEventRestorationRequirement
    .MutationPrecondition;
import com.openrsc.server.event.rsc.GameTickEventRestorationState
    .AuthoredConstructionKind;
import com.openrsc.server.event.rsc.GameTickEventRestorationState
    .AuthoredPlacementState;
import com.openrsc.server.event.rsc.GameTickEventRestorationState
    .SceneryState;

public final class GenerationIdempotencyRequirementFixture {
    public static void main(String[] args) {
        spawnRequiresGenerationAndOwnedDestination();
        removalRequiresGenerationAndExactEntity();
        missingIdentityRetainsRulesButCannotBind();
        unavailableStateRetainsNothing();
    }

    private static SceneryState scenery(boolean authored) {
        AuthoredPlacementState identity = authored
            ? AuthoredPlacementState.of(
                7L, 10, 10, 42, AuthoredConstructionKind.SCENERY)
            : null;
        return SceneryState.of(
            310, 310, 524, 489, 0, 0, null, 0, identity);
    }

    private static void spawnRequiresGenerationAndOwnedDestination() {
        GameTickEventRestorationRequirement value =
            GameTickEventRestorationRequirement.from(
                GameTickEventRestorationState.scenerySpawn(
                    scenery(true), false));
        check(value.getGenerationBindingRequirement()
                == GenerationBindingRequirement
                    .MATCH_RECONSTRUCTION_GENERATION
            && value.getDesiredState()
                == DesiredState.AUTHORED_SCENERY_PRESENT
            && value.getIdempotencyPolicy()
                == IdempotencyPolicy.ALREADY_SATISFIED_IS_NO_OP_SUCCESS
            && value.getMutationPrecondition()
                == MutationPrecondition
                    .DESTINATION_EMPTY_OR_EXACT_AUTHORED_TRANSIENT,
            "spawn is desired-state idempotent and generation fenced");
        assertCapturedButDormant(value);
    }

    private static void removalRequiresGenerationAndExactEntity() {
        GameTickEventRestorationRequirement value =
            GameTickEventRestorationRequirement.from(
                GameTickEventRestorationState.sceneryRemove(scenery(true)));
        check(value.getGenerationBindingRequirement()
                == GenerationBindingRequirement
                    .MATCH_RECONSTRUCTION_GENERATION
            && value.getDesiredState()
                == DesiredState.AUTHORED_SCENERY_ABSENT
            && value.getIdempotencyPolicy()
                == IdempotencyPolicy.ALREADY_SATISFIED_IS_NO_OP_SUCCESS
            && value.getMutationPrecondition()
                == MutationPrecondition.EXACT_AUTHORED_ENTITY_PRESENT,
            "removal is desired-state idempotent and generation fenced");
        assertCapturedButDormant(value);
    }

    private static void missingIdentityRetainsRulesButCannotBind() {
        GameTickEventRestorationRequirement value =
            GameTickEventRestorationRequirement.from(
                GameTickEventRestorationState.scenerySpawn(
                    scenery(false), false));
        check(value.getAuthoredTarget() == null
            && !value.isTargetBindingComplete(),
            "missing authored identity remains unable to bind");
        assertCapturedButDormant(value);
    }

    private static void unavailableStateRetainsNothing() {
        GameTickEventRestorationRequirement value =
            GameTickEventRestorationRequirement.from(
                GameTickEventRestorationState.unavailable());
        check(value.getGenerationBindingRequirement()
                == GenerationBindingRequirement.UNAVAILABLE
            && value.getDesiredState() == DesiredState.UNAVAILABLE
            && value.getIdempotencyPolicy()
                == IdempotencyPolicy.UNAVAILABLE
            && value.getMutationPrecondition()
                == MutationPrecondition.UNAVAILABLE
            && !value.isGenerationBindingRequirementCaptured()
            && !value.isDesiredStateCaptured()
            && !value.isIdempotencyPolicyCaptured()
            && !value.isMutationPreconditionCaptured(),
            "unknown callback gains no inferred desired-state rule");
        assertDormant(value);
    }

    private static void assertCapturedButDormant(
        GameTickEventRestorationRequirement value) {
        check(value.isGenerationBindingRequirementCaptured()
            && value.isDesiredStateCaptured()
            && value.isIdempotencyPolicyCaptured()
            && value.isMutationPreconditionCaptured(),
            "known rules are captured");
        assertDormant(value);
    }

    private static void assertDormant(
        GameTickEventRestorationRequirement value) {
        check(!value.isGenerationMatchPerformed()
            && !value.isTargetStateInspected()
            && !value.isMutationPerformed()
            && !value.isTargetLookupPerformed()
            && !value.isExecutableRestoration()
            && !value.isLifecycleAuthority(),
            "rules grant no inspection, mutation, or lifecycle authority");
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
    }
}
'''


class LayeredMapsSliceOneHundredElevenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-eleven-"
        )
        cls.classes = Path(cls.compile_temp.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.compile_temp.name) / (
            "src/com/openrsc/server/event/rsc/"
            "GenerationIdempotencyRequirementFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(STATE), str(REQUIREMENT), str(fixture),
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

    def test_requirement_fixture_is_executable_and_fail_closed(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.event.rsc."
                "GenerationIdempotencyRequirementFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_requirement_contains_no_runtime_target_or_mutation_handle(self):
        source = REQUIREMENT.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.model",
            "import com.openrsc.server.net",
            "GameTickEvent ",
            "registerGameObject",
            "unregisterGameObject",
            "getGameObject",
            "sendUpdatePackets",
            ".doRun()",
            ".stop()",
        ):
            self.assertNotIn(forbidden, source)
        self.assertIn("isGenerationMatchPerformed() { return false; }", source)
        self.assertIn("isTargetStateInspected() { return false; }", source)
        self.assertIn("isMutationPerformed() { return false; }", source)

    def test_known_pairs_and_stale_generation_refusal_are_explicit(self):
        source = REQUIREMENT.read_text(encoding="utf-8")
        for declaration in (
            "MATCH_RECONSTRUCTION_GENERATION",
            "AUTHORED_SCENERY_PRESENT",
            "AUTHORED_SCENERY_ABSENT",
            "ALREADY_SATISFIED_IS_NO_OP_SUCCESS",
            "DESTINATION_EMPTY_OR_EXACT_AUTHORED_TRANSIENT",
            "EXACT_AUTHORED_ENTITY_PRESENT",
        ):
            self.assertIn(declaration, source)
        self.assertIn(
            "A stale authored callback cannot bind into another population pass",
            source,
        )

    def test_living_plan_records_slice_one_hundred_eleven_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 111: Dormant generation and idempotency prerequisites",
            plan,
        )
        self.assertIn("No target state is inspected", plan)


if __name__ == "__main__":
    unittest.main()
