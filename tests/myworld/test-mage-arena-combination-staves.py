#!/usr/bin/env python3
"""Validate tier-11 Mage Arena combination staff scaffolding."""

import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ITEM_ID = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
CUSTOM_ITEMS = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
MYWORLD_ITEMS = ROOT / "server/conf/server/defs/ItemDefsMyWorld.json"
OBJECT_DEFS = ROOT / "server/conf/server/defs/GameObjectDef.xml"
CLIENT_DEFS = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
SCENERY_ID = ROOT / "server/src/com/openrsc/server/constants/SceneryId.java"
SKILL_GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"
EFFECTS = ROOT / "server/src/com/openrsc/server/content/EnchantingItemEffects.java"
SPELL_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java"
ADMINS = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java"
MAGE_ARENA = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/minigames/mage_arena/MageArena.java"
CHAMBER_GUARDIAN = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/minigames/mage_arena/Chamber_Guardian.java"
NPC_DEFS = ROOT / "server/conf/server/defs/NpcDefs.json"
NPC_DEFS_MYWORLD = ROOT / "server/conf/server/defs/NpcDefsMyWorld.json"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/tier-11-magic-gear-plan.md"
GOD_RELIC_PLAN = ROOT / "docs/myworld/in-progress-work-plans/god-relic-reward-plan.md"


def fail(message: str) -> None:
	print(f"FAIL: {message}")
	sys.exit(1)


def require(condition: bool, message: str) -> None:
	if not condition:
		fail(message)


def load_items(path: Path) -> dict[int, dict]:
	return {entry["id"]: entry for entry in json.loads(path.read_text(encoding="utf-8"))["items"]}


def load_npcs(path: Path) -> dict[int, dict]:
	return {entry["id"]: entry for entry in json.loads(path.read_text(encoding="utf-8"))["npcs"]}


def main() -> int:
	item_id = ITEM_ID.read_text(encoding="utf-8")
	custom_items = load_items(CUSTOM_ITEMS)
	myworld_items = load_items(MYWORLD_ITEMS)
	object_defs = OBJECT_DEFS.read_text(encoding="utf-8")
	client_defs = CLIENT_DEFS.read_text(encoding="utf-8")
	scenery_id = SCENERY_ID.read_text(encoding="utf-8")
	skill_guide = SKILL_GUIDE.read_text(encoding="utf-8")
	effects = EFFECTS.read_text(encoding="utf-8")
	spell_handler = SPELL_HANDLER.read_text(encoding="utf-8")
	admins = ADMINS.read_text(encoding="utf-8")
	mage_arena = MAGE_ARENA.read_text(encoding="utf-8")
	chamber_guardian = CHAMBER_GUARDIAN.read_text(encoding="utf-8")
	npc_defs = load_npcs(NPC_DEFS)
	myworld_npc_defs = load_npcs(NPC_DEFS_MYWORLD)
	plan = PLAN.read_text(encoding="utf-8")
	god_relic_plan = GOD_RELIC_PLAN.read_text(encoding="utf-8")

	expected = {
		3249: ("STAFF_OF_ELEMENTS", "Staff of Elements", "air, water, earth and fire", "0xE9A33C"),
		3250: ("STAFF_OF_POWER", "Staff of Power", "mind, chaos, death and blood", "0x8A4FD7"),
		3251: ("STAFF_OF_ENLIGHTENMENT", "Staff of Enlightenment", "body, cosmic, nature, law, soul and life", "0x56C7B2"),
	}

	for item_id_value, (constant, name, description_fragment, mask) in expected.items():
		require(f"{constant}({item_id_value})" in item_id, f"ItemId missing {constant}")
		item = custom_items.get(item_id_value)
		require(item is not None, f"ItemDefsCustom missing {name}")
		require(item["name"] == name, f"Wrong name for {item_id_value}")
		require(description_fragment in item["description"], f"{name} description should list rune coverage")
		require(item["isWearable"] == 1 and item["wearSlot"] == 4, f"{name} should be a mainhand weapon")
		require(item["requiredSkillID"] == 6 and item["requiredLevel"] == 80, f"{name} should require 80 Magic")
		require(item["weaponAimBonus"] == 20 and item["weaponPowerBonus"] == 10, f"{name} should match tier-11 staff legacy stats")
		require(item["isUntradable"] == 1, f"{name} should be untradable Mage Arena reward scaffolding")
		require(item["basePrice"] == 120000, f"{name} should use dragon-tier shop pricing")
		override = myworld_items.get(item_id_value)
		require(override is not None, f"ItemDefsMyWorld missing {name}")
		require(override["magicOffense"] == 64, f"{name} should have tier-11 magicOffense 64")
		require(override["requiredSkillID"] == 6 and override["requiredLevel"] == 80, f"{name} override should require 80 Magic")
		require(f'setCustomItemDefinition({item_id_value}, new ItemDef("{name}"' in client_defs, f"Client missing {name}")
		require(mask in client_defs, f"Client placeholder mask missing for {name}")
		require(f'new SkillMenuItem({item_id_value}, "80", "{name}")' in skill_guide, f"Magic guide missing {name}")

	require("public static final int maxCustom = 3309;" in item_id, "ItemId.maxCustom should include combination staves")

	for snippet in (
		"STAFF_OF_ELEMENTS_RUNES",
		"ItemId.AIR_RUNE.id(), ItemId.WATER_RUNE.id(), ItemId.EARTH_RUNE.id(), ItemId.FIRE_RUNE.id()",
		"STAFF_OF_POWER_RUNES",
		"ItemId.MIND_RUNE.id(), ItemId.CHAOS_RUNE.id(), ItemId.DEATH_RUNE.id(), ItemId.BLOOD_RUNE.id()",
		"STAFF_OF_ENLIGHTENMENT_RUNES",
		"ItemId.BODY_RUNE.id(), ItemId.COSMIC_RUNE.id(), ItemId.NATURE_RUNE.id(), ItemId.LAW_RUNE.id()",
		"ItemId.SOUL_RUNE.id(), ItemId.LIFE_RUNE.id()",
		"private static boolean isCombinationStaffForRune",
		"return contains(STAFF_OF_ELEMENTS_RUNES, runeId);",
		"return contains(STAFF_OF_POWER_RUNES, runeId);",
		"return contains(STAFF_OF_ENLIGHTENMENT_RUNES, runeId);",
	):
		require(snippet in effects, f"EnchantingItemEffects missing snippet: {snippet}")

	require("hasIbanBlastStaffEquipped(player)" in spell_handler, "Iban Blast should use shared staff requirement helper")
	require("hasEquipped(ItemId.STAFF_OF_IBAN.id())" in spell_handler, "Iban Blast should still accept Staff of Iban")
	require("hasEquipped(ItemId.STAFF_OF_POWER.id())" in spell_handler, "Iban Blast should accept Staff of Power")
	require("Quests.UNDERGROUND_PASS" in spell_handler, "Iban Blast should still require Underground Pass completion")
	require("getGodSpellCastCacheKey" not in spell_handler, "God spells should not keep Mage Arena cast-count cache helpers")
	require("this spell can only be used in the mage arena" not in spell_handler, "God spells should not require arena training")
	require("You must learn this spell first" not in spell_handler, "God spells should not require 100 arena casts")
	require("Well done .. you can now use" not in spell_handler, "God spells should not increment arena unlock counters")
	require("maged_kolodion" not in spell_handler, "Mage Arena spell casts should not trigger special retaliation")
	require("maged_kolodion" not in mage_arena, "Mage Arena should use normal combat instead of special magic retaliation flags")
	require("Mage Arena Learn Spell Event" not in mage_arena, "Mage Arena should not run old battle mage retaliation events")
	require("godSpellObject(" not in mage_arena, "Mage Arena should not use old god-spell scenery retaliation")
	for cast_cache in ("Saradomin strike_casts", "Flames of Zamorak_casts", "Void of Zamorak_casts", "Claws of Guthix_casts"):
		require(cast_cache not in spell_handler, f"SpellHandler should not use stale god spell cache: {cast_cache}")
		require(cast_cache not in admins, f"Admin spell unlocks should not set stale god spell cache: {cast_cache}")
	require("God spells no longer use Mage Arena cast-count unlocks." in admins, "Admin spell unlock message should document removed god-spell cast counts")

	require("MAGE_ARENA_STAFF_REWARD_CACHE" in mage_arena, "Mage Arena should track the new staff reward")
	require("claimMageArenaStoneStaff" in mage_arena, "Mage Arena should award the first staff from chamber stones")
	require("getMageArenaStaffForStone" in mage_arena, "Mage Arena should map each chamber stone to a staff")
	require("there you must decide what type of mage you want to be" in mage_arena, "Kolodion should no longer ask players to choose a god")
	require("claim your first staff from one of the three stones" in chamber_guardian, "Guardian should point first-time winners to the stones")
	require("charge spell" not in chamber_guardian.lower(), "Guardian should not keep old arena charge-spell training dialogue")
	require("god spell" not in chamber_guardian.lower(), "Guardian should not keep old god-spell training dialogue")
	require("player.getCache().set(\"mage_arena\", 4);" in mage_arena, "Mage Arena should complete after stone staff reward")
	for npc_id in (789, 790, 791):
		battle_mage = npc_defs[npc_id]
		myworld_battle_mage = myworld_npc_defs[npc_id]
		require(battle_mage["name"] == "Battle mage", f"NPC {npc_id} should remain a Battle mage")
		require(battle_mage["description"] == "A battle mage trained in elemental combat", f"NPC {npc_id} should not describe god-aligned spellcasting")
		require(int(battle_mage["attack"]) == 0, f"NPC {npc_id} base attack should stay 0")
		require(int(myworld_battle_mage["strength"]) == 52, f"NPC {npc_id} should use strength 52 as generic magic offense")
		require(int(battle_mage["hits"]) == 120, f"NPC {npc_id} should keep 120 hits")
		require(int(battle_mage["defense"]) == 90, f"NPC {npc_id} should keep 90 defense")
		require(int(battle_mage["combatlvl"]) == 52, f"NPC {npc_id} should keep combat level 52")
		require(float(myworld_battle_mage["meleeDefenseMultiplier"]) == 0.1, f"NPC {npc_id} should resist melee")
		require(float(myworld_battle_mage["rangedDefenseMultiplier"]) == 0.1, f"NPC {npc_id} should resist ranged")
		require(float(myworld_battle_mage["magicDefenseMultiplier"]) == 1.0, f"NPC {npc_id} should use normal magic defense")
	for stone_name, scenery_constant in (
		("Elemental Stone", "ELEMENTAL_STONE(1152)"),
		("Power Stone", "POWER_STONE(1153)"),
		("Enlightenment Stone", "ENLIGHTENMENT_STONE(1154)"),
	):
		require(f"<name>{stone_name}</name>" in object_defs, f"Server object defs should rename {stone_name}")
		require(f'new GameObjectDef("{stone_name}"' in client_defs, f"Client object defs should rename {stone_name}")
		require(scenery_constant in scenery_id, f"SceneryId should expose {scenery_constant}")
	for staff in ("STAFF_OF_ELEMENTS", "STAFF_OF_POWER", "STAFF_OF_ENLIGHTENMENT"):
		require(f"ItemId.{staff}.id()" in mage_arena, f"Mage Arena reward should include {staff}")
		require(f"ItemId.{staff}.id()" in chamber_guardian, f"Chamber Guardian shop should stock {staff}")
	for god_reward_snippet in (
		"awardGodCape",
		"getChosenGodCapeId",
		"mage_arena_god_choice",
		"ItemId.STAFF_OF_ZAMORAK.id(), 5",
		"ItemId.STAFF_OF_SARADOMIN.id(), 5",
		"ItemId.STAFF_OF_GUTHIX.id(), 5",
		"there you must decide which god you'll represent in the arena",
		"god relics are no longer claimed here",
		"SARADOMIN_STONE",
		"GUTHIX_STONE",
		"ZAMORAK_STONE",
	):
		require(god_reward_snippet not in mage_arena, f"Mage Arena should not keep old god reward snippet: {god_reward_snippet}")
		require(god_reward_snippet not in chamber_guardian, f"Chamber Guardian should not keep old god shop/reward snippet: {god_reward_snippet}")
	for old_stone_snippet in (
		"Saradomin stone",
		"Guthix stone",
		"Zamorak stone",
		"A faith stone",
		"chant to",
	):
		require(old_stone_snippet not in object_defs, f"Server object defs should not keep old stone snippet: {old_stone_snippet}")
		require(old_stone_snippet not in client_defs, f"Client object defs should not keep old stone snippet: {old_stone_snippet}")
		require(old_stone_snippet not in scenery_id, f"SceneryId should not keep old stone snippet: {old_stone_snippet}")

	require("placeholder generic staff icon and worn visuals" in plan, "Plan should note placeholder staff visuals")
	require("More substantial custom staff artwork is desired" in plan, "Plan should call for better staff art")
	require("Defeating Kolodion's demon form now sends the player to the chamber" in plan, "Plan should document implemented stone staff reward")
	require("God-spell cast-count training has been removed" in plan, "Plan should document removed god-spell training")
	require("god capes should no longer be Mage Arena rewards" in god_relic_plan, "God relic plan should own god cape reward direction")

	print("PASS: Mage Arena combination staff scaffolding validated")
	return 0


if __name__ == "__main__":
	sys.exit(main())
