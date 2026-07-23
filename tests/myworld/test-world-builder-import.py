#!/usr/bin/env python3
import hashlib
import json
import socket
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SOURCE_ROOT = ROOT / "tools/world-builder/src"
MAIN_CLASS = "com.openrsc.worldbuilder.WorldBuilderCli"
COMMIT = "b" * 40
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


class WorldBuilderImportTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="world-builder-import-classes-")
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

    def make_layout(self, root: Path, seed: int, overlays=True, port=43594):
        config = root / "server/myworld.conf"
        config.parent.mkdir(parents=True, exist_ok=True)
        config.write_text(
            "\n".join(
                (
                    "server_name: Fixture",
                    "server_bind_address: 0.0.0.0",
                    f"server_port: {port}",
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
        self.make_layout(root, 3, port=43595)
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
                result[path.relative_to(root).as_posix()] = (
                    path.stat().st_size,
                    path.stat().st_mtime_ns,
                    hashlib.sha256(path.read_bytes()).hexdigest(),
                )
        return result

    def run_cli(self, *args):
        return subprocess.run(
            ["java", "-cp", str(self.classes), MAIN_CLASS, *map(str, args)],
            cwd=ROOT,
            text=True,
            capture_output=True,
            timeout=20,
        )

    def run_cli_input(self, user_input: str, *args):
        return subprocess.run(
            ["java", "-cp", str(self.classes), MAIN_CLASS, *map(str, args)],
            cwd=ROOT,
            text=True,
            input=user_input,
            capture_output=True,
            timeout=20,
        )

    def run_cli_with_property(self, property_value, *args):
        return subprocess.run(
            [
                "java",
                f"-Dworldbuilder.import.failAfterReplacements={property_value}",
                "-cp",
                str(self.classes),
                MAIN_CLASS,
                *map(str, args),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
            timeout=20,
        )

    def run_cli_with_rollback_property(self, property_value, *args):
        return subprocess.run(
            [
                "java",
                f"-Dworldbuilder.rollback.failAfterReplacements={property_value}",
                "-cp",
                str(self.classes),
                MAIN_CLASS,
                *map(str, args),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
            timeout=20,
        )

    def prepared_export(self, base: Path, overlays=True, add_scenery=None):
        target, runtime, workspace = base / "target", base / "runtime", base / "project"
        self.make_layout(target, 11, overlays=overlays)
        self.make_runtime(runtime)
        prepared = self.run_cli(
            "prepare", "--server-root", target, "--runtime-root", runtime,
            "--workspace", workspace, "--port", "43615",
        )
        self.assertEqual(0, prepared.returncode, prepared.stderr)
        working = workspace / "working"
        self.write_archive(working / AUTHORED[0], 27)
        (working / AUTHORED[1]).write_bytes((working / AUTHORED[0]).read_bytes())
        if add_scenery is None:
            add_scenery = overlays
        if add_scenery:
            (working / AUTHORED[2]).write_text(
                '{"sceneries":[{"id":10,"pos":{"X":100,"Y":200},"direction":2}]}\n',
                encoding="utf-8",
            )
        exported = self.run_cli(
            "export", "--workspace", workspace, "--builder-version", "v-test",
            "--source-commit", COMMIT,
        )
        self.assertEqual(0, exported.returncode, exported.stderr)
        export_dir = Path(json.loads(exported.stdout)["exportDirectory"])
        return target, workspace, export_dir

    def preview(self, workspace: Path, export_dir: Path, target: Path):
        return self.run_cli(
            "import", "--workspace", workspace, "--export", export_dir,
            "--target-root", target, "--dry-run",
        )

    def apply(self, workspace: Path, export_dir: Path, target: Path):
        return self.run_cli(
            "import", "--workspace", workspace, "--export", export_dir,
            "--target-root", target, "--apply",
        )

    def undo(self, workspace: Path, target: Path, apply=False):
        return self.run_cli(
            "undo-import", "--workspace", workspace, "--target-root", target,
            "--apply" if apply else "--dry-run",
        )

    def test_dry_run_reports_exact_actions_without_target_writes(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-import-preview-") as temp:
            target, workspace, export_dir = self.prepared_export(Path(temp))
            before = self.snapshot(target)

            result = self.preview(workspace, export_dir, target)

            self.assertEqual(0, result.returncode, result.stderr)
            preview = json.loads(result.stdout)
            self.assertEqual("ready", preview["status"])
            self.assertTrue(preview["dryRun"])
            self.assertEqual([], preview["configurationChanges"])
            self.assertEqual(
                [
                    "server/conf/server/data/Custom_Landscape.orsc",
                    "Client_Base/Cache/video/Custom_Landscape.orsc",
                    "server/conf/server/defs/locs/MyWorldSceneryLocs.json",
                ],
                [action["relativePath"] for action in preview["actions"]],
            )
            self.assertTrue(all(action["operation"] == "replace" for action in preview["actions"]))
            self.assertEqual(before, self.snapshot(target))
            self.assertFalse((workspace / "backups").exists())
            self.assertFalse((workspace / "receipts").exists())

    def test_absent_unchanged_overlays_are_not_planned_as_additions(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-import-absent-") as temp:
            target, workspace, export_dir = self.prepared_export(Path(temp), overlays=False)
            result = self.preview(workspace, export_dir, target)
            self.assertEqual(0, result.returncode, result.stderr)
            preview = json.loads(result.stdout)
            self.assertEqual(2, len(preview["actions"]))
            self.assertTrue(all("Landscape" in action["relativePath"] for action in preview["actions"]))
            self.assertFalse(any((target / relative).exists() for relative in AUTHORED[2:]))

    def test_target_revision_drift_is_refused(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-import-drift-") as temp:
            target, workspace, export_dir = self.prepared_export(Path(temp))
            (target / AUTHORED[3]).write_text(
                '{"scenery_removals":[{"pos":{"X":1,"Y":2}}]}\n', encoding="utf-8"
            )
            changed = self.snapshot(target)
            result = self.preview(workspace, export_dir, target)
            self.assertEqual(3, result.returncode)
            self.assertIn("changed since this Builder project was created", result.stderr)
            self.assertEqual(changed, self.snapshot(target))

    def test_occupied_target_port_is_refused(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-import-running-") as temp:
            target, workspace, export_dir = self.prepared_export(Path(temp))
            before = self.snapshot(target)
            with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
                listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 0)
                listener.bind(("127.0.0.1", 43594))
                listener.listen(1)
                result = self.preview(workspace, export_dir, target)
            self.assertEqual(3, result.returncode)
            self.assertIn("offline state is uncertain", result.stderr)
            self.assertEqual(before, self.snapshot(target))

    def test_apply_installs_exact_files_and_writes_verified_receipt_and_backups(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-import-apply-") as temp:
            target, workspace, export_dir = self.prepared_export(Path(temp))
            before_bytes = {
                relative: (target / relative).read_bytes() for relative in AUTHORED
            }
            config_before = (target / "server/myworld.conf").read_bytes()

            result = self.apply(workspace, export_dir, target)

            self.assertEqual(0, result.returncode, result.stderr)
            imported = json.loads(result.stdout)
            self.assertEqual("imported", imported["status"])
            self.assertEqual(3, imported["changedFileCount"])
            receipt_path = Path(imported["receiptPath"])
            receipt = json.loads(receipt_path.read_text(encoding="utf-8"))
            self.assertEqual("successful", receipt["status"])
            self.assertEqual("import", receipt["transactionType"])
            self.assertEqual(3, len(receipt["files"]))
            manifest = json.loads((export_dir / "manifest.json").read_text(encoding="utf-8"))
            exported = {record["logicalName"]: record for record in manifest["files"]}
            self.assertEqual(
                exported["terrain"]["sha256"],
                hashlib.sha256((target / AUTHORED[0]).read_bytes()).hexdigest(),
            )
            self.assertEqual((target / AUTHORED[0]).read_bytes(), (target / AUTHORED[1]).read_bytes())
            self.assertEqual(
                exported["sceneryLocs"]["sha256"],
                hashlib.sha256((target / AUTHORED[2]).read_bytes()).hexdigest(),
            )
            for relative in (AUTHORED[0], AUTHORED[1], AUTHORED[2]):
                backup = (
                    workspace
                    / "backups"
                    / receipt["transactionId"]
                    / "before"
                    / relative
                )
                self.assertEqual(before_bytes[relative], backup.read_bytes())
            for relative in AUTHORED[3:]:
                self.assertEqual(before_bytes[relative], (target / relative).read_bytes())
            self.assertEqual(config_before, (target / "server/myworld.conf").read_bytes())
            self.assertFalse(any(target.glob(".world-builder-import-staging-*")))

    def test_human_workflows_preview_cancel_apply_and_undo(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-human-workflow-") as temp:
            target, workspace, export_dir = self.prepared_export(Path(temp))
            before = self.snapshot(target)
            before_bytes = {
                path.relative_to(target).as_posix(): path.read_bytes()
                for path in target.rglob("*") if path.is_file()
            }
            command = (
                "export-import", "--workspace", workspace,
                "--target-root", target, "--builder-version", "v-test",
                "--source-commit", COMMIT,
            )

            cancelled = self.run_cli_input("\n", *command)
            self.assertEqual(0, cancelled.returncode, cancelled.stderr)
            self.assertIn('"status": "ready"', cancelled.stdout)
            self.assertIn("Import cancelled", cancelled.stdout)
            self.assertEqual(before, self.snapshot(target))

            imported = self.run_cli_input("IMPORT\n", *command)
            self.assertEqual(0, imported.returncode, imported.stderr)
            self.assertIn('"status": "imported"', imported.stdout)
            installed = self.snapshot(target)
            self.assertNotEqual(before, installed)

            undo_command = (
                "undo-latest-import", "--workspace", workspace,
                "--target-root", target,
            )
            undo_cancelled = self.run_cli_input("\n", *undo_command)
            self.assertEqual(0, undo_cancelled.returncode, undo_cancelled.stderr)
            self.assertIn("Undo cancelled", undo_cancelled.stdout)
            self.assertEqual(installed, self.snapshot(target))

            undone = self.run_cli_input("UNDO\n", *undo_command)
            self.assertEqual(0, undone.returncode, undone.stderr)
            self.assertIn('"status": "rolled-back"', undone.stdout)
            after_undo_bytes = {
                path.relative_to(target).as_posix(): path.read_bytes()
                for path in target.rglob("*") if path.is_file()
            }
            self.assertEqual(before_bytes, after_undo_bytes)

    def test_injected_partial_failure_restores_every_target_byte(self):
        for fail_after in (1, 2, 3):
            with self.subTest(fail_after=fail_after), tempfile.TemporaryDirectory(
                prefix="world-builder-import-rollback-"
            ) as temp:
                target, workspace, export_dir = self.prepared_export(Path(temp))
                before = {
                    path.relative_to(target).as_posix(): path.read_bytes()
                    for path in target.rglob("*")
                    if path.is_file()
                }
                result = self.run_cli_with_property(
                    str(fail_after),
                    "import", "--workspace", workspace, "--export", export_dir,
                    "--target-root", target, "--apply",
                )
                self.assertEqual(4, result.returncode)
                self.assertIn("Injected import failure", result.stderr)
                after = {
                    path.relative_to(target).as_posix(): path.read_bytes()
                    for path in target.rglob("*")
                    if path.is_file()
                }
                self.assertEqual(before, after)
                receipts = list((workspace / "receipts").glob("*.json"))
                self.assertEqual(1, len(receipts))
                self.assertEqual(
                    "rolled-back",
                    json.loads(receipts[0].read_text(encoding="utf-8"))["status"],
                )
                self.assertFalse(any(target.glob(".world-builder-import-staging-*")))

    def test_apply_can_add_an_overlay_that_was_absent_at_source(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-import-add-") as temp:
            target, workspace, export_dir = self.prepared_export(
                Path(temp), overlays=False, add_scenery=True
            )
            self.assertFalse((target / AUTHORED[2]).exists())
            result = self.apply(workspace, export_dir, target)
            self.assertEqual(0, result.returncode, result.stderr)
            receipt = json.loads(Path(json.loads(result.stdout)["receiptPath"]).read_text())
            record = next(
                item for item in receipt["files"] if item["relativePath"] == AUTHORED[2]
            )
            self.assertFalse(record["existedBefore"])
            self.assertEqual("", record["beforeSha256"])
            self.assertEqual("", record["backupRelativePath"])
            self.assertTrue((target / AUTHORED[2]).is_file())

    def test_failure_after_added_overlay_restores_its_absence(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-import-add-failure-") as temp:
            target, workspace, export_dir = self.prepared_export(
                Path(temp), overlays=False, add_scenery=True
            )
            result = self.run_cli_with_property(
                "3", "import", "--workspace", workspace, "--export", export_dir,
                "--target-root", target, "--apply",
            )
            self.assertEqual(4, result.returncode)
            self.assertFalse(any((target / relative).exists() for relative in AUTHORED[2:]))
            self.assertEqual((target / AUTHORED[0]).read_bytes(), (target / AUTHORED[1]).read_bytes())

    def test_undo_preview_and_apply_restore_exact_source_and_write_safeguard(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-import-undo-") as temp:
            target, workspace, export_dir = self.prepared_export(Path(temp))
            source = {
                path.relative_to(target).as_posix(): path.read_bytes()
                for path in target.rglob("*")
                if path.is_file()
            }
            imported = self.apply(workspace, export_dir, target)
            self.assertEqual(0, imported.returncode, imported.stderr)
            imported_state = {
                relative: (target / relative).read_bytes()
                for relative in (AUTHORED[0], AUTHORED[1], AUTHORED[2])
            }
            before_preview = self.snapshot(target)

            preview = self.undo(workspace, target)

            self.assertEqual(0, preview.returncode, preview.stderr)
            preview_json = json.loads(preview.stdout)
            self.assertEqual("ready", preview_json["status"])
            self.assertEqual(3, len(preview_json["actions"]))
            self.assertEqual(before_preview, self.snapshot(target))

            undone = self.undo(workspace, target, apply=True)
            self.assertEqual(0, undone.returncode, undone.stderr)
            undo_json = json.loads(undone.stdout)
            self.assertEqual("rolled-back", undo_json["status"])
            restored = {
                path.relative_to(target).as_posix(): path.read_bytes()
                for path in target.rglob("*")
                if path.is_file()
            }
            self.assertEqual(source, restored)
            receipts = [
                json.loads(path.read_text(encoding="utf-8"))
                for path in (workspace / "receipts").glob("*.json")
            ]
            original = next(item for item in receipts if item["transactionType"] == "import")
            rollback = next(item for item in receipts if item["transactionType"] == "rollback")
            self.assertEqual("reverted", original["status"])
            self.assertEqual("successful", rollback["status"])
            self.assertEqual(original["transactionId"], rollback["revertsTransactionId"])
            safeguard = Path(undo_json["safeguardDirectory"]) / "before"
            for relative, expected in imported_state.items():
                self.assertEqual(expected, (safeguard / relative).read_bytes())
            second = self.undo(workspace, target)
            self.assertEqual(3, second.returncode)
            self.assertIn("No successful unreverted import", second.stderr)

    def test_undo_refuses_files_changed_after_import(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-import-undo-drift-") as temp:
            target, workspace, export_dir = self.prepared_export(Path(temp))
            imported = self.apply(workspace, export_dir, target)
            self.assertEqual(0, imported.returncode, imported.stderr)
            (target / AUTHORED[2]).write_text(
                '{"sceneries":[{"id":11,"pos":{"X":101,"Y":201},"direction":3}]}\n',
                encoding="utf-8",
            )
            changed = (target / AUTHORED[2]).read_bytes()
            result = self.undo(workspace, target)
            self.assertEqual(3, result.returncode)
            self.assertIn("Target changed during import", result.stderr)
            self.assertEqual(changed, (target / AUTHORED[2]).read_bytes())

    def test_injected_undo_failure_restores_imported_state(self):
        for fail_after in (1, 2, 3):
            with self.subTest(fail_after=fail_after), tempfile.TemporaryDirectory(
                prefix="world-builder-import-undo-failure-"
            ) as temp:
                target, workspace, export_dir = self.prepared_export(Path(temp))
                imported = self.apply(workspace, export_dir, target)
                self.assertEqual(0, imported.returncode, imported.stderr)
                imported_state = {
                    path.relative_to(target).as_posix(): path.read_bytes()
                    for path in target.rglob("*")
                    if path.is_file()
                }
                result = self.run_cli_with_rollback_property(
                    str(fail_after), "undo-import", "--workspace", workspace,
                    "--target-root", target, "--apply",
                )
                self.assertEqual(4, result.returncode)
                self.assertIn("Injected rollback failure", result.stderr)
                after = {
                    path.relative_to(target).as_posix(): path.read_bytes()
                    for path in target.rglob("*")
                    if path.is_file()
                }
                self.assertEqual(imported_state, after)
                receipts = [
                    json.loads(path.read_text(encoding="utf-8"))
                    for path in (workspace / "receipts").glob("*.json")
                ]
                self.assertEqual(
                    ["successful"],
                    [item["status"] for item in receipts if item["transactionType"] == "import"],
                )
                self.assertEqual(
                    ["rolled-back"],
                    [item["status"] for item in receipts if item["transactionType"] == "rollback"],
                )

    def test_undo_removes_overlay_that_import_added(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-import-undo-add-") as temp:
            target, workspace, export_dir = self.prepared_export(
                Path(temp), overlays=False, add_scenery=True
            )
            imported = self.apply(workspace, export_dir, target)
            self.assertEqual(0, imported.returncode, imported.stderr)
            self.assertTrue((target / AUTHORED[2]).exists())
            undone = self.undo(workspace, target, apply=True)
            self.assertEqual(0, undone.returncode, undone.stderr)
            self.assertFalse((target / AUTHORED[2]).exists())
            self.assertFalse(any((target / relative).exists() for relative in AUTHORED[2:]))


if __name__ == "__main__":
    unittest.main()
