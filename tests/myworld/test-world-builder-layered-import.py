#!/usr/bin/env python3
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
COMMIT = "c" * 40
PACKAGE_ROOT = Path(
    os.environ.get(
        "SPOILED_MILK_LAYERED_PACKAGE",
        ROOT / "tools/layered-maps/workspace/spoiled-milk-package/package",
    )
)
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
TARGET_PACKAGE = "server/conf/server/data/world-builder-layered/package"
CAPABILITY_MARKER = "server/world-builder-layered-import-v1.marker"


class WorldBuilderLayeredImportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if not (PACKAGE_ROOT / "manifest.json").is_file():
            raise AssertionError(
                "Generate the accepted package or set SPOILED_MILK_LAYERED_PACKAGE"
            )
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="world-builder-layered-import-classes-"
        )
        cls.classes = Path(cls.compile_temp.name)
        sources = sorted(str(path) for path in SOURCE_ROOT.rglob("*.java"))
        result = subprocess.run(
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
            text=True,
            capture_output=True,
        )
        if result.returncode:
            raise AssertionError(result.stdout + result.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    @staticmethod
    def write_archive(path: Path, seed: int):
        path.parent.mkdir(parents=True, exist_ok=True)
        raw = bytes((seed + index * 7) & 0xFF for index in range(48 * 48 * 10))
        with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("h0x48y37", raw)

    def make_layout(self, root: Path, seed: int, port: int):
        config = root / "server/myworld.conf"
        config.parent.mkdir(parents=True, exist_ok=True)
        config.write_text(
            "\n".join(
                (
                    "server_name: Layered fixture",
                    "server_bind_address: 127.0.0.1",
                    f"server_port: {port}",
                    f"ws_server_port: {port + 1}",
                    "max_players: 100",
                    "client_version: 10047",
                    "member_world: true",
                    "based_map_data: 64",
                    "want_myworld: true",
                    "custom_landscape: true",
                    "want_packet_register: true",
                    "want_sync_scene_baseline: false # must be transactionally changed",
                    "",
                )
            ),
            encoding="utf-8",
        )
        self.write_archive(root / AUTHORED[0], seed)
        (root / AUTHORED[1]).parent.mkdir(parents=True, exist_ok=True)
        (root / AUTHORED[1]).write_bytes((root / AUTHORED[0]).read_bytes())
        empty = (
            '{"sceneries": []}\n',
            '{"scenery_removals": []}\n',
            '{"npclocs": []}\n',
            '{"npc_removals": []}\n',
        )
        for relative, content in zip(AUTHORED[2:], empty):
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
        for index, relative in enumerate(CONTENT):
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(f"compatible-definition-{index}\n".encode())

    def make_runtime(self, root: Path):
        self.make_layout(root, 5, 43595)
        for relative, data in {
            "server/core.jar": b"core",
            "server/plugins.jar": b"plugins",
            "server/alertwords.txt": b"\n",
            "server/badwords.txt": b"\n",
            "server/goodwords.txt": b"\n",
            "server/globalrules.txt": b"rules\n",
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
        state = {}
        if not root.exists():
            return state
        for path in sorted(root.rglob("*")):
            relative = path.relative_to(root).as_posix()
            if path.is_symlink():
                state[relative] = ("link", os.readlink(path))
            elif path.is_dir():
                state[relative] = ("dir",)
            else:
                state[relative] = (
                    "file",
                    path.stat().st_size,
                    hashlib.sha256(path.read_bytes()).hexdigest(),
                )
        return state

    def run_cli(self, *args, user_input=None, property_name=None, property_value=None):
        command = ["java"]
        if property_name is not None:
            command.append(f"-D{property_name}={property_value}")
        command.extend(["-cp", str(self.classes), MAIN_CLASS, *map(str, args)])
        return subprocess.run(
            command,
            cwd=ROOT,
            text=True,
            input=user_input,
            capture_output=True,
            timeout=180,
        )

    def prepare_export(self, base: Path):
        target = base / "target"
        runtime = base / "runtime"
        workspace = base / "workspace"
        self.make_layout(target, 17, 43594)
        self.make_runtime(runtime)
        marker = target / CAPABILITY_MARKER
        marker.parent.mkdir(parents=True, exist_ok=True)
        marker.write_bytes((ROOT / CAPABILITY_MARKER).read_bytes())
        prepared = self.run_cli(
            "prepare",
            "--server-root",
            target,
            "--runtime-root",
            runtime,
            "--workspace",
            workspace,
            "--port",
            "43615",
            "--layered-package",
            PACKAGE_ROOT,
            "--layered-profile",
            "spoiled-milk-replacement",
        )
        self.assertEqual(0, prepared.returncode, prepared.stdout + prepared.stderr)
        created = self.run_cli(
            "create-level",
            "--workspace",
            workspace,
            "--level",
            "-3",
            "--anchor-x",
            "140",
            "--anchor-y",
            "640",
        )
        self.assertEqual(0, created.returncode, created.stdout + created.stderr)
        exported = self.run_cli(
            "export",
            "--workspace",
            workspace,
            "--builder-version",
            "v2-test",
            "--source-commit",
            COMMIT,
        )
        self.assertEqual(0, exported.returncode, exported.stdout + exported.stderr)
        return target, workspace, Path(json.loads(exported.stdout)["exportDirectory"])

    def import_command(self, workspace, export_dir, target, apply=False, **kwargs):
        return self.run_cli(
            "import",
            "--workspace",
            workspace,
            "--export",
            export_dir,
            "--target-root",
            target,
            "--apply" if apply else "--dry-run",
            **kwargs,
        )

    def undo_command(self, workspace, target, apply=False, **kwargs):
        return self.run_cli(
            "undo-import",
            "--workspace",
            workspace,
            "--target-root",
            target,
            "--apply" if apply else "--dry-run",
            **kwargs,
        )

    def test_preview_apply_undo_and_failure_recovery_are_fail_closed(self):
        with tempfile.TemporaryDirectory(
            prefix="world-builder-layered-import-fixture-"
        ) as temp:
            target, workspace, export_dir = self.prepare_export(Path(temp))
            target_before = self.snapshot(target)
            protected_before = {
                "source": self.snapshot(workspace / "source"),
                "working": self.snapshot(workspace / "working"),
            }

            preview_result = self.import_command(workspace, export_dir, target)
            self.assertEqual(
                0, preview_result.returncode, preview_result.stdout + preview_result.stderr
            )
            preview = json.loads(preview_result.stdout)
            self.assertEqual("layered-package-v1", preview["importMode"])
            self.assertEqual("ready", preview["status"])
            self.assertEqual(target.resolve(), Path(preview["targetRoot"]))
            self.assertEqual(
                [
                    "want_sync_scene_baseline",
                    "want_layered_player_location_authority",
                    "want_layered_spatial_runtime_authority",
                    "want_layered_protocol_client_authority",
                    "want_layered_synthetic_deep_fixture",
                    "want_layered_native_terrain_package",
                    "want_layered_native_terrain_residency",
                    "want_layered_native_terrain_readiness",
                    "want_layered_native_terrain_prediction",
                    "want_layered_native_terrain_symmetric_residency",
                    "want_layered_native_terrain_atomic_activation",
                    "layered_native_terrain_package_path",
                    "layered_native_terrain_manifest_sha256",
                    "layered_native_world_runtime_profile",
                ],
                [change["key"] for change in preview["configurationChanges"]],
            )
            self.assertGreater(len(preview["actions"]), 1782)
            self.assertTrue(
                all(
                    action["operation"] == "add"
                    and action["relativePath"].startswith(TARGET_PACKAGE + "/")
                    for action in preview["actions"][:-1]
                )
            )
            self.assertEqual("server/myworld.conf", preview["actions"][-1]["relativePath"])
            self.assertEqual("replace", preview["actions"][-1]["operation"])
            self.assertEqual(target_before, self.snapshot(target))
            self.assertEqual(protected_before["source"], self.snapshot(workspace / "source"))
            self.assertEqual(protected_before["working"], self.snapshot(workspace / "working"))
            self.assertFalse((workspace / "receipts").exists())
            self.assertFalse((workspace / "backups").exists())

            cancelled = self.run_cli(
                "export-import",
                "--workspace",
                workspace,
                "--target-root",
                target,
                "--builder-version",
                "v2-test",
                "--source-commit",
                COMMIT,
                user_input="not IMPORT\n",
            )
            self.assertEqual(0, cancelled.returncode, cancelled.stdout + cancelled.stderr)
            self.assertIn("Import cancelled", cancelled.stdout)
            self.assertEqual(target_before, self.snapshot(target))

            marker = target / CAPABILITY_MARKER
            marker_bytes = marker.read_bytes()
            marker.unlink()
            without_capability = self.snapshot(target)
            refused_capability = self.import_command(workspace, export_dir, target)
            self.assertEqual(3, refused_capability.returncode)
            self.assertIn("matching Spoiled Milk private-server release", refused_capability.stderr)
            self.assertEqual(without_capability, self.snapshot(target))
            marker.write_bytes(marker_bytes)
            self.assertEqual(target_before, self.snapshot(target))

            foreign = target / TARGET_PACKAGE / "foreign.txt"
            foreign.parent.mkdir(parents=True)
            foreign.write_text("not owned by World Builder\n", encoding="utf-8")
            foreign_snapshot = self.snapshot(target)
            refused_overwrite = self.import_command(workspace, export_dir, target)
            self.assertEqual(3, refused_overwrite.returncode)
            self.assertIn("already exists", refused_overwrite.stderr)
            self.assertEqual(foreign_snapshot, self.snapshot(target))
            foreign.unlink()
            for directory in (
                foreign.parent,
                foreign.parent.parent,
            ):
                directory.rmdir()
            self.assertEqual(target_before, self.snapshot(target))

            failed_import = self.import_command(
                workspace,
                export_dir,
                target,
                apply=True,
                property_name="worldbuilder.import.failAfterReplacements",
                property_value=str(len(preview["actions"])),
            )
            self.assertEqual(4, failed_import.returncode)
            self.assertIn("Injected import failure", failed_import.stderr)
            self.assertEqual(target_before, self.snapshot(target))
            self.assertFalse((target / TARGET_PACKAGE).exists())
            self.assertEqual(protected_before["source"], self.snapshot(workspace / "source"))
            self.assertEqual(protected_before["working"], self.snapshot(workspace / "working"))

            applied = self.run_cli(
                "export-import",
                "--workspace",
                workspace,
                "--target-root",
                target,
                "--builder-version",
                "v2-test",
                "--source-commit",
                COMMIT,
                user_input="IMPORT\n",
            )
            self.assertEqual(0, applied.returncode, applied.stdout + applied.stderr)
            self.assertIn('"status": "imported"', applied.stdout)
            installed_snapshot = self.snapshot(target)
            export_manifest = json.loads((export_dir / "manifest.json").read_text())
            installed_manifest = target / TARGET_PACKAGE / "manifest.json"
            self.assertEqual(
                export_manifest["layeredPackageManifestSha256"],
                hashlib.sha256(installed_manifest.read_bytes()).hexdigest(),
            )
            config_text = (target / "server/myworld.conf").read_text(encoding="utf-8")
            self.assertIn(
                "layered_native_world_runtime_profile: spoiled-milk-world-builder-export",
                config_text,
            )
            self.assertIn(
                "layered_native_terrain_manifest_sha256: "
                + export_manifest["layeredPackageManifestSha256"],
                config_text,
            )
            successful_receipts = []
            for path in (workspace / "receipts").glob("*.json"):
                candidate = json.loads(path.read_text(encoding="utf-8"))
                if (
                    candidate["transactionType"] == "import"
                    and candidate["status"] == "successful"
                ):
                    successful_receipts.append(candidate)
            self.assertEqual(1, len(successful_receipts))
            receipt = successful_receipts[0]
            self.assertEqual(2, receipt["schemaVersion"])
            self.assertEqual("layered-package-v1", receipt["importMode"])
            self.assertEqual("successful", receipt["status"])
            self.assertEqual(len(preview["actions"]), len(receipt["files"]))
            config_record = next(
                item for item in receipt["files"] if item["relativePath"] == "server/myworld.conf"
            )
            self.assertEqual(
                (target_before["server/myworld.conf"][2]), config_record["beforeSha256"]
            )
            self.assertEqual(protected_before["source"], self.snapshot(workspace / "source"))
            self.assertEqual(protected_before["working"], self.snapshot(workspace / "working"))

            drift_file = next(
                path
                for path in (target / TARGET_PACKAGE).rglob("*.raw")
                if path.is_file()
            )
            drift_before = drift_file.read_bytes()
            drift_file.write_bytes(bytes([drift_before[0] ^ 1]) + drift_before[1:])
            drifted_snapshot = self.snapshot(target)
            refused_undo = self.undo_command(workspace, target)
            self.assertEqual(3, refused_undo.returncode)
            self.assertIn("changed during import", refused_undo.stderr)
            self.assertEqual(drifted_snapshot, self.snapshot(target))
            drift_file.write_bytes(drift_before)
            self.assertEqual(installed_snapshot, self.snapshot(target))

            cancelled_undo = self.run_cli(
                "undo-latest-import",
                "--workspace",
                workspace,
                "--target-root",
                target,
                user_input="not UNDO\n",
            )
            self.assertEqual(
                0, cancelled_undo.returncode, cancelled_undo.stdout + cancelled_undo.stderr
            )
            self.assertIn("Undo cancelled", cancelled_undo.stdout)
            self.assertEqual(installed_snapshot, self.snapshot(target))

            undo_preview = self.undo_command(workspace, target)
            self.assertEqual(0, undo_preview.returncode, undo_preview.stdout + undo_preview.stderr)
            undo_plan = json.loads(undo_preview.stdout)
            self.assertEqual("restore", undo_plan["actions"][0]["operation"])
            self.assertEqual("server/myworld.conf", undo_plan["actions"][0]["relativePath"])
            self.assertTrue(
                all(action["operation"] == "remove" for action in undo_plan["actions"][1:])
            )
            self.assertEqual(installed_snapshot, self.snapshot(target))

            failed_undo = self.undo_command(
                workspace,
                target,
                apply=True,
                property_name="worldbuilder.rollback.failAfterReplacements",
                property_value="2",
            )
            self.assertEqual(4, failed_undo.returncode)
            self.assertIn("Injected rollback failure", failed_undo.stderr)
            self.assertEqual(installed_snapshot, self.snapshot(target))
            self.assertEqual(protected_before["source"], self.snapshot(workspace / "source"))
            self.assertEqual(protected_before["working"], self.snapshot(workspace / "working"))

            undone = self.run_cli(
                "undo-latest-import",
                "--workspace",
                workspace,
                "--target-root",
                target,
                user_input="UNDO\n",
            )
            self.assertEqual(0, undone.returncode, undone.stdout + undone.stderr)
            self.assertIn('"status": "rolled-back"', undone.stdout)
            self.assertEqual(target_before, self.snapshot(target))
            self.assertFalse((target / TARGET_PACKAGE).exists())
            self.assertEqual(protected_before["source"], self.snapshot(workspace / "source"))
            self.assertEqual(protected_before["working"], self.snapshot(workspace / "working"))

            source_raw = next((workspace / "source/layered-world/package").rglob("*.raw"))
            source_bytes = source_raw.read_bytes()
            source_raw.write_bytes(bytes([source_bytes[0] ^ 1]) + source_bytes[1:])
            target_after_undo = self.snapshot(target)
            refused_source = self.import_command(workspace, export_dir, target)
            self.assertEqual(3, refused_source.returncode)
            self.assertIn("source snapshot changed", refused_source.stderr)
            self.assertEqual(target_after_undo, self.snapshot(target))


if __name__ == "__main__":
    unittest.main()
