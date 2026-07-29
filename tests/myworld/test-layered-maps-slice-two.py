#!/usr/bin/env python3
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TOOL_ROOT = ROOT / "tools" / "layered-maps"
SOURCE_ROOT = TOOL_ROOT / "src"
MAIN_CLASS = "com.openrsc.layeredmaps.LayeredMapsCli"
TERRAIN_ENTRY_BYTES = 48 * 48 * 10


class LayeredMapsSliceTwoTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="layered-maps-slice-two-classes-")
        cls.classes = Path(cls.compile_temp.name)
        sources = sorted(str(path) for path in SOURCE_ROOT.rglob("*.java"))
        subprocess.run(
            [
                "javac",
                "-source",
                "8",
                "-target",
                "8",
                "-encoding",
                "UTF-8",
                "-d",
                str(cls.classes),
                *sources,
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def run_normalize(self, root, workspace):
        return subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                MAIN_CLASS,
                "normalize",
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

        (root / "server/myworld.conf").write_text(
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
        LayeredMapsSliceTwoTest.write_archive(server_terrain)
        shutil.copyfile(server_terrain, client_terrain)

        locs = root / "server/conf/server/defs/locs"
        locs.mkdir(parents=True)
        documents = {
            "BoundaryLocs.json": {
                "boundaries": [{"id": 1, "pos": {"X": 10, "Y": 20}, "direction": 0}]
            },
            "GroundItems.json": {
                "grounditems": [
                    {"id": 2, "pos": {"X": 11, "Y": 964}, "amount": 1, "respawn": 30}
                ]
            },
            "NpcLocs.json": {
                "npclocs": [
                    {
                        "id": 67,
                        "start": {"X": 647, "Y": 3534},
                        "min": {"X": 632, "Y": 3519},
                        "max": {"X": 662, "Y": 6549},
                    }
                ]
            },
            "MyWorldNpcRemovals.json": {
                "npc_removals": [
                    {
                        "id": 3,
                        "start": {"X": 30, "Y": 1888},
                        "min": {"X": 29, "Y": 1888},
                        "max": {"X": 31, "Y": 1889},
                    }
                ]
            },
            "SceneryLocsExpansion.json": {
                "sceneries": [
                    {"id": 4, "pos": {"X": 40, "Y": 2832}, "direction": 2, "type": 7}
                ]
            },
            "MyWorldSceneryRemovals.json": {
                "scenery_removals": [{"pos": {"X": 41, "Y": 100}}]
            },
        }
        for name, document in documents.items():
            (locs / name).write_text(
                json.dumps(document, sort_keys=True, separators=(",", ":")) + "\n",
                encoding="utf-8",
            )

        transitions = root / "server/conf/server/defs/extras/ObjectTelePoints.xml"
        transitions.parent.mkdir(parents=True)
        transitions.write_text(
            """<map>
  <entry>
    <Point><x>100</x><y>400</y></Point>
    <TelePoint><command>Climb-Down</command><x>100</x><y>3232</y></TelePoint>
  </entry>
  <entry>
    <Point><x>50</x><y>1000</y></Point>
    <TelePoint><command>walk through</command><x>55</x><y>1005</y></TelePoint>
  </entry>
</map>
""",
            encoding="utf-8",
        )

        server_source = root / "server/plugins/example/Travel.java"
        server_source.parent.mkdir(parents=True)
        server_source.write_text(
            "class Travel { void go() { teleport(100, 400); } }\n", encoding="utf-8"
        )
        client_source = root / "Client_Base/src/example/Plane.java"
        client_source.parent.mkdir(parents=True)
        client_source.write_text(
            "class Plane { static final int FLOOR_OFFSET = 944; }\n", encoding="utf-8"
        )
        return root

    @staticmethod
    def write_archive(path):
        with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for plane in range(4):
                raw = bytes((17 + plane + index * 13) & 0xFF for index in range(TERRAIN_ENTRY_BYTES))
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

    def test_normalization_is_deterministic_read_only_and_lossless(self):
        with tempfile.TemporaryDirectory(prefix="layered-maps-normalize-") as temp:
            root = self.fixture(temp)
            first_workspace = Path(temp) / "first-report"
            second_workspace = Path(temp) / "second-report"
            before = self.snapshot(root)

            first = self.run_normalize(root, first_workspace)
            second = self.run_normalize(root, second_workspace)

            self.assertEqual(0, first.returncode, first.stderr)
            self.assertEqual(0, second.returncode, second.stderr)
            self.assertEqual(before, self.snapshot(root))
            for name in ("world-inventory.json", "normalization-summary.json", "normalization.md"):
                self.assertEqual(
                    (first_workspace / name).read_bytes(),
                    (second_workspace / name).read_bytes(),
                    name,
                )

            inventory = json.loads(
                (first_workspace / "world-inventory.json").read_text(encoding="utf-8")
            )
            summary = inventory["summary"]
            self.assertEqual(4, summary["terrainSectorCount"])
            self.assertEqual(6, summary["placementSourceCount"])
            self.assertEqual(6, summary["placementRecordCount"])
            self.assertEqual(2, summary["transitionEdgeCount"])
            self.assertEqual(14, summary["coordinateCount"])
            self.assertEqual(13, summary["normalizedCoordinateCount"])
            self.assertEqual(1, summary["unresolvedCoordinateCount"])
            self.assertEqual(2, summary["unresolvedSourceOwnerCount"])
            self.assertTrue(summary["roundTripVerified"])
            self.assertTrue(
                all(owner["status"] == "unresolved" for owner in inventory["unresolvedSourceOwners"])
            )
            self.assertEqual({"-1", "0", "1", "2"}, set(inventory["terrain"]["sectorCountByLevel"]))

            npc_source = next(
                source
                for source in inventory["placementSources"]
                if source["path"].endswith("NpcLocs.json")
            )
            npc = npc_source["records"][0]
            self.assertEqual("partially-normalized", npc["status"])
            self.assertEqual(-1, npc["locations"]["start"]["coordinate"]["level"])
            self.assertEqual(6549, npc["unresolvedLocations"]["max"]["legacyCoordinate"]["Y"])
            self.assertTrue(npc["roundTripVerified"])

            edge = inventory["transitionGraph"]["edges"][0]
            self.assertEqual(-1, edge["levelDelta"])
            self.assertTrue(edge["sameGeographicAnchor"])
            self.assertEqual(0, edge["source"]["coordinate"]["level"])
            self.assertEqual(-1, edge["destination"]["coordinate"]["level"])
            self.assertTrue(edge["roundTripVerified"])
            self.assertEqual("legacy-coordinate-out-of-range", inventory["findings"][0]["code"])

    def test_unknown_structured_layout_is_refused_before_output(self):
        with tempfile.TemporaryDirectory(prefix="layered-maps-unknown-structure-") as temp:
            root = self.fixture(temp)
            unknown = root / "server/conf/server/defs/locs/UnknownLocs.json"
            unknown.write_text('{"mysteries":[]}\n', encoding="utf-8")
            workspace = Path(temp) / "refused-report"
            before = self.snapshot(root)

            result = self.run_normalize(root, workspace)

            self.assertEqual(3, result.returncode, result.stderr)
            self.assertIn("Unsupported placement root", result.stderr)
            self.assertEqual(before, self.snapshot(root))
            self.assertFalse(workspace.exists())

        with tempfile.TemporaryDirectory(prefix="layered-maps-malformed-record-") as temp:
            root = self.fixture(temp)
            malformed = root / "server/conf/server/defs/locs/BoundaryLocs.json"
            malformed.write_text(
                '{"boundaries":[{"id":1,"pos":{"X":10},"direction":0}]}\n',
                encoding="utf-8",
            )
            workspace = Path(temp) / "refused-report"
            before = self.snapshot(root)
            result = self.run_normalize(root, workspace)
            self.assertEqual(3, result.returncode, result.stderr)
            self.assertIn("exactly 32-bit integer X and Y", result.stderr)
            self.assertEqual(before, self.snapshot(root))
            self.assertFalse(workspace.exists())

    def test_unsafe_transition_xml_is_refused_before_output(self):
        with tempfile.TemporaryDirectory(prefix="layered-maps-unsafe-xml-") as temp:
            root = self.fixture(temp)
            transitions = root / "server/conf/server/defs/extras/ObjectTelePoints.xml"
            transitions.write_text(
                '<!DOCTYPE map [<!ENTITY xxe SYSTEM "file:///etc/passwd">]><map>&xxe;</map>\n',
                encoding="utf-8",
            )
            workspace = Path(temp) / "refused-report"
            before = self.snapshot(root)

            result = self.run_normalize(root, workspace)

            self.assertEqual(3, result.returncode, result.stderr)
            self.assertIn("malformed or unsafe", result.stderr)
            self.assertEqual(before, self.snapshot(root))
            self.assertFalse(workspace.exists())

    def test_inventory_schema_matches_fixture_output(self):
        schema_path = TOOL_ROOT / "schema/layered-world-inventory-v1.schema.json"
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
        summary_schema = json.loads(
            (TOOL_ROOT / "schema/normalization-summary-v1.schema.json").read_text(encoding="utf-8")
        )
        self.assertEqual("layered-world-inventory", schema["properties"]["manifestType"]["const"])
        with tempfile.TemporaryDirectory(prefix="layered-maps-schema-") as temp:
            root = self.fixture(temp)
            workspace = Path(temp) / "report"
            result = self.run_normalize(root, workspace)
            self.assertEqual(0, result.returncode, result.stderr)
            inventory = json.loads((workspace / "world-inventory.json").read_text(encoding="utf-8"))
            summary = json.loads(
                (workspace / "normalization-summary.json").read_text(encoding="utf-8")
            )
            try:
                import jsonschema
            except ImportError:
                jsonschema = None
            if jsonschema is not None:
                jsonschema.Draft202012Validator.check_schema(schema)
                jsonschema.validate(inventory, schema)
                jsonschema.Draft202012Validator.check_schema(summary_schema)
                jsonschema.validate(summary, summary_schema)

    def test_current_repository_inventory_records_known_anomaly_without_mutation(self):
        with tempfile.TemporaryDirectory(prefix="layered-maps-current-normalize-") as workspace:
            status_before = subprocess.run(
                ["git", "status", "--short"], cwd=ROOT, text=True, capture_output=True, check=True
            ).stdout
            protected = (
                ROOT / "server/myworld.conf",
                ROOT / "server/conf/server/data/Custom_Landscape.orsc",
                ROOT / "Client_Base/Cache/video/Custom_Landscape.orsc",
                ROOT / "server/conf/server/defs/locs/NpcLocs.json",
                ROOT / "server/conf/server/defs/extras/ObjectTelePoints.xml",
            )
            hashes_before = [hashlib.sha256(path.read_bytes()).hexdigest() for path in protected]

            result = self.run_normalize(ROOT, workspace)

            self.assertEqual(0, result.returncode, result.stderr)
            summary_document = json.loads(
                (Path(workspace) / "normalization-summary.json").read_text(encoding="utf-8")
            )
            summary = summary_document["summary"]
            self.assertEqual(1771, summary["terrainSectorCount"])
            self.assertEqual(40, summary["placementSourceCount"])
            self.assertEqual(49816, summary["placementRecordCount"])
            self.assertEqual(20, summary["transitionEdgeCount"])
            self.assertEqual(60680, summary["coordinateCount"])
            self.assertEqual(60679, summary["normalizedCoordinateCount"])
            self.assertEqual(1, summary["unresolvedCoordinateCount"])
            self.assertEqual(214, summary["unresolvedSourceOwnerCount"])
            self.assertEqual(6549, summary_document["findings"][0]["legacyY"])
            self.assertTrue((Path(workspace) / "world-inventory.json").stat().st_size > 1_000_000)
            self.assertEqual(hashes_before, [hashlib.sha256(path.read_bytes()).hexdigest() for path in protected])
            status_after = subprocess.run(
                ["git", "status", "--short"], cwd=ROOT, text=True, capture_output=True, check=True
            ).stdout
            self.assertEqual(status_before, status_after)


if __name__ == "__main__":
    unittest.main()
