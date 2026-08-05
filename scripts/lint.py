#!/usr/bin/env python3
"""Reproducible changed-code static analysis for the maintained Ant products."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import stat
import subprocess
import sys
import tarfile
import tempfile
import urllib.request
import zipfile
from collections import Counter
from pathlib import Path
from typing import Iterable
from xml.etree import ElementTree


ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "config" / "static-analysis"
DEFAULT_REPORTS = ROOT / "output" / "static-analysis"
TOOLS_MANIFEST = CONFIG / "tools.json"
JAVAC_BASELINE = CONFIG / "baseline" / "javac.txt"
SPOTBUGS_BASELINE = CONFIG / "baseline" / "spotbugs.txt"

JAVA_ROOTS = (
    "Client_Base/src",
    "PC_Client/src",
    "server/src",
    "server/plugins",
    "server/test",
    "tools/world-builder/src",
)
JAVA_EXTRA = ("tools/myworld/ExportBasicProjectileSheets.java",)
GENERATED_JAVA = {
    "Client_Base/src/com/openrsc/client/entityhandling/MyWorldItemOverrides.java",
    "Client_Base/src/orsc/remastered/RemasteredSpriteCatalogData.java",
    "server/src/com/openrsc/server/constants/ItemId.java",
    "server/src/com/openrsc/server/constants/NpcId.java",
    "server/src/com/openrsc/server/constants/custom/MyWorldItemId.java",
}
PYTHON_ROOTS = ("scripts", "tests/myworld", "tools/generators")
EXCLUDED_PREFIXES = ("legacy/", "tools/vendor/", "server/inc/ant/")
JAVAC_GATED_CATEGORIES = {"fallthrough", "overloads", "overrides", "unchecked", "varargs"}

PRODUCT_ARTIFACTS = {
    "client": ("Client_Base/Open_RSC_Client.jar",),
    "server": ("server/core.jar", "server/plugins.jar"),
    "world-builder": ("output/world-builder-tools/world-builder-tools.jar",),
}
SPOTBUGS_APPLICATION_PACKAGES = {
    "client": "orsc.-,com.openrsc.-",
    "server": "com.openrsc.-",
    "world-builder": "com.openrsc.worldbuilder.-",
}


class LintFailure(RuntimeError):
    pass


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def run(
    command: list[str],
    *,
    cwd: Path = ROOT,
    env: dict[str, str] | None = None,
    accepted: set[int] | None = None,
    capture: bool = True,
) -> subprocess.CompletedProcess[str]:
    result = subprocess.run(
        command,
        cwd=cwd,
        env=env,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.STDOUT if capture else None,
    )
    accepted = accepted or {0}
    if result.returncode not in accepted:
        output = result.stdout or ""
        raise LintFailure(
            f"Command failed ({result.returncode}): {' '.join(command)}\n{output}"
        )
    return result


def git(*arguments: str) -> str:
    return run(["git", *arguments]).stdout or ""


def normalize_path(value: str) -> str:
    path = Path(value)
    try:
        return path.resolve().relative_to(ROOT).as_posix()
    except ValueError:
        return path.as_posix()


def is_excluded(path: str) -> bool:
    return path.startswith(EXCLUDED_PREFIXES)


def under(path: str, roots: Iterable[str]) -> bool:
    return any(path == root or path.startswith(root + "/") for root in roots)


def is_java(path: str) -> bool:
    return (
        path.endswith(".java")
        and not is_excluded(path)
        and path not in GENERATED_JAVA
        and (under(path, JAVA_ROOTS) or path in JAVA_EXTRA)
    )


def is_python(path: str) -> bool:
    return path.endswith(".py") and not is_excluded(path) and under(path, PYTHON_ROOTS)


def is_shell(path: str) -> bool:
    return path.endswith(".sh") and not is_excluded(path)


def tracked_paths(predicate) -> list[str]:
    paths = git("ls-files", "-z").split("\0")
    return sorted(path for path in paths if path and predicate(path))


def resolve_base(explicit: str | None) -> str:
    candidates = [
        explicit,
        os.environ.get("SPOILED_MILK_LINT_BASE"),
        os.environ.get("GITHUB_BASE_SHA"),
        "spoiled-milk/main",
        "origin/main",
        "HEAD^",
    ]
    for candidate in candidates:
        if not candidate:
            continue
        result = subprocess.run(
            ["git", "rev-parse", "--verify", f"{candidate}^{{commit}}"],
            cwd=ROOT,
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
        )
        if result.returncode == 0:
            return result.stdout.strip()
    raise LintFailure("Unable to resolve a lint base; pass --base <commit>")


def changed_paths(base: str) -> list[str]:
    paths = set(
        path
        for path in git(
            "diff", "--name-only", "--diff-filter=ACMR", "-z", base, "--"
        ).split("\0")
        if path
    )
    paths.update(
        path
        for path in git("ls-files", "--others", "--exclude-standard", "-z").split("\0")
        if path
    )
    return sorted(path for path in paths if (ROOT / path).is_file())


HUNK = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@")


def changed_lines(base: str, path: str) -> list[tuple[int, int]]:
    if path not in git("ls-files", "-z", "--", path).split("\0"):
        count = len((ROOT / path).read_text(encoding="utf-8", errors="replace").splitlines())
        return [(1, max(1, count))]
    diff = git("diff", "--unified=0", "--no-color", base, "--", path)
    ranges: list[tuple[int, int]] = []
    for line in diff.splitlines():
        match = HUNK.match(line)
        if not match:
            continue
        start = int(match.group(1))
        count = int(match.group(2) or "1")
        if count:
            ranges.append((start, start + count - 1))
    return ranges


def touches_changed_line(
    ranges: dict[str, list[tuple[int, int]]], path: str, start: int, end: int | None = None
) -> bool:
    end = end or start
    return any(start <= range_end and end >= range_start for range_start, range_end in ranges.get(path, []))


def safe_extract_zip(archive: Path, destination: Path) -> None:
    with zipfile.ZipFile(archive) as source:
        for member in source.infolist():
            target = (destination / member.filename).resolve()
            if destination.resolve() not in target.parents and target != destination.resolve():
                raise LintFailure(f"Unsafe zip member: {member.filename}")
        source.extractall(destination)


def safe_extract_tar(archive: Path, destination: Path, mode: str) -> None:
    with tarfile.open(archive, mode) as source:
        for member in source.getmembers():
            target = (destination / member.name).resolve()
            if destination.resolve() not in target.parents and target != destination.resolve():
                raise LintFailure(f"Unsafe tar member: {member.name}")
        source.extractall(destination, filter="data")


class Toolchain:
    def __init__(self, offline: bool):
        self.offline = offline
        self.manifest = json.loads(TOOLS_MANIFEST.read_text(encoding="utf-8"))
        cache_root = Path(
            os.environ.get(
                "SPOILED_MILK_LINT_CACHE",
                str(Path(os.environ.get("XDG_CACHE_HOME", Path.home() / ".cache")) / "spoiled-milk" / "static-analysis"),
            )
        )
        self.cache_root = cache_root.expanduser().resolve()

    def entry(self, name: str) -> dict[str, str]:
        return self.manifest["tools"][name]

    def path(self, name: str) -> Path:
        entry = self.entry(name)
        tool_root = self.cache_root / f"{name}-{entry['version']}"
        executable = tool_root / entry["executable"]
        marker = tool_root / ".verified-sha256"
        if executable.exists() and marker.exists() and marker.read_text().strip() == entry["sha256"]:
            return executable
        if self.offline:
            raise LintFailure(f"Offline cache miss for {name} {entry['version']}: {tool_root}")
        self.install(name, tool_root)
        if not executable.exists():
            raise LintFailure(f"{name} archive did not contain {entry['executable']}")
        executable.chmod(executable.stat().st_mode | stat.S_IXUSR)
        return executable

    def install(self, name: str, tool_root: Path) -> None:
        entry = self.entry(name)
        self.cache_root.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(prefix=f"{name}-", dir=self.cache_root) as temporary:
            temp = Path(temporary)
            archive = temp / "download"
            print(f"Downloading {name} {entry['version']}...")
            request = urllib.request.Request(entry["url"], headers={"User-Agent": "spoiled-milk-static-analysis"})
            with urllib.request.urlopen(request) as response, archive.open("wb") as output:
                shutil.copyfileobj(response, output)
            actual = sha256(archive)
            if actual != entry["sha256"]:
                raise LintFailure(f"Checksum mismatch for {name}: expected {entry['sha256']}, got {actual}")
            extracted = temp / "extracted"
            extracted.mkdir()
            archive_kind = entry["archive"]
            if archive_kind == "file":
                shutil.copy2(archive, extracted / entry["executable"])
            elif archive_kind == "zip":
                safe_extract_zip(archive, extracted)
            elif archive_kind == "tar.xz":
                safe_extract_tar(archive, extracted, "r:xz")
            elif archive_kind == "tar.gz":
                safe_extract_tar(archive, extracted, "r:gz")
            else:
                raise LintFailure(f"Unsupported archive type for {name}: {archive_kind}")
            (extracted / ".verified-sha256").write_text(entry["sha256"] + "\n", encoding="utf-8")
            shutil.rmtree(tool_root, ignore_errors=True)
            extracted.rename(tool_root)

    def bootstrap(self) -> dict[str, Path]:
        return {name: self.path(name) for name in self.manifest["tools"]}


def java_major(java: Path) -> int:
    result = run([str(java), "-version"])
    text = result.stdout or ""
    match = re.search(r'version "(?:1\.)?(\d+)', text)
    if not match:
        raise LintFailure(f"Unable to parse Java version from {java}: {text}")
    return int(match.group(1))


def analysis_java() -> Path:
    candidates = []
    if os.environ.get("ANALYSIS_JAVA_HOME"):
        candidates.append(Path(os.environ["ANALYSIS_JAVA_HOME"]) / "bin" / "java")
    resolved = shutil.which("java")
    if resolved:
        candidates.append(Path(resolved))
    candidates.extend(
        [
            Path("/usr/lib/jvm/java-21-openjdk-amd64/bin/java"),
            Path("/usr/lib/jvm/java-21-openjdk/bin/java"),
        ]
    )
    for candidate in candidates:
        if candidate.is_file() and java_major(candidate) >= 21:
            return candidate
    raise LintFailure("Checkstyle 13 requires Java 21; set ANALYSIS_JAVA_HOME to a JDK 21+ installation")


def analysis_environment(java: Path) -> dict[str, str]:
    environment = os.environ.copy()
    java_home = java.parent.parent
    environment["JAVA_HOME"] = str(java_home)
    environment["PATH"] = str(java_home / "bin") + os.pathsep + environment.get("PATH", "")
    return environment


def write_report(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def read_baseline(path: Path) -> set[str]:
    return {
        line.strip()
        for line in path.read_text(encoding="utf-8").splitlines()
        if line.strip() and not line.startswith("#")
    }


def update_baseline(path: Path, header: list[str], findings: set[str]) -> None:
    content = "\n".join([*(f"# {line}" for line in header), *sorted(findings)]) + "\n"
    path.write_text(content, encoding="utf-8")


def with_occurrences(fingerprints: Iterable[str]) -> set[str]:
    counts = Counter(fingerprints)
    return {
        f"{fingerprint}|occurrence={occurrence}"
        for fingerprint, count in counts.items()
        for occurrence in range(1, count + 1)
    }


JAVAC_WARNING = re.compile(r"^(.*?\.java):(\d+): warning: \[([^]]+)] (.*)$")


def parse_javac(product: str, output: str) -> tuple[set[str], set[str]]:
    all_findings: list[str] = []
    gated: list[str] = []
    root_text = str(ROOT) + "/"
    lines = [re.sub(r"^\[javac]\s+", "", line.strip()) for line in output.splitlines()]
    for index, normalized_line in enumerate(lines):
        match = JAVAC_WARNING.match(normalized_line)
        if not match:
            continue
        path = normalize_path(match.group(1))
        category = match.group(3)
        message = " ".join(match.group(4).replace(root_text, "").split())
        context = ""
        if index + 1 < len(lines) and not JAVAC_WARNING.match(lines[index + 1]):
            candidate = lines[index + 1]
            if candidate and not re.fullmatch(r"\^+", candidate):
                context = " ".join(candidate.replace(root_text, "").split())
        fingerprint = f"{product}|{path}|{category}|{message}|context={context}"
        all_findings.append(fingerprint)
        if category in JAVAC_GATED_CATEGORIES:
            gated.append(fingerprint)
    return with_occurrences(all_findings), with_occurrences(gated)


def compiler(reports: Path, update: bool) -> None:
    reports.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    env["SPOILED_MILK_JAVAC_LINT"] = "-Xlint:all"
    env["SPOILED_MILK_JAVAC_MAXWARNS"] = "10000"
    builds = {
        "client": [str(ROOT / "scripts" / "build-client.sh")],
        "server": [str(ROOT / "scripts" / "build-server.sh")],
        "world-builder": [str(ROOT / "scripts" / "build-world-builder-tools.sh")],
    }
    all_findings: set[str] = set()
    gated: set[str] = set()
    for product, command in builds.items():
        print(f"Compiling {product} with -Xlint:all...")
        result = run(command, env=env)
        output = result.stdout or ""
        write_report(reports / f"javac-{product}.log", output)
        product_all, product_gated = parse_javac(product, output)
        all_findings.update(product_all)
        gated.update(product_gated)
    write_report(reports / "javac-fingerprints.txt", "\n".join(sorted(all_findings)) + "\n")
    if update:
        update_baseline(
            JAVAC_BASELINE,
            [
                "Generated by: ./scripts/lint.sh compiler --update-baseline",
                "Gated categories: fallthrough, overloads, overrides, unchecked, varargs",
                "Stable format: product|path|warning-category|message|context|occurrence",
            ],
            gated,
        )
        print(f"Updated {JAVAC_BASELINE.relative_to(ROOT)} with {len(gated)} findings")
        return
    new_findings = gated - read_baseline(JAVAC_BASELINE)
    if new_findings:
        raise LintFailure("New gated javac warnings:\n" + "\n".join(sorted(new_findings)))
    print(f"PASS: no new gated javac warnings ({len(gated)} observed, {len(all_findings)} reported)")


def checkstyle_findings(report: Path) -> list[tuple[str, int, int, str]]:
    root = ElementTree.parse(report).getroot()
    findings = []
    for file_node in root.findall("file"):
        path = normalize_path(file_node.attrib["name"])
        for error in file_node.findall("error"):
            line = int(error.attrib.get("line", "1"))
            source = error.attrib.get("source", "").rsplit(".", 1)[-1]
            findings.append((path, line, line, f"{source}: {error.attrib.get('message', '')}"))
    return findings


def pmd_findings(report: Path) -> list[tuple[str, int, int, str]]:
    root = ElementTree.parse(report).getroot()
    findings = []
    for file_node in root.findall("{*}file"):
        path = normalize_path(file_node.attrib["name"])
        for violation in file_node.findall("{*}violation"):
            start = int(violation.attrib.get("beginline", "1"))
            end = int(violation.attrib.get("endline", str(start)))
            message = " ".join((violation.text or "").split())
            findings.append((path, start, end, f"{violation.attrib.get('rule', '')}: {message}"))
    return findings


def shellcheck_findings(report: Path) -> list[tuple[str, int, int, str]]:
    data = json.loads(report.read_text(encoding="utf-8") or "{}")
    comments = data.get("comments", []) if isinstance(data, dict) else data
    return [
        (
            normalize_path(comment["file"]),
            int(comment["line"]),
            int(comment.get("endLine", comment["line"])),
            f"SC{comment['code']}: {comment['message']}",
        )
        for comment in comments
    ]


def ruff_findings(report: Path) -> list[tuple[str, int, int, str]]:
    data = json.loads(report.read_text(encoding="utf-8") or "[]")
    return [
        (
            normalize_path(item["filename"]),
            int(item["location"]["row"]),
            int(item.get("end_location", item["location"])["row"]),
            f"{item['code']}: {item['message']}",
        )
        for item in data
    ]


def enforce_changed(
    analyzer: str,
    findings: list[tuple[str, int, int, str]],
    ranges: dict[str, list[tuple[int, int]]],
) -> None:
    violations = [
        f"{path}:{start}: {message}"
        for path, start, end, message in findings
        if touches_changed_line(ranges, path, start, end)
    ]
    if violations:
        raise LintFailure(f"{analyzer} findings on changed lines:\n" + "\n".join(sorted(violations)))


def run_checkstyle(
    files: list[str], reports: Path, tools: Toolchain, java: Path, ranges, enforce: bool
) -> None:
    report = reports / "checkstyle.xml"
    if not files:
        write_report(report, '<?xml version="1.0"?><checkstyle version="13.8.0"/>\n')
        return
    command = [
        str(java), "-jar", str(tools.path("checkstyle")),
        "-c", str(CONFIG / "checkstyle.xml"), "-f", "xml", "-o", str(report),
        *(str(ROOT / path) for path in files),
    ]
    # Checkstyle's CLI exit status may equal the bounded violation count.
    run(command, accepted=set(range(0, 256)))
    findings = checkstyle_findings(report)
    if enforce:
        enforce_changed("Checkstyle", findings, ranges)
    print(f"Checkstyle: {len(findings)} report finding(s)")


def run_pmd(
    files: list[str], reports: Path, tools: Toolchain, java: Path, ranges, enforce: bool
) -> None:
    report = reports / "pmd.xml"
    if not files:
        write_report(report, '<?xml version="1.0"?><pmd xmlns="http://pmd.sourceforge.net/report/2.0.0"/>\n')
        return
    file_list = reports / "pmd-files.txt"
    write_report(file_list, "\n".join(str(ROOT / path) for path in files) + "\n")
    command = [
        str(tools.path("pmd")), "check", "--no-progress", "--no-cache",
        "--use-version", "java-1.8", "--rulesets", str(CONFIG / "pmd.xml"),
        "--format", "xml", "--report-file", str(report),
        "--file-list", str(file_list),
    ]
    run(command, env=analysis_environment(java), accepted={0, 4})
    findings = pmd_findings(report)
    if enforce:
        enforce_changed("PMD", findings, ranges)
    print(f"PMD: {len(findings)} report finding(s)")


def run_shellcheck(files: list[str], reports: Path, tools: Toolchain, ranges, enforce: bool) -> None:
    report = reports / "shellcheck.json"
    if not files:
        write_report(report, '{"comments":[]}\n')
        return
    result = run(
        [str(tools.path("shellcheck")), "--external-sources", "--format=json1", *(str(ROOT / path) for path in files)],
        accepted={0, 1},
    )
    write_report(report, result.stdout or '{"comments":[]}\n')
    findings = shellcheck_findings(report)
    if enforce:
        enforce_changed("ShellCheck", findings, ranges)
    print(f"ShellCheck: {len(findings)} report finding(s)")


def run_ruff(files: list[str], reports: Path, tools: Toolchain, ranges, enforce: bool) -> None:
    report = reports / "ruff.json"
    if not files:
        write_report(report, "[]\n")
        return
    result = run(
        [str(tools.path("ruff")), "check", "--no-cache", "--select", "E9,F", "--output-format", "json", *(str(ROOT / path) for path in files)],
        accepted={0, 1},
    )
    write_report(report, result.stdout or "[]\n")
    findings = ruff_findings(report)
    if enforce:
        enforce_changed("Ruff", findings, ranges)
    print(f"Ruff: {len(findings)} report finding(s)")


def run_cpd(files: list[str], reports: Path, tools: Toolchain, java: Path) -> None:
    report = reports / "cpd.xml"
    if len(files) < 2:
        write_report(report, '<?xml version="1.0"?><pmd-cpd/>\n')
        return
    file_list = reports / "cpd-files.txt"
    write_report(file_list, "\n".join(str(ROOT / path) for path in files) + "\n")
    command = [
        str(tools.path("pmd")), "cpd", "--minimum-tokens", "120", "--language", "java",
        "--format", "xml", "--no-fail-on-violation", "--file-list", str(file_list),
    ]
    result = run(command, env=analysis_environment(java))
    write_report(report, result.stdout or '<?xml version="1.0"?><pmd-cpd/>\n')
    print("CPD: report-only output written")


def spotbugs_fingerprints(product: str, report: Path) -> set[str]:
    root = ElementTree.parse(report).getroot()
    findings: list[str] = []
    for bug in root.findall(".//BugInstance"):
        class_node = bug.find("Class")
        method = bug.find("Method")
        field = bug.find("Field")
        member = ""
        if method is not None:
            member = method.attrib.get("name", "") + method.attrib.get("signature", "")
        elif field is not None:
            member = field.attrib.get("name", "")
        findings.append(
            "|".join(
                [
                    product,
                    bug.attrib.get("type", ""),
                    bug.attrib.get("priority", ""),
                    class_node.attrib.get("classname", "") if class_node is not None else "",
                    member,
                ]
            )
        )
    return with_occurrences(findings)


def run_spotbugs(reports: Path, tools: Toolchain, java: Path, update: bool) -> None:
    findings: set[str] = set()
    spotbugs = tools.path("spotbugs")
    for product, relative_artifacts in PRODUCT_ARTIFACTS.items():
        artifacts = [ROOT / path for path in relative_artifacts]
        missing = [str(path.relative_to(ROOT)) for path in artifacts if not path.is_file()]
        if missing:
            raise LintFailure("SpotBugs requires Ant-built artifacts: " + ", ".join(missing))
        report = reports / f"spotbugs-{product}.xml"
        command = [
            str(spotbugs), "-textui", "-xml:withMessages", "-output", str(report),
            "-effort:max", "-medium", "-include", str(CONFIG / "spotbugs-include.xml"),
            "-exclude", str(CONFIG / "spotbugs-exclude.xml"),
            "-onlyAnalyze", SPOTBUGS_APPLICATION_PACKAGES[product],
        ]
        if product == "server":
            libraries = sorted(str(path) for path in (ROOT / "server" / "lib").glob("*.jar"))
            command.extend(["-auxclasspath", os.pathsep.join(libraries)])
        elif product == "client":
            libraries = sorted(str(path) for path in (ROOT / "PC_Client" / "lib").glob("**/*.jar"))
            if libraries:
                command.extend(["-auxclasspath", os.pathsep.join(libraries)])
        command.extend(str(path) for path in artifacts)
        run(command, env=analysis_environment(java))
        product_findings = spotbugs_fingerprints(product, report)
        findings.update(product_findings)
        print(f"SpotBugs {product}: {len(product_findings)} medium/high confidence finding(s)")
    write_report(reports / "spotbugs-fingerprints.txt", "\n".join(sorted(findings)) + "\n")
    if update:
        update_baseline(
            SPOTBUGS_BASELINE,
            [
                "Generated by: ./scripts/lint.sh analyze --update-baseline",
                "Scope: selected medium/high confidence correctness findings from exact Ant-built product jars",
                "Stable format: product|bug-type|priority|class|member|occurrence",
            ],
            findings,
        )
        print(f"Updated {SPOTBUGS_BASELINE.relative_to(ROOT)} with {len(findings)} findings")
        return
    new_findings = findings - read_baseline(SPOTBUGS_BASELINE)
    if new_findings:
        raise LintFailure("New SpotBugs findings:\n" + "\n".join(sorted(new_findings)))
    print(f"PASS: no new SpotBugs findings ({len(findings)} observed)")


def metadata(reports: Path, tools: Toolchain, mode: str, base: str | None) -> None:
    configs = [
        TOOLS_MANIFEST,
        CONFIG / "checkstyle.xml",
        CONFIG / "pmd.xml",
        CONFIG / "spotbugs-include.xml",
        CONFIG / "spotbugs-exclude.xml",
    ]
    content = {
        "schemaVersion": 1,
        "mode": mode,
        "commit": git("rev-parse", "HEAD").strip(),
        "base": base,
        "dirtyPaths": [line[3:] for line in git("status", "--short").splitlines()],
        "tools": {name: entry["version"] for name, entry in tools.manifest["tools"].items()},
        "configSha256": {str(path.relative_to(ROOT)): sha256(path) for path in configs},
        "javaProducts": {name: list(paths) for name, paths in PRODUCT_ARTIFACTS.items()},
        "javaSourceRoots": list(JAVA_ROOTS),
        "javaChangedSourceOnly": list(JAVA_EXTRA),
        "pythonRoots": list(PYTHON_ROOTS),
        "excludedPrefixes": list(EXCLUDED_PREFIXES),
        "generatedJavaExclusions": sorted(GENERATED_JAVA),
    }
    write_report(reports / "metadata.json", json.dumps(content, indent=2, sort_keys=True) + "\n")


def analyze(reports: Path, tools: Toolchain, base: str, full: bool, update: bool) -> None:
    java = analysis_java()
    if full:
        java_files = tracked_paths(is_java)
        python_files = tracked_paths(is_python)
        shell_files = tracked_paths(is_shell)
        ranges: dict[str, list[tuple[int, int]]] = {}
    else:
        changed = changed_paths(base)
        java_files = [path for path in changed if is_java(path)]
        python_files = [path for path in changed if is_python(path)]
        shell_files = [path for path in changed if is_shell(path)]
        ranges = {path: changed_lines(base, path) for path in (*java_files, *python_files, *shell_files)}
    reports.mkdir(parents=True, exist_ok=True)
    print(
        f"Source scope: {len(java_files)} Java, {len(python_files)} Python, "
        f"{len(shell_files)} shell file(s){' (full report)' if full else ' (changed)'}"
    )
    run_checkstyle(java_files, reports, tools, java, ranges, not full)
    run_pmd(java_files, reports, tools, java, ranges, not full)
    run_shellcheck(shell_files, reports, tools, ranges, not full)
    run_ruff(python_files, reports, tools, ranges, not full)
    run_cpd(tracked_paths(is_java), reports, tools, java)
    run_spotbugs(reports, tools, java, update)
    metadata(reports, tools, "report" if full else "changed", base)


def self_test(reports: Path, tools: Toolchain, base: str) -> None:
    java = analysis_java()
    fixture = reports / "self-test"
    shutil.rmtree(fixture, ignore_errors=True)
    fixture.mkdir(parents=True)
    bad_java = fixture / "Bad.java"
    bad_java.write_text(
        "import java.util.*;\n"
        "class Bad { boolean same(String a,String b){return a==b;} "
        "void swallowed(){try{throw new Exception();}catch(Exception ignored){}} }\n"
        "class Extra {}\n",
        encoding="utf-8",
    )
    checkstyle_report = fixture / "checkstyle.xml"
    run(
        [str(java), "-jar", str(tools.path("checkstyle")), "-c", str(CONFIG / "checkstyle.xml"),
         "-f", "xml", "-o", str(checkstyle_report), str(bad_java)],
        accepted=set(range(0, 256)),
    )
    checkstyle_fixture_findings = checkstyle_findings(checkstyle_report)
    if not checkstyle_fixture_findings:
        raise LintFailure("Checkstyle self-test did not detect its fixture")

    pmd_report = fixture / "pmd.xml"
    run(
        [str(tools.path("pmd")), "check", "--no-progress", "--no-cache", "--use-version", "java-1.8",
         "--rulesets", str(CONFIG / "pmd.xml"), "--format", "xml", "--report-file", str(pmd_report),
         "--dir", str(bad_java)],
        env=analysis_environment(java),
        accepted={0, 4},
    )
    pmd_fixture_findings = pmd_findings(pmd_report)
    if not pmd_fixture_findings:
        raise LintFailure("PMD self-test did not detect its fixture")

    fixture_path = normalize_path(str(bad_java))
    fixture_ranges = {fixture_path: changed_lines(base, fixture_path)}
    for analyzer, findings in (
        ("Checkstyle fixture", checkstyle_fixture_findings),
        ("PMD fixture", pmd_fixture_findings),
    ):
        try:
            enforce_changed(analyzer, findings, fixture_ranges)
        except LintFailure:
            pass
        else:
            raise LintFailure(f"{analyzer} was not rejected on added lines")
        enforce_changed(analyzer, findings, {fixture_path: [(999, 999)]})

    bad_shell = fixture / "bad.sh"
    bad_shell.write_text("#!/usr/bin/env bash\nname=$1\necho $name\n", encoding="utf-8")
    shell_result = run([str(tools.path("shellcheck")), "--format=json1", str(bad_shell)], accepted={0, 1})
    write_report(fixture / "shellcheck.json", shell_result.stdout or '{"comments":[]}')
    if not shellcheck_findings(fixture / "shellcheck.json"):
        raise LintFailure("ShellCheck self-test did not detect its fixture")

    bad_python = fixture / "bad.py"
    bad_python.write_text("print(undefined_fixture_name)\n", encoding="utf-8")
    ruff_result = run(
        [str(tools.path("ruff")), "check", "--no-cache", "--select", "E9,F", "--output-format", "json", str(bad_python)],
        accepted={0, 1},
    )
    write_report(fixture / "ruff.json", ruff_result.stdout or "[]")
    if not ruff_findings(fixture / "ruff.json"):
        raise LintFailure("Ruff self-test did not detect its fixture")

    raw_java = fixture / "Raw.java"
    raw_java.write_text(
        "import java.util.ArrayList; class Raw { void add(){ ArrayList values=new ArrayList(); values.add(1); } }\n",
        encoding="utf-8",
    )
    javac = shutil.which("javac")
    if not javac:
        raise LintFailure("javac is required for the compiler self-test")
    javac_result = run([javac, "-Xlint:all", "-Xmaxwarns", "100", "-d", str(fixture), str(raw_java)])
    if "[unchecked]" not in (javac_result.stdout or ""):
        raise LintFailure("javac self-test did not emit an unchecked warning")
    _, javac_fixture = parse_javac("fixture", javac_result.stdout or "")
    if not javac_fixture or not javac_fixture.isdisjoint(read_baseline(JAVAC_BASELINE)):
        raise LintFailure("javac self-test did not create a new stable fingerprint")

    equals_java = fixture / "EqualsOnly.java"
    equals_java.write_text(
        "public class EqualsOnly { public boolean equals(Object other){ return other instanceof EqualsOnly; } }\n",
        encoding="utf-8",
    )
    run([javac, "-d", str(fixture), str(equals_java)])
    spotbugs_report = fixture / "spotbugs.xml"
    run(
        [str(tools.path("spotbugs")), "-textui", "-xml:withMessages", "-output", str(spotbugs_report),
         "-effort:max", "-low", str(fixture / "EqualsOnly.class")],
        env=analysis_environment(java),
    )
    spotbugs_fixture = spotbugs_fingerprints("fixture", spotbugs_report)
    if not spotbugs_fixture:
        raise LintFailure("SpotBugs self-test did not detect its fixture")
    if not spotbugs_fixture.isdisjoint(read_baseline(SPOTBUGS_BASELINE)):
        raise LintFailure("SpotBugs self-test did not create a new stable fingerprint")

    duplicate_a = fixture / "DuplicateA.java"
    duplicate_b = fixture / "DuplicateB.java"
    repeated = "int total=0; for(int i=0;i<20;i++){total+=i*i;} System.out.println(total);"
    duplicate_a.write_text(f"class DuplicateA {{ void run(){{{repeated}}} }}\n", encoding="utf-8")
    duplicate_b.write_text(f"class DuplicateB {{ void run(){{{repeated}}} }}\n", encoding="utf-8")
    cpd = run(
        [str(tools.path("pmd")), "cpd", "--minimum-tokens", "20", "--language", "java",
         "--format", "xml", "--no-fail-on-violation", "--dir", f"{duplicate_a},{duplicate_b}"],
        env=analysis_environment(java),
    )
    if "duplication" not in (cpd.stdout or ""):
        raise LintFailure("CPD self-test did not detect its fixture")
    print("PASS: javac, Checkstyle, PMD, CPD, SpotBugs, ShellCheck, and Ruff fixtures were detected")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "command",
        choices=("bootstrap", "compiler", "analyze", "report", "all", "self-test"),
        help="operation to run",
    )
    parser.add_argument("--base", help="Git commit/ref used for changed-line enforcement")
    parser.add_argument("--offline", action="store_true", help="refuse network access and use only verified cached tools")
    parser.add_argument("--update-baseline", action="store_true", help="replace the selected whole-program baseline explicitly")
    parser.add_argument("--reports", type=Path, default=DEFAULT_REPORTS, help="machine-readable report directory")
    return parser.parse_args()


def main() -> int:
    arguments = parse_args()
    reports = arguments.reports.expanduser().resolve()
    tools = Toolchain(arguments.offline)
    if arguments.command == "bootstrap":
        installed = tools.bootstrap()
        for name, path in installed.items():
            print(f"{name} {tools.entry(name)['version']}: {path}")
        return 0
    base = resolve_base(arguments.base)
    if arguments.command in {"compiler", "all", "report"}:
        compiler(reports, arguments.update_baseline)
    if arguments.command in {"analyze", "all", "report"}:
        tools.bootstrap()
        analyze(reports, tools, base, arguments.command == "report", arguments.update_baseline)
    if arguments.command == "self-test":
        tools.bootstrap()
        self_test(reports, tools, base)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (LintFailure, OSError, ValueError, ElementTree.ParseError, json.JSONDecodeError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
