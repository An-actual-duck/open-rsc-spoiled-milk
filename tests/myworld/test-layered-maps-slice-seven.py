#!/usr/bin/env python3
import hashlib
import json
import re
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TOOL_PACKAGE = ROOT / "tools/layered-maps/src/com/openrsc/layeredmaps"
SERVER_PACKAGE = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
TERRAIN_ARCHIVE_SOURCE = ROOT / "server/src/com/openrsc/server/io/WorldEditorTerrainArchive.java"
SERVER_TERRAIN = ROOT / "server/conf/server/data/Custom_Landscape.orsc"
CLIENT_TERRAIN = ROOT / "Client_Base/Cache/video/Custom_Landscape.orsc"
TERRAIN_SHA256 = "c48f9734f8faf027b9128c28dfcece468d3e84a5c1ed4b9a4452c2481392b6ee"
ENTRY = re.compile(r"h([0-3])x([0-9]+)y([0-9]+)")


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


SECTOR_FIXTURE = r"""
import java.io.File;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class LayeredMapSectorFixture {
    public static void main(String[] args) throws Exception {
        check(com.openrsc.layeredmaps.LegacyTerrainSectorCodec.ID.equals(
            com.openrsc.server.model.world.coordinate.LegacyTerrainSectorAdapter.ID),
            "codec ID parity");
        check(com.openrsc.layeredmaps.LegacyTerrainSectorCodec.ARCHIVE_SECTOR_X_OFFSET == 48,
            "tool X offset");
        check(com.openrsc.layeredmaps.LegacyTerrainSectorCodec.ARCHIVE_SECTOR_Y_OFFSET == 37,
            "tool Y offset");
        check(com.openrsc.server.model.world.coordinate.LegacyTerrainSectorAdapter
            .ARCHIVE_SECTOR_X_OFFSET == 48, "server X offset");
        check(com.openrsc.server.model.world.coordinate.LegacyTerrainSectorAdapter
            .ARCHIVE_SECTOR_Y_OFFSET == 37, "server Y offset");

        for (int plane = 0; plane < 4; plane++) {
            for (int archiveX = 0; archiveX <= 96; archiveX++) {
                for (int archiveY = 0; archiveY <= 80; archiveY++) {
                    String entry = "h" + plane + "x" + archiveX + "y" + archiveY;
                    compareEntry(entry);
                }
            }
        }

        try (ZipFile archive = new ZipFile(new File(args[0]))) {
            int count = 0;
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                compareEntry(entries.nextElement().getName());
                count++;
            }
            check(count == 1771, "current archive entry count");
        }

        com.openrsc.server.model.world.coordinate.WorldMapSectorId origin =
            com.openrsc.server.model.world.coordinate.LegacyTerrainSectorAdapter.decode(
                "h0x48y37");
        check(origin.getLevel() == 0 && origin.getSectorX() == 0
            && origin.getSectorY() == 0, "offset origin");
        com.openrsc.server.model.world.coordinate.WorldMapSectorId negative =
            com.openrsc.server.model.world.coordinate.LegacyTerrainSectorAdapter.decode(
                "h3x47y36");
        check(negative.getLevel() == -1 && negative.getSectorX() == -1
            && negative.getSectorY() == -1, "signed legacy sector");

        com.openrsc.server.model.world.coordinate.WorldMapSectorId instance =
            com.openrsc.server.model.world.coordinate.WorldMapSectorId.from(
                serverLocation("instance.quest_1", -1, -49, -2));
        check(instance.getSectorX() == -1 && instance.getSectorY() == -2
            && instance.getLevel() == -2, "signed instance sector");
        check(instance.equals(new com.openrsc.server.model.world.coordinate.WorldMapSectorId(
            new com.openrsc.server.model.world.coordinate.WorldSpaceId("instance.quest_1"),
            -2, -1, -2)), "sector equality");
        check(instance.hashCode() == new com.openrsc.server.model.world.coordinate.WorldMapSectorId(
            new com.openrsc.server.model.world.coordinate.WorldSpaceId("instance.quest_1"),
            -2, -1, -2).hashCode(), "sector hash");

        expectIllegal(() -> com.openrsc.server.model.world.coordinate
            .LegacyTerrainSectorAdapter.decode("h4x48y37"));
        expectIllegal(() -> com.openrsc.server.model.world.coordinate
            .LegacyTerrainSectorAdapter.decode("h0x-1y37"));
        expectIllegal(() -> com.openrsc.server.model.world.coordinate
            .LegacyTerrainSectorAdapter.decode("h0x999999999999999999999y37"));
        expectIllegal(() -> com.openrsc.server.model.world.coordinate
            .LegacyTerrainSectorAdapter.encode(serverSector("global", -2, 0, 0)));
        expectIllegal(() -> com.openrsc.server.model.world.coordinate
            .LegacyTerrainSectorAdapter.encode(serverSector("instance.quest_1", 0, 0, 0)));
        expectIllegal(() -> com.openrsc.server.model.world.coordinate
            .LegacyTerrainSectorAdapter.encode(serverSector("global", 0, -49, 0)));
        expectIllegal(() -> com.openrsc.server.model.world.coordinate
            .LegacyTerrainSectorAdapter.encode(serverSector("global", 0, 0, -38)));
        expectArithmetic(() -> com.openrsc.server.model.world.coordinate
            .LegacyTerrainSectorAdapter.encode(
                serverSector("global", 0, Integer.MAX_VALUE, 0)));
        expectNull(() -> com.openrsc.server.model.world.coordinate
            .LegacyTerrainSectorAdapter.decode(null));
        expectNull(() -> com.openrsc.server.model.world.coordinate
            .LegacyTerrainSectorAdapter.encode(null));
    }

    private static void compareEntry(String entry) {
        com.openrsc.layeredmaps.WorldMapSectorId tool =
            com.openrsc.layeredmaps.LegacyTerrainSectorCodec.decode(entry);
        com.openrsc.server.model.world.coordinate.WorldMapSectorId server =
            com.openrsc.server.model.world.coordinate.LegacyTerrainSectorAdapter.decode(entry);
        check(tool.getWorldSpace().getValue().equals(server.getWorldSpace().getValue()),
            "world-space parity");
        check(tool.getLevel() == server.getLevel(), "level parity");
        check(tool.getSectorX() == server.getSectorX(), "sector X parity");
        check(tool.getSectorY() == server.getSectorY(), "sector Y parity");
        check(com.openrsc.layeredmaps.LegacyTerrainSectorCodec.encode(tool).equals(entry),
            "tool round trip");
        check(com.openrsc.server.model.world.coordinate.LegacyTerrainSectorAdapter
            .encode(server).equals(entry), "server round trip");
    }

    private static com.openrsc.server.model.world.coordinate.WorldLocation serverLocation(
            String space, int x, int y, int level) {
        return new com.openrsc.server.model.world.coordinate.WorldLocation(
            new com.openrsc.server.model.world.coordinate.WorldSpaceId(space),
            new com.openrsc.server.model.world.coordinate.WorldCoordinate(x, y, level));
    }

    private static com.openrsc.server.model.world.coordinate.WorldMapSectorId serverSector(
            String space, int level, int sectorX, int sectorY) {
        return new com.openrsc.server.model.world.coordinate.WorldMapSectorId(
            new com.openrsc.server.model.world.coordinate.WorldSpaceId(space),
            level, sectorX, sectorY);
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


class LayeredMapsSliceSevenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-seven-")
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()

        point_source = cls.temp / "src/com/openrsc/server/model/Point.java"
        point_source.parent.mkdir(parents=True)
        point_source.write_text(POINT_STUB, encoding="utf-8")
        fixture_source = cls.temp / "src/LayeredMapSectorFixture.java"
        fixture_source.write_text(SECTOR_FIXTURE, encoding="utf-8")

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
                *(str(path) for path in sorted(TOOL_PACKAGE.glob("*.java"))),
                *(str(path) for path in sorted(SERVER_PACKAGE.glob("*.java"))),
                str(fixture_source),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_tool_and_server_sector_codecs_match_archive_wide(self):
        result = subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                "LayeredMapSectorFixture",
                str(SERVER_TERRAIN),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_normalization_separates_legacy_and_logical_sector_indices(self):
        with tempfile.TemporaryDirectory(prefix="layered-sector-normalize-") as workspace:
            result = subprocess.run(
                [
                    "java",
                    "-cp",
                    str(self.classes),
                    "com.openrsc.layeredmaps.LayeredMapsCli",
                    "normalize",
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
            inventory = json.loads(
                (Path(workspace) / "world-inventory.json").read_text(encoding="utf-8")
            )
            sectors = inventory["terrain"]["sectors"]
            self.assertEqual(1771, len(sectors))
            ranges = {}
            for sector in sectors:
                match = ENTRY.fullmatch(sector["legacyEntry"])
                self.assertIsNotNone(match)
                self.assertEqual(int(match.group(1)), sector["legacyPlane"])
                self.assertEqual(int(match.group(2)), sector["legacySectorX"])
                self.assertEqual(int(match.group(3)), sector["legacySectorY"])
                self.assertEqual(sector["legacySectorX"] - 48, sector["sectorX"])
                self.assertEqual(sector["legacySectorY"] - 37, sector["sectorY"])
                values = ranges.setdefault(
                    sector["level"],
                    [sector["sectorX"], sector["sectorX"], sector["sectorY"], sector["sectorY"]],
                )
                values[0] = min(values[0], sector["sectorX"])
                values[1] = max(values[1], sector["sectorX"])
                values[2] = min(values[2], sector["sectorY"])
                values[3] = max(values[3], sector["sectorY"])
            self.assertEqual(
                {
                    -1: [0, 20, 0, 20],
                    0: [0, 21, -1, 20],
                    1: [-1, 20, -1, 20],
                    2: [0, 20, 0, 20],
                },
                ranges,
            )

    def test_archives_and_authoritative_access_path_are_unchanged(self):
        self.assertEqual(TERRAIN_SHA256, hashlib.sha256(SERVER_TERRAIN.read_bytes()).hexdigest())
        self.assertEqual(TERRAIN_SHA256, hashlib.sha256(CLIENT_TERRAIN.read_bytes()).hexdigest())
        source = TERRAIN_ARCHIVE_SOURCE.read_text(encoding="utf-8")
        self.assertIn('String name = "h" + plane + "x" + c.sectorX + "y" + c.sectorY;', source)
        self.assertIn("public WorldMapSectorId toWorldMapSectorId()", source)
        self.assertIn(
            "return LegacyTerrainSectorAdapter.fromLegacySector(plane, sectorX, sectorY);",
            source,
        )
        callers = []
        for source_root in (ROOT / "server/src", ROOT / "server/plugins"):
            for path in source_root.rglob("*.java"):
                if path == TERRAIN_ARCHIVE_SOURCE:
                    continue
                if ".toWorldMapSectorId(" in path.read_text(encoding="utf-8"):
                    callers.append(path.relative_to(ROOT).as_posix())
        self.assertEqual([], callers)


if __name__ == "__main__":
    unittest.main()
