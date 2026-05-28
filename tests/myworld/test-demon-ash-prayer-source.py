#!/usr/bin/env python3
import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ITEM_ID = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
ITEM_DEFS_CUSTOM = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
CLIENT_ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
NPC = ROOT / "server/src/com/openrsc/server/model/entity/npc/Npc.java"
NPC_DROPS = ROOT / "server/src/com/openrsc/server/constants/NpcDrops.java"
BONES_PLUGIN = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/misc/Bones.java"
SUMMONING = ROOT / "server/src/com/openrsc/server/content/Summoning.java"
WORK_ITEMS = ROOT / "docs/myworld/work-items.md"
SUMMONING_PLAN = ROOT / "docs/myworld/summoning-plan.md"


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def main() -> int:
    failures: list[str] = []

    item_id = ITEM_ID.read_text(encoding="utf-8")
    client = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
    npc = NPC.read_text(encoding="utf-8")
    npc_drops = NPC_DROPS.read_text(encoding="utf-8")
    bones = BONES_PLUGIN.read_text(encoding="utf-8")
    summoning = SUMMONING.read_text(encoding="utf-8")
    work_items = WORK_ITEMS.read_text(encoding="utf-8")
    summoning_plan = SUMMONING_PLAN.read_text(encoding="utf-8")

    require("DEMON_ASH(3112)" in item_id, "ItemId must reserve Demon ash at id 3112", failures)
    max_custom_match = re.search(r"public static final int maxCustom = (\d+);", item_id)
    require(max_custom_match is not None and int(max_custom_match.group(1)) > 3112, "ItemId.maxCustom must include Demon ash", failures)

    custom_items = json.loads(ITEM_DEFS_CUSTOM.read_text(encoding="utf-8"))["items"]
    demon_ash = next((item for item in custom_items if item.get("id") == 3112), None)
    require(demon_ash is not None, "ItemDefsCustom must define Demon ash", failures)
    if demon_ash:
        require(demon_ash.get("name") == "Demon ash", "Demon ash item name must be player-facing", failures)
        require(demon_ash.get("command") == "scatter", "Demon ash must use the scatter inventory action", failures)
        require(demon_ash.get("isNoteable") == 1, "Demon ash should remain noteable like generic Ashes", failures)

    require('setCustomItemDefinition(3112, new ItemDef("Demon ash"' in client, "client must directly define Demon ash", failures)
    require('"scatter", 80, 23, "items:23"' in client, "client Demon ash must use the ashes sprite and scatter command", failures)

    require("bones = ItemId.DEMON_ASH.id();" in npc, "demon NPC bone drop path must produce Demon ash", failures)
    require("currentNpcDrops.addItemDrop(ItemId.ASHES.id(), 1, 6);" in npc_drops, "imp-style generic Ashes drops must remain generic", failures)

    require('command.equalsIgnoreCase("scatter")' in bones, "prayer plugin must accept scatter actions", failures)
    require("case DEMON_ASH:" in bones, "Demon ash must award Prayer XP through the bones plugin", failures)
    require("skillXP = 2 * 80;" in bones, "Demon ash Prayer XP should stay pinned at 80 before config division", failures)

    greater_demon = re.search(r"GREATER_DEMON_PROFILE\s*=.*?;\n", summoning, re.S)
    require(greater_demon is not None and "ItemId.DEMON_ASH.id()" in greater_demon.group(0), "Greater Demon summon must consume Demon ash", failures)
    require("case DEMON_ASH:" in summoning, "Black Unicorn prayer-drop handling must include Demon ash", failures)
    require("return 80;" in summoning, "Black Unicorn Demon ash XP must match the prayer plugin value", failures)

    require("Demon ash prayer source is implemented" in work_items, "work-items doc must mark Demon ash as implemented", failures)
    require("1 Demon ash" in summoning_plan, "summoning plan must document Greater Demon Demon ash cost", failures)

    if failures:
        print("FAIL:")
        for failure in failures:
            print(f"- {failure}")
        return 1
    print("PASS: Demon ash is wired as a prayer source, demon drop, and Greater Demon summon cost")
    return 0


if __name__ == "__main__":
    sys.exit(main())
