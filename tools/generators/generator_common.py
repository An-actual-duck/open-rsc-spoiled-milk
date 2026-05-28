#!/usr/bin/env python3
import argparse
import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MANIFEST_PATH = ROOT / "tools" / "generators" / "generators.json"


def fail(message: str) -> None:
    print(f"FAIL: {message}", file=sys.stderr)
    sys.exit(1)


def display_path(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def render_payload(payload: dict) -> str:
    rendered = json.dumps(payload, indent=2)
    if not rendered.endswith("\n"):
        rendered += "\n"
    return rendered


def load_generator_manifest() -> dict[str, object]:
    if not MANIFEST_PATH.exists():
        fail(f"Missing generator manifest: {MANIFEST_PATH}")

    with MANIFEST_PATH.open("r", encoding="utf-8") as handle:
        data = json.load(handle)

    generators = data.get("generators")
    if not isinstance(generators, list) or not generators:
        fail(
            f"{display_path(MANIFEST_PATH)} must contain a non-empty 'generators' array"
        )

    groups = data.get("groups", {})
    if not isinstance(groups, dict):
        fail(
            f"{display_path(MANIFEST_PATH)} field 'groups' must be an object when present"
        )

    required_fields = {
        "name",
        "script",
        "sourceDir",
        "target",
    }
    names: set[str] = set()
    scripts: set[str] = set()
    validated: list[dict[str, object]] = []

    for entry in generators:
        if not isinstance(entry, dict):
            fail(
                f"{display_path(MANIFEST_PATH)} contains a non-object generator entry: {entry!r}"
            )
        missing_fields = sorted(required_fields - set(entry.keys()))
        if missing_fields:
            fail(
                f"{display_path(MANIFEST_PATH)} generator entry is missing fields: "
                f"{', '.join(missing_fields)}"
            )
        name = entry["name"]
        script = entry["script"]
        if not isinstance(name, str) or not name:
            fail(
                f"{display_path(MANIFEST_PATH)} has a generator entry with invalid name"
            )
        if not isinstance(script, str) or not script:
            fail(f"{display_path(MANIFEST_PATH)} generator {name!r} has invalid script")
        if name in names:
            fail(
                f"{display_path(MANIFEST_PATH)} contains duplicate generator name {name!r}"
            )
        if script in scripts:
            fail(
                f"{display_path(MANIFEST_PATH)} contains duplicate generator script {script!r}"
            )
        for field in ("sourceDir", "target"):
            if not isinstance(entry[field], str) or not entry[field]:
                fail(
                    f"{display_path(MANIFEST_PATH)} generator {name!r} has invalid {field}"
                )
        names.add(name)
        scripts.add(script)
        validated.append(entry)

    validated_groups: dict[str, list[str]] = {}
    for group_name, group_entries in groups.items():
        if not isinstance(group_name, str) or not group_name:
            fail(f"{display_path(MANIFEST_PATH)} contains an invalid group name")
        if not isinstance(group_entries, list) or not group_entries:
            fail(
                f"{display_path(MANIFEST_PATH)} group {group_name!r} must be a non-empty array"
            )
        validated_names: list[str] = []
        seen_group_names: set[str] = set()
        for entry_name in group_entries:
            if not isinstance(entry_name, str) or not entry_name:
                fail(
                    f"{display_path(MANIFEST_PATH)} group {group_name!r} contains an invalid generator name"
                )
            if entry_name not in names:
                fail(
                    f"{display_path(MANIFEST_PATH)} group {group_name!r} references unknown "
                    f"generator {entry_name!r}"
                )
            if entry_name in seen_group_names:
                fail(
                    f"{display_path(MANIFEST_PATH)} group {group_name!r} contains duplicate "
                    f"generator {entry_name!r}"
                )
            seen_group_names.add(entry_name)
            validated_names.append(entry_name)
        validated_groups[group_name] = validated_names

    return {"generators": validated, "groups": validated_groups}


def parse_generator_args(
    description: str, source_dir: Path, target_path: Path
) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=description)
    parser.add_argument(
        "--check",
        action="store_true",
        help="Validate authored sources and fail if the generated target file is out of date.",
    )
    parser.add_argument(
        "--source-dir",
        type=Path,
        default=source_dir,
        help="Override the authored source directory for testing or custom workflows.",
    )
    parser.add_argument(
        "--target",
        type=Path,
        default=target_path,
        help="Override the generated output path for testing or custom workflows.",
    )
    return parser.parse_args()


def finalize_generation(
    *,
    check: bool,
    source_dir: Path,
    target_path: Path,
    rendered_payload: str,
    entry_count: int,
    entry_label: str,
    generator_command: str,
    summaries: list[str],
) -> None:
    if check:
        if not target_path.exists():
            fail(
                f"Missing generated file: {display_path(target_path)}; run {generator_command}"
            )
        existing = target_path.read_text(encoding="utf-8")
        if existing != rendered_payload:
            fail(
                f"{display_path(target_path)} is out of date with "
                f"{display_path(source_dir)}; run {generator_command}"
            )
        print(
            f"Validated {display_path(target_path)} against {display_path(source_dir)} "
            f"({entry_count} {entry_label})"
        )
    else:
        target_path.write_text(rendered_payload, encoding="utf-8")
        print(
            f"Generated {display_path(target_path)} from {display_path(source_dir)} "
            f"({entry_count} {entry_label})"
        )

    for summary in summaries:
        print(f"  - {summary}")
