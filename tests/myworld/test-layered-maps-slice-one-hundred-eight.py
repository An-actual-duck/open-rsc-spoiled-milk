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
LOGIN_REQUEST = ROOT / "server/src/com/openrsc/server/login/LoginRequest.java"
LOGIN_HANDLER = ROOT / (
    "server/src/com/openrsc/server/net/rsc/LoginPacketHandler.java"
)
ACTION_SENDER = ROOT / (
    "server/src/com/openrsc/server/net/rsc/ActionSender.java"
)
SERVER = ROOT / "server/src/com/openrsc/server/Server.java"
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.event.rsc;

import com.openrsc.server.event.rsc.GameTickEventRestorationRequirement
    .ArrivalOrderingRequirement;
import com.openrsc.server.event.rsc.GameTickEventRestorationRequirement
    .BindingEvidence;
import com.openrsc.server.event.rsc.GameTickEventRestorationRequirement
    .TargetConflictPolicy;
import com.openrsc.server.event.rsc.GameTickEventRestorationRequirement
    .TargetSubject;
import com.openrsc.server.event.rsc.GameTickEventRestorationState
    .AuthoredConstructionKind;
import com.openrsc.server.event.rsc.GameTickEventRestorationState
    .AuthoredPlacementState;
import com.openrsc.server.event.rsc.GameTickEventRestorationState
    .SceneryState;

public final class EventRestorationRequirementFixture {
    public static void main(String[] args) {
        authoredSpawnBindsDestination();
        authoredRemovalBindsExistingEntity();
        missingIdentityRefusesBothMutations();
        unavailableStateStaysUnavailable();
    }

    private static AuthoredPlacementState authored() {
        return AuthoredPlacementState.of(
            7L, 10, 10, 42, AuthoredConstructionKind.SCENERY);
    }

    private static SceneryState scenery(
        AuthoredPlacementState authoredPlacement) {
        return SceneryState.of(
            310, 310, 524, 489, 0, 0, null, 0,
            authoredPlacement);
    }

    private static void authoredSpawnBindsDestination() {
        GameTickEventRestorationRequirement requirement =
            GameTickEventRestorationRequirement.from(
                GameTickEventRestorationState.scenerySpawn(
                    scenery(authored()), false));
        check(requirement.getTargetSubject()
                == TargetSubject.AUTHORED_DESTINATION_SLOT
            && requirement.getBindingEvidence()
                == BindingEvidence.AUTHORED_PLACEMENT_IDENTITY
            && requirement.getTargetConflictPolicy()
                == TargetConflictPolicy.REFUSE_MISMATCH_OR_AMBIGUITY
            && requirement.getArrivalOrderingRequirement()
                == ArrivalOrderingRequirement
                    .RECONCILE_BEFORE_FIRST_VISIBILITY
            && requirement.isTargetBindingComplete()
            && requirement.isArrivalOrderingCaptured(),
            "authored spawn binds its destination before visibility");
        check(requirement.getAuthoredTarget().getGeneration() == 7L
            && requirement.getAuthoredTarget().getPackedRegionX() == 10
            && requirement.getAuthoredTarget().getPackedRegionY() == 10
            && requirement.getAuthoredTarget().getSourceOrdinal() == 42
            && requirement.getAuthoredTarget().getConstructionKind()
                == AuthoredConstructionKind.SCENERY,
            "authored destination identity is copied as scalars");
        assertDormant(requirement);
    }

    private static void authoredRemovalBindsExistingEntity() {
        GameTickEventRestorationRequirement requirement =
            GameTickEventRestorationRequirement.from(
                GameTickEventRestorationState.sceneryRemove(
                    scenery(authored())));
        check(requirement.getTargetSubject()
                == TargetSubject.AUTHORED_EXISTING_ENTITY
            && requirement.isTargetBindingComplete(),
            "authored removal requires the exact existing entity");
        assertDormant(requirement);
    }

    private static void missingIdentityRefusesBothMutations() {
        GameTickEventRestorationRequirement spawn =
            GameTickEventRestorationRequirement.from(
                GameTickEventRestorationState.scenerySpawn(
                    scenery(null), false));
        GameTickEventRestorationRequirement removal =
            GameTickEventRestorationRequirement.from(
                GameTickEventRestorationState.sceneryRemove(
                    scenery(null)));
        for (GameTickEventRestorationRequirement requirement
                : new GameTickEventRestorationRequirement[]{spawn, removal}) {
            check(requirement.getBindingEvidence()
                    == BindingEvidence.MISSING_AUTHORED_PLACEMENT_IDENTITY
                && requirement.getAuthoredTarget() == null
                && !requirement.isTargetBindingComplete()
                && requirement.getTargetConflictPolicy()
                    == TargetConflictPolicy.REFUSE_MISMATCH_OR_AMBIGUITY
                && requirement.isArrivalOrderingCaptured(),
                "identity-less mutation remains explicitly unresolved");
            assertDormant(requirement);
        }
    }

    private static void unavailableStateStaysUnavailable() {
        GameTickEventRestorationRequirement requirement =
            GameTickEventRestorationRequirement.from(
                GameTickEventRestorationState.unavailable());
        check(requirement.getTargetSubject() == TargetSubject.UNAVAILABLE
            && requirement.getBindingEvidence() == BindingEvidence.UNAVAILABLE
            && requirement.getAuthoredTarget() == null
            && requirement.getTargetConflictPolicy()
                == TargetConflictPolicy.UNAVAILABLE
            && requirement.getArrivalOrderingRequirement()
                == ArrivalOrderingRequirement.UNAVAILABLE
            && !requirement.isTargetBindingComplete()
            && !requirement.isArrivalOrderingCaptured(),
            "unknown callbacks gain no inferred prerequisite");
        assertDormant(requirement);
        expectNull(() -> GameTickEventRestorationRequirement.from(null));
    }

    private static void assertDormant(
        GameTickEventRestorationRequirement requirement) {
        check(requirement.isPointInTimeOnly()
            && requirement.isDetachedPrimitiveCopy()
            && !requirement.isTargetLookupPerformed()
            && !requirement.isArrivalGate()
            && !requirement.isExecutableRestoration()
            && !requirement.isLifecycleAuthority(),
            "requirement grants no target, arrival, or lifecycle authority");
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


class LayeredMapsSliceOneHundredEightTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-eight-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        fixture = cls.temp / (
            "src/com/openrsc/server/event/rsc/"
            "EventRestorationRequirementFixture.java"
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
                "EventRestorationRequirementFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_requirement_has_no_runtime_or_lifecycle_handle(self):
        source = REQUIREMENT.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.model",
            "import com.openrsc.server.net",
            "GameTickEvent ",
            "registerGameObject",
            "unregisterGameObject",
            "eventStore",
            "sendUpdatePackets",
            ".doRun()",
            ".stop()",
        ):
            self.assertNotIn(forbidden, source)
        self.assertIn("isTargetLookupPerformed() { return false; }", source)
        self.assertIn("isArrivalGate() { return false; }", source)
        self.assertIn("isExecutableRestoration() { return false; }", source)

    def test_current_login_sends_first_visibility_inside_login_event(self):
        login_request = LOGIN_REQUEST.read_text(encoding="utf-8")
        login_handler = LOGIN_HANDLER.read_text(encoding="utf-8")
        action_sender = ACTION_SENDER.read_text(encoding="utf-8")
        server = SERVER.read_text(encoding="utf-8")

        self.assertIn(
            'new ImmediateEvent(getServer().getWorld(), "Login Player")',
            login_request,
        )
        self.assertIn("ActionSender.sendLogin(loadedPlayer);", login_handler)
        send_login = action_sender[
            action_sender.index("static void sendLogin(Player player)"):
            action_sender.index(
                "public static void sendReleasedNameExplanation",
                action_sender.index("static void sendLogin(Player player)"),
            )
        ]
        self.assertLess(
            send_login.index("registerPlayer(player)"),
            send_login.index("sendUpdatePackets(player)"),
        )
        tick = server[
            server.index("public void run()"):
            server.index("monitorTickPerformance()", server.index("public void run()"))
        ]
        self.assertLess(
            tick.index("processNonPlayerEvents()"),
            tick.index("player.processTick()"),
        )

    def test_observer_remains_unchanged_and_without_requirement_access(self):
        source = OBSERVER.read_text(encoding="utf-8")
        self.assertNotIn("GameTickEventRestorationRequirement", source)
        self.assertNotIn("RECONCILE_BEFORE_FIRST_VISIBILITY", source)

    def test_living_plan_records_slice_one_hundred_eight_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 108: Dormant scenery target and arrival requirements",
            plan,
        )
        self.assertIn("RECONCILE_BEFORE_FIRST_VISIBILITY", plan)
        self.assertIn("No target lookup is performed", plan)


if __name__ == "__main__":
    unittest.main()
