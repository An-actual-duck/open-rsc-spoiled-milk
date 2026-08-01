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


class WorldBuilderRuntimePreparationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(prefix="world-builder-prepare-classes-")
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

    def make_layout(self, root: Path, terrain_seed: int, overlays: bool):
        config = root / "server/myworld.conf"
        config.parent.mkdir(parents=True, exist_ok=True)
        config.write_text(
            "\n".join(
                (
                    "server_name: Fixture # preserved comment",
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
        self.write_archive(root / AUTHORED[0], terrain_seed)
        (root / AUTHORED[1]).parent.mkdir(parents=True, exist_ok=True)
        (root / AUTHORED[1]).write_bytes((root / AUTHORED[0]).read_bytes())
        if overlays:
            for relative in AUTHORED[2:]:
                path = root / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('{"fixture":[]}\n', encoding="utf-8")
        else:
            (root / AUTHORED[2]).parent.mkdir(parents=True, exist_ok=True)

        for index, relative in enumerate(CONTENT):
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(f"compatible-definition-{index}\n".encode())

    def make_runtime(self, root: Path):
        self.make_layout(root, terrain_seed=3, overlays=True)
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
            "Client_Base/Cache/credentials.txt": b"must-not-copy",
            "Client_Base/Cache/uid.dat": b"must-not-copy",
            "Client_Base/Cache/ip.txt": b"remote.example",
            "Client_Base/Cache/port.txt": b"43594",
            "Client_Base/Cache/discord_inuse.txt": b"state",
        }.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)

    @staticmethod
    def snapshot(root: Path):
        result = {}
        for path in sorted(root.rglob("*")):
            relative = path.relative_to(root).as_posix()
            if path.is_dir():
                result[relative] = ("dir",)
            elif path.is_symlink():
                result[relative] = ("link", os.readlink(path))
            else:
                result[relative] = (
                    "file",
                    path.stat().st_size,
                    hashlib.sha256(path.read_bytes()).hexdigest(),
                )
        return result

    def run_prepare(self, target: Path, runtime: Path, workspace: Path):
        return subprocess.run(
            [
                "java",
                "-cp",
                str(self.classes),
                MAIN_CLASS,
                "prepare",
                "--server-root",
                str(target),
                "--runtime-root",
                str(runtime),
                "--workspace",
                str(workspace),
                "--port",
                "43615",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )

    def test_prepare_is_isolated_verified_and_secret_free(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-prepare-") as temp:
            base = Path(temp)
            target = base / "private-server"
            runtime = base / "release"
            workspace = base / "projects/My World"
            self.make_layout(target, terrain_seed=19, overlays=False)
            self.make_runtime(runtime)
            before = self.snapshot(target)

            result = self.run_prepare(target, runtime, workspace)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(before, self.snapshot(target))
            summary = json.loads(result.stdout)
            self.assertEqual("prepared", summary["status"])
            self.assertEqual(43615, summary["port"])

            working = workspace / "working"
            source = workspace / "source"
            self.assertEqual((target / AUTHORED[0]).read_bytes(), (working / AUTHORED[0]).read_bytes())
            self.assertEqual((target / AUTHORED[1]).read_bytes(), (working / AUTHORED[1]).read_bytes())
            self.assertEqual((target / AUTHORED[0]).read_bytes(), (source / AUTHORED[0]).read_bytes())
            self.assertEqual((target / AUTHORED[1]).read_bytes(), (source / AUTHORED[1]).read_bytes())
            for relative in AUTHORED[2:]:
                self.assertFalse((working / relative).exists())
                self.assertFalse((source / relative).exists())
            self.assertEqual(
                b"clean-seed-database",
                (working / "server/inc/sqlite/world_builder.db").read_bytes(),
            )
            self.assertFalse((working / "server/inc/sqlite/world-builder.credential").exists())
            for generated in ("credentials.txt", "uid.dat", "ip.txt", "port.txt", "discord_inuse.txt"):
                self.assertFalse((working / "Client_Base/Cache" / generated).exists())

            config = (working / "server/world-builder.conf").read_text(encoding="utf-8")
            for expected in (
                "world_builder_mode: true",
                "server_bind_address: 127.0.0.1",
                "server_port: 43615",
                "db_name: world_builder",
                "max_players: 1",
                "want_packet_register: false",
                "allow_in_game_world_editor: true",
                "want_feature_websockets: false",
            ):
                self.assertIn(expected, config)
            self.assertIn("server_name: Spoiled Milk World Builder # preserved comment", config)
            self.assertEqual(
                "db_type: sqlite\n",
                (working / "server/connections.conf").read_text(encoding="utf-8"),
            )
            self.assertEqual(
                (target / "server/myworld.conf").read_bytes(),
                (source / "server/myworld.conf").read_bytes(),
            )
            inventory = (workspace / "source-snapshot.sha256").read_text(encoding="utf-8")
            self.assertTrue(inventory.startswith("world-builder-source-v1\n"))
            self.assertIn("\tserver/myworld.conf\n", inventory)
            self.assertIn("\tproject-source.json\n", inventory)
            self.assertEqual(
                (workspace / "project-source.json").read_bytes(),
                (source / "project-source.json").read_bytes(),
            )

            source_before = self.snapshot(source)
            (working / AUTHORED[0]).write_bytes(b"working-only-change")
            generated_overlay = working / AUTHORED[2]
            generated_overlay.parent.mkdir(parents=True, exist_ok=True)
            generated_overlay.write_text('{"sceneries":[]}\n', encoding="utf-8")
            self.assertEqual(before, self.snapshot(target))
            self.assertEqual(source_before, self.snapshot(source))

    def test_first_prepare_does_not_require_or_import_ip_ban_state(self):
        for runtime_bans in (None, b"198.51.100.12\n"):
            with self.subTest(stale_runtime_bans=runtime_bans is not None):
                with tempfile.TemporaryDirectory(
                    prefix="world-builder-ipbans-isolation-"
                ) as temp:
                    base = Path(temp)
                    target = base / "private-server"
                    runtime = base / "release"
                    workspace = base / "projects/First World"
                    self.make_layout(target, terrain_seed=23, overlays=True)
                    self.make_runtime(runtime)
                    target_bans = target / "server/ipbans.txt"
                    target_bans.write_bytes(b"203.0.113.7\n")
                    runtime_bans_path = runtime / "server/ipbans.txt"
                    if runtime_bans is not None:
                        runtime_bans_path.write_bytes(runtime_bans)
                    target_before = self.snapshot(target)
                    runtime_before = self.snapshot(runtime)

                    result = self.run_prepare(target, runtime, workspace)

                    self.assertEqual(0, result.returncode, result.stderr)
                    self.assertEqual(target_before, self.snapshot(target))
                    self.assertEqual(runtime_before, self.snapshot(runtime))
                    self.assertFalse(
                        (workspace / "working/server/ipbans.txt").exists()
                    )
                    self.assertFalse((workspace / "source/server/ipbans.txt").exists())

    def test_changed_or_extended_source_snapshot_refuses_launch(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-source-guard-") as temp:
            base = Path(temp)
            target = base / "target"
            runtime = base / "runtime"
            workspace = base / "workspace"
            self.make_layout(target, terrain_seed=11, overlays=True)
            self.make_runtime(runtime)
            before = self.snapshot(target)
            prepared = self.run_prepare(target, runtime, workspace)
            self.assertEqual(0, prepared.returncode, prepared.stderr)

            source_terrain = workspace / "source" / AUTHORED[0]
            source_terrain.write_bytes(source_terrain.read_bytes() + b"changed")
            result = subprocess.run(
                [
                    "java", "-cp", str(self.classes), MAIN_CLASS, "run",
                    "--workspace", str(workspace), "--port", "43615",
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
                timeout=10,
            )
            self.assertEqual(3, result.returncode)
            self.assertIn("source snapshot changed", result.stderr)
            self.assertEqual(before, self.snapshot(target))
            self.assertFalse((workspace / "run/server.pid").exists())

    def test_existing_workspace_is_never_replaced(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-existing-") as temp:
            base = Path(temp)
            target = base / "target"
            runtime = base / "runtime"
            workspace = base / "workspace"
            self.make_layout(target, 8, overlays=True)
            self.make_runtime(runtime)
            workspace.mkdir()
            sentinel = workspace / "keep.txt"
            sentinel.write_text("keep", encoding="utf-8")

            result = self.run_prepare(target, runtime, workspace)
            self.assertEqual(3, result.returncode)
            self.assertIn("never replaced implicitly", result.stderr)
            self.assertEqual("keep", sentinel.read_text(encoding="utf-8"))

    def test_incompatible_content_refuses_without_workspace_or_target_writes(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-content-refusal-") as temp:
            base = Path(temp)
            target = base / "target"
            runtime = base / "runtime"
            workspace = base / "workspace"
            self.make_layout(target, 8, overlays=True)
            self.make_runtime(runtime)
            (target / CONTENT[2]).write_text("incompatible\n", encoding="utf-8")
            before = self.snapshot(target)

            result = self.run_prepare(target, runtime, workspace)
            self.assertEqual(3, result.returncode)
            self.assertIn("do not match this World Builder release", result.stderr)
            self.assertFalse(workspace.exists())
            self.assertEqual(before, self.snapshot(target))


if __name__ == "__main__":
    unittest.main()
