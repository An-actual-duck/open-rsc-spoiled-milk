#!/usr/bin/env python3
import json
from collections import Counter
from pathlib import Path
from typing import Any, cast

from generator_common import (
    ROOT,
    fail,
    finalize_generation,
    parse_generator_args,
    render_payload,
)

DEFAULT_SOURCE_DIR = ROOT / "tools" / "generators" / "npc-overrides"
DEFAULT_TARGET_PATH = (
    ROOT / "server" / "conf" / "server" / "defs" / "NpcDefsMyWorld.json"
)
ALLOWED_TOP_LEVEL_KEYS = {"npcs", "description"}
ALLOWED_NPC_FIELDS = {
    "id",
    "strength",
    "hairColour",
    "topColour",
    "bottomColour",
    "skinColour",
    "meleeDefenseMultiplier",
    "rangedDefenseMultiplier",
    "magicDefenseMultiplier",
}


def validate_source_file(data: object, source_path: Path) -> list[dict[str, Any]]:
    if not isinstance(data, dict):
        fail(f"{source_path.name} must contain a top-level JSON object")

    typed_data = cast(dict[str, Any], data)

    unknown_top_level = sorted(set(typed_data.keys()) - ALLOWED_TOP_LEVEL_KEYS)
    if unknown_top_level:
        fail(
            f"{source_path.name} has unknown top-level keys: {', '.join(unknown_top_level)}"
        )

    npcs = typed_data.get("npcs")
    if not isinstance(npcs, list):
        fail(f"{source_path.name} must contain a top-level 'npcs' array")

    return cast(list[dict[str, Any]], npcs)


def validate_npc_entry(entry: object, source_path: Path) -> dict[str, Any]:
    if not isinstance(entry, dict):
        fail(f"{source_path.name} contains a non-object entry: {entry!r}")

    typed_entry = cast(dict[str, Any], entry)

    npc_id = typed_entry.get("id")
    if not isinstance(npc_id, int):
        fail(f"{source_path.name} contains an entry without an integer id: {entry!r}")

    unknown_fields = sorted(set(typed_entry.keys()) - ALLOWED_NPC_FIELDS)
    if unknown_fields:
        fail(
            f"{source_path.name} entry {npc_id} has unknown fields: "
            f"{', '.join(unknown_fields)}"
        )

    if len(typed_entry) == 1:
        fail(
            f"{source_path.name} entry {npc_id} must override at least one field beyond id"
        )

    for field, value in typed_entry.items():
        if field == "id":
            continue
        if not isinstance(value, (int, float)):
            fail(f"{source_path.name} entry {npc_id} field {field} must be numeric")

    return typed_entry


def describe_npc_entry(entry: dict[str, Any]) -> str:
    if any(
        field in entry for field in ("hairColour", "topColour", "bottomColour", "skinColour")
    ):
        return "identity"
    if "strength" in entry:
        return "strength"

    if not any(
        field in entry
        for field in (
            "meleeDefenseMultiplier",
            "rangedDefenseMultiplier",
            "magicDefenseMultiplier",
        )
    ):
        return "other"

    melee = entry["meleeDefenseMultiplier"]
    ranged = entry["rangedDefenseMultiplier"]
    magic = entry["magicDefenseMultiplier"]
    if melee > ranged and melee > magic:
        return "melee"
    if ranged > melee and ranged > magic:
        return "ranged"
    if magic > melee and magic > ranged:
        return "magic"
    return "mixed"


def load_source_npcs(source_dir: Path) -> tuple[list[dict[str, Any]], list[str]]:
    if not source_dir.exists():
        fail(f"Missing source directory: {source_dir}")

    source_paths = sorted(source_dir.glob("*.json"))
    if not source_paths:
        fail(f"No source files found in {source_dir}")

    merged_npcs: list[dict[str, Any]] = []
    seen_ids: dict[int, Path] = {}
    summaries: list[str] = []

    for source_path in source_paths:
        with source_path.open("r", encoding="utf-8") as handle:
            data = json.load(handle)

        npcs = validate_source_file(data, source_path)
        category_counts: Counter[str] = Counter()

        for entry in npcs:
            validated_entry = validate_npc_entry(entry, source_path)
            npc_id = validated_entry["id"]
            if npc_id in seen_ids:
                fail(
                    f"Duplicate npc id {npc_id} in {source_path.name} and {seen_ids[npc_id].name}"
                )
            seen_ids[npc_id] = source_path
            merged_npcs.append(validated_entry)
            category_counts[describe_npc_entry(validated_entry)] += 1

        category_summary = ", ".join(
            f"{category}={count}" for category, count in sorted(category_counts.items())
        )
        summaries.append(f"{source_path.name}: {len(npcs)} npcs [{category_summary}]")

    return merged_npcs, summaries


def main() -> None:
    args = parse_generator_args(
        "Build or validate MyWorld NPC override definitions.",
        DEFAULT_SOURCE_DIR,
        DEFAULT_TARGET_PATH,
    )
    source_dir = args.source_dir.resolve()
    target_path = args.target.resolve()
    merged_npcs, summaries = load_source_npcs(source_dir)
    payload = {"npcs": merged_npcs}
    rendered = render_payload(payload)

    finalize_generation(
        check=args.check,
        source_dir=source_dir,
        target_path=target_path,
        rendered_payload=rendered,
        entry_count=len(merged_npcs),
        entry_label="npcs",
        generator_command="python3 ./tools/generators/run-generators.py --only npc",
        summaries=summaries,
    )


if __name__ == "__main__":
    main()
