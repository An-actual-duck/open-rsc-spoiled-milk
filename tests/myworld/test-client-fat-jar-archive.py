#!/usr/bin/env python3
import argparse
import os
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path
from zipfile import ZipFile


ROOT = Path(__file__).resolve().parents[2]
BUILD_XML = ROOT / "Client_Base/build.xml"
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
LWJGL_LIB = ROOT / "PC_Client/lib/lwjgl"
MODULES = ("lwjgl", "lwjgl-glfw", "lwjgl-opengl")
REQUIRED_ENTRIES = {
    "orsc/OpenRSC.class",
    "orsc/OpenGLFramePresenter.class",
    "org/lwjgl/Version.class",
    "org/lwjgl/glfw/GLFW.class",
    "myworld-assets/remastered-sprites/manifest.json",
    "myworld-assets/sprites/UI/summon/broodling-spider.png",
    "spoiled-milk-release-build.marker",
}


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def native_identity(path: Path) -> tuple[str, str] | None:
    for module in sorted(MODULES, key=len, reverse=True):
        prefix = module + "-"
        if not path.name.startswith(prefix) or not path.name.endswith(".jar"):
            continue
        version_and_classifier = path.name[len(prefix):-4]
        marker = "-natives-"
        if marker not in version_and_classifier:
            continue
        _, platform = version_and_classifier.split(marker, 1)
        return module, "natives-" + platform
    return None


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--require-native-classifier",
        action="append",
        default=[],
        help="Require all three LWJGL module jars for this classifier",
    )
    args = parser.parse_args()

    build = ET.parse(BUILD_XML).getroot()
    compile_target = build.find("target[@name='compile']")
    jar_task = None if compile_target is None else compile_target.find("jar")
    if jar_task is None:
        fail("Client Ant compile target is missing its jar task")
    if jar_task.get("duplicate") != "preserve":
        fail("Client fat JAR must preserve the first archive entry on dependency collisions")
    zipgroup = jar_task.find("zipgroupfileset")
    if zipgroup is None or zipgroup.get("includes") != "**/*.jar":
        fail("Client fat JAR no longer merges all nested desktop dependency jars")

    native_jars = sorted(LWJGL_LIB.glob("*-natives-*.jar"))
    classifiers: dict[str, set[str]] = {}
    expected_native_content: dict[str, bytes] = {}
    for native_jar in native_jars:
        identity = native_identity(native_jar)
        if identity is None:
            fail(f"Cannot identify native classifier from {native_jar.name}")
        module, classifier = identity
        classifiers.setdefault(classifier, set()).add(module)
        with ZipFile(native_jar) as archive:
            for name in archive.namelist():
                if name.endswith("/"):
                    continue
                if name.startswith(("linux/", "windows/", "macos/")) or (
                    name.startswith("META-INF/") and name.endswith(".sha1")
                ):
                    content = archive.read(name)
                    previous = expected_native_content.setdefault(name, content)
                    if previous != content:
                        fail(f"Native input jars disagree about duplicate entry {name}")

    for classifier in args.require_native_classifier:
        present_modules = classifiers.get(classifier, set())
        missing_modules = sorted(set(MODULES) - present_modules)
        if missing_modules:
            fail(f"Missing {classifier} input jars for: {', '.join(missing_modules)}")

    environment = dict(os.environ)
    environment["SPOILED_MILK_RELEASE_BUILD"] = "1"
    built = subprocess.run(
        [str(ROOT / "scripts/build-client.sh")],
        cwd=ROOT,
        env=environment,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=False,
    )
    if built.returncode != 0 or "BUILD SUCCESSFUL" not in built.stdout:
        fail("Release-marked client build failed:\n" + built.stdout)

    with ZipFile(CLIENT_JAR) as archive:
        names = archive.namelist()
        counts = Counter(names)
        duplicates = sorted(name for name, count in counts.items() if count > 1)
        if duplicates:
            fail(f"Client fat JAR contains duplicate paths: {duplicates}")
        missing = sorted((REQUIRED_ENTRIES | set(expected_native_content)) - set(names))
        if missing:
            fail(f"Client fat JAR is missing required entries: {missing}")
        if archive.read("spoiled-milk-release-build.marker") != b"release-build=true\n":
            fail("Client fat JAR contains an invalid release-build marker")
        changed_native_entries = sorted(
            name
            for name, content in expected_native_content.items()
            if archive.read(name) != content
        )
        if changed_native_entries:
            fail(f"Client fat JAR changed native entries: {changed_native_entries}")

    checked_classifiers = ", ".join(sorted(classifiers)) or "none"
    print(
        "PASS: client fat JAR paths are unique and required classes, assets, "
        f"release marker, and native inputs remain ({checked_classifiers})"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
