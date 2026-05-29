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

DEFAULT_SOURCE_DIR = ROOT / "tools" / "generators" / "item-overrides"
DEFAULT_TARGET_PATH = (
    ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsMyWorld.json"
)
ALLOWED_TOP_LEVEL_KEYS = {"items", "description"}
ALLOWED_ITEM_FIELDS = {
    "id",
    "name",
    "description",
    "meleeOffense",
    "rangedOffense",
    "magicOffense",
    "weaponSpeed",
    "meleeDefense",
    "rangedDefense",
    "magicDefense",
    "requiredLevel",
    "requiredSkillID",
    "isWearable",
    "appearanceID",
    "wearableID",
    "wearSlot",
    "prayerBonus",
    "weaponAimBonus",
    "weaponPowerBonus",
    "armourBonus",
    "magicBonus",
    "basePrice",
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

    items = typed_data.get("items")
    if not isinstance(items, list):
        fail(f"{source_path.name} must contain a top-level 'items' array")

    return cast(list[dict[str, Any]], items)


def validate_item_entry(entry: object, source_path: Path) -> dict[str, Any]:
    if not isinstance(entry, dict):
        fail(f"{source_path.name} contains a non-object entry: {entry!r}")

    typed_entry = cast(dict[str, Any], entry)

    item_id = typed_entry.get("id")
    if not isinstance(item_id, int):
        fail(f"{source_path.name} contains an entry without an integer id: {entry!r}")

    unknown_fields = sorted(set(typed_entry.keys()) - ALLOWED_ITEM_FIELDS)
    if unknown_fields:
        fail(
            f"{source_path.name} entry {item_id} has unknown fields: "
            f"{', '.join(unknown_fields)}"
        )

    if len(typed_entry) == 1:
        fail(
            f"{source_path.name} entry {item_id} must override at least one field beyond id"
        )

    for field, value in typed_entry.items():
        if field == "id":
            continue
        if field == "name":
            if not isinstance(value, str):
                fail(f"{source_path.name} entry {item_id} field {field} must be a string")
            if not value.strip():
                fail(f"{source_path.name} entry {item_id} field {field} must not be empty")
            continue
        if field == "description":
            if not isinstance(value, str):
                fail(f"{source_path.name} entry {item_id} field {field} must be a string")
            continue
        if not isinstance(value, (int, float)):
            fail(f"{source_path.name} entry {item_id} field {field} must be numeric")

    return typed_entry


def describe_item_entry(entry: dict[str, Any]) -> str:
    categories = []
    if any(
        field in entry
        for field in (
            "meleeOffense",
            "weaponSpeed",
            "requiredLevel",
            "requiredSkillID",
            "isWearable",
            "appearanceID",
            "wearableID",
            "wearSlot",
            "prayerBonus",
            "weaponAimBonus",
            "weaponPowerBonus",
        )
    ):
        categories.append("melee")
    if "name" in entry:
        categories.append("identity")
    if "description" in entry:
        categories.append("identity")
    if "rangedOffense" in entry:
        categories.append("ranged")
    if "magicOffense" in entry:
        categories.append("magic")
    if any(
        field in entry for field in ("meleeDefense", "rangedDefense", "magicDefense")
    ):
        categories.append("defense")
    if "basePrice" in entry:
        categories.append("economy")
    if not categories:
        categories.append("other")
    return "/".join(categories)


def load_source_items(source_dir: Path) -> tuple[list[dict[str, Any]], list[str]]:
    if not source_dir.exists():
        fail(f"Missing source directory: {source_dir}")

    source_paths = sorted(source_dir.glob("*.json"))
    if not source_paths:
        fail(f"No source files found in {source_dir}")

    merged_items: list[dict[str, Any]] = []
    seen_ids: dict[int, Path] = {}
    summaries: list[str] = []

    for source_path in source_paths:
        with source_path.open("r", encoding="utf-8") as handle:
            data = json.load(handle)

        items = validate_source_file(data, source_path)
        category_counts: Counter[str] = Counter()

        for entry in items:
            validated_entry = validate_item_entry(entry, source_path)
            item_id = validated_entry["id"]
            if item_id in seen_ids:
                fail(
                    f"Duplicate item id {item_id} in {source_path.name} and {seen_ids[item_id].name}"
                )
            seen_ids[item_id] = source_path
            merged_items.append(validated_entry)
            category_counts[describe_item_entry(validated_entry)] += 1

        category_summary = ", ".join(
            f"{category}={count}" for category, count in sorted(category_counts.items())
        )
        summaries.append(f"{source_path.name}: {len(items)} items [{category_summary}]")

    return merged_items, summaries


def main() -> None:
    args = parse_generator_args(
        "Build or validate MyWorld item override definitions.",
        DEFAULT_SOURCE_DIR,
        DEFAULT_TARGET_PATH,
    )
    source_dir = args.source_dir.resolve()
    target_path = args.target.resolve()
    merged_items, summaries = load_source_items(source_dir)
    payload = {"items": merged_items}
    rendered = render_payload(payload)

    finalize_generation(
        check=args.check,
        source_dir=source_dir,
        target_path=target_path,
        rendered_payload=rendered,
        entry_count=len(merged_items),
        entry_label="items",
        generator_command="python3 ./tools/generators/run-generators.py --only item",
        summaries=summaries,
    )


if __name__ == "__main__":
    main()
