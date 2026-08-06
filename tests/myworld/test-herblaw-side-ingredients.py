#!/usr/bin/env python3
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ITEM_ID = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
ITEM_DEFS_CUSTOM = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
ITEM_HERB_SECOND = ROOT / "server/conf/server/defs/extras/ItemHerbSecond.xml"
HERBLAW = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/herblaw/Herblaw.java"
COOKING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/cooking/ObjectCooking.java"
NPC_DROPS = ROOT / "server/src/com/openrsc/server/constants/NpcDrops.java"
CLIENT_ITEMS = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
SKILL_GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"
EATING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/itemactions/Eating.java"
ITEM_ACTION_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/ItemActionHandler.java"


def fail(message: str) -> None:
	print(f"FAIL: {message}")
	sys.exit(1)


def require(condition: bool, message: str) -> None:
	if not condition:
		fail(message)


def require_text(text: str, needle: str, message: str) -> None:
	require(needle in text, f"{message}: {needle}")


def forbid_text(text: str, needle: str, message: str) -> None:
	require(needle not in text, f"{message}: {needle}")


def main() -> None:
	item_id_text = ITEM_ID.read_text(encoding="utf-8")
	for snippet in (
		"FAIR_QUALITY_FISH_OIL(3240)",
		"GOOD_QUALITY_FISH_OIL(3241)",
		"FINE_QUALITY_FISH_OIL(3242)",
		"HIGH_QUALITY_FISH_OIL(3243)",
		"SUPERIOR_QUALITY_FISH_OIL(3244)",
		"SPIDER_EYE(3245)",
		"BAT_EYE(3246)",
		"BABY_DRAGON_EYE(3247)",
		"DEMON_EYE(3248)",
		"FERN_LEAF(3256)",
		"MUSHROOM(3257)",
		"FUNGUS(3258)",
		"RED_FLOWER(3259)",
		"BLUE_FLOWER(3260)",
		"public static final int maxCustom = 3311;",
	):
		require_text(item_id_text, snippet, "ItemId should define Herblaw side ingredient")

	custom_items = {
		item["id"]: item
		for item in json.loads(ITEM_DEFS_CUSTOM.read_text(encoding="utf-8"))["items"]
	}
	expected_names = {
		1410: "Low quality fish oil",
		3240: "Fair quality fish oil",
		3241: "Good quality fish oil",
		3242: "Fine quality fish oil",
		3243: "High quality fish oil",
		3244: "Superior quality fish oil",
		3245: "Spider eye",
		3238: "Zombie eye",
		3246: "Bat eye",
		3247: "Baby dragon's eye",
		3248: "Demon eye",
		3256: "Fern leaf",
		3257: "Mushroom",
		3258: "Fungus",
		3259: "Red flower",
		3260: "Blue flower",
	}
	for item_id, name in expected_names.items():
		item = custom_items.get(item_id)
		require(item is not None, f"Custom item {item_id} should exist")
		require(item["name"] == name, f"Custom item {item_id} should be named {name}")
		if item_id in {1410, 3240, 3241, 3242, 3243, 3244}:
			require(item["isStackable"] == 1, f"{name} should be stackable")
			require(item.get("command", "") == "", f"{name} should not expose an inventory command")
		else:
			require(item["isNoteable"] == 1, f"{name} should be noteable")
			require("potion ingredient" in item["description"].lower(), f"{name} should classify as Herblaw")

	recipes = {
		(int(node.findtext("unfinishedID")), int(node.findtext("secondID"))): int(node.findtext("potionID"))
		for node in ET.parse(ITEM_HERB_SECOND).getroot().findall("ItemHerbSecond")
	}
	expected_recipes = {
		(454, 3256): 474,
		(456, 3257): 477,
		(458, 3258): 480,
		(460, 220): 483,
		(462, 3259): 486,
		(935, 3260): 3198,
		(454, 1410): 489,
		(456, 3240): 492,
		(458, 3241): 495,
		(460, 3242): 498,
		(462, 3243): 566,
		(935, 3244): 3201,
		(454, 270): 569,
		(456, 3245): 963,
		(458, 3238): 1411,
		(460, 3246): 1414,
		(462, 3247): 1468,
		(935, 3248): 3204,
	}
	for key, value in expected_recipes.items():
		require(recipes.get(key) == value, f"Recipe {key} should create potion {value}")

	herblaw = HERBLAW.read_text(encoding="utf-8")
	require_text(herblaw, "return isFishOil(secondaryId) ? 10 : 1;", "all fish oil qualities should require ten secondaries")
	require_text(herblaw, "ItemId.SUPERIOR_QUALITY_FISH_OIL.id()", "Herblaw should include superior fish oil in fish-oil helper")

	cooking = COOKING.read_text(encoding="utf-8")
	for snippet in (
		"return weightedOilTier(1, 1, 1, 2);",
		"return weightedOilTier(5, 6, 6, 6);",
		"return ItemId.FISH_OIL.id();",
		"return ItemId.SUPERIOR_QUALITY_FISH_OIL.id();",
		"case 1190: // RAW_MANTA_RAY",
		"case 1192: // RAW_SEA_TURTLE",
	):
		require_text(cooking, snippet, "Cooking should roll tiered fish oil")

	drops = NPC_DROPS.read_text(encoding="utf-8")
	require_text(drops, "addNormalDrop(NpcId.GIANT_SPIDER_LVL8.id(), ItemId.SPIDER_EYE.id(), 1, 1", "low-level spider eye drop should be rarer")
	require_text(drops, "addNormalDrop(NpcId.GIANT_SPIDER_LVL31.id(), ItemId.SPIDER_EYE.id(), 1, 2", "higher-level spider eye drop should be easier")
	require_text(drops, "addNormalDrop(NpcId.POISON_SPIDER.id(), ItemId.SPIDER_EYE.id(), 1, 4", "poison spider should drop spider eyes")
	forbid_text(drops, "addNormalDrop(NpcId.SPIDER.id(), ItemId.SPIDER_EYE", "level-2 spider should not drop spider eyes")
	require_text(drops, "addNormalDrop(NpcId.GIANT_BAT.id(), ItemId.BAT_EYE.id(), 1, 4", "giant bats should drop bat eyes")
	require_text(drops, "babyBlueDragonDrops.addItemDrop(ItemId.BLUE_DRAGON_SCALE.id(), 1, 4);", "baby blue dragon scale chance should remain")
	require_text(drops, "babyBlueDragonDrops.addItemDrop(ItemId.BABY_DRAGON_EYE.id(), 1, 4);", "baby dragon eye should match scale chance")
	require_text(drops, "addNormalDrop(NpcId.LESSER_DEMON.id(), ItemId.DEMON_EYE.id(), 1, 1", "lesser demon eye drop should be rarest")
	require_text(drops, "addNormalDrop(NpcId.GREATER_DEMON.id(), ItemId.DEMON_EYE.id(), 1, 2", "greater demon eye drop should be easier")
	require_text(drops, "addNormalDrop(NpcId.BLACK_DEMON.id(), ItemId.DEMON_EYE.id(), 1, 4", "black demon eye drop should be easiest")

	client_items = CLIENT_ITEMS.read_text(encoding="utf-8")
	for snippet in (
		'new ItemDef("Low quality fish oil", "A low quality potion ingredient rendered from fish", "", 1, -1, "items:587"',
		'new ItemDef("Fair quality fish oil", "A fair quality potion ingredient rendered from fish", "", 2, -1, "items:587"',
		'new ItemDef("Good quality fish oil", "A good quality potion ingredient rendered from fish", "", 3, -1, "items:587"',
		'new ItemDef("Fine quality fish oil", "A fine quality potion ingredient rendered from fish", "", 4, -1, "items:587"',
		'new ItemDef("High quality fish oil", "A high quality potion ingredient rendered from fish", "", 5, -1, "items:587"',
		'new ItemDef("Superior quality fish oil", "A superior quality potion ingredient rendered from fish", "", 6, -1, "items:587"',
	):
		require_text(client_items, snippet, "fish oil should not expose Eat in client definitions")
	for snippet in (
		'new ItemDef("Spider eye", "A potion ingredient from a spider", "", 12, 116, "items:116"',
		'new ItemDef("Bat eye", "A potion ingredient from a bat", "", 20, 116, "items:116"',
		'new ItemDef("Baby dragon\'s eye", "A potion ingredient from a baby dragon", "", 36, 116, "items:116"',
		'new ItemDef("Demon eye", "A potion ingredient from a demon", "", 60, 116, "items:116"',
	):
		require_text(client_items, snippet, "new eyes should reuse masked Eye-of-newt visuals")
	for snippet in (
		'new ItemDef("Fern leaf", "A leafy potion ingredient harvested from ferns", "", 4, -1, "external-png:fern-leaf@17x19"',
		'new ItemDef("Mushroom", "A mushroom used as a potion ingredient", "", 8, -1, "external-png:mushroom@18x18"',
		'new ItemDef("Fungus", "A fungus used as a potion ingredient", "", 14, -1, "external-png:fungus@14x18"',
		'new ItemDef("Red flower", "A red flower used as a potion ingredient", "", 24, -1, "external-png:red-flower@15x18"',
		'new ItemDef("Blue flower", "A blue flower used as a potion ingredient", "", 40, -1, "external-png:blue-flower@15x18"',
	):
		require_text(client_items, snippet, "Brawn ingredients should use imported PNG visuals")
	require_text(client_items, 'new ItemDef("Superior quality fish oil"', "client should define superior fish oil")

	eating = EATING.read_text(encoding="utf-8")
	forbid_text(eating, "isFishOil(item.getCatalogId())", "Eating plugin should not route fish oil as food")
	forbid_text(eating, "You eat the fish oil", "Eating plugin should not consume fish oil")
	action_handler = ITEM_ACTION_HANDLER.read_text(encoding="utf-8")
	forbid_text(action_handler, "ItemId.FISH_OIL.id()", "Fish oil should not be treated as a combat consumable")

	guide = SKILL_GUIDE.read_text(encoding="utf-8")
	require_text(guide, '"fern leaf", "mushroom", "fungus", "limpwurt root", "red flower", "blue flower"', "guide should show Brawn tier order")
	require_text(guide, '"eye of newt", "spider eye", "zombie eye", "bat eye", "baby dragon\'s eye", "demon eye"', "guide should show insight tier order")
	require_text(guide, '"10 low quality fish oil", "10 fair quality fish oil", "10 good quality fish oil"', "guide should show low oil tiers")
	require_text(guide, '"10 fine quality fish oil", "10 high quality fish oil", "10 superior quality fish oil"', "guide should show high oil tiers")
	forbid_text(guide, "Low quality fish oil - 50% chance to heal 1", "fish oil should not be listed as food")

	print("PASS: Herblaw side ingredient expansion validated")


if __name__ == "__main__":
	main()
