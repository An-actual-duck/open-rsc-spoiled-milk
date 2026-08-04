#!/usr/bin/env python3
import hashlib
import io
import os
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PACKAGER = ROOT / "scripts" / "package-player-release.sh"
VERSION = "v0.1.0-alpha.1"
RENDERER_V2_FLAGS = [
    "-Dspoiledmilk.directFramebuffer=true",
    "-Dspoiledmilk.openglPresenter=true",
    "-Dspoiledmilk.openglInput=true",
    "-Dspoiledmilk.openglPrimaryWindow=true",
    "-Dspoiledmilk.renderer3DGeometryCapture=true",
    "-Dspoiledmilk.openglWorldMesh=true",
    "-Dspoiledmilk.openglWorldMeshTexturedVisible=true",
    "-Dspoiledmilk.openglWorldMeshTexturedStaticVisible=true",
    "-Dspoiledmilk.openglWorldStaticTextures=true",
    "-Dspoiledmilk.openglWorldTexturedAlpha=1.0",
    "-Dspoiledmilk.openglWorldSpritesVisible=true",
]
CLIENT_JVM_MEMORY_FLAGS = [
    "-Xms512m",
    "-Xmx2g",
]
CLIENT_RELEASE_FLAGS = [
    "-Dspoiledmilk.releaseBuild=true",
]
OPENGL_RUNTIME_ENTRIES = [
    "linux/x64/org/lwjgl/liblwjgl.so",
    "linux/x64/org/lwjgl/glfw/libglfw.so",
    "linux/x64/org/lwjgl/opengl/liblwjgl_opengl.so",
    "macos/x64/org/lwjgl/liblwjgl.dylib",
    "macos/x64/org/lwjgl/glfw/libglfw.dylib",
    "macos/x64/org/lwjgl/opengl/liblwjgl_opengl.dylib",
    "macos/arm64/org/lwjgl/liblwjgl.dylib",
    "macos/arm64/org/lwjgl/glfw/libglfw.dylib",
    "macos/arm64/org/lwjgl/opengl/liblwjgl_opengl.dylib",
    "windows/x64/org/lwjgl/lwjgl.dll",
    "windows/x64/org/lwjgl/glfw/glfw.dll",
    "windows/x64/org/lwjgl/opengl/lwjgl_opengl.dll",
]
CLERIC_SIGIL_ASSET_FILES = {
    "stone.png",
    "blessed-guth-sigil.png",
    "blessed-neutral-sigil.png",
    "blessed-sara-sigil.png",
    "blessed-zam-sigil.png",
    "unblessed-guth-sigil.png",
    "unblessed-neutral-sigil.png",
    "unblessed-sara-sigil.png",
    "unblessed-zam-sigil.png",
    "silver-blessed-guth-sigil.png",
    "silver-blessed-neutral-sigil.png",
    "silver-blessed-sara-sigil.png",
    "silver-blessed-zam-sigil.png",
    "silver-unblessed-guth-sigil.png",
    "silver-unblessed-neutral-sigil.png",
    "silver-unblessed-sara-sigil.png",
    "silver-unblessed-zam-sigil.png",
}
CLERIC_SIGIL_JAR_ENTRIES = {
    f"myworld-assets/sprites/items/inventory-ground/resources/sigils/{name}"
    for name in CLERIC_SIGIL_ASSET_FILES
}
CLERIC_SIGIL_SOURCE_PATHS = {
    ROOT / "dev/myworld/assets/sprites/items/inventory-ground/resources/sigils" / name
    for name in CLERIC_SIGIL_ASSET_FILES
}


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def write(path: Path, contents: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(contents, encoding="utf-8")


def git(repo: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=repo,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        fail(f"git {' '.join(args)} failed in release fixture:\n{result.stdout}{result.stderr}")
    return result.stdout.strip()


def make_fixture(
    fixture: Path,
    java_version: str = "17.0.13",
    build_script: str = "#!/usr/bin/env bash\nset -euo pipefail\n",
) -> Path:
    client_jar = fixture / "Client_Base" / "Open_RSC_Client.jar"
    client_jar.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(client_jar, "w") as jar:
        jar.writestr("myworld-assets/animations/projectile-moving/fixture/frame.png", "asset")
        jar.writestr("spoiled-milk-release-build.marker", "release-build=true\n")
        for entry in OPENGL_RUNTIME_ENTRIES:
            jar.writestr(entry, "native")
    write(fixture / "Client_Base" / "Cache" / "audio" / "audio.dat", "audio")
    write(fixture / "Client_Base" / "Cache" / "video" / "video.dat", "video")
    write(fixture / "Client_Base" / "Cache" / "config.txt", "Menus:1\n")
    write(fixture / "Client_Base" / "Cache" / "uid.dat", "developer-state")
    write(fixture / "Client_Base" / "Cache" / "credentials.txt", "secret")
    write(fixture / "Client_Base" / "clientSettings.conf", "local-settings")
    write(fixture / "LICENSE", "AGPL fixture")
    shutil.copytree(ROOT / "release" / "player", fixture / "release" / "player")

    runtime = fixture / "temurin-jre"
    write(runtime / "bin" / "java.exe", "runtime")
    write(runtime / "release", f'JAVA_VERSION="{java_version}"\n')
    write(runtime / "NOTICE", "runtime notice")
    write(runtime / "legal" / "java.base" / "LICENSE", "runtime license")
    write(fixture / ".gitignore", "output/\n")
    write(fixture / "scripts" / "build-client.sh", build_script)
    os.chmod(fixture / "scripts" / "build-client.sh", 0o755)

    git(fixture, "init", "--initial-branch=main")
    git(fixture, "config", "user.name", "Release Test")
    git(fixture, "config", "user.email", "release-test@example.invalid")
    git(fixture, "add", "--all")
    git(fixture, "commit", "-m", "Create release fixture")
    git(fixture, "remote", "add", "spoiled-milk", "https://example.invalid/spoiled-milk.git")
    git(fixture, "update-ref", "refs/remotes/spoiled-milk/main", "HEAD")
    return runtime


def run_packager(
    fixture: Path,
    runtime: Path,
    *extra: str,
    skip_build: bool = True,
    test_mode: bool = True,
) -> subprocess.CompletedProcess[str]:
    env = dict(os.environ)
    env["ROOT_DIR"] = str(fixture)
    if test_mode:
        env["SPOILED_MILK_RELEASE_TEST_MODE"] = "1"
    options = ["--skip-build"] if skip_build else []
    return subprocess.run(
        [
            "bash",
            str(PACKAGER),
            "--version",
            VERSION,
            "--host",
            "alpha.example.test",
            "--port",
            "43605",
            "--windows-jre",
            str(runtime),
            *options,
            *extra,
        ],
        cwd=ROOT,
        env=env,
        capture_output=True,
        text=True,
    )


def test_packager_rejects_localhost_player_endpoint() -> None:
    with tempfile.TemporaryDirectory(prefix="spoiled-release-test-") as temp_dir:
        fixture = Path(temp_dir)
        runtime = make_fixture(fixture)
        env = dict(os.environ)
        env["ROOT_DIR"] = str(fixture)
        env["SPOILED_MILK_RELEASE_TEST_MODE"] = "1"
        result = subprocess.run(
            [
                "bash",
                str(PACKAGER),
                "--version",
                VERSION,
                "--host",
                "localhost",
                "--port",
                "43605",
                "--windows-jre",
                str(runtime),
                "--skip-build",
                "--assets-cleared",
            ],
            cwd=ROOT,
            env=env,
            capture_output=True,
            text=True,
        )
        if result.returncode == 0:
            fail("release packager allowed a localhost player endpoint")
        if "Player release host must be a public host/IP" not in result.stderr:
            fail(f"release packager rejected localhost with an unclear error:\n{result.stderr}")


def test_packaged_archives_are_clean_and_configured() -> None:
    with tempfile.TemporaryDirectory(prefix="spoiled-release-test-") as temp_dir:
        fixture = Path(temp_dir)
        runtime = make_fixture(fixture)
        result = run_packager(fixture, runtime, "--assets-cleared")
        if result.returncode != 0:
            fail(f"release packager failed:\n{result.stdout}{result.stderr}")
        source_commit = git(fixture, "rev-parse", "HEAD")

        output = fixture / "output" / "releases" / VERSION
        generic = output / f"spoiled-milk-{VERSION}-java.zip"
        windows = output / f"spoiled-milk-{VERSION}-windows-x64.zip"
        checksum_file = output / "SHA256SUMS.txt"
        for artifact in [generic, windows, checksum_file]:
            if not artifact.exists():
                fail(f"packager did not create {artifact.name}")

        for archive, package_name in [
            (generic, f"spoiled-milk-{VERSION}-java"),
            (windows, f"spoiled-milk-{VERSION}-windows-x64"),
        ]:
            with zipfile.ZipFile(archive) as package:
                names = set(package.namelist())
                expected_root_entries = {
                    "README.txt",
                    "Play Spoiled Milk.cmd",
                    "game-files",
                }
                if archive == generic:
                    expected_root_entries.add("play-spoiled-milk.sh")
                root_entries = {
                    member[len(package_name) + 1:].split("/", 1)[0]
                    for member in names
                    if member.startswith(f"{package_name}/") and member != f"{package_name}/"
                }
                if root_entries != expected_root_entries:
                    fail(f"{archive.name} root entries should stay launcher-focused; got {sorted(root_entries)}")
                required = {
                    f"{package_name}/game-files/Spoiled_Milk_Client.jar",
                    f"{package_name}/game-files/Cache/ip.txt",
                    f"{package_name}/game-files/Cache/port.txt",
                    f"{package_name}/game-files/Cache/audio/audio.dat",
                    f"{package_name}/game-files/Cache/video/video.dat",
                    f"{package_name}/game-files/LICENSE",
                    f"{package_name}/README.txt",
                    f"{package_name}/game-files/ASSET-SOURCES.txt",
                    f"{package_name}/game-files/VERSION.txt",
                    f"{package_name}/game-files/SOURCE-COMMIT.txt",
                    f"{package_name}/Play Spoiled Milk.cmd",
                    f"{package_name}/game-files/Update Spoiled Milk.cmd",
                    f"{package_name}/game-files/update-spoiled-milk.ps1",
                }
                missing = required - names
                if missing:
                    fail(f"{archive.name} is missing {sorted(missing)}")
                for root_forbidden in [
                    "Spoiled_Milk_Client.jar",
                    "Cache/ip.txt",
                    "Update Spoiled Milk.cmd",
                    "update-spoiled-milk.ps1",
                    "ASSET-SOURCES.txt",
                    "VERSION.txt",
                    "SOURCE-COMMIT.txt",
                ]:
                    if f"{package_name}/{root_forbidden}" in names:
                        fail(f"{archive.name} should keep {root_forbidden} inside game-files/")

                forbidden_fragments = [
                    "uid.dat",
                    "credentials.txt",
                    "clientSettings.conf",
                    "MD5.SUM",
                    "PC_Launcher",
                    "server/",
                ]
                for member in names:
                    if any(fragment in member for fragment in forbidden_fragments):
                        fail(f"{archive.name} contains local or non-player material: {member}")

                if package.read(f"{package_name}/game-files/Cache/ip.txt").decode() != "alpha.example.test\n":
                    fail(f"{archive.name} does not carry the requested endpoint host")
                if package.read(f"{package_name}/game-files/Cache/port.txt").decode() != "43605\n":
                    fail(f"{archive.name} does not carry the requested endpoint port")
                if package.read(f"{package_name}/game-files/VERSION.txt").decode() != f"{VERSION}\n":
                    fail(f"{archive.name} does not carry the packaged version stamp")
                if package.read(f"{package_name}/game-files/SOURCE-COMMIT.txt").decode() != f"{source_commit}\n":
                    fail(f"{archive.name} does not carry the exact packaged source commit")
                packaged_client = package.read(f"{package_name}/game-files/Spoiled_Milk_Client.jar")
                with zipfile.ZipFile(io.BytesIO(packaged_client)) as client_jar:
                    if "spoiled-milk-release-build.marker" not in client_jar.namelist():
                        fail(f"{archive.name} client jar is missing its release-build marker")
                readme = package.read(f"{package_name}/README.txt").decode()
                for snippet in [
                    "Launch with play-spoiled-milk.sh on Linux or macOS.",
                    "Launch with Play Spoiled Milk.cmd on Windows.",
                    "automatically check for updates",
                ]:
                    if snippet not in readme:
                        fail(f"{archive.name} README should explain launch/update flow: {snippet!r}")
                launcher = package.read(f"{package_name}/Play Spoiled Milk.cmd").decode()
                for snippet in [
                    'if exist "%~dp0game-files\\Spoiled_Milk_Client.jar"',
                    'if exist "%~dp0Spoiled_Milk_Client.jar" del /q',
                    'if exist "%~dp0Cache" rmdir /s /q',
                    'powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0game-files\\update-spoiled-milk.ps1"',
                    "if errorlevel 1 pause & exit /b 1",
                    'cd /d "%~dp0game-files"',
                    "Spoiled_Milk_Client.jar",
                    *CLIENT_JVM_MEMORY_FLAGS,
                    *CLIENT_RELEASE_FLAGS,
                    *RENDERER_V2_FLAGS,
                ]:
                    if snippet not in launcher:
                        fail(f"{archive.name} launcher must update synchronously before launch: {snippet!r}")
                updater = package.read(f"{package_name}/game-files/update-spoiled-milk.ps1").decode()
                expected_kind = "windows-x64" if archive == windows else "java"
                for snippet in [
                    f'$CurrentVersion = "{VERSION}"',
                    f'$PackageKind = "{expected_kind}"',
                    "$PayloadDir = Split-Path -Parent $MyInvocation.MyCommand.Path",
                    "$InstallDir = Split-Path -Parent $PayloadDir",
                    "Invoke-RestMethod -Uri $ApiUrl",
                    "Invoke-WebRequest -Uri $asset.browser_download_url",
                    "Expand-Archive -Path $archive",
                    "Copy-Item -Path (Join-Path $packageRoot \"*\") -Destination $InstallDir",
                    "$legacyFiles = @(",
                    "Remove-Item -Recurse -Force -ErrorAction SilentlyContinue -Path $legacyDirs",
                ]:
                    if snippet not in updater:
                        fail(f"{archive.name} updater is missing {snippet!r}")

        with zipfile.ZipFile(generic) as package:
            if f"spoiled-milk-{VERSION}-java/play-spoiled-milk.sh" not in package.namelist():
                fail("generic Java package must contain its shell launcher")
            if f"spoiled-milk-{VERSION}-java/game-files/update-spoiled-milk.sh" not in package.namelist():
                fail("generic Java package must contain its shell updater inside game-files")
            shell_launcher = package.read(f"spoiled-milk-{VERSION}-java/play-spoiled-milk.sh").decode()
            for snippet in [
                'GAME_DIR="$ROOT_DIR/game-files"',
                'rm -rf "$ROOT_DIR/Cache" "$ROOT_DIR/runtime" "$ROOT_DIR/updates"',
                'if ! sh "$GAME_DIR/update-spoiled-milk.sh"; then',
                "Update check failed; launching installed Spoiled Milk client.",
                '"$GAME_DIR/update-spoiled-milk.sh"',
                'cd "$GAME_DIR"',
                '-jar "Spoiled_Milk_Client.jar"',
                *CLIENT_JVM_MEMORY_FLAGS,
                *CLIENT_RELEASE_FLAGS,
                *RENDERER_V2_FLAGS,
            ]:
                if snippet not in shell_launcher:
                    fail(f"generic Java shell launcher must tolerate update failure before launch: {snippet!r}")
            shell_updater = package.read(f"spoiled-milk-{VERSION}-java/game-files/update-spoiled-milk.sh").decode()
            for snippet in [
                f'CURRENT_VERSION="{VERSION}"',
                'PACKAGE_KIND="java"',
                'PAYLOAD_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"',
                'INSTALL_DIR="$(CDPATH= cd -- "$PAYLOAD_DIR/.." && pwd)"',
                "curl -fsSL \"$API_URL\"",
                "unzip -q \"$archive\"",
                'cp -R "$package_root/." "$INSTALL_DIR/"',
                'rm -rf "$INSTALL_DIR/Cache" "$INSTALL_DIR/runtime" "$INSTALL_DIR/updates"',
            ]:
                if snippet not in shell_updater:
                    fail(f"generic Java shell updater is missing {snippet!r}")
        with zipfile.ZipFile(windows) as package:
            if f"spoiled-milk-{VERSION}-windows-x64/game-files/runtime/bin/java.exe" not in package.namelist():
                fail("Windows package must contain its bundled runtime")
            if f"spoiled-milk-{VERSION}-windows-x64/game-files/update-spoiled-milk.sh" in package.namelist():
                fail("Windows package should use the PowerShell updater, not the POSIX shell updater")

        with tempfile.TemporaryDirectory(prefix="spoiled-launch-test-") as launch_temp:
            launch_root = Path(launch_temp)
            with zipfile.ZipFile(generic) as package:
                package.extractall(launch_root)
            game_dir = launch_root / f"spoiled-milk-{VERSION}-java"
            write(game_dir / "game-files" / "update-spoiled-milk.sh", "#!/usr/bin/env sh\nexit 42\n")
            fake_bin = launch_root / "bin"
            write(fake_bin / "java", "#!/usr/bin/env sh\nprintf 'java reached %s\\n' \"$*\"\n")
            os.chmod(fake_bin / "java", 0o755)
            launcher_result = subprocess.run(
                ["sh", str(game_dir / "play-spoiled-milk.sh")],
                cwd=launch_root,
                env={**os.environ, "PATH": f"{fake_bin}{os.pathsep}{os.environ['PATH']}"},
                capture_output=True,
                text=True,
            )
            if launcher_result.returncode != 0:
                fail(f"shell launcher should continue after updater failure:\n{launcher_result.stdout}{launcher_result.stderr}")
            if "Update check failed; launching installed Spoiled Milk client." not in launcher_result.stderr:
                fail("shell launcher should warn when the updater fails")
            if "java reached" not in launcher_result.stdout or "-jar" not in launcher_result.stdout:
                fail(f"shell launcher did not reach java after updater failure:\n{launcher_result.stdout}{launcher_result.stderr}")

        expected = {}
        for line in checksum_file.read_text(encoding="utf-8").splitlines():
            digest, name = line.split(maxsplit=1)
            expected[name] = digest
        for archive in [generic, windows]:
            digest = hashlib.sha256(archive.read_bytes()).hexdigest()
            if expected.get(archive.name) != digest:
                fail(f"checksum output does not match {archive.name}")


def test_release_gates_are_enforced() -> None:
    makefile = (ROOT / "Makefile").read_text(encoding="utf-8")
    if 'ASSETS_CLEARED=1' not in makefile or 'test "$(ASSETS_CLEARED)" = "1"' not in makefile:
        fail("Make packaging target must require explicit asset redistribution acknowledgement")
    if "download-windows-jre" not in makefile:
        fail("Makefile must expose the Windows JRE download helper")
    if "./scripts/ai-manager.sh release" not in makefile:
        fail("Make packaging target must use the manager roundup gate")

    with tempfile.TemporaryDirectory(prefix="spoiled-release-skip-gate-") as temp_dir:
        fixture = Path(temp_dir)
        runtime = make_fixture(fixture)
        result = run_packager(fixture, runtime, "--assets-cleared", test_mode=False)
        if result.returncode == 0 or "restricted to packaging regression tests" not in result.stderr:
            fail("packager must reject --skip-build outside an explicit test capability")

    with tempfile.TemporaryDirectory(prefix="spoiled-release-gates-") as temp_dir:
        fixture = Path(temp_dir)
        runtime = make_fixture(fixture)
        result = run_packager(fixture, runtime)
        if result.returncode == 0 or "--assets-cleared" not in result.stderr:
            fail("packager must require asset redistribution acknowledgement")

    with tempfile.TemporaryDirectory(prefix="spoiled-release-runtime-") as temp_dir:
        fixture = Path(temp_dir)
        runtime = make_fixture(fixture, "1.8.0_275")
        result = run_packager(fixture, runtime, "--assets-cleared")
        if result.returncode == 0 or "Java 17+" not in result.stderr:
            fail("packager must reject the obsolete Java 8 Windows runtime")

    with tempfile.TemporaryDirectory(prefix="spoiled-release-dirty-") as temp_dir:
        fixture = Path(temp_dir)
        runtime = make_fixture(fixture)
        write(fixture / "uncommitted-release-note.txt", "not committed\n")
        result = run_packager(fixture, runtime, "--assets-cleared")
        if result.returncode == 0 or "clean manager main worktree" not in result.stderr:
            fail("packager must reject dirty manager-main state")

    with tempfile.TemporaryDirectory(prefix="spoiled-release-branch-") as temp_dir:
        fixture = Path(temp_dir)
        runtime = make_fixture(fixture)
        git(fixture, "switch", "-c", "ai-workspace-01")
        result = run_packager(fixture, runtime, "--assets-cleared")
        if result.returncode == 0 or "manager branch main" not in result.stderr:
            fail("packager must reject an AI workspace branch")

    with tempfile.TemporaryDirectory(prefix="spoiled-release-operation-") as temp_dir:
        fixture = Path(temp_dir)
        runtime = make_fixture(fixture)
        write(fixture / ".git" / "MERGE_HEAD", f"{git(fixture, 'rev-parse', 'HEAD')}\n")
        result = run_packager(fixture, runtime, "--assets-cleared")
        if result.returncode == 0 or "in-progress Git merge operation" not in result.stderr:
            fail("packager must reject an in-progress Git operation")

    with tempfile.TemporaryDirectory(prefix="spoiled-release-head-") as temp_dir:
        fixture = Path(temp_dir)
        runtime = make_fixture(fixture)
        git(fixture, "commit", "--allow-empty", "-m", "Unpushed release candidate")
        result = run_packager(fixture, runtime, "--assets-cleared")
        if result.returncode == 0 or "HEAD to match spoiled-milk/main" not in result.stderr:
            fail("packager must reject a source revision that is not spoiled-milk/main")

    with tempfile.TemporaryDirectory(prefix="spoiled-release-build-mutation-") as temp_dir:
        fixture = Path(temp_dir)
        runtime = make_fixture(
            fixture,
            build_script=(
                "#!/usr/bin/env bash\n"
                "set -euo pipefail\n"
                "printf 'build mutation\\n' >> \"$ROOT_DIR/LICENSE\"\n"
            ),
        )
        result = run_packager(fixture, runtime, "--assets-cleared", skip_build=False)
        if result.returncode == 0 or "clean manager main worktree" not in result.stderr:
            fail("packager must recheck source state after the client build")


def test_player_launch_defaults_to_renderer_v2() -> None:
    defaults = (ROOT / "PC_Client" / "src" / "orsc" / "RendererRuntimeDefaults.java").read_text(encoding="utf-8")
    for flag in RENDERER_V2_FLAGS:
        property_name, value = flag.removeprefix("-D").split("=", 1)
        if f'"{property_name}"' not in defaults or f'"{value}"' not in defaults:
            fail(f"RendererRuntimeDefaults is missing player default {flag}")

    applet = (ROOT / "PC_Client" / "src" / "orsc" / "ORSCApplet.java").read_text(encoding="utf-8")
    direct_default = applet.index("RendererRuntimeDefaults.apply();")
    direct_flag = applet.index("DIRECT_FRAMEBUFFER_ENABLED")
    if direct_default > direct_flag:
        fail("renderer defaults must be applied before ORSCApplet reads the direct framebuffer flag")

    openrsc = (ROOT / "PC_Client" / "src" / "orsc" / "OpenRSC.java").read_text(encoding="utf-8")
    if "RendererRuntimeDefaults.apply();" not in openrsc:
        fail("OpenRSC main should apply renderer defaults before creating the scaled window")


def test_windows_runtime_download_helper_is_documented() -> None:
    helper = ROOT / "scripts" / "download-windows-jre.sh"
    text = helper.read_text(encoding="utf-8")
    for snippet in [
        "https://api.adoptium.net/v3/binary/latest/${JAVA_VERSION}/ga/windows/x64/jre/hotspot/normal/eclipse",
        "bin/java.exe",
        "legal/java.base/LICENSE",
        ".sha256.txt",
        "CHECKSUM_URL",
        "Downloaded archive checksum did not match Adoptium checksum",
        "JAVA_VERSION",
        "sha256sum",
        "temurin-${JAVA_VERSION}-windows-x64-jre",
    ]:
        if snippet not in text:
            fail(f"Windows JRE helper is missing required behavior: {snippet!r}")

    checklist = (ROOT / "docs" / "releases" / "RELEASE-CHECKLIST.md").read_text(encoding="utf-8")
    for snippet in [
        "./scripts/download-windows-jre.sh",
        "--windows-jre output/runtimes/temurin-17-windows-x64-jre",
        "Windows is only needed for final",
    ]:
        if snippet not in checklist:
            fail(f"release checklist must document Linux-side Windows JRE packaging: {snippet!r}")


def test_uid_first_run_state_is_not_packaged_or_reused() -> None:
    tracked_uid = subprocess.run(
        ["git", "ls-files", "--error-unmatch", "Client_Base/Cache/uid.dat"],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    if tracked_uid.returncode == 0:
        fail("tracked blank uid.dat must not remain in the release client cache")

    source = (ROOT / "Client_Base" / "src" / "orsc" / "mudclient.java").read_text(encoding="utf-8")
    for snippet in [
        "storedValue != null",
        "storedUID != 0L",
        "generatedUID == 0L",
        "new FileOutputStream(uID)",
    ]:
        if snippet not in source:
            fail(f"UID initialization is missing required behavior: {snippet!r}")

    config = (ROOT / "Client_Base" / "src" / "orsc" / "Config.java").read_text(encoding="utf-8")
    if 'WINDOW_TITLE = "Spoiled Milk"' not in config:
        fail("standalone client should use Spoiled Milk as its initial window title")


def test_runtime_visual_assets_are_embedded_in_client_jar() -> None:
    build = subprocess.run(
        ["./scripts/build-client.sh"],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    if build.returncode != 0:
        fail(f"client build failed while checking embedded assets:\n{build.stdout}{build.stderr}")

    required = {
        "myworld-assets/animations/projectile-moving/acid-basic/Acid VFX 01.png",
        "myworld-assets/animations/projectile-moving/earth-basic/Earth projectile Spritesheet .png",
        "myworld-assets/animations/projectile-moving/fire-basic/Firebolt SpriteSheet.png",
        "myworld-assets/animations/projectile-moving/ice-basic/IceVFX 1 Repeatable.png",
        "myworld-assets/animations/projectile-moving/thunder-basic/Thunder ball wo blur.png",
        "myworld-assets/animations/projectile-moving/water-basic/WaterBall - Startup and Infinite.png",
        "myworld-assets/animations/projectile-moving/wind-basic/Projectile 2.png",
        "myworld-assets/animations/projectile-moving/wood-basic/Wood VFX 01 Repeatable.png",
        "myworld-assets/animations/projectile-moving/holy-basic/Holy VFX 01 Repeatable.png",
        "myworld-assets/animations/projectile-moving/arrow-basic/arrow.png",
        "myworld-assets/animations/projectile-moving/bolt-basic/bolt-basic.png",
        "myworld-assets/animations/projectile-moving/dart-basic/dart.png",
        "myworld-assets/animations/projectile-moving/throwing-knife-basic/throwing-knife-basic.png",
        "myworld-assets/animations/projectile-moving/shuriken-basic/shuriken-basic.png",
        "myworld-assets/sprites/equipment/fishing-pole/numbered/00.png",
        "myworld-assets/sprites/items/inventory-ground/bolt.png",
        "myworld-assets/sprites/items/inventory-ground/dragon-hatchet.png",
        "myworld-assets/sprites/items/inventory-ground/weapons/demon-pitchfork-icon.png",
        "myworld-assets/sprites/items/inventory-ground/weapons/earth-sword-icon.png",
        "myworld-assets/sprites/items/inventory-ground/weapons/fire-sword-icon.png",
        "myworld-assets/sprites/items/inventory-ground/weapons/guthix-mace-icon.png",
        "myworld-assets/sprites/items/inventory-ground/weapons/ice-sword-icon.png",
        "myworld-assets/sprites/items/inventory-ground/weapons/saradomin-mace-icon.png",
        "myworld-assets/sprites/items/inventory-ground/weapons/zamorak-mace-icon.png",
        "myworld-assets/sprites/items/inventory-ground/geode.png",
        "myworld-assets/sprites/items/inventory-ground/geode@14x14.png",
        "myworld-assets/sprites/items/inventory-ground/geode@18x18.png",
        "myworld-assets/sprites/items/inventory-ground/geode@24x24.png",
        "myworld-assets/sprites/items/inventory-ground/geode@30x30.png",
        "myworld-assets/sprites/items/inventory-ground/guthix-symbol-mould.png",
        "myworld-assets/sprites/items/inventory-ground/hatchet-generic.png",
        "myworld-assets/sprites/items/inventory-ground/hood.png",
        "myworld-assets/sprites/items/inventory-ground/hood@35x27.png",
        "myworld-assets/sprites/items/inventory-ground/raw-dragon-metal.png",
        "myworld-assets/sprites/items/inventory-ground/resources/blue-flower.png",
        "myworld-assets/sprites/items/inventory-ground/resources/fern-leaf.png",
        "myworld-assets/sprites/items/inventory-ground/resources/fungus.png",
        "myworld-assets/sprites/items/inventory-ground/resources/mushroom.png",
        "myworld-assets/sprites/items/inventory-ground/resources/red-flower.png",
        "myworld-assets/sprites/items/inventory-ground/shuriken-basic.png",
        "myworld-assets/sprites/items/inventory-ground/shuriken-basic-poison.png",
        "myworld-assets/sprites/items/inventory-ground/shuriken-thrown.png",
        "myworld-assets/sprites/items/inventory-ground/symbol-of-guthix.png",
        "myworld-assets/sprites/items/inventory-ground/unblessed-symbol-of-guthix.png",
        "myworld-assets/sprites/items/inventory-ground/unstrung-symbol-of-guthix.png",
        "myworld-assets/sprites/items/inventory-ground/zombie-eye.png",
        "myworld-assets/sprites/items/inventory-ground/tools/arrowhead-mould.png",
        "myworld-assets/sprites/items/inventory-ground/tools/bolt-mould.png",
        "myworld-assets/sprites/items/inventory-ground/tools/dart-mould.png",
        "myworld-assets/sprites/items/inventory-ground/tools/shuriken-mould.png",
        "myworld-assets/sprites/items/inventory-ground/tools/throwing-knife-mould.png",
    }
    required |= CLERIC_SIGIL_JAR_ENTRIES
    with zipfile.ZipFile(ROOT / "Client_Base" / "Open_RSC_Client.jar") as jar:
        names = set(jar.namelist())
        missing = required - names
    if missing:
        fail(f"client jar is missing embedded runtime art: {sorted(missing)}")
    forbidden_prefixes = [
        "myworld-assets/sprites/ui/",
        "myworld-assets/animations/On Enemy/phoenix/",
        "myworld-assets/animations/On Enemy/kraken/",
        "myworld-assets/legacy animation folder/On Enemy/phoenix/",
        "myworld-assets/legacy animation folder/On Enemy/kraken/",
    ]
    included_forbidden = sorted(
        name for name in names if any(name.startswith(prefix) for prefix in forbidden_prefixes)
    )
    if included_forbidden:
        fail(f"client jar still contains removed CraftPix art: {included_forbidden[:5]}")
    allowed_item_asset_names = {
        "myworld-assets/sprites/items/inventory-ground/bangle.png",
        "myworld-assets/sprites/items/inventory-ground/bolt.png",
        "myworld-assets/sprites/items/inventory-ground/dragon-hatchet.png",
        "myworld-assets/sprites/items/inventory-ground/weapons/demon-pitchfork-icon.png",
        "myworld-assets/sprites/items/inventory-ground/weapons/earth-sword-icon.png",
        "myworld-assets/sprites/items/inventory-ground/weapons/fire-sword-icon.png",
        "myworld-assets/sprites/items/inventory-ground/weapons/guthix-mace-icon.png",
        "myworld-assets/sprites/items/inventory-ground/weapons/ice-sword-icon.png",
        "myworld-assets/sprites/items/inventory-ground/weapons/saradomin-mace-icon.png",
        "myworld-assets/sprites/items/inventory-ground/weapons/zamorak-mace-icon.png",
        "myworld-assets/sprites/items/inventory-ground/geode.png",
        "myworld-assets/sprites/items/inventory-ground/geode@14x14.png",
        "myworld-assets/sprites/items/inventory-ground/geode@18x18.png",
        "myworld-assets/sprites/items/inventory-ground/geode@24x24.png",
        "myworld-assets/sprites/items/inventory-ground/geode@30x30.png",
        "myworld-assets/sprites/items/inventory-ground/guthix-symbol-mould.png",
        "myworld-assets/sprites/items/inventory-ground/hatchet-generic.png",
        "myworld-assets/sprites/items/inventory-ground/hood.png",
        "myworld-assets/sprites/items/inventory-ground/hood@35x27.png",
        "myworld-assets/sprites/items/inventory-ground/medallion.png",
        "myworld-assets/sprites/items/inventory-ground/raw-dragon-metal.png",
        "myworld-assets/sprites/items/inventory-ground/resources/blue-flower.png",
        "myworld-assets/sprites/items/inventory-ground/resources/fern-leaf.png",
        "myworld-assets/sprites/items/inventory-ground/resources/fungus.png",
        "myworld-assets/sprites/items/inventory-ground/resources/mushroom.png",
        "myworld-assets/sprites/items/inventory-ground/resources/red-flower.png",
        "myworld-assets/sprites/items/inventory-ground/shuriken-basic.png",
        "myworld-assets/sprites/items/inventory-ground/shuriken-basic-poison.png",
        "myworld-assets/sprites/items/inventory-ground/shuriken-thrown.png",
        "myworld-assets/sprites/items/inventory-ground/symbol-of-guthix.png",
        "myworld-assets/sprites/items/inventory-ground/unblessed-symbol-of-guthix.png",
        "myworld-assets/sprites/items/inventory-ground/unstrung-symbol-of-guthix.png",
        "myworld-assets/sprites/items/inventory-ground/zombie-eye.png",
        "myworld-assets/sprites/items/inventory-ground/tools/arrowhead-mould.png",
        "myworld-assets/sprites/items/inventory-ground/tools/bangle-mould.png",
        "myworld-assets/sprites/items/inventory-ground/tools/bolt-mould.png",
        "myworld-assets/sprites/items/inventory-ground/tools/dart-mould.png",
        "myworld-assets/sprites/items/inventory-ground/tools/shuriken-mould.png",
        "myworld-assets/sprites/items/inventory-ground/tools/throwing-knife-mould.png",
    }
    allowed_item_asset_names |= CLERIC_SIGIL_JAR_ENTRIES
    unexpected_item_assets = sorted(
        name for name in names
        if name.startswith("myworld-assets/sprites/items/")
        and name.lower().endswith(".png")
        and name not in allowed_item_asset_names
    )
    if unexpected_item_assets:
        fail(f"client jar contains unexpected item art: {unexpected_item_assets[:5]}")

    for removed_path in [
        ROOT / "dev/myworld/assets/sprites/ui",
        ROOT / "dev/myworld/assets/archive",
        ROOT / "dev/myworld/assets/animations/On Enemy/phoenix",
        ROOT / "dev/myworld/assets/animations/On Enemy/kraken",
        ROOT / "dev/myworld/assets/legacy animation folder/On Enemy/phoenix",
        ROOT / "dev/myworld/assets/legacy animation folder/On Enemy/kraken",
    ]:
        if removed_path.exists():
            fail(f"removed CraftPix source path still exists: {removed_path.relative_to(ROOT)}")
    allowed_item_sprite_paths = {
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/bangle.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/bolt.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/dragon-hatchet.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/weapons/demon-pitchfork-icon.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/weapons/earth-sword-icon.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/weapons/fire-sword-icon.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/weapons/guthix-mace-icon.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/weapons/ice-sword-icon.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/weapons/saradomin-mace-icon.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/weapons/zamorak-mace-icon.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/geode.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/geode@14x14.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/geode@18x18.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/geode@24x24.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/geode@30x30.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/guthix-symbol-mould.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/hatchet-generic.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/hood.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/hood@35x27.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/medallion.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/raw-dragon-metal.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/resources/blue-flower.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/resources/fern-leaf.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/resources/fungus.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/resources/mushroom.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/resources/red-flower.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/shuriken-basic.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/shuriken-basic-poison.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/shuriken-thrown.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/symbol-of-guthix.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/unblessed-symbol-of-guthix.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/unstrung-symbol-of-guthix.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/zombie-eye.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/tools/arrowhead-mould.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/tools/bangle-mould.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/tools/bolt-mould.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/tools/dart-mould.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/tools/shuriken-mould.png",
        ROOT / "dev/myworld/assets/sprites/items/inventory-ground/tools/throwing-knife-mould.png",
    }
    allowed_item_sprite_paths |= CLERIC_SIGIL_SOURCE_PATHS
    item_sprite_root = ROOT / "dev/myworld/assets/sprites/items"
    if item_sprite_root.exists():
        unexpected_item_sprites = sorted(
            path.relative_to(ROOT).as_posix()
            for path in item_sprite_root.rglob("*.png")
            if path not in allowed_item_sprite_paths
        )
        if unexpected_item_sprites:
            fail(f"unexpected item sprite source files are present: {unexpected_item_sprites[:5]}")

    source = (ROOT / "Client_Base" / "src" / "orsc" / "ClientExternalAssetLoader.java").read_text(
        encoding="utf-8"
    )
    for snippet in ["EMBEDDED_ASSET_ROOT", "readAssetImage", "assetDirectoryExists"]:
        if snippet not in source:
            fail(f"runtime visual asset resource loading is missing {snippet}")


def main() -> None:
    test_packager_rejects_localhost_player_endpoint()
    test_packaged_archives_are_clean_and_configured()
    test_release_gates_are_enforced()
    test_windows_runtime_download_helper_is_documented()
    test_uid_first_run_state_is_not_packaged_or_reused()
    test_runtime_visual_assets_are_embedded_in_client_jar()
    print("PASS: player release packaging, embedded art, and first-run UID handling validated")


if __name__ == "__main__":
    main()
