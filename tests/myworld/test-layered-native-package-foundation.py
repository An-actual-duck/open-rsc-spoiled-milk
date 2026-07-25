#!/usr/bin/env python3
import json
import hashlib
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TOOL_ROOT = ROOT / "tools" / "layered-maps"
SOURCE_ROOT = TOOL_ROOT / "src"
MAIN_CLASS = "com.openrsc.layeredmaps.LayeredMapsCli"
BASELINE = TOOL_ROOT / "baselines/rsc-remastered-preservation-r64-v1.json"
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
        payload_path = package / "terrain/deep-l2-x9-y12.json"
        payload = json.loads(payload_path.read_text(encoding="utf-8"))
        payload["tile"]["overlay"] = 256
        payload_path.write_text(
            json.dumps(payload, indent=2) + "\n", encoding="utf-8"
        )
        payload_hash = hashlib.sha256(payload_path.read_bytes()).hexdigest()
        manifest_path = package / "manifest.json"
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        for sector in manifest["terrainSectors"]:
            if sector["path"] == "terrain/deep-l2-x9-y12.json":
                sector["sha256"] = payload_hash
        manifest_path.write_text(
            json.dumps(manifest, indent=2) + "\n", encoding="utf-8"
        )


if __name__ == "__main__":
    unittest.main()
