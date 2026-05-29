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


def apply_item_overrides(items, overrides):
    by_id = {item["id"]: item.copy() for item in items}
    for override in overrides:
        item_id = override["id"]
        if item_id in by_id:
            by_id[item_id].update(override)
    return by_id


def npc_by_id(npcs, npc_id: int):
    for npc in npcs:
        if npc["id"] == npc_id:
            return npc
    raise AssertionError(f"missing npc {npc_id}")


def main():
    items = load_json("server/conf/server/defs/ItemDefs.json", "item")
    item_overrides = load_json("server/conf/server/defs/ItemDefsMyWorld.json", "items")
    item_defs = apply_item_overrides(items, item_overrides)
    npcs = load_json("server/conf/server/defs/NpcDefs.json", "npcs")

    robe_of_guthix_top = item_defs[607]
    robe_of_guthix_bottom = item_defs[608]
    robe_of_saradomin_top = item_defs[807]
    robe_of_saradomin_bottom = item_defs[808]
    robe_of_zamorak_top = item_defs[702]
    robe_of_zamorak_bottom = item_defs[703]

    assert robe_of_guthix_top["name"] == "Robe of Guthix"
    assert robe_of_guthix_bottom["name"] == "Robe of Guthix"
    assert robe_of_saradomin_top["name"] == "Robe of Saradomin"
    assert robe_of_saradomin_bottom["name"] == "Robe of Saradomin"

    for item in (
        robe_of_guthix_top,
        robe_of_saradomin_top,
        robe_of_zamorak_top,
    ):
        assert item["magicBonus"] == 0, f"{item['name']} should not give magic bonus"
        assert item["prayerBonus"] == 6, f"{item['name']} top should match monks robe top"
    for item in (
        robe_of_guthix_bottom,
        robe_of_saradomin_bottom,
        robe_of_zamorak_bottom,
    ):
        assert item["magicBonus"] == 0, f"{item['name']} should not give magic bonus"
        assert item["prayerBonus"] == 5, f"{item['name']} bottom should match monks robe bottom"

    assert robe_of_saradomin_top["appearanceID"] == 77
    assert robe_of_saradomin_bottom["appearanceID"] == 82

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
