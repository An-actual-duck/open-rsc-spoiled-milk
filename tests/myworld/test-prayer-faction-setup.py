#!/usr/bin/env python3
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def load_json(path: str, key: str):
    return json.loads((ROOT / path).read_text())[key]


def item_by_id(items, item_id: int):
    for item in items:
        if item["id"] == item_id:
            return item
    raise AssertionError(f"missing item {item_id}")


def npc_by_id(npcs, npc_id: int):
    for npc in npcs:
        if npc["id"] == npc_id:
            return npc
    raise AssertionError(f"missing npc {npc_id}")


def main():
    items = load_json("server/conf/server/defs/ItemDefs.json", "item")
    custom_items = load_json("server/conf/server/defs/ItemDefsCustom.json", "items")
    item_overrides = load_json("server/conf/server/defs/ItemDefsMyWorld.json", "items")
    item_defs = {item["id"]: item.copy() for item in items}
    item_defs.update({item["id"]: item.copy() for item in custom_items})
    for override in item_overrides:
        item_defs.setdefault(override["id"], {}).update(override)
    npcs = load_json("server/conf/server/defs/NpcDefs.json", "npcs")

    for item_id in (388, 389, 607, 608, 702, 703, 807, 808):
        item = item_defs[item_id]
        assert item["name"].startswith("Retired ")
        assert item["isWearable"] == 0
        assert item["appearanceID"] == 0
        assert item["wearableID"] == 0
        assert item["wearSlot"] == -1
        assert item["prayerBonus"] == 0

    active_god_robes = {
        3138: ("Wool robe top blessed by Zamorak", 4),
        3139: ("Wool robe bottom blessed by Zamorak", 3),
        3143: ("Wool robe top blessed by Saradomin", 4),
        3144: ("Wool robe bottom blessed by Saradomin", 3),
        3148: ("Wool robe top blessed by Guthix", 4),
        3149: ("Wool robe bottom blessed by Guthix", 3),
    }
    for item_id, (name, prayer_bonus) in active_god_robes.items():
        item = item_defs[item_id]
        assert item["name"] == name
        assert item["isWearable"] == 1
        assert item["prayerBonus"] == prayer_bonus

    priest = npc_by_id(npcs, 9)
    druid = npc_by_id(npcs, 200)
    kaqemeex = npc_by_id(npcs, 204)
    sanfew = npc_by_id(npcs, 205)

    assert priest["description"] == "A priest of Saradomin"
    assert priest["topColour"] == 255
    assert priest["bottomColour"] == 255

    for npc in (druid, kaqemeex, sanfew):
        assert npc["topColour"] == 65280
        assert npc["bottomColour"] == 65280


if __name__ == "__main__":
    main()
