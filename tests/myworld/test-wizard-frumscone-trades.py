#!/usr/bin/env python3

import json
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
WIZARD_PLUGIN = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/npcs/yanille/WizardFrumscone.java"
NPC_DEFS = ROOT / "server/conf/server/defs/NpcDefs.json"
CLIENT_ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"

WIZARD_FRUMSCONE = 515


def fail(message: str) -> None:
	print(f"FAIL: {message}")
	sys.exit(1)


def require(condition: bool, message: str) -> None:
	if not condition:
		fail(message)


def main() -> None:
	plugin = WIZARD_PLUGIN.read_text(encoding="utf-8")
	require("implements TalkNpcTrigger, OpNpcTrigger" in plugin,
		"Wizard Frumscone should handle both dialogue and right-click NPC actions")
	require('private static final String CONVERT_OPTION = "Convert my noted Stone";' in plugin,
		"Wizard Frumscone should expose a Convert Stone right-click action")
	require('"Convert Stone".equalsIgnoreCase(command)' in plugin,
		"Wizard Frumscone right-click command matching should be case-insensitive")

	for line in (
		'"My Magic Zombies make excellent combat practice"',
		'"For each one you defeat down here, I\'ll prepare one Stone"',
		'"You must bring me the Stone in noted form"',
		'"Convert my noted Stone"',
	):
		require(line in plugin, f"Wizard Frumscone dialogue is missing {line}")

	require("countId(ItemId.RUNE_STONE.id(), Optional.of(true))" in plugin,
		"Wizard Frumscone should count genuinely noted Stone")
	require("new Item(ItemId.RUNE_STONE.id(), 1, false)" in plugin,
		"Wizard Frumscone should award ordinary Stone")
	require("MageGuildStoneCredits.spendCredits(player, quantity)" in plugin,
		"Wizard Frumscone should consume one kill credit per Stone")
	require("ZOMBIE_EYE" not in plugin and "BLUE_DRAGON_SCALE" not in plugin,
		"Wizard Frumscone should not retain the retired drop trades")
	require("GroundItem" not in plugin,
		"Wizard Frumscone should not drop conversion overflow")

	npcs = json.loads(NPC_DEFS.read_text(encoding="utf-8"))["npcs"]
	frumscone = next((npc for npc in npcs if npc["id"] == WIZARD_FRUMSCONE), None)
	require(frumscone is not None, "NpcDefs should include Wizard Frumscone")
	require(frumscone["command"] == "Convert Stone",
		"Server NPC definition should expose Convert Stone")
	require(frumscone["command2"] == "",
		"Server NPC definition should remove the obsolete second trade action")

	client_defs = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
	require('new NPCDef("Wizard Frumscone", "A confused looking wizard", "Convert Stone", ""' in client_defs,
		"Client runtime NPC definition should expose the Stone conversion command")

	print("PASS: Wizard Frumscone Stone conversion validated")


if __name__ == "__main__":
	main()
