#!/usr/bin/env python3
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def require_contains(path: Path, snippet: str, label: str | None = None) -> None:
    text = read_text(path)
    if snippet not in text:
        fail(f"{label or path.relative_to(ROOT)} must contain {snippet!r}")


def require_not_contains(path: Path, snippet: str, label: str | None = None) -> None:
    text = read_text(path)
    if snippet in text:
        fail(f"{label or path.relative_to(ROOT)} must not contain {snippet!r}")


def make_target_body(makefile_text: str, target: str) -> str:
    pattern = re.compile(rf"^{re.escape(target)}:\s*(.*?)^(?=\S|\Z)", re.M | re.S)
    match = pattern.search(makefile_text)
    if not match:
        fail(f"Makefile is missing target {target!r}")
    return match.group(1)


def test_root_makefile_is_myworld_first() -> None:
    makefile = read_text(ROOT / "Makefile")
    if ".DEFAULT_GOAL := help" not in makefile:
        fail("Root Makefile must keep help as the default target")
    if "-include .env" not in makefile:
        fail("Root Makefile must not require .env just to list MyWorld tasks")

    expected_targets = {
        "build-client": "./scripts/build-client.sh",
        "build-server": "./scripts/build-server.sh",
        "run": "./scripts/run-server.sh",
        "run-zgc": "./scripts/run-server-zgc.sh",
        "start-fresh": "./scripts/start-fresh.sh",
        "check": "./scripts/check.sh",
        "test": "./scripts/test.sh",
        "smoke": "./tests/myworld/test-smoke.sh",
        "benchmark": "./scripts/benchmark.sh",
        "benchmark-matrix": "./scripts/benchmark-matrix.sh",
    }
    for target, command in expected_targets.items():
        body = make_target_body(makefile, target)
        if command not in body:
            fail(f"Makefile target {target!r} must delegate to {command!r}")

    forbidden_active_paths = [
        "`pwd`/Start-Linux.sh",
        "`pwd`/Deployment_Scripts/run.sh",
        "`pwd`/Deployment_Scripts/combined-install.sh",
        "`pwd`/Deployment_Scripts/get-updates.sh",
        "backup-mariadb:",
        "rank-mariadb:",
        "import-authentic-mariadb:",
        "restore-sqlite-local:",
    ]
    for forbidden in forbidden_active_paths:
        if forbidden in makefile:
            fail(f"Root Makefile must not call archived launcher path {forbidden!r}")


def test_server_ant_defaults_to_myworld() -> None:
    build_xml = ET.parse(ROOT / "server" / "build.xml")
    conf_props = [
        prop
        for prop in build_xml.getroot().iter("property")
        if prop.attrib.get("name") == "confFile"
    ]
    if not conf_props:
        fail("server/build.xml must define the confFile property")
    if conf_props[0].attrib.get("value") != "myworld":
        fail("server/build.xml confFile default must be myworld")

    server_source = read_text(ROOT / "server" / "src" / "com" / "openrsc" / "server" / "Server.java")
    if 'return "myworld.conf";' not in server_source:
        fail("Server.java no-argument fallback must default to myworld.conf")
    if 'return "default.conf";' in server_source:
        fail("Server.java must not default no-argument launches to default.conf")


def test_root_scripts_are_repo_root_anchored() -> None:
    scripts = [
        ROOT / "scripts" / "build-client.sh",
        ROOT / "scripts" / "build-server.sh",
        ROOT / "scripts" / "check.sh",
        ROOT / "scripts" / "init-sqlite.sh",
        ROOT / "scripts" / "run-server.sh",
        ROOT / "scripts" / "run-server-zgc.sh",
        ROOT / "scripts" / "start-fresh.sh",
    ]
    for script in scripts:
        require_contains(script, "SCRIPT_ROOT=", script.relative_to(ROOT).as_posix())
        require_contains(script, 'ROOT_DIR="${ROOT_DIR:-$SCRIPT_ROOT}"', script.relative_to(ROOT).as_posix())

    require_contains(ROOT / "scripts" / "run-server.sh", "-DconfFile=myworld")
    require_contains(ROOT / "scripts" / "run-server-zgc.sh", "-DconfFile=myworld")
    require_contains(ROOT / "scripts" / "init-sqlite.sh", "myworld_seed.db")
    require_not_contains(ROOT / "scripts" / "init-sqlite.sh", "cabbage.db")


def test_active_server_configs_are_myworld_only() -> None:
    active_configs = sorted(path.name for path in (ROOT / "server").glob("*.conf"))
    expected_configs = ["connections.conf", "myworld-host.conf", "myworld.conf"]
    if active_configs != expected_configs:
        fail(f"Active server/*.conf files must be {expected_configs}, got {active_configs}")

    archived_configs = [
        "2001scape.conf",
        "default.conf",
        "openpk.conf",
        "preservation.conf",
        "rsccabbage.conf",
        "rsccoleslaw.conf",
        "uranium.conf",
    ]
    for name in archived_configs:
        if not (ROOT / "docs" / "inherited-openrsc" / "server-configs" / name).exists():
            fail(f"Archived inherited server config is missing: {name}")


def test_active_sqlite_seeds_are_myworld_only() -> None:
    active_sqlite = sorted(path.name for path in (ROOT / "server" / "inc" / "sqlite").glob("*.db"))
    expected_sqlite = ["myworld_dev.db", "myworld_seed.db", "spoiled_milk_alpha.db"]
    if active_sqlite != expected_sqlite:
        fail(f"Active server/inc/sqlite/*.db files must be {expected_sqlite}, got {active_sqlite}")

    archived_seeds = [
        "2001scape.db",
        "cabbage.db",
        "coleslaw.db",
        "openpk.db",
        "openrsc.db",
        "preservation.db",
        "uranium.db",
    ]
    for name in archived_seeds:
        if not (ROOT / "docs" / "inherited-openrsc" / "sqlite-seeds" / name).exists():
            fail(f"Archived inherited SQLite seed is missing: {name}")


def test_generated_tools_and_outputs_do_not_live_in_dev_myworld() -> None:
    generator_manifest = ROOT / "tools" / "generators" / "generators.json"
    require_contains(generator_manifest, "tools/generators/item-overrides")
    require_contains(generator_manifest, "tools/generators/npc-overrides")
    require_not_contains(generator_manifest, "dev/myworld")

    require_contains(ROOT / "tests" / "myworld" / "test-smoke.sh", 'LOG_DIR="$ROOT_DIR/output/logs"')
    require_not_contains(ROOT / "tests" / "myworld" / "test-smoke.sh", "dev/myworld/logs")

    require_contains(ROOT / "tools" / "benchmarks" / "benchmark-foundation.sh", "output/logs")
    require_contains(ROOT / "tools" / "benchmarks" / "benchmark-foundation.sh", "output/benchmarks/optimization")
    require_not_contains(ROOT / "tools" / "benchmarks" / "benchmark-foundation.sh", "dev/myworld/artifacts")

    gitignore = ROOT / ".gitignore"
    for snippet in ["output/", "__pycache__/", "*.class", "bin/"]:
        require_contains(gitignore, snippet)


def test_myworld_docs_are_consolidated() -> None:
    active_docs = sorted(path.name for path in (ROOT / "docs" / "myworld").glob("*.md"))
    expected_docs = [
        "README.md",
        "altar-enchantment-and-conversion-plan.md",
        "change-history.md",
        "combat-equipment-spec.md",
        "compatibility-only-content.md",
        "dev-admin-commands.md",
        "dual-element-spells.md",
        "fishing-rework-plan.md",
        "fishing-spot-map.md",
        "god-knight-equipment-audit.md",
        "jewelry-and-retired-robe-effects.md",
        "migration-regression-audit.md",
        "new-ideas-and-issues.md",
        "prayer-devotion-equipment-plan.md",
        "pvm-npc-cluster-audit.md",
        "pvm-population-and-cluster-plan.md",
        "resource-seed-plan.md",
        "summoning-plan.md",
        "testing-quick-reference.md",
        "work-items.md",
    ]
    if active_docs != expected_docs:
        fail(f"Active docs/myworld markdown files must be {expected_docs}, got {active_docs}")

    archive_dir = ROOT / "docs" / "myworld" / "archive"
    if not archive_dir.is_dir():
        fail("docs/myworld/archive must keep the detailed historical docs")

    for name in [
        "prayer-rework.md",
        "gathering-rework-plan.md",
        "quest-audit.md",
        "quest-choice-audit.md",
        "standalone-extraction-plan.md",
    ]:
        if not (archive_dir / name).exists():
            fail(f"Archived MyWorld doc is missing: {name}")


def test_inherited_root_files_are_archived() -> None:
    root_doc_names = [
        "Commands.md",
        "CONTRIBUTING.md",
        "Linux Getting Started Guide.md",
        "MacOS Getting Started Guide.md",
        "Raspberry Pi Getting Started Guide .md",
        "SECURITY.md",
        "Windows Getting Started Guide.md",
    ]
    for name in root_doc_names:
        if (ROOT / name).exists():
            fail(f"Inherited guide should stay archived, not at repo root: {name}")
        if not (ROOT / "docs" / "inherited-openrsc" / name).exists():
            fail(f"Archived inherited guide is missing: {name}")

    require_contains(ROOT / "docs" / "inherited-openrsc" / "README.md", "current MyWorld setup")

    root_launcher_names = [
        "Start-Linux.sh",
        "Start-Windows.cmd",
        "Deployment_Scripts",
    ]
    for name in root_launcher_names:
        if (ROOT / name).exists():
            fail(f"Inherited launcher should stay archived, not at repo root: {name}")
        if not (ROOT / "docs" / "inherited-openrsc" / "legacy-launchers" / name).exists():
            fail(f"Archived inherited launcher is missing: {name}")

    server_launcher_names = [
        "ant_launcher.sh",
        "run_server.sh",
    ]
    for name in server_launcher_names:
        if (ROOT / "server" / name).exists():
            fail(f"Inherited server launcher should stay archived, not under server/: {name}")
        if not (ROOT / "docs" / "inherited-openrsc" / "legacy-server-launchers" / name).exists():
            fail(f"Archived inherited server launcher is missing: {name}")


def main() -> None:
    test_root_makefile_is_myworld_first()
    test_server_ant_defaults_to_myworld()
    test_root_scripts_are_repo_root_anchored()
    test_active_server_configs_are_myworld_only()
    test_active_sqlite_seeds_are_myworld_only()
    test_generated_tools_and_outputs_do_not_live_in_dev_myworld()
    test_myworld_docs_are_consolidated()
    test_inherited_root_files_are_archived()
    print("PASS: MyWorld standalone layout guardrails validated")


if __name__ == "__main__":
    main()
