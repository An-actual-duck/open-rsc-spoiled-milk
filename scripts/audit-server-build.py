#!/usr/bin/env python3
"""Inventory and validate the shipped Ant server build path.

Gradle declarations are reported for drift visibility, but they are never used
as the expected dependency set. Run after ``scripts/build-server.sh`` with
``--check --require-artifacts`` for the strongest local validation.
"""

import argparse
import hashlib
import json
import re
import xml.etree.ElementTree as ET
import zipfile
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / "server"
BUILD_XML = SERVER / "build.xml"
BUILD_GRADLE = SERVER / "build.gradle"
LIB = SERVER / "lib"
CORE_JAR = SERVER / "core.jar"
PLUGINS_JAR = SERVER / "plugins.jar"
GAMEPLAY_OVERLAY_JAR = SERVER / "core-gameplay-overlay.jar"
WORLD_BUILDER_RUNTIME_JAR = SERVER / "world-builder-runtime/world-builder-managed-runtime.jar"
GAMEPLAY_OVERLAY_CLASS_STEMS = (
    "com/openrsc/server/model/container/BankPreset",
    "com/openrsc/server/model/container/Inventory",
    "com/openrsc/server/net/rsc/handlers/BankHandler",
    "com/openrsc/server/net/rsc/handlers/ItemEquip",
    "com/openrsc/server/net/rsc/handlers/ItemUnequip",
    "com/openrsc/server/net/rsc/handlers/ItemUseOnGroundItem",
    "com/openrsc/server/net/rsc/handlers/ItemUseOnItem",
    "com/openrsc/server/net/rsc/handlers/ItemUseOnNpc",
    "com/openrsc/server/net/rsc/handlers/ItemUseOnObject",
    "com/openrsc/server/net/rsc/handlers/PlayerTradeHandler",
)
TEST_ONLY_CLASS_PREFIXES = (
    "com/openrsc/server/combat/CurrentCombat",
)

ANT_TARGETS = ("compile_core", "compile_plugins", "runserver", "runserverzgc")
AUTHORITATIVE_SCRIPTS = {
    "scripts/build-server.sh": (
        "myworld_ant_build compile_core",
        "myworld_ant_build compile_plugins",
        "audit-server-build.py\" --check --require-artifacts",
    ),
    "scripts/run-server.sh": ("myworld_ant_server compile-and-run",),
    "scripts/run-server-zgc.sh": ("myworld_ant_server runserverzgc",),
    "scripts/run-hosted-server.sh": ("myworld_ant_server compile-and-run",),
    "scripts/lib/myworld-common.sh": ("tools/vendor/apache-ant-1.10.5", "myworld_ant_build()", "myworld_ant_server()"),
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def substitute(value: str, properties: dict[str, str]) -> str:
    for key, replacement in properties.items():
        value = value.replace("${" + key + "}", replacement)
    return value.rstrip("/")


def ant_inventory() -> dict:
    root = ET.parse(BUILD_XML).getroot()
    properties = {
        node.attrib["name"]: node.attrib["location"]
        for node in root.findall("property")
        if "name" in node.attrib and "location" in node.attrib
    }
    targets = {}
    missing = []

    def classpath_entry(element: ET.Element, target_name: str) -> dict:
        raw = element.attrib.get("location", element.attrib.get("path", ""))
        resolved = substitute(raw, properties)
        record = {"declared": raw, "resolved": resolved, "kind": "other", "exists": None}
        if resolved == "lib/*":
            record["kind"] = "jar-wildcard"
            record["matches"] = sorted(path.name for path in LIB.glob("*.jar"))
        elif resolved.endswith(".jar"):
            record["kind"] = "jar"
            path = SERVER / resolved
            record["exists"] = path.is_file()
            if not path.is_file():
                missing.append({"target": target_name, "path": resolved})
        return record

    for target_name in ANT_TARGETS:
        target = root.find(f"target[@name='{target_name}']")
        if target is None:
            targets[target_name] = []
            continue
        entries = []
        for classpath in target.findall(".//classpath"):
            refid = classpath.attrib.get("refid")
            if refid:
                referenced = root.find(f"path[@id='{refid}']")
                if referenced is None:
                    missing.append({"target": target_name, "path": "refid:" + refid})
                else:
                    entries.extend(
                        classpath_entry(element, target_name)
                        for element in referenced.findall("pathelement")
                    )
            entries.extend(
                classpath_entry(element, target_name)
                for element in classpath.findall("pathelement")
            )
        targets[target_name] = entries

    zipgroup = root.find("target[@name='compile_core']/.//zipgroupfileset")
    return {
        "build_file": str(BUILD_XML.relative_to(ROOT)),
        "default_target": root.attrib.get("default"),
        "source_roots": {"core": properties.get("src", "src"), "plugins": "plugins"},
        "artifacts": {"core": properties.get("jar", "core.jar"), "plugins": "plugins.jar"},
        "core_fat_jar_input": None if zipgroup is None else {
            "directory": zipgroup.attrib.get("dir"),
            "includes": zipgroup.attrib.get("includes"),
        },
        "targets": targets,
        "missing_explicit_entries": missing,
    }


def gradle_inventory() -> dict:
    lines = BUILD_GRADLE.read_text(encoding="utf-8").splitlines()
    declarations = []
    for line_number, line in enumerate(lines, 1):
        stripped = line.strip()
        if stripped.startswith(("implementation ", "implementation(", "testImplementation ", "testRuntimeOnly ")):
            declarations.append({"line": line_number, "declaration": stripped})
    source_dirs = re.findall(r'srcDirs\s+"([^"]+)"', "\n".join(lines))
    return {
        "build_file": str(BUILD_GRADLE.relative_to(ROOT)),
        "authority": "secondary/non-authoritative",
        "source_roots": source_dirs,
        "declarations": declarations,
        "imports_ant_build": 'ant.importBuild("build.xml")' in "\n".join(lines),
    }


def jar_classes(path: Path) -> set[str]:
    with zipfile.ZipFile(path) as archive:
        return {name for name in archive.namelist() if name.endswith(".class")}


def library_inventory(core_classes: set[str] | None) -> tuple[list[dict], dict]:
    libraries = []
    class_occurrences = Counter()
    for path in sorted(LIB.glob("*.jar")):
        classes = jar_classes(path)
        class_occurrences.update(classes)
        libraries.append({
            "file": path.name,
            "bytes": path.stat().st_size,
            "sha256": sha256(path),
            "class_entries": len(classes),
            "classes_also_in_core": None if core_classes is None else len(classes & core_classes),
        })
    duplicates = sum(count - 1 for count in class_occurrences.values() if count > 1)
    union = set(class_occurrences)
    return libraries, {
        "library_count": len(libraries),
        "unique_external_classes": len(union),
        "duplicate_class_entries_between_libraries": duplicates,
        "external_classes_also_in_core": None if core_classes is None else len(union & core_classes),
    }


def artifact_inventory(require_artifacts: bool) -> tuple[dict, list[str]]:
    errors = []
    result = {}
    for label, path in (("core", CORE_JAR), ("plugins", PLUGINS_JAR)):
        if not path.is_file():
            result[label] = {"file": path.name, "present": False}
            if require_artifacts:
                errors.append(f"missing {path.relative_to(ROOT)}; run scripts/build-server.sh")
            continue
        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8", errors="replace")
        result[label] = {
            "file": path.name,
            "present": True,
            "bytes": path.stat().st_size,
            "sha256": sha256(path),
            "entries": len(names),
            "class_entries": sum(name.endswith(".class") for name in names),
            "main_class": "com.openrsc.server.Server" if "Main-Class: com.openrsc.server.Server" in manifest else None,
        }

    if not GAMEPLAY_OVERLAY_JAR.is_file():
        result["gameplay_overlay"] = {"file": GAMEPLAY_OVERLAY_JAR.name, "present": False}
        if require_artifacts:
            errors.append(
                f"missing {GAMEPLAY_OVERLAY_JAR.relative_to(ROOT)}; run scripts/build-server.sh"
            )
    else:
        with zipfile.ZipFile(GAMEPLAY_OVERLAY_JAR) as archive:
            overlay_classes = {name for name in archive.namelist() if name.endswith(".class")}
            manifest = archive.read("META-INF/MANIFEST.MF").decode("utf-8", errors="replace")
        result["gameplay_overlay"] = {
            "file": GAMEPLAY_OVERLAY_JAR.name,
            "present": True,
            "bytes": GAMEPLAY_OVERLAY_JAR.stat().st_size,
            "sha256": sha256(GAMEPLAY_OVERLAY_JAR),
            "class_entries": len(overlay_classes),
            "main_class": "com.openrsc.server.Server" if "Main-Class: com.openrsc.server.Server" in manifest else None,
        }

        missing_stems = [
            stem for stem in GAMEPLAY_OVERLAY_CLASS_STEMS
            if stem + ".class" not in overlay_classes
        ]
        if missing_stems:
            errors.append(f"gameplay overlay is missing current Core classes: {missing_stems}")
        unexpected = sorted(
            name for name in overlay_classes
            if not any(
                name == stem + ".class" or name.startswith(stem + "$")
                for stem in GAMEPLAY_OVERLAY_CLASS_STEMS
            )
        )
        if unexpected:
            errors.append(f"gameplay overlay contains unreviewed classes: {unexpected}")
        if CORE_JAR.is_file():
            with zipfile.ZipFile(CORE_JAR) as core, zipfile.ZipFile(GAMEPLAY_OVERLAY_JAR) as overlay:
                stale = sorted(
                    name for name in overlay_classes
                    if name not in core.namelist() or overlay.read(name) != core.read(name)
                )
            if stale:
                errors.append(f"gameplay overlay does not match the current core.jar: {stale}")

    if result.get("core", {}).get("present"):
        core_classes = jar_classes(CORE_JAR)
        for prefix in TEST_ONLY_CLASS_PREFIXES:
            if any(name.startswith(prefix) for name in core_classes):
                errors.append(f"core.jar unexpectedly contains test fixture {prefix}")
        for required in (
            "com/openrsc/server/Server.class",
            "com/openrsc/server/plugins/io/PluginJarLoader.class",
            "com/lmax/disruptor/RingBuffer.class",
            "com/thoughtworks/xstream/XStream.class",
            "org/xmlpull/v1/XmlPullParser.class",
        ):
            if required not in core_classes:
                errors.append(f"core.jar missing required class {required}")
    if result.get("plugins", {}).get("present"):
        plugin_classes = jar_classes(PLUGINS_JAR)
        for prefix in TEST_ONLY_CLASS_PREFIXES:
            if any(name.startswith(prefix) for name in plugin_classes):
                errors.append(f"plugins.jar unexpectedly contains test fixture {prefix}")
        if not plugin_classes:
            errors.append("plugins.jar contains no plugin classes")
        if "com/openrsc/server/Server.class" in plugin_classes:
            errors.append("plugins.jar unexpectedly contains core Server.class")
        if not any(name.startswith("com/openrsc/server/plugins/authentic/") for name in plugin_classes):
            errors.append("plugins.jar contains no authentic plugin classes")
        if not any(name.startswith("com/openrsc/server/plugins/custom/myworld/") for name in plugin_classes):
            errors.append("plugins.jar contains no MyWorld plugin classes")
    return result, errors


def script_inventory() -> tuple[list[dict], list[str]]:
    records = []
    errors = []
    for relative, required_fragments in AUTHORITATIVE_SCRIPTS.items():
        path = ROOT / relative
        text = path.read_text(encoding="utf-8") if path.is_file() else ""
        missing = [fragment for fragment in required_fragments if fragment not in text]
        records.append({"file": relative, "required_fragments_present": not missing})
        if missing:
            errors.append(f"{relative} no longer follows the bundled Ant path: {missing}")
        if "gradlew" in text or " gradle " in text:
            errors.append(f"{relative} unexpectedly invokes Gradle")
    return records, errors


def build_report(require_artifacts: bool) -> tuple[dict, list[str]]:
    ant = ant_inventory()
    artifacts, artifact_errors = artifact_inventory(require_artifacts)
    core_classes = jar_classes(CORE_JAR) if CORE_JAR.is_file() else None
    libraries, duplication = library_inventory(core_classes)
    scripts, script_errors = script_inventory()
    errors = [
        f"missing explicit Ant classpath entry {item['target']}: {item['path']}"
        for item in ant["missing_explicit_entries"]
    ]
    errors.extend(artifact_errors)
    errors.extend(script_errors)

    for target_name in ("runserver", "runserverzgc"):
        kinds = {entry["kind"] for entry in ant["targets"].get(target_name, [])}
        paths = {entry["resolved"] for entry in ant["targets"].get(target_name, [])}
        if "jar-wildcard" not in kinds:
            errors.append(f"{target_name} no longer includes shipped server/lib jars")
        if "core.jar" not in paths:
            errors.append(f"{target_name} no longer includes core.jar")
        required_order = (
            "core-gameplay-overlay.jar",
            str(WORLD_BUILDER_RUNTIME_JAR.relative_to(SERVER)),
            "core.jar",
        )
        ordered_paths = [entry["resolved"] for entry in ant["targets"].get(target_name, [])]
        if not all(path in ordered_paths for path in required_order):
            errors.append(f"{target_name} lost the managed runtime gameplay-overlay boundary")
        elif [ordered_paths.index(path) for path in required_order] != sorted(
                ordered_paths.index(path) for path in required_order):
            errors.append(
                f"{target_name} must load the gameplay overlay before the managed runtime and core.jar"
            )

    loader = SERVER / "src/com/openrsc/server/plugins/io/PluginJarLoader.java"
    loader_text = loader.read_text(encoding="utf-8")
    if 'final String pathToJar = "./plugins.jar";' not in loader_text:
        errors.append("plugin discovery no longer loads ./plugins.jar; review build authority documentation")

    report = {
        "authority": {
            "production": "bundled Ant 1.10.5 + server/build.xml",
            "gradle": "secondary/non-authoritative until parity is established",
        },
        "scripts": scripts,
        "ant": ant,
        "libraries": libraries,
        "fat_jar_duplication": duplication,
        "artifacts": artifacts,
        "gradle": gradle_inventory(),
        "validation_errors": errors,
    }
    return report, errors


def print_human(report: dict) -> None:
    print("Server build authority")
    print(f"  production: {report['authority']['production']}")
    print(f"  Gradle:     {report['authority']['gradle']}")
    print("\nActive Ant source roots and artifacts")
    ant = report["ant"]
    print(f"  {ant['source_roots']['core']} -> {ant['artifacts']['core']} (fat jar of server/lib/*.jar)")
    print(f"  {ant['source_roots']['plugins']} -> {ant['artifacts']['plugins']} (plugin classes)")
    print("\nShipped server/lib inventory")
    for library in report["libraries"]:
        overlap = library["classes_also_in_core"]
        overlap_text = "artifact not built" if overlap is None else f"{overlap} classes also in core.jar"
        print(f"  {library['file']}: {library['bytes']} bytes, {overlap_text}")
    duplication = report["fat_jar_duplication"]
    print(
        "\nFat/runtime duplication: "
        f"{duplication['external_classes_also_in_core']} external classes are also in core.jar; "
        f"{duplication['duplicate_class_entries_between_libraries']} duplicate class inputs exist between lib jars."
    )
    print("\nAnt target classpaths")
    for target, entries in ant["targets"].items():
        rendered = []
        for entry in entries:
            if entry["kind"] == "jar-wildcard":
                rendered.append(f"{entry['resolved']} ({len(entry['matches'])} jars)")
            else:
                rendered.append(entry["resolved"])
        print(f"  {target}: {', '.join(rendered)}")
    print("\nGradle declarations (reported, not expected)")
    for declaration in report["gradle"]["declarations"]:
        print(f"  L{declaration['line']}: {declaration['declaration']}")
    if report["validation_errors"]:
        print("\nValidation errors")
        for error in report["validation_errors"]:
            print(f"  - {error}")
    else:
        print("\nPASS: shipped Ant build/classpath inventory is internally consistent")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="exit nonzero on Ant-path validation errors")
    parser.add_argument("--require-artifacts", action="store_true", help="require built core.jar and plugins.jar")
    parser.add_argument("--json", action="store_true", help="emit the inventory as JSON")
    args = parser.parse_args()
    report, errors = build_report(args.require_artifacts)
    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        print_human(report)
    return 1 if args.check and errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
