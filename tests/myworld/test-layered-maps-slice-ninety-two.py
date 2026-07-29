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
EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/GameTickEvent.java"
WORLD = ROOT / "server/src/com/openrsc/server/model/world/World.java"
HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameEventHandler.java"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
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
import com.openrsc.server.event.rsc.GameTickEventRestorationState
    .SceneryState;

public final class EventRestorationStateFixture {
    public static void main(String[] args) {
        spawnPayloadIsDetachedButInert();
        removalBindingEvidenceStaysHonest();
        invalidInputsRefuse();
    }

    private static void spawnPayloadIsDetachedButInert() {
        AuthoredPlacementState authored = AuthoredPlacementState.of(
            7L, 10, 10, 42, AuthoredConstructionKind.SCENERY);
        SceneryState scenery = SceneryState.of(
            310, 310, 524, 489, 0, 0, "private-owner", 0, authored);
        GameTickEventRestorationState state =
            GameTickEventRestorationState.scenerySpawn(scenery, true);

        check(state.getKind()
                == GameTickEventRestorationState.Kind.SCENERY_SPAWN
            && state.getTargetBindingEvidence()
                == GameTickEventRestorationState.TargetBindingEvidence
                    .NOT_REQUIRED
            && state.getExecutionSemantics()
                == GameTickEventRestorationState.ExecutionSemantics.ONE_SHOT
            && state.getTimeProgressionPolicy()
                == GameTickEventRestorationState.TimeProgressionPolicy
                    .CONTINUE_SERVER_TICKS
            && state.isExecutionSemanticsCaptured()
            && state.isForceFullBlock()
            && state.isDetachedCallbackPayloadComplete(),
            "spawn retains complete callback inputs");
        check(state.getScenery().getObjectId() == 310
            && state.getScenery().getPermanentObjectId() == 310
            && state.getScenery().getX() == 524
            && state.getScenery().getY() == 489
            && state.getScenery().hasOwner()
            && state.getScenery().getAuthoredPlacement().getGeneration() == 7L
            && state.getScenery().getAuthoredPlacement()
                .getConstructionKind() == AuthoredConstructionKind.SCENERY,
            "constructor, owner, and authored provenance are detached");
        check(state.isPointInTimeOnly()
            && state.isDetachedPrimitiveCopy()
            && !state.isRuntimeAttributesCaptured()
            && !state.isSchedulerStateCaptured()
            && !state.isSchedulerIdentityCaptured()
            && !state.isTargetBindingLookupPerformed()
            && !state.isStandaloneRestorationComplete()
            && !state.isPreservationPerformed()
            && !state.isReloadRequest()
            && !state.isEventCancellation()
            && !state.isEventReschedule()
            && !state.isEntityRegistry()
            && !state.isArrivalGate()
            && !state.isTeardownTransaction()
            && !state.isLifecycleAuthority(),
            "detached payload grants no scheduler or lifecycle authority");
    }

    private static void removalBindingEvidenceStaysHonest() {
        SceneryState identityless = SceneryState.of(
            4, 4, 145, 660, 0, 0, null, 3, null);
        GameTickEventRestorationState unresolved =
            GameTickEventRestorationState.sceneryRemove(identityless);
        check(unresolved.getKind()
                == GameTickEventRestorationState.Kind.SCENERY_REMOVE
            && unresolved.getTargetBindingEvidence()
                == GameTickEventRestorationState.TargetBindingEvidence
                    .LIVE_ENTITY_REFERENCE_ONLY
            && unresolved.getExecutionSemantics()
                == GameTickEventRestorationState.ExecutionSemantics.ONE_SHOT
            && unresolved.getTimeProgressionPolicy()
                == GameTickEventRestorationState.TimeProgressionPolicy
                    .CONTINUE_SERVER_TICKS
            && !unresolved.isDetachedCallbackPayloadComplete()
            && unresolved.getScenery().getRuntimeAttributeCount() == 3,
            "identity-less removal remains bound to an uncaptured live target");

        SceneryState authored = SceneryState.of(
            4, 1, 149, 656, 0, 0, null, 0,
            AuthoredPlacementState.of(
                8L, 3, 13, 91,
                AuthoredConstructionKind.HARVESTING_SCENERY));
        GameTickEventRestorationState rebindable =
            GameTickEventRestorationState.sceneryRemove(authored);
        check(rebindable.getTargetBindingEvidence()
                == GameTickEventRestorationState.TargetBindingEvidence
                    .AUTHORED_PLACEMENT_IDENTITY
            && rebindable.isDetachedCallbackPayloadComplete()
            && !rebindable.isTargetBindingLookupPerformed()
            && !rebindable.isStandaloneRestorationComplete(),
            "authored identity is binding evidence, not a performed lookup");

        GameTickEventRestorationState unavailable =
            GameTickEventRestorationState.unavailable();
        check(unavailable.getKind()
                == GameTickEventRestorationState.Kind.UNAVAILABLE
            && unavailable.getScenery() == null
            && unavailable.getTargetBindingEvidence()
                == GameTickEventRestorationState.TargetBindingEvidence
                    .UNAVAILABLE
            && unavailable.getExecutionSemantics()
                == GameTickEventRestorationState.ExecutionSemantics.UNAVAILABLE
            && unavailable.getTimeProgressionPolicy()
                == GameTickEventRestorationState.TimeProgressionPolicy
                    .UNAVAILABLE
            && !unavailable.isExecutionSemanticsCaptured(),
            "legacy callbacks default to unavailable state");
    }

    private static void invalidInputsRefuse() {
        expectIllegal(() -> SceneryState.of(
            -1, 1, 1, 1, 0, 0, null, 0, null));
        expectIllegal(() -> SceneryState.of(
            1, 1, 1, 1, 8, 0, null, 0, null));
        expectIllegal(() -> SceneryState.of(
            1, 1, 1, 1, 0, 2, null, 0, null));
        expectIllegal(() -> AuthoredPlacementState.of(
            0L, 1, 1, 1, AuthoredConstructionKind.SCENERY));
        expectIllegal(() -> AuthoredPlacementState.of(
            1L, 1, 1,
            GameTickEventRestorationState.MAXIMUM_AUTHORED_SOURCE_ORDINAL + 1,
            AuthoredConstructionKind.SCENERY));
        expectNull(() -> GameTickEventRestorationState.scenerySpawn(null, false));
        expectNull(() -> GameTickEventRestorationState.sceneryRemove(null));
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


class LayeredMapsSliceNinetyTwoTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-ninety-two-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        fixture = cls.temp / (
            "src/com/openrsc/server/event/rsc/"
            "EventRestorationStateFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(STATE), str(fixture),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_restoration_state_contract_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.event.rsc.EventRestorationStateFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_only_known_scenery_callbacks_declare_state(self):
        event = EVENT.read_text(encoding="utf-8")
        world = WORLD.read_text(encoding="utf-8")
        self.assertIn("getRestorationState()", event)
        self.assertIn("GameTickEventRestorationState.unavailable()", event)
        self.assertEqual(2, world.count("getRestorationState()"))
        self.assertIn("GameTickEventRestorationState.sceneryRemove(", world)
        self.assertIn("GameTickEventRestorationState.scenerySpawn(", world)
        self.assertIn("object.getRuntimeAttributeCount()", world)
        self.assertIn("loc.getAuthoredPlacementIdentity()", world)
        self.assertIn("forceFullBlock", world)

    def test_contract_is_detached_and_later_capture_remains_private(self):
        source = STATE.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.model.entity",
            "import com.openrsc.server.model.world",
            "import com.openrsc.server.external",
            "GameObject ", "GameObjectLoc ", "Region ", "event.stop()",
            "eventStore", "registerGameObject", "unregisterGameObject",
        ):
            self.assertNotIn(forbidden, source)
        method = "getRestorationState()"
        self.assertIn(method, HANDLER.read_text(encoding="utf-8"))
        observer = OBSERVER.read_text(encoding="utf-8")
        self.assertIn(method, observer)
        self.assertNotIn("scenery.getOwner()", observer)

    def test_living_plan_records_slice_ninety_two_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 92: Dormant scenery-event restoration state", plan
        )
        self.assertIn("LIVE_ENTITY_REFERENCE_ONLY", plan)
        self.assertIn("No callback is cancelled", plan)


if __name__ == "__main__":
    unittest.main()
