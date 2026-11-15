#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
ITEM_ID_PATH = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "constants" / "ItemId.java"
ITEM_DEFS_CUSTOM_PATH = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsCustom.json"
ENTITY_HANDLER_PATH = ROOT / "Client_Base" / "src" / "com" / "openrsc" / "client" / "entityhandling" / "EntityHandler.java"
AGILITY_UTILS_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "skills" / "agility" / "AgilityUtils.java"
GNOME_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "skills" / "agility" / "GnomeAgilityCourse.java"
BARBARIAN_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "skills" / "agility" / "BarbarianAgilityCourse.java"
WILDERNESS_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "skills" / "agility" / "WildernessAgilityCourse.java"
POUCH_PLUGIN_PATH = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "custom" / "myworld" / "skills" / "agility" / "AgilityRewardPouches.java"


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(path: Path, snippet: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    if snippet not in text:
        fail(f"{label} missing expected snippet: {snippet}")


def main() -> None:
    require(ITEM_ID_PATH, "TIER_1_AGILITY_POUCH(2328)", "ItemId.java")
    require(ITEM_ID_PATH, "TIER_2_AGILITY_POUCH(2329)", "ItemId.java")
    require(ITEM_ID_PATH, "TIER_3_AGILITY_POUCH(2330)", "ItemId.java")
    item_id_text = ITEM_ID_PATH.read_text(encoding="utf-8")
    max_custom_match = re.search(r"public static final int maxCustom = (\d+);", item_id_text)
    if not max_custom_match or int(max_custom_match.group(1)) <= 2330:
        fail("ItemId.maxCustom must include the agility pouch IDs")

    defs = json.loads(ITEM_DEFS_CUSTOM_PATH.read_text(encoding="utf-8"))["items"]
    by_id = {item["id"]: item for item in defs}
    expected = {
        2328: ("Agility Stronghold Chest", 1),
        2329: ("Agility Outpost Chest", 35),
        2330: ("Agility Wilderness Chest", 52),
    }
    for item_id, (name, req) in expected.items():
        item = by_id.get(item_id)
        if item is None:
            fail(f"Missing agility pouch item def {item_id}")
        if item["name"] != name:
            fail(f"Agility pouch {item_id} has unexpected name: {item['name']}")
        if item["isStackable"] != 1 or item["isUntradable"] != 0:
            fail(f"Agility pouch {item_id} should be stackable and tradable")
        if item["requiredLevel"] != req or item["requiredSkillID"] != 16:
            fail(f"Agility pouch {item_id} should record agility requirement {req}")

    require(ENTITY_HANDLER_PATH, 'setCustomItemDefinition(2328, new ItemDef("Agility Stronghold Chest"', "EntityHandler.java")
    require(ENTITY_HANDLER_PATH, 'setCustomItemDefinition(2329, new ItemDef("Agility Outpost Chest"', "EntityHandler.java")
    require(ENTITY_HANDLER_PATH, 'setCustomItemDefinition(2330, new ItemDef("Agility Wilderness Chest"', "EntityHandler.java")
    require(ENTITY_HANDLER_PATH, '300, 187, "items:187"', "Tier 1 temporary built-in casket sprite")
    require(ENTITY_HANDLER_PATH, '900, 187, "items:187"', "Tier 2 temporary built-in casket sprite")
    require(ENTITY_HANDLER_PATH, '1800, 187, "items:187"', "Tier 3 temporary built-in casket sprite")

    require(AGILITY_UTILS_PATH, "new Item(completionRewardId, 1)", "AgilityUtils.java")
    require(AGILITY_UTILS_PATH, "You receive a \" + pouch.getDef(player.getWorld()).getName()", "AgilityUtils.java")
    require(GNOME_PATH, "ItemId.TIER_1_AGILITY_POUCH.id()", "GnomeAgilityCourse.java")
    require(BARBARIAN_PATH, "ItemId.TIER_2_AGILITY_POUCH.id()", "BarbarianAgilityCourse.java")
    require(WILDERNESS_PATH, "ItemId.TIER_3_AGILITY_POUCH.id()", "WildernessAgilityCourse.java")

    require(POUCH_PLUGIN_PATH, "AgilityUtilityEquipment.rollUniques(player,", "AgilityRewardPouches.java")
    if "getRareDropTable" in POUCH_PLUGIN_PATH.read_text():
        fail("Agility pouches must not access the shared rare table")
    loot = json.loads((ROOT / "server/conf/server/defs/extras/AgilityPouchRewards.json").read_text())
    assert loot["schemaVersion"] == 1 and len(loot["tiers"]) == 3
    weights = {"Ammunition":25,"Runes":15,"Sigils":15,"Ingredients":10,"Ore":8,"Logs":8,"Herbs":8,"Gems":5,"Food":3,"Potions":3}
    protected = {3343,3344,3345,3346,3347,3348}
    coating = {3341,3342,3466,3470}
    def leaves(node):
        if "itemId" in node: return [node]
        return [leaf for child in node["options"] for leaf in leaves(child)]
    for tier in loot["tiers"]:
        number=tier["tier"]
        assert tier["pouchId"] == 2327+number and tier["minRewards"] == (2 if number<3 else 3) and tier["maxRewards"]==4
        assert {c["label"]:c["weight"] for c in tier["categories"]} == weights
        all_leaves=[leaf for c in tier["categories"] for leaf in leaves(c)]
        assert not protected & {leaf["itemId"] for leaf in all_leaves}
        assert coating & {leaf["itemId"] for leaf in all_leaves} == (coating if number==3 else set())
        for leaf in all_leaves:
            assert 1<=leaf["min"]<=leaf["max"]
        ammo=next(c for c in tier["categories"] if c["label"]=="Ammunition")
        assert {c["label"] for c in ammo["options"] if "options" in c} == {"Arrows","Bolts","Darts","Throwing knives","Shuriken"}
        runes=next(c for c in tier["categories"] if c["label"]=="Runes")
        assert 37 in {leaf["itemId"] for leaf in leaves(runes)}
        if number==3:
            ingredients=next(c for c in tier["categories"] if c["label"]=="Ingredients")
            rare=[leaf for leaf in leaves(ingredients) if leaf["itemId"] in coating]
            assert len(rare)==4 and all(r["weight"]==2 and r["min"]==r["max"]==1 for r in rare)
    require(POUCH_PLUGIN_PATH, "AgilityPouchRewards.forPlayer(player)", "Validated gameplay reward source")

    print("PASS: agility pouch setup validated")


if __name__ == "__main__":
    main()
