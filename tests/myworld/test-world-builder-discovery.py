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
SOURCE_ROOT = ROOT / "tools" / "world-builder" / "src"
SCHEMA_ROOT = ROOT / "tools" / "world-builder" / "schema"
MAIN_CLASS = "com.openrsc.worldbuilder.WorldBuilderCli"

AUTHORED_RELATIVE_PATHS = (
    "server/conf/server/data/Custom_Landscape.orsc",
    "Client_Base/Cache/video/Custom_Landscape.orsc",
    "server/conf/server/defs/locs/MyWorldSceneryLocs.json",
    "server/conf/server/defs/locs/MyWorldSceneryRemovals.json",
    "server/conf/server/defs/locs/MyWorldNpcLocs.json",
    "server/conf/server/defs/locs/MyWorldNpcRemovals.json",
)

CONTENT_RELATIVE_PATHS = (
    "server/conf/server/defs/TileDef.xml",
    "server/conf/server/defs/GameObjectDef.xml",
    "server/conf/server/defs/NpcDefs.json",
    "server/conf/server/defs/NpcDefsCustom.json",
    "server/conf/server/defs/NpcDefsMyWorld.json",
    "server/conf/server/defs/NpcDefsPatch18.json",
    "Client_Base/Cache/video/library.orsc",
)


class WorldBuilderDiscoveryTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="world-builder-discovery-classes-")
        cls.classes = Path(cls.compile_temp.name)
        sources = sorted(str(path) for path in SOURCE_ROOT.rglob("*.java"))
        subprocess.run(
            ["javac", "-source", "8", "-target", "8", "-d", str(cls.classes), *sources],
            check=True,
            cwd=ROOT,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def run_discovery(self, root, *extra):
        return subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                MAIN_CLASS,
                "discover",
                "--server-root",
                str(root),
                *extra,
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )

    def fixture(self, base, overlays=True):
        root = Path(base) / "private-server"
        config = root / "server" / "myworld.conf"
        config.parent.mkdir(parents=True)
        config.write_text(
            "\n".join(
                (
                    "client_version: 10047",
                    "\tmember_world: true",
                    "\tbased_map_data: 64 # fixture",
                    "\twant_myworld: true",
                    "\tcustom_landscape: true",
                    "",
                )
            ),
            encoding="utf-8",
        )

        server_terrain = root / AUTHORED_RELATIVE_PATHS[0]
        client_terrain = root / AUTHORED_RELATIVE_PATHS[1]
        server_terrain.parent.mkdir(parents=True)
        client_terrain.parent.mkdir(parents=True)
        self.write_archive(server_terrain, seed=11)
        shutil.copyfile(server_terrain, client_terrain)

        locs = root / "server/conf/server/defs/locs"
        locs.mkdir(parents=True)
        if overlays:
            for relative in AUTHORED_RELATIVE_PATHS[2:]:
                path = root / relative
                key = path.stem.replace("MyWorld", "").lower()
                path.write_text(json.dumps({key: []}, sort_keys=True) + "\n", encoding="utf-8")

        for index, relative in enumerate(CONTENT_RELATIVE_PATHS):
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes((f"definition-{index}-{relative}\n").encode("utf-8"))
        return root

    @staticmethod
    def write_archive(path, seed):
        raw = bytes((seed + index * 13) & 0xFF for index in range(48 * 48 * 10))
        with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            info = zipfile.ZipInfo("h0x48y37", (2024, 1, 2, 3, 4, 6))
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

    def assert_refused_without_writes(self, root, expected_message, *extra):
        before = self.snapshot(root)
        result = self.run_discovery(root, *extra)
        after = self.snapshot(root)
        self.assertEqual(3, result.returncode, result.stderr)
        self.assertIn(expected_message, result.stderr)
        self.assertEqual(before, after)

    def test_supported_fixture_is_read_only_and_deterministic(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-layout-") as temp:
            root = self.fixture(temp)
            before = self.snapshot(root)
            first = self.run_discovery(root)
            second = self.run_discovery(root)
            self.assertEqual(0, first.returncode, first.stderr)
            self.assertEqual(first.stdout, second.stdout)
            self.assertEqual(before, self.snapshot(root))

            manifest = json.loads(first.stdout)
            self.assertEqual(1, manifest["schemaVersion"])
            self.assertEqual("world-builder-project-source", manifest["manifestType"])
            self.assertEqual("spoiled-milk-repository-v1", manifest["layoutAdapter"])
            self.assertEqual("server/myworld.conf", manifest["selectedConfig"])
            self.assertRegex(manifest["selectedConfigSha256"], r"^[0-9a-f]{64}$")
            self.assertEqual(1, manifest["terrainSectorCount"])
            self.assertEqual(6, len(manifest["files"]))
            self.assertEqual(
                manifest["files"][0]["sha256"], manifest["files"][1]["sha256"]
            )
            self.assertTrue(all(file_state["present"] for file_state in manifest["files"]))
            self.assertRegex(manifest["contentFingerprintSha256"], r"^[0-9a-f]{64}$")
            self.assertRegex(manifest["sourceFingerprintSha256"], r"^[0-9a-f]{64}$")

    def test_current_repository_matches_supported_layout(self):
        result = self.run_discovery(ROOT)
        self.assertEqual(0, result.returncode, result.stderr)
        manifest = json.loads(result.stdout)
        self.assertEqual(10047, manifest["configuration"]["clientVersion"])
        self.assertGreater(manifest["terrainSectorCount"], 1000)
        self.assertTrue(all(file_state["present"] for file_state in manifest["files"]))

    def test_absent_optional_overlays_are_recorded_without_creation(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-optional-") as temp:
            root = self.fixture(temp, overlays=False)
            before = self.snapshot(root)
            result = self.run_discovery(root)
            self.assertEqual(0, result.returncode, result.stderr)
            manifest = json.loads(result.stdout)
            self.assertEqual(before, self.snapshot(root))
            for file_state in manifest["files"][2:]:
                self.assertFalse(file_state["present"])
                self.assertEqual(0, file_state["size"])
                self.assertEqual("", file_state["sha256"])

    def test_mismatched_client_archive_is_rejected_without_writes(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-mismatch-") as temp:
            root = self.fixture(temp)
            self.write_archive(root / AUTHORED_RELATIVE_PATHS[1], seed=19)
            self.assert_refused_without_writes(
                root, "Server and client Custom_Landscape.orsc files are not byte-identical"
            )

    def test_missing_client_archive_is_rejected_without_writes(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-missing-client-") as temp:
            root = self.fixture(temp)
            (root / AUTHORED_RELATIVE_PATHS[1]).unlink()
            self.assert_refused_without_writes(
                root, "Required private-server file is missing"
            )

    def test_invalid_terrain_entry_is_rejected_without_writes(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-invalid-terrain-") as temp:
            root = self.fixture(temp)
            for relative in AUTHORED_RELATIVE_PATHS[:2]:
                with zipfile.ZipFile(root / relative, "w") as archive:
                    archive.writestr("unexpected", b"not terrain")
            self.assert_refused_without_writes(
                root, "Terrain archive contains an unsupported entry"
            )

    def test_duplicate_required_config_is_ambiguous(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-config-") as temp:
            root = self.fixture(temp)
            config = root / "server/myworld.conf"
            config.write_text(
                config.read_text(encoding="utf-8") + "custom_landscape: true\n",
                encoding="utf-8",
            )
            self.assert_refused_without_writes(
                root, "appears more than once: custom_landscape"
            )

    def test_unsupported_map_mode_is_rejected_without_writes(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-map-mode-") as temp:
            root = self.fixture(temp)
            config = root / "server/myworld.conf"
            config.write_text(
                config.read_text(encoding="utf-8").replace(
                    "custom_landscape: true", "custom_landscape: false"
                ),
                encoding="utf-8",
            )
            self.assert_refused_without_writes(
                root, "requires custom_landscape: true"
            )

    def test_incompatible_content_fingerprint_is_rejected_without_writes(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-content-") as temp:
            root = self.fixture(temp)
            self.assert_refused_without_writes(
                root,
                "Target definitions do not match this World Builder release",
                "--expected-content-sha256",
                "0" * 64,
            )

    def test_missing_definition_is_rejected_without_writes(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-definitions-") as temp:
            root = self.fixture(temp)
            (root / CONTENT_RELATIVE_PATHS[0]).unlink()
            self.assert_refused_without_writes(
                root, "Required private-server file is missing"
            )

    @unittest.skipUnless(hasattr(os, "symlink"), "symbolic links unavailable")
    def test_symlink_escape_is_rejected_without_touching_external_file(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-symlink-") as temp:
            root = self.fixture(temp)
            external = Path(temp) / "outside.orsc"
            shutil.copyfile(root / AUTHORED_RELATIVE_PATHS[0], external)
            server_terrain = root / AUTHORED_RELATIVE_PATHS[0]
            server_terrain.unlink()
            server_terrain.symlink_to(external)
            external_before = external.read_bytes()
            self.assert_refused_without_writes(root, "escapes its root")
            self.assertEqual(external_before, external.read_bytes())

    def test_config_path_cannot_escape_server_root(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-traversal-") as temp:
            root = self.fixture(temp)
            self.assert_refused_without_writes(
                root,
                "Configuration path must remain inside the server root",
                "--config",
                "../outside.conf",
            )

    def test_versioned_manifest_schemas_are_pinned(self):
        project = json.loads((SCHEMA_ROOT / "project-manifest-v1.schema.json").read_text())
        export = json.loads((SCHEMA_ROOT / "export-manifest-v1.schema.json").read_text())
        receipt = json.loads((SCHEMA_ROOT / "import-receipt-v1.schema.json").read_text())
        self.assertEqual(1, project["properties"]["schemaVersion"]["const"])
        self.assertEqual(1, export["properties"]["schemaVersion"]["const"])
        self.assertEqual(1, receipt["properties"]["schemaVersion"]["const"])
        self.assertEqual(6, project["properties"]["files"]["minItems"])
        self.assertEqual(5, export["properties"]["files"]["minItems"])
        self.assertIn("pending", receipt["properties"]["status"]["enum"])
        self.assertIn("reverted", receipt["properties"]["status"]["enum"])


if __name__ == "__main__":
    unittest.main()
