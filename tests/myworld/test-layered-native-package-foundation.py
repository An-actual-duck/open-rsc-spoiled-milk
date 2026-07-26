#!/usr/bin/env python3
import json
import hashlib
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
BASELINE = TOOL_ROOT / "baselines/rsc-remastered-preservation-r64-v1.json"
TRANSITION_LOCK = (
    TOOL_ROOT / "baselines/preservation-transition-compatibility-v1.json"
)
PACKAGE = TOOL_ROOT / "fixtures/native-package-v1"


class LayeredNativePackageFoundationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-native-package-classes-"
        )
        cls.classes = Path(cls.compile_temp.name)
        sources = sorted(str(path) for path in SOURCE_ROOT.rglob("*.java"))
        subprocess.run(
            [
                "javac",
                "-source",
                "8",
                "-target",
                "8",
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

    def run_command(self, command, workspace, package=None):
        arguments = [
            "java",
            "-cp",
            str(self.classes),
            MAIN_CLASS,
            command,
            "--root",
            str(ROOT),
            "--workspace",
            str(workspace),
        ]
        if package is not None:
            arguments.extend(["--package", str(package)])
        return subprocess.run(
            arguments,
            cwd=ROOT,
            text=True,
            capture_output=True,
        )

    def test_preservation_baseline_regenerates_exact_frozen_manifest(self):
        with tempfile.TemporaryDirectory(prefix="preservation-baseline-") as temp:
            workspace = Path(temp) / "report"
            result = self.run_command("baseline", workspace)

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(
                BASELINE.read_bytes(),
                (workspace / "preservation-baseline.json").read_bytes(),
            )
            report = json.loads(BASELINE.read_text(encoding="utf-8"))
            self.assertEqual("rsc-remastered-preservation-r64-v1", report["baselineId"])
            self.assertEqual(12, len(report["files"]))
            self.assertRegex(report["sourceSetFingerprintSha256"], r"^[0-9a-f]{64}$")
            selectors = report["configuration"]["selectors"]
            self.assertEqual(64, selectors["basedMapData"])
            self.assertTrue(selectors["memberWorld"])
            self.assertFalse(selectors["customLandscape"])
            self.assertFalse(selectors["wantMyWorld"])
            terrain = {
                item["role"]: item
                for item in report["files"]
                if item["role"] in {
                    "server-authentic-terrain",
                    "client-authentic-terrain",
                }
            }
            self.assertEqual(
                terrain["server-authentic-terrain"]["sha256"],
                terrain["client-authentic-terrain"]["sha256"],
            )
            self.assertEqual(
                1764, terrain["server-authentic-terrain"]["archiveEntryCount"]
            )

    def test_preservation_transition_compatibility_is_pinned_not_guessed(self):
        with tempfile.TemporaryDirectory(
            prefix="preservation-transitions-"
        ) as temp:
            workspace = Path(temp) / "report"
            result = self.run_command("preservation-transitions", workspace)

            self.assertEqual(0, result.returncode, result.stderr)
            report = json.loads(
                (workspace / "transition-compatibility.json").read_text(
                    encoding="utf-8"
                )
            )
            lock = json.loads(TRANSITION_LOCK.read_text(encoding="utf-8"))
            self.assertEqual(
                lock["inventoryFingerprintSha256"],
                report["inventoryFingerprintSha256"],
            )
            self.assertEqual(
                lock["explicitTransitionSource"]["sha256"],
                report["explicitTransitionGraph"]["sourceSha256"],
            )
            self.assertEqual(
                20, report["explicitTransitionGraph"]["edgeCount"]
            )
            self.assertEqual(
                20, report["explicitTransitionGraph"]["normalizedEdgeCount"]
            )
            self.assertEqual(
                0, report["explicitTransitionGraph"]["unresolvedEdgeCount"]
            )
            self.assertFalse(
                report["declarativeCoverage"]["completeDeclarativeGraph"]
            )
            self.assertEqual(
                "not-yet-declarative",
                report["declarativeCoverage"]["scriptedSemanticStatus"],
            )
            self.assertEqual(
                "compatibility-runtime-preserved",
                report["scriptedSources"]["runtimeTreatment"],
            )
            self.assertEqual(
                lock["scriptedSourceSet"]["sourceFileCount"],
                len(report["scriptedSources"]["files"]),
            )
            self.assertTrue(
                report["policy"]["longDistanceTransitionsRemainValid"]
            )
            self.assertFalse(
                report["policy"]["scriptBehaviorMayBeSilentlyRewritten"]
            )

    def test_native_fixture_validates_arbitrary_declared_depth_and_chunk_split(self):
        with tempfile.TemporaryDirectory(prefix="native-package-report-") as temp:
            workspace = Path(temp) / "report"
            result = self.run_command("package-check", workspace, PACKAGE)

            self.assertEqual(0, result.returncode, result.stderr)
            report = json.loads(
                (workspace / "package-validation.json").read_text(encoding="utf-8")
            )
            self.assertEqual("rsc-remastered.native-loader-lab", report["packageId"])
            self.assertEqual(48, report["storageSectorSize"])
            self.assertEqual(24, report["presentationChunkSize"])
            self.assertEqual(3, report["terrainSectorCount"])
            self.assertEqual(1, report["placementSetCount"])
            self.assertEqual(1, report["npcPlacementCount"])
            self.assertEqual(1, report["groundItemPlacementCount"])
            self.assertEqual(2, report["sceneryPlacementCount"])
            self.assertEqual(2, report["boundaryPlacementCount"])
            self.assertEqual({0, -2, -3}, {level["level"] for level in report["levels"]})

    def test_level_is_data_not_a_fixed_minus_two_or_minus_three_enumeration(self):
        with tempfile.TemporaryDirectory(prefix="native-package-depth-") as temp:
            package = Path(temp) / "package"
            shutil.copytree(PACKAGE, package)
            manifest_path = package / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            for level in manifest["levels"]:
                if level["level"] == -3:
                    level["level"] = -37
            for sector in manifest["terrainSectors"]:
                if sector["level"] == -3:
                    sector["level"] = -37
            manifest_path.write_text(
                json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
            )

            workspace = Path(temp) / "report"
            result = self.run_command("package-check", workspace, package)

            self.assertEqual(0, result.returncode, result.stderr)
            report = json.loads(
                (workspace / "package-validation.json").read_text(encoding="utf-8")
            )
            self.assertIn(-37, {level["level"] for level in report["levels"]})

    def test_package_refuses_changed_payload_undeclared_level_and_bad_chunk_size(self):
        cases = (
            ("changed payload", self.change_payload, "hash differs"),
            ("undeclared level", self.undeclare_level, "undeclared level"),
            ("bad chunk", self.bad_chunk, "positive divisor of 48"),
            ("invalid uniform tile", self.invalid_uniform_tile, "unsigned byte"),
            ("invalid RLE tile", self.invalid_rle_tile, "unsigned byte"),
            ("underfilled RLE sector", self.underfill_rle_sector, "exactly 2304"),
            ("overfilled RLE sector", self.overfill_rle_sector, "remaining sector"),
            (
                "changed placement payload",
                self.change_placement_payload,
                "Placement payload hash differs",
            ),
            (
                "duplicate placement ID",
                self.duplicate_placement_id,
                "Duplicate placement ID",
            ),
            (
                "placement without terrain",
                self.move_placement_outside_terrain,
                "has no package terrain",
            ),
            (
                "invalid placement respawn",
                self.invalid_placement_respawn,
                "must be positive",
            ),
            (
                "inverted exact NPC bounds",
                self.invert_exact_npc_bounds,
                "minimum must not exceed maximum",
            ),
            (
                "exact NPC bounds without terrain",
                self.move_exact_npc_bounds_outside_terrain,
                "roam bounds have no package terrain",
            ),
            (
                "invalid boundary direction",
                self.invalid_boundary_direction,
                "must be 0..7",
            ),
            (
                "duplicate scenery slot",
                self.duplicate_scenery_slot,
                "Duplicate scenery slot",
            ),
        )
        for label, mutate, expected in cases:
            with self.subTest(label=label), tempfile.TemporaryDirectory(
                prefix="native-package-refusal-"
            ) as temp:
                package = Path(temp) / "package"
                shutil.copytree(PACKAGE, package)
                mutate(package)
                workspace = Path(temp) / "report"

                result = self.run_command("package-check", workspace, package)

                self.assertEqual(3, result.returncode, result.stderr)
                self.assertIn(expected, result.stderr)
                self.assertFalse(workspace.exists())

    def test_v1_entity_only_placement_payload_remains_supported(self):
        with tempfile.TemporaryDirectory(
            prefix="native-package-placement-v1-"
        ) as temp:
            package = Path(temp) / "package"
            shutil.copytree(PACKAGE, package)
            path = package / "placements/deep-l2-entities.json"
            payload = json.loads(path.read_text(encoding="utf-8"))
            payload["schemaVersion"] = 1
            payload["encoding"] = "layered-entity-placements-v1"
            payload.pop("scenery")
            payload.pop("boundaries")
            path.write_text(
                json.dumps(payload, indent=2) + "\n", encoding="utf-8"
            )
            manifest_path = package / "manifest.json"
            manifest = json.loads(
                manifest_path.read_text(encoding="utf-8")
            )
            manifest["packageVersion"] = "0.3.0"
            manifest["placementSets"][0]["encoding"] = (
                "layered-entity-placements-v1"
            )
            manifest["placementSets"][0]["sha256"] = hashlib.sha256(
                path.read_bytes()
            ).hexdigest()
            manifest_path.write_text(
                json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
            )
            workspace = Path(temp) / "report"

            result = self.run_command("package-check", workspace, package)

            self.assertEqual(0, result.returncode, result.stderr)
            report = json.loads(
                (workspace / "package-validation.json").read_text(
                    encoding="utf-8"
                )
            )
            self.assertEqual(1, report["npcPlacementCount"])
            self.assertEqual(1, report["groundItemPlacementCount"])
            self.assertEqual(0, report["sceneryPlacementCount"])
            self.assertEqual(0, report["boundaryPlacementCount"])

    def test_raw_sector_accepts_exact_native_bytes_and_refuses_wrong_length(self):
        with tempfile.TemporaryDirectory(prefix="native-package-raw-") as temp:
            package = Path(temp) / "package"
            shutil.copytree(PACKAGE, package)
            relative_path = self.replace_expansion_with_raw(package)

            accepted = self.run_command(
                "package-check", Path(temp) / "accepted", package
            )

            self.assertEqual(0, accepted.returncode, accepted.stderr)
            raw_path = package / relative_path
            raw_path.write_bytes(raw_path.read_bytes()[:-1])
            self.update_payload_hash(package, relative_path)

            refused = self.run_command(
                "package-check", Path(temp) / "refused", package
            )

            self.assertEqual(3, refused.returncode, refused.stderr)
            self.assertIn("exactly 23040 bytes", refused.stderr)

    def test_terrain_only_review_package_accepts_no_placement_sets(self):
        with tempfile.TemporaryDirectory(
            prefix="native-package-terrain-only-"
        ) as temp:
            package = Path(temp) / "package"
            shutil.copytree(PACKAGE, package)
            manifest_path = package / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            manifest["packageId"] = "rsc-remastered.terrain-only-review"
            manifest["placementSets"] = []
            manifest_path.write_text(
                json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
            )

            result = self.run_command(
                "package-check", Path(temp) / "report", package
            )

            self.assertEqual(0, result.returncode, result.stderr)
            report = json.loads(
                (Path(temp) / "report/package-validation.json").read_text(
                    encoding="utf-8"
                )
            )
            self.assertEqual(0, report["placementSetCount"])
            self.assertEqual(0, report["npcPlacementCount"])
            self.assertEqual(0, report["groundItemPlacementCount"])
            self.assertEqual(0, report["sceneryPlacementCount"])
            self.assertEqual(0, report["boundaryPlacementCount"])

    def test_v3_placements_preserve_exact_asymmetric_npc_roam_bounds(self):
        with tempfile.TemporaryDirectory(
            prefix="native-package-placement-v3-"
        ) as temp:
            package = Path(temp) / "package"
            shutil.copytree(PACKAGE, package)
            self.convert_placements_to_v3(package)

            result = self.run_command(
                "package-check", Path(temp) / "report", package
            )

            self.assertEqual(0, result.returncode, result.stderr)
            payload = json.loads(
                (package / "placements/deep-l2-entities.json").read_text(
                    encoding="utf-8"
                )
            )
            self.assertEqual(
                {
                    "minimum": {"x": 450, "y": 599},
                    "maximum": {"x": 455, "y": 603},
                },
                payload["npcs"][0]["roamBounds"],
            )

    def test_preservation_parity_package_is_exact_isolated_and_deterministic(self):
        source_archive = ROOT / "server/conf/server/data/Authentic_Landscape.orsc"
        source_sha = hashlib.sha256(source_archive.read_bytes()).hexdigest()
        npc_source = ROOT / "server/conf/server/defs/locs/NpcLocs.json"
        npc_source_sha = hashlib.sha256(npc_source.read_bytes()).hexdigest()
        with tempfile.TemporaryDirectory(
            prefix="preservation-terrain-package-"
        ) as temp:
            first_workspace = Path(temp) / "first"
            second_workspace = Path(temp) / "second"

            first = self.run_command(
                "preservation-package", first_workspace
            )

            self.assertEqual(0, first.returncode, first.stderr)
            report = json.loads(
                (first_workspace / "generation-report.json").read_text(
                    encoding="utf-8"
                )
            )
            self.assertEqual("transitions-pending", report["reviewState"])
            self.assertFalse(report["runtimePromotionApproved"])
            self.assertTrue(report["legacyRoundTripVerified"])
            self.assertEqual(
                "raw-layered-sector-v1", report["terrainEncoding"]
            )
            self.assertEqual(1764, report["terrainSectorCount"])
            self.assertEqual(1764 * 48 * 48 * 10, report["terrainPayloadBytes"])
            self.assertEqual(
                {"-1": 441, "0": 441, "1": 441, "2": 441},
                report["sectorCountByLevel"],
            )
            self.assertEqual(
                "layered-world-placements-v3",
                report["placementEncoding"],
            )
            self.assertEqual(32364, report["sourcePlacementRecords"])
            self.assertEqual(32364, report["convertedPlacementRecords"])
            self.assertEqual(
                {
                    "boundaries": 966,
                    "groundItems": 1016,
                    "npcs": 3612,
                    "scenery": 26770,
                },
                report["convertedPlacementRecordsByFamily"],
            )
            self.assertEqual(4, report["placementSetsGenerated"])
            self.assertEqual(0, report["unconvertedPlacementRecords"])
            self.assertEqual([], report["unresolvedPlacements"])
            self.assertEqual(1, len(report["conversionRepairs"]))
            repair = report["conversionRepairs"][0]
            self.assertEqual(
                "preservation-r64.npc.003376.max-y-6549-to-3549",
                repair["repairId"],
            )
            self.assertEqual("npc", repair["family"])
            self.assertEqual("base-npcs", repair["sourceRole"])
            self.assertEqual(3376, repair["sourceIndex"])
            self.assertEqual(67, repair["sourceDefinitionId"])
            self.assertEqual(
                "owner-approved-vanilla-baseline-repair",
                repair["policy"],
            )
            self.assertEqual("maximumPacked.y", repair["field"])
            self.assertEqual(6549, repair["sourceValue"])
            self.assertEqual(3549, repair["targetValue"])

            package = first_workspace / "package"
            manifest = json.loads(
                (package / "manifest.json").read_text(encoding="utf-8")
            )
            self.assertEqual(1764, len(manifest["terrainSectors"]))
            self.assertEqual(4, len(manifest["placementSets"]))
            self.assertTrue(all(
                placement["encoding"] == "layered-world-placements-v3"
                for placement in manifest["placementSets"]
            ))
            self.assertEqual(
                {-1, 0, 1, 2},
                {level["level"] for level in manifest["levels"]},
            )
            self.assertTrue(all(
                sector["encoding"] == "raw-layered-sector-v1"
                for sector in manifest["terrainSectors"]
            ))
            self.assertTrue(all(
                (package / sector["path"]).stat().st_size == 23040
                for sector in manifest["terrainSectors"]
            ))
            self.assert_preservation_terrain_round_trip(
                source_archive, package, manifest
            )
            self.assert_preservation_placement_round_trip(
                package, manifest
            )

            validation_workspace = Path(temp) / "validation"
            validation = self.run_command(
                "package-check", validation_workspace, package
            )
            self.assertEqual(0, validation.returncode, validation.stderr)
            validation_report = json.loads(
                (validation_workspace / "package-validation.json").read_text(
                    encoding="utf-8"
                )
            )
            self.assertEqual(1764, validation_report["terrainSectorCount"])
            self.assertEqual(4, validation_report["placementSetCount"])
            self.assertEqual(3612, validation_report["npcPlacementCount"])
            self.assertEqual(
                1016, validation_report["groundItemPlacementCount"]
            )
            self.assertEqual(
                26770, validation_report["sceneryPlacementCount"]
            )
            self.assertEqual(
                966, validation_report["boundaryPlacementCount"]
            )

            second = self.run_command(
                "preservation-package", second_workspace
            )
            self.assertEqual(0, second.returncode, second.stderr)
            self.assertEqual(
                self.package_tree_hash(package),
                self.package_tree_hash(second_workspace / "package"),
            )

            refused = self.run_command(
                "preservation-package", first_workspace
            )
            self.assertEqual(3, refused.returncode, refused.stderr)
            self.assertIn("use a fresh isolated workspace", refused.stderr)
            self.assertEqual(
                source_sha,
                hashlib.sha256(source_archive.read_bytes()).hexdigest(),
            )
            self.assertEqual(
                npc_source_sha,
                hashlib.sha256(npc_source.read_bytes()).hexdigest(),
            )

    def test_new_schemas_are_valid_and_keep_level_signed(self):
        baseline_schema = json.loads(
            (TOOL_ROOT / "schema/preservation-baseline-v1.schema.json").read_text(
                encoding="utf-8"
            )
        )
        package_schema = json.loads(
            (TOOL_ROOT / "schema/layered-world-package-v1.schema.json").read_text(
                encoding="utf-8"
            )
        )
        uniform_schema = json.loads(
            (TOOL_ROOT / "schema/uniform-layered-sector-v1.schema.json").read_text(
                encoding="utf-8"
            )
        )
        rle_schema = json.loads(
            (TOOL_ROOT / "schema/rle-layered-sector-v1.schema.json").read_text(
                encoding="utf-8"
            )
        )
        placement_schema = json.loads(
            (
                TOOL_ROOT
                / "schema/layered-entity-placements-v1.schema.json"
            ).read_text(encoding="utf-8")
        )
        world_placement_schema = json.loads(
            (
                TOOL_ROOT
                / "schema/layered-world-placements-v2.schema.json"
            ).read_text(encoding="utf-8")
        )
        world_placement_v3_schema = json.loads(
            (
                TOOL_ROOT
                / "schema/layered-world-placements-v3.schema.json"
            ).read_text(encoding="utf-8")
        )
        self.assertEqual(
            "rsc-remastered-preservation-r64-v1",
            baseline_schema["properties"]["baselineId"]["const"],
        )
        level = package_schema["properties"]["levels"]["items"]["properties"]["level"]
        self.assertEqual(-(2**31), level["minimum"])
        self.assertEqual(2**31 - 1, level["maximum"])
        self.assertEqual(48, package_schema["properties"]["storage"]["properties"]["sectorSize"]["const"])
        self.assertIn(
            24,
            package_schema["properties"]["storage"]["properties"][
                "presentationChunkSize"
            ]["enum"],
        )
        self.assertEqual(
            "uniform-layered-sector-v1",
            uniform_schema["properties"]["encoding"]["const"],
        )
        self.assertEqual(
            "rle-layered-sector-v1",
            rle_schema["properties"]["encoding"]["const"],
        )
        self.assertEqual(
            "x-major-y-minor",
            rle_schema["properties"]["tileOrder"]["const"],
        )
        self.assertEqual(
            "layered-entity-placements-v1",
            placement_schema["properties"]["encoding"]["const"],
        )
        self.assertEqual(
            "layered-world-placements-v2",
            world_placement_schema["properties"]["encoding"]["const"],
        )
        self.assertEqual(
            "layered-world-placements-v3",
            world_placement_v3_schema["properties"]["encoding"]["const"],
        )
        self.assertEqual(
            {
                "layered-entity-placements-v1",
                "layered-world-placements-v2",
                "layered-world-placements-v3",
            },
            set(
            package_schema["properties"]["placementSets"]["items"][
                "properties"
            ]["encoding"]["enum"]
            ),
        )
        self.assertEqual(
            {
                "uniform-layered-sector-v1",
                "rle-layered-sector-v1",
                "raw-layered-sector-v1",
            },
            set(
                package_schema["properties"]["terrainSectors"]["items"][
                    "properties"
                ]["encoding"]["enum"]
            ),
        )
        self.assertEqual(
            0,
            package_schema["properties"]["placementSets"]["minItems"],
        )

    def test_runtime_fixture_uses_client_renderable_definitions(self):
        manifest = json.loads(
            (PACKAGE / "manifest.json").read_text(encoding="utf-8")
        )
        for sector in manifest["terrainSectors"]:
            payload = json.loads(
                (PACKAGE / sector["path"]).read_text(encoding="utf-8")
            )
            tiles = (
                [payload["tile"]]
                if payload["encoding"] == "uniform-layered-sector-v1"
                else [run["tile"] for run in payload["runs"]]
            )
            for tile in tiles:
                self.assertLessEqual(tile["overlay"], 26)
                self.assertLessEqual(tile["roof"], 6)
                self.assertLessEqual(tile["verticalWall"], 214)
                self.assertLessEqual(tile["horizontalWall"], 214)
                diagonal = tile["diagonalWall"]
                self.assertTrue(
                    diagonal == 0
                    or 1 <= diagonal <= 214
                    or 12001 <= diagonal <= 12214
                )

    @staticmethod
    def change_payload(package):
        path = package / "terrain/deep-l2-x9-y12.json"
        path.write_text(path.read_text(encoding="utf-8") + "\n", encoding="utf-8")

    @staticmethod
    def undeclare_level(package):
        path = package / "manifest.json"
        manifest = json.loads(path.read_text(encoding="utf-8"))
        manifest["levels"] = [
            level for level in manifest["levels"] if level["level"] != -3
        ]
        path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

    @staticmethod
    def bad_chunk(package):
        path = package / "manifest.json"
        manifest = json.loads(path.read_text(encoding="utf-8"))
        manifest["storage"]["presentationChunkSize"] = 10
        path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

    @staticmethod
    def invalid_uniform_tile(package):
        payload_path = package / "terrain/deep-l2-x10-y12.json"
        payload = json.loads(payload_path.read_text(encoding="utf-8"))
        payload["tile"]["overlay"] = 256
        payload_path.write_text(
            json.dumps(payload, indent=2) + "\n", encoding="utf-8"
        )
        LayeredNativePackageFoundationTest.update_payload_hash(
            package, "terrain/deep-l2-x10-y12.json"
        )

    @staticmethod
    def invalid_rle_tile(package):
        payload_path = package / "terrain/deep-l2-x9-y12.json"
        payload = json.loads(payload_path.read_text(encoding="utf-8"))
        payload["runs"][1]["tile"]["overlay"] = 256
        payload_path.write_text(
            json.dumps(payload, indent=2) + "\n", encoding="utf-8"
        )
        LayeredNativePackageFoundationTest.update_payload_hash(
            package, "terrain/deep-l2-x9-y12.json"
        )

    @staticmethod
    def underfill_rle_sector(package):
        payload_path = package / "terrain/deep-l2-x9-y12.json"
        payload = json.loads(payload_path.read_text(encoding="utf-8"))
        payload["runs"][-1]["count"] -= 1
        payload_path.write_text(
            json.dumps(payload, indent=2) + "\n", encoding="utf-8"
        )
        LayeredNativePackageFoundationTest.update_payload_hash(
            package, "terrain/deep-l2-x9-y12.json"
        )

    @staticmethod
    def overfill_rle_sector(package):
        payload_path = package / "terrain/deep-l2-x9-y12.json"
        payload = json.loads(payload_path.read_text(encoding="utf-8"))
        payload["runs"][-1]["count"] += 1
        payload_path.write_text(
            json.dumps(payload, indent=2) + "\n", encoding="utf-8"
        )
        LayeredNativePackageFoundationTest.update_payload_hash(
            package, "terrain/deep-l2-x9-y12.json"
        )

    @staticmethod
    def change_placement_payload(package):
        path = package / "placements/deep-l2-entities.json"
        path.write_text(
            path.read_text(encoding="utf-8") + "\n", encoding="utf-8"
        )

    @staticmethod
    def duplicate_placement_id(package):
        path = package / "placements/deep-l2-entities.json"
        payload = json.loads(path.read_text(encoding="utf-8"))
        payload["groundItems"][0]["placementId"] = payload["npcs"][0][
            "placementId"
        ]
        path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        LayeredNativePackageFoundationTest.update_payload_hash(
            package, "placements/deep-l2-entities.json"
        )

    @staticmethod
    def move_placement_outside_terrain(package):
        path = package / "placements/deep-l2-entities.json"
        payload = json.loads(path.read_text(encoding="utf-8"))
        payload["groundItems"][0]["position"]["x"] = 10000
        path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        LayeredNativePackageFoundationTest.update_payload_hash(
            package, "placements/deep-l2-entities.json"
        )

    @staticmethod
    def invalid_placement_respawn(package):
        path = package / "placements/deep-l2-entities.json"
        payload = json.loads(path.read_text(encoding="utf-8"))
        payload["groundItems"][0]["respawnSeconds"] = 0
        path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        LayeredNativePackageFoundationTest.update_payload_hash(
            package, "placements/deep-l2-entities.json"
        )

    @staticmethod
    def invert_exact_npc_bounds(package):
        LayeredNativePackageFoundationTest.convert_placements_to_v3(package)
        path = package / "placements/deep-l2-entities.json"
        payload = json.loads(path.read_text(encoding="utf-8"))
        payload["npcs"][0]["roamBounds"]["minimum"]["x"] = 456
        path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        LayeredNativePackageFoundationTest.update_payload_hash(
            package, "placements/deep-l2-entities.json"
        )

    @staticmethod
    def move_exact_npc_bounds_outside_terrain(package):
        LayeredNativePackageFoundationTest.convert_placements_to_v3(package)
        path = package / "placements/deep-l2-entities.json"
        payload = json.loads(path.read_text(encoding="utf-8"))
        payload["npcs"][0]["roamBounds"]["maximum"]["x"] = 600
        path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        LayeredNativePackageFoundationTest.update_payload_hash(
            package, "placements/deep-l2-entities.json"
        )

    @staticmethod
    def invalid_boundary_direction(package):
        path = package / "placements/deep-l2-entities.json"
        payload = json.loads(path.read_text(encoding="utf-8"))
        payload["boundaries"][0]["direction"] = 8
        path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        LayeredNativePackageFoundationTest.update_payload_hash(
            package, "placements/deep-l2-entities.json"
        )

    @staticmethod
    def duplicate_scenery_slot(package):
        path = package / "placements/deep-l2-entities.json"
        payload = json.loads(path.read_text(encoding="utf-8"))
        duplicate = dict(payload["scenery"][0])
        duplicate["placementId"] = "deep-fixture-table-duplicate"
        duplicate["position"] = dict(duplicate["position"])
        payload["scenery"].append(duplicate)
        path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        LayeredNativePackageFoundationTest.update_payload_hash(
            package, "placements/deep-l2-entities.json"
        )

    @staticmethod
    def update_payload_hash(package, relative_path):
        payload_path = package / relative_path
        payload_hash = hashlib.sha256(payload_path.read_bytes()).hexdigest()
        manifest_path = package / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        for record in [
            *manifest["terrainSectors"],
            *manifest["placementSets"],
        ]:
            if record["path"] == relative_path:
                record["sha256"] = payload_hash
        manifest_path.write_text(
            json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
        )

    @staticmethod
    def replace_expansion_with_raw(package):
        json_relative = "terrain/expansion-l3-x9-y12.json"
        raw_relative = "terrain/expansion-l3-x9-y12.raw"
        source = json.loads(
            (package / json_relative).read_text(encoding="utf-8")
        )
        tile = source["tile"]
        raw_tile = bytes(
            [
                tile["elevation"],
                tile["texture"],
                tile["overlay"],
                tile["roof"],
                tile["verticalWall"],
                tile["horizontalWall"],
            ]
        ) + tile["diagonalWall"].to_bytes(4, byteorder="big")
        raw_path = package / raw_relative
        raw_path.write_bytes(raw_tile * (48 * 48))

        manifest_path = package / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        for sector in manifest["terrainSectors"]:
            if sector["path"] == json_relative:
                sector["encoding"] = "raw-layered-sector-v1"
                sector["path"] = raw_relative
                sector["sha256"] = hashlib.sha256(
                    raw_path.read_bytes()
                ).hexdigest()
        manifest_path.write_text(
            json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
        )
        return raw_relative

    @staticmethod
    def convert_placements_to_v3(package):
        relative_path = "placements/deep-l2-entities.json"
        path = package / relative_path
        payload = json.loads(path.read_text(encoding="utf-8"))
        payload["schemaVersion"] = 3
        payload["encoding"] = "layered-world-placements-v3"
        npc = payload["npcs"][0]
        npc.pop("roamRadius")
        npc["roamBounds"] = {
            "minimum": {"x": 450, "y": 599},
            "maximum": {"x": 455, "y": 603},
        }
        path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
        manifest_path = package / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        manifest["packageVersion"] = "0.8.0"
        manifest["placementSets"][0]["encoding"] = (
            "layered-world-placements-v3"
        )
        manifest_path.write_text(
            json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
        )
        LayeredNativePackageFoundationTest.update_payload_hash(
            package, relative_path
        )

    def assert_preservation_terrain_round_trip(
        self, source_archive, package, manifest
    ):
        plane_by_level = {0: 0, 1: 1, 2: 2, -1: 3}
        with zipfile.ZipFile(source_archive) as archive:
            for sector in manifest["terrainSectors"]:
                entry = "h{}x{}y{}".format(
                    plane_by_level[sector["level"]],
                    sector["sectorX"] + 48,
                    sector["sectorY"] + 37,
                )
                legacy = archive.read(entry)
                native = bytearray((package / sector["path"]).read_bytes())
                for offset in range(0, len(native), 10):
                    native[offset + 4], native[offset + 5] = (
                        native[offset + 5],
                        native[offset + 4],
                    )
                self.assertEqual(legacy, native, entry)

    def assert_preservation_placement_round_trip(self, package, manifest):
        generated = {
            "npcs": {},
            "groundItems": {},
            "scenery": {},
            "boundaries": {},
        }
        for placement_set in manifest["placementSets"]:
            payload = json.loads(
                (package / placement_set["path"]).read_text(encoding="utf-8")
            )
            for family in generated:
                for record in payload[family]:
                    generated[family][record["placementId"]] = record

        source_specs = (
            (
                "boundaries",
                ROOT / "server/conf/server/defs/locs/BoundaryLocs.json",
                "boundaries",
                "boundary",
            ),
            (
                "scenery",
                ROOT / "server/conf/server/defs/locs/SceneryLocs.json",
                "sceneries",
                "scenery",
            ),
            (
                "groundItems",
                ROOT / "server/conf/server/defs/locs/GroundItems.json",
                "grounditems",
                "ground-item",
            ),
        )
        for family, path, source_key, id_family in source_specs:
            values = json.loads(path.read_text(encoding="utf-8"))[source_key]
            for index, source in enumerate(values):
                placement_id = (
                    f"preservation-r64.{id_family}.{index:06d}"
                )
                record = generated[family][placement_id]
                expected_position = self.decode_packed_position(source["pos"])
                self.assertEqual(expected_position, record["position"])
                self.assertEqual(source["id"], record[
                    {
                        "boundaries": "boundaryId",
                        "scenery": "sceneryId",
                        "groundItems": "itemId",
                    }[family]
                ])
                if family in {"boundaries", "scenery"}:
                    self.assertEqual(source["direction"], record["direction"])
                else:
                    self.assertEqual(source["amount"], record["amount"])
                    self.assertEqual(
                        source["respawn"], record["respawnSeconds"]
                    )

        npc_values = json.loads(
            (
                ROOT / "server/conf/server/defs/locs/NpcLocs.json"
            ).read_text(encoding="utf-8")
        )["npclocs"]
        for index, source in enumerate(npc_values):
            placement_id = f"preservation-r64.npc.{index:06d}"
            if index == 3376:
                source = {
                    **source,
                    "max": {**source["max"], "Y": 3549},
                }
            record = generated["npcs"][placement_id]
            self.assertEqual(source["id"], record["npcId"])
            self.assertEqual(
                self.decode_packed_position(source["start"]),
                record["start"],
            )
            self.assertEqual(
                self.decode_packed_position(source["min"]),
                record["roamBounds"]["minimum"],
            )
            self.assertEqual(
                self.decode_packed_position(source["max"]),
                record["roamBounds"]["maximum"],
            )

    @staticmethod
    def decode_packed_position(position):
        plane_to_level = {0: 0, 1: 1, 2: 2, 3: -1}
        plane, y = divmod(position["Y"], 944)
        if plane not in plane_to_level:
            raise AssertionError(f"unsupported packed position: {position}")
        return {"x": position["X"], "y": y}

    @staticmethod
    def package_tree_hash(package):
        digest = hashlib.sha256()
        for path in sorted(item for item in package.rglob("*") if item.is_file()):
            digest.update(path.relative_to(package).as_posix().encode("utf-8"))
            digest.update(b"\0")
            digest.update(hashlib.sha256(path.read_bytes()).digest())
        return digest.hexdigest()


if __name__ == "__main__":
    unittest.main()
