#!/usr/bin/env python3
import hashlib
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_PACKAGE = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
ENTITY_HANDLER_SOURCE = ROOT / "server/src/com/openrsc/server/external/EntityHandler.java"
TELEPOINTS_SOURCE = ROOT / "server/conf/server/defs/extras/ObjectTelePoints.xml"
TELEPOINTS_SHA256 = "957b32b927860170905460b9d2a5f6377256ce493503b62b738175fdda68f4ed"


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


TRANSITION_FIXTURE = r"""
import com.openrsc.server.model.Point;
import com.openrsc.server.model.world.coordinate.LegacyPackedPointAdapter;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import com.openrsc.server.model.world.coordinate.WorldObjectTransition;

public final class ServerLayeredTransitionFixture {
    public static void main(String[] args) {
        for (int packedY = LegacyPackedPointAdapter.MIN_PACKED_Y;
                packedY <= LegacyPackedPointAdapter.MAX_PACKED_Y; packedY++) {
            Point source = Point.location(100, packedY);
            Point destination = Point.location(200,
                LegacyPackedPointAdapter.MAX_PACKED_Y - packedY);
            String command = "command-" + (packedY % 7);
            WorldObjectTransition transition = WorldObjectTransition.fromLegacyPoints(
                source, destination, command);

            check(transition.getSource().equals(
                LegacyPackedPointAdapter.fromLegacyPoint(source)), "source parity");
            check(transition.getDestination().equals(
                LegacyPackedPointAdapter.fromLegacyPoint(destination)), "destination parity");
            check(transition.getCommand().equals(command), "command preservation");
        }

        WorldObjectTransition crossSpace = new WorldObjectTransition(
            global(100, 400, 0),
            location("instance.quest_1", 100, 400, -2),
            "Enter");
        check(crossSpace.getSource().getWorldSpace().equals(WorldSpaceId.GLOBAL),
            "global source");
        check(crossSpace.getDestination().getCoordinate().getLevel() == -2,
            "deep destination");
        check(crossSpace.equals(new WorldObjectTransition(
            global(100, 400, 0),
            location("instance.quest_1", 100, 400, -2),
            "Enter")), "transition equality");
        check(crossSpace.hashCode() == new WorldObjectTransition(
            global(100, 400, 0),
            location("instance.quest_1", 100, 400, -2),
            "Enter").hashCode(), "transition hash");
        check(!crossSpace.equals(new WorldObjectTransition(
            location("instance.quest_1", 100, 400, -2),
            global(100, 400, 0),
            "Enter")), "directed identity");
        check(!crossSpace.equals(new WorldObjectTransition(
            global(100, 400, 0),
            location("instance.quest_1", 100, 400, -2),
            "enter")), "stored command identity");
        check(crossSpace.toString().contains("command='Enter'"),
            "transition description");

        expectNull(() -> new WorldObjectTransition(
            null, global(0, 0, 0), "command"));
        expectNull(() -> new WorldObjectTransition(
            global(0, 0, 0), null, "command"));
        expectNull(() -> new WorldObjectTransition(
            global(0, 0, 0), global(1, 1, 0), null));
        expectNull(() -> WorldObjectTransition.fromLegacyPoints(
            null, Point.location(1, 1), "command"));
        expectNull(() -> WorldObjectTransition.fromLegacyPoints(
            Point.location(1, 1), null, "command"));
    }

    private static WorldLocation global(int x, int y, int level) {
        return WorldLocation.global(new WorldCoordinate(x, y, level));
    }

    private static WorldLocation location(String space, int x, int y, int level) {
        return new WorldLocation(
            new WorldSpaceId(space), new WorldCoordinate(x, y, level));
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


class LayeredMapsSliceSixTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-six-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point_source = cls.temp / "src/com/openrsc/server/model/Point.java"
        point_source.parent.mkdir(parents=True)
        point_source.write_text(POINT_STUB, encoding="utf-8")
        fixture_source = cls.temp / "src/ServerLayeredTransitionFixture.java"
        fixture_source.write_text(TRANSITION_FIXTURE, encoding="utf-8")

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
                *(str(path) for path in sorted(SERVER_PACKAGE.glob("*.java"))),
                str(fixture_source),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_directed_transition_parity_and_isolation(self):
        result = subprocess.run(
            ["java", "-cp", str(self.classes), "ServerLayeredTransitionFixture"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_current_telepoint_xml_is_unchanged_and_fully_representable(self):
        raw = TELEPOINTS_SOURCE.read_bytes()
        self.assertEqual(TELEPOINTS_SHA256, hashlib.sha256(raw).hexdigest())
        root = ET.fromstring(raw)
        entries = root.findall("entry")
        self.assertEqual(20, len(entries))
        directed_edges = set()
        for entry in entries:
            source = entry.find("Point")
            destination = entry.find("TelePoint")
            self.assertIsNotNone(source)
            self.assertIsNotNone(destination)
            source_x = int(source.findtext("x"))
            source_y = int(source.findtext("y"))
            destination_x = int(destination.findtext("x"))
            destination_y = int(destination.findtext("y"))
            command = destination.findtext("command")
            self.assertGreaterEqual(source_x, 0)
            self.assertLessEqual(source_x, 32767)
            self.assertGreaterEqual(destination_x, 0)
            self.assertLessEqual(destination_x, 32767)
            self.assertGreaterEqual(source_y, 0)
            self.assertLessEqual(source_y, 3775)
            self.assertGreaterEqual(destination_y, 0)
            self.assertLessEqual(destination_y, 3775)
            self.assertTrue(command)
            directed_edges.add(
                (source_x, source_y, destination_x, destination_y, command)
            )
        self.assertEqual(20, len(directed_edges))

    def test_entity_handler_projection_preserves_authoritative_path(self):
        source = ENTITY_HANDLER_SOURCE.read_text(encoding="utf-8")
        self.assertIn("private HashMap<Point, TelePoint> objectTelePoints;", source)
        self.assertIn(
            'load(getPath("defs/extras/ObjectTelePoints.xml"))',
            source,
        )
        self.assertIn("public TelePoint getObjectTelePoint(Point location, String command)", source)
        self.assertIn(
            "public WorldObjectTransition getObjectWorldTransition(Point location, String command)",
            source,
        )
        self.assertIn("TelePoint destination = getObjectTelePoint(location, command);", source)
        self.assertIn(
            "location, destination, destination.getCommand());",
            source,
        )

        new_api_callers = []
        for source_root in (ROOT / "server/src", ROOT / "server/plugins"):
            for path in source_root.rglob("*.java"):
                if path == ENTITY_HANDLER_SOURCE:
                    continue
                if ".getObjectWorldTransition(" in path.read_text(encoding="utf-8"):
                    new_api_callers.append(path.relative_to(ROOT).as_posix())
        self.assertEqual([], new_api_callers)

    def test_only_approved_staged_sources_consume_the_contract(self):
        package_name = "com.openrsc.server.model.world.coordinate"
        consumers = []
        for source_root in (ROOT / "server/src", ROOT / "server/plugins"):
            for path in source_root.rglob("*.java"):
                if SERVER_PACKAGE in path.parents:
                    continue
                if package_name in path.read_text(encoding="utf-8"):
                    consumers.append(path.relative_to(ROOT).as_posix())
        consumers.sort()
        self.assertEqual(
            [
                "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java",
                "server/src/com/openrsc/server/database/GameDatabase.java",
                "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java",
                "server/src/com/openrsc/server/external/EntityHandler.java",
                "server/src/com/openrsc/server/external/GameObjectLoc.java",
                "server/src/com/openrsc/server/external/ItemLoc.java",
                "server/src/com/openrsc/server/external/NPCLoc.java",
                "server/src/com/openrsc/server/io/WorldEditorTerrainArchive.java",
                "server/src/com/openrsc/server/model/entity/player/Player.java",
                "server/src/com/openrsc/server/model/world/Area.java",
                "server/src/com/openrsc/server/model/world/region/LayeredAdjacentStepCollisionComparison.java",
                "server/src/com/openrsc/server/model/world/region/LayeredRegionTileSnapshot.java",
                "server/src/com/openrsc/server/model/world/region/LayeredTileNeighborhoodParityComparison.java",
                "server/src/com/openrsc/server/model/world/region/LayeredTileStateParityComparison.java",
                "server/src/com/openrsc/server/model/world/region/LayeredTraversalCollisionComparison.java",
                "server/src/com/openrsc/server/model/world/region/RegionManager.java",
                "server/src/com/openrsc/server/service/PlayerService.java",
            ],
            consumers,
        )


if __name__ == "__main__":
    unittest.main()
