#!/usr/bin/env python3
import fcntl
import hashlib
import json
import os
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE_ROOT = ROOT / "tools/world-builder/src"
MAIN_CLASS = "com.openrsc.worldbuilder.WorldBuilderCli"
COMMIT = "a" * 40
AUTHORED = (
    "server/conf/server/data/Custom_Landscape.orsc",
    "Client_Base/Cache/video/Custom_Landscape.orsc",
    "server/conf/server/defs/locs/MyWorldSceneryLocs.json",
    "server/conf/server/defs/locs/MyWorldSceneryRemovals.json",
    "server/conf/server/defs/locs/MyWorldNpcLocs.json",
    "server/conf/server/defs/locs/MyWorldNpcRemovals.json",
)
CONTENT = (
    "server/conf/server/defs/TileDef.xml",
    "server/conf/server/defs/GameObjectDef.xml",
    "server/conf/server/defs/NpcDefs.json",
    "server/conf/server/defs/NpcDefsCustom.json",
    "server/conf/server/defs/NpcDefsMyWorld.json",
    "server/conf/server/defs/NpcDefsPatch18.json",
    "Client_Base/Cache/video/library.orsc",
)
EMPTY_OVERLAYS = {
    AUTHORED[2]: '{"sceneries": []}\n',
    AUTHORED[3]: '{"scenery_removals": []}\n',
    AUTHORED[4]: '{"npclocs": []}\n',
    AUTHORED[5]: '{"npc_removals": []}\n',
}


class WorldBuilderExportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="world-builder-export-classes-")
        cls.classes = Path(cls.compile_temp.name)
        sources = sorted(str(path) for path in SOURCE_ROOT.rglob("*.java"))
        subprocess.run(
            ["javac", "-source", "8", "-target", "8", "-d", str(cls.classes), *sources],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    @staticmethod
    def write_archive(path: Path, seed: int):
        path.parent.mkdir(parents=True, exist_ok=True)
        raw = bytes((seed + index * 7) & 0xFF for index in range(48 * 48 * 10))
        with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("h0x48y37", raw)

    def make_layout(self, root: Path, seed: int, overlays=True):
        config = root / "server/myworld.conf"
        config.parent.mkdir(parents=True, exist_ok=True)
        config.write_text(
            "\n".join(
                (
                    "server_name: Fixture",
                    "server_bind_address: 0.0.0.0",
                    "server_port: 43594",
                    "max_players: 100",
                    "client_version: 10047",
                    "member_world: true",
                    "based_map_data: 64",
                    "want_myworld: true",
                    "custom_landscape: true",
                    "want_packet_register: true",
                    "",
                )
            ),
            encoding="utf-8",
        )
        self.write_archive(root / AUTHORED[0], seed)
        (root / AUTHORED[1]).parent.mkdir(parents=True, exist_ok=True)
        (root / AUTHORED[1]).write_bytes((root / AUTHORED[0]).read_bytes())
        if overlays:
            for relative, content in EMPTY_OVERLAYS.items():
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(content, encoding="utf-8")
        else:
            (root / AUTHORED[2]).parent.mkdir(parents=True, exist_ok=True)
        for index, relative in enumerate(CONTENT):
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(f"compatible-definition-{index}\n".encode())

    def make_runtime(self, root: Path):
        self.make_layout(root, 3)
        for relative, data in {
            "server/core.jar": b"core",
            "server/plugins.jar": b"plugins",
            "server/alertwords.txt": b"\n",
            "server/badwords.txt": b"\n",
            "server/goodwords.txt": b"\n",
            "server/globalrules.txt": b"rules\n",
            "server/ipbans.txt": b"\n",
            "server/lib/runtime.jar": b"library",
            "server/database/sqlite/core.sqlite": b"query definitions",
            "server/inc/sqlite/myworld_seed.db": b"clean-seed-database",
            "Client_Base/Open_RSC_Client.jar": b"client",
        }.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)

    @staticmethod
    def snapshot(root: Path):
        result = {}
        for path in sorted(root.rglob("*")):
            if path.is_file() and not path.is_symlink():
                result[path.relative_to(root).as_posix()] = hashlib.sha256(path.read_bytes()).hexdigest()
        return result

    def run_cli(self, *args):
        return subprocess.run(
            ["java", "-cp", str(self.classes), MAIN_CLASS, *map(str, args)],
            cwd=ROOT,
            text=True,
            capture_output=True,
            timeout=20,
        )

    def prepared(self, base: Path, overlays=True):
        target, runtime, workspace = base / "target", base / "runtime", base / "project"
        self.make_layout(target, 11, overlays=overlays)
        self.make_runtime(runtime)
        result = self.run_cli(
            "prepare", "--server-root", target, "--runtime-root", runtime,
            "--workspace", workspace, "--port", "43615",
        )
        self.assertEqual(0, result.returncode, result.stderr)
        return target, workspace

    def export(self, workspace: Path):
        return self.run_cli(
            "export", "--workspace", workspace, "--builder-version", "v-test",
            "--source-commit", COMMIT,
        )

    def test_changed_export_is_canonical_deterministic_and_target_safe(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-export-") as temp:
            target, workspace = self.prepared(Path(temp))
            target_before = self.snapshot(target)
            source_before = self.snapshot(workspace / "source")
            working = workspace / "working"
            self.write_archive(working / AUTHORED[0], 27)
            (working / AUTHORED[1]).write_bytes((working / AUTHORED[0]).read_bytes())
            (working / AUTHORED[2]).write_text(
                '{"sceneries":[{"id":10,"pos":{"X":100,"Y":200},"direction":2}]}\n',
                encoding="utf-8",
            )
            (working / AUTHORED[4]).write_text(
                '{"npclocs":[{"id":20,"start":{"X":110,"Y":210},'
                '"min":{"X":108,"Y":208},"max":{"X":112,"Y":212}}]}\n',
                encoding="utf-8",
            )

            first = self.export(workspace)
            self.assertEqual(0, first.returncode, first.stderr)
            first_result = json.loads(first.stdout)
            self.assertEqual("exported", first_result["status"])
            self.assertFalse(first_result["existing"])
            self.assertEqual(3, first_result["changedFileCount"])
            export_dir = Path(first_result["exportDirectory"])
            expected = {
                "CHANGE-SUMMARY.txt",
                "manifest.json",
                "authored/Custom_Landscape.orsc",
                "authored/MyWorldSceneryLocs.json",
                "authored/MyWorldSceneryRemovals.json",
                "authored/MyWorldNpcLocs.json",
                "authored/MyWorldNpcRemovals.json",
            }
            actual = {path.relative_to(export_dir).as_posix() for path in export_dir.rglob("*") if path.is_file()}
            self.assertEqual(expected, actual)
            manifest = json.loads((export_dir / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(1, manifest["schemaVersion"])
            self.assertEqual("world-builder-export", manifest["manifestType"])
            self.assertEqual(COMMIT, manifest["sourceCommit"])
            self.assertEqual(5, len(manifest["files"]))
            self.assertEqual(
                {"changedFileCount": 3, "terrainChanged": True, "sceneryChanged": True, "npcChanged": True},
                manifest["changeSummary"],
            )
            for file_record in manifest["files"]:
                exported = export_dir / file_record["bundlePath"]
                self.assertEqual(file_record["size"], exported.stat().st_size)
                self.assertEqual(file_record["sha256"], hashlib.sha256(exported.read_bytes()).hexdigest())
                self.assertTrue(file_record["sourcePresent"])
                self.assertRegex(file_record["sourceSha256"], r"^[0-9a-f]{64}$")
                self.assertEqual(
                    file_record["changed"],
                    file_record["sha256"] != file_record["sourceSha256"],
                )
            self.assertFalse(any("credential" in path.lower() or path.endswith(".db") for path in actual))

            first_snapshot = self.snapshot(export_dir)
            second = self.export(workspace)
            self.assertEqual(0, second.returncode, second.stderr)
            second_result = json.loads(second.stdout)
            self.assertTrue(second_result["existing"])
            self.assertEqual(first_result["exportDirectory"], second_result["exportDirectory"])
            self.assertEqual(first_result["authoredFingerprintSha256"], second_result["authoredFingerprintSha256"])
            self.assertEqual(first_snapshot, self.snapshot(export_dir))
            self.assertEqual(target_before, self.snapshot(target))
            self.assertEqual(source_before, self.snapshot(workspace / "source"))

            summary_path = export_dir / "CHANGE-SUMMARY.txt"
            summary_path.write_text("tampered\n", encoding="utf-8")
            tampered = self.export(workspace)
            self.assertEqual(3, tampered.returncode)
            self.assertIn("summary verification failed", tampered.stderr)
            self.assertEqual("tampered\n", summary_path.read_text(encoding="utf-8"))

    def test_no_changes_produces_no_export(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-noop-export-") as temp:
            target, workspace = self.prepared(Path(temp))
            before = self.snapshot(target)
            result = self.export(workspace)
            self.assertEqual(0, result.returncode, result.stderr)
            summary = json.loads(result.stdout)
            self.assertEqual("no-changes", summary["status"])
            self.assertEqual(0, summary["changedFileCount"])
            self.assertFalse((workspace / "exports").exists())
            self.assertEqual(before, self.snapshot(target))

    def test_absent_source_overlays_are_canonicalized_only_when_an_export_has_changes(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-absent-export-") as temp:
            target, workspace = self.prepared(Path(temp), overlays=False)
            working = workspace / "working"
            self.write_archive(working / AUTHORED[0], 31)
            (working / AUTHORED[1]).write_bytes((working / AUTHORED[0]).read_bytes())
            result = self.export(workspace)
            self.assertEqual(0, result.returncode, result.stderr)
            export_dir = Path(json.loads(result.stdout)["exportDirectory"])
            for relative, expected in EMPTY_OVERLAYS.items():
                canonical = export_dir / "authored" / Path(relative).name
                parsed = json.loads(canonical.read_text(encoding="utf-8"))
                root_name = next(iter(json.loads(expected)))
                self.assertEqual({root_name: []}, parsed)
            manifest = json.loads((export_dir / "manifest.json").read_text(encoding="utf-8"))
            for file_record in manifest["files"][1:]:
                self.assertFalse(file_record["sourcePresent"])
                self.assertEqual("", file_record["sourceSha256"])
                self.assertFalse(file_record["changed"])
            self.assertFalse(any((workspace / "source" / relative).exists() for relative in AUTHORED[2:]))
            self.assertFalse(any((working / relative).exists() for relative in AUTHORED[2:]))

    def test_malformed_overlay_and_active_project_are_refused_without_publication(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-export-refusal-") as temp:
            target, workspace = self.prepared(Path(temp))
            working = workspace / "working"
            overlay = working / AUTHORED[2]
            overlay.write_text('{"sceneries":[],"sceneries":[]}\n', encoding="utf-8")
            malformed = self.export(workspace)
            self.assertEqual(3, malformed.returncode)
            self.assertIn("Duplicate object key", malformed.stderr)
            self.assertFalse((workspace / "exports").exists())

            overlay.write_text(EMPTY_OVERLAYS[AUTHORED[2]], encoding="utf-8")
            lock_path = workspace.parent / f".{workspace.name}.world-builder.lock"
            with lock_path.open("w") as lock_file:
                fcntl.lockf(lock_file, fcntl.LOCK_EX | fcntl.LOCK_NB)
                active = self.export(workspace)
                self.assertEqual(3, active.returncode)
                self.assertIn("Close World Builder", active.stderr)
            self.assertFalse((workspace / "exports").exists())

            source_manifest = workspace / "source/project-source.json"
            source_manifest.write_text("{}\n", encoding="utf-8")
            drifted = self.export(workspace)
            self.assertEqual(3, drifted.returncode)
            self.assertIn("source snapshot changed", drifted.stderr)
            self.assertFalse((workspace / "exports").exists())

    def test_mismatched_working_terrain_is_refused_without_publication(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-terrain-refusal-") as temp:
            target, workspace = self.prepared(Path(temp))
            working = workspace / "working"
            self.write_archive(working / AUTHORED[0], 41)

            mismatched = self.export(workspace)

            self.assertEqual(3, mismatched.returncode)
            self.assertIn("not byte-identical", mismatched.stderr)
            self.assertFalse((workspace / "exports").exists())


if __name__ == "__main__":
    unittest.main()
