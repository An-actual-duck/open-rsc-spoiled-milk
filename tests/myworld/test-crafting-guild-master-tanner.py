#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
NPC_ID = ROOT / "server/src/com/openrsc/server/constants/NpcId.java"
NPC_DEFS = ROOT / "server/conf/server/defs/NpcDefsCustom.json"
NPC_LOCS = ROOT / "server/conf/server/defs/locs/NpcLocs.json"
SCENERY_LOCS = ROOT / "server/conf/server/defs/locs/MyWorldSceneryLocs.json"
TANNER = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/npcs/alkharid/Tanner.java"
CLIENT_DEFS = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def main() -> None:
    npc_id_text = NPC_ID.read_text(encoding="utf-8")
    if "MASTER_TANNER(837)" not in npc_id_text:
        fail("Master tanner should have a stable custom NPC id")

    npc_defs = json.loads(NPC_DEFS.read_text(encoding="utf-8"))["npcs"]
    master_defs = [npc for npc in npc_defs if npc.get("id") == 837]
    if len(master_defs) != 1:
        fail("Master tanner NPC definition should exist exactly once")
    master_def = master_defs[0]
    if master_def.get("name") != "Master tanner":
        fail("Crafting guild tanner should be named Master tanner")
    if master_def.get("command") != "Trade":
        fail("Master tanner should expose right-click Trade")

    npc_locs = json.loads(NPC_LOCS.read_text(encoding="utf-8"))["npclocs"]
    if not any(
        loc.get("id") == 837
        and loc.get("start") == {"X": 345, "Y": 1554}
        and loc.get("min") == {"X": 343, "Y": 1551}
        and loc.get("max") == {"X": 346, "Y": 1557}
        for loc in npc_locs
    ):
        fail("Crafting guild tanner spawn should use the Master tanner NPC id")
    if any(loc.get("id") == 172 and loc.get("start") == {"X": 345, "Y": 1554} for loc in npc_locs):
        fail("Crafting guild should not keep the base Tanner spawn")

    sceneries = json.loads(SCENERY_LOCS.read_text(encoding="utf-8"))["sceneries"]
    if not any(
        loc.get("id") == 592 and loc.get("pos") == {"X": 343, "Y": 1552} and loc.get("direction") == 2
        for loc in sceneries
    ):
        fail("Crafting guild tanning rack should be moved to 343,1552 without rotation")
    if any(loc.get("id") == 592 and loc.get("pos") == {"X": 347, "Y": 1554} for loc in sceneries):
        fail("Old awkward crafting guild tanning rack placement should be removed")

    tanner_text = TANNER.read_text(encoding="utf-8")
    required_tanner_snippets = [
        "Only the best leather, of course",
        "NpcId.MASTER_TANNER.id()",
        "public void onOpNpc(Player player, Npc n, String command)",
        "command.equalsIgnoreCase(\"Trade\")",
        "ActionSender.showShop(player, selectedShop)",
    ]
    for snippet in required_tanner_snippets:
        if snippet not in tanner_text:
            fail(f"Master tanner plugin behavior is missing: {snippet}")

    # Giant-family hides/leathers are grandfathered owned items, not stock.
    # Check this particular shop and quantities, rather than any ItemId mention.
    master_stock = re.search(r"private final Shop masterShop = new Shop\((.*?)\);", tanner_text, re.S)
    if master_stock is None:
        fail("Master tanner shop definition is missing")
    rows = re.findall(r"new Item\(ItemId\.([A-Z_]+)\.id\(\), (\d+)\)", master_stock.group(1))
    required_stock = {
        "WOLF_LEATHER": 10,
        "HELLHOUND_LEATHER": 10,
        "CURED_SPIDER_CARAPACE": 10,
        "CURED_MAGIC_SPIDER_CARAPACE": 10,
        "CURED_SCORPION_CARAPACE": 10,
        "THREAD": 100,
        "NEEDLE": 10,
    }
    if len(rows) != len(required_stock) or {name: int(quantity) for name, quantity in rows} != required_stock:
        fail("Master tanner stock must match retained materials/tools and exclude retired giant leathers")

    client_text = CLIENT_DEFS.read_text(encoding="utf-8")
    for snippet in (
        'setCustomNpcDefinition(836, new NPCDef(\n\t\t\t"Grey Knight", "An armoured follower of Guthix"',
        'setCustomNpcDefinition(837, new NPCDef(\n\t\t\t"Master tanner", "He works with the finest leather"',
        "Config.S_RIGHT_CLICK_TRADE ? \"Trade\" : \"\"",
    ):
        if snippet not in client_text:
            fail(f"Client Master tanner definition is missing: {snippet}")
    if "MASTER_TANNER_FALLBACK" in client_text:
        fail("Client Master tanner should be a stable NPC override, not a fallback")

    print("PASS: crafting guild Master tanner placement, trade option, and stock look correct")


if __name__ == "__main__":
    main()
