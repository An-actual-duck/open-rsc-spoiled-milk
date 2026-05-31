#!/usr/bin/env python3
import json
import re
import sys
from collections import Counter
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
CLIENT_NPC_DEFS = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def wilderness_level(x: int, y: int) -> int:
    height = y // 944
    wild = 2203 - (y + (1776 - (944 * height)))
    if x + 2304 >= 2640:
        return 0
    return 1 + wild // 6 if wild > 0 else 0


def require_valid_drop_budget(drops: str, table_name: str) -> None:
    start = drops.find(f'new DropTable("{table_name}")')
    require(start >= 0, f"Missing drop table: {table_name}")
    end = drops.find("addEmptyDrop", start)
    require(end >= 0, f"Missing empty-drop budget terminator: {table_name}")
    section = drops[start:end]
    weights = re.findall(r"add(?:Item|Table)Drop\([^;]*,\s*(\d+)\);", section)
    total = sum(int(weight) for weight in weights)
    require(total <= 128, f"{table_name} exceeds the 128 drop-weight budget: {total}")


def drop_table_section(drops: str, table_name: str) -> str:
    start = drops.find(f'new DropTable("{table_name}")')
    require(start >= 0, f"Missing drop table: {table_name}")
    end = drops.find("addEmptyDrop", start)
    require(end >= 0, f"Missing empty-drop budget terminator: {table_name}")
    return drops[start:end]


def client_npc_line(npc_id: int) -> str:
    count = 0
    pending_sprites = ""
    for line in CLIENT_NPC_DEFS.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if stripped.startswith("sprites = new int[]"):
            pending_sprites = stripped
        if "npcs.add(new NPCDef(" in stripped:
            if count == npc_id:
                return f"{pending_sprites}\n{stripped}"
            count += 1
    fail(f"Client NPC definition id {npc_id} is missing")


def main() -> None:
    npc_ids = (SERVER / "src/com/openrsc/server/constants/NpcId.java").read_text(encoding="utf-8")
    drops = (SERVER / "src/com/openrsc/server/constants/NpcDrops.java").read_text(encoding="utf-8")
    cracker = (SERVER / "plugins/com/openrsc/server/plugins/authentic/misc/HalloweenCracker.java").read_text(encoding="utf-8")
    populator = (SERVER / "src/com/openrsc/server/database/WorldPopulator.java").read_text(encoding="utf-8")
    audit = (ROOT / "tools/myworld/audit-npc-clusters.py").read_text(encoding="utf-8")

    require("GREY_KNIGHT(836)" in npc_ids, "Grey Knight NPC constant is missing")
    for snippet in (
        'new DropTable("Black Knight (66, 189) Jailer (265) Lord Darquarius (266) Renegade Knight (277)")',
        "ItemId.BLACK_2_HANDED_SWORD.id()",
        "ItemId.BLACK_SCIMITAR.id()",
        "ItemId.BLACK_BATTLE_AXE.id()",
        "ItemId.BLACK_GAUNTLETS.id()",
        "ItemId.BLACK_GREAVES.id()",
        "ItemId.WHITE_2_HANDED_SWORD.id()",
        "ItemId.WHITE_SCIMITAR.id()",
        "ItemId.WHITE_BATTLE_AXE.id()",
        "ItemId.WHITE_GAUNTLETS.id()",
        "ItemId.WHITE_GREAVES.id()",
        'new DropTable("Grey Knight (836)")',
        "ItemId.GREY_LONG_SWORD.id()",
        "ItemId.GREY_KITE_SHIELD.id()",
        "ItemId.GREY_PLATE_MAIL_BODY.id()",
        "ItemId.GREY_PLATE_MAIL_LEGS.id()",
        "ItemId.GREY_GAUNTLETS.id()",
        "ItemId.GREY_GREAVES.id()",
        "NpcId.GREY_KNIGHT.id()",
    ):
        require(snippet in drops, f"Knight drops missing: {snippet}")
    for table_name, forbidden in (
        ("Black Knight (66, 189) Jailer (265) Lord Darquarius (266) Renegade Knight (277)", ("BLACK_CHAIN_MAIL", "MEDIUM_BLACK_HELMET", "BLACK_PLATE_MAIL_TOP", "BLACK_PLATED_SKIRT", "BLACK_AXE", "BLACK_THROWING_KNIFE")),
        ("White Knight (102)", ("WHITE_CHAIN_MAIL", "MEDIUM_WHITE_HELMET", "WHITE_PLATE_MAIL_TOP", "WHITE_PLATED_SKIRT")),
        ("Grey Knight (836)", ("GREY_CHAIN_MAIL", "MEDIUM_GREY_HELMET", "GREY_PLATE_MAIL_TOP", "GREY_PLATED_SKIRT")),
    ):
        section = drop_table_section(drops, table_name)
        for item_name in forbidden:
            require(item_name not in section, f"{table_name} should not drop retired/hidden equipment: {item_name}")
    require("ItemId.BLACK_AXE.id()" not in drop_table_section(drops, "Dark Warrior (199)"),
            "Dark Warriors should not drop the hidden black hatchet")
    for hidden in ("BLACK_PLATE_MAIL_TOP", "BLACK_AXE", "BLACK_THROWING_KNIFE"):
        require(hidden not in cracker, f"Halloween cracker should not award hidden black equipment: {hidden}")
    require("ItemId.BLACK_GAUNTLETS.id()" in cracker and "ItemId.BLACK_GREAVES.id()" in cracker,
            "Halloween cracker black prize set should use the active hand/foot armour")
    require_valid_drop_budget(drops, "Black Knight (66, 189) Jailer (265) Lord Darquarius (266) Renegade Knight (277)")
    require_valid_drop_budget(drops, "White Knight (102)")
    require_valid_drop_budget(drops, "Grey Knight (836)")

    custom_defs = json.loads((SERVER / "conf/server/defs/NpcDefsCustom.json").read_text(encoding="utf-8"))
    grey = next((npc for npc in custom_defs["npcs"] if npc["id"] == 836), None)
    require(grey is not None, "Grey Knight definition is missing")
    require(grey["name"] == "Grey Knight" and grey["combatlvl"] == 56, "Grey Knight identity is incorrect")
    require(grey["attackable"] == 1 and grey["aggressive"] == 0, "Grey Knight combat disposition is incorrect")
    client_grey = client_npc_line(836)
    require('NPCDef("Grey Knight", "An armoured follower of Guthix"' in client_grey, "Client Grey Knight identity is incorrect")
    require("55, 58, 52, 60, true" in client_grey, "Client Grey Knight combat stats/disposition are incorrect")
    require("new int[]{19, 34, 43, -1, 49" in client_grey, "Client Grey Knight should use knight armour sprites")
    require("8421504, 8421504" in client_grey, "Client Grey Knight should use grey armour colours")
    myworld_defs = json.loads((SERVER / "conf/server/defs/NpcDefsMyWorld.json").read_text(encoding="utf-8"))
    grey_override = next((npc for npc in myworld_defs["npcs"] if npc["id"] == 836), None)
    require(grey_override is not None, "Grey Knight MyWorld combat override is missing")
    require(
        grey_override["meleeDefenseMultiplier"] == 1.0
        and grey_override["rangedDefenseMultiplier"] == 0.1
        and grey_override["magicDefenseMultiplier"] == 0.1,
        "Grey Knight should follow the armored knight defense profile",
    )

    overlay_path = SERVER / "conf/server/defs/locs/MyWorldNpcLocs.json"
    overlay = json.loads(overlay_path.read_text(encoding="utf-8"))["npclocs"]
    counts = Counter(loc["id"] for loc in overlay)
    expected_counts = {
        836: 4,
        199: 4,
        184: 4,
        57: 4,
        251: 4,
        188: 4,
        53: 4,
        232: 4,
        296: 4,
        189: 4,
        190: 5,
        201: 4,
        22: 4,
        294: 3,
        243: 4,
        342: 4,
        293: 4,
        555: 3,
        358: 1,
        311: 3,
        787: 3,
    }
    require(counts == expected_counts, f"Unexpected MyWorld spawn overlay counts: {dict(counts)}")
    for loc in (loc for loc in overlay if loc["id"] in (199, 184)):
        require(wilderness_level(loc["start"]["X"], loc["start"]["Y"]) > 0,
                f"Intended Wilderness addition is outside the Wilderness: {loc}")
    wilderness_additions = [loc for loc in overlay if loc["id"] != 836]
    require(len(wilderness_additions) == 74, "Wilderness overlay should add exactly 74 hostiles")
    for loc in wilderness_additions:
        require(wilderness_level(loc["start"]["X"], loc["start"]["Y"]) > 0,
                f"MyWorld Wilderness addition is outside the Wilderness: {loc}")

    require("MyWorldNpcLocs.json" in populator and "WANT_MYWORLD" in populator,
            "Runtime location loader does not include the MyWorld NPC overlay")
    require("MyWorldNpcLocs.json" in audit and "--wilderness-only" in audit
            and "Population By Wilderness Level" in audit,
            "Population audit does not cover the MyWorld Wilderness overlay")

    print("PASS: god knight drops and Wilderness population overlay validated")


if __name__ == "__main__":
    main()
