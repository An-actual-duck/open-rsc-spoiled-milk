#!/usr/bin/env python3
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import unittest
import zipfile
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TOOL_ROOT = ROOT / "tools" / "layered-maps"
SOURCE_ROOT = TOOL_ROOT / "src"
MAIN_CLASS = "com.openrsc.layeredmaps.LayeredMapsCli"
TERRAIN_ENTRY_BYTES = 48 * 48 * 10


COORDINATE_FIXTURE = r"""
import com.openrsc.layeredmaps.LegacyPackedCoordinateCodec;
import com.openrsc.layeredmaps.WorldCoordinate;
import com.openrsc.layeredmaps.WorldLocation;
import com.openrsc.layeredmaps.WorldSpaceId;

public final class CoordinateFixture {
    public static void main(String[] args) {
        int[] xs = {0, 1, 1008, LegacyPackedCoordinateCodec.MAX_LEGACY_X};
        for (int packedY = LegacyPackedCoordinateCodec.MIN_PACKED_Y;
                packedY <= LegacyPackedCoordinateCodec.MAX_PACKED_Y; packedY++) {
            int plane = packedY / LegacyPackedCoordinateCodec.LEVEL_STRIDE;
            int expectedLevel = LegacyPackedCoordinateCodec.levelForLegacyPlane(plane);
            int expectedY = packedY % LegacyPackedCoordinateCodec.LEVEL_STRIDE;
            for (int x : xs) {
                WorldCoordinate decoded = LegacyPackedCoordinateCodec.decode(x, packedY);
                check(decoded.getX() == x, "decoded X");
                check(decoded.getY() == expectedY, "decoded Y");
                check(decoded.getLevel() == expectedLevel, "decoded level");
                LegacyPackedCoordinateCodec.PackedCoordinate encoded =
                    LegacyPackedCoordinateCodec.encode(decoded);
                check(encoded.getX() == x, "encoded X");
                check(encoded.getY() == packedY, "encoded Y");
            }
        }

        check(LegacyPackedCoordinateCodec.levelForLegacyPlane(0) == 0, "plane 0");
        check(LegacyPackedCoordinateCodec.levelForLegacyPlane(1) == 1, "plane 1");
        check(LegacyPackedCoordinateCodec.levelForLegacyPlane(2) == 2, "plane 2");
        check(LegacyPackedCoordinateCodec.levelForLegacyPlane(3) == -1, "plane 3");

        WorldCoordinate signed = new WorldCoordinate(-1, -49, -2);
        check(signed.getSectorX() == -1 && signed.getLocalX() == 47, "signed X sector");
        check(signed.getSectorY() == -2 && signed.getLocalY() == 47, "signed Y sector");
        check(signed.atLevel(3).equals(new WorldCoordinate(-1, -49, 3)), "atLevel");

        WorldLocation global = WorldLocation.global(new WorldCoordinate(100, 400, 0));
        WorldLocation privateSpace = new WorldLocation(
            new WorldSpaceId("instance.quest_1"), new WorldCoordinate(100, 400, 0));
        check(!global.equals(privateSpace), "world-space isolation");

        expectIllegal(() -> LegacyPackedCoordinateCodec.decode(-1, 0));
        expectIllegal(() -> LegacyPackedCoordinateCodec.decode(32768, 0));
        expectIllegal(() -> LegacyPackedCoordinateCodec.decode(0, -1));
        expectIllegal(() -> LegacyPackedCoordinateCodec.decode(0, 3776));
        expectIllegal(() -> LegacyPackedCoordinateCodec.encode(new WorldCoordinate(-1, 0, 0)));
        expectIllegal(() -> LegacyPackedCoordinateCodec.encode(new WorldCoordinate(0, -1, 0)));
        expectIllegal(() -> LegacyPackedCoordinateCodec.encode(new WorldCoordinate(0, 944, 0)));
        expectIllegal(() -> LegacyPackedCoordinateCodec.encode(new WorldCoordinate(0, 0, -2)));
        expectIllegal(() -> LegacyPackedCoordinateCodec.encode(new WorldCoordinate(0, 0, 3)));
        expectIllegal(() -> new WorldSpaceId("Global"));
        expectArithmetic(() -> new WorldCoordinate(Integer.MAX_VALUE, 0, 0).translate(1, 0, 0));
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

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
"""


class LayeredMapsSliceOneTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-classes-")
        cls.classes = Path(cls.compile_temp.name)
        sources = sorted(str(path) for path in SOURCE_ROOT.rglob("*.java"))
        subprocess.run(
            ["javac", "-source", "8", "-target", "8", "-d", str(cls.classes), *sources],
            cwd=ROOT,
            check=True,
        )
        fixture_source = cls.classes / "CoordinateFixture.java"
        fixture_source.write_text(COORDINATE_FIXTURE, encoding="utf-8")
        subprocess.run(
            [
                "javac",
                "-source",
                "8",
                "-target",
                "8",
                "-cp",
                str(cls.classes),
                "-d",
                str(cls.classes),
                str(fixture_source),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def run_preflight(self, root, workspace):
        return subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                MAIN_CLASS,
                "preflight",
                "--root",
                str(root),
                "--workspace",
                str(workspace),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )

    @staticmethod
    def fixture(base):
        root = Path(base) / "target-repository"
        for marker in ("server/build.xml", "Client_Base/build.xml"):
            path = root / marker
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("<project/>\n", encoding="utf-8")

        config = root / "server/myworld.conf"
        config.write_text(
            "\n".join(
                (
                    "client_version: 10046",
                    "based_map_data: 64",
                    "member_world: true",
                    "custom_landscape: true",
                    "want_myworld: true",
                    "",
                )
            ),
            encoding="utf-8",
        )

        server_terrain = root / "server/conf/server/data/Custom_Landscape.orsc"
        client_terrain = root / "Client_Base/Cache/video/Custom_Landscape.orsc"
        server_terrain.parent.mkdir(parents=True, exist_ok=True)
        client_terrain.parent.mkdir(parents=True, exist_ok=True)
        LayeredMapsSliceOneTest.write_archive(server_terrain, seed=17)
        shutil.copyfile(server_terrain, client_terrain)

        locs = root / "server/conf/server/defs/locs"
        locs.mkdir(parents=True)
        (locs / "SceneryLocs.json").write_text(
            '{"scenery":[{"id":1,"x":100,"y":400}]}\n', encoding="utf-8"
        )
        telepoints = root / "server/conf/server/defs/extras/ObjectTelePoints.xml"
        telepoints.parent.mkdir(parents=True)
        telepoints.write_text("<telepoints><point x=\"100\" y=\"400\"/></telepoints>\n", encoding="utf-8")

        server_source = root / "server/plugins/example/Ladder.java"
        server_source.parent.mkdir(parents=True)
        server_source.write_text("class Ladder { void go() { teleport(100, 400); } }\n", encoding="utf-8")
        client_source = root / "Client_Base/src/example/Plane.java"
        client_source.parent.mkdir(parents=True)
        client_source.write_text("class Plane { static final int FLOOR_OFFSET = 944; }\n", encoding="utf-8")
        return root

    @staticmethod
    def write_archive(path, seed):
        with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for plane in range(4):
                raw = bytes((seed + plane + index * 13) & 0xFF for index in range(TERRAIN_ENTRY_BYTES))
                info = zipfile.ZipInfo(f"h{plane}x{48 + plane}y{37 + plane}", (2024, 1, 2, 3, 4, 6))
                info.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(info, raw)

    @staticmethod
    def snapshot(root):
        state = {}
        for path in sorted(Path(root).rglob("*")):
            relative = path.relative_to(root).as_posix()
            if path.is_symlink():
                state[relative] = ("link", os.readlink(path))
            elif path.is_dir():
                state[relative] = ("dir",)
            else:
                stat = path.stat()
                state[relative] = (
                    "file",
                    stat.st_size,
                    stat.st_mtime_ns,
                    hashlib.sha256(path.read_bytes()).hexdigest(),
                )
        return state

    def test_reference_coordinate_model_and_exhaustive_legacy_round_trip(self):
        result = subprocess.run(
            ["java", "-cp", str(self.classes), "CoordinateFixture"],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_supported_preflight_is_read_only_deterministic_and_structured(self):
        with tempfile.TemporaryDirectory(prefix="layered-maps-fixture-") as temp:
            root = self.fixture(temp)
            first_workspace = Path(temp) / "report-one"
            second_workspace = Path(temp) / "report-two"
            before = self.snapshot(root)

            first = self.run_preflight(root, first_workspace)
            second = self.run_preflight(root, second_workspace)

            self.assertEqual(0, first.returncode, first.stderr)
            self.assertEqual(0, second.returncode, second.stderr)
            self.assertEqual(before, self.snapshot(root))
            self.assertEqual(
                (first_workspace / "preflight.json").read_bytes(),
                (second_workspace / "preflight.json").read_bytes(),
            )
            self.assertEqual(
                (first_workspace / "preflight.md").read_bytes(),
                (second_workspace / "preflight.md").read_bytes(),
            )

            report = json.loads((first_workspace / "preflight.json").read_text(encoding="utf-8"))
            self.assertEqual(1, report["schemaVersion"])
            self.assertEqual("layered-maps-preflight", report["reportType"])
            self.assertEqual("signed-layered-v1", report["coordinateModel"])
            self.assertEqual("legacy-packed-y-v1", report["legacyCodec"])
            self.assertEqual("spoiled-milk-repository-v1", report["layoutAdapter"])
            self.assertRegex(report["sourceFingerprintSha256"], r"^[0-9a-f]{64}$")
            self.assertEqual(4, report["terrain"]["sectorCount"])
            self.assertEqual({"0", "1", "2", "3"}, set(report["terrain"]["planes"]))
            self.assertEqual(report["candidateSourceCount"], len(report["candidateSources"]))
            self.assertGreaterEqual(report["candidateSourceCount"], 5)
            self.assertTrue(
                any(source["role"] == "transition" for source in report["candidateSources"])
            )
            self.assertTrue(
                any("teleport-call" in source["signals"] for source in report["candidateSources"])
            )
            self.assertIn("Candidates require later parsing", (first_workspace / "preflight.md").read_text())

    def test_inconsistent_and_unknown_targets_are_refused_without_writes(self):
        with tempfile.TemporaryDirectory(prefix="layered-maps-refusal-") as temp:
            root = self.fixture(temp)
            client_terrain = root / "Client_Base/Cache/video/Custom_Landscape.orsc"
            self.write_archive(client_terrain, seed=99)
            workspace = Path(temp) / "refused-report"
            before = self.snapshot(root)

            result = self.run_preflight(root, workspace)

            self.assertEqual(3, result.returncode, result.stderr)
            self.assertIn("not byte-identical", result.stderr)
            self.assertEqual(before, self.snapshot(root))
            self.assertFalse(workspace.exists())

            unknown = Path(temp) / "unknown-target"
            unknown.mkdir()
            unknown_workspace = Path(temp) / "unknown-report"
            unknown_before = self.snapshot(unknown)
            result = self.run_preflight(unknown, unknown_workspace)
            self.assertEqual(3, result.returncode, result.stderr)
            self.assertIn("Required repository file is missing", result.stderr)
            self.assertEqual(unknown_before, self.snapshot(unknown))
            self.assertFalse(unknown_workspace.exists())

    def test_schemas_are_valid_json_and_match_the_emitted_contract(self):
        coordinate_schema = json.loads(
            (TOOL_ROOT / "schema/signed-layered-v1.schema.json").read_text(encoding="utf-8")
        )
        report_schema = json.loads(
            (TOOL_ROOT / "schema/preflight-report-v1.schema.json").read_text(encoding="utf-8")
        )
        self.assertEqual(["worldSpace", "coordinate"], coordinate_schema["required"])
        self.assertIn("sourceFingerprintSha256", report_schema["required"])
        self.assertEqual(1, report_schema["properties"]["schemaVersion"]["const"])
        self.assertEqual("project", ET.parse(TOOL_ROOT / "build.xml").getroot().tag)
        self.assertTrue(os.access(TOOL_ROOT / "layered-maps.sh", os.X_OK))

    def test_current_repository_matches_adapter_without_git_or_input_changes(self):
        with tempfile.TemporaryDirectory(prefix="layered-maps-current-report-") as workspace:
            status_before = subprocess.run(
                ["git", "status", "--short"], cwd=ROOT, text=True, capture_output=True, check=True
            ).stdout
            protected = (
                ROOT / "server/myworld.conf",
                ROOT / "server/conf/server/data/Custom_Landscape.orsc",
                ROOT / "Client_Base/Cache/video/Custom_Landscape.orsc",
            )
            hashes_before = [hashlib.sha256(path.read_bytes()).hexdigest() for path in protected]

            result = self.run_preflight(ROOT, workspace)

            self.assertEqual(0, result.returncode, result.stderr)
            report = json.loads((Path(workspace) / "preflight.json").read_text(encoding="utf-8"))
            self.assertGreater(report["terrain"]["sectorCount"], 1000)
            self.assertGreater(report["candidateSourceCount"], 100)
            self.assertEqual(hashes_before, [hashlib.sha256(path.read_bytes()).hexdigest() for path in protected])
            status_after = subprocess.run(
                ["git", "status", "--short"], cwd=ROOT, text=True, capture_output=True, check=True
            ).stdout
            self.assertEqual(status_before, status_after)


if __name__ == "__main__":
    unittest.main()
