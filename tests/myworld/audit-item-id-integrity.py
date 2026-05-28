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
ITEM_ID_ENUM = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"


def load_entries(path: Path) -> list[dict]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(payload, dict) and isinstance(payload.get("items"), list):
        return payload["items"]
    if isinstance(payload, dict) and isinstance(payload.get("item"), list):
        return payload["item"]
    raise ValueError(f"Unknown item definition shape: {path}")


def load_merged_server_items() -> dict[int, dict]:
    items: dict[int, dict] = {}
    for path in SERVER_ITEM_PATHS:
        for entry in load_entries(path):
            item_id = int(entry["id"])
            merged = dict(items.get(item_id, {}))
            merged.update(entry)
            items[item_id] = merged
    return items


def load_runtime_indexed_server_items() -> list[dict | None]:
    items: list[dict | None] = []
    for path in SERVER_ITEM_PATHS[:2]:
        for entry in load_entries(path):
            item_id = int(entry["id"])
            while len(items) <= item_id:
                items.append(None)
            items[item_id] = entry
    return items


def duplicate_ids_by_file() -> dict[str, list[int]]:
    result: dict[str, list[int]] = {}
    for path in SERVER_ITEM_PATHS:
        seen: set[int] = set()
        duplicates: set[int] = set()
        for entry in load_entries(path):
            item_id = int(entry["id"])
            if item_id in seen:
                duplicates.add(item_id)
            seen.add(item_id)
        result[str(path.relative_to(ROOT))] = sorted(duplicates)
    return result


def parse_client_direct_defs() -> dict[int, dict]:
	source = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
	direct: dict[int, dict] = {}
	pattern = re.compile(
		r'setCustomItemDefinition\(\s*(\d+)\s*,\s*new ItemDef\(\s*"((?:[^"\\]|\\.)*)"\s*,'
		r'\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*"([^"]*)"',
		re.S,
	)
	for match in pattern.finditer(source):
		direct[int(match.group(1))] = {
			"name": bytes(match.group(2), "utf-8").decode("unicode_escape"),
			"sprite_id": int(match.group(6)),
			"sprite_location": match.group(7),
		}
	return direct


def parse_item_id_enum() -> dict[int, str]:
    source = ITEM_ID_ENUM.read_text(encoding="utf-8")
    constants: dict[int, str] = {}
    for match in re.finditer(r"\b([A-Z0-9_]+)\((\d+)\)", source):
        name = match.group(1)
        item_id = int(match.group(2))
        if item_id in constants:
            raise AssertionError(f"Duplicate ItemId value {item_id}: {constants[item_id]} and {name}")
        constants[item_id] = name
    return constants


def parse_animation_defs() -> list[dict]:
    source = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
    pattern = re.compile(
        r'animations\.add\(new AnimationDef\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*,\s*([^,]+)',
        re.S,
    )
    animations: list[dict] = []
    for match in pattern.finditer(source):
        animations.append(
            {
                "name": match.group(1),
                "type": match.group(2),
                "colour": match.group(3).strip(),
            }
        )
    return animations


def main() -> int:
    server_items = load_merged_server_items()
    runtime_items = load_runtime_indexed_server_items()
    client_direct = parse_client_direct_defs()
    enum_items = parse_item_id_enum()
    animations = parse_animation_defs()

    failures: list[str] = []

    for path, duplicates in duplicate_ids_by_file().items():
        if duplicates:
            failures.append(f"{path} has duplicate IDs: {duplicates[:20]}")

    for item_id in (2050, 2051, 2052):
        name = server_items.get(item_id, {}).get("name")
        client_name = client_direct.get(item_id, {}).get("name")
        enum_name = enum_items.get(item_id)
        print(f"{item_id}: server={name!r} client={client_name!r} enum={enum_name!r}")

    for item_id in (2050, 2051, 2052, 2072, 2085, 2098):
        if item_id >= len(runtime_items) or runtime_items[item_id] is None:
            failures.append(f"Runtime server item index {item_id} is empty")
            continue
        indexed_id = int(runtime_items[item_id]["id"])
        if indexed_id != item_id:
            failures.append(f"Runtime server item index {item_id} contains declared id {indexed_id}")

    mismatched_direct_names: list[str] = []
    for item_id, client_entry in sorted(client_direct.items()):
        client_name = client_entry["name"]
        server_name = server_items.get(item_id, {}).get("name")
        if server_name is not None and server_name != client_name:
            mismatched_direct_names.append(f"{item_id}: server={server_name!r} client={client_name!r}")

    if mismatched_direct_names:
        failures.append("Direct client/server item-name mismatches:\n  " + "\n  ".join(mismatched_direct_names[:80]))

    missing_server_defs = sorted(item_id for item_id in client_direct if item_id not in server_items)
    if missing_server_defs:
        failures.append(f"Direct client definitions without server definitions: {missing_server_defs[:80]}")

    missing_client_baseline = [
        item_id for item_id in (2050, 2051, 2052)
        if client_direct.get(item_id, {}).get("name") != server_items.get(item_id, {}).get("name")
    ]
    if missing_client_baseline:
        failures.append(f"Baseline robe IDs are not aligned on client/server: {missing_client_baseline}")

    expected_spear_sprites = {
        2207: (283, "items:283", 718),
        2208: (283, "items:283", 719),
        2209: (283, "items:283", 720),
        2210: (283, "items:283", 721),
        2211: (383, "items:383", 718),
        2212: (383, "items:383", 719),
        2213: (383, "items:383", 720),
        2214: (383, "items:383", 721),
    }
    for item_id, (sprite_id, sprite_location, appearance_id) in expected_spear_sprites.items():
        client_entry = client_direct.get(item_id, {})
        server_entry = server_items.get(item_id, {})
        if client_entry.get("sprite_id") != sprite_id or client_entry.get("sprite_location") != sprite_location:
            failures.append(
                f"Client spear sprite drift for {item_id}: "
                f"{client_entry.get('sprite_id')}/{client_entry.get('sprite_location')} expected {sprite_id}/{sprite_location}"
            )
        if int(server_entry.get("appearanceID", -1)) != appearance_id:
            failures.append(
                f"Server spear appearance drift for {item_id}: "
                f"{server_entry.get('appearanceID')} expected {appearance_id}"
            )

    expected_robe_sprites = {
        2050: (86, "items:86", 724),
        2051: (87, "items:87", 725),
        2052: (88, "items:88", 726),
    }
    for item_id, (sprite_id, sprite_location, appearance_id) in expected_robe_sprites.items():
        client_entry = client_direct.get(item_id, {})
        server_entry = server_items.get(item_id, {})
        if client_entry.get("sprite_id") != sprite_id or client_entry.get("sprite_location") != sprite_location:
            failures.append(
                f"Client robe sprite drift for {item_id}: "
                f"{client_entry.get('sprite_id')}/{client_entry.get('sprite_location')} expected {sprite_id}/{sprite_location}"
            )
        if int(server_entry.get("appearanceID", -1)) != appearance_id:
            failures.append(
                f"Server robe appearance drift for {item_id}: "
                f"{server_entry.get('appearanceID')} expected {appearance_id}"
            )

    expected_robe_appearances = {
        2050: ("wizardshat", "0xFFFFFF"),
        2051: ("wizardsrobe", "0xFFFFFF"),
        2052: ("skirt", "0xFFFFFF"),
        2072: ("wizardshat", "0x87CEEB"),
        2085: ("wizardsrobe", "0x87CEEB"),
        2098: ("skirt", "0x87CEEB"),
    }
    for item_id, (animation_name, colour) in expected_robe_appearances.items():
        appearance_id = int(server_items.get(item_id, {}).get("appearanceID", -1))
        # The Java client conditionally omits six pre-robe animation definitions at runtime.
        # The robe block reserves six source entries so server appearance IDs still resolve
        # to the intended runtime animation slots.
        animation_index = appearance_id - 1 + 6
        if animation_index < 0 or animation_index >= len(animations):
            failures.append(f"Robe appearance {item_id} points outside client animation table: {appearance_id}")
            continue
        animation = animations[animation_index]
        if animation.get("name") != animation_name or animation.get("colour") != colour:
            failures.append(
                f"Robe appearance {item_id} resolves to animation {animation_index} "
                f"{animation.get('name')}/{animation.get('colour')} expected {animation_name}/{colour}"
            )

    for item_id, client_entry in sorted(client_direct.items()):
        name = client_entry["name"].lower()
        if 2050 <= item_id <= 2675 and "wizard hat" in name and client_entry["sprite_location"] == "items:195":
            failures.append(f"Custom wizard hat {item_id} still points at candle sprite items:195")
        if 2050 <= item_id <= 2675 and "robe top" in name and client_entry["sprite_location"] == "items:84":
            failures.append(f"Custom robe top {item_id} still points at battle axe sprite items:84")

    print()
    print(f"Merged server item count: {len(server_items)}")
    print(f"Direct client custom definitions checked: {len(client_direct)}")
    print(f"ItemId enum constants checked: {len(enum_items)}")

    if failures:
        print("\nFAIL:")
        for failure in failures:
            print(failure)
        return 1

    print("\nPASS: item ID integrity audit found no direct client/server ID drift")
    return 0


if __name__ == "__main__":
    sys.exit(main())
