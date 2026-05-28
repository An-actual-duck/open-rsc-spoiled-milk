#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_ITEM_PATHS = [
    ROOT / "server/conf/server/defs/ItemDefs.json",
    ROOT / "server/conf/server/defs/ItemDefsCustom.json",
    ROOT / "server/conf/server/defs/ItemDefsMyWorld.json",
]
CLIENT_ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
EXTERNAL_ITEM_SPRITE_DIRS = [
    ROOT / "dev/myworld/assets/sprites/items/inventory-ground/agility-pouches",
    ROOT / "dev/myworld/assets/sprites/items/inventory-ground/tools",
    ROOT / "dev/myworld/assets/sprites/items/inventory-ground/weapons",
    ROOT / "dev/myworld/assets/sprites/items/inventory-ground/resources",
    ROOT / "dev/myworld/assets/sprites/items/inventory-ground",
    ROOT / "output/pngs",
]


def load_server_items() -> dict[int, dict]:
    items: dict[int, dict] = {}
    for path in SERVER_ITEM_PATHS:
        payload = json.loads(path.read_text(encoding="utf-8"))
        if isinstance(payload, dict) and "items" in payload and isinstance(payload["items"], list):
            entries = payload["items"]
        elif isinstance(payload, dict) and "item" in payload and isinstance(payload["item"], list):
            entries = payload["item"]
        elif isinstance(payload, dict):
            entries = payload.values()
        else:
            entries = payload
        for entry in entries:
            items[int(entry["id"])] = entry
    return items


def parse_itemdef_ids(source: str) -> set[int]:
    ids: set[int] = set()

    for match in re.finditer(r"setCustomItemDefinition\(\s*(\d+)\s*,\s*new ItemDef", source):
        ids.add(int(match.group(1)))

    for match in re.finditer(r"new ItemDef\((.*?)\)\);", source, re.S):
        body = match.group(1).strip()
        id_match = re.search(r",\s*(-?\d+)\s*$", body)
        if id_match:
            ids.add(int(id_match.group(1)))

    helper_patterns = [
        r'addMetalBoltDefinition\(".*?",\s*(\d+),',
        r'addMetalShearsDefinition\(".*?",\s*(\d+),',
        r'addWoodCrossbowDefinition\(".*?",\s*(\d+),',
        r'addFishingRodDefinition\(".*?",\s*".*?",\s*(\d+),',
        r'addResourceSeedDefinition\(".*?",\s*".*?",\s*(\d+),',
        r'addCustomWoodBowDefinitions\(".*?",\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),',
        r'addCustomWoodStaffDefinitions\(".*?",\s*".*?",\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),\s*(\d+),',
    ]
    for pattern in helper_patterns:
        for match in re.finditer(pattern, source):
            for group in match.groups():
                ids.add(int(group))

    for match in re.finditer(r"addWoolAccessoryLine\((\d+),", source):
        start = int(match.group(1))
        ids.update(range(start, start + 14 * 10))

    ranged_helpers = [
        "addAmuletLine",
        "addNecklaceLine",
        "addLawBankingNecklaceLine",
        "addRingLine",
        "addOffsetRingLine",
        "addSoulRingLine",
        "addAttunedRingLine",
        "addLawAmuletLine",
        "addLawBankingRingLine",
        "addCosmicAmuletLine",
        "addDeathAmuletLine",
        "addSoulAmuletLine",
        "addSoulNecklaceLine",
        "addLifeRingLine",
        "addLifeNecklaceLine",
        "addLifeAmuletLine",
    ]
    tier_arg_pattern = r"(tiers|new String\[\]\s*\{.*?\})"
    for helper in ranged_helpers:
        pattern = re.compile(rf"{helper}\(\s*(\d+)\s*,\s*{tier_arg_pattern}\s*,", re.S)
        for match in pattern.finditer(source):
            start = int(match.group(1))
            tier_arg = match.group(2)
            count = 5 if tier_arg == "tiers" else len(re.findall(r'"[^"]+"', tier_arg))
            ids.update(range(start, start + count))

    return ids


def external_png_refs(source: str) -> set[str]:
    return set(re.findall(r'"external-png:([^"]+)"', source))


def external_png_exists(asset_name: str) -> bool:
    file_name = asset_name if asset_name.endswith(".png") else f"{asset_name}.png"
    return any((base / file_name).is_file() for base in EXTERNAL_ITEM_SPRITE_DIRS)


def missing_external_pngs(source: str) -> list[str]:
    return sorted(ref for ref in external_png_refs(source) if not external_png_exists(ref))


def render_entry(entry: dict) -> str:
    flags: list[str] = []
    if entry.get("isWearable") == 1:
        flags.append("wearable")
    if entry.get("command"):
        flags.append(f'cmd={entry["command"]}')
    if entry.get("isUntradable") == 1:
        flags.append("untradable")
    if entry.get("isMembersOnly") == 1:
        flags.append("members")
    return f'{entry["id"]:>4}  {entry["name"]}' + (f" [{' '.join(flags)}]" if flags else "")


def print_id_bands(missing_ids: list[int]) -> None:
    bands: dict[int, int] = {}
    for item_id in missing_ids:
        band = (item_id // 100) * 100
        bands[band] = bands.get(band, 0) + 1
    for band, count in sorted(bands.items()):
        print(f"{band:>4}-{band + 99:<4}: {count}")


def main() -> int:
    server_items = load_server_items()
    source = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
    client_ids = parse_itemdef_ids(source)
    missing_ids = sorted(set(server_items) - client_ids)
    missing_pngs = missing_external_pngs(source)

    print(f"Server item ids: {len(server_items)}")
    print(f"Client item ids: {len(client_ids)}")
    print(f"Missing on client: {len(missing_ids)}")
    print(f"Missing external PNG assets: {len(missing_pngs)}")
    print()

    failures: list[str] = []
    if missing_ids:
        wearable_or_usable = [
            server_items[item_id]
            for item_id in missing_ids
            if server_items[item_id].get("isWearable") == 1 or server_items[item_id].get("command")
        ]
        print(f"Missing wearable/usable items: {len(wearable_or_usable)}")
        for entry in wearable_or_usable[:80]:
            print(render_entry(entry))

        if len(wearable_or_usable) > 80:
            print(f"... {len(wearable_or_usable) - 80} more wearable/usable items omitted")

        print()
        print("Top missing id bands:")
        print_id_bands(missing_ids)
        failures.append(f"{len(missing_ids)} server item IDs have no client definition")

    if missing_pngs:
        print()
        print("Missing external PNG references:")
        for ref in missing_pngs:
            print(f"  external-png:{ref}")
        failures.append(f"{len(missing_pngs)} external PNG references are missing")

    if failures:
        print()
        print("FAIL:")
        for failure in failures:
            print(f"  {failure}")
        return 1

    print("PASS: every server item id has a client definition and every external PNG reference exists")
    return 0


if __name__ == "__main__":
    sys.exit(main())
