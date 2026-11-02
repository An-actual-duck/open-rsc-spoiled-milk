#!/usr/bin/env python3
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OBJECT_DEFS = ROOT / "server/conf/server/defs/GameObjectDef.xml"
HARVESTING_DEFS = ROOT / "server/conf/server/defs/extras/ObjectHarvesting.xml"
ITEM_DEFS_CUSTOM = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
ITEM_HERB_SECOND = ROOT / "server/conf/server/defs/extras/ItemHerbSecond.xml"
HARVESTING = ROOT / "server/plugins/com/openrsc/server/plugins/custom/skills/harvesting/Harvesting.java"
CLIENT_DEFS = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
SKILL_GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"
MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
ASSET_ROOT = ROOT / "dev/myworld/assets/runtime/sprites/items/inventory-ground/resources"


BRAWN_HARVESTABLES = {
	34: (3256, 1, 50, "Fern leaf"),
	396: (3256, 1, 50, "Fern leaf"),
	397: (3256, 1, 50, "Fern leaf"),
	398: (3256, 1, 50, "Fern leaf"),
	399: (3256, 1, 50, "Fern leaf"),
	401: (3256, 1, 50, "Fern leaf"),
	402: (3256, 1, 50, "Fern leaf"),
	38: (3257, 8, 60, "Mushroom"),
	205: (3258, 30, 122, "Fungus"),
	37: (3259, 54, 312, "Red flower"),
	285: (3260, 70, 768, "Blue flower"),
}


def fail(message: str) -> None:
	print(f"FAIL: {message}")
	sys.exit(1)


def require(condition: bool, message: str) -> None:
	if not condition:
		fail(message)


def require_text(text: str, snippet: str, message: str) -> None:
	require(snippet in text, f"{message}: {snippet}")


def main() -> None:
	object_defs = ET.parse(OBJECT_DEFS).getroot().findall("GameObjectDef")
	harvesting_defs = {
		int(entry.findtext("int")): entry.find("ObjectHarvestingDef")
		for entry in ET.parse(HARVESTING_DEFS).getroot().findall("entry")
	}
	custom_items = {
		int(item["id"]): item
		for item in json.loads(ITEM_DEFS_CUSTOM.read_text(encoding="utf-8"))["items"]
	}
	recipes = {
		(int(node.findtext("unfinishedID")), int(node.findtext("secondID"))): int(node.findtext("potionID"))
		for node in ET.parse(ITEM_HERB_SECOND).getroot().findall("ItemHerbSecond")
	}

	for object_id, (item_id, level, xp, item_name) in BRAWN_HARVESTABLES.items():
		require(object_id < len(object_defs), f"Object {object_id} should exist")
		obj = object_defs[object_id]
		require(obj.findtext("command1") == "Harvest", f"Object {object_id} should expose primary Harvest")
		require(obj.findtext("command2") == "Examine", f"Object {object_id} should keep Examine")

		harvesting_def = harvesting_defs.get(object_id)
		require(harvesting_def is not None, f"Object {object_id} should have ObjectHarvestingDef")
		require(int(harvesting_def.findtext("prodId")) == item_id, f"Object {object_id} should produce {item_id}")
		require(int(harvesting_def.findtext("requiredLvl")) == level, f"Object {object_id} should require Harvesting {level}")
		require(int(harvesting_def.findtext("exp")) == xp, f"Object {object_id} should award {xp} XP")

		item = custom_items.get(item_id)
		require(item is not None, f"{item_name} item {item_id} should exist")
		require(item["name"] == item_name, f"Item {item_id} should be named {item_name}")
		require(item["isNoteable"] == 1, f"{item_name} should be noteable")
		require("potion ingredient" in item["description"].lower(), f"{item_name} should classify as Herblaw")

	quest_flower = object_defs[188]
	require(quest_flower.findtext("command1") == "WalkTo", "Pirate's Treasure flower should keep WalkTo")
	require(quest_flower.findtext("command2") == "Examine", "Pirate's Treasure flower should keep Examine")
	require(188 not in harvesting_defs, "Pirate's Treasure flower must not become harvestable")

	expected_recipes = {
		(454, 3256): 474,
		(456, 3257): 477,
		(458, 3258): 480,
		(460, 220): 483,
		(462, 3259): 486,
		(935, 3260): 3198,
	}
	for recipe, potion_id in expected_recipes.items():
		require(recipes.get(recipe) == potion_id, f"Brawn recipe {recipe} should create {potion_id}")

	for file_name in ("fern-leaf.png", "mushroom.png", "fungus.png", "red-flower.png", "blue-flower.png"):
		require((ASSET_ROOT / file_name).is_file(), f"Missing imported Brawn ingredient sprite {file_name}")

	harvesting = HARVESTING.read_text(encoding="utf-8")
	require_text(harvesting, "LIMPWURTROOT(1281, new ItemLevelXPTrio(ItemId.LIMPWURT_ROOT.id(), 38, 144))", "Limpwurt should align to T6 shears")

	client_defs = CLIENT_DEFS.read_text(encoding="utf-8")
	for snippet in (
		'new GameObjectDef("Fern", "A leafy plant", "Harvest", "Examine", 0, 1, 1, 0, "fern", i++)',
		'new GameObjectDef("Flower", "Ooh thats pretty", "Harvest", "Examine", 0, 1, 1, 0, "flower", i++)',
		'new GameObjectDef("Mushroom", "I think it\'s a poisonous one", "Harvest", "Examine", 0, 1, 1, 0, "mushroom", i++)',
		'new GameObjectDef("Fungus", "A creepy looking fungus", "Harvest", "Examine", 0, 1, 1, 0, "nastyfungus", i++)',
		'new GameObjectDef("flower", "A nice colourful flower", "Harvest", "Examine", 1, 1, 1, 0, "blueflower", i++)',
	):
		require_text(client_defs, snippet, "Client should expose Brawn harvest object action")
	require_text(client_defs, 'new GameObjectDef("Flower", "Ooh thats pretty", "WalkTo", "Examine", 0, 1, 1, 0, "flower", i++)', "Client should keep Pirate's Treasure flower safe")

	guide = SKILL_GUIDE.read_text(encoding="utf-8")
	for snippet in (
		'"fern leaf", "mushroom", "fungus", "limpwurt root", "red flower", "blue flower"',
		'new SkillMenuItem(3256, "1", "Fern leaf - T1 shears")',
		'new SkillMenuItem(3260, "70", "Blue flower - T10 shears")',
		'} else if (curTab == 5) {',
		'} else if (curTab == 6) {',
	):
		require_text(guide, snippet, "Guide should expose Brawn ingredient progression")

	mudclient = MUDCLIENT.read_text(encoding="utf-8")
	require_text(mudclient, 'skillGuideChosenTabs.add("Brawn");', "Harvest guide should include Brawn tab")

	print("PASS: Herblaw Brawn harvestable ingredients validated")


if __name__ == "__main__":
	main()
