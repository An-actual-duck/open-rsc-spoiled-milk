#!/usr/bin/env python3

import json
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
AUDIT = ROOT / "scripts/audit-server-build.py"
BUILD_XML = ROOT / "server/build.xml"
BUILD_GRADLE = ROOT / "server/build.gradle"
DOCUMENT = ROOT / "docs/myworld/info/server-build-source-of-truth.md"
BUILD_SCRIPT = ROOT / "scripts/build-server.sh"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    result = subprocess.run(
        [sys.executable, str(AUDIT), "--check", "--require-artifacts", "--json"],
        cwd=ROOT,
        capture_output=True,
        text=True,
    )
    require(result.returncode == 0, f"Server build audit failed:\n{result.stdout}{result.stderr}")
    report = json.loads(result.stdout)

    require(
        report["authority"]["production"] == "bundled Ant 1.10.5 + server/build.xml",
        "Bundled Ant is no longer recorded as production authority",
    )
    require(
        report["gradle"]["authority"] == "secondary/non-authoritative",
        "Gradle is no longer clearly secondary",
    )
    require(not report["validation_errors"], "Shipped Ant path has validation errors")
    require(not report["ant"]["missing_explicit_entries"], "Ant references nonexistent jars")
    require(len(report["libraries"]) == 21, "Review the documented shipped-jar inventory")
    require(report["artifacts"]["core"]["main_class"] == "com.openrsc.server.Server", "core.jar is not executable")
    require(report["artifacts"]["plugins"]["class_entries"] > 0, "plugins.jar is empty")
    require(report["artifacts"]["gameplay_overlay"]["class_entries"] >= 10, "gameplay overlay is incomplete")
    require(
        len(report["ant"]["targets"]["compile_core"]) == 20,
        "compile_core classpath reference is missing or changed unexpectedly",
    )
    require(
        all(entry["exists"] for entry in report["ant"]["targets"]["compile_core"]),
        "compile_core classpath contains a missing shipped jar",
    )
    require(
        report["fat_jar_duplication"]["external_classes_also_in_core"] > 0,
        "core.jar no longer appears to be the documented fat jar",
    )

    for target in ("runserver", "runserverzgc"):
        entries = report["ant"]["targets"][target]
        require(any(entry["kind"] == "jar-wildcard" for entry in entries), f"{target} lost server/lib wildcard")
        require(any(entry["resolved"] == "core.jar" for entry in entries), f"{target} lost core.jar")
        paths = [entry["resolved"] for entry in entries]
        overlay = paths.index("core-gameplay-overlay.jar")
        runtime = paths.index("world-builder-runtime/world-builder-managed-runtime.jar")
        core = paths.index("core.jar")
        require(overlay < runtime < core, f"{target} can let the imported runtime shadow current gameplay")

    build_xml = BUILD_XML.read_text(encoding="utf-8")
    for obsolete in (
        "disruptor-3.3.0.jar",
        "disruptor-3.3.5.jar",
        "xpp3_min-1.1.4c.jar",
        "xstream-1.4.9.jar",
    ):
        require(obsolete not in build_xml, f"Obsolete missing Ant classpath entry returned: {obsolete}")

    gradle = BUILD_GRADLE.read_text(encoding="utf-8")
    require("SECONDARY / NON-AUTHORITATIVE BUILD DESCRIPTION" in gradle, "Gradle authority warning is missing")
    require("not production-Ant artifact parity" in gradle, "Gradle run task overstates parity")
    require("does not validate production-Ant packaging parity" in gradle, "Gradle test task overstates parity")

    build_script = BUILD_SCRIPT.read_text(encoding="utf-8")
    require(
        'audit-server-build.py" --check --require-artifacts' in build_script,
        "Authoritative server build does not run its shipped-path inventory",
    )

    document = DOCUMENT.read_text(encoding="utf-8")
    for statement in (
        "The production server build is the repository-bundled Apache Ant 1.10.5",
        "secondary and\nnon-authoritative",
        "server/compile_server.sh` is an inherited, superseded helper",
        "14,070 unique external library classes",
        "MySQL runtime testing is optional",
        "core-gameplay-overlay.jar",
        "scripts/audit-server-build.py --check --require-artifacts",
    ):
        require(statement in document, f"Server build authority documentation is missing: {statement}")

    print("PASS: Ant server build authority, artifacts, plugin boundary, and Gradle drift are guarded")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, FileNotFoundError, json.JSONDecodeError) as error:
        print(f"FAIL: {error}")
        raise SystemExit(1)
