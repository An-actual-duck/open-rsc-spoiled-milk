#!/usr/bin/env python3
import json
import subprocess
import sys
import tempfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str((ROOT / "tools" / "generators").resolve()))

from generator_common import load_generator_manifest

RUNNER = ROOT / "tools" / "generators" / "run-generators.py"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def run_generator(script: Path, source_dir: Path, target_path: Path, check: bool = False) -> subprocess.CompletedProcess[str]:
    command = [
        sys.executable,
        str(script),
        "--source-dir",
        str(source_dir),
        "--target",
        str(target_path),
    ]
    if check:
        command.append("--check")
    return subprocess.run(command, capture_output=True, text=True)


def run_runner(*args: str) -> subprocess.CompletedProcess[str]:
    command = [sys.executable, str(RUNNER), *args]
    return subprocess.run(command, capture_output=True, text=True)


def write_json(path: Path, payload: dict) -> None:
    rendered = json.dumps(payload, indent=2)
    if not rendered.endswith("\n"):
        rendered += "\n"
    path.write_text(rendered, encoding="utf-8")


def expect_success(result: subprocess.CompletedProcess[str], label: str, snippet: str) -> None:
    if result.returncode != 0:
        fail(f"{label} unexpectedly failed:\n{result.stderr or result.stdout}")
    output = result.stdout + result.stderr
    if snippet not in output:
        fail(f"{label} missing expected output snippet {snippet!r}:\n{output}")


def expect_failure(result: subprocess.CompletedProcess[str], label: str, snippet: str) -> None:
    if result.returncode == 0:
        fail(f"{label} unexpectedly passed")
    output = result.stdout + result.stderr
    if snippet not in output:
        fail(f"{label} missing expected failure snippet {snippet!r}:\n{output}")


def find_generator(name: str) -> dict[str, object]:
    for generator in load_generator_manifest()["generators"]:
        if generator["name"] == name:
            return generator
    fail(f"Generator manifest missing entry for {name!r}")


def run_manifest_generator(
    generator: dict[str, object], source_dir: Path, target_path: Path, check: bool = False
) -> subprocess.CompletedProcess[str]:
    return run_generator(ROOT / str(generator["script"]), source_dir, target_path, check=check)


def test_item_generator(generator: dict[str, object]) -> None:
    with tempfile.TemporaryDirectory(prefix="myworld-item-generator-") as tmp_dir_str:
        tmp_dir = Path(tmp_dir_str)
        source_dir = tmp_dir / str(generator["sourceDir"]).split("/")[-1]
        source_dir.mkdir()
        target_path = tmp_dir / Path(str(generator["target"])).name

        write_json(
            source_dir / "10-melee.json",
            {
                "description": "valid base item fixture",
                "items": [
                    {"id": 100, "meleeOffense": 5},
                    {"id": 101, "meleeDefense": 3, "requiredLevel": 10, "requiredSkillID": 0},
                ],
            },
        )
        result = run_manifest_generator(generator, source_dir, target_path)
        expect_success(result, "item generator happy path", "Generated")

        result = run_manifest_generator(generator, source_dir, target_path, check=True)
        expect_success(result, "item generator up-to-date check", "Validated")

        write_json(
            source_dir / "20-duplicate.json",
            {
                "items": [
                    {"id": 100, "rangedOffense": 4},
                ]
            },
        )
        result = run_manifest_generator(generator, source_dir, target_path)
        expect_failure(result, "item generator duplicate id guard", "Duplicate item id 100")
        (source_dir / "20-duplicate.json").unlink()

        write_json(
            source_dir / "20-unknown-top-level.json",
            {
                "items": [{"id": 102, "magicOffense": 2}],
                "unexpected": True,
            },
        )
        result = run_manifest_generator(generator, source_dir, target_path)
        expect_failure(result, "item generator unknown key guard", "unknown top-level keys")
        (source_dir / "20-unknown-top-level.json").unlink()

        target_path.write_text('{"items":[]}\n', encoding="utf-8")
        result = run_manifest_generator(generator, source_dir, target_path, check=True)
        expect_failure(result, "item generator stale output check", "is out of date")


def test_npc_generator(generator: dict[str, object]) -> None:
    with tempfile.TemporaryDirectory(prefix="myworld-npc-generator-") as tmp_dir_str:
        tmp_dir = Path(tmp_dir_str)
        source_dir = tmp_dir / str(generator["sourceDir"]).split("/")[-1]
        source_dir.mkdir()
        target_path = tmp_dir / Path(str(generator["target"])).name

        write_json(
            source_dir / "10-melee.json",
            {
                "description": "valid base npc fixture",
                "npcs": [
                    {
                        "id": 200,
                        "meleeDefenseMultiplier": 1.0,
                        "rangedDefenseMultiplier": 0.1,
                        "magicDefenseMultiplier": 0.1,
                    },
                    {
                        "id": 201,
                        "strength": 12,
                        "meleeDefenseMultiplier": 0.5,
                        "rangedDefenseMultiplier": 0.5,
                        "magicDefenseMultiplier": 1.0,
                    },
                ],
            },
        )
        result = run_manifest_generator(generator, source_dir, target_path)
        expect_success(result, "npc generator happy path", "Generated")

        result = run_manifest_generator(generator, source_dir, target_path, check=True)
        expect_success(result, "npc generator up-to-date check", "Validated")

        write_json(
            source_dir / "20-duplicate.json",
            {
                "npcs": [
                    {
                        "id": 200,
                        "meleeDefenseMultiplier": 0.1,
                        "rangedDefenseMultiplier": 1.0,
                        "magicDefenseMultiplier": 0.1,
                    }
                ]
            },
        )
        result = run_manifest_generator(generator, source_dir, target_path)
        expect_failure(result, "npc generator duplicate id guard", "Duplicate npc id 200")
        (source_dir / "20-duplicate.json").unlink()

        write_json(
            source_dir / "20-unknown-field.json",
            {
                "npcs": [
                    {
                        "id": 202,
                        "meleeDefenseMultiplier": 0.1,
                        "rangedDefenseMultiplier": 0.1,
                        "magicDefenseMultiplier": 1.0,
                        "combatStyle": "magic",
                    }
                ]
            },
        )
        result = run_manifest_generator(generator, source_dir, target_path)
        expect_failure(result, "npc generator unknown key guard", "unknown fields")
        (source_dir / "20-unknown-field.json").unlink()

        target_path.write_text('{"npcs":[]}\n', encoding="utf-8")
        result = run_manifest_generator(generator, source_dir, target_path, check=True)
        expect_failure(result, "npc generator stale output check", "is out of date")


def test_runner_only_selection() -> None:
    result = run_runner("--list", "--only", "item")
    expect_success(result, "runner only-item list", "item:")
    output = result.stdout + result.stderr
    if "npc:" in output:
        fail(f"runner only-item list unexpectedly included npc entry:\n{output}")

    result = run_runner("--dry-run", "--only", "npc")
    expect_success(result, "runner only-npc dry-run", "npc:")
    output = result.stdout + result.stderr
    if "item:" in output:
        fail(f"runner only-npc dry-run unexpectedly included item entry:\n{output}")

    result = run_runner("--only", "missing-generator")
    expect_failure(result, "runner unknown generator guard", "unknown generator name(s)")


def test_runner_group_selection() -> None:
    result = run_runner("--list", "--group", "combat")
    expect_success(result, "runner combat-group list", "group combat: item, npc")
    output = result.stdout + result.stderr
    if "item:" not in output or "npc:" not in output:
        fail(f"runner combat-group list missing expected generators:\n{output}")

    result = run_runner("--dry-run", "--group", "items")
    expect_success(result, "runner items-group dry-run", "item:")
    output = result.stdout + result.stderr
    if "npc:" in output:
        fail(f"runner items-group dry-run unexpectedly included npc entry:\n{output}")

    result = run_runner("--list", "--group", "npcs", "--only", "item")
    expect_success(result, "runner mixed group and only selection", "item:")
    output = result.stdout + result.stderr
    if "npc:" not in output:
        fail(f"runner mixed group and only selection missing npc entry:\n{output}")

    result = run_runner("--group", "missing-group")
    expect_failure(result, "runner unknown group guard", "unknown generator group(s)")


def main() -> None:
    item_generator = find_generator("item")
    npc_generator = find_generator("npc")
    test_item_generator(item_generator)
    test_npc_generator(npc_generator)
    test_runner_only_selection()
    test_runner_group_selection()
    print("PASS: generator regression checks validated")
    print("Generators validated: item, npc")


if __name__ == "__main__":
    main()
