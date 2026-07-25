#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
CONFIGURATION = ROOT / "server/src/com/openrsc/server/ServerConfiguration.java"
REGION_MANAGER = (
    ROOT
    / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
ENTITY = ROOT / "server/src/com/openrsc/server/model/entity/Entity.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
PLAYER_SERVICE = ROOT / "server/src/com/openrsc/server/service/PlayerService.java"
DATABASE = ROOT / "server/src/com/openrsc/server/database/GameDatabase.java"
UPDATER = ROOT / "server/src/com/openrsc/server/GameStateUpdater.java"
STRUCT = (
    ROOT
    / "server/src/com/openrsc/server/net/rsc/struct/outgoing/"
    "LayeredSceneContextStruct.java"
)
GENERATOR = (
    ROOT
    / "server/src/com/openrsc/server/net/rsc/generators/impl/"
    "PayloadCustomGenerator.java"
)
DEVELOPMENT = (
    ROOT
    / "server/plugins/com/openrsc/server/plugins/authentic/commands/"
    "Development.java"
)
CLIENT_STATE = ROOT / "Client_Base/src/orsc/LayeredSceneContextState.java"
CLIENT_HANDLER = ROOT / "Client_Base/src/orsc/PacketHandler.java"
CLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
CLIENT_WORLD = ROOT / "Client_Base/src/orsc/graphics/three/World.java"
PLAN = (
    ROOT
    / "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


POINT_STUB = r"""
package com.openrsc.server.model;

public final class Point {
    private final int x;
    private final int y;

    private Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public static Point location(int x, int y) {
        return new Point(x, y);
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
"""


FIXTURE = r"""
package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.Point;

public final class LayeredSyntheticDeepFixture {
    public static void main(String[] args) {
        WorldLocation surface =
            WorldLocation.global(new WorldCoordinate(450, 600, 0));
        Point surfacePoint =
            LayeredCompatibilityPointAdapter.toCompatibilityPoint(
                surface, false);
        check(surfacePoint.getX() == 450 && surfacePoint.getY() == 600,
            "ordinary projection remains unchanged");
        check(LegacyPackedPointAdapter.ID.equals(
            LayeredCompatibilityPointAdapter.projectionId(
                surface, false)), "ordinary projection identity");

        WorldLocation deep =
            LayeredCompatibilityPointAdapter.syntheticDeepEntry();
        expectIllegal(() ->
            LayeredCompatibilityPointAdapter.toCompatibilityPoint(
                deep, false));
        Point deepPoint =
            LayeredCompatibilityPointAdapter.toCompatibilityPoint(
                deep, true);
        check(deepPoint.getX() == 450 && deepPoint.getY() == 600,
            "deep point receipt");
        check(LayeredCompatibilityPointAdapter.SYNTHETIC_DEEP_FIXTURE_ID
            .equals(LayeredCompatibilityPointAdapter.projectionId(
                deep, true)), "deep projection identity");
        check(LayeredCompatibilityPointAdapter.compatibilityPlane(
            deep, true) == 0, "deep terrain plane");

        WorldLocation walked =
            LayeredCompatibilityPointAdapter.fromCompatibilityPoint(
                Point.location(451, 600), deep, true, false);
        check(walked.equals(
            LayeredCompatibilityPointAdapter.deepLocation(451, 600)),
            "in-bounds movement retains deep scope");
        expectIllegal(() ->
            LayeredCompatibilityPointAdapter.fromCompatibilityPoint(
                Point.location(461, 600), deep, true, false));
        WorldLocation explicitExit =
            LayeredCompatibilityPointAdapter.fromCompatibilityPoint(
                Point.location(461, 600), deep, true, true);
        check(explicitExit.equals(
            WorldLocation.global(new WorldCoordinate(461, 600, 0))),
            "explicit scope exit");

        check(LayeredCompatibilityPointAdapter.requireReceipt(
            LayeredCompatibilityPointAdapter.SYNTHETIC_DEEP_FIXTURE_ID,
            deep, deepPoint, true).equals(deep), "exact deep receipt");
        expectIllegal(() -> LayeredCompatibilityPointAdapter.requireReceipt(
            LegacyPackedPointAdapter.ID, deep, deepPoint, true));
        expectIllegal(() -> LayeredCompatibilityPointAdapter.requireReceipt(
            LayeredCompatibilityPointAdapter.SYNTHETIC_DEEP_FIXTURE_ID,
            deep, Point.location(451, 600), true));
        expectIllegal(() -> LayeredCompatibilityPointAdapter.deepLocation(
            439, 600));
        expectIllegal(() -> LayeredCompatibilityPointAdapter
            .toCompatibilityPoint(
                new WorldLocation(
                    new WorldSpaceId("instance-1"),
                    new WorldCoordinate(450, 600, -2)),
                true));
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
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


class LayeredSyntheticDeepFixtureTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-synthetic-deep-fixture-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LayeredSyntheticDeepFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")

        sources = [
            point,
            COORDINATES / "WorldCoordinate.java",
            COORDINATES / "WorldSpaceId.java",
            COORDINATES / "WorldLocation.java",
            COORDINATES / "LegacyPackedPointAdapter.java",
            COORDINATES / "LayeredCompatibilityPointAdapter.java",
            fixture,
        ]
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
                *(str(path) for path in sources),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_projection_bounds_receipts_and_scope_exit(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "LayeredSyntheticDeepFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_fourth_gate_and_runtime_consumers_are_explicit(self):
        configuration = CONFIGURATION.read_text(encoding="utf-8")
        region_manager = REGION_MANAGER.read_text(encoding="utf-8")
        entity = ENTITY.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        player_service = PLAYER_SERVICE.read_text(encoding="utf-8")
        database = DATABASE.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")
        client = CLIENT.read_text(encoding="utf-8")
        client_world = CLIENT_WORLD.read_text(encoding="utf-8")

        self.assertIn("WANT_LAYERED_SYNTHETIC_DEEP_FIXTURE", configuration)
        self.assertIn(
            "OPENRSC_LAYERED_SYNTHETIC_DEEP_FIXTURE", configuration
        )
        self.assertIn('"want_layered_synthetic_deep_fixture"', configuration)
        for prerequisite in (
            "WANT_LAYERED_PLAYER_LOCATION_AUTHORITY",
            "WANT_LAYERED_SPATIAL_RUNTIME_AUTHORITY",
            "WANT_LAYERED_PROTOCOL_CLIENT_AUTHORITY",
        ):
            self.assertIn(prerequisite, region_manager)
        self.assertIn("LayeredCompatibilityPointAdapter", entity)
        self.assertIn("LayeredCompatibilityPointAdapter", player)
        self.assertIn("LayeredCompatibilityPointAdapter", path_validation)
        self.assertIn("WANT_LAYERED_SYNTHETIC_DEEP_FIXTURE", player_service)
        self.assertIn('command.equalsIgnoreCase("deepfixture")', development)
        self.assertNotIn(
            "requireSyntheticDeepSurfaceRectangleClear", development
        )
        self.assertIn("SYNTHETIC_DEEP_NPC_ATTRIBUTE", development)
        self.assertIn("SYNTHETIC_DEEP_ITEM_ATTRIBUTE", development)
        self.assertIn("syntheticDeepFixtureTile", region_manager)
        self.assertIn(
            "LayeredCompatibilityPointAdapter.deepLocation(x, y)",
            path_validation,
        )
        self.assertIn(
            "WANT_LAYERED_SYNTHETIC_DEEP_FIXTURE", database
        )
        self.assertIn(
            "setSyntheticDeepFixtureTerrain", client
        )
        self.assertIn(
            "applySyntheticDeepFixtureTerrain", client_world
        )
        self.assertIn(
            "tile.groundOverlay = 0", client_world
        )

    def test_protocol_v1_v2_projection_boundary_is_explicit(self):
        updater = UPDATER.read_text(encoding="utf-8")
        struct = STRUCT.read_text(encoding="utf-8")
        generator = GENERATOR.read_text(encoding="utf-8")
        client_state = CLIENT_STATE.read_text(encoding="utf-8")
        client_handler = CLIENT_HANDLER.read_text(encoding="utf-8")

        self.assertIn("LAYERED_SCENE_CONTEXT_PROTOCOL_VERSION = 1", updater)
        self.assertIn(
            "SYNTHETIC_DEEP_SCENE_CONTEXT_PROTOCOL_VERSION = 2", updater
        )
        self.assertIn("projectionId", struct)
        self.assertIn("protocolVersion >= 2", generator)
        self.assertIn("SYNTHETIC_DEEP_PROTOCOL_VERSION = 2", client_state)
        self.assertIn("SYNTHETIC_DEEP_PROJECTION", client_state)
        self.assertIn(
            "String projectionId = protocolVersion >= 2", client_handler
        )

    def test_plan_records_bounded_nonproduction_fixture(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "Phase 5 Authority Milestone D: Synthetic Deep-Level",
            plan,
        )
        self.assertIn("synthetic-deep-fixture-v1", plan)
        self.assertIn("X `440..460`, Y `590..610`", plan)
        self.assertIn("not the deep-underground map implementation", plan)
        self.assertIn(
            "never added to JSON/XML placements",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
