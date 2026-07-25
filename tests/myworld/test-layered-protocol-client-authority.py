#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_STATE = (
    ROOT / "Client_Base/src/orsc/LayeredSceneContextState.java"
)
CLIENT_HANDLER = ROOT / "Client_Base/src/orsc/PacketHandler.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
SCENE_BASELINE_STATE = ROOT / "Client_Base/src/orsc/SceneBaselineState.java"
MOVEMENT_STAGE = ROOT / "Client_Base/src/orsc/MovementSnapshotStage.java"
CONFIGURATION = ROOT / "server/src/com/openrsc/server/ServerConfiguration.java"
REGION_MANAGER = (
    ROOT
    / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
GAME_STATE_UPDATER = ROOT / "server/src/com/openrsc/server/GameStateUpdater.java"
OPCODES = ROOT / "server/src/com/openrsc/server/net/rsc/enums/OpcodeOut.java"
PAYLOAD_VALIDATOR = (
    ROOT / "server/src/com/openrsc/server/net/rsc/PayloadValidator.java"
)
CUSTOM_GENERATOR = (
    ROOT
    / "server/src/com/openrsc/server/net/rsc/generators/impl/"
    "PayloadCustomGenerator.java"
)
PLAN = (
    ROOT
    / "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r"""
package orsc;

public final class LayeredProtocolClientAuthorityFixture {
    public static void main(String[] args) {
        LayeredSceneContextState state = new LayeredSceneContextState();
        check("layer client waiting".equals(state.summary()), "initial state");

        LayeredSceneContextState.ApplyResult surface = state.accept(
            1, 1, 100, "global", 100, 400, 0, 100, 400);
        check(!surface.isScopeChanged(), "initial context is not a transition");
        check(surface.getLegacyPlane() == 0, "surface plane");
        check(state.matchesSequence(1), "surface sequence");
        check("global:0:1".equals(state.scopeIdentity()), "surface scope");
        state.acceptLegacyPlayerPosition(100, 400);
        state.acceptLegacyPlayerPosition(101, 401);
        check(state.summary().contains("101,401,L0"), "surface movement");

        expectState(() -> state.accept(
            1, 1, 101, "global", 101, 401, 0, 101, 401));
        expectIllegal(() -> state.accept(
            1, 2, 102, "global", 101, 401, 0, 101, 402));
        check(state.matchesSequence(1), "failed receipt is atomic");

        LayeredSceneContextState.ApplyResult sameScope = state.accept(
            1, 2, 103, "global", 101, 401, 0, 101, 401);
        check(!sameScope.isScopeChanged(), "same-scope refresh");
        state.acceptLegacyPlayerPosition(101, 401);

        LayeredSceneContextState.ApplyResult upper = state.accept(
            1, 3, 104, "global", 101, 401, 1, 101, 1345);
        check(upper.isScopeChanged(), "upper transition");
        check(upper.getLegacyPlane() == 1, "upper plane");
        state.acceptLegacyPlayerPosition(101, 1345);
        expectState(() -> state.acceptLegacyPlayerPosition(101, 401));

        LayeredSceneContextState.ApplyResult underground = state.accept(
            1, 4, 105, "global", 101, 401, -1, 101, 3233);
        check(underground.isScopeChanged(), "underground transition");
        check(underground.getLegacyPlane() == 3, "underground plane");
        state.acceptLegacyPlayerPosition(101, 3233);
        check(state.summary().contains("contexts/positions/scopes 4/5/2"),
            "acceptance counters");

        expectIllegal(() -> state.accept(
            1, 5, 106, "instance-1", 101, 401, -1, 101, 3233));
        expectIllegal(() -> state.accept(
            1, 5, 106, "global", 101, 401, -2, 101, 401));
        expectIllegal(() -> state.accept(
            2, 5, 106, "global", 101, 401, -1, 101, 3233));
        expectIllegal(() -> state.accept(
            1, 5, 106, "Bad Space", 101, 401, -1, 101, 3233));
        check(state.matchesSequence(4), "refusals preserve context");

        state.reset();
        check(!state.hasContext(), "logout reset");
        state.accept(1, 1, 200, "global", 120, 648, 0, 120, 648);
        state.acceptLegacyPlayerPosition(120, 648);
        check(state.matchesSequence(1), "reconnect sequence restart");
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void expectState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
"""


class LayeredProtocolClientAuthorityTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-protocol-client-authority-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        fixture = cls.temp / "src/orsc/LayeredProtocolClientAuthorityFixture.java"
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            [
                "javac",
                "-Xlint:all",
                "-source",
                "8",
                "-target",
                "8",
                "-encoding",
                "UTF-8",
                "-d",
                str(cls.classes),
                str(CLIENT_STATE),
                str(fixture),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_context_receipts_scope_changes_refusals_and_reconnect(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "orsc.LayeredProtocolClientAuthorityFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_gate_protocol_and_client_consumers_are_explicit(self):
        configuration = CONFIGURATION.read_text(encoding="utf-8")
        region_manager = REGION_MANAGER.read_text(encoding="utf-8")
        updater = GAME_STATE_UPDATER.read_text(encoding="utf-8")
        opcodes = OPCODES.read_text(encoding="utf-8")
        validator = PAYLOAD_VALIDATOR.read_text(encoding="utf-8")
        generator = CUSTOM_GENERATOR.read_text(encoding="utf-8")
        handler = CLIENT_HANDLER.read_text(encoding="utf-8")
        client = CLIENT.read_text(encoding="utf-8")
        baseline = SCENE_BASELINE_STATE.read_text(encoding="utf-8")
        movement_stage = MOVEMENT_STAGE.read_text(encoding="utf-8")

        self.assertIn("WANT_LAYERED_PROTOCOL_CLIENT_AUTHORITY", configuration)
        self.assertIn(
            "OPENRSC_LAYERED_PROTOCOL_CLIENT_AUTHORITY", configuration
        )
        self.assertIn('"want_layered_protocol_client_authority"', configuration)
        self.assertIn(
            "Layered protocol/client authority requires layered Player",
            region_manager,
        )
        self.assertIn("ensureLayeredSceneContext(player)", updater)
        self.assertIn("LAYERED_SCENE_BASELINE_PROTOCOL_VERSION = 6", updater)
        self.assertIn(
            "LAYERED_MOVEMENT_SNAPSHOT_PROTOCOL_VERSION = 2", updater
        )
        self.assertIn("SEND_LAYERED_SCENE_CONTEXT", opcodes)
        self.assertIn("LayeredSceneContextStruct.class", validator)
        self.assertIn("SEND_LAYERED_SCENE_CONTEXT, 152", generator)
        self.assertIn("updateLayeredSceneContext(length)", handler)
        self.assertIn("acceptLegacyPlayerPosition", handler)
        self.assertIn("resetLayeredSceneIdentityCaches", client)
        self.assertIn("resetForScopeChange", baseline)
        self.assertIn("void reset()", movement_stage)

    def test_plan_preserves_compatibility_and_defers_level_minus_two(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "Phase 5 Authority Milestone C: Protocol and Client Location Identity",
            plan,
        )
        self.assertIn(
            "existing opcode layouts for", plan
        )
        self.assertIn(
            "The first synthetic level `-2` fixture follows this milestone",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
