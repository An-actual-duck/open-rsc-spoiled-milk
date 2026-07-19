#!/usr/bin/env python3
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TOOL_PACKAGE = ROOT / "tools/layered-maps/src/com/openrsc/layeredmaps"
SERVER_PACKAGE = ROOT / "server/src/com/openrsc/server/model/world/coordinate"


POINT_STUB = r"""
package com.openrsc.server.model;

public class Point {
    private final short x;
    private final short y;

    private Point(short x, short y) {
        this.x = x;
        this.y = y;
    }

    public static Point location(int x, int y) {
        return location((short) x, (short) y);
    }

    public static Point location(short x, short y) {
        if (x < 0 || y < 0) {
            throw new IllegalArgumentException("negative packed point");
        }
        return new Point(x, y);
    }

    public final int getX() {
        return x;
    }

    public final int getY() {
        return y;
    }
}
"""


PARITY_FIXTURE = r"""
import com.openrsc.server.model.Point;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;

public final class ServerLayeredCoordinateFixture {
    public static void main(String[] args) {
        check(LegacyPackedPointAdapter.ID.equals(
            com.openrsc.layeredmaps.LegacyPackedCoordinateCodec.ID), "codec ID");

        int[] xs = {0, 1, 1008, LegacyPackedPointAdapter.MAX_LEGACY_X};
        for (int packedY = LegacyPackedPointAdapter.MIN_PACKED_Y;
                packedY <= LegacyPackedPointAdapter.MAX_PACKED_Y; packedY++) {
            for (int x : xs) {
                com.openrsc.layeredmaps.WorldCoordinate tool =
                    com.openrsc.layeredmaps.LegacyPackedCoordinateCodec.decode(x, packedY);
                com.openrsc.server.model.world.coordinate.WorldLocation server =
                    LegacyPackedPointAdapter.fromPackedValues(x, packedY);
                com.openrsc.server.model.world.coordinate.WorldCoordinate coordinate =
                    server.getCoordinate();

                check(server.getWorldSpace().equals(
                    com.openrsc.server.model.world.coordinate.WorldSpaceId.GLOBAL), "global space");
                check(coordinate.getX() == tool.getX(), "decoded X parity");
                check(coordinate.getY() == tool.getY(), "decoded Y parity");
                check(coordinate.getLevel() == tool.getLevel(), "decoded level parity");

                Point point = LegacyPackedPointAdapter.toLegacyPoint(server);
                check(point.getX() == x && point.getY() == packedY, "server packed round trip");
                check(LegacyPackedPointAdapter.fromLegacyPoint(point).equals(server),
                    "Point bridge round trip");

                com.openrsc.layeredmaps.LegacyPackedCoordinateCodec.PackedCoordinate toolPacked =
                    com.openrsc.layeredmaps.LegacyPackedCoordinateCodec.encode(tool);
                check(point.getX() == toolPacked.getX(), "encoded X parity");
                check(point.getY() == toolPacked.getY(), "encoded Y parity");
            }
        }

        for (int plane = 0; plane < 4; plane++) {
            int toolLevel = com.openrsc.layeredmaps.LegacyPackedCoordinateCodec
                .levelForLegacyPlane(plane);
            int serverLevel = LegacyPackedPointAdapter.levelForLegacyPlane(plane);
            check(toolLevel == serverLevel, "plane mapping parity");
            check(LegacyPackedPointAdapter.legacyPlaneForLevel(serverLevel) == plane,
                "server reverse plane mapping");
        }

        com.openrsc.server.model.world.coordinate.WorldCoordinate signed =
            new com.openrsc.server.model.world.coordinate.WorldCoordinate(-1, -49, -2);
        check(signed.getSectorX() == -1 && signed.getLocalX() == 47, "signed X addressing");
        check(signed.getSectorY() == -2 && signed.getLocalY() == 47, "signed Y addressing");
        check(signed.atLevel(3).equals(
            new com.openrsc.server.model.world.coordinate.WorldCoordinate(-1, -49, 3)),
            "server atLevel");

        com.openrsc.server.model.world.coordinate.WorldLocation global =
            com.openrsc.server.model.world.coordinate.WorldLocation.global(
                new com.openrsc.server.model.world.coordinate.WorldCoordinate(100, 400, 0));
        com.openrsc.server.model.world.coordinate.WorldLocation instance =
            new com.openrsc.server.model.world.coordinate.WorldLocation(
                new com.openrsc.server.model.world.coordinate.WorldSpaceId("instance.quest_1"),
                new com.openrsc.server.model.world.coordinate.WorldCoordinate(100, 400, 0));
        check(!global.equals(instance), "world-space identity");

        expectIllegal(() -> LegacyPackedPointAdapter.fromPackedValues(-1, 0));
        expectIllegal(() -> LegacyPackedPointAdapter.fromPackedValues(32768, 0));
        expectIllegal(() -> LegacyPackedPointAdapter.fromPackedValues(0, -1));
        expectIllegal(() -> LegacyPackedPointAdapter.fromPackedValues(0, 3776));
        expectIllegal(() -> LegacyPackedPointAdapter.toLegacyPoint(instance));
        expectIllegal(() -> LegacyPackedPointAdapter.toLegacyPoint(location(-1, 0, 0)));
        expectIllegal(() -> LegacyPackedPointAdapter.toLegacyPoint(location(0, -1, 0)));
        expectIllegal(() -> LegacyPackedPointAdapter.toLegacyPoint(location(0, 944, 0)));
        expectIllegal(() -> LegacyPackedPointAdapter.toLegacyPoint(location(0, 0, -2)));
        expectIllegal(() -> LegacyPackedPointAdapter.toLegacyPoint(location(0, 0, 3)));
        expectIllegal(() -> new com.openrsc.server.model.world.coordinate.WorldSpaceId("Global"));
        expectArithmetic(() -> new com.openrsc.server.model.world.coordinate.WorldCoordinate(
            Integer.MAX_VALUE, 0, 0).translate(1, 0, 0));
        expectNull(() -> LegacyPackedPointAdapter.fromLegacyPoint(null));
        expectNull(() -> LegacyPackedPointAdapter.toLegacyPoint(null));
    }

    private static com.openrsc.server.model.world.coordinate.WorldLocation location(
            int x, int y, int level) {
        return com.openrsc.server.model.world.coordinate.WorldLocation.global(
            new com.openrsc.server.model.world.coordinate.WorldCoordinate(x, y, level));
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected checked refusal.
        }
    }

    private static void expectArithmetic(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected ArithmeticException");
        } catch (ArithmeticException expected) {
            // Expected overflow refusal.
        }
    }

    private static void expectNull(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected null refusal.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
"""


class LayeredMapsSliceThreeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-three-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point_source = cls.temp / "src/com/openrsc/server/model/Point.java"
        point_source.parent.mkdir(parents=True)
        point_source.write_text(POINT_STUB, encoding="utf-8")
        fixture_source = cls.temp / "src/ServerLayeredCoordinateFixture.java"
        fixture_source.write_text(PARITY_FIXTURE, encoding="utf-8")

        tool_sources = sorted(TOOL_PACKAGE.glob("*.java"))
        server_sources = sorted(SERVER_PACKAGE.glob("*.java"))
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
                str(point_source),
                *(str(path) for path in tool_sources),
                *(str(path) for path in server_sources),
                str(fixture_source),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_server_adapter_matches_tool_contract_exhaustively(self):
        result = subprocess.run(
            ["java", "-cp", str(self.classes), "ServerLayeredCoordinateFixture"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_coordinate_package_has_only_the_approved_staged_consumers(self):
        package_name = "com.openrsc.server.model.world.coordinate"
        checked = 0
        consumers = []
        roots = (ROOT / "server/src", ROOT / "server/plugins")
        for source_root in roots:
            for path in source_root.rglob("*.java"):
                if SERVER_PACKAGE in path.parents:
                    continue
                checked += 1
                if package_name in path.read_text(encoding="utf-8"):
                    consumers.append(path.relative_to(ROOT).as_posix())
        self.assertGreater(checked, 500)
        consumers.sort()
        self.assertEqual(
            [
                "server/src/com/openrsc/server/database/GameDatabase.java",
                "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java",
                "server/src/com/openrsc/server/external/EntityHandler.java",
                "server/src/com/openrsc/server/external/GameObjectLoc.java",
                "server/src/com/openrsc/server/external/ItemLoc.java",
                "server/src/com/openrsc/server/external/NPCLoc.java",
                "server/src/com/openrsc/server/io/WorldEditorTerrainArchive.java",
                "server/src/com/openrsc/server/model/entity/player/Player.java",
                "server/src/com/openrsc/server/model/world/Area.java",
                "server/src/com/openrsc/server/model/world/region/RegionManager.java",
                "server/src/com/openrsc/server/service/PlayerService.java",
            ],
            consumers,
        )

    def test_server_package_contains_only_the_approved_staged_boundary(self):
        self.assertEqual(
            {
                "LegacyPackedPointAdapter.java",
                "LegacyPlayerLocationPersistenceSnapshot.java",
                "LegacyTerrainSectorAdapter.java",
                "LayeredCoordinateParitySnapshot.java",
                "LayeredLocationMirror.java",
                "LayeredRegionMembershipMirror.java",
                "LayeredVisibilityWindowMirror.java",
                "WorldCoordinate.java",
                "WorldArea.java",
                "WorldLocation.java",
                "WorldMapSectorId.java",
                "WorldObjectTransition.java",
                "WorldRegionKey.java",
                "WorldRegionWindow.java",
                "WorldSpaceId.java",
                "WorldTileBounds.java",
                "package-info.java",
            },
            {path.name for path in SERVER_PACKAGE.glob("*.java")},
        )
        adapter = (SERVER_PACKAGE / "LegacyPackedPointAdapter.java").read_text(encoding="utf-8")
        self.assertIn("fromLegacyPoint(Point point)", adapter)
        self.assertIn("toLegacyPoint(WorldLocation location)", adapter)
        self.assertIn("WorldSpaceId.GLOBAL.equals", adapter)
        self.assertNotIn("setLocation", adapter)

    def test_preflight_classifies_adapter_as_a_resolved_contract(self):
        with tempfile.TemporaryDirectory(prefix="layered-server-preflight-") as workspace:
            result = subprocess.run(
                [
                    "java",
                    "-cp",
                    str(self.classes),
                    "com.openrsc.layeredmaps.LayeredMapsCli",
                    "preflight",
                    "--root",
                    str(ROOT),
                    "--workspace",
                    workspace,
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(0, result.returncode, result.stderr)
            report = json.loads((Path(workspace) / "preflight.json").read_text(encoding="utf-8"))
            contracts = [
                source
                for source in report["candidateSources"]
                if source["role"] == "server-layered-coordinate-contract"
            ]
            self.assertEqual(
                [
                    "server/src/com/openrsc/server/model/world/coordinate/LegacyPackedPointAdapter.java",
                    "server/src/com/openrsc/server/model/world/coordinate/LegacyPlayerLocationPersistenceSnapshot.java",
                    "server/src/com/openrsc/server/model/world/coordinate/WorldRegionKey.java",
                    "server/src/com/openrsc/server/model/world/coordinate/WorldRegionWindow.java",
                ],
                sorted(contract["path"] for contract in contracts),
            )


if __name__ == "__main__":
    unittest.main()
