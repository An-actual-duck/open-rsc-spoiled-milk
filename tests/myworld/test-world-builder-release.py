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
PACKAGER = ROOT / "scripts/package-world-builder-v2-release.sh"
LEGACY_PACKAGER = ROOT / "scripts/package-world-builder-release.sh"
VERSION = "v0.1.0-alpha.1"
VERSION_NUMBER = VERSION[1:]
PACKAGE_ROOT = "Spoiled Milk World Builder 2"
PRODUCT_ID = "rsc-world-editor-v2"
NATIVE_ENTRIES = (
    "linux/x64/org/lwjgl/liblwjgl.so",
    "linux/x64/org/lwjgl/glfw/libglfw.so",
    "linux/x64/org/lwjgl/opengl/liblwjgl_opengl.so",
    "windows/x64/org/lwjgl/lwjgl.dll",
    "windows/x64/org/lwjgl/glfw/glfw.dll",
    "windows/x64/org/lwjgl/opengl/lwjgl_opengl.dll",
)


def write(path: Path, contents: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(contents, encoding="utf-8")


def make_jar(path: Path, entries: tuple[str, ...]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w") as archive:
        for entry in entries:
            archive.writestr(entry, b"fixture")


def git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args], cwd=root, text=True, capture_output=True, check=True
    )
    return result.stdout.strip()


def make_fixture(
    root: Path, resolved_icons: bool = True, linux_os: str = "Linux"
) -> tuple[Path, Path]:
    make_jar(
        root / "Client_Base/Open_RSC_Client.jar",
        (
            "orsc/WorldBuilderClientProfile.class",
            "myworld-assets/ui/world-editor/action-save.png",
            *NATIVE_ENTRIES,
        ),
    )
    make_jar(
        root / "server/core.jar",
        (
            "com/openrsc/server/content/worldedit/WorldEditStorageContext.class",
            "com/openrsc/server/content/worldedit/WorldBuilderRuntimeControl.class",
        ),
    )
    make_jar(root / "server/plugins.jar", ("fixture/Plugin.class",))
    make_jar(
        root / "output/world-builder-tools/world-builder-tools.jar",
        ("com/openrsc/worldbuilder/WorldBuilderCli.class",),
    )
    write(root / "Client_Base/Cache/audio/audio.dat", "audio")
    write(root / "Client_Base/Cache/video/library.orsc", "library")
    write(root / "Client_Base/Cache/video/Custom_Landscape.orsc", "terrain")
    write(root / "Client_Base/Cache/config.txt", "Menus:1\n")
    write(root / "Client_Base/Cache/credentials.txt", "must-not-ship")
    write(root / "Client_Base/Cache/uid.dat", "must-not-ship")
    write(root / "Client_Base/src/orsc/Config.java", "CLIENT_VERSION = 10048;\n")
    write(root / "server/lib/runtime.jar", "runtime")
    write(root / "server/conf/server/data/Custom_Landscape.orsc", "terrain")
    write(root / "server/conf/server/defs/TileDef.xml", "<tiles/>\n")
    write(root / "server/database/sqlite/core.sqlite", "queries")
    write(root / "server/inc/sqlite/myworld_seed.db", "clean-seed")
    write(root / "server/myworld.conf", "\tclient_version: 10048\n")
    for name in ("alertwords.txt", "badwords.txt", "goodwords.txt"):
        write(root / "server" / name, "\n")
    write(root / "server/ipbans.txt", "ignored generated bans must not ship\n")
    write(root / "server/globalrules.txt", "rules\n")
    write(root / "tools/world-builder/schema/project.json", "{}\n")
    shutil.copytree(
        ROOT / "release/world-builder-v2", root / "release/world-builder-v2"
    )
    write(root / "layered-world-package/manifest.json", '{"schemaVersion":1}\n')
    write(root / "layered-world-package/terrain/fixture.raw", "layered terrain\n")
    write(root / "release/player/ASSET-SOURCES.txt", "player assets resolved\n")
    write(root / "LICENSE", "AGPL fixture\n")
    credits = (
        "All editor icons | Project owner | Confirmed original work | redistribution permitted\n"
        if resolved_icons
        else "All editor icons | Pending confirmation | not release-ready\n"
    )
    write(root / "dev/myworld/assets/ui/world-editor/CREDITS.md", credits)

    linux_runtime = root / "temurin-linux-jre"
    write(
        linux_runtime / "bin/java",
        "#!/usr/bin/env bash\n"
        'if [[ "${1:-}" == -version ]]; then exit 0; fi\n'
        'printf \'%s\\n\' "$@" > "$FAKE_JAVA_CALLS"\n',
    )
    (linux_runtime / "bin/java").chmod(0o755)
    write(
        linux_runtime / "release",
        f'JAVA_VERSION="17.0.13"\nOS_NAME="{linux_os}"\nOS_ARCH="x86_64"\n',
    )
    write(linux_runtime / "NOTICE", "Linux runtime notice\n")
    write(linux_runtime / "legal/java.base/LICENSE", "Linux runtime license\n")
    windows_runtime = root / "temurin-windows-jre"
    write(windows_runtime / "bin/java.exe", "runtime")
    write(
        windows_runtime / "release",
        'JAVA_VERSION="17.0.13"\nOS_NAME="Windows"\nOS_ARCH="x86_64"\n',
    )
    write(windows_runtime / "NOTICE", "Windows runtime notice\n")
    write(windows_runtime / "legal/java.base/LICENSE", "Windows runtime license\n")
    write(root / ".gitignore", "output/\nserver/ipbans.txt\n")

    git(root, "init", "--initial-branch=main")
    git(root, "config", "user.name", "World Builder Release Test")
    git(root, "config", "user.email", "world-builder-test@example.invalid")
    git(root, "add", "--all")
    git(root, "commit", "-m", "Create World Builder release fixture")
    git(root, "remote", "add", "spoiled-milk", "https://example.invalid/spoiled-milk.git")
    git(root, "update-ref", "refs/remotes/spoiled-milk/main", "HEAD")
    return linux_runtime, windows_runtime


def run_packager(
    root: Path,
    linux_runtime: Path,
    windows_runtime: Path,
    skip_build: bool = True,
) -> subprocess.CompletedProcess[str]:
    env = dict(os.environ)
    env["ROOT_DIR"] = str(root)
    arguments = [
        "bash",
        str(PACKAGER),
        "--version",
        VERSION,
        "--linux-jre",
        str(linux_runtime),
        "--windows-jre",
        str(windows_runtime),
        "--layered-package",
        str(root / "layered-world-package"),
        "--assets-cleared",
    ]
    if skip_build:
        env["SPOILED_MILK_WORLD_BUILDER_V2_RELEASE_TEST_MODE"] = "1"
        arguments.append("--skip-build")
    return subprocess.run(
        arguments,
        cwd=ROOT,
        env=env,
        text=True,
        capture_output=True,
    )


class WorldBuilderReleaseTest(unittest.TestCase):
    def test_legacy_release_entrypoint_is_frozen_and_fail_closed(self):
        result = subprocess.run(
            ["bash", str(LEGACY_PACKAGER)],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertIn("frozen at v1.1.0", result.stderr)
        self.assertIn("package-world-builder-v2-release.sh", result.stderr)

    def test_public_v2_packaging_stays_locked_until_layered_release_gate(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-v2-release-gate-") as temp:
            fixture = Path(temp)
            linux_runtime, windows_runtime = make_fixture(fixture)
            result = run_packager(
                fixture, linux_runtime, windows_runtime, skip_build=False
            )
            self.assertNotEqual(0, result.returncode)
            self.assertIn(
                "layered export/import and final release validation",
                result.stderr,
            )

    def test_packager_rejects_unresolved_icon_provenance(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-release-credits-") as temp:
            fixture = Path(temp)
            linux_runtime, windows_runtime = make_fixture(fixture, resolved_icons=False)
            result = run_packager(fixture, linux_runtime, windows_runtime)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("icon provenance is unresolved", result.stderr)

    def test_packager_is_manager_main_only(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-release-branch-") as temp:
            fixture = Path(temp)
            linux_runtime, windows_runtime = make_fixture(fixture)
            git(fixture, "switch", "-c", "feature-test")
            result = run_packager(fixture, linux_runtime, windows_runtime)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("manager branch main", result.stderr)

    def test_packager_rejects_runtime_for_the_wrong_platform(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-release-runtime-") as temp:
            fixture = Path(temp)
            linux_runtime, windows_runtime = make_fixture(fixture, linux_os="Windows")
            result = run_packager(fixture, linux_runtime, windows_runtime)
            self.assertNotEqual(0, result.returncode)
            self.assertIn('Linux JRE must report OS_NAME="Linux"', result.stderr)

    def test_archives_are_clean_complete_and_launchable_without_repo_paths(self):
        with tempfile.TemporaryDirectory(prefix="world-builder-release-package-") as temp:
            fixture = Path(temp) / "fixture"
            fixture.mkdir()
            linux_runtime, windows_runtime = make_fixture(fixture)
            result = run_packager(fixture, linux_runtime, windows_runtime)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            source_commit = git(fixture, "rev-parse", "HEAD")
            output = fixture / "output/releases/world-builder-v2" / VERSION
            linux_archive = output / f"rsc-world-editor-v2-{VERSION_NUMBER}-linux-x64.zip"
            windows_archive = output / f"rsc-world-editor-v2-{VERSION_NUMBER}-windows-x64.zip"
            checksums = output / "SHA256SUMS.txt"
            for artifact in (linux_archive, windows_archive, checksums):
                self.assertTrue(artifact.is_file(), artifact)

            expected_checksums = {
                path.name: hashlib.sha256(path.read_bytes()).hexdigest()
                for path in (linux_archive, windows_archive)
            }
            checksum_text = checksums.read_text(encoding="utf-8")
            for name, digest in expected_checksums.items():
                self.assertIn(f"{digest}  {name}", checksum_text)

            for archive_path, windows in ((linux_archive, False), (windows_archive, True)):
                with zipfile.ZipFile(archive_path) as archive:
                    names = set(archive.namelist())
                    prefix = f"{PACKAGE_ROOT}/"
                    required = {
                        prefix + "Start World Builder.sh",
                        prefix + "Start World Builder.cmd",
                        prefix + "Import Map Changes.sh",
                        prefix + "Import Map Changes.cmd",
                        prefix + "Undo Last Map Import.sh",
                        prefix + "Undo Last Map Import.cmd",
                        prefix + "README.txt",
                        prefix + "RELEASE-IDENTITY.json",
                        prefix + "VERSION.txt",
                        prefix + "SOURCE-COMMIT.txt",
                        prefix + "LICENSE",
                        prefix + "ASSET-SOURCES.txt",
                        prefix + "PLAYER-ASSET-SOURCES.txt",
                        prefix + "EDITOR-ICON-CREDITS.txt",
                        prefix + "builder-runtime/Client_Base/Open_RSC_Client.jar",
                        prefix + "builder-runtime/server/core.jar",
                        prefix + "builder-runtime/server/plugins.jar",
                        prefix + "builder-runtime/server/inc/sqlite/myworld_seed.db",
                        prefix + "builder-runtime/launcher/world-builder-tools.jar",
                        prefix + "builder-runtime/layered-world/package/manifest.json",
                    }
                    self.assertFalse(required - names, required - names)
                    if windows:
                        self.assertIn(prefix + "runtime/bin/java.exe", names)
                        self.assertIn(prefix + "runtime/release", names)
                    else:
                        self.assertIn(prefix + "runtime/bin/java", names)
                        self.assertIn(prefix + "runtime/release", names)
                        java_mode = archive.getinfo(prefix + "runtime/bin/java").external_attr >> 16
                        self.assertTrue(java_mode & 0o111, oct(java_mode))
                    forbidden = (
                        "/workspace/", "/exports/", "/backups/", "/receipts/", "/logs/",
                        "world_builder.db", "world-builder.credential", "credentials.txt",
                        "uid.dat", "clientSettings.conf", "builder-runtime/server/ipbans.txt",
                        "/ip.txt", "/port.txt",
                    )
                    self.assertFalse(
                        [name for name in names if any(fragment in name for fragment in forbidden)]
                    )
                    self.assertEqual(
                        f"{VERSION}\n", archive.read(prefix + "VERSION.txt").decode()
                    )
                    self.assertEqual(
                        f"{source_commit}\n",
                        archive.read(prefix + "SOURCE-COMMIT.txt").decode(),
                    )
                    identity = json.loads(
                        archive.read(prefix + "RELEASE-IDENTITY.json").decode()
                    )
                    self.assertEqual(1, identity["schemaVersion"])
                    self.assertEqual(PRODUCT_ID, identity["productId"])
                    self.assertEqual(2, identity["productGeneration"])
                    self.assertEqual(PRODUCT_ID, identity["updateChannel"])
                    self.assertEqual([PRODUCT_ID], identity["automaticUpgradeFromProductIds"])
                    self.assertEqual("rsc-world-editor-v1", identity["legacyProductId"])
                    self.assertEqual("v1.1.0", identity["legacyFinalTag"])
                    self.assertFalse(identity["legacyWorkspaceMigration"])
                    self.assertEqual("signed-layered-v1", identity["worldCoordinateModel"])
                    self.assertEqual(VERSION, identity["version"])
                    self.assertEqual(source_commit, identity["sourceCommit"])
                    readme = archive.read(prefix + "README.txt").decode()
                    self.assertIn(VERSION, readme)
                    self.assertIn(source_commit, readme)
                    self.assertNotIn("@VERSION@", readme)
                    self.assertIn("Ctrl+Shift+S      Save world edits", readme)
                    self.assertIn("Ctrl+Enter        Toggle editor-shortcut mode", readme)
                    self.assertIn("Set a North Wall, East Wall, or Diagonal Wall value to 0", readme)
                    self.assertIn("Set Floor Texture to 8 to erase terrain", readme)
                    self.assertIn("Save does not copy those files into the parent private server", readme)
                    self.assertIn("never installs as an automatic v1 update", readme)
                    self.assertIn(
                        "does not open or migrate a v1 workspace",
                        " ".join(readme.split()),
                    )
                    self.assertIn('"Import Map Changes.sh"', readme)
                    normalized_readme = " ".join(readme.split())
                    self.assertIn(
                        "exact state immediately before the most recent successful",
                        normalized_readme,
                    )
                    self.assertIn(
                        "server/conf/server/data/world-builder-layered/package/",
                        readme,
                    )
                    self.assertIn("pinned manifest SHA-256", normalized_readme)
                    start_cmd = archive.read(prefix + "Start World Builder.cmd").decode()
                    self.assertIn(r"runtime\bin\java.exe", start_cmd)
                    self.assertIn("launch --server-root", start_cmd)
                    self.assertIn("run --workspace", start_cmd)
                    self.assertIn("--layered-package", start_cmd)
                    self.assertIn("--layered-profile spoiled-milk-replacement", start_cmd)
                    import_cmd = archive.read(prefix + "Import Map Changes.cmd").decode()
                    self.assertIn("export-import", import_cmd)
                    undo_cmd = archive.read(prefix + "Undo Last Map Import.cmd").decode()
                    self.assertIn("undo-latest-import", undo_cmd)
                    if not windows:
                        import_sh = archive.read(prefix + "Import Map Changes.sh").decode()
                        undo_sh = archive.read(prefix + "Undo Last Map Import.sh").decode()
                        for launcher in (import_sh, undo_sh):
                            self.assertIn("WORLD_BUILDER_TERMINAL_SESSION", launcher)
                            self.assertIn("x-terminal-emulator", launcher)
                            self.assertIn("Press Enter to close this window", launcher)

            extracted = Path(temp) / "private-server"
            extracted.mkdir()
            subprocess.run(
                ["unzip", "-q", str(linux_archive), "-d", str(extracted)], check=True
            )
            package = extracted / PACKAGE_ROOT
            calls = Path(temp) / "java-calls.txt"
            env = dict(os.environ)
            env["WORLD_BUILDER_PORT"] = "44600"
            env["WORLD_BUILDER_NO_TERMINAL"] = "1"
            env["FAKE_JAVA_CALLS"] = str(calls)

            started = subprocess.run(
                ["bash", str(package / "Start World Builder.sh")],
                cwd=Path(temp), env=env, text=True, capture_output=True,
            )
            self.assertEqual(0, started.returncode, started.stdout + started.stderr)
            start_call = calls.read_text(encoding="utf-8")
            self.assertIn("launch\n", start_call)
            self.assertIn(str(extracted), start_call)
            self.assertIn("44600\n", start_call)

            write(package / "workspace/project-source.json", "{}\n")
            refused_legacy = subprocess.run(
                ["bash", str(package / "Start World Builder.sh")],
                cwd=Path(temp), env=env, text=True, capture_output=True,
            )
            self.assertNotEqual(0, refused_legacy.returncode)
            self.assertIn("legacy or unidentified", refused_legacy.stderr)

            write(package / "workspace/layered-review.json", "{}\n")
            restarted = subprocess.run(
                ["bash", str(package / "Start World Builder.sh")],
                cwd=Path(temp), env=env, text=True, capture_output=True,
            )
            self.assertEqual(0, restarted.returncode, restarted.stdout + restarted.stderr)
            self.assertIn("run\n", calls.read_text(encoding="utf-8"))

            imported = subprocess.run(
                ["bash", str(package / "Import Map Changes.sh")],
                cwd=Path(temp), env=env, text=True, capture_output=True,
            )
            self.assertEqual(0, imported.returncode, imported.stdout + imported.stderr)
            import_call = calls.read_text(encoding="utf-8")
            self.assertIn("export-import\n", import_call)
            self.assertIn(VERSION, import_call)
            self.assertIn(source_commit, import_call)

            undone = subprocess.run(
                ["bash", str(package / "Undo Last Map Import.sh")],
                cwd=Path(temp), env=env, text=True, capture_output=True,
            )
            self.assertEqual(0, undone.returncode, undone.stdout + undone.stderr)
            self.assertIn("undo-latest-import\n", calls.read_text(encoding="utf-8"))

            fake_bin = Path(temp) / "fake-bin"
            terminal_calls = Path(temp) / "terminal-calls.txt"
            fake_terminal = fake_bin / "x-terminal-emulator"
            write(
                fake_terminal,
                "#!/usr/bin/env bash\n"
                "printf '%s\\n' CALL \"$@\" >> \"$FAKE_TERMINAL_CALLS\"\n",
            )
            fake_terminal.chmod(0o755)
            desktop_env = dict(env)
            desktop_env.pop("WORLD_BUILDER_NO_TERMINAL")
            desktop_env["DISPLAY"] = ":99"
            desktop_env["PATH"] = f"{fake_bin}:{desktop_env['PATH']}"
            desktop_env["FAKE_TERMINAL_CALLS"] = str(terminal_calls)
            for launcher_name in ("Import Map Changes.sh", "Undo Last Map Import.sh"):
                launched = subprocess.run(
                    ["bash", str(package / launcher_name)],
                    cwd=Path(temp), env=desktop_env, text=True, capture_output=True,
                )
                self.assertEqual(0, launched.returncode, launched.stdout + launched.stderr)
            terminal_call = terminal_calls.read_text(encoding="utf-8")
            self.assertEqual(2, terminal_call.count("CALL\n"))
            self.assertIn("-e\n", terminal_call)
            self.assertIn("WORLD_BUILDER_TERMINAL_SESSION=1\n", terminal_call)
            self.assertIn(str(package / "Import Map Changes.sh"), terminal_call)
            self.assertIn(str(package / "Undo Last Map Import.sh"), terminal_call)


if __name__ == "__main__":
    unittest.main()
