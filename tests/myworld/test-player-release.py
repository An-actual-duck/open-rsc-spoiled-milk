#!/usr/bin/env python3
import hashlib
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


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def write(path: Path, contents: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(contents, encoding="utf-8")


def make_fixture(fixture: Path, java_version: str = "17.0.13") -> Path:
    client_jar = fixture / "Client_Base" / "Open_RSC_Client.jar"
    client_jar.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(client_jar, "w") as jar:
        jar.writestr("myworld-assets/animations/Projectiles/fixture/frame.png", "asset")
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
    return runtime


def run_packager(fixture: Path, runtime: Path, *extra: str) -> subprocess.CompletedProcess[str]:
    env = dict(os.environ)
    env["ROOT_DIR"] = str(fixture)
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
            "--skip-build",
            *extra,
        ],
        cwd=ROOT,
        env=env,
        capture_output=True,
        text=True,
    )


def test_packaged_archives_are_clean_and_configured() -> None:
    with tempfile.TemporaryDirectory(prefix="spoiled-release-test-") as temp_dir:
        fixture = Path(temp_dir)
        runtime = make_fixture(fixture)
        result = run_packager(fixture, runtime, "--assets-cleared")
        if result.returncode != 0:
            fail(f"release packager failed:\n{result.stdout}{result.stderr}")

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
                required = {
                    f"{package_name}/Spoiled_Milk_Client.jar",
                    f"{package_name}/Cache/ip.txt",
                    f"{package_name}/Cache/port.txt",
                    f"{package_name}/Cache/audio/audio.dat",
                    f"{package_name}/Cache/video/video.dat",
                    f"{package_name}/LICENSE",
                    f"{package_name}/README.txt",
                    f"{package_name}/ASSET-SOURCES.txt",
                    f"{package_name}/Play Spoiled Milk.cmd",
                }
                missing = required - names
                if missing:
                    fail(f"{archive.name} is missing {sorted(missing)}")

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

                if package.read(f"{package_name}/Cache/ip.txt").decode() != "alpha.example.test\n":
                    fail(f"{archive.name} does not carry the requested endpoint host")
                if package.read(f"{package_name}/Cache/port.txt").decode() != "43605\n":
                    fail(f"{archive.name} does not carry the requested endpoint port")

        with zipfile.ZipFile(generic) as package:
            if f"spoiled-milk-{VERSION}-java/play-spoiled-milk.sh" not in package.namelist():
                fail("generic Java package must contain its shell launcher")
        with zipfile.ZipFile(windows) as package:
            if f"spoiled-milk-{VERSION}-windows-x64/runtime/bin/java.exe" not in package.namelist():
                fail("Windows package must contain its bundled runtime")

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
    uid_file = ROOT / "Client_Base" / "Cache" / "uid.dat"
    if uid_file.exists():
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
        "myworld-assets/animations/Projectiles/thunder-ball/thunder-ball1.png",
        "myworld-assets/sprites/equipment/fishing-pole/numbered/00.png",
    }
    with zipfile.ZipFile(ROOT / "Client_Base" / "Open_RSC_Client.jar") as jar:
        names = set(jar.namelist())
        missing = required - names
    if missing:
        fail(f"client jar is missing embedded runtime art: {sorted(missing)}")
    forbidden_prefixes = [
        "myworld-assets/sprites/ui/",
        "myworld-assets/sprites/items/",
        "myworld-assets/sprites/world/",
        "myworld-assets/animations/On Enemy/phoenix/",
        "myworld-assets/animations/On Enemy/kraken/",
    ]
    included_forbidden = sorted(
        name for name in names if any(name.startswith(prefix) for prefix in forbidden_prefixes)
    )
    if included_forbidden:
        fail(f"client jar still contains removed CraftPix art: {included_forbidden[:5]}")

    for removed_path in [
        ROOT / "dev/myworld/assets/sprites/ui",
        ROOT / "dev/myworld/assets/sprites/items",
        ROOT / "dev/myworld/assets/sprites/world",
        ROOT / "dev/myworld/assets/archive",
        ROOT / "dev/myworld/assets/animations/On Enemy/phoenix",
        ROOT / "dev/myworld/assets/animations/On Enemy/kraken",
    ]:
        if removed_path.exists():
            fail(f"removed CraftPix source path still exists: {removed_path.relative_to(ROOT)}")

    source = (ROOT / "Client_Base" / "src" / "orsc" / "mudclient.java").read_text(encoding="utf-8")
    for snippet in ["EMBEDDED_ASSET_ROOT", "readAssetImage", "assetDirectoryExists"]:
        if snippet not in source:
            fail(f"runtime visual asset resource loading is missing {snippet}")


def main() -> None:
    test_packaged_archives_are_clean_and_configured()
    test_release_gates_are_enforced()
    test_windows_runtime_download_helper_is_documented()
    test_uid_first_run_state_is_not_packaged_or_reused()
    test_runtime_visual_assets_are_embedded_in_client_jar()
    print("PASS: player release packaging, embedded art, and first-run UID handling validated")


if __name__ == "__main__":
    main()
