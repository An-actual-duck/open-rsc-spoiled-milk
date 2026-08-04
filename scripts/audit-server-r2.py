#!/usr/bin/env python3
"""Build and enforce the deterministic Server R2 ownership baseline."""

import argparse
import fnmatch
import hashlib
import importlib.util
import json
import re
import subprocess
import sys
from collections import Counter, defaultdict
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CONFIG = ROOT / "config/server-r2/ownership-rules.json"
BASELINE = ROOT / "config/server-r2/foundation-content-dependencies.json"
JSON_REPORT = ROOT / "docs/myworld/info/server-r2-ownership-inventory.json"
HUMAN_REPORT = ROOT / "docs/myworld/info/server-r2-ownership-inventory.md"
AUDIT_BUILD = ROOT / "scripts/audit-server-build.py"


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def git_files():
    output = subprocess.check_output(
        ["git", "ls-files", "-z"], cwd=str(ROOT)
    ).decode("utf-8")
    return sorted(path for path in output.split("\0") if path)


def in_scope(path, config):
    scope = config["inputScope"]
    if path in scope["excludeExact"] or any(
        path.startswith(prefix) for prefix in scope["excludePrefixes"]
    ):
        return False
    return path in scope["includeExact"] or any(
        path.startswith(prefix) for prefix in scope["includePrefixes"]
    )


def expand_braces(pattern):
    match = re.search(r"\{([^{}]+)\}", pattern)
    if match is None:
        return [pattern]
    values = []
    for option in match.group(1).split(","):
        values.extend(
            expand_braces(pattern[: match.start()] + option + pattern[match.end() :])
        )
    return values


def classify_path(path, config):
    for rule in config["rules"]:
        if any(fnmatch.fnmatchcase(path, pattern) for pattern in expand_braces(rule["pattern"])):
            return rule["category"], rule["reason"], rule["pattern"]
    return "unresolved", "No ownership rule matched this shipped input", None


def input_kind(path):
    name = Path(path).name.lower()
    suffix = Path(path).suffix.lower()
    if path.startswith("server/src/") and suffix == ".java":
        return "ant-core-source"
    if path.startswith("server/plugins/") and suffix == ".java":
        return "ant-plugin-source"
    if path.startswith("server/lib/") and suffix == ".jar":
        return "ant-library"
    if path in ("server/build.xml", "server/build.gradle"):
        return "build-definition"
    if path.startswith("server/conf/server/defs/locs/"):
        return "population-source"
    if path.startswith("server/conf/server/defs/"):
        return "definition"
    if path.startswith("server/conf/server/data/"):
        return "map-or-asset-archive"
    if path.startswith("server/conf/") or suffix in (".conf", ".properties"):
        return "configuration"
    if path.startswith("server/database/"):
        if "/patches/" in path or "/upgrades/" in path:
            return "database-patch"
        if "/queries/" in path:
            return "database-query"
        return "database-schema-or-addon"
    if path.endswith("myworld_seed.db"):
        return "database-seed"
    if path.startswith("release/") or "package-world-builder" in path:
        return "release-input"
    if path.startswith("tools/layered-maps/"):
        if "/fixtures/" in path:
            return "layered-proof-fixture"
        return "layered-tool-input"
    if path.startswith("scripts/"):
        return "build-launch-or-operator-script"
    if suffix == ".java":
        return "java-source"
    if suffix in (".pem", ".ttf", ".jag", ".mem", ".orsc", ".osar"):
        return "runtime-asset"
    if name.endswith((".cmd", ".bat", ".sh")):
        return "compatibility-launcher"
    return "runtime-or-build-input"


def text_for(path):
    try:
        return path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        return None


def java_fqn(path, text):
    package = re.search(r"(?m)^\s*package\s+([\w.]+)\s*;", text)
    if package is None:
        return None
    return package.group(1) + "." + Path(path).stem


def java_imports(text):
    imports = []
    for match in re.finditer(r"(?m)^\s*import\s+(?:static\s+)?([\w.*]+)\s*;", text):
        value = match.group(1)
        if value.endswith(".*"):
            continue
        imports.append(value)
    return sorted(set(imports))


def resolve_import(value, fqn_to_path):
    candidate = value
    while "." in candidate:
        if candidate in fqn_to_path:
            return fqn_to_path[candidate]
        candidate = candidate.rsplit(".", 1)[0]
    return None


def build_audit_report():
    spec = importlib.util.spec_from_file_location("server_build_audit", str(AUDIT_BUILD))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    report, errors = module.build_report(False)
    return report, errors


def layered_profiles():
    path = ROOT / "server/src/com/openrsc/server/io/NativeLayeredWorldRuntimeProfile.java"
    text = path.read_text(encoding="utf-8")
    declaration = re.search(
        r"public\s+enum\s+NativeLayeredWorldRuntimeProfile\s*\{(.*?);", text, re.S
    )
    body = declaration.group(1) if declaration is not None else ""
    profiles = re.findall(r"(?m)^\s*[A-Z][A-Z0-9_]*\(\s*\"([^\"]+)\"", body)
    return {
        "authorityFile": str(path.relative_to(ROOT)),
        "profiles": sorted(set(profiles)),
        "configuredProfiles": {
            relative: sorted(set(re.findall(r"(?m)^\s*layered_native_world_runtime_profile:\s*(\S+)", (ROOT / relative).read_text(encoding="utf-8"))))
            for relative in ("server/myworld.conf", "server/myworld-host.conf")
        },
    }


def dependency_key(edge):
    return (edge["source"], edge["target"], edge["import"])


def new_foundation_content_dependencies(current, baseline):
    allowed = {dependency_key(edge) for edge in baseline}
    return sorted(
        (edge for edge in current if dependency_key(edge) not in allowed),
        key=dependency_key,
    )


def build_report(config=None):
    config = read_json(CONFIG) if config is None else config
    paths = [path for path in git_files() if in_scope(path, config)]
    records = []
    texts = {}
    fqn_to_path = {}
    for relative in paths:
        absolute = ROOT / relative
        category, reason, rule = classify_path(relative, config)
        text = text_for(absolute)
        if text is not None:
            texts[relative] = text
        fqn = java_fqn(relative, text) if text is not None and relative.endswith(".java") else None
        if fqn:
            fqn_to_path[fqn] = relative
        records.append({
            "path": relative,
            "kind": input_kind(relative),
            "category": category,
            "classificationReason": reason,
            "classificationRule": rule,
            "bytes": absolute.stat().st_size,
            "sha256": sha256(absolute),
            "javaFqn": fqn,
            "dependencies": [],
            "reverseDependencies": [],
            "brandSignals": [],
        })

    by_path = {record["path"]: record for record in records}
    signal_patterns = {
        name: re.compile(pattern) for name, pattern in config["brandSignals"].items()
    }
    for relative, text in texts.items():
        by_path[relative]["brandSignals"] = sorted(
            name for name, pattern in signal_patterns.items() if pattern.search(text)
        )
        if not relative.endswith(".java"):
            continue
        dependencies = []
        for imported in java_imports(text):
            target = resolve_import(imported, fqn_to_path)
            if target is not None and target != relative:
                dependencies.append({"path": target, "import": imported})
        by_path[relative]["dependencies"] = sorted(
            dependencies, key=lambda item: (item["path"], item["import"])
        )

    reverse = defaultdict(list)
    for record in records:
        for dependency in record["dependencies"]:
            reverse[dependency["path"]].append(record["path"])
    for record in records:
        record["reverseDependencies"] = sorted(set(reverse[record["path"]]))

    violations = []
    for record in records:
        if record["category"] != "foundation":
            continue
        for dependency in record["dependencies"]:
            target = by_path[dependency["path"]]
            if target["category"] == "spoiled-milk-content":
                violations.append({
                    "source": record["path"],
                    "target": target["path"],
                    "import": dependency["import"],
                })
    violations.sort(key=dependency_key)

    build, build_errors = build_audit_report()
    ant = build["ant"]
    build_summary = {
        "authority": build["authority"],
        "buildFile": ant["build_file"],
        "defaultTarget": ant["default_target"],
        "sourceRoots": ant["source_roots"],
        "artifacts": ant["artifacts"],
        "coreFatJarInput": ant["core_fat_jar_input"],
        "libraries": [
            {
                "file": library["file"],
                "bytes": library["bytes"],
                "sha256": library["sha256"],
                "classEntries": library["class_entries"],
            }
            for library in build["libraries"]
        ],
        "gradle": build["gradle"],
        "validationErrors": build_errors,
    }
    kind_counts = Counter(record["kind"] for record in records)
    category_counts = Counter(record["category"] for record in records)
    plugin_counts = Counter()
    for record in records:
        match = re.match(r"server/plugins/com/openrsc/server/plugins/([^/]+)/.*\.java$", record["path"])
        if match:
            plugin_counts[match.group(1)] += 1
    digest = hashlib.sha256()
    for record in records:
        digest.update((record["path"] + "\0" + record["sha256"] + "\n").encode("utf-8"))

    return {
        "schemaVersion": 1,
        "evidenceBaseCommit": config["evidenceBaseCommit"],
        "inputTreeSha256": digest.hexdigest(),
        "scope": config["inputScope"],
        "categories": config["categories"],
        "summary": {
            "shippedInputs": len(records),
            "categoryCounts": dict(sorted(category_counts.items())),
            "kindCounts": dict(sorted(kind_counts.items())),
            "coreJavaFiles": kind_counts["ant-core-source"],
            "pluginJavaFiles": kind_counts["ant-plugin-source"],
            "pluginFamilies": dict(sorted(plugin_counts.items())),
            "libraryJars": kind_counts["ant-library"],
            "definitionInputs": kind_counts["definition"],
            "populationInputs": kind_counts["population-source"],
            "configurationInputs": kind_counts["configuration"],
            "databasePatchInputs": kind_counts["database-patch"],
            "directBrandSignalFiles": sum(bool(record["brandSignals"]) for record in records),
            "foundationToSpoiledMilkDependencies": len(violations),
        },
        "build": build_summary,
        "layeredProfiles": layered_profiles(),
        "dependencyMethod": (
            "Explicit non-wildcard Java imports resolved to tracked Ant core/plugin source files; "
            "same-package, wildcard, reflective, configuration, and data references remain visible "
            "through ownership and brand-signal inventories rather than inferred as import edges."
        ),
        "foundationToSpoiledMilkDependencies": violations,
        "unresolvedInputs": [
            {"path": record["path"], "reason": record["classificationReason"]}
            for record in records if record["category"] == "unresolved"
        ],
        "inputs": records,
    }


def json_text(report):
    return json.dumps(report, indent=2, sort_keys=True) + "\n"


def escaped(value):
    return str(value).replace("|", "\\|").replace("\n", " ")


def human_text(report):
    summary = report["summary"]
    lines = [
        "# Server R2 Ownership and Dependency Inventory",
        "",
        "> Generated by `python3 scripts/audit-server-r2.py --write`. Do not edit by hand.",
        "",
        f"Evidence base: `{report['evidenceBaseCommit']}`",
        f"Input tree SHA-256: `{report['inputTreeSha256']}`",
        "",
        "## Summary",
        "",
        f"- Shipped inputs: **{summary['shippedInputs']}**",
        f"- Ant core/plugin Java: **{summary['coreJavaFiles']} / {summary['pluginJavaFiles']}**",
        f"- Shipped libraries: **{summary['libraryJars']}**",
        f"- Definition/population/config/database-patch inputs: **{summary['definitionInputs']} / {summary['populationInputs']} / {summary['configurationInputs']} / {summary['databasePatchInputs']}**",
        f"- Direct Spoiled Milk/MyWorld signal files: **{summary['directBrandSignalFiles']}**",
        f"- Baselined foundation-to-Spoiled-Milk dependencies: **{summary['foundationToSpoiledMilkDependencies']}**",
        "",
        "### Ownership categories",
        "",
        "| Category | Inputs |",
        "| --- | ---: |",
    ]
    for category in report["categories"]:
        lines.append(f"| {category} | {summary['categoryCounts'].get(category, 0)} |")
    lines.extend(["", "### Plugin families", "", "| Family | Java files |", "| --- | ---: |"])
    for family, count in summary["pluginFamilies"].items():
        lines.append(f"| {family} | {count} |")
    lines.extend([
        "",
        "## Ant and Artifact Boundary",
        "",
        f"- Authority: {report['build']['authority']['production']}",
        f"- Core: `{report['build']['sourceRoots']['core']}` -> `{report['build']['artifacts']['core']}` (fat JAR includes `server/lib/*.jar`)",
        f"- Plugins: `{report['build']['sourceRoots']['plugins']}` -> `{report['build']['artifacts']['plugins']}`",
        f"- Gradle: {report['build']['authority']['gradle']}",
        "",
        "## Layered Runtime Profiles",
        "",
    ])
    for profile in report["layeredProfiles"]["profiles"]:
        lines.append(f"- `{profile}`")
    lines.extend([
        "",
        "## Existing Foundation-to-Spoiled-Milk Dependency Debt",
        "",
        "The guard permits only these exact existing import edges. Removing an edge is allowed; adding one fails the audit.",
        "",
        "| Foundation source | Spoiled Milk target | Import |",
        "| --- | --- | --- |",
    ])
    for edge in report["foundationToSpoiledMilkDependencies"]:
        lines.append(f"| `{edge['source']}` | `{edge['target']}` | `{edge['import']}` |")
    if not report["foundationToSpoiledMilkDependencies"]:
        lines.append("| _none_ | _none_ | _none_ |")
    lines.extend([
        "",
        "## Explicitly Unresolved Inputs",
        "",
        "These inputs remain shipped. R2-0 does not guess their eventual target/content/foundation owner.",
        "",
        "| Input | Reason |",
        "| --- | --- |",
    ])
    for item in report["unresolvedInputs"]:
        lines.append(f"| `{item['path']}` | {escaped(item['reason'])} |")
    if not report["unresolvedInputs"]:
        lines.append("| _none_ | _none_ |")
    lines.extend([
        "",
        "## Complete Shipped-Input Inventory",
        "",
        "Brand signals are evidence only and never change a file's owner classification.",
        "",
        "| Input | Kind | Owner | Direct signals | Reverse dependencies | Rule/reason |",
        "| --- | --- | --- | --- | ---: | --- |",
    ])
    for record in report["inputs"]:
        signals = ", ".join(record["brandSignals"]) or "-"
        lines.append(
            f"| `{record['path']}` | {record['kind']} | {record['category']} | {signals} | "
            f"{len(record['reverseDependencies'])} | {escaped(record['classificationReason'])} |"
        )
    return "\n".join(lines) + "\n"


def write_reports(report):
    JSON_REPORT.write_text(json_text(report), encoding="utf-8")
    HUMAN_REPORT.write_text(human_text(report), encoding="utf-8")


def write_baseline(report):
    payload = {
        "schemaVersion": 1,
        "evidenceBaseCommit": report["evidenceBaseCommit"],
        "policy": "Exact foundation-to-Spoiled-Milk import edges allowed at R2-0; additions fail, removals are accepted.",
        "dependencies": report["foundationToSpoiledMilkDependencies"],
    }
    BASELINE.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def check(report, base=None):
    errors = []
    if report["build"]["validationErrors"]:
        errors.extend("build audit: " + error for error in report["build"]["validationErrors"])
    if not BASELINE.is_file():
        errors.append("dependency baseline is missing")
        baseline = []
    else:
        baseline_data = read_json(BASELINE)
        baseline = baseline_data.get("dependencies", [])
        if baseline_data.get("evidenceBaseCommit") != report["evidenceBaseCommit"]:
            errors.append("dependency baseline evidence commit differs from ownership rules")
    additions = new_foundation_content_dependencies(
        report["foundationToSpoiledMilkDependencies"], baseline
    )
    if base:
        changed = set(
            subprocess.check_output(
                ["git", "diff", "--name-only", "--diff-filter=ACMR", base + "...HEAD"],
                cwd=str(ROOT),
            ).decode("utf-8").splitlines()
        )
        additions = [edge for edge in additions if edge["source"] in changed]
    for edge in additions:
        errors.append(
            "new foundation-to-Spoiled-Milk dependency: "
            + edge["source"] + " -> " + edge["target"] + " (" + edge["import"] + ")"
        )
    expected = ((JSON_REPORT, json_text(report)), (HUMAN_REPORT, human_text(report)))
    for path, content in expected:
        if not path.is_file() or path.read_text(encoding="utf-8") != content:
            errors.append(str(path.relative_to(ROOT)) + " is stale; run audit-server-r2.py --write")
    return errors


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="validate reports, build integration, and dependency debt")
    parser.add_argument("--write", action="store_true", help="refresh deterministic human and JSON reports")
    parser.add_argument("--refresh-dependency-baseline", action="store_true", help="explicitly replace the reviewed dependency-debt baseline")
    parser.add_argument("--json", action="store_true", help="print the JSON inventory")
    parser.add_argument("--base", help="limit new dependency enforcement to files changed from this Git base")
    args = parser.parse_args()
    report = build_report()
    if args.refresh_dependency_baseline:
        write_baseline(report)
    if args.write:
        write_reports(report)
    errors = check(report, args.base) if args.check else []
    if args.json:
        sys.stdout.write(json_text(report))
    elif not args.write and not args.refresh_dependency_baseline and not args.check:
        sys.stdout.write(human_text(report))
    if errors:
        for error in errors:
            print("FAIL: " + error, file=sys.stderr)
        return 1
    if args.check:
        print(
            "PASS: deterministic Server R2 inventory, Ant integration, and dependency baseline are current",
            file=sys.stderr if args.json else sys.stdout,
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
