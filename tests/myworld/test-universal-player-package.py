#!/usr/bin/env python3
"""Exercise the real consolidation tool with private, offline release fixtures."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location(
    "universal_player_package", ROOT / "scripts/package-universal-player.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
release_spec = importlib.util.spec_from_file_location(
    "player_release_fixture", ROOT / "tests/myworld/test-player-release.py")
release_fixture = importlib.util.module_from_spec(release_spec)
release_spec.loader.exec_module(release_fixture)
VERSION = "v0.3.5"
COMMIT = "1" * 40


class UniversalPackageTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="universal-player-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.templates = ROOT / "release/player"
        self.common = {
            "game-files/Spoiled_Milk_Client.jar": b"fixture game client",
            "game-files/VERSION.txt": (VERSION + "\n").encode(),
            "game-files/SOURCE-COMMIT.txt": (COMMIT + "\n").encode(),
            "game-files/Cache/config.txt": b"packaged settings\n",
            "game-files/Cache/ip.txt": b"fixture.example.org\n",
            "game-files/Cache/port.txt": b"43605\n",
            "game-files/LICENSE": b"license",
            "game-files/ASSET-SOURCES.txt": b"credits",
            "game-files/Update Spoiled Milk.cmd": b"@echo off\n",
        }

    def inputs(self, java_changes=None, windows_changes=None, unsafe=False):
        for kind, changes in [("java", java_changes), ("windows-x64", windows_changes)]:
            files = dict(self.common)
            files["README.txt"] = b"legacy instructions"
            files["Play Spoiled Milk.cmd"] = (
                self.templates / (
                    "Play Spoiled Milk.cmd" if kind == "java"
                    else "Play Spoiled Milk Windows.cmd")).read_bytes()
            files["game-files/update-spoiled-milk.ps1"] = (
                self.templates / "update-spoiled-milk.ps1").read_text().replace(
                    "@VERSION@", VERSION).replace("@PACKAGE_KIND@", kind).encode()
            if kind == "java":
                files["play-spoiled-milk.sh"] = (
                    self.templates / "play-spoiled-milk.sh").read_bytes()
                files["game-files/update-spoiled-milk.sh"] = (
                    self.templates / "update-spoiled-milk.sh").read_text().replace(
                        "@VERSION@", VERSION).encode()
            else:
                files["game-files/runtime/bin/java.exe"] = b"bundled windows java"
                files["game-files/runtime/legal/java.base/LICENSE"] = b"runtime license"
            files.update(changes or {})
            path = self.root / f"spoiled-milk-{VERSION}-{kind}.zip"
            with zipfile.ZipFile(path, "w") as archive:
                prefix = f"spoiled-milk-{VERSION}-{kind}/"
                for name, contents in files.items():
                    if contents is not None:
                        archive.writestr(prefix + name, contents)
                if unsafe and kind == "java":
                    archive.writestr(prefix + "../outside", b"bad")

    def build(self, commit=COMMIT):
        return module.build(VERSION, self.root, self.templates, commit)

    def test_one_payload_three_launchers_and_runtime(self):
        self.inputs()
        report = self.build()
        self.assertEqual(COMMIT, report["gameSourceCommit"])
        with zipfile.ZipFile(self.root / module.NAME) as archive:
            names = archive.namelist()
            top = {name[len(module.ROOT):].split("/")[0] for name in names}
            self.assertEqual({
                "README.txt", "Play Spoiled Milk.cmd", "play-spoiled-milk.sh",
                "Play Spoiled Milk.command", "game-files"}, top)
            self.assertEqual(1, sum(name.endswith("/Spoiled_Milk_Client.jar") for name in names))
            for name, contents in self.common.items():
                self.assertEqual(contents, archive.read(module.ROOT + name))
            self.assertIn(module.ROOT + "game-files/runtime/bin/java.exe", names)
            self.assertIn(
                '$PackageKind = "windows-x64"',
                archive.read(module.ROOT + "game-files/update-spoiled-milk.ps1").decode())
            self.assertIn(
                'PACKAGE_KIND="java"',
                archive.read(module.ROOT + "game-files/update-spoiled-milk.sh").decode())
            for name in ("play-spoiled-milk.sh", "Play Spoiled Milk.command"):
                self.assertTrue(archive.getinfo(module.ROOT + name).external_attr >> 16 & 0o111)
            readme = archive.read(module.ROOT + "README.txt").decode()
            for value in (VERSION, "Windows", "LINUX", "macOS", "Java 17"):
                self.assertIn(value.lower(), readme.lower())
            self.assertNotIn("@VERSION@", readme)

    def test_posix_launchers_reach_java_from_a_folder_with_spaces(self):
        self.inputs()
        self.build()
        with zipfile.ZipFile(self.root / module.NAME) as archive:
            archive.extractall(self.root / "unpacked")
        install = self.root / "unpacked/Spoiled Milk"
        (install / "game-files/update-spoiled-milk.sh").write_text("#!/bin/sh\nexit 0\n")
        fake_bin = self.root / "bin"
        fake_bin.mkdir()
        java = fake_bin / "java"
        java.write_text("#!/bin/sh\nprintf 'JAVA %s\\n' \"$PWD\" \"$*\"\n")
        java.chmod(0o755)
        for launcher in ("play-spoiled-milk.sh", "Play Spoiled Milk.command"):
            with self.subTest(launcher=launcher):
                result = subprocess.run(
                    ["sh", str(install / launcher)], cwd=self.root,
                    env={**os.environ, "PATH": str(fake_bin) + os.pathsep + os.environ["PATH"]},
                    text=True, capture_output=True, timeout=10)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIn(str(install / "game-files"), result.stdout)
                self.assertIn("-jar Spoiled_Milk_Client.jar", result.stdout)

    def test_older_windows_conditional_is_repaired(self):
        broken = (
            self.templates / "Play Spoiled Milk Windows.cmd").read_text().replace(
                "if errorlevel 1 (\n  pause\n  exit /b 1\n)",
                "if errorlevel 1 pause & exit /b 1")
        self.inputs(windows_changes={"Play Spoiled Milk.cmd": broken.encode()})
        self.build()
        with zipfile.ZipFile(self.root / module.NAME) as archive:
            launcher = archive.read(module.ROOT + "Play Spoiled Milk.cmd").decode()
        self.assertNotIn("if errorlevel 1 pause & exit /b 1", launcher)
        self.assertIn("if errorlevel 1 (\n  pause\n  exit /b 1\n)", launcher)
        self.assertIn("runtime\\bin\\java.exe", launcher)

    def test_rejects_mismatched_game_payload(self):
        self.inputs(windows_changes={"game-files/Cache/config.txt": b"different map config"})
        with self.assertRaisesRegex(ValueError, "differ unexpectedly"):
            self.build()
        self.assertFalse((self.root / module.NAME).exists())

    def test_rejects_mismatched_release_commit(self):
        self.inputs()
        with self.assertRaisesRegex(ValueError, "expected release commit"):
            self.build("2" * 40)

    def test_rejects_missing_windows_runtime(self):
        self.inputs(windows_changes={"game-files/runtime/bin/java.exe": None})
        with self.assertRaisesRegex(ValueError, "bundled Java runtime"):
            self.build()

    def test_rejects_archive_traversal(self):
        self.inputs(unsafe=True)
        with self.assertRaisesRegex(ValueError, "Unsafe"):
            self.build()

    def test_refuses_to_overwrite_existing_download(self):
        self.inputs()
        self.build()
        before = (self.root / module.NAME).read_bytes()
        with self.assertRaises(FileExistsError):
            self.build()
        self.assertEqual(before, (self.root / module.NAME).read_bytes())

    def prepare_tagged_release(self):
        fixture = self.root / "source"
        fixture.mkdir()
        runtime = release_fixture.make_fixture(
            fixture, build_script="#!/bin/sh\nexit 99\n")
        result = release_fixture.run_packager(fixture, runtime, "--assets-cleared")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        output = fixture / "output/releases" / release_fixture.VERSION
        (output / module.NAME).rename(output / "previous-download.zip")
        return fixture, output

    def consolidate(self, fixture):
        return subprocess.run([
            "bash", str(release_fixture.PACKAGER), "--version", release_fixture.VERSION,
            "--consolidate-existing", "--assets-cleared"],
            env={**os.environ, "ROOT_DIR": str(fixture)},
            text=True, capture_output=True, timeout=30)

    def test_consolidation_reuses_tagged_payload_without_building(self):
        fixture, output = self.prepare_tagged_release()
        release_fixture.git(fixture, "tag", release_fixture.VERSION)
        legacy = [
            output / f"spoiled-milk-{release_fixture.VERSION}-{kind}.zip"
            for kind in ("java", "windows-x64")]
        hashes = {p: module.digest(p.read_bytes()) for p in legacy}
        result = self.consolidate(fixture)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        for p, before in hashes.items():
            self.assertEqual(before, module.digest(p.read_bytes()))
        report = json.loads((output / "universal-player-package.json").read_text())
        self.assertEqual(
            release_fixture.git(fixture, "rev-parse", "HEAD"), report["gameSourceCommit"])
        self.assertEqual(
            (output / "previous-download.zip").read_bytes(),
            (output / module.NAME).read_bytes())

    def test_consolidation_rejects_payload_from_a_different_tag(self):
        fixture, output = self.prepare_tagged_release()
        release_fixture.git(fixture, "commit", "--allow-empty", "-m", "Different source")
        release_fixture.git(fixture, "update-ref", "refs/remotes/spoiled-milk/main", "HEAD")
        release_fixture.git(fixture, "tag", release_fixture.VERSION)
        result = self.consolidate(fixture)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("expected release commit", result.stderr)
        self.assertFalse((output / module.NAME).exists())


if __name__ == "__main__":
    unittest.main()
