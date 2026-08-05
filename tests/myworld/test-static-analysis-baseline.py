#!/usr/bin/env python3

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CONFIG = ROOT / "config" / "static-analysis"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    manifest = json.loads((CONFIG / "tools.json").read_text(encoding="utf-8"))
    require(manifest["schemaVersion"] == 1, "Tool manifest schema changed without a contract update")
    require(
        set(manifest["tools"]) == {"checkstyle", "pmd", "spotbugs", "shellcheck", "ruff"},
        "Pinned analyzer inventory is incomplete",
    )
    for name, entry in manifest["tools"].items():
        require(entry["url"].startswith("https://"), f"{name} download is not HTTPS")
        require(re.fullmatch(r"[0-9a-f]{64}", entry["sha256"]) is not None, f"{name} SHA-256 is invalid")
        require(entry["version"] in entry["url"], f"{name} URL does not identify its pinned version")

    lint = (ROOT / "scripts" / "lint.py").read_text(encoding="utf-8")
    for source_root in (
        '"Client_Base/src"',
        '"PC_Client/src"',
        '"server/src"',
        '"server/plugins"',
        '"server/test"',
        '"tools/world-builder/src"',
    ):
        require(source_root in lint, f"Maintained Java root is absent from lint scope: {source_root}")
    for artifact in (
        '"Client_Base/Open_RSC_Client.jar"',
        '"server/core.jar"',
        '"server/plugins.jar"',
        '"output/world-builder-tools/world-builder-tools.jar"',
    ):
        require(artifact in lint, f"Ant artifact is absent from SpotBugs scope: {artifact}")
    for boundary in (
        "changed_lines(base, path)",
        "touches_changed_line",
        "was not rejected on added lines",
        "with_occurrences",
        "new_findings = gated - read_baseline(JAVAC_BASELINE)",
        "new_findings = findings - read_baseline(SPOTBUGS_BASELINE)",
        '"-onlyAnalyze", SPOTBUGS_APPLICATION_PACKAGES[product]',
        '"--no-fail-on-violation"',
    ):
        require(boundary in lint, f"Changed-code/baseline policy is missing: {boundary}")

    checkstyle = (CONFIG / "checkstyle.xml").read_text(encoding="utf-8")
    pmd = (CONFIG / "pmd.xml").read_text(encoding="utf-8")
    for prohibited in ("Indentation", "LineLength", "Javadoc", "MethodName", "MemberName"):
        require(prohibited not in checkstyle, f"Formatting/convention rule entered Checkstyle baseline: {prohibited}")
    for required in ("EmptyCatchBlock", "OneTopLevelClass", "OuterTypeFilename", "UnusedImports"):
        require(required in checkstyle, f"Structural Checkstyle rule is missing: {required}")
    for prohibited in ("CyclomaticComplexity", "CommentRequired", "UnusedPrivate"):
        require(prohibited not in pmd, f"Deferred PMD rule entered the initial baseline: {prohibited}")
    for required in ("CloseResource", "CompareObjectsWithEquals", "EmptyCatchBlock"):
        require(required in pmd, f"Correctness PMD rule is missing: {required}")

    spotbugs = (CONFIG / "spotbugs-include.xml").read_text(encoding="utf-8")
    for required in ("CORRECTNESS", "MT_CORRECTNESS", "HE_EQUALS_USE_HASHCODE", "DE_MIGHT_IGNORE"):
        require(required in spotbugs, f"SpotBugs correctness scope is missing: {required}")
    for excluded in ("STYLE", "PERFORMANCE", "MALICIOUS_CODE"):
        require(excluded not in spotbugs, f"Broad SpotBugs category entered the baseline: {excluded}")

    for baseline_name in ("javac.txt", "spotbugs.txt"):
        fingerprints = [
            line
            for line in (CONFIG / "baseline" / baseline_name).read_text(encoding="utf-8").splitlines()
            if line and not line.startswith("#")
        ]
        require(fingerprints, f"{baseline_name} is unexpectedly empty")
        require(len(fingerprints) == len(set(fingerprints)), f"{baseline_name} contains duplicate fingerprints")
        require(
            all(re.search(r"\|occurrence=[1-9][0-9]*$", line) for line in fingerprints),
            f"{baseline_name} does not retain occurrence counts",
        )
    require(
        all("|context=" in line for line in (CONFIG / "baseline" / "javac.txt").read_text(encoding="utf-8").splitlines() if line and not line.startswith("#")),
        "javac baseline does not retain normalized source context",
    )

    for build_file, default_lint in (
        (ROOT / "Client_Base" / "build.xml", "-Xlint:unchecked"),
        (ROOT / "server" / "build.xml", "-Xlint:deprecation"),
        (ROOT / "tools" / "world-builder" / "build.xml", "-Xlint:none"),
    ):
        text = build_file.read_text(encoding="utf-8")
        require(default_lint in text, f"Normal warning default changed in {build_file.relative_to(ROOT)}")
        require("${javac.lint}" in text and "${javac.maxwarns}" in text, f"Analysis override missing in {build_file.relative_to(ROOT)}")

    workflow = (ROOT / ".github" / "workflows" / "static-analysis.yml").read_text(encoding="utf-8")
    for statement in (
        "distribution: temurin",
        "java-version: '8'",
        "java-version: '21'",
        "./scripts/lint.sh compiler",
        "./scripts/lint.sh self-test --offline",
        "./scripts/lint.sh analyze --offline",
        "Client_Base/Open_RSC_Client.jar",
        "output/world-builder-tools/world-builder-tools.jar",
    ):
        require(statement in workflow, f"CI lane is missing: {statement}")

    document = (ROOT / "docs" / "myworld" / "info" / "static-analysis.md").read_text(encoding="utf-8")
    require("test-all.sh` does not" not in document, "Use direct wording for the World Builder test boundary")
    require("They are not transitively covered by `tests/myworld/test-all.sh`" in document, "World Builder test boundary is undocumented")
    require("never regenerate either" in document, "Baseline review policy is undocumented")

    print("PASS: static-analysis scope, pinned tools, changed-code gates, builds, and CI lanes are guarded")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, FileNotFoundError, json.JSONDecodeError) as error:
        print(f"FAIL: {error}")
        raise SystemExit(1)
